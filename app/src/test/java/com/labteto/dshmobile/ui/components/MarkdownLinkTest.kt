package com.labteto.dshmobile.ui.components

import com.labteto.dshmobile.core.markdown.safeHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Only http(s) URLs leave the app; a harness reply cannot smuggle another scheme out. */
class MarkdownLinkTest {

    @Test
    fun httpAndHttpsUrlsPassThrough() {
        assertEquals("https://example.com/a?b=1", safeHttpUrl("https://example.com/a?b=1"))
        assertEquals("http://ds.local:3080", safeHttpUrl("http://ds.local:3080"))
    }

    @Test
    fun surroundingWhitespaceIsTrimmed() {
        assertEquals("https://example.com", safeHttpUrl("  https://example.com  "))
    }

    @Test
    fun otherSchemesAreRefused() {
        assertNull(safeHttpUrl("file:///etc/passwd"))
        assertNull(safeHttpUrl("javascript:alert(1)"))
        assertNull(safeHttpUrl("intent://example.com"))
        assertNull(safeHttpUrl("data:text/html,hi"))
    }

    @Test
    fun emptyAndBlankUrlsAreRefused() {
        assertNull(safeHttpUrl(""))
        assertNull(safeHttpUrl("   "))
    }

    @Test
    fun schemeCaseIsHonouredLeniently() {
        assertEquals("HTTPS://example.com", safeHttpUrl("HTTPS://example.com"))
    }
}
