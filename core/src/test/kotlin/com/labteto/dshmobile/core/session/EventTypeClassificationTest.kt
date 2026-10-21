package com.labteto.dshmobile.core.session

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Every durable event type the harness declares has to be one this client decided about.
 *
 * The harness generates its own vocabulary — `KNOWN_SESSION_EVENT_TYPES` in
 * `packages/core/session/src/known-event-types.ts`, written by `gen-persistence-catalog` and
 * verified fresh in its own CI — so the authoritative list is a file, not a guess. This test reads
 * it and compares it to [EventClassification] in both directions, which is the only way the
 * comparison is worth anything:
 *
 *  * a type the harness declares and this client has not classified would reach the transcript as
 *    a raw JSON row headed with its own event name, which is the compatibility contract for a type
 *    from the *future* but is just an oversight for one shipping today;
 *  * a type this client classifies that the harness no longer declares is a dead entry, and dead
 *    entries are how the real bug happened — the skip list still named `tool/code-dispatch` long
 *    after it was renamed `tool/ptc-dispatch`, so PTC dispatch metadata had been printing into the
 *    middle of people's transcripts, and nothing said a word.
 *
 * **The check never skips.** It used to: it read a harness checkout and, finding none, skipped —
 * which is how `job/registered` and `job/settled` reached a phone as raw JSON rows. The vocabulary
 * is therefore also kept as a pinned copy in the test resources
 * (`harness-event-vocabulary.txt`, refreshed by `scripts/refresh-event-vocabulary.py`), and the
 * comparison against that copy is unconditional. When a live harness *is* found — a source
 * checkout, or the installed `dsh-session` package — it is compared against the pinned copy too,
 * so a harness that has grown since the copy was taken fails here rather than on a phone.
 */
class EventTypeClassificationTest {

    /**
     * The `KNOWN_SESSION_EVENT_TYPES` members, read out of the generated file.
     *
     * Read lexically rather than by parsing TypeScript: the file is generated, so its shape is
     * stable, and every member sits alone on its line in quotes. Anything looser would match the
     * `MESSAGE_PROJECTION_EVENT_TYPES` set further down the same file, so the scan stops at the end
     * of the first `new Set([...])`.
     */
    private fun declaredTypes(source: File): Set<String> {
        val text = source.readText()
        val start = text.indexOf("KNOWN_SESSION_EVENT_TYPES")
        check(start >= 0) { "no KNOWN_SESSION_EVENT_TYPES in ${source.path}" }
        val open = text.indexOf("new Set([", start)
        val close = text.indexOf("])", open)
        check(open >= 0 && close > open) { "could not find the set literal in ${source.path}" }
        return Regex("'([^']+)'").findAll(text.substring(open, close))
            .map { it.groupValues[1] }
            .toSet()
    }

    /** The pinned copy of the harness's vocabulary. It ships with the client, so it is always there. */
    private fun pinnedVocabulary(): Set<String> {
        val stream = checkNotNull(javaClass.getResourceAsStream("/harness-event-vocabulary.txt")) {
            "the pinned event vocabulary is missing from the test resources"
        }
        val types = stream.bufferedReader().useLines { lines ->
            lines.map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toList()
        }
        check(types.size > 40) { "read only ${types.size} types from the pinned vocabulary" }
        return types.toSet()
    }

    /**
     * A live harness vocabulary, or null when there is none on this machine.
     *
     * Two sources, in order: a source checkout, whose file is the one the harness generates, and
     * the installed `@deepseek-ai/dsh-session` package, whose compiled file has the same shape.
     * The second is the one that matters in practice — a machine that runs the harness has the
     * package, and it is the package the phone is talking to.
     *
     * A machine often carries several harness versions at once, so the installed ones are ranked
     * by the package's own version and the newest wins. Comparing the pin against an old harness
     * would report types the harness has since dropped, which is the other half of the staleness
     * this test is about.
     */
    private fun liveVocabulary(): Set<String>? {
        System.getenv("DSH_SESSION_PKG")?.let { pkg ->
            val file = File(pkg, INSTALLED_PATH)
            if (file.isFile) return declaredTypes(file)
        }
        val checkout = listOfNotNull(
            System.getenv("DSH_HARNESS_SRC")?.let { File(it, CHECKOUT_PATH) },
            File("../deepseek-harness/$CHECKOUT_PATH"),
            File("../../deepseek-harness/$CHECKOUT_PATH"),
            File("G:/LAB/deepseek-harness/$CHECKOUT_PATH"),
        ).firstOrNull { it.isFile }
        if (checkout != null) return declaredTypes(checkout)

        val newest = installedPackages()
            .filter { File(it, INSTALLED_PATH).isFile }
            .maxByOrNull { versionKey(harnessVersion(it)) }
            ?: return null
        return declaredTypes(File(newest, INSTALLED_PATH))
    }

