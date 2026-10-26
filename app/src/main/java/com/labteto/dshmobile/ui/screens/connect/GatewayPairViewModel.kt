package com.labteto.dshmobile.ui.screens.connect

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.labteto.dshmobile.connection.ConnectionManager
import com.labteto.dshmobile.connection.DeviceCredentialStore
import com.labteto.dshmobile.connection.GatewayIdentity
import com.labteto.dshmobile.connection.GatewayIdentityStore
import com.labteto.dshmobile.connection.GatewaySessions
import com.labteto.dshmobile.connection.HostsStore
import com.labteto.dshmobile.core.wire.MobileAccess
import com.labteto.dshmobile.core.wire.MobileAccessInvite
import com.labteto.dshmobile.core.wire.MobileAccessInviteResult
import com.labteto.dshmobile.core.wire.MobileAccessMetadata
import com.labteto.dshmobile.core.wire.MobileAccessPairing
import com.labteto.dshmobile.core.wire.MobileAccessTls
import com.labteto.dshmobile.core.wire.NativePairOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Base64
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Pairing with a `dsh-mobile` gateway, as a sheet over the connect screen.
 *
 * Upstream 0.18.0 replaced its pair screen with a one-step "paste the `dsh web` line" flow, and that
 * address is exactly the wrong entry point for a gateway: a gateway refuses a bare `/api` call
 * until this device carries a session, and it issues that session only in exchange for a pairing
 * key its own panel prints. So the gateway keeps its own small flow rather than being folded into
 * the harness one, where it would have to lie about what the field means.
 *
 * The order is the security story and mirrors the old screen: read the gateway's metadata over a
 * connection that trusts anything (nothing is sent), fetch the CA it serves, check that against the
 * fingerprint the key already carries, and only then spend the single-use claim over a pinned
 * connection. A key for a different computer fails before the code is used.
 */
