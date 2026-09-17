package com.labteto.dshmobile.ui.screens.main

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.R
import com.labteto.dshmobile.core.session.QueueItem
import com.labteto.dshmobile.core.wire.dto.GoalPhase
import com.labteto.dshmobile.core.wire.dto.GoalSnapshot
import com.labteto.dshmobile.core.wire.dto.SessionStatsView
import com.labteto.dshmobile.core.wire.dto.TodoItem
import com.labteto.dshmobile.core.wire.dto.TokenUsageView
import com.labteto.dshmobile.data.SessionStore
import com.labteto.dshmobile.ui.components.DisclosureRow
import com.labteto.dshmobile.ui.components.DsButton
import com.labteto.dshmobile.ui.components.DsButtonVariant
import com.labteto.dshmobile.ui.components.DsDialog
import com.labteto.dshmobile.ui.components.DsMenu
import com.labteto.dshmobile.ui.components.DsPill
import com.labteto.dshmobile.ui.components.FeatherIcons
import com.labteto.dshmobile.ui.components.MenuItem
import com.labteto.dshmobile.ui.components.SectionHeader
import com.labteto.dshmobile.ui.components.StateDot
import com.labteto.dshmobile.ui.components.formatDurationMs
import com.labteto.dshmobile.ui.components.formatTokens
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType
import kotlinx.coroutines.launch

/**
 * The strip of persistent context between the transcript and the composer: to-dos, the ongoing
 * goal, the queue, and the run statistics.
 *
 * These render as collapsed one-line summaries and expand on tap, rather than appearing and
 * vanishing as their data arrives. A dock that pops into existence mid-turn shoves the transcript
 * and the composer around while you are reading or typing, which is most of what made the old
 * layout feel unsettled.
 */

