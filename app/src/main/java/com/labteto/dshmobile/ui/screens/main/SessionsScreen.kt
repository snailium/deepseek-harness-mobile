package com.labteto.dshmobile.ui.screens.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labteto.dshmobile.R
import com.labteto.dshmobile.data.SessionRow
import com.labteto.dshmobile.data.SessionStore
import com.labteto.dshmobile.data.WorkspaceRow
import com.labteto.dshmobile.ui.components.DisclosureRow
import com.labteto.dshmobile.ui.components.DsBottomSheet
import com.labteto.dshmobile.ui.components.DsButton
import com.labteto.dshmobile.ui.components.DsButtonVariant
import com.labteto.dshmobile.ui.components.DsDialog
import com.labteto.dshmobile.ui.components.DsIconButton
import com.labteto.dshmobile.ui.components.DsPill
import com.labteto.dshmobile.ui.components.DsMenu
import com.labteto.dshmobile.ui.components.EmptyHero
import com.labteto.dshmobile.ui.components.MenuItem
import com.labteto.dshmobile.ui.components.SectionHeader
import com.labteto.dshmobile.ui.components.StateDot
import com.labteto.dshmobile.ui.components.StateDotState
import com.labteto.dshmobile.ui.components.relativeTime
import com.labteto.dshmobile.ui.rememberHostsStore
import com.labteto.dshmobile.ui.rememberSessionStore
import com.labteto.dshmobile.ui.theme.DsAnimations
import com.labteto.dshmobile.ui.theme.DsShapes
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType
import com.labteto.dshmobile.ui.components.FeatherIcons
import com.labteto.dshmobile.ui.components.autoMirrorDirectional
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** [com.labteto.dshmobile.connection.HostsStore.sessionSort]: the workspace's own row order. */
private const val SORT_MANUAL = "manual"

/** [com.labteto.dshmobile.connection.HostsStore.sessionSort]: most recently updated first. */
private const val SORT_UPDATED = "updated"

/**
 * The Sessions screen: the full-screen chat history, pushed over the chat the way Messages pushes
 * its conversation list.
 *
 * Two rules keep it readable. Blank sessions are hidden — the harness treats a session with no turn
 * as scratch space and reuses it, so listing them just accumulates empty rows. And times are
 * relative, because a clock time cannot distinguish "an hour ago" from "last Tuesday".
 *
 * The chrome follows iOS: a compact navigation bar — back chevron, centered title, host switcher
 * and sort on the trailing side, all on one baseline — a 40dp search capsule with a Cancel button
 * while it is focused, a plain hairline-separated list with swipe-to-archive, and a floating
 * compose button (the Messages pattern) that creates a session or a workspace.
 */
