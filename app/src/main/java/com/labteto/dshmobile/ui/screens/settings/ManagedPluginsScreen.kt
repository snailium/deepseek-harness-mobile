package com.labteto.dshmobile.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labteto.dshmobile.R
import com.labteto.dshmobile.core.wire.dto.PluginManagerRow
import com.labteto.dshmobile.core.wire.dto.ResolvedSettingsSchema
import com.labteto.dshmobile.core.wire.dto.SettingsSchemaResolver
import com.labteto.dshmobile.data.PluginMutationOutcome
import com.labteto.dshmobile.data.PluginMutationState
import com.labteto.dshmobile.core.wire.dto.PluginReadOnly
import com.labteto.dshmobile.ui.components.SearchField
import com.labteto.dshmobile.ui.components.SearchFieldCloseButton
import com.labteto.dshmobile.ui.components.rememberDsToast
import com.labteto.dshmobile.ui.rememberSessionStore
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import com.labteto.dshmobile.ui.theme.DsShapes
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import java.util.Locale
import kotlinx.serialization.json.JsonObject

/**
 * The plugins page: every row the Host's plugin manager reports, one switch each.
 *
 * This is the *system* half of the fork's plugin surfaces — the rows the profile composes — and it is
 * entered from Settings. The other half, the bundles an operator installs, lives behind the chat
 * list's own button, because installing a package and turning a row on are different acts with
 * different risks: one changes what the harness is made of, the other changes what it does.
 *
 * A row opens onto its own settings where the Host has described a namespace for it. That mapping is
 * not invented: a row's `patchId` *is* the settings namespace for a row a bundle owns, so the match
 * is a set operation. A row no bundle owns has no such id and therefore no form — it still gets its
 * switch and its description, which is everything the wire actually says about it.
 */
