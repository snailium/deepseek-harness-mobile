package com.labteto.dshmobile.ui.screens.pair

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.labteto.dshmobile.connection.ConnectMode
import com.labteto.dshmobile.connection.ConnectionManager
import com.labteto.dshmobile.connection.GatewayIdentity
import com.labteto.dshmobile.connection.GatewaySessions
import com.labteto.dshmobile.connection.HarnessClientFactory
import com.labteto.dshmobile.connection.HostConfig
import com.labteto.dshmobile.connection.HostsStore
import com.labteto.dshmobile.connection.RelayCredentialStore
import com.labteto.dshmobile.connection.RelayIdentity
import com.labteto.dshmobile.core.wire.MobileAccess
import com.labteto.dshmobile.core.wire.MobileAccessInvite
import com.labteto.dshmobile.core.wire.MobileAccessInviteResult
import com.labteto.dshmobile.core.wire.MobileAccessPairing
import com.labteto.dshmobile.core.wire.MobileAccessTls
import com.labteto.dshmobile.core.wire.NativePairOutcome
import com.labteto.dshmobile.core.wire.ObservedKey
import com.labteto.dshmobile.core.wire.PairingPayloadResult
import com.labteto.dshmobile.core.wire.RelayOrigin
import com.labteto.dshmobile.core.wire.RelayPairOutcome
import com.labteto.dshmobile.core.wire.RelayPairing
import com.labteto.dshmobile.core.wire.RelayPairingPayload
import com.labteto.dshmobile.core.wire.RelayTls
import com.labteto.dshmobile.core.wire.TransportFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Base64
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import javax.inject.Inject

/** How far the pairing attempt has got. */
enum class PairStage { Idle, Claiming, Paired }

/**
 * How the relay's identity was established.
 *
 * The difference is real and the screen says so. A scanned QR carries the relay's key, so the very
 * first byte this device sends is verified against it. A typed address carries no key at all — the
 * relay only reveals one in its answer to the claim — so the certificate is trusted on first
 * contact. Both end with the same pin stored; only one of them proved it.
 */
enum class KeyProvenance {
    /** No TLS at all: the relay is running `tls: off`, and the token will travel in the clear. */
    Plaintext,

    /** The key came from the QR and was verified before anything was sent. */
    Verified,

    /** The key was whatever answered at a typed address. */
    TrustedOnFirstUse,
}

data class PairUiState(
    val stage: PairStage = PairStage.Idle,
    val url: String = "",
    val code: String = "",
    val deviceName: String = defaultDeviceName(),
    /** Set once a QR has been read, so the screen can show what it is about to pair with. */
    val scanned: RelayPairingPayload? = null,
    /** Set once a `dsh-mobile` invitation has been read, for the same reason. */
    val gatewayInvite: MobileAccessInvite? = null,
    val failure: PairFailure? = null,
    /**
     * The endpoint that was just enrolled; the screen hands this back and closes.
     *
     * A one-shot signal, cleared by [PairViewModel.acknowledgePaired] as the screen acts on it.
     * It cannot be left set: this ViewModel is scoped to the activity rather than to the screen,
     * so it outlives a closed pairing screen — and a latched value would close the *next* one the
     * instant it opened, which looks exactly like the button not working.
     */
    val paired: HostConfig? = null,
) {
    val busy: Boolean get() = stage == PairStage.Claiming
}

/** Why a pairing attempt did not produce a credential. */
sealed interface PairFailure {
    /** The scanned code is not a relay pairing payload. */
    data object NotAPairingCode : PairFailure

    /** A pairing payload from a relay newer than this build. */
    data class TooNew(val version: Int) : PairFailure

    /** The code had already expired before it was sent, so there was nothing to try. */
    data object Expired : PairFailure

    /** The relay refused the code: wrong, expired, or already claimed. */
    data object Rejected : PairFailure

    /** Locked out for [seconds]. */
    data class RateLimited(val seconds: Long) : PairFailure

    /** Something answered but does not serve the claim endpoint. */
    data class NotARelay(val authority: String) : PairFailure

    /**
     * The relay is there and its fence refused the address it was reached by.
     *
     * Nothing about the code was wrong — it was never read. Only the relay's operator can widen
     * the set of authorities it answers to, so this is the one pairing failure the phone cannot
     * resolve on its own.
     */
    data class HostRefused(val authority: String) : PairFailure

