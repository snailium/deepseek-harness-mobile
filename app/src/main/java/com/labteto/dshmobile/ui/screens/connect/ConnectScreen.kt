package com.labteto.dshmobile.ui.screens.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labteto.dshmobile.R
import com.labteto.dshmobile.connection.ConnectStage
import com.labteto.dshmobile.connection.DiscoveredHost
import com.labteto.dshmobile.connection.HostConfig
import com.labteto.dshmobile.ui.components.DisclosureRow
import com.labteto.dshmobile.ui.components.dsTextFieldColors
import com.labteto.dshmobile.ui.components.DsButton
import com.labteto.dshmobile.ui.components.DsButtonSize
import com.labteto.dshmobile.ui.components.DsButtonVariant
import com.labteto.dshmobile.ui.components.DsCard
import com.labteto.dshmobile.ui.components.DsIconButton
import com.labteto.dshmobile.ui.components.DsPill
import com.labteto.dshmobile.ui.components.DsSegment
import com.labteto.dshmobile.ui.components.DsSegmented
import com.labteto.dshmobile.ui.components.EmptyHero
import com.labteto.dshmobile.ui.components.FeatherIcons
import com.labteto.dshmobile.ui.components.SectionHeader
import com.labteto.dshmobile.ui.components.StateDot
import com.labteto.dshmobile.ui.components.StateDotState
import com.labteto.dshmobile.ui.components.relativeTime
import com.labteto.dshmobile.ui.theme.DsShapes
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType
import kotlinx.coroutines.launch

