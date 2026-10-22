package com.labteto.dshmobile.ui.screens.main

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.labteto.dshmobile.R
import com.labteto.dshmobile.core.wire.DshApiClient
import com.labteto.dshmobile.core.wire.PreviewPaging
import com.labteto.dshmobile.core.wire.RpcResult
import com.labteto.dshmobile.core.wire.dto.*
import com.labteto.dshmobile.data.SessionStore
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labteto.dshmobile.ui.components.DsButton
import com.labteto.dshmobile.ui.components.DsButtonSize
import com.labteto.dshmobile.ui.components.DsButtonVariant
import com.labteto.dshmobile.ui.components.DsIconButton
import com.labteto.dshmobile.ui.components.DsPill
import com.labteto.dshmobile.ui.components.DsSegment
import com.labteto.dshmobile.ui.components.DsSegmented
import com.labteto.dshmobile.ui.components.EmptyHero
import com.labteto.dshmobile.ui.components.FeatherIcons
import com.labteto.dshmobile.ui.theme.DsShapes
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsType
import androidx.compose.ui.unit.sp
import com.labteto.dshmobile.ui.components.DsCard
import com.labteto.dshmobile.ui.components.MarkdownText
import com.labteto.dshmobile.ui.theme.DsTitleBar
import com.labteto.dshmobile.ui.theme.DsTheme
import kotlinx.coroutines.*
import java.io.File

internal fun <T> RpcResult<T>.requireValue(): T = when (this) {
    is RpcResult.Ok -> value
    is RpcResult.Err -> throw IllegalStateException("${error.code}: ${error.message}")
}

/**
 * One page of a workspace file, and the page size that was used to get it.
 *
 * The request names no page size, so the host serves the page it is configured to serve — the
 * biggest one it is willing to, and the only size the client cannot know for itself. A client
 * that named its own size would be guessing: too small and it cuts a document short at a line
 * that means nothing, too large and the host refuses the request outright.
 *
 * A host that refuses a page on *bytes* (`workspace-file/too-large`) is asked again with fewer
 * lines, halving down to a floor. A file of long lines is the case this exists for: it fits in a
 * page of a hundred lines and not in a page of a thousand, and the reader should see it rather
 * than an error. The size that worked is returned so every later page is the same size.
 */
internal suspend fun readWorkspacePage(
    api: DshApiClient,
    sessionId: String,
    path: String,
    offset: Int,
    limit: Int?,
): Pair<WorkspaceFileText, Int?> {
    var size = limit
    while (true) {
        when (val result = api.workspaceFileRead(sessionId, path, WorkspaceFileRange(offset, size))) {
            is RpcResult.Ok -> return result.value to size
            is RpcResult.Err -> {
                val next = if (PreviewPaging.isTooLarge(result.error.code)) PreviewPaging.nextPageSize(size) else null
                if (next == null) throw IllegalStateException("${result.error.code}: ${result.error.message}")
                size = next
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkspacePanels(store: SessionStore, state: PanelState, onDismiss: () -> Unit) {
    val colors = DsTheme.colors
    val sessions by store.sessions.collectAsStateWithLifecycle()
    val cwd = sessions.firstOrNull { it.sessionId == state.key.sessionId }?.cwd
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = colors.bgBase) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = DsSpacing.small, vertical = DsSpacing.xsmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DsIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                        onClick = onDismiss,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.panel_workspace), style = DsType.large20, color = colors.labelPrimary)
                        cwd?.let {
                            Text(technicalDisplay(it), style = DsType.caption11, color = colors.labelCaption, softWrap = true)
                        }
                    }
                }
                // `weight`, not `fillMaxSize`: the panel body is the *rest* of the column, and a
                // weight says exactly that. A `fillMaxSize` child takes the column's whole height
                // and is then placed below the title row, so anything that does not subtract the
                // space already taken leaves the preview hanging off the bottom of the screen.
                WorkspacePanelBody(store, state, Modifier.weight(1f))
            }
        }
    }
}

