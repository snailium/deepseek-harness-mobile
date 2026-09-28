package com.labteto.dshmobile.ui.screens.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.R
import com.labteto.dshmobile.core.wire.dto.AgentPresetComposition
import com.labteto.dshmobile.core.wire.dto.PluginFiberPhase
import com.labteto.dshmobile.core.wire.dto.PluginInventoryEntry
import com.labteto.dshmobile.core.wire.dto.PluginInventorySnapshot
import com.labteto.dshmobile.ui.components.DsIconButton
import com.labteto.dshmobile.ui.components.DsSegment
import com.labteto.dshmobile.ui.components.DsSegmented
import com.labteto.dshmobile.ui.components.StateDot
import com.labteto.dshmobile.ui.components.StateDotState
import com.labteto.dshmobile.ui.theme.DsShapes
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsTitleBar
import com.labteto.dshmobile.ui.theme.DsType
import java.util.Locale

/**
 * The deployment's built-in plugins — the Cordis loader tree, read-only.
 *
 * This is the harness's Settings -> "Built-in plugins" tab: `pluginInventory/list`'s composed tree
 * plus each agent preset's composition. It is an inspection view and stays one. The editable plugin
 * surface is the *bundle* page ([PluginsScreen]), which is what the harness's own top-level Plugins
 * page offers and what the drawer's plugin button opens.
 *
 * The separation matters. This list is flat and ~200 rows long, and most rows report themselves as
 * individually togglable — but `include:llm` is one of those rows and roughly two dozen packages
 * inject the `llm` service, so turning that one off stops the harness from booting. The harness does
 * not put a switch on this list either, and neither does this page: the rows are here to be
 * inspected and named, not edited.
 */
@Composable
fun BuiltInPluginsScreen(
    inventory: PluginInventorySnapshot,
    onClose: () -> Unit,
) {
    val colors = DsTheme.colors
    var filter by remember { mutableStateOf("") }
    var page by remember { mutableStateOf(PluginPage.COMPOSED) }
    var selectedPreset by remember { mutableStateOf(inventory.agentPresets.firstOrNull()?.id) }

    // The composed list is the live tree; a preset tab shows what that preset *would* mount. Both
    // answer to the same filter, so the query is applied to whichever set is on screen rather than
    // being reset on every tab change.
    val rows = remember(inventory, page, selectedPreset) {
        when (page) {
            PluginPage.COMPOSED -> inventory.entries
            PluginPage.PRESETS ->
                inventory.agentPresets.firstOrNull { it.id == selectedPreset }?.rows.orEmpty()
        }
    }
    val matching = remember(rows, filter) {
        val q = filter.trim()
        if (q.isEmpty()) rows else rows.filter { it.matches(q) }
    }
    val listState = rememberLazyListState()

    Surface(modifier = Modifier.fillMaxSize(), color = colors.bgBase) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = DsSpacing.comfortable, vertical = DsSpacing.medium),
        ) {
            PluginsTitleBar(onClose)

            if (inventory.agentPresets.isNotEmpty()) {
                DsSegmented(
                    segments = listOf(
                        DsSegment(PluginPage.COMPOSED.key, stringResource(R.string.plugins_tab_composed)),
                        DsSegment(PluginPage.PRESETS.key, stringResource(R.string.plugins_tab_presets)),
                    ),
                    selectedKey = page.key,
                    onSelect = { key ->
                        page = PluginPage.entries.first { it.key == key }
                        listState.requestScrollToItem(0)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    stretch = true,
                )
            }

            if (page == PluginPage.PRESETS) {
                PresetStrip(
                    presets = inventory.agentPresets,
                    selected = selectedPreset,
                    onSelect = { id ->
                        selectedPreset = id
                        listState.requestScrollToItem(0)
                    },
                )
            }

            Spacer(Modifier.height(DsSpacing.small))

            TextField(
                value = filter,
                onValueChange = { filter = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(stringResource(R.string.plugins_search_hint), style = DsType.std14)
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.bgLayer2,
                    unfocusedContainerColor = colors.bgLayer2,
                    focusedIndicatorColor = colors.accent,
                    unfocusedIndicatorColor = colors.borderL2,
                    cursorColor = colors.accent,
                ),
            )

            Spacer(Modifier.height(DsSpacing.small))

            // Counted above the list rather than below it: the number is what tells you whether the
            // filter is worth clearing, and a footer is off screen once the list is long.
            Text(
                stringResource(R.string.plugins_count, matching.size, rows.size),
                style = DsType.caption11,
                color = colors.labelTertiary,
            )

            Spacer(Modifier.height(DsSpacing.xsmall))

            if (matching.isEmpty()) {
                Text(
                    stringResource(
                        if (rows.isEmpty()) R.string.plugins_empty else R.string.plugins_no_match,
                    ),
                    style = DsType.small13,
                    color = colors.labelTertiary,
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.xsmall),
                ) {
                    items(matching, key = { it.entryId }) { entry -> PluginCard(entry) }
                    item { Spacer(Modifier.height(DsSpacing.xlarge)) }
                }
            }
        }
    }
}

/** Which set of rows the page is showing. */
private enum class PluginPage(val key: String) {
    COMPOSED("composed"),
    PRESETS("presets"),
}

