package com.labteto.dshmobile.connection

import com.labteto.dshmobile.core.wire.DshApiClient
import com.labteto.dshmobile.core.wire.MobileAccess
import com.labteto.dshmobile.core.wire.OkHttpRpcTransport
import com.labteto.dshmobile.core.wire.RelayTls
import com.labteto.dshmobile.core.wire.RemoteStreamMux
import com.labteto.dshmobile.core.wire.WsChannel
import com.labteto.dshmobile.core.wire.dto.REMOTE_STREAM_MUX_PATH
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place a [DshApiClient] is built.
 *
 * Three facts have to agree for a call to reach a relay at all — the scheme, the certificate pin and
 * the bearer token — and they have to agree across the unary transport *and* the mux upgrade,
 * because the connection loop needs both to succeed inside one 3000ms generation. A relay refuses
 * an upgrade that arrives without the header, and the loop can only report that as a stream that
 * would not open. Splitting the assembly across the manager and the discovery engine is how one of
 * the three quietly goes missing, so it happens here or nowhere.
 *
 * Harness 0.1.2 reduced two downlink sockets to one, so there is a single [RemoteStreamMux] per
 * connection generation rather than a socket factory the client calls twice.
 */
@Singleton
class HarnessClientFactory @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val credentials: RelayCredentialStore,
    private val sessions: HarnessSessionStore,
    private val gatewaySessions: GatewaySessions,
) {
    /**
     * Pinned clients, one per fingerprint.
     *
     * Each carries its own `SSLContext` and connection pool, so building one per request would
     * throw away every kept-alive connection — the resident mux socket included. There is one
     * relay in play at a time and the key is a base64 hash, so the map never grows meaningfully.
     */
    private val pinned = ConcurrentHashMap<String, OkHttpClient>()

    /**
     * The HTTP client to reach [fingerprint]'s relay with, or the shared one when nothing is pinned.
     *
     * A null fingerprint means plaintext or a certificate the platform already trusts; both are
     * served correctly by the default trust store.
     */
    fun httpClient(fingerprint: String?): OkHttpClient =
        if (fingerprint == null) okHttpClient
        else pinned.getOrPut(fingerprint) { RelayTls.pinnedClient(okHttpClient, fingerprint) }

    /** The `Authorization` value for [config], or null when it is not a paired relay. */
    suspend fun authorizationFor(config: HostConfig): String? =
        if (config.isRelay) credentials.authorization(config.id) else null

    /**
     * The HTTP client for [config], pinned when the endpoint publishes an identity to pin.
     *
     * A relay pins a public key; a gateway pins a CA. Both are cached by the value pinned, because
     * each carries its own `SSLContext` and connection pool and rebuilding one per request would
     * discard every kept-alive connection, the resident mux socket included.
     */
    fun httpClient(config: HostConfig): OkHttpClient = when {
        config.isGateway -> gatewaySessions.clientFor(config)
        else -> httpClient(config.relayFingerprint)
    }

    /**
     * The harness browser session for [config], or null.
     *
     * Only for a direct connection. Behind a relay the relay holds the harness session and
     * injects it upstream, so sending one from here would put the host's own credential on the
     * network — and the relay strips the header anyway.
     */
    private suspend fun cookieFor(config: HostConfig): String? = when {
        // The relay holds the harness session itself, so a cookie sent from here would put the
        // host's own credential on the network — and the relay strips it anyway.
        config.isRelay -> null
        // A gateway mints a session *for this device*; it is the whole of what `/api` authenticates
        // against, and the harness behind it never sees this cookie.
        //
        // Minted rather than read, because the mux is opened *before* any unary call: the loop's
        // first step is the WebSocket upgrade, so a session that only the unary path knew how to
        // obtain would leave every generation failing its handshake with a 401 nobody could fix.
        config.isGateway -> gatewaySessions.session(config)?.let {
            MobileAccess.sessionCookie(it.sessionToken)
        }
        else -> sessions.cookie(config.id)
    }

    /**
     * A client for [config], carrying whatever credential and pin that endpoint needs.
     *
     * [timeouts] is for probes, and a probe keeps the budget it is given. The live connection passes
     * none and gets [NO_READ_DEADLINE_MS] instead: the response wait on that channel is gated on work
     * the host does, and a long `session/page` on a big session — or a `/compact`, which is what
     * found this — is not a stalled request. Connect and write stay bounded, so a dead *link* is
     * still caught; see `OkHttpRpcTransport`'s class comment and `NO_READ_DEADLINE_MS`.
     */
    suspend fun clientFor(config: HostConfig, timeouts: ProbeTimeouts? = null): DshApiClient {
        // A gateway wraps its transport rather than configuring one: the session it authenticates
        // with expires mid-connection, and the retry that mints a new one has to sit between the
        // caller and the socket to be invisible.
        if (config.isGateway) return DshApiClient(GatewayTransport(config, gatewaySessions, timeouts))
        val http = httpClient(config.relayFingerprint)
        val authorization = authorizationFor(config)
        val base = config.baseUrl
        return DshApiClient(
            transport = OkHttpRpcTransport(
                baseUrl = base,
                client = http,
                connectTimeoutMs = connectTimeoutFor(timeouts),
                readTimeoutMs = readTimeoutFor(timeouts),
                authorization = authorization,
                cookie = cookieFor(config),
            ),
        )
    }

    /**
     * The mux carrying every stream of one connection generation.
     *
     * Separate from [clientFor] because its lifetime is the generation's, not the client's: the
     * connection loop builds a new one per attempt and closes it when the generation ends, while
     * the unary client outlives both.
     */
    suspend fun muxFor(config: HostConfig): RemoteStreamMux {
        val http = httpClient(config)
        val authorization = authorizationFor(config)
        val base = config.baseUrl
        val cookie = cookieFor(config)
        // The gateway's fence runs on the upgrade too, and refuses a handshake with no accepted
        // origin with the same bare 403 it uses for a POST. A relay and a bare harness do not read
        // the header, so it is sent only where it is required.
        val origin = if (config.isGateway) MobileAccess.originOf(base) else null
        return RemoteStreamMux { sink ->
            WsChannel(com.labteto.dshmobile.core.wire.resolveHarnessUrl(base, REMOTE_STREAM_MUX_PATH).toString(), http, sink, authorization, cookie, origin)
        }
    }

    /**
     * A client for an address nothing is remembered about yet — the LAN sweep and the manual field.
     *
     * Deliberately unauthenticated: an address that has not been paired has no credential to send,
     * and a relay answers such a probe with the 403 that routes the user to pairing.
     */
    fun anonymousClient(baseUrl: String, timeouts: ProbeTimeouts): DshApiClient = DshApiClient(
        transport = OkHttpRpcTransport(
            baseUrl = baseUrl,
            client = okHttpClient,
            connectTimeoutMs = timeouts.connectMs,
            readTimeoutMs = timeouts.readMs,
        ),
    )

}
