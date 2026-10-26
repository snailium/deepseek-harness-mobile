package com.labteto.dshmobile.ui.screens.connect

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.labteto.dshmobile.R

/**
 * The camera half of gateway pairing: one QR, read once, handed back as text.
 *
 * Its own file and its own name because upstream 0.18.0 ships no scanner at all — it deleted the
 * pair screen that carried this, along with the dependency. Keeping it here means the zxing
 * dependency and the manifest permission are additions the fork owns, not edits to a file upstream
 * will rewrite.
 *
 * The options are the ones the old pair screen used, for the same reasons: no beep (a pairing code
 * is not a supermarket scan), orientation unlocked (the QR is usually on a laptop held at an angle),
 * and QR only (an app key is never a barcode).
 */
@Composable
fun rememberGatewayQrScanner(onText: (String) -> Unit): () -> Unit {
    val prompt = stringResource(R.string.gateway_scan_prompt)
    val launcher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.takeIf { it.isNotBlank() }?.let(onText)
    }
    return {
        // A failure here is a device with no camera app or no permission prompt available. The
        // key can still be typed, so this must not take the sheet down with it.
        runCatching {
            launcher.launch(
                ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt(prompt)
                    .setBeepEnabled(false)
                    .setOrientationLocked(false),
            )
        }
    }
}
