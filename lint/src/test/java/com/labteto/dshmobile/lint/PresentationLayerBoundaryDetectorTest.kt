package com.labteto.dshmobile.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test

/**
 * The boundary check, tested in both directions.
 *
 * A detector that never fires passes every build and enforces nothing — which is exactly the
 * failure mode this whole rule exists to prevent, so the "it fires" case is the load-bearing test
 * and the "it stays quiet" case guards against it becoming noise people disable.
 */
class PresentationLayerBoundaryDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = PresentationLayerBoundaryDetector()

    override fun getIssues(): List<Issue> = listOf(PresentationLayerBoundaryDetector.ISSUE)

    /**
     * Pinned to the default test mode.
     *
     * `LintDetectorTest` otherwise re-runs every case under an "import aliases" variant, which adds
     * a second import line and so reports one more error than the source contains. The detector
     * behaves identically either way — the difference is the harness, not the check — so the mode
     * is pinned rather than the expectation being written to accommodate it.
     */
    override fun lint(): TestLintTask = super.lint()
        .allowMissingSdk()
        .testModes(com.android.tools.lint.checks.infrastructure.TestMode.DEFAULT)

    @Test
    fun testComponentReadingTheSessionStoreIsReported() {
        lint()
            .files(
                kotlin(
                    """
                    package com.labteto.dshmobile.data
                    class SessionStore
                    """,
                ).indented(),
                kotlin(
                    """
                    package com.labteto.dshmobile.ui.components
                    import com.labteto.dshmobile.data.SessionStore
                    fun Card(store: SessionStore) = store.toString()
                    """,
                ).indented(),
            )
            .run()
            .expectErrorCount(1)
            .expectContains("PresentationLayerBoundary")
    }

    @Test
    fun testThemeFileImportingTheStoreHelperIsReported() {
        lint()
            .files(
                kotlin(
                    """
                    package com.labteto.dshmobile.ui
                    fun rememberSessionStore(): String = "store"
                    """,
                ).indented(),
                kotlin(
                    """
                    package com.labteto.dshmobile.ui.theme
                    import com.labteto.dshmobile.ui.rememberSessionStore
                    fun spacing() = rememberSessionStore()
                    """,
                ).indented(),
            )
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun testScreenMayDependOnTheStoreBecauseItOwnsTheState() {
        lint()
            .files(
                kotlin(
                    """
                    package com.labteto.dshmobile.data
                    class SessionStore
                    """,
                ).indented(),
                kotlin(
                    """
                    package com.labteto.dshmobile.ui.screens.main
                    import com.labteto.dshmobile.data.SessionStore
                    fun Screen(store: SessionStore) = store.toString()
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    @Test
    fun testFullyQualifiedReferenceWithoutAnImportIsReported() {
        // The gap an import-only scan leaves: this line imports nothing, so a detector that only
        // looked at imports would report the file clean while the boundary is broken.
        lint()
            .files(
                kotlin(
                    """
                    package com.labteto.dshmobile.ui.screens.main
                    fun previewPath(value: String): String? = value
                    """,
                ).indented(),
                kotlin(
                    """
                    package com.labteto.dshmobile.ui.components
                    fun Markdown(raw: String) =
                        com.labteto.dshmobile.ui.screens.main.previewPath(raw)
                    """,
                ).indented(),
            )
            .run()
            .expectErrorCount(1)
            .expectContains("PresentationLayerBoundary")
    }

    @Test
    fun testResourcesAndCoreValueTypesStayAvailableToAComponent() {
        lint()
            .files(
                kotlin(
                    """
                    package com.labteto.dshmobile.core.wire.dto
                    data class TodoItem(val text: String)
                    """,
                ).indented(),
                kotlin(
                    """
                    package com.labteto.dshmobile.ui.components
                    import com.labteto.dshmobile.R
                    import com.labteto.dshmobile.core.wire.dto.TodoItem
                    fun Row(item: TodoItem, label: Int = R.string.app_name) = item.text + label
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }
}
