package com.labteto.dshmobile.ui.screens.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.R
import com.labteto.dshmobile.core.session.AssistantMessageNode
import com.labteto.dshmobile.core.session.ChatNode
import com.labteto.dshmobile.core.session.ConversationSnapshot
import com.labteto.dshmobile.core.session.ToolCallNode
import com.labteto.dshmobile.core.session.ToolResultNode
import com.labteto.dshmobile.core.session.UserMessageNode
import com.labteto.dshmobile.core.wire.dto.SessionStatsView
import com.labteto.dshmobile.core.wire.dto.TokenUsageView
import com.labteto.dshmobile.ui.components.DisclosureRow
import com.labteto.dshmobile.ui.components.SectionHeader
import com.labteto.dshmobile.ui.components.StateDot
import com.labteto.dshmobile.ui.components.StateDotState
import com.labteto.dshmobile.ui.components.formatDurationMs
import com.labteto.dshmobile.ui.components.formatTokens
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType

private val MonoCaption = DsType.caption11.copy(fontFamily = DsType.codeFont)

/**
 * The turn-by-turn ledger: what the agent did, in order, with its inputs and outputs.
 *
 * Where the Chat tab shows the conversation as it reads, this shows it as it *ran* — every tool
 * call with its arguments and result, grouped by turn. It replaces the cramped trajectory section
 * that used to sit inside the details panel; one home per fact.
 */
@Composable
internal fun TrajectoryTab(
    conversation: ConversationSnapshot?,
    stats: SessionStatsView?,
    usage: TokenUsageView?,
    cwd: String?,
    listState: LazyListState,
    /** Whether a turn is live right now; drives the running dot on in-flight calls. */
    running: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = DsTheme.colors
    val nodes = conversation?.nodes ?: emptyList()
    val groups = remember(nodes) { groupByTurn(nodes) }
    // Result lookup by call id, built once per snapshot instead of a filterIsInstance scan per
    // tool row per composition.
    val resultsByCallId = remember(nodes) {
        nodes.mapNotNull { it as? ToolResultNode }
            .filter { it.callId.isNotBlank() }
            .associateBy { it.callId }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (groups.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.trajectory_empty),
                    style = DsType.caption11,
                    color = colors.labelTertiary,
                )
            }
        }
        groups.forEach { (turn, turnNodes) ->
            item(key = "turn-$turn") {
                SectionHeader(
                    title = stringResource(R.string.trajectory_turn, turn),
                    action = stringResource(R.string.trajectory_steps, turnNodes.count { it is ToolCallNode }),
                )
            }
            items(
                count = turnNodes.size,
                key = { index -> "n-${turnNodes[index].seq}" },
            ) { index ->
                TrajectoryRow(turnNodes[index], resultsByCallId, cwd, running)
            }
        }
        if (stats != null || usage != null) {
            item(key = "totals") {
                Spacer(Modifier.width(8.dp))
                TrajectoryTotals(stats, usage)
            }
        }
    }
}

@Composable
private fun TrajectoryRow(
    node: ChatNode,
    resultsByCallId: Map<String, ToolResultNode>,
    cwd: String?,
    running: Boolean,
) {
    val colors = DsTheme.colors
    when (node) {
        is UserMessageNode -> {
            val preview = node.previewText.trim()
            if (preview.isNotEmpty()) {
                Text("> $preview", style = DsType.footnote, color = colors.labelSecondary)
            }
        }
        is AssistantMessageNode -> {
            val snippet = node.plainText.trim().take(160)
            if (snippet.isNotEmpty()) {
                Text(
                    snippet,
                    style = DsType.footnote,
                    color = colors.labelTertiary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        is ToolCallNode -> {
            ToolLedgerRow(node, resultsByCallId[node.callId], cwd, running)
        }
        else -> Unit
    }
}

@Composable
private fun ToolLedgerRow(call: ToolCallNode, result: ToolResultNode?, cwd: String?, running: Boolean) {
    val colors = DsTheme.colors
    val row = remember(call.callId, cwd) { toolRowModel(call.name, call.arguments, cwd) }
    var expanded by remember(call.callId) { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        StateDot(
            when {
                // Without a result the call is either still running or — after the turn ended,
                // or across a paging boundary — simply has no result row attached. Only the
                // live-turn case earns the chasing dot; a stale "running" on a finished turn
                // reads as the ledger never having settled.
                result == null && running -> StateDotState.Running
                result?.isError == true -> StateDotState.Error
                else -> StateDotState.Done
            },
            size = 8.dp,
        )
        Spacer(Modifier.width(6.dp))
        DisclosureRow(
            title = row.title,
            summary = row.summary,
            // The ledger already leads with its own state dot, so the slot keeps the glyph.
            icon = row.variant.featherIcon(),
            expanded = expanded,
            onToggle = { expanded = !expanded },
            modifier = Modifier.weight(1f),
        ) {
            Column(Modifier.padding(start = 28.dp, top = 2.dp)) {
                Text(stringResource(R.string.chat_input_placeholder), style = DsType.caption11, color = colors.labelCaption)
                Text(call.arguments, style = MonoCaption, color = colors.labelTertiary)
                result?.content?.let { content ->
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.chat_output_placeholder), style = DsType.caption11, color = colors.labelCaption)
                    Text(
                        content.toString(),
                        style = MonoCaption,
                        color = if (result.isError) colors.error else colors.labelTertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrajectoryTotals(stats: SessionStatsView?, usage: TokenUsageView?) {
    val colors = DsTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        SectionHeader(stringResource(R.string.trajectory_tab_usage))
        stats?.let {
            Text(
                stringResource(R.string.chat_stats_turns, it.turns, it.steps),
                style = DsType.footnote,
                color = colors.labelTertiary,
            )
            Text(
                stringResource(
                    R.string.chat_stats_timing,
                    formatDurationMs(it.llmMs),
                    formatDurationMs(it.toolMs),
                ),
                style = DsType.footnote,
                color = colors.labelTertiary,
            )
            Text(
                stringResource(
                    R.string.chat_stats_speed,
                    formatDurationMs(it.meanTtftMs),
                    it.tokensPerSecond?.let { rate -> String.format(java.util.Locale.US, "%.0f", rate) } ?: "—",
                ),
                style = DsType.footnote,
                color = colors.labelTertiary,
            )
        }
        usage?.let {
            Text(
                stringResource(
                    R.string.trajectory_usage,
                    formatTokens(it.inputTokens),
                    formatTokens(it.outputTokens),
                    formatTokens(it.cacheReadTokens),
                    formatTokens(it.cacheWriteTokens),
                ),
                style = DsType.footnote,
                color = colors.labelTertiary,
            )
        }
    }
}
