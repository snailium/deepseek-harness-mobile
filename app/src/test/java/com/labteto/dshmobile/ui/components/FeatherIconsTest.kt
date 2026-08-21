package com.labteto.dshmobile.ui.components

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The glyphs are hand-transcribed path data, and a mistyped builder fails quietly — an empty path
 * renders as a blank 16dp hole in a tool row rather than as an error. This checks each one actually
 * produced geometry on the grid the rest of the set uses.
 */
class FeatherIconsTest {

    private val glyphs: Map<String, ImageVector> = mapOf(
        "Terminal" to FeatherIcons.Terminal,
        "FileText" to FeatherIcons.FileText,
        "FilePlus" to FeatherIcons.FilePlus,
        "Edit3" to FeatherIcons.Edit3,
        "Search" to FeatherIcons.Search,
        "Globe" to FeatherIcons.Globe,
        "Tool" to FeatherIcons.Tool,
        "Code" to FeatherIcons.Code,
        "GitBranch" to FeatherIcons.GitBranch,
        "CheckSquare" to FeatherIcons.CheckSquare,
        "Archive" to FeatherIcons.Archive,
        "AlertTriangle" to FeatherIcons.AlertTriangle,
        "Info" to FeatherIcons.Info,
        "Menu" to FeatherIcons.Menu,
        "ChevronRight" to FeatherIcons.ChevronRight,
        "Check" to FeatherIcons.Check,
        "X" to FeatherIcons.X,
        "ChevronDown" to FeatherIcons.ChevronDown,
        "ChevronLeft" to FeatherIcons.ChevronLeft,
        "ChevronUp" to FeatherIcons.ChevronUp,
        "ArrowLeft" to FeatherIcons.ArrowLeft,
        "ArrowRight" to FeatherIcons.ArrowRight,
        "ArrowUp" to FeatherIcons.ArrowUp,
        "ArrowDown" to FeatherIcons.ArrowDown,
        "MoreVertical" to FeatherIcons.MoreVertical,
        "MoreHorizontal" to FeatherIcons.MoreHorizontal,
        "Settings" to FeatherIcons.Settings,
        "Eye" to FeatherIcons.Eye,
        "EyeOff" to FeatherIcons.EyeOff,
        "Download" to FeatherIcons.Download,
        "Shield" to FeatherIcons.Shield,
        "Image" to FeatherIcons.Image,
        "Users" to FeatherIcons.Users,
        "Copy" to FeatherIcons.Copy,
        "ThumbsUp" to FeatherIcons.ThumbsUp,
        "ThumbsDown" to FeatherIcons.ThumbsDown,
        "ChevronsUpDown" to FeatherIcons.ChevronsUpDown,
        "Clock" to FeatherIcons.Clock,
        "ExternalLink" to FeatherIcons.ExternalLink,
        "Trash2" to FeatherIcons.Trash2,
        "Square" to FeatherIcons.Square,
        "Send" to FeatherIcons.Send,
        "Loader" to FeatherIcons.Loader,
        "Folder" to FeatherIcons.Folder,
        "MessageSquare" to FeatherIcons.MessageSquare,
        "User" to FeatherIcons.User,
        "LogOut" to FeatherIcons.LogOut,
        "HelpCircle" to FeatherIcons.HelpCircle,
        "RefreshCw" to FeatherIcons.RefreshCw,
        "Plus" to FeatherIcons.Plus,
        "Layout" to FeatherIcons.Layout,
        "Pencil" to FeatherIcons.Pencil,
    )

    @Test
    fun `every glyph draws something on the 24-unit grid`() {
        glyphs.forEach { (name, vector) ->
            assertEquals("$name viewport width", 24f, vector.viewportWidth, 0f)
            assertEquals("$name viewport height", 24f, vector.viewportHeight, 0f)
            assertEquals("$name path count", 1, vector.root.size)
            val path = vector.root.first() as VectorPath
            assertTrue("$name has no path nodes", path.pathData.isNotEmpty())
        }
    }

    @Test
    fun `every glyph is stroked in Feather's own weight, never filled`() {
        glyphs.forEach { (name, vector) ->
            val path = vector.root.first() as VectorPath
            assertEquals("$name stroke width", 2f, path.strokeLineWidth, 0f)
            assertEquals("$name stroke cap", StrokeCap.Round, path.strokeLineCap)
            assertEquals("$name stroke join", StrokeJoin.Round, path.strokeLineJoin)
            assertEquals("$name should have no fill", null, path.fill)
        }
    }

    @Test
    fun `the set is memoized, so a scrolling transcript rebuilds nothing`() {
        assertTrue(FeatherIcons.Terminal === FeatherIcons.Terminal)
    }
}
