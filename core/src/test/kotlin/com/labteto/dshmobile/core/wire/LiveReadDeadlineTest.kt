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
 * The live unary channel is built with `readTimeoutMs = 0` — no read deadline — because the response
 * wait is gated on work the *host* does, and a request the host is working on puts nothing on the
 * wire while it works. `/compact` is the case that found it: against `saya-ch/dsh-mobile` it was
 * killed at exactly 30s with `transport failure: timeout` while the summary it asked for was still
 * being written. That is OkHttp's own meaning of `0`, so the transport needs no special case; what
 * needs pinning is that nothing downstream quietly restores a default.
 *
 * A discovery probe still passes an explicit short budget, and the second case here is what keeps
 * that honest: a deadline that *is* given must still fire. Otherwise "the live channel has no
 * deadline" would be implemented as "this transport ignores deadlines", and a sweep would block for
 * as long as the socket felt like it.
 *
 * Neither case waits 30s. The slow server sleeps 1500ms against a 400ms budget, so the distinction
 * costs about two seconds.
 */
class LiveReadDeadlineTest {

    private val readTimeoutMs = 400L
    private val serverWorkMs = 1_500L

    @Test
    fun `no deadline means a slow host is waited for`() = withSlowServer { port ->
        val transport = OkHttpRpcTransport(
            baseUrl = "http://127.0.0.1:$port",
            connectTimeoutMs = 2_000,
            readTimeoutMs = 0,
            writeTimeoutMs = 2_000,
        )

        // The host takes ~4x the deadline above to answer. This must simply wait.
        val answered = transport.post("/api/commands/execute", "{}")
        assertTrue("the live channel should have waited for the host", answered.status == 200)

        // And it must hold for every route on that channel, not just the one that found the bug:
        // a long `session/page` on a big session is not a stalled request either.
        val paged = transport.post("/api/session/page", "{}")
        assertTrue("every route on the live channel waits", paged.status == 200)
    }

    @Test
    fun `a deadline that is given still fires`() = withSlowServer { port ->
        val transport = OkHttpRpcTransport(
            baseUrl = "http://127.0.0.1:$port",
            connectTimeoutMs = 2_000,
            readTimeoutMs = readTimeoutMs,
            writeTimeoutMs = 2_000,
        )
        try {
            transport.post("/api/session/list", "{}")
            fail("a probe's budget must still cut the request off")
        } catch (e: RpcTransportException) {
            // Assert that the deadline fired, not OkHttp's wording for it. The same fact surfaces
            // as `SocketTimeoutException("Read timed out")` when the socket's SO_TIMEOUT reaches
            // first and as `InterruptedIOException("timeout")` when Okio's own timeout does — which
            // of the two wins is a race, and pinning either string made this test fail for a reason
            // it is not about. The cause chain is the stable part.
            val timedOut = generateSequence(e as Throwable?) { it.cause }.any {
                it is java.io.InterruptedIOException
            }
            assertTrue("expected a timeout, got: ${e.message}", timedOut)
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
                // The client hung up, which is what the deadline case is asserting. Nothing to
                // report: the server's job here is only to be slow.
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
