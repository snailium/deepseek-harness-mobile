package com.labteto.dshmobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.ui.theme.DsShapes
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsTheme

/**
 * An elevated surface: rounded, filled, softly shadowed - the redesign's replacement for the old
 * flat hairline-bordered plate.
 *
 * The card floats on surfaceRaised with a quiet ambient shadow and almost no border, so it reads
 * as a designed layer of depth rather than a flat platter pressed onto the grouped gray. Callers
 * more comfortable with a border can pass bordered = true to keep the hairline.
 */
@Composable
fun DsCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    bordered: Boolean = false,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(DsSpacing.tiny),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = DsTheme.colors
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(DsSpacing.elevationCard, DsShapes.groupCard)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = DsShapes.groupCard,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceRaised),
        border = if (bordered) BorderStroke(1.dp, colors.borderL2) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier.padding(horizontal = DsSpacing.medium, vertical = DsSpacing.small),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

/** Backwards-compatible name for [DsCard]: the app's one elevated grouped surface. */
@Composable
fun DsElevatedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    bordered: Boolean = false,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(DsSpacing.tiny),
    content: @Composable ColumnScope.() -> Unit,
) {
    DsCard(
        modifier = modifier,
        onClick = onClick,
        bordered = bordered,
        verticalArrangement = verticalArrangement,
        content = content,
    )
}