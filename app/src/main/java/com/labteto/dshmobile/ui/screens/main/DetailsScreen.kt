package com.labteto.dshmobile.ui.screens.main

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.R
import com.labteto.dshmobile.core.session.ChatNode
import com.labteto.dshmobile.core.session.ConversationSnapshot
import com.labteto.dshmobile.core.session.QueueItem
import com.labteto.dshmobile.core.session.PlanModeNode
import com.labteto.dshmobile.core.session.WorkflowNode
import com.labteto.dshmobile.core.wire.dto.ContextBreakdownView
import com.labteto.dshmobile.core.wire.dto.ContextPressureView
import com.labteto.dshmobile.core.wire.dto.HostDescription
import com.labteto.dshmobile.core.wire.dto.JobView
import com.labteto.dshmobile.core.wire.dto.SessionStatsView
import com.labteto.dshmobile.core.wire.dto.SubagentListEntry
import com.labteto.dshmobile.core.wire.dto.TokenUsageView
import com.labteto.dshmobile.data.SessionRow
import com.labteto.dshmobile.ui.components.ContextMeterDetail
import com.labteto.dshmobile.ui.components.DisclosureRow
import com.labteto.dshmobile.ui.components.DsButton
import com.labteto.dshmobile.ui.components.DsButtonSize
import com.labteto.dshmobile.ui.components.DsButtonVariant
import com.labteto.dshmobile.ui.components.DsIconButton
import com.labteto.dshmobile.ui.components.DsPill
import com.labteto.dshmobile.ui.components.DsToastHost
import com.labteto.dshmobile.ui.components.ApprovalPanel
import com.labteto.dshmobile.ui.components.PlanReviewPanel
import com.labteto.dshmobile.ui.components.QuestionsPanel
import com.labteto.dshmobile.ui.components.planReviewOf
import com.labteto.dshmobile.data.QuestionOutcome
import com.labteto.dshmobile.core.wire.dto.AskUserQuestionAnswer
import com.labteto.dshmobile.core.wire.dto.AskUserQuestionAnswerItem
import com.labteto.dshmobile.core.wire.dto.AskUserQuestionOption
import com.labteto.dshmobile.ui.components.SectionHeader
import com.labteto.dshmobile.ui.components.StateDot
import com.labteto.dshmobile.core.wire.dto.AgentPresetListValue
import com.labteto.dshmobile.core.wire.dto.SessionModelsValue
import com.labteto.dshmobile.ui.components.StateDotState
import com.labteto.dshmobile.ui.components.ToggleRow
import com.labteto.dshmobile.ui.components.ToastTone
import com.labteto.dshmobile.ui.components.formatDurationMs
import com.labteto.dshmobile.ui.components.formatTokens
import com.labteto.dshmobile.ui.components.rememberDsToast
import com.labteto.dshmobile.ui.rememberSessionStore
import com.labteto.dshmobile.ui.theme.DsShapes
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType
import com.labteto.dshmobile.ui.components.FeatherIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * The Active tab: everything about the open session that is not the conversation — the
 * mission-control surface for the live goal, plan, approvals, questions, queue, jobs, subagents,
 * context and host. Collapsible cards rather than one flat wall of headings, because on a
 * phone-width screen a flat list means the section you want is always three screens down.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveScreen(
    modifier: Modifier = Modifier,
) {
    val store = rememberSessionStore()
    val colors = DsTheme.colors
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val toast = rememberDsToast()
    val clipboard = LocalClipboardManager.current

    val conversation by store.currentConversation.collectAsStateWithLifecycle()
    val jobs by store.jobs.collectAsStateWithLifecycle()
    val hostInfo by store.hostInfo.collectAsStateWithLifecycle()
    val subagents by store.subagents.collectAsStateWithLifecycle()
    val sessions by store.sessions.collectAsStateWithLifecycle()
    val currentSessionId by store.currentSessionId.collectAsStateWithLifecycle()
    val stats by store.sessionStats.collectAsStateWithLifecycle()
    val usage by store.tokenUsage.collectAsStateWithLifecycle()
    val breakdown by store.contextBreakdown.collectAsStateWithLifecycle()
    val pressure by store.contextPressure.collectAsStateWithLifecycle()
    val models by store.models.collectAsStateWithLifecycle()
    val agentPresets by store.agentPresets.collectAsStateWithLifecycle()
    val pendingApproval by store.pendingApproval.collectAsStateWithLifecycle()
    val pendingQuestions by store.pendingQuestions.collectAsStateWithLifecycle()
    val current = sessions.firstOrNull { it.sessionId == currentSessionId }

    // This panel owns its own sheets rather than reaching back into ChatScreen's: it is reachable
    // on its own on a phone, where the chat surface is not even composed behind it.
    var sheet by remember { mutableStateOf<DetailsSheet?>(null) }

    val savedLabel = stringResource(R.string.chat_export_saved)
    val failedLabel = stringResource(R.string.chat_export_failed)
    val copiedLabel = stringResource(R.string.common_copied)

    val answerRefused = stringResource(R.string.questions_answer_refused)
    val answerUnsent = stringResource(R.string.questions_answer_unsent)
    fun refusalOf(outcome: QuestionOutcome): String? = when (outcome) {
        is QuestionOutcome.Accepted -> null
        is QuestionOutcome.Refused -> answerRefused.format(outcome.reason)
        is QuestionOutcome.Unsent -> answerUnsent
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { sink ->
                    store.exportSessionTo(sink, includeDescendants = true)
                } ?: false
            }.getOrDefault(false)
            toast.second(if (ok) savedLabel else failedLabel, if (ok) ToastTone.Success else ToastTone.Error)
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        // The screen sits on the grouped gray like every support surface; its cards are the
        // white plates, so it reads as a stack of groups rather than a white slab.
        color = colors.bgBase,
    ) {
        Box {
            Column(modifier = Modifier.fillMaxSize()) {
                // M3 top app bar; WindowInsets(0) because the home Scaffold supplies the status
                // bar inset. The cards scroll beneath it.
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.nav_active),
                            style = DsType.navTitle,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bgBase),
                    windowInsets = WindowInsets(0, 0, 0, 0),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = DsSpacing.medium)
                        .padding(bottom = DsSpacing.medium),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.small),
                ) {
                    // Server-initiated requests that block the turn — surfaced here so a pending
                    // approval or question is never missed while the conversation is scrolled away.
                    pendingApproval?.takeIf { it.sessionId == currentSessionId }?.let { approval ->
                        ApprovalPanel(
                            toolName = approval.toolName,
                            reason = approval.reason,
                            onAllow = { scope.launch { store.respondApproval(approval.sessionId, approval.approvalId, true) } },
                            onReject = { scope.launch { store.respondApproval(approval.sessionId, approval.approvalId, false) } },
                        )
                    }
                    pendingQuestions?.takeIf { it.sessionId == currentSessionId }?.let { questions ->
                        var planBusy by remember(questions.rpcId) { mutableStateOf(false) }
                        val review = remember(questions.rpcId) { planReviewOf(questions.items) }
                        fun settle(block: suspend () -> QuestionOutcome) {
                            planBusy = true
                            scope.launch {
                                refusalOf(block())?.let {
                                    planBusy = false
                                    toast.second(it, ToastTone.Error)
                                }
                            }
                        }
                        if (review != null) {
                            fun decide(option: AskUserQuestionOption) = settle {
                                store.answerQuestions(
                                    questions.sessionId,
                                    AskUserQuestionAnswer(listOf(AskUserQuestionAnswerItem(review.id, listOf(option.label)))),
                                )
                            }
                            PlanReviewPanel(
                                review = review,
                                busy = planBusy,
                                onApprove = { decide(review.approve) },
                                onDecline = { review.decline?.let { decide(it) } },
                                onDiscuss = { settle { store.dismissQuestions(questions.sessionId) } },
                            )
                        } else {
                            QuestionsPanel(
                                requestKey = questions.rpcId,
                                questions = questions.items,
                                onSubmit = { answer -> refusalOf(store.answerQuestions(questions.sessionId, answer)) },
                                onDismiss = { refusalOf(store.dismissQuestions(questions.sessionId)) },
                            )
                        }
                    }
                    SessionCard(
                        session = current,
                        models = models,
                        presets = agentPresets,
                        onRename = { title ->
                            scope.launch { currentSessionId?.let { store.renameSession(it, title) } }
                        },
                        onFork = { scope.launch { currentSessionId?.let { store.forkSession(it) } } },
                        onArchive = { scope.launch { currentSessionId?.let { store.archiveSession(it) } } },
                        onOpenModels = { sheet = DetailsSheet.Models },
                        onOpenPresets = {
                            scope.launch { store.refreshAgentPresets() }
                            sheet = DetailsSheet.Presets
                        },
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.small)) {
                        DsButton(
                            text = stringResource(R.string.chat_export),
                            icon = FeatherIcons.Download,
                            onClick = {
                                exportLauncher.launch("dsh-session-${currentSessionId.orEmpty()}.zip")
                            },
                            variant = DsButtonVariant.Outline,
                            size = DsButtonSize.Small,
                            enabled = currentSessionId != null,
                        )
                        DsButton(
                            text = stringResource(R.string.common_copy),
                            onClick = {
                                scope.launch {
                                    store.exportSessionUrl()?.let { url ->
                                        clipboard.setText(AnnotatedString(url))
                                        toast.second(copiedLabel, ToastTone.Info)
                                    }
                                }
                            },
                            variant = DsButtonVariant.Ghost,
                            size = DsButtonSize.Small,
                        )
                    }

                    val conv = conversation
                    if (conv == null) {
                        Text(
                            stringResource(R.string.chat_details_empty),
                            style = DsType.caption11,
                            color = colors.labelTertiary,
                        )
                    } else {
                        ContextCard(breakdown, pressure, usage, stats, currentSessionId)
                        GoalCard(conv, store, currentSessionId)
                        PlanCard(conv, currentSessionId) { next ->
                            scope.launch { store.runCommand(if (next) "/plan" else "/plan off") }
                        }
                        JobsCard(jobs, currentSessionId)
                        QueueCard(conv.queue, store, currentSessionId)
                        SubagentsCard(subagents, currentSessionId) { id -> scope.launch { store.openSubagentTranscript(id) } }
                        WorkflowCard(conv.nodes, currentSessionId)
                    }

                    HostCard(hostInfo, currentSessionId)
                }
            }
            DsToastHost(toast, modifier = Modifier.fillMaxWidth())
        }
    }

    when (sheet) {
        DetailsSheet.Models -> ModelsSheet(models = models, store = store, onDismiss = { sheet = null })
        DetailsSheet.Presets -> PresetsSheet(
            presets = agentPresets,
            currentPreset = current?.agentPreset,
            sessionBlank = current?.blank ?: false,
            store = store,
            onDismiss = { sheet = null },
        )
        null -> Unit
    }
}

