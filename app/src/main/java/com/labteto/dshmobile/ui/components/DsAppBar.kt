package com.labteto.dshmobile.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType

/**
 * The app's single top-app-bar voice, standardised on Material 3 [TopAppBar].
 *
 * Every chrome bar (Chats, Active, Settings, and a pushed Settings) used to set the same
 * container colour and title style inline, but each picked its own title typeface and only some
 * pinned correctly. This wrapper is the one place a bar's colour, title, and inset policy are
 * decided, so the app bars read identically wherever they appear.
 *
 * [windowInsets] is pinned to zero on purpose: tab screens live inside the Home [Scaffold],
 * which already supplies the status-bar inset, and full-screen pushes (Settings from Connect)
 * apply their own [androidx.compose.foundation.layout.safeDrawingPadding]. A second status-bar
 * inset here would double the top padding — one surface owns the inset.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DsTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = DsTheme.colors
    TopAppBar(
        title = {
            Text(
                title,
                style = DsType.navTitle,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        },
        modifier = modifier,
        navigationIcon = navigationIcon ?: {},
        actions = actions ?: {},
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colors.bgBase,
            scrolledContainerColor = colors.bgBase,
            titleContentColor = colors.labelPrimary,
            navigationIconContentColor = colors.labelSecondary,
            actionIconContentColor = colors.labelSecondary,
        ),
        windowInsets = WindowInsets(0, 0, 0, 0),
    )
}