@Composable
fun SessionsScreen(
    hostLabel: String?,
    onSwitchHost: () -> Unit,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val colors = DsTheme.colors
    val store = rememberSessionStore()
    val scope = rememberCoroutineScope()
    val hostsStore = rememberHostsStore()
    val focusManager = LocalFocusManager.current

    val sessions by store.sessions.collectAsStateWithLifecycle()
    val workspaces by store.workspaces.collectAsStateWithLifecycle()
    val archivedIds by store.archivedSessionIds.collectAsStateWithLifecycle()
    val searchResults by store.searchResults.collectAsStateWithLifecycle()
    val contentSearchAvailable by store.contentSearchAvailable.collectAsStateWithLifecycle()
    val currentSessionId by store.currentSessionId.collectAsStateWithLifecycle()
    val hostInfo by store.hostInfo.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    // Persisted, not remembered: the order you read your sessions in is a preference, and it used
    // to reset every time the screen was left.
    val sessionSort by hostsStore.sessionSort.collectAsStateWithLifecycle(initialValue = SORT_MANUAL)
    val sortByRecency = sessionSort == SORT_UPDATED
    var composeOpen by remember { mutableStateOf(false) }
    var newWorkspaceOpen by remember { mutableStateOf(false) }
    var newSessionOpen by remember { mutableStateOf(false) }
    // The iOS Cancel button appears next to the search field while it has focus.
    var searchFocused by remember { mutableStateOf(false) }
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(query) {
        delay(250)
        store.search(query.trim())
    }

    // Local matching is not debounced: it is a string comparison over a list already in memory, and
    // making someone wait a quarter second for it is what made search feel like it did nothing.
    val searchHits = remember(sessions, workspaces, archivedIds, query, searchResults) {
        deriveSearchResults(
            sessions = sessions,
            workspaces = workspaces,
            archivedIds = archivedIds,
            query = query,
            contentHits = searchResults,
        )
    }

    // Blank sessions are scratch space the harness reuses; subagent transcripts belong under their
    // parent, not as top-level rows.
    val listable = sessions.filter { it.sessionId !in archivedIds && !it.blank }
    val sessionsById = sessions.associateBy { it.sessionId }
    val archivedSessions = sessions.filter { it.sessionId in archivedIds }
    val workspaceSessionIds = workspaces.flatMap { it.sessionIds }.toSet()

    // Subagents nest under the session that spawned them. `origin` is the discriminator, not
    // `parentSessionId` — an ordinary fork sets a parent too, and a fork is a session in its own
    // right that belongs at the top level. Grouping is by *immediate* parent so a subagent that
    // spawned its own subagents nests to whatever depth the run actually reached; a child whose
    // parent is archived or blank attaches to the nearest ancestor still on screen instead of
    // disappearing with it.
    val childrenByParent = remember(listable) { indexSubagents(listable, sessionsById) }
    val nestedIds = remember(childrenByParent) {
        childrenByParent.values.flatten().mapTo(HashSet()) { it.sessionId }
    }
    // Every session between the open one and the root, so a subtree holding it opens by default.
    val openPath = remember(currentSessionId, sessionsById) {
        buildSet {
            var cursor = currentSessionId?.let { sessionsById[it] }
            while (cursor != null && add(cursor.sessionId)) {
                cursor = cursor.parentSessionId?.let { sessionsById[it] }
            }
        }
    }

    // `collapsed` holds explicit choices only; the default is closed unless the subtree holds the
    // session you are looking at, so opening the screen mid-run shows you where you are.
    fun isExpanded(sessionId: String): Boolean = collapsed[sessionId]?.not() ?: (sessionId in openPath)
    fun toggleChildren(sessionId: String) {
        collapsed[sessionId] = isExpanded(sessionId)
    }

    /** Depth-first expansion of one top-level session, honouring each row's collapse state. */
    fun subtree(root: SessionRow): List<Pair<SessionRow, Int>> {
        val out = mutableListOf<Pair<SessionRow, Int>>()
        fun walk(row: SessionRow, depth: Int) {
            out += row to depth
            val children = childrenByParent[row.sessionId].orEmpty()
            if (children.isEmpty() || !isExpanded(row.sessionId)) return
            val ordered = if (sortByRecency) children.sortedByDescending(SessionRow::updatedAt) else children
            ordered.forEach { walk(it, depth + 1) }
        }
        walk(root, 0)
        return out
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgBase)
            .safeDrawingPadding(),
    ) {
        // ---- iOS navigation bar: back · centered title · host switcher + sort ----
        // One line, one baseline. The 28sp large title and the two-line header stack are gone,
        // and with them the sort chip that used to float unaligned beside the host's second line.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = DsSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DsIconButton(
                icon = FeatherIcons.ArrowLeft,
                contentDescription = stringResource(R.string.common_back),
                onClick = onClose,
                mirrorForRtl = true,
            )
            // Centered in the space between the back button and the trailing controls, so a long
            // host name can never push it under either one.
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.chatlist_title),
                    style = DsType.navTitle,
                    color = colors.labelPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // The host is a compact chip on the title's row, itself the anchor for its verbs:
            // switching harnesses or opening Settings is one tap, no card or footer row needed.
            HostChip(
                hostLabel = hostLabel,
                onSwitchHost = onSwitchHost,
                onOpenSettings = onOpenSettings,
            )
            Spacer(Modifier.width(DsSpacing.xsmall))
            SortButton { next ->
                scope.launch { hostsStore.setSessionSort(if (next) SORT_UPDATED else SORT_MANUAL) }
            }
        }

        // ---- Search: 40dp iOS capsule; Cancel fades in beside it while focused ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DsSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchCapsule(
                modifier = Modifier.weight(1f),
                query = query,
                onQueryChange = { query = it },
                onFocusChange = { searchFocused = it },
            )
            AnimatedVisibility(
                visible = searchFocused,
                enter = fadeIn(DsAnimations.fade) + slideInHorizontally(initialOffsetX = { it / 3 }),
                exit = fadeOut(DsAnimations.fade) + slideOutHorizontally(targetOffsetX = { it / 3 }),
            ) {
                Text(
                    stringResource(R.string.common_cancel),
                    style = DsType.body17.copy(fontWeight = FontWeight.Medium),
                    color = colors.accent,
                    modifier = Modifier
                        .clip(DsShapes.row)
                        .clickable(role = Role.Button, onClick = {
                            query = ""
                            focusManager.clearFocus()
                        })
                        .padding(horizontal = DsSpacing.medium, vertical = DsSpacing.small),
                )
            }
        }
        // Stated once, quietly, and only while searching. Most harnesses ship with the content
        // index off, so this is a normal capability note — not a failure.
        if (!contentSearchAvailable && query.isNotBlank()) {
            Text(
                stringResource(R.string.chatlist_search_content_off),
                style = DsType.caption11,
                color = colors.labelCaption,
                modifier = Modifier.padding(start = DsSpacing.medium, top = DsSpacing.tiny),
            )
        }

        // ---- The list, with the compose action floating over it (Messages pattern) ----
        Box(Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Room for the floating compose button at the trailing bottom corner.
            contentPadding = PaddingValues(top = DsSpacing.xsmall, bottom = 96.dp),
        ) {
            if (query.isNotBlank()) {
                item(key = "search-header") {
                    SectionHeader(
                        stringResource(R.string.common_search),
                        modifier = Modifier.padding(horizontal = DsSpacing.medium),
                    )
                }
                if (searchHits.items.isEmpty()) {
                    item(key = "search-empty") {
                        Text(
                            stringResource(R.string.chatlist_search_empty),
                            style = DsType.std14,
                            color = colors.labelTertiary,
                            modifier = Modifier.padding(vertical = DsSpacing.small),
                        )
                    }
                }
                items(searchHits.items, key = { it.session.sessionId }) { hit ->
                    SearchResultRow(hit, store, scope, onClose)
                }
                if (searchHits.hasMore) {
                    item(key = "search-more") {
                        Text(
                            stringResource(R.string.chatlist_search_refine),
                            style = DsType.caption11,
                            color = colors.labelCaption,
                            modifier = Modifier.padding(vertical = DsSpacing.xsmall),
                        )
                    }
                }
                return@LazyColumn
            }

            var anyShown = false
            for (workspace in workspaces) {
                val roots = workspace.sessionIds
                    .mapNotNull { id -> listable.firstOrNull { it.sessionId == id } }
                    .filterNot { it.sessionId in nestedIds }
                    .let { if (sortByRecency) it.sortedByDescending(SessionRow::updatedAt) else it }
                if (roots.isEmpty()) continue
                anyShown = true
                // Only the workspace you are working in is open by default. With twenty sessions
                // and their subagents in one group, expanding everything buries the list you came
                // for; the explicit map entry then remembers whatever you choose.
                val holdsCurrent = roots.any { it.sessionId in openPath }
                val isCollapsed = collapsed[workspace.workspaceId] ?: !holdsCurrent
                item(key = "ws-${workspace.workspaceId}") {
                    WorkspaceHeader(
                        workspace = workspace,
                        collapsed = isCollapsed,
                        // Sessions, not sessions-plus-their-subagents: a subagent count belongs on
                        // the row that spawned them, where it says something.
                        sessionCount = roots.size,
                        onToggle = { collapsed[workspace.workspaceId] = !isCollapsed },
                        store = store,
                        scope = scope,
                        onNewSession = {
                            scope.launch {
                                store.createSession(workspaceId = workspace.workspaceId)
                                onClose()
                            }
                        },
                    )
                }
                if (!isCollapsed) {
                    val flat = roots.flatMap { subtree(it) }
                    items(flat, key = { it.first.sessionId }) { (session, depth) ->
                        Box(Modifier.animateItem()) {
                            SessionRowItem(
                                session = session,
                                isCurrent = session.sessionId == currentSessionId,
                                store = store,
                                scope = scope,
                                onClose = onClose,
                                depth = depth,
                                childCount = childrenByParent[session.sessionId].orEmpty().size,
                                childrenExpanded = isExpanded(session.sessionId),
                                onToggleChildren = { toggleChildren(session.sessionId) },
                            )
                        }
                    }
                }
            }

            // Sessions the harness never registered in a workspace, plus any subagent whose whole
            // ancestry is archived or blank — those have no row left to nest under.
            val ungrouped = listable.filter {
                it.sessionId !in workspaceSessionIds && it.sessionId !in nestedIds
            }
            if (ungrouped.isNotEmpty()) {
                anyShown = true
                item(key = "sessions-header") {
                    SectionHeader(
                        stringResource(R.string.chatlist_sessions),
                        modifier = Modifier.padding(horizontal = DsSpacing.medium),
                    )
                }
                val flat = ungrouped
                    .let { if (sortByRecency) it.sortedByDescending(SessionRow::updatedAt) else it }
                    .flatMap { subtree(it) }
                items(flat, key = { it.first.sessionId }) { (session, depth) ->
                    Box(Modifier.animateItem()) {
                        SessionRowItem(
                            session = session,
                            isCurrent = session.sessionId == currentSessionId,
                            store = store,
                            scope = scope,
                            onClose = onClose,
                            depth = depth,
                            childCount = childrenByParent[session.sessionId].orEmpty().size,
                            childrenExpanded = isExpanded(session.sessionId),
                            onToggleChildren = { toggleChildren(session.sessionId) },
                        )
                    }
                }
            }

            if (archivedSessions.isNotEmpty()) {
                anyShown = true
                item(key = "archived") {
                    var archivedExpanded by remember { mutableStateOf(false) }
                    DisclosureRow(
                        title = stringResource(R.string.chatlist_archived),
                        summary = archivedSessions.size.toString(),
                        expanded = archivedExpanded,
                        onToggle = { archivedExpanded = !archivedExpanded },
                        modifier = Modifier.padding(horizontal = DsSpacing.medium),
                    ) {
                        archivedSessions.forEach { session ->
                            SessionRowItem(session, false, store, scope, onClose)
                        }
                    }
                }
            }

            if (!anyShown) {
                item(key = "empty") {
                    EmptyHero(
                        headline = stringResource(R.string.chatlist_empty),
                        subtitle = stringResource(R.string.chatlist_empty_hint),
                    )
                }
            }
        }

        Surface(
                onClick = { composeOpen = true },
                shape = CircleShape,
                color = colors.buttonInfoFill,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = DsSpacing.large, bottom = DsSpacing.large)
                    .size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        FeatherIcons.Plus,
                        contentDescription = stringResource(R.string.chatlist_new_session),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }

    // The compose button opens an action sheet: creating a session and creating a workspace are
    // the two things "make something new" can mean here.
    if (composeOpen) {
        DsBottomSheet(title = null, onDismiss = { composeOpen = false }) {
            SheetRow(
                title = stringResource(R.string.chatlist_new_session),
                leading = {
                    Icon(
                        FeatherIcons.MessageSquare,
                        contentDescription = null,
                        tint = colors.labelSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                },
                onClick = {
                    composeOpen = false
                    newSessionOpen = true
                },
            )
            SheetRow(
                title = stringResource(R.string.chatlist_new_workspace),
                leading = {
                    Icon(
                        FeatherIcons.Folder,
                        contentDescription = null,
                        tint = colors.labelSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                },
                onClick = {
                    composeOpen = false
                    newWorkspaceOpen = true
                },
            )
        }
    }

    if (newSessionOpen) {
        NewSessionDialog(
            workspaces = workspaces,
            homeCwd = hostInfo?.cwd,
            onPick = { workspaceId ->
                newSessionOpen = false
                scope.launch {
                    store.createSession(workspaceId = workspaceId)
                    onClose()
                }
            },
            onDismiss = { newSessionOpen = false },
        )
    }

    if (newWorkspaceOpen) {
        NewWorkspaceDialog(
            onDismiss = { newWorkspaceOpen = false },
            onCreate = { path ->
                scope.launch {
                    store.createWorkspace(path)
                    newWorkspaceOpen = false
                }
            },
        )
    }
}

/**
 * The host identity as a compact chip on the navigation bar's trailing side, itself the anchor
 * for its verbs.
 *
 * Tapping the host (or the chevron) offers the two things you can do to the connection: switch to
 * another harness, or open Settings. The old connected-to card, footer rows and the two-line
 * header stack are all gone — the chip is one tappable pill on the title's row.
 */
@Composable
private fun HostChip(
    hostLabel: String?,
    onSwitchHost: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = DsTheme.colors
    if (hostLabel == null) return
    DsMenu(
        anchor = {
            Row(
                modifier = Modifier
                    .heightIn(min = 30.dp)
                    .clip(DsShapes.pillFull)
                    .background(colors.hoverSolid)
                    .padding(horizontal = DsSpacing.compact),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DsSpacing.tiny),
            ) {
                StateDot(StateDotState.Done, size = 6.dp)
                Text(
                    hostLabel,
                    style = DsType.footnote,
                    color = colors.labelSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 132.dp),
                )
                Icon(
                    FeatherIcons.ChevronDown,
                    contentDescription = null,
                    tint = colors.labelTertiary,
                    modifier = Modifier.size(12.dp),
                )
            }
        },
        items = listOf(
            MenuItem(text = stringResource(R.string.chatlist_switch_host)) { onSwitchHost() },
            MenuItem(text = stringResource(R.string.settings_title)) { onOpenSettings() },
        ),
    )
}

