package com.labteto.dshmobile.ui.screens.main

import com.labteto.dshmobile.core.session.AssistantMessageNode
import com.labteto.dshmobile.core.session.ChatBlock
import com.labteto.dshmobile.core.session.ChatNode
import com.labteto.dshmobile.core.session.ToolCallNode
import com.labteto.dshmobile.core.session.ToolResultNode
import com.labteto.dshmobile.core.session.TurnStartNode
import com.labteto.dshmobile.core.session.UserMessageNode
import kotlinx.serialization.json.JsonNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Search must find what the reader can read and skip what the harness merely did.
 *
 * The scope decision is the whole feature: including thinking is what makes a long session
 * searchable at all (the reasoning is the longest text and it is collapsed by default), and
 * excluding tool traffic is what keeps a hit list from filling with JSON.
 */
class TranscriptSearchTest {

    private fun user(seq: Long, text: String, source: String = "user") = UserMessageNode(
        seq = seq,
        messageId = "m$seq",
        blocks = listOf(ChatBlock(kind = "text", text = text)),
        sourceKind = source,
    )

    private fun assistant(seq: Long, vararg blocks: ChatBlock) = AssistantMessageNode(
        seq = seq,
        messageId = "m$seq",
        turn = 1,
        step = 1,
        blocks = blocks.toList(),
    )

    private fun text(value: String) = ChatBlock(kind = "text", text = value)
    private fun thinking(value: String) = ChatBlock(kind = "reasoning", text = value)

    private fun toolCallBlock(value: String) =
        ChatBlock(kind = "tool-call", text = value, toolCallId = "c1", toolName = "Read")

    @Test
    fun `a blank query matches nothing`() {
        val nodes: List<ChatNode> = listOf(user(1, "hello"))
        assertTrue(findTranscriptMatches(nodes, "").isEmpty())
        assertTrue(findTranscriptMatches(nodes, "   ").isEmpty())
    }

    @Test
    fun `user messages and answers are both in scope`() {
        val nodes = listOf(
            user(1, "where is the config"),
            assistant(2, text("the config lives in settings.yaml")),
        )
        assertEquals(
            listOf(TranscriptMatch(1, MatchKind.User), TranscriptMatch(2, MatchKind.Answer)),
            findTranscriptMatches(nodes, "config"),
        )
    }

    @Test
    fun `thinking matches even though it is collapsed`() {
        val nodes = listOf(assistant(7, thinking("maybe the cache is stale")))
        assertEquals(listOf(TranscriptMatch(7, MatchKind.Thinking)), findTranscriptMatches(nodes, "cache"))
    }

    @Test
    fun `prose wins the tie when a message matches both ways`() {
        val nodes = listOf(assistant(3, thinking("check the config"), text("the config is fine")))
        assertEquals(listOf(TranscriptMatch(3, MatchKind.Answer)), findTranscriptMatches(nodes, "config"))
    }

    @Test
    fun `one message yields one match however often it repeats`() {
        val nodes = listOf(assistant(4, text("config config config")))
        assertEquals(1, findTranscriptMatches(nodes, "config").size)
    }

    @Test
    fun `tool calls and their results are out of scope`() {
        val nodes = listOf(
            ToolCallNode(seq = 5, callId = "c1", name = "Read", arguments = "{\"path\":\"config\"}", turn = 1, step = 1),
            ToolResultNode(seq = 6, callId = "c1", content = JsonNull, isError = false, turn = 1, step = 1),
            assistant(7, toolCallBlock("read config")),
            assistant(8, ChatBlock(kind = "tool-result", text = "config file contents")),
        )
        assertTrue(findTranscriptMatches(nodes, "config").isEmpty())
    }

    @Test
    fun `structural nodes never match`() {
        val nodes = listOf(TurnStartNode(seq = 9, turn = 1))
        assertTrue(findTranscriptMatches(nodes, "1").isEmpty())
    }

    @Test
    fun `matching is case insensitive and trimmed`() {
        val nodes = listOf(user(1, "Mixed Case Needle"))
        assertEquals(1, findTranscriptMatches(nodes, "  mixed case  ").size)
    }

    @Test
    fun `injected context is searchable like any other message`() {
        // The system prompt renders as a collapsed disclosure rather than a bubble, but it is
        // still message text the reader may want to find.
        val nodes = listOf(user(1, "you are a helpful agent", source = "agent-instructions"))
        assertEquals(listOf(TranscriptMatch(1, MatchKind.User)), findTranscriptMatches(nodes, "helpful"))
    }

    @Test
    fun `results keep transcript order`() {
        val nodes = listOf(
            user(1, "alpha"),
            assistant(2, text("alpha")),
            user(3, "alpha"),
        )
        assertEquals(listOf(1L, 2L, 3L), findTranscriptMatches(nodes, "alpha").map { it.seq })
    }

    @Test
    fun `the cursor wraps at both ends`() {
        assertEquals(0, stepMatchCursor(cursor = 2, delta = 1, count = 3))
        assertEquals(2, stepMatchCursor(cursor = 0, delta = -1, count = 3))
        assertEquals(1, stepMatchCursor(cursor = 0, delta = 1, count = 3))
    }

    @Test
    fun `the cursor stays put when there is nothing to step through`() {
        assertEquals(0, stepMatchCursor(cursor = 0, delta = 1, count = 0))
    }
}
