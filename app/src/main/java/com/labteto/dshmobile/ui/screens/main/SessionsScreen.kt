package com.labteto.dshmobile.ui.screens.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labteto.dshmobile.R
import com.labteto.dshmobile.connection.ConnectionPhase
import com.labteto.dshmobile.connection.ConnectionUiState
import com.labteto.dshmobile.core.session.AssistantMessageNode
import com.labteto.dshmobile.core.session.UserMessageNode
import com.labteto.dshmobile.data.SessionRow
import com.labteto.dshmobile.data.SessionStore
import com.labteto.dshmobile.data.WorkspaceRow
import com.labteto.dshmobile.ui.components.DisclosureRow
import com.labteto.dshmobile.ui.components.DsBottomSheet
import com.labteto.dshmobile.ui.components.DsButton
import com.labteto.dshmobile.ui.components.DsButtonVariant
import com.labteto.dshmobile.ui.components.DsDialog
import com.labteto.dshmobile.ui.components.DsPill
import com.labteto.dshmobile.ui.components.DsMenu
import com.labteto.dshmobile.ui.components.DsTopAppBar
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

/** [com.labteto.dshmobile.connection.HostsStore.sessionSort]: registration order. */
private const val SORT_MANUAL = "manual"

/** [com.labteto.dshmobile.connection.HostsStore.sessionSort]: most recently updated first. */
private const val SORT_UPDATED = "updated"

