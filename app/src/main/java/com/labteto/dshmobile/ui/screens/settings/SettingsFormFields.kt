package com.labteto.dshmobile.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.labteto.dshmobile.R
import com.labteto.dshmobile.core.wire.dto.SettingsField
import com.labteto.dshmobile.ui.components.DsSegment
import com.labteto.dshmobile.ui.components.DsSegmented
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * One settings field, rendered as an editable control.
 *
 * The field tree comes from [com.labteto.dshmobile.core.wire.dto.SettingsSchemaResolver], which has
 * already turned the compiled schema's uid graph into typed nodes. This file's job is only to pick a
 * control per type and to read/write a value at a path — it makes no decisions about the schema.
 *
 * [value] is the field's *effective* value from the namespace, not its default: the schema's default
 * is what a field falls back to, and showing it as though it were set would misreport what the
 * harness is actually running with.
 *
 * A field whose type this build cannot render is drawn as a labelled row carrying its raw value.
 * Showing it read-only is deliberate — a form that silently dropped the key would look complete
 * while hiding part of the configuration.
 */
@Composable
fun SettingsFieldRow(
    field: SettingsField,
    value: JsonElement?,
    onEdit: (List<String>, JsonElement) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DsTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        when (field) {
            is SettingsField.Group -> {
                // A group's label is its path segment; the root's path is empty and gets none.
                val label = field.path.lastOrNull()
                if (label != null) {
                    Text(
                        label,
                        style = DsType.small13Strong,
                        color = colors.labelSecondary,
                        modifier = Modifier.padding(top = DsSpacing.small, bottom = DsSpacing.tiny),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.small)) {
                    field.fields.forEach { child ->
                        SettingsFieldRow(
                            field = child,
                            value = child.readFrom(value),
                            onEdit = onEdit,
                        )
                    }
                }
            }

            is SettingsField.MapField,
            is SettingsField.ListField,
            is SettingsField.Unsupported,
            -> UnrenderedField(field, value)

            is SettingsField.Toggle -> {
                val checked = value?.jsonPrimitive?.booleanOrNull
                    ?: field.node.default?.jsonPrimitive?.booleanOrNull
                    ?: false
                LabelledRow(field) {
                    Switch(
                        checked = checked,
                        onCheckedChange = { next -> onEdit(field.path, JsonPrimitive(next)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.bgBase,
                            checkedTrackColor = colors.accent,
                            uncheckedThumbColor = colors.labelTertiary,
                            uncheckedTrackColor = colors.hoverSolid,
                            uncheckedBorderColor = colors.borderL2,
                        ),
                    )
                }
            }

            is SettingsField.Choice -> {
                val current = value?.jsonPrimitive?.contentOrNull
                    ?: field.node.default?.jsonPrimitive?.contentOrNull
                    ?: field.options.first()
                LabelledRow(field) {
                    DsSegmented(
                        segments = field.options.map { DsSegment(it, it) },
                        selectedKey = current,
                        onSelect = { next -> onEdit(field.path, JsonPrimitive(next)) },
                    )
                }
            }

            is SettingsField.Text -> {
                val text = value?.jsonPrimitive?.contentOrNull
                    ?: field.node.default?.jsonPrimitive?.contentOrNull
                    ?: ""
                LabelledRow(field, stacked = true) {
                    DebouncedField(
                        value = text,
                        onCommit = { next -> onEdit(field.path, JsonPrimitive(next)) },
                    )
                }
            }

            is SettingsField.Number -> {
                val text = value?.jsonPrimitive?.contentOrNull
                    ?: field.node.default?.jsonPrimitive?.contentOrNull
                    ?: ""
                LabelledRow(field, stacked = true) {
                    // A text field rather than a stepper: the schema's bounds are advisory
                    // (`min`/`max`/`step`), and a numeric keypad is the only phone affordance that
                    // suits a value which is sometimes a port and sometimes a token count.
                    DebouncedField(
                        value = text,
                        numeric = true,
                        onCommit = { next ->
                            // Only a parseable number is written. A half-typed "-" or "1." leaves the
                            // stored value alone rather than sending something the schema would
                            // reject, which keeps a keystroke from producing an error banner.
                            next.trim().toDoubleOrNull()?.let { parsed ->
                                onEdit(field.path, JsonPrimitive(normalizeNumber(next, parsed)))
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * How long typing must stop before a text field commits its value.
 *
 * Long enough to cover the gap between two words on a phone keyboard, short enough that a reader who
 * taps away and looks at the screen sees their change already applied.
 */
private const val SETTLE_MS = 500L

/**
 * A single-line text field that commits its value once typing settles.
 *
 * Committing on every keystroke sent one `settings/update` RPC *and* a full `settings/list` refresh
 * per character. That made the cursor jump as the echoed value came back, flickered the form, and —
 * because the update carries an `expectedRevision` CAS token — let a burst of in-flight writes race
 * each other into revision conflicts. The draft is local; only a settled value reaches [onCommit].
 *
 * The incoming [value] is still adopted when it changes underneath (another client, or this field's
 * own write landing), and the comparison skips that echo so adopting it cannot loop.
 */
@Composable
private fun DebouncedField(
    value: String,
    onCommit: (String) -> Unit,
    numeric: Boolean = false,
) {
    val colors = DsTheme.colors
    var draft by remember { mutableStateOf(value) }
    LaunchedEffect(value) { if (value != draft) draft = value }
    LaunchedEffect(draft) {
        if (draft == value) return@LaunchedEffect
        delay(SETTLE_MS)
        onCommit(draft)
    }
    TextField(
        value = draft,
        onValueChange = { draft = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
        textStyle = DsType.std14.copy(fontFamily = FontFamily.Monospace),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.bgLayer2,
            unfocusedContainerColor = colors.bgLayer2,
            focusedIndicatorColor = colors.accent,
            unfocusedIndicatorColor = colors.borderL2,
            cursorColor = colors.accent,
        ),
    )
}

/**
 * A labelled row, with the control on the right or beneath.
 *
 * The description is the reason this form is usable at all: a schema's field names are camelCase, so
 * without the plugin's own sentence every row is `rrfConstant` and nothing else.
 */
@Composable
private fun LabelledRow(
    field: SettingsField,
    stacked: Boolean = false,
    control: @Composable () -> Unit,
) {
    val colors = DsTheme.colors
    val label = field.path.lastOrNull().orEmpty()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = DsType.small13,
                    color = colors.labelPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                field.node.description?.let { description ->
                    Text(description, style = DsType.caption11, color = colors.labelTertiary)
                }
            }
            if (!stacked) {
                Spacer(Modifier.width(DsSpacing.small))
                control()
            }
        }
        if (stacked) {
            Spacer(Modifier.height(DsSpacing.tiny))
            control()
        }
        // A required field is worth marking: the Host rejects a namespace write that omits one, so
        // the reader should know before the save fails.
        if (field.node.required) {
            Text(
                stringResource(R.string.plugins_field_required),
                style = DsType.caption11,
                color = colors.labelCaption,
            )
        }
    }
}

/**
 * A field this build cannot edit, shown with its value rather than omitted.
 *
 * Lists, maps and unrecognised types all land here. Rendering them read-only is honest — the reader
 * sees that the key exists and what it holds — where hiding them would make the form look complete.
 */
@Composable
private fun UnrenderedField(field: SettingsField, value: JsonElement?) {
    val colors = DsTheme.colors
    val label = field.path.lastOrNull().orEmpty()
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = DsType.small13, color = colors.labelPrimary)
        field.node.description?.let {
            Text(it, style = DsType.caption11, color = colors.labelTertiary)
        }
        val shown = value?.toString()
        Text(
            text = shown ?: stringResource(R.string.plugins_field_not_editable),
            style = DsType.caption11.copy(fontFamily = FontFamily.Monospace),
            color = colors.labelCaption,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Read one field's value out of its parent object.
 *
 * A group's [value] is the object at its path, so a child reads its own last segment from it. A
 * non-object parent yields null, which every control treats as "fall back to the schema default".
 */
private fun SettingsField.readFrom(parent: JsonElement?): JsonElement? {
    val obj = parent as? kotlinx.serialization.json.JsonObject ?: return null
    return obj[path.lastOrNull() ?: return null]
}

/**
 * Render a numeric edit in the form the user typed.
 *
 * `toDoubleOrNull` accepts `3443`, `3443.0` and `3.443e3` alike, and writing the parsed double back
 * would turn a port into `3443.0`. An integral result is therefore written as a long, and anything
 * else keeps its double.
 */
private fun normalizeNumber(typed: String, parsed: Double): Number =
    if (typed.trim().matches(Regex("-?\\d+"))) parsed.toLong() else parsed
