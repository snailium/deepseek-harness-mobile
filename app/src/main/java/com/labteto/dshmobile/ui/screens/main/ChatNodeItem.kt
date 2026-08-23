package com.labteto.dshmobile.ui.screens.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.R
import com.labteto.dshmobile.core.session.AssistantMessageNode
import com.labteto.dshmobile.core.session.ChatNode
import com.labteto.dshmobile.core.session.CommandNode
import com.labteto.dshmobile.core.session.CompactionNode
import com.labteto.dshmobile.core.session.GoalNode
import com.labteto.dshmobile.core.session.OtherNode
import com.labteto.dshmobile.core.session.PlanModeNode
import com.labteto.dshmobile.core.session.RetryNode
import com.labteto.dshmobile.core.session.SubagentNode
import com.labteto.dshmobile.core.session.TitleNode
import com.labteto.dshmobile.core.session.TodoNode
import com.labteto.dshmobile.core.session.ToolCallNode
import com.labteto.dshmobile.core.session.ToolResultNode
import com.labteto.dshmobile.core.session.TurnEndNode
import com.labteto.dshmobile.core.session.TurnErrorNode
import com.labteto.dshmobile.core.session.TurnStartNode
import com.labteto.dshmobile.core.session.UserMessageNode
import com.labteto.dshmobile.core.session.WorkflowNode
import com.labteto.dshmobile.core.wire.dto.ToolEventView
import com.labteto.dshmobile.ui.components.AttachmentImage
import com.labteto.dshmobile.ui.components.DisclosureRow
import com.labteto.dshmobile.ui.components.DisclosureState
import com.labteto.dshmobile.ui.components.DsPill
import com.labteto.dshmobile.ui.components.FeatherIcons
import com.labteto.dshmobile.ui.components.MarkdownText
import com.labteto.dshmobile.ui.components.StateDot
import com.labteto.dshmobile.ui.components.StateDotState
import com.labteto.dshmobile.ui.components.ThinkingRow
import com.labteto.dshmobile.ui.components.ToolCard
import com.labteto.dshmobile.ui.components.UserBubble
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsAnimations
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Everything one transcript row needs that is not on the node itself.
 *
 * [@Stable] and rebuilt by the transcript from the *stable nodes view* the incremental fold
 * publishes, so rows that do not change do not re-derive per stream tick.
 */
@Stable
internal class ChatNodeContext(
    val nodes: List<ChatNode>,
    val toolViews: Map<Long, ToolEventView>,
    val running: Boolean,
    val cwd: String?,
    val onOpenSubagent: (String) -> Unit,
    val onBranchFrom: (Long) -> Unit,
    val onFeedback: (Long, Boolean) -> Unit,
) {
    /**
     * Tool result by call id, built once per snapshot instead of a `filterIsInstance` scan per
     * tool row per composition.
     */
    val resultsByCallId: Map<String, ToolResultNode> by lazy {
        val map = HashMap<String, ToolResultNode>()
        for (node in nodes) {
            if (node is ToolResultNode && node.callId.isNotBlank()) map[node.callId] = node
        }
        map
    }
}

/**
 * One node of the conversation. The `when` is exhaustive over [ChatNode] on purpose: a harness that
 * grows a new event type still renders, because the fold produces an `OtherNode` rather than
 * dropping it, and this shows it rather than a gap in the transcript.
 */
