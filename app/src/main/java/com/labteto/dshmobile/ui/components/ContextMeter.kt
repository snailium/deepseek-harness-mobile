package com.labteto.dshmobile.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.R
import com.labteto.dshmobile.core.wire.dto.ContextBreakdownView
import com.labteto.dshmobile.core.wire.dto.ContextPressureView
import com.labteto.dshmobile.ui.theme.Ds
import com.labteto.dshmobile.ui.theme.DsAnimations
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType

/**
 * How full the model's context window is, split by what is filling it.
 *
 * The harness keeps this next to the composer rather than in a panel, and that placement is the
 * point: context pressure is something you act on while writing the next message, not something
 * you go looking for after a turn fails.
 */

/**
 * The compact ring trigger — a 28dp round button carrying the occupancy arc (web parity: the
 * desktop's `ContextMeter` is a 14px ring beside the send button). Tapping opens
 * [ContextDetailSheet] with the per-segment reading. Renders nothing until a provider reports both
 * pressure and a route capacity, exactly like the web.
 */
@Composable
fun ContextRing(
    breakdown: ContextBreakdownView?,
    pressure: ContextPressureView?,
    modifier: Modifier = Modifier,
) {
    val colors = DsTheme.colors
    val ratio = pressure?.usedRatio ?: return
    var open by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(colors.hoverSolid)
            .border(1.dp, colors.borderL2, CircleShape)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.chat_context_window),
                onClick = { open = true },
            ),
        contentAlignment = Alignment.Center,
    ) {
        ContextRingArc(ratio, Modifier.size(14.dp))
    }

    if (open) {
        ContextDetailSheet(
            breakdown = breakdown,
            pressure = pressure,
            onDismiss = { open = false },
        )
    }
}

/** The 14dp occupancy arc: a track circle with a round-capped fill sweeping from the top. */
@Composable
private fun ContextRingArc(ratio: Float, modifier: Modifier) {
    val colors = DsTheme.colors
    val animated by animateFloatAsState(
        targetValue = ratio,
        animationSpec = DsAnimations.fade,
        label = "contextPressure",
    )
    Canvas(modifier = modifier) {
        // Web geometry: 14px viewBox with a 2px stroke.
        val strokePx = size.width * 2f / 14f
        val radius = (size.width - strokePx) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            color = colors.borderL3,
            radius = radius,
            center = center,
            style = Stroke(width = strokePx),
        )
        if (animated > 0f) {
            // Sweep clockwise from 12 o'clock: start at -90 degrees.
            drawArc(
                color = colors.labelSecondary,
                startAngle = -90f,
                sweepAngle = animated * 360f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }
    }
}

/**
 * The expanded reading: the occupancy headline with used/window figures, the segmented bar, and a
 * row per composition part (system prompt, tools, conversation) with its token share. Mirrors the
 * web's click-open panel (`ContextMeter.module.css`: 264px plate, 12px padding, 4px bar).
 */
@Composable
fun ContextDetailSheet(
    breakdown: ContextBreakdownView?,
    pressure: ContextPressureView?,
    onDismiss: () -> Unit,
) {
    val colors = DsTheme.colors
    val ratio = pressure?.usedRatio
    val percent = (ratio ?: 0f).times(100f).toInt()
    val usedTokens = pressure?.projectedTokens ?: pressure?.pressureTokens
    val window = pressure?.contextWindow
    val total = breakdown?.total

    DsBottomSheet(
        title = stringResource(R.string.chat_context_window),
        onDismiss = onDismiss,
    ) {
        // Same breathing room as the attachment sheet: the sheet's own 8dp gap puts the headline
        // directly on top of the figures and reads as one block.
        Spacer(Modifier.height(DsSpacing.small))
        if (ratio != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.chat_context_used, percent),
                    style = DsType.std14Strong,
                    color = colors.labelPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (usedTokens != null && window != null) {
                    Text(
                        stringResource(
                            R.string.chat_context_figures,
                            formatTokens(usedTokens),
                            formatTokens(window),
                        ),
                        style = DsType.small13,
                        color = colors.labelSecondary,
                    )
                }
            }
        }

        // The segmented bar: one segment per composition part in share order, or a single
        // accent fill when the harness reports no breakdown.
        val segments = if (total == null || total <= 0) {
            listOf(1f to colors.accent)
        } else {
            listOf(
                breakdown.systemTokens.toFloat() / total to Ds.MeterSystem,
                breakdown.toolsTokens.toFloat() / total to Ds.MeterTools,
                breakdown.messageTokens.toFloat() / total to Ds.MeterMessages,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(colors.bgModulePlatform),
            horizontalArrangement = Arrangement.Start,
        ) {
            segments.forEach { (share, color) ->
                if (share > 0f) {
                    Box(Modifier.weight(share).height(4.dp).background(color))
                }
            }
        }

        if (breakdown != null && (total ?: 0L) > 0) {
            ContextRow(stringResource(R.string.chat_context_system), Ds.MeterSystem, breakdown.systemTokens)
            ContextRow(stringResource(R.string.chat_context_tools), Ds.MeterTools, breakdown.toolsTokens)
            ContextRow(stringResource(R.string.chat_context_messages), Ds.MeterMessages, breakdown.messageTokens)
        }
    }
}

