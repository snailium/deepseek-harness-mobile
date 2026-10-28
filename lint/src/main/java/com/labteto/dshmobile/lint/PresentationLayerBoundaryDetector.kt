package com.labteto.dshmobile.lint

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TextFormat
import com.android.tools.lint.client.api.UElementHandler
import org.jetbrains.uast.UImportStatement

/**
 * Keeps the presentation layer from reaching the data layer.
 *
 * See `lint/build.gradle.kts` for why this exists. The short version: this fork keeps its visual
 * divergence in `ui/theme` and `ui/components` so an upstream merge can rebase it without having
 * to judge, hunk by hunk, which half of a change is functional and which is visual. That only
 * holds while those packages cannot pull in session state, and "cannot" has to mean mechanically.
 *
 * A composable that needs session state takes it as a parameter. That is not merely tidier — it is
 * what makes the component unable to absorb a functional change in the first place.
 */
class PresentationLayerBoundaryDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UImportStatement::class.java)



    /**
     * The second pass: fully-qualified references, which the import scan cannot see.
     *
     * `com.labteto.dshmobile.ui.screens.main.previewPath(raw)` written inline imports nothing, so an
     * import-only check reports clean while the boundary is broken. Scanning the file's text as
     * well is what makes the rule hold against code that is written to dodge it, not merely code
     * that is careless.
     */
    override fun afterCheckFile(context: com.android.tools.lint.detector.api.Context) {
        val file = context.file
        if (!isGuarded(file.path.replace('\\', '/'))) return
        // The qualified pass exists to catch references the import scan cannot see. Anything that
        // *is* imported is already reported by the UAST handler, and `afterCheckFile` runs before
        // that handler, so the imported names are read off the source here rather than collected
        // from a shared map — otherwise one broken import is reported twice and the count stops
        // meaning "one problem".
        // `Context.contents` is private in this lint version and the UAST handlers only see
        // imports, so the qualified pass reads the file itself. Cheap: it only runs for the two
        // guarded directories.
        val source = try {
            java.io.File(file.path).readText()
        } catch (e: java.io.IOException) {
            return
        }
        // Strip import lines before scanning: the regex would otherwise match the import's own
        // text, and every import is already reported by the UAST handler above. What is left is
        // genuinely inline usage, which is the whole point of this pass.
        val body = source.lineSequence()
            .filterNot { it.trimStart().startsWith("import ") }
            .joinToString("\n")
        qualifiedViolations(body)
            .forEach { (reference, reason) ->
            context.report(
                ISSUE,
                com.android.tools.lint.detector.api.Location.create(file),
                "`${context.file.name}` may not reference `$reference`: $reason",
            )
        }
    }

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitImportStatement(node: UImportStatement) {
            // `UImportStatement` exposes the imported name through its reference rather than as a
            // string, so resolve it here — `asSourceString()` on the reference is the FQN for a
            // plain import and includes the `.*` for an on-demand one, which the prefix match below
            // handles either way.
            val imported = node.importReference?.asSourceString()?.removeSuffix(".*") ?: return
            val violation = violationFor(imported) ?: return
            if (!isGuarded(context.file.path.replace('\\', '/'))) return

            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "`${context.file.name}` may not depend on `$imported`: $violation",
            )
        }
    }

    /**
     * Fully-qualified references to a forbidden package, found by walking the file's own text.
     *
     * A regex over the source is deliberate here: the reference is a *name*, not a resolved symbol,
     * and asking UAST to resolve it would mean compiling the very code the check is meant to
     * reject. The pattern requires a following identifier character, so a bare package prefix in a
     * comment or a string does not trip it — and the guarded packages are named in full rather than
     * matched loosely.
     */
    private fun qualifiedViolations(source: String): List<Pair<String, String>> {
        val pattern = Regex(
            "com\\.labteto\\.dshmobile\\.(data|di|notify|connection)\\.[A-Za-z_]|" +
                "com\\.labteto\\.dshmobile\\.ui\\.(media|screens)\\.[A-Za-z_]",
        )
        return pattern.findAll(source)
            .map { it.value.removeSuffix(".").let { hit -> hit to (violationFor(hit) ?: "") } }
            .filter { it.second.isNotEmpty() }
            .toList()
    }

    /** The reason [imported] is off-limits to the presentation layer, or null when it is fine. */
    private fun violationFor(imported: String): String? = when {
        imported.startsWith("com.labteto.dshmobile.data.") ->
            "`data` is the session/state layer; take what you need as a parameter instead"

        // `components` may not reach *up* into a screen either: a shared component that depends on
        // one screen's helper is not shared, it is that screen's code in the wrong directory — and
        // it drags the screen's whole dependency graph along with it when a merge rebases.
        imported.startsWith("com.labteto.dshmobile.ui.screens.") ->
            "a component must not depend on a screen; move the helper down into `components`"

        imported.startsWith("com.labteto.dshmobile.ui.media.") ->
            "`ui/media` resolves attachments through the session store; pass the resolved state in"

        imported == "com.labteto.dshmobile.ui.rememberSessionStore" ->
            "the store is session state; a component that reads it cannot be previewed or rebased"

        imported.startsWith("com.labteto.dshmobile.di.") ->
            "injected dependencies belong to the layer that owns the state"

        imported.startsWith("com.labteto.dshmobile.notify.") ->
            "notifications are application behaviour, not presentation"

        imported.startsWith("com.labteto.dshmobile.connection.") ->
            "connection state belongs to the data layer; pass a value down"

        imported.startsWith("dagger.") || imported.startsWith("javax.inject.") ->
            "injection is wiring, not presentation"

        else -> null
    }

    /**
     * Whether [filePath] sits in a guarded package.
     *
     * Matched on the path rather than the declared package because lint hands us files, and a file
     * whose `package` line disagrees with its directory is a problem this check is not about.
     */
    private fun isGuarded(filePath: String): Boolean =
        filePath.contains("/ui/theme/") || filePath.contains("/ui/components/")

    companion object {
        /** `import a.b.C` — captures `a.b.C`, ignoring an `as` alias and a trailing `.*`. */
        private val IMPORT_LINE = Regex(
            "^\\s*import\\s+([A-Za-z_][A-Za-z0-9_.]*)",
            RegexOption.MULTILINE,
        )

        private val IMPLEMENTATION = Implementation(
            PresentationLayerBoundaryDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "PresentationLayerBoundary",
            briefDescription = "Presentation layer must not depend on the data layer",
            explanation = """
                `ui/theme` and `ui/components` hold this fork's visual divergence. They stay \
                mergeable across upstream releases only while they cannot pull in session state — \
                a component that reads the store is a component that can silently absorb a \
                functional change, which is exactly how the fork became impossible to split by \
                file.

                Take the value you need as a parameter. `R`, `core.*` DTOs and pure helpers such \
                as the Markdown parser remain available.
            """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        /** Convenience for tests and for reading the message in a readable form. */
        fun describe(): String = ISSUE.getExplanation(TextFormat.TEXT)
    }
}
