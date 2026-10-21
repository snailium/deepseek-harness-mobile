package com.labteto.dshmobile.ui.screens.main

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A background job's events must render as a job row, not as a raw JSON disclosure.
 *
 * The harness emits `job/registered` and `job/settled` for every background job. Unclassified,
 * they reached the transcript as a block titled with the event name — `job/registered` — holding
 * the payload verbatim. That is the compatibility contract for an event type from the *future*;
 * for a type shipping today it is just the event name printed where a sentence should be.
 *
 * There is no Compose test runtime in this environment, so the contract is pinned against the
 * source: the failure it guards against is a *missing* branch, which is a source-level fact.
 */
class JobRowRenderingTest {

    private val nodeItem = File(
        "src/main/java/com/labteto/dshmobile/ui/screens/main/ChatNodeItem.kt",
    ).also { assertTrue("${it.absolutePath} not found", it.isFile) }.readText()

    private val visibility = File(
        "src/main/java/com/labteto/dshmobile/ui/screens/main/ChatNodeVisibility.kt",
    ).also { assertTrue("${it.absolutePath} not found", it.isFile) }.readText()

    @Test
    fun `a job node is drawn as a job row`() {
        assertTrue(
            "the job node must be drawn by its own row, not left to the generic disclosure",
            nodeItem.contains("is JobNode -> JobRow(node)"),
        )
        assertTrue(
            "the row must name the job in words rather than by its event name",
            nodeItem.contains("R.string.job_row_started") && nodeItem.contains("R.string.job_row_settled"),
        )
    }

    @Test
    fun `a job's outcome is a state, not a word in a paragraph`() {
        // The settled event carries the status; the row has to read it, because a failed job that
        // looks like a finished one is worse than no row at all.
        assertTrue(
            "the row must read the job's status",
            nodeItem.contains("JobStatus.fromWire"),
        )
        assertTrue(
            "the status must reach the row's state, so the glyph can carry the outcome",
            nodeItem.contains("DisclosureState.Error") && nodeItem.contains("DisclosureState.Stopped"),
        )
    }

    @Test
    fun `a job row is content, not bookkeeping`() {
        val structural = nodeItem.substringAfter("internal val STRUCTURAL_EVENT_TYPES")
            .substringBefore(")")
        assertFalse(
            "a job's start and outcome are content — hiding them loses the conversation",
            structural.contains("job/"),
        )
        assertTrue(
            "the job node must render",
            visibility.contains("is JobNode -> true"),
        )
    }
}
