package com.labteto.dshmobile.core.wire

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * The read deadline, proved against a host that is slow rather than dead.
 *
 * `commands/execute` is answered when the harness finishes the command — `/compact` summarizes the
 * whole session — and nothing crosses the wire while it works. OkHttp's read timeout is an *idle*
 * timeout, so on the old 30s default a working host was indistinguishable from a dead one, and the
 * call was aborted mid-work: `transport failure: timeout` at exactly 30s against
 * `saya-ch/dsh-mobile`.
 *
 * The test does not wait 30s to say so. `readTimeoutMs` is 400 and the server sleeps 1500, which
 * makes the same distinction in about two seconds: a host-bounded path must survive the quiet, and
 * an ordinary path must still be cut off — relaxing the deadline everywhere would trade this bug
 * for a worse one, a stalled request that never reports.
 */
class HostBoundedReadDeadlineTest {

    private val readTimeoutMs = 400L
    private val serverWorkMs = 1_500L

    @Test
    fun `a slow host is not mistaken for a dead one`() = withSlowServer { port ->
        val transport = OkHttpRpcTransport(
            baseUrl = "http://127.0.0.1:$port",
            connectTimeoutMs = 2_000,
            readTimeoutMs = readTimeoutMs,
            writeTimeoutMs = 2_000,
        )

        // The host takes ~4x the read deadline to answer. This must simply wait.
        val answered = transport.post("/api/commands/execute", "{}")
        assertTrue("the host-bounded call should have been answered", answered.status == 200)

        // The same delay on an ordinary route must still be refused, or the deadline is gone
        // rather than scoped.
        try {
            transport.post("/api/session/list", "{}")
            fail("an ordinary call must keep its read deadline")
        } catch (e: RpcTransportException) {
            assertTrue(
                "expected a timeout, got: ${e.message}",
                e.message.orEmpty().contains("timeout", ignoreCase = true),
            )
        }
    }

    /** Runs [body] against a local server that answers every route after [serverWorkMs]. */
    private fun withSlowServer(body: suspend (Int) -> Unit): Unit = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newFixedThreadPool(4)
        server.createContext("/") { exchange ->
            try {
                exchange.requestBody.use { it.readBytes() }
                Thread.sleep(serverWorkMs)
                val payload = """{"ok":true}""".toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, payload.size.toLong())
                exchange.responseBody.use { it.write(payload) }
            } catch (_: Exception) {
                // The client hung up, which is what the ordinary-route case is asserting. Nothing
                // to report: the server's job here is only to be slow.
            }
        }
        server.start()
        try {
            body(server.address.port)
        } finally {
            server.stop(0)
        }
    }
}