@Composable
fun ManagedPluginsScreen(onClose: () -> Unit) {
    val store = rememberSessionStore()
    val rows by store.pluginRows.collectAsStateWithLifecycle()
    val mutation by store.pluginMutation.collectAsStateWithLifecycle()
    val settings by store.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val toast = rememberDsToast()
    val toggleFailed = stringResource(R.string.plugins_toggle_failed)
    val refusalManagement = stringResource(R.string.plugins_locked_management)

    var filter by remember { mutableStateOf("") }
    // Which row's options are open, held as the row so the dialog survives a reload.
    var openRow by remember { mutableStateOf<PluginManagerRow?>(null) }
    val listState = rememberLazyListState()
    val locale = Locale.getDefault().toLanguageTag()

    LaunchedEffect(Unit) {
        store.refreshPlugins()
        // Alongside the rows, not instead of them: the configuration plane is loopback-only on a
        // relayed connection, so a phone on the network gets switches without forms. Both outcomes
        // are ordinary.
        store.refreshSettings()
    }

    // A refusal arrives inside a successful call, so it has to be surfaced: the request succeeded
    // and the Host still declined, and a switch that silently springs back reads as a broken switch.
    LaunchedEffect(store) {
        store.pluginEvents.collect { outcome ->
            when (outcome) {
                is PluginMutationOutcome.Applied -> Unit
                is PluginMutationOutcome.Refused -> toast.second(
                    when (outcome.reason) {
                        PluginReadOnly.MANAGEMENT_REQUIRED -> refusalManagement
                        else -> outcome.reason ?: toggleFailed
                    },
                )
                is PluginMutationOutcome.Failed -> toast.second(toggleFailed)
            }
        }
    }

    BackHandler { onClose() }

    // Resolved once, as on the bundles page: the uid graph is shared between namespaces and walking
    // it per row would repeat that work on every recomposition.
    val schemaByNamespace = remember(settings) {
        val describe = settings ?: return@remember emptyMap()
        describe.namespaces.associate { namespace ->
            val refs = ((namespace.schema as? JsonObject)?.get("refs") as? JsonObject).orEmpty()
            namespace.ns to SettingsSchemaResolver(refs).resolve(namespace)
        }
    }
    val valuesByNamespace = remember(settings) {
        settings?.namespaces?.associate { it.ns to it.value }.orEmpty()
    }
    val revisionsByNamespace = remember(settings) {
        settings?.namespaces?.associate { it.ns to it.revision }.orEmpty()
    }

    val colors = DsTheme.colors
    val loaded = rows
    val matching = remember(loaded, filter) {
        val all = loaded.orEmpty()
        val query = filter.trim()
        if (query.isEmpty()) all else all.filter { it.matches(query) }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = colors.bgBase) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = DsSpacing.comfortable, vertical = DsSpacing.medium),
        ) {
            PluginsTitleBar(onClose)

            if (loaded == null) {
                Text(
                    stringResource(R.string.plugins_unavailable),
                    style = DsType.std14,
                    color = colors.labelTertiary,
                )
                return@Column
            }

            SearchField(
                query = filter,
                onQueryChange = { filter = it },
                placeholder = stringResource(R.string.plugins_search_hint),
                modifier = Modifier.fillMaxWidth().padding(top = DsSpacing.small),
                trailing = { SearchFieldCloseButton(onClick = { filter = "" }) },
            )
            Spacer(Modifier.height(DsSpacing.small))

            if (matching.isEmpty()) {
                Text(
                    stringResource(if (loaded.isEmpty()) R.string.plugins_empty else R.string.plugins_no_match),
                    style = DsType.std14,
                    color = colors.labelTertiary,
                )
                return@Column
            }

            // Two sections, because they answer two different questions. A row no bundle owns is part
            // of the profile itself; a row a bundle owns came in with a package, and turning it off
            // is a local change to something an operator installed. Same switch, different blast
            // radius, and one flat list would hide that.
            val profileRows = matching.filter { it.patchId == null }
            val bundleRows = matching.filterNot { it.patchId == null }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.xsmall),
            ) {
                if (profileRows.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.plugins_tab_composed), profileRows.size) }
                }
                items(profileRows, key = { it.entryId }) { row ->
                    RowCard(
                        row = row,
                        mutation = mutation,
                        schemaByNamespace = schemaByNamespace,
                        locale = locale,
                        onToggle = { entryId, next -> scope.launch { store.setPluginEnabled(entryId, next) } },
                        onOpen = { openRow = it },
                    )
                }
                if (bundleRows.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.plugins_tab_bundles), bundleRows.size) }
                }
                items(bundleRows, key = { "b:${it.entryId}" }) { row ->
                    RowCard(
                        row = row,
                        mutation = mutation,
                        schemaByNamespace = schemaByNamespace,
                        locale = locale,
                        onToggle = { entryId, next -> scope.launch { store.setPluginEnabled(entryId, next) } },
                        onOpen = { openRow = it },
                    )
                }
                item { Spacer(Modifier.height(DsSpacing.xlarge)) }
            }
        }
    }

    // Beside the Surface, not inside its column: a dialog is not part of the page's layout.
    openRow?.let { row ->
        val ns = row.patchId
        PluginRowDialog(
            title = row.meta?.title?.resolve(locale) ?: row.moduleName,
            description = row.meta?.description?.resolve(locale),
            settings = ns?.let { namespace ->
                schemaByNamespace[namespace]?.let { schema ->
                    {
                        NamespaceSettingsForm(
                            ns = namespace,
                            schema = schema,
                            value = valuesByNamespace[namespace],
                            revision = revisionsByNamespace[namespace],
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

/**
 * Whether a row answers a query.
 *
 * The module name is matched as well as the title: a reader chasing a plugin they saw in a log types
 * the module specifier, and the row shows the friendly title.
 */
internal fun PluginManagerRow.matches(query: String): Boolean {
    val q = query.trim().lowercase(Locale.ROOT)
    if (q.isEmpty()) return true
    return (meta?.title?.resolve() ?: moduleName).lowercase(Locale.ROOT).contains(q) ||
        moduleName.lowercase(Locale.ROOT).contains(q) ||
        entryId.lowercase(Locale.ROOT).contains(q)
}

/**
 * One row, as both sections draw it.
 *
 * A function rather than a lambda inlined twice: the two sections differ only in which rows they
 * contain, and a second copy would be a second place to fix the switch.
 */
@Composable
private fun RowCard(
    row: PluginManagerRow,
    mutation: PluginMutationState?,
    schemaByNamespace: Map<String, ResolvedSettingsSchema>,
    locale: String,
    onToggle: (String, Boolean) -> Unit,
    onOpen: (PluginManagerRow) -> Unit,
) {
    val ns = row.patchId?.takeIf { it in schemaByNamespace }
    PluginRow(
        // Resolved against the device locale, not the DTO's own no-locale default: a manifest that
        // carries a `zh` title should name itself in Chinese here.
        title = row.meta?.title?.resolve(locale) ?: row.moduleName,
        description = row.meta?.description?.resolve(locale),
        enabled = row.enabled,
        togglable = row.togglable,
        busy = mutation?.target == row.entryId,
        lockReason = row.readOnlyReason,
        onToggle = { next -> onToggle(row.entryId, next) },
        // Only a row with a described namespace has options to open.
        onOpen = if (ns != null) ({ onOpen(row) }) else null,
        modifier = Modifier.clip(DsShapes.row).background(DsTheme.colors.bgLayer1),
    )
}
