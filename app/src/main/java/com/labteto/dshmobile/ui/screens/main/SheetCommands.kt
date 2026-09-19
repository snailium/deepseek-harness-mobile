package com.labteto.dshmobile.ui.screens.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.R
import com.labteto.dshmobile.core.wire.dto.CommandDescriptor
import com.labteto.dshmobile.core.wire.dto.SkillEntry
import com.labteto.dshmobile.ui.components.DsBottomSheet
import com.labteto.dshmobile.ui.components.FeatherIcons
import com.labteto.dshmobile.ui.components.SearchField
import com.labteto.dshmobile.ui.components.SearchFieldCloseButton
import com.labteto.dshmobile.ui.theme.DsShapes
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType

/**
 * The `/` sheet: the harness's own commands and skills.
 *
 * Commands come from the harness's per-session catalog rather than a hardcoded list, because which
 * commands exist depends on the deployment and on the session's agent preset. When the harness
 * exposes no catalog the section says so instead of inventing entries — a menu that offers a
 * command the host will reject is worse than a menu that admits it does not know.
 *
 * Attaching and the Queue/Steer mode used to live here too. They moved out: the composer's `+`
 * button was three unrelated things in one menu (a command palette, a file picker and a send-mode
 * switch), and the split is now `/` for commands and the paperclip for attachments. The send mode
 * went to the session details panel, where a working preference belongs rather than a per-message
 * menu.
 */
@Composable
internal fun CommandSheet(
    commands: List<CommandDescriptor>,
    commandsAvailable: Boolean,
    skills: List<SkillEntry>,
    onRunCommand: (String) -> Unit,
    onPrefillDraft: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = DsTheme.colors
    var query by remember { mutableStateOf("") }
    val filteredCommands = remember(commands, query) { commands.filterByQuery(query) { it.name to it.description } }
    val filteredSkills = remember(skills, query) { skills.filterByQuery(query) { it.name to it.description } }
    val searchable = commands.size + skills.size > 12

    DsBottomSheet(title = stringResource(R.string.chat_composer_commands), onDismiss = onDismiss) {
        if (searchable) {
            // The app's own search pill, not a Material3 TextField: that one is a 56dp outlined
            // box, so it read as a control from a different app sitting above the list.
            SearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.common_search),
                modifier = Modifier.fillMaxWidth(),
                trailing = if (query.isNotEmpty()) {
                    { SearchFieldCloseButton(onClick = { query = "" }) }
                } else {
                    null
                },
            )
        }

        // Commands ----------------------------------------------------------
        // One size up from the shared SectionHeader (14 → 16): inside a sheet these two headers
        // are the only chrome above the rows, and at 14 they read as just another row label.
        Text(
            stringResource(R.string.chat_composer_commands),
            style = DsType.base16Strong,
            color = colors.labelSecondary,
            modifier = Modifier.fillMaxWidth(),
        )
        when {
            !commandsAvailable -> Text(
                stringResource(R.string.chat_commands_unavailable),
                style = DsType.caption11,
                color = colors.labelTertiary,
            )
            filteredCommands.isEmpty() -> Text(
                stringResource(R.string.chat_commands_empty),
                style = DsType.caption11,
                color = colors.labelTertiary,
            )
            else -> LazyColumn(Modifier.heightIn(max = 260.dp)) {
                items(filteredCommands, key = { it.name }) { command ->
                    SheetRow(
                        // Always the command itself, never a friendly name. This list used to
                        // special-case four commands into localized labels ("Goal", "Plan mode",
                        // "Download session log", "Submit feedback") while every other entry showed
                        // its own `/name` — so the column mixed two conventions, and the four
                        // localized ones stopped being recognisable as commands at all. The
                        // description beside it is what explains the command; the title's job is to
                        // say what to type.
                        title = command.line,
                        subtitle = command.description.ifBlank { null },
                        trailing = command.input?.hint,
                        onClick = {
                            onDismiss()
                            // A bare command runs immediately; one that takes an argument prefills
                            // the composer so the argument can be typed where the hint is visible.
                            if (command.input == null) {
                                onRunCommand(command.line)
                            } else {
                                onPrefillDraft(command.draftPrefix)
                            }
                        },
                    )
                }
            }
        }

        // Skills ------------------------------------------------------------
        if (filteredSkills.isNotEmpty()) {
            Text(
                stringResource(R.string.skills_title),
                style = DsType.base16Strong,
                color = colors.labelSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(Modifier.heightIn(max = 220.dp)) {
                items(filteredSkills, key = { it.name }) { skill ->
                    SheetRow(
                        title = "/${skill.name}",
                        subtitle = skill.description.ifBlank { null },
                        trailing = if (!skill.modelInvocable) {
                            stringResource(R.string.skills_user_only)
                        } else {
                            null
                        },
                        onClick = {
                            onDismiss()
                            onPrefillDraft("/${skill.name} ")
                        },
                    )
                }
            }
        }
    }
}

