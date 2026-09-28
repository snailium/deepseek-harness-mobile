package com.labteto.dshmobile.ui.screens.settings

/**
 * `@deepseek-ai/dsh-client-ui-plan` → `ui-plan`.
 *
 * Ported from the harness's own `moduleShortName`, prefix for prefix, so the app's two plugin pages
 * and the harness's own list all name the same plugin the same way. Lives here rather than in either
 * page because both the bundle list and the built-in list print these names.
 *
 * The prefixes are tried in order and the longest wins on purpose: `dsh-client-ui-plan` must lose
 * `dsh-client-`, not `dsh-`, or it would print as `client-ui-plan`.
 */
internal fun moduleShortName(moduleName: String): String {
    val prefixes = listOf("cordis:", "cordis-plugin-", "dsh-host-", "dsh-client-", "dsh-")
    var name = moduleName.substringAfterLast('/')
    for (prefix in prefixes) name = name.removePrefix(prefix)
    return name.ifBlank { moduleName }
}
