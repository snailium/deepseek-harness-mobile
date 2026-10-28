package com.labteto.dshmobile.ui.screens.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Extension

import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labteto.dshmobile.R
import com.labteto.dshmobile.data.SessionRow
import com.labteto.dshmobile.data.SessionStore
import com.labteto.dshmobile.core.wire.RpcResult
import com.labteto.dshmobile.core.wire.dto.DirectoryListing
import com.labteto.dshmobile.data.WorkspaceRow
import com.labteto.dshmobile.ui.components.DisclosureRow
import com.labteto.dshmobile.ui.components.DsButton
import com.labteto.dshmobile.ui.components.DsButtonVariant
import com.labteto.dshmobile.ui.components.DsDialog
import com.labteto.dshmobile.ui.components.DsIconButton
import com.labteto.dshmobile.ui.components.DsPill
import com.labteto.dshmobile.ui.components.DsMenu
import com.labteto.dshmobile.ui.components.EmptyHero
import com.labteto.dshmobile.ui.components.MenuItem
import com.labteto.dshmobile.ui.components.SearchField
import com.labteto.dshmobile.ui.components.SearchFieldCloseButton
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
import com.labteto.dshmobile.ui.theme.DsTitleBar
import com.labteto.dshmobile.ui.theme.DsType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.labteto.dshmobile.ui.components.FeatherIcons

/** [com.labteto.dshmobile.connection.HostsStore.sessionSort]: the workspace's own row order. */
private const val SORT_MANUAL = "manual"

/** [com.labteto.dshmobile.connection.HostsStore.sessionSort]: most recently updated first. */
private const val SORT_UPDATED = "updated"

/**
 * The chat history: workspaces, their sessions, and search.
 *
 * Two rules keep it readable. Blank sessions are hidden — the harness treats a session with no turn
 * as scratch space and reuses it, so listing them just accumulates empty rows. And times are
 * relative, because a clock time cannot distinguish "an hour ago" from "last Tuesday".
 */
/**
 * The `origin` value the harness uses for a subagent transcript.
 *
 * A shared constant because this list has exactly one rule about subagents — they are not rows — and
 * the rule is spelled in one place. `parentSessionId` is deliberately not the test: an ordinary fork
 * sets a parent too, and a fork is a session in its own right.
 */
