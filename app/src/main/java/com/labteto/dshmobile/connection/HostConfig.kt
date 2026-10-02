package com.labteto.dshmobile.connection

import com.labteto.dshmobile.core.wire.dto.HostDescription
import kotlinx.serialization.Serializable

/**
 * One remembered harness endpoint, reached directly or through a `dsh-relay`.
 *
 * The `last*` fields cache the newest `host.describe` so a Recent card can say what the harness is
 * before its liveness probe lands — and can still say it about a harness that is now switched off.
 * They all default, because the whole list is persisted as one JSON blob whose decode failure is
 * swallowed: a field without a default would silently wipe every remembered host on upgrade. That
 * applies just as much to the relay fields below, which is why every one of them has a default even
 * though a paired relay always has three of them set.
 *
 * The bearer token is deliberately **not** here. This record is serialized into plain DataStore;
 * the credential lives in [RelayCredentialStore], keyed by [id].
 */
@Serializable
data class HostConfig(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val isLoopback: Boolean = false,
    /**
     * Speak TLS to this endpoint. The harness itself never serves HTTPS — this is for a reverse
     * proxy someone put in front of it (Caddy at `https://agent.home`, say), which is the only
     * way it is ever reached over TLS.
     */
    val useTls: Boolean = false,
    val basePath: String = "",
    val lastConnectedAt: Long = 0L,
    /**
     * The host account's home directory, as of the last successful connection.
     *
     * This is the only host fact 0.1.2 still publishes. Through 0.1.1 the record also remembered
     * the harness version, its working directory and its attached-session count, all from
     * `host.describe`; that call is gone and nothing replaces those three, so they are no longer
     * remembered or shown.
     */
    val lastHome: String? = null,
    /** Base64 SHA-256 of the relay's DER SubjectPublicKeyInfo; null when there is nothing to pin. */
    val relayFingerprint: String? = null,
    /** The relay's own id for this device, shown in its device list. Non-null iff this host is paired. */
    val relayDeviceId: String? = null,
    /** Epoch millis the device token expires, as the relay reported it at pairing. */
    val relayTokenExpiresAt: Long = 0L,
    /**
     * The `dsh-mobile` gateway's own id for this device, shown in its device list.
     *
     * Non-null iff this host is a gateway this device paired with, which is why it — and not a
     * transport flag — is what [isGateway] reads. A gateway reached over plain HTTP on a LAN and one
     * reached through a TLS tunnel are the same kind of endpoint, and an `https` address this device
     * never paired with is not one.
     */
    val gatewayDeviceId: String? = null,
    /** The gateway's stable instance id, as it named itself at pairing. */
    val gatewayInstanceId: String? = null,
    /**
     * Base64 DER of the CA the gateway's listener is signed by, when it serves a private one.
     *
     * Not a secret — a certificate is public — so it lives here with the rest of the record rather
     * than in the encrypted store. Null means the listener uses a publicly trusted certificate (a
     * tunnel, a reverse proxy) and the platform trust store is the whole decision.
     */
    val gatewayCaDer: String? = null,
    /** Lower-case hex SHA-256 of [gatewayCaDer]; what the pairing input was checked against. */
    val gatewayCaFingerprint: String? = null,
) {
    /** Endpoint identity and display form, including TLS and the reverse-proxy root. */
    val authority: String get() = endpointKey(host, port, useTls, basePath)
    val baseUrl: String get() = harnessBaseUrl(host, port, useTls, basePath)

    /** What a card prints: the authority, scheme-qualified only when it is not the plain default. */
    val displayAddress: String get() = authority

    /**
     * Whether this endpoint is a paired relay.
     *
     * Keyed on the device id rather than on the transport: a relay running `tls: off` is still a
     * relay that needs its bearer token, and an `https` address this device never paired with is
     * not one.
     */
    val isRelay: Boolean get() = relayDeviceId != null

    /**
     * Whether this endpoint is a paired `dsh-mobile` gateway.
     *
     * Mutually exclusive with [isRelay] in practice: the two are different products with different
     * credential models, and a host that somehow carried both ids would be one this build has no
     * coherent way to address. Pairing always clears the other side's identity, so the state cannot
     * arise from this app.
     */
    val isGateway: Boolean get() = gatewayDeviceId != null

    /** Whether this endpoint replaces the harness's own session (gateway or relay). */
    val isCredentialed: Boolean get() = isRelay || isGateway

    /** Whether traffic to this endpoint travels in the clear. */
    val isPlaintext: Boolean get() = !useTls
}

