package com.labteto.dshmobile.ui.screens.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labteto.dshmobile.R
import com.labteto.dshmobile.ui.components.DsButton
import com.labteto.dshmobile.ui.components.DsButtonVariant
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType

/**
 * The gateway pairing form, as a dialog over the connect screen.
 *
 * It replaces the pair *screen* upstream deleted, and deliberately does not look like the harness's
 * own connect form: the two ask for different things. That form wants an address and, optionally, an
 * edge-proxy credential; this one wants the key or link the DSH Mobile panel prints for an Android
 * app, and derives everything else from it.
 *
 * The address field appears only for a bare key, which names the computer but not where it is.
 * Showing it beside a pasted link would invite editing a value the link already decides.
 */
@Composable
fun GatewayPairSheet(
    onDismiss: () -> Unit,
    viewModel: GatewayPairViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = DsTheme.colors
    val needsAddress = state.address.isBlank() || (state.keyOrLink.isNotBlank() && !state.keyOrLink.contains("://"))
    val scan = rememberGatewayQrScanner(onText = viewModel::scanned)
    // A claim succeeded: close, rather than leaving a spent key on screen for a second tap.
    LaunchedEffect(state.pairedHostId) {
        if (state.pairedHostId != null) onDismiss()
    }

    AlertDialog(
        onDismissRequest = { if (!state.busy) onDismiss() },
        title = { Text(stringResource(R.string.gateway_pair_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.small)) {
                Text(
                    stringResource(R.string.gateway_pair_hint),
                    style = DsType.caption11,
                    color = colors.labelSecondary,
                )
                DsButton(
                    text = stringResource(R.string.gateway_scan),
                    onClick = { if (!state.busy) scan() },
                    enabled = !state.busy,
                    variant = DsButtonVariant.Info,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.keyOrLink,
                    onValueChange = viewModel::setKeyOrLink,
                    label = { Text(stringResource(R.string.gateway_key_label)) },
                    singleLine = true,
                    enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (needsAddress) {
                    OutlinedTextField(
                        value = state.address,
                        onValueChange = viewModel::setAddress,
                        label = { Text(stringResource(R.string.gateway_address_label)) },
                        placeholder = { Text(stringResource(R.string.gateway_address_placeholder)) },
                        singleLine = true,
                        enabled = !state.busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = state.deviceName,
                    onValueChange = viewModel::setDeviceName,
                    label = { Text(stringResource(R.string.gateway_device_name_label)) },
                    singleLine = true,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.message?.let { message ->
                    Text(
                        message.text(),
                        style = DsType.caption11,
                        color = if (state.failed) colors.warnLabel else colors.labelSecondary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = viewModel::pair,
                enabled = !state.busy && state.pairedHostId == null && state.keyOrLink.isNotBlank(),
            ) {
                Text(stringResource(if (state.busy) R.string.gateway_action_pairing else R.string.gateway_action_pair))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.busy) {
                Text(stringResource(R.string.gateway_action_cancel))
            }
        },
    )
}

/** The message, resolved. Strings live in `gateway_strings.xml`; see its header for why. */
@Composable
private fun GatewayPairMessage.text(): String = when (this) {
    GatewayPairMessage.NotAKey -> stringResource(R.string.gateway_msg_not_a_key)
    GatewayPairMessage.AddressRequired -> stringResource(R.string.gateway_msg_address_required)
    is GatewayPairMessage.NotAGateway -> stringResource(R.string.gateway_msg_not_a_gateway, origin)
    is GatewayPairMessage.CaMismatch -> stringResource(R.string.gateway_msg_ca_mismatch, origin)
    GatewayPairMessage.Rejected -> stringResource(R.string.gateway_msg_rejected)
    is GatewayPairMessage.HostRefused -> stringResource(R.string.gateway_msg_host_refused, origin)
    is GatewayPairMessage.RateLimited -> stringResource(R.string.gateway_msg_rate_limited, seconds)
    is GatewayPairMessage.Unreachable -> stringResource(R.string.gateway_msg_unreachable, origin)
    is GatewayPairMessage.Failed -> detail ?: stringResource(R.string.gateway_msg_failed)
}
