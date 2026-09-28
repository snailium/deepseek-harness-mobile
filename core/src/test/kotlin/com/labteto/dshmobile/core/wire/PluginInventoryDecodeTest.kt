package com.labteto.dshmobile.core.wire

import com.labteto.dshmobile.core.wire.dto.PluginFiberPhase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream

/**
 * `pluginInventory/list` decoding.
 *
 * The inventory is the one place the app reads the harness's own composition, so it sees whatever
 * the deployment happens to mount — including rows from plugins this build has never heard of. One
 * unrecognised `fiberPhase` must cost that row and nothing else; emptying the list would report a
 * deployment as having no plugins at all.
 */
class PluginInventoryDecodeTest {

    private class FixedTransport(private val body: String) : RpcTransport {
        override suspend fun post(path: String, body: String): RpcHttpResponse =
            RpcHttpResponse(200, this.body)

        override suspend fun <T> download(path: String, consume: (String?, String?, InputStream) -> T): T =
            error("not used")

        override suspend fun upload(
            path: String,
            contentType: String,
            contentLength: Long,
            body: InputStream,
            onProgress: ((Long) -> Unit)?,
        ): RpcHttpResponse = error("not used")
    }

    private fun clientReturning(value: String) = DshApiClient(
        transport = FixedTransport(
            """{"type":"server-response","rpcId":"1","result":{"ok":true,"value":$value}}""",
        ),
    )

    private suspend fun listOrFail(value: String) =
        when (val r = clientReturning(value).pluginInventoryList()) {
            is RpcResult.Ok -> r.value
            is RpcResult.Err -> error("expected success, got ${r.error.code}: ${r.error.message}")
        }

    @Test
    fun `a full row decodes`() = runTest {
        val snapshot = listOrFail(
            """{"entries":[{"entryId":"e1","moduleName":"@deepseek-ai/dsh-client-ui-plan",""" +
                """"enabled":true,"fiberPhase":"active"}]}""",
        )
        val entry = snapshot.entries.single()
        assertEquals("e1", entry.entryId)
        assertEquals("@deepseek-ai/dsh-client-ui-plan", entry.moduleName)
        assertTrue(entry.enabled)
        assertEquals(PluginFiberPhase.ACTIVE, entry.fiberPhase)
    }

    /** A disabled row never mounts, so the host sends no phase at all. */
    @Test
    fun `an absent phase decodes as null rather than failing`() = runTest {
        val snapshot = listOrFail("""{"entries":[{"entryId":"e1","moduleName":"m","enabled":false}]}""")
        assertNull(snapshot.entries.single().fiberPhase)
    }

    @Test
    fun `an explicit null phase decodes as null`() = runTest {
        val snapshot = listOrFail(
            """{"entries":[{"entryId":"e1","moduleName":"m","enabled":true,"fiberPhase":null}]}""",
        )
        assertNull(snapshot.entries.single().fiberPhase)
    }

    /**
     * `WireJson` coerces an out-of-range value to the property's default, so a phase this build has
     * never heard of costs the phase, not the row. That is the outcome worth having: the plugin is
     * still named and its enablement is still right, and only the one field nobody can interpret
     * goes quiet.
     */
    @Test
    fun `an unknown phase keeps its row and reads as no phase`() = runTest {
        val snapshot = listOrFail(
            """{"entries":[""" +
                """{"entryId":"good","moduleName":"m","enabled":true,"fiberPhase":"active"},""" +
                """{"entryId":"weird","moduleName":"m","enabled":true,"fiberPhase":"reticulating"},""" +
                """{"entryId":"also-good","moduleName":"m","enabled":false}]}""",
        )
        assertEquals(listOf("good", "weird", "also-good"), snapshot.entries.map { it.entryId })
        assertNull(snapshot.entries[1].fiberPhase)
    }

