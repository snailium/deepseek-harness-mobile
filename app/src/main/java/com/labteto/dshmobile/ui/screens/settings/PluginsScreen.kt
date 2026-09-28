package com.labteto.dshmobile.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.labteto.dshmobile.core.wire.dto.PluginBundle
import com.labteto.dshmobile.core.wire.dto.PluginBundleGroup
import com.labteto.dshmobile.core.wire.dto.PluginReadOnly
import com.labteto.dshmobile.data.PluginMutationState
import com.labteto.dshmobile.ui.components.DsIconButton
import com.labteto.dshmobile.ui.components.StateDot
import com.labteto.dshmobile.ui.components.StateDotState
import com.labteto.dshmobile.ui.theme.DsShapes
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsTitleBar
import com.labteto.dshmobile.ui.theme.DsType
import java.util.Locale
import kotlinx.coroutines.launch
import com.labteto.dshmobile.ui.rememberSessionStore
import com.labteto.dshmobile.ui.components.rememberDsToast
import com.labteto.dshmobile.data.PluginMutationOutcome
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect

/**
 * The profile's plugin bundles, with a live switch on each one.
 *
 * This is the harness's own top-level Plugins page — `pluginManager/listBundles`, its Official and
 * Installed sections — and it is *not* the Settings tab called "Built-in plugins". Those are two
 * different services and the difference is the whole design here:
 *
 * - **Bundles** ([PluginBundle]) is what an operator installs and turns on. Seventeen entries on a
 *   stock profile, each owning a *set* of loader rows. Toggling one is the supported operation the
 *   harness's own page offers.
 * - **Built-in plugins** (`pluginInventory/list` / `pluginManager/listPlugins`) is the flat
 *   Cordis loader tree — 198 rows here — shown read-only for inspection. It is deliberately not
 *   reproduced on this page.
 *
 * That second distinction is not academic. `listPlugins` also exposes a per-row setter, and a flat
 * list of 198 rows where most rows report themselves as togglable reads as a menu of safe switches.
 * It is not: `include:llm` reports no `readOnlyReason` at all, yet roughly two dozen packages inject
 * the `llm` service, so turning that one row off stops the harness from booting. The web UI never
 * offers a switch for it because it never shows this list as editable, and neither does this page.
 *
 * Read-only against a deployment that composes no manager: [bundles] is null, and the page says so
 * rather than rendering an empty list.
 */
@Composable
fun PluginsScreen(onClose: () -> Unit) {
    val store = rememberSessionStore()
    val bundles by store.pluginBundles.collectAsStateWithLifecycle()
    val mutation by store.pluginMutation.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val toast = rememberDsToast()
    val toggleFailed = stringResource(R.string.plugins_toggle_failed)
    val refusalManagement = stringResource(R.string.plugins_locked_management)

    // Fetched on open: the composition changes only when the harness restarts or when a toggle here
    // is applied, and a toggle reloads explicitly.
    LaunchedEffect(Unit) { store.refreshPlugins() }

    // A refusal arrives inside a successful call, which is why it has to be surfaced at all: the
    // request succeeded and the Host still declined, and a user would otherwise read that as a
    // broken switch.
    LaunchedEffect(store) {
        store.pluginEvents.collect { outcome ->
            when (outcome) {
                is PluginMutationOutcome.Applied -> Unit
                is PluginMutationOutcome.Refused -> toast.second(
                    when (outcome.reason) {
                        PluginReadOnly.MANAGEMENT_REQUIRED -> refusalManagement
                        // An unfamiliar code is shown verbatim: the vocabulary is the Host's, and a
                        // plausible-sounding guess is worse than an ugly but true one.
                        else -> outcome.reason ?: toggleFailed
                    },
                )
                is PluginMutationOutcome.Failed -> toast.second(toggleFailed)
            }
        }
    }

    val colors = DsTheme.colors
    var filter by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }
    val listState = rememberLazyListState()
    val locale = Locale.getDefault().toLanguageTag()

    val matching = remember(bundles, filter, locale) {
        val all = bundles.orEmpty()
        val query = filter.trim()
        if (query.isEmpty()) all else all.filter { it.matches(query, locale) }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = colors.bgBase) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = DsSpacing.comfortable, vertical = DsSpacing.medium),
        ) {
            PluginsTitleBar(onClose)

            val loaded = bundles
            if (loaded == null) {
                Text(
                    stringResource(R.string.plugins_unavailable),
                    style = DsType.small13,
                    color = colors.labelTertiary,
                )
                return@Column
            }

            TextField(
                value = filter,
                onValueChange = { filter = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = DsSpacing.small),
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

            BundleList(
                bundles = matching,
                total = loaded.size,
                mutation = mutation,
                expanded = expanded,
                onToggleExpand = { name ->
                    expanded = if (name in expanded) expanded - name else expanded + name
                },
                onToggle = { name, enabled -> scope.launch { store.setBundleEnabled(name, enabled) } },
                listState = listState,
                locale = locale,
            )
        }
    }
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
 * The bundle list, grouped the way the harness groups it.
 *
 * The section headers carry counts so the shape of the profile is legible before scrolling: eight
 * installed and nine available is a sentence; the same rows without headers is just a list.
 */
