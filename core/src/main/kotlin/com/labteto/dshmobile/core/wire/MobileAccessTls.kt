package com.labteto.dshmobile.core.wire

import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

/**
 * Transport trust for a `dsh-mobile` gateway on a LAN.
 *
 * A LAN gateway terminates its own TLS with a certificate signed by a private CA it generated for
 * this computer, so nothing in the platform store can vouch for it. What can is the pairing input
 * itself: on the LAN the gateway's `instanceId` *is* the SHA-256 of that CA's DER form (see
 * [MobileAccessPairing.certificateFingerprint]), so the QR, key or link a person already holds
 * carries the identity, and the certificate merely has to match it.
 *
 * That is a different shape from [RelayTls], which pins one public key read off the wire. Here the
 * anchor is a whole CA, fetched once at pairing from `/mobile-access/ca.cer` and verified against
 * the fingerprint before it is stored — so the pin is established by evidence the user already has,
 * not by trust on first use.
 *
 * Pure JVM (`javax.net.ssl`), so `:core` stays free of Android imports.
 */
object MobileAccessTls {

    /**
     * A client that accepts a chain only when it is signed by [caDer].
     *
     * The hostname verifier passes unconditionally, and that is not a hole: the trust manager has
     * already refused every chain that does not end at the pinned CA, so the CA *is* the identity.
     * It matters because a LAN gateway's certificate covers the addresses it knew when it was
     * minted, while a phone may reach it by another one — a hotspot address, a second interface —
     * and a name check would reject a computer the user explicitly paired with.
     */
    fun caPinnedClient(base: OkHttpClient, caDer: ByteArray): OkHttpClient {
        val trustManager = caTrustManager(caDer)
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf<javax.net.ssl.TrustManager>(trustManager), null)
        return base.newBuilder()
            .sslSocketFactory(context.socketFactory, trustManager)
            .hostnameVerifier(HostnameVerifier { _, _ -> true })
            .build()
    }

    /**
     * A client that accepts whatever certificate it is offered.
     *
     * For two flows only, both of which send nothing a stranger could use: fetching
     * `/mobile-access/ca.cer` before the CA is known, and probing an address to ask whether it is a
     * gateway at all. In the first, the fetched CA is checked against the fingerprint from the
     * pairing input before it is ever trusted — the offer is accepted, and then *verified*.
     */
    fun unpinnedClient(base: OkHttpClient): OkHttpClient {
        val trustManager = AcceptingTrustManager()
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf<javax.net.ssl.TrustManager>(trustManager), null)
        return base.newBuilder()
            .sslSocketFactory(context.socketFactory, trustManager)
            .hostnameVerifier(HostnameVerifier { _, _ -> true })
            .build()
    }

    /** Whether [caDer] parses as the certificate it is claimed to be. */
    fun parseCertificate(caDer: ByteArray): X509Certificate? = runCatching {
        CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(caDer)) as X509Certificate
    }.getOrNull()

    /** A trust manager that validates against exactly one CA. */
    private fun caTrustManager(caDer: ByteArray): X509TrustManager {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("dsh-mobile", parseCertificate(caDer) ?: throw CertificateException("gateway CA is not a certificate"))
        }
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(keyStore)
        val delegate = factory.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
            ?: throw CertificateException("no X.509 trust manager for the gateway CA")
        return CaOnlyTrustManager(delegate)
    }
}

/**
 * Wraps the platform manager built from the pinned CA.
 *
 * The wrapper exists so a rejected chain carries a type this app can name — OkHttp wraps a trust
 * failure into an `SSLHandshakeException`, and "this computer's certificate does not match the one
 * in its pairing code" is a message worth keeping rather than collapsing into a generic TLS error.
 */
private class CaOnlyTrustManager(private val delegate: X509TrustManager) : X509TrustManager {

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (chain.isNullOrEmpty()) throw CertificateException("gateway presented no certificate")
        try {
            delegate.checkServerTrusted(chain, authType)
        } catch (e: CertificateException) {
            throw GatewayPinMismatchException(e)
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("dsh-mobile clients do not present certificates")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers
}

/** The certificate a gateway presented does not chain to the CA recorded when this device paired. */
class GatewayPinMismatchException(cause: Throwable) :
    CertificateException("the gateway's certificate is not signed by the CA recorded at pairing", cause)

/** Accepts any server chain. See [MobileAccessTls.unpinnedClient] for the two places that is right. */
private class AcceptingTrustManager : X509TrustManager {

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (chain.isNullOrEmpty()) throw CertificateException("gateway presented no certificate")
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("dsh-mobile clients do not present certificates")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
