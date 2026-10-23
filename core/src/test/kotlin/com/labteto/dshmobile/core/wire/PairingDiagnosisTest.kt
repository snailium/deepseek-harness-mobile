package com.labteto.dshmobile.core.wire

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A bare 403 from a `dsh-mobile` gateway must not be reported as a relay's host fence.
 *
 * The phone cannot tell the two products apart from the status alone, so the reading depends on
 * asking the address what it serves. Every case below is one that produced, or would have produced,
 * the wrong instruction on screen.
 */
class PairingDiagnosisTest {

    private val gateway = MobileAccessMetadata(version = 1, pluginVersion = "0.6.16")

    private fun reading(outcome: RelayPairOutcome, metadata: MobileAccessMetadata?) =
        relayClaimReading(outcome, metadata)

    @Test
    fun `a gateway refusing a relay claim is not a relay host fence`() {
        assertEquals(RelayClaimReading.GatewayNotRelay, reading(RelayPairOutcome.HostRefused, gateway))
    }

    @Test
    fun `a gateway that answered 404 is still a gateway`() {
        assertEquals(RelayClaimReading.GatewayNotRelay, reading(RelayPairOutcome.NotARelay, gateway))
    }

    @Test
    fun `a relay refusing this address keeps the host-fence advice`() {
        assertEquals(RelayClaimReading.HostFence, reading(RelayPairOutcome.HostRefused, null))
    }

    @Test
    fun `nothing serving a claim is reported as nothing there`() {
        assertEquals(RelayClaimReading.NothingThere, reading(RelayPairOutcome.NotARelay, null))
    }

    @Test
    fun `an empty metadata object is not a gateway`() {
        // `{}` decodes into defaults, so presence alone would call any 200 a gateway.
        assertEquals(
            RelayClaimReading.HostFence,
            reading(RelayPairOutcome.HostRefused, MobileAccessMetadata()),
        )
    }

    @Test
    fun `outcomes that already say what happened are not re-read`() {
        assertNull(reading(RelayPairOutcome.Rejected, gateway))
        assertNull(reading(RelayPairOutcome.RateLimited(1), gateway))
        assertNull(reading(RelayPairOutcome.Unreachable(TransportFailure.TIMEOUT, null), gateway))
    }
}
