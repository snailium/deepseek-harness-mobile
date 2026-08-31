package com.labteto.dshmobile.ui.screens.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The address parser is the whole of the connect form's defaulting, so every rule it encodes is
 * pinned here: scheme prefix wins, an explicit port wins, a bare IP means the LAN convention
 * (http:3080), and a bare domain means a TLS edge (https:443).
 */
class AddressParseTest {

    @Test
    fun `pasted https url keeps scheme and defaults to 443`() {
        assertEquals(
            ParsedAddress("ds.yeasin.tech", 443, "https", portExplicit = false),
            parseAddress("https://ds.yeasin.tech"),
        )
    }

    @Test
    fun `bare domain defaults to https 443`() {
        assertEquals(
            ParsedAddress("ds.yeasin.tech", 443, "https", portExplicit = false),
            parseAddress("ds.yeasin.tech"),
        )
    }

    @Test
    fun `domain with explicit port keeps it and stays https`() {
        assertEquals(
            ParsedAddress("ds.yeasin.tech", 8443, "https", portExplicit = true),
            parseAddress("ds.yeasin.tech:8443"),
        )
    }

    @Test
    fun `pasted http url defaults to 80`() {
        assertEquals(
            ParsedAddress("ds.yeasin.tech", 80, "http", portExplicit = false),
            parseAddress("http://ds.yeasin.tech"),
        )
    }

    @Test
    fun `pasted http url with port keeps both`() {
        assertEquals(
            ParsedAddress("ds.yeasin.tech", 3080, "http", portExplicit = true),
            parseAddress("http://ds.yeasin.tech:3080"),
        )
    }

    @Test
    fun `bare ip means the harness convention http 3080`() {
        assertEquals(
            ParsedAddress("192.168.1.20", 3080, "http", portExplicit = false),
            parseAddress("192.168.1.20"),
        )
    }

    @Test
    fun `ip with explicit port keeps it`() {
        assertEquals(
            ParsedAddress("192.168.1.20", 9999, "http", portExplicit = true),
            parseAddress("192.168.1.20:9999"),
        )
    }

    @Test
    fun `localhost defaults to the harness convention`() {
        assertEquals(
            ParsedAddress("localhost", 3080, "http", portExplicit = false),
            parseAddress("localhost"),
        )
    }

    @Test
    fun `explicit scheme wins over an ip`() {
        assertEquals(
            ParsedAddress("192.168.1.20", 443, "https", portExplicit = false),
            parseAddress("https://192.168.1.20"),
        )
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals(
            ParsedAddress("ds.yeasin.tech", 443, "https", portExplicit = false),
            parseAddress("  ds.yeasin.tech  "),
        )
    }

    @Test
    fun `unknown scheme prefix is rejected`() {
        assertNull(parseAddress("ftp://ds.yeasin.tech"))
    }

    @Test
    fun `empty and blank input are rejected`() {
        assertNull(parseAddress(""))
        assertNull(parseAddress("   "))
    }

    @Test
    fun `scheme with no host is rejected`() {
        assertNull(parseAddress("https://"))
    }

    @Test
    fun `ipv6 literals are rejected rather than mis-parsed`() {
        assertNull(parseAddress("::1"))
        assertNull(parseAddress("[::1]:8080"))
    }

    @Test
    fun `doubled colon is rejected`() {
        assertNull(parseAddress("host:8080:9090"))
    }

    @Test
    fun `ports out of range or non-numeric are rejected`() {
        assertNull(parseAddress("host:70000"))
        assertNull(parseAddress("host:0"))
        assertNull(parseAddress("host:abc"))
        assertNull(parseAddress("host:-1"))
    }

    @Test
    fun `whitespace inside the address is rejected`() {
        assertNull(parseAddress("my.host:80 extra"))
    }
}
