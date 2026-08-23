package com.labteto.dshmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.R
import com.labteto.dshmobile.ui.theme.Ds
import com.labteto.dshmobile.ui.theme.DsColors
import com.labteto.dshmobile.ui.theme.DsShapes
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType
import com.labteto.dshmobile.ui.theme.DshTheme

/** A URL worth opening: http(s) only, so a harness reply cannot smuggle another scheme out. */
internal fun safeHttpUrl(raw: String): String? {
    val url = raw.trim()
    if (url.isEmpty()) return null
    val lower = url.lowercase()
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) return null
    return url
}

/**
 * Block-level Markdown renderer.
 *
 * Renders fenced code blocks (with a light syntax highlighter), h1-h4 headings on the display
 * voice, bullet/ordered/nested/task lists, blockquotes, horizontal rules, and real tables with a
 * header band and cell separators. Inline spans cover bold, italic, inline-code chips and links.
 * Anything the tiny parser cannot handle degrades to plain text - it never crashes and never
 * shows raw markers.
 */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val colors = DsTheme.colors
    val blocks = remember(text) { parseMarkdown(text) }
    Column(modifier.fillMaxWidth()) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> DsType.mdH1
                        2 -> DsType.mdH2
                        3 -> DsType.mdH3
                        else -> DsType.mdH4
                    }
                    InlineMarkdown(
                        block.text,
                        style.copy(color = colors.labelPrimary),
                        Modifier.padding(top = headingTop(block.level), bottom = 4.dp),
                    )
                }
                is MdBlock.Paragraph -> InlineMarkdown(
                    block.lines.joinToString(" "),
                    DsType.mdBody.copy(color = colors.labelPrimary),
                    Modifier.fillMaxWidth().padding(bottom = 6.dp),
                )
                is MdBlock.MdList -> MdListBlock(block)
                is MdBlock.Blockquote -> MdBlockquote(block)
                is MdBlock.Code -> CodeBlock(block.lang, block.code)
                is MdBlock.Table -> MdTableBlock(block)
                is MdBlock.HRule -> HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    thickness = 1.dp,
                    color = colors.dividerSoft,
                )
            }
        }
    }
}

private fun headingTop(level: Int) = when (level) {
    1 -> 16.dp
    2 -> 12.dp
    else -> 10.dp
}

// ---- Parser (deterministic, line-based) ------------------------------------

private val HEADING_REGEX = Regex("^(#{1,4})\\s+(.*)$")
private val BULLET_REGEX = Regex("^([\\t ]*)([-*+])\\s+(.*)$")
private val ORDERED_REGEX = Regex("^([\\t ]*)\\d+\\.\\s+(.*)$")
private val TASK_REGEX = Regex("^\\[([ xX])\\]\\s+(.*)$")
private val HR_REGEX = Regex("^\\s*(-{3,}|\\*{3,}|_{3,})\\s*$")

private sealed interface MdBlock {
    data class Paragraph(val lines: List<String>) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class MdList(val items: List<MdListItem>, val ordered: Boolean) : MdBlock
    data class Blockquote(val lines: List<String>) : MdBlock
    data class Code(val lang: String?, val code: String) : MdBlock
    data class Table(val rows: List<MdTableRow>) : MdBlock
    data object HRule : MdBlock
}

/** One list row: its text plus how deeply it is nested and whether it is a checkbox item. */
data class MdListItem(val text: String, val depth: Int, val checked: Boolean?)

data class MdTableRow(val cells: List<String>)

