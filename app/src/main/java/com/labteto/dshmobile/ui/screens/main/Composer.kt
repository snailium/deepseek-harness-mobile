package com.labteto.dshmobile.ui.screens.main

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.input.key.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.R
import com.labteto.dshmobile.core.wire.dto.ContextBreakdownView
import com.labteto.dshmobile.core.wire.dto.SessionModelsValue
import com.labteto.dshmobile.core.wire.dto.ContextPressureView
import com.labteto.dshmobile.core.wire.dto.FULL_ACCESS_PRESET
import com.labteto.dshmobile.core.wire.dto.EncodedImageAttachment
import com.labteto.dshmobile.core.wire.dto.FileAttachmentRef
import com.labteto.dshmobile.core.wire.dto.PermissionSelect
import com.labteto.dshmobile.core.wire.dto.displayPermissionPreset
import com.labteto.dshmobile.ui.components.ContextMeter
import com.labteto.dshmobile.ui.components.StateDot
import com.labteto.dshmobile.ui.components.StateDotState
import com.labteto.dshmobile.ui.components.skeleton
import com.labteto.dshmobile.ui.theme.DsAnimations
import com.labteto.dshmobile.ui.theme.DsShapes
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType
import com.labteto.dshmobile.ui.components.FeatherIcons

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
 * The composer's draft text, held in its own snapshot state so typing recomposes only the
 * composer, not the whole conversation surface.
 *
 * The screen reads it back through [ComposerDraft.value] only inside event handlers (send,
 * prefill) — never during composition of the screen itself — so a keystroke invalidates just the
 * composer's subtree.
 */
@Stable
internal class ComposerDraft internal constructor(initial: String) {
    var value by mutableStateOf(initial)
}

private val ComposerDraftSaver = androidx.compose.runtime.saveable.Saver<ComposerDraft, String>(
    save = { it.value },
    restore = { ComposerDraft(it) },
)

/** A saveable [ComposerDraft]; keyed on the session so a switch starts from a clean draft. */
@Composable
internal fun rememberComposerDraft(sessionId: String?): ComposerDraft =
    androidx.compose.runtime.saveable.rememberSaveable(sessionId, saver = ComposerDraftSaver) {
        ComposerDraft("")
    }

/**
 * The message composer.
 *
 * One card, two rows: the input row — `+`, the growing field, send/stop — and, only when the
 * harness offers them, a slim second row with the permission chip and the context meter. The
 * field is a [BasicTextField] rather than Material's `TextField`, because M3 enforces a 56dp
 * minimum height inside its decoration box: a single-line field over a 44dp action row is what
 * made the composer read as a slab. The send affordance pins to the field's last line as it
 * grows (the ChatGPT/WhatsApp arrangement), so the card stays ~66dp at rest and only earns
 * height for what is actually typed.
 *
 * The model selector lives here, above the input: a model choice configures the *next* turn,
 * so it belongs at the point of action — the same reasoning that put Gemini's picker inside its
 * prompt bar (2025 redesign) and ChatGPT's inside its composer. The header keeps identity and
 * navigation only.
 */
