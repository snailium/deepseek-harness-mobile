package com.labteto.dshmobile.ui.screens.main

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A file preview must keep extending as the reader reaches the end of what it has.
 *
 * The preview reads a file in pages, and a page boundary is not the end of a file. The preview
 * used to stop there: the reader scrolled to the end of the loaded page and the scroll stopped
 * with it, and the rest of the document was behind a button at the bottom of the panel. For a
 * document that is read rather than skimmed — a README, a spec — that reads as the file being
 * cut off, which is what it was.
 *
 * There is no Compose test runtime in this environment, so the contract is pinned against the
 * source: the failure it guards against is a *missing* behaviour, which is a source-level fact.
 */
class PreviewAutoExtendTest {

    private val panels = File(
        "src/main/java/com/labteto/dshmobile/ui/screens/main/WorkspacePanels.kt",
    ).also { assertTrue("${it.absolutePath} not found", it.isFile) }.readText()

    @Test
    fun `the preview extends the document when the reader reaches the end of it`() {
        assertTrue(
            "the preview must watch the scroll position to know the reader reached the end",
            panels.contains("snapshotFlow") && panels.contains("maxValue"),
        )
        assertTrue(
            "the preview must ask for the next page when the reader reaches the end",
            panels.contains("load(true)"),
        )
    }

    @Test
    fun `the preview does not name a page size of its own`() {
        // The host caps a page by its own configured size and serves that when the request says
        // nothing about one. A client that names its own size is guessing: too small and it cuts
        // a document short at an arbitrary line, too large and the host refuses outright. The
        // preview used to send 500 lines of its own, which is how a 600-line README lost its
        // last hundred.
        assertTrue(
            "the preview must read through the paging helper, which is what leaves the size to the host",
            panels.contains("readWorkspacePage"),
        )
        assertFalse(
            "the preview must not send a page size of its own",
            Regex("WorkspaceFileRange\\([^)]*\\b\\d{2,}\\b").containsMatchIn(panels),
        )
    }
}
