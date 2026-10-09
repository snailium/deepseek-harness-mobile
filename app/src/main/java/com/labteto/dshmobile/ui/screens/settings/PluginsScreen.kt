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
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.labteto.dshmobile.core.wire.dto.PluginManagerRow
import com.labteto.dshmobile.core.wire.dto.PluginReadOnly
import com.labteto.dshmobile.data.PluginMutationState
import com.labteto.dshmobile.core.wire.featureValue
import com.labteto.dshmobile.core.wire.removePluginBundle
import com.labteto.dshmobile.data.HarnessFeature
import com.labteto.dshmobile.ui.components.FeatherIcons
import com.labteto.dshmobile.ui.screens.main.PluginInstallSubject
import com.labteto.dshmobile.ui.screens.main.approvalSubject
import com.labteto.dshmobile.ui.screens.main.stageFeatureDraft
import com.labteto.dshmobile.ui.components.DsIconButton
import com.labteto.dshmobile.ui.components.SearchField
import com.labteto.dshmobile.ui.components.SearchFieldCloseButton
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import com.labteto.dshmobile.core.wire.dto.SettingsSchemaResolver
import com.labteto.dshmobile.core.wire.dto.ResolvedSettingsSchema
import com.labteto.dshmobile.ui.components.DsDialog
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState

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
    // The live rows, for the switches inside a bundle's form: the bundle list carries no enablement.
    val rows by store.pluginRows.collectAsStateWithLifecycle()
    // Installing is not a call this page can make and wait on: a package may have to be fetched and
    // built, so the work lives in `PluginOperations`, survives this page closing, reports progress
    // and can be cancelled. What this page owns is the form that starts it and the card that shows
    // it while it runs.
    val host = store.activeHostKey
    val operation by store.pluginOperations.state.collectAsStateWithLifecycle()
    val pending by store.pluginOperations.pending.collectAsStateWithLifecycle()
    val presets by store.agentPresets.collectAsStateWithLifecycle()
    val offline = stringResource(R.string.common_offline)
    val creatorDraft = stringResource(R.string.harness_creator_draft)
    var spec by remember(host) { mutableStateOf("") }
    var registry by remember(host) { mutableStateOf("") }
    var installedVersion by remember(host) { mutableStateOf<String?>(null) }
    var installOpen by remember(host) { mutableStateOf(false) }
    var confirm by remember(host) { mutableStateOf<PluginInstallSubject?>(null) }
    var removeTarget by remember(host) { mutableStateOf<ProfileBundle?>(null) }
    val current = operation.takeIf { it.host == host }
    val installing = pending.any { it.host == host && it.busy }
    val mutation by store.pluginMutation.collectAsStateWithLifecycle()
    val settings by store.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val toast = rememberDsToast()
    val toggleFailed = stringResource(R.string.plugins_toggle_failed)
    val refusalManagement = stringResource(R.string.plugins_locked_management)

    // Fetched on open: the composition changes only when the harness restarts or when a toggle here
    // is applied, and a toggle reloads explicitly.
    LaunchedEffect(Unit) {
        store.refreshPlugins()
        // Attempted alongside the bundles, not instead of them: the configuration plane is
        // loopback-only on a relayed connection, so a phone on the network gets the switches but
        // not the forms. Both outcomes are ordinary and the page reports which one it got.
        store.refreshSettings()
    }

    // A refusal arrives inside a successful call, which is why it has to be surfaced at all: the
    // request succeeded and the Host still declined, and a user would otherwise read that as a
    // broken switch.
    // An install or a removal changes the list, and the operation reports when it is done rather
    // than returning to this call site.
    LaunchedEffect(current?.result, current?.error, current?.unknown) {
        if (current?.result != null || current?.error != null) store.refreshPlugins()
    }

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
    // Which bundle's detail dialog is open. Held as the bundle itself rather than a name so the
    // dialog keeps rendering after a reload replaces the list with equal-but-new objects.
    var openBundle by remember { mutableStateOf<PluginBundle?>(null) }
    val listState = rememberLazyListState()
    val locale = Locale.getDefault().toLanguageTag()

    // The page is entered from the chat drawer, and a drawer's back button exits the app: without a
    // handler here, back on this screen walks straight out of the activity. SettingsScreen does the
    // same thing for the same reason.
    BackHandler(enabled = openBundle == null) { onClose() }

    // rowId is the settings namespace: a bundle's configuration is whichever namespaces its own
    // rows own. Resolved once here rather than per card, because the uid graph is shared across
    // namespaces and walking it inside a composable would repeat that work on every recomposition.
    val schemaByNamespace = remember(settings) {
        val describe = settings ?: return@remember emptyMap()
        val refs = describe.namespaces.associate { namespace ->
            namespace.ns to (((namespace.schema as? JsonObject)?.get("refs") as? JsonObject).orEmpty())
        }
        describe.namespaces.associate { namespace ->
            val resolver = SettingsSchemaResolver(refs[namespace.ns].orEmpty())
            namespace.ns to resolver.resolve(namespace)
        }
    }
    val valuesByNamespace = remember(settings) {
        settings?.namespaces?.associate { it.ns to it.value }.orEmpty()
    }
    val revisionsByNamespace = remember(settings) {
        settings?.namespaces?.associate { it.ns to it.revision }.orEmpty()
    }

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
            PluginsTitleBar(
                onClose = onClose,
                actions = {
                    DsIconButton(
                        icon = FeatherIcons.Download,
                        contentDescription = stringResource(R.string.harness_install_update),
                        onClick = {
                            spec = ""; registry = ""; installedVersion = null; installOpen = true
                        },
                        tint = colors.labelTertiary,
                        iconSize = DsTitleBar.iconSize,
                        touchTarget = DsTitleBar.iconTouchTarget,
                    )
                    // Offered only when the Host actually has the preset it stages: a Create button
                    // that opens a session on a preset the host does not carry is a broken button.
                    if (presets?.presets?.any { it.id == "cordis" && it.broken == null } == true) {
                        DsIconButton(
                            icon = FeatherIcons.Plus,
                            contentDescription = stringResource(R.string.harness_create_plugin),
                            onClick = {
                                scope.launch {
                                    if (host != null && store.stageFeatureDraft(host, creatorDraft, "cordis")) onClose()
                                    else toast.second(offline)
                                }
                            },
                            tint = colors.labelTertiary,
                            iconSize = DsTitleBar.iconSize,
                            touchTarget = DsTitleBar.iconTouchTarget,
                        )
                    }
                },
            )

            val loaded = bundles
            if (loaded == null) {
                Text(
                    stringResource(R.string.plugins_unavailable),
                    style = DsType.std14,
                    color = colors.labelTertiary,
                )
                return@Column
            }

            // The app's one search field: a 56dp Material3 TextField used to sit here, which is the
            // same drift SearchField exists to prevent.
            SearchField(
                query = filter,
                onQueryChange = { filter = it },
                placeholder = stringResource(R.string.plugins_search_hint),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = DsSpacing.small),
                trailing = {
                    SearchFieldCloseButton(onClick = { filter = "" })
                },
            )

            Spacer(Modifier.height(DsSpacing.small))

            current?.takeIf { it.busy || it.result != null || it.error != null }?.let { state ->
                PluginOperationCard(
                    state = state,
                    onCancel = { listOfNotNull(host, state.requestId).takeIf { it.size == 2 }?.let { store.pluginOperations.cancel(it[0], it[1]) } },
                    onApprove = { confirm = it },
                )
                Spacer(Modifier.height(DsSpacing.small))
            }

            BundleList(
                bundles = matching,
                total = loaded.size,
                mutation = mutation,
                onOpen = { openBundle = it },
                onToggle = { name, enabled -> scope.launch { store.setBundleEnabled(name, enabled) } },
                // Update is an install with the form already filled in from the row: the harness has
                // no separate "upgrade" verb, it re-resolves the spec.
                onUpdate = { bundle ->
                    spec = bundle.name; registry = ""; installedVersion = bundle.version; installOpen = true
                },
                onRemove = { removeTarget = it },
                busy = installing,
                listState = listState,
                locale = locale,
            )
        }
    }

    if (installOpen) {
        InstallPluginDialog(
            spec = spec,
            registry = registry,
            installedVersion = installedVersion,
            busy = installing,
            onSpecChange = { spec = it },
            onRegistryChange = { registry = it },
            onInstall = {
                val target = spec.trim()
                if (target.isNotEmpty()) {
                    installOpen = false
                    confirm = PluginInstallSubject(target, registry.trim().ifBlank { null })
                }
            },
            onDismiss = { installOpen = false },
        )
    }

    confirm?.let { subject ->
        ApproveBuildsDialog(
            subject = subject,
            busy = installing,
            onConfirm = {
                confirm = null
                if (host != null) store.pluginOperations.install(host, subject.target, subject.registry, subject.approvedBuilds)
            },
            onDismiss = { confirm = null },
        )
    }

    removeTarget?.let { bundle ->
        RemoveBundleDialog(
            bundle = bundle,
            locale = locale,
            busy = installing,
            onConfirm = {
                removeTarget = null
                scope.launch {
                    val api = store.apiForHost(host) ?: return@launch
                    val result = store.featureCall(HarnessFeature.PLUGIN_MANAGEMENT, api) { api.removePluginBundle(bundle.name) }
                    if (host != null) store.pluginOperations.changed(host, result.featureValue())
                    store.refreshPlugins()
                }
            },
            onDismiss = { removeTarget = null },
        )
    }

    // The detail dialog lives beside the Surface rather than inside the scroll: a dialog is not part
    // of the page's layout, and nesting it in the column made it inherit the column's constraints.
    openBundle?.let { bundle ->
        BundleDetailDialog(
            bundle = bundle,
            liveRows = rows.orEmpty(),
            mutation = mutation,
            schemaByNamespace = schemaByNamespace,
            valuesByNamespace = valuesByNamespace,
            revisionsByNamespace = revisionsByNamespace,
            settingsAvailable = settings != null,
            locale = locale,
            onEdit = { ns, path, value, revision ->
                scope.launch { store.updateSetting(ns, buildSettingPatch(path, value), revision) }
            },
            onToggle = { entryId, enabled -> scope.launch { store.setPluginEnabled(entryId, enabled) } },
            onDismiss = { openBundle = null },
        )
    }
}

