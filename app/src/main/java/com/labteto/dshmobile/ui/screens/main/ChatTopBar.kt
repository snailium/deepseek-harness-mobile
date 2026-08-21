package com.labteto.dshmobile.ui.screens.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.R
import com.labteto.dshmobile.ui.components.DsIconButton
import com.labteto.dshmobile.ui.components.DsMenu
import com.labteto.dshmobile.ui.components.DsSegment
import com.labteto.dshmobile.ui.components.DsSegmented
import com.labteto.dshmobile.ui.components.FeatherIcons
import com.labteto.dshmobile.ui.components.MenuItem
import com.labteto.dshmobile.ui.components.StateDot
import com.labteto.dshmobile.ui.components.StateDotState
import com.labteto.dshmobile.ui.theme.DsAnimations
import com.labteto.dshmobile.ui.theme.DsShapes
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType

/** The two views of a session the harness offers. */
internal enum class ChatTab { Chat, Trajectory }

/**
 * The conversation chrome: a large-title navigation row over a utility row.
 *
 * The back arrow pops to the home shell (Chats · Active · Settings). The title collapses from 28sp
 * to the 17sp navigation size once the reader scrolls, and the host line fades — "which session am
 * I in" stays on screen at every scroll position. The status dot rides the same row, and a trailing
 * overflow carries Presets, Subagents and Switch harness. The model selector lives above the
 * composer, where the model it switches configures the next turn.
 * The utility row carries the Chat / Trajectory view switcher, which never folds, with the
 * session's agent preset and subagent chips in its trailing space — those fold away once the
 * reader scrolls, like the host line.
 */
@Composable
internal fun ChatTopBar(
    title: String,
    running: Boolean,
    hostLabel: String?,
    agentPresetLabel: String?,
    subagentCount: Int,
    tab: ChatTab,
    /** True once the reader scrolls the transcript: the session-meta row folds away. */
    collapsed: Boolean,
    modelLabel: String?,
    modelsRoutable: Boolean,
    /** Opens the model picker; the choice configures the next turn. */
    onOpenModels: () -> Unit,
    /** Pops back to the home shell (Chats · Active · Settings). */
    onBack: () -> Unit,
    onOpenPresets: () -> Unit,
    onOpenSubagents: () -> Unit,
    onSwitchHost: () -> Unit,
    onTabChange: (ChatTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DsTheme.colors
    Column(modifier.fillMaxWidth().background(colors.bgChat)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = DsSpacing.tiny)
                .animateContentSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DsIconButton(
                icon = FeatherIcons.ArrowLeft,
                contentDescription = stringResource(R.string.common_back),
                onClick = onBack,
                tint = colors.labelSecondary,
                iconSize = 18.dp,
                mirrorForRtl = true,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = DsSpacing.tiny),
            ) {
                Text(
                    title,
                    style = if (collapsed) DsType.navTitle else DsType.largeTitle,
                    color = colors.labelPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // The host line belongs to the expanded state: once the reader is deep in the
                // transcript the navigation title takes over, like iOS collapsing a large title.
                AnimatedVisibility(
                    visible = !collapsed && hostLabel != null,
                    enter = fadeIn(DsAnimations.fade),
                    exit = fadeOut(DsAnimations.fade),
                ) {
                    hostLabel?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StateDot(StateDotState.Done, size = 6.dp)
                            Spacer(Modifier.width(DsSpacing.xsmall))
                            Text(
                                it,
                                style = DsType.footnote,
                                color = colors.labelTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            if (modelLabel != null) {
                ModelChip(
                    label = modelLabel,
                    routable = modelsRoutable,
                    onClick = onOpenModels,
                )
            }
            Spacer(Modifier.width(DsSpacing.tiny))
            StateDot(
                if (running) StateDotState.Running else StateDotState.Idle,
                contentDescription = stringResource(
                    if (running) R.string.status_running else R.string.status_idle,
                ),
            )
            DsMenu(
                anchor = {
                    Icon(
                        FeatherIcons.MoreVertical,
                        contentDescription = stringResource(R.string.chatlist_session_actions),
                        tint = colors.labelTertiary,
                        modifier = Modifier.size(18.dp),
                    )
                },
                items = listOf(
                    MenuItem(stringResource(R.string.presets_title)) { onOpenPresets() },
                    MenuItem(stringResource(R.string.subagents_title)) { onOpenSubagents() },
                    MenuItem(stringResource(R.string.chatlist_switch_host)) { onSwitchHost() },
                ),
            )
        }

        val hasChips = agentPresetLabel != null || subagentCount > 0
        // One utility row instead of two: the view switcher is always visible, and the
        // turn-configuration chips share its trailing space. The chips fold away once the reader
        // scrolls — they configure the turn, which a reader mid-way through a long transcript is
        // not doing — and returning to the top brings them back. The tabs never fold: switching
        // views is navigation, not configuration, so it has to stay reachable mid-read.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DsSpacing.medium, vertical = DsSpacing.tiny),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DsSpacing.tiny),
        ) {
            ChatTabRow(tab = tab, onTabChange = onTabChange)
            AnimatedVisibility(
                visible = !collapsed && hasChips,
                enter = fadeIn(DsAnimations.fade),
                exit = fadeOut(DsAnimations.fade),
                // The slot stays reserved at its weight share, so the tabs never shift when the
                // chips fade in or out; long chip sets scroll within their own strip.
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DsSpacing.tiny),
                ) {
                    if (agentPresetLabel != null) {
                        MetaChip(
                            icon = FeatherIcons.Layout,
                            label = agentPresetLabel,
                            onClick = onOpenPresets,
                        )
                    }
                    if (subagentCount > 0) {
                        MetaChip(
                            icon = FeatherIcons.Users,
                            label = "$subagentCount",
                            onClick = onOpenSubagents,
                            semanticsLabel = stringResource(R.string.subagents_title),
                        )
                    }
                }
            }
        }

        Spacer(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.borderL1),
        )
    }
}

