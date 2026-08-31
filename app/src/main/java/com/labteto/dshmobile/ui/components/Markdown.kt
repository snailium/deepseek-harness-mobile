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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.core.markdown.InlineSegment
import com.labteto.dshmobile.core.markdown.MdBlock
import com.labteto.dshmobile.core.markdown.parseInlineSegments
import com.labteto.dshmobile.core.markdown.parseMarkdown
import com.labteto.dshmobile.ui.theme.DsColors
import com.labteto.dshmobile.ui.theme.DsShapes
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType
import com.labteto.dshmobile.ui.theme.DshTheme

/**
 * Block-level Markdown renderer: fenced code blocks, #-#### headings, bullet and
 * ordered lists (nested), blockquotes, horizontal rules, GFM pipe tables with
 * per-column alignment, and paragraphs with inline **bold**, *italic*,
 * ~~strikethrough~~, `code` chips and clickable [links](https://example.com).
 * Parsing lives in :core (`com.labteto.dshmobile.core.markdown`) and is unit-tested there.
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
        androidx.compose.foundation.text.BasicText(result, modifier = modifier, style = style)
        return
    }
    ClickableText(
        text = result,
        modifier = modifier,
        style = style,
    ) { annotation ->
        val index = try { annotation.toInt() } catch (e: NumberFormatException) { -1 }
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
                SpanStyle(fontFamily = codeStyle.fontFamily, color = codeStyle.color),
            ) { append(segment.text) }
            is InlineSegment.Link -> {
                val index = links.size
                val start = builder.length
                links += segment.url
                builder.withStyle(SpanStyle(color = colors.accent, textDecoration = TextDecoration.Underline)) {
                    append(segment.text)
                }
                val end = builder.length
                builder.addStringAnnotation("link", index.toString(), start, end)
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
        Text(
            "•",
            style = DsType.mdBody.copy(color = colors.labelSecondary),
            modifier = Modifier.width(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        InlineMarkdown(block.text, DsType.mdBody.copy(color = colors.labelPrimary), Modifier.weight(1f))
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
            .fillMaxWidth()
            .clip(DsShapes.block)
            .border(1.dp, colors.borderL1, DsShapes.block)
            .horizontalScroll(rememberScrollState()),
    ) {
        TableRow(block.header, header = true, alignments = block.alignments)
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.borderL1))
        block.rows.forEach { row ->
            TableRow(row, header = false, alignments = block.alignments)
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, header: Boolean, alignments: List<MdBlock.Alignment>) {
    val colors = DsTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        cells.forEachIndexed { index, cell ->
            val alignment = alignments.getOrNull(index) ?: MdBlock.Alignment.LEFT
            val textAlign = when (alignment) {
                MdBlock.Alignment.LEFT -> TextAlign.Start
                MdBlock.Alignment.CENTER -> TextAlign.Center
                MdBlock.Alignment.RIGHT -> TextAlign.End
            }
            Box(
                Modifier
                    .weight(1f)
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

/** Fenced code block with a sticky banner (lang · copy) and a mono pre. */
@Composable
private fun CodeBlock(lang: String?, code: String, modifier: Modifier = Modifier) {
    val colors = DsTheme.colors
    val clipboard = LocalClipboardManager.current
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
                lang?.let { "$it · copy" } ?: "copy",
                style = DsType.caption11Strong.copy(fontFamily = DsType.codeFont, color = colors.labelCaption),
                color = colors.labelCaption,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = "Copy code",
                tint = colors.labelTertiary,
                modifier = Modifier
                    .size(16.dp)
                    .clip(DsShapes.chip)
                    .clickable { clipboard.setText(AnnotatedString(code)) }
                    .padding(2.dp),
            )
        }
        Text(
            code,
            style = DsType.mdCode,
            color = colors.labelPrimary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

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
                - second item

                1. ordered one
                2. ordered two

                > A quoted thought.

                ```kotlin
                val answer = 42
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