    /** A row missing a required member has nothing to fall back on, so that one is dropped. */
    @Test
    fun `a malformed row drops without emptying the list`() = runTest {
        val snapshot = listOrFail(
            """{"entries":[""" +
                """{"entryId":"good","moduleName":"m","enabled":true},""" +
                """{"moduleName":"m","enabled":true}]}""",
        )
        assertEquals(listOf("good"), snapshot.entries.map { it.entryId })
    }

    @Test
    fun `unrecognised members on a row are ignored`() = runTest {
        val snapshot = listOrFail(
            """{"entries":[{"entryId":"e1","moduleName":"m","enabled":true,"somethingNew":42}]}""",
        )
        assertEquals("e1", snapshot.entries.single().entryId)
    }

    @Test
    fun `a deployment with no plugins decodes as an empty list`() = runTest {
        assertTrue(listOrFail("""{"entries":[]}""").entries.isEmpty())
    }

    /** The codec folds an absent value into `{}`, so the empty case has to survive a missing key. */
    @Test
    fun `a missing entries key decodes as an empty list`() = runTest {
        assertTrue(listOrFail("{}").entries.isEmpty())
    }

    // --------------------------------------------------------------- 0.1.7 display metadata

    /**
     * The manifest identity is what turns a page of `@deepseek-ai/dsh-…` into readable rows, and it
     * arrives on the same row as the tuple rather than through a second call.
     */
    @Test
    fun `a row carries its manifest title and description`() = runTest {
        val snapshot = listOrFail(
            """{"entries":[{"entryId":"e1","moduleName":"@deepseek-ai/dsh-llm","enabled":true,""" +
                """"fiberPhase":"active","meta":{"title":"@deepseek-ai/dsh-llm",""" +
                """"description":"Provider-neutral LLM service"}}]}""",
        )
        val entry = snapshot.entries.single()
        assertEquals("@deepseek-ai/dsh-llm", entry.meta?.title)
        assertEquals("Provider-neutral LLM service", entry.meta?.description)
    }

    /**
     * `plugin-packages` leaves `meta` off a loose module with no owning manifest. The row still has
     * a name and a state, so it must survive without one.
     */
    @Test
    fun `a row without meta decodes and falls back to its module name`() = runTest {
        val snapshot = listOrFail(
            """{"entries":[{"entryId":"include","moduleName":"cordis:include","enabled":true}]}""",
        )
        val entry = snapshot.entries.single()
        assertNull(entry.meta)
        assertEquals("cordis:include", entry.displayTitle)
    }

    @Test
    fun `a meta with only a title decodes`() = runTest {
        val snapshot = listOrFail(
            """{"entries":[{"entryId":"e1","moduleName":"m","enabled":true,"meta":{"title":"T"}}]}""",
        )
        assertEquals("T", snapshot.entries.single().meta?.title)
        assertNull(snapshot.entries.single().meta?.description)
    }

    /** A blank title is not a name; preferring it would print an empty row. */
    @Test
    fun `a blank title falls back to the module name`() = runTest {
        val snapshot = listOrFail(
            """{"entries":[{"entryId":"e1","moduleName":"@deepseek-ai/dsh-llm","enabled":true,""" +
                """"meta":{"title":""}}]}""",
        )
        assertEquals("@deepseek-ai/dsh-llm", snapshot.entries.single().displayTitle)
    }

    // -------------------------------------------------------------------- agent presets

    /**
     * The presets are compositions, not the live tree: `standard` and `ptc` overlap heavily, and a
     * row's `enabled` there says what that preset would mount.
     */
    @Test
    fun `agent presets decode with their rows and default flag`() = runTest {
        val snapshot = listOrFail(
            """{"entries":[],"agentPresets":[""" +
                """{"id":"standard","isDefault":true,"rows":[""" +
                """{"entryId":"r1","moduleName":"@deepseek-ai/dsh-persona","enabled":true,""" +
                """"fiberPhase":"active","meta":{"title":"persona","description":"Persona"}}]},""" +
                """{"id":"minimal","rows":[{"entryId":"r2","moduleName":"m","enabled":false}]}]}""",
        )
        assertEquals(listOf("standard", "minimal"), snapshot.agentPresets.map { it.id })
        assertTrue(snapshot.agentPresets[0].isDefault)
        assertEquals("persona", snapshot.agentPresets[0].rows.single().meta?.title)
        // An omitted `isDefault` is a non-default preset, not a decode failure.
        assertEquals(false, snapshot.agentPresets[1].isDefault)
    }

