package com.labteto.dshmobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

/**
 * The Feather glyphs the transcript and chrome use, traced as Compose vectors.
 *
 * Feather (https://feathericons.com, MIT © Cole Bemis) — see `THIRD_PARTY_NOTICES.md`. The set is
 * inlined rather than pulled in as a dependency for the same reason the harness ships its own
 * `ic_ds_*` icons inline: fifteen glyphs is not worth a library, and the ones that matter here have
 * to sit on the same 24-unit grid with the same 2-unit round-capped stroke as the desktop UI they
 * mirror. Material's own icons are a different drawing language (filled, 20-unit optical sizing) and
 * mixing the two in one row reads as a mistake.
 *
 * Every glyph is stroke-only and drawn in black, so `Icon`'s tint colours it.
 */
internal object FeatherIcons {

    /** `terminal` — the shell tools (bash, pwsh). */
    val Terminal: ImageVector by lazy {
        feather("Terminal") {
            moveTo(4f, 17f); lineTo(10f, 11f); lineTo(4f, 5f)
            moveTo(12f, 19f); lineTo(20f, 19f)
        }
    }

    /** `file-text` — reading a file. */
    val FileText: ImageVector by lazy {
        feather("FileText") {
            documentOutline()
            moveTo(16f, 13f); lineTo(8f, 13f)
            moveTo(16f, 17f); lineTo(8f, 17f)
            moveTo(10f, 9f); lineTo(8f, 9f)
        }
    }

    /** `file-plus` — creating a file. */
    val FilePlus: ImageVector by lazy {
        feather("FilePlus") {
            documentOutline()
            moveTo(12f, 18f); lineTo(12f, 12f)
            moveTo(9f, 15f); lineTo(15f, 15f)
        }
    }

    /** `edit-3` — editing a file. */
    val Edit3: ImageVector by lazy {
        feather("Edit3") {
            moveTo(12f, 20f); horizontalLineToRelative(9f)
            moveTo(16.5f, 3.5f)
            arcToRelative(2.121f, 2.121f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3f, 3f)
            lineTo(7f, 19f)
            lineToRelative(-4f, 1f)
            lineToRelative(1f, -4f)
            lineTo(16.5f, 3.5f)
            close()
        }
    }

    /** `search` — grep, glob, web search. */
    val Search: ImageVector by lazy {
        feather("Search") {
            circle(11f, 11f, 8f)
            moveTo(21f, 21f); lineTo(16.65f, 16.65f)
        }
    }

    /** `globe` — web fetch / retrieval cards. */
    val Globe: ImageVector by lazy {
        feather("Globe") {
            circle(12f, 12f, 10f)
            moveTo(2f, 12f); lineTo(22f, 12f)
            moveTo(12f, 2f)
            arcToRelative(15.3f, 15.3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4f, 10f)
            arcToRelative(15.3f, 15.3f, 0f, isMoreThanHalf = false, isPositiveArc = true, -4f, 10f)
            arcToRelative(15.3f, 15.3f, 0f, isMoreThanHalf = false, isPositiveArc = true, -4f, -10f)
            arcToRelative(15.3f, 15.3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4f, -10f)
            close()
        }
    }