@Composable
internal fun ChatNodeItem(node: ChatNode, context: ChatNodeContext) {
    val colors = DsTheme.colors
    when (node) {
        // Turn boundaries are structure, not content — the transcript shows the work, not the frame.
        is TurnStartNode -> Unit

        is UserMessageNode -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            node.blocks.filter { it.kind == "image" }.forEach { block ->
                parseImageRef(block)?.let { ref ->
                    AttachmentImage(
                        attachmentId = ref.attachmentId,
                        intrinsicWidth = ref.width,
                        intrinsicHeight = ref.height,
                        contentDescription = ref.name,
                    )
                }
            }
            val text = node.displayText()
            if (text.isNotBlank()) UserBubble(text)
        }

        is AssistantMessageNode -> AssistantMessage(node, context)

        is ToolCallNode -> ToolCallRow(node, context)

        // Rendered inside the matching ToolCallNode's card; not a standalone row.
        is ToolResultNode -> Unit

        is TurnEndNode -> when (node.reasonKind) {
            "completed" -> Unit
            "aborted", "interrupted" -> DsPill(text = stringResource(R.string.chat_stopped), warn = true)
            "error" -> Row(verticalAlignment = Alignment.CenterVertically) {
                StateDot(StateDotState.Error, size = 8.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.chat_error_turn) + node.reasonDetail?.let { " · $it" }.orEmpty(),
                    style = DsType.small13,
                    color = colors.error,
                )
            }
            "max-tokens" -> DsPill(text = stringResource(R.string.chat_max_tokens), warn = true)
            else -> Unit
        }

        is TodoNode -> parseTodos(node.todos)?.let { TodoDock(it) }

        is GoalNode -> parseGoal(node.data)?.let { GoalSummary(it) }

        is PlanModeNode -> DsPill(
            text = stringResource(if (node.active) R.string.plan_mode_on else R.string.plan_mode_off),
            warn = true,
        )

        is CompactionNode -> CompactionRow(node)

        is RetryNode -> {
            val delayMs = (node.data as? JsonObject)?.let { obj ->
                obj["delayMs"].asLong() ?: obj["ms"].asLong() ?: obj["providerRetryAfterMs"].asLong()
            }
            val label = if (delayMs != null && delayMs > 0) {
                stringResource(R.string.chat_retry_scheduled, (delayMs / 1000).toInt().coerceAtLeast(1))
            } else {
                // A retry with no stated delay used to read "Loading…", which says nothing about
                // what happened; four of them in a row before a failure is a story worth telling.
                stringResource(R.string.chat_retrying)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                StateDot(StateDotState.Warning, size = 8.dp)
                Spacer(Modifier.width(6.dp))
                Text(label, style = DsType.caption11, color = colors.labelTertiary)
            }
        }

        is TurnErrorNode -> Row(verticalAlignment = Alignment.CenterVertically) {
            StateDot(StateDotState.Error, size = 8.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.chat_error_turn) + " · " + node.message,
                style = DsType.small13,
                color = colors.error,
            )
            node.code?.let { Text(" · $it", style = DsType.caption11, color = colors.labelTertiary) }
        }

        is CommandNode -> CommandRow(node)

        is WorkflowNode -> WorkflowRow(node.data, context.onOpenSubagent)

        is TitleNode -> Text(node.title, style = DsType.caption11, color = colors.labelTertiary)
        is SubagentNode -> Text(
            stringResource(R.string.subagents_title),
            style = DsType.caption11,
            color = colors.labelTertiary,
        )
        // Unknown event types stay visible — that is the compatibility contract — but the
        // structural ones are not "unknown", they are bookkeeping, and printing `step/start` /
        // `step/end` between every tool call buried the actual work in noise.
        is OtherNode -> if (node.type !in STRUCTURAL_EVENT_TYPES) {
            Text(node.type, style = DsType.caption11, color = colors.labelCaption)
        }
    }
}

/** Event types that carry no user-facing content; they frame the transcript rather than fill it. */
internal val STRUCTURAL_EVENT_TYPES = setOf(
    "step/start",
    "step/end",
    "request/header",
    "request/context",
    "session/end-seed",
    "session/title-llm-request",
    "agent/inbox/spliced",
    "assistant/chunk",
)

