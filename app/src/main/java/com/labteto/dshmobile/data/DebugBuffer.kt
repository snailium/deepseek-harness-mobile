package com.labteto.dshmobile.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * The on-device diagnostic buffer.
 *
 * The phone has no adb, so this ring is the logcat substitute: the session list's debug dialog
 * reads [snapshot] and its Copy button ships the text back to the developer. It is deliberately
 * small (a bounded deque, not a file) — a few hundred lines of `<what> -> <outcome>` is what a
 * diagnosis needs, and anything more would outlive the process that produced it anyway.
 *
 * Lines are appended with a `[HH:MM:SS]` prefix so two pastes from different sessions can be
 * interleaved by time. The buffer dies with the process; nothing here persists to disk.
 */
object DebugBuffer {

    /** Keep the newest [MAX_LINES] lines, capped at [MAX_BYTES] total (whichever binds first). */
    private const val MAX_LINES = 400
    private const val MAX_BYTES = 64 * 1024

    private val lines = ConcurrentLinkedDeque<String>()
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.US)

    /** Append one diagnostic line, dropping the oldest entries past the bounds. */
    @Synchronized
    fun append(line: String) {
        val stamped = "[${clock.format(Date())}] $line"
        lines.addLast(stamped)
        while (lines.size > MAX_LINES) lines.removeFirst()
        var size = 0
        for (l in lines) size += l.length + 1
        while (size > MAX_BYTES && lines.isNotEmpty()) {
            size -= (lines.removeFirst().length + 1)
        }
    }

    /** The buffer contents, oldest first — the text the dialog renders and Copy ships. */
    @Synchronized
    fun snapshot(): String = lines.joinToString("\n")

    /** Drop everything; called on a fresh connection generation so old hosts do not bleed in. */
    @Synchronized
    fun clear() {
        lines.clear()
    }
}
