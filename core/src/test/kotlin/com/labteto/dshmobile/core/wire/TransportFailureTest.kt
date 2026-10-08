package com.labteto.dshmobile.core.wire

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

class TransportFailureTest {

    @Test
    fun `http status classifies ahead of the cause`() {
        assertEquals(TransportFailure.TRUST_FENCE, TransportFailures.classify(RpcTransportException(403, "no")))
        assertEquals(TransportFailure.NOT_FOUND, TransportFailures.classify(RpcTransportException(404, "no")))
        assertEquals(TransportFailure.NOT_A_HARNESS, TransportFailures.classify(RpcTransportException(500, "no")))
    }

    /**
     * A gateway's `upstream_timeout` must not read as "this is not a harness".
     *
     * `dsh-mobile` answers `504 {"error":"upstream_timeout"}` when its `upstreamApiTimeoutMs` expires
     * while the harness is still working — the other half of the same `/compact` bug this app fixed
     * on its own side. Before 504 had a case it fell through to [TransportFailure.NOT_A_HARNESS],
     * whose whole meaning is "the address is wrong or nothing is listening", and which sends the
     * reader to re-check a hostname that was never the problem while their summary is still being
     * written.
     */
    @Test
    fun `a gateway that gave up waiting is not a wrong address`() {
        val gateway = RpcTransportException(504, "the gateway stopped waiting for the harness")
        assertEquals(TransportFailure.UPSTREAM_TIMEOUT, TransportFailures.classify(gateway))
        assertNotEquals(TransportFailure.NOT_A_HARNESS, TransportFailures.classify(gateway))
        // Still distinct from a dead upstream: 502 means reconnect, 504 means the work is running.
        assertNotEquals(TransportFailure.UPSTREAM_DOWN, TransportFailures.classify(gateway))
    }

    @Test
    fun `the 504 wording names the gateway deadline, and echoes the body when it can`() {
        val bare = carrierMessage(504)
        assertTrue(bare, bare.contains("504"))
        assertTrue(bare, bare.contains("gateway", ignoreCase = true))

        val named = carrierMessage(504, """{"error":"upstream_timeout"}""")
        assertTrue(named, named.contains("upstream_timeout"))
    }

    @Test
    fun `status zero falls through to the io cause`() {
        val refused = RpcTransportException(0, "transport failure", ConnectException("Connection refused"))
        assertEquals(TransportFailure.REFUSED, TransportFailures.classify(refused))

        val timedOut = RpcTransportException(0, "transport failure", SocketTimeoutException("timeout"))
        assertEquals(TransportFailure.TIMEOUT, TransportFailures.classify(timedOut))
    }

    @Test
    fun `socket exception subtypes map to their own kinds`() {
        assertEquals(TransportFailure.TIMEOUT, TransportFailures.classify(SocketTimeoutException()))
        assertEquals(TransportFailure.DNS, TransportFailures.classify(UnknownHostException("nope.local")))
        assertEquals(TransportFailure.UNREACHABLE, TransportFailures.classify(NoRouteToHostException()))
        assertEquals(TransportFailure.UNREACHABLE, TransportFailures.classify(PortUnreachableException()))
        assertEquals(TransportFailure.REFUSED, TransportFailures.classify(ConnectException()))
        assertEquals(TransportFailure.OTHER, TransportFailures.classify(null as Throwable?))
    }

    /**
     * SSLException is an IOException, so ordering matters: reaching the message sniffer would
     * read "Unrecognized SSL message" as OTHER and blame nothing in particular, when the actual
     * diagnosis — a certificate this device does not trust, or https:// at a plain-HTTP server —
     * has its own advice.
     */
    @Test
    fun `tls failures classify as their own kind`() {
        assertEquals(TransportFailure.TLS, TransportFailures.classify(SSLHandshakeException("no trust anchor")))
        assertEquals(
            TransportFailure.TLS,
            TransportFailures.classify(SSLException("Unrecognized SSL message, plaintext connection?")),
        )
        val wrapped = RpcTransportException(0, "transport failure", SSLHandshakeException("no trust anchor"))
        assertEquals(TransportFailure.TLS, TransportFailures.classify(wrapped))
    }

    /**
     * Android can report a kernel connect deadline as a plain IOException naming ETIMEDOUT rather
     * than as SocketTimeoutException. The manual-connect path does not rely on this, but the sweep
     * does, and reading it as OTHER would blame the wrong thing.
     */
    @Test
    fun `plain io exceptions fall back to their message`() {
        assertEquals(TransportFailure.TIMEOUT, TransportFailures.classify(IOException("failed: ETIMEDOUT")))
        assertEquals(TransportFailure.REFUSED, TransportFailures.classify(IOException("ECONNREFUSED (Connection refused)")))
        assertEquals(TransportFailure.UNREACHABLE, TransportFailures.classify(IOException("ENETUNREACH")))
        assertEquals(TransportFailure.OTHER, TransportFailures.classify(IOException("something else entirely")))
    }

    @Test
    fun `details round-trip through an RpcError`() {
        val error = RpcError(
            code = "forbidden",
            message = "harness trust fence rejected the request (HTTP 403)",
            details = TransportFailures.details(TransportFailure.TRUST_FENCE, 403),
        )
        assertEquals(TransportFailure.TRUST_FENCE, TransportFailures.of(error))
        assertEquals(403, TransportFailures.statusOf(error))
    }

    @Test
    fun `an error with no marker reads back as null rather than a wrong guess`() {
        val bare = RpcError("agent-busy", "busy", JsonObject(emptyMap()))
        assertNull(TransportFailures.of(bare))
        assertNull(TransportFailures.statusOf(bare))
    }

    @Test
    fun `status is omitted when there was none`() {
        val error = RpcError("internal", "x", TransportFailures.details(TransportFailure.TIMEOUT))
        assertEquals(TransportFailure.TIMEOUT, TransportFailures.of(error))
        assertNull(TransportFailures.statusOf(error))
    }
}
