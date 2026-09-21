package com.labteto.dshmobile.ui.screens.main

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.labteto.dshmobile.core.wire.dto.GoalSnapshot
import com.labteto.dshmobile.core.wire.dto.SessionStatsView
import com.labteto.dshmobile.core.wire.dto.TodoItem
import com.labteto.dshmobile.core.wire.dto.TokenUsageView
import com.labteto.dshmobile.ui.components.DisclosureRow
import com.labteto.dshmobile.ui.components.DsPill
import com.labteto.dshmobile.ui.components.FeatherIcons
import com.labteto.dshmobile.ui.components.SectionHeader
import com.labteto.dshmobile.ui.components.StateDot
import com.labteto.dshmobile.ui.components.formatDurationMs
import com.labteto.dshmobile.ui.components.formatTokens
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
                    .padding(start = DsSpacing.disclosureBodyIndent, top = 2.dp),
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

/**
 * The run statistics line under the transcript.
 *
 * Two of these numbers are aggregates on the wire, not averages: `ttftMs` is a *sum* across
 * `ttftSteps`, and there is no throughput field at all — it comes from decoded tokens over decode
 * milliseconds. Printing them raw would have shown a time-to-first-token of several minutes.
 */
@Composable
internal fun StatsFooter(
    stats: SessionStatsView?,
    usage: TokenUsageView?,
    modifier: Modifier = Modifier,
) {
    if (stats == null && usage == null) return
    val colors = DsTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val parts = buildList {
        stats?.let {
            add(stringResource(R.string.chat_stats_turns, it.turns, it.steps))
            add(
                stringResource(
                    R.string.chat_stats_speed,
                    formatDurationMs(it.meanTtftMs),
                    it.tokensPerSecond?.let { rate -> String.format(java.util.Locale.US, "%.0f", rate) } ?: "—",
                ),
            )
        }
        usage?.cacheHitRatio?.let { add(stringResource(R.string.chat_stats_cache, (it * 100).toInt())) }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Text(
            parts.joinToString(" · "),
            style = DsType.statsText,
            color = colors.labelCaption,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
        )
        if (expanded) {
            stats?.let {
                DetailLine(
                    stringResource(
                        R.string.chat_stats_timing,
                        formatDurationMs(it.llmMs),
                        formatDurationMs(it.toolMs),
                    ),
                )
            }
            usage?.let {
                DetailLine(
                    stringResource(
                        R.string.chat_stats_tokens,
                        formatTokens(it.inputTokens),
                        formatTokens(it.outputTokens),
                    ),
                )
            }
        }
    }
}

@Composable
private fun DetailLine(text: String) {
    Text(
        text,
        style = DsType.statsText,
        color = DsTheme.colors.labelCaption,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

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
        Column(modifier.padding(horizontal = DsSpacing.small, vertical = 3.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .heightIn(min = 22.dp)
                    .padding(vertical = 2.dp),
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
