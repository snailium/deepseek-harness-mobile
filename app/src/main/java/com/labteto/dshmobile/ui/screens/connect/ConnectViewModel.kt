package com.labteto.dshmobile.ui.screens.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.labteto.dshmobile.connection.ConnectStage
import com.labteto.dshmobile.connection.ConnectionManager
import com.labteto.dshmobile.connection.ConnectionPhase
import com.labteto.dshmobile.connection.DiscoveredHost
import com.labteto.dshmobile.connection.DiscoveryEngine
import com.labteto.dshmobile.connection.HostConfig
import com.labteto.dshmobile.connection.HostsStore
import com.labteto.dshmobile.connection.ProbeOutcome
import com.labteto.dshmobile.connection.ProbeTimeouts
import com.labteto.dshmobile.core.wire.dto.HostDescription
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.net.InetAddress
import javax.inject.Inject

/** Whether a remembered harness is answering right now. */
sealed interface HostProbe {
    /** The probe is in flight. */
    data object Probing : HostProbe

    /** It answered `host.describe`; [description] is what it said. */
    data class Reachable(val description: HostDescription) : HostProbe

    /** No answer — switched off, asleep, or on another network. */
    data object Unreachable : HostProbe
}

/** How far the subnet sweep has got, so the UI can show more than a spinner. */
data class ScanProgress(val probed: Int, val total: Int)

/**
 * A manual-connect attempt held at the remote-confirmation step: the address is outside this
 * phone's local network, and the user has not confirmed this endpoint yet. Everything the attempt
 * needs is carried here so confirming (or dismissing) does not depend on the fields still holding
 * the same text.
 */
data class PendingRemoteConnect(
    val host: String,
    val port: Int,
    val scheme: String,
    val token: String?,
    val cfClientId: String?,
    val cfClientSecret: String?,
    val isLoopback: Boolean,
    val authority: String,
    val label: String,
)

data class ConnectUiState(
    val remembered: List<HostConfig> = emptyList(),
    /** Liveness per remembered host, keyed by `host:port`. Absent = not probed yet. */
    val recentStatus: Map<String, HostProbe> = emptyMap(),
    val discovered: List<DiscoveredHost> = emptyList(),
    val scanning: Boolean = false,
    val scanProgress: ScanProgress? = null,
    /** What the connect attempt is doing right now. */
    val stage: ConnectStage = ConnectStage.Idle,
    /** Why the last attempt failed, or null. */
    val failure: ConnectFailure? = null,
    /** The authority actually attempted, e.g. `192.168.1.20:3080` — never the live field text. */
    val attempted: String? = null,
    /** The loop is still retrying in the background, so a cancel is worth offering. */
    val retrying: Boolean = false,
    /** A remote (off-LAN) manual attempt awaiting the user's confirmation. */
    val pendingRemoteConfirm: PendingRemoteConnect? = null,
) {
    /**
     * Derived, never stored.
     *
     * The old stored boolean was only ever cleared by a callback that could not fire, so a failed
     * connect left the button disabled for the rest of the session. Reading it off the stage means
     * there is no latch to get stuck: `disconnect()` resets the whole state object, and every other
     * path ends in either [ConnectStage.Connected] or a failure the screen displays.
     */
    val connecting: Boolean
        get() = stage != ConnectStage.Idle && stage != ConnectStage.Connected

    /**
     * Discovered harnesses that are not already remembered.
     *
     * A harness in both lists used to render twice, with two different Connect buttons doing the
     * same thing; the Recent card is the one with the history on it, so the sweep yields.
     */
    val unknownDiscovered: List<DiscoveredHost>
        get() {
            val known = remembered.map { it.authority }.toSet()
            return discovered.filterNot { it.authority in known }
        }
}

