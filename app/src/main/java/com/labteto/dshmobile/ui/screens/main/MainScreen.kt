package com.labteto.dshmobile.ui.screens.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.ui.theme.DsAnimations
import kotlinx.coroutines.launch

/**
 * Discord-style shell:
 *  - swipe right from the LEFT edge (or anywhere on the content) opens the chat-list drawer
 *    (ModalNavigationDrawer's built-in gesture; swipe left on the drawer
 *    content closes it, scrim tap and Back also work)
 *  - swipe left from the RIGHT edge opens the session Details panel
 *  - swipe right anywhere on the open Details panel closes it
 *
 * The details gesture detector only claims the drags it owns (leftward from the right edge
 * band, or any drag on the details area while it is open) and leaves every other horizontal
 * drag unconsumed. ModalNavigationDrawer puts an anchoredDraggable(Horizontal) on the whole
 * surface, so an always-consuming detector here would starve the drawer's open gesture.
 * Horizontal edge drags are axis-orthogonal to the chat list's vertical scroll, so the two
 * never conflict.
 *
 * [hostLabel] is the connected harness (shown in the chrome and the drawer); [onSwitchHost]
 * disconnects and returns to the connect screen, so switching harnesses never requires the
 * Settings detour.
 */
@Composable
fun MainScreen(
    hostLabel: String?,
    onSwitchHost: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var detailsOpen by remember { mutableStateOf(false) }
    // 300dp is most of a narrow phone and none of a tablet; cap to 88% of the screen so a
    // small device still keeps the chat surface visible behind the panel. The edge-swipe
    // detector below shares this value, so both stay in sync by construction.
    val configuration = LocalConfiguration.current
    val detailsWidth = remember(configuration) {
        minOf(300.dp, configuration.screenWidthDp.dp * 0.88f)
    }
    // The panel lives on the physical side its *edge gesture* uses: Alignment.CenterEnd flips by
    // itself, but the detector measures pixels, so it has to know which side that is.
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatListDrawer(
                hostLabel = hostLabel,
                onSwitchHost = onSwitchHost,
                onClose = { scope.launch { drawerState.close() } },
                onOpenSettings = onOpenSettings,
            )
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(detailsOpen, detailsWidth, isRtl) {
                    val width = size.width.toFloat()
                    val edgeBandPx = 28.dp.toPx()
                    val detailsAreaPx = detailsWidth.toPx() * 0.9f
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startX = down.position.x
                        // Claim only gestures this screen handles: drags starting in the edge band
                        // on the panel's side open it; drags starting on the open panel close it.
                        // Everything else (notably swipes from the opposite edge) must stay
                        // unconsumed for the drawer's built-in open gesture.
                        val owned = if (!detailsOpen) {
                            if (isRtl) startX <= edgeBandPx else startX >= width - edgeBandPx
                        } else {
                            if (isRtl) startX >= width - detailsAreaPx else startX <= detailsAreaPx
                        }
                        if (!owned) return@awaitEachGesture

                        var claimed = false
                        awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
                            // While closed, only a drag toward the panel's side belongs to this
                            // screen; a drag toward the drawer's edge is the drawer's to open with.
                            claimed = detailsOpen || (if (isRtl) overSlop > 0f else overSlop < 0f)
                            if (claimed) change.consume()
                        } ?: return@awaitEachGesture
                        if (!claimed) return@awaitEachGesture

                        var totalX = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUpIgnoreConsumed()) break
                            // A deeper scrollable claimed the drag; let it keep it.
                            if (change.isConsumed) break
                            totalX += change.positionChange().x
                            change.consume()
                            val threshold = width * 0.12f
                            val opened = if (isRtl) totalX >= threshold else totalX <= -threshold
                            val closed = if (isRtl) totalX <= -threshold else totalX >= threshold
                            if (!detailsOpen && opened) {
                                detailsOpen = true
                                break
                            }
                            if (detailsOpen && closed) {
                                detailsOpen = false
                                break
                            }
                        }
                    }
                },
        ) {
            ChatScreen(
                hostLabel = hostLabel,
                onOpenDetails = { detailsOpen = true },
                onOpenDrawer = { scope.launch { drawerState.open() } },
                detailsOpen = detailsOpen,
            )

            AnimatedVisibility(
                visible = detailsOpen,
                // Explicit spec: the platform default runs 300ms, which lags behind the drag the
                // panel is usually opened with. The offset sign follows the panel's side: in RTL
                // the sheet lands on the left and slides in from the left.
                enter = slideInHorizontally(DsAnimations.panelSlide) { if (isRtl) -it else it },
                exit = slideOutHorizontally(DsAnimations.panelSlide) { if (isRtl) -it else it },
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                DetailsPanel(
                    onClose = { detailsOpen = false },
                    modifier = Modifier.width(detailsWidth),
                )
            }
        }
    }
}