/**
 * The session-order control: an icon-only navigation-bar button whose menu names both orders.
 *
 * The chrome is one line now, so the text chip has to go; the menu's labels — already translated
 * in all eleven locales — carry the meaning the icon alone cannot, and the content description
 * says what the button does for assistive tech.
 */
@Composable
private fun SortButton(onPick: (byRecency: Boolean) -> Unit) {
    val colors = DsTheme.colors
    val updated = stringResource(R.string.chatlist_sort_updated)
    val manual = stringResource(R.string.chatlist_sort_manual)
    DsMenu(
        anchor = {
            Icon(
                FeatherIcons.ChevronsUpDown,
                contentDescription = stringResource(R.string.chatlist_sort_title),
                tint = colors.labelSecondary,
                modifier = Modifier.size(18.dp),
            )
        },
        items = listOf(
            MenuItem(text = manual) { onPick(false) },
            MenuItem(text = updated) { onPick(true) },
        ),
    )
}

/**
 * The iOS search capsule, built by hand because Material3's TextField enforces a 56dp minimum
 * height that clips the field when it is forced shorter — which is exactly what the old field did
 * at 44dp, cutting the placeholder and the typed text off at top and bottom.
 *
 * Fixed 40dp tall (the iOS search-field height), gray fill, magnifier, clear button and accent
 * cursor; the Cancel button that appears beside it lives in the caller, so the capsule only has
 * to be a field.
 */