// ---------------------------------------------------------------------------
// Assistant messages
// ---------------------------------------------------------------------------

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun AssistantMessage(node: AssistantMessageNode, context: ChatNodeContext) {
    val colors = DsTheme.colors
    val isLast = context.nodes.lastOrNull()?.seq == node.seq
    val streaming = context.running && isLast
    val reasoningExpanded = remember(node.seq) { mutableStateMapOf<Int, Boolean>() }
    var actionsVisible by remember(node.seq) { mutableStateOf(false) }
    // A message whose blocks are all tool-call references renders nothing; streaming still earns
    // the typing card, because the first visible chunk can be seconds away.
    val hasVisibleContent = node.blocks.any {
        (it.kind == "text" && !it.text.isNullOrBlank()) || it.kind == "reasoning" || it.kind == "image"
    }
    if (!streaming && !hasVisibleContent) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Tap toggles the actions row; long-press opens it directly. Both gestures are
            // discoverable in reverse: once the row has been seen once, the bubble carries it.
            .combinedClickable(
                enabled = !streaming,
                onClick = { actionsVisible = !actionsVisible },
                onLongClick = { actionsVisible = true },
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AssistantAvatar()
        // Capped like the user bubble: an uncapped card stretches line length across the whole
        // screen on a wide device, which reads as a wall of text past ~75 characters a line.
        BoxWithConstraints(Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .widthIn(max = minOf(560.dp, maxWidth * 0.92f))
                    .shadow(DsSpacing.elevationQuiet, AssistantBubbleShape)
                    .background(colors.assistantCard, AssistantBubbleShape)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
            node.blocks.forEachIndexed { index, block ->
                when (block.kind) {
                    "text" -> MarkdownText(block.text.orEmpty())
                    "reasoning" -> {
                        val expanded = reasoningExpanded[index] ?: false
                        ThinkingRow(
                            summary = block.text?.lineSequence()?.firstOrNull()
                                ?: stringResource(R.string.chat_thinking),
                            expanded = expanded,
                            onToggle = { reasoningExpanded[index] = !expanded },
                            streaming = streaming,
                        )
                        AnimatedVisibility(visible = expanded) {
                            MarkdownText(block.text.orEmpty())
                        }
                    }
                    // Tool calls arrive as their own nodes and render as cards; the inline block is a
                    // duplicate reference, so it stays quiet here.
                    "tool-call", "tool-result" -> Unit
                    "image" -> parseImageRef(block)?.let { ref ->
                        AttachmentImage(
                            attachmentId = ref.attachmentId,
                            intrinsicWidth = ref.width,
                            intrinsicHeight = ref.height,
                            contentDescription = ref.name,
                        )
                    }
                    else -> block.text?.let {
                        Text(it, style = DsType.caption11, color = colors.labelTertiary)
                    }
                }
            }
            if (node.interrupted) {
                DsPill(text = stringResource(R.string.chat_stopped), warn = true)
            }
            if (streaming) {
                TypingIndicator()
            }
            // The only affordance a touchscreen has for a hidden action row: a quiet
            // ellipsis on the newest message. It stays put while the row is open, so the tap
            // that closes the row lands on the same control that opened it.
            AnimatedVisibility(
                visible = actionsVisible && !streaming,
                enter = fadeIn(DsAnimations.fade),
                exit = fadeOut(DsAnimations.fade),
            ) {
                MessageActionsRow(node, context)
            }
            if (isLast && !actionsVisible && !streaming) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.chat_message_actions)) {
                            actionsVisible = true
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        FeatherIcons.MoreHorizontal,
                        contentDescription = null,
                        tint = colors.labelCaption,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            }
        }
    }
}

/**
 * Left-tail bubble shape: the card points at the avatar, mirroring the user bubble's right pill.
 * The harness web UI draws assistant turns container-less, but the mobile transcript diverges the
 * same way it already does for `userBubble` — a conversation needs two visual sides.
 */
private val AssistantBubbleShape = RoundedCornerShape(
    topStart = 18.dp,
    topEnd = 18.dp,
    bottomStart = 6.dp,
    bottomEnd = 18.dp,
)

/** 26dp accent chip marking the assistant's side of the conversation. */
@Composable
private fun AssistantAvatar() {
    val colors = DsTheme.colors
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.accentTertiary),
        contentAlignment = Alignment.Center,
    ) {
        Text("DS", style = DsType.caption11Strong, color = colors.accent)
    }
}

/** Three staggered dots while a turn streams, in place of a silent wait. */
@Composable
private fun TypingIndicator() {
    val colors = DsTheme.colors
    val typingLabel = stringResource(R.string.chat_typing)
    val transition = rememberInfiniteTransition(label = "typingDots")
    Row(
        modifier = Modifier
            .padding(top = 2.dp)
            .semantics { contentDescription = typingLabel },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 450,
                        delayMillis = index * 150,
                        easing = FastOutSlowInEasing,
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "typingDot$index",
            )
            Box(
                Modifier
                    .size(6.dp)
                    .graphicsLayer { this.alpha = alpha }
                    .background(colors.labelTertiary, CircleShape),
            )
        }
    }
}

