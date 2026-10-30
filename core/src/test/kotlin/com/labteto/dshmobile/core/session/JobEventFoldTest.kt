package com.labteto.dshmobile.core.session

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A background job's durable events have to reach the transcript as a job row.
 *
 * The harness emits `job/registered` when a background job starts and `job/settled` when it
 * finishes. Before they were classified they fell through the fold to the generic untyped row,
 * and the transcript showed a block titled `job/registered` holding the raw payload — the event
 * name as a heading, which is the compatibility contract for an event type from the *future* and
 * not what a type shipping today should get.
 *
 * The payload shapes are the harness's own: `job/registered` carries the job's identity and its
 * label, `job/settled` adds the outcome and why it settled.
 */
class JobEventFoldTest {

    private fun event(type: String, seq: Long, data: kotlinx.serialization.json.JsonObject) =
        SessionEventEnvelope(type, seq, seq, data)

    private fun registered(seq: Long, id: String = "bash-1") = event(
        "job/registered", seq, buildJsonObject {
            put("jobId", id)
            put("owner", "session-1")
            put("kind", "bash")
            put("label", "npm run build")
            put("startedAt", 1_700_000_000_000)
            putJsonObject("toolCall") { put("callId", "call-1"); put("rootCallId", "call-0") }
        },
    )

    private fun settled(seq: Long, id: String = "bash-1") = event(
        "job/settled", seq, buildJsonObject {
            put("jobId", id)
            put("owner", "session-1")
            put("kind", "bash")
            put("label", "npm run build")
            put("startedAt", 1_700_000_000_000)
            put("finishedAt", 1_700_000_060_000)
            put("status", "failed")
            put("cause", "producer")
            put("producerReported", true)
            put("detail", "exit code: 3")
        },
    )

    @Test
    fun `a job's start and its outcome are job rows, not raw json`() {
        val snapshot = EventFold("s1").fold(listOf(registered(0), settled(1)))

        assertEquals(2, snapshot.nodes.size)
        assertTrue(
            "the job events must not reach the transcript as untyped rows: " +
                snapshot.nodes.filterIsInstance<OtherNode>(),
            snapshot.nodes.none { it is OtherNode },
        )

        val start = snapshot.nodes[0] as JobNode
        assertEquals("job/registered", start.kind)
        assertEquals("bash-1", start.data.jsonObject["jobId"]?.jsonPrimitive?.content)
        assertEquals("npm run build", start.data.jsonObject["label"]?.jsonPrimitive?.content)

        val end = snapshot.nodes[1] as JobNode
        assertEquals("job/settled", end.kind)
        assertEquals("failed", end.data.jsonObject["status"]?.jsonPrimitive?.content)
        assertEquals("exit code: 3", end.data.jsonObject["detail"]?.jsonPrimitive?.content)
    }

    @Test
    fun `the journal keeps the job events whatever the transcript does with them`() {
        val events = listOf(registered(0), settled(1))
        val snapshot = EventFold("s1").fold(events)
        assertEquals(events, snapshot.journal)
    }
}