/** Which picker, if any, is open over the details panel. */
private enum class DetailsSheet { Models, Presets }

// ---------------------------------------------------------------------------
// Cards
// ---------------------------------------------------------------------------

/** A titled card that expands on tap and remembers its state for the panel's lifetime. */
@Composable
private fun Card(
    title: String,
    summary: String? = null,
    initiallyExpanded: Boolean = false,
    /** Key expansion on the session so switching sessions resets the panel to its defaults. */
    sessionKey: String? = null,
    content: @Composable () -> Unit,
) {
    var expanded by remember(sessionKey, title) { mutableStateOf(initiallyExpanded) }
    val colors = DsTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(DsShapes.groupCard)
            .background(colors.bgLayer1)
            .border(1.dp, colors.borderL2, DsShapes.groupCard)
            .animateContentSize(),
    ) {
        DisclosureRow(
            title = title,
            summary = summary,
            expanded = expanded,
            onToggle = { expanded = !expanded },
            modifier = Modifier.padding(horizontal = DsSpacing.small),
        ) {
            Column(
                Modifier.padding(start = 24.dp, top = 4.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: SessionRow?,
    models: SessionModelsValue?,
    presets: AgentPresetListValue?,
    onRename: (String) -> Unit,
    onFork: () -> Unit,
    onArchive: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenPresets: () -> Unit,
) {
    val colors = DsTheme.colors
    if (session == null) return
    var renaming by remember(session.sessionId) { mutableStateOf(false) }
    Card(
        title = session.title ?: session.cwd?.let { basename(it) } ?: session.sessionId,
        summary = session.cwd?.let { basename(it) },
        initiallyExpanded = true,
        sessionKey = session.sessionId,
    ) {
        session.cwd?.let { Text(it, style = DsType.caption11, color = colors.labelCaption) }
        // The model and the preset are the two things about a session people most often come here
        // to check, and until now this panel could show only one of them and could change neither.
        // Both pills carry an onClick, which is also what makes them look pressable.
        Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.xsmall)) {
            models?.let { value ->
                val group = value.groups.firstOrNull { it.id == value.current.provider }
                val name = group?.models?.firstOrNull { it.id == value.current.model }?.name
                DsPill(text = name ?: value.current.model, onClick = onOpenModels)
            }
            session.agentPreset?.let {
                DsPill(text = agentPresetLabel(it, presets), onClick = onOpenPresets)
            }
        }
        if (session.running) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StateDot(StateDotState.Running)
                Spacer(Modifier.width(DsSpacing.xsmall))
                Text(
                    stringResource(R.string.jobs_running),
                    style = DsType.caption11,
                    color = colors.labelSecondary,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.small)) {
            DsButton(
                text = stringResource(R.string.common_rename),
                onClick = { renaming = true },
                variant = DsButtonVariant.Ghost,
                size = DsButtonSize.Small,
            )
            DsButton(
                text = stringResource(R.string.chatlist_session_fork),
                onClick = onFork,
                variant = DsButtonVariant.Ghost,
                size = DsButtonSize.Small,
            )
            DsButton(
                text = stringResource(R.string.common_archive),
                onClick = onArchive,
                variant = DsButtonVariant.Ghost,
                size = DsButtonSize.Small,
            )
        }
    }
    if (renaming) {
        RenameDialog(
            initial = session.title.orEmpty(),
            title = stringResource(R.string.chatlist_session_rename),
            onDismiss = { renaming = false },
            onConfirm = {
                onRename(it)
                renaming = false
            },
        )
    }
}

