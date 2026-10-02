package com.labteto.dshmobile.core.wire

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Device enrolment and credential upkeep against a `dsh-mobile` gateway.
 *
 * These are the exchanges that are *not* the harness wire protocol: the gateway's own routes are
 * never forwarded upstream, so they carry no `client-request` envelope and answer with plain JSON.
 * Everything else this client sends goes to the same origin as harness RPC, carrying the cookie
 * obtained here.
 *
 * The gateway's trust fence runs before every route: the `Host` must be one it answers to, the peer
 * must be inside its allowed CIDRs, and — for any method but GET — an `Origin` header must be
 * present *and* accepted. A request that forgets the last one is refused with a bare 403 that reads
 * exactly like a wrong pairing code, which is why [post] always derives the header from the address
 * it is calling.
 *
 * The budgets are short on purpose: pairing is watched by a person, and renewal happens inside a
 * reconnect, so neither may sit on the 30s an ordinary `/api` call is allowed.
 */
object MobileAccessPairing {

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private const val CONNECT_MS = 8_000L
    private const val READ_MS = 12_000L

    /** Fallback back-off for a 429 that carries no `Retry-After`. */
    const val DEFAULT_RETRY_AFTER_SECONDS: Long = 30L

    /**
     * What an address is, if it is a gateway at all.
     *
     * Unauthenticated, and deliberately the first thing asked of an address: the metadata route
     * names the plugin and the discovery protocol, so a bare harness, a `dsh-relay` or somebody's
     * web server is told apart from a gateway before any credential is anywhere near the wire.
     */
    suspend fun metadata(baseUrl: String, client: OkHttpClient): MobileAccessMetadata? {
        val answered = get(baseUrl, MobileAccess.METADATA_PATH, client) ?: return null
        if (answered.status != 200) return null
        return decodeOrNull(MobileAccessMetadata.serializer(), answered.body)
    }

    /** The device card, for an address that is already reachable. */
    suspend fun card(baseUrl: String, client: OkHttpClient): MobileAccessCard? {
        val answered = get(baseUrl, MobileAccess.DISCOVERY_PATH, client) ?: return null
        if (answered.status != 200) return null
        return decodeOrNull(MobileAccessCard.serializer(), answered.body)
    }

