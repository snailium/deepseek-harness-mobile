package com.labteto.dshmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.ui.theme.DsShapes
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType
import com.labteto.dshmobile.ui.theme.DshTheme

/**
 * One changed file: its path, then its line counts.
 *
 * Shaped for the `workspace/changes` summary the harness serves per turn. It is a plain data
 * component — it takes values, not a store — so it can be previewed and so a rebase of the screen
 * that uses it cannot drag the session layer in behind it. That is the same boundary the
 * presentation-layer lint check enforces.
 */
data class ChangedFileRowModel(
    /**
     * What to show. The harness's `display` form: relative inside the session's working directory,
     * a `../` path for files above it, `~` under home. Already slash-separated, so it needs no
     * rewriting for a phone — and rewriting it would lose the distinction the harness drew.
     */
    val display: String,
    /** Lines added, or `null` when the harness could not count this file. */
    val added: Int?,
    /** Lines deleted, or `null` when the harness could not count this file. */
    val deleted: Int?,
    /**
     * Why the counts are absent, when they are.
     *
     * A binary or oversized file reports `0/0` at the source. Rendering those numbers would claim
     * "nothing changed" about a file the harness could not read, so the reason is carried instead
     * and shown in the slot the counts would have taken.
     */
    val uncountedLabel: String? = null,
)

/**
 * A turn's changed files, drawn as a code block.
 *
 * Deliberately the same construction as the transcript's fenced code block — the `codeBlockBg`
 * body, the `codeBlockBanner` header strip, `DsShapes.block` corners and a `borderL1` edge. Both
 * are framed content lifted out of the prose around them, and a second framing recipe for the same
 * job would read as a different kind of thing.
 *
 * A `null` [files] is not the same as an empty list: `null` means the host had no summary to serve,
 * which is normal for a transcript old enough that its session was disposed. An empty list would
 * mean the host answered and the turn changed nothing.
 */
@Composable
fun ChangedFilesCard(
    title: String,
    files: List<ChangedFileRowModel>?,
    modifier: Modifier = Modifier,
    /** Shown in the body when [files] is null. */
    unavailableLabel: String? = null,
    /** Shown under the list when the host's cap omitted entries; null hides it. */
    moreLabel: String? = null,
) {
    val colors = DsTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(DsShapes.block)
            .background(colors.codeBlockBg)
            .border(1.dp, colors.borderL1, DsShapes.block),
    ) {
        // The banner is the code block's, down to the mono caption: it is the strip that says what
        // the framed body is, and using the prose face here made the card look like a card and the
        // code block look like something else.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.codeBlockBanner)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = DsType.small13Strong.copy(
                    fontFamily = DsType.codeFont,
                    color = colors.labelCaption,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // The aggregate, when the rows carry counts. Right-aligned in the banner so the eye can
            // take in the size of the change without reading every row.
            val added = files?.sumOf { it.added ?: 0 }
            val deleted = files?.sumOf { it.deleted ?: 0 }
            if (files != null && files.isNotEmpty() && (added!! > 0 || deleted!! > 0)) {
                Text(
                    "+$added",
                    style = DsType.small13Strong.copy(fontFamily = DsType.codeFont),
                    color = colors.success,
                )
                Spacer(Modifier.width(DsSpacing.xsmall))
                Text(
                    "\u2212$deleted",
                    style = DsType.small13Strong.copy(fontFamily = DsType.codeFont),
                    color = colors.error,
                )
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp)) {
            when {
                files == null -> Text(
                    unavailableLabel.orEmpty(),
                    style = DsType.small13,
                    color = colors.labelTertiary,
                )
                files.isEmpty() -> Text(
                    // The host answered and the turn changed nothing. Distinct from "unavailable",
                    // and the caller supplies the wording for its own locale.
                    unavailableLabel.orEmpty(),
                    style = DsType.small13,
                    color = colors.labelTertiary,
                )
                else -> {
                    files.forEach { file ->
                        ChangedFileLine(file)
                    }
                    moreLabel?.let {
                        Text(
                            it,
                            style = DsType.small13,
                            color = colors.labelTertiary,
                            modifier = Modifier.padding(top = DsSpacing.tiny),
                        )
                    }
                }
            }
        }
    }
}

