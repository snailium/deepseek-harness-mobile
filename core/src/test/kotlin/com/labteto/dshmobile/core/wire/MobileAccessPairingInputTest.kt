package com.labteto.dshmobile.core.wire

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What this client will accept as a `dsh-mobile` pairing invitation, and what it will not.
 *
 * The desktop panel offers three spellings of the same thing — a bare app key, a pairing link, and
 * a link whose fragment carries the key instead of the instance/token pair — and a person copying
 * from it does not choose. Accepting all three costs one parser; rejecting a working invitation
 * costs a support round trip. The refusals matter just as much: a QR that is not an invitation must
 * be told apart from one this build cannot read, and neither may be mistaken for the other.
 */
class MobileAccessPairingInputTest {

    private val id = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    private fun parsed(text: String): MobileAccessInvite? =
        (MobileAccess.parsePairingInput(text) as? MobileAccessInviteResult.Valid)?.invite

    @Test
    fun `a bare app key names a gateway but no address`() {
        val invite = parsed("dsh1.$id.abc123_TOKEN") ?: error("key was not accepted")
        assertEquals(id, invite.instanceId)
        assertEquals("abc123_TOKEN", invite.token)
        assertNull(invite.origin)
        assertFalse(invite.pinsPairingCa)
    }

    @Test
    fun `the pinned prefix is remembered`() {
        // `dsh2.` is what a gateway with its own self-signed ingress hands out: the CA it serves has
        // to be pinned, not merely observed.
        val invite = parsed("dsh2.$id.token") ?: error("key was not accepted")
        assertTrue(invite.pinsPairingCa)
    }

    @Test
    fun `a pairing link carries the origin`() {
        val invite = parsed("https://192.168.1.20:3443/mobile-access/pair#instance=$id&token=zzz")
            ?: error("link was not accepted")
        assertEquals("https://192.168.1.20:3443", invite.origin)
        assertEquals(id, invite.instanceId)
        assertEquals("zzz", invite.token)
        assertFalse(invite.pinsPairingCa)
    }

    @Test
    fun `a key-shaped link is read as a key`() {
        val invite = parsed("https://phone.example/mobile-access/pair#key=dsh2.$id.tok")
            ?: error("link was not accepted")
        assertEquals("https://phone.example", invite.origin)
        assertTrue(invite.pinsPairingCa)
        assertEquals("tok", invite.token)
    }

    @Test
    fun `surrounding whitespace is not part of the credential`() {
        // The link is copied out of a panel, and a copied line arrives with whatever the terminal
        // wrapped around it. Trimming is the difference between a working pairing and a 401.
        assertTrue(parsed("  dsh1.$id.tok\n") != null)
    }

    @Test
    fun `a QR that is not an invitation is refused`() {
        for (text in listOf("", "hello", "https://example.com", "dsh3.$id.tok", "dsh1.short.tok")) {
            assertEquals(
                "should not accept: $text",
                MobileAccessInviteResult.NotAPairingCode,
                MobileAccess.parsePairingInput(text),
            )
        }
    }

    @Test
    fun `an upper-case id is not the id a gateway prints`() {
        // The gateway spells this value lower-case and the comparison at pairing is exact, so a
        // case-folded accept here would only move the failure into the TLS handshake.
        assertFalse(MobileAccess.isInstanceId(id.uppercase()))
        assertTrue(MobileAccess.isInstanceId(id))
    }

    @Test
    fun `an origin is derived from an address and never from a path`() {
        assertEquals("https://host:3443", MobileAccess.originOf("https://host:3443/base/path"))
        assertEquals("http://127.0.0.1:13443", MobileAccess.originOf("http://127.0.0.1:13443"))
        assertEquals("https://host:3443", MobileAccess.originOf("https://host:3443/"))
        assertNull(MobileAccess.originOf("not a url"))
    }

    @Test
    fun `the session cookie is spelled the way the gateway reads it`() {
        assertEquals("dsh_ma_session=abc", MobileAccess.sessionCookie("abc"))
    }

    @Test
    fun `a certificate fingerprint is lower-case hex of the DER form`() {
        // The gateway names a LAN installation by `fingerprint256` of the certificate with the
        // colons removed; an upper-case or SPKI-based value would never match it.
        val fingerprint = MobileAccessPairing.certificateFingerprint("abc".toByteArray())
        assertEquals(64, fingerprint.length)
        assertEquals(fingerprint.lowercase(), fingerprint)
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            fingerprint,
        )
    }
}