@Composable
fun ConnectScreen(onOpenSettings: () -> Unit, viewModel: ConnectViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = DsTheme.colors
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    // Saveable: a rotation mid-connect used to wipe a hand-typed address.
    var address by rememberSaveable { mutableStateOf("") }
    var scheme by rememberSaveable { mutableStateOf("http") }
    var port by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    var cfClientId by rememberSaveable { mutableStateOf("") }
    var cfClientSecret by rememberSaveable { mutableStateOf("") }
    var showToken by rememberSaveable { mutableStateOf(false) }
    var showSecret by rememberSaveable { mutableStateOf(false) }
    // Until the user touches the Advanced controls the scheme and port follow the address field;
    // the first edit hands them over to the explicit values.
    var advancedTouched by rememberSaveable { mutableStateOf(false) }
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    var authExpanded by rememberSaveable { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    // Where the manual form sits in the scroll, so Edit can bring it into view.
    var manualTop by remember { mutableStateOf(0) }

    val parsed = remember(address) { parseAddress(address) }
    val effectiveScheme = if (advancedTouched) scheme else (parsed?.scheme ?: scheme)
    val effectivePortText =
        if (advancedTouched && port.isNotBlank()) port else parsed?.port?.toString() ?: ""
    // Read outside the effect: CompositionLocals are only reachable from composition, and the
    // scroll-to-edit effect below needs the pixel value in its suspend body.
    val scrollPadPx = with(LocalDensity.current) { DsSpacing.medium.toPx() }.toInt()

    fun connect() {
        val target = parseAddress(address)
        if (target == null) {
            // The parser rejected the text (garbage, an IPv6 literal, a bad port), so the form
            // has no endpoint to offer — hand the VM nothing and let its InvalidInput diagnosis
            // say what an address must look like. The field keeps what the user typed.
            viewModel.connectManual("", "", "http", token, cfClientId, cfClientSecret)
            return
        }
        viewModel.connectManual(
            host = target.host,
            port = effectivePortText,
            scheme = effectiveScheme,
            token = token,
            cfClientId = cfClientId,
            cfClientSecret = cfClientSecret,
        )
    }

    // Edit on a Recent card pre-fills the whole form and scrolls it into view, so a mistyped
    // secret can be corrected without deleting the host and starting over.
    LaunchedEffect(editingId) {
        val target = editingId
            ?.let { id -> state.remembered.firstOrNull { it.id == id } }
            ?: return@LaunchedEffect
        address = target.displayAddress
        scheme = target.scheme
        port = target.port.toString()
        token = target.authToken.orEmpty()
        cfClientId = target.cfClientId.orEmpty()
        cfClientSecret = target.cfClientSecret.orEmpty()
        advancedTouched = true
        advancedExpanded = true
        authExpanded = token.isNotBlank() || cfClientId.isNotBlank() || cfClientSecret.isNotBlank()
        if (manualTop > 0) {
            scope.launch { scrollState.animateScrollTo((manualTop - scrollPadPx).coerceAtLeast(0)) }
        }
        editingId = null
    }

    Surface(modifier = Modifier.fillMaxSize(), color = colors.bgBase) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(scrollState)
                .padding(DsSpacing.xlarge),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.large),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                DsIconButton(
                    icon = FeatherIcons.Settings,
                    contentDescription = stringResource(R.string.settings_title),
                    onClick = onOpenSettings,
                    tint = colors.labelTertiary,
                )
            }

            // First-run vs returning: a fresh install gets a short explainer of what DSH Mobile
            // is and the three options below; a returning user just wants the compact hero and the
            // Resume card underneath.
            if (state.remembered.isEmpty()) {
                EmptyHero(
                    headline = stringResource(R.string.onboarding_intro_title),
                    subtitle = stringResource(R.string.onboarding_intro_body),
                    chips = emptyList(),
                    onChipClick = {},
                )
                // A big, unmissable primary path for someone who has never connected: the scan is
                // the hands-free way to find the harness, and it is what the guide leads with. The
                // manual form below still covers the edge cases.
                DsButton(
                    text = stringResource(R.string.onboarding_scan),
                    icon = FeatherIcons.Search,
                    onClick = { viewModel.scan() },
                    variant = DsButtonVariant.Primary,
                    size = DsButtonSize.Large,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                EmptyHero(
                    headline = stringResource(R.string.app_long_name),
                    subtitle = stringResource(R.string.connect_subtitle),
                    chips = emptyList(),
                    onChipClick = {},
                )
            }

            // Security banner. Info-toned: the point is a calm reminder of where the protection
            // lives, not an alarm about a fault — and once read, it stays dismissed until the
            // next launch (rememberSaveable scopes it to the process lifetime).
            var securityDismissed by rememberSaveable { mutableStateOf(false) }
            if (!securityDismissed) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = DsShapes.alert,
                    color = colors.accentTertiary,
                ) {
                    Row(
                        modifier = Modifier.padding(start = DsSpacing.medium, top = DsSpacing.small, bottom = DsSpacing.small, end = DsSpacing.xsmall),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.connect_security_banner),
                            style = DsType.small13,
                            color = colors.labelSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        DsIconButton(
                            icon = FeatherIcons.X,
                            contentDescription = stringResource(R.string.common_close),
                            onClick = { securityDismissed = true },
                            iconSize = 16.dp,
                        )
                    }
                }
            }

            // ---- Resume -----------------------------------------------------
            // The one-tap path back for a returning user: the most recent harness, big and
            // primary. First-time users see no card here and the manual form below is the top.
            state.remembered.firstOrNull()?.let { last ->
                val probe = state.recentStatus[last.authority]
                val home = (probe as? HostProbe.Reachable)?.description?.home
                DsCard(onClick = { viewModel.connectTo(last) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StateDot(
                            when (probe) {
                                is HostProbe.Reachable -> StateDotState.Done
                                HostProbe.Probing -> StateDotState.Running
                                else -> StateDotState.Idle
                            },
                            size = 8.dp,
                            contentDescription = stringResource(
                                when (probe) {
                                    is HostProbe.Reachable -> R.string.status_online
                                    HostProbe.Probing -> R.string.status_running
                                    else -> R.string.status_offline
                                },
                            ),
                        )
                        Spacer(Modifier.width(DsSpacing.compact))
                        Column(Modifier.weight(1f)) {
                            Text(
                                last.displayAddress,
                                style = DsType.std14Strong,
                                color = colors.labelPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (home != null) {
                                Text(
                                    stringResource(R.string.connect_harness_home, basename(home)),
                                    style = DsType.caption11,
                                    color = colors.labelTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        DsButton(
                            text = stringResource(R.string.connect_button),
                            onClick = { viewModel.connectTo(last) },
                            variant = DsButtonVariant.Info,
                            size = DsButtonSize.Small,
                        )
                    }
                }
            }

            // ---- Manual -----------------------------------------------------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { manualTop = it.positionInParent().y.toInt() },
                verticalArrangement = Arrangement.spacedBy(DsSpacing.small),
            ) {
                SectionHeader(stringResource(R.string.connect_manual_title))
                // The form is one grouped plate: field, endpoint reading, and the disclosures
                // live inside it, M3 settings-group style.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.groupCard)
                        .background(colors.bgLayer1)
                        .border(1.dp, colors.borderL2, DsShapes.groupCard)
                        .padding(horizontal = DsSpacing.medium, vertical = DsSpacing.small),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.small),
                ) {
                TextField(
                    value = address,
                    onValueChange = { address = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(stringResource(R.string.connect_address_hint), style = DsType.std14)
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.connect_address_label)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { connect() }),
                    colors = connectFieldColors(),
                )
                // The endpoint the form will actually use, stated before Connect is tapped:
                // scheme and port defaults are guesses, and a guess the user can see is a guess
                // the user can correct.
                parsed?.let {
                    Text(
                        stringResource(
                            R.string.connect_endpoint,
                            "$effectiveScheme://${it.host}:$effectivePortText",
                        ),
                        style = DsType.caption11,
                        color = colors.labelTertiary,
                    )
                }
                DisclosureRow(
                    title = stringResource(R.string.connect_advanced_title),
                    expanded = advancedExpanded,
                    onToggle = { advancedExpanded = !advancedExpanded },
                ) {
                    Column(
                        modifier = Modifier.padding(start = DsSpacing.medium),
                        verticalArrangement = Arrangement.spacedBy(DsSpacing.small),
                    ) {
                        DsSegmented(
                            segments = listOf(
                                DsSegment("http", stringResource(R.string.connect_scheme_http)),
                                DsSegment("https", stringResource(R.string.connect_scheme_https)),
                            ),
                            selectedKey = effectiveScheme,
                            onSelect = {
                                scheme = it
                                advancedTouched = true
                            },
                        )
                        TextField(
                            value = port,
                            onValueChange = {
                                port = it.filter { c -> c.isDigit() }
                                advancedTouched = true
                            },
                            modifier = Modifier.width(140.dp),
                            singleLine = true,
                            label = { Text(stringResource(R.string.connect_port_label)) },
                            placeholder = {
                                Text(stringResource(R.string.connect_port_auto), style = DsType.std14)
                            },
                            colors = connectFieldColors(),
                        )
                    }
                }
                // Optional edge-proxy credentials, sent only when filled in. The token is an
                // `Authorization: Bearer`; the two Cloudflare fields are a Cloudflare Access
                // service token, which travels as its own pair of headers. The Client ID is not
                // secret (it identifies the token); the Secret and the access token are masked.
                DisclosureRow(
                    title = stringResource(R.string.connect_auth_title),
                    expanded = authExpanded,
                    onToggle = { authExpanded = !authExpanded },
                ) {
                    Column(
                        modifier = Modifier.padding(start = DsSpacing.medium),
                        verticalArrangement = Arrangement.spacedBy(DsSpacing.small),
                    ) {
                        Text(
                            stringResource(R.string.connect_auth_hint),
                            style = DsType.caption11,
                            color = colors.labelTertiary,
                        )
                        SecretField(
                            value = token,
                            onValueChange = { token = it },
                            label = stringResource(R.string.connect_token_label),
                            visible = showToken,
                            onToggleVisibility = { showToken = it },
                        )
                        SecretField(
                            value = cfClientId,
                            onValueChange = { cfClientId = it },
                            label = stringResource(R.string.connect_cf_id_label),
                            visible = true,
                            toggleable = false,
                        )
                        SecretField(
                            value = cfClientSecret,
                            onValueChange = { cfClientSecret = it },
                            label = stringResource(R.string.connect_cf_secret_label),
                            visible = showSecret,
                            onToggleVisibility = { showSecret = it },
                        )
                    }
                }
                } // end of the grouped form plate
                DsButton(
                    text = stringResource(R.string.connect_button),
                    onClick = { connect() },
                    enabled = !state.connecting,
                    variant = DsButtonVariant.Info,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.connecting) ConnectProgressRow(state.stage, state.attempted)
                state.failure?.let { failure ->
                    ConnectFailureBlock(
                        failure = failure,
                        attempted = state.attempted,
                        retrying = state.retrying,
                        onCancel = viewModel::cancelConnect,
                    )
                }
                // The wiki is the user guide; a caption link keeps the form self-servicing.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    DsButton(
                        text = stringResource(R.string.connect_help),
                        onClick = {
                            runCatching { uriHandler.openUri(HELP_URL) }
                        },
                        variant = DsButtonVariant.Ghost,
                        size = DsButtonSize.Small,
                    )
                }
            }

            // ---- Recent -----------------------------------------------------
            Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.small)) {
                SectionHeader(stringResource(R.string.connect_remembered))
                if (state.remembered.isEmpty()) {
                    Text(
                        stringResource(R.string.connect_remembered_empty),
                        style = DsType.std14,
                        color = colors.labelCaption,
                    )
                } else {
                    state.remembered.forEach { saved ->
                        RecentHarnessCard(
                            host = saved,
                            probe = state.recentStatus[saved.authority],
                            onConnect = { viewModel.connectTo(saved) },
                            onEdit = { editingId = saved.id },
                            onForget = { viewModel.forget(saved) },
                        )
                    }
                }
            }

            // ---- Discovered --------------------------------------------------
            Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.small)) {
                SectionHeader(
                    title = stringResource(R.string.connect_discovered),
                    action = stringResource(R.string.connect_scan),
                    onAction = { viewModel.scan() },
                )
                val unknown = state.unknownDiscovered
                // Results and progress coexist: the sweep streams, so a host found in the first
                // batch belongs on screen while the rest of the subnet is still being knocked.
                if (state.scanning) {
                    ScanProgressRow(state.scanProgress) { viewModel.cancelScan() }
                }
                if (unknown.isEmpty()) {
                    if (!state.scanning) {
                        Text(
                            stringResource(R.string.connect_discovered_hint),
                            style = DsType.std14,
                            color = colors.labelCaption,
                        )
                    }
                } else {
                    unknown.forEach { found ->
                        DiscoveredHarnessCard(found) { viewModel.connectDiscovered(found) }
                    }
                }
            }

            Spacer(Modifier.height(DsSpacing.xlarge))
        }

        // A manual connect to an address outside this phone's network pauses here once. The
        // harness itself has no login, so the warning is the user's chance to notice they are
        // sending agent control to somewhere else on the internet.
        state.pendingRemoteConfirm?.let { pending ->
            val address = if (pending.scheme == "https") "https://${pending.authority}" else pending.authority
            AlertDialog(
                onDismissRequest = viewModel::dismissRemoteConfirm,
                title = { Text(stringResource(R.string.connect_remote_confirm_title)) },
                text = {
                    Text(
                        stringResource(R.string.connect_remote_confirm_body, address),
                        style = DsType.std14,
                    )
                },
                confirmButton = {
                    TextButton(onClick = viewModel::confirmRemote) {
                        Text(stringResource(R.string.connect_remote_confirm_continue))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissRemoteConfirm) {
                        Text(stringResource(R.string.common_cancel))
                    }
                },
            )
        }
    }
}

