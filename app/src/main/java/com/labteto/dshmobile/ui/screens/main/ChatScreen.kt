package com.labteto.dshmobile.ui.screens.main

import android.graphics.BitmapFactory
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.ui.components.PageColumn
import com.labteto.dshmobile.ui.media.sampleSizeFor
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labteto.dshmobile.core.wire.dto.AskUserQuestionAnswer
import com.labteto.dshmobile.core.wire.dto.AskUserQuestionAnswerItem
import com.labteto.dshmobile.core.wire.dto.AskUserQuestionOption
import com.labteto.dshmobile.core.wire.RpcResult
import com.labteto.dshmobile.core.wire.dto.CommandSubmitAttachment
import com.labteto.dshmobile.core.wire.dto.ImageLimitsView
import com.labteto.dshmobile.data.CommandOutcome
import com.labteto.dshmobile.data.PromptOutcome
import com.labteto.dshmobile.data.QuestionOutcome
import com.labteto.dshmobile.data.SessionStore
import com.labteto.dshmobile.ui.components.ApprovalPanel
import com.labteto.dshmobile.ui.components.ConnectionBanner
import com.labteto.dshmobile.ui.components.DsToastHost
import com.labteto.dshmobile.ui.components.PlanReviewPanel
import com.labteto.dshmobile.ui.components.planReviewOf
import com.labteto.dshmobile.ui.components.QuestionsPanel
import com.labteto.dshmobile.ui.components.rememberDsToast
import com.labteto.dshmobile.ui.rememberSessionStore
import com.labteto.dshmobile.ui.theme.DsAnimations
import com.labteto.dshmobile.ui.theme.DsTheme
import androidx.compose.ui.res.stringResource
import com.labteto.dshmobile.R
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import com.labteto.dshmobile.core.session.readImageBounded
import com.labteto.dshmobile.core.wire.dto.ImageRejection
import androidx.compose.runtime.LaunchedEffect
import com.labteto.dshmobile.connection.AppSettings
import com.labteto.dshmobile.ui.rememberHostsStore
import androidx.compose.runtime.collectAsState
import com.labteto.dshmobile.ui.components.TranscriptSearchBar
import com.labteto.dshmobile.ui.theme.DsSpacing

/**
 * The chat surface: chrome, transcript or trajectory, the persistent docks, and the composer.
 *
 * Everything below the tabs stays outside the tab swap on purpose — you can keep typing, and keep
 * answering an approval, while reading the trajectory, and the keyboard-attached surface never
 * animates out from under the cursor.
 */