/**
 * The Sessions screen: the full-screen chat history, pushed over the chat the way Messages pushes
 * its conversation list.
 *
 * The list is a single flat sequence of sessions — no workspace grouping. Grouping by workspace
 * buried sessions three levels deep and made the list read like a file browser; the harness
 * registers most sessions in one or two workspaces anyway, so the grouping mostly cost rows of
 * headers. A session's working directory still rides its meta line, so "where does this live" is
 * answered per-row rather than by section. Subagent transcripts still nest under the session that
 * spawned them: that is lineage, not grouping, and it is what makes a run's output readable.
 *
 * Workspace verbs (create / rename / delete) live behind the folder button in the app bar — the
 * features are all still here, just not as list furniture.
 *
 * Two rules keep it readable. Blank sessions are hidden — the harness treats a session with no turn
 * as scratch space and reuses it, so listing them just accumulates empty rows. And times are
 * relative, because a clock time cannot distinguish "an hour ago" from "last Tuesday".
 *
 * The chrome is Material 3: a top app bar with the title, connection state, search toggle and
 * sort on the actions side, an expanding M3 search field, a hairline-separated list with
 * swipe-to-archive and subagent nesting, and an extended FAB that starts a session.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    hostLabel: String?,
    connectionState: ConnectionUiState? = null,
    onSwitchHost: () -> Unit,
    onOpenSession: (String) -> Unit,
) {
    val colors = DsTheme.colors
    val store = rememberSessionStore()
    val scope = rememberCoroutineScope()
    val hostsStore = rememberHostsStore()

    val sessions by store.sessions.collectAsStateWithLifecycle()
    val workspaces by store.workspaces.collectAsStateWithLifecycle()
    val archivedIds by store.archivedSessionIds.collectAsStateWithLifecycle()
    val searchResults by store.searchResults.collectAsStateWithLifecycle()
    val contentSearchAvailable by store.contentSearchAvailable.collectAsStateWithLifecycle()
    val currentSessionId by store.currentSessionId.collectAsStateWithLifecycle()
    val hostInfo by store.hostInfo.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    // Persisted, not remembered: the order you read your sessions in is a preference, and it used
    // to reset every time the screen was left. Recency is the default; "manual" now means the
    // harness's own registration order, since there are no workspace groups to order any more.
    val sessionSort by hostsStore.sessionSort.collectAsStateWithLifecycle(initialValue = SORT_UPDATED)
    val sortByRecency = sessionSort == SORT_UPDATED
    var workspacesOpen by remember { mutableStateOf(false) }
    var newWorkspaceOpen by remember { mutableStateOf(false) }
    // Search is an expanding bar (Android pattern): the magnifier in the app bar toggles it.
    var searchVisible by remember { mutableStateOf(false) }
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

    // ---- Workspace grouping: sessions are grouped under their workspace, collapsible. ----
    val topLevelSessions = remember(listable, nestedIds, sortByRecency) {
        orderTopLevel(listable, nestedIds, sortByRecency)
    }

    // Map each top-level session to its workspace via cwd basename matching.
    data class WsGroup(val workspace: WorkspaceRow?, val sessions: List<SessionRow>)
    val workspaceGroups = remember(topLevelSessions, workspaces) {
        val wsByBasename = workspaces.associateBy { basename(it.path).lowercase() }
        val grouped = mutableMapOf<String, MutableList<SessionRow>>()
        val ungrouped = mutableListOf<SessionRow>()
        for (s in topLevelSessions) {
            val key = s.cwd?.let { basename(it).lowercase() } ?: ""
            if (key.isNotBlank() && wsByBasename.containsKey(key)) {
                grouped.getOrPut(key) { mutableListOf() }.add(s)
            } else {
                ungrouped.add(s)
            }
        }
        val groups = mutableListOf<WsGroup>()
        // Workspaces with sessions, in the harness's registration order.
        for (ws in workspaces) {
            val key = basename(ws.path).lowercase()
            val sess = grouped.remove(key) ?: continue
            groups.add(WsGroup(ws, sess))
        }
        // Any workspace that has no matching sessions still appears (empty group).
        for (ws in workspaces) {
            if (groups.none { it.workspace?.workspaceId == ws.workspaceId }) {
                groups.add(WsGroup(ws, emptyList()))
            }
        }
        if (ungrouped.isNotEmpty()) groups.add(WsGroup(null, ungrouped))
        groups
    }

    // Workspace collapse state: default expanded.
    val wsCollapsed = remember { mutableStateMapOf<String, Boolean>() }
    fun isWsExpanded(wsId: String): Boolean = wsCollapsed[wsId]?.not() ?: true
    fun toggleWs(wsId: String) { wsCollapsed[wsId] = isWsExpanded(wsId) }

    // ---- DEBUG: long-press title to show workspace/session data for diagnosis ----
    var showDebugInfo by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgBase),
    ) {
        // ---- M3 top app bar: title · count · host · workspaces · search · sort ----
        // WindowInsets(0) because the home Scaffold already supplies the status-bar inset to the
        // content; a second status-bar inset here would double the top padding.
        DsTopAppBar(
            title = stringResource(R.string.chatlist_title),
            actions = {
                // Connection state rides the bar as a quiet dot beside the host label.
                if (connectionState != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(DsShapes.pillFull)
                            .clickable(role = Role.Button, onClick = onSwitchHost)
                            .padding(horizontal = DsSpacing.compact, vertical = DsSpacing.tiny),
                    ) {
                        ConnectionDot(connectionState)
                        Spacer(Modifier.width(DsSpacing.tiny))
                        Text(
                            hostLabel ?: stringResource(R.string.settings_connection_host),
                            style = DsType.m3LabelMedium,
                            color = colors.labelSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 132.dp),
                        )
                    }
                }
                // Workspaces: the folder button owns the workspace verbs now that the list no
                // longer groups by them.
                IconButton(
                    onClick = { workspacesOpen = true },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        FeatherIcons.Folder,
                        contentDescription = stringResource(R.string.chatlist_workspaces),
                        tint = colors.labelSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = { searchVisible = !searchVisible },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        FeatherIcons.Search,
                        contentDescription = stringResource(R.string.common_search),
                        tint = colors.labelSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = { showDebugInfo = true },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        FeatherIcons.Info,
                        contentDescription = "Debug",
                        tint = colors.labelSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                SortButton { next ->
                    scope.launch { hostsStore.setSessionSort(if (next) SORT_UPDATED else SORT_MANUAL) }
                }
            },
        )

        // ---- Search: expands below the bar as an M3 search field; Cancel clears and closes ----
        AnimatedVisibility(
            visible = searchVisible,
            enter = fadeIn(DsAnimations.fade) + expandVertically(),
            exit = fadeOut(DsAnimations.fade) + shrinkVertically(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DsSpacing.medium, vertical = DsSpacing.xsmall),
            ) {
                SearchCapsule(
                    modifier = Modifier.fillMaxWidth(),
                    query = query,
                    onQueryChange = { query = it },
                    onFocusChange = {},
                )
                // Stated once, quietly, and only while searching. Most harnesses ship with the
                // content index off, so this is a normal capability note — not a failure.
                if (!contentSearchAvailable && query.isNotBlank()) {
                    Text(
                        stringResource(R.string.chatlist_search_content_off),
                        style = DsType.m3LabelSmall,
                        color = colors.labelCaption,
                        modifier = Modifier.padding(start = DsSpacing.small, top = DsSpacing.tiny),
                    )
                }
            }
        }

        // ---- The list, with the compose action floating over it (Messages pattern) ----
        Box(Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = DsSpacing.xsmall, bottom = DsSpacing.large),
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
                    SearchResultRow(hit, store, scope, onOpenSession)
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

            // ---- Needs your attention: live sessions pinned above the list ----
            val needsAttention = listable
                .filter { it.pendingInteraction != null || it.running }
                .sortedByDescending { it.updatedAt }
            if (needsAttention.isNotEmpty()) {
                item(key = "attention") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DsSpacing.medium, vertical = DsSpacing.xsmall),
                    ) {
                        SectionHeader(
                            stringResource(R.string.chatlist_needs_attention),
                            modifier = Modifier.padding(bottom = DsSpacing.xsmall),
                        )
                        needsAttention.forEach { session ->
                            AttentionRow(
                                session = session,
                                onOpen = {
                                    scope.launch { store.openSession(session.sessionId) }
                                    onOpenSession(session.sessionId)
                                },
                            )
                        }
                    }
                }
            }

            if (workspaceGroups.isEmpty() && archivedSessions.isEmpty()) {
                item(key = "empty") {
                    EmptyHero(
                        headline = stringResource(R.string.chatlist_empty),
                        subtitle = stringResource(R.string.chatlist_empty_hint),
                        chips = listOf(stringResource(R.string.chatlist_new_session)),
                        onChipClick = {
                            scope.launch {
                                store.createSession()
                                store.currentSessionId.value?.let(onOpenSession)
                            }
                        },
                    )
                }
            } else {
                // Sessions grouped under workspaces; each workspace is a collapsible section.
                for (group in workspaceGroups) {
                    if (group.workspace != null) {
                        val ws = group.workspace
                        item(key = "ws_${ws.workspaceId}") {
                            WorkspaceGroupHeader(
                                title = ws.title,
                                path = ws.path,
                                sessionCount = group.sessions.size,
                                expanded = isWsExpanded(ws.workspaceId),
                                onToggle = { toggleWs(ws.workspaceId) },
                                onNewSession = {
                                    scope.launch {
                                        store.createSession(workspaceId = ws.workspaceId)
                                        store.currentSessionId.value?.let(onOpenSession)
                                    }
                                },
                            )
                        }
                        if (isWsExpanded(ws.workspaceId)) {
                            for ((session, depth) in group.sessions.flatMap { subtree(it) }) {
                                item(key = session.sessionId) {
                                    Box(Modifier.animateItem()) {
                                        SessionRowItem(
                                            session = session,
                                            isCurrent = session.sessionId == currentSessionId,
                                            store = store,
                                            scope = scope,
                                            onOpenSession = onOpenSession,
                                            depth = depth + 1,
                                            childCount = childrenByParent[session.sessionId].orEmpty().size,
                                            childrenExpanded = isExpanded(session.sessionId),
                                            onToggleChildren = { toggleChildren(session.sessionId) },
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Ungrouped sessions: shown under a collapsible "Ungrouped" header.
                        item(key = "ws_ungrouped") {
                            WorkspaceGroupHeader(
                                title = stringResource(R.string.chatlist_ungrouped),
                                path = "",
                                sessionCount = group.sessions.size,
                                expanded = isWsExpanded("ungrouped"),
                                onToggle = { toggleWs("ungrouped") },
                                onNewSession = {
                                    scope.launch {
                                        store.createSession()
                                        store.currentSessionId.value?.let(onOpenSession)
                                    }
                                },
                            )
                        }
                        if (isWsExpanded("ungrouped")) {
                            for ((session, depth) in group.sessions.flatMap { subtree(it) }) {
                                item(key = session.sessionId) {
                                    Box(Modifier.animateItem()) {
                                        SessionRowItem(
                                            session = session,
                                            isCurrent = session.sessionId == currentSessionId,
                                            store = store,
                                            scope = scope,
                                            onOpenSession = onOpenSession,
                                            depth = depth + 1,
                                            childCount = childrenByParent[session.sessionId].orEmpty().size,
                                            childrenExpanded = isExpanded(session.sessionId),
                                            onToggleChildren = { toggleChildren(session.sessionId) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (archivedSessions.isNotEmpty()) {
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
                            SessionRowItem(session, false, store, scope, onOpenSession)
                        }
                    }
                }
            }
        }

        // ---- DEBUG: workspace/session data dialog for diagnosis ----
        if (showDebugInfo) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val debugText = buildString {
                appendLine("=== Workspaces (${workspaces.size}) ===")
                workspaces.forEach { ws ->
                    appendLine("  [${ws.workspaceId}] path=${ws.path} title=${ws.title} sessions=${ws.sessionIds}")
                }
                if (workspaces.isEmpty()) appendLine("  (none)")
                appendLine()
                appendLine("=== Sessions (${topLevelSessions.size} top-level) ===")
                topLevelSessions.forEach { s ->
                    appendLine("  [${s.sessionId}] cwd=${s.cwd} title=${s.title}")
                }
                if (topLevelSessions.isEmpty()) appendLine("  (none)")
                appendLine()
                appendLine("=== Groups (${workspaceGroups.size}) ===")
                workspaceGroups.forEach { g ->
                    val label = g.workspace?.let { "${it.title} (${it.path})" } ?: "ungrouped"
                    appendLine("  $label → ${g.sessions.size} sessions: ${g.sessions.map { it.sessionId.take(8) }}")
                }
                appendLine()
                val conv = store.currentConversation.value
                if (conv != null) {
                    appendLine("=== Transcript nodes (${conv.nodes.size}) hasMore=${conv.hasMore} ===")
                    conv.nodes.forEach { node ->
                        when (node) {
                            is UserMessageNode -> {
                                val kind = node.sourceKind ?: "(none)"
                                val role = node.role ?: "(none)"
                                appendLine("  [${node.seq}] user msg: source=$kind role=$role isSystem=${node.isSystem} text=\"${node.previewText.take(60)}\"")
                            }
                            is AssistantMessageNode -> {
                                appendLine("  [${node.seq}] assistant msg (turn ${node.turn})")
                            }
                            else -> appendLine("  [${node.seq}] ${node::class.simpleName}")
                        }
                    }
                } else {
                    appendLine("=== Transcript: no session open ===")
                }
            }
            DsDialog(title = "Debug Info", onDismiss = { showDebugInfo = false }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(max = 400.dp),
                ) {
                    Text(
                        debugText,
                        style = DsType.caption11.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(DsSpacing.compact))
                Row(horizontalArrangement = Arrangement.End) {
                    DsButton(text = "Copy", onClick = {
                        val clip = android.content.ClipData.newPlainText("debug", debugText)
                        (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)?.setPrimaryClip(clip)
                        showDebugInfo = false
                    })
                }
            }
        }

        }
    }

    // ---- Workspaces: the sheet that owns the workspace verbs. ----
    // The list no longer groups by workspace, so the verbs need a home. The sheet lists every
    // workspace with its session count and a "New session" tap; long-pressing a row offers
    // rename / delete. The dialogs those verbs open are hoisted here so they render over the
    // sheet as proper composables.
    var renameWorkspace by remember { mutableStateOf<WorkspaceRow?>(null) }
    var deleteWorkspace by remember { mutableStateOf<WorkspaceRow?>(null) }

    if (workspacesOpen) {
        WorkspacesSheet(
            workspaces = workspaces,
            onDismiss = { workspacesOpen = false },
            onNewWorkspace = {
                workspacesOpen = false
                newWorkspaceOpen = true
            },
            onNewSessionIn = { workspaceId ->
                workspacesOpen = false
                scope.launch {
                    store.createSession(workspaceId = workspaceId)
                    store.currentSessionId.value?.let(onOpenSession)
                }
            },
            onRename = { workspace ->
                workspacesOpen = false
                renameWorkspace = workspace
            },
            onDelete = { workspace ->
                workspacesOpen = false
                deleteWorkspace = workspace
            },
        )
    }

    renameWorkspace?.let { workspace ->
        RenameDialog(
            initial = workspace.title,
            title = stringResource(R.string.chatlist_workspace_rename),
            onDismiss = { renameWorkspace = null },
            onConfirm = {
                scope.launch { store.renameWorkspace(workspace.workspaceId, it) }
                renameWorkspace = null
            },
        )
    }

    deleteWorkspace?.let { workspace ->
        ConfirmDialog(
            title = stringResource(R.string.chatlist_workspace_delete),
            body = stringResource(R.string.chatlist_workspace_delete_confirm),
            confirmLabel = stringResource(R.string.common_remove),
            onDismiss = { deleteWorkspace = null },
            onConfirm = {
                scope.launch { store.deleteWorkspace(workspace.workspaceId) }
                deleteWorkspace = null
            },
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
 * A workspace group header: collapsible triangle on the left, workspace name and path in the
 * middle, session count and a "+" button on the right.
 */
