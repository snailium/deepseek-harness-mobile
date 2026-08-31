package com.labteto.dshmobile.core.session

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The incremental fold driver must produce exactly the same conversation as the whole-log fold,
 * while keeping the node-list identity stable when nothing renderable changed — that identity is
 * what lets the transcript skip re-deriving rows on a streaming tick that only merged chunk
 * deltas into the open accumulator.
 */
class IncrementalFoldTest {

    private fun event(type: String, seq: Long, data: JsonObject): SessionEventEnvelope =
        SessionEventEnvelope(type, seq, seq, data)

    private fun turnStart(seq: Long, turn: Int = 1) =
        event("turn/start", seq, buildJsonObject { put("turn", turn) })

    private fun userMessage(seq: Long, text: String) =
        event("user/message", seq, buildJsonObject {
            put("id", "m$seq")
            putJsonArray("content") {
                add(buildJsonObject { put("type", "text"); put("text", text) })
            }
        })

    private fun chunk(seq: Long, turn: Int = 1, step: Int = 1, index: Int = 0, type: String, extra: JsonObject = buildJsonObject {}) =
        event("assistant/chunk", seq, buildJsonObject {
            put("turn", turn); put("step", step)
            putJsonObject("chunk") {
                put("type", type); put("index", index)
                extra.forEach { (k, v) -> put(k, v) }
            }
        })

    private fun assistantMessage(seq: Long, turn: Int = 1, step: Int = 1, text: String) =
        event("assistant/message", seq, buildJsonObject {
            put("turn", turn); put("step", step)
            putJsonObject("message") {
                put("id", "a$seq")
                putJsonArray("content") {
                    add(buildJsonObject { put("type", "text"); put("text", text) })
                }
            }
        })

    private fun turnEnd(seq: Long, turn: Int = 1) =
        event("turn/end", seq, buildJsonObject {
            put("turn", turn)
            putJsonObject("reason") { put("kind", "completed") }
        })

    /** A realistic streamed turn: start, user, chunk deltas, message, end. */
    private fun streamedTurn(base: Long): List<SessionEventEnvelope> {
        val turn = (base / 100).toInt() + 1
        return listOf(
            turnStart(base, turn),
            userMessage(base + 1, "hello $turn"),
            chunk(base + 2, turn, 1, 0, "block-start", buildJsonObject { put("blockType", "text") }),
            chunk(base + 3, turn, 1, 0, "text-delta", buildJsonObject { put("text", "the ") }),
            chunk(base + 4, turn, 1, 0, "text-delta", buildJsonObject { put("text", "reply") }),
            assistantMessage(base + 5, turn, 1, "the reply"),
            turnEnd(base + 6, turn),
        )
    }

    @Test
    fun `incremental fold matches whole-log fold event by event`() {
        val all = (0L..6L step 7).flatMap { streamedTurn(it) } + listOf(
            turnStart(700, 101),
            userMessage(701, "one more"),
            assistantMessage(702, 101, 1, "done"),
            turnEnd(703, 101),
        )

        // Whole-log reference.
        val reference = EventFold("s1").fold(all)

        // Feed the same events one at a time through an incremental driver.
        val seeded = EventFold("s1").fold(emptyList())
        val driver = EventFold.Incremental(seeded, "s1")
        var last: ConversationSnapshot? = null
        for (event in all) {
            last = driver.apply(event)
        }
        val incremental = last!!

        assertEquals(reference.nodes.map { it.seq }, incremental.nodes.map { it.seq })
        assertEquals(reference.nodes, incremental.nodes)
        assertEquals(reference.running, incremental.running)
        assertEquals(reference.blank, incremental.blank)
        assertEquals(reference.gap, incremental.gap)
        assertEquals(reference.lastSeq, incremental.lastSeq)
    }