/**
 * The paperclip sheet: what to attach.
 *
 * Two rows rather than one picker, because the platform makes them different gestures — a photo
 * comes from the media store and a file from the document provider — and the harness treats them
 * differently too: 0.1.3 takes any file, but the image path also feeds the multimodal route.
 */
@Composable
internal fun AttachmentSheet(
    canAttach: Boolean,
    onAttachImage: () -> Unit,
    onAttachFile: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = DsTheme.colors
    DsBottomSheet(title = stringResource(R.string.chat_composer_attach_title), onDismiss = onDismiss) {
        // The sheet's own 8dp gap between children is too tight here: the title sits on top of a
        // two-row list, and at that distance it reads as one block. An extra row of air separates
        // "what this is" from "what you can do".
        Spacer(Modifier.height(DsSpacing.small))
        SheetRow(
            leading = {
                Icon(
                    FeatherIcons.Image,
                    contentDescription = null,
                    tint = colors.labelSecondary,
                    modifier = Modifier.size(20.dp),
                )
            },
            title = stringResource(R.string.chat_composer_attach_image),
            // The row stays enabled exactly when the file one is: both upload against an open
            // session, so both need one.
            subtitle = if (canAttach) null else stringResource(R.string.err_attachment_failed),
            enabled = canAttach,
            onClick = {
                onDismiss()
                onAttachImage()
            },
        )
        // Harness 0.1.3 takes any file, not only pictures. The bytes go up as soon as one is
        // picked and the message cites the receipt.
        SheetRow(
            leading = {
                Icon(
                    FeatherIcons.Paperclip,
                    contentDescription = null,
                    tint = colors.labelSecondary,
                    modifier = Modifier.size(20.dp),
                )
            },
            title = stringResource(R.string.chat_composer_attach_file),
            subtitle = null,
            enabled = canAttach,
            onClick = {
                onDismiss()
                onAttachFile()
            },
        )
    }
}

/** One tappable row of a sheet: title, optional subtitle, optional trailing hint. */
@Composable
internal fun SheetRow(
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val colors = DsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.row)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = DsSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(DsSpacing.medium))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = DsType.std14Strong,
                color = if (enabled) colors.labelPrimary else colors.labelTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = DsType.caption11,
                    color = colors.labelTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(DsSpacing.small))
            Text(
                trailing,
                style = DsType.caption11.copy(fontFamily = DsType.codeFont),
                color = colors.labelCaption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Some hints are long enough to crowd out the description they sit beside
                // (`/goal` advertises five alternatives), so the hint yields, not the name.
                modifier = Modifier.widthIn(max = 120.dp),
            )
        }
    }
}

/** Case-insensitive contains over a row's name and description. */
private inline fun <T> List<T>.filterByQuery(query: String, selector: (T) -> Pair<String, String>): List<T> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return this
    return filter { item ->
        val (name, description) = selector(item)
        fuzzyContains(name, needle) || description.lowercase().contains(needle)
    }
}
