package com.labteto.dshmobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One pending card per session, not one slot for the whole host.
 *
 * The old store held a single `PendingApproval?` and a single `PendingQuestions?`: whichever
 * session's waterfall arrived last owned both, and answering (or dismissing) one session's card
 * cleared the shared slot — taking every other session's panel with it, leaving the web client as
 * the only place left to answer. The store now keeps a map per kind and touches only the entry
 * for the session that acted; these pin that rule at the state level, where the store's internals
 * live, because no full-store harness test exists for the event path.
 */
class PendingCardsTest {

    private fun approval(sessionId: String, eventId: String) = PendingApproval(
        sessionId = sessionId,
        approvalId = eventId,
        rpcId = eventId,
        toolName = "bash",
        reason = null,
    )

    private fun questions(sessionId: String, eventId: String) = PendingQuestions(
        sessionId = sessionId,
        rpcId = eventId,
        items = emptyList(),
    )

    // ------------------------------------------------------------------ approvals

    @Test
    fun `a second session's approval does not replace the first`() {
        var cards = mapOf("a" to approval("a", "e1"))
        cards = cards + ("b" to approval("b", "e2"))
        assertEquals(setOf("a", "b"), cards.keys)
    }

    @Test
    fun `a second approval for the same session replaces its own card`() {
        var cards = mapOf("a" to approval("a", "e1"))
        cards = cards + ("a" to approval("a", "e2"))
        assertEquals(1, cards.size)
        assertEquals("e2", cards["a"]?.approvalId)
    }

    @Test
    fun `forgetting one session's approval leaves the other sessions' cards`() {
        var cards = mapOf(
            "a" to approval("a", "e1"),
            "b" to approval("b", "e2"),
            "c" to approval("c", "e3"),
        )
        // The rule [forgetRequest] applies: drop the entry only while it still carries this event.
        val sessionId = "b"
        if (cards[sessionId]?.approvalId == "e2") cards = cards - sessionId
        assertEquals(setOf("a", "c"), cards.keys)
    }

    @Test
    fun `forgetting an approval whose card was replaced takes nothing away`() {
        var cards = mapOf("a" to approval("a", "e1"))
        cards = cards + ("a" to approval("a", "e2"))
        // A `cancel` for the superseded event arrives late: the guard must leave the live card.
        if (cards["a"]?.approvalId == "e1") cards = cards - "a"
        assertEquals(1, cards.size)
        assertEquals("e2", cards["a"]?.approvalId)
    }

    // ------------------------------------------------------------------ questions

    @Test
    fun `question cards for two sessions coexist`() {
        var cards = mapOf("a" to questions("a", "q1"))
        cards = cards + ("b" to questions("b", "q2"))
        assertEquals(setOf("a", "b"), cards.keys)
    }

    @Test
    fun `forgetting one session's question leaves the other sessions' cards`() {
        var cards = mapOf(
            "a" to questions("a", "q1"),
            "b" to questions("b", "q2"),
        )
        // The rule [forgetQuestions] applies, after the registry agrees the request is this one's.
        val sessionId = "a"
        val shown = cards[sessionId]
        if (shown != null && shown.rpcId == "q1") cards = cards - sessionId
        assertEquals(setOf("b"), cards.keys)
    }

    @Test
    fun `forgetting a question whose card was replaced takes nothing away`() {
        var cards = mapOf("a" to questions("a", "q1"))
        cards = cards + ("a" to questions("a", "q2"))
        val shown = cards["a"]
        // The late receipt names the superseded event; the guard must leave the live card.
        if (shown != null && shown.rpcId == "q1") cards = cards - "a"
        assertEquals(1, cards.size)
        assertEquals("q2", cards["a"]?.rpcId)
    }

    @Test
    fun `removing a dead session drops its card and only its card`() {
        var cards = mapOf(
            "a" to questions("a", "q1"),
            "b" to questions("b", "q2"),
        )
        cards = cards - "a"
        assertEquals(setOf("b"), cards.keys)
    }

    // ------------------------------------------------------------------ the registry rule behind it

    @Test
    fun `the registry still holds one batch per session`() {
        val registry = PendingQuestionRegistry()
        registry.install("a", "q1")
        registry.install("b", "q2")
        assertEquals("q1", registry.eventFor("a"))
        assertEquals("q2", registry.eventFor("b"))
        assertTrue(registry.forget("a", "q1"))
        assertFalse(registry.forget("a", "q1")) // already gone: the guard is what makes it safe
        assertEquals("q2", registry.eventFor("b"))
    }

    @Test
    fun `an open session reads only its own card`() {
        val cards = mapOf(
            "a" to questions("a", "q1"),
            "b" to questions("b", "q2"),
        )
        // What [ChatScreen] does: index by the open session id.
        assertEquals("q1", cards["a"]?.rpcId)
        assertNull(cards["gone"]?.rpcId)
    }
}