/**
 * Per-message actions, revealed on tap rather than always shown — a transcript with a row of icons
 * under every message reads as clutter, and these are all occasional.
 */
@Composable
private fun MessageActionsRow(node: AssistantMessageNode, context: ChatNodeContext) {
    val colors = DsTheme.colors
    val clipboard = LocalClipboardManager.current
    Row(
        modifier = Modifier.padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionIcon(FeatherIcons.Copy, stringResource(R.string.chat_copy_message)) {
            clipboard.setText(AnnotatedString(node.plainText))
        }
        ActionIcon(FeatherIcons.GitBranch, stringResource(R.string.chat_branch_message)) {
            context.onBranchFrom(node.seq)
        }
        ActionIcon(FeatherIcons.ThumbsUp, stringResource(R.string.chat_feedback_up)) {
            context.onFeedback(node.seq, true)
        }
        ActionIcon(FeatherIcons.ThumbsDown, stringResource(R.string.chat_feedback_down)) {
            context.onFeedback(node.seq, false)
        }
    }
}

@Composable
private fun ActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    // 40dp hit area around a 16dp glyph: a row of four at 48dp each crowds the bubble, and the
    // glyph stays the quiet accent it was while the tap area grows to a thumb-sized target.
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = DsTheme.colors.labelTertiary,
            modifier = Modifier.size(16.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Tool calls
// ---------------------------------------------------------------------------

@Composable
private fun ToolCallRow(node: ToolCallNode, context: ChatNodeContext) {
    val colors = DsTheme.colors
    val result = context.resultsByCallId[node.callId]
    val callView = context.toolViews[node.seq]
    val resultView = result?.let { context.toolViews[it.seq] }
    val card = buildToolCardView(
        call = node,
        result = result,
        callView = callView,
        resultView = resultView,
        running = result == null && context.running,
    )
    // The row header is derived here rather than taken from the card: only this layer knows the
    // tool's name and the session's cwd, which is what turns an absolute path into `app\build.gradle.kts`.
    val row = toolRowModel(
        toolName = node.name,
        argumentsJson = node.arguments,
        cwd = context.cwd,
        viewTitle = (resultView ?: callView).titleOrNull(),
    )
    var expanded by remember(node.callId) { mutableStateOf(false) }
    // The leading slot carries the outcome: a red dot for a failed call, the tool glyph otherwise.
    val state = when {
        result?.isError == true -> DisclosureState.Error
        result == null && context.running -> DisclosureState.Running
        else -> DisclosureState.Idle
    }
    ToolCard(
        view = card,
        expanded = expanded,
        onToggle = { expanded = !expanded },
        titleOverride = row.title,
        summaryOverride = row.summary,
        iconOverride = row.variant.featherIcon(),
        state = state,
    )
    if (result?.isError == true) {
        // The dot is colour-only, so the word stays — but without a second dot beside it.
        Text(
            stringResource(R.string.common_error),
            style = DsType.caption11,
            color = colors.error,
            modifier = Modifier.padding(start = 26.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Process groups
// ---------------------------------------------------------------------------

/**
 * One disclosure for a whole stretch of agentic work: the reasoning-bearing assistant message(s)
 * and the tool calls that follow them (see [groupTranscriptItems]).
 *
 * The header is the process's summary — "Thinking" with a shimmer while the turn streams, a plain
 * "Thought · N tool calls" once it settles — and the body is everything the model did: the
 * thinking bubble(s), then the tool cards, each still individually expandable. Auto-collapse is
 * the point (Wroblewski's agentic-UI rule: once the work is done, the process folds back to a
 * summary): the group opens while it is the live tail of a running turn, and folds itself back
 * up when the turn moves on — unless the reader opened it by hand, in which case that choice wins.
 */
@Composable
internal fun ProcessGroupItem(
    item: ProcessItem,
    context: ChatNodeContext,
    /**
     * Whether this process is the live tail of a running turn. Computed by the transcript against
     * its *renderable* tail: tool results are consumed inside their call's card, so a group whose
     * calls are still streaming results stays "live" (and open) even though the raw node stream's
     * last event is a result this transcript does not draw.
     */
    live: Boolean,
) {
    var touched by remember(item.key) { mutableStateOf(false) }
    var expanded by remember(item.key) { mutableStateOf(live) }
    LaunchedEffect(live) {
        if (!live && !touched) expanded = false
    }
    val toolLabel = pluralStringResource(R.plurals.tool_calls_count, item.tools.size, item.tools.size)
    val title = when {
        live -> stringResource(R.string.chat_thinking)
        item.messages.isNotEmpty() -> stringResource(R.string.chat_process_thought)
        else -> toolLabel
    }
    DisclosureRow(
        title = title,
        summary = if (item.tools.isNotEmpty()) toolLabel else null,
        icon = FeatherIcons.Loader,
        state = if (live) DisclosureState.Running else DisclosureState.Idle,
        expanded = expanded,
        onToggle = {
            touched = true
            expanded = !expanded
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item.messages.forEach { message ->
                AssistantMessage(message, context)
            }
            item.tools.forEach { call ->
                ToolCallRow(call, context)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Compaction / commands / workflow
// ---------------------------------------------------------------------------

@Composable
private fun CompactionRow(node: CompactionNode) {
    val summaryText = remember(node.seq) {
        runCatching {
            val array = (node.data as? JsonObject)?.get("summary") as? JsonArray
            array?.mapNotNull { (it as? JsonObject)?.get("text").asString() }?.joinToString("\n")
        }.getOrNull()
    }
    var expanded by remember(node.seq) { mutableStateOf(false) }
    DisclosureRow(
        title = stringResource(R.string.chat_compaction),
        summary = stringResource(R.string.chat_compaction_summary),
        icon = FeatherIcons.Archive,
        expanded = expanded,
        onToggle = { expanded = !expanded },
    ) {
        if (!summaryText.isNullOrBlank()) MarkdownText(summaryText)
    }
}

@Composable
private fun CommandRow(node: CommandNode) {
    val colors = DsTheme.colors
    val data = node.data as? JsonObject
    val name = data?.get("name").asString() ?: node.kind
    val text = data?.get("text").asString()
    var expanded by remember(node.seq) { mutableStateOf(false) }
    DisclosureRow(
        title = "/$name",
        summary = text,
        icon = FeatherIcons.Terminal,
        expanded = expanded,
        onToggle = { expanded = !expanded },
    ) {
        Text(
            node.data.toString(),
            style = DsType.caption11.copy(fontFamily = DsType.codeFont),
            color = colors.labelCaption,
            modifier = Modifier.padding(start = 28.dp, top = 2.dp),
        )
    }
}

@Composable
private fun WorkflowRow(
    data: kotlinx.serialization.json.JsonElement,
    onOpenMember: (String) -> Unit,
) {
    val colors = DsTheme.colors
    val obj = data as? JsonObject ?: return
    val name = obj["name"].asString()
    val status = obj["status"].asString() ?: obj["stopReason"].asString() ?: obj["outcome"].asString()
    val members = remember(data) { parseWorkflowMembers(data) }
    var expanded by remember(data) { mutableStateOf(false) }
    DisclosureRow(
        title = stringResource(R.string.workflow_title),
        summary = listOfNotNull(name, workflowStatusLabel(status)).joinToString(" · ").ifEmpty { null },
        icon = FeatherIcons.GitBranch,
        expanded = expanded,
        onToggle = { expanded = !expanded },
    ) {
        members.forEach { member ->
            val memberChildId = member.childId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, top = 2.dp)
                    .then(
                        if (memberChildId != null) {
                            Modifier.clickable { onOpenMember(memberChildId) }
                        } else {
                            Modifier
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StateDot(workflowMemberDot(member.status), size = 8.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    member.label ?: memberChildId.orEmpty(),
                    style = DsType.small13,
                    color = colors.labelSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                member.status?.let {
                    Text(
                        workflowStatusLabel(it) ?: it,
                        style = DsType.caption11,
                        color = colors.labelTertiary,
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}
