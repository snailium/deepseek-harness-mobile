package com.labteto.dshmobile.connection

import com.labteto.dshmobile.core.wire.MobileAccessPairing
import com.labteto.dshmobile.core.wire.MobileAccessTls
import com.labteto.dshmobile.core.wire.NativeRenewOutcome
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * The short-lived half of a gateway credential, and the only thing that has to be re-minted.
 *
 * A gateway wants two credentials: a **device token** that never expires and proves which device
 * this is, and a **session** (a cookie plus a CSRF token) that expires in minutes. Only the device
 * token is persisted — it is the whole credential — and the session is renewed from it. That split
 * is the gateway's, not ours, and it is why a restart does not require pairing again.
 *
 * Sessions live in memory on purpose. Persisting one would mean a stale cookie on disk that has to
 * be validated against an expiry clock this process does not control, and the renewal it would save
 * is one cheap request.
 */
@Singleton
class GatewaySessions @Inject constructor(
    private val credentials: DeviceCredentialStore,
    private val identities: GatewayIdentityStore,
    private val okHttpClient: OkHttpClient,
) {
    /** One gateway session. [expiresAt] is epoch millis; 0 means "the gateway did not say". */
    data class Session(val token: String, val csrf: String, val expiresAt: Long)

    private val sessions = ConcurrentHashMap<String, Session>()

    /**
     * The session for [hostId], when it is still worth using.
     *
     * Anything inside the last 30 seconds counts as expired: a request that starts now may still be
     * in flight when the gateway stops honouring it, and renewing first is cheaper than a retry.
     */
    fun cached(hostId: String): Session? = sessions[hostId]?.let { session ->
        if (session.expiresAt == 0L || session.expiresAt - SKEW_MS > System.currentTimeMillis()) session else null
    }

    /** Remember what a claim just handed us. */
    fun adopt(hostId: String, token: String, csrf: String, expiresAt: Long) {
        if (token.isBlank()) return
        sessions[hostId] = Session(token, csrf, expiresAt)
    }

    /** Drop the session for [hostId] — the device token, if any, is still worth keeping. */
    fun forget(hostId: String) {
        sessions.remove(hostId)
    }

    /**
     * Make sure [hostId] has a usable session, renewing from the stored device token if needed.
     *
     * Returns false when there is nothing to renew with, or when the gateway refused the device
     * token — which is terminal and means this endpoint has to be paired again. The caller connects
     * either way and lets the handshake report the refusal, because a connect screen that refuses
     * to try is harder to diagnose than one that says what the gateway said.
     */
    suspend fun ensure(hostId: String, baseUrl: String): Boolean {
        if (cached(hostId) != null) return true
        val deviceToken = credentials.token(hostId) ?: return false
        val identity = identities.get(hostId)
        val client = pinnedClient(identity?.caDerB64)
        return when (val outcome = MobileAccessPairing.nativeRenew(baseUrl, deviceToken, client)) {
            is NativeRenewOutcome.Renewed -> {
                adopt(hostId, outcome.response.sessionToken, outcome.response.csrfToken, outcome.response.sessionExpiresAt)
                true
            }
            // Revoked is the one answer that cannot be recovered: the device is gone from the
            // gateway's list, so keeping its token would only make every later connect look like a
            // network fault.
            NativeRenewOutcome.Revoked -> {
                credentials.remove(hostId)
                false
            }
            else -> false
        }
    }

    /** A client that trusts the gateway's own CA when it serves one, and the platform store otherwise. */
    private fun pinnedClient(caDerB64: String?): OkHttpClient {
        val der = caDerB64?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
            ?: return okHttpClient
        return runCatching { MobileAccessTls.caPinnedClient(okHttpClient, der) }.getOrDefault(okHttpClient)
    }

    private companion object {
        const val SKEW_MS: Long = 30_000
    }
}
