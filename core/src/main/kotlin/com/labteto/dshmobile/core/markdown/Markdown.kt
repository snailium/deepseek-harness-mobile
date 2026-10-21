package com.labteto.dshmobile.core.markdown

/**
 * Block-level Markdown parser, pure JVM (no Android imports) so the chat
 * renderer's parsing is unit-testable in :core.
 *
 * Supports: fenced code blocks, # through #### headings, bullet and ordered
 * lists (nested by indentation), blockquotes, horizontal rules, GFM pipe
 * tables with per-column alignment, and paragraphs. Inline markup — **bold**,
 * *italic*, ~~strikethrough~~, `code` and [links](url) — is parsed into
 * segments the UI layer styles.
 *
 * The parser is deterministic and line-based; unknown constructs fall through
 * to paragraphs rather than being dropped.
 */

// ---- Blocks -----------------------------------------------------------------

sealed interface MdBlock {
    data class Paragraph(val lines: List<String>) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock

    /**
     * [text] is the item content without its marker; [indent] is nesting depth (0 = top).
     * [checked] is non-null for task-list items (`- [x] done`, `- [ ] todo`).
     * [ordered] says whether the parent list is numbered or bulleted — a renderer needs it to
     * pick the marker, and the parser is the only layer that knows which grammar rule matched.
     */
    data class ListItem(
        val text: String,
        val indent: Int,
        val checked: Boolean? = null,
        val ordered: Boolean = false,
    ) : MdBlock
    data class Blockquote(val lines: List<String>) : MdBlock
    data class Code(val lang: String?, val code: String) : MdBlock
    object HorizontalRule : MdBlock

    enum class Alignment { LEFT, CENTER, RIGHT }

    /**
     * [header] and each row of [rows] are cell strings in source order.
     * [alignments] has one entry per column; missing entries default to LEFT.
     */
    data class Table(
        val header: List<String>,
        val rows: List<List<String>>,
        val alignments: List<Alignment>,
    ) : MdBlock
}

// ---- Inline segments ----------------------------------------------------------

sealed interface InlineSegment {
    data class Plain(val text: String) : InlineSegment

    /**
     * Emphasis carries its inner segments as well as its flat text.
     *
     * `**` is not a leaf: Markdown nests, and a reply routinely writes `` **`blocks` 里有 `Table`** ``
     * — bold around code spans. Treating the emphasis body as opaque text left the backticks
     * visible inside an otherwise bold run, which is exactly how a rendered reply looked like it had
     * never been parsed. [children] is the body re-parsed; [text] remains the flat body for callers
     * that only want the words.
     */
    data class Bold(val text: String, val children: List<InlineSegment> = emptyList()) : InlineSegment
    data class Italic(val text: String, val children: List<InlineSegment> = emptyList()) : InlineSegment
    data class Strikethrough(val text: String, val children: List<InlineSegment> = emptyList()) : InlineSegment
    data class Code(val text: String) : InlineSegment
    data class Link(val text: String, val url: String) : InlineSegment
}

// ---- Parser -------------------------------------------------------------------

private val HEADING_REGEX = Regex("^(#{1,4})\\s+(.*)$")
private val ORDERED_MARKER_REGEX = Regex("(?m)^\\s*\\d+[.)]\\s+")
private val BULLET_MARKER_REGEX = Regex("(?m)^\\s*[-*+]\\s+")
private val HR_REGEX = Regex("^\\s{0,3}([-*_])\\s*(?:\\1\\s*){2,}$")
private val TASK_REGEX = Regex("^\\[([ xX])]\\s+(.*)$")

