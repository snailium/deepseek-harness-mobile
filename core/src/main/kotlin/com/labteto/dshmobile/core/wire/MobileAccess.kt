package com.labteto.dshmobile.core.wire

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The `dsh-mobile` gateway's own vocabulary: routes, cookies and the pairing input.
 *
 * `dsh-mobile` is a community plugin that puts an authenticated gateway in front of the harness so
 * a phone can reach it over the LAN or a tunnel. It is not a `dsh-relay`, and the difference is not
 * cosmetic: it terminates the client's session itself, in a cookie, and mints a device token of its
 * own — whereas a relay holds one long-lived bearer token this client sends on every request.
 *
 * What this object holds is the part that can be decided without a socket: path names, header and
 * cookie names, the pairing input grammar and the discovery handshake. Everything that has to talk
 * to a gateway lives in [MobileAccessPairing].
 *
 * All of it was read off `dsh-mobile` 0.5.3 and then exercised against the real gateway; the
 * comments name the behaviour that was verified rather than the shape of the source.
 */
object MobileAccess {

    /** The gateway's own namespace on both its listener and the harness web server. */
    const val AUTH_PREFIX: String = "/mobile-access"

    /** Unauthenticated identity probe: names the plugin and the discovery protocol. */
    const val METADATA_PATH: String = "$AUTH_PREFIX/metadata"

    /** Unauthenticated liveness probe. */
    const val HEALTH_PATH: String = "$AUTH_PREFIX/health"

    /** Unauthenticated device card: `{deviceName, origin, port, protocol, instanceId}`. */
    const val DISCOVERY_PATH: String = "$AUTH_PREFIX/discovery"

    /** The private CA as base64 DER, served only when the gateway terminates its own TLS. */
    const val CA_PATH: String = "$AUTH_PREFIX/ca.cer"

    /** Claim a pairing code for a native (non-browser) client. */
    const val NATIVE_PAIR_PATH: String = "$AUTH_PREFIX/auth/native-pair"

    /** Trade a stored device token for a fresh session, without the user's involvement. */
    const val NATIVE_RENEW_PATH: String = "$AUTH_PREFIX/auth/native-renew"

    /** Ask whether a stored device token is still accepted. */
    const val NATIVE_PROBE_PATH: String = "$AUTH_PREFIX/auth/native-probe"

    /** The page a pairing link points at; the credential itself rides its fragment. */
    const val PAIR_PAGE_PATH: String = "$AUTH_PREFIX/pair"

    /**
     * The session cookie every later request carries.
     *
     * `dsh_ma_session` is a *cookie*, not a bearer header, and that is the single biggest difference
     * from `dsh-relay`: the gateway answers 401 to any `/api` call that arrives without it, on the
     * unary transport and on the mux upgrade alike.
     */
    const val SESSION_COOKIE: String = "dsh_ma_session"

    /**
     * The device cookie a *browser* is given at pairing.
     *
     * Never sent by this client: it is the browser's renewal credential, scoped to the renew route
     * alone, and a native client holds the same secret explicitly instead (see [NativePairResponse]).
     */
    const val DEVICE_COOKIE: String = "dsh_ma_device"

    /** The CSRF token, also issued readable as a cookie for the browser page. */
    const val CSRF_HEADER: String = "x-dsh-mobile-csrf"

    /**
     * What the gateway's trust fence demands of every mutating request.
     *
     * Not decoration. `assertExternalTrust(requireOrigin = isMutation)` refuses a POST or PUT with
     * no `Origin` header outright — a **403** before any route is reached — so a native client has
     * to send the very origin it is addressing. Verified: the same POST answers 200 with the header
     * and 403 without it, while a *missing CSRF token* is the other 403, on a request that did
     * carry the origin.
     */
    const val ORIGIN_HEADER: String = "Origin"

    /**
     * The ASCII datagram a phone broadcasts to find computers on the LAN.
     *
     * Discovery is a UDP dialogue, not mDNS alone: the phone sends this string to the *HTTPS port*
     * of every computer's broadcast address, and each gateway answers with its [DiscoveryCard]
     * JSON. The port is the listener's, so a gateway moved to 8443 answers there.
     */
    const val DISCOVERY_QUERY: String = "DSH_MOBILE_DISCOVER_V1"

    /** The only discovery protocol this build understands. */
    const val DISCOVERY_PROTOCOL: Int = 1

