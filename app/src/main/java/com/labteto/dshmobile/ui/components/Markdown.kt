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
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
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
import com.labteto.dshmobile.core.markdown.InlineSegment
import com.labteto.dshmobile.core.markdown.MdBlock
import com.labteto.dshmobile.core.markdown.parseInlineSegments
import com.labteto.dshmobile.core.markdown.parseMarkdown
import com.labteto.dshmobile.core.markdown.safeHttpUrl
import com.labteto.dshmobile.ui.theme.Ds
import com.labteto.dshmobile.ui.theme.DsColors
import com.labteto.dshmobile.ui.theme.DsShapes
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType
import com.labteto.dshmobile.ui.theme.DshTheme

/**
 * Block-level Markdown renderer: fenced code blocks (with syntax highlighting), #-#### headings,
 * bullet and ordered lists (nested, including task-list items), blockquotes, horizontal rules,
 * GFM pipe tables with per-column alignment, and paragraphs with inline **bold**, *italic*,
 * ~~strikethrough~~, `code` chips and clickable [links](https://example.com).
 *
 * Parsing lives in :core (`com.labteto.dshmobile.core.markdown`) and is unit-tested there.
 * Only http(s) URLs become tappable; all other schemes render as plain text.
 */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val colors = DsTheme.colors
    val blocks = remember(text) { parseMarkdown(text) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> DsType.mdH1
                        2 -> DsType.mdH2
                        3 -> DsType.mdH3
                        else -> DsType.mdH4
                    }
                    InlineMarkdown(block.text, style.copy(color = colors.labelPrimary), Modifier.padding(top = 10.dp))
                }
                is MdBlock.Paragraph -> InlineMarkdown(
                    block.lines.joinToString(" "),
                    DsType.mdBody.copy(color = colors.labelPrimary),
                    Modifier.fillMaxWidth(),
                )
                is MdBlock.ListItem -> MdListItemRow(block)
                is MdBlock.Blockquote -> MdBlockquote(block)
                is MdBlock.Code -> CodeBlock(block.lang, block.code)
                is MdBlock.HorizontalRule -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .height(1.dp)
                        .background(colors.borderL1),
                )
                is MdBlock.Table -> MarkdownTable(block)
            }
        }
    }
}

// ---- Inline rendering ------------------------------------------------------

/** Renders one line of markdown with bold/italic/strikethrough/code/link spans. */
@Composable
private fun InlineMarkdown(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val colors = DsTheme.colors
    val uriHandler = LocalUriHandler.current
    val codeStyle = style.copy(
        fontFamily = DsType.codeFont,
        color = colors.labelPrimary,
    )
    val (result, links) = remember(text, style, codeStyle, colors) {
        buildInlineContent(text, codeStyle, colors)
    }
    if (links.isEmpty()) {
        BasicText(result, modifier = modifier, style = style)
        return
    }
    ClickableText(
        text = result,
        modifier = modifier,
        style = style,
    ) { offset ->
        val index = result.getStringAnnotations("link", offset, offset).firstOrNull()
            ?.item?.toIntOrNull()
            ?: -1
        val url = links.getOrNull(index) ?: return@ClickableText
        runCatching { uriHandler.openUri(url) }
    }
}

private fun buildInlineContent(
    text: String,
    codeStyle: TextStyle,
    colors: DsColors,
): Pair<AnnotatedString, List<String>> {
    val builder = AnnotatedString.Builder()
    val links = mutableListOf<String>()
    parseInlineSegments(text).forEach { segment ->
        when (segment) {
            is InlineSegment.Plain -> builder.append(segment.text)
            is InlineSegment.Bold -> builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(segment.text) }
            is InlineSegment.Italic -> builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(segment.text) }
            is InlineSegment.Strikethrough -> builder.withStyle(
                SpanStyle(textDecoration = TextDecoration.LineThrough),
            ) { append(segment.text) }
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
                    val index = links.size
                    val start = builder.length
                    links += target
                    builder.withStyle(SpanStyle(color = colors.accent, textDecoration = TextDecoration.Underline)) {
                        append(segment.text)
                    }
                    val end = builder.length
                    builder.addStringAnnotation("link", index.toString(), start, end)
                } else {
                    // Non-http(s) scheme: render the link text as plain, non-tappable.
                    builder.append(segment.text)
                }
            }
        }
    }
    return builder.toAnnotatedString() to links
}

