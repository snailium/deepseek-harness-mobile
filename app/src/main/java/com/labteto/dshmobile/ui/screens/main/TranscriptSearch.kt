package com.labteto.dshmobile.ui.screens.main

import com.labteto.dshmobile.core.session.AssistantMessageNode
import com.labteto.dshmobile.core.session.ChatNode
import com.labteto.dshmobile.core.session.UserMessageNode
import java.util.Locale

/** Where a hit landed, so assistive tech can say what kind of message it found. */
internal enum class MatchKind { User, Answer, Thinking }

/**
 * One message containing the query, identified by the seq of the node it lives in.
 *
 * The seq — not an index into the rendered transcript — is the handle on purpose: the transcript
 * groups and filters nodes before laying them out, so a node index means nothing to the list, while
 * a seq can be resolved to a row by whoever owns the layout.
 */
internal data class TranscriptMatch(val seq: Long, val kind: MatchKind)

/**
 * Every message whose prose contains [query], in transcript order.
 *
 * What is in scope is the conversation as the reader can read it: what they sent, what the model
 * answered, and the model's thinking — which is collapsed behind a disclosure row by default, and
 * is exactly the text a reader is otherwise unable to find again. Thinking counts even though it is
 * folded away, because the alternative is a search that silently skips the longest text in the
 * session.
 *
 * What is out of scope is everything the harness did rather than said: tool calls and their results
 * (`ToolCallNode` / `ToolResultNode`, and the `tool-call` / `tool-result` blocks inside an assistant
 * message). Those are arguments and file contents — matching them buries the prose the reader is
 * looking for under JSON, and the Trajectory tab is where that material is read.
 *
 * One message yields at most one match even when both its text and its thinking hit: prev/next
 * should walk messages, not fragments, or a single long answer looks like a run of separate hits.
 * Prose wins the tie, since it is what is visible without expanding anything.
 *
 * Matching is a case-insensitive substring test, and a blank or all-whitespace query matches
 * nothing — an empty field is not a search for everything.
 */
internal fun findTranscriptMatches(nodes: List<ChatNode>, query: String): List<TranscriptMatch> {
    val needle = query.trim().lowercase(Locale.ROOT)
    if (needle.isEmpty()) return emptyList()

    fun hit(text: String?): Boolean = text?.lowercase(Locale.ROOT)?.contains(needle) == true

    val out = mutableListOf<TranscriptMatch>()
    for (node in nodes) {
        when (node) {
            // The bubble's own text, not the raw blocks: `displayText` is what the transcript
            // actually shows, so search finds exactly what the reader can see.
            is UserMessageNode -> {
                if (hit(node.displayText())) out += TranscriptMatch(node.seq, MatchKind.User)
            }

            is AssistantMessageNode -> {
                val text = node.blocks.any { it.kind == "text" && hit(it.text) }
                val thinking = node.blocks.any { it.kind == "reasoning" && hit(it.text) }
                when {
                    text -> out += TranscriptMatch(node.seq, MatchKind.Answer)
                    thinking -> out += TranscriptMatch(node.seq, MatchKind.Thinking)
                }
            }

            // Structure, not prose: turn boundaries, tool traffic, todos, errors.
            else -> Unit
        }
    }
    return out
}

/**
 * The next cursor position after a step of [delta] through [count] matches, wrapping at both ends.
 *
 * Wrapping rather than clamping is what makes the arrows usable in a short session: with three
 * matches, "next" from the last one should return to the first, not go dead.
 */
internal fun stepMatchCursor(cursor: Int, delta: Int, count: Int): Int {
    if (count <= 0) return 0
    return ((cursor + delta) % count + count) % count
}