@Composable
private fun ContextRow(label: String, swatch: Color, tokens: Long) {
    val colors = DsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(swatch))
            Spacer(Modifier.width(6.dp))
            Text(label, style = DsType.small13, color = colors.labelSecondary)
        }
        Text(
            "~${formatTokens(tokens)}",
            style = DsType.small13,
            color = colors.labelPrimary,
        )
    }
}

/**
 * Compact token count, the web's `formatTokens`: raw below 1k, one decimal under 100 (K/M),
 * rounded beyond — `4.2K`, `128K`, `1.3M`. The K/M suffix is localized through strings.xml so a
 * locale can reword it without touching code.
 */
@Composable
private fun formatTokens(value: Long): String {
    val scaled = { candidate: Double ->
        if (candidate >= 100) Math.round(candidate).toString() else (Math.round(candidate * 10) / 10.0).toString()
    }
    return when {
        value < 1_000L -> value.toString()
        value < 1_000_000L -> stringResource(R.string.chat_context_tokens_thousand, scaled(value.toDouble() / 1e3))
        else -> stringResource(R.string.chat_context_tokens_million, scaled(value.toDouble() / 1e6))
    }
}

/**
 * The expanded reading used in the details panel: the same bar plus a labelled legend.
 */
@Composable
fun ContextMeter(
    breakdown: ContextBreakdownView?,
    pressure: ContextPressureView?,
    modifier: Modifier = Modifier,
    /** Fixed bar width in dp; null lets the caller's modifier size it (the detail reading). */
    barWidth: Int? = 44,
) {
    val colors = DsTheme.colors
    val ratio = pressure?.usedRatio ?: return
    val animated by animateFloatAsState(
        targetValue = ratio,
        animationSpec = DsAnimations.fade,
        label = "contextPressure",
    )
    val total = breakdown?.total?.takeIf { it > 0 }
    // Without a breakdown the bar still tells you how full the window is; with one it also says
    // what is filling it, which is what turns "compact soon" into "compact the messages".
    val segments = if (total == null) {
        listOf(1f to colors.accent)
    } else {
        listOf(
            breakdown.systemTokens.toFloat() / total to Ds.MeterSystem,
            breakdown.toolsTokens.toFloat() / total to Ds.MeterTools,
            breakdown.messageTokens.toFloat() / total to Ds.MeterMessages,
        )
    }

    Canvas(
        modifier = modifier
            .then(if (barWidth != null) Modifier.width(barWidth.dp) else Modifier)
            .height(6.dp)
            .clip(CircleShape)
            .background(colors.bgModulePlatform),
    ) {
        var x = 0f
        val filled = size.width * animated
        segments.forEach { (share, color) ->
            val segmentWidth = filled * share
            if (segmentWidth > 0f) {
                drawRect(
                    color = color,
                    topLeft = Offset(x, 0f),
                    size = Size(segmentWidth, size.height),
                )
                x += segmentWidth
            }
        }
    }
}

@Composable
private fun ContextMeterLegendRow(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = DsType.caption11, color = DsTheme.colors.labelTertiary)
    }
}

/** The expanded reading used in the details panel: the same bar plus a labelled legend. */
@Composable
fun ContextMeterDetail(
    breakdown: ContextBreakdownView?,
    pressure: ContextPressureView?,
    modifier: Modifier = Modifier,
) {
    val colors = DsTheme.colors
    val ratio = pressure?.usedRatio
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (ratio != null) {
            Text(
                stringResource(R.string.chat_context_used, (ratio * 100).toInt()),
                style = DsType.caption11,
                color = colors.labelSecondary,
            )
        }
        ContextMeter(breakdown, pressure, Modifier.fillMaxWidth(), barWidth = null)
        if (breakdown != null && breakdown.total > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ContextMeterLegendRow(stringResource(R.string.chat_context_system), Ds.MeterSystem)
                ContextMeterLegendRow(stringResource(R.string.chat_context_tools), Ds.MeterTools)
                ContextMeterLegendRow(stringResource(R.string.chat_context_messages), Ds.MeterMessages)
            }
        }
    }
}
