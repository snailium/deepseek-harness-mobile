package com.labteto.dshmobile.ui.screens.main

import com.labteto.dshmobile.core.session.AssistantMessageNode
import com.labteto.dshmobile.core.session.ChatNode
import com.labteto.dshmobile.core.session.ToolCallNode

/**
 * One transcript row after turn grouping: either a single node or a whole agentic process.
 *
 * An agentic turn is a monologue: reasoning blocks inside an assistant message, then a run of
 * tool calls. Rendered as separate rows — a "Thinking" disclosure in the bubble and one card per
 * tool below it — the reader gets several disconnected chevrons for what is one unit of work, and
 * expanding the thinking never reveals the tools that followed it. [ProcessItem] is that unit: a
 * single disclosure whose header summarizes the whole process and whose body holds the bubbles
 * and the tool cards, so one tap shows everything the model did in that stretch.
 */
internal sealed interface TranscriptItem {
    val key: String
}

/** A node that stands alone (user messages, pills, errors, plain text replies…). */
internal data class NodeItem(
    override val key: String,
    val node: ChatNode,
) : TranscriptItem

/** Reasoning-bearing assistant messages plus the tool calls that follow them. */
internal data class ProcessItem(
    override val key: String,
    val messages: List<AssistantMessageNode>,
    val tools: List<ToolCallNode>,
) : TranscriptItem {
    val firstSeq: Long get() = messages.firstOrNull()?.seq ?: tools.first().seq
    val lastSeq: Long get() = tools.lastOrNull()?.seq ?: messages.last().seq
}

/**
 * Split the transcript's renderable nodes into process groups and single rows.
 *
 * The rules:
 *  - a [ToolCallNode] opens or joins the open process — tools always belong to the process they
 *    run in, even when no reasoning message preceded them;
 *  - an [AssistantMessageNode] *with a reasoning block* opens or joins the process — its thinking
 *    is the process's first act;
 *  - a text-only assistant message, a user message, or any other node closes the open process and
 *    stands alone, because it is either a result (what the turn *produced*) or a new turn's input.
 *
 * The input is expected to be the already-[rendersContent]-filtered node list; [ProcessItem] keys are derived from the first member's seq,
 * so the transcript can key lazy rows on them without colliding with plain [NodeItem] keys.
 */
internal fun groupTranscriptItems(nodes: List<ChatNode>): List<TranscriptItem> {
    val out = mutableListOf<TranscriptItem>()
    val openMessages = mutableListOf<AssistantMessageNode>()
    val openTools = mutableListOf<ToolCallNode>()
    fun flush() {
        if (openMessages.isEmpty() && openTools.isEmpty()) return
        out += ProcessItem(
            key = "g${openMessages.firstOrNull()?.seq ?: openTools.first().seq}",
            messages = openMessages.toList(),
            tools = openTools.toList(),
        )
        openMessages.clear()
        openTools.clear()
    }
    for (node in nodes) {
        when (node) {
            is ToolCallNode -> openTools += node
            is AssistantMessageNode ->
                if (node.blocks.any { it.kind == "reasoning" }) {
                    openMessages += node
                } else {
                    flush()
                    out += NodeItem(key = node.seq.toString(), node = node)
                }
            else -> {
                flush()
                out += NodeItem(key = node.seq.toString(), node = node)
            }
        }
    }
    flush()
    return out
}
