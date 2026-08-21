package com.labteto.dshmobile.connection

import com.labteto.dshmobile.core.wire.dto.HostDescription
import kotlinx.serialization.Serializable

/**
 * One remembered harness endpoint.
 *
 * The `last*` fields cache the newest `host.describe` so a Recent card can say what the harness is
 * before its liveness probe lands — and can still say it about a harness that is now switched off.
 * They all default, because the whole list is persisted as one JSON blob whose decode failure is
 * swallowed: a field without a default would silently wipe every remembered host on upgrade.
 */
@Serializable
data class HostConfig(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val isLoopback: Boolean = false,
    val lastConnectedAt: Long = 0L,
    val lastVersion: String? = null,
    val lastCwd: String? = null,
    val lastSessions: Int? = null,
    /**
     * URL scheme, `http` or `https`. Defaults keep hosts saved by older builds decoding as the
     * plain-HTTP endpoints they were. Normalize through [normalizeScheme] before storing.
     */
    val scheme: String = "http",
    /**
     * Optional `Authorization: Bearer` value, for a bearer-gated proxy in front of the harness.
     * Stored app-private like the address itself; only ever sent when set.
     */
    val authToken: String? = null,
    /** Cloudflare Access service-token Client ID, sent as `CF-Access-Client-Id`. */
    val cfClientId: String? = null,
    /** Cloudflare Access service-token Client Secret, sent as `CF-Access-Client-Secret`. */
    val cfClientSecret: String? = null,
    /** True once the user confirmed connecting to this remote (off-LAN) endpoint. */
    val remoteConfirmed: Boolean = false,
) {
    val authority: String get() = "$host:$port"
    val baseUrl: String get() = "$scheme://$authority"

    /**
     * The extra headers an edge proxy in front of the harness needs, or empty for a plain LAN
     * endpoint. This is the single place auth values become wire headers — the transport applies
     * the map verbatim to every POST, download and WebSocket upgrade.
     */
    val authHeaders: Map<String, String> get() = authHeaders(authToken, cfClientId, cfClientSecret)

    /** A short label for screens: `https://host:port` — the scheme only when it is https. */
    val displayAddress: String get() = if (scheme == "https") "$scheme://$authority" else authority

    companion object {
        /** Coerce any input to the two schemes the wire understands. */
        fun normalizeScheme(value: String?): String =
            if (value.equals("https", ignoreCase = true)) "https" else "http"

        /** Compose the wire headers for the three optional auth values; empty when none is set. */
        fun authHeaders(token: String?, cfClientId: String?, cfClientSecret: String?): Map<String, String> =
            buildMap {
                token?.takeIf { it.isNotBlank() }?.let { put("Authorization", "Bearer $it") }
                cfClientId?.takeIf { it.isNotBlank() }?.let { put("CF-Access-Client-Id", it) }
                cfClientSecret?.takeIf { it.isNotBlank() }?.let { put("CF-Access-Client-Secret", it) }
            }
    }
}

/**
 * A harness found by the active LAN scan.
 *
 * Carries the whole probe answer rather than two fields of it: the sweep already paid for the round
 * trip, and the card wants the session count and the default model too.
 *
 * [description] is null when the harness was identified by its static manifest but its trust fence
 * refused `host.describe` from this address. That is a real find, not a miss — it is a harness with
 * a `--trusted-host` still to add — so it is listed and explained rather than dropped.
 */
data class DiscoveredHost(
    val host: String,
    val port: Int,
    val description: HostDescription?,
) {
    val authority: String get() = "$host:$port"

    /** Whether the harness accepted an `/api` call from this device. */
    val trusted: Boolean get() = description != null
}

/** App-level persisted settings (DataStore). */
data class AppSettings(
    val autoConnectLast: Boolean = true,
    val autoConnectLan: Boolean = false,
    val autoConnectLoopback: Boolean = true,
    val keepConnectedInBackground: Boolean = false,
    val notifyTurnComplete: Boolean = true,
    val notifyGoal: Boolean = true,
    val notifyNeedsAction: Boolean = true,
    val themePreference: String = "system", // light | dark | system
    /** Android 12+ dynamic color (Material You). Below API 31 this is a no-op. */
    val dynamicColor: Boolean = false,
    val localeOverride: String? = null, // null = system
    val knownPorts: List<Int> = listOf(3080),
    /**
     * Whether to ask GitHub for the latest release on start.
     *
     * The only request this app makes to anything other than the harness the user pointed it at,
     * so it is worth being able to switch off — on a restricted network, or by anyone who would
     * rather it stayed local-only.
     */
    val updateCheckEnabled: Boolean = true,
    /** A release the user has already declined, so it is offered once rather than every launch. */
    val dismissedUpdate: String? = null,
)