/**
 * One row of the card: path on the left, counts on the right.
 *
 * The path is mono, like the body of a code block — these are file paths, and the mono face is what
 * makes a column of them scannable. The counts use [DsType.codeFont] too, so `+12` and `−3` line up
 * between rows instead of drifting with proportional digits.
 *
 * Long paths truncate from the **left** (`…src/somefile.md`), not the right: the file name is what
 * identifies a change, and a default ellipsis would hide exactly that part while keeping the
 * directory prefix. [tailEllipsis] drops whole leading segments until the remainder fits in the
 * measured row width; the final segment (the name) is never cut — if even it overflows on its own,
 * Compose's right-ellipsis takes over and cuts inside the name.
 */
@Composable
private fun ChangedFileLine(file: ChangedFileRowModel) {
    val colors = DsTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            tailEllipsis(file.display),
            style = DsType.small13.copy(fontFamily = DsType.codeFont),
            color = colors.labelSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(DsSpacing.small))
        if (file.added != null && file.deleted != null) {
            DiffCount("+${file.added}", colors.success)
            Spacer(Modifier.width(DsSpacing.xsmall))
            DiffCount("\u2212${file.deleted}", colors.error)
        } else {
            Text(
                file.uncountedLabel.orEmpty(),
                style = DsType.small13.copy(fontFamily = DsType.codeFont),
                color = colors.labelTertiary,
                maxLines = 1,
            )
        }
    }
}

/**
 * One line count.
 *
 * Fixed-width and end-aligned: without a floor, a `+3` and a `+120` in the same column make the
 * minus signs wander, and the numbers stop being comparable at a glance.
 */
@Composable
private fun DiffCount(text: String, color: Color) {
    Text(
        text,
        style = DsType.small13.copy(fontFamily = DsType.codeFont),
        color = color,
        maxLines = 1,
        modifier = Modifier.widthIn(min = 30.dp),
    )
}

/**
 * Left-truncate a slash-separated path so the tail (the file name) survives.
 *
 * Compose's `TextOverflow.Ellipsis` cuts from the right, which hides exactly the part that
 * identifies a change — the name — and keeps the directory prefix. That is backwards for this
 * column: two rows both ending in `…/Foo.kt` would be indistinguishable by their visible text,
 * while two rows sharing a long common prefix are still told apart by the name. So we drop whole
 * leading segments until the remainder fits the available width, and prepend a single `…` to mark
 * the cut. If even the final segment alone overflows, we return it as-is and let Compose's own
 * right-ellipsis finish the job inside the name — at that point the path is so long that no
 * truncation strategy can save both ends.
 *
 * The budget is a **character count**, not pixels: the row renders in [DsType.codeFont], a
 * monospace face, where every character has the same advance. Counting characters is exact, pure
 * and testable without a layout pass — no density, no text-measurer, no composable state. The
 * number itself is calibrated against the row's geometry (card padding, gap to the counts, the
 * counts' 30dp floor) and rounded down so that a path at the budget never overflows; if a future
 * change tightens the row's layout, [MAX_PATH_CHARS] is the single knob to adjust.
 */
internal const val MAX_PATH_CHARS = 40

internal fun tailEllipsis(path: String): String {
    if (path.length <= MAX_PATH_CHARS) return path
    val segments = path.split('/')
    // Keep the longest suffix that fits under the budget once the leading `…` is accounted for.
    val budget = MAX_PATH_CHARS - 1
    var kept = segments.last()
    if (kept.length > budget) {
        // Even the name alone overflows: hand it to Compose's right-ellipsis rather than cut it
        // ourselves — a mid-name `…` is at least honest about where the cut landed.
        return kept
    }
    for (i in (segments.lastIndex - 1) downTo 0) {
        val candidate = "${segments[i]}/$kept"
        if (candidate.length <= budget) {
            kept = candidate
        } else {
            break
        }
    }
    return "…$kept"
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun ChangedFilesCardPreview() {
    DshTheme {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChangedFilesCard(
                title = "File changes in this turn",
                files = listOf(
                    ChangedFileRowModel("app/src/main/java/Foo.kt", added = 12, deleted = 3),
                    ChangedFileRowModel("core/src/main/kotlin/Bar.kt", added = 120, deleted = 0),
                    ChangedFileRowModel("res/icon.png", added = null, deleted = null, uncountedLabel = "binary"),
                    ChangedFileRowModel("res/huge.bin", added = null, deleted = null, uncountedLabel = "too large"),
                ),
                moreLabel = "3 more not shown",
            )
            ChangedFilesCard(
                title = "File changes in this turn",
                files = null,
                unavailableLabel = "File list no longer available",
            )
        }
    }
}
