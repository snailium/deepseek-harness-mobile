package com.labteto.dshmobile.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.R
import com.labteto.dshmobile.core.wire.dto.ProfileBundle
import com.labteto.dshmobile.data.PluginOperationState
import com.labteto.dshmobile.ui.components.DsButton
import com.labteto.dshmobile.ui.components.DsButtonVariant
import com.labteto.dshmobile.ui.components.DsDialog
import com.labteto.dshmobile.ui.screens.main.FeatureAction
import com.labteto.dshmobile.ui.screens.main.FeatureField
import com.labteto.dshmobile.ui.screens.main.PluginInstallSubject
import com.labteto.dshmobile.ui.screens.main.approvalSubject
import com.labteto.dshmobile.ui.screens.main.displaySubject
import com.labteto.dshmobile.ui.screens.main.technicalDisplay
import com.labteto.dshmobile.ui.theme.DsShapes
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType

/**
 * The install form: what to install, and from where.
 *
 * Two fields and one action. The Host's inspection step is deliberately not here — it answers "may I
 * install this", which the install itself answers, and a phone that has to run a separate inspection
 * before it may try reads as two steps where the harness has one.
 */
@Composable
internal fun InstallPluginDialog(
    spec: String,
    registry: String,
    installedVersion: String?,
    busy: Boolean,
    onSpecChange: (String) -> Unit,
    onRegistryChange: (String) -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    DsDialog(title = stringResource(R.string.harness_install_update), onDismiss = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.small),
        ) {
            FeatureField(spec, onSpecChange, R.string.harness_package, !busy, singleLine = true, technical = true)
            FeatureField(registry, onRegistryChange, R.string.harness_registry, !busy, singleLine = true, technical = true)
            // What is installed now, so an update says what it is updating from rather than looking
            // like a fresh install with a suspiciously familiar name.
            installedVersion?.let {
                Text(
                    stringResource(R.string.ux_a_installed_version, technicalDisplay(it)),
                    style = DsType.small13,
                    color = DsTheme.colors.labelSecondary,
                )
            }
            FeatureAction(
                text = stringResource(R.string.harness_install_update),
                enabled = !busy && spec.isNotBlank(),
                variant = DsButtonVariant.Primary,
                onClick = onInstall,
            )
        }
    }
}

/**
 * The build-approval step, which only appears when the Host asks for it.
 *
 * An install can want to build a package from source, and the harness will not do that without the
 * builds being named back to it. This dialog exists for exactly that reply: [subject] carries the
 * builds the Host is asking about, so approving is a list, not a yes.
 */
@Composable
internal fun ApproveBuildsDialog(
    subject: PluginInstallSubject,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = DsTheme.colors
    DsDialog(title = stringResource(R.string.ux_a_approve_retry), onDismiss = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.small),
        ) {
            Text(
                subject.target,
                style = DsType.std14Strong,
                color = colors.labelPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            subject.approvedBuilds.orEmpty().forEach { build ->
                Text(technicalDisplay(build), style = DsType.small13, color = colors.labelSecondary)
            }
            FeatureAction(
                text = stringResource(R.string.ux_a_approve_retry),
                enabled = !busy,
                variant = DsButtonVariant.Primary,
                onClick = onConfirm,
            )
        }
    }
}

/** Confirmation for a removal, which is not undoable from the phone. */
@Composable
internal fun RemoveBundleDialog(
    bundle: ProfileBundle,
    locale: String,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    DsDialog(title = stringResource(R.string.common_remove), onDismiss = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.small),
        ) {
            Text(
                bundle.displayTitle(locale),
                style = DsType.std14Strong,
                color = DsTheme.colors.labelPrimary,
            )
            FeatureAction(
                text = stringResource(R.string.common_remove),
                enabled = !busy,
                variant = DsButtonVariant.Danger,
                onClick = onConfirm,
            )
        }
    }
}

/**
 * The state of the one install this host is running.
 *
 * An install is minutes long — it fetches and may build — so it is reported as a card on the page
 * rather than as a spinner on the button that started it: the page stays usable, and leaving and
 * returning finds the same card, because the operation lives in the store rather than in this
 * composition.
 */
@Composable
internal fun PluginOperationCard(
    state: PluginOperationState,
    onCancel: () -> Unit,
    onApprove: (PluginInstallSubject) -> Unit,
) {
    val colors = DsTheme.colors
    val approval = state.approvalSubject()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.row)
            .background(colors.bgLayer1)
            .padding(DsSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.xsmall),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.busy) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(DsSpacing.xsmall))
            }
            Text(
                state.displaySubject()?.let(::technicalDisplay)
                    ?: stringResource(R.string.ux_a_previous_install),
                style = DsType.std14Strong,
                color = colors.labelPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // The Host's own phase and log tail, verbatim: they name what is being waited on, and a
        // paraphrase would be a guess about a build this client cannot see.
        listOfNotNull(state.phase, state.log.trim().takeIf { it.isNotEmpty() }, state.error)
            .forEach { line ->
                Text(
                    technicalDisplay(line),
                    style = DsType.small13,
                    color = if (line == state.error) colors.error else colors.labelTertiary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        when {
            approval != null -> DsButton(
                text = stringResource(R.string.ux_a_approve_retry),
                onClick = { onApprove(approval) },
                variant = DsButtonVariant.Primary,
            )
            state.busy -> DsButton(
                text = stringResource(R.string.common_cancel),
                onClick = onCancel,
                variant = DsButtonVariant.Outline,
            )
        }
    }
}
