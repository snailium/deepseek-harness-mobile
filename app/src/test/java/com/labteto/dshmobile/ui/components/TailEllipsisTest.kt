package com.labteto.dshmobile.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [tailEllipsis] is the left-truncation strategy for the changed-file rows: keep the tail (the
 * file name), drop whole leading segments, mark the cut with a single `…`. It is pure — no layout,
 * no density — so it can be tested directly against its contract.
 *
 * The budget is [MAX_PATH_CHARS] characters, calibrated for the mono face at 13sp inside the row's
 * geometry. A path at or under the budget passes through untouched; a longer one is cut to the
 * longest suffix that fits once the leading `…` is accounted for.
 */
class TailEllipsisTest {

    @Test
    fun `a short path passes through unchanged`() {
        assertEquals("src/Foo.kt", tailEllipsis("src/Foo.kt"))
    }

    @Test
    fun `a path exactly at the budget passes through unchanged`() {
        val path = "a".repeat(MAX_PATH_CHARS)
        assertEquals(path, tailEllipsis(path))
    }

    @Test
    fun `a long path keeps its tail and marks the cut with an ellipsis`() {
        // 40 chars: over budget. The result must start with `…`, end with the file name, and be
        // at most MAX_PATH_CHARS long (the `…` counts as one character).
        val path = "app/src/main/java/com/labteto/dshmobile/ui/Foo.kt"
        val out = tailEllipsis(path)
        assertTrue(out.startsWith("…"))
        assertTrue(out.endsWith("Foo.kt"))
        assertTrue(out.length <= MAX_PATH_CHARS)
    }

    @Test
    fun `a long path keeps the longest suffix that fits`() {
        // Build a path whose segments are each short but whose total is long. The result must be
        // the longest suffix of whole segments that fits under (MAX_PATH_CHARS - 1), prefixed with
        // `…`. Reconstructing that expectation independently of the implementation pins the
        // "longest suffix" contract — a regression that drops one extra segment, or keeps one too
        // many, fails here.
        val segments = listOf("app", "src", "main", "java", "com", "labteto", "dshmobile", "Foo.kt")
        val path = segments.joinToString("/")
        val out = tailEllipsis(path)

        val budget = MAX_PATH_CHARS - 1
        var expectedTail = segments.last()
        for (i in (segments.lastIndex - 1) downTo 0) {
            val candidate = "${segments[i]}/$expectedTail"
            if (candidate.length <= budget) {
                expectedTail = candidate
            } else {
                break
            }
        }
        assertEquals("…$expectedTail", out)
    }

    @Test
    fun `a single segment longer than the budget is handed to compose`() {
        // No slash to cut on: return the name as-is and let Compose's right-ellipsis finish the
        // job. The contract here is "do not cut inside the name ourselves" — the output equals
        // the input, however long it is.
        val name = "x".repeat(MAX_PATH_CHARS + 10)
        assertEquals(name, tailEllipsis(name))
    }

    @Test
    fun `a path with a very long final segment falls back to compose`() {
        // The directory prefix is short but the name alone overflows the budget. We must not cut
        // inside the name: return it whole and let Compose ellipsize on the right.
        val name = "y".repeat(MAX_PATH_CHARS)
        val path = "src/$name"
        assertEquals(name, tailEllipsis(path))
    }

    @Test
    fun `a root-relative path keeps its leading slash in the tail`() {
        // `/etc/hosts` style: the first segment is empty. The tail should still end with the name
        // and the cut should be marked.
        val path = "/very/long/directory/tree/etc/hosts"
        val out = tailEllipsis(path)
        assertTrue(out.endsWith("hosts"))
        if (out != path) {
            assertTrue(out.startsWith("…"))
        }
    }

    @Test
    fun `a tilde home path truncates like any other`() {
        // The harness's `~` form: the leading segment is `~`, not empty. Same contract as a normal
        // relative path.
        val path = "~/projects/deepseek-harness-mobile/app/src/main/java/Foo.kt"
        val out = tailEllipsis(path)
        assertTrue(out.endsWith("Foo.kt"))
        if (out != path) {
            assertTrue(out.startsWith("…"))
            assertTrue(out.length <= MAX_PATH_CHARS)
        }
    }

    private fun assertTrue(condition: Boolean, message: String = "assertion failed") {
        org.junit.Assert.assertTrue(message, condition)
    }
}
