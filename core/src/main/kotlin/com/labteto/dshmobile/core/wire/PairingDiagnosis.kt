package com.labteto.dshmobile.core.wire

/**
 * What a failed relay claim should be re-read as, once the address has been asked what it serves.
 *
 * The two products answer the same route name differently, and a bare refusal says nothing about
 * which one is on the other end. `dsh-relay` answers 403 from its DNS-rebinding fence when it does
 * not know the address it was reached by; a `dsh-mobile` gateway answers 403 to any request that
 * misses its session and CSRF headers — and a relay claim reaches it without either. The status is
 * therefore not enough to advise the reader, and the advice differs completely: one is fixed by
 * editing the relay's `publicHostnames`, the other by pairing with the gateway's own key instead.
 *
 * Asking the address for `/mobile-access/metadata` settles it, because only a gateway serves that.
 * This is the decision, kept out of the view model so it can be tested without a network.
 */
sealed interface RelayClaimReading {
    /**
     * A `dsh-mobile` gateway answered the relay's URLs.
     *
     * Nothing is wrong with the address or the code — the wrong *product's* pairing flow was used.
     * The gateway pairs with its own app key or QR, never with a relay code.
     */
    data object GatewayNotRelay : RelayClaimReading

    /** A relay, refusing the address it was reached by. Its operator owns that list. */
    data object HostFence : RelayClaimReading

    /** Something answered, but nothing there serves a relay claim. */
    data object NothingThere : RelayClaimReading
}

/**
 * Whether the metadata an address served names it as a gateway.
 *
 * An empty object is not enough: [`MobileAccessMetadata`] decodes `{}` into defaults, and a page
 * that happens to answer 200 with `{}` is not a gateway. Either field being set is what the
 * plugin actually writes.
 */
fun MobileAccessMetadata.namesItselfGateway(): Boolean = version > 0 || pluginVersion != null

/**
 * Re-read [outcome] in the light of what [gateway] says the address is.
 *
 * Only the two refusals are re-read; every other outcome already says what happened, and a null
 * return means "keep the original reading".
 */
fun relayClaimReading(
    outcome: RelayPairOutcome,
    gateway: MobileAccessMetadata?,
): RelayClaimReading? {
    val servesGateway = gateway?.namesItselfGateway() == true
    return when (outcome) {
        RelayPairOutcome.HostRefused ->
            if (servesGateway) RelayClaimReading.GatewayNotRelay else RelayClaimReading.HostFence
        RelayPairOutcome.NotARelay ->
            if (servesGateway) RelayClaimReading.GatewayNotRelay else RelayClaimReading.NothingThere
        else -> null
    }
}