@Composable
private fun ContextCard(
    breakdown: ContextBreakdownView?,
    pressure: ContextPressureView?,
    usage: TokenUsageView?,
    stats: SessionStatsView?,
    sessionKey: String? = null,
) {
    val colors = DsTheme.colors
    if (breakdown == null && pressure == null && usage == null && stats == null) return
    Card(
        title = stringResource(R.string.chat_context_title),
        summary = pressure?.usedRatio?.let { "${(it * 100).toInt()}%" },
        initiallyExpanded = true,
        sessionKey = sessionKey,
    ) {
        ContextMeterDetail(breakdown, pressure)
        usage?.let {
            Text(
                stringResource(
                    R.string.chat_stats_tokens,
                    formatTokens(it.inputTokens),
                    formatTokens(it.outputTokens),
                ),
                style = DsType.caption11,
                color = colors.labelTertiary,
            )
            it.cacheHitRatio?.let { ratio ->
                Text(
                    stringResource(R.string.chat_stats_cache, (ratio * 100).toInt()),
                    style = DsType.caption11,
                    color = colors.labelTertiary,
                )
            }
        }
        stats?.let {
            Text(
                stringResource(
                    R.string.chat_stats_timing,
                    formatDurationMs(it.llmMs),
                    formatDurationMs(it.toolMs),
                ),
                style = DsType.caption11,
                color = colors.labelTertiary,
            )
        }
    }
}

