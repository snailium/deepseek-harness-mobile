package com.labteto.dshmobile.core.wire.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The `pluginManager` DTOs — the profile's *manageable* plugin surface.
 *
 * This is a different service from `pluginInventory`, and the difference is the whole point.
 * `pluginInventory/list` is a read-only projection of the Cordis loader tree: it says what is
 * mounted, and it has no counterpart that changes anything. `pluginManager/…` is what the harness's
 * own Plugins page drives — it can list the bundles a profile composes, turn one on or off, install
 * and remove them, and it applies the change live without a harness restart.
 *
 * Neither the inventory nor this surface is a substitute for the other. The inventory sees rows that
 * no bundle owns (`cordis:include`, loose modules); the manager sees the *bundles* a user installed
 * or could install, which is what the Official and Installed sections on the harness page are.
 *
 * ## Read-only rows are per-row, not per-service
 *
 * Every row and bundle carries an optional [readOnlyReason], and it is the authoritative answer to
 * "may this be toggled" — not something the client should infer from the name or the owning scope.
 * Two values are produced today:
 *
 * - `management-required` — the row belongs to a bundle that is not itself manageable, typically the
 *   base or app bundle a profile is built on. Disabling it would take out the harness.
 * - `unaddressable` — the row has no owning manifest, so there is no package to name a patch
 *   against. The root `cordis:include` entry is the one such row.
 *
 * A row with no reason may still fail: a mutation re-checks on the Host, and answers a
 * [PluginMutationResult] whose `application` is `failed` with the same vocabulary in its error code.
 * So the client treats [readOnlyReason] as a *display* hint — it greys the switch rather than hiding
 * it — and still renders whatever the Host says when a call is actually made.
 */

/**
 * Why a row or bundle cannot be toggled.
 *
 * Kept as a string rather than an enum: the Host's vocabulary is open (its own refusal codes include
 * `unknown-plugin` and `invalid-spec`, which only appear on a mutation), and a value this build has
 * never seen should leave the row visible with its switch disabled rather than fail the decode.
 */
object PluginReadOnly {
    /** The owning bundle is not manageable — the profile is built on it. */
    const val MANAGEMENT_REQUIRED = "management-required"

    /** No owning manifest, so there is no package to address a patch against. */
    const val UNADDRESSABLE = "unaddressable"
}

/**
 * Which set a bundle belongs to on the harness's Plugins page.
 *
 * The split is derivable from the bundle's own flags and that is how it is computed here, rather
 * than by matching the package scope: `installed` is what actually separates a bundle the operator
 * added from one that ships with the profile.
 */
enum class PluginBundleGroup {
    /** Ships with the profile but is not installed — the catalog of what could be turned on. */
    OFFICIAL,

    /** Present in the profile's compose list. */
    INSTALLED,
}

/**
 * One row of `pluginManager/listPlugins`.
 *
 * A superset of [PluginInventoryEntry]: same identity and lifecycle fields, plus the two the
 * manager adds. [patchId] is the row id inside its owning bundle's patch — the handle a
 * `cordis.patch.yml` override names, and null for a row no bundle claims.
 */
@Serializable
data class PluginManagerRow(
    @SerialName("entryId") val entryId: String,
    @SerialName("moduleName") val moduleName: String,
    @SerialName("enabled") val enabled: Boolean,
    @SerialName("fiberPhase") val fiberPhase: PluginFiberPhase? = null,
    @SerialName("meta") val meta: PluginInventoryMeta? = null,
    /** Row id within the owning bundle's patch; null when no bundle owns this row. */
    @SerialName("patchId") val patchId: String? = null,
    /** Set when the Host refuses to toggle this row; null when it may be toggled. */
    @SerialName("readOnlyReason") val readOnlyReason: String? = null,
) {
    /** The name to print, preferring the manifest title. */
    val displayTitle: String get() = meta?.title?.resolve() ?: moduleName

    /** Whether the Host expects to accept a toggle for this row. */
    val togglable: Boolean get() = readOnlyReason == null
}

/**
 * One row inside a bundle's composition, as `pluginManager/listBundles` reports it.
 *
 * [rowId] is the row's identity *within its bundle's patch* and is always present. [entryId] is the
 * live loader-tree id, and it exists only once the row has actually been composed into the running
 * tree — a bundle that is installed but disabled, or merely offered in the catalog, reports rows
 * with a `rowId` and no `entryId` at all. Requiring it here is what silently dropped seven of a live
 * profile's seventeen bundles: the enclosing bundle failed to decode because its rows did.
 */
