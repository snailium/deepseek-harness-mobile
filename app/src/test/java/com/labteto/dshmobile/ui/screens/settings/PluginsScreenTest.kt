package com.labteto.dshmobile.ui.screens.settings

import com.labteto.dshmobile.core.wire.dto.LocalizedText
import com.labteto.dshmobile.core.wire.dto.ProfileBundle
import com.labteto.dshmobile.core.wire.dto.PluginBundleGroup
import com.labteto.dshmobile.core.wire.dto.PluginInventoryMeta
import com.labteto.dshmobile.core.wire.dto.PluginReadOnly
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The plugin page's pure row logic.
 *
 * Two things are worth pinning without a device: how a bundle answers a filter, and how a module
 * specifier is shortened for display. Both are string transforms over data the Host sends, and both
 * decide what the reader sees.
 *
 * The DTO-level rules that used to live here — togglability from `readOnlyReason`, the display-title
 * fallback, the localization union — now sit on the DTOs themselves and are covered by
 * `PluginManagerDecodeTest` and `PluginInventoryDecodeTest` in `:core`, which is where they belong:
 * they are facts about the wire format, not about this screen.
 */
class PluginsScreenTest {

    private fun bundle(
        name: String = "dsh-relay",
        version: String? = "0.3.0",
        description: String? = "Authenticated remote access",
        title: LocalizedText? = null,
        installed: Boolean = true,
        enabled: Boolean = true,
        readOnlyReason: String? = null,
    ) = ProfileBundle(
        name = name,
        version = version,
        description = description,
        meta = title?.let { PluginInventoryMeta(title = it) },
        enabled = enabled,
        installed = installed,
        readOnlyReason = readOnlyReason,
    )

    // ------------------------------------------------------------------------- filter matching

    @Test
    fun `a query matches the bundle name`() {
        assertTrue(bundle().matches("relay"))
    }

    @Test
    fun `matching ignores case`() {
        assertTrue(bundle().matches("RELAY"))
    }

    @Test
    fun `a query matches the version`() {
        assertTrue(bundle(name = "x", version = "0.3.0").matches("0.3"))
    }

    @Test
    fun `a query matches the description`() {
        assertTrue(bundle().matches("authenticated"))
    }

    @Test
    fun `a query matches a localized title in the requested locale`() {
        val localized = bundle(
            name = "@deepseek-ai/dsh-experimental-voice-input-bundle",
            title = LocalizedText(byLocale = mapOf("en" to "Voice input", "zh" to "语音输入")),
        )
        assertTrue(localized.matches("voice", locale = "en-US"))
        assertTrue(localized.matches("语音", locale = "zh-CN"))
    }

    @Test
    fun `an empty query matches every bundle`() {
        assertTrue(bundle().matches(""))
        assertTrue(bundle().matches("   "))
    }

    @Test
    fun `a query that matches nothing is rejected`() {
        assertFalse(bundle().matches("zzz"))
    }

    /** A bundle whose optional fields are absent must not throw when the filter consults them. */
    @Test
    fun `a minimal bundle still matches on its name`() {
        val bare = bundle(name = "dsh-relay", version = null, description = null)
        assertTrue(bare.matches("relay"))
        assertFalse(bare.matches("authenticated"))
    }

    // -------------------------------------------------------------------------- the two groups

    /**
     * `installed` is what divides the harness page's two sections, and the page draws them in that
     * order. A bundle that is merely available must not land in the installed section.
     */
    @Test
    fun `installed and uninstalled bundles sort into different groups`() {
        assertEquals(PluginBundleGroup.INSTALLED, bundle(installed = true).group)
        assertEquals(PluginBundleGroup.OFFICIAL, bundle(installed = false).group)
    }

    // --------------------------------------------------------------------------- editability

    /**
     * A bundle with no stated reason is togglable; one with a reason is not. The switch is drawn
     * either way, so this only decides whether it is enabled.
     */
    @Test
    fun `a bundle without a read-only reason is togglable`() {
        assertTrue(bundle(readOnlyReason = null).togglable)
        assertNull(bundle(readOnlyReason = null).readOnlyReason)
    }

    @Test
    fun `a management-required bundle is not togglable`() {
        val locked = bundle(
            name = "@deepseek-ai/dsh-base",
            readOnlyReason = PluginReadOnly.MANAGEMENT_REQUIRED,
        )
        assertFalse(locked.togglable)
        assertEquals(PluginReadOnly.MANAGEMENT_REQUIRED, locked.readOnlyReason)
    }

    /** An unfamiliar reason still locks the row; the vocabulary is the Host's, not this build's. */
    @Test
    fun `an unknown read-only reason still locks the bundle`() {
        assertFalse(bundle(readOnlyReason = "something-new").togglable)
    }

    // -------------------------------------------------------------------------- display title

    @Test
    fun `a plain title is used verbatim`() {
        val named = bundle(name = "dsh-relay", title = LocalizedText(plain = "Relay"))
        assertEquals("Relay", named.displayTitle())
    }

