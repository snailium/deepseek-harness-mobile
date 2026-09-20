package com.labteto.dshmobile.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.labteto.dshmobile.ui.theme.DsShapes
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsTheme

/**
 * One settings group as a raised card, so groups read as blocks rather than a running list.
 *
 * The title carries the group's 8dp breathing room on both sides; the card itself keeps only its
 * inner padding. With [collapsible] the whole title row toggles the body — the chevron is the
 * affordance, and it is a plain arrow rather than the list's right-pointing one, because this folds
 * a block instead of expanding a group in a tree.
 */
@Composable
fun SettingsCard(
    title: String,
    collapsible: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = DsTheme.colors
    var expanded by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        SectionHeader(
            title = title,
            action = if (collapsible) (if (expanded) "▴" else "▾") else null,
            onAction = if (collapsible) { { expanded = !expanded } } else { null },
        )
        AnimatedVisibility(visible = expanded || !collapsible) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(DsShapes.block)
                    .background(colors.bgLayer1)
                    .padding(horizontal = DsSpacing.medium, vertical = DsSpacing.small),
            ) {
                content()
            }
        }
    }
}
