package com.labteto.dshmobile.core.wire

import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.test.runTest
import com.labteto.dshmobile.core.wire.dto.EncodedImageAttachment
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The typert Remote gateway shares the ordinary client-request envelope: `{"args": …}` is the
 * *payload*, not the body, and the envelope's `method` must equal the path. Posting a bare body —
 * which is what this client used to do — is rejected before it reaches a handler, so these tests
 * pin the shape rather than merely the happy path.
 */
class DshApiClientRemoteTest {

    private class RecordingTransport(
        private val responder: (path: String, body: String) -> RpcHttpResponse,
    ) : RpcTransport {
        var lastPath: String? = null
        var lastBody: String? = null
        var lastDownloadPath: String? = null
        var downloadBytes: ByteArray = ByteArray(0)

        override suspend fun post(path: String, body: String): RpcHttpResponse {
            lastPath = path
            lastBody = body
            return responder(path, body)
        }

        override suspend fun <T> download(
            path: String,
            consume: (String?, String?, InputStream) -> T,
        ): T {
            lastDownloadPath = path
            return consume("application/zip", "attachment; filename=\"x.zip\"", ByteArrayInputStream(downloadBytes))
        }
    }

    private fun client(transport: RpcTransport) = DshApiClient(transport = transport)

    private fun ok(rpcId: String, value: String) = RpcHttpResponse(
        status = 200,
        body = """{"type":"server-response","rpcId":"$rpcId","result":{"ok":true,"value":$value}}""",
    )

    @Test
    fun `commands list posts a client-request envelope with agentId in args`() = runTest {
        val transport = RecordingTransport { _, body ->
            val rpcId = Json.parseToJsonElement(body).jsonObject["rpcId"]!!.jsonPrimitive.content
            ok(
                rpcId,
                """[{"name":"permission","description":"Switch","input":{"hint":"<preset>"}},""" +
                    """{"name":"goal","description":"Set","input":{"hint":"<objective>","images":true}}]""",
            )
        }
        val result = client(transport).commandsList("session-1")

        assertEquals("/api/commands/list", transport.lastPath)
        val envelope = Json.parseToJsonElement(transport.lastBody!!).jsonObject
        assertEquals("client-request", envelope["type"]!!.jsonPrimitive.content)
        assertEquals("commands/list", envelope["method"]!!.jsonPrimitive.content)
        assertTrue(envelope["rpcId"]!!.jsonPrimitive.content.isNotBlank())

        // The gateway matches arg names against the descriptor exactly: the session-addressed
        // parameter is `agentId`, and an unexpected key fails the whole call.
        val args = envelope["payload"]!!.jsonObject["args"]!!.jsonObject
        assertEquals(setOf("agentId"), args.keys)
        assertEquals("session-1", args["agentId"]!!.jsonPrimitive.content)

        val commands = (result as RpcResult.Ok).value
        assertEquals(2, commands.size)
        assertEquals("permission", commands.first().name)
        assertEquals("<preset>", commands.first().input?.hint)
        // `input.images` arrived in 0.1.0-rc.8 and is only ever sent as true, so a descriptor
        // without the key is a command that takes none — which is every command before rc.8.
        assertFalse(commands.first().acceptsImages)
        assertTrue(commands.last().acceptsImages)
    }

    @Test
    fun `a command is always sent the three-argument shape`() = runTest {
        // Through 0.1.1 this client chose between a two- and a three-argument shape from the
        // presence of `host.describe.home`, because rc.7 declared no `images` parameter and the
        // gateway refuses a missing key as readily as an unexpected one. `host.describe` is gone
        // and every 0.1.2 host declares the parameter, so there is nothing left to choose — and
        // no version-shaped branch left in this client at all.
        val transport = RecordingTransport { _, body ->
            val rpcId = Json.parseToJsonElement(body).jsonObject["rpcId"]!!.jsonPrimitive.content
            ok(rpcId, "{}")
        }
        client(transport).commandsExecute("session-2", "/compact")

        assertEquals("/api/commands/execute", transport.lastPath)
        val args = Json.parseToJsonElement(transport.lastBody!!)
            .jsonObject["payload"]!!.jsonObject["args"]!!.jsonObject
        assertEquals(setOf("agentId", "line", "images"), args.keys)
        assertEquals(0, args["images"]!!.jsonArray.size)
    }

    @Test
    fun `a command carries the composer's images`() = runTest {
        val transport = RecordingTransport { _, body ->
            val rpcId = Json.parseToJsonElement(body).jsonObject["rpcId"]!!.jsonPrimitive.content
            ok(rpcId, "{}")
        }
        client(transport).commandsExecute(
            "session-2",
            "/goal ship it",
            listOf(EncodedImageAttachment("image/png", "AAAA")),
        )

        val args = Json.parseToJsonElement(transport.lastBody!!)
            .jsonObject["payload"]!!.jsonObject["args"]!!.jsonObject
        val image = args["images"]!!.jsonArray.single().jsonObject
        assertEquals("image/png", image["mediaType"]!!.jsonPrimitive.content)
        assertEquals("AAAA", image["data"]!!.jsonPrimitive.content)
        // explicitNulls = false: an absent display name is an absent key, not a null one.
        assertEquals(setOf("mediaType", "data"), image.keys)
    }

    @Test
    fun `a 404 becomes capability-unavailable rather than a connection failure`() = runTest {
        val transport = RecordingTransport { _, _ ->
            throw RpcTransportException(404, "carrier returned HTTP 404")
        }
        val result = client(transport).commandsList("session-3")

        // A harness that composes no command registry answers 404. That is a missing optional
        // capability, not a broken link, and callers rely on the distinction to degrade the menu
        // instead of raising a failure banner over a healthy session.
        assertEquals("capability-unavailable", (result as RpcResult.Err).error.code)
    }

    @Test
    fun `a 403 becomes forbidden`() = runTest {
        val transport = RecordingTransport { _, _ ->
            throw RpcTransportException(403, "harness trust fence rejected the request (HTTP 403)")
        }
        val result = client(transport).commandsList("session-4")
        assertEquals("forbidden", (result as RpcResult.Err).error.code)
    }

    @Test
    fun `malformed command rows drop out instead of emptying the catalog`() = runTest {
        val transport = RecordingTransport { _, body ->
            val rpcId = Json.parseToJsonElement(body).jsonObject["rpcId"]!!.jsonPrimitive.content
            ok(rpcId, """[{"name":"plan"},{"unexpected":true},{"name":"compact","future":42}]""")
        }
        val result = client(transport).commandsList("session-5")

        val commands = (result as RpcResult.Ok).value
        assertEquals(listOf("plan", "compact"), commands.map { it.name })
    }

    @Test
    fun `session export streams from a GET with the session id in the query`() = runTest {
        val transport = RecordingTransport { _, _ -> error("export must not POST") }
        transport.downloadBytes = "PK-zip-bytes".toByteArray()

        val result = client(transport).sessionExport("session-6", includeDescendants = true) { _, _, body ->
            body.readBytes().decodeToString()
        }

        assertEquals(
            "/api/session.export?sessionId=session-6&includeDescendants=true",
            transport.lastDownloadPath,
        )
        assertEquals("PK-zip-bytes", (result as RpcResult.Ok).value)
        assertNull(transport.lastPath)
    }
}