@HiltViewModel
class GatewayPairViewModel @Inject constructor(
    private val hostsStore: HostsStore,
    private val credentials: DeviceCredentialStore,
    private val identities: GatewayIdentityStore,
    private val sessions: GatewaySessions,
    private val connectionManager: ConnectionManager,
    private val okHttpClient: okhttp3.OkHttpClient,
) : ViewModel() {

    data class UiState(
        /** The pasted app key or pairing link. */
        val keyOrLink: String = "",
        /** Used only when [keyOrLink] is a bare key, which names the computer but not its address. */
        val address: String = "",
        val deviceName: String = defaultGatewayDeviceName(),
        val busy: Boolean = false,
        val message: GatewayPairMessage? = null,
        val failed: Boolean = false,
        /**
         * Set once this device is enrolled, so the sheet can close.
         *
         * Without it the dialog stayed open after a successful claim, and a second tap on "Pair"
         * sent the same single-use key again. The gateway answers 401 to that — it cannot tell a
         * spent key from a wrong one — so a pairing that had already worked reported "no longer
         * valid" and the device appeared on the computer while the phone said it had failed.
         */
        val pairedHostId: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun setKeyOrLink(value: String) = _state.update {
        // A pasted link carries its own address; showing both fields filled invites editing the
        // one that will be ignored.
        val invite = (MobileAccess.parsePairingInput(value) as? MobileAccessInviteResult.Valid)?.invite
        it.copy(keyOrLink = value, address = invite?.origin ?: it.address, message = null, failed = false)
    }

    /**
     * A QR was read.
     *
     * It is claimed immediately rather than filling the form and waiting for a tap: the key is
     * single-use and expires in minutes, and pointing the camera at one is already a yes.
     */
    fun scanned(text: String) {
        setKeyOrLink(text)
        pair()
    }

    fun setAddress(value: String) = _state.update { it.copy(address = value, message = null, failed = false) }

    fun setDeviceName(value: String) = _state.update { it.copy(deviceName = value) }

    fun clearMessage() = _state.update { it.copy(message = null, failed = false) }

    /**
     * Acknowledge the [UiState.pairedHostId] signal.
     *
     * It has to be consumed, not just read. This view model outlives the dialog (it is scoped to the
     * activity, like every Hilt view model behind this screen), so a latched value closes the *next*
     * dialog the instant it opens — which is exactly how "the pairing window vanished and I cannot
     * pair again" happens. The key and any message go with it: they describe a pairing that is over.
     */
    fun acknowledgePaired() = _state.update {
        it.copy(pairedHostId = null, keyOrLink = "", address = "", message = null, failed = false)
    }

    /** Read, verify, claim, remember, connect. */
    fun pair() {
        val current = _state.value
        if (current.busy) return
        val invite = (MobileAccess.parsePairingInput(current.keyOrLink.trim()) as? MobileAccessInviteResult.Valid)?.invite
        if (invite == null) {
            _state.update { it.copy(message = GatewayPairMessage.NotAKey, failed = true) }
            return
        }
        val origin = invite.origin ?: MobileAccess.originOf(withScheme(current.address.trim()))
        if (origin == null) {
            _state.update { it.copy(message = GatewayPairMessage.AddressRequired, failed = true) }
            return
        }
        _state.update { it.copy(busy = true, message = null, failed = false) }
        viewModelScope.launch {
            // Already paired with this computer? Then the key is only a way to name it again, and
            // spending it would fail: a used key answers 401, which reads as "expired" even though
            // the pairing worked. Reconnect instead, so re-scanning an old QR is harmless.
            val knownId = identities.all().entries
                .firstOrNull { it.value.instanceId == invite.instanceId }
                ?.key
            val knownHost = knownId?.let { id -> hostsStore.hosts.first().firstOrNull { it.id == id } }
            if (knownHost != null) {
                sessions.ensure(knownHost.id, knownHost.baseUrl)
                _state.update { it.copy(busy = false, pairedHostId = knownHost.id) }
                connectionManager.connect(knownHost)
                return@launch
            }
            runCatching { claim(invite, origin, current.deviceName.ifBlank { defaultGatewayDeviceName() }) }
                .onFailure { error ->
                    _state.update {
                        val reason = (error as? GatewayPairFailure)?.reason
                        it.copy(busy = false, message = reason ?: GatewayPairMessage.Failed(error.message), failed = true)
                    }
                }
        }
    }

    private suspend fun claim(invite: MobileAccessInvite, origin: String, name: String) {
        val secure = origin.startsWith("https://")
        // Reads only, on a connection that cannot be verified yet: nothing about this device is sent.
        val probe = if (secure) MobileAccessTls.unpinnedClient(okHttpClient) else okHttpClient
        val metadata: MobileAccessMetadata = MobileAccessPairing.metadata(origin, probe)
            ?: fail(GatewayPairMessage.NotAGateway(origin))

        val caDer = if (secure) MobileAccessPairing.caCertificate(origin, probe) else null
        val fingerprint = caDer?.let { MobileAccessPairing.certificateFingerprint(it) }
        if (fingerprint != null && fingerprint != invite.instanceId) {
            fail(GatewayPairMessage.CaMismatch(origin))
        }
        if (fingerprint == null && invite.pinsPairingCa) {
            fail(GatewayPairMessage.CaMismatch(origin))
        }
        val claimClient = when {
            caDer != null -> MobileAccessTls.caPinnedClient(okHttpClient, caDer)
            else -> okHttpClient
        }
        when (val outcome = MobileAccessPairing.nativePair(origin, invite.token, name, claimClient)) {
            is NativePairOutcome.Paired -> enrol(origin, invite, outcome.response, caDer, fingerprint)
            NativePairOutcome.Rejected -> fail(GatewayPairMessage.Rejected)
            NativePairOutcome.HostRefused -> fail(GatewayPairMessage.HostRefused(origin))
            is NativePairOutcome.RateLimited -> fail(GatewayPairMessage.RateLimited(outcome.retryAfterSeconds))
            is NativePairOutcome.Unreachable ->
                fail(GatewayPairMessage.Unreachable(origin))
        }
    }

    /**
     * Store the credential, then connect.
     *
     * Order matters: the endpoint has to exist to have an id, the identity and the device token are
     * stored against that id, and only then does the session make sense.
     */
    private suspend fun enrol(
        origin: String,
        invite: MobileAccessInvite,
        response: com.labteto.dshmobile.core.wire.NativePairResponse,
        caDer: ByteArray?,
        fingerprint: String?,
    ) {
        val url = origin.toHttpUrl()
        val config = hostsStore.rememberHost(
            name = url.host,
            host = url.host,
            port = url.port,
            isLoopback = false,
            scheme = url.scheme,
        )
        identities.put(
            config.id,
            GatewayIdentity(
                deviceId = response.deviceId,
                // The invitation's id, not the answer's: a gateway that answered with a different
                // one is a different computer than the key named.
                instanceId = invite.instanceId,
                caDerB64 = caDer?.let { Base64.getEncoder().encodeToString(it) },
                caFingerprint = fingerprint,
            ),
        )
        if (response.deviceToken.isNotBlank()) credentials.put(config.id, response.deviceToken)
        sessions.adopt(config.id, response.sessionToken, response.csrfToken, response.sessionExpiresAt)
        _state.update { it.copy(busy = false, message = null, failed = false, pairedHostId = config.id) }
        connectionManager.connect(config)
    }

    private fun fail(message: GatewayPairMessage): Nothing = throw GatewayPairFailure(message)

    /** A typed host with no scheme gets https: a bare address here is a tunnel, not a LAN harness. */
    private fun withScheme(value: String): String =
        if (value.contains("://")) value else "https://$value"

}

/** What the gateway's device list shows: the phone's own model name, which the user recognises. */
private fun defaultGatewayDeviceName(): String = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android"

private fun String.toHttpUrl(): okhttp3.HttpUrl =
    toHttpUrlOrNull() ?: throw IllegalArgumentException("invalid-url")

/** Why a pairing attempt stopped, in a form the sheet can translate. */
sealed interface GatewayPairMessage {
    data object NotAKey : GatewayPairMessage
    data object AddressRequired : GatewayPairMessage
    data class NotAGateway(val origin: String) : GatewayPairMessage
    data class CaMismatch(val origin: String) : GatewayPairMessage
    data object Rejected : GatewayPairMessage
    data class HostRefused(val origin: String) : GatewayPairMessage
    data class RateLimited(val seconds: Long) : GatewayPairMessage
    data class Unreachable(val origin: String) : GatewayPairMessage
    data class Failed(val detail: String?) : GatewayPairMessage
}

/** Carries a [GatewayPairMessage] out of [GatewayPairViewModel.claim] as a failure. */
class GatewayPairFailure(val reason: GatewayPairMessage) : IllegalStateException()
