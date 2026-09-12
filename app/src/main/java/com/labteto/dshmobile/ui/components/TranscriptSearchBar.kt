package com.labteto.dshmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.R
import com.labteto.dshmobile.ui.theme.DsShapes
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
 * The field is hand-rolled for the same reason the session list's is: Material3's `TextField`
 * enforces a 56dp minimum height, which is taller than the utility row it has to live in.
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
    modifier: Modifier = Modifier,
) {
    val colors = DsTheme.colors
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    // The bar appears because the reader asked for it, so it takes focus on arrival: a search
    // field that needs a second tap to start typing is a search field that looks broken.
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    // One pill holding the whole control — magnifier, field, counter, clear, arrows — rather than
    // a field with satellites beside it: the utility row already shares its width with the view
    // switcher, and separate 32dp targets would leave the field about one word wide on a phone.
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(DsShapes.pillFull)
            .background(colors.hoverSolid)
            .padding(start = DsSpacing.small, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            FeatherIcons.Search,
            contentDescription = null,
            tint = colors.labelTertiary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(DsSpacing.tiny))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
            textStyle = DsType.m3BodyMedium.copy(color = colors.labelPrimary),
            cursorBrush = SolidColor(colors.accent),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            // Dismissing the keyboard is the point: the hits are behind it, and stepping to one is
            // the next thing the reader does.
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            stringResource(R.string.chat_search_hint),
                            style = DsType.m3BodyMedium,
                            color = colors.labelTertiary,
                            maxLines = 1,
                        )
                    }
                    inner()
                }
            },
        )
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
    }
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
            .size(28.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) colors.labelSecondary else colors.labelTertiary.copy(alpha = 0.4f),
            modifier = Modifier.size(15.dp),
        )
    }
}
