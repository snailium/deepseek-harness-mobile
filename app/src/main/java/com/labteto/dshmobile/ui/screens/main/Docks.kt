package com.labteto.dshmobile.ui.screens.main

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.R
import com.labteto.dshmobile.core.session.QueueItem
import com.labteto.dshmobile.core.wire.dto.GoalPhase
import com.labteto.dshmobile.core.wire.dto.GoalSnapshot
import com.labteto.dshmobile.core.wire.dto.SessionStatsView
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
internal fun QueueDock(queue: List<QueueItem>, store: SessionStore, modifier: Modifier = Modifier) {
    if (queue.isEmpty()) return
    val scope = rememberCoroutineScope()
    val colors = DsTheme.colors
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
                DsMenu(
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
                            editText = item.previewText
                        },
                        MenuItem(stringResource(R.string.chat_queue_remove), danger = true) {
                            scope.launch { store.updateQueue(item.id, "remove") }
                        },
                        MenuItem(stringResource(R.string.chat_queue_steer)) {
                            scope.launch { store.updateQueue(item.id, "steer") }
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
                    onClick = {
                        scope.launch { store.updateQueue(id, "edit", editText) }
                        editingId = null
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

