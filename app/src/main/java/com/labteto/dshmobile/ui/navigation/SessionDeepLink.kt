package com.labteto.dshmobile.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carries a session the user asked to open (a notification deep link) from the activity into the
 * navigation shell, until the shell consumes it.
 *
 * The activity owns the intent (including warm-start onNewIntent) but the NavHost owns navigation,
 * so the id crosses that boundary through this process-scoped holder rather than a direct call.
 */
@Singleton
class SessionDeepLink @Inject constructor() {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending

    fun request(sessionId: String) {
        _pending.value = sessionId
    }

    fun consume() {
        _pending.value = null
    }
}