    /** A harness with no preset service omits the key entirely, and that is not an error. */
    @Test
    fun `a missing agentPresets key decodes as an empty list`() = runTest {
        val snapshot = listOrFail("""{"entries":[]}""")
        assertTrue(snapshot.agentPresets.isEmpty())
        // And the flag defaults off rather than throwing on an absent boolean.
        assertEquals(false, snapshot.managementAvailable)
    }

    @Test
    fun `managementAvailable decodes when the host sends it`() = runTest {
        assertTrue(listOrFail("""{"entries":[],"managementAvailable":true}""").managementAvailable)
    }

    // ------------------------------------------------------------- whole-list robustness

    /**
     * The narrowing is per-row at the top level and per-row inside each preset. A single unreadable
     * row must not take down the preset that owns it, which is the same contract [entries] has.
     */
    @Test
    fun `a malformed preset row drops without emptying the preset`() = runTest {
        val snapshot = listOrFail(
            """{"entries":[],"agentPresets":[{"id":"standard","rows":[""" +
                """{"entryId":"good","moduleName":"m","enabled":true},""" +
                """{"moduleName":"m","enabled":true}]}]}""",
        )
        assertEquals(listOf("good"), snapshot.agentPresets.single().rows.map { it.entryId })
    }

    /**
     * The captured answer from a live harness 0.1.7-rc.2, trimmed to a representative slice.
     *
     * The hand-written cases above encode what the schema says; this one encodes what the harness
     * actually sends, which is the part hand-written cases get wrong. It caught nothing on the day
     * it was added, but the `meta`-less `cordis:include` row and the four preset compositions are
     * exactly the shapes a future refactor would silently start dropping.
     */
    @Test
    fun `the live 0_1_7 answer decodes`() = runTest {
        val body = javaClass.classLoader!!
            .getResourceAsStream("plugin-inventory-017.json")!!
            .bufferedReader().use { it.readText() }
        val snapshot = listOrFail(body)

        assertEquals(12, snapshot.entries.size)
        assertTrue(snapshot.managementAvailable)

        // The meta-less row survives with its identity intact and a usable display name.
        val bare = snapshot.entries.single { it.entryId == "include" }
        assertNull(bare.meta)
        assertEquals("cordis:include", bare.displayTitle)
        assertTrue(bare.enabled)

        // A row with meta keeps the manifest title and description.
        val described = snapshot.entries.first { it.entryId == "include:plugin-manager" }
        assertEquals("@deepseek-ai/dsh-plugin-manager", described.meta?.title)
        assertEquals(
            "Current-profile plugin and bundle management shared by dsh CLI, Web and agent tools",
            described.meta?.description,
        )

        // All four standing presets arrive, in the order the harness lists them, with `standard`
        // marked default and each carrying its own composition rows.
        assertEquals(listOf("standard", "ptc", "minimal", "cordis"), snapshot.agentPresets.map { it.id })
        assertEquals("standard", snapshot.agentPresets.single { it.isDefault }.id)
        snapshot.agentPresets.forEach { preset ->
            assertTrue("preset ${preset.id} has no rows", preset.rows.isNotEmpty())
        }
    }

    /** An entry row with a blank module name has nothing to display, so it is narrowed out. */
    @Test
    fun `a row with blank identity strings is dropped`() = runTest {
        val snapshot = listOrFail(
            """{"entries":[""" +
                """{"entryId":"good","moduleName":"m","enabled":true},""" +
                """{"entryId":"","moduleName":"m","enabled":true}]}""",
        )
        assertEquals(listOf("good"), snapshot.entries.map { it.entryId })
    }
}
