package com.labteto.dshmobile.ui.screens.main

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.input.key.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.R
import com.labteto.dshmobile.core.wire.dto.ContextBreakdownView
import com.labteto.dshmobile.core.wire.dto.ContextPressureView
import com.labteto.dshmobile.core.wire.dto.FULL_ACCESS_PRESET
import com.labteto.dshmobile.core.wire.dto.EncodedImageAttachment
import com.labteto.dshmobile.core.wire.dto.FileAttachmentRef
import com.labteto.dshmobile.core.wire.dto.PermissionSelect
import com.labteto.dshmobile.ui.components.ContextRing
import com.labteto.dshmobile.ui.components.skeleton
import com.labteto.dshmobile.ui.theme.DsAnimations
import com.labteto.dshmobile.ui.theme.DsShapes
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor

/**
 * Something picked and waiting to be sent with the next message.
 *
 * Two kinds, because the harness carries them two ways. An [Image] rides the prompt as bytes,
 * so it is held here fully encoded. A [File] is uploaded the moment it is picked — harness
 * 0.1.3 streams it to the host verbatim — and what the prompt carries is the receipt that upload
 * answered with, so the chip tracks the upload rather than the bytes.
 */
internal sealed interface PendingAttachment {
    /** Display name, when the picker had one. */
    val name: String?

    /**
     * A picked image, held with its decoded preview.
     *
     * [bytes], [width] and [height] are the encoded size and intrinsic dimensions the picker already
     * had to learn to admit the image; keeping them means the *next* pick can be measured against
     * the message's running totals without decoding everything already attached a second time.
     */
    data class Image(
        val mediaType: String,
        val base64: String,
        val preview: ImageBitmap?,
        val bytes: Int,
        val width: Int,
        val height: Int,
        override val name: String? = null,
    ) : PendingAttachment {
        /** The wire form `session/prompt` carries. */
        fun encoded(): EncodedImageAttachment = EncodedImageAttachment(mediaType, base64, name)
    }

    /** A picked file and the state of its upload. [id] is what a progress callback keys on. */
    data class File(
        val id: String,
        val uri: Uri,
        override val name: String,
        /** Size as the provider reported it; `-1` when it would not say. */
        val size: Long,
        val state: FileUploadState,
    ) : PendingAttachment {
        /** The receipt to cite, once the upload has one. */
        val receiptId: String? get() = (state as? FileUploadState.Ready)?.receiptId
    }
}

/** Where one file's upload stands. */
internal sealed interface FileUploadState {
    /** Bytes are still going up; [sent] is the running count. */
    data class Uploading(val sent: Long) : FileUploadState

    /** The host holds the file and minted a receipt for it. */
    data class Ready(val receiptId: String, val file: FileAttachmentRef) : FileUploadState

    /** The upload did not complete; the chip offers a retry. */
    data class Failed(val message: String) : FileUploadState
}

/**
 * The message composer, laid out like the harness's own: the `+` and the permission chip on the
 * left, the send affordance on the right.
 *
 * The model selector is deliberately *not* here — it moved to the top bar, which leaves this row
 * for the two controls you change mid-conversation and keeps the composer from wrapping on a
 * narrow phone.
 */