@Composable
private fun SearchCapsule(
    modifier: Modifier,
    query: String,
    onQueryChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
) {
    val colors = DsTheme.colors
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(DsShapes.pillFull)
            .background(colors.hoverSolid)
            .padding(horizontal = DsSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            FeatherIcons.Search,
            contentDescription = null,
            tint = colors.labelTertiary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(DsSpacing.small))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { onFocusChange(it.isFocused) },
            textStyle = DsType.base16.copy(color = colors.labelPrimary),
            cursorBrush = SolidColor(colors.accent),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            stringResource(R.string.chatlist_search_hint),
                            style = DsType.base16,
                            color = colors.labelTertiary,
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (query.isNotEmpty()) {
            Spacer(Modifier.width(DsSpacing.xsmall))
            Icon(
                FeatherIcons.X,
                contentDescription = stringResource(R.string.chatlist_search_clear),
                tint = colors.labelTertiary,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = { onQueryChange("") })
                    .padding(8.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Rows
// ---------------------------------------------------------------------------

/**
 * A workspace header that collapses its group and carries the workspace verbs.
 *
 * Rename and remove exist on the wire and had no UI at all; a long-press menu is where a
 * phone user expects to find them.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkspaceHeader(
    workspace: WorkspaceRow,
    collapsed: Boolean,
    sessionCount: Int,
    onToggle: () -> Unit,
    store: SessionStore,
    scope: CoroutineScope,
    onNewSession: () -> Unit,
) {
    val colors = DsTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (collapsed) 0f else 90f,
        animationSpec = DsAnimations.chevron,
        label = "workspaceChevron",
    )
    val label = workspace.title.ifBlank { basename(workspace.path) }
    val haptics = LocalHapticFeedback.current

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .clip(DsShapes.row)
                .combinedClickable(
                    onClick = onToggle,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                    onLongClickLabel = stringResource(R.string.chatlist_workspace_actions),
                )
                .padding(horizontal = DsSpacing.medium, vertical = DsSpacing.xsmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                FeatherIcons.ChevronRight,
                contentDescription = null,
                tint = colors.labelTertiary,
                modifier = Modifier
                    .size(12.dp)
                    .graphicsLayer { rotationZ = rotation }
                    .autoMirrorDirectional(),
            )
            Spacer(Modifier.width(DsSpacing.tiny))
            // Group headers read as iOS section titles: name in 13 semibold, count beside it.
            Text(
                label,
                style = DsType.footnote.copy(fontWeight = FontWeight.SemiBold),
                color = colors.labelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(sessionCount.toString(), style = DsType.footnote, color = colors.labelCaption)
        }
        if (menuOpen) {
            WorkspaceMenu(
                onDismiss = { menuOpen = false },
                onNewSession = {
                    menuOpen = false
                    onNewSession()
                },
                onRename = {
                    menuOpen = false
                    renaming = true
                },
                onDelete = {
                    menuOpen = false
                    deleting = true
                },
            )
        }
    }

    if (renaming) {
        RenameDialog(
            initial = workspace.title,
            title = stringResource(R.string.chatlist_workspace_rename),
            onDismiss = { renaming = false },
            onConfirm = {
                scope.launch { store.renameWorkspace(workspace.workspaceId, it) }
                renaming = false
            },
        )
    }
    if (deleting) {
        ConfirmDialog(
            title = stringResource(R.string.chatlist_workspace_delete),
            body = stringResource(R.string.chatlist_workspace_delete_confirm),
            confirmLabel = stringResource(R.string.common_remove),
            onDismiss = { deleting = false },
            onConfirm = {
                scope.launch { store.deleteWorkspace(workspace.workspaceId) }
                deleting = false
            },
        )
    }
}

@Composable
private fun WorkspaceMenu(
    onDismiss: () -> Unit,
    onNewSession: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    DsDialog(title = null, onDismiss = onDismiss) {
        SheetRow(title = stringResource(R.string.chatlist_workspace_new_session), onClick = onNewSession)
        SheetRow(title = stringResource(R.string.chatlist_workspace_rename), onClick = onRename)
        SheetRow(title = stringResource(R.string.chatlist_workspace_delete), onClick = onDelete)
    }
}

/**
 * 40dp hit area around a 16dp glyph — the same affordance ChatNodeItem uses for message actions,
 * so a row's overflow and a bubble's overflow share a size and a feel.
 */
@Composable
private fun ActionIcon(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = DsTheme.colors.labelTertiary,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * One session row: status, title, relative time, and the session verbs on long-press.
 *
 * [depth] indents the row under whatever spawned it, and a row with [childCount] subagents grows a
 * disclosure chevron that opens them in place. Subagents used to be dumped into one flat
 * "Subagents" heading per workspace, which said nothing about which run produced which — with a
 * dozen of them from three sessions it was a wall of near-identical rows.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SessionRowItem(
    session: SessionRow,
    isCurrent: Boolean,
    store: SessionStore,
    scope: CoroutineScope,
    onClose: () -> Unit,
    depth: Int = 0,
    childCount: Int = 0,
    childrenExpanded: Boolean = false,
    onToggleChildren: () -> Unit = {},
) {
    val colors = DsTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var archiveConfirmOpen by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (childrenExpanded) 90f else 0f,
        animationSpec = DsAnimations.chevron,
        label = "sessionChevron",
    )
    val haptics = LocalHapticFeedback.current

    // iOS swipe-to-archive: a trailing swipe reveals the red Archive action. Archiving is
    // irreversible in this UI (there is no restore path), so the swipe hands over to the same
    // confirmation the context menu uses, and the row always snaps back here — it only leaves
    // the list when the harness confirms the archive.
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                archiveConfirmOpen = true
            }
            false
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.35f },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(DsShapes.row)
                    .background(colors.errorFill),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DsSpacing.tiny),
                    modifier = Modifier.padding(end = DsSpacing.large),
                ) {
                    Icon(
                        FeatherIcons.Archive,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        stringResource(R.string.common_archive),
                        style = DsType.small13Strong,
                        color = Color.White,
                    )
                }
            }
        },
    ) {
        // The content must be opaque: SwipeToDismissBox composes its red archive background
        // *behind* the row at all times, and a transparent row lets it show through at rest —
        // until this fill existed, every row on screen looked red. The red only appears now
        // while a row is actually being swiped.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bgBase),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(start = (depth * 16).dp)
                    .background(if (isCurrent) colors.selection else Color.Transparent)
                    .combinedClickable(
                        onClick = {
                            scope.launch {
                                store.openSession(session.sessionId)
                                onClose()
                            }
                        },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuOpen = true
                        },
                        onLongClickLabel = stringResource(R.string.chatlist_session_actions),
                    )
                    .padding(horizontal = DsSpacing.medium, vertical = DsSpacing.xsmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            // The current session carries iOS's plain-list selection — a neutral gray fill,
            // nothing else. No rail, no tint: the gray says "selected" without competing with
            // the status dot.
            // The chevron is its own tap target: opening a session and looking at what it spawned
            // are different intentions, and conflating them means you cannot do one without the
            // other. Both branches occupy the same 36dp slot (32dp target + 4dp gap) so titles
            // stay aligned down a column of mixed rows.
            if (childCount > 0) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.chatlist_subagents),
                            onClick = onToggleChildren,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        FeatherIcons.ChevronRight,
                        contentDescription = null,
                        tint = colors.labelTertiary,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer { rotationZ = chevronRotation }
                            .autoMirrorDirectional(),
                    )
                }
            } else {
                Spacer(Modifier.width(32.dp))
            }
            Spacer(Modifier.width(DsSpacing.tiny))
            StateDot(
                state = when {
                    session.running -> StateDotState.Running
                    session.pendingInteraction != null -> StateDotState.Warning
                    else -> StateDotState.Idle
                },
                contentDescription = stringResource(
                    when {
                        session.running -> R.string.status_running
                        session.pendingInteraction != null -> R.string.chatlist_needs_action
                        else -> R.string.status_idle
                    },
                ),
            )
            Spacer(Modifier.width(DsSpacing.small))
            Column(Modifier.weight(1f)) {
                Text(
                    text = sessionTitle(session),
                    style = DsType.rowTitle,
                    color = colors.labelPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    session.cwd?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            basename(it),
                            style = DsType.footnote,
                            color = colors.labelCaption,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(" · ", style = DsType.footnote, color = colors.labelCaption)
                    }
                    Text(
                        relativeTime(session.updatedAt),
                        style = DsType.footnote,
                        color = colors.labelCaption,
                    )
                }
            }
            // No "Needs you" pill: the row's state dot already turns amber for a pending
            // interaction — one signal, not two.
            // The count replaces the old "Subagents" pill on parents: with the children indented
            // underneath, what is worth saying is how many are down there when the row is closed.
            if (childCount > 0) {
                Spacer(Modifier.width(DsSpacing.xsmall))
                DsPill(text = childCount.toString())
            } else if (session.origin == "subagent" && depth == 0) {
                // Only reached by an orphan — its whole ancestry is archived or blank — where the
                // indent cannot say what the row is.
                Spacer(Modifier.width(DsSpacing.xsmall))
                DsPill(text = stringResource(R.string.chatlist_subagents))
            }
            // The current session's overflow is always visible: long-press is the affordance
            // everywhere else, and the one row people act on most should not hide its verbs.
                if (isCurrent) {
                    Spacer(Modifier.width(DsSpacing.xsmall))
                    ActionIcon(
                        icon = FeatherIcons.MoreHorizontal,
                        label = stringResource(R.string.chatlist_session_actions),
                        onClick = { menuOpen = true },
                    )
                }
            }
            // iOS plain-list separator, aligned to the title's leading edge (12dp row inset +
            // 32dp chevron slot + 4dp + 8dp dot + 8dp gap) so it never runs under the icons.
            HorizontalDivider(
                thickness = 1.dp,
                color = colors.borderL1,
                modifier = Modifier.padding(start = (64 + depth * 16).dp),
            )
            }
        }

        if (menuOpen) {
            DsDialog(title = null, onDismiss = { menuOpen = false }) {
                SheetRow(title = stringResource(R.string.chatlist_session_rename)) {
                    menuOpen = false
                    renameOpen = true
                }
                SheetRow(title = stringResource(R.string.chatlist_session_fork)) {
                    menuOpen = false
                    scope.launch { store.forkSession(session.sessionId) }
                }
                SheetRow(title = stringResource(R.string.chatlist_session_archive)) {
                    menuOpen = false
                    archiveConfirmOpen = true
                }
            }
        }

        if (renameOpen) {
        RenameDialog(
            initial = session.title.orEmpty(),
            title = stringResource(R.string.chatlist_session_rename),
            onDismiss = { renameOpen = false },
            onConfirm = {
                scope.launch { store.renameSession(session.sessionId, it) }
                renameOpen = false
            },
        )
    }

    if (archiveConfirmOpen) {
        ConfirmDialog(
            title = stringResource(R.string.chatlist_session_archive),
            body = sessionTitle(session),
            confirmLabel = stringResource(R.string.common_archive),
            onDismiss = { archiveConfirmOpen = false },
            onConfirm = {
                scope.launch { store.archiveSession(session.sessionId) }
                archiveConfirmOpen = false
            },
        )
    }
}

