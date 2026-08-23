package com.labteto.dshmobile.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.labteto.dshmobile.R
import com.labteto.dshmobile.connection.ConnectionUiState
import com.labteto.dshmobile.ui.components.FeatherIcons
import com.labteto.dshmobile.ui.screens.main.ChatsScreen
import com.labteto.dshmobile.ui.screens.settings.SettingsScreen
import com.labteto.dshmobile.ui.theme.DsTheme

/**
 * The home destinations. Active is gone: the per-session live state (approvals, questions,
 * goal, queue, context) lives in the chat itself, and session metadata (model, preset, export,
 * host) moved into a details sheet reachable from a session row - so the home page is the two
 * things a companion app actually needs: the work (Chats) and the app (Settings).
 */
internal enum class HomeTab(val labelRes: Int, val icon: ImageVector) {
    Chats(R.string.chatlist_title, FeatherIcons.MessageSquare),
    Settings(R.string.settings_title, FeatherIcons.Settings),
}

/**
 * The connected shell: a Material 3 [Scaffold] with a bottom navigation bar over the two
 * top-level tabs - Chats (the landing list) and Settings. The conversation itself is a pushed
 * destination ([SessionRoute]) reached from Chats, so the bottom bar belongs to the home level
 * only, exactly like ChatGPT/Claude/Gemini.
 */
@Composable
fun HomeScreen(
    hostLabel: String?,
    connectionState: ConnectionUiState? = null,
    onSwitchHost: () -> Unit,
    onOpenSession: (String) -> Unit,
) {
    val colors = DsTheme.colors
    var selected by rememberSaveable { mutableStateOf(HomeTab.Chats) }

    Scaffold(
        containerColor = colors.bgBase,
        bottomBar = {
            NavigationBar {
                HomeTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab,
                        onClick = { selected = tab },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (selected) {
                HomeTab.Chats -> ChatsScreen(
                    hostLabel = hostLabel,
                    connectionState = connectionState,
                    onSwitchHost = onSwitchHost,
                    onOpenSession = onOpenSession,
                )
                HomeTab.Settings -> SettingsScreen(onClose = null)
            }
        }
    }
}