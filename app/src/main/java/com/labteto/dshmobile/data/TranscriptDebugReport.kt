package com.labteto.dshmobile.data

import com.labteto.dshmobile.core.session.AssistantMessageNode
import com.labteto.dshmobile.core.session.ConversationSnapshot
import com.labteto.dshmobile.core.session.UserMessageNode

/**
 * Render the fold's own state as a machine-readable report.
 *
 * This is a diagnostic dump, not chrome: the section banners and `key=value` frames *are* the
 * format, and translating them would make two dumps impossible to compare. It lives outside the
 * `ui` package for that reason — the localisation guard scans UI sources and would be right to
 * object to prose there, and a report builder has no business pretending to be a screen.
 *
 * The report answers the question a screenshot cannot when a transcript looks wrong: what the
 * fold actually holds, in seq order, with each node's provenance (`sourceKind`, `role`) and
 * whether it was classified as injected context rather than a user turn.
 */
internal fun transcriptDebugReport(
    loadingOlder: Boolean,
    loadOlderFailed: Boolean,
    lastPageStopReason: String?,
    conversation: ConversationSnapshot?,
): String = buildString {
    appendLine("=== Paging state ===")
    appendLine("loadingOlder=$loadingOlder failed=$loadOlderFailed")
    appendLine("lastStop=${lastPageStopReason ?: "(never paged)"}")
    appendLine()
    appendLine("=== Transcript nodes ===")
    if (conversation == null) {
        appendLine("(no session open)")
        return@buildString
    }
    appendLine("(count=${conversation.nodes.size} hasMore=${conversation.hasMore})")
    conversation.nodes.forEach { node ->
        when (node) {
            is UserMessageNode -> {
                val kind = node.sourceKind ?: "(none)"
                val role = node.role ?: "(none)"
                val text = node.previewText.take(60)
                appendLine(
                    "  [${node.seq}] user msg: source=$kind role=$role isSystem=${node.isSystem} text=\"$text\"",
                )
            }
            is AssistantMessageNode -> {
                appendLine("  [${node.seq}] assistant msg (turn ${node.turn})")
            }
            else -> appendLine("  [${node.seq}] ${node::class.simpleName}")
        }
    }
}
