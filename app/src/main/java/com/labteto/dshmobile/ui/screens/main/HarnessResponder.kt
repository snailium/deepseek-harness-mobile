package com.labteto.dshmobile.ui.screens.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.labteto.dshmobile.R
import com.labteto.dshmobile.data.CommandOutcome
import com.labteto.dshmobile.data.QuestionOutcome
import com.labteto.dshmobile.data.SessionStore
import kotlinx.coroutines.launch

/**
 * Everything that answers the harness when it asks the reader something.
 *
 * Three call sites share one shape: the approval panel's allow/reject, the plan review's
 * approve/decline/discuss, and the questions panel's submit/dismiss. Each sends an answer and then
 * has to decide what to *say* about the reply — and the answer is not "nothing went wrong means
 * nothing to say", because a refusal leaves the host's wait open and the tool call behind it
 * blocked. A panel that reported nothing would read as buttons that do nothing.
 *
 * That mapping is the reason this is one object rather than three lambdas: `QuestionOutcome`
 * becomes a message in exactly one place, and `report` for command outcomes sits beside it because
 * a command run from the sheet and a question answered from a panel are the same kind of event from
 * the reader's side — the harness replied, and here is what it said.
 *
 * [busy] is exposed for the plan review, whose buttons disable while its answer is in flight. The
 * approval and question panels do not need it: their answers settle the request, so the card
 * disappears rather than waiting.
 */
internal class HarnessResponder(
    private val store: SessionStore,
    private val toast: (String) -> Unit,
    strings: Strings,
) {
    /** Refreshed on every recomposition by the factory; the holder is remembered so [busy] survives. */
    var strings: Strings = strings
        internal set

    /** Message resources, read from composition and passed in — a holder cannot call `stringResource`. */
    data class Strings(
        val commandFailed: String,
        val unknownCommand: String,
        val answerRefused: String,
        val answerUnsent: String,
    )

    /** True while an answer this object sent is still in flight. */
    var busy: Boolean = false
        private set

    /** What to tell the reader about a command the harness ran, or nothing when it succeeded quietly. */
    fun report(outcome: CommandOutcome) {
        when (outcome) {
            is CommandOutcome.Ok -> outcome.text?.takeIf { it.isNotBlank() }?.let(toast)
            is CommandOutcome.Unknown -> toast(strings.unknownCommand.format(outcome.line))
            is CommandOutcome.Failed -> toast(strings.commandFailed.format(outcome.message))
        }
    }

    /**
     * What to tell the reader about a question response, or null when the harness took it.
     *
     * A refusal is worth naming rather than swallowing: the host's wait stays open and the tool call
     * that opened it stays blocked, so a card that quietly did nothing would leave the session
     * stuck with no explanation.
     */
    fun refusalOf(outcome: QuestionOutcome): String? = when (outcome) {
        is QuestionOutcome.Accepted -> null
        is QuestionOutcome.Refused -> strings.answerRefused.format(outcome.reason)
        is QuestionOutcome.Unsent -> strings.answerUnsent
    }

    /**
     * Approve or reject a pending tool call.
     *
     * The answer rides the session's own composer scope rather than a scope of this holder's: it is
     * the same lifetime — the call must complete even if the reader navigates away mid-flight, and
     * it must die with the session, not with the composition that happened to send it.
     */
    fun respondToApproval(sessionId: String, approvalId: String, allow: Boolean) {
        store.composers.scope.launch {
            refusalOf(store.respondApproval(sessionId, approvalId, allow))?.let(toast)
        }
    }

    /**
     * Answer a question batch, *returning* the refusal text rather than showing it.
     *
     * `QuestionsPanel` renders its own error inline — the reader is looking at the form they just
     * filled in, and a toast that appears over it and fades is the wrong place for "the host
     * refused this answer". So the panel's contract is `suspend (…) -> String?`, and this adapts
     * the responder's mapping to it instead of duplicating the `when`.
     */
    suspend fun answerQuestionsOrRefusal(
        sessionId: String,
        answer: com.labteto.dshmobile.core.wire.dto.AskUserQuestionAnswer,
    ): String? = refusalOf(store.answerQuestions(sessionId, answer))

    /** End a question request unanswered, with the same return contract. */
    suspend fun dismissQuestionsOrRefusal(sessionId: String): String? =
        refusalOf(store.dismissQuestions(sessionId))

    /** End a question request unanswered, reporting the refusal as a toast. */
    fun dismissQuestions(sessionId: String) {
        store.composers.scope.launch {
            dismissQuestionsOrRefusal(sessionId)?.let(toast)
        }
    }

    /**
     * Answer a plan review, holding [busy] for the duration.
     *
     * `busy` clears only on a *refusal*, matching what the panel needs: an accepted answer settles
     * the request, so the card goes away and a cleared flag would be invisible either way.
     */
    fun answerPlanReview(
        sessionId: String,
        answer: com.labteto.dshmobile.core.wire.dto.AskUserQuestionAnswer,
    ) {
        busy = true
        store.composers.scope.launch {
            refusalOf(store.answerQuestions(sessionId, answer))?.let {
                busy = false
                toast(it)
            }
        }
    }
}

/** The responder for the open session, with its message resources read from composition. */
@Composable
internal fun rememberHarnessResponder(
    store: SessionStore,
    toast: (String) -> Unit,
): HarnessResponder {
    val strings = HarnessResponder.Strings(
        commandFailed = stringResource(R.string.err_command_failed),
        unknownCommand = stringResource(R.string.err_command_unknown),
        answerRefused = stringResource(R.string.questions_answer_refused),
        answerUnsent = stringResource(R.string.questions_answer_unsent),
    )
    return remember(store) {
        HarnessResponder(store = store, toast = toast, strings = strings)
    }.also {
        // Refreshed each recomposition: a locale change or a config reload gives new strings, and
        // the holder is remembered so `busy` survives.
        it.strings = strings
    }
}
