package com.labteto.dshmobile.connection

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.labteto.dshmobile.core.wire.MobileAccess
import com.labteto.dshmobile.core.wire.MobileAccessCard
import com.labteto.dshmobile.core.wire.WireJson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds `dsh-relay` listeners and `dsh-mobile` gateways by their mDNS advertisement.
 *
 * Both products publish under their own DNS-SD type and both carry everything needed to address
 * them: a relay says whether it terminates TLS and which key to pin, and a gateway says the origin
 * it answers on plus the instance id that names it. Either removes the subnet sweep entirely — a
 * browse answers in a second or two where knocking 254 addresses takes tens of them.
 *
 * A gateway is also found by *broadcast*, which is the path its own app uses: the phone sends
 * [MobileAccess.DISCOVERY_QUERY] as a UDP datagram to the gateway's port, and every computer that
 * hears it answers with a device card. That reaches a gateway whose mDNS Android's resolver drops,
 * and mDNS reaches one that a firewall keeps the broadcast from, so both run and the union is what
 * the caller sees.
 *
 * A relay publishes `_dsh._tcp` with everything a client needs before it connects: the port, whether
 * the primary listener terminates TLS, and the key to pin. That removes the subnet sweep entirely —
 * a browse answers in a second or two where knocking 254 addresses takes tens of them, and it finds
 * a relay on a subnet the sweep would never look at.
 *
 * Nothing depends on it. The relay's `mdns` flag can be off, some networks drop multicast, and
 * Android's own resolver is unreliable enough that treating a browse as authoritative would make
 * discovery worse than the sweep it replaces. So this reports what it finds and the caller falls
 * back.
 *
 * Only IPv4 results are kept, matching [DiscoveryEngine] — the rest of the app builds authorities as
 * `host:port`, which an IPv6 literal cannot be written as without brackets.
 */