@Composable
private fun BundleList(
    bundles: List<PluginBundle>,
    total: Int,
    mutation: PluginMutationState?,
    expanded: Set<String>,
    onToggleExpand: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    listState: LazyListState,
    locale: String,
) {
    val colors = DsTheme.colors
    if (bundles.isEmpty()) {
        Text(
            stringResource(if (total == 0) R.string.plugins_empty else R.string.plugins_no_match),
            style = DsType.small13,
            color = colors.labelTertiary,
        )
        return
    }
    val installed = bundles.filter { it.group == PluginBundleGroup.INSTALLED }
    val available = bundles.filter { it.group == PluginBundleGroup.OFFICIAL }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.xsmall),
    ) {
        if (installed.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.plugins_section_installed), installed.size) }
            items(installed, key = { "i:${it.name}" }) { bundle ->
                BundleCard(bundle, mutation, bundle.name in expanded, onToggleExpand, onToggle, locale)
            }
        }
        if (available.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.plugins_section_available), available.size) }
            items(available, key = { "o:${it.name}" }) { bundle ->
                BundleCard(bundle, mutation, bundle.name in expanded, onToggleExpand, onToggle, locale)
            }
        }
        item { Spacer(Modifier.height(DsSpacing.xlarge)) }
    }
}

@Composable
private fun SectionHeader(label: String, count: Int) {
    val colors = DsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = DsSpacing.small, bottom = DsSpacing.tiny),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = DsType.small13Strong, color = colors.labelSecondary)
        Spacer(Modifier.width(DsSpacing.xsmall))
        Text(count.toString(), style = DsType.caption11, color = colors.labelTertiary)
    }
}

/**
 * One bundle, with a switch when the Host offers one.
 *
 * The switch is drawn but disabled for a bundle the Host refuses, rather than hidden: that a bundle
 * cannot be turned off is a fact about the profile worth seeing, and a list that silently omits the
 * control for some rows reads as a rendering bug. The reason becomes the row's own caption.
 */
@Composable
private fun BundleCard(
    bundle: PluginBundle,
    mutation: PluginMutationState?,
    expanded: Boolean,
    onToggleExpand: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    locale: String,
) {
    val colors = DsTheme.colors
    val busy = mutation?.target == bundle.name

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.row)
            .background(colors.bgLayer1)
            .padding(DsSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.tiny),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    bundle.displayTitle(locale),
                    style = DsType.small13Strong,
                    color = colors.labelPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                bundle.version?.let {
                    Text(it, style = DsType.caption11, color = colors.labelCaption)
                }
            }
            Spacer(Modifier.width(DsSpacing.xsmall))
            if (bundle.togglable) {
                Switch(
                    checked = bundle.enabled,
                    onCheckedChange = { next -> onToggle(bundle.name, next) },
                    enabled = !busy,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.bgBase,
                        checkedTrackColor = colors.accent,
                        uncheckedThumbColor = colors.labelTertiary,
                        uncheckedTrackColor = colors.hoverSolid,
                        uncheckedBorderColor = colors.borderL2,
                    ),
                )
            } else {
                BundleLock(reason = bundle.readOnlyReason)
            }
        }

        bundle.displayDescription?.let { description ->
            Text(description, style = DsType.caption11, color = colors.labelTertiary)
        }

        // The rows this bundle contributes, behind a disclosure. A bundle can carry ninety of them
        // (dsh-base does), and inlining that would make one bundle look like the whole page. Shown
        // without switches: this is the bundle's contents, not a place to edit them.
        if (bundle.rows.isNotEmpty()) {
            Text(
                stringResource(R.string.plugins_bundle_rows, bundle.rows.size),
                style = DsType.caption11,
                color = if (expanded) colors.accent else colors.labelTertiary,
                modifier = Modifier
                    .clip(DsShapes.row)
                    .clickable { onToggleExpand(bundle.name) }
                    .padding(vertical = DsSpacing.tiny),
            )
            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.tiny)) {
                    bundle.rows.forEach { row ->
                        Text(
                            row.displayTitle,
                            style = DsType.caption11,
                            color = colors.labelCaption,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A locked bundle's state.
 *
 * It reports *why* it is locked rather than repeating its enablement: "Enabled" beside a greyed
 * switch tells the reader nothing they cannot already see.
 */
@Composable
private fun BundleLock(reason: String?) {
    val colors = DsTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        StateDot(StateDotState.Idle)
        Spacer(Modifier.width(DsSpacing.tiny))
        Text(
            text = reason?.let { readOnlyLabel(it)?.let { id -> stringResource(id) } ?: it }
                ?: stringResource(R.string.plugins_locked_generic),
            style = DsType.caption11,
            color = colors.labelTertiary,
        )
    }
}

/**
 * The sentence for a read-only reason, or null when this build has no wording for it.
 *
 * Null rather than a generic fallback so the caller can show the Host's own code instead: the codes
 * are stable and greppable, and a plausible-sounding guess is worse than an ugly but true one.
 */
private fun readOnlyLabel(reason: String): Int? = when (reason) {
    PluginReadOnly.MANAGEMENT_REQUIRED -> R.string.plugins_locked_management
    else -> null
}

/**
 * Whether a bundle answers a query.
 *
 * The version is matched as well as the name: a user chasing a specific release types what they see,
 * and the version is on the row.
 */
internal fun PluginBundle.matches(query: String, locale: String? = null): Boolean {
    val q = query.trim().lowercase(Locale.ROOT)
    if (q.isEmpty()) return true
    return name.lowercase(Locale.ROOT).contains(q) ||
        version?.lowercase(Locale.ROOT)?.contains(q) == true ||
        displayTitle(locale).lowercase(Locale.ROOT).contains(q) ||
        displayDescription?.lowercase(Locale.ROOT)?.contains(q) == true
}
