package com.labteto.dshmobile.ui.components

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guard against user-facing English string literals slipping into Compose sources.
 *
 * The Android lint rule for this (`HardcodedText`) only inspects XML layouts, so the app's own
 * lint config explicitly leaves Compose literals "to be caught in review" — and review missed
 * ToolCards.kt's "killed by", "HTTP", "showing X of Y" and friends for every release until 0.8.
 * This test scans the UI sources instead: any sentence-like literal (two or more word tokens)
 * outside a @Preview must be a string resource. Deliberate exceptions are listed below.
 */
class LocalizedStringsTest {

    /** Exact literals that are allowed to stay in code, and why. */
    private val allowedLiterals = setOf(
        // Preview fixtures — never rendered by the app.
        "Nothing running yet",
        "Ask the harness anything, or pick a suggestion below.",
        "Summarize this repo",
        "Run the test suite",
        "Explain a diff",
        "Tool calls",
        "Expanded body",
        "Build the shared component library.",
        "Working through the diff…",
        "Connection lost — retrying…",
        "val a = 1",
        "val a = 2",
        // The harness's own tool vocabulary, deliberately untranslated in every locale
        // (see ToolRowModel's kdoc: a translated verb would stop matching the desktop UI).
        "Tool call",
        // Summary joining an untranslated tool name with a relativised path.
        "\$toolName · \$relative",
        // Markdown.kt block-list regex source and the adjacent-string scanner
        // artifact ("x" to Regex(...)) — regex markup, not user-facing copy.
        "^([\\\\t ]*)([-*+])\\\\s+(.*)$",
        "^([\\\\t ]*)\\\\d+\\\\.\\\\s+(.*)$",
        " to Regex(",
    )

    @Test
    fun uiSourcesDoNotEmbedUserFacingEnglish() {
        val uiRoot = File("src/main/java/com/labteto/dshmobile/ui")
        assertTrue("UI sources not found at $uiRoot", uiRoot.isDirectory)

        val offenders = uiRoot.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .flatMap { file -> offendersIn(file) }
            .toList()

        assertTrue(
            "Sentence-like string literals found in UI sources — move them to strings.xml:\n" +
                offenders.joinToString("\n") { "  ${it.path}:${it.line}: ${it.literal}" },
            offenders.isEmpty(),
        )
    }

    private fun offendersIn(file: File): List<Offense> {
        val lines = file.readLines()
        return lines.mapIndexedNotNull { index, raw ->
            val lineNumber = index + 1
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("//") || line.startsWith("*") ||
                line.startsWith("/*") || line.startsWith("import ") || line.startsWith("package ")
            ) {
                return@mapIndexedNotNull null
            }
            val literal = sentenceLikeLiteral(line) ?: return@mapIndexedNotNull null
            if (literal in allowedLiterals) return@mapIndexedNotNull null
            Offense(file.path, lineNumber, literal)
        }
    }

    /**
     * A quoted literal containing at least two whitespace-separated word tokens of prose.
     *
     * Template expressions (`${...}` and `$name`) are stripped first: a summary like
     * `"${job.kind} · $status"` or a percentage is data, not chrome — it cannot be
     * translated and flagging it would drown the real signal. English *words* around templates
     * ("killed by $signal", "showing $shown of …") still get caught.
     */
    private fun sentenceLikeLiteral(line: String): String? {
        val match = SENTENCE_LITERAL.find(line) ?: return null
        val literal = match.groupValues[1]
        if (literal in allowedLiterals) return null
        // Strip template expressions, then count prose tokens. A literal must contain real
        // whitespace to qualify: "dsh-session-…zip" (a file name) and "42%" never do, while
        // "killed by $signal" and "showing $shown of …" do.
        if (!WHITESPACE.containsMatchIn(literal)) return null
        val plain = literal
            .replace(TEMPLATE_EXPR, " ")
            .replace(TEMPLATE_VAR, " ")
        val wordTokens = plain.split(Regex("\\s+")).count { it.any(Char::isLetter) }
        return if (wordTokens >= 2) literal else null
    }

    private data class Offense(val path: String, val line: Int, val literal: String)

    private companion object {
        // A double-quoted literal, possibly a template, on one line.
        val SENTENCE_LITERAL = Regex("\"([^\"]{3,})\"")
        val TEMPLATE_EXPR = Regex("\\$\\{[^}]*}")
        val TEMPLATE_VAR = Regex("\\$[A-Za-z_][A-Za-z0-9_.]*")
        val WHITESPACE = Regex("\\s")
    }
}
