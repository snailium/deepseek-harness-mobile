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
import com.labteto.dshmobile.ui.components.FeatherIcons
import com.labteto.dshmobile.ui.screens.main.ActiveScreen
import com.labteto.dshmobile.ui.screens.main.ChatsScreen
import com.labteto.dshmobile.ui.screens.settings.SettingsScreen
import com.labteto.dshmobile.ui.theme.DsTheme

/** The three top-level destinations carried by the bottom navigation bar. */
internal enum class HomeTab(val labelRes: Int, val icon: ImageVector) {
    Chats(R.string.chatlist_title, FeatherIcons.MessageSquare),
    Active(R.string.nav_active, FeatherIcons.Activity),
    Settings(R.string.settings_title, FeatherIcons.Settings),
}

/**
 * The connected shell: a Material 3 [Scaffold] with a bottom navigation bar over the three
 * top-level tabs — Chats (the landing list), Active (the live-session control center) and
 * Settings. The conversation itself is a pushed destination ([SessionRoute]) reached from Chats,
 * so the bottom bar belongs to the home level only, exactly like ChatGPT/Claude/Gemini.
 *
 * Tab selection is [rememberSaveable] rather than a nested NavHost: the three tabs are siblings
 * with no in-tab back stacks, and the pushed destinations sit above this whole scaffold at the
 * top-level graph anyway.
 */
@Composable
fun HomeScreen(
    hostLabel: String?,
    onSwitchHost: () -> Unit,
    onOpenSession: (String) -> Unit,
) {
    val colors = DsTheme.colors
    var selected by rememberSaveable { mutableStateOf(HomeTab.Chats) }

    Scaffold(
        containerColor = colors.bgBase,
        bottomBar = {
            // The bar inherits `surfaceContainer` from the Ds-mapped M3 scheme
            // (bgLayer1), so there is no hardcoded colour here to drift out of sync.
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
                    onSwitchHost = onSwitchHost,
                    onOpenSession = onOpenSession,
                )
                HomeTab.Active -> ActiveScreen()
                HomeTab.Settings -> SettingsScreen(onClose = null)
            }
        }
    }
}
