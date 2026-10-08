package com.labteto.dshmobile.core.wire.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Plugin-inventory DTOs, ported from `packages/host/plugin-inventory/src/types.ts`.
 *
 * This inventory remains read-only. Harness 0.2.1 adds writes under the separate
 * pluginManager namespace; see Harness021Dtos and Harness021Api, and `PluginManagerDtos` for the
 * fork's own view of the same namespace.
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
 * One display string that a manifest may declare either plainly or per locale.
 *
 * The two shapes coexist in a stock profile: a third-party package's manifest writes
 * `"title": "dsh-relay"`, while an official bundle writes `"title": {"en": "Voice input", "zh": "语音输入"}`
 * so the harness's own UI can follow its language. A plain `String?` field rejects the second
 * outright — and because these sit inside a larger object, that rejection costs the whole bundle,
 * which is how seven of a live profile's seventeen bundles disappeared from a list of seventeen.
 *
 * Decoding is deliberately permissive and never fails: a value of an unexpected shape reads as
 * absent rather than throwing. Resolution takes the preferred locale, then the first declared one,
 * so a locale the manifest does not carry still gets a readable name.
 */
@Serializable(with = LocalizedTextSerializer::class)
data class LocalizedText(
    /** The plain form, when the manifest declared one. */
    val plain: String? = null,
    /** The per-locale form, keyed by BCP-47 tag. */
    val byLocale: Map<String, String> = emptyMap(),
) {
    /** Whether there is any text at all. */
    val isBlank: Boolean get() = resolve() == null

    /**
     * The best string for [locale], falling back through its language, then any declared value.
     *
     * The middle step matters: a phone set to `zh-rCN` should match a manifest's `zh` entry, and a
     * manifest declaring only `zh` should still name itself on an English phone rather than vanish.
     */
    fun resolve(locale: String? = null): String? {
        plain?.takeIf { it.isNotBlank() }?.let { return it }
        if (byLocale.isEmpty()) return null
        val tag = locale?.replace('_', '-')
        if (tag != null) {
            byLocale[tag]?.takeIf { it.isNotBlank() }?.let { return it }
            val language = tag.substringBefore('-')
            byLocale[language]?.takeIf { it.isNotBlank() }?.let { return it }
        }
        // Deterministic fallback: the manifest's own first entry, ordered by key so the same
        // manifest always resolves the same way regardless of map iteration order.
        return byLocale.toSortedMap().values.firstOrNull { it.isNotBlank() }
    }
}

/**
 * Reads [LocalizedText] from either shape a manifest uses.
 *
 * Written by hand rather than generated because the type is a union the serializer generator cannot
 * express, and because the failure mode it prevents is silent: a strict decoder drops the enclosing
 * object rather than the field.
 */
object LocalizedTextSerializer : KSerializer<LocalizedText> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalizedText", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): LocalizedText {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        return when (element) {
            is JsonPrimitive -> LocalizedText(plain = element.contentOrNull)
            is JsonObject -> LocalizedText(
                byLocale = element.mapNotNull { (key, value) ->
                    (value as? JsonPrimitive)?.contentOrNull?.let { key to it }
                }.toMap(),
            )
            else -> LocalizedText()
        }
    }

    override fun serialize(encoder: Encoder, value: LocalizedText) {
        // The app only ever reads these, but the compiler needs a body: emit the plain form when
        // there is one and the first locale otherwise, matching the shape a manifest would use.
        val text = value.plain ?: value.resolve()
        if (text == null) encoder.encodeNull() else encoder.encodeString(text)
    }
}

/**
 * Display identity of an entry's owning package, read from its manifest.
 *
 * Every field mirrors the manifest verbatim and is display copy rather than anything structural.
 * `plugin-packages` resolves it at request time and leaves it off entirely for a loose module with
 * no owning manifest, which is why the whole object is optional on a row.
 *
 * The fields are [LocalizedText] because the two shapes coexist — see that type for why a plain
 * string field is not enough. [icon] is an inline `data:` URI on the official bundles and is not
 * decoded here: nothing in the app renders it, and carrying a base64 SVG per bundle through the
 * wire format would cost more than it is worth.
 */
@Serializable
data class PluginInventoryMeta(
    @SerialName("title") val title: LocalizedText? = null,
    @SerialName("description") val description: LocalizedText? = null,
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
    val displayTitle: String get() = meta?.title?.resolve() ?: moduleName
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