/** Read endpoint-specific history first, retaining the pre-prefix key as an upgrade fallback. */
internal fun HostConfig.rememberedSession(saved: Map<String, String>): String? =
    saved[baseUrl] ?: saved["$host:$port"]

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
    /** True when the advertisement said the listener terminates TLS. */
    val useTls: Boolean = false,
    val basePath: String = "",
    /** SPKI pin from the mDNS `pin` record, when the listener terminates TLS. */
    val fingerprint: String? = null,
    /**
     * Whether this is a `dsh-relay` rather than a bare harness.
     *
     * A relay answers `/relay/health` and refuses `/api` until this device pairs, so it can never be
     * connected to straight from a discovery card the way a harness can — it routes to pairing.
     */
    val isRelay: Boolean = false,
    /**
     * The relay answered and its fence refused the address it was reached by.
     *
     * Still a find. The relay is running, on the right port, and one entry in its
     * `publicHostnames` away — hiding it would send someone looking for a fault that is not there.
     */
    val hostRefused: Boolean = false,
    /**
     * Whether this is a `dsh-mobile` gateway rather than a bare harness.
     *
     * A gateway refuses every `/api` call until this device pairs, so like [isRelay] it routes to
     * pairing instead of connecting.
     */
    val isGateway: Boolean = false,
    /**
     * The gateway's instance id, when the advertisement carried one.
     *
     * On a LAN this is the fingerprint of the CA that signs the listener, so it is also what the
     * pairing input is checked against — a discovered card and a scanned code agree on it, and a
     * code for a different computer does not.
     */
    val instanceId: String? = null,
    /** What the computer calls itself in its advertisement. */
    val deviceName: String? = null,
) {
    val authority: String get() = endpointKey(host, port, useTls, basePath)

    /** Origin to address this endpoint by. */
    val baseUrl: String get() = harnessBaseUrl(host, port, useTls, basePath)

    /** Whether the harness accepted an `/api` call from this device. */
    val trusted: Boolean get() = description != null
}

/** App-level persisted settings (DataStore). */
data class AppSettings(
    val autoConnectLast: Boolean = true,
    val autoConnectLan: Boolean = false,
    val autoConnectLoopback: Boolean = true,
    /** Relay mode's counterpart to [autoConnectLan]: connect to a paired relay that mDNS finds. */
    val autoConnectRelay: Boolean = false,
    val keepConnectedInBackground: Boolean = true,
    val notifyTurnComplete: Boolean = true,
    val notifyGoal: Boolean = true,
    val notifyNeedsAction: Boolean = true,
    /**
     * Which way the user chose to reach a harness: `lan` or `relay`.
     *
     * Not a detected value. The two paths have different trust models — an unauthenticated LAN
     * harness against a credentialed relay — and auto-connect never crosses between them, so the
     * app connects only the way that was actually picked. Defaults to `lan` so an install that
     * predates relay support comes back where it was.
     */
    val connectMode: String = ConnectMode.LAN,
    val themePreference: String = "system", // light | dark | system
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
    /**
     * What the send button does while a turn is running: `queue` (append a new turn) or `steer`
     * (splice into the running one).
     *
     * Persisted rather than per-draft because it is a working preference, not a property of one
     * message: someone who steers does so all session, and re-picking it after every restart —
     * or worse, silently queueing when they meant to steer — is the failure this avoids.
     */
    val promptMode: String = PromptMode.QUEUE,
    /**
     * Whether the keyboard's enter key sends the message.
     *
     * Off by default, and that default is the point: the field is multi-line, so enter inserts a
     * newline the way every other multi-line field does, and the send button is the affordance.
     * On is for anyone who types short messages and wants the keyboard to submit.
     */
    val enterToSend: Boolean = false,
)

/**
 * The two things a message can do to a turn that is already running.
 *
 * Both ride `session/prompt`; the host has always accepted `mode: queue|steer` mid-turn, and the
 * distinction only exists while `running` is true — an idle agent has nothing to steer.
 */
object PromptMode {
    /** Append the message as its own turn, to run when the current one finishes. */
    const val QUEUE: String = "queue"

    /** Splice the message into the turn in flight, as if the model had just been told this. */
    const val STEER: String = "steer"
}

/** The two ways the app can reach a harness. Persisted as [AppSettings.connectMode]. */
object ConnectMode {
    /** Straight at a harness on the local network, over plain HTTP, with no credential. */
    const val LAN: String = "lan"

    /**
     * Through a paired endpoint: a `dsh-relay`, or a `dsh-mobile` gateway.
     *
     * One mode for both, because the user-facing decision is the same one — "reach my computer the
     * way I enrolled this phone, from anywhere" — and the two products differ in how they hold the
     * credential, not in what choosing that path means. What the mode excludes is the *unpaired*
     * case: a bare harness on the local network, which is signed in once with no credential of its
     * own. Auto-connect never crosses between the two, because doing so would make the choice on the
     * connect screen a suggestion.
     */
    const val RELAY: String = "relay"

    /** Read a stored value back, falling back to [LAN] for anything unrecognised. */
    fun of(value: String?): String = if (value == RELAY) RELAY else LAN
}
