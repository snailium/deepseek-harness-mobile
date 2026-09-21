package com.labteto.dshmobile.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.R
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType

/**
 * In-transcript search: the field, the `n / m` counter, and the two step arrows.
 *
 * It sits beside the Chat / Trajectory switcher rather than inside either view because the query
 * belongs to the session, not to the view — stepping through hits should work the same whether the
 * reader is looking at the conversation or the ledger.
 *
 * The pill itself is [SearchField]; this adds the finder-specific trailing controls. One pill holds
 * the whole control — magnifier, field, counter, clear, arrows — rather than a field with
 * satellites beside it: the utility row already shares its width with the view switcher, and
 * separate 32dp targets would leave the field about one word wide on a phone.
 *
 * The arrows wrap around at both ends and go dead when nothing matches, so a tap is never a no-op
 * that looks like a broken button.
 */
@Composable
internal fun TranscriptSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    /** 1-based position of the highlighted hit; ignored when [matchCount] is zero. */
    matchPosition: Int,
    matchCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    /** Called when the trailing × is tapped; null hides the button (permanent-resident mode). */
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = DsTheme.colors

    SearchField(
        query = query,
        onQueryChange = onQueryChange,
        placeholder = stringResource(R.string.chat_search_hint),
        modifier = modifier,
        trailing = {
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(DsSpacing.xsmall))
                Text(
                    stringResource(R.string.chat_search_counter, matchPosition, matchCount),
                    style = DsType.caption11,
                    color = if (matchCount == 0) colors.labelTertiary else colors.labelSecondary,
                    maxLines = 1,
                )
                Spacer(Modifier.width(DsSpacing.xsmall))
                Icon(
                    FeatherIcons.X,
                    contentDescription = stringResource(R.string.chat_search_clear),
                    tint = colors.labelTertiary,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { onQueryChange("") }
                        .padding(6.dp),
                )
            }
            StepArrow(
                icon = FeatherIcons.ChevronUp,
                contentDescription = stringResource(R.string.chat_search_previous),
                enabled = matchCount > 0,
                onClick = onPrevious,
            )
            StepArrow(
                icon = FeatherIcons.ChevronDown,
                contentDescription = stringResource(R.string.chat_search_next),
                enabled = matchCount > 0,
                onClick = onNext,
            )
            if (onClose != null) {
                SearchFieldCloseButton(
                    onClick = onClose,
                    contentDescription = stringResource(R.string.chat_search_close),
                )
            }
        },
    )
}

/** One step arrow: a 28dp target so it can be hit, a 15dp glyph so it stays quiet. */
@Composable
private fun StepArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = DsTheme.colors
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) colors.labelSecondary else colors.labelTertiary.copy(alpha = 0.4f),
            modifier = Modifier.size(13.dp),
        )
    }
}
