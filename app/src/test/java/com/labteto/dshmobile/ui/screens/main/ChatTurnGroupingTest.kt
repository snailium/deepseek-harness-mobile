package com.labteto.dshmobile.ui.screens.main

import com.labteto.dshmobile.core.session.AssistantMessageNode
import com.labteto.dshmobile.core.session.ChatBlock
import com.labteto.dshmobile.core.session.ToolCallNode
import com.labteto.dshmobile.core.session.TurnEndNode
import com.labteto.dshmobile.core.session.UserMessageNode
import kotlinx.serialization.json.JsonNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Turn grouping: a stretch of agentic work — reasoning blocks plus the tool calls that follow —
 * must fold into one disclosure row, and anything that is not that work must stand alone.
 *
 * These pin the contract the transcript relies on: one tap on the process header reveals the
 * whole turn's thinking and tools, and rows that are results (plain text replies) or new inputs
 * (user messages) never get swallowed into a process.
 */
class ChatTurnGroupingTest {

    private fun reasoning(seq: Long, text: String = "think") = AssistantMessageNode(
        seq = seq,
        messageId = null,
        turn = 1,
        step = 1,
        blocks = listOf(ChatBlock(kind = "reasoning", text = text)),
    )

    private fun textOnly(seq: Long, text: String = "done") = AssistantMessageNode(
        seq = seq,
        messageId = null,
        turn = 1,
        step = 1,
        blocks = listOf(ChatBlock(kind = "text", text = text)),
    )

    private fun tool(seq: Long, callId: String = "c$seq") =
        ToolCallNode(seq = seq, callId = callId, name = "bash", arguments = "{}", turn = 1, step = 1)

    private fun user(seq: Long) = UserMessageNode(
        seq = seq,
        messageId = null,
        blocks = listOf(ChatBlock("text", "hello")),
        sourceKind = "user",
    )

    @Test
    fun `reasoning followed by tool calls is one process`() {
        val items = groupTranscriptItems(listOf(reasoning(1), tool(2), tool(3)))
        assertEquals(1, items.size)
        val process = items.single() as ProcessItem
        assertEquals(listOf<Long>(1), process.messages.map { it.seq })
        assertEquals(listOf<Long>(2, 3), process.tools.map { it.seq })
        assertEquals("g1", process.key)
    }

    @Test
    fun `a text-only reply stands alone and closes the open process`() {
        val items = groupTranscriptItems(listOf(reasoning(1), tool(2), textOnly(3)))
        assertEquals(2, items.size)
        val process = items[0] as ProcessItem
        assertEquals(listOf<Long>(1), process.messages.map { it.seq })
        assertEquals(listOf<Long>(2), process.tools.map { it.seq })
        val reply = items[1] as NodeItem
        assertEquals(3, reply.node.seq)
    }

    @Test
    fun `tool calls without preceding reasoning still group`() {
        val items = groupTranscriptItems(listOf(tool(1), tool(2), textOnly(3)))
        val process = items[0] as ProcessItem
        assertTrue(process.messages.isEmpty())
        assertEquals(listOf<Long>(1, 2), process.tools.map { it.seq })
    }

    @Test
    fun `a user message closes the process`() {
        val items = groupTranscriptItems(listOf(reasoning(1), tool(2), user(3), reasoning(4), tool(5)))
        assertEquals(3, items.size)
        assertEquals(1, (items[0] as ProcessItem).firstSeq)
        assertEquals(3, (items[1] as NodeItem).node.seq)
        assertEquals(4, (items[2] as ProcessItem).firstSeq)
    }

    @Test
    fun `other rows pass through untouched`() {
        val end = TurnEndNode(seq = 9, turn = 1, reasonKind = "error")
        val items = groupTranscriptItems(listOf(end, textOnly(10)))
        assertEquals(2, items.size)
        assertEquals(end, (items[0] as NodeItem).node)
        assertEquals(10, (items[1] as NodeItem).node.seq)
    }

    @Test
    fun `consecutive reasoning messages merge into one process`() {
        val items = groupTranscriptItems(listOf(reasoning(1), reasoning(2), tool(3)))
        val process = items.single() as ProcessItem
        assertEquals(listOf<Long>(1, 2), process.messages.map { it.seq })
        assertEquals(1, process.firstSeq)
        assertEquals(3, process.lastSeq)
    }

    @Test
    fun `an empty list groups to an empty list`() {
        assertTrue(groupTranscriptItems(emptyList()).isEmpty())
    }

    @Test
    fun `process item firstSeq falls back to the first tool when there is no message`() {
        val items = groupTranscriptItems(listOf(tool(7)))
        val process = items.single() as ProcessItem
        assertNull(process.messages.firstOrNull())
        assertEquals(7, process.firstSeq)
    }
}
