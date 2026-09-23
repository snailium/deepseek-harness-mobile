package com.labteto.dshmobile.ui.screens.main

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The composer's trailing controls are one row of round buttons, and the subagent chip has to be
 * one of them rather than a chip from somewhere else.
 *
 * That is a geometry contract, not a taste: `/`, the paperclip, the permission trigger and the
 * subagent chip all have to measure 28dp across, or the row steps and the odd one out reads as a
 * control that does not belong. There is no device in this environment to render the row on, so
 * the contract is pinned against the source instead — the failure it guards against (someone
 * retuning one button's size, or reintroducing a pill) is exactly a source change.
 */
class ComposerTrailingRowTest {

    private val composer = source("Composer.kt")

    private fun source(name: String): String {
        // Unit tests run from the module directory, so `src/main` is relative to the working dir.
        val file = File("src/main/java/com/labteto/dshmobile/ui/screens/main/$name")
        assertTrue("$name not found at ${file.absolutePath}", file.isFile)
        return file.readText()
    }

    /**
     * The body of a top-level declaration, from its `fun name(` to the closing brace.
     *
     * The parameter list is skipped first: a default value like `onOpenSubagents: () -> Unit = {}`
     * carries braces of its own, and treating the first `{` as the body would end the scan inside
     * the signature.
     */
    private fun bodyOf(source: String, name: String): String {
        val start = source.indexOf("fun $name(")
        assertTrue("fun $name( not found", start >= 0)
        // Walk the parameter list to its matching close paren.
        var i = source.indexOf('(', start)
        var parens = 0
        while (i < source.length) {
            when (source[i]) {
                '(' -> parens++
                ')' -> {
                    parens--
                    if (parens == 0) break
                }
            }
            i++
        }
        var depth = 0
        i = source.indexOf('{', i)
        val from = i
        while (i < source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(from, i + 1)
                }
            }
            i++
        }
        error("unbalanced braces in $name")
    }

    @Test
    fun `every trailing control is a 28dp circle`() {
        // The three fixed-size controls, each of which draws its own 28dp circle by hand.
        assertEquals(
            "ComposerRoundButton lost its 28dp circle",
            1,
            occurrences(bodyOf(composer, "ComposerRoundButton"), ".size(28.dp)"),
        )
        assertEquals("PermissionChip lost its 28dp circle", 1, occurrences(bodyOf(composer, "PermissionChip"), ".size(28.dp)"))
        assertEquals("SubagentChip is not 28dp across", 1, occurrences(bodyOf(composer, "SubagentChip"), ".size(28.dp)"))
    }

    @Test
    fun `the subagent chip cancels exactly the row's gap so it touches the permission trigger`() {
        val row = bodyOf(composer, "Composer")
        // The row's gap and the offset that cancels it have to be the same token. They are today
        // (`compact`, 8dp); pinning the *relationship* rather than the number is what makes this
        // test survive a spacing retune that keeps the two buttons touching.
        assertTrue("the action row's gap is no longer DsSpacing.compact", row.contains("Arrangement.spacedBy(DsSpacing.compact)"))
        assertTrue(
            "the subagent chip no longer offsets by the row's own gap, so a gap reappears between it and the permission trigger",
            row.contains("Modifier.offset(x = DsSpacing.compact)"),
        )
        // The chip is placed before the permission trigger, not after it.
        val chip = row.indexOf("SubagentChip(")
        val permission = row.indexOf("PermissionChip(")
        assertTrue("SubagentChip must precede PermissionChip in the row", chip in 1 until permission)
    }

    @Test
    fun `the gap is only cancelled when the permission trigger is actually drawn`() {
        val row = bodyOf(composer, "Composer")
        // `PermissionChip` returns early when `select == null`, so with no permission service the
        // chip has no left-hand neighbour. Cancelling a gap against an absent sibling would push
        // it into the context ring instead.
        assertTrue(
            "the offset must be conditional on permissions != null",
            row.contains("if (permissions != null)"),
        )
        assertTrue(
            "PermissionChip must still draw nothing when there is no permission select",
            bodyOf(composer, "PermissionChip").contains("if (select == null) return"),
        )
    }

    @Test
    fun `the chip is hidden when there are no subagents`() {
        val row = bodyOf(composer, "Composer")
        assertTrue(
            "the subagent chip must be guarded by subagentCount > 0, or it opens an empty sheet",
            row.contains("if (subagentCount > 0)"),
        )
    }

    @Test
    fun `the subagent chip is gone from the tab row`() {
        val topBar = source("ChatTopBar.kt")
        assertFalse(
            "the tab row must not draw a subagent chip any more — it moved to the composer",
            topBar.contains("onOpenSubagents"),
        )
        assertFalse("the tab row must not take a subagent count", topBar.contains("subagentCount"))
    }

    private fun occurrences(haystack: String, needle: String): Int {
        var count = 0
        var index = haystack.indexOf(needle)
        while (index >= 0) {
            count++
            index = haystack.indexOf(needle, index + needle.length)
        }
        return count
    }
}