@Composable
private fun GoalCard(
    conversation: ConversationSnapshot,
    store: com.labteto.dshmobile.data.SessionStore,
    sessionKey: String? = null,
) {
    val colors = DsTheme.colors
    val goal = parseGoal(conversation.projections["goal"])
    Card(
        title = stringResource(R.string.goal_title),
        summary = goal?.objective?.take(40),
        sessionKey = sessionKey,
    ) {
        if (goal == null) {
            Text(stringResource(R.string.goal_none), style = DsType.caption11, color = colors.labelTertiary)
            return@Card
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                goal.objective,
                style = DsType.small13,
                color = colors.labelPrimary,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(DsSpacing.small))
            DsPill(text = stringResource(goalPhaseLabelRes(goal.phase)))
        }
        goal.blockedReason?.let {
            Text(
                stringResource(R.string.goal_blocked_reason, it.message),
                style = DsType.caption11,
                color = colors.warnLabel,
            )
        }
        if (goal.maxGoalRounds > 0) {
            Text(
                stringResource(R.string.goal_max_rounds, goal.maxGoalRounds.toString()),
                style = DsType.caption11,
                color = colors.labelCaption,
            )
        }
        GoalBar(goal, store)
    }
}

/**
 * Plan mode, as a switch.
 *
 * The card used to put the current state in its title and the *opposite* state on a pill below it,
 * so it read as contradicting itself — and both the pill and the title moved together, leaving
 * nothing to say which one was the button. A switch states one thing and offers one action.
 *
 * [onTogglePlan] is handed the state to move to, because the two directions are different commands:
 * bare `/plan` only ever enters plan mode, and leaving needs `/plan off`. Sending `/plan` for both,
 * as this did, meant the control could turn plan mode on and never off again.
 */