private fun parseMarkdown(markdown: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
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
                i++ // skip closing fence
                blocks += MdBlock.Code(lang, code.toString().trimEnd('\n'))
            }
            HR_REGEX.matches(trimmed) -> {
                blocks += MdBlock.HRule
                i++
            }
            HEADING_REGEX.matches(trimmed) -> {
                val match = HEADING_REGEX.matchEntire(trimmed)!!
                val level = match.groupValues[1].length
                val text = match.groupValues[2].trim().trimEnd('#').trim()
                blocks += MdBlock.Heading(level, text)
                i++
            }
            BULLET_REGEX.matches(trimmed) -> {
                val items = mutableListOf<MdListItem>()
                while (i < lines.size) {
                    val t = lines[i]
                    val bm = BULLET_REGEX.matchEntire(t.trimStart()) ?: break
                    val indent = bm.groupValues[1].length
                    val depth = (indent / 2).coerceAtMost(4)
                    val rawText = bm.groupValues[3].trim()
                    val task = TASK_REGEX.matchEntire(rawText)
                    val checked = task?.let { it.groupValues[1].lowercase() == "x" }
                    val itemText = task?.groupValues?.get(2)?.trim() ?: rawText
                    items += MdListItem(itemText, depth, checked)
                    i++
                }
                blocks += MdBlock.MdList(items, ordered = false)
            }
            ORDERED_REGEX.matches(trimmed) -> {
                val items = mutableListOf<MdListItem>()
                while (i < lines.size) {
                    val t = lines[i]
                    val om = ORDERED_REGEX.matchEntire(t.trimStart()) ?: break
                    val indent = om.groupValues[1].length
                    val depth = (indent / 2).coerceAtMost(4)
                    val rawText = om.groupValues[2].trim()
                    val task = TASK_REGEX.matchEntire(rawText)
                    val checked = task?.let { it.groupValues[1].lowercase() == "x" }
                    val itemText = task?.groupValues?.get(2)?.trim() ?: rawText
                    items += MdListItem(itemText, depth, checked)
                    i++
                }
                blocks += MdBlock.MdList(items, ordered = true)
            }
            trimmed.startsWith(">") -> {
                val quote = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                    quote += lines[i].trim().removePrefix(">").trim()
                    i++
                }
                blocks += MdBlock.Blockquote(quote)
            }
            trimmed.startsWith("|") -> {
                val raw = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                    if (!isTableSeparator(lines[i])) raw += lines[i]
                    i++
                }
                val rows = raw.mapNotNull { line ->
                    val c = line.trim().removePrefix("|").removeSuffix("|")
                    val cells = c.split("|").map { it.trim() }
                    if (cells.isEmpty()) null else MdTableRow(cells)
                }
                if (rows.isNotEmpty()) blocks += MdBlock.Table(rows)
            }
            line.isBlank() -> i++
            else -> {
                val para = mutableListOf(line)
                i++
                while (i < lines.size && lines[i].isNotBlank() && !isSpecialLine(lines[i])) {
                    para += lines[i]
                    i++
                }
                blocks += MdBlock.Paragraph(para)
            }
        }
    }
    return blocks
}

private fun isSpecialLine(line: String): Boolean {
    val trimmed = line.trimStart()
    return trimmed.startsWith("```") ||
        HEADING_REGEX.matches(trimmed) ||
        HR_REGEX.matches(trimmed) ||
        BULLET_REGEX.matches(trimmed) ||
        ORDERED_REGEX.matches(trimmed) ||
        trimmed.startsWith(">") ||
        trimmed.startsWith("|")
}

/** Table separator rows (only pipes, dashes, colons and spaces) are dropped. */
private fun isTableSeparator(line: String): Boolean =
    line.replace(Regex("[|:\\-\\s]"), "").isEmpty()

// ---- Inline rendering ------------------------------------------------------

private sealed interface InlineSegment {
    data class Plain(val text: String) : InlineSegment
    data class Bold(val text: String) : InlineSegment
    data class Italic(val text: String) : InlineSegment
    data class Code(val text: String) : InlineSegment
    data class Link(val text: String, val url: String) : InlineSegment
}

