package com.labteto.dshmobile.ui.screens.connect

import com.labteto.dshmobile.connection.ProbeOutcome
import com.labteto.dshmobile.core.wire.GenerationFailure
import com.labteto.dshmobile.core.wire.TransportFailure
import com.labteto.dshmobile.core.wire.TransportFailures

/**
 * Why a connection attempt did not succeed, at the level a person can act on.
 *
 * One step above [ProbeOutcome]: the probe knows what the socket did, this knows what to tell
 * someone standing between a phone and a computer. Deliberately free of Android imports so the whole
 * mapping is unit-testable — the app's tests are plain JVM, with no Robolectric.
 */
sealed interface ConnectFailure {

    /** The address or port was not usable as typed. */
    data object InvalidInput : ConnectFailure

    /**
     * An edge proxy (Cloudflare Access or similar) in front of the harness refused the request —
     * the endpoint is reachable, but the credentials it carries were missing or rejected.
     */
    data object AccessDenied : ConnectFailure

    /** Nothing answered — dropped packets. Firewall, or a router isolating wireless clients. */
    data object Timeout : ConnectFailure

    /** Actively refused — the computer is there, the harness is not listening on that port. */
    data object Refused : ConnectFailure

    /** The harness answered and its `Host` trust fence rejected this address. */
    data object TrustFence : ConnectFailure

    /** The name did not resolve on this network. */
    data object DnsFailure : ConnectFailure

    /** Something is listening, but it is not a harness. */
    data object NotAHarness : ConnectFailure

    /** The API answered but the event streams would not open. */
    data object StreamsBlocked : ConnectFailure

    /** Anything else; [detail] is the carrier's own words. */
    data class Other(val detail: String) : ConnectFailure

    companion object {

        /** Map a pre-flight probe outcome. */
        fun from(outcome: ProbeOutcome): ConnectFailure = when (outcome) {
            is ProbeOutcome.Reachable -> Other("")
            ProbeOutcome.AccessDenied -> AccessDenied
            ProbeOutcome.TrustFence -> TrustFence
            ProbeOutcome.Refused -> Refused
            ProbeOutcome.Timeout -> Timeout
            ProbeOutcome.DnsFailure -> DnsFailure
            // No route is a different-network problem; "nothing answered" is the honest reading.
            ProbeOutcome.Unreachable -> Timeout
            ProbeOutcome.NotAHarness -> NotAHarness
            is ProbeOutcome.Other -> Other(outcome.detail)
        }

        /** Map a failure from inside the connection loop's readiness handshake. */
        fun from(failure: GenerationFailure): ConnectFailure = when (failure) {
            is GenerationFailure.StreamsTimedOut -> StreamsBlocked
            is GenerationFailure.StreamFailed -> fromKind(failure.kind, failure.message, StreamsBlocked)
            is GenerationFailure.DescribeFailed ->
                fromKind(TransportFailures.of(failure.error), failure.error.message, Other(failure.error.message))
        }

        private fun fromKind(
            kind: TransportFailure?,
            message: String?,
            fallback: ConnectFailure,
        ): ConnectFailure = when (kind) {
            TransportFailure.TRUST_FENCE -> TrustFence
            TransportFailure.ACCESS_DENIED -> AccessDenied
            TransportFailure.REFUSED -> Refused
            TransportFailure.TIMEOUT, TransportFailure.UNREACHABLE -> Timeout
            TransportFailure.DNS -> DnsFailure
            TransportFailure.NOT_FOUND, TransportFailure.NOT_A_HARNESS -> NotAHarness
            TransportFailure.OTHER -> message?.takeIf { it.isNotBlank() }?.let { Other(it) } ?: fallback
            null -> fallback
        }
    }
}
