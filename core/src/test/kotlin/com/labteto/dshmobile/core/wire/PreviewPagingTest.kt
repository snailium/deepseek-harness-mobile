package com.labteto.dshmobile.core.wire

import com.labteto.dshmobile.core.wire.dto.WorkspaceFileRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A file preview asks the host for its pages, and the host owns the page size.
 *
 * The client used to send a page size of its own — 500 lines — which is smaller than the page
 * the host is willing to serve, and it cut a document short at a line that had nothing to do
 * with the file. A README of 600 lines lost its last hundred, and the preview ended there: the
 * reader scrolled to a stop and the rest of the file was a button away.
 *
 * So the request says nothing about a size, and the host's own page size is what arrives.
 */
class PreviewPagingTest {

    @Test
    fun `a range with no size says nothing about one`() {
        val json = WireJson.encodeToString(WorkspaceFileRange.serializer(), WorkspaceFileRange(offset = 5))
        assertTrue("the offset is the one thing the client does decide: $json", "\"offset\":5" in json)
        assertFalse(
            "the client must not name a page size of its own — the host caps the page: $json",
            "\"limit\"" in json,
        )
    }

    @Test
    fun `an explicit size is sent when the client has one`() {
        val json = WireJson.encodeToString(WorkspaceFileRange.serializer(), WorkspaceFileRange(offset = 5, limit = 200))
        assertTrue("\"offset\":5" in json)
        assertTrue("\"limit\":200" in json)
    }

    @Test
    fun `the page size ladder halves down to a floor and then stops`() {
        // The host refused the first page, so the client names a size: a thousand lines.
        var size = PreviewPaging.nextPageSize(null)
        assertEquals(PreviewPaging.FALLBACK_LINES, size)
        // Each refusal halves it.
        size = PreviewPaging.nextPageSize(size)
        assertEquals(500, size)
        size = PreviewPaging.nextPageSize(size)
        assertEquals(250, size)
        size = PreviewPaging.nextPageSize(size)
        assertEquals(125, size)
        size = PreviewPaging.nextPageSize(size)
        assertEquals(62, size)
        size = PreviewPaging.nextPageSize(size)
        assertEquals(31, size)
        size = PreviewPaging.nextPageSize(size)
        assertEquals(PreviewPaging.MIN_LINES, size)
        // And at the floor there is nothing left to try: the host's own error is the answer.
        assertEquals(null, PreviewPaging.nextPageSize(size))
        assertEquals(null, PreviewPaging.nextPageSize(PreviewPaging.MIN_LINES - 1))
    }

    @Test
    fun `only the host's own too-large answer asks for a smaller page`() {
        assertTrue(PreviewPaging.isTooLarge(PreviewPaging.TOO_LARGE))
        assertFalse(PreviewPaging.isTooLarge("workspace-file/not-found"))
        assertFalse(PreviewPaging.isTooLarge("workspace-file/not-regular-file"))
        assertFalse(PreviewPaging.isTooLarge("gateway/bad-request"))
    }
}
