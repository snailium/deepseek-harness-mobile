package com.labteto.dshmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.R
import com.labteto.dshmobile.ui.theme.DsAnimations
import com.labteto.dshmobile.ui.theme.DsShapes
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType
import com.labteto.dshmobile.ui.theme.DshTheme

/**
 * Right-aligned user message bubble: solid `userBubble` fill with white text, r22, 17/25 text.
 *
 * This is the iOS 18 Messages arrangement — the user's side is a filled blue bubble, white text,
 * no border — and it is the one thing that tells the two sides of the conversation apart at a
 * glance (the assistant renders container-less in the harness web UI, so this bubble used to be
 * the *only* visual distinction and still had to carry it with a tinted fill and a hairline).
 * With a solid fill the border is unnecessary; the width cap keeps a short message a narrow pill
 * hugging the right margin rather than a full-width band that looks like more prose.
 *
 * The cap mirrors the harness's `max-width: min(525px, 82%)`, which is why this measures its parent
 * rather than hardcoding a dp: a flat 320dp was most of a phone's width and none of a tablet's.
 */
@Composable
fun UserBubble(text: String, modifier: Modifier = Modifier) {
    val colors = DsTheme.colors
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(
            text,
            style = DsType.bubbleText,
            color = colors.onAccent,
            modifier = Modifier
                .widthIn(max = minOf(525.dp, maxWidth * 0.82f))
                .shadow(DsSpacing.elevationQuiet, DsShapes.bubble)
                .background(
                    Brush.linearGradient(listOf(colors.userBubble, colors.userBubbleHighlight)),
                    DsShapes.bubble,
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/** 24dp disclosure row for collapsible thinking blocks; muted, never italic. */
@Composable
fun ThinkingRow(
    summary: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    streaming: Boolean = false,
) {
    val colors = DsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 24.dp)
            .clip(DsShapes.row)
            .clickable(onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (streaming) {
            StateDot(StateDotState.Running, size = 8.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            summary ?: stringResource(R.string.chat_thinking),
            style = DsType.mdSmall.copy(color = colors.labelTertiary),
            color = colors.labelTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val rotation by animateFloatAsState(
            targetValue = if (expanded) 180f else 0f,
            animationSpec = DsAnimations.chevron,
            label = "thinkingChevron",
        )
        Icon(
            FeatherIcons.ChevronDown,
            contentDescription = stringResource(R.string.chat_thinking),
            tint = colors.labelTertiary,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer { rotationZ = rotation },
        )
    }
}

/** How a connection notice should read: a hard failure, or a recovery in progress. */
enum class BannerTone { Error, Info }

/**
 * Full-width top strip announcing a connection problem.
 *
 * Error is the red failure strip; Info is the calm "reconnecting" notice — the two used to share
 * the red, which made a routine backoff retry look like a crash. [actionLabel]/[onAction] add a
 * right-aligned Retry for the failure case; the recovery case has nothing to press.
 */
@Composable
fun ConnectionBanner(
    message: String,
    tone: BannerTone = BannerTone.Error,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = DsTheme.colors
    val background = if (tone == BannerTone.Error) colors.errorFill else colors.accentTertiary
    val foreground = if (tone == BannerTone.Error) Color.White else colors.labelSecondary
    // A warning triangle on the calm "reconnecting" notice was the wrong glyph for the wrong
    // message; the error strip keeps the warning, the recovery notice gets a refresh.
    val glyph = if (tone == BannerTone.Error) FeatherIcons.AlertTriangle else FeatherIcons.RefreshCw
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            glyph,
            contentDescription = null,
            tint = foreground,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            message,
            style = DsType.small13,
            color = foreground,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                actionLabel,
                style = DsType.small13Strong,
                color = foreground,
                modifier = Modifier
                    .clip(DsShapes.row)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun ChatComponentsPreview() {
    DshTheme {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            UserBubble("Build the shared component library.")
            ThinkingRow("Working through the diff…", expanded = false, onToggle = {}, streaming = true)
            ConnectionBanner("Connection lost — retrying…")
        }
    }
}
