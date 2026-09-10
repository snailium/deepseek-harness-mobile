package com.labteto.dshmobile.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a question panel is allowed to retire itself.
 *
 * The client that answers a waterfall is never sent the `cancel` frame for it: the gateway's
 * `receiveRemoteEventResult` drops the answering client from the request's delivery set *before*
 * `finishRemoteEvent` reads that set to decide who to notify, so the cancellation reaches only the
 * other clients holding the same prompt. Treating the `cancel` frame as the only way out left the
 * panel up forever — "Submitting…" on a request the host had already settled and moved past.
 *
 * The receipt from the answer call is therefore the answerer's only notice, and this is the rule
 * that reads it.
 */
class QuestionOutcomeTest {

    @Test
    fun `an accepted answer settles the request`() {
        assertTrue(QuestionOutcome.Accepted.settledTheRequest())
    }

    @Test
    fun `not-pending settles the request too`() {
        // The host is saying the wait it was asked about is already gone — the same condition the
        // `cancel` frame would have reported, arriving as a refusal instead. A panel left up here
        // is stale for the same reason.
        assertTrue(QuestionOutcome.Refused("not-pending").settledTheRequest())
    }

    @Test
    fun `a malformed payload leaves the panel up`() {
        // `bad-response` means the answer did not match the request it addressed. The wait is
        // still open, so the user has to be able to try again.
        assertFalse(QuestionOutcome.Refused("bad-response").settledTheRequest())
    }

    @Test
    fun `an unsent answer leaves the panel up`() {
        assertFalse(QuestionOutcome.Unsent.settledTheRequest())
    }
}
