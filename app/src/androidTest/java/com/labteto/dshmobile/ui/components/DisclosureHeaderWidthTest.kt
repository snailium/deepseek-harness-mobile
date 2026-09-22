package com.labteto.dshmobile.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.ui.theme.DshTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The disclosure header's width contract.
 *
 * Six elements share the line: a chevron and a glyph (fixed), the title (hugs its text, never
 * ellipsised), a separator (fixed), the summary (absorbs everything left, ellipsised) and a
 * trailing slot (fixed, at the right edge).
 *
 * Only the summary may carry a `weight`. A weighted child is *allocated* a share before anything is
 * measured, so giving the title a weight too split the line evenly: a short name still claimed half
 * of it, which is what left the visible gap between the name and the separator while a long
 * description ellipsised at half the width it should have had. `fill = false` does not repair that —
 * it only lets a child use *less* than its allocation, never take what its text needs.
 *
 * These assertions are about geometry, not appearance, because that is the part a reader notices as
 * "the row does not fill the line" and the part no screenshot review reliably catches.
 */
class DisclosureHeaderWidthTest {

    @get:Rule
    val rule = createComposeRule()

    private fun rowRect(tag: String): Rect =
        rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    /** The device's density, for turning pixel bounds into the dp the assertions reason in. */
    private val density: Float get() = rule.density.density

    /**
     * A long summary must still reach the trailing edge.
     *
     * This is the reported symptom: the header stops short of the right margin. It happens whenever
     * the title is not also given a weight, so the summary is the only flexible child and receives
     * every pixel the fixed ones leave.
     */
    @Test
    fun summaryFillsTheRowToTheTrailingEdge() {
        rule.setContent {
            DshTheme {
                Box(Modifier.width(360.dp)) {
                    DisclosureRow(
                        title = "Read",
                        summary = "app/src/main/java/com/labteto/dshmobile/ui/components/DisclosureRow.kt",
                        icon = FeatherIcons.FileText,
                        onToggle = {},
                        modifier = Modifier.fillMaxWidth().testTag("row"),
                    )
                }
            }
        }

        val row = rowRect("row")
        val summary = rowRect(DISCLOSURE_SUMMARY_TAG)

        // The summary's right edge is the row's right edge, give or take the row's own padding.
        val slack = row.right - summary.right
        assertTrue(
            "summary should reach the row's right edge, but stopped ${slack}px short " +
                "(row=${row.right}, summary=${summary.right})",
            slack < 2f,
        )
    }

    /**
     * A short title keeps its intrinsic width rather than claiming a share of the line.
     *
     * With a weight, `Read` would be allocated half the row and the separator would be pushed
     * rightward — the gap between the name and the separator that reads as a layout mistake.
     */
    @Test
    fun shortTitleDoesNotClaimAShareOfTheLine() {
        rule.setContent {
            DshTheme {
                Box(Modifier.width(360.dp)) {
                    DisclosureRow(
                        title = "Re",
                        summary = "x",
                        icon = FeatherIcons.FileText,
                        onToggle = {},
                        modifier = Modifier.fillMaxWidth().testTag("row"),
                    )
                }
            }
        }

        val row = rowRect("row")
        val title = rowRect(DISCLOSURE_TITLE_TAG)

        // "Re" at 14sp is a few tens of px. Half of a 360dp row would be ~180dp, far above this.
        val titleWidthDp = title.width / density
        assertTrue(
            "a 2-character title took ${titleWidthDp}dp; it should hug its text, not a share of the row",
            titleWidthDp < 60f,
        )
    }

    /**
     * A long title is the pathological case, not the normal one: it must not push the summary off
     * the row entirely. Ellipsis is the backstop here, and this pins that the backstop is reached
     * rather than the row overflowing.
     */
    @Test
    fun pathologicalTitleDoesNotOverflowTheRow() {
        val absurd = "x".repeat(300)
        rule.setContent {
            DshTheme {
                Box(Modifier.width(360.dp)) {
                    DisclosureRow(
                        title = absurd,
                        summary = "short",
                        icon = FeatherIcons.FileText,
                        onToggle = {},
                        modifier = Modifier.fillMaxWidth().testTag("row"),
                    )
                }
            }
        }

        val row = rowRect("row")
        val summary = rowRect(DISCLOSURE_SUMMARY_TAG)
        val title = rowRect(DISCLOSURE_TITLE_TAG)

        assertEquals(
            "row must not grow past its constraint",
            360f * density,
            row.width,
            2f,
        )
        assertTrue(
            "a 300-character title should be ellipsised, not drawn at full width",
            title.width <= row.width,
        )
        assertTrue("summary must remain on the row", summary.right <= row.right + 2f)
    }
}
