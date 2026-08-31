package com.labteto.dshmobile.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsTheme

/**
 * Icon button with a guaranteed 48dp touch target, now a Material 3 [IconButton] (state-layer
 * ripple instead of the old hover fill).
 *
 * [mirrorForRtl] flips back/forward glyphs with the reading direction, which Material's
 * auto-mirrored icons handled implicitly.
 */
@Composable
fun DsIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = DsTheme.colors.labelSecondary,
    iconSize: Dp = 20.dp,
    /** True for back/forward glyphs, which must flip with the reading direction (RTL). */
    mirrorForRtl: Boolean = false,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    IconButton(
        onClick = onClick,
        modifier = modifier.size(DsSpacing.touchTarget),
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = tint,
            disabledContentColor = tint.copy(alpha = 0.4f),
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer { scaleX = if (mirrorForRtl && rtl) -1f else 1f },
        )
    }
}
