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
import com.labteto.dshmobile.core.wire.dto.ProfileBundle
import com.labteto.dshmobile.core.wire.dto.ProfileBundleRow
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
 * - **Bundles** ([ProfileBundle]) is what an operator installs and turns on. Seventeen entries on a
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
    // Which row's detail dialog is open. Held as the row itself rather than a name so the dialog
    // keeps rendering after a reload replaces the list with equal-but-new objects.
    var openRow by remember { mutableStateOf<ProfileBundleRow?>(null) }
    val listState = rememberLazyListState()
    val locale = Locale.getDefault().toLanguageTag()

    // The page is entered from the chat drawer, and a drawer's back button exits the app: without a
    // handler here, back on this screen walks straight out of the activity. SettingsScreen does the
    // same thing for the same reason.
    BackHandler(enabled = openRow == null) { onClose() }

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
        if (query.isEmpty()) {
            all
        } else {
            // A row matches as well as a bundle. With ninety rows under one bundle, filtering by the
            // package name alone would make the field useless for finding a plugin — and a bundle
            // that matches on a row keeps only that row, so the hit is what is on screen.
            all.mapNotNull { bundle ->
                if (bundle.matches(query, locale)) {
                    bundle
                } else {
                    bundle.copy(rows = bundle.rows.filter { it.matches(query, locale) })
                        .takeIf { it.rows.isNotEmpty() }
                }
            }
        }
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
                liveRows = rows.orEmpty(),
                openable = schemaByNamespace.keys,
                onOpenRow = { openRow = it },
                onToggle = { entryId, enabled -> scope.launch { store.setPluginEnabled(entryId, enabled) } },
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
    openRow?.let { row ->
        val ns = row.rowId
        val schema = schemaByNamespace[ns]
        PluginRowDialog(
            title = row.displayTitle,
            description = row.meta?.description?.resolve(locale),
            // The form appears only when this connection can read the configuration plane. A relay
            // hides it, which is ordinary rather than a fault, so the dialog says so.
            settings = when {
                schema == null -> null
                settings == null -> { { ConfigUnavailable() } }
                else -> {
                    {
                        NamespaceSettingsForm(
                            ns = ns,
                            schema = schema,
                            value = valuesByNamespace[ns],
                            revision = revisionsByNamespace[ns],
                            onEdit = { target, path, value, revision ->
                                scope.launch { store.updateSetting(target, buildSettingPatch(path, value), revision) }
                            },
                        )
                    }
                }
            },
            onDismiss = { openRow = null },
        )
    }
}

/** Shown in place of a form when the Host describes settings this connection cannot read. */
@Composable
private fun ConfigUnavailable() {
    Text(
        stringResource(R.string.plugins_config_unavailable),
        style = DsType.std14,
        color = DsTheme.colors.warnLabel,
    )
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
    bundles: List<ProfileBundle>,
    total: Int,
    mutation: PluginMutationState?,
    liveRows: List<PluginManagerRow>,
    openable: Set<String>,
    onOpenRow: (ProfileBundleRow) -> Unit,
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
        // A bundle and its rows are one block: the card is the package, and everything under it is
        // what the package put in the tree. Every row is drawn here rather than behind a dialog,
        // because the switch is per row and a switch nobody can see is a switch nobody uses.
        if (installed.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.plugins_section_installed), installed.size) }
            installed.forEach { bundle ->
                item(key = "i:${bundle.name}") {
                    BundleCard(bundle, onUpdate, onRemove, busy, locale)
                }
                items(bundle.rows, key = { "i:${bundle.name}:${it.rowId}" }) { row ->
                    PluginRowItem(row, liveRows, openable, mutation, busy, locale, onOpenRow, onToggle)
                }
            }
        }
        if (available.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.plugins_section_available), available.size) }
            available.forEach { bundle ->
                item(key = "o:${bundle.name}") {
                    BundleCard(bundle, onUpdate, onRemove, busy, locale)
                }
                items(bundle.rows, key = { "o:${bundle.name}:${it.rowId}" }) { row ->
                    PluginRowItem(row, liveRows, openable, mutation, busy, locale, onOpenRow, onToggle)
                }
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
 * One bundle: the package, and the two things that are about the package.
 *
 * Update and remove belong here and nowhere else. They act on *this* bundle's package, so they are
 * per bundle by the shape of the API, while enablement is per row — a distinction the list now draws
 * by putting the switches on the rows below and only the package actions on the card.
 *
 * The card carries no switch of its own. `setBundleEnabled` still exists, but a bundle toggle is a
 * shorthand: it stands in for every row under it, and the reader's question is always about one row.
 */
@Composable
private fun BundleCard(
    bundle: ProfileBundle,
    onUpdate: (ProfileBundle) -> Unit,
    onRemove: (ProfileBundle) -> Unit,
    installBusy: Boolean,
    locale: String,
) {
    val colors = DsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.row)
            .background(colors.bgLayer1)
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
        if (bundle.readOnlyReason != null) {
            // Kept even without a switch: it is why the removal is not offered, and a missing control
            // with no explanation reads as a bug.
            PluginLock(reason = bundle.readOnlyReason)
        }
        if (bundle.installed) {
            DsIconButton(
                icon = FeatherIcons.RefreshCw,
                contentDescription = stringResource(R.string.ux_a_update),
                onClick = { onUpdate(bundle) },
                tint = colors.labelTertiary,
                enabled = !installBusy,
            )
        }
        // `readOnlyReason` gates the removal as well as the toggle: a bundle the profile is built on
        // is one the Host refuses to take away, and offering the action anyway would only produce a
        // refusal the lock above already explained.
        if (bundle.removable && bundle.readOnlyReason == null) {
            DsIconButton(
                icon = FeatherIcons.Trash2,
                contentDescription = stringResource(R.string.common_remove),
                onClick = { onRemove(bundle) },
                tint = colors.labelTertiary,
                enabled = !installBusy,
            )
        }
    }
}