@Singleton
class MdnsDiscovery @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Browse for [windowMs], reporting each relay as it resolves.
     *
     * The window is the whole budget: mDNS has no "that is all of them" and a browse left running
     * would keep the multicast socket and its wake-ups alive for as long as the screen is open.
     *
     * @param onFound fires per relay, so a card can appear before the window closes.
     * @return every distinct relay resolved inside the window.
     */
    suspend fun browse(
        windowMs: Long = DEFAULT_WINDOW_MS,
        onFound: (DiscoveredHost) -> Unit = {},
    ): List<DiscoveredHost> = withContext(Dispatchers.IO) {
        val nsd = runCatching { context.getSystemService(Context.NSD_SERVICE) as? NsdManager }.getOrNull()
            ?: return@withContext emptyList()
        val found = CopyOnWriteArrayList<DiscoveredHost>()
        // Resolution is serialized behind this: on several Android versions a second concurrent
        // resolveService fails the first with FAILURE_ALREADY_ACTIVE rather than queueing.
        val resolving = Mutex()
        val queue = Channel<Advertisement>(Channel.UNLIMITED)
        val listeners = mutableListOf<NsdManager.DiscoveryListener>()

        // One listener per service type, because NsdManager binds a discovery to the type it was
        // started with. They share the queue, so a gateway and a relay found in the same window are
        // resolved in the order they arrive rather than in two separate phases.
        for (kind in ServiceKind.entries) {
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String?) = Unit
                override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                    serviceInfo?.let { queue.trySend(Advertisement(kind, it)) }
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo?) = Unit
                override fun onDiscoveryStopped(serviceType: String?) = Unit
                override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) = Unit

                override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) = Unit
            }
            val started = runCatching {
                nsd.discoverServices(kind.serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            }.isSuccess
            if (started) listeners.add(listener)
        }
        if (listeners.isEmpty()) return@withContext emptyList()

        try {
            withTimeoutOrNull(windowMs) {
                supervisorScope {
                    launch {
                        for (advertisement in queue) {
                            val host = resolving.withLock { resolve(nsd, advertisement) } ?: continue
                            if (found.none { it.authority == host.authority }) {
                                found.add(host)
                                onFound(host)
                            }
                        }
                    }
                }
            }
        } finally {
            queue.close()
            // Best-effort: an already-stopped discovery throws IllegalArgumentException, and a
            // browse that outlives its window is worse than a noisy stop.
            listeners.forEach { runCatching { nsd.stopServiceDiscovery(it) } }
        }
        found.toList()
    }

    /**
     * Ask every computer on this device's subnets whether it is running a gateway.
     *
     * The datagram goes to the gateway's *listener* port, which is 3443 unless its setup moved it —
     * so a gateway on a custom port is found by mDNS and not here. That is the trades: this answers
     * even when multicast is being dropped, and it costs one socket and one packet per interface.
     *
     * A broadcast is best-effort by nature. No reply inside [timeoutMs] is an empty list, not a
     * failure, and the caller merges this with whatever the browse returned.
     */
    suspend fun broadcastGateways(
        timeoutMs: Long = BROADCAST_WINDOW_MS,
        port: Int = MobileAccess.DEFAULT_PORT,
        onFound: (DiscoveredHost) -> Unit = {},
    ): List<DiscoveredHost> = withContext(Dispatchers.IO) {
        val query = MobileAccess.DISCOVERY_QUERY.toByteArray(Charsets.US_ASCII)
        val found = CopyOnWriteArrayList<DiscoveredHost>()
        runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = SOCKET_READ_MS
                val targets = broadcastTargets(port)
                targets.forEach { target ->
                    runCatching { socket.send(DatagramPacket(query, query.size, target)) }
                }
                val deadline = System.currentTimeMillis() + timeoutMs
                val buffer = ByteArray(MAX_CARD_BYTES)
                // `isActive` as well as the deadline: leaving the discovery screen cancels this
                // coroutine, and without the check the loop kept the socket and its blocking reads
                // alive for the rest of the window — up to 2.5s of a retained multicast socket for
                // a screen nobody is looking at.
                while (currentCoroutineContext().isActive && System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    val received = runCatching { socket.receive(packet); true }.getOrDefault(false)
                    if (!received) continue
                    val card = runCatching {
                        WireJson.decodeFromString(
                            MobileAccessCard.serializer(),
                            String(packet.data, 0, packet.length, Charsets.UTF_8),
                        )
                    }.getOrNull() ?: continue
                    asDiscovered(card, packet.address?.hostAddress)?.let { host ->
                        // Reported per reply, so a card can appear while the window is still open —
                        // the same reason the browse streams rather than batch-collecting.
                        if (found.none { it.authority == host.authority }) {
                            found.add(host)
                            onFound(host)
                        }
                    }
                }
            }
        }
        found.toList()
    }

    /**
     * Per-interface broadcast addresses for [port].
     *
     * The interface's own broadcast address is used rather than a synthesized `x.y.z.255`: a /16 or a
     * /22 network has a different one, and guessing wrong means the packet goes nowhere while the
     * code reports having sent it.
     */
    private fun broadcastTargets(port: Int): List<InetSocketAddress> {
        val targets = mutableListOf<InetSocketAddress>()
        runCatching {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                for (address in iface.interfaceAddresses) {
                    val broadcast = address.broadcast ?: continue
                    targets.add(InetSocketAddress(broadcast, port))
                }
            }
        }
        return targets
    }

    /** Read a broadcast card as a candidate gateway. */
    private fun asDiscovered(card: MobileAccessCard, sourceAddress: String?): DiscoveredHost? {
        if (card.protocol != MobileAccess.DISCOVERY_PROTOCOL) return null
        val instanceId = card.instanceId?.takeIf { MobileAccess.isInstanceId(it) } ?: return null
        val origin = card.origin
        val host = origin?.let { MobileAccess.originOf(it)?.substringAfter("://") }
            ?.substringBefore(':')
            ?.takeIf { it.isNotBlank() }
            ?: sourceAddress ?: return null
        val port = origin?.let { originPort(it) } ?: card.port ?: MobileAccess.DEFAULT_PORT
        val tls = origin?.startsWith("https://") ?: true
        return DiscoveredHost(
            host = host,
            port = port,
            description = null,
            useTls = tls,
            isGateway = true,
            instanceId = instanceId,
            deviceName = card.deviceName,
        )
    }

    /** The port an origin names, or the scheme's default when it names none. */
    private fun originPort(origin: String): Int {
        val authority = MobileAccess.originOf(origin)?.substringAfter("://").orEmpty()
        val explicit = authority.substringAfterLast(':', "").toIntOrNull()
        if (explicit != null && ':' in authority) return explicit
        return if (origin.startsWith("https://")) 443 else 80
    }

    /**
     * Resolve one advertisement, or null when it does not resolve or is not a usable endpoint.
     *
     * An unresolved advertisement keeps its raw, un-resolved fields, and that is deliberate: the
     * gateway's TXT record already carries the origin and the instance id, so a resolver that fails
     * to add an address (`onResolveFailed` on a busy network is routine) still leaves the card
     * usable — the origin is the address.
     */
    private suspend fun resolve(nsd: NsdManager, advertisement: Advertisement): DiscoveredHost? {
        val resolved = withTimeoutOrNull(RESOLVE_MS) { awaitResolve(nsd, advertisement.info) }
            ?: advertisement.info
        return when (advertisement.kind) {
            ServiceKind.RELAY -> asRelay(resolved)
            ServiceKind.GATEWAY -> asGateway(resolved)
        }
    }

    /**
     * Read a resolved advertisement as a `dsh-mobile` gateway, or null when it is not one.
     *
     * The TXT records carry the addressing, so the resolved address is only a fallback: `origin`
     * says which scheme and which port the listener actually answers on, and the gateway's own
     * setup may have moved either. `protocol` is checked rather than assumed — it is the version of
     * this advertisement's vocabulary, and a newer one may mean fields this build would misread.
     */
    private fun asGateway(info: NsdServiceInfo): DiscoveredHost? {
        val txt = info.attributes.orEmpty()
        fun record(key: String): String? = txt[key]?.let { String(it, Charsets.UTF_8) }
        if (record("protocol") != MobileAccess.DISCOVERY_PROTOCOL.toString()) return null
        val instanceId = record("instanceId")?.takeIf { MobileAccess.isInstanceId(it) } ?: return null

        val origin = record("origin")
        val originHost = origin?.let { MobileAccess.originOf(it)?.substringAfter("://") }
            ?.substringBefore(':')
            ?.takeIf { it.isNotBlank() }
        @Suppress("DEPRECATION")
        val resolved = (info.host as? Inet4Address)?.hostAddress
        val host = originHost ?: resolved ?: return null
        val port = origin?.let { originPort(it) } ?: info.port.takeIf { it in 1..65535 }
            ?: MobileAccess.DEFAULT_PORT
        return DiscoveredHost(
            host = host,
            port = port,
            description = null,
            // A gateway that terminated its own TLS advertises an https origin; one behind a tunnel
            // or a reverse proxy advertises the public name, which the browse cannot see anyway.
            useTls = origin?.startsWith("https://") ?: true,
            isGateway = true,
            instanceId = instanceId,
            deviceName = record("deviceName"),
        )
    }

    @Suppress("DEPRECATION")
    private suspend fun awaitResolve(nsd: NsdManager, service: NsdServiceInfo): NsdServiceInfo? {
        // `resolveService` is deprecated for `registerServiceInfoCallback` on API 34+. The
        // replacement does not exist below 34 and this app supports 26, so the deprecated call is
        // the one that works everywhere; there is nothing here that the newer API would do better.
        val answer = Channel<NsdServiceInfo?>(Channel.CONFLATED)
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                answer.trySend(null)
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                answer.trySend(serviceInfo)
            }
        }
        // Only the call is guarded: `resolveService` throws when a resolve is already in flight,
        // which the mutex above should prevent but which is a crash if it ever slips. The receive is
        // deliberately outside, so the enclosing timeout's cancellation propagates instead of being
        // swallowed as a null result and leaving this coroutine running in a cancelled state.
        if (runCatching { nsd.resolveService(service, listener) }.isFailure) return null
        return answer.receive()
    }

    /**
     * Read a resolved advertisement as a relay, or null when it is not one this client can use.
     *
     * The TXT records are checked rather than assumed: `_dsh._tcp` is this ecosystem's service type,
     * not this plugin's, so an advertisement without `relay=dsh-relay` is somebody else's and an
     * unrecognised `v` may mean fields whose meaning this build would get wrong.
     */
    private fun asRelay(info: NsdServiceInfo): DiscoveredHost? {
        val txt = info.attributes.orEmpty()
        fun record(key: String): String? = txt[key]?.let { String(it, Charsets.UTF_8) }
        if (record("relay") != RELAY_SERVICE) return null
        if (record("v") != SUPPORTED_VERSION) return null

        @Suppress("DEPRECATION")
        val address = info.host as? Inet4Address ?: return null
        val host = address.hostAddress ?: return null
        val port = info.port.takeIf { it in 1..65535 } ?: return null

        val tls = record("tls") ?: TLS_OFF
        val pin = record("pin")?.takeIf { it.isNotBlank() }
        return DiscoveredHost(
            host = host,
            port = port,
            // A relay never answers `host.describe` to an unpaired device, so there is nothing to
            // cache here. The card says "pair with this" rather than "connect", which is the truth.
            description = null,
            useTls = tls != TLS_OFF,
            fingerprint = pin,
            isRelay = true,
        )
    }

    /** Which product an advertisement came from, and the type it was browsed under. */
    private enum class ServiceKind(val serviceType: String) {
        /** `dsh-relay` publishes this one. */
        RELAY("_dsh._tcp."),

        /** `dsh-mobile` publishes this one; NsdManager wants the trailing dot. */
        GATEWAY("_dsh-mobile._tcp."),
    }

    /** One advertisement, tagged with the type it was found under. */
    private data class Advertisement(val kind: ServiceKind, val info: NsdServiceInfo)

    private companion object {
        /** DNS-SD type the relay publishes under. NsdManager wants the trailing dot. */
        const val SERVICE_TYPE = "_dsh._tcp"

        /** TXT `relay` value that identifies this plugin rather than some other `_dsh._tcp` service. */
        const val RELAY_SERVICE = "dsh-relay"

        /** TXT `v` this build understands. */
        const val SUPPORTED_VERSION = "1"

        /** TXT `tls` value meaning the primary listener serves plaintext. */
        const val TLS_OFF = "off"

        /** How long a browse runs before it is called done. */
        const val DEFAULT_WINDOW_MS = 4_000L

        /** Per-advertisement resolve budget, so one silent responder cannot eat the window. */
        const val RESOLVE_MS = 2_000L

        /** How long to keep listening for broadcast replies. */
        const val BROADCAST_WINDOW_MS = 2_500L

        /** Per-receive wait, so the loop can notice its own deadline between packets. */
        const val SOCKET_READ_MS = 400

        /** A discovery card is a few hundred bytes; anything larger is not one. */
        const val MAX_CARD_BYTES = 2_048
    }
}
