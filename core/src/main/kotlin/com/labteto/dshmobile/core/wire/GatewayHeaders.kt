package com.labteto.dshmobile.core.wire

/**
 * The three headers a `dsh-mobile` gateway wants on a request, as a plain map.
 *
 * Deliberately not transport parameters. The transport already carries an arbitrary header map
 * (`extraHeaders`) for an authenticating edge in front of a harness — a Cloudflare Access service
 * token travels the same way — and a gateway's session cookie, `Origin` and CSRF token are just
 * three more headers. Threading them through as named constructor arguments instead meant editing
 * `RpcTransport`, which is upstream's file and the single most merge-hostile thing in the tree:
 * every upstream release reshapes the credential model there.
 *
 * So the gateway's needs are expressed here, outside upstream's files, and handed to the existing
 * seam. Everything this file names is ours; a future upstream merge cannot collide with it.
 */
object GatewayHeaders {

    /** The request header a gateway reads the session cookie from. */
    const val COOKIE: String = "Cookie"

    /** The header the gateway's trust fence checks on every mutating request. */
    const val ORIGIN: String = "Origin"

    /** The anti-forgery header the gateway demands beside [ORIGIN] on a mutation. */
    const val CSRF: String = "x-dsh-mobile-csrf"

    /**
     * The headers for an authenticated call.
     *
     * [origin] is derived from the endpoint the caller is addressing, never typed by a person: the
     * fence compares it against the address it was reached by, so a value that disagrees with the
     * URL can only fail. [csrf] is the short-lived token the gateway handed out with the session.
     */
    fun forSession(sessionToken: String, origin: String, csrf: String): Map<String, String> = mapOf(
        COOKIE to MobileAccess.sessionCookie(sessionToken),
        ORIGIN to origin,
        CSRF to csrf,
    )

    /**
     * The headers a *claim* carries, which is none of the above.
     *
     * A pairing claim has no session yet — that is what it is asking for — so it sends the
     * single-use code in a JSON body and nothing else. Named rather than left as an empty map at the
     * call site so "pairing sends no session headers" reads as a decision instead of an omission.
     */
    fun forClaim(): Map<String, String> = emptyMap()
}