@Serializable
data class PluginBundleRow(
    @SerialName("rowId") val rowId: String,
    @SerialName("moduleName") val moduleName: String,
    /** The live entry id; null until this row is composed into the running tree. */
    @SerialName("entryId") val entryId: String? = null,
    @SerialName("meta") val meta: PluginInventoryMeta? = null,
) {
    /** The name to print, preferring the manifest title over the module specifier. */
    val displayTitle: String get() = meta?.title?.resolve() ?: moduleName
}

/**
 * One bundle of `pluginManager/listBundles`.
 *
 * [installed] separates the profile's own bundles from the ones an operator added; [removable] says
 * whether the Host would accept removing it, which is false for the base and app bundles a profile
 * cannot do without. [overrides] names rows this bundle replaces from a bundle it layers over.
 */
@Serializable
data class PluginBundle(
    @SerialName("name") val name: String,
    @SerialName("version") val version: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("meta") val meta: PluginInventoryMeta? = null,
    @SerialName("enabled") val enabled: Boolean = false,
    @SerialName("installed") val installed: Boolean = false,
    /** Whether the Host offers this bundle in the catalog to install. */
    @SerialName("optional") val optional: Boolean = false,
    /** Whether the Host would accept a removal. */
    @SerialName("removable") val removable: Boolean = false,
    @SerialName("rows") val rows: List<PluginBundleRow> = emptyList(),
    /** Row ids this bundle replaces from a bundle it layers over. */
    @SerialName("overrides") val overrides: List<String> = emptyList(),
    @SerialName("readOnlyReason") val readOnlyReason: String? = null,
) {
    /** The one-line description to print, preferring the manifest's. */
    val displayDescription: String?
        get() = meta?.description?.resolve() ?: description?.takeIf { it.isNotBlank() }

    /**
     * The name to print for this bundle in [locale].
     *
     * A bundle has no module specifier to fall back to the way a plugin row does — its [name] is a
     * package name that is also its identity, so it is the fallback. The locale argument exists
     * because a bundle's manifest may localize its title, and the caller is the only one that knows
     * which language the reader is using.
     */
    fun displayTitle(locale: String? = null): String =
        meta?.title?.resolve(locale) ?: name

    /** Whether the Host expects to accept a toggle for this bundle. */
    val togglable: Boolean get() = readOnlyReason == null

    /** Which section this bundle belongs to. */
    val group: PluginBundleGroup
        get() = if (installed) PluginBundleGroup.INSTALLED else PluginBundleGroup.OFFICIAL
}

/**
 * What `pluginManager/setPluginEnabled` and `setBundleEnabled` answer.
 *
 * The call succeeds at the carrier and value level even when the change did not happen: `ok: true`
 * with `application: "failed"` and an [error] is how a refusal arrives. Only [application]
 * distinguishes "the harness applied it" from "the harness declined", which is why a caller must
 * check it rather than treating `ok` as success.
 */
@Serializable
data class PluginMutationResult(
    /** `enable` or `disable` — which direction was attempted. */
    @SerialName("stage") val stage: String? = null,
    /** The entry id or bundle name the call addressed. */
    @SerialName("target") val target: String? = null,
    /** The state that was requested. */
    @SerialName("enabled") val enabled: Boolean = false,
    /** Whether anything actually moved; false when the target was already in that state. */
    @SerialName("changed") val changed: Boolean = false,
    /** `applied` or `failed`. */
    @SerialName("application") val application: String? = null,
    @SerialName("warnings") val warnings: List<String> = emptyList(),
    /** Present only when [application] is `failed`. */
    @SerialName("error") val error: PluginMutationError? = null,
) {
    /** Whether the Host applied the change. */
    val applied: Boolean get() = application == APPLIED && error == null

    companion object {
        const val APPLIED = "applied"
        private const val FAILED = "failed"

        /** Whether an application value means the change did not happen. */
        fun failed(application: String?): Boolean = application == FAILED
    }
}

/** Why a mutation was refused. */
@Serializable
data class PluginMutationError(
    /** One of [PluginReadOnly]'s values, or a manager refusal such as `unknown-plugin`. */
    @SerialName("code") val code: String? = null,
    @SerialName("message") val message: String? = null,
)