/** The page's own title bar, matching every other screen's 44dp row. */
@Composable
internal fun PluginsTitleBar(onClose: () -> Unit, actions: (@Composable () -> Unit)? = null) {
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
        if (actions != null) {
            Spacer(Modifier.weight(1f))
            actions()
        }
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
    onOpen: (PluginBundle) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onUpdate: (ProfileBundle) -> Unit,
    onRemove: (ProfileBundle) -> Unit,
    busy: Boolean,
    listState: LazyListState,
    locale: String,
) {
    val colors = DsTheme.colors
    if (bundles.isEmpty()) {
        Text(
            stringResource(if (total == 0) R.string.plugins_empty else R.string.plugins_no_match),
            style = DsType.std14,
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
                BundleCard(bundle, mutation, onToggle, { onOpen(bundle) }, onUpdate, onRemove, busy, locale)
            }
        }
        if (available.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.plugins_section_available), available.size) }
            items(available, key = { "o:${it.name}" }) { bundle ->
                BundleCard(bundle, mutation, onToggle, { onOpen(bundle) }, onUpdate, onRemove, busy, locale)
            }
        }
        item { Spacer(Modifier.height(DsSpacing.xlarge)) }
    }
}

@Composable
internal fun SectionHeader(label: String, count: Int) {
    val colors = DsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = DsSpacing.small, bottom = DsSpacing.tiny),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = DsType.std14Strong, color = colors.labelSecondary)
        Spacer(Modifier.width(DsSpacing.xsmall))
        Text(count.toString(), style = DsType.small13, color = colors.labelTertiary)
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
    onToggle: (String, Boolean) -> Unit,
    onOpen: () -> Unit,
    onUpdate: (ProfileBundle) -> Unit,
    onRemove: (ProfileBundle) -> Unit,
    installBusy: Boolean,
    locale: String,
) {
    val colors = DsTheme.colors
    val busy = mutation?.target == bundle.name

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.row)
            .background(colors.bgLayer1)
            .clickable(onClick = onOpen)
            .padding(DsSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                bundle.displayTitle(locale),
                style = DsType.std14Strong,
                color = colors.labelPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // The version and the description share one caption line: the list is a list, and two
            // stacked captions per row made it read like a form.
            val caption = listOfNotNull(bundle.version, bundle.displayDescription).joinToString(" · ")
            if (caption.isNotEmpty()) {
                Text(
                    caption,
                    style = DsType.small13,
                    color = colors.labelTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(DsSpacing.small))
        // Update and remove are row actions, on the row they act on: both are about this bundle's
        // package, and both are per-row in the harness rather than page-wide.
        if (bundle.installed) {
            DsIconButton(
                icon = FeatherIcons.RefreshCw,
                contentDescription = stringResource(R.string.ux_a_update),
                onClick = { onUpdate(bundle) },
                tint = colors.labelTertiary,
                enabled = !installBusy,
            )
        }
        if (bundle.removable && bundle.readOnlyReason == null) {
            DsIconButton(
                icon = FeatherIcons.Trash2,
                contentDescription = stringResource(R.string.common_remove),
                onClick = { onRemove(bundle) },
                tint = colors.labelTertiary,
                enabled = !installBusy,
            )
        }
        if (bundle.togglable) {
            Switch(
                checked = bundle.enabled,
                onCheckedChange = { next -> onToggle(bundle.name, next) },
                enabled = !busy,
                colors = pluginSwitchColors(colors),
            )
        } else {
            PluginLock(reason = bundle.readOnlyReason)
        }
    }
}