@Composable
internal fun Composer(
    composerDraft: ComposerDraft,
    attachments: List<PendingAttachment>,
    onRemoveAttachment: (Int) -> Unit,
    onRetryAttachment: (Int) -> Unit,
    permissions: PermissionSelect?,
    pendingPermission: String?,
    onPermissionPick: (String) -> Unit,
    contextBreakdown: ContextBreakdownView?,
    contextPressure: ContextPressureView?,
    /** The current model's display label; null hides the model chip in the config strip. */
    modelLabel: String?,
    /** Whether the model list is routable; a non-routable list shows a warning dot. */
    modelsRoutable: Boolean,
    /** Opens the model picker; the choice configures the next turn. */
    onOpenModels: () -> Unit,
    running: Boolean,
    enabled: Boolean,
    onOpenSheet: () -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    /** Prompt mode for queue/steer while a turn is running. */
    promptMode: String = "queue",
    /** Whether the enter key sends the message; when false it inserts a newline. */
    enterToSend: Boolean = false,
    modifier: Modifier = Modifier,
    preparing: Boolean = false,
) {
    val colors = DsTheme.colors
    val haptics = LocalHapticFeedback.current
    // A file that is still uploading has no receipt to cite yet, and one that failed never will;
    // the send affordance waits for the chips rather than sending a message that names neither.
    val draft = composerDraft.value
    // A file that is still uploading has no receipt to cite yet, and one that failed never will;
    // the send affordance waits for the chips rather than sending a message that names neither.
    val attachmentsSettled = attachments.none { it is PendingAttachment.File && it.state !is FileUploadState.Ready }
    val canSend = enabled && !preparing && (draft.isNotBlank() || attachments.isNotEmpty()) && attachmentsSettled
    val currentDraft by rememberUpdatedState(draft)
    val currentOnSend by rememberUpdatedState(onSend)

    fun doSend() {
        if (!canSend || running) return
        val text = currentDraft
        composerDraft.value = ""
        // The long-press feedback doubles as the send tick; the heavier long-press haptic stays
        // on stop, the disruptive action.
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        currentOnSend(text)
    }

    fun doQueueOrSteer() {
        if (!canSend || !running) return
        val text = currentDraft
        composerDraft.value = ""
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        currentOnSend(text)
    }

    // ---- Config strip: model · permission · context, above the input card ----
    // This is the Gemini/ChatGPT arrangement: the model (and the other turn-configuration
    // controls) live where the next turn is written, never in the title chrome.
    val hasConfig = modelLabel != null || permissions != null || contextPressure?.usedRatio != null
    if (hasConfig) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DsSpacing.medium, vertical = DsSpacing.tiny),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DsSpacing.compact),
        ) {
            if (modelLabel != null) {
                ModelChip(
                    label = modelLabel,
                    routable = modelsRoutable,
                    onClick = onOpenModels,
                )
            }
            PermissionChip(
                select = permissions,
                pending = pendingPermission,
                enabled = enabled,
                onPick = onPermissionPick,
            )
            Spacer(Modifier.weight(1f))
            ContextMeter(contextBreakdown, contextPressure)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DsSpacing.medium, vertical = DsSpacing.xsmall)
            // A quiet 4dp ambient shadow: the card floats without the old 8dp slab look.
            .shadow(4.dp, DsShapes.composer)
            .animateContentSize(),
        shape = DsShapes.composer,
        color = colors.composerCard,
        border = BorderStroke(1.dp, colors.borderL1),
    ) {
        Column(
            Modifier.padding(horizontal = DsSpacing.medium, vertical = DsSpacing.small),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.xsmall),
        ) {
            TextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { event ->
                    if (event.key == Key.Enter && (event.isCtrlPressed || event.isMetaPressed)) {
                        if (event.type == KeyEventType.KeyUp && canSend) {
                            val text = currentDraft
                            currentOnDraftChange("")
                            currentOnSend(text)
                        }
                        true
                    } else false
                },
                keyboardActions = KeyboardActions(onSend = {
                    if (canSend) { val text = currentDraft; currentOnDraftChange(""); currentOnSend(text) }
                }),
                enabled = enabled,
                placeholder = {
                    Text(
                        stringResource(R.string.chat_composer_hint),
                        style = DsType.std14,
                        color = colors.labelTertiary,
                    )
                },
                minLines = 1,
                maxLines = 8,
                textStyle = DsType.std14,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = colors.accent,
                    focusedTextColor = colors.labelPrimary,
                    unfocusedTextColor = colors.labelPrimary,
                ),
            )

            if (preparing) Text(stringResource(R.string.photos_preparing), style = DsType.caption11)
            AnimatedVisibility(visible = attachments.isNotEmpty()) {
                AttachmentStrip(attachments, onRemoveAttachment, onRetryAttachment)
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DsSpacing.compact),
            ) {
                CircleAction(
                    icon = FeatherIcons.Plus,
                    contentDescription = stringResource(R.string.chat_composer_commands),
                    size = 36,
                    background = colors.hoverSolid,
                    tint = colors.labelPrimary,
                    enabled = enabled,
                    onClick = onOpenSheet,
                    backgroundBrush = Brush.linearGradient(
                        listOf(colors.accentTertiary, colors.accentTertiary),
                    ),
                )

                val selectionColors = TextSelectionColors(
                    handleColor = colors.accent,
                    backgroundColor = colors.accent.copy(alpha = 0.4f),
                )
                CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { composerDraft.value = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = DsSpacing.small),
                        enabled = enabled,
                        textStyle = DsType.body17.copy(color = colors.labelPrimary),
                        cursorBrush = SolidColor(colors.accent),
                        minLines = 1,
                        maxLines = 5,
                        keyboardOptions = if (enterToSend) {
                            KeyboardOptions(imeAction = ImeAction.Send)
                        } else {
                            KeyboardOptions(imeAction = ImeAction.None)
                        },
                        keyboardActions = if (enterToSend) {
                            KeyboardActions(onSend = { doSend() })
                        } else {
                            KeyboardActions()
                        },
                        decorationBox = { innerTextField ->
                            Box {
                                if (draft.isEmpty() && attachments.isEmpty()) {
                                    Text(
                                        stringResource(R.string.chat_composer_hint),
                                        style = DsType.body17,
                                        color = colors.labelTertiary,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                }

                // Three states: not running → Send; running + empty draft → Stop;
                // running + non-empty draft → Queue/Steer (per promptMode).
                AnimatedContent(
                    targetState = if (!running) 0 else if (draft.isNotBlank() || attachments.isNotEmpty()) 1 else 2,
                    transitionSpec = {
                        (fadeIn(DsAnimations.fade) + scaleIn(initialScale = 0.85f))
                            .togetherWith(fadeOut(DsAnimations.fade) + scaleOut(targetScale = 0.85f))
                    },
                    label = "sendStopQueue",
                ) { state ->
                    when (state) {
                        2 -> CircleAction(
                            icon = null,
                            contentDescription = stringResource(R.string.chat_composer_stop),
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
                        1 -> CircleAction(
                            icon = FeatherIcons.ArrowUp,
                            contentDescription = stringResource(
                                if (promptMode == "steer") R.string.chat_composer_steer else R.string.chat_composer_queue,
                            ),
                            size = 36,
                            background = colors.primaryButtonGradientStart,
                            tint = Color.White,
                            enabled = true,
                            onClick = { doQueueOrSteer() },
                            backgroundBrush = Brush.linearGradient(
                                listOf(colors.primaryButtonGradientStart, colors.primaryButtonGradientEnd),
                            ),
                        )
                        else -> CircleAction(
                            icon = FeatherIcons.ArrowUp,
                            contentDescription = stringResource(R.string.chat_composer_send),
                            size = 36,
                            background = if (canSend) colors.primaryButtonGradientStart else colors.buttonPrimaryDimmed,
                            tint = if (canSend) Color.White else colors.labelTertiary,
                            enabled = canSend,
                            onClick = { doSend() },
                            backgroundBrush = if (canSend) {
                                Brush.linearGradient(
                                    listOf(colors.primaryButtonGradientStart, colors.primaryButtonGradientEnd),
                                )
                            } else null,
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
    val option = select.options.firstOrNull { it.value == effective }
    val label = if (option != null) {
        displayPermissionPreset(option.value, option.name)
    } else {
        stringResource(R.string.permission_custom)
    }

    Box {
        Row(
            modifier = Modifier
                .clip(DsShapes.cube)
                .clickable(
                    enabled = enabled && pending == null,
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.permission_preset),
                    onClick = { menuOpen = true },
                )
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .then(
                    if (pending != null) {
                        Modifier.skeleton(colors.bgLayer2, colors.hover, DsShapes.cube)
                    } else {
                        Modifier
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                FeatherIcons.Shield,
                contentDescription = stringResource(R.string.permission_preset),
                tint = if (effective == FULL_ACCESS_PRESET) colors.warnLabel else colors.labelTertiary,
                modifier = Modifier.size(14.dp),
            )
            Text(
                label,
                style = DsType.small13,
                color = colors.labelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                FeatherIcons.ChevronDown,
                contentDescription = null,
                tint = colors.labelTertiary,
                modifier = Modifier.size(12.dp),
            )
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
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.chat_composer_remove_image),
                            onClick = { onRemove(index) },
                        ),
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
    contentDescription: String,
    size: Int,
    background: Color,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    backgroundBrush: Brush? = null,
    content: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (backgroundBrush != null) backgroundBrush else Brush.linearGradient(listOf(background, background)))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            content != null -> content()
            icon != null -> Icon(
                icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size((size * 0.46f).dp),
            )
        }
    }
}
