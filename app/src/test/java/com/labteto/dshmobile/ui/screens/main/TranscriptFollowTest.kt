package com.labteto.dshmobile.ui.screens.main

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The transcript must keep following the tail, and the two ways it stopped are both source-level.
 *
 * A reverse-layout list was assumed to make tail-following free — newest is index 0, so an appended
 * row lands where the list is already anchored. That only holds while the viewport sits exactly on
 * index 0, and adding content is itself what moves it off: a tool call appends a row and the
 * streaming row grows line by line. Nothing then brought it back, so a running turn stopped
 * following and the reader was left behind a "Jump to newest" button.
 *
 * There is no Compose test runtime in this environment, so the contract is pinned against the
 * source. That is the right target anyway: both failures were *missing* code, not wrong geometry.
 *
 * **Upstream replaced this fork's implementation of the same fix** (upstream PR #57, plus the
 * follow-up that keeps following after a pick lands at the tail). The behaviour this file guards is
 * unchanged; the shape it is pinned to is now upstream's rather than ours:
 *
 * - The follow is a *state*, not a live position read. Reading the position on every content
 *   change is exactly what the anchoring breaks: a row landing below moves the first visible index
 *   to 1 on its own, and reading that as "the reader scrolled away" switches the follow off at the
 *   moment it is needed. Only a scroll session — a drag, or the fling that follows one — moves the
 *   reader on purpose, so the position is read when a scroll session *ends*.
 * - The scroll to the tail is `requestScrollToItem(0)`, not `scrollToItem(0)`: the former is the
 *   one call that overrides the key anchoring for the coming measure. It schedules no animation,
 *   which is also why the follow must not animate.
 * - The follow is skipped while a scroll is in progress, so a reader who is actively dragging is
 *   not yanked forward when a row lands.
 */
class TranscriptFollowTest {

    private val transcript = File(
        "src/main/java/com/labteto/dshmobile/ui/screens/main/ChatTranscript.kt",
    ).also { assertTrue("${it.absolutePath} not found", it.isFile) }.readText()

    /**
     * The follow machinery on its own: from the tail test to the paging code that follows it. The
     * search jump's own animated scroll sits *above* this region and the history pick's jump below
     * it, so neither can be mistaken for the follow.
     */
    private val followRegion: String = transcript
        .substringAfter("fun atTail()")
        .substringBefore("// Reaching the oldest loaded message")

    @Test
    fun `the tail is followed, not merely reported`() {
        // A position test alone only reports; there has to be an effect that acts on it, keyed on
        // the row count so it fires when the transcript grows rather than on every scroll.
        assertTrue(
            "tail-following must be keyed on the item count, or it fires on scroll rather than on new content",
            followRegion.contains("LaunchedEffect(itemCount"),
        )
        assertTrue(
            "the transcript must scroll back to index 0 when a row is appended while at the tail",
            followRegion.contains("requestScrollToItem(0)"),
        )
    }

    @Test
    fun `following is conditional on already being at the tail`() {
        // The reader who scrolled back to read history must not be yanked forward every time the
        // agent emits a tool call.
        assertTrue(
            "the follow must be gated on whether the reader is following",
            followRegion.contains("if (followTail &&"),
        )
        assertTrue(
            "a reader who is mid-drag must not be pulled forward by a row landing",
            followRegion.contains("!listState.isScrollInProgress"),
        )
    }

    @Test
    fun `following is intent, not a live position read`() {
        // The anchoring moves the first visible index on its own when a row lands below, so the
        // position is only the answer when the reader has just stopped scrolling. That is the
        // whole of the signal: a scroll session, and where the list came to rest at the end of it.
        assertTrue(
            "the follow must be a state that a scroll session updates, not a position read on every change",
            followRegion.contains("var followTail by remember(sessionId)"),
        )
        assertTrue(
            "the follow state must be updated when a scroll session ends",
            followRegion.contains("snapshotFlow { listState.isScrollInProgress }"),
        )
    }

    @Test
    fun `the follow is not animated`() {
        // An animated scroll would visibly trail the streaming reply it is chasing.
        assertFalse("the follow must not animate", followRegion.contains("animateScrollToItem"))
    }
}