    /** The LAN listener's default port. A setup may move it, and the card always says where it is. */
    const val DEFAULT_PORT: Int = 3443

    /** App-key prefixes: `dsh1` when nothing extra is pinned, `dsh2` when a pairing CA is. */
    const val APP_KEY_PLAIN: String = "dsh1"
    const val APP_KEY_PINNED: String = "dsh2"

    /** Length of the hex fingerprint that names a gateway and, on the LAN, its CA. */
    private const val INSTANCE_ID_LENGTH = 64

    /**
     * Read a scanned QR, a pasted key or a copied pairing link.
     *
     * Three spellings reach this function because all three are offered by the desktop panel, and
     * the app cannot control which one a person copies:
     *
     * - the app key, `dsh1.<instanceId>.<token>`
     * - a pairing link, `<origin>/mobile-access/pair#instance=<id>&token=<token>`
     * - the same link with `#key=dsh2.<id>.<token>`, used when a self-signed ingress pinned a CA
     *
     * The link carries the origin, which is what a *remote* pairing needs — there is no discovery
     * broadcast on the far side of a tunnel. A bare key has none, so it is only usable for an
     * address the caller already knows (a discovered LAN computer, or the one being typed in).
     */
    fun parsePairingInput(text: String): MobileAccessInviteResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return MobileAccessInviteResult.NotAPairingCode

        // A link first: it is the longer form, and an app key never contains a slash.
        if (trimmed.contains("/")) {
            val link = parsePairingLink(trimmed) ?: return MobileAccessInviteResult.NotAPairingCode
            return MobileAccessInviteResult.Valid(link)
        }
        val key = parseAppKey(trimmed) ?: return MobileAccessInviteResult.NotAPairingCode
        return MobileAccessInviteResult.Valid(key)
    }

    /** The app key form: `dsh<1|2>.<64 hex>.<token>`. */
    private fun parseAppKey(text: String): MobileAccessInvite? {
        val parts = text.split('.')
        if (parts.size != 3) return null
        val (prefix, instanceId, token) = parts
        if (prefix != APP_KEY_PLAIN && prefix != APP_KEY_PINNED) return null
        if (!isInstanceId(instanceId) || token.isEmpty()) return null
        return MobileAccessInvite(
            instanceId = instanceId,
            token = token,
            origin = null,
            pinsPairingCa = prefix == APP_KEY_PINNED,
        )
    }

    /**
     * The link form, read by hand rather than with a URL parser.
     *
     * The credential rides the **fragment**, and `java.net.URI` parses a fragment happily — but the
     * origin here is whatever the gateway advertises (a LAN address, a tunnel hostname, a public
     * IPv4), and a parser that rejects one of those would reject a working invitation. Splitting on
     * the known markers accepts exactly the two shapes the plugin emits and nothing else.
     */
    private fun parsePairingLink(text: String): MobileAccessInvite? {
        val marker = text.indexOf("#")
        if (marker < 0) return null
        val origin = originOfLink(text.substring(0, marker)) ?: return null
        val fragment = text.substring(marker + 1)
        val params = fragment.split('&').mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) null else part.substring(0, eq) to part.substring(eq + 1)
        }.toMap()

        params["key"]?.let { key ->
            return parseAppKey(key)?.copy(origin = origin)
        }
        val instanceId = params["instance"] ?: return null
        val token = params["token"] ?: return null
        if (!isInstanceId(instanceId) || token.isEmpty()) return null
        // A link never says `dsh2`: the pinned-CA case is exactly the one that uses `#key=`.
        return MobileAccessInvite(instanceId, token, origin, pinsPairingCa = false)
    }

    /** `scheme://authority` of a pairing link, or null when it is not one. */
    private fun originOfLink(prefix: String): String? {
        val schemeEnd = prefix.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = prefix.substring(0, schemeEnd).lowercase()
        if (scheme != "http" && scheme != "https") return null
        val authority = prefix.substring(schemeEnd + 3).substringBefore('/')
        if (authority.isEmpty() || authority.contains('@')) return null
        return "$scheme://$authority"
    }

    /** Whether [value] is the 64-character lowercase hex id a gateway names itself by. */
    fun isInstanceId(value: String): Boolean =
        value.length == INSTANCE_ID_LENGTH && value.all { it in "0123456789abcdef" }

    /** The `Origin` header value for a base URL: scheme and authority, no path. */
    fun originOf(baseUrl: String): String? {
        val trimmed = baseUrl.trim().trimEnd('/')
        val schemeEnd = trimmed.indexOf("://")
        if (schemeEnd <= 0) return null
        return trimmed.substring(0, schemeEnd).lowercase() + "://" +
            trimmed.substring(schemeEnd + 3).substringBefore('/')
    }

    /** The `Cookie` header value carrying [sessionToken]. */
    fun sessionCookie(sessionToken: String): String = "$SESSION_COOKIE=$sessionToken"
}

