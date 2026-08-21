package com.labteto.dshmobile.ui.navigation

import kotlinx.serialization.Serializable

/**
 * The app's typed navigation routes.
 *
 * [HomeRoute] is the connected shell — a bottom navigation bar hosting the Chats, Active and
 * Settings tabs. [ConnectRoute] and [SessionRoute] are pushed full-screen over it, and
 * [SettingsRoute] is a pushed settings screen reachable from the connect screen (before a
 * harness is connected, there is no home scaffold to host a Settings tab).
 */
@Serializable
data object HomeRoute

@Serializable
data object ConnectRoute

@Serializable
data object SettingsRoute

@Serializable
data class SessionRoute(val sessionId: String)
