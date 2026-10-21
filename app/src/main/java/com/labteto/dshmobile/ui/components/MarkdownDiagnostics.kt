package com.labteto.dshmobile.ui.components

import androidx.compose.runtime.mutableStateListOf

/**
 * A temporary in-app read-out of what the Markdown renderer actually receives.
 *
 * Written to close the "a reply's tables and inline code show as raw text" report on a phone,
 * where there is no adb. Every [MarkdownText] call records the text it was handed and the block
 * kinds the parser produced; the chat screen shows the newest entries above the composer.
 *
 * Remove this file, its recording call, and the read-out once the report is closed.
 */
object MarkdownDiagnostics {

    /** One recorded render: the raw head of the text and the blocks it parsed into. */
    data class Entry(
        val length: Int,
        val kinds: List<String>,
        val head: String,
        val at: Long,
    ) {
        /** True when the text looks like Markdown that ought to have parsed into more than prose. */
        val suspicious: Boolean get() =
            ("Table" in kinds).not() &&
                (head.contains("| ---") || head.contains("|---") || head.contains("`"))
    }

    private const val LIMIT = 12

    val entries = mutableStateListOf<Entry>()

    fun record(text: String, kinds: List<String>) {
        // Recomposition is frequent; only a genuinely new render is worth a row.
        val head = text.take(200).replace("\n", "\\n")
        val newest = entries.firstOrNull()
        if (newest != null && newest.length == text.length && newest.head == head && newest.kinds == kinds) return
        entries.add(0, Entry(text.length, kinds, head, System.currentTimeMillis()))
        while (entries.size > LIMIT) entries.removeAt(entries.lastIndex)
    }

    fun clear() {
        entries.clear()
    }
}
