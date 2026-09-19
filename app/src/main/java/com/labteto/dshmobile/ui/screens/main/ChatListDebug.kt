package com.labteto.dshmobile.ui.screens.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.R
import com.labteto.dshmobile.data.DebugBuffer
import com.labteto.dshmobile.data.SessionStore
import com.labteto.dshmobile.data.transcriptDebugReport
import com.labteto.dshmobile.ui.components.DsButton
import com.labteto.dshmobile.ui.components.DsDialog
import com.labteto.dshmobile.ui.components.DsIconButton
import com.labteto.dshmobile.ui.components.FeatherIcons
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType

/**
 * The session drawer's on-device diagnostics, parked here rather than deleted.
 *
 * **Currently disabled.** [CHAT_LIST_DEBUG_ENABLED] is `false`, so neither the toolbar button nor
 * the dialog is reachable and nothing in this file is called from anywhere. The code is kept
 * because the phone has no adb: when a transcript renders wrong or a request silently fails, this
 * dialog is the only way to get a report off the device, and rebuilding it from scratch each time
 * is the expensive part. Re-enabling is the one flag plus the two call sites named below.
 *
 * Why it was pulled out of `ChatListDrawer.kt`: the drawer is the app's busiest composable, and
 * carrying a diagnostics dialog in it meant every reader of that file had to skip past ~50 lines
 * of debug chrome to follow the list logic. The feature is self-contained — one button, one
 * dialog, one buffer — so it belongs in its own file regardless of whether it is switched on.
 *
 * ### To re-enable
 *
 * 1. Flip [CHAT_LIST_DEBUG_ENABLED] to `true`.
 * 2. In `ChatListDrawer.kt`, restore the trigger in the toolbar row (between the search and
 *    settings buttons) and the dialog at the end of the composable:
 *
 *        var showDebugInfo by remember { mutableStateOf(false) }
 *        ...
 *        ChatListDebugButton(onClick = { showDebugInfo = true })
 *        ...
 *        ChatListDebugDialog(visible = showDebugInfo, store = store, onDismiss = { showDebugInfo = false })
 *
 * 3. In `SessionStore.kt`, restore the two `DebugBuffer` calls: `DebugBuffer.clear()` on a fresh
 *    connection generation, and `DebugBuffer.append(...)` inside `log()`. Both are marked with a
 *    `DEBUG-BUFFER` comment at the call site. Without them the dialog opens empty — the buffer is
 *    fed only by those two lines.
 *
 * `DebugBuffer` and `transcriptDebugReport` are themselves untouched and still compile; only their
 * call sites are commented out, so step 3 is the whole of the plumbing.
 */
internal const val CHAT_LIST_DEBUG_ENABLED: Boolean = false

/**
 * The toolbar button that opens the diagnostics dialog.
 *
 * Returns without emitting anything while [CHAT_LIST_DEBUG_ENABLED] is false, so a call site can
 * be left in place across the flag without leaving a gap in the row.
 */
@Composable
internal fun ChatListDebugButton(onClick: () -> Unit) {
    if (!CHAT_LIST_DEBUG_ENABLED) return
    DsIconButton(
        icon = FeatherIcons.Info,
        contentDescription = stringResource(R.string.debug_dialog_title),
        onClick = onClick,
        tint = DsTheme.colors.labelTertiary,
    )
}

/**
 * The diagnostics dialog: the on-device log plus a dump of what the fold holds.
 *
 * Both halves answer questions a screenshot cannot — the buffer carries what the app said to
 * itself (`<what> -> <outcome>` per line), and the transcript report carries the fold's own node
 * list in seq order. Copy ships the pair back to a developer.
 */
@Composable
internal fun ChatListDebugDialog(
    visible: Boolean,
    store: SessionStore,
    onDismiss: () -> Unit,
) {
    if (!CHAT_LIST_DEBUG_ENABLED || !visible) return
    val colors = DsTheme.colors
    val context = LocalContext.current
    val bufferText = DebugBuffer.snapshot()
    val transcriptText = transcriptDebugReport(
        loadingOlder = store.loadingOlder.value,
        loadOlderFailed = store.loadOlderFailed.value,
        conversation = store.currentConversation.value,
    )
    val debugText = buildString {
        appendLine("=== Debug buffer ===")
        appendLine(bufferText.ifEmpty { context.getString(R.string.debug_buffer_empty) })
        appendLine()
        append(transcriptText)
    }
    DsDialog(title = stringResource(R.string.debug_dialog_title), onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                debugText,
                style = DsType.xsmall12.copy(fontFamily = FontFamily.Monospace),
                color = colors.labelSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(DsSpacing.compact))
        Row(horizontalArrangement = Arrangement.End) {
            DsButton(
                text = stringResource(R.string.common_copy),
                onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as? android.content.ClipboardManager
                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("dsh-debug", debugText))
                    onDismiss()
                },
            )
        }
    }
}
