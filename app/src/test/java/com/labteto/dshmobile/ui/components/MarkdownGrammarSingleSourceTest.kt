package com.labteto.dshmobile.ui.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Markdown grammar has exactly one implementation, and it lives in `:core`.
 *
 * This guard exists because it was broken for months. The fork's own renderer rewrite (`0dff6c4`)
 * deleted the app-side inline parser and moved the grammar to `core/markdown`; a later merge of
 * `ymolliks/deepseek-harness-mobile v0.19.0` (`3caf9c4`) brought that parser back, and the follow-up
 * feature commit (`15dbbc9`) edited *that* copy instead of deleting it. From then on the block
 * parser (one copy, in core) and the inline parser (two copies, the app using the stale one) drifted
 * apart: a table fix in core worked, an inline-code fix in core did nothing, and a reply's
 * `` `code` `` kept rendering with its backticks because the app was running a grammar that predated
 * the fork's own improvements to it.
 *
 * A second copy is not a style problem, it is a correctness problem: whichever copy the renderer
 * happens to call is the one that decides what a reply looks like, and a fix to the other is not a
 * fix. So the rule is enforced rather than documented — the merge that reintroduces a local grammar
 * fails here instead of failing on a phone weeks later.
 *
 * Scalars only, no Compose and no `:core` symbols, so it is a pure source assertion.
 */
class MarkdownGrammarSingleSourceTest {

    /** The module that owns the grammar. Every other module must import, never redeclare. */
    private val owner = "core/src/main/kotlin/com/labteto/dshmobile/core/markdown/Markdown.kt"

    /**
     * The declarations that make up the inline grammar.
     *
     * Each is a name the renderer consumes. If any is declared outside [owner], some module is
     * carrying its own grammar: the renderer may still compile — that is what makes it dangerous —
     * but it will be parsing with the wrong rules.
     */
    private val inlineGrammarDeclarations = listOf(
        "fun parseInlineSegments",
        "interface InlineSegment",
    )

    /** The block-level entry point, for the same reason: one parser, one place. */
    private val blockGrammarDeclarations = listOf(
        "fun parseMarkdown(",
        "sealed interface MdBlock",
    )

    /** Kotlin sources under a module's main source set. */
    private fun kotlinSources(module: String): List<File> {
        // The Gradle test working directory is the module directory, but a whole-repo run may put it
        // at the root; accept both so the guard cannot be skipped by how it was invoked.
        val fromRoot = File(module)
        val root = when {
            fromRoot.isDirectory -> fromRoot
            File(module.substringAfter('/')).isDirectory -> File(module.substringAfter('/'))
            else -> return emptyList()
        }
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .onEach { require(it.isFile) }
            .toList()
    }

    @Test
    fun `the grammar declarations are present exactly once, in core`() {
        val sources = kotlinSources("core/src/main/kotlin") + kotlinSources("app/src/main/java")
        assertTrue("expected to find the app sources", kotlinSources("app/src/main/java").isNotEmpty())

        (inlineGrammarDeclarations + blockGrammarDeclarations).forEach { declaration ->
            val declaring = sources.filter { file ->
                file.readLines().any { line ->
                    val trimmed = line.trim()
                    // A declaration, not a call or an import: the line names it and does not qualify
                    // or reference it. Comments are matched too, which is deliberate — a commented
                    // -out copy is a copy waiting to be uncommented.
                    trimmed.contains(declaration) &&
                        !trimmed.startsWith("import ") &&
                        !trimmed.startsWith("*") &&
                        !trimmed.startsWith("//")
                }
            }.map { it.path.replace('\\', '/').substringAfterLast('/') + " @ " + it.path.replace('\\', '/') }
                .filter { owner !in it.substringAfter(" @ ") }

            assertEquals(
                "`$declaration` is declared outside $owner. The Markdown grammar has one home; a " +
                    "second copy means the renderer and the parser can disagree — see this test's " +
                    "class documentation for the merge that caused exactly that.",
                emptyList<String>(),
                declaring,
            )
        }
    }

    @Test
    fun `the renderer imports the grammar instead of declaring it`() {
        val renderer = kotlinSources("app/src/main/java")
            .firstOrNull { it.name == "Markdown.kt" }
            ?: throw AssertionError("the Compose renderer Markdown.kt is missing")

        val source = renderer.readText()
        assertTrue(
            "the renderer must import parseInlineSegments from :core",
            source.contains("import com.labteto.dshmobile.core.markdown.parseInlineSegments"),
        )
        assertTrue(
            "the renderer must import InlineSegment from :core",
            source.contains("import com.labteto.dshmobile.core.markdown.InlineSegment"),
        )
        assertTrue(
            "the renderer must import parseMarkdown from :core",
            source.contains("import com.labteto.dshmobile.core.markdown.parseMarkdown"),
        )
    }
}
