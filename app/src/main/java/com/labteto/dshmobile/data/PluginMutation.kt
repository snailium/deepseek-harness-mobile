package com.labteto.dshmobile.data

/**
 * The plugin toggle in flight, enough for a page to spin the right switch.
 *
 * [target] is an entry id for a single row and a bundle name for a bundle, and the page compares it
 * against whichever row it is drawing. That is why it is carried rather than the page holding a
 * boolean: one switch changes at a time but a page draws every switch, and only the one being
 * mutated should look busy.
 */
data class PluginMutationState(
    val target: String,
    val enabled: Boolean,
)

/**
 * What became of one plugin toggle.
 *
 * Three outcomes rather than a success flag, because the middle one is the case a caller gets wrong:
 * the Host answers a *successful* call with `application: failed` when it refuses to touch a row, so
 * "the request succeeded" and "the plugin changed" are different facts and the UI has to tell the
 * user which one happened.
 */
sealed interface PluginMutationOutcome {
    /** The Host applied the change. */
    data class Applied(val target: String?, val enabled: Boolean) : PluginMutationOutcome

    /**
     * The Host declined, with [reason] naming why in its own vocabulary.
     *
     * A reason is a value from `PluginReadOnly` when the row is structurally off limits, or a manager
     * refusal such as `unknown-plugin` when the target no longer exists. The page maps the ones it
     * knows to a sentence and falls back to showing the code, so an unfamiliar reason is still
     * reported rather than swallowed.
     */
    data class Refused(val target: String?, val reason: String?) : PluginMutationOutcome

    /** The call itself failed — no connection, or a harness without the manager. */
    data class Failed(val code: String) : PluginMutationOutcome
}
