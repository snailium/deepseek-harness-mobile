package com.labteto.dshmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
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
 * The app's one search field: a 32dp pill holding a magnifier, the field, and whatever trailing
 * controls the caller supplies.
 *
 * **Why this is a component rather than a recipe.** Three places needed a search field — the
 * transcript finder, the session drawer, and the commands sheet — and each was written out by hand
 * from the same description. They drifted: the commands sheet ended up on a Material3 `TextField`,
 * which is a 56dp pill with an outline and a different placeholder weight, so it read as a
 * different control sitting in the same app. A shared component makes "the same" structural
 * instead of aspirational.
 *
 * **Why not Material3's `TextField`.** It enforces a 56dp minimum height — taller than the utility
 * row and the drawer's toolbar it has to live in — and its outline fights the flat fill the rest of
 * the chrome uses. `BasicTextField` has no decoration box at all, so the field is exactly as tall
 * as its content and the pill is drawn here.
 *
 * The caller owns the trailing controls through [trailing], because what belongs there genuinely
 * differs: the transcript finder needs a counter, a clear button and two step arrows; the drawer
 * and the commands sheet need only a close button. Keeping that slot open is what lets one
 * component serve all three without a parameter for each arrangement.
 *
 * @param query the current text.
 * @param onQueryChange called on every edit.
 * @param placeholder shown while [query] is empty; pass a localized string.
 * @param onImeSearch invoked when the keyboard's search key is pressed. Dismissing the keyboard is
 *   the usual job here — the results are behind it — so the default hides it.
 * @param autoFocus requests focus on first composition. Right for a field that only exists because
 *   the reader asked for it, wrong for one that is always on screen.
 * @param trailing controls drawn after the field, inside the pill. It is a `RowScope` receiver so
 *   a caller can `weight` its own content against the field.
 */
@Composable
internal fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
    onImeSearch: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = DsTheme.colors
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember(autoFocus) { FocusRequester() }

    if (autoFocus) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }

    Row(
        modifier = modifier
            .height(32.dp)
            .clip(DsShapes.pillFull)
            .background(colors.hoverSolid)
            // A 1dp hairline over the fill, not instead of it: the flat pill alone blends into the
            // sheet's own background and reads as a recessed area rather than an input. The border
            // is drawn after the clip so it follows the pill's corners exactly.
            .border(1.dp, colors.borderL2, DsShapes.pillFull)
            // 8dp leading, 2dp trailing: the magnifier needs room to breathe, while the trailing
            // controls are already 24dp targets with their own inset padding and would sit too far
            // from the edge at 8dp.
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
            keyboardActions = KeyboardActions(onSearch = {
                keyboard?.hide()
                onImeSearch?.invoke()
            }),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            placeholder,
                            style = DsType.m3BodyMedium,
                            color = colors.labelTertiary,
                            maxLines = 1,
                        )
                    }
                    inner()
                }
            },
        )
        trailing?.invoke(this)
    }
}

/**
 * The trailing close button every search field can carry: a 24dp target with a 13dp glyph.
 *
 * Here rather than in each caller because the three call sites had three slightly different
 * versions of it, which is how they drifted in the first place.
 */
@Composable
internal fun SearchFieldCloseButton(
    onClick: () -> Unit,
    contentDescription: String = stringResource(R.string.common_cancel),
) {
    val colors = DsTheme.colors
    Spacer(Modifier.width(DsSpacing.xsmall))
    Icon(
        FeatherIcons.X,
        contentDescription = contentDescription,
        tint = colors.labelTertiary,
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
            .padding(6.dp),
    )
}