/**
 * One bundle's detail: its configuration form, and the rows it contributes.
 *
 * A dialog rather than an inline expansion, because the form is long — `relay` alone declares around
 * thirty fields — and inlining it made two bundles fill the whole page. The list stays a list.
 *
 * The switch is not repeated here. Enablement belongs to the row the user just tapped, and a second
 * control for the same value in a dialog is a way to disagree with yourself.
 */
@Composable
private fun BundleDetailDialog(
    bundle: ProfileBundle,
    liveRows: List<PluginManagerRow>,
    mutation: PluginMutationState?,
    schemaByNamespace: Map<String, ResolvedSettingsSchema>,
    valuesByNamespace: Map<String, JsonElement>,
    revisionsByNamespace: Map<String, Long>,
    settingsAvailable: Boolean,
    locale: String,
    onEdit: (String, List<String>, JsonElement, Long?) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = DsTheme.colors
    var rowsOpen by remember(bundle.name) { mutableStateOf(false) }

    // rowId *is* the settings namespace, so a bundle's configuration is the intersection of its
    // rows' rowIds with the described namespaces — a plain set operation, no invented mapping.
    val configured = remember(bundle, schemaByNamespace) {
        bundle.rows.filter { it.rowId in schemaByNamespace }
    }
    // Everything else the bundle carries: real rows with no form, listed but not configurable.
    val plain = remember(bundle, schemaByNamespace) {
        bundle.rows.filterNot { it.rowId in schemaByNamespace }
    }

    // The title is the display name — `dsh-relay` as a heading is the raw row id, and the list row
    // already shows the friendly one. Version and description are one caption line, matching the
    // list row instead of stacking two captions under the title.
    DsDialog(title = bundle.displayTitle(locale), onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.small),
        ) {
            val caption = listOfNotNull(bundle.version, bundle.displayDescription).joinToString(" · ")
            if (caption.isNotEmpty()) {
                Text(caption, style = DsType.small13, color = colors.labelTertiary)
            }

            if (configured.isEmpty()) {
                Text(
                    stringResource(R.string.plugins_config_none),
                    style = DsType.std14,
                    color = colors.labelTertiary,
                )
            } else if (!settingsAvailable) {
                // The bundle has settings the Host knows about but this connection cannot read. On a
                // relay that is the expected outcome rather than a fault, so it says which.
                Text(
                    stringResource(R.string.plugins_config_unavailable),
                    style = DsType.std14,
                    color = colors.warnLabel,
                )
            } else {
                configured.forEach { row ->
                    val ns = row.rowId
                    val schema = schemaByNamespace[ns] ?: return@forEach
                    // The live row, matched by entry id. `listBundles` says what a bundle *declares*
                    // and `listPlugins` says what the loader tree actually holds; only the second
                    // knows whether the row is on. A row that is not composed yet has no entry id
                    // and therefore no switch — drawing one disabled would report "locked" about a
                    // row that is merely not mounted.
                    val live = liveRows.firstOrNull { it.entryId == row.entryId }
                    PluginRowCard(
                        title = row.displayTitle,
                        description = row.meta?.description?.resolve(),
                        enabled = live?.enabled,
                        togglable = live?.togglable == true,
                        busy = live != null && mutation?.target == live.entryId,
                        lockReason = live?.readOnlyReason,
                        onToggle = live?.let { open -> { next: Boolean -> onToggle(open.entryId, next) } },
                        settings = {
                            NamespaceSettingsForm(
                                ns = ns,
                                schema = schema,
                                value = valuesByNamespace[ns],
                                revision = revisionsByNamespace[ns],
                                onEdit = onEdit,
                            )
                        },
                    )
                }
            }

            // What the bundle contributes that has no form of its own, behind a disclosure: a
            // bundle can carry ninety rows (dsh-base does), and the rows with settings are the cards
            // above — listing those again here would say the bundle has twice the rows it has.
            if (plain.isNotEmpty()) {
                Text(
                    stringResource(R.string.plugins_bundle_rows, plain.size),
                    style = DsType.small13,
                    color = if (rowsOpen) colors.accent else colors.labelTertiary,
                    modifier = Modifier
                        .clip(DsShapes.row)
                        .clickable { rowsOpen = !rowsOpen }
                        .padding(vertical = DsSpacing.tiny),
                )
                if (rowsOpen) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DsShapes.row)
                            .background(colors.bgLayer1)
                            .padding(horizontal = DsSpacing.medium, vertical = DsSpacing.small),
                        verticalArrangement = Arrangement.spacedBy(DsSpacing.tiny),
                    ) {
                        plain.forEach { row ->
                            Text(
                                row.displayTitle,
                                style = DsType.small13,
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
}

/**
 * The value at [path] within a namespace's effective value object.
 *
 * Walks segment by segment so a nested field reads its own value rather than its group's. A missing
 * segment yields null, which every control reads as "fall back to the schema default".
 */
internal fun fieldValueAt(path: List<String>, root: JsonElement?): JsonElement? {
    var current = root
    for (segment in path) {
        current = (current as? JsonObject)?.get(segment) ?: return null
    }
    return current
}

/**
 * Build the partial object `settings/update` merges into a namespace's user layer.
 *
 * The Host takes a patch, not a whole value, so this nests [path] into single-key objects at each
 * level: editing `providers.b70-smg.models` sends `{"providers":{"b70-smg":{"models":…}}}`, which
 * merges without disturbing the siblings the form is not touching.
 */
internal fun buildSettingPatch(path: List<String>, value: JsonElement): JsonObject {
    require(path.isNotEmpty()) { "a settings patch addresses at least one key" }
    return path.dropLast(1).foldRight(JsonObject(mapOf(path.last() to value))) { segment, acc ->
        JsonObject(mapOf(segment to acc))
    }
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
