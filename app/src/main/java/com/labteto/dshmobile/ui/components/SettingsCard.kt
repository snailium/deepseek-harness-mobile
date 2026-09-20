package com.labteto.dshmobile.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
 * The 8dp around the title is applied here rather than inside [SectionHeader]: that one is a shared
 * row (connect, pair, docks, trajectory) whose callers choose their own spacing, and baking it in
 * would double every other page's gaps. With [collapsible] the whole title row toggles the body —
 * the chevron is the affordance, and it is a plain arrow rather than the list's right-pointing one,
 * because this folds a block instead of expanding a group in a tree.
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
        // The header row, with the card's own 8dp breathing room on both sides.
        val action: (() -> Unit)? = if (collapsible) { { expanded = !expanded } } else { null }
        // Collapsible: the whole row is the toggle, not just the chevron — a 12sp glyph is too small
        // a target for the thumb that is already on the title.
        if (collapsible) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = DsSpacing.small)
                    .clickable(onClick = { expanded = !expanded }),
            ) {
                SectionHeader(title, action = if (expanded) "▴" else "▾", onAction = action)
            }
        } else {
            Column(Modifier.padding(vertical = DsSpacing.small)) {
                SectionHeader(title)
            }
        }

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
