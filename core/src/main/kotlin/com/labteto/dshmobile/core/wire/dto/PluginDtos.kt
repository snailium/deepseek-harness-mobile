package com.labteto.dshmobile.core.wire.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Plugin-inventory DTOs, ported from `packages/host/plugin-inventory/src/types.ts`.
 *
 * The inventory is read-only by design: `pluginInventory/list` is the namespace's entire surface —
 * there is no enable/disable call anywhere in the harness. Which plugins load is decided by
 * `cordis.patch.yml`, and the settings that configure them go through `settings.*`, which is
 * loopback-pinned and answers 403 to anything reaching the host over the network. So a phone can
 * see the composition and nothing more, which is exactly what the harness's own "Plugin list" tab
 * offers.
 *
 * What the answer *does* carry is richer than the entry tuple alone. `meta` supplies the display
 * title and one-line description straight from the installed package manifest, and
 * `agentPresets` carries the same rows as each standing preset composes them. Both are optional:
 * a harness without `plugin-packages` composed answers rows without `meta`, and one without
 * `agentPresets` omits that key — so every field below defaults rather than being required.
 */

/** Where a plugin has got to in the Cordis loader's lifecycle. Absent means it never mounted. */
@Serializable
enum class PluginFiberPhase {
    @SerialName("pending")
    PENDING,

    @SerialName("loading")
    LOADING,

    @SerialName("active")
    ACTIVE,

    @SerialName("failed")
    FAILED,

    @SerialName("unloading")
    UNLOADING,
}

/**
 * Display identity of an entry's owning package, read from its `package.json`.
 *
 * Both fields mirror the manifest verbatim — the title is usually the package name and the
 * description is the line the package author wrote — so this is display copy rather than anything
 * structural. `plugin-packages` resolves it at request time and leaves it off entirely for a loose
 * module with no owning manifest, which is why the whole object is optional on a row.
 */
@Serializable
data class PluginInventoryMeta(
    @SerialName("title") val title: String? = null,
    @SerialName("description") val description: String? = null,
)

/** One row of `pluginInventory/list`, and of each row list under `agentPresets`. */
@Serializable
data class PluginInventoryEntry(
    /** The loader-tree entry id — stable, and what a `cordis.patch.yml` override targets. */
    @SerialName("entryId") val entryId: String,
    /** The exact module specifier, e.g. `@deepseek-ai/dsh-client-ui-plan`. */
    @SerialName("moduleName") val moduleName: String,
    /** Effective enablement, already folded through any disabled ancestor group. */
    @SerialName("enabled") val enabled: Boolean,
    @SerialName("fiberPhase") val fiberPhase: PluginFiberPhase? = null,
    /** Manifest title and description; absent for a module no package owns. */
    @SerialName("meta") val meta: PluginInventoryMeta? = null,
) {
    /**
     * What a list should print as this row's name.
     *
     * The manifest title is the same string as the module name on nearly every row, so preferring
     * it buys nothing there; it matters for the handful of rows whose package declares a friendlier
     * title, and it is the only name available when a row has no module name to shorten.
     */
    val displayTitle: String get() = meta?.title?.takeIf { it.isNotBlank() } ?: moduleName
}

/**
 * One standing agent preset, as the rows it composes.
 *
 * The same plugin appears under several presets — the composition rows are drawn from `rows` in
 * `cordis.patch.yml`, so `standard` and `ptc` overlap heavily. [rows] therefore describes what
 * *that* preset would mount, not what is mounted now; the top-level [PluginInventorySnapshot.entries]
 * is the live tree.
 */
@Serializable
data class AgentPresetComposition(
    @SerialName("id") val id: String,
    @SerialName("isDefault") val isDefault: Boolean = false,
    @SerialName("rows") val rows: List<PluginInventoryEntry> = emptyList(),
)

/**
 * Value of `pluginInventory/list`.
 *
 * [managementAvailable] says whether the profile can install plugins at all — it is present only
 * when the harness composed its `pluginManager`. It is *not* a promise that this client may manage
 * anything: the management calls themselves are loopback-pinned, so a phone reaching the harness
 * over the network sees `true` and still cannot call them.
 */
@Serializable
data class PluginInventorySnapshot(
    @SerialName("entries") val entries: List<PluginInventoryEntry> = emptyList(),
    @SerialName("agentPresets") val agentPresets: List<AgentPresetComposition> = emptyList(),
    @SerialName("managementAvailable") val managementAvailable: Boolean = false,
)
