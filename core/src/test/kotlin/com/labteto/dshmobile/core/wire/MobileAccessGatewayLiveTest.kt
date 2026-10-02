package com.labteto.dshmobile.core.wire

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The `dsh-mobile` contract, exercised against a running gateway.
 *
 * Skipped unless an isolated test installation is pointed at, because it *creates a device* on the
 * gateway it talks to: `pairing/open` mints a single-use code and `native-pair` consumes it, leaving
 * a device record behind. That is a mutation, and running it against somebody's real computer would
 * enrol a test device in their device list — so it never starts on its own.
 *
 * What it pins is the part unit tests cannot: that the headers this client sends are the ones the
 * gateway accepts. Every line of it was learned the hard way — a session cookie without an `Origin`
 * on a POST is a 403 that reads exactly like a wrong code, and its absence is invisible in any
 * test that only checks parsing.
 *
 * The harness is an isolated `DSH_HOME` with the plugin installed; see the fork's notes for the
 * procedure. Two variables drive it:
 *
 * - `DSH_GATEWAY_TEST_ADMIN_URL` — the harness web server, where the loopback-only admin API that
 *   opens a pairing window lives (`http://127.0.0.1:13080` for the test installation).
 * - `DSH_GATEWAY_TEST_URL` — the gateway listener itself (`http://127.0.0.1:13443`).
 */
class MobileAccessGatewayLiveTest {

    private val adminUrl: String? = System.getenv("DSH_GATEWAY_TEST_ADMIN_URL")
    private val gatewayUrl: String? = System.getenv("DSH_GATEWAY_TEST_URL")

    private val client = OkHttpClient()

    private fun openPairingWindow(): String {
        val origin = adminUrl!!
        val body = """{"ttlMs":120000}""".toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$origin/api/mobile-access/pairing/open")
            .header("Origin", origin)
            .header("Sec-Fetch-Site", "same-origin")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            assertEquals(201, response.code)
            val text = response.body?.string().orEmpty()
            val key = Regex("\"appKey\":\"([^\"]+)\"").find(text)?.groupValues?.get(1)
            assertNotNull("pairing answer carried no app key: $text", key)
            return key!!
        }
    }

    @Test
    fun `a pairing key pairs this device and the session reaches the harness`() = runBlocking {
        assumeTrue("no test gateway configured", adminUrl != null && gatewayUrl != null)
        val gateway = gatewayUrl!!

        // The key is what a QR would carry; parsing it is part of the contract being checked.
        val invite = (MobileAccess.parsePairingInput(openPairingWindow()) as? MobileAccessInviteResult.Valid)
            ?.invite
        assertNotNull("the gateway's own key did not parse", invite)
        val pairing = invite!!

        // Unauthenticated identity probe, exactly as the app does it before sending anything.
        val metadata = MobileAccessPairing.metadata(gateway, client)
        assertNotNull("metadata route did not answer as a gateway", metadata)
        assertTrue((metadata!!.pluginVersion ?: "").isNotBlank())

        // A fixed test installation serves plain HTTP on loopback, so there is no CA to pin.
        val ca = MobileAccessPairing.caCertificate(gateway, client)
        assertEquals(null, ca)

        val paired = MobileAccessPairing.nativePair(gateway, pairing.token, "live-test", client)
        val credentials = (paired as? NativePairOutcome.Paired)?.response
        assertNotNull("native pairing was refused: $paired", credentials)
        assertTrue(credentials!!.deviceToken.isNotBlank())
        assertTrue(credentials.sessionToken.isNotBlank())

        // The whole point: an ordinary harness call, carrying the cookie the gateway issued plus
        // the origin and CSRF token it demands of a mutation.
        val authorized = DshApiClient(
            OkHttpRpcTransport(
                baseUrl = gateway,
                client = client,
                cookie = MobileAccess.sessionCookie(credentials.sessionToken),
                origin = MobileAccess.originOf(gateway),
                csrf = credentials.csrfToken,
            ),
        )
        assertTrue(
            "an authenticated call did not reach the harness",
            authorized.sessionCanOpenWorkspacePath() is RpcResult.Ok,
        )

        // Without the cookie the same call is refused, which is what makes the line above evidence.
        val anonymous = DshApiClient(
            OkHttpRpcTransport(
                baseUrl = gateway,
                client = client,
                origin = MobileAccess.originOf(gateway),
                csrf = credentials.csrfToken,
            ),
        )
        val refused = anonymous.sessionCanOpenWorkspacePath()
        assertTrue("an unauthenticated call was answered", refused is RpcResult.Err)

        // And the durable half still mints sessions, which is what keeps the pairing alive.
        val renewed = MobileAccessPairing.nativeRenew(gateway, credentials.deviceToken, client)
        assertTrue("renewal failed: $renewed", renewed is NativeRenewOutcome.Renewed)
    }
}
