package com.labteto.dshmobile.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import com.labteto.dshmobile.ui.theme.DsTheme

/**
 * The one field style in the app: transparent-to-the-surface container, accent cursor and focus
 * line, hairline resting line.
 *
 * Every screen used to carry its own copy (connect fields, dialog fields, sheet fields); a fix
 * to one of them silently skipped the others. All call sites route through this.
 */
@Composable
fun dsTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = DsTheme.colors.bgLayer1,
    unfocusedContainerColor = DsTheme.colors.bgLayer1,
    focusedIndicatorColor = DsTheme.colors.accent,
    unfocusedIndicatorColor = DsTheme.colors.borderL2,
    cursorColor = DsTheme.colors.accent,
)

/** Keyboard options shared by single-line fields: Done on the IME. */
fun dsDoneKeyboard(): KeyboardOptions = KeyboardOptions.Default
