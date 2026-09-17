package com.labteto.dshmobile.ui.screens.main

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The permission domain's presentation layer, ported from the web client
 * (`dsh-client-ui-conversation/src/client/skeleton/PermissionSelect.tsx` and its `presentation.ts`).
 *
 * The web reference draws a fixed glyph beside each of the three built-in presets; the app used a
 * generic shield for all of them. The vectors below are transcribed from that SVG source at the
 * same 16x16 viewBox, so the shapes match the desktop pixel for pixel rather than merely
 * resembling it. A host-configured preset outside the built-in table gets no glyph — the web's
 * rule — instead of a misleading one.
 *
 * This lives in the app, not in `core`: `core` is a pure-JVM module with no Compose on its
 * classpath, and an [ImageVector] is Compose.
 */

/** The two non-full-access preset ids the web design ships glyphs for. */
const val READ_ONLY_PRESET = "read-only"
const val WORKSPACE_WRITE_PRESET = "workspace-write"

/** Glyph for a permission option value, or null for a host-configured name outside the design set. */
internal fun permissionPresetGlyph(value: String, fullAccessPreset: String): ImageVector? = when (value) {
    READ_ONLY_PRESET -> PermissionGlyphs.ReadOnly
    WORKSPACE_WRITE_PRESET -> PermissionGlyphs.WorkspaceWrite
    fullAccessPreset -> PermissionGlyphs.FullAccess
    else -> null
}

/** The shared outline both shield presets reuse, exactly as the web source reuses `shieldOutline`. */
private const val SHIELD_OUTLINE =
    "M8.20554 0.899994L14.7901 3.36857V7.01026C14.7901 12 11.0466 14.2103 8.20554 15.3C5.36446 " +
        "14.2103 1.62012 12 1.62012 7.01026V3.36857L8.20554 0.899994Z"

private object PermissionGlyphs {

    val ReadOnly: ImageVector by lazy {
        glyph("PermissionReadOnly") {
            stroke(SHIELD_OUTLINE, width = 1.31831f)
            fill(
                "M12.1654 5.7552L8.9447 9.41475C8.73044 9.65816 8.53628 9.8804 8.35774 10.0423C8.1713 " +
                    "10.2114 7.94235 10.3717 7.64016 10.4254C7.48207 10.4535 7.32 10.4552 7.16151 " +
                    "10.4294C6.85843 10.3801 6.62728 10.2223 6.43836 10.0559C6.25752 9.89653 6.06037 " +
                    "9.67732 5.84264 9.43705L4.72925 8.20897L5.63557 7.38707L6.74897 8.61594C6.98603 " +
                    "8.87755 7.12974 9.03533 7.24673 9.13839C7.31033 9.19443 7.34485 9.21476 7.35823 " +
                    "9.22122C7.38068 9.22484 7.40352 9.22515 7.42593 9.22122C7.40522 9.22502 7.42893 " +
                    "9.23294 7.53583 9.136C7.65132 9.03126 7.79316 8.87139 8.02643 8.60638L11.2479 " +
                    "4.94763L12.1654 5.7552Z",
            )
        }
    }

    val WorkspaceWrite: ImageVector by lazy {
        glyph("PermissionWorkspaceWrite") {
            fill(
                "M8.08887 0.251709C8.20479 0.23085 8.32486 0.241168 8.43652 0.282959L15.0215 " +
                    "2.75171C15.2787 2.84819 15.4492 3.09414 15.4492 3.3689V7.0105C15.4492 7.10986 " +
                    "15.4441 7.2081 15.4414 7.30542C15.0285 7.07175 14.5905 6.87695 14.1309 " +
                    "6.73022V3.82495L8.20508 1.60327L2.2793 3.82495V7.0105C2.27936 9.7171 3.4745 " +
                    "11.5379 5.02734 12.7947C5.01025 12.9942 5 13.1962 5 13.4001C5.00001 13.7617 " +
                    "5.02722 14.1169 5.08008 14.4636C2.91555 13.0393 0.961014 10.752 0.960938 " +
                    "7.0105V3.3689C0.960938 3.09417 1.13146 2.84821 1.38867 2.75171L7.97461 " +
                    "0.282959L8.08887 0.251709Z",
            )
            fill("M11.3525 5.64688V6.85688H5V5.64688H11.3525Z")
            fill("M9.5824 8.29376V9.50376H5V8.29376H9.5824Z")
            fill(
                "M14.6647 15.6852H10.0338C10.3878 15.3751 10.7567 15.0517 11.0772 14.7706C11.2531 " +
                    "14.6164 11.4144 14.4746 11.5511 14.3547H14.6647V15.6852Z",
            )
            fill(
                "M8.14852 14.1308L7.33925 15.4976C7.22458 15.6912 7.42245 15.9194 7.63037 " +
                    "15.8333L9.09785 15.2254L15.0399 10.0719L14.0905 8.97733L8.14852 14.1308Z",
            )
        }
    }

    val FullAccess: ImageVector by lazy {
        glyph("PermissionFullAccess") {
            stroke(SHIELD_OUTLINE, width = 1.31831f)
            fill("M9.10094 4.5V8.75939H7.59888V4.5H9.10094Z")
            fill("M9.10094 9.8114V11.5H7.59888V9.8114H9.10094Z")
        }
    }

    /**
     * Build one 16x16 icon from web path data. The source paints with `currentColor`; here the
     * paths are black and the call site tints the whole vector, which is the same arrangement the
     * web gets from `currentColor` resolving against its CSS `color`.
     */
    private fun glyph(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 16f,
            viewportHeight = 16f,
        ).apply(block).build()

    private fun ImageVector.Builder.fill(pathData: String) {
        path(fill = SolidColor(Color.Black), pathBuilder = { svgPath(pathData) })
    }

    private fun ImageVector.Builder.stroke(pathData: String, width: Float) {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = { svgPath(pathData) },
        )
    }

    /**
     * Replay one SVG `d` attribute onto a [PathBuilder].
     *
     * The web source's paths use four commands — absolute move/line/close and the quadratic
     * shorthand — so that is all this parses. Anything else throws rather than silently drawing a
     * truncated icon, because a wrong permission glyph is worse than a build failure.
     */
    private fun PathBuilder.svgPath(d: String) {
        val tokens = d.split(' ', ',', '\n')
            .map(String::trim)
            .filter(String::isNotEmpty)
        var index = 0
        while (index < tokens.size) {
            val command = tokens[index]
            index++
            when (command) {
                "M", "L" -> {
                    moveOrLine(command, tokens[index].toFloat(), tokens[index + 1].toFloat())
                    index += 2
                }
                "Q" -> {
                    quadTo(
                        tokens[index].toFloat(),
                        tokens[index + 1].toFloat(),
                        tokens[index + 2].toFloat(),
                        tokens[index + 3].toFloat(),
                    )
                    index += 4
                }
                "Z" -> close()
                else -> error("unsupported SVG path command in permission glyph: $command")
            }
        }
    }

    private fun PathBuilder.moveOrLine(command: String, x: Float, y: Float) {
        if (command == "M") moveTo(x, y) else lineTo(x, y)
    }
}