    /** The version an installed `dsh-session` package declares, or an empty string if it says none. */
    private fun harnessVersion(packageDir: File): String {
        val manifest = File(packageDir, "package.json")
        if (!manifest.isFile) return ""
        return runCatching {
            Json.parseToJsonElement(manifest.readText())
                .jsonObject["version"]?.jsonPrimitive?.content ?: ""
        }.getOrDefault("")
    }

    /**
     * A comparable key for a semver-ish version string.
     *
     * The numbers are zero-padded so they compare as numbers, because "0.10.0" is newer than
     * "0.9.0" and a plain string comparison says otherwise. A prerelease sorts below the same
     * release without one, which is what puts `0.2.1-rc.2` below `0.2.1` and above `0.2.1-rc.1`.
     */
    private fun versionKey(version: String): String {
        val core = version.substringBefore('-')
        val prerelease = version.substringAfter('-', "")
        val numbers = core.split('.').map { (it.toIntOrNull() ?: 0).toString().padStart(6, '0') }.toMutableList()
        while (numbers.size < 3) numbers.add("000000")
        return numbers.joinToString(".") + "|" + (if (prerelease.isEmpty()) "1" else "0" + prerelease)
    }

    /** The installed `dsh-session` packages on this machine, newest node version first. */
    private fun installedPackages(): List<File> {
        val home = System.getProperty("user.home") ?: return emptyList()
        val nvm = File(home, ".nvm/versions/node")
        val roots = buildList {
            if (nvm.isDirectory) {
                addAll(nvm.listFiles()?.sortedByDescending { it.name }?.toList() ?: emptyList())
            }
            add(File("/usr/lib/node_modules"))
            add(File("/usr/local/lib/node_modules"))
        }
        return roots.map { File(it, "lib/node_modules/@deepseek-ai/dsh-session") } +
            roots.map { File(it, "@deepseek-ai/dsh-session") } +
            // The usual place: `dsh-session` is a dependency of `dsh`, so npm nests it.
            roots.map { File(it, "lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai/dsh-session") }
    }

    @Test
    fun `every harness event type is classified, and nothing is classified that the harness dropped`() {
        val declared = pinnedVocabulary()

        val unclassified = (declared - EventClassification.CLASSIFIED).sorted()
        assertEquals(
            "the harness declares these and this client has not decided what to do with them",
            emptyList<String>(), unclassified,
        )

        // The legacy set is deliberately outside the harness's current vocabulary, so it is
        // excluded from the reverse check rather than being allowed to hide a stale entry.
        val stale = (EventClassification.CLASSIFIED - declared - EventClassification.FOLDED_LEGACY).sorted()
        assertEquals(
            "this client classifies these and the harness no longer declares them",
            emptyList<String>(), stale,
        )
    }

    @Test
    fun `the pinned vocabulary is not behind a live harness`() {
        val live = liveVocabulary() ?: return
        val pinned = pinnedVocabulary()

        val added = (live - pinned).sorted()
        assertEquals(
            "the harness now declares these and the pinned copy does not; refresh it with " +
                "scripts/refresh-event-vocabulary.py",
            emptyList<String>(), added,
        )
        val dropped = (pinned - live).sorted()
        assertEquals(
            "the pinned copy declares these and the live harness does not; refresh it with " +
                "scripts/refresh-event-vocabulary.py",
            emptyList<String>(), dropped,
        )
    }

    @Test
    fun `no type is in two minds at once`() {
        val folded = EventClassification.FOLDED
        val logOnly = EventClassification.LOG_ONLY
        val passthrough = EventClassification.PASSTHROUGH
        assertEquals("folded and log-only overlap", emptySet<String>(), folded intersect logOnly)
        assertEquals("folded and passthrough overlap", emptySet<String>(), folded intersect passthrough)
        assertEquals("log-only and passthrough overlap", emptySet<String>(), logOnly intersect passthrough)
    }

    private companion object {
        const val CHECKOUT_PATH = "packages/core/session/src/known-event-types.ts"
        const val INSTALLED_PATH = "lib/types/known-event-types.js"
    }
}
