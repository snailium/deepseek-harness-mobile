package com.labteto.dshmobile.connection

import com.labteto.dshmobile.core.wire.DshApiClient
import com.labteto.dshmobile.core.wire.OkHttpRpcTransport
import com.labteto.dshmobile.core.wire.RpcResult
import com.labteto.dshmobile.core.wire.ServerRequest
import com.labteto.dshmobile.core.wire.WsDownlink
import com.labteto.dshmobile.core.wire.WsDownlinkSink
import com.labteto.dshmobile.mockharness.MockHarness
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Dns
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress

/**
 * The remote-path guarantee: whatever auth headers a host is configured with reach every exchange
 * the app makes — the unary POST, the session-export download, and both WebSocket upgrades —
 * through the same transport the connection manager builds from a HostConfig.
 */
class RemoteHeadersTest {

    private val mock = MockHarness(trustedHosts = listOf("ds.yeasin.tech"))
    private var port: Int = 0

    /**
     * A client that dials the mock on loopback while carrying the real authority in the Host
     * header — the same split a tunneled endpoint has — so the Host-header assertion below is the
     * fence's actual input, not an artifact of the test host.
     */
    private val loopbackClient = OkHttpClient.Builder()
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                if (hostname == "ds.yeasin.tech") {
                    listOf(InetAddress.getByName("127.0.0.1"))
                } else {
                    Dns.SYSTEM.lookup(hostname)
                }
        })
        .build()

    @Before
    fun start() {
        port = runBlocking { mock.start() }
    }

    @After
    fun stop() = runBlocking { mock.stop() }

    private val cfHeaders = mapOf(
        "Authorization" to "Bearer test-token",
        "CF-Access-Client-Id" to "client-id.access",
        "CF-Access-Client-Secret" to "top-secret",
    )

    @Test
    fun `configured headers ride on POST download and WebSocket upgrade`() = runBlocking {
        val base = "http://ds.yeasin.tech:$port"
        val client = loopbackClient
        val api = DshApiClient(
            transport = OkHttpRpcTransport(base, client, extraHeaders = cfHeaders),
            wsFactory = { path, sink -> WsDownlink("$base$path", client, sink, cfHeaders) },
        )

        // Unary POST.
        assertTrue(api.hostDescribe() is RpcResult.Ok)

        // Download (the one non-envelope channel).
        val exported = api.sessionExport("demo") { _, _, _ -> }
        assertTrue(exported is RpcResult.Ok)

        // WebSocket upgrade.
        val opened = CompletableDeferred<Unit>()
        val ws = api.openEvents(
            mux = true,
            sink = object : WsDownlinkSink {
                override fun onFrame(frame: ServerRequest) {}
                override fun onOpen() {
                    opened.complete(Unit)
                }

                override fun onClosed(cause: Throwable?) {}
            },
        )
        ws.start()
        withTimeout(5_000) { opened.await() }
        // The server records the upgrade's headers just after the handshake completes; wait for
        // the record rather than racing it.
        withTimeout(5_000) {
            while (mock.observedHeaders["events.mux"] == null) {
                kotlinx.coroutines.delay(50)
            }
        }
        ws.close()

        listOf("host.describe", "session.export", "events.mux").forEach { key ->
            val observed = mock.observedHeaders[key]
                ?: throw AssertionError("mock recorded no headers for $key")
            assertEquals("Bearer test-token", observed["Authorization"])
            assertEquals("client-id.access", observed["CF-Access-Client-Id"])
            assertEquals("top-secret", observed["CF-Access-Client-Secret"])
        }
        // The Host header is what the trust fence reads; it must be the configured authority.
        assertEquals(
            "ds.yeasin.tech:$port",
            mock.observedHeaders["host.describe"]?.get("Host"),
        )
    }

    @Test
    fun `no headers are sent when none are configured`() = runBlocking {
        val base = "http://127.0.0.1:$port"
        val client = OkHttpClient()
        val api = DshApiClient(
            transport = OkHttpRpcTransport(base, client),
            wsFactory = { path, sink -> WsDownlink("$base$path", client, sink) },
        )
        assertTrue(api.hostDescribe() is RpcResult.Ok)
        val observed = mock.observedHeaders["host.describe"]
            ?: throw AssertionError("mock recorded no headers for host.describe")
        assertNull(observed["Authorization"])
        assertNull(observed["CF-Access-Client-Id"])
        assertNull(observed["CF-Access-Client-Secret"])
    }
}