@Composable
internal fun TodoDock(todos: List<TodoEntry>, modifier: Modifier = Modifier) {
    if (todos.isEmpty()) return
    var expanded by remember(todos) { mutableStateOf(false) }
    val completed = todos.count { it.status == "completed" }
    DisclosureRow(
        title = stringResource(R.string.chat_todo_title),
        summary = stringResource(R.string.chat_todo_progress, completed, todos.size),
        icon = FeatherIcons.CheckSquare,
        expanded = expanded,
        onToggle = { expanded = !expanded },
        modifier = modifier,
    ) {
        todos.forEach { todo ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StateDot(todoStatusDot(todo.status), size = 8.dp)
                Spacer(Modifier.width(8.dp))
                Text(todo.content, style = DsType.small13, color = DsTheme.colors.labelSecondary)
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

/**
 * The live to-do bar pinned above the message area, styled after the web `TodoPanel`.
 *
 * It is invisible while no list exists and reappears on every fresh `todo/write`, so its
 * collapse state resets with the data: a new list arrives collapsed, showing only the progress
 * summary. Expanding reveals the items; the close button hides the bar for the rest of this
 * list's life — a later write is a new list and brings the bar back.
 */
@Composable
internal fun TodoBar(
    todos: List<TodoItem>,
    dismissed: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (todos.isEmpty() || dismissed) return
    var expanded by remember(todos) { mutableStateOf(false) }
    val colors = DsTheme.colors
    val progress = buildString {
        val done = todos.count { it.status == "completed" }
        val active = todos.count { it.status == "in_progress" }
        val pending = todos.size - done - active
        if (done > 0) append(stringResource(R.string.todo_progress_done, done))
        if (active > 0) {
            if (isNotEmpty()) append(" · ")
            append(stringResource(R.string.todo_progress_active, active))
        }
        if (pending > 0) {
            if (isNotEmpty()) append(" · ")
            append(stringResource(R.string.todo_progress_pending, pending))
        }
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(0.5.dp, colors.borderL1, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = colors.tipSurface,
    ) {
        Column(modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    FeatherIcons.CheckSquare,
                    contentDescription = null,
                    tint = colors.labelTertiary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.chat_todo_title),
                    style = DsType.small13Strong,
                    color = colors.labelPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    progress,
                    style = DsType.small13,
                    color = colors.labelTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) FeatherIcons.ChevronUp else FeatherIcons.ChevronDown,
                    contentDescription = null,
                    tint = colors.labelTertiary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        FeatherIcons.X,
                        contentDescription = stringResource(R.string.todo_close),
                        tint = colors.labelTertiary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    todos.forEach { todo ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TodoStatusGlyph(todo.status)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                todo.content,
                                style = DsType.small13,
                                color = colors.labelSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The web panel's per-status ring: filled check, spinning progress arc, dashed pending. */
@Composable
private fun TodoStatusGlyph(status: String) {
    val colors = DsTheme.colors
    when (status) {
        "completed" -> Canvas(Modifier.size(14.dp)) {
            val stroke = 1.2f * density
            drawCircle(Color.Transparent, style = Stroke(stroke))
            drawArc(
                color = colors.success,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            val d = size.minDimension
            drawLine(
                color = colors.success,
                start = Offset(d * 0.28f, d * 0.54f),
                end = Offset(d * 0.45f, d * 0.71f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = colors.success,
                start = Offset(d * 0.45f, d * 0.71f),
                end = Offset(d * 0.72f, d * 0.43f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }

        "in_progress" -> {
            val transition = rememberInfiniteTransition(label = "todoProgress")
            val spin by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "todoProgressSpin",
            )
            Canvas(Modifier.size(14.dp)) {
                val stroke = 1.2f * density
                drawArc(
                    color = colors.accent,
                    startAngle = spin - 90f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }

        else -> Canvas(Modifier.size(14.dp)) {
            val stroke = 1.2f * density
            drawCircle(
                color = colors.labelCaption,
                style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.4f * density, 2.4f * density), 0f)),
            )
        }
    }
}

/** The read-only goal summary shown inline in the transcript when a goal event lands. */
@Composable
internal fun GoalSummary(goal: GoalSnapshot) {
    val colors = DsTheme.colors
    SectionHeader(stringResource(R.string.goal_title))
    DsPill(text = stringResource(goalPhaseLabelRes(goal.phase)))
    Spacer(Modifier.height(4.dp))
    Text(goal.objective, style = DsType.small13, color = colors.labelSecondary)
    goal.blockedReason?.let {
        Text(
            stringResource(R.string.goal_blocked_reason, it.message),
            style = DsType.caption11,
            color = colors.warnLabel,
        )
    }
}

/** The live goal bar above the composer, with its phase verbs. */
@Composable
internal fun GoalBar(goal: GoalSnapshot, store: SessionStore, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val colors = DsTheme.colors
    var editing by remember { mutableStateOf(false) }
    var editText by remember(goal.revision) { mutableStateOf(goal.objective) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StateDot(
            when (goal.phase) {
                GoalPhase.ACTIVE -> com.labteto.dshmobile.ui.components.StateDotState.Running
                GoalPhase.BLOCKED -> com.labteto.dshmobile.ui.components.StateDotState.Warning
                GoalPhase.COMPLETE -> com.labteto.dshmobile.ui.components.StateDotState.Done
                GoalPhase.PAUSED -> com.labteto.dshmobile.ui.components.StateDotState.Idle
            },
            size = 8.dp,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            goal.objective,
            style = DsType.small13,
            color = colors.labelSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        DsPill(text = stringResource(goalPhaseLabelRes(goal.phase)))
        Spacer(Modifier.width(4.dp))
        DsMenu(
            anchor = {
                Icon(
                    FeatherIcons.MoreVertical,
                    contentDescription = stringResource(R.string.goal_edit),
                    tint = colors.labelTertiary,
                    modifier = Modifier.size(20.dp),
                )
            },
            items = buildList {
                when (goal.phase) {
                    GoalPhase.ACTIVE -> add(
                        MenuItem(stringResource(R.string.goal_pause)) {
                            scope.launch { store.goalAction("pause") }
                        },
                    )
                    GoalPhase.PAUSED, GoalPhase.BLOCKED -> add(
                        MenuItem(stringResource(R.string.goal_resume)) {
                            scope.launch { store.goalAction("resume") }
                        },
                    )
                    GoalPhase.COMPLETE -> Unit
                }
                add(MenuItem(stringResource(R.string.goal_edit)) { editing = true })
                add(
                    MenuItem(stringResource(R.string.goal_clear), danger = true) {
                        scope.launch { store.goalAction("clear") }
                    },
                )
            },
        )
    }
    if (editing) {
        DsDialog(title = stringResource(R.string.goal_edit), onDismiss = { editing = false }) {
            TextField(
                value = editText,
                onValueChange = { editText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.goal_title), style = DsType.std14) },
                colors = dialogTextFieldColors(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DsButton(
                    text = stringResource(R.string.common_ok),
                    onClick = {
                        scope.launch { store.goalAction("edit", editText) }
                        editing = false
                    },
                    variant = DsButtonVariant.Info,
                )
                DsButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { editing = false },
                    variant = DsButtonVariant.Ghost,
                )
            }
        }
    }
}

/** Pending turns, with the edit / remove / steer verbs the harness exposes. */
@Composable
internal fun QueueDock(queue: List<QueueItem>, store: SessionStore, modifier: Modifier = Modifier, sessionId: String? = store.currentSessionId.value, blocked: Boolean = false, operationState: androidx.compose.runtime.MutableState<Boolean>? = null) {
    if (queue.isEmpty()) return
    val scope = rememberCoroutineScope()
    val colors = DsTheme.colors
    val busyState: androidx.compose.runtime.MutableState<Boolean> = operationState ?: remember(sessionId) { mutableStateOf(false) }
    var busy by busyState
    fun change(id: String, action: String, text: String? = null) {
        if (busy || blocked) return
        busy = true
        scope.launch { try { store.updateQueue(id, action, text, sessionId) } finally { busy = false } }
    }
    var expanded by remember(queue.size) { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }

    DisclosureRow(
        title = stringResource(R.string.chat_queue_title),
        summary = queue.size.toString(),
        expanded = expanded,
        onToggle = { expanded = !expanded },
        modifier = modifier,
    ) {
        DsButton(text = stringResource(R.string.chat_queue_steer), enabled = !busy && !blocked,
            onClick = {
                busy = true
                val ids = queue.map { it.id }
                scope.launch { try { ids.forEach { store.updateQueue(it, "steer", sessionId = sessionId) } } finally { busy = false } }
            }, variant = DsButtonVariant.Ghost)
        queue.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    item.previewText,
                    style = DsType.small13,
                    color = colors.labelSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                DsPill(text = item.placement)
                if (!busy && !blocked && item.placement != "steering") DsMenu(
                    anchor = {
                        Icon(
                            FeatherIcons.MoreVertical,
                            contentDescription = stringResource(R.string.chat_queue_edit),
                            tint = colors.labelTertiary,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    items = listOf(
                        MenuItem(stringResource(R.string.chat_queue_edit)) {
                            editingId = item.id
                            editText = (item.content as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? kotlinx.serialization.json.JsonObject)?.get("text").asString() }?.joinToString("\n") ?: item.previewText
                        },
                        MenuItem(stringResource(R.string.chat_queue_remove), danger = true) {
                            change(item.id, "remove")
                        },
                        MenuItem(stringResource(R.string.chat_queue_steer)) {
                            change(item.id, "steer")
                        },
                    ),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
    }

    editingId?.let { id ->
        DsDialog(title = stringResource(R.string.chat_queue_edit), onDismiss = { editingId = null }) {
            TextField(
                value = editText,
                onValueChange = { editText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.chat_composer_hint), style = DsType.std14) },
                colors = dialogTextFieldColors(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DsButton(
                    text = stringResource(R.string.common_ok),
                    enabled = editText.isNotBlank() && !busy && !blocked,
                    onClick = {
                        busy = true
                        scope.launch {
                            try { if (store.updateQueue(id, "edit", editText, sessionId)) editingId = null }
                            finally { busy = false }
                        }
                    },
                    variant = DsButtonVariant.Info,
                )
                DsButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { editingId = null },
                    variant = DsButtonVariant.Ghost,
                )
            }
        }
    }
}