    /** `tool` — an unclassified tool call. */
    val Tool: ImageVector by lazy {
        feather("Tool") {
            moveTo(14.7f, 6.3f)
            arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 1.4f)
            lineToRelative(1.6f, 1.6f)
            arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.4f, 0f)
            lineToRelative(3.77f, -3.77f)
            arcToRelative(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = true, -7.94f, 7.94f)
            lineToRelative(-6.91f, 6.91f)
            arcToRelative(2.12f, 2.12f, 0f, isMoreThanHalf = false, isPositiveArc = true, -3f, -3f)
            lineToRelative(6.91f, -6.91f)
            arcToRelative(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = true, 7.94f, -7.94f)
            lineToRelative(-3.76f, 3.76f)
            close()
        }
    }

    /** `code` — `run_code` and diff cards. */
    val Code: ImageVector by lazy {
        feather("Code") {
            moveTo(16f, 18f); lineTo(22f, 12f); lineTo(16f, 6f)
            moveTo(8f, 6f); lineTo(2f, 12f); lineTo(8f, 18f)
        }
    }

    /** `git-branch` — workflow rows. */
    val GitBranch: ImageVector by lazy {
        feather("GitBranch") {
            moveTo(6f, 3f); lineTo(6f, 15f)
            circle(18f, 6f, 3f)
            circle(6f, 18f, 3f)
            moveTo(18f, 9f)
            arcToRelative(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, -9f, 9f)
        }
    }

    /** `check-square` — todo docks. */
    val CheckSquare: ImageVector by lazy {
        feather("CheckSquare") {
            moveTo(9f, 11f); lineTo(12f, 14f); lineTo(22f, 4f)
            moveTo(21f, 12f)
            verticalLineToRelative(7f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, 2f)
            horizontalLineTo(5f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, -2f)
            verticalLineTo(5f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, -2f)
            horizontalLineToRelative(11f)
        }
    }

    /** `archive` — compaction rows. */
    val Archive: ImageVector by lazy {
        feather("Archive") {
            moveTo(21f, 8f); lineTo(21f, 21f); lineTo(3f, 21f); lineTo(3f, 8f)
            rectangle(1f, 3f, 22f, 5f)
            moveTo(10f, 12f); lineTo(14f, 12f)
        }
    }

    /** `alert-triangle` — warnings and connection banners. */
    val AlertTriangle: ImageVector by lazy {
        feather("AlertTriangle") {
            moveTo(10.29f, 3.86f)
            lineTo(1.82f, 18f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.71f, 3f)
            horizontalLineToRelative(16.94f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.71f, -3f)
            lineTo(13.71f, 3.86f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, -3.42f, 0f)
            close()
            moveTo(12f, 9f); lineTo(12f, 13f)
            moveTo(12f, 17f); lineTo(12.01f, 17f)
        }
    }

    /** `info` — the details-panel button. Outline, unlike Material's filled disc. */
    val Info: ImageVector by lazy {
        feather("Info") {
            circle(12f, 12f, 10f)
            moveTo(12f, 16f); lineTo(12f, 12f)
            moveTo(12f, 8f); lineTo(12.01f, 8f)
        }
    }

    /** `menu` — the drawer button. */
    val Menu: ImageVector by lazy {
        feather("Menu") {
            moveTo(3f, 6f); lineTo(21f, 6f)
            moveTo(3f, 12f); lineTo(21f, 12f)
            moveTo(3f, 18f); lineTo(21f, 18f)
        }
    }

    /** `chevron-right` — disclosure affordance; rotates to 90° when open. */
    val ChevronRight: ImageVector by lazy {
        feather("ChevronRight") {
            moveTo(9f, 18f); lineTo(15f, 12f); lineTo(9f, 6f)
        }
    }
    /** `check` — confirmations and selections. */
    val Check: ImageVector by lazy {
        feather("Check") {
            moveTo(20f, 6f); lineTo(9f, 17f); lineTo(4f, 12f)
        }
    }

    /** `x` — dismiss and remove. */
    val X: ImageVector by lazy {
        feather("X") {
            moveTo(18f, 6f); lineTo(6f, 18f)
            moveTo(6f, 6f); lineTo(18f, 18f)
        }
    }

    /** `chevron-down` — dropdown and expand hints. */
    val ChevronDown: ImageVector by lazy {
        feather("ChevronDown") {
            moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f)
        }
    }

    /** `chevron-left` — paging back; mirrors under RTL. */
    val ChevronLeft: ImageVector by lazy {
        feather("ChevronLeft") {
            moveTo(15f, 18f); lineTo(9f, 12f); lineTo(15f, 6f)
        }
    }

    /** `chevron-up` — collapse hints. */
    val ChevronUp: ImageVector by lazy {
        feather("ChevronUp") {
            moveTo(18f, 15f); lineTo(12f, 9f); lineTo(6f, 15f)
        }
    }

    /** `arrow-left` — back navigation; mirrors under RTL. */
    val ArrowLeft: ImageVector by lazy {
        feather("ArrowLeft") {
            moveTo(19f, 12f); lineTo(5f, 12f)
            moveTo(12f, 19f); lineTo(5f, 12f); lineTo(12f, 5f)
        }
    }

    /** `arrow-right` — forward navigation; mirrors under RTL. */
    val ArrowRight: ImageVector by lazy {
        feather("ArrowRight") {
            moveTo(5f, 12f); lineTo(19f, 12f)
            moveTo(12f, 5f); lineTo(19f, 12f); lineTo(12f, 19f)
        }
    }

    /** `arrow-up` — send. */
    val ArrowUp: ImageVector by lazy {
        feather("ArrowUp") {
            moveTo(12f, 19f); lineTo(12f, 5f)
            moveTo(5f, 12f); lineTo(12f, 5f); lineTo(19f, 12f)
        }
    }

    /** `arrow-down` — download-style hints. */
    val ArrowDown: ImageVector by lazy {
        feather("ArrowDown") {
            moveTo(12f, 5f); lineTo(12f, 19f)
            moveTo(19f, 12f); lineTo(12f, 19f); lineTo(5f, 12f)
        }
    }

    /** `more-vertical` — row action menus. */
    val MoreVertical: ImageVector by lazy {
        feather("MoreVertical") {
            circle(12f, 12f, 1f)
            circle(12f, 5f, 1f)
            circle(12f, 19f, 1f)
        }
    }

    /** `more-horizontal` — message actions. */
    val MoreHorizontal: ImageVector by lazy {
        feather("MoreHorizontal") {
            circle(12f, 12f, 1f)
            circle(19f, 12f, 1f)
            circle(5f, 12f, 1f)
        }
    }

    /** `settings` — the gear; settings screens and buttons. */
    val Settings: ImageVector by lazy {
        feather("Settings") {
            moveTo(19.4f, 15f)
            arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 19.73f, 16.82f)
            lineTo(19.79f, 16.88f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 19.79f, 19.71f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 16.96f, 19.71f)
            lineTo(16.9f, 19.65f)
            arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 15.08f, 19.32f)
            arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 14.08f, 20.83f)
            lineTo(14.08f, 21f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12.08f, 23f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 10.08f, 21f)
            lineTo(10.08f, 20.91f)
            arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 9f, 19.4f)
            arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 7.18f, 19.73f)
            lineTo(7.12f, 19.79f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4.29f, 19.79f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4.29f, 16.96f)
            lineTo(4.35f, 16.9f)
            arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 4.68f, 15.08f)
            arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 3.17f, 14.08f)
            lineTo(3f, 14.08f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1f, 12.08f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3f, 10.08f)
            lineTo(3.09f, 10.08f)
            arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 4.6f, 9f)
            arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 4.27f, 7.18f)
            lineTo(4.21f, 7.12f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4.21f, 4.29f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 7.04f, 4.29f)
            lineTo(7.1f, 4.35f)
            arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 8.92f, 4.68f)
            lineTo(9f, 4.68f)
            arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 10f, 3.17f)
            lineTo(10f, 3f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12f, 1f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 14f, 3f)
            lineTo(14f, 3.09f)
            arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 15f, 4.6f)
            arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 16.82f, 4.27f)
            lineTo(16.88f, 4.21f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 19.71f, 4.21f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 19.71f, 7.04f)
            lineTo(19.65f, 7.1f)
            arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 19.32f, 8.92f)
            lineTo(19.32f, 9f)
            arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 20.83f, 10f)
            lineTo(21f, 10f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 23f, 12f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 21f, 14f)
            lineTo(20.91f, 14f)
            arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 19.4f, 15f)
            close()
            circle(12f, 12f, 3f)
        }
    }

    /** `eye` — reveal a masked field. */
    val Eye: ImageVector by lazy {
        feather("Eye") {
            moveTo(1f, 12f)
            reflectiveCurveTo(5f, 4f, 12f, 4f)
            reflectiveCurveTo(23f, 12f, 23f, 12f)
            reflectiveCurveTo(19f, 20f, 12f, 20f)
            reflectiveCurveTo(1f, 12f, 1f, 12f)
            close()
            circle(12f, 12f, 3f)
        }
    }

    /** `eye-off` — mask a field. */
    val EyeOff: ImageVector by lazy {
        feather("EyeOff") {
            moveTo(17.94f, 17.94f)
            arcTo(10.07f, 10.07f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12f, 20f)
            curveTo(5f, 20f, 1f, 12f, 1f, 12f)
            arcTo(18.45f, 18.45f, 0f, isMoreThanHalf = false, isPositiveArc = true, 6.06f, 6.06f)
            moveTo(9.9f, 4.24f)
            arcTo(9.12f, 9.12f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12f, 4f)
            curveTo(19f, 4f, 23f, 12f, 23f, 12f)
            arcTo(18.5f, 18.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20.84f, 15.19f)
            moveTo(14.12f, 14.12f)
            arcTo(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, 9.88f, 9.88f)
            moveTo(1f, 1f); lineTo(23f, 23f)
        }
    }

    /** `download` — export. */
    val Download: ImageVector by lazy {
        feather("Download") {
            moveTo(21f, 15f)
            lineTo(21f, 19f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 19f, 21f)
            lineTo(5f, 21f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3f, 19f)
            lineTo(3f, 15f)
            moveTo(7f, 10f); lineTo(12f, 15f); lineTo(17f, 10f)
            moveTo(12f, 15f); lineTo(12f, 3f)
        }
    }

    /** `shield` — permission presets. */
    val Shield: ImageVector by lazy {
        feather("Shield") {
            moveTo(12f, 22f)
            reflectiveCurveTo(20f, 18f, 20f, 12f)
            lineTo(20f, 5f)
            lineTo(12f, 2f)
            lineTo(4f, 5f)
            lineTo(4f, 12f)
            curveTo(4f, 18f, 12f, 22f, 12f, 22f)
            close()
        }
    }

    /** `image` — the attach command. */
    val Image: ImageVector by lazy {
        feather("Image") {
            roundedRect(3f, 3f, 18f, 18f, 2f)
            circle(8.5f, 8.5f, 1.5f)
            moveTo(21f, 15f); lineTo(15f, 9f); lineTo(5f, 21f)
        }
    }

    /** `users` — subagents. */
    val Users: ImageVector by lazy {
        feather("Users") {
            moveTo(17f, 21f)
            lineTo(17f, 19f)
            arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = false, 13f, 15f)
            lineTo(5f, 15f)
            arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1f, 19f)
            lineTo(1f, 21f)
            circle(9f, 7f, 4f)
            moveTo(23f, 21f)
            lineTo(23f, 19f)
            arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = false, 20f, 15.13f)
            moveTo(16f, 3.13f)
            arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 16f, 10.88f)
        }
    }

    /** `copy` — copy to clipboard. */
    val Copy: ImageVector by lazy {
        feather("Copy") {
            roundedRect(9f, 9f, 13f, 13f, 2f)
            moveTo(5f, 15f)
            lineTo(4f, 15f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 13f)
            lineTo(2f, 4f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4f, 2f)
            lineTo(13f, 2f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 15f, 4f)
            lineTo(15f, 5f)
        }
    }

    /** `thumbs-up` — positive feedback. */
    val ThumbsUp: ImageVector by lazy {
        feather("ThumbsUp") {
            moveTo(14f, 9f)
            lineTo(14f, 5f)
            arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = false, 11f, 2f)
            lineTo(7f, 11f)
            lineTo(7f, 22f)
            lineTo(18.28f, 22f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 20.28f, 20.3f)
            lineTo(21.66f, 11.3f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 19.66f, 9f)
            close()
            moveTo(7f, 22f)
            lineTo(4f, 22f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 20f)
            lineTo(2f, 13f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4f, 11f)
            lineTo(7f, 11f)
        }
    }

    /** `thumbs-down` — negative feedback. */
    val ThumbsDown: ImageVector by lazy {
        feather("ThumbsDown") {
            moveTo(10f, 15f)
            lineTo(10f, 19f)
            arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = false, 13f, 22f)
            lineTo(17f, 13f)
            lineTo(17f, 2f)
            lineTo(5.72f, 2f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 3.72f, 3.7f)
            lineTo(2.34f, 12.7f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 4.34f, 15f)
            close()
            moveTo(17f, 2f)
            lineTo(19.67f, 2f)
            arcTo(2.31f, 2.31f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 4f)
            lineTo(22f, 11f)
            arcTo(2.31f, 2.31f, 0f, isMoreThanHalf = false, isPositiveArc = true, 19.67f, 13f)
            lineTo(17f, 13f)
        }
    }

    /** `chevrons-up-down` — the session sort control. */
    val ChevronsUpDown: ImageVector by lazy {
        feather("ChevronsUpDown") {
            moveTo(7f, 15f); lineTo(12f, 20f); lineTo(17f, 15f)
            moveTo(7f, 9f); lineTo(12f, 4f); lineTo(17f, 9f)
        }
    }

    /** `clock` — timings. */
    val Clock: ImageVector by lazy {
        feather("Clock") {
            circle(12f, 12f, 10f)
            moveTo(12f, 6f); lineTo(12f, 12f); lineTo(16f, 14f)
        }
    }

    /** `external-link` — links out. */
    val ExternalLink: ImageVector by lazy {
        feather("ExternalLink") {
            moveTo(18f, 13f)
            lineTo(18f, 19f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 16f, 21f)
            lineTo(5f, 21f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3f, 19f)
            lineTo(3f, 8f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 5f, 6f)
            lineTo(11f, 6f)
            moveTo(15f, 3f); lineTo(21f, 3f); lineTo(21f, 9f)
            moveTo(10f, 14f); lineTo(21f, 3f)
        }
    }

    /** `trash-2` — deletion. */
    val Trash2: ImageVector by lazy {
        feather("Trash2") {
            moveTo(3f, 6f); lineTo(5f, 6f); lineTo(21f, 6f)
            moveTo(19f, 6f)
            lineTo(19f, 20f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 17f, 22f)
            lineTo(7f, 22f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 5f, 20f)
            lineTo(5f, 6f)
            moveTo(8f, 6f)
            lineTo(8f, 4f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 10f, 2f)
            lineTo(14f, 2f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 16f, 4f)
            lineTo(16f, 6f)
            moveTo(10f, 11f); lineTo(10f, 17f)
            moveTo(14f, 11f); lineTo(14f, 17f)
        }
    }

    /** `square` — the stop control. */
    val Square: ImageVector by lazy {
        feather("Square") {
            roundedRect(3f, 3f, 18f, 18f, 2f)
        }
    }

    /** `send` — sending. */
    val Send: ImageVector by lazy {
        feather("Send") {
            moveTo(22f, 2f); lineTo(11f, 13f)
            moveTo(22f, 2f)
            lineTo(15f, 22f)
            lineTo(11f, 13f)
            lineTo(2f, 9f)
            lineTo(22f, 2f)
            close()
        }
    }

    /** `loader` — indeterminate progress. */
    val Loader: ImageVector by lazy {
        feather("Loader") {
            moveTo(12f, 2f); lineTo(12f, 4f)
            moveTo(12f, 20f); lineTo(12f, 22f)
            moveTo(4.93f, 4.93f); lineTo(6.34f, 6.34f)
            moveTo(17.66f, 17.66f); lineTo(19.07f, 19.07f)
            moveTo(2f, 12f); lineTo(4f, 12f)
            moveTo(20f, 12f); lineTo(22f, 12f)
            moveTo(6.34f, 17.66f); lineTo(4.93f, 19.07f)
            moveTo(19.07f, 4.93f); lineTo(17.66f, 6.34f)
        }
    }

    /** `folder` — workspaces. */
    val Folder: ImageVector by lazy {
        feather("Folder") {
            moveTo(22f, 19f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 21f)
            lineTo(4f, 21f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 19f)
            lineTo(2f, 5f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4f, 3f)
            lineTo(9f, 3f)
            lineTo(11f, 6f)
            lineTo(20f, 6f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 8f)
            close()
        }
    }

    /** `message-square` — chat copy. */
    val MessageSquare: ImageVector by lazy {
        feather("MessageSquare") {
            moveTo(21f, 15f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 19f, 17f)
            lineTo(7f, 17f)
            lineTo(3f, 21f)
            lineTo(3f, 5f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 5f, 3f)
            lineTo(19f, 3f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 21f, 5f)
            close()
        }
    }

    /** `user` — single entities. */
    val User: ImageVector by lazy {
        feather("User") {
            moveTo(20f, 21f)
            lineTo(20f, 19f)
            arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = false, 16f, 15f)
            lineTo(8f, 15f)
            arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = false, 4f, 19f)
            lineTo(4f, 21f)
            circle(12f, 7f, 4f)
        }
    }

    /** `log-out` — switching hosts. */
    val LogOut: ImageVector by lazy {
        feather("LogOut") {
            moveTo(9f, 21f)
            lineTo(5f, 21f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3f, 19f)
            lineTo(3f, 5f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 5f, 3f)
            lineTo(9f, 3f)
            moveTo(16f, 17f); lineTo(21f, 12f); lineTo(16f, 7f)
            moveTo(21f, 12f); lineTo(9f, 12f)
        }
    }

    /** `help-circle` — help affordances. */
    val HelpCircle: ImageVector by lazy {
        feather("HelpCircle") {
            circle(12f, 12f, 10f)
            moveTo(9.09f, 9f)
            arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 14.92f, 10f)
            curveTo(14.92f, 12f, 11.92f, 13f, 11.92f, 13f)
            moveTo(12f, 17f); lineTo(12.01f, 17f)
        }
    }

    /** `refresh-cw` — retry and reconnect. */
    val RefreshCw: ImageVector by lazy {
        feather("RefreshCw") {
            moveTo(23f, 4f); lineTo(23f, 10f); lineTo(17f, 10f)
            moveTo(1f, 20f); lineTo(1f, 14f); lineTo(7f, 14f)
            moveTo(3.51f, 9f)
            arcTo(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 18.36f, 5.64f)
            lineTo(23f, 10f)
            moveTo(1f, 14f)
            lineTo(5.64f, 18.36f)
            arcTo(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = false, 20.49f, 15f)
        }
    }

    /** `plus` — add. */
    val Plus: ImageVector by lazy {
        feather("Plus") {
            moveTo(12f, 5f); lineTo(12f, 19f)
            moveTo(5f, 12f); lineTo(19f, 12f)
        }
    }

    /** `activity` — the live-session control center (Active tab). */
    val Activity: ImageVector by lazy {
        feather("Activity") {
            moveTo(22f, 12f); lineTo(18f, 12f); lineTo(15f, 21f); lineTo(9f, 3f); lineTo(6f, 12f); lineTo(2f, 12f)
        }
    }

    /** `layout` — agent presets. */
    val Layout: ImageVector by lazy {
        feather("Layout") {
            roundedRect(3f, 3f, 18f, 18f, 2f)
            moveTo(3f, 9f); lineTo(21f, 9f)
            moveTo(9f, 21f); lineTo(9f, 9f)
        }
    }

    /** `edit-2` — the pencil. */
    val Pencil: ImageVector by lazy {
        feather("Edit2") {
            moveTo(17f, 3f)
            arcTo(2.83f, 2.83f, 0f, isMoreThanHalf = true, isPositiveArc = true, 21f, 7f)
            lineTo(7.5f, 20.5f)
            lineTo(2f, 22f)
            lineTo(3.5f, 16.5f)
            lineTo(17f, 3f)
            close()
        }
    }
}

