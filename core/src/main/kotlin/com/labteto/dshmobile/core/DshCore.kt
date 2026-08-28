package com.labteto.dshmobile.core

/** Core module placeholder for the baseline build; wire protocol code lands here. */
object DshCore {
    /**
     * The harness release this client's DTOs and call shapes were ported from and verified
     * against.
     *
     * Display-only, and it has no version to compare itself against any more: 0.1.2 removed
     * `host.describe`, so the harness no longer tells a client what it is. Where a shape used to
     * differ between releases this client read the difference off the wire rather than off a
     * version string; 0.1.2 left no such difference to read, so there is now no version-shaped
     * branch in the client at all — see `docs/COMPATIBILITY.md`.
     */
    const val PROTOCOL_BASELINE = "0.1.2-alpha.1"
}