@Composable
private fun PlanCard(
    conversation: ConversationSnapshot,
    sessionKey: String? = null,
    onTogglePlan: (active: Boolean) -> Unit,
) {
    val active = parsePlanActive(conversation) ?: return
    Card(
        title = stringResource(R.string.plan_mode_title),
        summary = stringResource(if (active) R.string.plan_mode_state_on else R.string.plan_mode_state_off),
        // Open by default: unlike the other cards this one is a control, and a control you have to
        // expand before you can reach is most of the way back to not having it.
        initiallyExpanded = true,
        sessionKey = sessionKey,
    ) {
        ToggleRow(
            label = stringResource(R.string.plan_mode_hint),
            checked = active,
            onChange = { onTogglePlan(!active) },
        )
    }
}

@Composable
private fun JobsCard(jobs: List<JobView>, sessionKey: String? = null) {
    val colors = DsTheme.colors
    Card(
        title = stringResource(R.string.jobs_title),
        summary = jobs.size.takeIf { it > 0 }?.toString(),
        sessionKey = sessionKey,
    ) {
        if (jobs.isEmpty()) {
            Text(stringResource(R.string.jobs_empty), style = DsType.caption11, color = colors.labelTertiary)
            return@Card
        }
        // A running job's elapsed time has to be driven by a ticker; reading the clock inside a
        // composable that never recomposes leaves the timer frozen at its first value.
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        val hasRunning = jobs.any { it.finishedAt == null }
        LaunchedEffect(hasRunning) {
            while (hasRunning) {
                delay(1_000)
                now = System.currentTimeMillis()
            }
        }
        jobs.forEach { job ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                StateDot(jobStatusDot(job.status))
                Spacer(Modifier.width(DsSpacing.small))
                Column(Modifier.weight(1f)) {
                    Text(
                        job.label,
                        style = DsType.small13,
                        color = colors.labelPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        job.detail?.let { "${job.kind} · $it" } ?: job.kind,
                        style = DsType.caption11,
                        color = colors.labelCaption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(DsSpacing.small))
                Text(
                    formatDurationMs((job.finishedAt ?: now) - job.startedAt),
                    style = DsType.caption11,
                    color = colors.labelCaption,
                )
            }
        }
    }
}

@Composable
private fun QueueCard(
    queue: List<QueueItem>,
    store: com.labteto.dshmobile.data.SessionStore,
    sessionKey: String? = null,
) {
    val colors = DsTheme.colors
    val scope = rememberCoroutineScope()
    Card(
        title = stringResource(R.string.chat_queue_title),
        summary = queue.size.takeIf { it > 0 }?.toString(),
        sessionKey = sessionKey,
    ) {
        if (queue.isEmpty()) {
            Text(
                stringResource(R.string.chat_queue_empty),
                style = DsType.caption11,
                color = colors.labelTertiary,
            )
            return@Card
        }
        queue.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        item.previewText,
                        style = DsType.small13,
                        color = colors.labelPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(item.placement, style = DsType.caption11, color = colors.labelCaption)
                }
                DsButton(
                    text = stringResource(R.string.common_remove),
                    onClick = { scope.launch { store.updateQueue(item.id, "remove") } },
                    variant = DsButtonVariant.Ghost,
                    size = DsButtonSize.Small,
                )
            }
        }
    }
}