/**
 * One of a bundle's rows: indented under its card, with the switch that belongs to it.
 *
 * The live state comes from `listPlugins` matched on entry id, not from the bundle's own row list:
 * `listBundles` says what a bundle *declares* and `listPlugins` says what the loader tree holds, and
 * only the second knows whether a row is on. A row that is not composed yet has no entry id and so
 * gets no switch — a disabled one would report "locked" about a row that is merely not mounted.
 */
@Composable
private fun PluginRowItem(
    row: ProfileBundleRow,
    liveRows: List<PluginManagerRow>,
    openable: Set<String>,
    mutation: PluginMutationState?,
    installBusy: Boolean,
    locale: String,
    onOpen: (ProfileBundleRow) -> Unit,
    onToggle: (String, Boolean) -> Unit,
) {
    val live = liveRows.firstOrNull { it.entryId == row.entryId }
    PluginRow(
        title = row.meta?.title?.resolve(locale) ?: row.moduleName,
        description = row.meta?.description?.resolve(locale),
        enabled = live?.enabled,
        togglable = live?.togglable == true,
        busy = installBusy || (live != null && mutation?.target == live.entryId),
        lockReason = live?.readOnlyReason,
        onToggle = live?.let { target -> { next: Boolean -> onToggle(target.entryId, next) } },
        // Only a row with a described namespace has options to open; for the rest the row is the
        // whole truth about it, and a dialog saying "no settings" would be a dialog for nothing.
        onOpen = if (row.rowId in openable) ({ onOpen(row) }) else null,
        modifier = Modifier.padding(start = DsSpacing.medium),
    )
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
internal fun ProfileBundleRow.matches(query: String, locale: String? = null): Boolean {
    val q = query.trim().lowercase(Locale.ROOT)
    if (q.isEmpty()) return true
    return (meta?.title?.resolve(locale) ?: moduleName).lowercase(Locale.ROOT).contains(q) ||
        moduleName.lowercase(Locale.ROOT).contains(q) ||
        rowId.lowercase(Locale.ROOT).contains(q)
}

/**
 * Whether a bundle answers a query.
 *
 * Deliberately *not* over its rows: a bundle whose row matched is handled by the caller, which keeps
 * only that row. Folding rows in here would say "this bundle matches" and then show all ninety of
 * its rows.
 */
internal fun ProfileBundle.matches(query: String, locale: String? = null): Boolean {
    val q = query.trim().lowercase(Locale.ROOT)
    if (q.isEmpty()) return true
    return name.lowercase(Locale.ROOT).contains(q) ||
        version?.lowercase(Locale.ROOT)?.contains(q) == true ||
        displayTitle(locale).lowercase(Locale.ROOT).contains(q) ||
        displayDescription?.lowercase(Locale.ROOT)?.contains(q) == true
}
