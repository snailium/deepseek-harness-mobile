package com.labteto.dshmobile.ui.screens.main

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `workspace/changes` must render as a file list through the shared card, not as a raw JSON
 * disclosure and not as a second, divergent copy of that card.
 *
 * The event carries only `{turn}`, and it used to fall through to the generic disclosure — a block
 * titled `workspace/changes` holding `{"turn": 5}`. The file list comes from a separate route, so
 * this pins the rendering path: that the route is reached, and that the component stays a component.
 *
 * There is no Compose test runtime in this environment, so the contract is pinned against the
 * source: the failures it guards against — a missing branch, a lost fetch, a forked card — are all
 * source-level facts.
 */
class WorkspaceChangesRenderingTest {

    private val nodeItem = File(
        "src/main/java/com/labteto/dshmobile/ui/screens/main/ChatNodeItem.kt",
    ).also { assertTrue("${it.absolutePath} not found", it.isFile) }.readText()

    private val card = File(
        "src/main/java/com/labteto/dshmobile/ui/components/ChangedFilesCard.kt",
    ).also { assertTrue("${it.absolutePath} not found", it.isFile) }.readText()

    @Test
    fun `workspace changes are routed to the card, not dumped as raw json`() {
        assertTrue(
            "workspace/changes must be handled before the generic disclosure",
            nodeItem.contains("\"workspace/changes\""),
        )
        assertTrue(
            "it must render through the shared card",
            nodeItem.contains("ChangedFilesCard("),
        )
        // ...and it must not be silently hidden either: it is real information about the turn.
        val structural = nodeItem.substringAfter("internal val STRUCTURAL_EVENT_TYPES")
            .substringBefore(")")
        assertFalse(
            "workspace/changes is content, not bookkeeping — hiding it loses the turn's file summary notice",
            structural.contains("workspace/changes"),
        )
    }

    @Test
    fun `the row still fetches its summary`() {
        // The card takes data; something has to go and get it. Moving the rendering into a
        // component could have dropped the fetch, leaving a card that never loads.
        assertTrue(
            "the row must still ask for the turn's summary",
            nodeItem.contains("loadWorkspaceChanges("),
        )
        assertTrue(
            "the fetch must be keyed on the row's own event seq, which is what the route addresses",
            nodeItem.contains("node.seq.toInt()"),
        )
    }

    @Test
    fun `the card is a data component, not a second consumer of the store`() {
        // The presentation-layer rule: a component under ui/components may not reach into the
        // session store. If this ever fails, the card stops being previewable and a screen rebase
        // drags the data layer along with it.
        assertFalse(
            "the card must not read the session store",
            card.contains("rememberSessionStore"),
        )
        assertFalse(
            "the card must not import the data layer",
            card.contains("import com.labteto.dshmobile.data."),
        )
        assertTrue(
            "the card should take plain models instead",
            card.contains("ChangedFileRowModel"),
        )
    }

    @Test
    fun `the card follows the code-block recipe`() {
        // It was asked for as a code-block-styled card. A re-skin that swaps these tokens for
        // ad-hoc colours is what "a second framing recipe for the same job" looks like, and it is
        // the thing this check exists to catch.
        assertTrue("body uses the code block background", card.contains("colors.codeBlockBg"))
        assertTrue("header uses the code block banner", card.contains("colors.codeBlockBanner"))
        assertTrue("corners match a block", card.contains("DsShapes.block"))
        assertTrue("edge matches a block", card.contains("colors.borderL1"))
    }

    @Test
    fun `a null list and an empty list are told apart`() {
        // `null` is the host having no summary (normal once a session is disposed); an empty list
        // is the host answering that the turn changed nothing. Collapsing them would either show
        // "unavailable" for a real empty turn or claim a summary that was never served.
        assertTrue(
            "the card must branch on null separately from empty",
            card.contains("files == null ->"),
        )
        assertTrue(
            "and on empty separately from populated",
            card.contains("files.isEmpty() ->"),
        )
    }

    @Test
    fun `uncounted files are labelled rather than shown as zero`() {
        // A binary or oversized file reports 0/0 at the source. Rendering those numbers would claim
        // "nothing changed" about a file the harness could not read.
        assertTrue(
            "the card must render a label when counts are absent",
            card.contains("uncountedLabel"),
        )
        assertTrue(
            "the model must make absence expressible",
            card.contains("val added: Int?,"),
        )
    }
}
