package com.labteto.dshmobile.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

/**
 * The registry AGP discovers through `META-INF/services`.
 *
 * `api` is pinned to the lint version this module compiles against (31.7.3, matching AGP 8.7.3);
 * a registry that advertises an API the host does not know is rejected at load time, which
 * surfaces as "the check silently did not run" rather than as an error — so it is pinned rather
 * than derived.
 */
class PresentationLayerIssueRegistry : IssueRegistry() {
    override val issues: List<Issue> = listOf(PresentationLayerBoundaryDetector.ISSUE)

    override val api: Int = CURRENT_API

    override val minApi: Int = 14

    override val vendor: Vendor = Vendor(
        vendorName = "DSH Mobile fork",
        identifier = "com.labteto.dshmobile.lint",
        feedbackUrl = "https://github.com/snailium/deepseek-harness-mobile/issues",
    )
}