    /**
     * The gateway's private CA, as DER, when it terminates its own TLS.
     *
     * Null means "nothing to pin here", which is an answer rather than a failure: behind a public
     * certificate (a tunnel, a reverse proxy) there is no private CA, `/mobile-access/ca.cer`
     * answers 404, and the platform trust store is the whole of the decision.
     *
     * The fetch is unauthenticated, which is why the *caller* must compare the result against the
     * fingerprint from the pairing input — see [certificateFingerprint]. Checking it here would
     * invite a caller that forgot to.
     */
    suspend fun caCertificate(baseUrl: String, client: OkHttpClient): ByteArray? {
        val answered = get(baseUrl, MobileAccess.CA_PATH, client) ?: return null
        if (answered.status != 200) return null
        return runCatching { java.util.Base64.getMimeDecoder().decode(answered.body.trim()) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    /**
     * The lower-case hex SHA-256 of a certificate's DER form.
     *
     * This is the gateway's own identity spelling, not the relay's. `dsh-mobile` names a LAN
     * installation by `X509Certificate.fingerprint256` with the colons removed — a hash of the
     * *certificate*, where a relay publishes a hash of the *public key* (see [RelayTls.spkiPin]).
     * The two are not interchangeable, and a pin computed the wrong way round fails as a torn
     * connection rather than a clean refusal.
     */
    fun certificateFingerprint(der: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(der)
            .joinToString("") { "%02x".format(it) }

    /**
     * Claim [token] for this device.
     *
     * 201 answers the credential. 401 is the gateway declining the token — wrong, expired, or
     * already claimed, which it does not distinguish — and 403 is its fence, meaning the request
     * arrived under a `Host`/`Origin` it does not answer to.
     */
    suspend fun nativePair(
        baseUrl: String,
        token: String,
        label: String,
        client: OkHttpClient,
    ): NativePairOutcome = when (
        val outcome = post(
            baseUrl,
            MobileAccess.NATIVE_PAIR_PATH,
            WireJson.encodeToString(NativePairRequest.serializer(), NativePairRequest(token, label)),
            client,
        )
    ) {
        is GatewayExchange.Answered -> when (outcome.status) {
            201 -> decodeOrNull(NativePairResponse.serializer(), outcome.body)
                ?.let { NativePairOutcome.Paired(it) } ?: NativePairOutcome.Unreachable(noPairingRoute())
            401 -> NativePairOutcome.Rejected
            403 -> NativePairOutcome.HostRefused
            429 -> NativePairOutcome.RateLimited(retryAfter(outcome))
            else -> NativePairOutcome.Unreachable(noPairingRoute())
        }
        is GatewayExchange.Failed -> NativePairOutcome.Unreachable(outcome.failure())
    }

    /**
     * Trade a stored device token for a fresh session.
     *
     * This is what keeps a paired device paired: the session cookie is short-lived by design and the
     * device token is the durable half. A 401 here is terminal — revoked by its operator, or expired
     * — and no retry can change that; only pairing again can.
     */
    suspend fun nativeRenew(
        baseUrl: String,
        deviceToken: String,
        client: OkHttpClient,
    ): NativeRenewOutcome = when (
        val outcome = post(
            baseUrl,
            MobileAccess.NATIVE_RENEW_PATH,
            WireJson.encodeToString(
                NativeRenewRequest.serializer(),
                NativeRenewRequest(deviceToken),
            ),
            client,
        )
    ) {
        is GatewayExchange.Answered -> when (outcome.status) {
            200 -> decodeOrNull(NativeRenewResponse.serializer(), outcome.body)
                ?.let { NativeRenewOutcome.Renewed(it) } ?: NativeRenewOutcome.Unreachable(noRenewRoute())
            401 -> NativeRenewOutcome.Revoked
            403 -> NativeRenewOutcome.HostRefused
            429 -> NativeRenewOutcome.RateLimited(retryAfter(outcome))
            else -> NativeRenewOutcome.Unreachable(noRenewRoute())
        }
        is GatewayExchange.Failed -> NativeRenewOutcome.Unreachable(outcome.failure())
    }

    /**
     * Whether a stored device token is still accepted.
     *
     * Used to tell "this device was revoked" apart from "the computer is off" before showing a
     * pairing prompt: both leave an unreachable gateway, and only one of them needs a new code.
     */
    suspend fun nativeProbe(
        baseUrl: String,
        deviceToken: String,
        client: OkHttpClient,
    ): NativeProbeOutcome = when (
        val outcome = post(
            baseUrl,
            MobileAccess.NATIVE_PROBE_PATH,
            WireJson.encodeToString(
                NativeProbeRequest.serializer(),
                NativeProbeRequest(deviceToken),
            ),
            client,
        )
    ) {
        is GatewayExchange.Answered -> when (outcome.status) {
            200 -> decodeOrNull(NativeProbeResponse.serializer(), outcome.body)
                ?.let { NativeProbeOutcome.Known(it) } ?: NativeProbeOutcome.Unreachable(noProbeRoute())
            401 -> NativeProbeOutcome.Unknown
            403 -> NativeProbeOutcome.HostRefused
            429 -> NativeProbeOutcome.RateLimited(retryAfter(outcome))
            else -> NativeProbeOutcome.Unreachable(noProbeRoute())
        }
        is GatewayExchange.Failed -> NativeProbeOutcome.Unreachable(outcome.failure())
    }

    // ------------------------------------------------------------------ plumbing

    /** One gateway route answer, or the reason none arrived. */
    private sealed interface GatewayExchange {
        data class Answered(
            val status: Int,
            val body: String,
            val retryAfter: String?,
        ) : GatewayExchange

        data class Failed(val cause: IOException) : GatewayExchange

        /** Classify a carrier failure with the same vocabulary every other call uses. */
        fun failure(): GatewayUnreachable = when (this) {
            is Failed -> GatewayUnreachable(
                TransportFailures.classify(cause),
                cause.message,
            )
            is Answered -> GatewayUnreachable(TransportFailure.OTHER, "no answer")
        }
    }

    /**
     * GET one gateway route.
     *
     * No `Origin`: the fence demands it only of a mutating method, and a GET that carried one would
     * be claiming a browser context this client is not in.
     */
    private suspend fun get(baseUrl: String, path: String, client: OkHttpClient): GatewayExchange.Answered? {
        val target = route(baseUrl, path) ?: return null
        val request = Request.Builder().url(target).header("Accept", "application/json").get().build()
        return execute(request, client) as? GatewayExchange.Answered
    }

    /**
     * POST one gateway route, with the trust fence's own precondition applied.
     *
     * `Origin` is derived from [baseUrl] and never accepted as a parameter: the value the fence
     * accepts *is* the origin being addressed, so deriving it is the only way the two cannot
     * disagree.
     */
    private suspend fun post(
        baseUrl: String,
        path: String,
        body: String,
        client: OkHttpClient,
    ): GatewayExchange {
        val target = route(baseUrl, path) ?: return GatewayExchange.Failed(IOException("not a usable gateway address"))
        val builder = Request.Builder()
            .url(target)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .post(body.toRequestBody(JSON))
        MobileAccess.originOf(baseUrl)?.let { builder.header(MobileAccess.ORIGIN_HEADER, it) }
        return execute(builder.build(), client)
    }

    /**
     * The verdict for an address that answered, but not with this route's shape.
     *
     * [TransportFailure.NOT_FOUND] rather than a bespoke code: a 404 from a gateway is the same fact
     * as a 404 from a harness — nothing serves that path here — and the app already has wording for
     * it. The three helpers differ only in which route was asked for, which the message carries.
     */
    private fun noRoute(message: String) = GatewayUnreachable(TransportFailure.NOT_FOUND, message)

    private fun noPairingRoute() = noRoute("no pairing route at this address")

    private fun noRenewRoute() = noRoute("no renewal route at this address")

    private fun noProbeRoute() = noRoute("no probe route at this address")

    /** Seconds a 429 asked for. */
    private fun retryAfter(answered: GatewayExchange.Answered): Long =
        answered.retryAfter?.toLongOrNull()?.coerceAtLeast(1) ?: DEFAULT_RETRY_AFTER_SECONDS

    private fun <T> decodeOrNull(
        serializer: kotlinx.serialization.DeserializationStrategy<T>,
        body: String,
    ): T? = runCatching { WireJson.decodeFromString(serializer, body) }.getOrNull()

    /** Resolve [path] against a gateway origin, or null when the origin is not a usable URL. */
    private fun route(baseUrl: String, path: String): HttpUrl? =
        baseUrl.trim().trimEnd('/').toHttpUrlOrNull()?.newBuilder()?.encodedPath(path)?.build()

    private suspend fun execute(request: Request, client: OkHttpClient): GatewayExchange =
        suspendCancellableCoroutine { continuation ->
            val call = client.newBuilder()
                .connectTimeout(CONNECT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(READ_MS, TimeUnit.MILLISECONDS)
                .build()
                .newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resume(GatewayExchange.Failed(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        val answered = GatewayExchange.Answered(
                            status = resp.code,
                            body = resp.body?.string().orEmpty(),
                            retryAfter = resp.header("Retry-After"),
                        )
                        if (continuation.isActive) continuation.resume(answered)
                    }
                }
            })
        }
}

/** Why a gateway could not be reached at all, in the vocabulary every other call reports. */
data class GatewayUnreachable(
    val kind: TransportFailure,
    val message: String?,
)

/** How a native claim ended. */
sealed interface NativePairOutcome {
    /** Enrolled; [response] carries the device credential. */
    data class Paired(val response: NativePairResponse) : NativePairOutcome

    /**
     * The gateway refused the code.
     *
     * One answer for wrong, expired and already-claimed: the gateway does not separate them, and
     * inventing a distinction here would be a guess. The remedy is the same for all three — ask the
     * computer for a fresh code.
     */
    data object Rejected : NativePairOutcome

    /**
     * The fence refused the address this request was made under.
     *
     * The gateway is there and reachable; it answers only to `Host`/`Origin` values it knows. On a
     * LAN that is an address it was not set up for; through a tunnel it means the request arrived
     * under the wrong hostname. Not a wrong code — the code was never read.
     */
    data object HostRefused : NativePairOutcome

    /** Too many attempts from this address. */
    data class RateLimited(val retryAfterSeconds: Long) : NativePairOutcome

    /** Nothing answered, or nothing there serves the pairing route. */
    data class Unreachable(val failure: GatewayUnreachable) : NativePairOutcome
}

/** How a renewal ended. */
sealed interface NativeRenewOutcome {
    /** A fresh session; [response] carries it. */
    data class Renewed(val response: NativeRenewResponse) : NativeRenewOutcome

    /**
     * The device is no longer accepted: revoked by its operator, or its own token expired.
     *
     * Terminal for this device credential — renewing again cannot succeed — so the caller drops the
     * stored token and routes the user to pairing instead of retrying on every reconnect.
     */
    data object Revoked : NativeRenewOutcome

    /** The fence refused this address — see [NativePairOutcome.HostRefused]. */
    data object HostRefused : NativeRenewOutcome

    /** Too many attempts from this address. */
    data class RateLimited(val retryAfterSeconds: Long) : NativeRenewOutcome

    /** Nothing answered, or nothing there serves the renewal route. */
    data class Unreachable(val failure: GatewayUnreachable) : NativeRenewOutcome
}

/** What a liveness probe learned. */
sealed interface NativeProbeOutcome {
    /** Still paired; [response] says until when. */
    data class Known(val response: NativeProbeResponse) : NativeProbeOutcome

    /** The gateway no longer accepts this device's token. */
    data object Unknown : NativeProbeOutcome

    /** The fence refused this address. */
    data object HostRefused : NativeProbeOutcome

    /** Too many attempts from this address. */
    data class RateLimited(val retryAfterSeconds: Long) : NativeProbeOutcome

    /** Nothing answered, or nothing there serves the probe route. */
    data class Unreachable(val failure: GatewayUnreachable) : NativeProbeOutcome
}
