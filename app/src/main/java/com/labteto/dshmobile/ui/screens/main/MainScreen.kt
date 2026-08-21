package com.labteto.dshmobile.ui.screens.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.labteto.dshmobile.ui.theme.DsAnimations

/**
 * The connected shell: the chat surface with two full-screen pushed destinations.
 *
 * The chat list and the session details used to be a modal drawer and an edge-swipe panel —
 * neither is an iPhone pattern, and HIG is explicit that sidebars don't belong on a phone. Both
 * are now pushed screens over the chat, like Messages and Settings navigate: Sessions slides in
 * from the leading edge, Details from the trailing edge (both mirrored in RTL), each with its own
 * back affordance and a system-back pop. The chat stays composed underneath, so scroll, draft and
 * composer survive a round trip.
 *
 * [hostLabel] is the connected harness (shown in the Sessions chrome); [onSwitchHost]
 * disconnects and returns to the connect screen, so switching harnesses never requires the
 * Settings detour.
 */
@Composable
fun MainScreen(
    hostLabel: String?,
    onSwitchHost: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var pushed by rememberSaveable { mutableStateOf<PushDestination?>(null) }
    // Back pops the pushed screen before it can reach the chat root or exit the app.
    BackHandler(enabled = pushed != null) { pushed = null }

    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // Sessions is the list (leading side), Details the inspector (trailing side); the slide
    // direction follows the side the screen belongs to, so the stack reads as a push in both
    // reading directions.
    Box(Modifier.fillMaxSize()) {
        ChatScreen(
            hostLabel = hostLabel,
            onOpenSessions = { pushed = PushDestination.Sessions },
            onOpenDetails = { pushed = PushDestination.Details },
        )

        AnimatedVisibility(
            visible = pushed == PushDestination.Sessions,
            enter = slideInHorizontally(DsAnimations.panelSlide) { width -> if (isRtl) width else -width },
            exit = slideOutHorizontally(DsAnimations.panelSlide) { width -> if (isRtl) width else -width },
            modifier = Modifier.fillMaxSize(),
        ) {
            SessionsScreen(
                hostLabel = hostLabel,
                onSwitchHost = onSwitchHost,
                onOpenSettings = onOpenSettings,
                onClose = { pushed = null },
            )
        }

        AnimatedVisibility(
            visible = pushed == PushDestination.Details,
            enter = slideInHorizontally(DsAnimations.panelSlide) { width -> if (isRtl) -width else width },
            exit = slideOutHorizontally(DsAnimations.panelSlide) { width -> if (isRtl) -width else width },
            modifier = Modifier.fillMaxSize(),
        ) {
            DetailsScreen(onClose = { pushed = null })
        }
    }
}

/** What is pushed over the chat surface. */
private enum class PushDestination { Sessions, Details }
