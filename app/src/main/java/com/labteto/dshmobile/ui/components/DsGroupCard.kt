package com.labteto.dshmobile.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.ui.theme.DsShapes
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsTheme

/**
 * A titled settings-style group: section header over a card of rows.
 *
 * The card carries the same hairline [DsCard] does. On the gray `bgBase` the white fill already
 * separates the group from the page; the hairline keeps the group visible on any surface that
 * shares the white layer.
 */
@Composable
fun DsGroupCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = DsTheme.colors
    Column(modifier.fillMaxWidth().animateContentSize()) {
        // Group header: 13 semibold with breathing room above, like iOS inset-grouped sections.
        SectionHeader(
            title,
            modifier = Modifier.padding(start = DsSpacing.comfortable, bottom = DsSpacing.xsmall),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(DsShapes.groupCard)
                .background(colors.bgLayer1)
                .border(1.dp, colors.borderL2, DsShapes.groupCard)
                .padding(horizontal = DsSpacing.medium, vertical = DsSpacing.small),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.xsmall),
        ) {
            content()
        }
    }
}
