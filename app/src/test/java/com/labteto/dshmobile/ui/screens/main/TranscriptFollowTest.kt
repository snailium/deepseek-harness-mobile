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
 */
class TranscriptFollowTest {

    private val transcript = File(
        "src/main/java/com/labteto/dshmobile/ui/screens/main/ChatTranscript.kt",
    ).also { assertTrue("${it.absolutePath} not found", it.isFile) }.readText()

    @Test
    fun `the tail is followed, not merely reported`() {
        // `atNewest` alone only drives the button's visibility. There has to be an effect that
        // acts on it, keyed on the row count so it fires when the transcript grows.
        assertTrue(
            "the transcript must scroll back to index 0 when a row is appended while at the tail",
            transcript.contains("listState.scrollToItem(0)"),
        )
        assertTrue(
            "tail-following must be keyed on the item count, or it fires on scroll rather than on new content",
            transcript.contains("LaunchedEffect(itemCount"),
        )
    }

    @Test
    fun `following is conditional on already being at the tail`() {
        // The reader who scrolled back to read history must not be yanked forward every time the
        // agent emits a tool call.
        assertTrue(
            "tail-following must be guarded by atNewest, or it fights a reader reading history",
            transcript.contains("val followTail = atNewest.value"),
        )
        assertTrue(
            "the guard must actually short-circuit the effect",
            transcript.contains("!followTail) return@LaunchedEffect"),
        )
    }

    @Test
    fun `the follow is not animated`() {
        // An animated scroll would visibly trail the streaming reply it is chasing.
        val follow = transcript.substringAfter("val followTail = atNewest.value")
            .substringBefore("LaunchedEffect(itemCount")
            .plus(transcript.substringAfter("LaunchedEffect(itemCount").substringBefore("// Reaching the oldest"))
        assertTrue("the follow should scroll directly", follow.contains("scrollToItem(0)"))
        assertFalse("the follow must not animate", follow.contains("animateScrollToItem"))
    }
}