    @Test
    fun `chunk delta into open accumulator keeps node view identity stable`() {
        val turn = streamedTurn(0)
        val driver = EventFold.Incremental(EventFold("s1").fold(emptyList()), "s1")
        turn.forEach { driver.apply(it) }
        val before = driver.snapshot()

        // A chunk delta that changes the *open* block does not create a new node — the rendered
        // node list is unchanged, so the snapshot's nodes must be the same instance. Contiguous
        // seq (no gap), because a gap is itself a structural change.
        val delta = chunk(7, 1, 1, 0, "text-delta", buildJsonObject { put("text", "extra") })
        val after = driver.apply(delta)!!
        assertSame("nodes view must be stable across a merge-only delta", before.nodes, after.nodes)
        assertEquals("seq advances on every event", 7L, after.lastSeq)
    }

    @Test
    fun `a structural event invalidates the node view`() {
        val driver = EventFold.Incremental(EventFold("s1").fold(emptyList()), "s1")
        driver.apply(turnStart(1))
        driver.apply(userMessage(2, "hi"))
        val before = driver.snapshot()

        val after = driver.apply(assistantMessage(3, 1, 1, "a reply"))!!
        assertNotSame("a new message node must yield a fresh nodes view", before.nodes, after.nodes)
        assertEquals(3, after.nodes.size)
    }

    @Test
    fun `duplicate events are skipped and do not change the view`() {
        val driver = EventFold.Incremental(EventFold("s1").fold(emptyList()), "s1")
        driver.apply(turnStart(1))
        val first = driver.apply(userMessage(2, "hi"))!!
        val dup = driver.apply(userMessage(2, "hi"))
        assertEquals(null, dup)
        // The driver did not consume the duplicate; the last snapshot is unchanged.
        assertSame(first.nodes, driver.snapshot().nodes)
    }

    @Test
    fun `gap detection survives the incremental path`() {
        val driver = EventFold.Incremental(EventFold("s1").fold(emptyList()), "s1")
        driver.apply(turnStart(0))
        val snapshot = driver.apply(turnEnd(5))!!
        assertTrue("a gap between 0 and 5 must be reported", snapshot.gap)
    }

    @Test
    fun `interrupted mark replaces the node and invalidates the view`() {
        val driver = EventFold.Incremental(EventFold("s1").fold(emptyList()), "s1")
        driver.apply(turnStart(0, 2))
        driver.apply(assistantMessage(1, 2, 1, "partial"))
        val before = driver.snapshot()
        val after = driver.apply(
            event("turn/end", 2, buildJsonObject {
                put("turn", 2)
                putJsonObject("reason") { put("kind", "aborted") }
            }),
        )!!
        assertNotSame("replacing the node must invalidate the view", before.nodes, after.nodes)
        val assistant = after.nodes.first { it is AssistantMessageNode } as AssistantMessageNode
        assertTrue(assistant.interrupted)
    }

    @Test
    fun `metadata-only events do not change the node view`() {
        val driver = EventFold.Incremental(EventFold("s1").fold(emptyList()), "s1")
        driver.apply(turnStart(1))
        driver.apply(userMessage(2, "hi"))
        val before = driver.snapshot()

        // A request/header event is log-only; it must not touch the rendered nodes.
        val meta = event("request/header", 3, buildJsonObject { put("x", "y") })
        val after = driver.apply(meta)!!
        assertSame("metadata must not change the node view", before.nodes, after.nodes)
        assertEquals(3L, after.lastSeq)
    }

    @Test
    fun `paged-prepend style reseed reproduces the merged list`() {
        // Simulate a history page merged at the head: the store clears the driver, merges the
        // list, and reseeds — the fold of the merged list must be the reference.
        val early = streamedTurn(0)
        val late = streamedTurn(100)
        val merged = early + late

        val reseeded = EventFold("s1").fold(merged)
        val driver = EventFold.Incremental(reseeded, "s1")
        var last: ConversationSnapshot? = null
        for (event in late) last = driver.apply(event)
        // The tail was already folded by the seed; replaying it is a no-op, so the driver keeps
        // the seeded snapshot.
        val snapshot = last ?: driver.snapshot()
        assertEquals(merged.last().seq, snapshot.lastSeq)
        assertEquals(reseeded.nodes, snapshot.nodes)
    }
}
