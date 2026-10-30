package com.labteto.dshmobile.core.wire

import com.labteto.dshmobile.core.wire.dto.PluginBundleGroup
import com.labteto.dshmobile.core.wire.dto.PluginReadOnly
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream

/**
 * `pluginManager/…` decoding and mutation results.
 *
 * This is the service the harness's own Plugins page drives, and the one thing about it worth
 * pinning hardest is that a refusal is **not** a carrier error: the Host answers `ok: true` with
 * `application: "failed"` for a row it will not toggle. A client that reads only `ok` reports a
 * change that never happened, so [PluginMutationResult.applied] is the field the UI binds to and
 * these tests hold it to that.
 */
class PluginManagerDecodeTest {

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

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().use { it.readText() }

    // ---------------------------------------------------------------------- listPlugins

    @Test
    fun `a plugin row carries its patch id and editability`() = runTest {
        val rows = when (val r = clientReturning(
            """[{"entryId":"include:llm","moduleName":"@deepseek-ai/dsh-llm","enabled":true,""" +
                """"fiberPhase":"active","patchId":"llm",""" +
                """"meta":{"title":"@deepseek-ai/dsh-llm","description":"Provider-neutral"}}]""",
        ).pluginManagerListPlugins()) {
            is RpcResult.Ok -> r.value
            is RpcResult.Err -> error("expected success, got ${r.error.code}")
        }
        val row = rows.single()
        assertEquals("llm", row.patchId)
        assertNull(row.readOnlyReason)
        assertTrue(row.togglable)
        assertEquals("@deepseek-ai/dsh-llm", row.displayTitle)
    }

    /** A row no bundle claims has no patch to address, and the Host marks it unaddressable. */
    @Test
    fun `an unaddressable row reads as not togglable`() = runTest {
        val rows = listRows(
            """[{"entryId":"include","moduleName":"cordis:include","enabled":true,""" +
                """"fiberPhase":"active","readOnlyReason":"unaddressable"}]""",
        )
        val row = rows.single()
        assertFalse(row.togglable)
        assertEquals(PluginReadOnly.UNADDRESSABLE, row.readOnlyReason)
        assertNull(row.patchId)
    }

    @Test
    fun `a management-required row reads as not togglable`() = runTest {
        val rows = listRows(
            """[{"entryId":"include:timer","moduleName":"@deepseek-ai/cordis-plugin-timer",""" +
                """"enabled":true,"readOnlyReason":"management-required"}]""",
        )
        assertFalse(rows.single().togglable)
    }

    /**
     * The vocabulary is open — a reason this build has never seen must leave the row visible with
     * its switch disabled, not drop the row or fail the list.
     */
    @Test
    fun `an unknown read-only reason still disables the row`() = runTest {
        val rows = listRows(
            """[{"entryId":"e1","moduleName":"m","enabled":true,"readOnlyReason":"some-new-reason"}]""",
        )
        assertEquals("some-new-reason", rows.single().readOnlyReason)
        assertFalse(rows.single().togglable)
    }

    @Test
    fun `a row without meta falls back to its module name`() = runTest {
        val rows = listRows("""[{"entryId":"e1","moduleName":"cordis:include","enabled":true}]""")
        assertEquals("cordis:include", rows.single().displayTitle)
    }

    @Test
    fun `a row missing required identity is dropped`() = runTest {
        val rows = listRows(
            """[{"entryId":"good","moduleName":"m","enabled":true},""" +
                """{"moduleName":"m","enabled":true}]""",
        )
        assertEquals(listOf("good"), rows.map { it.entryId })
    }

    @Test
    fun `a manager-less harness reads as no rows rather than a failure`() = runTest {
        assertTrue(listRows("[]").isEmpty())
    }

    // ---------------------------------------------------------------------- listBundles

