package com.labteto.dshmobile.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.R
import com.labteto.dshmobile.core.wire.dto.PluginReadOnly
import com.labteto.dshmobile.core.wire.dto.ResolvedSettingsSchema
import com.labteto.dshmobile.ui.components.DsDialog
import com.labteto.dshmobile.ui.components.StateDot
import com.labteto.dshmobile.ui.components.StateDotState
import com.labteto.dshmobile.ui.theme.DsColors
import com.labteto.dshmobile.ui.theme.DsShapes
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType
import kotlinx.serialization.json.JsonElement

/**
 * One plugin row in a list: what identifies it, and its own switch on the right.
 *
 * The switch belongs to the *row*, not to whatever contains it. A bundle's enablement is shorthand
 * the harness offers, but what a reader is looking at is a list of plugins, and the thing they want
 * to turn off is one of them. A switch on the bundle would also mean one toggle standing in for
 * ninety rows (dsh-base carries that many), where the interesting state is per row.
 *
 * Tapping the row opens *that row's* settings rather than expanding in place: a form can be thirty
 * fields (`relay` alone), and a list of ninety rows that each open in place is a list that has to be
 * scrolled past to be understood.
 *
 * A row the Host refuses still draws the switch, disabled, with the reason beside it. Hiding the
 * control would make "this cannot be turned off" indistinguishable from a rendering bug.
 */
@Composable
internal fun PluginRow(
    title: String,
    description: String?,
    enabled: Boolean?,
    togglable: Boolean,
    busy: Boolean,
    lockReason: String?,
    onToggle: ((Boolean) -> Unit)?,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = DsTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(DsShapes.row)
            .then(
                if (onOpen != null) Modifier.clickable(role = Role.Button, onClick = onOpen) else Modifier,
            )
            .padding(horizontal = DsSpacing.medium, vertical = DsSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.tiny),
        ) {
            Text(
                title,
                style = DsType.std14,
                color = colors.labelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = DsType.small13,
                    color = colors.labelTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(DsSpacing.small))
        when {
            enabled != null && togglable && onToggle != null -> Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                enabled = !busy,
                colors = pluginSwitchColors(colors),
            )
            enabled != null -> PluginLock(reason = lockReason)
        }
    }
}

/**
 * One row's settings, in a dialog.
 *
 * The switch is deliberately absent: it is on the row the reader just tapped, and a second control
 * for the same value in the dialog is a way to disagree with yourself.
 */
@Composable
internal fun PluginRowDialog(
    title: String,
    description: String?,
    settings: (@Composable () -> Unit)?,
    onDismiss: () -> Unit,
) {
    val colors = DsTheme.colors
    DsDialog(title = title, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.small),
        ) {
            description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = DsType.small13, color = colors.labelTertiary)
            }
            if (settings == null) {
                Text(
                    stringResource(R.string.plugins_config_none),
                    style = DsType.std14,
                    color = colors.labelTertiary,
                )
            } else {
                settings()
            }
        }
    }
}

/**
 * One namespace's form: the fields of a resolved schema, each writing back through [onEdit].
 *
 * Extracted so every surface that shows a plugin's settings renders them the same way.
 */
@Composable
internal fun NamespaceSettingsForm(
    ns: String,
    schema: ResolvedSettingsSchema,
    value: JsonElement?,
    revision: Long?,
    onEdit: (String, List<String>, JsonElement, Long?) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.small),
    ) {
        schema.fields.forEach { field ->
            SettingsFieldRow(
                field = field,
                value = fieldValueAt(field.path, value),
                onEdit = { path, next -> onEdit(ns, path, next, revision) },
            )
        }
    }
}

/** The app's switch colours, shared so every plugin row reads as the same control. */
@Composable
internal fun pluginSwitchColors(colors: DsColors) = SwitchDefaults.colors(
    checkedThumbColor = colors.bgBase,
    checkedTrackColor = colors.accent,
    uncheckedThumbColor = colors.labelTertiary,
    uncheckedTrackColor = colors.hoverSolid,
    uncheckedBorderColor = colors.borderL2,
)

/**
 * Why a row cannot be toggled, in place of its switch.
 *
 * A dot plus a sentence rather than a disabled switch on its own: the switch tells the reader
 * nothing they cannot already see.
 */
@Composable
internal fun PluginLock(reason: String?) {
    val colors = DsTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        StateDot(StateDotState.Idle)
        Spacer(Modifier.width(DsSpacing.tiny))
        Text(
            text = reason?.let { readOnlyLabel(it)?.let { id -> stringResource(id) } ?: it }
                ?: stringResource(R.string.plugins_locked_generic),
            style = DsType.small13,
            color = colors.labelTertiary,
        )
    }
}

/**
 * The sentence for a read-only reason, or null when this build has no wording for it.
 *
 * Null rather than a generic fallback so the caller can show the Host's own code instead: the codes
 * are stable and greppable, and a plausible-sounding guess is worse than an ugly but true one.
 */
internal fun readOnlyLabel(reason: String): Int? = when (reason) {
    PluginReadOnly.MANAGEMENT_REQUIRED -> R.string.plugins_locked_management
    else -> null
}