@Composable
fun ChatScreen(
    onOpenDetails: () -> Unit,
    onOpenDrawer: () -> Unit,
    detailsOpen: Boolean,
) {
    val store = rememberSessionStore()
    val scope = rememberCoroutineScope()
    val colors = DsTheme.colors
    val context = LocalContext.current
    val toast = rememberDsToast()

    val conversation by store.currentConversation.collectAsStateWithLifecycle()
    // The fold derives the live to-do list from the event stream (see SessionStore.todos);
    // dismissal is keyed on the list itself, so a new `todo/write` restores the bar.
    val liveTodos = store.todos.collectAsStateWithLifecycle().value
    var todosDismissed by remember(liveTodos) { mutableStateOf(false) }
    val currentSessionId by store.currentSessionId.collectAsStateWithLifecycle()
    // In-transcript search. The bar is collapsible: the toolbar's search icon opens it, and a ×
    // inside the bar closes it. The query and cursor live above the tab swap — they belong to the
    // session rather than to either view, and stepping past the last hit wraps to the first.
    // The finder's four values are one thing — the cursor only means anything against the current
    // query's results, and the query only against the open session — so they travel together in a
    // holder. See `TranscriptSearchState`.
    val search = rememberTranscriptSearchState(currentSessionId)
    val matches = remember(conversation?.nodes, search.query) {
        search.matches(conversation?.nodes.orEmpty())
    }
    val cursor = search.cursorIn(matches)
    val currentMatch = search.current(matches)

    val sessions by store.sessions.collectAsStateWithLifecycle()
    val models by store.models.collectAsStateWithLifecycle()
    val skills by store.skills.collectAsStateWithLifecycle()
    val commands by store.commands.collectAsStateWithLifecycle()
    val commandsAvailable by store.commandsAvailable.collectAsStateWithLifecycle()
    val subagents by store.subagents.collectAsStateWithLifecycle()
    val subagentConversation by store.subagentConversation.collectAsStateWithLifecycle()
    val subagentMode by store.subagentMode.collectAsStateWithLifecycle()
    val connectionError by store.connectionError.collectAsStateWithLifecycle()
    val loadingOlder by store.loadingOlder.collectAsStateWithLifecycle()
    val loadOlderFailed by store.loadOlderFailed.collectAsStateWithLifecycle()
    val pendingApproval by store.pendingApproval.collectAsStateWithLifecycle()
    val pendingQuestions by store.pendingQuestions.collectAsStateWithLifecycle()
    val permissions by store.permissions.collectAsStateWithLifecycle()
    val pendingPermission by store.pendingPermission.collectAsStateWithLifecycle()
    val agentPresets by store.agentPresets.collectAsStateWithLifecycle()
    val sessionStats by store.sessionStats.collectAsStateWithLifecycle()
    val tokenUsage by store.tokenUsage.collectAsStateWithLifecycle()
    val contextBreakdown by store.contextBreakdown.collectAsStateWithLifecycle()
    val contextPressure by store.contextPressure.collectAsStateWithLifecycle()
    val imageLimits by store.imageLimits.collectAsStateWithLifecycle()

    val currentSession = sessions.firstOrNull { it.sessionId == currentSessionId }
    val title = currentSession?.title
        ?: currentSession?.cwd?.let { basename(it) }
        ?: currentSessionId.orEmpty()

    val connection by store.connectionState.collectAsStateWithLifecycle()
    // The persisted send mode seeds each new draft; the details panel's card writes it.
    val hostsStore = rememberHostsStore()
    val appSettings by hostsStore.settings.collectAsState(initial = AppSettings())
    val hostKey = connection.host?.let { "${it.baseUrl}|${it.id}" }.orEmpty()
    val composer = remember(hostKey, currentSessionId) {
        store.composers.get(
            ComposerKey(hostKey, currentSessionId.orEmpty()),
            defaultMode = appSettings.promptMode,
        )
    }
    var tab by rememberSaveable { mutableStateOf(ChatTab.Chat) }

    var panelKey by remember { mutableStateOf<ComposerKey?>(null) }
    var feedback by remember { mutableStateOf<Triple<ComposerKey, String, Boolean>?>(null) }
    var sheet by remember { mutableStateOf<ChatSheet?>(null) }

    // Hoisted above the tab swap so each view keeps its own scroll position across switches.
    val chatListState = rememberLazyListState()
    val trajectoryListState = rememberLazyListState()

    val commandFailed = stringResource(R.string.err_command_failed)
    val unknownCommand = stringResource(R.string.err_command_unknown)

    fun report(outcome: CommandOutcome) {
        when (outcome) {
            is CommandOutcome.Ok -> outcome.text?.takeIf { it.isNotBlank() }?.let { toast.second(it) }
            is CommandOutcome.Unknown -> toast.second(unknownCommand.format(outcome.line))
            is CommandOutcome.Failed -> toast.second(commandFailed.format(outcome.message))
        }
    }

    val answerRefused = stringResource(R.string.questions_answer_refused)
    val answerUnsent = stringResource(R.string.questions_answer_unsent)

    /**
     * What to tell the user about a question response, or null when the harness took it.
     *
     * A refusal is worth naming rather than swallowing: the host's wait stays open and the tool
     * call that opened it stays blocked, so a card that quietly did nothing would leave the session
     * stuck with no explanation.
     */
    fun refusalOf(outcome: QuestionOutcome): String? = when (outcome) {
        is QuestionOutcome.Accepted -> null
        is QuestionOutcome.Refused -> answerRefused.format(outcome.reason)
        is QuestionOutcome.Unsent -> answerUnsent
    }

    // The composer's submit path — draft, send mode, attachments, the two in-flight flags and
    // every operation on them — lives in one object. See `ComposerSubmitter`.
    val submitter = rememberComposerSubmitter(
        store = store,
        composer = composer,
        commands = commands,
        imageLimits = imageLimits,
        report = { outcome -> report(outcome) },
    )
    val draft = submitter.draft
    val attachments = submitter.attachments
    val mode = submitter.mode
    val filePicker = rememberFilePicker(submitter)
    val imagePicker = rememberImagePicker(submitter)

    androidx.compose.runtime.CompositionLocalProvider(
        com.labteto.dshmobile.ui.media.LocalAttachmentScope provides (composer.key.host to composer.key.sessionId),
        com.labteto.dshmobile.ui.components.LocalFileOpener provides { path: String ->
        store.panels.get(composer.key).open(path); panelKey = composer.key
    }) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.bgBase,
    ) {
        // The activity draws edge to edge, so every top-level surface has to consume the insets
        // itself or the chrome ends up underneath the status bar. safeDrawing covers the status
        // bar, the gesture area and the keyboard in one modifier.
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            ChatTopBar(
                title = title,
                running = conversation?.running == true,
                models = models,
                agentPresetLabel = currentSession?.agentPreset?.takeIf { agentPresets?.modeSelectionEnabled != false }?.let { agentPresetLabel(it, agentPresets) },
                subagentCount = subagents.size,
                detailsOpen = detailsOpen,
                tab = tab,
                onOpenDrawer = onOpenDrawer,
                onOpenModels = { sheet = ChatSheet.Models },
                onOpenPresets = {
                    scope.launch { store.refreshAgentPresets() }
                    sheet = ChatSheet.Presets
                },
                onOpenSubagents = { sheet = ChatSheet.Subagents },
                onOpenDetails = onOpenDetails,
                onTabChange = { tab = it },
                // Null when there is no session to point at, which hides the button rather than
                // offering a tap that does nothing.
                onOpenWorkspace = if (currentSessionId != null) {
                    { panelKey = composer.key }
                } else {
                    null
                },
                searchQuery = search.query,
                // The counter is 1-based and reads 0/0 when nothing matches.
                searchPosition = if (matches.isEmpty()) 0 else cursor + 1,
                searchCount = matches.size,
                onSearchQueryChange = { search.onQueryChange(it) },
                onSearchPrevious = { search.step(-1, matches.size) },
                onSearchNext = { search.step(1, matches.size) },
                searchOpen = search.open,
                onToggleSearch = { search.open = !search.open },
            )

            connectionError?.let {
                androidx.compose.material3.TextButton(onClick = { store.retryConnection() }) { ConnectionBanner(it) }
            }
            if (conversation?.gap == true) {
                ConnectionBanner(stringResource(R.string.common_reconnecting))
            }

            // Collapsible transcript search: hidden by default, opened by the toolbar's search
            // icon, closed by its own × button. Sits above the todo dock so hits are visible
            // while reading.
            AnimatedVisibility(visible = search.open) {
                TranscriptSearchBar(
                    query = search.query,
                    onQueryChange = { search.onQueryChange(it) },
                    matchPosition = if (matches.isEmpty()) 0 else cursor + 1,
                    matchCount = matches.size,
                    onPrevious = { search.step(-1, matches.size) },
                    onNext = { search.step(1, matches.size) },
                    onClose = { search.close() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DsSpacing.pageHorizontal, vertical = DsSpacing.tiny),
                )
            }

            // The agent's live to-do list, pinned above the transcript. Hidden until a
            // `todo/write` arrives, and dismissed state is keyed on the list so a later write
            // brings the bar back on its own.
            liveTodos?.let { todos ->
                TodoBar(
                    todos = todos,
                    dismissed = todosDismissed,
                    onDismiss = { todosDismissed = true },
                    // Shares the page inset with the transcript and the composer: the bar spans
                    // the same column, so its edges have to sit on the same lines.
                    modifier = Modifier.padding(horizontal = DsSpacing.pageHorizontal, vertical = 2.dp),
                )
            }

            val nodeContext = ChatNodeContext(
                nodes = conversation?.nodes ?: emptyList(),
                eventTimes = conversation?.journal?.associate { it.seq to it.time }.orEmpty(),
                running = conversation?.running == true,
                cwd = currentSession?.cwd,
                onOpenSubagent = { childId ->
                    scope.launch { store.openSubagentTranscript(childId) }
                    sheet = ChatSheet.Subagents
                },
                onBranchFrom = { seq -> scope.launch { currentSessionId?.let { store.forkSession(it, seq) } } },
                onFeedback = { seq, positive ->
                    conversation?.nodes?.filterIsInstance<com.labteto.dshmobile.core.session.AssistantMessageNode>()
                        ?.firstOrNull { it.seq == seq }?.messageId?.let { feedback = Triple(composer.key, it, positive) }
                },
            )

            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    (
                        slideInHorizontally { width -> if (forward) width / 6 else -width / 6 } +
                            fadeIn(DsAnimations.fade)
                        )
                        .togetherWith(fadeOut(DsAnimations.fade)) using SizeTransform(clip = false)
                },
                modifier = Modifier.weight(1f),
                label = "chatTab",
            ) { current ->
                when (current) {
                    ChatTab.Chat -> ChatTranscript(
                        conversation = conversation,
                        loading = conversation == null && currentSessionId != null,
                        loadingOlder = loadingOlder,
                        loadOlderFailed = loadOlderFailed,
                        context = nodeContext,
                        listState = chatListState,
                        onLoadOlder = { scope.launch { store.loadOlder() } },
                        focusSeq = currentMatch?.seq,
                    )
                    ChatTab.Trajectory -> TrajectoryTab(
                        conversation = conversation,
                        stats = sessionStats,
                        usage = tokenUsage,
                        cwd = currentSession?.cwd,
                        listState = trajectoryListState,
                    )
                }
            }

            conversation?.let { conv ->
                PageColumn(spacing = 4.dp) {
                    // The to-do list lives in the pinned bar above the transcript, not here:
                    // two renderings of the same list on one screen read as two different lists.
                    parseGoal(conv.projections["goal"])?.let { GoalBar(it, store) }
                    QueueDock(conv.queue, store)
                }
            }

            // Server-initiated requests take over the bottom of the screen: they block the turn,
            // so burying them behind a scroll would strand the session.
            val approval = pendingApproval
            if (approval != null && approval.sessionId == currentSessionId) {
                // A refusal is said out loud here rather than swallowed, for the reason [refusalOf]
                // gives: the host's wait — and the tool call behind it — stays open, and a panel
                // that reported nothing would read as two buttons that do nothing.
                fun decide(allow: Boolean) = scope.launch {
                    refusalOf(store.respondApproval(approval.sessionId, approval.approvalId, allow))
                        ?.let { toast.second(it) }
                }
                ApprovalPanel(
                    toolName = approval.toolName,
                    reason = approval.reason,
                    // The page inset lives here, at the call site, because the panel is one of
                    // several stacked in the same column: a composable that inset itself would
                    // fight whichever container also holds it.
                    modifier = Modifier.padding(horizontal = DsSpacing.pageHorizontal),
                    onAllow = { decide(true) },
                    onReject = { decide(false) },
                )
            }
            val questions = pendingQuestions
            if (questions != null && questions.sessionId == currentSessionId) {
                var planBusy by remember(questions.rpcId) { mutableStateOf(false) }
                // A plan review rides the question channel but is a different decision, so it gets
                // the card built for it. The narrowing decides which — and hands back anything the
                // card could not answer in full, because the card answers one question and the host
                // refuses an answer batch shorter than the request it resolves.
                val review = remember(questions.rpcId) { planReviewOf(questions.items) }
                if (review != null) {
                    fun settle(block: suspend () -> QuestionOutcome) {
                        planBusy = true
                        scope.launch {
                            refusalOf(block())?.let {
                                planBusy = false
                                toast.second(it)
                            }
                        }
                    }
                    fun decide(option: AskUserQuestionOption) = settle {
                        store.answerQuestions(
                            questions.sessionId,
                            AskUserQuestionAnswer(
                                listOf(AskUserQuestionAnswerItem(review.id, listOf(option.label))),
                            ),
                        )
                    }
                    PlanReviewPanel(
                        review = review,
                        busy = planBusy,
                        modifier = Modifier.padding(horizontal = DsSpacing.pageHorizontal),
                        onApprove = { decide(review.approve) },
                        onDecline = { review.decline?.let { decide(it) } },
                        // Wanting to talk it over first is not one of the options the asker stated,
                        // so it ends the request rather than answering it with the refusal.
                        onDiscuss = {
                            submitter.onDraftChange("")
                            settle { store.dismissQuestions(questions.sessionId) }
                        },
                    )
                } else {
                    QuestionsPanel(
                        requestKey = questions.rpcId,
                        questions = questions.items,
                        modifier = Modifier.padding(horizontal = DsSpacing.pageHorizontal),
                        onSubmit = { answer ->
                            refusalOf(store.answerQuestions(questions.sessionId, answer))
                        },
                        onDismiss = { refusalOf(store.dismissQuestions(questions.sessionId)) },
                    )
                }
            }

            Composer(
                draft = draft,
                onDraftChange = submitter::onDraftChange,
                attachments = attachments,
                onRemoveAttachment = submitter::removeAttachment,
                onRetryAttachment = { index ->
                    submitter.retryAttachment(index)
                },
                permissions = permissions,
                pendingPermission = pendingPermission,
                onPermissionPick = { value -> scope.launch { report(store.setPermissionPreset(value)) } },
                contextBreakdown = contextBreakdown,
                contextPressure = contextPressure,
                running = conversation?.running == true,
                enterToSend = appSettings.enterToSend,
                enabled = currentSessionId != null && !composer.submitting,
                preparing = composer.preparing,
                onOpenCommands = { sheet = ChatSheet.Commands },
                onOpenAttachments = { sheet = ChatSheet.Attachments },
                // A lambda, not `::send`. The composer holds this through rememberUpdatedState,
                // which keeps what it has when the new value is equal to it, and a reference to a
                // local function equals every other reference to that function whatever it
                // captured. Each session's `::send` compared equal to the first and was dropped, so
                // the button went on sending with the attachment list of whichever session was
                // open when this screen first composed. A lambda is rebuilt when what it captures
                // changes and compares by identity, so the composer always holds the current one.
                onSend = submitter::send,
                onStop = { scope.launch { store.cancelTurn() } },
            )

            StatsFooter(stats = sessionStats, usage = tokenUsage)
        }
        DsToastHost(toast, modifier = Modifier.fillMaxWidth())
    }

    }
    panelKey?.let { key -> WorkspacePanels(store, store.panels.get(key), onDismiss = { panelKey = null }) }
    feedback?.let { (key, id, positive) -> FeedbackDialog(store, key, id, positive) { feedback = null } }
    when (sheet) {
        ChatSheet.Commands -> CommandSheet(
            commands = commands,
            commandsAvailable = commandsAvailable,
            skills = skills,
            // The sheet only auto-runs commands that take no input at all, and a command that
            // takes no input takes no attachments either — so a pending attachment refuses here
            // for the same reason it refuses at the composer, rather than being silently dropped.
            onRunCommand = { line ->
                val name = line.removePrefix("/").substringBefore(' ')
                if (attachments.isEmpty()) {
                    scope.launch { report(store.runCommand(line)) }
                } else {
                    toast.second(context.getString(R.string.err_command_no_images, name))
                }
            },
            onPrefillDraft = submitter::prefill,
            onDismiss = { sheet = null },
        )
        ChatSheet.Attachments -> AttachmentSheet(
            // Both rows upload against an open session, so both need one; and a pick already in
            // flight has to finish before another starts.
            canAttach = currentSessionId != null && !composer.preparing && !composer.submitting,
            onAttachImage = {
                if (!composer.preparing && store.composers.imagePickTarget == null) {
                    store.composers.imagePickTarget = composer to (imageLimits ?: ImageLimitsView())
                    imagePicker.launch("image/*")
                }
            },
            onAttachFile = { store.composers.filePickTarget = composer; filePicker.launch(arrayOf("*/*")) },
            onDismiss = { sheet = null },
        )
        ChatSheet.Models -> ModelsSheet(models = models, store = store, onDismiss = { sheet = null })
        ChatSheet.Presets -> PresetsSheet(
            presets = agentPresets,
            currentPreset = currentSession?.agentPreset,
            sessionBlank = currentSession?.blank ?: false,
            store = store,
            onDismiss = { sheet = null },
        )
        ChatSheet.Subagents -> SubagentsSheet(
            store = store,
            entries = subagents,
            conversation = subagentConversation,
            mode = subagentMode,
            onDismiss = { sheet = null },
        )
        null -> Unit
    }
}