/**
 * A pairing invitation, whoever spelled it.
 *
 * [origin] is null for a bare app key: such a key says *what* to pair with and not *where*, so the
 * caller supplies the address (a discovered computer, or one the user typed).
 */
data class MobileAccessInvite(
    /** The gateway's stable id, and on the LAN the fingerprint of the CA that signs its listener. */
    val instanceId: String,
    /** The single-use pairing token. */
    val token: String,
    /** `scheme://authority` from a link, or null when the input was a bare key. */
    val origin: String?,
    /** True for a `dsh2.` key: the gateway serves a CA this device must pin before trusting it. */
    val pinsPairingCa: Boolean,
)

/** How a scanned or pasted pairing input was read. */
sealed interface MobileAccessInviteResult {
    /** An invitation this build understands. */
    data class Valid(val invite: MobileAccessInvite) : MobileAccessInviteResult

    /** Not a `dsh-mobile` pairing input at all — some other QR, or text that is not one. */
    data object NotAPairingCode : MobileAccessInviteResult
}

/** `GET /mobile-access/metadata` — what an address says it is before anything is sent to it. */
@Serializable
data class MobileAccessMetadata(
    @SerialName("version") val version: Int = 0,
    @SerialName("pluginVersion") val pluginVersion: String? = null,
    @SerialName("minimumAndroidAppVersion") val minimumAndroidAppVersion: String? = null,
    @SerialName("discoveryProtocol") val discoveryProtocol: Int? = null,
)

/** The LAN discovery card: what a broadcast reply or `GET /mobile-access/discovery` carries. */
@Serializable
data class MobileAccessCard(
    @SerialName("deviceName") val deviceName: String? = null,
    /** The origin to address this computer by, scheme included. */
    @SerialName("origin") val origin: String? = null,
    @SerialName("port") val port: Int? = null,
    @SerialName("protocol") val protocol: Int? = null,
    @SerialName("instanceId") val instanceId: String? = null,
)

/** The body of a native pairing claim. [label] is what the operator sees in the device list. */
@Serializable
data class NativePairRequest(
    val token: String,
    val label: String,
)

/** A successful native claim: the device credential, plus a session to use immediately. */
@Serializable
data class NativePairResponse(
    @SerialName("instanceId") val instanceId: String? = null,
    @SerialName("deviceId") val deviceId: String = "",
    /** Long-lived; the whole credential. Stored encrypted, never logged. */
    @SerialName("deviceToken") val deviceToken: String = "",
    @SerialName("deviceExpiresAt") val deviceExpiresAt: Long = 0L,
    /** Short-lived, and re-mintable from [deviceToken] — so it is never persisted. */
    @SerialName("sessionToken") val sessionToken: String = "",
    @SerialName("csrfToken") val csrfToken: String = "",
    @SerialName("sessionExpiresAt") val sessionExpiresAt: Long = 0L,
)

/** The body of a renewal. */
@Serializable
data class NativeRenewRequest(
    @SerialName("deviceToken") val deviceToken: String,
)

/** The body of a liveness probe. */
@Serializable
data class NativeProbeRequest(
    @SerialName("deviceToken") val deviceToken: String,
)

/** A renewal's answer: a fresh session, same device. */
@Serializable
data class NativeRenewResponse(
    @SerialName("instanceId") val instanceId: String? = null,
    @SerialName("deviceId") val deviceId: String = "",
    @SerialName("sessionToken") val sessionToken: String = "",
    @SerialName("csrfToken") val csrfToken: String = "",
    @SerialName("sessionExpiresAt") val sessionExpiresAt: Long = 0L,
)

/** A probe's answer: the device is still known, and until when. */
@Serializable
data class NativeProbeResponse(
    @SerialName("instanceId") val instanceId: String? = null,
    @SerialName("deviceId") val deviceId: String = "",
    @SerialName("deviceExpiresAt") val deviceExpiresAt: Long = 0L,
)