/**
 * One credential field: masked by default with a show/hide toggle, so a Client Secret typed once
 * is not permanently visible on the connect screen. [toggleable] is false for values that are not
 * secret (the Cloudflare Client ID identifies the token; it need not be hidden).
 */
@Composable
private fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    toggleable: Boolean = true,
    onToggleVisibility: (Boolean) -> Unit = {},
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            label = { Text(label) },
            placeholder = { Text(stringResource(R.string.connect_token_hint), style = DsType.std14) },
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                if (toggleable) {
                    IconButton(onClick = { onToggleVisibility(!visible) }) {
                        Icon(
                            if (visible) FeatherIcons.EyeOff else FeatherIcons.Eye,
                            contentDescription = stringResource(R.string.connect_token_show_hide),
                            tint = DsTheme.colors.labelTertiary,
                        )
                    }
                }
            },
            colors = connectFieldColors(),
        )
    }
}

@Composable
private fun connectFieldColors() = dsTextFieldColors()

/**
 * One remembered harness.
 *
 * Everything on the card is already known or already probed — the address, the last-connected
 * stamp, the harness version, the project it is sitting in, how many sessions are attached — and a
 * status dot says whether it is answering right now. The previous row showed an icon and an IP
 * repeated twice, which is why the list read as blank space.
 */