    @Test
    fun `a bundle decodes with its flags and rows`() = runTest {
        val bundles = listBundles(
            """[{"name":"dsh-relay","version":"0.3.0","description":"Authenticated remote access",""" +
                """"enabled":true,"installed":true,"optional":false,"removable":true,""" +
                """"rows":[{"rowId":"relay","moduleName":"dsh-relay","entryId":"include:relay"}]}]""",
        )
        val bundle = bundles.single()
        assertEquals("dsh-relay", bundle.name)
        assertEquals("0.3.0", bundle.version)
        assertTrue(bundle.enabled)
        assertTrue(bundle.removable)
        assertEquals(listOf("relay"), bundle.rows.map { it.rowId })
    }

    /** `installed` is what divides the harness page's two sections, not the package scope. */
    @Test
    fun `installed and uninstalled bundles land in different groups`() = runTest {
        val bundles = listBundles(
            """[{"name":"dsh-relay","installed":true,"enabled":true},""" +
                """{"name":"@deepseek-ai/dsh-base","installed":false,"enabled":true}]""",
        )
        assertEquals(PluginBundleGroup.INSTALLED, bundles[0].group)
        assertEquals(PluginBundleGroup.OFFICIAL, bundles[1].group)
    }

    @Test
    fun `a bundle's description prefers the manifest meta`() = runTest {
        val bundles = listBundles(
            """[{"name":"x","description":"from top level","meta":{"description":"from manifest"}}]""",
        )
        assertEquals("from manifest", bundles.single().displayDescription)
    }

    @Test
    fun `a bundle with a blank description reads as none`() = runTest {
        assertNull(listBundles("""[{"name":"x","description":"   "}]""").single().displayDescription)
    }

    @Test
    fun `a bundle with no name is dropped`() = runTest {
        assertTrue(listBundles("""[{"name":"","installed":true}]""").isEmpty())
    }

    // --------------------------------------------------------------- mutation results

    /**
     * The important case: the Host applies it. `ok: true` alone would not have proved that.
     */
    @Test
    fun `an applied mutation reports success`() = runTest {
        val r = setBundleEnabled(
            """{"stage":"enable","target":"@deepseek-ai/dsh-experimental-voice-input-bundle",""" +
                """"enabled":true,"changed":true,"application":"applied","warnings":[]}""",
        )
        assertTrue(r.applied)
        assertTrue(r.changed)
        assertEquals("applied", r.application)
    }

    /**
     * A refusal arrives inside a successful call. [PluginMutationResult.applied] must be false even
     * though the carrier said `ok`, or the UI flips a switch that the Host never moved.
     */
    @Test
    fun `a refused mutation is not applied even though the call succeeded`() = runTest {
        val r = setBundleEnabled(
            """{"stage":"enable","target":"include:timer","enabled":false,"changed":false,""" +
                """"application":"failed","error":{"code":"management-required"}}""",
        )
        assertFalse(r.applied)
        assertFalse(r.changed)
        assertEquals(PluginReadOnly.MANAGEMENT_REQUIRED, r.error?.code)
    }

    @Test
    fun `an unaddressable refusal carries its code`() = runTest {
        val r = setBundleEnabled(
            """{"stage":"enable","target":"include","changed":false,"application":"failed",""" +
                """"error":{"code":"unaddressable"}}""",
        )
        assertFalse(r.applied)
        assertEquals(PluginReadOnly.UNADDRESSABLE, r.error?.code)
    }

    /** A no-op that the Host accepted: already in the requested state, so nothing moved. */
    @Test
    fun `an unchanged but applied mutation still reads as applied`() = runTest {
        val r = setBundleEnabled("""{"changed":false,"application":"applied"}""")
        assertTrue(r.applied)
        assertFalse(r.changed)
    }

    /** A result missing `application` must not be read as success. */
    @Test
    fun `a result without an application field is not applied`() = runTest {
        assertFalse(setBundleEnabled("""{"changed":true}""").applied)
    }

