package com.labteto.dshmobile.ui.screens.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Groups
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
import com.labteto.dshmobile.core.wire.dto.SessionModelsValue
import com.labteto.dshmobile.ui.components.DsIconButton
import com.labteto.dshmobile.ui.components.DsSegment
import com.labteto.dshmobile.ui.components.DsSegmented
import com.labteto.dshmobile.ui.components.FeatherIcons
import com.labteto.dshmobile.ui.components.StateDot
import com.labteto.dshmobile.ui.components.TranscriptSearchBar
import com.labteto.dshmobile.ui.components.StateDotState
import com.labteto.dshmobile.ui.components.skeleton
import com.labteto.dshmobile.ui.theme.DsAnimations
import com.labteto.dshmobile.ui.theme.DsShapes
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType

/** The two views of a session the harness offers. */
internal enum class ChatTab { Chat, Trajectory }

/**
 * The session chrome: a two-row bar plus the Chat / Trajectory tabs.
 *
 * Row one carries the controls that belong to the *connection* — the drawer, the model, the live
 * status. Row two carries the ones that belong to the *session* — its title, its agent preset, its
 * subagents. Splitting them is what makes room for the model selector on the left without eliding
 * the session title down to nothing on a phone.
 *
 * The title and the chips share row two rather than stacking, and the row disappears entirely when
 * it would be empty: four stacked rows of chrome over a white page ate a third of a phone screen
 * before the first message, and the chip row kept its padding even with no chips to pad.
 */
@Composable
internal fun ChatTopBar(
    title: String,
    running: Boolean,
    models: SessionModelsValue?,
    agentPresetLabel: String?,
    subagentCount: Int,
    detailsOpen: Boolean,
    tab: ChatTab,
    onOpenDrawer: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenPresets: () -> Unit,
    onOpenSubagents: () -> Unit,
    onOpenDetails: () -> Unit,
    onTabChange: (ChatTab) -> Unit,
    /** Opens the session's workspace file panel; null hides the button. */
    onOpenWorkspace: (() -> Unit)? = null,
    /** Transcript search: the query, where the cursor sits, and how many messages matched. */
    searchQuery: String = "",
    searchPosition: Int = 0,
    searchCount: Int = 0,
    onSearchQueryChange: (String) -> Unit = {},
    onSearchPrevious: () -> Unit = {},
    onSearchNext: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = DsTheme.colors
    Column(modifier.fillMaxWidth().background(colors.bgBase)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = DsSpacing.tiny),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DsIconButton(
                icon = FeatherIcons.Menu,
                contentDescription = stringResource(R.string.chatlist_open),
                onClick = onOpenDrawer,
                tint = colors.labelSecondary,
                iconSize = 18.dp,
            )
            // The session title lives here, on the identity row, rather than on a row of its own
            // below: it is what the screen *is*, and a separate row spent a whole line of a phone
            // screen restating it. It shares the row with the controls and ellipsises, so a long
            // title can never push the buttons off.
            if (title.isNotBlank()) {
                Text(
                    title,
                    // A step up from the row's other text: the title is the one piece of prose in
                    // the header, so it carries the emphasis rather than matching the controls.
                    style = DsType.base16Strong,
                    color = colors.labelPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = DsSpacing.tiny, end = DsSpacing.tiny),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            // The workspace panel is a destination, not a label: an icon frees the line of text it
            // used to occupy above the transcript while staying one tap away.
            if (onOpenWorkspace != null) {
                DsIconButton(
                    icon = FeatherIcons.Folder,
                    contentDescription = stringResource(R.string.panel_workspace),
                    onClick = onOpenWorkspace,
                    tint = colors.labelTertiary,
                    iconSize = 18.dp,
                )
            }
            StateDot(if (running) StateDotState.Running else StateDotState.Idle)
            if (!detailsOpen) {
                DsIconButton(
                    icon = FeatherIcons.Info,
                    contentDescription = stringResource(R.string.chat_details_title),
                    onClick = onOpenDetails,
                    tint = colors.labelTertiary,
                    iconSize = 18.dp,
                )
            }
        }

        val hasChips = agentPresetLabel != null || subagentCount > 0
        // The chips row appears only when there is a chip to show: the title moved up, so a row
        // holding nothing but empty space would be a blank band under the header.
        if (hasChips) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DsSpacing.medium, vertical = DsSpacing.tiny),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DsSpacing.tiny),
            ) {
                if (agentPresetLabel != null) {
                    MetaChip(
                        icon = Icons.Outlined.Dashboard,
                        label = agentPresetLabel,
                        onClick = onOpenPresets,
                    )
                }
                if (subagentCount > 0) {
                    MetaChip(
                        icon = Icons.Outlined.Groups,
                        label = "$subagentCount",
                        onClick = onOpenSubagents,
                    )
                }
            }
        }

        ChatTabRow(
            tab = tab,
            onTabChange = onTabChange,
            searchQuery = searchQuery,
            searchPosition = searchPosition,
            searchCount = searchCount,
            onSearchQueryChange = onSearchQueryChange,
            onSearchPrevious = onSearchPrevious,
            onSearchNext = onSearchNext,
        )
    }
}

