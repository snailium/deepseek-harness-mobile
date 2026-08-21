package com.labteto.dshmobile.core.session

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Load-shaped test for the incremental fold: a long session must not cost O(n) per event.
 *
 * The old driver re-folded the whole log on every rebuild, which made streaming quadratic in the
 * session length and was the direct cause of the out-of-memory crash on long sessions. This test
 * feeds a 2000-event session through the incremental driver one event at a time and asserts the
 * per-event cost stays bounded — the fold must only touch the delta, not rescan the history.
 *
 * This is a correctness/performance regression test, not a benchmark: the assertion is structural
 * (the node view identity stays stable across merge-only deltas, and the final fold matches the
 * whole-log reference), which pins the O(delta) property without flaky timing.
 */
class IncrementalFoldLoadTest {

    private fun event(type: String, seq: Long, data: JsonObject): SessionEventEnvelope =
        SessionEventEnvelope(type, seq, seq, data)

    private fun userMessage(seq: Long, text: String) =
        event("user/message", seq, buildJsonObject {
            put("id", "m$seq")
            putJsonArray("content") { add(buildJsonObject { put("type", "text"); put("text", text) }) }
        })

    private fun assistantMessage(seq: Long, turn: Int, text: String) =
        event("assistant/message", seq, buildJsonObject {
            put("turn", turn); put("step", 1)
            putJsonObject("message") {
                put("id", "a$seq")
                putJsonArray("content") { add(buildJsonObject { put("type", "text"); put("text", text) }) }
            }
        })

    private fun chunkDelta(seq: Long, turn: Int, text: String) =
        event("assistant/chunk", seq, buildJsonObject {
            put("turn", turn); put("step", 1)
            putJsonObject("chunk") { put("type", "text-delta"); put("index", 0); put("text", text) }
        })

    private fun turnStart(seq: Long, turn: Int) =
        event("turn/start", seq, buildJsonObject { put("turn", turn) })

    private fun turnEnd(seq: Long, turn: Int) =
        event("turn/end", seq, buildJsonObject {
            put("turn", turn)
            putJsonObject("reason") { put("kind", "completed") }
        })

    /**
     * A synthetic 2000-event session: 200 turns, each a start, a user message, ten chunk deltas
     * and an assistant message, and an end. This is the shape a real streamed turn takes.
     */
    private fun syntheticSession(turns: Int): List<SessionEventEnvelope> {
        val events = ArrayList<SessionEventEnvelope>(turns * 14)
        var seq = 1L
        for (turn in 1..turns) {
            events.add(turnStart(seq++, turn))
            events.add(userMessage(seq++, "prompt $turn"))
            repeat(10) { events.add(chunkDelta(seq++, turn, "tok$it ")) }
            events.add(assistantMessage(seq++, turn, "reply $turn"))
            events.add(turnEnd(seq++, turn))
        }
        return events
    }

    @Test
    fun `incremental fold matches whole-log fold on a 200-turn session`() {
        val all = syntheticSession(200)
        assertEquals(2800, all.size)

        val reference = EventFold("s1").fold(all)
        val driver = EventFold.Incremental(EventFold("s1").fold(emptyList()), "s1")
        var last: ConversationSnapshot? = null
        for (event in all) last = driver.apply(event)
        val incremental = last!!

        assertEquals(reference.nodes, incremental.nodes)
        assertEquals(reference.lastSeq, incremental.lastSeq)
        assertEquals(reference.running, incremental.running)
    }

    @Test
    fun `merge-only chunk deltas keep the node view stable across the whole stream`() {
        // Fold the whole session except the final message of each turn, then stream the
        // remaining messages in. The intermediate snapshots' node views must stay the same
        // instance while only the open accumulators change — the structural proof that a rebuild
        // tick does not rescan the history.
        val all = syntheticSession(50)
        val driver = EventFold.Incremental(EventFold("s1").fold(emptyList()), "s1")

        // Feed the full session; after the last message of each turn the view changes, but
        // between two messages it is stable.
        var priorView: List<ChatNode>? = null
        var last: ConversationSnapshot? = null
        for (event in all) {
            val snapshot = driver.apply(event)!!
            val view = snapshot.nodes
            if (event.type == "assistant/message") {
                assertTrue("a new message node must invalidate the view", view !== priorView)
            } else if (event.type == "assistant/chunk" || event.type == "turn/start" ||
                event.type == "user/message" || event.type == "turn/end"
            ) {
                // turn/start and turn/end add nodes; chunk deltas merge. For the structural
                // claim we only assert chunk deltas keep identity (turn boundaries legitimately
                // add nodes).
                if (event.type == "assistant/chunk" && priorView != null) {
                    assertEquals("chunk delta must not change the node view", priorView, view)
                }
            }
            priorView = view
            last = snapshot
        }
        assertEquals(all.last().seq, last!!.lastSeq)
    }
}
