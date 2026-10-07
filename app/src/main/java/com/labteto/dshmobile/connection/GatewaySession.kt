package com.labteto.dshmobile.connection

import com.labteto.dshmobile.core.wire.MobileAccess
import com.labteto.dshmobile.core.wire.MobileAccessMetadata
import com.labteto.dshmobile.core.wire.MobileAccessPairing
import com.labteto.dshmobile.core.wire.MobileAccessTls
import com.labteto.dshmobile.core.wire.NativePairResponse
import com.labteto.dshmobile.core.wire.NativeRenewOutcome
import com.labteto.dshmobile.core.wire.NativeRenewResponse
import com.labteto.dshmobile.core.wire.OkHttpRpcTransport
import com.labteto.dshmobile.core.wire.RpcHttpResponse
import com.labteto.dshmobile.core.wire.RpcTransport
import com.labteto.dshmobile.core.wire.RpcTransportException
import java.io.InputStream
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * The short-lived half of a `dsh-mobile` pairing, kept in memory.
 *
 * A gateway hands out two credentials at pairing and they could not be more different. The device
 * token is durable: it identifies the phone in the operator's device list, survives restarts, and is
 * the only thing that can mint a session — so it is encrypted at rest beside the relay tokens. The
 * session cookie is the opposite: it expires in hours, is re-mintable at any time from the device
 * token, and has no business being written to disk. This class holds the second kind and knows how
 * to ask for more of it.
 *
 * Sessions are per host, not global: a phone may be paired with a laptop on the LAN and with a
 * desktop through a tunnel at the same time, and each one issues its own.
 */
@Singleton
class GatewaySessions @Inject constructor(
    private val credentials: RelayCredentialStore,
    private val okHttpClient: OkHttpClient,
) {
    /** One gateway session: what rides the cookie, and what a mutation must present with it. */
    data class Session(
        val sessionToken: String,
        val csrfToken: String,
        val expiresAt: Long,
    ) {
        /** Whether this session is worth sending, with a minute of slack for clock skew. */
        fun isLive(now: Long = System.currentTimeMillis()): Boolean =
            sessionToken.isNotEmpty() && now < expiresAt - EXPIRY_SLACK_MS
    }

    private val live = ConcurrentHashMap<String, Session>()

    /** One pinned client per configured CA, built once (see [clientFor]). */
    private val pinnedClients = ConcurrentHashMap<String, OkHttpClient>()

    /**
     * A usable session for [config], renewing when the held one has aged out.
     *
     * Null means there is nothing to renew *from* — the device was never paired here, or its token
     * was dropped — which is the caller's signal to route the user to pairing rather than to retry.
     */
    suspend fun session(config: HostConfig): Session? {
        live[config.id]?.takeIf { it.isLive() }?.let { return it }
        return renew(config)
    }

    /** Mint a fresh session from the stored device token, replacing whatever was held. */
    suspend fun renew(config: HostConfig): Session? {
        val deviceToken = credentials.token(config.id) ?: return null
        return when (
            val outcome = MobileAccessPairing.nativeRenew(config.baseUrl, deviceToken, clientFor(config))
        ) {
            is NativeRenewOutcome.Renewed -> {
                val session = outcome.response.toSession()
                live[config.id] = session
                session
            }
            // Terminal: revoked by the operator or expired outright. Dropping the token here is what
            // turns the next connect into "pair again" instead of a renewal that can never succeed.
            NativeRenewOutcome.Revoked -> {
                credentials.remove(config.id)
                live.remove(config.id)
                null
            }
            // Transient: no network, a throttling gateway, a fence that refused this address. The
            // token stays, because none of those say anything about whether it is still good.
            else -> null
        }
    }

    /** Adopt the session a pairing just answered with, so the first connection needs no renewal. */
    fun adopt(hostId: String, response: NativePairResponse) {
        live[hostId] = Session(response.sessionToken, response.csrfToken, response.sessionExpiresAt)
    }

    /** The cookie header for [hostId], or null when no session is held. */
    fun cookie(hostId: String): String? =
        live[hostId]?.takeIf { it.isLive() }?.let { MobileAccess.sessionCookie(it.sessionToken) }

    /** The CSRF header value for [hostId], or null when no session is held. */
    fun csrf(hostId: String): String? = live[hostId]?.takeIf { it.isLive() }?.csrfToken

    /** Forget the session for [hostId]. The device token is untouched. */
    fun forget(hostId: String) {
        live.remove(hostId)
    }

    /**
     * The HTTP client for [config]: the shared one, or one that trusts only the CA pairing pinned.
     *
     * A gateway on a LAN terminates its own TLS with a private CA, and nothing in the platform store
     * vouches for it. The CA travels in [HostConfig] as base64 DER because that is the whole
     * decision — a chain that does not end at it is refused, and the hostname is deliberately not
     * checked (the CA is the identity).
     */
    fun clientFor(config: HostConfig): OkHttpClient {
        val encoded = config.gatewayCaDer ?: return okHttpClient
        // Cached, because this is called on *every* request: GatewayTransport.delegate() rebuilds its
        // transport per RPC, and building the client each time meant decoding the DER, parsing the
        // certificate, standing up a KeyStore, a TrustManagerFactory and an SSLContext, and then
        // handing OkHttp a brand-new client whose connection pool and TLS session cache are born
        // empty — so no connection or session was ever reused.
        //
        // Keyed by the CA rather than by host id: re-pairing a host installs a new CA under the same
        // id, and a stale pinned client would then refuse the host it was just paired with.
        return pinnedClients.getOrPut(encoded) {
            val der = runCatching { Base64.getDecoder().decode(encoded) }.getOrNull()
                ?: return@getOrPut okHttpClient
            MobileAccessTls.caPinnedClient(okHttpClient, der)
        }
    }

    private fun NativeRenewResponse.toSession(): Session =
        Session(sessionToken, csrfToken, sessionExpiresAt)

    private companion object {
        /** Renew a minute before the gateway would refuse, rather than after it does. */
        const val EXPIRY_SLACK_MS = 60_000L
    }
}