/**
 * The model chip: display names, not wire ids.
 *
 * `session.models` returns `deepseek-official / deepseek-v4-pro / max`, which is not what anyone
 * calls it — the catalog's own names resolve that to `DeepSeek-V4-Pro Max`.
 *
 * Drawn as a filled pill rather than the harness's transparent trigger. That is not a style
 * preference: on the web the affordance is the hover state, and a touch screen has no hover, so
 * bare text over the transcript gave no sign the model was switchable at all.
 */
@Composable
internal fun ModelChip(
    models: SessionModelsValue?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Drop the *leading* part of an over-long model name rather than the tail.
     *
     * Model ids are long and their distinguishing part is at the end (`…/Qwen3.8-27B-Q4_K_M` and
     * `…/Qwen3.8-27B-Q8_0` differ only there), so the usual trailing ellipsis hides exactly the
     * characters a reader needs. The composer's narrow slot is where this matters.
     */
    truncateLeading: Boolean = false,
) {
    val colors = DsTheme.colors
    if (models == null) {
        Box(
            modifier
                .padding(horizontal = DsSpacing.small)
                .width(120.dp)
                .height(14.dp)
                .skeleton(colors.bgLayer2, colors.hover),
        )
        return
    }
    val current = models.current
    val group = models.groups.firstOrNull { it.id == current.provider }
    val model = group?.models?.firstOrNull { it.id == current.model }
    val effort = model?.reasoning?.efforts?.firstOrNull { it.id == current.reasoningEffort }
    val modelLabel = model?.name ?: current.model

    Row(
        modifier = modifier
            // No width cap of its own: the caller decides. In the composer the chip takes the
            // leftover width, and capping it at 240dp there would leave a gap the round buttons
            // then had to be pushed across by a competing spacer.
            .heightIn(min = 28.dp)
            .clip(DsShapes.pillFull)
            .background(colors.hoverSolid)
            .border(1.dp, colors.borderL2, DsShapes.pillFull)
            .clickable(onClick = onClick)
            .padding(horizontal = DsSpacing.compact, vertical = DsSpacing.tiny),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.tiny),
    ) {
        if (!models.routable) {
            StateDot(StateDotState.Warning, size = 6.dp)
        }
        Text(
            if (truncateLeading) modelLabel.trimStartForDisplay() else modelLabel,
            style = DsType.small13Strong,
            color = colors.labelPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        effort?.let {
            Text(it.name, style = DsType.small13, color = colors.labelTertiary, maxLines = 1)
        }
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = colors.labelSecondary,
            modifier = Modifier.size(14.dp),
        )
    }
}


/**
 * Trim a model name's head so its tail survives.
 *
 * This is a coarse cut, not a measured one: Compose will still ellipsise whatever is left if it
 * does not fit, and measuring per-glyph would mean a layout pass per keystroke for a label that
 * changes once a session. Keeping the last [MODEL_LABEL_TAIL] characters covers the case that
 * actually occurs — a provider path in front of a short, meaningful model id — while a name that
 * is short throughout passes through untouched.
 */
private const val MODEL_LABEL_TAIL = 16

private fun String.trimStartForDisplay(): String {
    val slash = lastIndexOf('/')
    // A provider prefix is the part worth dropping first, and it is unambiguous when present.
    if (slash >= 0 && slash < length - 1) {
        val tail = substring(slash + 1)
        if (tail.length <= MODEL_LABEL_TAIL) return "\u2026$tail"
    }
    return if (length <= MODEL_LABEL_TAIL + 4) this else "\u2026" + takeLast(MODEL_LABEL_TAIL)
}