    @Test
    fun `a bundle without a title falls back to its package name`() {
        assertEquals("dsh-relay", bundle(title = null).displayTitle())
    }

    @Test
    fun `a localized title resolves through the locale`() {
        val localized = bundle(title = LocalizedText(byLocale = mapOf("en" to "Voice input", "zh" to "语音输入")))
        assertEquals("Voice input", localized.displayTitle("en-US"))
        assertEquals("语音输入", localized.displayTitle("zh-CN"))
    }

    /** A locale the manifest does not carry still gets a readable name rather than nothing. */
    @Test
    fun `a localized title falls back for an unknown locale`() {
        val localized = bundle(title = LocalizedText(byLocale = mapOf("zh" to "语音输入")))
        assertEquals("语音输入", localized.displayTitle("de-DE"))
    }

    // ------------------------------------------------------------------------ module shortening

    /**
     * Ported from the harness's own `moduleShortName`, so both lists name a plugin the same way. The
     * scope and the `dsh-` prefixes are identical on nearly every row and push the part that differs
     * off a phone screen.
     */
    @Test
    fun `moduleShortName drops the scope and the dsh prefix`() {
        assertEquals("llm", moduleShortName("@deepseek-ai/dsh-llm"))
    }

    /**
     * The prefixes are tried in order and `dsh-client-` precedes `dsh-`, so the longer one wins.
     * Stripping `dsh-` first would leave `client-ui-plan`, which is not what the harness prints.
     */
    @Test
    fun `moduleShortName prefers the longest matching prefix`() {
        assertEquals("ui-plan", moduleShortName("@deepseek-ai/dsh-client-ui-plan"))
    }

    @Test
    fun `moduleShortName drops cordis prefixes`() {
        assertEquals("include", moduleShortName("cordis:include"))
        assertEquals("timer", moduleShortName("@deepseek-ai/cordis-plugin-timer"))
    }

    @Test
    fun `moduleShortName leaves a bare name alone`() {
        assertEquals("relay", moduleShortName("relay"))
    }

    /** Every prefix stripped would otherwise yield an empty string, which names nothing. */
    @Test
    fun `moduleShortName never returns blank`() {
        assertEquals("dsh-", moduleShortName("dsh-"))
    }
}

/**
 * The configuration form's pure helpers.
 *
 * `buildSettingPatch` is the one that decides whether an edit lands where the user aimed it: the
 * Host merges a patch rather than replacing a namespace, so the nesting has to match the field's
 * path exactly. Getting it wrong writes a key the schema does not declare, which the Host accepts
 * and nothing reads — a silent no-op rather than an error.
 */
class SettingsPatchTest {

    @Test
    fun `a top-level edit becomes a single-key object`() {
        assertEquals(
            """{"port":3443}""",
            buildSettingPatch(listOf("port"), JsonPrimitive(3443)).toString(),
        )
    }

    @Test
    fun `a nested edit nests one object per path segment`() {
        assertEquals(
            """{"providers":{"b70-smg":{"models":["a"]}}}""",
            buildSettingPatch(
                listOf("providers", "b70-smg", "models"),
                JsonArray(listOf(JsonPrimitive("a"))),
            ).toString(),
        )
    }

    /** A path must address something; an empty one would build `{}` and write nothing. */
    @Test
    fun `an empty path is rejected`() {
        val thrown = runCatching { buildSettingPatch(emptyList(), JsonPrimitive(1)) }.exceptionOrNull()
        assertTrue("expected a rejection, got $thrown", thrown is IllegalArgumentException)
    }

    // ------------------------------------------------------------------- reading a value back

    @Test
    fun `a top-level value reads out of the effective object`() {
        val root = Json.parseToJsonElement("""{"port":3443,"bind":"0.0.0.0"}""")
        assertEquals(JsonPrimitive(3443), fieldValueAt(listOf("port"), root))
    }

    @Test
    fun `a nested value walks its path`() {
        val root = Json.parseToJsonElement("""{"providers":{"b70-smg":{"models":[1,2]}}}""")
        assertEquals(
            Json.parseToJsonElement("""[1,2]"""),
            fieldValueAt(listOf("providers", "b70-smg", "models"), root),
        )
    }

    /** A key the user has not set reads as absent, so the control falls back to the schema default. */
    @Test
    fun `a missing segment reads as null rather than throwing`() {
        val root = Json.parseToJsonElement("""{"a":{"b":1}}""")
        assertNull(fieldValueAt(listOf("a", "gone"), root))
        assertNull(fieldValueAt(listOf("gone", "b"), root))
    }

    /** A scalar where an object was expected is null, not an exception. */
    @Test
    fun `descending into a scalar reads as null`() {
        assertNull(fieldValueAt(listOf("port", "deeper"), Json.parseToJsonElement("""{"port":1}""")))
    }
}
