package com.labteto.dshmobile.ui.screens.settings

import com.labteto.dshmobile.core.wire.dto.PluginFiberPhase
import com.labteto.dshmobile.core.wire.dto.PluginInventoryEntry
import com.labteto.dshmobile.core.wire.dto.PluginInventoryMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The plugin page's pure row logic.
 *
 * Two things on that page are worth pinning without a device: how a row answers a filter, and how a
 * module specifier is shortened for display. Both are string transforms over data the harness
 * sends, and both decide what the reader sees.
 *
 * The filter is deliberately case-insensitive and matches the entry id as well as the visible
 * fields — the entry id is what `cordis.patch.yml` names, so pasting one into the box is the
 * natural way to find the row it configures.
 */
class PluginsScreenTest {

    private fun row(
        entryId: String = "e1",
        moduleName: String = "@deepseek-ai/dsh-llm",
        enabled: Boolean = true,
        phase: PluginFiberPhase? = PluginFiberPhase.ACTIVE,
        title: String? = null,
        description: String? = null,
    ) = PluginInventoryEntry(
        entryId = entryId,
        moduleName = moduleName,
        enabled = enabled,
        fiberPhase = phase,
        meta = if (title == null && description == null) null
        else PluginInventoryMeta(title = title, description = description),
    )

    // ------------------------------------------------------------------------- filter matching

    @Test
    fun `a query matches the module name`() {
        assertTrue(row().matches("dsh-llm"))
    }

    @Test
    fun `matching ignores case`() {
        assertTrue(row().matches("DSH-LLM"))
    }

    @Test
    fun `a query matches the entry id`() {
        assertTrue(row(entryId = "include:plugin-manager").matches("include:plugin"))
    }

    @Test
    fun `a query matches the manifest title`() {
        assertTrue(row(title = "Plugin manager").matches("manager"))
    }

    @Test
    fun `a query matches the manifest description`() {
        assertTrue(row(description = "Provider-neutral LLM service").matches("neutral"))
    }

    /** An empty or whitespace-only box is "no filter", not "match nothing". */
    @Test
    fun `an empty query matches every row`() {
        assertTrue(row().matches(""))
        assertTrue(row().matches("   "))
    }

    @Test
    fun `a query that matches nothing is rejected`() {
        assertFalse(row(description = "Provider-neutral LLM service").matches("zzz"))
    }

    /** A row with no `meta` must not throw when the filter consults fields it does not have. */
    @Test
    fun `matching a row without meta only consults its identity`() {
        val bare = row(moduleName = "cordis:include", entryId = "include")
        assertTrue(bare.matches("include"))
        assertFalse(bare.matches("neutral"))
    }

    // --------------------------------------------------------------------------- display title

    /**
     * The manifest title and the module name are the same string on nearly every row, so the
     * fallback is not cosmetic — it is the path most rows take.
     */
    @Test
    fun `displayTitle prefers the manifest title`() {
        assertEquals("Plugin manager", row(title = "Plugin manager").displayTitle)
    }

    @Test
    fun `displayTitle falls back to the module name`() {
        assertEquals("@deepseek-ai/dsh-llm", row().displayTitle)
    }

    /**
     * The harness sends `meta` with a title on the overwhelming majority of rows, so a blank one is
     * the case that reaches the fallback in practice. Printing it would leave a nameless row.
     */
    @Test
    fun `a blank title falls back to the module name`() {
        assertEquals("@deepseek-ai/dsh-llm", row(title = "   ").displayTitle)
    }

    // ------------------------------------------------------------------------ module shortening

    /**
     * Ported from the harness's own `moduleShortName`, so both lists name a plugin the same way.
     * The scope and the `dsh-` prefixes are identical on nearly every row and push the part that
     * differs off a phone screen.
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

    /** A bare name with no prefix to strip is returned as-is rather than emptied. */
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