// ---- Block renderers --------------------------------------------------------

@Composable
private fun MdListItemRow(block: MdBlock.ListItem) {
    val colors = DsTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = (block.indent * 20).dp),
        verticalAlignment = Alignment.Top,
    ) {
        val checked = block.checked
        if (checked != null) {
            // Task-list item: render a checkbox indicator.
            Text(
                if (checked) "☑" else "☐",
                style = DsType.mdBody.copy(
                    color = if (checked) colors.success else colors.labelSecondary,
                ),
                modifier = Modifier.width(20.dp),
            )
        } else {
            Text(
                "\u2022",
                style = DsType.mdBody.copy(color = colors.labelSecondary),
                modifier = Modifier.width(18.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        InlineMarkdown(
            block.text,
            DsType.mdBody.copy(
                color = colors.labelPrimary,
                textDecoration = if (block.checked == true) TextDecoration.LineThrough else null,
            ),
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun MdBlockquote(block: MdBlock.Blockquote) {
    val colors = DsTheme.colors
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 2.dp)) {
        Box(
            Modifier
                .width(2.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(1.dp))
                .background(colors.citation),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            block.lines.forEach { line ->
                InlineMarkdown(line, DsType.mdSmall.copy(color = colors.labelTertiary), Modifier.fillMaxWidth())
            }
        }
    }
}

/** GFM pipe table: header row, divider, body rows; horizontally scrollable when wide. */
@Composable
private fun MarkdownTable(block: MdBlock.Table) {
    val colors = DsTheme.colors
    Column(
        Modifier
            .clip(DsShapes.block)
            .border(1.dp, colors.borderL1, DsShapes.block)
            .horizontalScroll(rememberScrollState()),
    ) {
        TableRow(block.header, header = true, alignments = block.alignments)
        Box(Modifier.width(400.dp).height(1.dp).background(colors.borderL1))
        block.rows.forEach { row ->
            TableRow(row, header = false, alignments = block.alignments)
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, header: Boolean, alignments: List<MdBlock.Alignment>) {
    val colors = DsTheme.colors
    Row(Modifier.padding(vertical = 6.dp)) {
        cells.forEachIndexed { index, cell ->
            val alignment = alignments.getOrNull(index) ?: MdBlock.Alignment.LEFT
            Box(
                Modifier
                    .widthIn(min = 60.dp, max = 240.dp)
                    .padding(horizontal = 10.dp),
                contentAlignment = when (alignment) {
                    MdBlock.Alignment.LEFT -> Alignment.CenterStart
                    MdBlock.Alignment.CENTER -> Alignment.Center
                    MdBlock.Alignment.RIGHT -> Alignment.CenterEnd
                },
            ) {
                InlineMarkdown(
                    cell,
                    if (header) DsType.mdSmall.copy(fontWeight = FontWeight.SemiBold, color = colors.labelPrimary)
                    else DsType.mdSmall.copy(color = colors.labelSecondary),
                    Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ---- Code block with syntax highlighting ------------------------------------

/** Fenced code block with a banner (lang · copy) and a syntax-highlighted body. */
@Composable
private fun CodeBlock(lang: String?, code: String, modifier: Modifier = Modifier) {
    val colors = DsTheme.colors
    val clipboard = LocalClipboardManager.current
    val langLabel = stringResource(
        if (lang != null) R.string.tool_copy_code_lang else R.string.tool_copy,
        lang ?: "",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(DsShapes.block)
            .background(colors.codeBlockBg)
            .border(1.dp, colors.borderL1, DsShapes.block),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.codeBlockBanner)
                .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                langLabel,
                style = DsType.caption11Strong.copy(fontFamily = DsType.codeFont, color = colors.labelCaption),
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = stringResource(R.string.tool_copy_code),
                tint = colors.labelTertiary,
                modifier = Modifier
                    .size(16.dp)
                    .clip(DsShapes.chip)
                    .clickable { clipboard.setText(AnnotatedString(code)) }
                    .padding(2.dp),
            )
        }
        val highlighted = remember(code, lang) { highlightCode(code, lang) }
        Text(
            highlighted,
            style = DsType.mdCode,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        )
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

private fun keywordSet(lang: String?): Set<String> = KEYWORDS_BY_LANG[lang] ?: emptySet()

/**
 * Very light syntax highlighter: splits each line on strings/comments, then word-level regexes.
 * Always degrades to plain text; never throws. Returns an AnnotatedString with per-token colors.
 */
private fun highlightCode(code: String, lang: String?): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val langKey = normalizeLang(lang)
    val keywords = keywordSet(langKey)
    val commentRe = COMMENT_RE[langKey]
    val lines = code.split("\n")
    lines.forEachIndexed { li, line ->
        if (li > 0) builder.append('\n')
        highlightLine(builder, line, langKey, keywords, commentRe)
    }
    return builder.toAnnotatedString()
}

private fun highlightLine(
    builder: AnnotatedString.Builder,
    text: String,
    lang: String?,
    keywords: Set<String>,
    commentRe: Regex?,
) {
    var i = 0
    while (i < text.length) {
        // Comment swallows the rest of the line.
        if (commentRe != null) {
            val cm = commentRe.find(text, i)
            if (cm != null && cm.range.first == i) {
                builder.withStyle(SpanStyle(color = Ds.SyntaxComment)) { append(cm.value) }
                return
            }
        }
        // String literal.
        val sm = STRING_RE.find(text, i)
        if (sm != null && sm.range.first == i) {
            builder.withStyle(SpanStyle(color = Ds.SyntaxString)) { append(sm.value) }
            i += sm.value.length
            continue
        }
        // Word token.
        val wordMatch = Regex("[A-Za-z_][A-Za-z0-9_]*").find(text, i)
        if (wordMatch != null && wordMatch.range.first == i) {
            val word = wordMatch.value
            when {
                word in keywords -> builder.withStyle(SpanStyle(color = Ds.SyntaxKeyword)) { append(word) }
                text.getOrNull(i + word.length) == '(' ->
                    builder.withStyle(SpanStyle(color = Ds.SyntaxFunction)) { append(word) }
                word[0].isUpperCase() ->
                    builder.withStyle(SpanStyle(color = Ds.SyntaxConstant)) { append(word) }
                else -> builder.append(word)
            }
            i += word.length
            continue
        }
        // Number.
        val numMatch = Regex("\\d+\\.?\\d*").find(text, i)
        if (numMatch != null && numMatch.range.first == i) {
            builder.withStyle(SpanStyle(color = Ds.SyntaxConstant)) { append(numMatch.value) }
            i += numMatch.value.length
            continue
        }
        // Single non-special character.
        builder.append(text[i])
        i++
    }
}

// ---- Preview -----------------------------------------------------------------

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun MarkdownTextPreview() {
    DshTheme {
        MarkdownText(
            text = """
                # Heading

                A paragraph with **bold**, *italic*, ~~struck~~ and `inline code` plus a [link](https://example.com).

                - first item
                  - nested item
                - [x] done task
                - [ ] open task

                1. ordered one
                2. ordered two

                > A quoted thought.

                ```kotlin
                fun answer(): Int {
                    val value = 42 // the magic number
                    return value + 1
                }
                ```

                | Name  | Age | Score |
                |:------|:---:|------:|
                | Ada   | 36  | 99.5  |
                | Alan  | 41  | 87.0  |

                ---

                After the rule.
            """.trimIndent(),
            modifier = Modifier.padding(16.dp),
        )
    }
}