@Composable
private fun RecentHarnessCard(
    host: HostConfig,
    probe: HostProbe?,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onForget: () -> Unit,
) {
    val colors = DsTheme.colors
    val reachable = probe as? HostProbe.Reachable
    val title = if (host.isLoopback) stringResource(R.string.connect_same_device) else host.name
    val home = reachable?.description?.home

    DsCard(onClick = onConnect) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StateDot(
                when (probe) {
                    is HostProbe.Reachable -> StateDotState.Done
                    HostProbe.Probing -> StateDotState.Running
                    HostProbe.Unreachable -> StateDotState.Idle
                    null -> StateDotState.Idle
                },
                size = 8.dp,
                contentDescription = stringResource(
                    when (probe) {
                        is HostProbe.Reachable -> R.string.status_online
                        HostProbe.Probing -> R.string.status_running
                        else -> R.string.status_offline
                    },
                ),
            )
            Spacer(Modifier.width(DsSpacing.compact))
            Text(
                title,
                style = DsType.std14Strong,
                color = colors.labelPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (host.lastConnectedAt > 0L) {
                    relativeTime(host.lastConnectedAt)
                } else {
                    stringResource(R.string.connect_never)
                },
                style = DsType.caption11,
                color = colors.labelCaption,
                maxLines = 1,
            )
        }
        Text(
            if (home != null) {
                listOfNotNull(host.displayAddress, basename(home)).joinToString(" · ")
            } else {
                host.displayAddress
            },
            style = DsType.caption11,
            color = colors.labelTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                statusLine(probe, null, null),
                style = DsType.caption11,
                color = if (probe is HostProbe.Unreachable) colors.labelCaption else colors.labelTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // Edit restores the host into the manual form — the one way to fix a mistyped
            // credential short of deleting the host and typing everything again.
            DsButton(
                text = stringResource(R.string.common_edit),
                icon = FeatherIcons.Pencil,
                onClick = onEdit,
                variant = DsButtonVariant.Ghost,
                size = DsButtonSize.Small,
            )
            DsButton(
                text = stringResource(R.string.common_delete),
                onClick = onForget,
                variant = DsButtonVariant.Ghost,
                size = DsButtonSize.Small,
            )
        }
    }
}

