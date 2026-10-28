package com.labteto.dshmobile.connection

import com.labteto.dshmobile.core.wire.dto.HostDescription

/**
 * What answered — or did not — at one `host:port`.
 *
 * [DiscoveryEngine.probe] used to return `HostDescription?`, which made a firewall, a loopback-only
 * bind, a trust-fence rejection and a typo the same event. They need opposite instructions, so the
 * probe keeps the distinction and the connect screen spends it.
 */
sealed interface ProbeOutcome {

    /**
     * A harness answered.
     *
     * [description] is null when the endpoint proved itself but its home directory was not read —
     * on the LAN sweep, which does not spend a second request for a caption, and on any host whose
     * directory listing is unavailable. Reachability and description were one call through 0.1.1;
     * from 0.1.2 they are not, so "reachable" no longer implies "described".
     */
    data class Reachable(val description: HostDescription?) : ProbeOutcome

    /** HTTP 403 — the harness is there and its `Host` trust fence refused this address. */
    data object TrustFence : ProbeOutcome

    /**
     * HTTP 401 — the harness is there, accepted the address, and has no browser session for this
     * client.
     *
     * New with harness 0.1.2, which authenticates the whole `/api` surface against a signed cookie
     * obtained by exchanging a launch token. Deliberately not folded into [TrustFence]: a 403 is
     * about where the request came from and is fixed on the harness, while this is about who is
     * asking and is fixed by exchanging a token from the harness's startup URL.
     */
    data object Unauthenticated : ProbeOutcome

    /**
     * HTTP 401 from the harness that a relay is forwarding to: the relay reached upstream and was
     * refused there, not here.
     *
     * A relay answers 403 for a device it will not accept — missing, expired and revoked tokens all
     * — and never 401, so a 401 arriving through one can only be the harness's own answer, passed
     * along with its status intact. It is the relay's browser session that is missing, and the
     * relay is fixed on the computer; the phone's credential was accepted to get this far.
     * Deliberately not [PairingRequired] for that reason: pairing again replaces the one thing that
     * already worked.
     */
    data object RelayUnauthenticated : ProbeOutcome

    /**
     * HTTP 403 from a relay: this device has no credential it will accept any more.
     *
     * The same status as [TrustFence], and the relay answers it for a missing, expired and revoked
     * token alike (never 401 — deliberately, so a client that already reads 403 as "reached it, it
     * refused me" keeps a usable hint). What separates the two here is local knowledge: the app
     * knows whether it ever paired with this address.
     */
    data object PairingRequired : ProbeOutcome

    /**
     * The relay's public key is not the one pinned when this device paired.
     *
     * Worth its own outcome because the honest reading is ambiguous and the user is the only one who
     * can resolve it: the relay regenerates its certificate — with a new key — whenever the set of
     * addresses it covers changes, so a harness laptop that moved networks produces exactly this,
     * and so would somebody else answering on that address.
     */
    data object CertificateChanged : ProbeOutcome

    /** The host answered the network but nothing listens on the port. */
    data object Refused : ProbeOutcome

    /** Nothing answered at all: the packets were dropped rather than refused. */
    data object Timeout : ProbeOutcome

    /** The name did not resolve. */
    data object DnsFailure : ProbeOutcome

    /** No route to the host — usually a different network entirely. */
    data object Unreachable : ProbeOutcome

    /** Something is listening, but it does not speak the harness protocol. */
    data object NotAHarness : ProbeOutcome

    /**
     * The socket opened but the TLS handshake failed — a certificate this phone does not trust,
     * or `https://` aimed at a plain-HTTP server.
     */
    data object TlsFailure : ProbeOutcome

    /** Anything else; [detail] is the carrier's own words, for the fallback message. */
    data class Other(val detail: String) : ProbeOutcome
}

/** Connect/read budgets for a probe. */
data class ProbeTimeouts(val connectMs: Long, val readMs: Long) {
    companion object {
        /**
         * The first pass of a sweep: a bare TCP connect, nothing more.
         *
         * On a /24 almost every address is dead or refuses instantly, and both answers arrive in
         * well under this budget. Only the few that open a socket go on to pay for HTTP, so the
         * deadline that decides the length of a scan is this one — not [Sweep].
         */
        val Knock = ProbeTimeouts(connectMs = 300, readMs = 300)

        /** Sweeping 254 addresses: fail fast, most of them are nothing. */
        val Sweep = ProbeTimeouts(connectMs = 700, readMs = 1_500)

        /**
         * One address the user typed: worth more patience than a sweep entry, but still bounded —
         * this runs behind a progress indicator, not a frozen button.
         */
        val Manual = ProbeTimeouts(connectMs = 2_500, readMs = 4_000)
    }
}

/**
 * The live channel's connect budget.
 *
 * Bounded on purpose: a link that will not open is a link problem, and no amount of waiting turns a
 * refused socket into a working one.
 */
internal const val LIVE_CONNECT_TIMEOUT_MS = 30_000L

/**
 * The live channel's read budget: none. OkHttp's own `0`, and the point of this pair of functions.
 *
 * A response wait is gated on work the *host* does, and a request the host is working on puts
 * nothing on the wire while it works — OkHttp's read timeout is an *idle* timeout, so a host working
 * perfectly looks exactly like a host that has died. `/compact` is the case that found it: it
 * summarizes a whole session, and this app killed it at exactly 30s with `transport failure:
 * timeout` while the same command was still running. The gateway in front of it had made the same
 * mistake in the same place (`upstreamTimeoutMs`, 30s), which is why the bug appeared from both
 * sides at once.
 *
 * Enlarging the number was rejected at both ends for the same reason: any finite value is a guess
 * about somebody else's work, and a guess that is merely bigger fails identically on the session
 * large enough to matter. `dsh-mobile` made `upstreamApiTimeoutMs` default to 0 in 0.5.6; this is
 * the same decision for the same field on this side.
 *
 * What still bounds it: [LIVE_CONNECT_TIMEOUT_MS] for the link, the transport's write timeout for
 * the upload, and cancellation for the caller. Liveness does not depend on this — the mux reports a
 * dead link on its own (`ConnectionLoop`), which is why removing the deadline does not hide a
 * disconnection.
 */
internal const val NO_READ_DEADLINE_MS = 0L

/** Connect budget for a client: the probe's own, or the live channel's. */
internal fun connectTimeoutFor(probe: ProbeTimeouts?): Long = probe?.connectMs ?: LIVE_CONNECT_TIMEOUT_MS

/**
 * Read budget for a client: the probe's own, or none on the live channel.
 *
 * Both ends of this expression matter. A probe keeps the short budget it was given — a sweep of 254
 * addresses is decided by it — while a live request is never cut off for being quiet. Keeping the
 * choice in one function is what makes "the live channel has no read deadline" an assertion a test
 * can hold, rather than a property of two call sites that could drift apart.
 */
internal fun readTimeoutFor(probe: ProbeTimeouts?): Long = probe?.readMs ?: NO_READ_DEADLINE_MS