/**
 * The workspace browser itself — Files / Preview / Terminal — without the screen's header, which
 * [WorkspacePanels] draws around it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkspacePanelBody(store: SessionStore, state: PanelState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val key = state.key
    val activeDocument = state.previews.getOrNull(state.selectedPreview)?.path
    fun listDirectory(path: String) {
        if (state.busy) return
        state.busy = true; state.error = null
        scope.launch {
            try {
                val api = store.apiForHost(key.host) ?: error(context.getString(R.string.common_offline))
                when (val result = api.workspaceFileList(key.sessionId, path)) {
                    is RpcResult.Ok -> { state.listing = result.value; state.directory = path }
                    // A reference guessed to be a folder (see isFolderReference) that names a
                    // file: show the file, and leave Files on the folder that holds it.
                    is RpcResult.Err -> if (result.error.code == "workspace-file/not-directory") {
                        state.directory = path.substringBeforeLast('/', ".").ifEmpty { "." }
                        state.listing = null
                        state.open(path)
                    } else result.requireValue()
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { state.error = e.message }
            finally { state.busy = false }
        }
    }
    // Keyed on the directory and on a missing listing too: `browse()` clears the listing and may
    // set a new directory, and either must fetch even though the session is unchanged.
    LaunchedEffect(key, state.directory, state.listing == null) { if (state.listing == null) listDirectory(state.directory) }
    // Invalidate previews when the host reports a file observation. OS-only changes are checked
    // by stat each time a tab opens and by the explicit Refresh action.
    //
    // Harness 0.1.7 watches one named target per stream rather than the whole workspace, so each
    // open preview gets a watch of its own; the set follows the tabs. A 0.1.6 host refuses the
    // `path` argument and is watched workspace-wide instead, once.
    val mux = store.muxForHost(key.host)
    val watched = state.previews.map { it.path }.distinct()
    var workspaceWide by remember(key, mux) { mutableStateOf(false) }
    fun invalidate(raw: kotlinx.serialization.json.JsonElement) {
        val frame = com.labteto.dshmobile.core.wire.decodeFromJsonElement(WorkspaceFileWatchFrame.serializer(), raw)
        frame.change?.let { change ->
            state.previews.filter { it.stat?.absolutePath == change.absolutePath }.forEach {
                if (change.absent || it.stat?.version != change.version) { it.stat = null; it.bytes = null; it.text = null }
            }
        }
    }
    if (mux != null && !workspaceWide) {
        watched.forEach { path -> key(path) {
            LaunchedEffect(key, mux, path) {
                try {
                    mux.openStream("workspaceFiles/changes", kotlinx.serialization.json.buildJsonObject {
                        put("workspaceFileScopeId", kotlinx.serialization.json.JsonPrimitive(key.sessionId))
                        put("path", kotlinx.serialization.json.JsonPrimitive(path))
                    }).collect { invalidate(it) }
                } catch (e: CancellationException) { throw e }
                catch (e: com.labteto.dshmobile.core.wire.RemoteStreamException) {
                    if (e.error.code == "gateway/arguments-invalid") workspaceWide = true
                }
                catch (_: Exception) { /* File reads remain available without the optional observation feed. */ }
            }
        } }
    }
    if (mux != null && workspaceWide) {
        LaunchedEffect(key, mux) {
            try {
                mux.openStream("workspaceFiles/changes", kotlinx.serialization.json.buildJsonObject {
                    put("workspaceFileScopeId", kotlinx.serialization.json.JsonPrimitive(key.sessionId))
                }).collect { invalidate(it) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* As above: the feed is an optimisation, not a dependency. */ }
        }
    }
    // A link in a previewed document is relative to that document, folders as much as files.
    fun resolve(path: String) = activeDocument?.let { com.labteto.dshmobile.core.session.resolvePreviewReference(it, path) } ?: path
    CompositionLocalProvider(
        com.labteto.dshmobile.ui.components.LocalFileOpener provides { path: String -> state.open(resolve(path)) },
        com.labteto.dshmobile.ui.components.LocalFolderOpener provides { path: String -> state.browse(resolve(path)) },
    ) {
            val colors = DsTheme.colors
            Column(modifier.padding(horizontal = DsSpacing.medium), verticalArrangement = Arrangement.spacedBy(DsSpacing.small)) {
                DsSegmented(
                    segments = listOf(
                        DsSegment("0", stringResource(R.string.panel_files)),
                        DsSegment("1", stringResource(R.string.panel_preview) + state.previews.size.takeIf { it > 0 }?.let { " · $it" }.orEmpty()),
                        DsSegment("2", stringResource(R.string.panel_terminal)),
                    ),
                    selectedKey = state.section.toString(),
                    onSelect = { state.section = it.toInt() },
                    role = Role.Tab,
                    stretch = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                when (state.section) {
                    0 -> {
                        // The breadcrumb: a folder icon for the workspace root (tappable — jumps back
                        // to "."), then each directory level separated by ›. Tapping any segment
                        // re-lists that directory. Upstream 0.15.0 puts a Root / Parent / Retry trio
                        // here; the path *is* the navigation, and a segment tap does both of the
                        // first two, so the trio would be three buttons saying what the row already
                        // says. The reload affordance is the pull below, not a fourth button.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = DsSpacing.small, vertical = DsSpacing.small),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                FeatherIcons.Folder,
                                stringResource(R.string.panel_workspace_root),
                                tint = colors.labelTertiary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable(enabled = !state.busy) { listDirectory(".") },
                            )
                            val segments = state.directory.split('/').filter { it.isNotEmpty() }
                            if (segments.isEmpty()) {
                                Text(stringResource(R.string.panel_workspace_root), style = DsType.small13Strong, color = colors.labelPrimary)
                            } else {
                                segments.forEachIndexed { i, segment ->
                                    val path = segments.take(i + 1).joinToString("/")
                                    Text(
                                        segment,
                                        style = if (i == segments.lastIndex) DsType.small13Strong else DsType.small13,
                                        color = if (i == segments.lastIndex) colors.labelPrimary else colors.labelTertiary,
                                        modifier = Modifier.clickable(enabled = !state.busy) { listDirectory(path) },
                                    )
                                    Text(" › ", style = DsType.small13, color = colors.labelCaption)
                                }
                            }
                        }
                        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = colors.accent, trackColor = colors.borderL1)
                        state.error?.let { Text(it, style = DsType.small13, color = colors.error) }
                        if (state.listing?.truncated == true) {
                            Text(stringResource(R.string.panel_truncated), style = DsType.caption11, color = colors.warnLabel)
                        }
                        val entries = state.listing?.entries.orEmpty()
                            .sortedWith(compareBy({ it.type != "directory" }, { it.name.lowercase() }))
                        if (!state.busy && state.listing != null && entries.isEmpty() && state.error == null) {
                            Text(stringResource(R.string.panel_empty_folder), style = DsType.std14, color = colors.labelTertiary, modifier = Modifier.padding(DsSpacing.medium))
                        }
                        // Pull to refresh: a directory can change under the user (the agent writes
                        // files while they browse), and the breadcrumb's root glyph is navigation
                        // rather than a reload — so the list itself carries the affordance.
                        val pullState = rememberPullToRefreshState()
                        PullToRefreshBox(
                            isRefreshing = state.busy,
                            onRefresh = { listDirectory(state.directory) },
                            modifier = Modifier.weight(1f),
                            state = pullState,
                        ) {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            state = state.directoryScroll.getOrPut(state.directory) { androidx.compose.foundation.lazy.LazyListState() },
                            verticalArrangement = Arrangement.spacedBy(DsSpacing.tiny),
                        ) {
                            items(entries, key = { it.name }) { entry ->
                                val directory = entry.type == "directory"
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(DsShapes.row)
                                        .clickable(enabled = !state.busy) {
                                            val path = if (state.directory == ".") entry.name else "${state.directory}/${entry.name}"
                                            if (directory) listDirectory(path) else state.open(path)
                                        }
                                        .padding(horizontal = DsSpacing.small, vertical = DsSpacing.xsmall),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(DsSpacing.small),
                                ) {
                                    Icon(
                                        if (directory) FeatherIcons.Folder else FeatherIcons.FileText,
                                        null,
                                        Modifier.size(18.dp),
                                        tint = if (directory) colors.accent else colors.labelTertiary,
                                    )
                                    Text(
                                        technicalDisplay(entry.name),
                                        style = DsType.std14,
                                        color = colors.labelPrimary,
                                        modifier = Modifier.weight(1f),
                                        softWrap = true,
                                    )
                                    if (directory) {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(16.dp), tint = colors.labelCaption)
                                    } else {
                                        entry.size?.let { Text(formatBytes(it), style = DsType.caption11, color = colors.labelCaption) }
                                    }
                                }
                            }
                        }
                        }
                    }
                    1 -> {
                        if (state.previews.isEmpty()) {
                            EmptyHero(headline = stringResource(R.string.panel_preview_empty), subtitle = stringResource(R.string.panel_preview_hint), showPreview = false)
                        } else {
                            // Open files as pills; the live one is selected.
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(DsSpacing.xsmall)) {
                                state.previews.forEachIndexed { i, preview ->
                                    DsPill(
                                        text = technicalDisplay(preview.path.substringAfterLast('/').substringAfterLast('\\')),
                                        selected = i == state.selectedPreview,
                                        onClick = { state.selectedPreview = i },
                                    )
                                }
                            }
                            val index = state.selectedPreview.coerceIn(0, state.previews.lastIndex)
                            val preview = state.previews[index]
                            key(preview) {
                                DocumentPreview(
                                    store, key, preview, Modifier.weight(1f),
                                    onClose = { state.previews.removeAt(index); state.selectedPreview = (index - 1).coerceAtLeast(0) },
                                    onDirectory = {
                                        state.previews.remove(preview); state.selectedPreview = (index - 1).coerceAtLeast(0)
                                        state.browse(preview.path)
                                    },
                                )
                            }
                        }
                    }
                    2 -> TerminalPanel(store, state, Modifier.weight(1f))
                }
            }
    }
}