private fun parseInlineSegments(text: String): List<InlineSegment> {
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
                if (end != -1) {
                    flush()
                    segments += InlineSegment.Bold(text.substring(i + 2, end))
                    i = end + 2
                } else {
                    sb.append(text[i]); i++
                }
            }
            text.startsWith("*", i) -> {
                val end = text.indexOf("*", i + 1)
                if (end != -1) {
                    flush()
                    segments += InlineSegment.Italic(text.substring(i + 1, end))
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

/** Renders one line of markdown with bold/italic/code/link spans. */
@Composable
private fun InlineMarkdown(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val colors = DsTheme.colors
    val codeStyle = style.copy(fontFamily = DsType.codeFont)
    val uriHandler = LocalUriHandler.current
    val (result, links) = remember(text, style, codeStyle, colors) {
        buildInlineContent(text, codeStyle, colors)
    }
    ClickableText(
        text = result,
        modifier = modifier,
        style = style,
        onClick = { offset ->
            links.firstOrNull { (range, _) -> offset in range }?.second?.let { target ->
                uriHandler.openUri(target)
            }
        },
    )
}

/**
 * The rendered line plus the link ranges within it, so [InlineMarkdown] can turn a tap into an
 * open. Links are accent + underline, and only http(s) URLs become tappable. Inline code gets a
 * monospace face on a tinted chip background so it reads as code, not body.
 */
private fun buildInlineContent(
    text: String,
    codeStyle: TextStyle,
    colors: DsColors,
): Pair<AnnotatedString, List<Pair<IntRange, String>>> {
    val builder = AnnotatedString.Builder()
    val links = mutableListOf<Pair<IntRange, String>>()
    parseInlineSegments(text).forEach { segment ->
        when (segment) {
            is InlineSegment.Plain -> builder.append(segment.text)
            is InlineSegment.Bold -> builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(segment.text) }
            is InlineSegment.Italic -> builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(segment.text) }
            is InlineSegment.Code -> builder.withStyle(
                SpanStyle(
                    fontFamily = codeStyle.fontFamily,
                    color = colors.labelPrimary,
                    background = colors.inlineCode,
                ),
            ) { append(segment.text) }
            is InlineSegment.Link -> {
                val target = safeHttpUrl(segment.url)
                if (target != null) {
                    val start = builder.length
                    builder.withStyle(
                        SpanStyle(
                            color = colors.accent,
                            textDecoration = TextDecoration.Underline,
                        ),
                    ) { append(segment.text) }
                    links.add((start until builder.length) to target)
                } else {
                    builder.append(segment.text)
                }
            }
        }
    }
    return builder.toAnnotatedString() to links
}
// ---- Block renderers --------------------------------------------------------

