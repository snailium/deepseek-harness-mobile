package com.labteto.dshmobile.ui.screens.main

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.labteto.dshmobile.R
import com.labteto.dshmobile.core.wire.RpcResult
import com.labteto.dshmobile.core.wire.dto.CommandDescriptor
import com.labteto.dshmobile.core.wire.dto.ImageLimitsView
import com.labteto.dshmobile.core.wire.dto.CommandSubmitAttachment
import com.labteto.dshmobile.data.CommandOutcome
import kotlinx.coroutines.launch
import com.labteto.dshmobile.data.PromptOutcome
import com.labteto.dshmobile.ui.components.rememberDsToast
import android.util.Base64
import com.labteto.dshmobile.core.session.PhotoBatchAdmission
import com.labteto.dshmobile.core.session.readImageBounded
import com.labteto.dshmobile.core.wire.dto.ImageRejection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.labteto.dshmobile.data.SessionStore
import java.util.UUID

/**
 * Everything the composer's send button does, in one object.
 *
 * This was six local functions and two launchers inside `ChatScreen`, which is what made that
 * screen's layout half unreadable: the layout needed `send`, `startUpload`, `filePicker`, the
 * draft, the attachment list and the submit flags, so every parameter list that carried them grew
 * by seven. Grouping them means the layout takes one object.
 *
 * The rule this follows is the same one the docks and the transcript finder follow: **state that
 * changes together belongs together.** Draft text, send mode, pending attachments and the two
 * in-flight flags all describe "a message being composed", and a reader who has one of them has a
 * reason to want the others.
 *
 * [ComposerDraft] stays the owner of the *values* — it outlives this holder, because it is kept by
 * `ComposerRepository` so a gallery callback and an upload never follow UI navigation. This object
 * owns the *operations* on them.
 */