/** The page's own title bar, matching every other screen's 44dp row. */
@Composable
private fun PluginsTitleBar(onClose: () -> Unit) {
    val colors = DsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DsTitleBar.height)
            .padding(top = DsSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DsTitleBar.iconGap),
    ) {
        DsIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.common_back),
            onClick = onClose,
            tint = colors.labelTertiary,
            iconSize = DsTitleBar.iconSize,
            touchTarget = DsTitleBar.iconTouchTarget,
        )
        Text(
            stringResource(R.string.settings_plugins),
            style = DsTitleBar.titleStyle,
            color = colors.labelPrimary,
        )
    }
}

/**
 * The preset picker, as a horizontal row of pills.
 *
 * A `DsSegmented` would be wrong here: the count varies by profile, and four presets already
 * overrun a phone's width. Pills that scroll keep every preset reachable at any count.
 */
@Composable
private fun PresetStrip(
    presets: List<AgentPresetComposition>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    val colors = DsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = DsSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.xsmall),
    ) {
        presets.forEach { preset ->
            val active = preset.id == selected
            Text(
                text = preset.id,
                style = if (active) DsType.small13Strong else DsType.small13,
                color = if (active) colors.labelPrimary else colors.labelTertiary,
                maxLines = 1,
                modifier = Modifier
                    .clip(DsShapes.pillFull)
                    .background(if (active) colors.accentTertiary else colors.hoverSolid)
                    .clickable { onSelect(preset.id) }
                    .padding(horizontal = DsSpacing.medium, vertical = DsSpacing.xsmall),
            )
        }
    }
}

/**
 * One plugin, as a card.
 *
 * The name line prefers the manifest title, then the shortened module name — which are the same
 * string on nearly every row, but a package that declares a friendlier title gets to use it. The
 * module specifier stays visible underneath, because it is what `cordis.patch.yml` actually names.
 */
@Composable
private fun PluginCard(entry: PluginInventoryEntry) {
    val colors = DsTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.row)
            .background(colors.bgLayer1)
            .padding(DsSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.tiny),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                moduleShortName(entry.displayTitle),
                style = DsType.small13Strong,
                color = colors.labelPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(DsSpacing.xsmall))
            PluginState(entry)
        }
        Text(
            entry.moduleName,
            style = DsType.caption11,
            color = colors.labelCaption,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // The description is what the sheet never had: without it every row is a package name and
        // the reader has to know the ecosystem to tell one from another.
        entry.meta?.description?.resolve()?.let { description ->
            Text(
                description,
                style = DsType.caption11,
                color = colors.labelTertiary,
            )
        }
    }
}

/**
 * A row's lifecycle state, as a dot and a word.
 *
 * Enablement and mount phase are two different facts and only one of them is ever interesting: a
 * disabled plugin is disabled whether or not it ever mounted, so the phase is reported only for
 * the ones the composition asked for.
 */
@Composable
private fun PluginState(entry: PluginInventoryEntry) {
    val colors = DsTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        StateDot(
            when {
                !entry.enabled -> StateDotState.Idle
                else -> when (entry.fiberPhase) {
                    PluginFiberPhase.ACTIVE -> StateDotState.Done
                    PluginFiberPhase.LOADING, PluginFiberPhase.UNLOADING -> StateDotState.Running
                    PluginFiberPhase.FAILED -> StateDotState.Error
                    PluginFiberPhase.PENDING, null -> StateDotState.Idle
                }
            },
        )
        Spacer(Modifier.width(DsSpacing.tiny))
        Text(
            stringResource(
                if (!entry.enabled) {
                    R.string.plugins_disabled
                } else {
                    pluginPhaseLabel(entry.fiberPhase)
                },
            ),
            style = DsType.caption11,
            color = if (entry.enabled && entry.fiberPhase == PluginFiberPhase.FAILED) {
                colors.error
            } else {
                colors.labelTertiary
            },
        )
    }
}

/**
 * Whether a row answers a query, comparing case-insensitively.
 *
 * The entry id is matched as well as the visible fields, because the entry id is what
 * `cordis.patch.yml` names — pasting one into the box is the natural way to find the row it
 * configures. Both sides are lower-cased here rather than by the caller: the list is a few hundred
 * rows and the whole filter re-runs on each keystroke, but a contract that requires the caller to
 * remember is one a later call site gets wrong silently.
 */
internal fun PluginInventoryEntry.matches(query: String): Boolean {
    val q = query.trim().lowercase(Locale.ROOT)
    if (q.isEmpty()) return true
    return moduleName.lowercase(Locale.ROOT).contains(q) ||
        entryId.lowercase(Locale.ROOT).contains(q) ||
        meta?.title?.resolve()?.lowercase(Locale.ROOT)?.contains(q) == true ||
        meta?.description?.resolve()?.lowercase(Locale.ROOT)?.contains(q) == true
}

@Composable
internal fun pluginPhaseLabel(phase: PluginFiberPhase?): Int = when (phase) {
    PluginFiberPhase.PENDING -> R.string.plugins_phase_pending
    PluginFiberPhase.LOADING -> R.string.plugins_phase_loading
    PluginFiberPhase.ACTIVE -> R.string.plugins_phase_active
    PluginFiberPhase.FAILED -> R.string.plugins_phase_failed
    PluginFiberPhase.UNLOADING -> R.string.plugins_phase_unloading
    null -> R.string.plugins_phase_unmounted
}

