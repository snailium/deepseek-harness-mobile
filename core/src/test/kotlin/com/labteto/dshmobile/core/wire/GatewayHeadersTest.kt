package com.labteto.dshmobile.core.wire

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gateway's three headers, and the shape they are handed to the transport in.
 *
 * The point of these tests is not that a map has three entries — it is that the names and the
 * cookie encoding stay what the gateway's fence reads. A renamed header fails closed with a bare
 * 403, which is indistinguishable from a wrong credential, so a silent rename would surface as
 * "pairing stopped working" and nothing more.
 */
class GatewayHeadersTest {

    @Test
    fun `a session sends the cookie, the origin and the csrf token`() {
        val headers = GatewayHeaders.forSession(
            sessionToken = "sess-123",
            origin = "https://dsh2.example.com",
            csrf = "csrf-456",
        )

        assertEquals("dsh_ma_session=sess-123", headers[GatewayHeaders.COOKIE])
        assertEquals("https://dsh2.example.com", headers[GatewayHeaders.ORIGIN])
        assertEquals("csrf-456", headers[GatewayHeaders.CSRF])
        assertEquals(3, headers.size)
    }

    @Test
    fun `the cookie is spelled the way the gateway reads it`() {
        // `MobileAccess.sessionCookie` owns the name; this pins the pair they must agree on.
        val headers = GatewayHeaders.forSession("tok", "https://h", "c")
        assertEquals(MobileAccess.sessionCookie("tok"), headers[GatewayHeaders.COOKIE])
        assertTrue(headers[GatewayHeaders.COOKIE]!!.startsWith("dsh_ma_session="))
    }

    @Test
    fun `a claim carries no session headers`() {
        // A claim is what asks for a session; sending any of these would be a lie about having one.
        assertTrue(GatewayHeaders.forClaim().isEmpty())
    }
}
