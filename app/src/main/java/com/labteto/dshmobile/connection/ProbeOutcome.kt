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

    /** A harness answered `host.describe`. */
    data class Reachable(val description: HostDescription) : ProbeOutcome

    /** HTTP 403 — the harness is there and its `Host` trust fence refused this address. */
    data object TrustFence : ProbeOutcome

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

    /** Anything else; [detail] is the carrier's own words, for the fallback message. */
    data class Other(val detail: String) : ProbeOutcome
}

/** Connect/read budgets for a probe. */
data class ProbeTimeouts(val connectMs: Long, val readMs: Long) {
    companion object {
        /** Sweeping 254 addresses: fail fast, most of them are nothing. */
        val Sweep = ProbeTimeouts(connectMs = 700, readMs = 1_500)

        /**
         * One address the user typed: worth more patience than a sweep entry, but still bounded —
         * this runs behind a progress indicator, not a frozen button.
         */
        val Manual = ProbeTimeouts(connectMs = 2_500, readMs = 4_000)
    }
}