/** The third line: what the harness is, or why it has nothing to say. */
@Composable
private fun statusLine(probe: HostProbe?, version: String?, sessions: Int?): String = when {
    probe is HostProbe.Probing -> stringResource(R.string.connect_checking)
    probe is HostProbe.Unreachable -> stringResource(R.string.connect_unreachable)
    version != null -> listOfNotNull(
        stringResource(R.string.connect_harness_version_only, version),
        sessions?.let { stringResource(R.string.connect_sessions_short, it) },
    ).joinToString(" · ")
    else -> stringResource(R.string.common_loading)
}

/**
 * One sweep result.
 *
 * A harness whose trust fence refused us is still shown. It is the single most recoverable outcome
 * the scan can produce — the harness is running, on the right port, one `--trusted-host` away — and
 * reporting it as "nothing found" sends people looking for a fault that is not there.
 */
@Composable
private fun DiscoveredHarnessCard(found: DiscoveredHost, onConnect: () -> Unit) {
    val colors = DsTheme.colors
    val description = found.description
    DsCard(onClick = if (description != null) onConnect else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                FeatherIcons.Globe,
                contentDescription = null,
                tint = if (description != null) colors.accent else colors.warn,
                modifier = Modifier.width(14.dp),
            )
            Spacer(Modifier.width(DsSpacing.compact))
            Text(
                found.authority,
                style = DsType.std14Strong,
                color = colors.labelPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (description != null) {
                DsButton(
                    text = stringResource(R.string.connect_button),
                    onClick = onConnect,
                    variant = DsButtonVariant.Info,
                    size = DsButtonSize.Small,
                )
            } else {
                DsPill(text = stringResource(R.string.connect_found_untrusted), warn = true)
            }
        }
        if (description != null) {
            Text(
                basename(description.home).takeIf { it.isNotBlank() }.orEmpty(),
                style = DsType.caption11,
                color = colors.labelTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                stringResource(R.string.connect_found_untrusted_hint),
                style = DsType.caption11,
                color = colors.labelTertiary,
            )
        }
    }
}

