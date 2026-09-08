package com.labteto.dshmobile.ui.screens.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.R
import com.labteto.dshmobile.core.session.ConversationSnapshot
import com.labteto.dshmobile.ui.components.DsButton
import com.labteto.dshmobile.ui.components.DsButtonSize
import com.labteto.dshmobile.ui.components.DsButtonVariant
import com.labteto.dshmobile.ui.components.EmptyHero
import com.labteto.dshmobile.ui.components.skeleton
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType

/**
 * How far below the viewport bottom the last visible item may extend and still count as
 * "near bottom" (button hidden). 120px ≈ 60dp on a 2x-density device — roughly four to five
 * lines of text. A long final message no longer keeps the button hidden until the reader
 * scrolls past its top; a slight scroll-up reveals it.
 */
private const val NEAR_BOTTOM_THRESHOLD_PX = 120

/**
 * The conversation itself.
 *
 * Auto-scroll only follows the tail when the reader is already there — scrolling back through a
 * long transcript while a turn streams should not keep yanking the view down. Scrolling the other
 * way pages history in without a button.
 */
@Composable
internal fun ChatTranscript(
    conversation: ConversationSnapshot?,
    loading: Boolean,
    loadingOlder: Boolean,
    loadOlderFailed: Boolean,
    context: ChatNodeContext,
    listState: LazyListState,
    onLoadOlder: () -> Unit,
    onSuggest: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    /** Observes user scrolls (e.g. to fold the chrome); null keeps the list plain. */
    scrollConnection: NestedScrollConnection? = null,
) {
    // Only the nodes that draw something: a zero-height item still costs its 4dp gap, and a turn's
    // worth of structural events stacks those gaps into a blank band under the chrome.
    val nodes = remember(conversation?.nodes) {
        conversation?.nodes.orEmpty().filter { it.rendersContent() }
    }
    // Then fold consecutive reasoning messages and tool calls into process rows, so one disclosure
    // covers a whole stretch of agentic work instead of a chevron per block (see ChatTurnGrouping).
    val rows = remember(nodes) { groupTranscriptItems(nodes) }
    val hasMore = conversation?.hasMore == true
    val itemCount = rows.size + if (hasMore) 1 else 0
    val sessionId = conversation?.sessionId

    // The streaming tail re-keys/re-derives every row while a turn streams; animating each item
    // on every tick is what made the transcript feel rubbery. Only the *settled* state earns the
    // layout animation — the moment the turn stops, rows animate into place once.
    val streaming = conversation?.running == true

    // Both keyed on the session so a freshly opened one starts from a clean assumption rather than
    // inheriting the previous transcript's position — and so the collector always writes to the
    // state the composition is currently reading.
    var wasNearBottom by remember(sessionId) { mutableStateOf(true) }
    LaunchedEffect(listState, sessionId) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@snapshotFlow true
            if (info.totalItemsCount == 0) return@snapshotFlow true
            // "Near bottom" means the last visible item's bottom edge is within a small threshold
            // of the viewport bottom. A long final message used to keep the button hidden until
            // the reader scrolled past its top; now scrolling up just a few lines (60dp ≈ 4-5
            // lines of text) shows the button.
            val gapBelow = last.offset + last.size - info.viewportSize.height
            gapBelow <= NEAR_BOTTOM_THRESHOLD_PX
        }.collect { wasNearBottom = it }
    }

    // Keyed on the *newest* seq, not the item count, so only growth at the tail moves the view.
    // Counting items conflated two opposite events: a turn streaming in at the bottom, which should
    // follow, and a page of history arriving at the top, which must not — asking for older messages
    // and being thrown back to the newest one is the opposite of what the tap meant. The paging row
    // appearing and disappearing changed the count too, which moved the view for no reason at all.
    //
    // The LazyColumn is bottom-anchored (Arrangement.Bottom), so new content at the tail pushes
    // the viewport up automatically — no scroll needed. An explicit animateScrollToItem here
    // fought that natural shift and produced a visible flash on every rebuild tick. We only jump
    // on session switch, where the list state is stale from the previous transcript.
    var lastSession by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(sessionId) {
        if (itemCount == 0) return@LaunchedEffect
        val switched = sessionId != lastSession
        lastSession = sessionId
        if (switched) listState.scrollToItem(itemCount - 1)
    }

    if (loading) {
        TranscriptSkeleton(modifier)
        return
    }

    val colors = DsTheme.colors
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (scrollConnection != null) {
                        Modifier.nestedScroll(scrollConnection)
                    } else {
                        Modifier
                    },
                ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            // Bottom-anchored: a transcript shorter than the viewport belongs above the composer, not
            // pinned under the tab strip with the empty half below it. 10dp between rows gives the
            // message cards room to breathe — 4dp was tuned for flat text and reads cramped around
            // bubbles.
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
        ) {
            if (hasMore) {
                item(key = "load-older") {
                    LoadOlderRow(
                        loading = loadingOlder,
                        failed = loadOlderFailed,
                        onRetry = onLoadOlder,
                    )
                }
            }
            if (nodes.isEmpty()) {
                item(key = "empty") {
                    // A blank session should say what it is for: the chips prefill the composer, so a
                    // first-time user gets a concrete next step instead of an empty page.
                    EmptyHero(
                        headline = stringResource(R.string.chat_empty_title),
                        subtitle = stringResource(R.string.chat_empty_hint),
                        chips = listOf(
                            stringResource(R.string.chat_suggest_summarize),
                            stringResource(R.string.chat_suggest_tests),
                            stringResource(R.string.chat_suggest_diff),
                        ),
                        onChipClick = onSuggest,
                    )
                }
            } else {
                items(rows, key = { it.key }) { item ->
                    Column(if (streaming) Modifier else Modifier.animateItem()) {
                        when (item) {
                            is NodeItem -> ChatNodeItem(node = item.node, context = context)
                            is ProcessItem -> ProcessGroupItem(
                                item = item,
                                context = context,
                                // The group is live while it is the tail of a running turn: reasoning
                                // and calls stream in, results are folded into their cards, and the
                                // moment the tail moves past the group (final text, a new turn) it
                                // collapses to its summary unless the reader opened it by hand.
                                live = conversation?.running == true &&
                                    item.lastSeq == nodes.lastOrNull()?.seq,
                            )
                        }
                    }
                }
            }
        }

        // Floating "scroll to bottom" button: visible when the reader is not at the tail.
        val scope = rememberCoroutineScope()
        AnimatedVisibility(
            visible = !wasNearBottom && rows.isNotEmpty(),
            enter = fadeIn() + androidx.compose.animation.slideInVertically { it / 4 },
            exit = fadeOut() + androidx.compose.animation.slideOutVertically { it / 4 },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 12.dp),
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.bgLayer3)
                    .border(1.dp, colors.borderL1)
                    .clickable {
                        scope.launch { listState.scrollToItem(itemCount - 1, Int.MAX_VALUE) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.labelPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/**
 * Head of the transcript while more history exists. Shows a "Load more" button that the reader
 * taps to fetch the next page; no automatic paging is triggered by scrolling.
 */
@Composable
private fun LoadOlderRow(
    loading: Boolean,
    failed: Boolean,
    onRetry: () -> Unit,
) {
    val colors = DsTheme.colors
    when {
        loading -> Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = colors.labelTertiary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.chat_loading_older),
                style = DsType.caption11,
                color = colors.labelTertiary,
            )
        }

        else -> DsButton(
            text = if (failed) stringResource(R.string.chat_load_older_retry) else stringResource(R.string.chat_load_older),
            onClick = onRetry,
            variant = DsButtonVariant.Ghost,
            size = DsButtonSize.Small,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Placeholder bubbles while a session's history loads, instead of an empty white screen. */
@Composable
private fun TranscriptSkeleton(modifier: Modifier = Modifier) {
    val colors = DsTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listOf(0.55f, 0.9f, 0.75f, 0.4f).forEach { fraction ->
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(14.dp)
                    .skeleton(colors.bgLayer2, colors.hover),
            )
        }
    }
}
