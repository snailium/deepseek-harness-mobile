package com.labteto.dshmobile.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One predicate decides which remembered hosts each mode lists and auto-connects to. The
 * failure it guards against is a host crossing modes: a loopback record offered a subnet check
 * it can never pass under local network, or a LAN host under "this phone".
 */
class ConnectModeTest {

    private val lan = HostConfig(id = "a", name = "desk", host = "192.168.1.20", port = 3080)
    private val loopback = HostConfig(id = "b", name = "127.0.0.1", host = "127.0.0.1", port = 3080, isLoopback = true)
    private val relay = HostConfig(
        id = "c", name = "relay", host = "192.168.1.20", port = 3443, useTls = true, relayDeviceId = "dev",
    )
    private val gateway = HostConfig(
        id = "d", name = "gateway", host = "192.168.1.30", port = 4443, useTls = true, gatewayDeviceId = "gw",
    )

    @Test
    fun `stored values read back, and anything else is local network`() {
        assertEquals(ConnectMode.LOOPBACK, ConnectMode.of("loopback"))
        assertEquals(ConnectMode.RELAY, ConnectMode.of("relay"))
        assertEquals(ConnectMode.LAN, ConnectMode.of("lan"))
        assertEquals(ConnectMode.LAN, ConnectMode.of(null))
        assertEquals(ConnectMode.LAN, ConnectMode.of("usb"))
    }

    @Test
    fun `each host belongs to exactly one mode`() {
        assertTrue(lan.belongsTo(ConnectMode.LAN))
        assertFalse(lan.belongsTo(ConnectMode.LOOPBACK))
        assertFalse(lan.belongsTo(ConnectMode.RELAY))

        assertTrue(loopback.belongsTo(ConnectMode.LOOPBACK))
        assertFalse(loopback.belongsTo(ConnectMode.LAN))
        assertFalse(loopback.belongsTo(ConnectMode.RELAY))

        assertTrue(relay.belongsTo(ConnectMode.RELAY))
        assertFalse(relay.belongsTo(ConnectMode.LAN))
        assertFalse(relay.belongsTo(ConnectMode.LOOPBACK))

        // A gateway is a paired endpoint too: it belongs to relay mode, whatever address it has.
        // It is not local network — the user chose "reach my computer from anywhere" when they
        // enrolled it, and listing it under LAN would make that choice a suggestion again.
        assertTrue(gateway.belongsTo(ConnectMode.RELAY))
        assertFalse(gateway.belongsTo(ConnectMode.LAN))
        assertFalse(gateway.belongsTo(ConnectMode.LOOPBACK))
    }

    /** A relay is a relay whatever address it has; the device id is what makes it one. */
    @Test
    fun `a relay on loopback is still a relay`() {
        val local = relay.copy(host = "127.0.0.1", isLoopback = true)
        assertTrue(local.belongsTo(ConnectMode.RELAY))
        assertFalse(local.belongsTo(ConnectMode.LOOPBACK))
    }
}