    /**
     * A row that is not composed into the running tree has no `entryId`.
     *
     * This is the trap that cost seven of a live profile's seventeen bundles: the enclosing bundle
     * fails to decode whole because one of its rows is missing a field the row is not required to
     * have. A disabled bundle is exactly the case a user is most likely to be looking at.
     */
    @Test
    fun `a bundle row without an entry id still decodes`() = runTest {
        val bundles = listBundles(
            """[{"name":"@deepseek-ai/dsh-acp-app","installed":false,"enabled":false,"rows":[""" +
                """{"rowId":"acp-app-startup","moduleName":"@deepseek-ai/dsh-acp-app/startup"},""" +
                """{"rowId":"acp","moduleName":"@deepseek-ai/dsh-acp-app","entryId":"include:acp"}]}]""",
        )
        val rows = bundles.single().rows
        assertEquals(listOf("acp-app-startup", "acp"), rows.map { it.rowId })
        assertNull(rows[0].entryId)
        assertEquals("include:acp", rows[1].entryId)
    }

    /**
     * An official bundle's manifest may localize its title and description. A plain-string field
     * rejects the object form, and the rejection takes the whole bundle with it.
     */
    @Test
    fun `a bundle with a localized title decodes and resolves`() = runTest {
        val bundles = listBundles(
            """[{"name":"@deepseek-ai/dsh-experimental-voice-input-bundle","installed":false,""" +
                """"enabled":false,"description":"plain fallback",""" +
                """"meta":{"title":{"en":"Voice input","zh":"语音输入"},""" +
                """"description":{"en":"Transcribe locally","zh":"在本机转写"}}}]""",
        )
        val bundle = bundles.single()
        assertEquals("Voice input", bundle.displayTitle())
        assertEquals("Transcribe locally", bundle.displayDescription)
    }

    // -------------------------------------------------------------------- live fixtures

    /**
     * The captured answers from a live harness 0.1.7-rc.2: 198 plugin rows and 17 bundles. Every
     * `readOnlyReason` variant the Host produces is present in the rows fixture, which is what makes
     * it worth keeping — the hand-written cases above encode the schema, this encodes the Host.
     */
    @Test
    fun `the live plugin row answer decodes`() = runTest {
        val rows = listRows(fixture("plugin-manager-rows.json"))
        assertTrue(rows.isNotEmpty())

        // The root include row is the unaddressable one.
        val root = rows.single { it.entryId == "include" }
        assertEquals(PluginReadOnly.UNADDRESSABLE, root.readOnlyReason)
        assertFalse(root.togglable)

        // Some rows are management-required, and those are the ones whose bundle is not manageable.
        assertTrue(rows.any { it.readOnlyReason == PluginReadOnly.MANAGEMENT_REQUIRED })

        // And most rows are freely togglable.
        assertTrue(rows.any { it.togglable })
    }

    @Test
    fun `the live bundle answer decodes with both groups populated`() = runTest {
        val bundles = listBundles(fixture("plugin-manager-bundles.json"))
        assertEquals(17, bundles.size)

        val installed = bundles.filter { it.group == PluginBundleGroup.INSTALLED }
        val official = bundles.filter { it.group == PluginBundleGroup.OFFICIAL }
        assertEquals(8, installed.size)
        assertEquals(9, official.size)

        // The two the profile is built on are never removable, and carry no description gap.
        val base = bundles.single { it.name == "@deepseek-ai/dsh-base" }
        assertFalse(base.removable)
        assertEquals(PluginReadOnly.MANAGEMENT_REQUIRED, base.readOnlyReason)

        // A bundle's rows name plugins that also appear in the flat list.
        assertTrue(bundles.flatMap { it.rows }.isNotEmpty())
    }

    // ------------------------------------------------------------------------- helpers

    private suspend fun listRows(value: String) =
        when (val r = clientReturning(value).pluginManagerListPlugins()) {
            is RpcResult.Ok -> r.value
            is RpcResult.Err -> error("expected success, got ${r.error.code}: ${r.error.message}")
        }

    private suspend fun listBundles(value: String) =
        when (val r = clientReturning(value).pluginManagerListBundles()) {
            is RpcResult.Ok -> r.value
            is RpcResult.Err -> error("expected success, got ${r.error.code}: ${r.error.message}")
        }

    private suspend fun setBundleEnabled(value: String) =
        when (val r = clientReturning(value).pluginManagerSetBundleEnabled("x", true)) {
            is RpcResult.Ok -> r.value
            is RpcResult.Err -> error("expected success, got ${r.error.code}: ${r.error.message}")
        }
}
