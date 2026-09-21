package com.labteto.dshmobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.ui.theme.DsSpacing

/**
 * The chat surface's page column: the inset every screen-wide container shares.
 *
 * `DsSpacing.pageHorizontal` exists because each container used to pick its own horizontal inset
 * and the left edges stopped lining up. That fixed the *value* but not the *repetition*: the token
 * was still applied by hand at eleven call sites, so a new container could forget it and nothing
 * would say so. This is the token's structure — the padding is inside, and a caller that wants the
 * page column cannot accidentally omit it.
 *
 * Use it for anything that spans the screen width and holds transcript-level content: the dock
 * stack, the question and approval panels, the to-do strip, the composer card. A chip's inner
 * padding has no obligation to match the page margin, so nested surfaces keep choosing by amount.
 */
@Composable
fun PageColumn(
    modifier: Modifier = Modifier,
    /** Extra vertical padding above the column's own content; 0 for a flush block. */
    vertical: Dp = 0.dp,
    /** Gap between children; 0 when the children space themselves. */
    spacing: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DsSpacing.pageHorizontal, vertical = vertical),
        verticalArrangement = if (spacing > 0.dp) Arrangement.spacedBy(spacing) else Arrangement.Top,
        content = content,
    )
}

/**
 * The page inset as [PaddingValues], for the callers that need it as padding rather than as a
 * wrapper — a `LazyColumn`'s `contentPadding` cannot be expressed as a wrapping Column, because the
 * list has to scroll *under* the inset rather than be inset from outside it.
 */
val pageContentPadding: PaddingValues
    get() = PaddingValues(horizontal = DsSpacing.pageHorizontal)
