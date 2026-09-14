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

    /**
     * Whether the row drawn for this item is the one containing the node [seq].
     *
     * Search needs this to scroll: a hit inside a process group belongs to the group's single row
     * rather than to a row of its own, so "which row shows seq N" cannot be answered by comparing
     * seqs alone.
     */
    fun holds(seq: Long): Boolean
}

/** A node that stands alone (user messages, pills, errors, plain text replies…). */
internal data class NodeItem(
    override val key: String,
    val node: ChatNode,
) : TranscriptItem {
    override fun holds(seq: Long): Boolean = node.seq == seq
}

/** Reasoning-bearing assistant messages plus the tool calls that follow them. */
internal data class ProcessItem(
    override val key: String,
    val messages: List<AssistantMessageNode>,
    val tools: List<ToolCallNode>,
) : TranscriptItem {
    val firstSeq: Long get() = messages.firstOrNull()?.seq ?: tools.first().seq
    val lastSeq: Long get() = tools.lastOrNull()?.seq ?: messages.last().seq

    override fun holds(seq: Long): Boolean =
        messages.any { it.seq == seq } || tools.any { it.seq == seq }
}

/**
 * Whether an assistant message carries text the reader must see without expanding anything.
 *
 * A model that ends a step with its reply in the same message as its reasoning (common with the
 * DeepSeek provider) would otherwise have the answer folded into the process group — hidden until
 * the reader expands the "Thought" row. Blank or tool-only text does not count: there is nothing to
 * reveal, and such a message belongs in the group like any other.
 */
internal fun AssistantMessageNode.hasVisibleText(): Boolean = blocks.any { block ->
    block.kind == "text" && !block.text.isNullOrBlank()
}

/**
 * Split the transcript's renderable nodes into process groups and single rows.
 *
 * The rules:
 *  - a [ToolCallNode] opens or joins the open process — tools always belong to the process they
 *    run in, even when no reasoning message preceded them;
 *  - an [AssistantMessageNode] with a reasoning block *and no visible text* opens or joins the
 *    process — pure thinking is the process's first act;
 *  - any other assistant message closes the open process and stands alone: its text (a reply, or a
 *    status line that ends the step) is what the turn *produced*, and it must be readable without
 *    expanding anything. A user message or any other node closes the process for the same reason.
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
                if (node.blocks.any { it.kind == "reasoning" } && !node.hasVisibleText()) {
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