fun parseMarkdown(markdown: String): List<MdBlock> {
    val parsed = mutableListOf<MdBlock>()
    val lines = markdown.replace("\r\n", "\n").split("\n")
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("```") -> {
                val lang = trimmed.removePrefix("```").trim().ifEmpty { null }
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    code.append(lines[i]).append('\n')
                    i++
                }
                i++ // skip closing fence (or run off the end)
                parsed += MdBlock.Code(lang, code.toString().trimEnd('\n'))
            }
            HR_REGEX.matches(trimmed) -> {
                parsed += MdBlock.HorizontalRule
                i++
            }
            HEADING_REGEX.matches(trimmed) -> {
                val match = HEADING_REGEX.matchEntire(trimmed)!!
                parsed += MdBlock.Heading(match.groupValues[1].length, headingText(match.groupValues[2]))
                i++
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
                while (i < lines.size && isListLine(lines[i], ordered = false)) {
                    parsed += listLine(lines[i], ordered = false)
                    i++
                }
            }
            ORDERED_MARKER_REGEX.containsMatchIn(trimmed) -> {
                while (i < lines.size && isListLine(lines[i], ordered = true)) {
                    parsed += listLine(lines[i], ordered = true)
                    i++
                }
            }
            trimmed.startsWith(">") -> {
                val quote = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                    quote += lines[i].trim().removePrefix(">").trim()
                    i++
                }
                parsed += MdBlock.Blockquote(quote)
            }
            startsTable(lines, i) -> {
                val headerCells = splitTableRow(trimmed)
                val alignments = parseAlignments(splitTableRow(lines[i + 1]), headerCells.size)
                i += 2 // header + separator
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                    rows += splitTableRow(lines[i].trim(), headerCells.size)
                    i++
                }
                parsed += MdBlock.Table(headerCells, rows, alignments)
            }
            line.isBlank() -> i++
            else -> {
                val para = mutableListOf(line.trim())
                i++
                // A table may start immediately after the prose that introduces it, with no blank
                // line between: a reply often writes "same model for comparison:" and then the
                // table. The header line is not special on its own, so the table head has to be
                // recognized here — by looking at the line after it — or the paragraph swallows
                // the whole table and the reply shows raw pipe rows.
                while (i < lines.size && lines[i].isNotBlank() && !isSpecialLine(lines[i]) &&
                    !startsTable(lines, i)
                ) {
                    para += lines[i].trim()
                    i++
                }
                parsed += MdBlock.Paragraph(para)
            }
        }
    }
    return parsed
}

/** `:---` → LEFT, `:--:` → CENTER, `---:` → RIGHT; anything else → LEFT. */
private fun parseAlignments(separatorCells: List<String>, columnCount: Int): List<MdBlock.Alignment> =
    List(columnCount) { index ->
        val cell = separatorCells.getOrNull(index).orEmpty()
        when {
            cell.startsWith(":") && cell.endsWith(":") && cell.length >= 2 -> MdBlock.Alignment.CENTER
            cell.endsWith(":") -> MdBlock.Alignment.RIGHT
            else -> MdBlock.Alignment.LEFT
        }
    }

private fun headingText(raw: String): String = raw.trim().trimEnd('#').trim()

private fun isListLine(line: String, ordered: Boolean): Boolean {
    val trimmed = line.trimStart()
    return if (ordered) ORDERED_MARKER_REGEX.containsMatchIn(trimmed)
    else trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")
}

private fun listLine(line: String, ordered: Boolean): MdBlock.ListItem {
    val indent = line.length - line.trimStart().length
    var content = if (ordered) {
        ORDERED_MARKER_REGEX.replace(line.trim(), "")
    } else {
        line.trimStart().removePrefix("- ").removePrefix("* ").removePrefix("+ ")
    }
    // Task-list items: `- [x] done` or `1. [ ] todo`
    val task = TASK_REGEX.matchEntire(content.trim())
    if (task != null) {
        content = task.groupValues[2].trim()
        return MdBlock.ListItem(
            content,
            indent / 2,
            checked = task.groupValues[1].lowercase() == "x",
            ordered = ordered,
        )
    }
    return MdBlock.ListItem(content.trim(), indent / 2, ordered = ordered)
}

private fun isSpecialLine(line: String): Boolean {
    val trimmed = line.trimStart()
    return trimmed.startsWith("```") ||
        HR_REGEX.matches(trimmed) ||
        HEADING_REGEX.matches(trimmed) ||
        trimmed.startsWith("- ") ||
        trimmed.startsWith("* ") ||
        trimmed.startsWith("+ ") ||
        ORDERED_MARKER_REGEX.containsMatchIn(trimmed) ||
        trimmed.startsWith(">") ||
        (trimmed.startsWith("|") && isTableSeparator(line))
}