@Composable
private fun SubagentsCard(
    subagents: List<SubagentListEntry>,
    sessionKey: String? = null,
    onOpen: (String) -> Unit,
) {
    val colors = DsTheme.colors
    Card(
        title = stringResource(R.string.subagents_title),
        summary = subagents.size.takeIf { it > 0 }?.toString(),
        sessionKey = sessionKey,
    ) {
        if (subagents.isEmpty()) {
            Text(
                stringResource(R.string.subagents_empty),
                style = DsType.caption11,
                color = colors.labelTertiary,
            )
            return@Card
        }
        subagents.forEach { entry ->
            val id = subagentId(entry)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = id != null) { id?.let(onOpen) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StateDot(if (subagentRunning(entry)) StateDotState.Running else StateDotState.Idle)
                Spacer(Modifier.width(DsSpacing.small))
                Column(Modifier.weight(1f)) {
                    Text(
                        subagentLabel(entry) ?: id.orEmpty(),
                        style = DsType.small13,
                        color = colors.labelPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(
                            if (subagentRunning(entry)) R.string.subagents_running
                            else R.string.subagents_inactive,
                        ),
                        style = DsType.caption11,
                        color = colors.labelCaption,
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkflowCard(nodes: List<ChatNode>, sessionKey: String? = null) {
    val colors = DsTheme.colors
    val workflows = remember(nodes) { parseWorkflows(nodes) }
    if (workflows.isEmpty()) return
    Card(
        title = stringResource(R.string.workflow_title),
        summary = workflows.size.toString(),
        sessionKey = sessionKey,
    ) {
        workflows.forEach { workflow ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        workflow.name,
                        style = DsType.small13,
                        color = colors.labelPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.workflow_members, workflow.members),
                        style = DsType.caption11,
                        color = colors.labelCaption,
                    )
                }
                Spacer(Modifier.width(DsSpacing.small))
                DsPill(
                    text = stringResource(
                        when (workflow.status) {
                            WorkflowStatus.Running -> R.string.workflow_running
                            WorkflowStatus.Completed -> R.string.workflow_completed
                            WorkflowStatus.Failed -> R.string.workflow_failed
                        },
                    ),
                    warn = workflow.status == WorkflowStatus.Failed,
                )
            }
        }
    }
}

@Composable
private fun HostCard(hostInfo: HostDescription?, sessionKey: String? = null) {
    val colors = DsTheme.colors
    if (hostInfo == null) return
    Card(
        title = stringResource(R.string.settings_host_info),
        summary = hostInfo.version,
        sessionKey = sessionKey,
    ) {
        Text(
            stringResource(R.string.connect_harness_version, hostInfo.version, hostInfo.cwd),
            style = DsType.caption11,
            color = colors.labelCaption,
        )
        Text(
            stringResource(R.string.connect_attached_sessions, hostInfo.attachedSessions),
            style = DsType.caption11,
            color = colors.labelCaption,
        )
    }
}

// ---------------------------------------------------------------------------
// Parsing helpers local to the panel
// ---------------------------------------------------------------------------

/** Plan mode: the projection when present, otherwise the last `plan/mode` event seen. */
private fun parsePlanActive(conversation: ConversationSnapshot): Boolean? {
    val fromProjection = conversation.projections["plan"]?.let { element ->
        runCatching {
            val obj = element as? JsonObject ?: return@runCatching null
            obj["active"]?.jsonPrimitive?.booleanOrNull
                ?: obj["active"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
        }.getOrNull()
    }
    return fromProjection
        ?: conversation.nodes.filterIsInstance<PlanModeNode>().lastOrNull()?.active
}

internal enum class WorkflowStatus { Running, Completed, Failed }

internal data class WorkflowView(
    val runId: String,
    val name: String,
    val status: WorkflowStatus,
    val members: Int,
)

private class WorkflowBuilder(val runId: String) {
    var name: String = ""
    val memberSeqs = mutableSetOf<String>()
    var status: WorkflowStatus? = null
    var failed = false
}

/** Fold the workflow event family into one row per run. */
internal fun parseWorkflows(nodes: List<ChatNode>): List<WorkflowView> {
    val builders = linkedMapOf<String, WorkflowBuilder>()
    for (node in nodes) {
        if (node !is WorkflowNode) continue
        val data = node.data as? JsonObject ?: continue
        val runId = data["runId"].asString() ?: continue
        val builder = builders.getOrPut(runId) { WorkflowBuilder(runId) }
        when (node.kind) {
            "tool-workflow/run-start" -> data["name"].asString()?.let { builder.name = it }
            "tool-workflow/agent-start" -> data["seq"].asString()?.let { builder.memberSeqs.add(it) }
            "tool-workflow/agent-end" -> {
                val outcome = data["outcome"].asString()
                if (outcome == "failed" || outcome == "cancelled") builder.failed = true
            }
            "tool-workflow/run-end" -> {
                val stop = data["stopReason"].asString()
                builder.status = if (stop == "completed") WorkflowStatus.Completed else WorkflowStatus.Failed
            }
        }
    }
    return builders.values.map { builder ->
        WorkflowView(
            runId = builder.runId,
            name = builder.name.ifBlank { builder.runId },
            status = builder.status ?: if (builder.failed) WorkflowStatus.Failed else WorkflowStatus.Running,
            members = builder.memberSeqs.size,
        )
    }
}