/** Which sheet, if any, is open over the chat surface. */
private enum class ChatSheet { Commands, Attachments, Models, Presets, Subagents }

/**
 * A picked document's display name and size, as its provider reports them.
 *
 * The name falls back to the last path segment when the provider offers none, and the size to
 * `-1` — the upload route accepts a chunked body, so an unknown length costs only the progress
 * ring. A null name means the provider answered nothing at all, which is a read failure.
 */
internal fun describeDocument(resolver: android.content.ContentResolver, uri: android.net.Uri): Pair<String?, Long> {
    var name: String? = null
    var size = -1L
    runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
    }
    return (name ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }) to size
}


/**
 * What a bounds pass over the picked bytes tells us: the image's intrinsic size, the media type
 * its bytes actually are, and a thumbnail for the composer strip.
 *
 * A [width] of zero means the bytes did not parse as an image at all.
 */
internal data class DecodedPick(
    val width: Int,
    val height: Int,
    val detectedMediaType: String?,
    val preview: ImageBitmap?,
)

/**
 * Measure and thumbnail a picked image in one pass.
 *
 * The bounds pass was always here for the thumbnail's sample size; it also answers the two
 * questions the host's admission asks — how large is this, and is it really the type its provider
 * claims — so the picker can refuse an image before spending a round trip on it rather than after.
 */
internal fun decodePick(bytes: ByteArray): DecodedPick {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds) }
    val width = bounds.outWidth.coerceAtLeast(0)
    val height = bounds.outHeight.coerceAtLeast(0)
    val preview = if (width <= 0) {
        null
    } else {
        runCatching {
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(maxOf(width, height), PREVIEW_WIDTH_PX)
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
        }.getOrNull()
    }
    return DecodedPick(if (preview == null) 0 else width, if (preview == null) 0 else height, bounds.outMimeType, preview)
}

/** The composer thumbnail is 56dp; decoding much past that is wasted memory. */
private const val PREVIEW_WIDTH_PX = 224