/**
 * The RPC transport for a gateway host: a session cookie, an `Origin`, a CSRF token — and a retry.
 *
 * The gateway authenticates every `/api` call against its session cookie and refuses a mutating
 * request that arrives without both the origin and the CSRF token. The cookie expires on its own
 * schedule, so a *single* 401 is expected over a long-lived connection and must not surface as the
 * connection having failed: this wrapper renews from the device token and repeats the call once.
 *
 * A second 401 is reported as it stands. It means the renewal did not help — the address is
 * answering as some other gateway, or the fence refused it — and repeating the call a third time
 * would only delay the verdict the user has to see.
 *
 * Built per call rather than held: the session it carries can change under it, and a transport that
 * cached a cookie would keep retrying with the one that just expired.
 */
class GatewayTransport(
    private val config: HostConfig,
    private val sessions: GatewaySessions,
    private val timeouts: ProbeTimeouts?,
) : RpcTransport {

    private val origin: String? = MobileAccess.originOf(config.baseUrl)

    private suspend fun delegate(): RpcTransport? {
        val session = sessions.session(config) ?: return null
        return OkHttpRpcTransport(
            baseUrl = config.baseUrl,
            client = sessions.clientFor(config),
            connectTimeoutMs = timeouts?.connectMs ?: DEFAULT_TIMEOUT_MS,
            readTimeoutMs = timeouts?.readMs ?: DEFAULT_TIMEOUT_MS,
            cookie = MobileAccess.sessionCookie(session.sessionToken),
            origin = origin,
            csrf = session.csrfToken,
        )
    }

    override suspend fun post(path: String, body: String): RpcHttpResponse =
        once { it.post(path, body) }

    override suspend fun <T> download(
        path: String,
        consume: (contentType: String?, contentDisposition: String?, body: InputStream) -> T,
    ): T = once { it.download(path, consume) }

    override suspend fun upload(
        path: String,
        contentType: String,
        contentLength: Long,
        body: InputStream,
        onProgress: ((sent: Long) -> Unit)?,
    ): RpcHttpResponse = once { it.upload(path, contentType, contentLength, body, onProgress) }

    /**
     * Run [block] against a fresh transport, renewing once if the session it carried had expired.
     *
     * The failure this catches is deliberately narrow: only a 401, which is the gateway saying the
     * cookie is finished. A 403 is the fence refusing the address and a 500 is the harness failing —
     * neither is fixed by a new session, and retrying either would be noise.
     */
    private suspend fun <T> once(block: suspend (RpcTransport) -> T): T {
        val first = delegate() ?: throw RpcTransportException(401, "no gateway session for this host")
        try {
            return block(first)
        } catch (e: RpcTransportException) {
            if (e.status != 401) throw e
            sessions.renew(config) ?: throw e
            val second = delegate() ?: throw e
            return block(second)
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000L
    }
}

/**
 * What a gateway says it is, for the pairing screen and the connection diagnostics.
 *
 * One call, and it is unauthenticated on purpose: naming the plugin and the discovery protocol is
 * how an address is told apart from a bare harness before anything is sent to it.
 */
suspend fun gatewayMetadata(baseUrl: String, client: OkHttpClient): MobileAccessMetadata? =
    MobileAccessPairing.metadata(baseUrl, client)