/** The preset and subagent chips. Same reasoning as [ModelChip]: a tap target has to look like one. */
@Composable
private fun MetaChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val colors = DsTheme.colors
    Row(
        modifier = Modifier
            .heightIn(min = 28.dp)
            .clip(DsShapes.pillFull)
            .background(colors.hoverSolid)
            .border(1.dp, colors.borderL2, DsShapes.pillFull)
            .clickable(onClick = onClick)
            .padding(horizontal = DsSpacing.compact, vertical = DsSpacing.tiny),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.tiny),
    ) {
        Icon(icon, contentDescription = null, tint = colors.labelTertiary, modifier = Modifier.size(14.dp))
        Text(label, style = DsType.small13, color = colors.labelSecondary, maxLines = 1)
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = colors.labelSecondary,
            modifier = Modifier.size(12.dp),
        )
    }
}

/**
 * The tab strip, as a compact segmented control rather than underlined tabs.
 *
 * Two short labels sitting over a full-width underline read as a page heading and cost a row of
 * their own; a 28dp track wraps to the labels and lets the chrome end there.
 */
@Composable
private fun ChatTabRow(
    tab: ChatTab,
    onTabChange: (ChatTab) -> Unit,
    searchQuery: String,
    searchPosition: Int,
    searchCount: Int,
    onSearchQueryChange: (String) -> Unit,
    onSearchPrevious: () -> Unit,
    onSearchNext: () -> Unit,
) {
    val colors = DsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DsSpacing.medium, vertical = DsSpacing.tiny),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.tiny),
    ) {
        // Which tab is live is load-bearing, not decoration: the two views render a user message
        // completely differently — a right-aligned bubble in Chat, a `> line` of caption text in
        // the Trajectory ledger — so a reader who cannot tell at a glance concludes the chat itself
        // is broken.
        DsSegmented(
            // 40/60: "Chat" is the shorter label, so it keeps a compact hit target while
            // "Trajectory" gets the room its longer word needs. An even split ellipsised the
            // longer one to buy the shorter one space it had no use for.
            segments = listOf(
                DsSegment(TAB_CHAT, stringResource(R.string.chat_tab), weight = 0.4f),
                DsSegment(TAB_TRAJECTORY, stringResource(R.string.trajectory_title), weight = 0.6f),
            ),
            selectedKey = if (tab == ChatTab.Chat) TAB_CHAT else TAB_TRAJECTORY,
            onSelect = { key ->
                onTabChange(if (key == TAB_CHAT) ChatTab.Chat else ChatTab.Trajectory)
            },
            role = Role.Tab,
            // Stretched, not hugged: which view is live is a decision the reader keeps making, so
            // the control gets a primary control's height rather than an inline chip's.
            //
            // 144dp, down from 240dp. The floor is set by the two labels, not by taste: at 13sp
            // "Chat" needs ~28dp and "Trajectory" ~60dp of text, plus 8dp of padding and the
            // track's own 6dp of chrome and gap. A 40/60 split of the inner width therefore gives
            // Trajectory ~83dp at 144dp and ~76dp at 132dp — the latter ellipsises, so anything
            // under 144 means "Trajector…". Narrower than that has to come out of the 40/60 ratio,
            // not out of the track.
            stretch = true,
            modifier = Modifier
                .widthIn(max = 144.dp)
                .heightIn(max = 32.dp),
        )
        // The transcript search is a permanent resident of the utility row — it belongs to the
        // session, not to either view, so switching tabs never takes it away. Capped at 320dp and
        // pushed to the right edge: on a wide screen it does not stretch into an absurd pill, and
        // the eye finds it in the same place every time.
        TranscriptSearchBar(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            matchPosition = searchPosition,
            matchCount = searchCount,
            onPrevious = onSearchPrevious,
            onNext = onSearchNext,
            modifier = Modifier.weight(1f).widthIn(max = 320.dp),
        )
    }
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(colors.borderL1),
    )
}

private const val TAB_CHAT = "chat"
private const val TAB_TRAJECTORY = "trajectory"
