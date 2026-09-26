package com.labteto.dshmobile.ui.screens.main

import androidx.annotation.StringRes
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.labteto.dshmobile.R
import com.labteto.dshmobile.core.wire.dto.AgentPresetEntry
import com.labteto.dshmobile.core.wire.dto.AgentPresetListValue
import com.labteto.dshmobile.core.wire.dto.GoalPhase
import com.labteto.dshmobile.core.wire.dto.JobStatus
import com.labteto.dshmobile.core.wire.dto.SubagentListEntry
import com.labteto.dshmobile.ui.components.StateDotState
import com.labteto.dshmobile.ui.theme.DsTheme

/** Shared label and status mappings for the chat surface. */

@StringRes
internal fun goalPhaseLabelRes(phase: GoalPhase): Int = when (phase) {
    GoalPhase.ACTIVE -> R.string.goal_phase_active
    GoalPhase.PAUSED -> R.string.goal_phase_paused
    GoalPhase.BLOCKED -> R.string.goal_phase_blocked
    GoalPhase.COMPLETE -> R.string.goal_phase_complete
    // A phase from a newer harness: show the goal with no phase word rather than an invented one.
    GoalPhase.UNKNOWN -> R.string.goal_phase_active
}

internal fun todoStatusDot(status: String): StateDotState = when (status) {
    "completed" -> StateDotState.Done
    "in_progress" -> StateDotState.Running
    else -> StateDotState.Idle
}

internal fun jobStatusDot(status: JobStatus): StateDotState = when (status) {
    JobStatus.RUNNING, JobStatus.STOPPING -> StateDotState.Running
    JobStatus.COMPLETED -> StateDotState.Done
    JobStatus.KILLED, JobStatus.FAILED -> StateDotState.Error
    // Neither running nor settled as far as this build can tell, so claim neither.
    JobStatus.UNKNOWN -> StateDotState.Idle
}

/** A job's lifecycle in words, or null for a state this build cannot name. */
@Composable
internal fun jobStatusLabel(status: JobStatus): String? = when (status) {
    JobStatus.RUNNING -> stringResource(R.string.jobs_running)
    JobStatus.STOPPING -> stringResource(R.string.jobs_stopping)
    JobStatus.COMPLETED -> stringResource(R.string.jobs_completed)
    JobStatus.KILLED -> stringResource(R.string.jobs_killed)
    JobStatus.FAILED -> stringResource(R.string.jobs_failed)
    JobStatus.UNKNOWN -> null
}

@Composable
internal fun workflowStatusLabel(status: String?): String? = when (status) {
    "running" -> stringResource(R.string.workflow_running)
    "completed" -> stringResource(R.string.workflow_completed)
    "failed", "error", "cancelled" -> stringResource(R.string.workflow_failed)
    else -> null
}

internal fun workflowMemberDot(status: String?): StateDotState = when (status) {
    "running" -> StateDotState.Running
    "completed" -> StateDotState.Done
    "failed", "error", "cancelled" -> StateDotState.Error
    else -> StateDotState.Idle
}

// ---------------------------------------------------------------------------
// Subagents
// ---------------------------------------------------------------------------

internal fun subagentId(entry: SubagentListEntry): String? = when (entry) {
    is SubagentListEntry.ChildOneShot -> entry.id
    is SubagentListEntry.ChildContinuable -> entry.id
    is SubagentListEntry.Diagnostic -> entry.id
    else -> null
}

internal fun subagentLabel(entry: SubagentListEntry): String? = when (entry) {
    is SubagentListEntry.ChildOneShot -> entry.label
    is SubagentListEntry.ChildContinuable -> entry.label
    is SubagentListEntry.Diagnostic -> entry.reason
    else -> null
}

internal fun subagentRunning(entry: SubagentListEntry): Boolean = when (entry) {
    is SubagentListEntry.ChildOneShot -> entry.activity == "running" || entry.activity == "active"
    is SubagentListEntry.ChildContinuable -> entry.activity == "running" || entry.activity == "active"
    else -> false
}

// ---------------------------------------------------------------------------
// Agent presets
// ---------------------------------------------------------------------------

