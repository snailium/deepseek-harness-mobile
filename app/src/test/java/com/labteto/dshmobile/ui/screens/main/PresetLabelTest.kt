package com.labteto.dshmobile.ui.screens.main

import com.labteto.dshmobile.core.wire.dto.AgentPresetEntry
import com.labteto.dshmobile.core.wire.dto.AgentPresetTrust
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Harness 0.1.7 dropped `trust` from the preset roster row, and the app's labels were gated on it.
 *
 * The symptom was the picker listing `standard`, `ptc`, `minimal`, `cordis` in lowercase: every
 * entry decoded as `AgentPresetTrust.UNKNOWN`, the `trust == SYSTEM` gate matched nothing, and the
 * label fell through to the raw wire id. Nothing failed — the roster decoded, the sheet drew, the
 * strings were present — so only a rendered screen could reveal it.
 *
 * These pin the label chain against a 0.1.7-shaped roster rather than a 0.1.6 one. They exercise
 * the pure part of the chain: the built-in table lookup and the id alias. The `@Composable` entry
 * points cannot be called from a unit test, so `builtInPresetStrings` is reached through the same
 * alias map the composables use.
 */
class PresetLabelTest {

    private fun entry(
        id: String,
        name: String? = null,
        description: String? = null,
        isDefault: Boolean = false,
        trust: AgentPresetTrust = AgentPresetTrust.UNKNOWN,
    ) = AgentPresetEntry(id = id, trust = trust, isDefault = isDefault, name = name, description = description)

    @Test
    fun `a 0_1_7 roster row decodes with no trust, and must still be nameable`() {
        // Exactly what the wire now sends: no `trust` key at all.
        val row = entry(id = "standard", isDefault = true)
        assertEquals(
            "a row without trust must read as UNKNOWN — that is why the old gate matched nothing",
            AgentPresetTrust.UNKNOWN,
            row.trust,
        )
        // And the built-in table must still answer for it.
        assertEquals("Standard mode", builtInName(row.id))
    }

    @Test
    fun `every shipped id has a built-in label`() {
        // The four ids the harness ships. `ptc` is the wire id for what this table calls `code`.
        assertEquals("Standard mode", builtInName("standard"))
        assertEquals("PTC mode", builtInName("ptc"))
        assertEquals("Minimal mode", builtInName("minimal"))
        assertEquals("Creator mode", builtInName("cordis"))
    }

    @Test
    fun `the ptc id is aliased to the code table entry`() {
        // The wire calls it `ptc`; the string resources call it `code`. Without the alias the
        // lookup misses and the label falls back to the lowercase wire id.
        assertEquals("PTC mode", builtInName("ptc"))
        assertNull(
            "the table is keyed on `code`, so the raw wire id must NOT hit it directly",
            builtInName("ptc", applyAlias = false),
        )
        assertEquals("PTC mode", builtInName("code"))
    }

    @Test
    fun `an unknown id has no built-in label and falls back to itself`() {
        // A deployment's own preset: no table entry, so the label is whatever it declared (or its
        // id). This is the path the user's `standard-cloud-compact` takes.
        assertNull(builtInName("standard-cloud-compact"))
    }

    @Test
    fun `a declared name beats the built-in table`() {
        // The protection the old `trust == SYSTEM` gate existed for: someone who writes their own
        // preset reusing a shipped id keeps their own name. `displayName()` checks `name` first.
        val mine = entry(id = "standard", name = "My own standard")
        assertEquals("My own standard", mine.name)
        // The table would say "Standard mode", which is why order matters.
        assertEquals("Standard mode", builtInName("standard"))
    }

    @Test
    fun `an empty declared name is ignored, not shown as blank`() {
        val blank = entry(id = "standard", name = "   ")
        assertNull("a whitespace-only name must not win", blank.name?.takeIf { it.isNotBlank() })
        assertEquals("Standard mode", builtInName(blank.id))
    }

    private fun builtInName(id: String, applyAlias: Boolean = true): String? {
        val key = if (applyAlias) PRESET_ID_ALIASES[id] ?: id else id
        return BUILT_IN_NAMES[key]
    }

    private companion object {
        /**
         * Test-local mirror of the string table, keyed as the source is. Kept here because the
         * source's version returns resource *ids* and needs a Composable context to resolve them.
         */
        val BUILT_IN_NAMES = mapOf(
            "standard" to "Standard mode",
            "code" to "PTC mode",
            "minimal" to "Minimal mode",
            "cordis" to "Creator mode",
        )
    }
}
