package com.labteto.dshmobile.core.wire.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One turn's changed files, as `GET /api/changes.summary?sessionId=…&seq=…` serves them.
 *
 * Not an RPC. The web client reads this over an authenticated GET route registered as an exact
 * fetch route, the same shape as `session.export` — the summary lives on the Host only while its
 * Session does, and the `workspace/changes` durable event carries nothing but its `turn`.
 *
 * Field notes are the harness's own; the shapes are mirrored rather than guessed so a future
 * divergence shows up as a decode failure here instead of a wrong-looking screen.
 */
@Serializable
data class WorkspaceChangesSummary(
    /** The turn whose file changes this summary describes. */
    @SerialName("turn") val turn: Int,
    /** Changed files in `display` order, capped at the plugin's `maxFiles`. */
    @SerialName("files") val files: List<WorkspaceChangedFile> = emptyList(),
    /**
     * Complete changed-file count, **including** files the cap omitted.
     *
     * So `total > files.size` means the list is truncated, and the row has to say so rather than
     * quietly presenting a partial list as the whole change set.
     */
    @SerialName("total") val total: Int = 0,
    /** Lines added over every changed file, including those the cap omitted. */
    @SerialName("added") val added: Int = 0,
    /** Lines deleted over every changed file, including those the cap omitted. */
    @SerialName("deleted") val deleted: Int = 0,
)

/** One file changed during a turn. */
@Serializable
data class WorkspaceChangedFile(
    /**
     * Path relative to the session's working directory, or an absolute Host path outside it.
     *
     * [display] is what to show; this is what to act on.
     */
    @SerialName("path") val path: String,
    /**
     * Sort key and label: the relative path inside the working directory, a `../` path for
     * repository files above it, a `~` path under the home directory, else the absolute path.
     * Always slash-separated, so it is safe to render as-is.
     */
    @SerialName("display") val display: String,
    /** Lines added; zero for a binary or oversized file. */
    @SerialName("added") val added: Int = 0,
    /** Lines deleted; zero for a binary or oversized file. */
    @SerialName("deleted") val deleted: Int = 0,
    /**
     * Git reported the file as binary, or a captured side holds a NUL byte.
     *
     * Carried because the counts are meaningless in that case: a `+0 −0` row next to a real
     * `+12 −3` row implies "no change" when what it means is "not measurable this way".
     */
    @SerialName("binary") val binary: Boolean = false,
    /**
     * A captured side exceeded the plugin's `maxFileBytes`.
     *
     * The file is listed without counts or a comparison, for the same reason as [binary].
     */
    @SerialName("oversized") val oversized: Boolean = false,
) {
    /**
     * Whether this row's line counts carry information.
     *
     * Both flags suppress the counts at the source (the harness sends `0`/`0`), so a renderer that
     * ignored them would draw a confident "no lines changed" for a file it simply could not read.
     */
    val hasCounts: Boolean get() = !binary && !oversized
}
