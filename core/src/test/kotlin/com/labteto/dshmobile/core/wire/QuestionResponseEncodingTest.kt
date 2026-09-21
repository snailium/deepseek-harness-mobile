package com.labteto.dshmobile.core.wire

import com.labteto.dshmobile.core.wire.dto.RemoteEventRejection
import com.labteto.dshmobile.core.wire.dto.RemoteEventOutcome
import com.labteto.dshmobile.core.wire.dto.AskUserQuestionAnswer
import com.labteto.dshmobile.core.wire.dto.AskUserQuestionAnswerItem
import com.labteto.dshmobile.core.wire.dto.QUESTION_CANCELLED
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two envelopes that settle a question request.
 *
 * Both are shapes the host parses strictly and then discards what it does not know: `custom`
 * written one level out of place was accepted, stripped, and lost, and a dismissal sent as an
 * ordinary answer is not a dismissal at all — the model reads an empty selection as "no
 * preference". Neither failure announces itself, so both are pinned here.
 */
class QuestionResponseEncodingTest {

    private class RecordingTransport : RpcTransport {
        var lastPath: String? = null
        var lastBody: String? = null

        override suspend fun post(path: String, body: String): RpcHttpResponse {
            lastPath = path
            lastBody = body
            return RpcHttpResponse(status = 200, body = """{"accepted":true}""")
        }

        override suspend fun <T> download(
            path: String,
            consume: (String?, String?, InputStream) -> T,
        ): T = consume(null, null, ByteArrayInputStream(ByteArray(0)))

        override suspend fun upload(
            path: String,
            contentType: String,
            contentLength: Long,
            body: InputStream,
            onProgress: ((Long) -> Unit)?,
        ): RpcHttpResponse = error("not used")
    }

    private fun client(transport: RpcTransport) = DshApiClient(transport = transport)

    private fun body(transport: RecordingTransport) =
        Json.parseToJsonElement(transport.lastBody!!).jsonObject

    /**
     * Unwrap the `args` frame the way the gateway does, refusing anything else.
     *
     * `parseRemoteEventResultPayload` accepts a payload only when it has *exactly one* own key,
     * `args`, with the result nested inside. A body that posts the result at the top level is
     * refused before the outcome is ever read — which is what made a question answer report
     * "Could not reach the harness." on a perfectly healthy connection. Asserting the frame, not
     * just the fields inside it, is the whole point of this helper.
     */
    private fun framedResult(transport: RecordingTransport): JsonObject {
        val payload = body(transport)["payload"]!!.jsonObject
        assertEquals(
            "the gateway accepts only an {args: {...}} frame on \$events/result",
            setOf("args"),
            payload.keys,
        )
        return payload["args"]!!.jsonObject
    }

    @Test
    fun `custom rides its own answer, not the list beside it`() = runTest {
        val transport = RecordingTransport()
        val answer = AskUserQuestionAnswer(
            listOf(
                AskUserQuestionAnswerItem("profile", listOf("Alpha")),
                AskUserQuestionAnswerItem("detail", emptyList(), "in my own words"),
            ),
        )
        val value = Json.parseToJsonElement(
            encodeToString(AskUserQuestionAnswer.serializer(), answer),
        )
        client(transport).answerEvent("client-1", "evt-1", RemoteEventOutcome.Result(value = value))

        // `/api/respond` is gone: an answer is an ordinary unary call to the Gateway's own
        // `$events/result` endpoint, bound to the generation by `clientId`.
        assertEquals("/api/\$events/result", transport.lastPath)
        val envelope = body(transport)
        assertEquals("client-request", envelope["type"]!!.jsonPrimitive.content)
        // Through the frame-aware helper: the fields below only ever reach the host if the
        // {args: …} wrapper is present, so asserting them on a bare payload would pass while the
        // real request is refused.
        val payload = framedResult(transport)
        assertEquals("client-1", payload["clientId"]!!.jsonPrimitive.content)
        assertEquals("evt-1", payload["eventId"]!!.jsonPrimitive.content)
        val outcome = payload["outcome"]!!.jsonObject
        assertEquals("result", outcome["kind"]!!.jsonPrimitive.content)
        val answers = outcome["value"]!!.jsonObject["answers"]!!.jsonArray
        assertNull(answers[0].jsonObject["custom"])
        assertEquals("in my own words", answers[1].jsonObject["custom"]!!.jsonPrimitive.content)
        // The batch itself carries nothing but the list; a sibling key here is what went missing.
        assertEquals(setOf("answers"), outcome["value"]!!.jsonObject.keys)
    }

    @Test
    fun `an answer with no free text omits the key rather than sending null`() {
        val json = encodeToString(
            AskUserQuestionAnswer.serializer(),
            AskUserQuestionAnswer(listOf(AskUserQuestionAnswerItem("a", listOf("Alpha")))),
        )
        assertFalse(json.contains("custom"))
        // `selected` is not optional the same way: an omitted list fails the host's schema.
        assertTrue(json.contains("\"selected\""))
    }

    @Test
    fun `a dismissal rejects the waterfall rather than answering it`() = runTest {
        // Answering every item with an empty selection is a valid *answer* the model reads as
        // "no preference". A dismissal has to fail the host's wait instead, so the tool call
        // settles as cancelled — and it must not be `next`, which would delegate to the host's
        // own later listeners rather than ending the request.
        val transport = RecordingTransport()
        client(transport).answerEvent(
            "client-1",
            "evt-2",
            RemoteEventOutcome.Rejected(
                error = RemoteEventRejection(
                    name = "UserQuestionError",
                    message = QUESTION_CANCELLED.message,
                    code = QUESTION_CANCELLED.code,
                ),
            ),
        )

        val outcome = framedResult(transport)["outcome"]!!.jsonObject
        assertEquals("rejected", outcome["kind"]!!.jsonPrimitive.content)
        val error = outcome["error"]!!.jsonObject
        assertEquals("cancelled", error["code"]!!.jsonPrimitive.content)
        assertEquals("the user closed this question request", error["message"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a question answer rides inside the gateway's args frame`() = runTest {
        // The bug this pins: posting the bare result object was refused by the host before it read
        // the outcome, so every answer surfaced as "Could not reach the harness." while the
        // connection was fine. The frame is load-bearing, not cosmetic.
        val transport = RecordingTransport()
        client(transport).answerEvent(
            "client-1",
            "evt-1",
            RemoteEventOutcome.Result(value = JsonPrimitive("yes")),
        )

        val result = framedResult(transport)
        assertEquals(setOf("clientId", "eventId", "outcome"), result.keys)
        assertEquals("client-1", result["clientId"]!!.jsonPrimitive.content)
        assertEquals("evt-1", result["eventId"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a dismissal rides inside the same args frame`() = runTest {
        // The approval waterfall shares this path, so one frame covers both. A dismissal that
        // escaped the frame made an approval tap do nothing at all.
        val transport = RecordingTransport()
        client(transport).answerEvent(
            "client-1",
            "evt-2",
            RemoteEventOutcome.Rejected(
                error = RemoteEventRejection(
                    name = "UserQuestionError",
                    message = QUESTION_CANCELLED.message,
                    code = QUESTION_CANCELLED.code,
                ),
            ),
        )

        val result = framedResult(transport)
        assertEquals("rejected", result["outcome"]!!.jsonObject["kind"]!!.jsonPrimitive.content)
    }
}