@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val connectionManager: ConnectionManager,
    private val discoveryEngine: DiscoveryEngine,
    private val hostsStore: HostsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ConnectUiState())
    val state: StateFlow<ConnectUiState> = _state.asStateFlow()

    /**
     * Non-null while this ViewModel owns the outcome rather than the manager.
     *
     * Validation and the pre-flight probe happen here and can end the attempt before the manager is
     * ever engaged; everything from the handshake on belongs to the manager. Set to null only when
     * handing over, so a locally-decided result is not overwritten by a manager still reporting the
     * previous attempt — and so a stale manager stage cannot pin the screen on "Reaching…".
     */
    private var localStage: ConnectStage? = null

    /** The sweep in flight, so a second tap cannot start a rival one and Cancel has something to stop. */
    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            connectionManager.state.collect { conn ->
                _state.update { current ->
                    val connected = conn.phase == ConnectionPhase.CONNECTED
                    val owned = localStage != null
                    current.copy(
                        stage = localStage ?: conn.stage,
                        failure = when {
                            connected -> null
                            owned -> current.failure
                            else -> conn.failure
                        },
                        retrying = !owned && !connected && conn.attempts > 0,
                        // The Recent card's liveness dot used to be greyed by the failure callback
                        // that no longer exists; without this a dead entry keeps looking healthy.
                        recentStatus = current.attempted
                            ?.takeIf { !owned && conn.failure != null }
                            ?.let { current.recentStatus + (it to HostProbe.Unreachable) }
                            ?: current.recentStatus,
                    )
                }
            }
        }
    }

    init {
        viewModelScope.launch {
            hostsStore.hosts.collect { hosts ->
                _state.update { it.copy(remembered = hosts) }
            }
        }
        viewModelScope.launch { autoConnect() }
        viewModelScope.launch { probeRemembered() }
    }

    /**
     * Probe every remembered host once, concurrently.
     *
     * Without this a Recent row can only offer a Connect button that may or may not do anything;
     * one `host.describe` per entry is what turns the list into something you can read before
     * tapping. Results are folded back into storage so the metadata survives the harness going away.
     */
    private suspend fun probeRemembered() {
        val hosts = hostsStore.hosts.first()
        if (hosts.isEmpty()) return
        _state.update { current ->
            current.copy(recentStatus = hosts.associate { it.authority to HostProbe.Probing })
        }
        supervisorScope {
            hosts.map { host ->
                async {
                    val description = runCatching {
                        // A remembered host is named, not swept — worth waiting for. The probe uses
                        // the host's own scheme and auth headers, so a remote https endpoint behind
                        // Cloudflare Access reports live with the same credentials it connects with.
                        discoveryEngine.probeConfig(host, ProbeTimeouts.Manual)
                    }.getOrNull()
                    _state.update { current ->
                        current.copy(
                            recentStatus = current.recentStatus + (
                                host.authority to (
                                    description?.let { HostProbe.Reachable(it) } ?: HostProbe.Unreachable
                                    )
                                ),
                        )
                    }
                    if (description != null) {
                        hostsStore.cacheDescription(host.host, host.port, description)
                    }
                }
            }.awaitAll()
        }
    }

    /** Re-run the liveness pass, e.g. after the user comes back to the screen. */
    fun refreshRecent() {
        viewModelScope.launch { probeRemembered() }
    }

    private suspend fun autoConnect() {
        val settings = hostsStore.settingsOnce()
        // 1. Last used host.
        if (settings.autoConnectLast) {
            val last = hostsStore.hosts.first().firstOrNull()
            if (last != null) {
                val desc = discoveryEngine.probeConfig(last, ProbeTimeouts.Manual)
                if (desc != null) {
                    connectTo(last)
                    return
                }
            }
        }
        // 2. LAN discovery.
        if (settings.autoConnectLan) {
            val first = firstReachableOnLan(settings.knownPorts)
            if (first != null) {
                val config = hostsStore.rememberHost(
                    name = hostLabel(first.host),
                    host = first.host,
                    port = first.port,
                    isLoopback = false,
                    description = first.description,
                )
                connectTo(config)
                return
            }
        }
        // 3. Same-device loopback.
        if (settings.autoConnectLoopback) {
            val desc = discoveryEngine.probe(LOOPBACK, DEFAULT_PORT)
            if (desc != null) {
                val config = hostsStore.rememberHost(
                    name = hostLabel(LOOPBACK),
                    host = LOOPBACK,
                    port = DEFAULT_PORT,
                    isLoopback = true,
                    description = desc,
                )
                connectTo(config)
            }
        }
    }

    /**
     * Sweep only until something answers, then stop.
     *
     * Auto-connect has no use for the rest of the subnet, so finishing the sweep before making the
     * first attempt is latency nobody asked for. A trust-fenced host does not count — auto-connect
     * cannot do anything with one, and stopping on it would hide a usable harness further along.
     */
    private suspend fun firstReachableOnLan(ports: List<Int>): DiscoveredHost? = coroutineScope {
        val hit = CompletableDeferred<DiscoveredHost?>()
        val sweep = launch {
            val all = discoveryEngine.scan(ports, onFound = { found ->
                if (found.trusted) hit.complete(found)
            })
            hit.complete(all.firstOrNull { it.trusted })
        }
        val result = hit.await()
        sweep.cancel()
        result
    }

    /**
     * Sweep the subnet, showing hosts as they are confirmed.
     *
     * Results stream rather than landing in one batch at the end: the harness someone is looking
     * for is usually found in the first fraction of the sweep, and making them watch the remaining
     * two hundred addresses finish before it appears is the difference between "fast" and "fast on
     * paper". [discovered] is therefore cleared at the start and appended to, not replaced.
     */
    fun scan() {
        if (scanJob?.isActive == true) return
        _state.update {
            it.copy(scanning = true, scanProgress = null, failure = null, discovered = emptyList())
        }
        scanJob = viewModelScope.launch {
            val settings = hostsStore.settingsOnce()
            try {
                discoveryEngine.scan(
                    ports = settings.knownPorts,
                    onProgress = { probed, total ->
                        _state.update { it.copy(scanProgress = ScanProgress(probed, total)) }
                    },
                    onFound = { found ->
                        _state.update { state ->
                            if (state.discovered.any { it.authority == found.authority }) state
                            else state.copy(discovered = state.discovered + found)
                        }
                    },
                )
            } finally {
                // Also the cancel path: a sweep the user stopped keeps whatever it already found.
                _state.update { it.copy(scanning = false, scanProgress = null) }
            }
        }
    }

    /** Stop a sweep in flight, keeping anything it has already turned up. */
    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
    }

    fun connectManual(
        host: String,
        port: String,
        scheme: String,
        token: String,
        cfClientId: String,
        cfClientSecret: String,
    ) {
        // A pasted full URL lands in the host field; its scheme wins over the toggle.
        val pasted = host.trim()
        val pastedPrefix = pasted.substringBefore("://", "").lowercase()
        val schemeValue = if (pastedPrefix == "http" || pastedPrefix == "https") {
            pastedPrefix
        } else {
            HostConfig.normalizeScheme(scheme)
        }
        val hostValue = if (pastedPrefix == "http" || pastedPrefix == "https") {
            pasted.substringAfter("://")
        } else {
            pasted
        }
        val portText = port.trim()
        val portInt = if (portText.isBlank()) {
            if (schemeValue == "https") DEFAULT_HTTPS_PORT else DEFAULT_HTTP_PORT
        } else {
            portText.toIntOrNull()
        }
        if (hostValue.isBlank() || portInt == null || portInt !in 1..65535) {
            fail(ConnectFailure.InvalidInput, attempted = null)
            return
        }
        val authority = "$hostValue:$portInt"
        val isLoopback = hostValue == LOOPBACK || hostValue == "localhost"
        val tokenValue = token.trim().takeIf { it.isNotBlank() }
        val cfIdValue = cfClientId.trim().takeIf { it.isNotBlank() }
        val cfSecretValue = cfClientSecret.trim().takeIf { it.isNotBlank() }

        localStage = ConnectStage.Validating
        _state.update { it.copy(stage = ConnectStage.Validating, failure = null, attempted = authority) }

        viewModelScope.launch {
            // Anything that is not a local-subnet IPv4 literal counts as remote — a public IP or a
            // domain like ds.yeasin.tech. Confirm once (unless this endpoint was confirmed before),
            // then the attempt proceeds exactly like a LAN one.
            val remote = !isLoopback && !discoveryEngine.isDefinitelyLocal(hostValue)
            if (remote) {
                val alreadyConfirmed = hostsStore.hosts.first()
                    .any { it.host == hostValue && it.port == portInt && it.remoteConfirmed }
                if (!alreadyConfirmed) {
                    _state.update {
                        it.copy(
                            pendingRemoteConfirm = PendingRemoteConnect(
                                host = hostValue,
                                port = portInt,
                                scheme = schemeValue,
                                token = tokenValue,
                                cfClientId = cfIdValue,
                                cfClientSecret = cfSecretValue,
                                isLoopback = isLoopback,
                                authority = authority,
                                label = hostLabel(hostValue),
                            ),
                        )
                    }
                    return@launch
                }
            }
            attemptConnect(
                host = hostValue,
                port = portInt,
                scheme = schemeValue,
                token = tokenValue,
                cfClientId = cfIdValue,
                cfClientSecret = cfSecretValue,
                isLoopback = isLoopback,
                authority = authority,
                remoteConfirmed = remote,
            )
        }
    }

    /** The user accepted the remote-connect confirmation; run the held attempt. */
    fun confirmRemote() {
        val pending = _state.value.pendingRemoteConfirm ?: return
        _state.update { it.copy(pendingRemoteConfirm = null) }
        viewModelScope.launch {
            attemptConnect(
                host = pending.host,
                port = pending.port,
                scheme = pending.scheme,
                token = pending.token,
                cfClientId = pending.cfClientId,
                cfClientSecret = pending.cfClientSecret,
                isLoopback = pending.isLoopback,
                authority = pending.authority,
                remoteConfirmed = true,
            )
        }
    }

    /** The user declined the remote-connect confirmation; drop the attempt. */
    fun dismissRemoteConfirm() {
        localStage = ConnectStage.Idle
        _state.update {
            it.copy(pendingRemoteConfirm = null, stage = ConnectStage.Idle, failure = null)
        }
    }

    /**
     * Probe, remember and connect one endpoint with the scheme and auth headers it will be reached
     * by. Shared by the LAN manual path and the remote path after confirmation, so both speak the
     * same wire and fail with the same diagnosis.
     */
    private suspend fun attemptConnect(
        host: String,
        port: Int,
        scheme: String,
        token: String?,
        cfClientId: String?,
        cfClientSecret: String?,
        isLoopback: Boolean,
        authority: String,
        remoteConfirmed: Boolean,
    ) {
        localStage = ConnectStage.Reaching
        _state.update { it.copy(stage = ConnectStage.Reaching) }
        val probeConfig = HostConfig(
            id = "temp-${hostLabel(host)}",
            name = hostLabel(host),
            host = host,
            port = port,
            isLoopback = isLoopback,
            useTls = (scheme == "https"),
            scheme = scheme,
            authToken = token,
            cfClientId = cfClientId,
            cfClientSecret = cfClientSecret,
            remoteConfirmed = remoteConfirmed,
        )
        val outcome = discoveryEngine.probeOutcome(
            host = host,
            port = port,
            timeouts = ProbeTimeouts.Manual,
            preflight = true,
            useTls = (scheme == "https"),
            config = probeConfig,
        )
        if (outcome !is ProbeOutcome.Reachable) {
            fail(ConnectFailure.from(outcome), authority)
            return
        }
        hostsStore.addKnownPort(port)
        val config = hostsStore.rememberHost(
            name = hostLabel(host),
            host = host,
            port = port,
            isLoopback = isLoopback,
            description = outcome.description,
            scheme = scheme,
            authToken = token,
            cfClientId = cfClientId,
            cfClientSecret = cfClientSecret,
            remoteConfirmed = remoteConfirmed,
        )
        connectTo(config)
    }

    /** Stop a connect attempt that the loop would otherwise keep retrying every few seconds. */
    fun cancelConnect() {
        // Keep ownership: the manager resets its own state on disconnect, but claiming Idle here
        // means the screen is never briefly re-driven by a trailing emission.
        localStage = ConnectStage.Idle
        connectionManager.disconnect()
        _state.update { it.copy(stage = ConnectStage.Idle, failure = null, retrying = false) }
    }

    /**
     * End the attempt with a reason, and mark the attempted host unreachable if there was one.
     *
     * Holds [localStage] at Idle rather than releasing it: this outcome was decided here, and a
     * manager emission from an earlier attempt must not replace it or revive `connecting`.
     */
    private fun fail(failure: ConnectFailure, attempted: String?) {
        localStage = ConnectStage.Idle
        _state.update {
            it.copy(
                stage = ConnectStage.Idle,
                failure = failure,
                attempted = attempted ?: it.attempted,
                retrying = false,
                recentStatus = attempted?.let { key -> it.recentStatus + (key to HostProbe.Unreachable) }
                    ?: it.recentStatus,
            )
        }
    }

    /**
     * Connect to a remembered host, and say so when it does not work.
     *
     * Progress and failure now arrive on the manager's own state flow, which the collector in
     * `init` folds in — so a tap on a dead Recent entry reports the same diagnosis as a manual
     * attempt instead of looking like an inert button.
     */
    fun connectTo(host: HostConfig) {
        localStage = null
        _state.update {
            it.copy(
                stage = ConnectStage.OpeningStreams,
                failure = null,
                attempted = host.authority,
                retrying = false,
            )
        }
        viewModelScope.launch { connectionManager.connect(host) }
    }

    fun connectDiscovered(discovered: DiscoveredHost) {
        _state.update { it.copy(failure = null, attempted = discovered.authority) }
        viewModelScope.launch {
            hostsStore.addKnownPort(discovered.port)
            val config = hostsStore.rememberHost(
                name = hostLabel(discovered.host),
                host = discovered.host,
                port = discovered.port,
                isLoopback = false,
                description = discovered.description,
            )
            connectTo(config)
        }
    }

    fun forget(host: HostConfig) {
        viewModelScope.launch { hostsStore.removeHost(host.id) }
    }

    fun clearError() = _state.update { it.copy(failure = null) }

    /**
     * A readable name for an address: reverse DNS when the network offers one, the address itself
     * otherwise. Storing the IP as the name made a card's two lines say the same thing twice.
     */
    private suspend fun hostLabel(address: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val canonical = InetAddress.getByName(address).canonicalHostName
            canonical.takeIf { it.isNotBlank() && it != address }?.substringBefore('.') ?: address
        }.getOrDefault(address)
    }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        const val DEFAULT_PORT = 3080
        const val DEFAULT_HTTP_PORT = 80
        const val DEFAULT_HTTPS_PORT = 443
    }
}