    /** Nothing usable answered. */
    data class Unreachable(val authority: String) : PairFailure

    /** The key at the address is not the one the QR named. */
    data class CertificateMismatch(val authority: String) : PairFailure

    /**
     * A `dsh-mobile` key was given but no address to use it against.
     *
     * A bare app key says *what* to pair with and not *where* it is — the address comes from a LAN
     * discovery card or from the field. A link carries its own, so this never happens for a QR.
     */
    data object AddressRequired : PairFailure

    /** Something answered, but nothing there serves the `dsh-mobile` routes. */
    data class NotAGateway(val authority: String) : PairFailure

    /**
     * The certificate the gateway served is not the one its pairing input named.
     *
     * Only reachable for a LAN gateway, whose `instanceId` *is* the fingerprint of its CA. Either
     * the code belongs to a different computer, or something else is answering on that address —
     * and the second is why this is a refusal rather than a warning.
     */
    data class GatewayCaMismatch(val authority: String) : PairFailure

    /** The typed address is not a URL this app can address. */
    data object InvalidUrl : PairFailure

    /** No code was typed. */
    data object InvalidCode : PairFailure
}

/**
 * Enrolling this device with a `dsh-relay`.
 *
 * One exchange, once: the relay mints a bearer token, shows it exactly once, and keeps only a keyed
 * hash afterwards. Everything downstream — the transport, both downlinks, discovery — depends on
 * that token having been stored, so the order here is deliberate: remember the endpoint first so it
 * has an id, store the credential against that id, and only then connect.
 */
