package com.labteto.dshmobile.ui.components

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.labteto.dshmobile.ui.theme.DsThemeTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * An inline code span must reach the reader as code, wherever it sits.
 *
 * The mono face alone was not enough of a signal — in a run of CJK body text a code span read as
 * ordinary words — so the span carries the block-code surface as a background too. Both had to
 * survive being nested in emphasis: `**`blocks`里有 `Table`**` is an ordinary thing for a reply to
 * write, and emitting the emphasis and the code as two overlapping spans left the mono face subject
 * to merge order. The span is now flattened instead.
 */
class InlineCodeSpanTest {

    private val mono = FontFamily.Monospace
    private val colors = DsThemeTokens.dark

    private fun render(text: String, style: TextStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp)) =
        buildInlineContent(text, style.copy(fontFamily = mono, background = colors.codeBlockBg), colors) {}

    private fun spanOver(result: androidx.compose.ui.text.AnnotatedString, needle: String): SpanStyle {
        val at = result.text.indexOf(needle)
        check(at >= 0) { "`$needle` is not in ${result.text}" }
        // The narrowest span covering the needle is the one that decides how it is drawn.
        return result.spanStyles
            .filter { it.start <= at && it.end >= at + needle.length }
            .minByOrNull { it.end - it.start }
            ?.item ?: SpanStyle()
    }

    @Test
    fun plainCodeSpanIsMonospacedOnTheCodeSurface() {
        val out = render("use `foo` here")
        val span = spanOver(out, "foo")
        assertEquals(mono, span.fontFamily)
        assertEquals(colors.codeBlockBg, span.background)
    }

    @Test
    fun codeSpanInsideBoldKeepsMonoAndBackgroundAndStaysBold() {
        val out = render("**`blocks` 里有 `Table`**")
        assertEquals("blocks 里有 Table", out.text)
        val span = spanOver(out, "blocks")
        assertEquals("a code span inside bold must keep the mono face", mono, span.fontFamily)
        assertEquals(colors.codeBlockBg, span.background)
        assertEquals("and it must still read as bold", FontWeight.Bold, span.fontWeight)
    }

    @Test
    fun codeSpanInsideItalicKeepsMono() {
        val out = render("*see `x`*")
        val span = spanOver(out, "x")
        assertEquals(mono, span.fontFamily)
        assertEquals(FontStyle.Italic, span.fontStyle)
    }

    @Test
    fun textOutsideCodeSpanIsNotMonospaced() {
        val out = render("plain and `code`")
        val span = spanOver(out, "plain")
        assertNotNull(out.text)
        assertEquals("plain text must not inherit the code face", null, span.fontFamily)
    }
}
