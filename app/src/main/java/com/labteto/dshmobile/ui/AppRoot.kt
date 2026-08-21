package com.labteto.dshmobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.labteto.dshmobile.BuildConfig
import com.labteto.dshmobile.R
import com.labteto.dshmobile.connection.ConnectionPhase
import com.labteto.dshmobile.ui.components.DsButton
import com.labteto.dshmobile.ui.components.DsButtonVariant
import com.labteto.dshmobile.ui.components.DsDialog
import com.labteto.dshmobile.ui.navigation.ConnectRoute
import com.labteto.dshmobile.ui.navigation.HomeRoute
import com.labteto.dshmobile.ui.navigation.HomeScreen
import com.labteto.dshmobile.ui.navigation.SessionRoute
import com.labteto.dshmobile.ui.navigation.SettingsRoute
import com.labteto.dshmobile.ui.screens.connect.ConnectScreen
import com.labteto.dshmobile.ui.screens.main.ConversationScreen
import com.labteto.dshmobile.ui.screens.settings.SettingsScreen
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType
import com.labteto.dshmobile.ui.theme.DshTheme
import com.labteto.dshmobile.ui.theme.ThemePreference
import com.labteto.dshmobile.update.AvailableUpdate

/**
 * Application root: theme + locale-aware shell, and the single navigation source of truth.
 *
 * One NavHost replaces the three ad-hoc state machines the shell used to keep — the
 * showSettings/showConnect booleans and the PushDestination enum. Connection state decides the
 * current screen: connected → HomeRoute (bottom nav: Chats · Active · Settings), otherwise
 * ConnectRoute. A conversation (SessionRoute) is pushed over the home shell from the Chats list;
 * SettingsRoute is pushed from the connect screen, before a home shell exists.
 */
@Composable
fun AppRoot(viewModel: AppViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val connection by viewModel.connectionState.collectAsStateWithLifecycle()
    val themePreference = remember(settings.themePreference) {
        runCatching { ThemePreference.valueOf(settings.themePreference.uppercase()) }
            .getOrDefault(ThemePreference.SYSTEM)
    }

    val update by viewModel.availableUpdate.collectAsStateWithLifecycle()
    val pendingSession by viewModel.pendingSession.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.checkForUpdate(BuildConfig.VERSION_NAME) }

    DshTheme(preference = themePreference, dynamicColor = settings.dynamicColor) {
        val navController = rememberNavController()

        // Switching harnesses latches here: the connection is dropped and the connect screen is
        // shown regardless of the old phase, until a fresh connect (or a state reset) moves on.
        var forceConnect by rememberSaveable { mutableStateOf(false) }
        val showMain = !forceConnect && (
            connection.phase == ConnectionPhase.CONNECTED ||
                (connection.phase == ConnectionPhase.RECONNECTING && connection.hasConnected)
            )

        // The start destination mirrors the connection state at first composition; a later change
        // is caught by the effect below, which clears the back stack so back can never return to
        // the wrong screen (e.g. Connect after a disconnect, or Home after a switch).
        val startDestination: Any = if (showMain) HomeRoute else ConnectRoute

        LaunchedEffect(showMain) {
            val target: Any = if (showMain) HomeRoute else ConnectRoute
            navController.navigate(target) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
        // A notification tap names the session to open; once connected, push its conversation.
        // It only fires while showMain is true so it can never race the connect navigation into a
        // session the store has not mirrored yet.
        LaunchedEffect(pendingSession, showMain) {
            val sessionId = pendingSession
            if (sessionId != null && showMain) {
                viewModel.consumePendingSession()
                navController.navigate(SessionRoute(sessionId))
            }
        }

        // A fresh connect clears the "switch host" latch, so the next successful handshake returns
        // to the home shell instead of being trapped on the connect screen.
        LaunchedEffect(connection.phase) {
            if (connection.phase == ConnectionPhase.CONNECTING ||
                connection.phase == ConnectionPhase.CONNECTED
            ) {
                forceConnect = false
            }
        }

        fun switchHost() {
            viewModel.disconnect()
            forceConnect = true
        }

        NavHost(navController = navController, startDestination = startDestination) {
            composable<HomeRoute> {
                HomeScreen(
                    hostLabel = connection.host?.displayAddress,
                    onSwitchHost = ::switchHost,
                    onOpenSession = { sessionId -> navController.navigate(SessionRoute(sessionId)) },
                )
            }
            composable<ConnectRoute> {
                ConnectScreen(onOpenSettings = { navController.navigate(SettingsRoute) })
            }
            composable<SessionRoute> { entry ->
                val route = entry.toRoute<SessionRoute>()
                ConversationScreen(
                    sessionId = route.sessionId,
                    hostLabel = connection.host?.displayAddress,
                    onBack = { navController.popBackStack() },
                    onSwitchHost = ::switchHost,
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(onClose = { navController.popBackStack() })
            }
        }

        // Offered over whatever is on screen, and only once per release: dismissing records the
        // version, so the next launch is quiet until there is a newer one.
        update?.let { UpdateDialog(it, onDismiss = { viewModel.dismissUpdate(it.version) }) }
    }
}

/** "There is a newer release" — a link out, not an installer; the app cannot update itself. */
@Composable
private fun UpdateDialog(update: AvailableUpdate, onDismiss: () -> Unit) {
    val colors = DsTheme.colors
    val uriHandler = LocalUriHandler.current
    DsDialog(title = stringResource(R.string.update_available_title), onDismiss = onDismiss) {
        Text(
            stringResource(R.string.update_available_body, update.version, BuildConfig.VERSION_NAME),
            style = DsType.std14,
            color = colors.labelSecondary,
            modifier = Modifier.padding(bottom = DsSpacing.medium),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.small)) {
            DsButton(
                text = stringResource(R.string.update_open),
                onClick = {
                    runCatching { uriHandler.openUri(update.url) }
                    onDismiss()
                },
                variant = DsButtonVariant.Info,
            )
            DsButton(
                text = stringResource(R.string.update_later),
                onClick = onDismiss,
                variant = DsButtonVariant.Ghost,
            )
        }
    }
}