internal const val SUBAGENT_ORIGIN = "subagent"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListDrawer(
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlugins: () -> Unit,
    onOpenAutomations: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = DsTheme.colors
    val store = rememberSessionStore()
    val automationAvailable by store.automationAvailable.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val hostsStore = rememberHostsStore()

    val sessions by store.sessions.collectAsStateWithLifecycle()
    val workspaces by store.workspaces.collectAsStateWithLifecycle()
    val archivedIds by store.archivedSessionIds.collectAsStateWithLifecycle()
    val searchResults by store.searchResults.collectAsStateWithLifecycle()
    val contentSearchAvailable by store.contentSearchAvailable.collectAsStateWithLifecycle()
    val currentSessionId by store.currentSessionId.collectAsStateWithLifecycle()
    val refreshing by store.refreshingSessions.collectAsStateWithLifecycle()
    // Null against a harness without pinning (before 0.1.7), which hides the section and the menu row.
    val pinnedIds by store.pinnedSessionIds.collectAsStateWithLifecycle()

    // Pull-to-refresh: the session list is a snapshot of the harness's state, and a session created
    // elsewhere (another client, the web UI) only appears once we ask again.
    val pullState = rememberPullToRefreshState()

    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    // The on-device diagnostics dialog is disabled and lives in ChatListDebug.kt; its
    // `showDebugInfo` state went with it.
    // Persisted, not remembered: the order you read your sessions in is a preference, and it used
    // to reset every time the drawer was closed.
    val sessionSort by hostsStore.sessionSort.collectAsStateWithLifecycle(initialValue = SORT_MANUAL)
    val sortByRecency = sessionSort == SORT_UPDATED
    val showArchived by hostsStore.showArchivedSessions.collectAsStateWithLifecycle(initialValue = false)
    var newWorkspaceOpen by remember { mutableStateOf(false) }
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }
    var ungroupedExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        delay(250)
        store.search(query.trim())
    }

    // Local matching is not debounced: it is a string comparison over a list already in memory, and
    // making someone wait a quarter second for it is what made search feel like it did nothing.
    val searchHits = remember(sessions, workspaces, query, searchResults) {
        deriveSearchResults(
            sessions = sessions,
            workspaces = workspaces,
            query = query,
            contentHits = searchResults,
        )
    }

    // Blank sessions are scratch space the harness reuses, and a subagent transcript is not a
    // conversation anyone started — it is what one of them spawned. Neither belongs in a list of
    // sessions, and the drawer draws no subagent marker of any kind: no nested rows, no disclosure
    // chevron, no count. A subagent is entered from the composer of the session that owns it, and
    // that is the only door. The `origin` discriminator is what says "subagent"; `parentSessionId`
    // alone does not, because an ordinary fork sets a parent too and a fork *is* a session in its
    // own right. Archived sessions are listed with their workspace, dimmed — a separate section
    // would hide them, and the one door into an archived session is its own row.
    // Archived sessions are listed with their workspace, dimmed — when the toggle says so. Off by
    // default: a list of live conversations should not carry its dead weight on first open.
    val listable = remember(sessions, showArchived) {
        sessions.filter { !it.blank && it.origin != SUBAGENT_ORIGIN && (showArchived || it.sessionId !in archivedIds) }
    }
    val workspaceSessionIds = remember(workspaces) { workspaces.flatMap { it.sessionIds }.toSet() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.sidebar)
            .safeDrawingPadding()
            .padding(horizontal = DsSpacing.medium),
    ) {
        // The title row is the drawer's title bar: the same shape as the chat page's — a 44dp
        // floor, the title in base16Strong, icon controls at 18dp/24dp. Search and settings live
        // here rather than on a second row, because they configure the list, not the sessions it
        // holds; the sort chip is the one control that belongs to the list itself and stays with it.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = DsTitleBar.height)
                .padding(top = DsSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DsTitleBar.iconGap),
        ) {
            Text(
                text = stringResource(R.string.chatlist_title),
                style = DsTitleBar.titleStyle,
                color = colors.labelPrimary,
                modifier = Modifier.weight(1f),
            )
            // Closing the field clears the query too: a hidden field holding text left the list
            // filtered by something no longer on screen.
            DsIconButton(
                icon = Icons.Filled.Search,
                contentDescription = stringResource(R.string.common_search),
                onClick = {
                    searchOpen = !searchOpen
                    if (!searchOpen) query = ""
                },
                tint = if (searchOpen) colors.accent else colors.labelTertiary,
                iconSize = DsTitleBar.iconSize,
                touchTarget = DsTitleBar.iconTouchTarget,
            )
            // Same verb as the drawer's old bottom button, promoted into the bar: adding a
            // workspace is a list-level action, so it sits with search and plugins rather than
            // in a row that no one scrolled to. The glyph mirrors dsh-web's "Add workspace…"
            // entry — an outlined circle holding a plus.
            DsIconButton(
                icon = FeatherIcons.FolderPlus,
                contentDescription = stringResource(R.string.chatlist_add_workspace),
                onClick = { newWorkspaceOpen = true },
                tint = colors.labelTertiary,
                iconSize = DsTitleBar.iconSize,
                touchTarget = DsTitleBar.iconTouchTarget,
            )
            // Between search and settings, matching where the harness puts its own plugin entry:
            // it is a thing you reach while working, not while configuring the app.
            DsIconButton(
                icon = Icons.Filled.Extension,
                contentDescription = stringResource(R.string.settings_plugins),
                onClick = onOpenPlugins,
                tint = colors.labelTertiary,
                iconSize = DsTitleBar.iconSize,
                touchTarget = DsTitleBar.iconTouchTarget,
            )
            DsIconButton(
                icon = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.settings_title),
                onClick = onOpenSettings,
                tint = colors.labelTertiary,
                iconSize = DsTitleBar.iconSize,
                touchTarget = DsTitleBar.iconTouchTarget,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = DsSpacing.xsmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The archive toggle sits with the sort chip: both configure what the list shows, and
            // the glyph is the same one a row's menu uses for the verb. Tinted when on, like the
            // search icon in the title bar above. A spacer between them pushes the two to opposite
            // edges — the archive toggle left-aligned with the list it acts on, the sort chip where
            // it always was, right.
            DsIconButton(
                icon = FeatherIcons.Archive,
                contentDescription = stringResource(
                    if (showArchived) R.string.chatlist_hide_archived else R.string.chatlist_show_archived,
                ),
                onClick = { scope.launch { hostsStore.setShowArchivedSessions(!showArchived) } },
                tint = if (showArchived) colors.accent else colors.labelTertiary,
                iconSize = DsTitleBar.iconSize,
                touchTarget = DsTitleBar.iconTouchTarget,
            )
            Spacer(Modifier.weight(1f))
            SortChip(sortByRecency) { next ->
                scope.launch { hostsStore.setSessionSort(if (next) SORT_UPDATED else SORT_MANUAL) }
            }
        }

        if (automationAvailable != false) DsButton(
            text = stringResource(R.string.harness_automations), onClick = onOpenAutomations,
            variant = DsButtonVariant.Ghost, modifier = Modifier.fillMaxWidth(),
        )

        // The search field folds away rather than permanently occupying a row of a phone-height
        // drawer, which is otherwise pure overhead for the common case. Same pill as every other
        // search field in the app, through SearchField — this used to be a hand-copied version of
        // that recipe, which is exactly how it drifted from the transcript's.
        AnimatedVisibility(visible = searchOpen) {
            Column(modifier = Modifier.padding(top = DsSpacing.small)) {
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = stringResource(R.string.chatlist_search_hint),
                    modifier = Modifier.fillMaxWidth(),
                    trailing = {
                        SearchFieldCloseButton(
                            onClick = {
                                searchOpen = false
                                query = ""
                            },
                        )
                    },
                )
                // Stated once, quietly, and only while searching. Most harnesses ship with the
                // content index off, so this is a normal capability note — not a failure.
                if (!contentSearchAvailable && query.isNotBlank()) {
                    Text(
                        stringResource(R.string.chatlist_search_content_off),
                        style = DsType.xsmall12,
                        color = colors.labelCaption,
                        modifier = Modifier.padding(top = DsSpacing.tiny),
                    )
                }
            }
        }

        Spacer(Modifier.height(DsSpacing.small))

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { store.refreshSessions() } },
            state = pullState,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (query.isNotBlank()) {
                    item(key = "search-header") {
                        Text(
                            stringResource(R.string.chatlist_search_results),
                            style = DsType.base16Strong,
                            color = colors.labelSecondary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (searchHits.items.isEmpty()) {
                        item(key = "search-empty") {
                            Text(
                                stringResource(R.string.chatlist_search_empty),
                                style = DsType.base16,
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
                                style = DsType.xsmall12,
                                color = colors.labelCaption,
                                modifier = Modifier.padding(vertical = DsSpacing.xsmall),
                            )
                        }
                    }
                    return@LazyColumn
                }

                var anyShown = false
                // Pinned sessions lead, in pin order, as a shortcut: each stays in its own group too.
                val pinnedRows = pinnedIds.orEmpty().mapNotNull { id -> listable.firstOrNull { it.sessionId == id } }
                if (pinnedRows.isNotEmpty()) {
                    anyShown = true
                    item(key = "pinned-header") { SectionHeader(stringResource(R.string.chatlist_pinned)) }
                    items(pinnedRows, key = { "pinned-${it.sessionId}" }) { session ->
                        Box(Modifier.animateItem()) {
                            SessionRowItem(
                                session = session,
                                isCurrent = session.sessionId == currentSessionId,
                                store = store,
                                scope = scope,
                                onClose = onClose,
                                pinned = true,
                            )
                        }
                    }
                }
                for (workspace in workspaces) {
                    val roots = workspace.sessionIds
                        .mapNotNull { id -> listable.firstOrNull { it.sessionId == id } }
                        .let { if (sortByRecency) it.sortedByDescending(SessionRow::updatedAt) else it }
                    // An empty workspace still gets its header: it is where the first session is
                    // created (the "+" in that header), so hiding workspaces with no sessions would
                    // hide the only door into them. A freshly created workspace is exactly such a
                    // one — invisible, and therefore unreachable.
                    anyShown = true
                    // Only the workspace you are working in is open by default: with twenty sessions
                    // in one group, expanding everything buries the list you came for. The explicit
                    // map entry then remembers whatever you choose.
                    val holdsCurrent = roots.any { it.sessionId == currentSessionId }
                    val isCollapsed = collapsed[workspace.workspaceId] ?: !holdsCurrent
                    item(key = "ws-${workspace.workspaceId}") {
                        WorkspaceHeader(
                            workspace = workspace,
                            collapsed = isCollapsed,
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
                        items(roots, key = { it.sessionId }) { session ->
                            Box(Modifier.animateItem()) {
                                SessionRowItem(
                                    session = session,
                                    isCurrent = session.sessionId == currentSessionId,
                                    archived = session.sessionId in archivedIds,
                                    store = store,
                                    scope = scope,
                                    onClose = onClose,
                                    pinned = pinnedIds?.let { session.sessionId in it },
                                )
                            }
                        }
                    }
                }

                // Sessions the harness never registered in a workspace.
                val ungrouped = listable.filter { it.sessionId !in workspaceSessionIds }
                if (ungrouped.isNotEmpty()) {
                    anyShown = true
                    item(key = "ungrouped-header") {
                        GroupHeader(
                            label = stringResource(R.string.chatlist_sessions),
                            count = ungrouped.size,
                            collapsed = !ungroupedExpanded,
                            onToggle = { ungroupedExpanded = !ungroupedExpanded },
                        )
                    }
                    if (ungroupedExpanded) {
                        val ungroupedRows = ungrouped
                            .let { if (sortByRecency) it.sortedByDescending(SessionRow::updatedAt) else it }
                        items(ungroupedRows, key = { "ug-${it.sessionId}" }) { session ->
                            Box(Modifier.animateItem()) {
                                SessionRowItem(
                                    session = session,
                                    isCurrent = session.sessionId == currentSessionId,
                                    archived = session.sessionId in archivedIds,
                                    store = store,
                                    scope = scope,
                                    onClose = onClose,
                                    pinned = pinnedIds?.let { session.sessionId in it },
                                )
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
        }

        // The old bottom row — "+ Workspace" and "+ Session" — is gone: the workspace verb moved
        // into the title bar (above), and a session is created per workspace through that header's
        // "+" button, so the two buttons had nowhere left to live.
    }

    // ---- Diagnostics: extracted to ChatListDebug.kt and currently disabled. ----
    //
    // The on-device log dialog used to live here. It is self-contained (one button, one dialog,
    // one buffer) and was ~50 lines of debug chrome in the app's busiest composable, so it moved
    // to its own file. That file carries the re-enable instructions; nothing here calls it while
    // CHAT_LIST_DEBUG_ENABLED is false.

    if (newWorkspaceOpen) {
        NewWorkspaceDialog(
            store = store,
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
 * The session-order control.
 *
 * It used to be a bare ⇅ icon whose only label was a content description, which told a sighted user
 * nothing: two arrows over a chat list could as easily mean sync, move, or reorder. Naming the
 * current order and offering the other one is the whole fix — and the strings for both modes were
 * already translated in all eleven locales, waiting for a control to use them.
 */
@Composable
private fun SortChip(byRecency: Boolean, onPick: (byRecency: Boolean) -> Unit) {
    val colors = DsTheme.colors
    val updated = stringResource(R.string.chatlist_sort_updated)
    val manual = stringResource(R.string.chatlist_sort_manual)
    DsMenu(
        anchor = {
            Row(
                modifier = Modifier
                    .clip(DsShapes.cube)
                    .padding(horizontal = DsSpacing.xsmall, vertical = DsSpacing.tiny),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DsSpacing.tiny),
            ) {
                Icon(
                    Icons.Filled.SwapVert,
                    contentDescription = stringResource(R.string.chatlist_sort_title),
                    tint = colors.labelTertiary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    if (byRecency) updated else manual,
                    style = DsType.small13Strong,
                    color = colors.labelSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        items = listOf(
            MenuItem(text = manual) { onPick(false) },
            MenuItem(text = updated) { onPick(true) },
        ),
    )
}

// ---------------------------------------------------------------------------
// Rows
// ---------------------------------------------------------------------------

/**
 * A collapsible group header with the same visual style as [WorkspaceHeader]:
 * chevron + bold label + count. Used for Ungrouped and Archived sections.
 */
@Composable
private fun GroupHeader(
    label: String,
    count: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    val colors = DsTheme.colors
    val rotation by animateFloatAsState(
        targetValue = if (collapsed) 0f else 90f,
        animationSpec = DsAnimations.chevron,
        label = "groupChevron",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.row)
            .clickable(onClick = onToggle)
            .padding(vertical = DsSpacing.xsmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.labelTertiary,
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer { rotationZ = rotation },
        )
        Spacer(Modifier.width(DsSpacing.tiny))
        Text(
            label,
            style = DsType.base16Strong,
            color = colors.labelSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(count.toString(), style = DsType.xsmall12, color = colors.labelCaption)
    }
}

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

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(DsShapes.row)
                .combinedClickable(onClick = onToggle, onLongClick = { menuOpen = true })
                .padding(vertical = DsSpacing.xsmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.labelTertiary,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = rotation },
            )
            Spacer(Modifier.width(DsSpacing.tiny))
            Text(
                label,
                style = DsType.body17,
                color = colors.labelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(DsSpacing.small))
            Text(sessionCount.toString(), style = DsType.xsmall12, color = colors.labelCaption)
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(colors.hoverSolid)
                    .border(1.dp, colors.borderL2, CircleShape)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.chatlist_workspace_new_session)) { onNewSession() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    FeatherIcons.Plus,
                    contentDescription = stringResource(R.string.chatlist_workspace_new_session),
                    tint = colors.labelPrimary,
                    modifier = Modifier.size(12.dp),
                )
            }
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
 * One session row: status, title and time on one line, and the session verbs on long-press.
 *
 * A row is a session and nothing else. It carries no indentation and no subagent marker: this list
 * holds the conversations someone started, and what a session spawned is reached from that session's
 * own composer rather than from the list of its peers. An archived session sits in its workspace,
 * dimmed — the long-press menu offers to bring it back, not to archive it again.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRowItem(
    session: SessionRow,
    isCurrent: Boolean,
    store: SessionStore,
    scope: CoroutineScope,
    onClose: () -> Unit,
    /** Whether the session is pinned; null when the harness has no pinning to offer. */
    pinned: Boolean? = null,
    archived: Boolean = false,
) {
    val colors = DsTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var archiveConfirmOpen by remember { mutableStateOf(false) }
    var unarchiveBusy by remember { mutableStateOf(false) }
    var archiveBusy by remember { mutableStateOf<com.labteto.dshmobile.data.ArchiveOutcome.Busy?>(null) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(DsShapes.row)
                .background(if (isCurrent) colors.sidebarNavActive else androidx.compose.ui.graphics.Color.Transparent)
                .combinedClickable(
                    onClick = {
                        scope.launch {
                            store.openSession(session.sessionId)
                            onClose()
                        }
                    },
                    onLongClick = { menuOpen = true },
                )
                .padding(horizontal = DsSpacing.small, vertical = DsSpacing.xsmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A current-session accent rail reads faster than a background tint alone on a
            // low-contrast sidebar.
            Box(
                Modifier
                    .width(2.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (isCurrent) colors.accent else androidx.compose.ui.graphics.Color.Transparent),
            )
            Spacer(Modifier.width(DsSpacing.small))
            // No disclosure slot. It was a fixed 16dp holding either a subagent chevron or, for a row
            // with no children, nothing at all — a spacer whose only job was to line this row up with
            // rows that had one. With the chevron gone the slot is 16dp of blank in front of every
            // session name, so it goes with it; the titles are aligned by there being one rail.
            //
            // 1.5x the component's default: at 8dp the dot is a speck next to a 16sp title.
            StateDot(
                state = when {
                    session.running -> StateDotState.Running
                    session.pendingInteraction != null -> StateDotState.Warning
                    else -> StateDotState.Idle
                },
                size = 12.dp,
            )
            Spacer(Modifier.width(DsSpacing.small))
            // One line: the name on the left, the time on the right. The workspace is the group's
            // header, and a second line under every row was where the list stopped scanning.
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = displaySessionTitle(session),
                    style = DsType.base16,
                    color = if (archived) colors.labelCaption else colors.labelPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(DsSpacing.small))
                Text(
                    relativeTime(session.updatedAt),
                    style = DsType.xsmall12,
                    color = colors.labelCaption,
                )
            }
            if (session.pendingInteraction != null) {
                Spacer(Modifier.width(DsSpacing.xsmall))
                DsPill(text = stringResource(R.string.chatlist_needs_action), warn = true)
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
                if (pinned != null) {
                    SheetRow(
                        title = stringResource(if (pinned) R.string.chatlist_session_unpin else R.string.chatlist_session_pin),
                    ) {
                        menuOpen = false
                        scope.launch { store.setPinned(session.sessionId, !pinned) }
                    }
                }
                if (archived) {
                    // The session is already archived: the row offers to bring it back, not to
                    // archive it again. No confirmation — unarchiving is reversible and cheap.
                    SheetRow(
                        title = stringResource(if (unarchiveBusy) R.string.common_loading else R.string.chatlist_session_unarchive),
                        enabled = !unarchiveBusy,
                    ) {
                        menuOpen = false
                        unarchiveBusy = true
                        scope.launch {
                            try { store.unarchiveSession(session.sessionId) } finally { unarchiveBusy = false }
                        }
                    }
                } else {
                    SheetRow(title = stringResource(R.string.chatlist_session_archive)) {
                        menuOpen = false
                        archiveConfirmOpen = true
                    }
                }
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
            body = displaySessionTitle(session),
            confirmLabel = stringResource(R.string.common_archive),
            onDismiss = { archiveConfirmOpen = false },
            onConfirm = {
                archiveConfirmOpen = false
                scope.launch {
                    (store.archiveSession(session.sessionId) as? com.labteto.dshmobile.data.ArchiveOutcome.Busy)
                        ?.let { archiveBusy = it }
                }
            },
        )
    }

    archiveBusy?.let { busy ->
        ArchiveBusyDialog(
            busy = busy,
            onDismiss = { archiveBusy = null },
            onConfirm = {
                archiveBusy = null
                scope.launch { store.archiveSession(busy.sessionId, stopActivity = true) }
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
            .clip(DsShapes.row)
            .clickable {
                scope.launch {
                    store.openSession(hit.session.sessionId)
                    onClose()
                }
            }
            .padding(horizontal = DsSpacing.tiny, vertical = DsSpacing.xsmall),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = displaySessionTitle(hit.session),
                style = DsType.base16,
                color = colors.labelPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        hit.workspaceLabel.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = DsType.xsmall12,
                color = colors.labelCaption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        hit.snippet?.let {
            Text(
                text = it,
                style = DsType.xsmall12,
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

/**
 * Add a workspace by browsing the host's directories, the way dsh-web does — not by typing a
 * path. The dialog starts at the account's home directory; every tap descends one level, the
 * breadcrumb row and the up-arrow button walk back up, "New folder" creates a child in the
 * current directory, and "Use this folder" registers whatever is selected.
 *
 * The listing comes from `directoryPicker/list`, which already answers with the ancestor chain
 * (`crumbs`) the web picker renders as its trail, so the phone needs no path arithmetic of its
 * own: a crumb carries its absolute path verbatim. "New folder" goes through
 * `directoryPicker/createDirectory` and re-lists the parent afterwards — same two RPCs the web
 * picker uses.
 */
@Composable
private fun NewWorkspaceDialog(
    store: SessionStore,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    val colors = DsTheme.colors
    val scope = rememberCoroutineScope()
    var listing by remember { mutableStateOf<DirectoryListing?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // "New folder" is a separate overlay on top of the browser: one text field, create, then
    // re-list the parent so the new directory shows up and can be entered.
    var newFolderOpen by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var creatingFolder by remember { mutableStateOf(false) }

    fun browse(path: String? = null) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                val api = store.apiForHost(store.activeHostKey) ?: throw IllegalStateException("offline")
                when (val r = api.hostListDirectory(path)) {
                    is RpcResult.Ok -> { listing = r.value }
                    is RpcResult.Err -> error = r.error.message
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { error = e.message }
            finally { busy = false }
        }
    }

    fun goUp() {
        val l = listing ?: return
        if (l.crumbs.size < 2) return // already at the filesystem root
        browse(l.crumbs[l.crumbs.lastIndex - 1].path)
    }

    fun createFolder() {
        val l = listing ?: return
        val name = newFolderName.trim()
        if (name.isEmpty() || creatingFolder) return
        creatingFolder = true
        error = null
        scope.launch {
            try {
                val api = store.apiForHost(store.activeHostKey) ?: throw IllegalStateException("offline")
                when (val r = api.hostCreateDirectory(l.path, name)) {
                    is RpcResult.Ok -> { newFolderOpen = false; newFolderName = ""; browse(l.path) }
                    is RpcResult.Err -> error = r.error.message
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { error = e.message }
            finally { creatingFolder = false }
        }
    }

    LaunchedEffect(Unit) { browse(null) }

    DsDialog(title = stringResource(R.string.chatlist_add_workspace), onDismiss = onDismiss) {
        listing?.let { l ->
            // Toolbar: the up-arrow walks to the parent (the last ancestor in the host's own
            // crumb chain — no path arithmetic), "New folder" opens the create overlay. Both sit
            // at the right of a row that also carries the breadcrumb trail, like the web picker.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(DsSpacing.tiny),
                ) {
                    l.crumbs.forEachIndexed { i, crumb ->
                        val isCurrent = i == l.crumbs.lastIndex
                        Text(
                            crumb.name,
                            style = if (isCurrent) DsType.small13Strong else DsType.small13,
                            color = if (isCurrent) colors.labelPrimary else colors.labelTertiary,
                            modifier = Modifier.clickable(enabled = !isCurrent && !busy) { browse(crumb.path) },
                        )
                        if (!isCurrent) Text("›", style = DsType.small13, color = colors.labelCaption)
                    }
                }
                DsIconButton(
                    icon = FeatherIcons.ChevronUp,
                    contentDescription = stringResource(R.string.chatlist_go_up),
                    onClick = { goUp() },
                    enabled = !busy && l.crumbs.size >= 2,
                    tint = colors.labelSecondary,
                    iconSize = 18.dp,
                    touchTarget = DsSpacing.touchTarget,
                )
                DsIconButton(
                    icon = FeatherIcons.FolderPlus,
                    contentDescription = stringResource(R.string.chatlist_new_folder),
                    onClick = { newFolderName = ""; newFolderOpen = true },
                    enabled = !busy,
                    tint = colors.labelSecondary,
                    iconSize = 18.dp,
                    touchTarget = DsSpacing.touchTarget,
                )
            }
        }
        if (busy && listing == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(DsSpacing.xsmall))
                Text(stringResource(R.string.common_loading), style = DsType.small13, color = colors.labelTertiary)
            }
        } else {
            error?.let {
                Text(it, style = DsType.small13, color = colors.error)
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
            ) {
                items(listing?.entries.orEmpty(), key = { it.path }) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DsShapes.row)
                            .clickable(enabled = !busy && !entry.hidden) { browse(entry.path) }
                            .padding(vertical = DsSpacing.xsmall, horizontal = DsSpacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            FeatherIcons.Folder,
                            contentDescription = null,
                            tint = if (entry.hidden) colors.labelCaption else colors.labelTertiary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(DsSpacing.xsmall))
                        Text(
                            entry.name,
                            style = DsType.base16,
                            color = if (entry.hidden) colors.labelCaption else colors.labelPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        if (newFolderOpen) {
            // The create overlay: one field for the name, Cancel / Create. Creating re-lists the
            // current directory so the new folder is immediately visible and enterable.
            TextField(
                value = newFolderName,
                onValueChange = { newFolderName = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.chatlist_new_folder_name), style = DsType.std14) },
                singleLine = true,
                colors = dialogTextFieldColors(),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                DsButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { newFolderOpen = false },
                    variant = DsButtonVariant.Ghost,
                    enabled = !creatingFolder,
                )
                Spacer(Modifier.width(DsSpacing.small))
                DsButton(
                    text = stringResource(R.string.chatlist_new_folder),
                    onClick = { createFolder() },
                    variant = DsButtonVariant.Info,
                    enabled = newFolderName.isNotBlank() && !creatingFolder,
                )
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                DsButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = onDismiss,
                    variant = DsButtonVariant.Ghost,
                )
                Spacer(Modifier.width(DsSpacing.small))
                DsButton(
                    text = stringResource(R.string.chatlist_use_folder),
                    onClick = { onCreate(listing?.path.orEmpty()) },
                    variant = DsButtonVariant.Info,
                    enabled = listing != null && !busy,
                )
            }
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

/** Display title: an explicit title, else the working directory's folder, else the id. */
internal fun displaySessionTitle(session: SessionRow): String =
    sessionTitle(session).let { if (session.title.isNullOrBlank()) technicalDisplay(it) else it }

internal fun sessionTitle(session: SessionRow): String {
    val title = session.title?.takeIf { it.isNotBlank() }
    val folder = session.cwd?.takeIf { it.isNotBlank() }?.let { basename(it) }?.takeIf { it.isNotBlank() }
    return title ?: folder ?: session.sessionId
}
