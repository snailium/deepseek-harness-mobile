package com.labteto.dshmobile.core.wire.dto

import com.labteto.dshmobile.core.wire.WireJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `workspace/changes` is a durable event carrying only a turn number; the file list lives behind
 * `GET /api/changes.summary`, which answers a **bare** summary object rather than the usual
 * `{ok, value}` envelope.
 *
 * These pin that wire shape, including the two fields the route deliberately withholds and the two
 * flags that make a file's line counts meaningless.
 */
class WorkspaceChangesDtoTest {

    private fun decode(json: String): WorkspaceChangesSummary =
        WireJson.decodeFromString(WorkspaceChangesSummary.serializer(), json)

    @Test
    fun `a bare summary object decodes`() {
        // Exactly what handleChangesSummary returns: turn/files/total/added/deleted, no envelope,
        // and no cwd or snapshot ids even though the host-side type carries them.
        val s = decode(
            """
            {"turn":3,"files":[
              {"path":"app/Foo.kt","display":"app/Foo.kt","added":12,"deleted":3},
              {"path":"/etc/hosts","display":"~/hosts","added":1,"deleted":0,"binary":true}
            ],"total":5,"added":40,"deleted":7}
            """.trimIndent(),
        )
        assertEquals(3, s.turn)
        assertEquals(2, s.files.size)
        assertEquals("app/Foo.kt", s.files[0].display)
        assertEquals(12, s.files[0].added)
        assertEquals(3, s.files[0].deleted)
        // total counts files the cap omitted, so it can exceed the list.
        assertEquals(5, s.total)
        assertEquals(40, s.added)
        assertEquals(7, s.deleted)
    }

    @Test
    fun `a capped list is detectable`() {
        val s = decode("""{"turn":1,"files":[{"path":"a","display":"a"}],"total":9}""")
        assertTrue("the renderer must be able to tell the list is truncated", s.total > s.files.size)
    }

    @Test
    fun `absent counts default to zero rather than failing the decode`() {
        // The route always sends them, but a missing field must not kill the whole row.
        val s = decode("""{"turn":1,"files":[{"path":"a","display":"a"}]}""")
        assertEquals(0, s.added)
        assertEquals(0, s.deleted)
        assertEquals(1, s.turn)
    }

    @Test
    fun `binary and oversized suppress the counts`() {
        // The harness reports 0/0 for both, so a renderer that trusted the numbers would claim
        // "nothing changed" about a file it could not read.
        val binary = decode("""{"turn":1,"files":[{"path":"a","display":"a","added":0,"deleted":0,"binary":true}]}""")
        assertFalse("a binary file's counts must not be presented as real", binary.files[0].hasCounts)

        val oversized = decode("""{"turn":1,"files":[{"path":"b","display":"b","oversized":true}]}""")
        assertFalse("an oversized file's counts must not be presented as real", oversized.files[0].hasCounts)

        val normal = decode("""{"turn":1,"files":[{"path":"c","display":"c","added":4,"deleted":1}]}""")
        assertTrue("an ordinary file's counts are real", normal.files[0].hasCounts)
    }

    @Test
    fun `an empty file list decodes`() {
        // A turn that changed nothing still emits the event.
        val s = decode("""{"turn":2,"files":[],"total":0}""")
        assertTrue(s.files.isEmpty())
        assertEquals(2, s.turn)
    }

    @Test
    fun `display is kept verbatim, because it is already a slash-separated label`() {
        // `../` above cwd and `~` under home are the harness's own forms; rewriting them for a
        // phone would lose the distinction it drew.
        val s = decode("""{"turn":1,"files":[{"path":"../x","display":"../x"}]}""")
        assertEquals("../x", s.files[0].display)
    }
}
