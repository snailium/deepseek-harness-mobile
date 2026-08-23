package com.labteto.dshmobile.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp

/**
 * The app's single text-field voice: an M3 [TextField] with the Ds field colours and an
 * optional explicit [minHeight].
 *
 * The one reason the composer hand-rolls a [androidx.compose.foundation.text.BasicTextField]
 * is that M3's default `TextField` enforces a 56dp minimum height, which reads as a slab on a
 * 44dp action row. M3 lets you override the height; this wrapper makes that the default policy
 * instead of a per-call workaround, so every field (composer, connect, sheets, dialogs) shares
 * the same look and the same height behaviour.
 *
 * [minHeight] defaults to null (the M3 56dp standard); pass a smaller value for compact
 * single-line rows like the composer's input row.
 */
@Composable
fun DsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    minHeight: Dp? = null,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.then(
            if (minHeight != null) Modifier.heightIn(min = minHeight) else Modifier,
        ),
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        singleLine = singleLine,
        minLines = minLines,
        enabled = enabled,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        colors = dsTextFieldColors(),
    )
}