internal class ComposerSubmitter(
    private val store: SessionStore,
    private val composer: ComposerDraft,
    private val context: android.content.Context,
    private val toast: (String) -> Unit,
    /** The harness's command registry, for deciding whether a `/line` is a command or a message. */
    commands: List<CommandDescriptor>,
    imageLimits: ImageLimitsView?,
    /** Reports a command outcome; owned by the screen, which also has the approval path. */
    report: (CommandOutcome) -> Unit,
) {
    // These three arrive after the first composition — `commands` and `imageLimits` are projections,
    // and `report` closes over the screen's toast state — so they are refreshed on every
    // recomposition rather than captured once. A holder built with the first frame's values would
    // refuse commands it had not heard of yet.
    var commands: List<CommandDescriptor> = commands
        internal set
    var imageLimits: ImageLimitsView? = imageLimits
        internal set
    var report: (CommandOutcome) -> Unit = report
        internal set

    val draft: String get() = composer.text
    val attachments get() = composer.attachments
    val submitting: Boolean get() = composer.submitting
    val preparing: Boolean get() = composer.preparing

    fun onDraftChange(next: String) {
        composer.text = next
    }

    /** What the send button does mid-turn; the details panel's card is the durable control. */
    var mode: String
        get() = composer.mode
        set(value) { composer.mode = value }

    fun removeAttachment(index: Int) {
        composer.attachments.removeAt(index)
    }

    /** Retry a file chip whose upload failed. */
    fun retryAttachment(index: Int) {
        (composer.attachments.getOrNull(index) as? PendingAttachment.File)?.let { startUpload(it) }
    }

    /** Replace one pending file by identity; a chip that was removed meanwhile is left removed. */
    fun updateFile(
        target: ComposerDraft,
        id: String,
        transform: (PendingAttachment.File) -> PendingAttachment.File,
    ) {
        val attachments = target.attachments
        val index = attachments.indexOfFirst { it is PendingAttachment.File && it.id == id }
        if (index >= 0) attachments[index] = transform(attachments[index] as PendingAttachment.File)
    }

    /**
     * Stream one picked file to the host and settle its chip.
     *
     * The upload starts the moment the file is picked, as the web client's does, so by the time the
     * message is sent the receipt is usually already there; the chip shows progress until it is, and
     * the send affordance waits for it.
     */
    fun startUpload(file: PendingAttachment.File, target: ComposerDraft = composer) {
        updateFile(target, file.id) { it.copy(state = FileUploadState.Uploading(0)) }
        store.composers.scope.launch {
            val result = store.uploadFile(
                name = file.name,
                targetSessionId = target.key.sessionId,
                targetHost = target.key.host,
                size = file.size,
                open = { runCatching { context.contentResolver.openInputStream(file.uri) }.getOrNull() },
                onProgress = { sent ->
                    updateFile(target, file.id) { it.copy(state = FileUploadState.Uploading(sent)) }
                },
            )
            updateFile(target, file.id) {
                when (result) {
                    is RpcResult.Ok -> it.copy(
                        state = FileUploadState.Ready(result.value.receiptId, result.value.file),
                    )
                    is RpcResult.Err -> it.copy(state = FileUploadState.Failed(result.error.message))
                }
            }
        }
    }

    /**
     * Admit a picked image batch and stage the survivors.
     *
     * The admission rules are the harness's, not ours: per-message count, total bytes, and the
     * decoded dimensions all have to fit before an image is worth encoding, and a batch that is
     * partly refused still stages what passed. `PhotoBatchAdmission` is the same object the send
     * path consults, so a chip that is staged is a chip that will be accepted.
     */
    fun acceptPickedImages(uris: List<android.net.Uri>) {
        val selection = store.composers.imagePickTarget
        val target = selection?.first
        store.composers.imagePickTarget = null
        if (target != null && uris.isNotEmpty()) {
            target.preparing = true
            val limits = selection.second
            store.composers.scope.launch {
                val failures = mutableListOf<String>()
                try {
                    val existing = target.attachments.filterIsInstance<PendingAttachment.Image>()
                    val accepted = withContext(Dispatchers.IO) {
                        val selected = mutableListOf<PendingAttachment.Image>()
                        val admission = PhotoBatchAdmission(limits, existing.map { it.bytes })
                        for (uri in uris) {
                            try {
                                if (admission.full) {
                                    failures.add(imageRejectionText(context, ImageRejection.TOO_MANY, limits))
                                    continue
                                }
                                val resolver = context.contentResolver
                                val mediaType = resolver.getType(uri)
                                val bytes = (resolver.openInputStream(uri) ?: throw java.io.IOException("Unreadable image" )).use {
                                    readImageBounded(it, limits.maxImageBytes.coerceIn(0, Int.MAX_VALUE.toLong() - 1))
                                }
                                if (bytes == null) {
                                    failures.add(imageRejectionText(context, ImageRejection.TOO_LARGE, limits))
                                    continue
                                }
                                val pick = decodePick(bytes)
                                val rejection = admission.accept(
                                    mediaType.orEmpty(), pick.detectedMediaType, bytes.size, pick.width, pick.height,
                                )
                                if (rejection != null) {
                                    failures.add(imageRejectionText(context, rejection, limits))
                                    continue
                                }
                                selected.add(PendingAttachment.Image(
                                    mediaType = mediaType.orEmpty(), base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
                                    preview = pick.preview, bytes = bytes.size, width = pick.width, height = pick.height,
                                ))
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { failures.add(context.getString(R.string.err_attachment_failed)) }
                        }
                        selected
                    }
                    target.attachments.addAll(accepted)
                    if (failures.isNotEmpty()) toast(
                        context.getString(
                            R.string.photos_skipped, failures.size, failures.distinct().joinToString("; "),
                        ),
                    )
                } finally { target.preparing = false }
            }
        }
    }

    /** Register a picked document and begin its upload. Returns the launcher's callback body. */
    fun acceptPickedFile(uri: Uri?) {
        val target = store.composers.filePickTarget
        store.composers.filePickTarget = null
        if (uri == null || target == null) return
        val (name, size) = describeDocument(context.contentResolver, uri)
        if (name == null) {
            toast(context.getString(R.string.err_file_read_failed))
            return
        }
        val file = PendingAttachment.File(
            id = UUID.randomUUID().toString(),
            uri = uri,
            name = name,
            size = size,
            state = FileUploadState.Uploading(0),
        )
        target.attachments.add(file)
        startUpload(file, target)
    }

    /**
     * Submit the composer's contents, as a command or as a prompt.
     *
     * A refusal restores the draft and leaves the attachments staged: a refusal the reader cannot
     * act on without re-picking every file is not much of a refusal. The same applies to a failed
     * submission — an error is something to correct, and correcting it should not start with
     * picking the files again.
     */
    fun send(text: String) {
        if (composer.preparing || composer.submitting) {
            composer.text = text
            return
        }
        val delivery = composer.mode
        val targetId = composer.key.sessionId
        val targetHost = composer.key.host
        val pending = composer.attachments.toList()
        if (text.isBlank() && pending.isEmpty()) return
        val images = pending.filterIsInstance<PendingAttachment.Image>()
        val files = pending.filterIsInstance<PendingAttachment.File>()
        // A file without a receipt cannot be cited. The send affordance already waits for the
        // chips, but a keyboard send lands here too.
        if (files.any { it.state !is FileUploadState.Ready }) {
            composer.text = text
            toast(
                context.getString(
                    if (files.any { it.state is FileUploadState.Failed }) {
                        R.string.chat_attachment_upload_failed
                    } else {
                        R.string.chat_attachment_still_uploading
                    },
                ),
            )
            return
        }
        val receipts = files.mapNotNull { it.receiptId }
        // A slash line that names a registered command is not a message: `session/prompt` would
        // hand it to the model verbatim, so it has to be recognised here and written through the
        // command gateway. A miss falls through to the prompt path — that is how skills work.
        when (val submission = adjudicate(text, commands, pending.size, store.commandAttachmentsSupported)) {
            is Submission.Refused -> {
                composer.text = text
                val message = when (submission.reason) {
                    RefusalReason.COMMAND_TAKES_NO_ATTACHMENTS -> R.string.err_command_no_images
                    RefusalReason.HOST_TOO_OLD -> R.string.err_command_images_host
                }
                toast(context.getString(message, submission.command))
            }

            is Submission.Command -> {
                composer.submitting = true
                composer.attachments.clear()
                val submitted = images.map { it.encoded().asSubmit() } +
                    receipts.map { CommandSubmitAttachment.File(it) }
                store.composers.scope.launch {
                    try {
                        val outcome = store.runCommand(submission.line, submitted, targetId, targetHost)
                        // An attachment-carrying command consumes its attachments only on success.
                        // A plain command that fails keeps today's behaviour, because its whole
                        // submission was the line. The restore only lands in a composer nobody has
                        // touched meanwhile — the call is in flight while the user can still type.
                        if (outcome is CommandOutcome.Failed) composer.restoreRejected(text, pending)
                        report(outcome)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        composer.restoreRejected(text, pending)
                        throw e
                    } catch (e: Exception) {
                        composer.restoreRejected(text, pending)
                        toast(e.message ?: context.getString(R.string.panel_failed))
                    } finally {
                        composer.submitting = false
                    }
                }
            }

            is Submission.Prompt -> {
                composer.submitting = true
                composer.attachments.clear()
                store.composers.scope.launch {
                    try {
                        // One call, whatever the count. The host admits a prompt's images as a
                        // single batch, and that batch is the only thing its per-message count and
                        // total-size bounds are measured against — sending one image per call made
                        // a single message into several and put both limits permanently out of
                        // reach. Files ride the same call as receipts; a receipt the host refuses
                        // stays staged, so restoring the chips is enough to try again.
                        val outcome = if (pending.isEmpty()) {
                            store.prompt(text, delivery, targetId, targetHost)
                        } else {
                            store.promptWithAttachments(
                                text,
                                delivery,
                                images.map { it.encoded() },
                                receipts,
                                targetId,
                                targetHost,
                            )
                        }
                        if (outcome !is PromptOutcome.Ok) {
                            composer.restoreRejected(text, pending)
                            toast(
                                if (outcome is PromptOutcome.Rejected) {
                                    imageRejectionText(context, outcome.rejection, imageLimits, outcome.reason)
                                } else {
                                    (outcome as PromptOutcome.Failed).message
                                },
                            )
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        composer.restoreRejected(text, pending)
                        throw e
                    } catch (e: Exception) {
                        composer.restoreRejected(text, pending)
                        toast(e.message ?: context.getString(R.string.panel_failed))
                    } finally {
                        composer.submitting = false
                    }
                }
            }
        }
    }

    /** Prefill the draft, used by the model picker's "switch and ask" path. */
    fun prefill(prefix: String) {
        composer.text = prefix
    }
}

/**
 * The composer's submit path for the open session.
 *
 * `commands` and `imageLimits` are passed on every call rather than captured, because both are
 * projections that arrive after the first composition and change during a session; a holder built
 * once with the first frame's values would refuse commands it had not heard of yet.
 */
@Composable
internal fun rememberComposerSubmitter(
    store: SessionStore,
    composer: ComposerDraft,
    commands: List<CommandDescriptor>,
    imageLimits: ImageLimitsView?,
    report: (CommandOutcome) -> Unit,
): ComposerSubmitter {
    val context = LocalContext.current
    val toast = rememberDsToast()
    return remember(store, composer) {
        ComposerSubmitter(
            store = store,
            composer = composer,
            context = context,
            toast = { toast.second(it) },
            commands = commands,
            imageLimits = imageLimits,
            report = report,
        )
    }.also {
        // Refreshed each recomposition; the holder itself is remembered so the draft's identity
        // and the in-flight flags survive.
        it.commands = commands
        it.imageLimits = imageLimits
        it.report = report
    }
}

/** The file picker launcher, wired to the submitter. */
@Composable
internal fun rememberFilePicker(submitter: ComposerSubmitter) =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        submitter.acceptPickedFile(uri)
    }

/** The image picker launcher, wired to the submitter. Multi-select, because a batch is admitted. */
@Composable
internal fun rememberImagePicker(submitter: ComposerSubmitter) =
    rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        submitter.acceptPickedImages(uris)
    }