@Composable
private fun WorkspaceGroupHeader(
    title: String,
    path: String,
    sessionCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onNewSession: () -> Unit,
) {
    val colors = DsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.row)
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(horizontal = DsSpacing.medium, vertical = DsSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Collapse triangle.
        Icon(
            if (expanded) FeatherIcons.ChevronDown else FeatherIcons.ChevronRight,
            contentDescription = null,
            tint = colors.labelTertiary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(DsSpacing.xsmall))
        Text(
            title,
            style = DsType.m3TitleMedium,
            color = colors.labelPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // Session count.
        if (sessionCount > 0) {
            Text(
                sessionCount.toString(),
                style = DsType.caption11,
                color = colors.labelCaption,
                modifier = Modifier.padding(horizontal = DsSpacing.xsmall),
            )
        }
        // Filled circle with "+": new session in this workspace.
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(colors.accent)
                .clickable(onClick = onNewSession),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                FeatherIcons.Plus,
                contentDescription = stringResource(R.string.chatlist_workspace_new_session),
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** A small connection-state dot: green when connected, amber reconnecting, gray idle. */
@Composable
private fun ConnectionDot(connectionState: ConnectionUiState) {
    val state = when (connectionState.phase) {
        ConnectionPhase.CONNECTED -> StateDotState.Done
        ConnectionPhase.CONNECTING, ConnectionPhase.RECONNECTING -> StateDotState.Running
        else -> StateDotState.Idle
    }
    StateDot(state, size = 7.dp)
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
 * The compact search field, built by hand because Material3's TextField enforces a 56dp minimum
 * height that clips the field when it is forced shorter — which is exactly what the old field did
 * at 44dp, cutting the placeholder and the typed text off at top and bottom.
 *
 * Fixed 40dp tall, gray fill, magnifier, clear button and accent cursor. It lives under the top
 * app bar in an expanding block; clearing the query collapses it.
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
            textStyle = DsType.m3BodyLarge.copy(color = colors.labelPrimary),
            cursorBrush = SolidColor(colors.accent),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            stringResource(R.string.chatlist_search_hint),
                            style = DsType.m3BodyLarge,
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
 * One session row: a status tile up front, title, meta (folder · time), and the session verbs on
 * the overflow — always visible, not just on the current row.
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
    onOpenSession: (String) -> Unit,
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

    // M3 swipe-to-dismiss: a trailing swipe reveals the red Archive action. Archiving is
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
                    .heightIn(min = 60.dp)
                    .padding(start = (depth * 16).dp)
                    // M3 selected state: a tonal brand-tinted wash.
                    .background(if (isCurrent) colors.selectionTonal else Color.Transparent)
                    .combinedClickable(
                        onClick = {
                            scope.launch { store.openSession(session.sessionId) }
                            onOpenSession(session.sessionId)
                        },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuOpen = true
                        },
                        onLongClickLabel = stringResource(R.string.chatlist_session_actions),
                    )
                    .padding(start = 8.dp, end = 12.dp, top = DsSpacing.xsmall, bottom = DsSpacing.xsmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            // The chevron is its own tap target: opening a session and looking at what it spawned
            // are different intentions. Both branches occupy the same 32dp slot so titles stay
            // aligned down a column of mixed rows.
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
                Spacer(Modifier.width(16.dp))
            }
            Spacer(Modifier.width(DsSpacing.tiny))
            // Leading status tile: a filled circle that reads at a glance — running (brand
            // pulse), needs-you (amber), idle (quiet gray).
            SessionStatusTile(session)
            Spacer(Modifier.width(DsSpacing.small))
            // M3 ListItem anatomy: headline (16 Medium) over a supporting meta line (14).
            Column(Modifier.weight(1f)) {
                Text(
                    text = sessionTitle(session),
                    style = DsType.m3TitleMedium,
                    color = colors.labelPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    session.cwd?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            basename(it),
                            style = DsType.m3BodyMedium,
                            color = colors.labelCaption,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(" · ", style = DsType.m3BodyMedium, color = colors.labelCaption)
                    }
                    Text(
                        relativeTime(session.updatedAt),
                        style = DsType.m3BodyMedium,
                        color = colors.labelCaption,
                    )
                }
            }
            // One quiet trailing badge: the subagent count on parents, the overflow on the
            // current row — never both crowding the same line.
            if (childCount > 0) {
                Spacer(Modifier.width(DsSpacing.xsmall))
                DsPill(text = childCount.toString())
            } else if (session.origin == "subagent" && depth == 0) {
                // Only reached by an orphan — its whole ancestry is archived or blank — where the
                // indent cannot say what the row is.
                Spacer(Modifier.width(DsSpacing.xsmall))
                DsPill(text = stringResource(R.string.chatlist_subagents))
            }
            Spacer(Modifier.width(DsSpacing.xsmall))
            // The overflow is always visible now — the current-row-only rule meant every other
            // row hid its actions behind a long press nobody discovers.
            ActionIcon(
                icon = FeatherIcons.MoreHorizontal,
                label = stringResource(R.string.chatlist_session_actions),
                onClick = { menuOpen = true },
            )
            }
            // Plain hairline separator, aligned to the title's leading edge so it never runs
            // under the leading icons.
            HorizontalDivider(
                thickness = 1.dp,
                color = colors.borderL1,
                modifier = Modifier.padding(start = (76 + depth * 16).dp),
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
 * The leading status tile: a filled circle whose color and glyph say at a glance whether the
 * session is running, waiting on you, or idle.
 *
 * Idle is the quiet default (gray fill, message glyph). Running is the brand pulse — the same
 * state dot the chat uses, sized up into the tile. Needs-you is the amber warn fill with a
 * lightning glyph, so the row's "you owe this session something" reads before the title does.
 */
@Composable
private fun SessionStatusTile(session: SessionRow) {
    val colors = DsTheme.colors
    val running = session.running
    val warning = session.pendingInteraction != null
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(
                when {
                    running -> colors.accentTertiary
                    warning -> colors.warnTertiary
                    else -> colors.bgLayer2
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            running -> {
                Box(contentAlignment = Alignment.Center) {
                    StateDot(StateDotState.Running, size = 20.dp)
                }
            }
            warning -> {
                Icon(
                    FeatherIcons.Zap,
                    contentDescription = stringResource(R.string.chatlist_needs_action),
                    tint = colors.warnLabel,
                    modifier = Modifier.size(16.dp),
                )
            }
            else -> {
                Icon(
                    FeatherIcons.MessageSquare,
                    contentDescription = stringResource(R.string.status_idle),
                    tint = colors.labelTertiary,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
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
    onOpenSession: (String) -> Unit,
) {
    val colors = DsTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                scope.launch { store.openSession(hit.session.sessionId) }
                onOpenSession(hit.session.sessionId)
            }
            .padding(horizontal = DsSpacing.medium, vertical = DsSpacing.small),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = sessionTitle(hit.session),
                style = DsType.m3TitleMedium,
                color = colors.labelPrimary,
                maxLines = 2,
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
                style = DsType.m3BodyMedium,
                color = colors.labelCaption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        hit.snippet?.let {
            Text(
                text = it,
                style = DsType.m3BodyMedium,
                color = colors.labelSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Workspaces sheet
// ---------------------------------------------------------------------------

/**
 * The workspaces sheet: every workspace with its session count, plus the workspace verbs.
 *
 * With grouping gone from the list, this is the single home for "new session in a workspace",
 * "new workspace", rename and delete — everything the old workspace headers offered, reachable
 * from the app bar. A workspace row long-presses to rename or delete, mirroring the old header's
 * menu; the footer row creates a workspace.
 */
@Composable
private fun WorkspacesSheet(
    workspaces: List<WorkspaceRow>,
    onDismiss: () -> Unit,
    onNewWorkspace: () -> Unit,
    onNewSessionIn: (String) -> Unit,
    onRename: (WorkspaceRow) -> Unit,
    onDelete: (WorkspaceRow) -> Unit,
) {
    // Long-pressing a workspace row opens the same three-verb menu the old header had.
    var menuWorkspace by remember { mutableStateOf<WorkspaceRow?>(null) }
    DsBottomSheet(
        title = stringResource(R.string.chatlist_workspaces),
        onDismiss = onDismiss,
    ) {
        if (workspaces.isEmpty()) {
            Text(
                stringResource(R.string.chatlist_no_workspaces),
                style = DsType.std14,
                color = DsTheme.colors.labelSecondary,
            )
        }
        workspaces.forEach { workspace ->
            WorkspaceSheetRow(
                workspace = workspace,
                onClick = { onNewSessionIn(workspace.workspaceId) },
                onLongClick = { menuWorkspace = workspace },
            )
        }
        SheetRow(
            title = stringResource(R.string.chatlist_new_workspace),
            leading = {
                Icon(
                    FeatherIcons.Folder,
                    contentDescription = null,
                    tint = DsTheme.colors.labelSecondary,
                    modifier = Modifier.size(20.dp),
                )
            },
            onClick = onNewWorkspace,
        )
    }

    // The verbs ride a small dialog over the sheet, like the old workspace header's menu.
    menuWorkspace?.let { workspace ->
        DsDialog(title = null, onDismiss = { menuWorkspace = null }) {
            SheetRow(title = stringResource(R.string.chatlist_workspace_new_session)) {
                menuWorkspace = null
                onNewSessionIn(workspace.workspaceId)
            }
            SheetRow(title = stringResource(R.string.chatlist_workspace_rename)) {
                menuWorkspace = null
                onRename(workspace)
            }
            SheetRow(title = stringResource(R.string.chatlist_workspace_delete)) {
                menuWorkspace = null
                onDelete(workspace)
            }
        }
    }
}

/**
 * One workspace in the sheet: title (folder name), path, session count. Tap starts a session
 * there; long-press offers rename / delete.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkspaceSheetRow(
    workspace: WorkspaceRow,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = DsTheme.colors
    val haptics = LocalHapticFeedback.current
    val label = workspace.title.ifBlank { basename(workspace.path) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.row)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
                onLongClickLabel = stringResource(R.string.chatlist_workspace_actions),
            )
            .padding(vertical = DsSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            FeatherIcons.Folder,
            contentDescription = null,
            tint = colors.labelSecondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(DsSpacing.medium))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = DsType.std14Strong,
                color = colors.labelPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (workspace.path.isNotBlank()) {
                Text(
                    workspace.path,
                    style = DsType.caption11,
                    color = colors.labelTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(DsSpacing.small))
        Text(
            workspace.sessionIds.size.toString(),
            style = DsType.caption11,
            color = colors.labelCaption,
        )
    }
}

// ---------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------

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

/**
 * The flat list order: top-level sessions (not nested under a subagent tree), in either
 * recency order (default) or the harness's registration order.
 *
 * Free function so the ordering rules are testable without a device.
 */
internal fun orderTopLevel(
    listable: List<SessionRow>,
    nestedIds: Set<String>,
    byRecency: Boolean,
): List<SessionRow> {
    val roots = listable.filterNot { it.sessionId in nestedIds }
    return if (byRecency) roots.sortedByDescending(SessionRow::updatedAt) else roots
}

// ---------------------------------------------------------------------------
// Host status strip + needs-attention rows (homepage redesign)
// ---------------------------------------------------------------------------

/**
 * One live/needs-action session in the pinned strip: title, state dot, relative time.
 * Tap opens the conversation; the list row below already carries the full verbs.
 */
@Composable
private fun AttentionRow(
    session: SessionRow,
    onOpen: () -> Unit,
) {
    val colors = DsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.row)
            .background(colors.bgLayer2)
            .clickable(onClick = onOpen)
            .padding(horizontal = DsSpacing.small, vertical = DsSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    when {
                        session.running -> colors.accentTertiary
                        else -> colors.warnTertiary
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                session.running -> StateDot(StateDotState.Running, size = 14.dp)
                else -> Icon(
                    FeatherIcons.Zap,
                    contentDescription = null,
                    tint = colors.warnLabel,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
        Spacer(Modifier.width(DsSpacing.small))
        Text(
            sessionTitle(session),
            style = DsType.m3TitleMedium,
            color = colors.labelPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(DsSpacing.small))
        Text(
            relativeTime(session.updatedAt),
            style = DsType.m3LabelSmall,
            color = colors.labelCaption,
        )
        Spacer(Modifier.width(DsSpacing.xsmall))
        Icon(
            FeatherIcons.ChevronRight,
            contentDescription = null,
            tint = colors.labelTertiary,
            modifier = Modifier.size(14.dp).autoMirrorDirectional(),
        )
    }
}