/**
 * One search result: the session's own name first, then where it lives, then the matching excerpt
 * if the host had one.
 *
 * The row used to lead with the excerpt and label itself with a raw session id, which is neither
 * something anyone searched for nor something they can recognise. A result should name the thing
 * you are about to open.
 */
@Composable
private fun SearchResultRow(
    hit: SearchHit,
    store: SessionStore,
    scope: CoroutineScope,
    onClose: () -> Unit,
) {
    val colors = DsTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                scope.launch {
                    store.openSession(hit.session.sessionId)
                    onClose()
                }
            }
            .padding(horizontal = DsSpacing.medium, vertical = DsSpacing.small),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = sessionTitle(hit.session),
                style = DsType.rowTitle,
                color = colors.labelPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (hit.session.origin == "subagent") {
                Spacer(Modifier.width(DsSpacing.xsmall))
                DsPill(text = stringResource(R.string.chatlist_subagents))
            }
        }
        hit.workspaceLabel.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = DsType.footnote,
                color = colors.labelCaption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        hit.snippet?.let {
            Text(
                text = it,
                style = DsType.footnote,
                color = colors.labelSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------

@Composable
private fun NewSessionDialog(
    workspaces: List<WorkspaceRow>,
    homeCwd: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = DsTheme.colors
    DsDialog(title = stringResource(R.string.chatlist_new_session_in), onDismiss = onDismiss) {
        if (workspaces.isEmpty()) {
            Text(
                stringResource(R.string.chatlist_no_workspaces),
                style = DsType.std14,
                color = colors.labelSecondary,
            )
        }
        workspaces.forEach { workspace ->
            SheetRow(
                title = workspace.title.ifBlank { basename(workspace.path) },
                subtitle = workspace.path,
                onClick = { onPick(workspace.workspaceId) },
            )
        }
        SheetRow(
            title = stringResource(R.string.chatlist_home_directory),
            subtitle = homeCwd,
            onClick = { onPick(null) },
        )
    }
}

@Composable
private fun NewWorkspaceDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var pathText by remember { mutableStateOf("") }
    DsDialog(title = stringResource(R.string.chatlist_new_workspace), onDismiss = onDismiss) {
        TextField(
            value = pathText,
            onValueChange = { pathText = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.chatlist_workspace_path), style = DsType.std14) },
            singleLine = true,
            colors = dialogTextFieldColors(),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            DsButton(
                text = stringResource(R.string.common_cancel),
                onClick = onDismiss,
                variant = DsButtonVariant.Ghost,
            )
            Spacer(Modifier.width(DsSpacing.small))
            DsButton(
                text = stringResource(R.string.common_save),
                onClick = { onCreate(pathText.trim()) },
                variant = DsButtonVariant.Info,
                enabled = pathText.isNotBlank(),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Walk a (possibly nested) subagent session's parent chain up to the session directly registered in
 * a workspace, returning that workspace id — or null for an orphan.
 */
/**
 * Group subagent sessions under the visible session that spawned them.
 *
 * Keyed by immediate parent, so a subagent that spawned its own subagents nests as deeply as the
 * run actually went. Free function so the rules that are easy to get wrong — forks staying at the
 * top level, orphans re-attaching, cycles not hanging — can be tested without a device.
 */
internal fun indexSubagents(
    listable: List<SessionRow>,
    sessionsById: Map<String, SessionRow>,
): Map<String, List<SessionRow>> {
    val listableIds = listable.mapTo(HashSet()) { it.sessionId }

    /**
     * The nearest ancestor that is actually on screen.
     *
     * Usually the immediate parent. The walk exists for the case that used to lose rows entirely:
     * archiving a session, or the harness reusing a blank one, removes it from the list while its
     * subagents remain — those attach to the next ancestor up rather than vanishing with it. The
     * visited set guards against a lineage cycle, which would otherwise hang the walk.
     */
    fun attachPoint(child: SessionRow): String? {
        // Seeded with the child so a lineage cycle cannot walk back around and make the row its own
        // parent — which would nest it inside itself and render nothing at all.
        val visited = hashSetOf(child.sessionId)
        var cursor = child.parentSessionId
        while (cursor != null && visited.add(cursor)) {
            if (cursor in listableIds) return cursor
            cursor = sessionsById[cursor]?.parentSessionId
        }
        return null
    }

    return listable
        .filter { it.origin == "subagent" }
        .mapNotNull { child -> attachPoint(child)?.let { it to child } }
        .groupBy({ it.first }, { it.second })
}

/** Display title: an explicit title, else the working directory's folder, else the id. */
internal fun sessionTitle(session: SessionRow): String {
    val title = session.title?.takeIf { it.isNotBlank() }
    val folder = session.cwd?.takeIf { it.isNotBlank() }?.let { basename(it) }?.takeIf { it.isNotBlank() }
    return title ?: folder ?: session.sessionId
}
