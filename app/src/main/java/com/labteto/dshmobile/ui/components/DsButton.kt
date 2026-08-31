package com.labteto.dshmobile.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.ui.theme.DsAnimations
import com.labteto.dshmobile.ui.theme.DsShapes
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType
import com.labteto.dshmobile.ui.theme.DshTheme
import com.labteto.dshmobile.ui.components.FeatherIcons

/** Button variants mirroring the harness primary/info/ghost/outline/danger palette. */
enum class DsButtonVariant { Primary, Info, Ghost, Outline, Danger }

/** Button sizes: [Large] is h48 hero, [Normal] is h36 r18, [Small] is h28 r14. */
enum class DsButtonSize { Large, Normal, Small }

/** Ink/ghost/outline button in the DeepSeek Harness style. */
@Composable
fun DsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: DsButtonVariant = DsButtonVariant.Primary,
    size: DsButtonSize = DsButtonSize.Normal,
    icon: ImageVector? = null,
) {
    val colors = DsTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()

    // Animate scale on press for tactile feedback
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) DsAnimations.Scale.pressed else DsAnimations.Scale.normal,
        animationSpec = DsAnimations.pressScale,
        label = "buttonScale"
    )

    val large = size == DsButtonSize.Large
    val normal = size == DsButtonSize.Normal
    val small = size == DsButtonSize.Small

    val fill: Color
    val content: Color
    when (variant) {
        DsButtonVariant.Primary -> { fill = colors.brandPrimary; content = colors.onBrandPrimary }
        DsButtonVariant.Info -> { fill = colors.buttonInfoFill; content = colors.onAccent }
        DsButtonVariant.Ghost -> { fill = Color.Transparent; content = colors.labelPrimary }
        DsButtonVariant.Outline -> { fill = Color.Transparent; content = colors.labelPrimary }
        DsButtonVariant.Danger -> { fill = colors.errorFill; content = Color.White }
    }

    val background = when {
        !enabled -> when (variant) {
            DsButtonVariant.Ghost, DsButtonVariant.Outline -> colors.bgLayer2
            else -> fill.copy(alpha = 0.4f)
        }
        // Ghost/outline hover keeps a flat transparent->hover fill; solid variants stay their fill
        hovered -> when (variant) {
            DsButtonVariant.Primary -> colors.primaryButtonGradientStart
            DsButtonVariant.Info -> colors.buttonInfoHover
            DsButtonVariant.Ghost, DsButtonVariant.Outline -> colors.hover
            DsButtonVariant.Danger -> lerp(colors.error, Color.Black, 0.15f)
        }
        else -> fill
    }
    val contentColor = if (enabled) content else content.copy(alpha = 0.5f)

    val btnHeight = if (large) 48.dp else if (normal) 36.dp else 28.dp
    val shape = if (small) DsShapes.buttonSmall else DsShapes.buttonCapsule
    val textStyle = if (small) DsType.small13Strong else DsType.std14Strong
    val iconSize = if (small) 14.dp else 16.dp
    val hPadding = if (small) 12.dp else 16.dp

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(btnHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        enabled = enabled,
        shape = shape,
        color = background,
        contentColor = contentColor,
        border = if (variant == DsButtonVariant.Outline) BorderStroke(1.dp, colors.borderL2) else null,
        interactionSource = interaction,
    ) {
        val row = @Composable {
            Row(
                modifier = Modifier.fillMaxHeight().padding(horizontal = hPadding),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize), tint = contentColor)
                    if (text.isNotEmpty()) Spacer(Modifier.width(6.dp))
                }
                if (text.isNotEmpty()) {
                    Text(text, style = textStyle, color = contentColor, maxLines = 1)
                }
            }
        }
        if (variant == DsButtonVariant.Primary && enabled) {
            // Gradient fill clipped to the shape, painted behind the ghost row content.
            Box(
                modifier = Modifier.fillMaxSize().clip(shape).background(
                    Brush.linearGradient(
                        listOf(colors.primaryButtonGradientStart, colors.primaryButtonGradientEnd),
                    ),
                ),
                contentAlignment = Alignment.Center,
            ) { row() }
        } else {
            row()
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun DsButtonPreview() {
    DshTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DsButton("Primary", {}, icon = FeatherIcons.Plus)
            DsButton("Info", {}, variant = DsButtonVariant.Info)
            DsButton("Ghost", {}, variant = DsButtonVariant.Ghost)
            DsButton("Outline", {}, variant = DsButtonVariant.Outline)
            DsButton("Danger", {}, variant = DsButtonVariant.Danger)
            DsButton("Disabled", {}, enabled = false)
            DsButton("Small", {}, size = DsButtonSize.Small)
            DsButton("Large", {}, size = DsButtonSize.Large)
        }
    }
}