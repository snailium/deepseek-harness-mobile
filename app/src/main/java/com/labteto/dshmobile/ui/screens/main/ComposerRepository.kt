package com.labteto.dshmobile.ui.screens.main

import com.labteto.dshmobile.connection.PromptMode
import androidx.compose.runtime.*
import kotlinx.coroutines.*

internal data class ComposerKey(val host: String, val sessionId: String)

/** Owned by SessionStore, so a gallery callback and an upload never follow UI navigation. */
internal class ComposerDraft(val key: ComposerKey) {
    var text by mutableStateOf("")

    /**
     * What the send button does while a turn runs, for this draft.
     *
     * Seeded from the persisted setting the first time the draft is looked at, then owned here so
     * the + sheet's one-off override survives until the session changes. The settings card in the
     * details panel is the durable choice; this is the working copy.
     */
    var mode by mutableStateOf(PromptMode.QUEUE)
    var preparing by mutableStateOf(false)
    var submitting by mutableStateOf(false)
    val attachments = mutableStateListOf<PendingAttachment>()

    fun restoreRejected(submittedText: String, submitted: List<PendingAttachment>) {
        if (text.isBlank()) text = submittedText
        attachments.addAll(0, submitted.filterNot { it in attachments })
    }
}

internal class ComposerRepository {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val drafts = mutableMapOf<ComposerKey, ComposerDraft>()
    /**
     * The draft for [key], created on first use and seeded with the persisted send mode.
     *
     * [defaultMode] is read from settings by the caller rather than injected here: the repository
     * is not a settings holder, and a draft made before settings have loaded should still get the
     * user's choice on the next call — `getOrPut` keeps the first one, so the seed only has to be
     * right at creation, and the details-panel card writes settings directly for a live change.
     */
    fun get(key: ComposerKey, defaultMode: String = PromptMode.QUEUE): ComposerDraft =
        drafts.getOrPut(key) {
            ComposerDraft(key).apply { mode = defaultMode }
        }
    var imagePickTarget: Pair<ComposerDraft, com.labteto.dshmobile.core.wire.dto.ImageLimitsView>? = null
    var filePickTarget: ComposerDraft? = null
}