/**
 * What the connect attempt is doing, named.
 *
 * A greyed-out button is the same picture whether the handshake is a second from finishing or the
 * packets are being dropped by a firewall. Naming the stage costs one line and turns a wait into a
 * progress report — and when it stops, the stage it stopped on is itself a clue.
 */
@Composable
private fun ConnectProgressRow(stage: ConnectStage, attempted: String?) {
    val colors = DsTheme.colors
    val label = when (stage) {
        ConnectStage.Validating -> stringResource(R.string.connect_stage_validating)
        ConnectStage.Reaching -> stringResource(R.string.connect_stage_reaching, attempted.orEmpty())
        ConnectStage.OpeningStreams -> stringResource(R.string.connect_stage_streams)
        ConnectStage.Verifying -> stringResource(R.string.connect_stage_verifying)
        ConnectStage.Connected -> stringResource(R.string.connect_stage_connected)
        ConnectStage.Idle -> return
    }
    Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.xsmall)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StateDot(StateDotState.Running, size = 8.dp)
            Spacer(Modifier.width(DsSpacing.xsmall))
            Text(label, style = DsType.std14, color = colors.labelTertiary)
        }
        LinearProgressIndicator(
            progress = { stage.ordinal / (ConnectStage.entries.size - 1).toFloat() },
            modifier = Modifier.fillMaxWidth(),
            color = colors.accent,
            trackColor = colors.hoverSolid,
        )
    }
}

/**
 * Why it did not connect, and what to do about it.
 *
 * Deliberately one sentence of cause and one of action, with no commands: the device that failed is
 * the phone, and the fix almost always happens on the computer. `harness/README.md` carries the
 * PowerShell.
 */
