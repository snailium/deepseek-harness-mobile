package com.labteto.dshmobile.ui.screens.main

import com.labteto.dshmobile.core.wire.DshApiClient
import com.labteto.dshmobile.core.wire.OkHttpRpcTransport
import com.labteto.dshmobile.core.wire.RpcResult
import com.labteto.dshmobile.core.wire.dto.WorkspaceFileRange
import com.labteto.dshmobile.mockharness.MockHarness
import com.labteto.dshmobile.mockharness.PanelScenario
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A file preview must get the whole document, not the first page a client happened to ask for.
 *
 * The preview used to send a page size of its own — 500 lines — which is smaller than the page
 * the host is willing to serve, so a document of 1,200 lines lost everything past the five
 * hundredth, and the reader scrolled to a stop there. The request now names no size at all and
 * takes the host's own page, which is the whole document for any file the host will serve in
 * one page.
 *
 * These run against the mock harness, which models the host's two page rules: a page holds at
 * most `maxLines` lines and at most `maxBytes` of them, and a request that names a page bigger
 * than `maxLines` is refused outright rather than trimmed.
 */
class WorkspacePreviewPagingTest {

    private fun withHarness(block: suspend (DshApiClient) -> Unit) = runBlocking {
        val harness = MockHarness()
        val scenario = PanelScenario(harness)
        val port = harness.start()
        try {
            val api = DshApiClient(
                OkHttpRpcTransport("http://127.0.0.1:$port", OkHttpClient(), 5000, 5000),
            )
            block(api)
        } finally {
            scenario.archived.clear()
        }
    }

    @Test
    fun `a document longer than the old client cap arrives in one page`() = withHarness { api ->
        val page = (api.workspaceFileRead("cold-child", "LONG.md", WorkspaceFileRange(1)) as RpcResult.Ok).value
        assertEquals(
            "the client must not cut the document at a page size of its own choosing",
            1200, page.lines,
        )
        assertTrue("and the whole of it must be there: ${page.text.length} characters", page.eof)
        assertTrue("the first line must be the first line", page.text.startsWith("line 1"))
        assertTrue("the last line must be the last line", page.text.endsWith("line 1200"))
    }

    @Test
    fun `the preview's own read path gets the host's page, not one of its own choosing`() = withHarness { api ->
        val (page, usedLimit) = readWorkspacePage(api, "cold-child", "LONG.md", 1, null)
        assertEquals(
            "the preview must not cut the document at a page size of its own choosing",
            1200, page.lines,
        )
        assertTrue("and the whole of it must be there", page.eof)
        assertEquals(
            "and the size it used must be the host's own, not one the client named",
            null, usedLimit,
        )
    }

    @Test
    fun `a host that refuses the page is asked again with fewer lines`() = withHarness { api ->
        // The file's first page is over the host's byte cap, so the host refuses it and the
        // client asks again with fewer lines until one fits.
        val (page, usedLimit) = readWorkspacePage(api, "cold-child", "WIDE.md", 1, null)
        assertTrue("a page must come back, not an error", page.lines > 0)
        assertEquals(
            "the smaller page must be the one the policy names",
            com.labteto.dshmobile.core.wire.PreviewPaging.FALLBACK_LINES, usedLimit,
        )
        assertTrue("and it must be a page the host accepts", page.lines <= usedLimit!!)
    }

    @Test
    fun `the host's own refusal is what the client answers`() = withHarness { api ->
        // The host refuses a page that cannot fit, and refuses a page bigger than it serves.
        val refused = api.workspaceFileRead("cold-child", "WIDE.md", WorkspaceFileRange(1, 5000))
        assertEquals(
            "workspace-file/too-large",
            (refused as RpcResult.Err).error.code,
        )
        val tooBig = api.workspaceFileRead("cold-child", "LONG.md", WorkspaceFileRange(1, 5001))
        assertEquals(
            "gateway/bad-request",
            (tooBig as RpcResult.Err).error.code,
        )
    }
}
