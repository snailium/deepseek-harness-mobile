package com.labteto.dshmobile.core.wire

/**
 * How a file preview asks the host for its pages.
 *
 * A preview reads a workspace file in pages of lines. The host owns the page size: it caps a page
 * by its configured `maxLines` and serves that when the request says nothing about a size. So the
 * client asks for no size at all, and takes what the host offers.
 *
 * The host caps a page by *bytes* as well as by lines, and answers `workspace-file/too-large`
 * when the page it would serve is over that cap. A file of long lines trips it — the first
 * thousand lines of a file with very long rows are more bytes than the host will put in one
 * page — and the answer is not an error the reader can act on. So the client asks again with
 * fewer lines, halving down to a floor, and remembers the size that worked.
 *
 * The ladder is here rather than in the composable because it is a decision with no UI in it, and
 * a decision that decides nothing is the kind that rots.
 */
object PreviewPaging {

    /**
     * The line count a page is first asked for when the host's own page size did not fit.
     *
     * Small enough that a file of long lines is very likely to fit, large enough that a page is
     * worth the round trip.
     */
    const val FALLBACK_LINES: Int = 1000

    /**
     * The smallest page the client will ask for.
     *
     * Below this a page stops being a page, and the reader is better served by the host's error
     * than by a thousand round trips. It is low enough that a file of very long lines still
     * fits: the host's cap is on the page's bytes, so a page of a few dozen long rows is
     * megabytes of text.
     */
    const val MIN_LINES: Int = 25

    /** The host's answer when the page it would serve is over its byte cap. */
    const val TOO_LARGE: String = "workspace-file/too-large"

    /**
     * The next page size to try after the host refused the current one, or null when there is
     * nothing left to try.
     *
     * A null [current] means the client has not named a size yet — the host is serving its own,
     * and the refusal was on bytes. The first explicit attempt is [FALLBACK_LINES]; after that the
     * size halves, because a page that was too big by a factor of two is usually fine at half.
     */
    fun nextPageSize(current: Int?): Int? = when {
        current == null -> FALLBACK_LINES
        current <= MIN_LINES -> null
        else -> (current / 2).coerceAtLeast(MIN_LINES)
    }

    /** Whether a host error means "this page is too big", as opposed to any other failure. */
    fun isTooLarge(code: String): Boolean = code == TOO_LARGE
}