@Composable
private fun ConnectFailureBlock(
    failure: ConnectFailure,
    attempted: String?,
    retrying: Boolean,
    onCancel: () -> Unit,
) {
    val colors = DsTheme.colors
    val authority = attempted.orEmpty()
    val port = authority.substringAfterLast(':', "").toIntOrNull() ?: 0
    // `connect_failed` is formatted from the two halves so it reads as one address; feeding it the
    // whole authority plus an empty port left a trailing colon. Blank means there was nothing to
    // attempt (bad input), and a headline naming no address would say nothing.
    val title = when {
        failure is ConnectFailure.TrustFence -> stringResource(R.string.connect_fail_fence_title)
        authority.isBlank() -> null
        else -> stringResource(
            R.string.connect_failed,
            authority.substringBeforeLast(':', authority),
            port.toString(),
        )
    }
    val body = when (failure) {
        ConnectFailure.InvalidInput -> stringResource(R.string.connect_fail_invalid)
        is ConnectFailure.DifferentSubnet -> stringResource(
            R.string.connect_fail_subnet,
            authority,
            failure.localPrefix ?: stringResource(R.string.connect_unreachable),
        )
        ConnectFailure.AccessDenied -> stringResource(R.string.connect_fail_access, authority)
        ConnectFailure.Timeout -> stringResource(R.string.connect_fail_timeout, authority, port)
        ConnectFailure.Refused -> stringResource(R.string.connect_fail_refused, authority)
        ConnectFailure.TrustFence -> stringResource(R.string.connect_failed_fence)
        ConnectFailure.Unauthenticated -> stringResource(R.string.connect_fail_unauthenticated, authority)
        ConnectFailure.PairingRequired -> stringResource(R.string.connect_fail_pairing)
        ConnectFailure.CertificateChanged -> stringResource(R.string.connect_fail_certificate, authority)
        ConnectFailure.DnsFailure -> stringResource(R.string.connect_fail_dns, authority)
        ConnectFailure.NotAHarness -> stringResource(R.string.connect_fail_not_harness, authority)
        ConnectFailure.TlsFailure -> stringResource(R.string.connect_fail_tls, authority)
        ConnectFailure.StreamsBlocked -> stringResource(R.string.connect_fail_streams, authority)
        is ConnectFailure.Other -> stringResource(R.string.connect_fail_other, authority, failure.detail)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = DsShapes.alert,
        color = colors.warnTertiary,
    ) {
        Column(
            modifier = Modifier.padding(DsSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.xsmall),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StateDot(StateDotState.Error, size = 8.dp)
                Spacer(Modifier.width(DsSpacing.xsmall))
                Text(
                    title ?: body,
                    style = DsType.std14,
                    color = colors.warnLabel,
                )
            }
            // With no headline the body has already been shown beside the dot.
            if (title != null) Text(body, style = DsType.small13, color = colors.warnLabel)
            // The loop backs off and retries forever; without this there is no way to stop it.
            if (retrying) {
                DsButton(
                    text = stringResource(R.string.connect_cancel),
                    onClick = onCancel,
                    variant = DsButtonVariant.Ghost,
                    size = DsButtonSize.Small,
                )
            }
        }
    }
}

/** Determinate sweep feedback: a /24 takes long enough that a static label reads as a hang. */
@Composable
private fun ScanProgressRow(progress: ScanProgress?, onCancel: () -> Unit) {
    val colors = DsTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.xsmall)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (progress == null) {
                    stringResource(R.string.connect_scanning)
                } else {
                    stringResource(R.string.connect_scan_progress, progress.probed, progress.total)
                },
                style = DsType.std14,
                color = colors.labelTertiary,
                modifier = Modifier.weight(1f),
            )
            DsButton(
                text = stringResource(R.string.common_cancel),
                onClick = onCancel,
                variant = DsButtonVariant.Ghost,
                size = DsButtonSize.Small,
            )
        }
        if (progress != null && progress.total > 0) {
            LinearProgressIndicator(
                progress = { progress.probed.toFloat() / progress.total },
                modifier = Modifier.fillMaxWidth(),
                color = colors.accent,
                trackColor = colors.hoverSolid,
            )
        } else {
            Box(Modifier.fillMaxWidth().height(4.dp))
        }
    }
}

/** Last path segment of a host cwd, so a card can name the project rather than print a full path. */
private fun basename(path: String): String =
    path.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\').ifBlank { path }

/** The user-facing connect guide, from the upstream wiki. */
private const val HELP_URL = "https://github.com/sorsama/deepseek-harness-mobile/wiki/Connecting"
