package com.labteto.dshmobile.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsTheme

/**
 * A titled settings-style group: section header over an elevated [DsCard] of rows.
 *
 * The header uses the M3 labelLarge voice so card groups read as designed sections rather
 * than grey captions over flat platters.
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
        DsCard(
            verticalArrangement = Arrangement.spacedBy(DsSpacing.xsmall),
            content = content,
        )
    }
}