@Composable
private fun DocumentPreview(
    store: SessionStore, key: ComposerKey, tab: PreviewTab, modifier: Modifier,
    onClose: () -> Unit, onDirectory: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = DsTheme.colors
    // "Open on host" needs a desktop on the harness computer. A headless host (a server behind a
    // tunnel, a container) says so up front through `canOpenWorkspacePath`, and the button is
    // withheld rather than offered and failed. Null while the answer is still in flight.
    var canOpenOnHost by remember(key.host) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(key.host) {
        canOpenOnHost = runCatching {
            store.apiForHost(key.host)?.sessionCanOpenWorkspacePath()?.let { (it as? RpcResult.Ok)?.value } ?: false
        }.getOrDefault(false)
    }
    val openFailed = stringResource(R.string.panel_open_host_failed)
    val extension = tab.path.substringAfterLast('.', "").lowercase()
    val binary = extension in setOf("png", "jpg", "jpeg", "webp", "gif", "pdf")
    fun load(more: Boolean = false) {
        if (tab.busy) return
        tab.busy = true; tab.error = null
        scope.launch {
            try {
                val api = store.apiForHost(key.host) ?: error(context.getString(R.string.common_offline))
                val statResult = api.workspaceFileStat(key.sessionId, tab.path)
                // A reference guessed to be a file that names a folder (`.github`): browse it.
                if (statResult is RpcResult.Err && statResult.error.code == "workspace-file/not-regular-file" &&
                    (statResult.error.details as? kotlinx.serialization.json.JsonObject)?.get("kind")
                        ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } == "directory"
                ) { onDirectory(); return@launch }
                val stat = statResult.requireValue()
                val changed = stat.version != tab.stat?.version
                if (changed) {
                    tab.text = null; tab.bytes = null; tab.nextLine = 1; tab.eof = false
                    tab.pageLimit = null
                }
                tab.stat = stat
                if (binary && tab.bytes == null) {
                    if ((stat.bytes ?: 0) > 32L * 1024 * 1024) error(context.getString(R.string.panel_too_large))
                    val data = api.workspaceFileReadBytes(key.sessionId, tab.path).requireValue()
                    if (data.version != stat.version) error(context.getString(R.string.panel_changed))
                    if (data.data.size > 32 * 1024 * 1024) error(context.getString(R.string.panel_too_large))
                    tab.bytes = data.data
                    tab.eof = true
                } else if (!binary && (tab.text == null || more)) {
                    val (data, usedLimit) = readWorkspacePage(api, key.sessionId, tab.path, tab.nextLine, tab.pageLimit)
                    tab.pageLimit = usedLimit
                    if (data.version != stat.version) error(context.getString(R.string.panel_changed))
                    if (tab.text.orEmpty().length + data.text.length > 4 * 1024 * 1024) error(context.getString(R.string.panel_too_large))
                    tab.text = if (more && !changed) tab.text.orEmpty() + "\n" + data.text else data.text
                    tab.nextLine = data.offset + data.lines
                    tab.eof = data.eof || data.lines == 0
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { tab.error = e.message }
            finally { tab.busy = false }
        }
    }
    LaunchedEffect(tab) { load() }
    LaunchedEffect(tab.stat) { if (tab.stat == null) load() }
    // The document keeps growing as the reader reaches its end.
    //
    // A page boundary is not the end of a file, and a reader who has scrolled to the bottom
    // should not have to go looking for a button to keep reading — the "Load more" button is
    // still there for the case where the file is short enough to fit on screen already. Only a
    // document that is taller than the viewport can reach the end by scrolling, so the
    // auto-extension is limited to that: a file that already fits must not turn into a run of
    // round trips nobody asked for.
    val loadMore by rememberUpdatedState<() -> Unit> { { load(true) } }
    val autoLoadSlack = with(LocalDensity.current) { 400.dp.toPx() }
    LaunchedEffect(tab) {
        snapshotFlow { tab.scroll.value to tab.scroll.maxValue }
            .collect { (value, max) ->
                if (!tab.eof && tab.text != null && max > 0 && max - value <= autoLoadSlack) loadMore()
            }
    }
    // DIAG: the scroll's own numbers, sampled rather than read in the composition so the read-out
    // does not recompose the header on every frame of a drag. `maxValue + viewport` is the content's
    // measured height, and comparing it with the text's own length is what says whether the
    // document is short or the viewport is tall. Remove once the report is closed.
    var viewportHeight by remember { mutableIntStateOf(0) }
    var scrollValue by remember { mutableIntStateOf(0) }
    var scrollMax by remember { mutableIntStateOf(0) }
    var viewportBounds by remember { mutableStateOf("") }
    LaunchedEffect(tab) {
        while (true) {
            scrollValue = tab.scroll.value
            scrollMax = tab.scroll.maxValue
            delay(500)
        }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DsSpacing.small)) {
        // File header: name, path and size on the left; the three actions on the right.
        DsCard(verticalArrangement = Arrangement.spacedBy(DsSpacing.tiny)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (binary) FeatherIcons.Image else FeatherIcons.FileText,
                    null,
                    Modifier.size(18.dp),
                    tint = colors.labelTertiary,
                )
                Spacer(Modifier.width(DsSpacing.small))
                Column(Modifier.weight(1f)) {
                    val name = tab.path.substringAfterLast('/').substringAfterLast('\\')
                    // One line, ellipsized. A long name used to wrap, and a name that wraps
                    // splits mid-word ("optimization_result.m" / "d") and makes the header taller
                    // than the file's own importance warrants.
                    Text(
                        technicalDisplay(name),
                        style = DsType.std14Strong,
                        color = colors.labelPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // The folder it is in, when it is in one; a root file would only repeat its name.
                    val folder = tab.path.replace('\\', '/').substringBeforeLast('/', "")
                    val meta = listOfNotNull(
                        folder.takeIf { it.isNotEmpty() }?.let { technicalDisplay("$it/") },
                        tab.stat?.bytes?.let { formatBytes(it) },
                    ).joinToString(" · ")
                    if (meta.isNotEmpty()) {
                        SelectionContainer {
                            Text(meta, style = DsType.caption11, color = colors.labelCaption, softWrap = true)
                        }
                    }
                    // DIAG: scroll geometry, sampled. See the note where the sampler is declared.
                    val text = tab.text
                    if (text != null) {
                        val screen = LocalConfiguration.current.screenHeightDp
                        Text(
                            "DIAG scroll $scrollValue/$scrollMax · vp $viewportHeight · " +
                                "content ${scrollMax + viewportHeight} · box $viewportBounds · " +
                                "screen ${screen}dp · chars ${text.length} · " +
                                "lines ${text.count { it == '\n' } + 1}",
                            style = DsType.caption11,
                            color = colors.warnLabel,
                        )
                    }
                }
                if (canOpenOnHost == true) DsIconButton(
                    icon = FeatherIcons.ExternalLink,
                    contentDescription = stringResource(R.string.panel_open_host),
                    onClick = { scope.launch {
                        try { store.apiForHost(key.host)?.sessionOpenWorkspacePath(key.sessionId, tab.path)?.requireValue() }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { tab.error = openFailed }
                    } },
                )
                DsIconButton(
                    icon = FeatherIcons.RefreshCw,
                    contentDescription = stringResource(R.string.common_retry),
                    onClick = { tab.stat = null; tab.text = null; tab.bytes = null },
                    enabled = !tab.busy,
                )
                DsIconButton(
                    icon = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.common_close),
                    onClick = onClose,
                )
            }
        }
        if (tab.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = colors.accent, trackColor = colors.borderL1)
        tab.error?.let { Text(it, style = DsType.small13, color = colors.error) }
        when {
            extension == "pdf" && tab.bytes != null -> PdfPreview(tab.bytes!!, Modifier.weight(1f))
            binary && tab.bytes != null -> {
                var image by remember(tab.bytes) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
                LaunchedEffect(tab.bytes) {
                    image = withContext(Dispatchers.Default) {
                        val bytes = tab.bytes ?: return@withContext null
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        val options = BitmapFactory.Options().apply { inSampleSize = com.labteto.dshmobile.ui.media.sampleSizeFor(maxOf(bounds.outWidth, bounds.outHeight), 2048) }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
                    }
                    if (image == null) tab.error = context.getString(R.string.panel_failed)
                }
                image?.let { Image(it, tab.path, Modifier.fillMaxWidth().weight(1f)) }
            }
            extension in setOf("html", "htm", "svg") && tab.text != null -> {
                val base = android.net.Uri.Builder().scheme("https").authority("preview.invalid")
                    .path("/workspace/" + tab.path.replace('\\', '/').removePrefix("/")).build()
                AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { ctx ->
                    WebView(ctx).apply {
                        if (android.os.Build.VERSION.SDK_INT <= 30) setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                        settings.javaScriptEnabled = false
                        settings.allowFileAccess = false; settings.allowContentAccess = false
                        settings.blockNetworkLoads = true
                        webViewClient = object : WebViewClient() {
                            private var totalBytes = 0L
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = true
                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse {
                                val denied = { WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream(ByteArray(0))) }
                                val uri = request?.url ?: return denied()
                                if (uri.scheme != "https" || uri.host != "preview.invalid") return denied()
                                val relative = uri.path?.let { com.labteto.dshmobile.core.session.relativePreviewResource(base.path.orEmpty(), it) }
                                    ?.takeIf { it.isNotBlank() } ?: return denied()
                                return try {
                                    val bytes = runBlocking(Dispatchers.IO) {
                                        withTimeout(10000) {
                                            val api = store.apiForHost(key.host) ?: return@withTimeout null
                                            val data = api.workspaceFileReadBytes(
                                                key.sessionId, relative, WorkspaceByteReadOptions(baseFile = tab.path),
                                            ).requireValue()
                                            if ((data.bytes ?: 0) > 4 * 1024 * 1024 || data.data.size > 4 * 1024 * 1024) return@withTimeout null
                                            data.data
                                        }
                                    } ?: return denied()
                                    synchronized(this) { totalBytes += bytes.size; if (totalBytes > 32 * 1024 * 1024) return denied() }
                                    val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(relative.substringAfterLast('.')) ?: "application/octet-stream"
                                    WebResourceResponse(mime, "utf-8", bytes.inputStream())
                                } catch (_: Exception) { denied() }
                            }
                        }
                    }
                }, update = { it.loadDataWithBaseURL(base.toString(), tab.text.orEmpty(), if (extension == "svg") "image/svg+xml" else "text/html", "utf-8", null) },
                    onRelease = { it.destroy() })
            }
            tab.text != null -> {
                // Markdown reads as a document; everything else is code, in the app's code font on
                // a code background with the file's own line breaks, scrollable both ways.
                val markdown = extension in setOf("md", "markdown")
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onSizeChanged { viewportHeight = it.height }
                        .onGloballyPositioned { coords ->
                            val b = coords.boundsInRoot()
                            viewportBounds = "${b.top.toInt()}..${b.bottom.toInt()}"
                        }
                        .clip(DsShapes.block)
                        .background(if (markdown) androidx.compose.ui.graphics.Color.Transparent else colors.bgLayer1)
                        .then(if (markdown) Modifier else Modifier.border(1.dp, colors.borderL2, DsShapes.block)),
                ) {
                    SelectionContainer(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(tab.scroll)
                            .then(if (markdown) Modifier else Modifier.horizontalScroll(rememberScrollState()))
                            .padding(DsSpacing.medium),
                    ) {
                        if (markdown) DocumentMarkdown(tab.text.orEmpty())
                        else Text(
                            tab.text.orEmpty(),
                            style = DsType.caption11.copy(fontFamily = DsType.codeFont, fontSize = 12.sp, lineHeight = 18.sp),
                            color = colors.labelPrimary,
                            softWrap = false,
                        )
                    }
                }
            }
        }
        if (!tab.eof && tab.text != null) {
            DsButton(
                text = stringResource(R.string.panel_more),
                onClick = { load(true) },
                enabled = !tab.busy,
                variant = DsButtonVariant.Outline,
                size = DsButtonSize.Small,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * A PDF held open for as long as its bytes are the ones on screen.
 *
 * `PdfRenderer` needs a seekable descriptor, so the bytes have to reach disk once — but the *page*
 * is not part of that. The renderer used to be built and torn down inside a `LaunchedEffect` keyed
 * on the page number, so every Previous/Next tap rewrote the whole document to the cache, re-parsed
 * it, built a fresh renderer and deleted the file again. Slow, and a lot of pointless flash writes
 * for a document that had not changed.
 */
private class PdfDocument(private val context: Context) {
    private var file: File? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    val pageCount: Int get() = renderer?.pageCount ?: 0

    /** Writes and opens [bytes]; false when they are not a PDF this device can read. */
    fun open(bytes: ByteArray): Boolean {
        close()
        return try {
            val created = File.createTempFile("preview-", ".pdf", context.cacheDir)
            created.writeBytes(bytes)
            val fd = ParcelFileDescriptor.open(created, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdf = PdfRenderer(fd)
            file = created; descriptor = fd; renderer = pdf
            pdf.pageCount > 0
        } catch (_: Exception) {
            close(); false
        }
    }

    /** Renders one page from the open document, or null when there is nothing to render. */
    fun render(page: Int): androidx.compose.ui.graphics.ImageBitmap? {
        val pdf = renderer ?: return null
        return pdf.openPage(page.coerceIn(0, pdf.pageCount - 1)).use { p ->
            val scale = minOf(2f, 2048f / maxOf(p.width, p.height))
            val image = Bitmap.createBitmap((p.width * scale).toInt().coerceAtLeast(1), (p.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            image.eraseColor(android.graphics.Color.WHITE)
            p.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            image.asImageBitmap()
        }
    }

    fun close() {
        runCatching { renderer?.close() }
        runCatching { descriptor?.close() }
        runCatching { file?.delete() }
        renderer = null; descriptor = null; file = null
    }
}

@Composable
private fun PdfPreview(bytes: ByteArray, modifier: Modifier) {
    val context = LocalContext.current
    var page by remember(bytes) { mutableIntStateOf(0) }
    var count by remember(bytes) { mutableIntStateOf(0) }
    var failure by remember(bytes) { mutableStateOf(false) }
    var bitmap by remember(bytes, page) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    val document = remember(bytes) { PdfDocument(context) }
    DisposableEffect(document) { onDispose { document.close() } }
    // Opened once per document; the page effect below only ever renders from this one handle.
    LaunchedEffect(document, bytes) {
        val opened = withContext(Dispatchers.IO) { document.open(bytes) }
        count = document.pageCount
        failure = !opened
    }
    LaunchedEffect(document, page, count) {
        // count changes when the document is finally open, which is what sequences this after it.
        if (count == 0) return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) { runCatching { document.render(page) }.getOrNull() }
    }
    Column(modifier) {
        if (failure) Text(stringResource(R.string.panel_failed))
        bitmap?.let { Image(it, null, Modifier.weight(1f).fillMaxWidth()) }
        Row {
            TextButton(onClick = { page-- }, enabled = page > 0) { Text(stringResource(R.string.common_back)) }
            Text("${page + 1} / $count", Modifier.padding(16.dp))
            TextButton(onClick = { page++ }, enabled = page + 1 < count) { Text(stringResource(R.string.panel_next)) }
        }
    }
}

/** `1.2 KB`, `3.4 MB` — a size a person can read beside a file name. */
private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