/** The preset and subagent chips: a tap target has to look like one. */
@Composable
private fun MetaChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    /** What the chip does, for assistive tech; the [label] may be a bare count. */
    semanticsLabel: String = label,
) {
    val colors = DsTheme.colors
    Row(
        modifier = Modifier
            .heightIn(min = 28.dp)
            .clip(DsShapes.pillFull)
            .background(colors.hoverSolid)
            .border(1.dp, colors.borderL2, DsShapes.pillFull)
            .clickable(
                role = Role.Button,
                onClickLabel = semanticsLabel,
                onClick = onClick,
            )
            .padding(horizontal = DsSpacing.compact, vertical = DsSpacing.tiny),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.tiny),
    ) {
        Icon(icon, contentDescription = null, tint = colors.labelTertiary, modifier = Modifier.size(14.dp))
        Text(label, style = DsType.small13, color = colors.labelSecondary, maxLines = 1)
        Icon(
            FeatherIcons.ChevronDown,
            contentDescription = null,
            tint = colors.labelSecondary,
            modifier = Modifier.size(12.dp),
        )
    }
}

/**
 * The model selector, as a prominent chip on the identity row.
 *
 * The model choice configures the next turn, so it earns a first-class place in the chrome instead
 * of hiding above the composer. The non-routable warning dot carries over from the old placement.
 */
@Composable
private fun ModelChip(
    label: String,
    routable: Boolean,
    onClick: () -> Unit,
) {
    val colors = DsTheme.colors
    Row(
        modifier = Modifier
            .heightIn(min = 32.dp)
            .clip(DsShapes.pillFull)
            .background(colors.hoverSolid)
            .border(1.dp, colors.borderL2, DsShapes.pillFull)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.models_title),
                onClick = onClick,
            )
            .padding(horizontal = DsSpacing.compact, vertical = DsSpacing.tiny),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.tiny),
    ) {
        if (!routable) {
            StateDot(StateDotState.Warning, size = 6.dp)
        }
        Text(label, style = DsType.small13Strong, color = colors.labelPrimary, maxLines = 1)
        Icon(
            FeatherIcons.ChevronDown,
            contentDescription = null,
            tint = colors.labelSecondary,
            modifier = Modifier.size(12.dp),
        )
    }
}

/**
 * The view switcher, as a compact segmented control rather than underlined tabs.
 *
 * Two short labels sitting over a full-width underline read as a page heading and cost a row of
 * their own; a 28dp track wraps to the labels and lets the chrome end there. The track sits
 * inline on the utility row next to the session-meta chips, keeping the header at two rows.
 */
@Composable
private fun ChatTabRow(tab: ChatTab, onTabChange: (ChatTab) -> Unit) {
    // Which tab is live is load-bearing, not decoration: the two views render a user message
    // completely differently — a right-aligned bubble in Chat, a `> line` of caption text in
    // the Trajectory ledger — so a reader who cannot tell at a glance concludes the chat itself
    // is broken.
    DsSegmented(
        segments = listOf(
            DsSegment(TAB_CHAT, stringResource(R.string.chat_tab)),
            DsSegment(TAB_TRAJECTORY, stringResource(R.string.trajectory_title)),
        ),
        selectedKey = if (tab == ChatTab.Chat) TAB_CHAT else TAB_TRAJECTORY,
        onSelect = { key ->
            onTabChange(if (key == TAB_CHAT) ChatTab.Chat else ChatTab.Trajectory)
        },
        role = Role.Tab,
    )
}

private const val TAB_CHAT = "chat"
private const val TAB_TRAJECTORY = "trajectory"
