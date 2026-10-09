package com.labteto.dshmobile.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.R
import com.labteto.dshmobile.core.wire.dto.PluginReadOnly
import com.labteto.dshmobile.core.wire.dto.ResolvedSettingsSchema
import com.labteto.dshmobile.ui.components.FeatherIcons
import com.labteto.dshmobile.ui.components.StateDot
import com.labteto.dshmobile.ui.components.StateDotState
import com.labteto.dshmobile.ui.theme.DsAnimations
import com.labteto.dshmobile.ui.theme.DsColors
import com.labteto.dshmobile.ui.theme.DsShapes
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType
import kotlinx.serialization.json.JsonElement

/**
 * One plugin row: what identifies it while collapsed, what configures it once open.
 *
 * Collapsed is the row's identity and its one control — the name, the manifest description, and the
 * enable switch. Open is that row's own settings, inline rather than on another page, because a
 * plugin's configuration is the reason someone taps the row and a second surface to hold eight
 * fields is a surface to navigate back from.
 *
 * The switch stays on the header line in both states. It is the row's own control, and hiding it
 * while the row is open would mean collapsing the row again to turn the plugin off: a plugin's
 * enablement is read while its settings are being edited.
 *
 * A row the Host refuses still draws the switch, disabled, with the reason beside it. Hiding the
 * control would make "this cannot be turned off" indistinguishable from a rendering bug, and the
 * reason is a fact about the profile worth reading.
 */
@Composable
internal fun PluginRowCard(
    title: String,
    description: String?,
    enabled: Boolean?,
    togglable: Boolean,
    busy: Boolean,
    lockReason: String?,
    onToggle: ((Boolean) -> Unit)?,
    initiallyExpanded: Boolean = false,
    settings: (@Composable () -> Unit)? = null,
) {
    val colors = DsTheme.colors
    // Keyed on the title: a reload replaces the list with equal-but-new rows, and an expansion that
    // survived that is the behaviour a reader expects from a row they just opened.
    var open by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val expandable = settings != null
    val rotation by animateFloatAsState(
        targetValue = if (open) 90f else 0f,
        animationSpec = DsAnimations.chevron,
        label = "plugin-row-chevron",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.row)
            .background(colors.bgLayer1)
            .then(
                if (expandable) {
                    Modifier.clickable(role = Role.Button) { open = !open }
                } else {
                    Modifier
                },
            )
            .padding(DsSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.small),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.tiny),
            ) {
                Text(
                    title,
                    style = DsType.std14Strong,
                    color = colors.labelPrimary,
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
            if (expandable) {
                Spacer(Modifier.width(DsSpacing.xsmall))
                Icon(
                    FeatherIcons.ChevronRight,
                    contentDescription = null,
                    tint = colors.labelTertiary,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { rotationZ = rotation },
                )
            }
        }
        if (expandable) {
            AnimatedVisibility(visible = open) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.small),
                ) {
                    settings?.invoke()
                }
            }
        }
    }
}

/**
 * One namespace's form: the fields of a resolved schema, each writing back through [onEdit].
 *
 * Extracted so the bundle dialog and the plugins page render a plugin's settings the same way. The
 * namespace id is deliberately *not* a heading here — the row that contains the form is already
 * titled with the plugin's name, and repeating the raw namespace id above its fields was the flat
 * list this replaced.
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