/**
 * Localized copy for the four presets the harness ships.
 *
 * The host does *not* localize these. It reads `name`/`description` straight out of each preset's
 * `preset.yml`, so `agentPresets/list` answers whatever those files say — and the shipped four
 * declare no `name` at all, which is why they need this table. The harness's own web UI carries the
 * same table for the same four ids.
 *
 * **Keyed on the id, not on a trust tier.** Harness 0.1.7 dropped `trust` from the roster row
 * entirely, so every entry now reads as `UNKNOWN`; gating on `trust == SYSTEM` therefore stopped
 * matching anything and every preset fell through to its raw wire id — the picker listed
 * `standard`, `ptc`, `minimal`, `cordis` in lowercase. The protection the gate was there for is
 * kept by [AgentPresetEntry.displayName], which prefers a name the deployment actually declared:
 * someone who writes their own preset called `standard` still sees their own name.
 */
private fun builtInPresetStrings(id: String): Pair<Int, Int>? = when (id) {
    "standard" -> R.string.preset_standard_name to R.string.preset_standard_desc
    "code" -> R.string.preset_code_name to R.string.preset_code_desc
    "minimal" -> R.string.preset_minimal_name to R.string.preset_minimal_desc
    "cordis" -> R.string.preset_cordis_name to R.string.preset_cordis_desc
    else -> null
}

/**
 * What to call a preset: whatever the deployment named it, else the app's translation for a shipped
 * id, else the raw id.
 *
 * The declared `name` wins over the built-in table, which is what keeps a self-authored preset that
 * reuses a shipped id labelled as its author intended. The wire ids and the built-in table also
 * disagree for one entry — the shipped preset is `ptc` on the wire and `code` in this table — so
 * both spellings are accepted [in builtInPresetStrings]; see [PRESET_ID_ALIASES].
 */
@Composable
internal fun AgentPresetEntry.displayName(): String =
    name?.takeIf { it.isNotBlank() }
        ?: builtInPresetStrings(PRESET_ID_ALIASES[id] ?: id)?.let { stringResource(it.first) }
        ?: id

/**
 * Wire id → the id this app's built-in table is keyed on.
 *
 * The harness ships the Code-Mode preset as `ptc`; this table (and the strings) call it `code`.
 * Before 0.1.7 the mismatch was invisible because `trust` gated the lookup and a missing entry just
 * fell back to the wire name — which the host rendered from `preset.yml`. With `trust` gone, the
 * table is the only source of a readable label, so the alias has to be explicit.
 */
internal val PRESET_ID_ALIASES = mapOf("ptc" to "code")

/**
 * What to call the preset a session is pinned to, given only its id.
 *
 * The roster is host-scoped and arrives on its own schedule, and the chip renders as soon as the
 * session does — so without this the chrome showed a raw wire id (`standard`) until something
 * happened to fetch the list. Falling back to the shipped name for a shipped id is right in every
 * deployment that has not replaced that preset, and corrects itself the moment the roster lands.
 */
@Composable
internal fun agentPresetLabel(id: String, roster: AgentPresetListValue?): String {
    val entry = roster?.presets?.firstOrNull { it.id == id }
    if (entry != null) return entry.displayName()
    return builtInPresetStrings(PRESET_ID_ALIASES[id] ?: id)?.let { stringResource(it.first) } ?: id
}

/**
 * The preset's one-line summary, translated for the shipped four. Null when there is none.
 *
 * Mirrors [displayName]: a description the deployment declared wins over the built-in table, so a
 * self-authored preset keeps its own words.
 */
@Composable
internal fun AgentPresetEntry.displayDescription(): String? =
    description?.takeIf { it.isNotBlank() }
        ?: builtInPresetStrings(PRESET_ID_ALIASES[id] ?: id)?.let { stringResource(it.second) }

// ---------------------------------------------------------------------------
// Shared field styling
// ---------------------------------------------------------------------------

@Composable
internal fun dialogTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = DsTheme.colors.bgLayer1,
    unfocusedContainerColor = DsTheme.colors.bgLayer1,
    focusedIndicatorColor = DsTheme.colors.accent,
    unfocusedIndicatorColor = DsTheme.colors.borderL2,
    cursorColor = DsTheme.colors.accent,
)
