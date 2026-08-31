package com.labteto.dshmobile.ui.screens.main

import com.labteto.dshmobile.data.SessionRow
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Flat-list ordering: the session list is a single sequence now (no workspace grouping), so the
 * only ordering question is what "top level" means and which order the top-level rows appear in.
 *
 * The distinction that matters: a session nested under a subagent tree is not a top-level row no
 * matter how recently it was updated — its parent owns it. Everything else is top level and sorts
 * by recency (default) or registration order ("manual").
 */
class SessionListOrderingTest {

    private fun session(
        id: String,
        updatedAt: Long = 0L,
        parent: String? = null,
        origin: String? = null,
    ) = SessionRow(
        sessionId = id,
        title = id,
        running = false,
        blank = false,
        parentSessionId = parent,
        origin = origin,
        cwd = null,
        agentPreset = null,
        updatedAt = updatedAt,
        pendingInteraction = null,
    )

    private fun order(listable: List<SessionRow>, nested: Set<String>, byRecency: Boolean) =
        orderTopLevel(listable, nested, byRecency)

    @Test
    fun recencyOrderPutsNewestFirst() {
        val rows = listOf(
            session("old", updatedAt = 100),
            session("new", updatedAt = 900),
            session("mid", updatedAt = 500),
        )
        assertEquals(
            listOf("new", "mid", "old"),
            order(rows, emptySet(), byRecency = true).map { it.sessionId },
        )
    }

    @Test
    fun manualOrderIsRegistrationOrder() {
        val rows = listOf(
            session("first", updatedAt = 900),
            session("second", updatedAt = 100),
        )
        assertEquals(
            listOf("first", "second"),
            order(rows, emptySet(), byRecency = false).map { it.sessionId },
        )
    }

    @Test
    fun nestedSessionsNeverSurfaceAsTopLevel() {
        val root = session("root", updatedAt = 100)
        val kid = session("kid", updatedAt = 999, parent = "root", origin = "subagent")
        val rows = listOf(root, kid)
        assertEquals(
            listOf("root"),
            order(rows, setOf("kid"), byRecency = true).map { it.sessionId },
        )
    }

    @Test
    fun orphanSubagentsWithNoVisibleAncestorStayTopLevel() {
        val orphan = session("orphan", updatedAt = 500, parent = "hidden", origin = "subagent")
        val rows = listOf(orphan)
        assertEquals(
            listOf("orphan"),
            order(rows, emptySet(), byRecency = true).map { it.sessionId },
        )
    }

    @Test
    fun orderIsStableAcrossRootsAndNestedRows() {
        val rootA = session("a", updatedAt = 300)
        val kidA = session("a-kid", updatedAt = 800, parent = "a", origin = "subagent")
        val rootB = session("b", updatedAt = 600)
        val rows = listOf(rootA, kidA, rootB)
        assertEquals(
            listOf("b", "a"),
            order(rows, setOf("a-kid"), byRecency = true).map { it.sessionId },
        )
    }
}