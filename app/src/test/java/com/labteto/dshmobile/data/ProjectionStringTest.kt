package com.labteto.dshmobile.data

import com.labteto.dshmobile.core.wire.dto.SessionProjectionsBlock
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The agent preset reaches the client only as a projection, and the chrome silently showed nothing
 * until this was read.
 *
 * `SessionSummary` — the type behind a session-list row — has no `agentPreset` field. The id lives
 * in `SessionProjectionMap`, so it rides the row's `projections.values` or arrives later as a live
 * projection update. The app declared the field on its own DTO anyway, which meant it deserialised
 * to `null` forever: both the top-bar chip and the details panel's pill were gated on it, so the
 * preset was invisible and unreachable in both places.
 *
 * These pin the parsing itself, since that is what was wrong.
 */
class ProjectionStringTest {

    private fun block(vararg pairs: Pair<String, kotlinx.serialization.json.JsonElement>) =
        SessionProjectionsBlock(asOfSeq = 1, values = buildJsonObject { pairs.forEach { (k, v) -> put(k, v) } })

    @Test
    fun `a bare string projection is read`() {
        val b = block("agentPreset" to JsonPrimitive("standard"))
        assertEquals("standard", projectionString(b, "agentPreset"))
    }

    @Test
    fun `the object form is read too`() {
        // The shape has varied across harness versions, and title is read the same lenient way.
        val b = block("agentPreset" to buildJsonObject { put("agentPreset", JsonPrimitive("code")) })
        assertEquals("code", projectionString(b, "agentPreset"))
    }

    @Test
    fun `a missing key is null rather than an error`() {
        // A host that predates the projection simply has no entry; that must not throw.
        assertNull(projectionString(block("title" to JsonPrimitive("hi")), "agentPreset"))
    }

    @Test
    fun `a null projection value is null`() {
        // `SessionProjectionMap` declares `agentPreset: string | null`, so an explicit null is a
        // real payload and not a malformed one.
        assertNull(projectionString(block("agentPreset" to JsonNull), "agentPreset"))
    }

    @Test
    fun `a null block is null`() {
        assertNull(projectionString(null, "agentPreset"))
    }

    @Test
    fun `an unexpected shape does not throw`() {
        // An array where a string was expected: return null rather than crash the list refresh.
        val b = block("agentPreset" to kotlinx.serialization.json.buildJsonArray { add(JsonPrimitive("x")) })
        assertNull(projectionString(b, "agentPreset"))
    }

    @Test
    fun `title still reads through the shared helper`() {
        // `title` was refactored onto the same helper as the preset, so it must not regress.
        val b = block("title" to JsonPrimitive("Refactor the composer"))
        assertEquals("Refactor the composer", projectionString(b, "title"))
    }
}
