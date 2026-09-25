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
    fun `the round controls are 28dp and the subagent pill matches their height`() {
        // The two controls that are still circles draw their own 28dp circle by hand.
        assertEquals(
            "ComposerRoundButton lost its 28dp circle",
            1,
            occurrences(bodyOf(composer, "ComposerRoundButton"), ".size(28.dp)"),
        )
        assertEquals("PermissionChip lost its 28dp circle", 1, occurrences(bodyOf(composer, "PermissionChip"), ".size(28.dp)"))
        // The subagent chip is a pill now, so it is sized by height rather than by a square. It
        // still has to be 28dp tall or the row steps.
        val chip = bodyOf(composer, "SubagentChip")
        assertEquals("SubagentChip is not 28dp tall", 1, occurrences(chip, ".height(28.dp)"))
        assertFalse(
            "SubagentChip must not be a fixed square any more — it has to fit a glyph and a count",
            chip.contains(".size(28.dp)"),
        )
    }

    @Test
    fun `the subagent pill shows a glyph and a count, separated`() {
        val chip = bodyOf(composer, "SubagentChip")
        // Both halves are the point of the pill: the glyph says what the control opens and the
        // count says how many are running. Dropping either leaves a control that cannot be read.
        assertTrue("the pill must draw a group glyph", chip.contains("FeatherIcons.Users"))
        assertTrue("the pill must draw the count", chip.contains("count.toString()"))
        // The gap is what keeps the glyph from touching the digit.
        assertTrue(
            "the pill must space its glyph from its count",
            chip.contains("Arrangement.spacedBy(DsSpacing.tiny)"),
        )
        // A pill, not a circle.
        assertTrue("the pill must be fully rounded", chip.contains("DsShapes.pillFull"))
    }

    @Test
    fun `the subagent chip precedes the permission trigger and keeps the row's gap`() {
        val row = bodyOf(composer, "Composer")
        val chip = row.indexOf("SubagentChip(")
        val permission = row.indexOf("PermissionChip(")
        assertTrue("SubagentChip must precede PermissionChip in the row", chip in 1 until permission)
        // As a pill it no longer cancels the row's gap: butting a pill against a circle reads as a
        // collision, and the shape difference already groups the two.
        assertFalse(
            "the pill must not cancel the row's gap any more",
            row.contains("Modifier.offset(x = DsSpacing.compact)"),
        )
    }

    @Test
    fun `the permission trigger still hides itself when there is no permission service`() {
        // The pill no longer offsets against this, but the early return is still load-bearing: a
        // dead control would be worse than none.
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

    @Test
    fun `the preset chip is gone from the chrome but still reachable from the details panel`() {
        val topBar = source("ChatTopBar.kt")
        // A preset is chosen once per session and then left alone, so it does not earn a permanent
        // row above the conversation. It used to have one, and the row cost vertical space in
        // exactly the band a phone has least of.
        assertFalse(
            "the top bar must not draw a preset chip any more — it belongs in the details panel",
            topBar.contains("agentPresetLabel"),
        )
        assertFalse("the top bar must not offer a preset route", topBar.contains("onOpenPresets"))

        // Removing the chrome copy must not remove the only route: the details panel carries the
        // pill and the sheet it opens. Without this the preset would become unreachable entirely.
        val details = source("DetailsPanel.kt")
        assertTrue(
            "the details panel must still render a preset pill",
            details.contains("agentPresetLabel(it, presets)"),
        )
        assertTrue(
            "the details panel must still open the preset sheet",
            details.contains("DetailsSheet.Presets"),
        )
        assertTrue(
            "the details panel must refresh the roster before opening the sheet, or it lists stale presets",
            details.contains("store.refreshAgentPresets()"),
        )
    }

    @Test
    fun `no dead chrome imports are left behind`() {
        val topBar = source("ChatTopBar.kt")
        // `Dashboard` was the preset chip's glyph and `Groups` the subagent chip's; both chips have
        // left the chrome, so both imports are dead weight that reads as "still in use".
        assertFalse("unused Dashboard import", topBar.contains("icons.outlined.Dashboard"))
        assertFalse("unused Groups import", topBar.contains("icons.outlined.Groups"))
        // `KeyboardArrowDown` must stay: ModelChip still draws it.
        assertTrue("ModelChip still needs its chevron import", topBar.contains("icons.filled.KeyboardArrowDown"))
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