@HiltViewModel
class PairViewModel @Inject constructor(
    private val hostsStore: HostsStore,
    private val credentials: RelayCredentialStore,
    private val clientFactory: HarnessClientFactory,
    private val connectionManager: ConnectionManager,
    private val gatewaySessions: GatewaySessions,
    private val okHttpClient: OkHttpClient,
) : ViewModel() {

    private val _state = MutableStateFlow(PairUiState())
    val state: StateFlow<PairUiState> = _state.asStateFlow()

    /** Prefill the address, e.g. when re-pairing a relay that revoked this device. */
    fun prefill(url: String) {
        if (_state.value.url.isNotBlank()) return
        _state.update { it.copy(url = url) }
    }

    fun setUrl(value: String) = _state.update { it.copy(url = value, failure = null) }

    /**
     * The code field, which two products fill differently.
     *
     * A relay's code is digits and nothing else, so those were the only characters this kept. A
     * `dsh-mobile` key is `<prefix>.<64 hex>.<token>` — dots and base64url — so filtering to digits
     * would silently delete it as it was typed or pasted, which looks exactly like a key that does
     * not work. Whitespace is still dropped: it is never part of either.
     */
    fun setCode(value: String) =
        _state.update { it.copy(code = value.filterNot { c -> c.isWhitespace() }, failure = null) }

    fun setDeviceName(value: String) = _state.update { it.copy(deviceName = value) }

    fun clearFailure() = _state.update { it.copy(failure = null) }

    /**
     * A QR was decoded.
     *
     * The claim runs immediately rather than filling the form and waiting for a tap: the code is
     * single-use and lives for five minutes by default, and a scan is already an unambiguous "yes".
     */
    fun onScanned(text: String) {
        // The two products have different payloads and the scanner cannot know which is on the
        // screen, so the relay grammar is tried first (it is the stricter of the two) and a
        // `dsh-mobile` invitation is what a non-match is then read as. Neither grammar accepts the
        // other's text, so the order is not a policy — it is just which one gets asked first.
        if (MobileAccess.parsePairingInput(text) is MobileAccessInviteResult.Valid &&
            RelayPairing.parsePayload(text) is PairingPayloadResult.NotAPairingCode
        ) {
            onGatewayScanned(text)
            return
        }
        when (val parsed = RelayPairing.parsePayload(text)) {
            is PairingPayloadResult.NotAPairingCode ->
                _state.update { it.copy(failure = PairFailure.NotAPairingCode) }
            is PairingPayloadResult.TooNew ->
                _state.update { it.copy(failure = PairFailure.TooNew(parsed.version)) }
            is PairingPayloadResult.Valid -> {
                val payload = parsed.payload
                if (!payload.isLive(System.currentTimeMillis())) {
                    _state.update { it.copy(failure = PairFailure.Expired) }
                    return
                }
                _state.update {
                    it.copy(scanned = payload, url = payload.url, code = payload.code, failure = null)
                }
                claim(payload.url, payload.code, payload.fingerprint)
            }
        }
    }

    /** Claim a code the user typed, against an address they typed. */
    fun submit() {
        val current = _state.value
        // A `dsh-mobile` invitation may be sitting in either field: a link is a URL and lands in
        // the address box when it is pasted, while a bare key has nowhere to go but the code box.
        val invite = (MobileAccess.parsePairingInput(current.url.trim()) as? MobileAccessInviteResult.Valid)?.invite
            ?: (MobileAccess.parsePairingInput(current.code.trim()) as? MobileAccessInviteResult.Valid)?.invite
        if (invite != null) {
            claimGateway(invite, current.url.trim(), _state.value.deviceName.ifBlank { defaultDeviceName() })
            return
        }
        val url = current.url.trim()
        if (url.toHttpUrlOrNull() == null) {
            _state.update { it.copy(failure = PairFailure.InvalidUrl) }
            return
        }
        if (current.code.isBlank()) {
            _state.update { it.copy(failure = PairFailure.InvalidCode) }
            return
        }
        // No fingerprint: a typed address carries no key, so this is the trust-on-first-use path.
        claim(url, current.code, fingerprint = null)
    }

    /**
     * A `dsh-mobile` invitation arrived from the scanner.
     *
     * A link carries its own address, so it can be claimed immediately — the code is single-use and
     * short-lived, and a scan is already an unambiguous yes. A bare key does not, so it waits: the
     * screen fills in what it knows and the address field stays the user's to complete.
     */
    private fun onGatewayScanned(text: String) {
        val invite = (MobileAccess.parsePairingInput(text) as? MobileAccessInviteResult.Valid)?.invite
            ?: run {
                _state.update { it.copy(failure = PairFailure.NotAPairingCode) }
                return
            }
        val declared = invite.origin
        if (declared == null) {
            _state.update { it.copy(gatewayInvite = invite, code = invite.token, failure = null) }
            return
        }
        _state.update {
            it.copy(gatewayInvite = invite, url = declared, code = invite.token, failure = null)
        }
        claimGateway(invite, declared, _state.value.deviceName.ifBlank { defaultDeviceName() })
    }

    /**
     * Enrol with a `dsh-mobile` gateway: pin what it serves, then claim, then connect.
     *
     * The order is the whole security story. The gateway is spoken to first over a connection that
     * trusts anything, because nothing is known about it yet — but nothing is *sent* on that
     * connection either: the metadata probe and the CA fetch are unauthenticated reads. The
     * certificate that comes back is then checked against the fingerprint in the invitation, which
     * the user already holds, and only a match is stored. The claim — the one request that carries
     * the single-use code — is made over a connection pinned to that CA (or, when the gateway
     * serves a publicly trusted certificate, over the ordinary system-trust client).
     *
     * That is why a wrong-computer code fails *before* the code is spent: the fingerprint is
     * checked first, and a mismatch is a refusal rather than a warning.
     */
    private fun claimGateway(invite: MobileAccessInvite, address: String, name: String) {
        val declared = invite.origin
        val origin = declared ?: MobileAccess.originOf(address)
        if (origin == null || origin.toHttpUrlOrNull() == null) {
            // A bare key with nothing usable to point it at. Not a malformed request — an
            // incomplete one, and the field it needs is the one the user has not filled in.
            _state.update { it.copy(gatewayInvite = invite, failure = PairFailure.AddressRequired) }
            return
        }
        val parsed = origin.toHttpUrlOrNull() ?: run {
            _state.update { it.copy(failure = PairFailure.InvalidUrl) }
            return
        }
        val authority = "${parsed.host}:${parsed.port}"
        _state.update {
            it.copy(
                stage = PairStage.Claiming,
                gatewayInvite = invite,
                url = origin,
                failure = null,
            )
        }
        viewModelScope.launch {
            val secure = parsed.scheme == "https"
            // Reads only, on a connection that cannot be verified yet; see the KDoc above.
            val probe = if (secure) MobileAccessTls.unpinnedClient(okHttpClient) else okHttpClient
            if (MobileAccessPairing.metadata(origin, probe) == null) {
                fail(PairFailure.NotAGateway(authority))
                return@launch
            }
            val caDer = if (secure) MobileAccessPairing.caCertificate(origin, probe) else null
            val fingerprint = caDer?.let { MobileAccessPairing.certificateFingerprint(it) }
            if (fingerprint != null && fingerprint != invite.instanceId) {
                fail(PairFailure.GatewayCaMismatch(authority))
                return@launch
            }
            if (fingerprint == null && invite.pinsPairingCa) {
                // A `dsh2.` key promises a CA to pin and the gateway served none, so the invitation
                // and the endpoint disagree. Continuing would mean trusting the platform store
                // after being told not to.
                fail(PairFailure.GatewayCaMismatch(authority))
                return@launch
            }
            val claimClient = when {
                caDer != null -> MobileAccessTls.caPinnedClient(okHttpClient, caDer)
                secure -> okHttpClient
                else -> okHttpClient
            }
            when (val outcome = MobileAccessPairing.nativePair(origin, invite.token, name, claimClient)) {
                is NativePairOutcome.Paired -> enrolGateway(parsed, invite, outcome, caDer, fingerprint)
                NativePairOutcome.Rejected -> fail(PairFailure.Rejected)
                NativePairOutcome.HostRefused -> fail(PairFailure.HostRefused(authority))
                is NativePairOutcome.RateLimited -> fail(PairFailure.RateLimited(outcome.retryAfterSeconds))
                is NativePairOutcome.Unreachable -> fail(
                    if (outcome.failure.kind == TransportFailure.CERTIFICATE_PIN) {
                        PairFailure.GatewayCaMismatch(authority)
                    } else {
                        PairFailure.Unreachable(authority)
                    },
                )
            }
        }
    }

    /** Store what the claim produced, in the order everything downstream depends on. */
    private suspend fun enrolGateway(
        url: HttpUrl,
        invite: MobileAccessInvite,
        outcome: NativePairOutcome.Paired,
        caDer: ByteArray?,
        fingerprint: String?,
    ) {
        val response = outcome.response
        val config = hostsStore.rememberHost(
            name = url.host,
            host = url.host,
            port = url.port,
            isLoopback = false,
            useTls = url.scheme == "https",
            gateway = GatewayIdentity(
                deviceId = response.deviceId,
                // The invitation's id, not the answer's: the answer repeats it, and a gateway that
                // answered with a different one is a different computer than the code named.
                instanceId = invite.instanceId,
                caDer = caDer?.let { Base64.getEncoder().encodeToString(it) },
                caFingerprint = fingerprint,
            ),
        )
        // The endpoint is remembered first so it has an id, the durable half of the credential is
        // stored against that id, and the session is adopted so the first connect needs no renewal.
        gatewaySessions.forget(config.id)
        if (response.deviceToken.isNotBlank()) credentials.put(config.id, response.deviceToken)
        gatewaySessions.adopt(config.id, response)
        // A paired endpoint lives on the "paired" half of the connect screen, and a gateway is one.
        // Without this the endpoint just enrolled would be filed under the LAN half and not shown.
        hostsStore.setSetting { it.copy(connectMode = ConnectMode.RELAY) }
        _state.update { it.copy(stage = PairStage.Paired, paired = config, failure = null) }
        connectionManager.connect(config)
    }

    private fun claim(url: String, code: String, fingerprint: String?) {
        _state.update { it.copy(stage = PairStage.Claiming, failure = null) }
        viewModelScope.launch {
            // Ask the address where the relay is before deciding how to talk to it. Since relay
            // 0.1.1 the harness's own port redirects `/relay` to the relay's listener, so the
            // address people already know — the one this app has asked them for since day one —
            // is a usable way in. The redirect has to be resolved here rather than followed by the
            // HTTP client: it names a different scheme and port, which decides both the pin to
            // present and what gets remembered, and a 302 would rewrite the claim's POST to a GET.
            val effective = when (val located = RelayPairing.locate(url, okHttpClient)) {
                is RelayOrigin.Redirected -> located.origin
                // `None` still gets an attempt. Health is unauthenticated and always served, so
                // this should not happen — but refusing to try on its say-so would turn one
                // unanswered probe into a pairing that cannot be completed at all.
                else -> url
            }
            val parsed = effective.toHttpUrlOrNull()
            if (parsed == null) {
                fail(PairFailure.InvalidUrl)
                return@launch
            }
            val authority = "${parsed.host}:${parsed.port}"
            val secure = parsed.scheme == "https"
            val observed = ObservedKey()
            val client = when {
                fingerprint != null -> clientFactory.httpClient(fingerprint)
                // The relay only reveals its key in the claim answer, so a typed https address has
                // to be spoken to before it can be verified. The screen labels this differently
                // from a scanned pairing for exactly that reason.
                secure -> RelayTls.trustOnFirstUseClient(okHttpClient, observed)
                else -> okHttpClient
            }
            val name = _state.value.deviceName.ifBlank { defaultDeviceName() }
            when (val outcome = RelayPairing.claim(effective, code, name, client)) {
                is RelayPairOutcome.Paired -> enrol(parsed, outcome, fingerprint ?: observed.pin)
                RelayPairOutcome.Rejected -> fail(PairFailure.Rejected)
                RelayPairOutcome.HostRefused -> fail(PairFailure.HostRefused(authority))
                RelayPairOutcome.NotARelay -> fail(PairFailure.NotARelay(authority))
                is RelayPairOutcome.RateLimited -> fail(PairFailure.RateLimited(outcome.retryAfterSeconds))
                is RelayPairOutcome.Unreachable -> fail(
                    if (outcome.kind == TransportFailure.CERTIFICATE_PIN) {
                        PairFailure.CertificateMismatch(authority)
                    } else {
                        PairFailure.Unreachable(authority)
                    },
                )
            }
        }
    }

    private suspend fun enrol(
        url: okhttp3.HttpUrl,
        outcome: RelayPairOutcome.Paired,
        pin: String?,
    ) {
        val response = outcome.response
        val config = hostsStore.rememberHost(
            name = url.host,
            host = url.host,
            port = url.port,
            isLoopback = false,
            basePath = url.encodedPath.trimEnd('/'),
            relay = RelayIdentity(
                deviceId = response.deviceId,
                useTls = url.scheme == "https",
                // The relay repeats its pin in the claim answer, which is what makes a typed
                // pairing pinnable at all. Preferring it over the observed key means a relay that
                // renews with the same key keeps working, and a mismatch between the two would
                // already have failed the request.
                fingerprint = response.fingerprint ?: pin,
                tokenExpiresAt = response.expiresAt,
            ),
        )
        credentials.put(config.id, response.token)
        _state.update { it.copy(stage = PairStage.Paired, paired = config, failure = null) }
        connectionManager.connect(config)
    }

    /**
     * Consume the [PairUiState.paired] signal.
     *
     * Called by the screen as it closes, so reopening pairing later starts from Idle rather than
     * re-firing the close it already handled.
     */
    fun acknowledgePaired() {
        _state.update { it.copy(stage = PairStage.Idle, paired = null) }
    }

    private fun fail(failure: PairFailure) {
        _state.update { it.copy(stage = PairStage.Idle, failure = failure) }
    }

}

/**
 * How this pairing would establish the relay's key, given what has been entered so far.
 *
 * A function of the state rather than a call into the view model, so the screen's notice recomposes
 * with the address field as the user types it.
 */
fun PairUiState.provenance(): KeyProvenance {
    val secure = url.trim().toHttpUrlOrNull()?.scheme == "https"
    return when {
        !secure -> KeyProvenance.Plaintext
        scanned?.fingerprint != null -> KeyProvenance.Verified
        else -> KeyProvenance.TrustedOnFirstUse
    }
}

/**
 * A name the operator will recognise in the relay's device list.
 *
 * `Build.MODEL` rather than a generated id: the list is read by a person deciding what to revoke,
 * and "Pixel 8" answers that question where a UUID does not.
 */
private fun defaultDeviceName(): String =
    listOfNotNull(Build.MANUFACTURER?.takeIf { Build.MODEL?.startsWith(it, ignoreCase = true) == false }, Build.MODEL)
        .joinToString(" ")
        .ifBlank { "Android device" }
