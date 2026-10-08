package com.labteto.dshmobile.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which budget a client gets: the live channel's, or a probe's.
 *
 * This is the half of the `/compact` fix that a transport-level test cannot protect. The transport
 * honours whatever read timeout it is handed — including `0`, which is OkHttp's "none" — so the
 * bug was never in the transport. It was in the two construction sites that both chose a deadline
 * for a *link* and applied it to *work*, and the risk now is that someone re-adds a default at one
 * of them because an unbounded wait looks alarming.
 *
 * So the assertion is on the choice, not on the socket: a live client has **no** read deadline, and
 * a probe keeps the one it was given. The probe half is not decoration — without it, "no deadline"
 * could be implemented by ignoring budgets entirely, and a 254-address sweep would hang.
 */
class LiveTimeoutBudgetTest {

    @Test
    fun `the live channel has no read deadline`() {
        assertEquals(0L, NO_READ_DEADLINE_MS)
        assertEquals(
            "a live client must not carry a read deadline",
            0L,
            readTimeoutFor(probe = null),
        )
    }

    @Test
    fun `the live channel keeps a bounded connect budget`() {
        // A link that will not open is a link problem; waiting longer does not fix it. This is the
        // deadline that stays, and the reason removing the read one is not "no timeouts at all".
        assertTrue(LIVE_CONNECT_TIMEOUT_MS > 0)
        assertEquals(LIVE_CONNECT_TIMEOUT_MS, connectTimeoutFor(probe = null))
    }

    @Test
    fun `a probe keeps the budget it was given`() {
        listOf(ProbeTimeouts.Knock, ProbeTimeouts.Sweep, ProbeTimeouts.Manual).forEach { probe ->
            assertEquals("connect budget for $probe", probe.connectMs, connectTimeoutFor(probe))
            assertEquals("read budget for $probe", probe.readMs, readTimeoutFor(probe))
        }
        // And the sweep's own numbers are what the scan's duration depends on, so a zero here would
        // be a hang rather than a decision.
        assertTrue(ProbeTimeouts.Knock.connectMs > 0 && ProbeTimeouts.Knock.readMs > 0)
        assertTrue(ProbeTimeouts.Sweep.connectMs > 0 && ProbeTimeouts.Sweep.readMs > 0)
        assertTrue(ProbeTimeouts.Manual.connectMs > 0 && ProbeTimeouts.Manual.readMs > 0)
    }
}