/** A separator row is only pipes, dashes, colons and spaces — at least one dash. */
private fun isTableSeparator(line: String): Boolean {
    val trimmed = line.trim()
    if (!trimmed.contains('-')) return false
    return trimmed.replace(Regex("[|:\\-\\s]"), "").isEmpty()
}

/**
 * Whether a table starts at [index]: a pipe row whose next line is a separator row.
 *
 * Shared by the block dispatcher and the paragraph loop. The header line alone is not `special` —
 * a bare pipe row is just prose — so the only thing that identifies a table is the separator
 * beneath it, and every place that has to stop at a table has to look one line ahead.
 */
private fun startsTable(lines: List<String>, index: Int): Boolean =
    lines[index].trimStart().startsWith("|") &&
        lines.getOrNull(index + 1)?.let { isTableSeparator(it) } == true

/**
 * Splits `| a | b | c |` into the three cell strings, trimming each.
 * [expectedCount] pads short rows with empty cells so every row lines up
 * under the header (a harness may truncate trailing empty cells).
 */
private fun splitTableRow(line: String, expectedCount: Int? = null): List<String> {
    val trimmed = line.trim().removePrefix("|").removeSuffix("|")
    var cells = trimmed.split("|").map { it.trim() }
    if (expectedCount != null && cells.size < expectedCount) {
        cells = cells + List(expectedCount - cells.size) { "" }
    }
    return cells
}

// ---- Inline parser -------------------------------------------------------------

fun parseInlineSegments(text: String): List<InlineSegment> {
    val segments = mutableListOf<InlineSegment>()
    val sb = StringBuilder()
    var i = 0
    fun flush() {
        if (sb.isNotEmpty()) {
            segments += InlineSegment.Plain(sb.toString())
            sb.clear()
        }
    }
    while (i < text.length) {
        when {
            text.startsWith("`", i) -> {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    flush()
                    segments += InlineSegment.Code(text.substring(i + 1, end))
                    i = end + 1
                } else {
                    sb.append(text[i]); i++
                }
            }
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1 && end > i + 2) {
                    flush()
                    val body = text.substring(i + 2, end)
                    // Re-parse the body: emphasis wraps other inline markup, it does not suppress it.
                    segments += InlineSegment.Bold(body, parseInlineSegments(body))
                    i = end + 2
                } else {
                    sb.append(text[i]); i++
                }
            }
            text.startsWith("~~", i) -> {
                val end = text.indexOf("~~", i + 2)
                if (end != -1 && end > i + 2) {
                    flush()
                    val body = text.substring(i + 2, end)
                    segments += InlineSegment.Strikethrough(body, parseInlineSegments(body))
                    i = end + 2
                } else {
                    sb.append(text[i]); i++
                }
            }
            text.startsWith("*", i) -> {
                val end = text.indexOf('*', i + 1)
                // A space right after the opener is not an emphasis marker
                // (e.g. "2 * 3" stays literal).
                if (end != -1 && end > i + 1 && text[i + 1] != ' ') {
                    flush()
                    val body = text.substring(i + 1, end)
                    segments += InlineSegment.Italic(body, parseInlineSegments(body))
                    i = end + 1
                } else {
                    sb.append(text[i]); i++
                }
            }
            text.startsWith("[", i) -> {
                val close = text.indexOf("](", i + 1)
                if (close != -1) {
                    val end = text.indexOf(')', close + 2)
                    if (end != -1) {
                        flush()
                        segments += InlineSegment.Link(text.substring(i + 1, close), text.substring(close + 2, end))
                        i = end + 1
                    } else {
                        sb.append(text[i]); i++
                    }
                } else {
                    sb.append(text[i]); i++
                }
            }
            else -> {
                sb.append(text[i]); i++
            }
        }
    }
    flush()
    return segments
}

// ---- URL safety -----------------------------------------------------------------

/**
 * A URL worth opening: http(s) only, so a harness reply cannot smuggle another scheme out.
 * Returns the trimmed URL if it is safe, null otherwise.
 */
fun safeHttpUrl(raw: String): String? {
    val url = raw.trim()
    if (url.isEmpty()) return null
    val lower = url.lowercase()
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) return null
    return url
}