// ---------------------------------------------------------------------------
// Builders
// ---------------------------------------------------------------------------

/** One Feather glyph: 24-unit grid, 2-unit round-capped stroke, no fill. */
private fun feather(name: String, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = "Feather.$name",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        )
    }.build()

/**
 * Mirrors a directional glyph when the layout is RTL.
 *
 * Feather's arrows and chevrons are drawn for LTR; Material's AutoMirrored icons handled this
 * implicitly. Back/forward affordances (the back button, pager chevrons, disclosure rows) flip
 * with the reading direction; everything else stays put.
 */
@Composable
fun Modifier.autoMirrorDirectional(): Modifier {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return if (rtl) graphicsLayer { scaleX = -1f } else this
}

/** SVG's `<circle>`, as the two half-arcs an `M … a … a …` path would draw. */
private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = false, 2 * r, 0f)
    arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = false, -2 * r, 0f)
}

/** SVG's `<rect>` without corner radii. */
private fun PathBuilder.rectangle(x: Float, y: Float, width: Float, height: Float) {
    moveTo(x, y)
    horizontalLineToRelative(width)
    verticalLineToRelative(height)
    horizontalLineToRelative(-width)
    close()
}

/** SVG's `<rect>` with corner radii. */
private fun PathBuilder.roundedRect(x: Float, y: Float, width: Float, height: Float, r: Float) {
    moveTo(x + r, y)
    lineTo(x + width - r, y)
    arcToRelative(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, r, r)
    lineTo(x + width, y + height - r)
    arcToRelative(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, -r, r)
    lineTo(x + r, y + height)
    arcToRelative(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, -r, -r)
    lineTo(x, y + r)
    arcToRelative(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, r, -r)
    close()
}

/** The dog-eared page both `file-text` and `file-plus` are drawn on. */
private fun PathBuilder.documentOutline() {
    moveTo(14f, 2f)
    horizontalLineTo(6f)
    arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, -2f, 2f)
    verticalLineToRelative(16f)
    arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2f, 2f)
    horizontalLineToRelative(12f)
    arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2f, -2f)
    verticalLineTo(8f)
    close()
    moveTo(14f, 2f); lineTo(14f, 8f); lineTo(20f, 8f)
}