@Composable
internal fun Composer(
    draft: String,
    onDraftChange: (String) -> Unit,
    attachments: List<PendingAttachment>,
    onRemoveAttachment: (Int) -> Unit,
    onRetryAttachment: (Int) -> Unit,
    permissions: PermissionSelect?,
    pendingPermission: String?,
    onPermissionPick: (String) -> Unit,
    contextBreakdown: ContextBreakdownView?,
    contextPressure: ContextPressureView?,
    running: Boolean,
    enabled: Boolean,
    /**
     * Whether the enter key sends. Off, the field keeps the multi-line default and enter inserts
     * a newline, which is what a field of this shape is expected to do.
     */
    enterToSend: Boolean = false,
    onOpenSheet: () -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    preparing: Boolean = false,
) {
    val colors = DsTheme.colors
    val haptics = LocalHapticFeedback.current
    // A file that is still uploading has no receipt to cite yet, and one that failed never will;
    // the send affordance waits for the chips rather than sending a message that names neither.
    val attachmentsSettled = attachments.none { it is PendingAttachment.File && it.state !is FileUploadState.Ready }
    val canSend = enabled && !preparing && (draft.isNotBlank() || attachments.isNotEmpty()) && attachmentsSettled
    val currentDraft by rememberUpdatedState(draft)
    val currentOnDraftChange by rememberUpdatedState(onDraftChange)
    val currentOnSend by rememberUpdatedState(onSend)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            // A hard ceiling on the card. The transcript above the composer takes the leftover
            // height, so an unbounded card is not a cosmetic problem — it is the transcript
            // disappearing. Attachments plus a full-height field still fit under this.
            .heightIn(max = 280.dp)
            .padding(horizontal = DsSpacing.pageHorizontal, vertical = DsSpacing.small)
            .animateContentSize(),
        shape = DsShapes.composer,
        color = colors.composerCard,
        border = BorderStroke(1.dp, colors.borderL1),
    ) {
        Column(
            Modifier.padding(horizontal = DsSpacing.small, vertical = DsSpacing.xsmall),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.small),
        ) {
            // BasicTextField, not Material3's TextField. Material3 enforces a hard-coded 56dp
            // minimum height (TextFieldDefaults.MinHeight) plus its own internal content padding,
            // neither of which a caller can remove — the `colors` block only hides its container
            // and indicator. That unremovable inset is the "large border" around the text: every
            // value tuned on the card around it was fighting a floor it could not go under.
            //
            // BasicTextField has no decoration box at all, so the field is exactly as tall as its
            // text and its placeholder needs a manual overlay.
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp)
                    .onPreviewKeyEvent { event ->
                        // Ctrl/Cmd+Enter is the shortcut and always sends. Plain Enter sends only
                        // when the reader asked for it: the field is multi-line, so
                        // newline-by-default is what it would otherwise do.
                        val shortcut = event.isCtrlPressed || event.isMetaPressed
                        if (event.key == Key.Enter && (shortcut || enterToSend)) {
                            if (event.type == KeyEventType.KeyUp && canSend) {
                                val text = currentDraft
                                currentOnDraftChange("")
                                currentOnSend(text)
                            }
                            true
                        } else false
                    },
                enabled = enabled,
                textStyle = DsType.base16.copy(color = colors.labelPrimary),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = if (enterToSend) {
                    KeyboardOptions(imeAction = ImeAction.Send)
                } else {
                    KeyboardOptions(imeAction = ImeAction.Default)
                },
                keyboardActions = if (enterToSend) {
                    KeyboardActions(
                        onSend = {
                            if (canSend) {
                                val text = currentDraft
                                currentOnDraftChange("")
                                currentOnSend(text)
                            }
                        },
                    )
                } else {
                    KeyboardActions()
                },
                minLines = 1,
                maxLines = 6,
                decorationBox = { innerTextField ->
                    // The placeholder has to be drawn by hand now that there is no decoration box
                    // to do it. It must not occupy space, or the field jumps by a line when the
                    // first character lands.
                    Box(modifier = Modifier.padding(DsSpacing.textFieldInset)) {
                        if (draft.isEmpty()) {
                            Text(
                                stringResource(R.string.chat_composer_hint),
                                style = DsType.base16,
                                color = colors.labelTertiary,
                            )
                        }
                        innerTextField()
                    }
                },
            )

            if (preparing) Text(stringResource(R.string.photos_preparing), style = DsType.caption11)
            AnimatedVisibility(visible = attachments.isNotEmpty()) {
                AttachmentStrip(attachments, onRemoveAttachment, onRetryAttachment)
            }

            Row(
                // Bottom, not centre: the action buttons ride the field's last line as it grows,
                // which is the ChatGPT/WhatsApp arrangement. Centre made them drift to the middle
                // of a tall draft, away from the line being typed.
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DsSpacing.compact),
            ) {
                // Built exactly like the permission trigger beside it — a 28dp circle, a 1dp
                // ring, a 14dp glyph — rather than through CircleAction. That helper scales its
                // glyph to the button and carries the send/stop styling; trying to make it also
                // imitate a control it was never shaped for produced two rounds of a button that
                // looked wrong in a different way each time. The three round controls in this row
                // now share one construction, so a change to one is a change to all.
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(colors.hoverSolid)
                        .border(1.dp, colors.borderL2, CircleShape)
                        .clickable(
                            enabled = enabled,
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.chat_composer_commands),
                            onClick = onOpenSheet,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.chat_composer_commands),
                        tint = colors.labelPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }

                // Permission and context sit together at the trailing edge, both icon-only: a
                // 28dp preset-glyph button (the web hides its label below ~460px, and a phone
                // never has that room) and a 14dp occupancy ring. Each opens its own bottom sheet.
                // Pinned past the flexible spacer so neither can be crowded out.
                PermissionChip(
                    select = permissions,
                    pending = pendingPermission,
                    enabled = enabled,
                    onPick = onPermissionPick,
                )

                ContextRing(contextBreakdown, contextPressure)

                // Send is always present; stop joins it while a turn runs.
                //
                // These used to share one slot, swapping on `running`, which meant a running
                // session offered no way to send at all — the Queue and Steer modes in the + sheet
                // were unreachable from the phone, and the on-screen return key inserts a newline,
                // so there was nothing else to press. The host has always admitted
                // `session/prompt` with `mode: queue|steer` mid-turn; only the button was missing.
                //
                // Stop keeps the right-hand position it had, so the gesture for stopping a turn is
                // where it always was and send appears beside it rather than under the thumb
                // already reaching for stop.
                CircleAction(
                    icon = Icons.Filled.ArrowUpward,
                    description = stringResource(R.string.chat_composer_send),
                    size = 36,
                    background = if (canSend) colors.buttonInfoFill else colors.buttonPrimaryDimmed,
                    tint = if (canSend) Color.White else colors.labelTertiary,
                    enabled = canSend,
                    onClick = {
                        val text = currentDraft
                        currentOnDraftChange("")
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOnSend(text)
                    },
                )

                AnimatedVisibility(
                    visible = running,
                    enter = fadeIn(DsAnimations.fade) + scaleIn(initialScale = 0.85f),
                    exit = fadeOut(DsAnimations.fade) + scaleOut(targetScale = 0.85f),
                ) {
                    CircleAction(
                        icon = null,
                        description = stringResource(R.string.chat_composer_stop),
                        size = 36,
                        background = colors.error,
                        tint = Color.White,
                        enabled = true,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onStop()
                        },
                    ) {
                        Box(
                            Modifier
                                .size(11.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The permission preset chip.
 *
 * Renders nothing when the projection key is absent: that means the harness composes no permission
 * service at all, and a dead control would be worse than none. Labels come from the wire, because
 * the preset table is deployment-configurable — mapping ids to local strings would mislabel any
 * deployment that renamed one.
 */
@Composable
private fun PermissionChip(
    select: PermissionSelect?,
    pending: String?,
    enabled: Boolean,
    onPick: (String) -> Unit,
) {
    val colors = DsTheme.colors
    if (select == null) return
    var menuOpen by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf<String?>(null) }
    val effective = pending ?: select.currentValue

    Box {
        // Icon only: the preset's own glyph on a 28dp round target. The label moves to the
        // picker sheet, where there is room for it and its description.
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (pending == null) colors.hoverSolid else colors.bgLayer2)
                .border(1.dp, colors.borderL2, CircleShape)
                .clickable(
                    enabled = enabled && pending == null,
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.permission_preset),
                ) { menuOpen = true },
            contentAlignment = Alignment.Center,
        ) {
            permissionPresetGlyph(effective, FULL_ACCESS_PRESET)?.let { glyph ->
                Icon(
                    glyph,
                    contentDescription = stringResource(R.string.permission_preset),
                    tint = if (effective == FULL_ACCESS_PRESET) colors.warnLabel else colors.labelSecondary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        if (menuOpen) {
            PermissionMenu(
                select = select,
                current = effective,
                onDismiss = { menuOpen = false },
                onPick = { value ->
                    menuOpen = false
                    // Full access removes the approval prompt entirely, so it gets an explicit
                    // acknowledgement the way the desktop client does.
                    if (value == FULL_ACCESS_PRESET) confirming = value else onPick(value)
                },
            )
        }
    }

    confirming?.let { target ->
        FullAccessConfirmDialog(
            onDismiss = { confirming = null },
            onConfirm = {
                confirming = null
                onPick(target)
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Attachments
// ---------------------------------------------------------------------------

@Composable
private fun AttachmentStrip(
    attachments: List<PendingAttachment>,
    onRemove: (Int) -> Unit,
    onRetry: (Int) -> Unit,
) {
    val colors = DsTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.small),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        attachments.forEachIndexed { index, attachment ->
            Box {
                when (attachment) {
                    is PendingAttachment.Image -> Box(
                        Modifier
                            .size(56.dp)
                            .clip(DsShapes.block)
                            .background(colors.bgModulePlatform),
                    ) {
                        attachment.preview?.let {
                            Image(
                                bitmap = it,
                                contentDescription = attachment.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    is PendingAttachment.File -> FileAttachmentChip(attachment, onRetry = { onRetry(index) })
                }
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(colors.toastBg)
                        .clickable { onRemove(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(
                            if (attachment is PendingAttachment.File) R.string.chat_composer_remove_file
                            else R.string.chat_composer_remove_image,
                        ),
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

/**
 * One file in the strip: name, then either its size, its upload progress, or the failure.
 *
 * The chip is the same height as an image tile so the strip does not step. A failed upload is
 * retried by tapping the chip itself — the remove affordance stays where it is on every kind.
 */
@Composable
private fun FileAttachmentChip(file: PendingAttachment.File, onRetry: () -> Unit) {
    val colors = DsTheme.colors
    val failed = file.state is FileUploadState.Failed
    Row(
        modifier = Modifier
            .height(56.dp)
            .widthIn(min = 120.dp, max = 200.dp)
            .clip(DsShapes.block)
            .background(colors.bgModulePlatform)
            .border(1.dp, if (failed) colors.error else colors.borderL3, DsShapes.block)
            .clickable(enabled = failed, onClick = onRetry)
            .padding(start = 10.dp, end = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (val state = file.state) {
            is FileUploadState.Uploading -> CircularProgressIndicator(
                progress = {
                    if (file.size > 0) (state.sent.toFloat() / file.size).coerceIn(0f, 1f) else 0f
                },
                modifier = Modifier.size(18.dp),
                color = colors.accent,
                trackColor = colors.borderL3,
                strokeWidth = 2.dp,
            )
            else -> Icon(
                Icons.Outlined.Description,
                contentDescription = null,
                tint = if (failed) colors.error else colors.labelSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
        Column {
            Text(
                file.name,
                style = DsType.small13,
                color = colors.labelPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when (val state = file.state) {
                    is FileUploadState.Uploading -> stringResource(R.string.chat_attachment_uploading)
                    is FileUploadState.Ready -> fileSizeText(state.file.bytes)
                    is FileUploadState.Failed -> stringResource(R.string.chat_attachment_upload_failed)
                },
                style = DsType.caption11,
                color = if (failed) colors.error else colors.labelTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Shared circular affordance
// ---------------------------------------------------------------------------

@Composable
private fun CircleAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    description: String,
    size: Int,
    background: Color,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    content: (@Composable () -> Unit)? = null,
) {
    val colors = DsTheme.colors
    Surface(
        onClick = onClick,
        // Described here rather than on the icon, because not every one of these has an icon: the
        // stop button draws a plain square through `content`, and while the description hung off
        // the icon that button announced nothing at all to a screen reader.
        // `size` then `requiredSize`: the Row may try to squeeze a sibling when space is short,
        // and a squashed circle reads as a broken button. requiredSize wins that negotiation.
        modifier = Modifier
            .requiredSize(size.dp)
            .semantics { this.contentDescription = description },
        enabled = enabled,
        shape = CircleShape,
        color = background,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            when {
                content != null -> content()
                icon != null -> Icon(
                    icon,
                    // The Surface above already carries it; repeating it here would have a screen
                    // reader say the label twice.
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size((size * 0.46f).dp),
                )
            }
        }
    }
}
