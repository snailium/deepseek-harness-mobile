package com.labteto.dshmobile.ui.screens.connect

/**
 * One endpoint the user typed, with the defaults already applied.
 *
 * [portExplicit] records whether the user actually wrote a port: it is what lets the form tell
 * "user said :443" from "we guessed 443" when it pre-fills the Advanced section.
 */
data class ParsedAddress(
    val host: String,
    val port: Int,
    val scheme: String,
    val portExplicit: Boolean,
) {
    val authority: String get() = "$host:$port"
}

/**
 * Turn whatever the user typed into an endpoint, applying the defaults that make the common cases
 * just work:
 *  - `https://ds.yeasin.tech` — scheme wins, port defaults to 443
 *  - `ds.yeasin.tech:8443` — a domain with an explicit port
 *  - `192.168.1.20` — a bare LAN address means the harness's own convention, http on 3080
 *  - `localhost` — same as a bare address
 *
 * The scheme-vs-IP rule is the whole point: a domain almost always fronts a TLS edge (a tunnel, a
 * reverse proxy), and a bare IP almost always *is* the harness, which serves plain http. Guessing
 * wrong is recoverable — the Advanced section lets either be overridden, and the live endpoint
 * caption shows the guess before Connect is ever tapped.
 *
 * Returns null for input nothing sane can be made of (garbage, a bracket-IPv6 literal, a port out
 * of range). The caller shows the InvalidInput diagnosis.
 */
fun parseAddress(input: String): ParsedAddress? {
    val raw = input.trim()
    if (raw.isEmpty()) return null

    // A pasted `scheme://` prefix wins over everything, exactly as the old host field behaved.
    val prefix = raw.substringBefore("://", "").lowercase()
    val scheme = when (prefix) {
        "http", "https" -> prefix
        "" -> null
        else -> return null
    }
    val rest = if (scheme != null) raw.substringAfter("://") else raw
    if (rest.isEmpty()) return null

    // A trailing `:port` is explicit. Split on the *last* colon so a hostname with colons (IPv6)
    // is rejected outright rather than mis-parsed.
    val lastColon = rest.lastIndexOf(':')
    val (hostPart, portText) = if (lastColon > 0) {
        rest.substring(0, lastColon) to rest.substring(lastColon + 1)
    } else {
        rest to ""
    }
    // A host may contain at most one colon — the trailing port separator. Anything else (a
    // bracket-IPv6 literal, a doubled colon, whitespace) is rejected outright rather than
    // mis-parsed into a host that can never resolve.
    if (hostPart.contains(':') || hostPart.contains('[') || hostPart.contains(']') ||
        hostPart.contains(' ')
    ) {
        return null
    }
    if (hostPart.isEmpty()) return null

    val portExplicit = portText.isNotEmpty()
    val port = when {
        portExplicit -> portText.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        scheme == "https" -> DEFAULT_HTTPS_PORT
        scheme == "http" -> DEFAULT_HTTP_PORT
        isIpLiteral(hostPart) || hostPart == "localhost" -> DEFAULT_HARNESS_PORT
        else -> DEFAULT_HTTPS_PORT
    }
    val effectiveScheme = scheme ?: DEFAULT_SCHEME_FOR(hostPart)

    return ParsedAddress(host = hostPart, port = port, scheme = effectiveScheme, portExplicit = portExplicit)
}

private fun isIpLiteral(host: String): Boolean {
    val parts = host.split('.')
    if (parts.size != 4) return false
    return parts.all { part -> part.isNotEmpty() && part.all(Char::isDigit) && part.toIntOrNull() in 0..255 }
}

private fun DEFAULT_SCHEME_FOR(host: String): String =
    if (isIpLiteral(host) || host == "localhost") "http" else "https"

const val DEFAULT_HARNESS_PORT = 3080
const val DEFAULT_HTTP_PORT = 80
const val DEFAULT_HTTPS_PORT = 443