@Composable
private fun MdListBlock(block: MdBlock.MdList) {
    val colors = DsTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp, start = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        var orderedIndex = 0
        block.items.forEach { item ->
            val bullet: String
            if (item.checked != null) {
                bullet = if (item.checked) "[x]" else "[ ]"
            } else {
                orderedIndex++
                bullet = when {
                    block.ordered -> "$orderedIndex."
                    item.depth % 2 == 0 -> "\u2022"
                    else -> "- "
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    bullet,
                    style = DsType.mdBody.copy(
                        color = if (item.checked == true) colors.success else colors.labelSecondary,
                    ),
                    textAlign = if (block.ordered) TextAlign.End else TextAlign.Start,
                    modifier = Modifier.width(if (block.ordered) 30.dp else 18.dp)
                        .padding(start = (item.depth * 16).dp),
                )
                Spacer(Modifier.width(6.dp))
                InlineMarkdown(
                    item.text,
                    DsType.mdBody.copy(
                        color = colors.labelPrimary,
                        textDecoration = if (item.checked == true) TextDecoration.LineThrough else null,
                    ),
                    Modifier.weight(1f).padding(top = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun MdBlockquote(block: MdBlock.Blockquote) {
    val colors = DsTheme.colors
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 6.dp),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(1.5.dp))
                .background(colors.citation),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            block.lines.forEach { line ->
                InlineMarkdown(
                    line,
                    DsType.mdBody.copy(color = colors.labelSecondary),
                    Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Fenced code block with a sticky banner (lang · copy) and syntax-highlighted mono lines. */
@Composable
private fun CodeBlock(lang: String?, code: String, modifier: Modifier = Modifier) {
    val colors = DsTheme.colors
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(DsShapes.block)
            .background(colors.codeBlockBg)
            .border(1.dp, colors.borderL2, DsShapes.block),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.codeBlockBanner)
                .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                lang?.let { stringResource(R.string.tool_copy_code_lang, it) } ?: stringResource(R.string.tool_copy),
                style = DsType.caption11Strong.copy(fontFamily = DsType.codeFont, color = colors.labelCaption),
                color = colors.labelCaption,
                modifier = Modifier.weight(1f),
            )
            Icon(
                FeatherIcons.Copy,
                contentDescription = stringResource(R.string.tool_copy_code),
                tint = colors.labelTertiary,
                modifier = Modifier
                    .size(16.dp)
                    .clip(DsShapes.chip)
                    .clickable { clipboard.setText(AnnotatedString(code)) }
                    .padding(2.dp),
            )
        }
        // Long lines scroll horizontally instead of wrapping, so dense code stays intact.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            Text(
                highlightCode(code, lang, colors),
                style = DsType.mdCode.copy(color = colors.labelPrimary),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

/** A real table: header band, aligned columns, cell separators, horizontal scroll when wide. */
@Composable
private fun MdTableBlock(block: MdBlock.Table) {
    val colors = DsTheme.colors
    if (block.rows.isEmpty()) return
    val colCount = block.rows.maxOf { it.cells.size }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.block)
            .background(colors.surfaceSunken)
            .border(1.dp, colors.borderL1, DsShapes.block),
    ) {
        block.rows.forEachIndexed { rowIdx, row ->
            if (rowIdx > 0) {
                HorizontalDivider(thickness = 1.dp, color = colors.dividerSoft)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .background(if (rowIdx == 0) colors.chipSurface else Color(0xFF000000).copy(alpha = 0f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (col in 0 until colCount) {
                    val cell = row.cells.getOrNull(col) ?: ""
                    InlineMarkdown(
                        cell,
                        (if (rowIdx == 0) DsType.mdTableHeader else DsType.mdTable).copy(
                            color = if (rowIdx == 0) colors.labelSecondary else colors.labelPrimary,
                        ),
                        Modifier.widthIn(min = 96.dp).padding(horizontal = 6.dp),
                    )
                }
            }
        }
    }
}
// ---- Lightweight syntax highlighting ---------------------------------------

private val LANG_ALIAS = mapOf(
    "js" to "typescript", "javascript" to "typescript", "ts" to "typescript",
    "sh" to "bash", "shell" to "bash", "zsh" to "bash", "console" to "bash",
    "py" to "python", "yml" to "yaml", "kt" to "kotlin", "html" to "xml",
    "htm" to "xml", "vue" to "xml",
)

private fun normalizeLang(lang: String?): String? = lang?.lowercase()?.let { LANG_ALIAS[it] ?: it }

private enum class Tok { Comment, String, Number, Keyword, Function, Parameter, Type, Plain }

private val STRING_RE = Regex("(\"([^\\\"\\n]*)\"|'([^'\\n]*)')")
private val COMMENT_RE = mapOf<String, Regex>(
    "java" to Regex("//[^\\n]*"),
    "kotlin" to Regex("//[^\\n]*"),
    "typescript" to Regex("//[^\\n]*"),
    "c" to Regex("//[^\\n]*"),
    "python" to Regex("#[^\\n]*"),
    "yaml" to Regex("#[^\\n]*"),
    "json" to Regex("//[^\\n]*"),
    "bash" to Regex("#[^\\n]*"),
    "sql" to Regex("(--[^\\n]*|#\\s[^\\n]*)"),
    "xml" to Regex("<!--[\\s\\S]*?-->"),
)

private val KEYWORDS_BY_LANG = mapOf<String, Set<String>>(
    "kotlin" to setOf("fun", "val", "var", "if", "else", "return", "class", "object", "when", "for", "in", "import", "package", "private", "suspend", "data", "override", "this", "null", "true", "false"),
    "java" to setOf("public", "private", "static", "class", "void", "int", "return", "new", "if", "else", "for", "import", "null", "true", "false", "String"),
    "typescript" to setOf("const", "let", "var", "function", "return", "export", "import", "async", "await", "class", "interface", "type", "if", "else", "null", "true", "false", "this", "extends", "implements"),
    "python" to setOf("def", "return", "import", "from", "class", "if", "else", "elif", "for", "in", "and", "or", "not", "None", "True", "False", "async", "await", "with", "try", "except", "lambda", "yield"),
    "json" to setOf("true", "false", "null"),
    "yaml" to setOf("true", "false", "null", "yes", "no", "on", "off"),
    "bash" to setOf("if", "then", "else", "fi", "for", "in", "do", "done", "function", "return", "echo", "export", "local", "case", "esac"),
    "sql" to setOf("SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "CREATE", "TABLE", "JOIN", "ON", "AND", "OR", "NOT", "NULL", "ORDER", "BY", "GROUP", "HAVING", "LIMIT", "AS", "PRIMARY", "KEY", "INT", "VARCHAR", "TEXT", "BOOLEAN"),
    "xml" to setOf("true", "false", "null"),
)

private fun keywordSet(lang: String?): Set<String> = KEYWORDS_BY_LANG[lang] ?: KEYWORDS_BY_LANG["kotlin"] ?: emptySet()

private fun syntaxColor(lang: String?, token: Tok, colors: DsColors): Color = when (token) {
    Tok.Comment -> colors.labelTertiary
    Tok.String -> Ds.SyntaxString
    Tok.Number -> Ds.SyntaxConstant
    Tok.Keyword -> Ds.SyntaxKeyword
    Tok.Function -> Ds.SyntaxFunction
    Tok.Parameter -> Ds.SyntaxParameter
    Tok.Type -> Ds.SyntaxConstant
    Tok.Plain -> colors.labelPrimary
}

/**
 * Very light syntax highlighter: splits each line on strings/comments, then word-level regexes.
 * Always degrades to plain text; never throws.
 */
private fun highlightCode(code: String, lang: String?, colors: DsColors): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val langKey = normalizeLang(lang)
    val keywords = keywordSet(langKey)
    val commentRe = COMMENT_RE[langKey]
    val lines = code.split("\n")
    lines.forEachIndexed { li, line ->
        // A comment swallows everything after it on the line.
        if (commentRe != null) {
            val m = commentRe.find(line)
            if (m != null) {
                highlightLineSegment(builder, line.substring(0, m.range.first), langKey, keywords, colors)
                builder.withStyle(
                    SpanStyle(color = syntaxColor(langKey, Tok.Comment, colors), fontStyle = FontStyle.Italic),
                ) { append(line.substring(m.range.first)) }
            } else {
                highlightLineSegment(builder, line, langKey, keywords, colors)
            }
        } else {
            highlightLineSegment(builder, line, langKey, keywords, colors)
        }
        if (li < lines.lastIndex) builder.append("\n")
    }
    return builder.toAnnotatedString()
}

private fun highlightLineSegment(
    builder: AnnotatedString.Builder,
    text: String,
    lang: String?,
    keywords: Set<String>,
    colors: DsColors,
) {
    if (text.isEmpty()) return
    // Split out strings first.
    val pieces = mutableListOf<Pair<String, Tok>>()
    var idx = 0
    STRING_RE.findAll(text).forEach { sm ->
        if (sm.range.first > idx) pieces += text.substring(idx, sm.range.first) to Tok.Plain
        pieces += sm.value to Tok.String
        idx = sm.range.last + 1
    }
    if (idx < text.length) pieces += text.substring(idx) to Tok.Plain
    pieces.forEach { (piece, tok) ->
        if (tok == Tok.String) {
            builder.withStyle(SpanStyle(color = syntaxColor(lang, Tok.String, colors))) { append(piece) }
            return@forEach
        }
        // Function calls: name followed by ( is a function color.
        var wordIdx = 0
        FUNC_CALL_RE.findAll(piece).forEach { fm ->
            if (fm.range.first > wordIdx) highlightPlainWords(builder, piece.substring(wordIdx, fm.range.first), lang, keywords, colors)
            val name = fm.groupValues[1]
            builder.withStyle(SpanStyle(color = syntaxColor(lang, Tok.Function, colors))) { append(name); append("(") }
            wordIdx = fm.range.last + 1
        }
        if (wordIdx < piece.length) highlightPlainWords(builder, piece.substring(wordIdx), lang, keywords, colors)
    }
}

private val FUNC_CALL_RE = Regex("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\(")

private fun highlightPlainWords(
    builder: AnnotatedString.Builder,
    text: String,
    lang: String?,
    keywords: Set<String>,
    colors: DsColors,
) {
    var i = 0
    val wordRe = Regex("[a-zA-Z_][a-zA-Z0-9_]*")
    wordRe.findAll(text).forEach { wm ->
        if (wm.range.first > i) builder.append(text.substring(i, wm.range.first))
        val word = wm.value
        val tok = when {
            keywords.contains(word) -> Tok.Keyword
            word.length > 1 && word[0].isUpperCase() -> Tok.Type
            else -> Tok.Plain
        }
        builder.withStyle(SpanStyle(color = syntaxColor(lang, tok, colors))) { append(word) }
        i = wm.range.last + 1
    }
    if (i < text.length) builder.append(text.substring(i))
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun MarkdownTextPreview() {
    DshTheme {
        MarkdownText(
            modifier = Modifier.padding(16.dp),
            text = previewMarkdown,
        )
    }
}

private val previewMarkdown = """
# Heading

A paragraph with **bold**, *italic* and `inline code` plus a [link](https://example.com).

- first level
  - nested item
- [x] done task
- [ ] open task

1. ordered one
2. ordered two

> A quoted thought, wrapped nicely.

```kotlin
fun answer(): Int {
    val value = 42
    return value + 1
}
```

| col a | col b |
| ----- | ----- |
| 1     | 2     |
| 3     | 4     |

---

Done.
""".trimIndent()