package com.labteto.dshmobile.ui.screens.main

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `workspace/changes` must render as a file list, not as a raw JSON disclosure.
 *
 * The event carries only `{turn}`, and it used to fall through to the generic disclosure — a block
 * titled `workspace/changes` holding `{"turn": 5}`. The file list comes from a separate route, so
 * this pins the rendering contract rather than the fetch, which is covered where it lives.
 *
 * There is no Compose test runtime in this environment, so the contract is pinned against the
 * source: the failure it guards against is a *missing* branch, which is a source-level fact.
 */
class WorkspaceChangesRenderingTest {

    private val nodeItem = File(
        "src/main/java/com/labteto/dshmobile/ui/screens/main/ChatNodeItem.kt",
    ).also { assertTrue("${it.absolutePath} not found", it.isFile) }.readText()

    @Test
    fun `workspace changes are labelled rather than dumped as raw json`() {
        // The event carries only `{turn}`. Falling through to the generic disclosure drew a block
        // titled `workspace/changes` containing `{"turn": 5}`.
        assertTrue(
            "workspace/changes must be handled before the generic disclosure",
            nodeItem.contains("\"workspace/changes\""),
        )
        assertTrue(
            "it must render a label, not the raw payload",
            nodeItem.contains("R.string.workspace_changes"),
        )
        // ...and it must not be silently hidden either: it is real information about the turn.
        val structural = nodeItem.substringAfter("internal val STRUCTURAL_EVENT_TYPES")
            .substringBefore(")")
        assertFalse(
            "workspace/changes is content, not bookkeeping — hiding it loses the turn's file summary notice",
            structural.contains("workspace/changes"),
        )
    }
}
