package com.labteto.dshmobile.core.wire

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which unary calls go out without a read deadline.
 *
 * This exists because the answer was once "none of them", and the cost was a `/compact` aborted at
 * exactly 30s against `saya-ch/dsh-mobile` with `transport failure: timeout`. A command execution is
 * answered when the *host* finishes it — `/compact` summarizes the whole session — so an idle-read
 * deadline cannot tell a working host from a dead one.
 *
 * The list is the thing that rots: a new host-bounded endpoint added to [DshApiClient] and not
 * added here silently reintroduces the bug, on the slowest and most important session there is.
 * So the assertion is on the criterion, not on a count.
 */
class HostBoundedPathTest {

    @Test
    fun `a command execution carries no read deadline`() {
        assertTrue(isHostBoundedPath("/api/commands/execute"))
    }

    @Test
    fun `ordinary unary calls keep their read deadline`() {
        // The deadline is what notices a link that has gone quiet; relaxing it everywhere would
        // trade this bug for a worse one (a stalled request that never reports).
        listOf(
            "/api/session/list",
            "/api/session/page",
            "/api/session/prompt",
            "/api/settings/list",
            "/api/workspaceFiles/list",
            "/api/terminal/list",
            "/api/plugins/list",
        ).forEach { path ->
            assertFalse("$path must keep its read deadline", isHostBoundedPath(path))
        }
    }

    @Test
    fun `the match is exact, not a prefix or a substring`() {
        // A near-miss must not inherit the exemption: the `/api/commands/execute` spelling is what
        // the client sends, and anything else is a different route with its own answer time.
        assertFalse(isHostBoundedPath("/api/commands"))
        assertFalse(isHostBoundedPath("/api/commands/executeSomethingElse"))
        assertFalse(isHostBoundedPath("/api/commands/execute?x=1"))
        assertFalse(isHostBoundedPath("commands/execute"))
    }
}
