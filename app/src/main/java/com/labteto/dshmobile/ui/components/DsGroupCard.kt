package com.labteto.dshmobile.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.ui.theme.DsShapes
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsTheme

/**
 * A titled settings-style group: section header over a Material 3 [Card] of rows, flat and
 * hairline-bordered like [DsCard].
 */
@Composable
fun DsGroupCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = DsTheme.colors
    Column(modifier.fillMaxWidth().animateContentSize()) {
        SectionHeader(
            title,
            modifier = Modifier.padding(start = DsSpacing.comfortable, bottom = DsSpacing.xsmall),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = DsShapes.groupCard,
            colors = CardDefaults.cardColors(containerColor = colors.bgLayer1),
            border = BorderStroke(1.dp, colors.borderL2),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                Modifier.padding(horizontal = DsSpacing.medium, vertical = DsSpacing.small),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.xsmall),
            ) {
                content()
            }
        }
    }
}
