package com.labteto.dshmobile.ui.screens.main

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.labteto.dshmobile.R
import com.labteto.dshmobile.core.wire.decodeFromJsonElement
import com.labteto.dshmobile.core.wire.dto.*
import com.labteto.dshmobile.data.SessionStore
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import com.labteto.dshmobile.ui.components.DsButton
import com.labteto.dshmobile.ui.components.DsButtonSize
import com.labteto.dshmobile.ui.components.DsButtonVariant
import com.labteto.dshmobile.ui.components.DsIconButton
import com.labteto.dshmobile.ui.components.DsPill
import com.labteto.dshmobile.ui.components.EmptyHero
import com.labteto.dshmobile.ui.components.FeatherIcons
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsType
import com.labteto.dshmobile.ui.theme.DsTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.*
import org.json.JSONObject
import java.util.UUID

@Composable
internal fun TerminalPanel(store: SessionStore, state: PanelState, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val key = state.key
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var shellMenu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<String?>(null) }
    fun operation(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message }
            finally { busy = false }
        }
    }
    suspend fun refresh() {
        val api = store.apiForHost(key.host) ?: error(context.getString(R.string.common_offline))
        state.terminals = api.terminalList(key.sessionId).requireValue()
        state.shells = api.terminalShells(key.sessionId).requireValue()
        if (state.terminals.none { it.id == state.selectedTerminal }) state.selectedTerminal = state.terminals.firstOrNull()?.id
    }
    // Auto-open: on every entry to this tab, switch to an existing terminal if one is alive;
    // create one only when there are none. The terminal lives server-side and survives navigation
    // — closing the panel does not kill it (the terminal/retain stream holds it). Re-entering the
    // tab restores the last session via PanelState persistence in PanelRepository.
    //
    // This deliberately does NOT go through `operation`: that helper only launches a coroutine and
    // returns, so the emptiness test used to run before the listing arrived, saw an empty list on
    // every visit, and created a fresh terminal each time. `refresh()` is already suspend —
    // awaiting it here is the whole fix.
    LaunchedEffect(key) {
        val api = store.apiForHost(key.host)
        if (api == null) { error = context.getString(R.string.common_offline); return@LaunchedEffect }
        try {
            refresh()
            if (state.terminals.isEmpty()) {
                val info = api.terminalCreate(key.sessionId, TerminalCreateRequest(UUID.randomUUID().toString(), 80, 24, state.shellPath)).requireValue()
                state.selectedTerminal = info.id
                refresh()
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message }
    }
    val colors = DsTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DsSpacing.small)) {
        // Toolbar: which shell, a new one, refresh. Not scrollable and not wrapping: the row is a
        // fixed four items, and upstream's `Spacer(weight(1f))` below needs a bounded width — a
        // `weight` inside a `horizontalScroll` row is measured at infinity and cannot work.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.xsmall)) {
            Box {
                DsPill(
                    text = state.shells.firstOrNull { it.path == state.shellPath }?.name ?: stringResource(R.string.terminal_shell),
                    onClick = { if (!busy) shellMenu = true },
                )
                DropdownMenu(shellMenu, { shellMenu = false }) {
                    state.shells.forEach { shell -> DropdownMenuItem(text = { Text(shell.name) }, onClick = { state.shellPath = shell.path; shellMenu = false }) }
                }
            }
            DsButton(
                text = stringResource(R.string.terminal_new),
                icon = Icons.Filled.Add,
                enabled = !busy,
                onClick = { operation {
                    val api = store.apiForHost(key.host) ?: error(context.getString(R.string.common_offline))
                    val info = api.terminalCreate(key.sessionId, TerminalCreateRequest(UUID.randomUUID().toString(), 80, 24, state.shellPath)).requireValue()
                    state.selectedTerminal = info.id
                    refresh()
                } },
                variant = DsButtonVariant.Info,
                size = DsButtonSize.Small,
            )
            Spacer(Modifier.weight(1f))
            DsIconButton(
                icon = FeatherIcons.RefreshCw,
                contentDescription = stringResource(R.string.common_retry),
                onClick = { operation { refresh() } },
                enabled = !busy,
            )
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = colors.accent, trackColor = colors.borderL1)
        error?.let { Text(it, style = DsType.small13, color = colors.error) }
        // Open terminals as pills; the live one is selected.
        if (state.terminals.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(DsSpacing.xsmall)) {
                state.terminals.forEach { terminal ->
                    DsPill(text = terminal.title, selected = terminal.id == state.selectedTerminal, onClick = { state.selectedTerminal = terminal.id })
                }
            }
        }
        val terminal = state.terminals.firstOrNull { it.id == state.selectedTerminal }
        if (terminal == null) {
            EmptyHero(headline = stringResource(R.string.terminal_empty), subtitle = null, showPreview = false)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(terminal.title, style = DsType.std14Strong, color = colors.labelPrimary, modifier = Modifier.weight(1f))
                DsIconButton(
                    icon = FeatherIcons.Edit3,
                    contentDescription = stringResource(R.string.common_rename),
                    onClick = { rename = terminal.title },
                )
                DsIconButton(
                    icon = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.common_close),
                    enabled = !busy,
                    onClick = { operation {
                        store.apiForHost(key.host)?.terminalClose(key.sessionId, terminal.id)?.requireValue()
                            ?: error(context.getString(R.string.common_offline))
                        refresh()
                    } },
                )
            }
            key(terminal.id) { TerminalScreen(store, key, terminal, Modifier.weight(1f)) }
            rename?.let { title -> AlertDialog(onDismissRequest = { rename = null }, title = { Text(stringResource(R.string.common_rename)) },
                text = { OutlinedTextField(title, { rename = it }) },
                confirmButton = { TextButton(enabled = title.isNotBlank() && title.length <= 120 && !busy, onClick = { operation {
                    store.apiForHost(key.host)?.terminalRename(key.sessionId, terminal.id, title)?.requireValue()
                        ?: error(context.getString(R.string.common_offline))
                    rename = null; refresh()
                } }) { Text(stringResource(R.string.common_save)) } },
                dismissButton = { TextButton(onClick = { rename = null }) { Text(stringResource(R.string.common_cancel)) } }) }
        }
    }
}

private class TerminalBridge(private val receive: (String) -> Unit) {
    @JavascriptInterface fun postMessage(message: String) { if (message.length <= 131072) receive(message) }
}

/**
 * Reads the terminal bundle, then hands off to [TerminalWeb].
 *
 * Split out because the bundle is a few hundred KB of JavaScript that used to be read
 * synchronously inside the WebView's `remember` — on the main thread, every time the tab was
 * opened. The page is assembled as one string and handed to `loadDataWithBaseURL`, so the WebView
 * cannot fetch those files itself; reading them off the main thread is the smaller change than
 * rewiring the page through `WebViewAssetLoader`, and it keeps composition free of I/O.
 */
@Composable
private fun TerminalScreen(store: SessionStore, key: ComposerKey, initial: WebTerminalInfo, modifier: Modifier) {
    val context = LocalContext.current
    // `held` seeds the state so a second visit to the tab shows the terminal immediately instead
    // of flashing a spinner for a read that already happened.
    var assets by remember { mutableStateOf(TerminalAssets.held) }
    LaunchedEffect(context) {
        if (assets == null) assets = withContext(Dispatchers.IO) { TerminalAssets.load(context) }
    }
    val loaded = assets
    if (loaded == null) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        TerminalWeb(store, key, initial, modifier, loaded)
    }
}

/**
 * The terminal bundle, read once per process.
 *
 * `held` lets a second visit to the tab skip the read entirely rather than paying for it again.
 */
private class TerminalAssets(val js: String, val fit: String, val css: String) {
    companion object {
        @Volatile private var shared: TerminalAssets? = null

        val held: TerminalAssets? get() = shared

        fun load(context: Context): TerminalAssets = shared ?: TerminalAssets(
            js = context.assets.open("terminal/xterm.js").bufferedReader().use { it.readText() },
            fit = context.assets.open("terminal/addon-fit.js").bufferedReader().use { it.readText() },
            css = context.assets.open("terminal/xterm.css").bufferedReader().use { it.readText() },
        ).also { shared = it }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TerminalWeb(
    store: SessionStore,
    key: ComposerKey,
    initial: WebTerminalInfo,
    modifier: Modifier,
    assets: TerminalAssets,
) {
    val context = LocalContext.current
    val connection by store.connectionState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var reconnect by remember { mutableIntStateOf(0) }
    var info by remember { mutableStateOf(initial) }
    var writable by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(false) }
    // TEMPORARY scroll diagnostics. The terminal's touch scrolling reportedly moves one row no
    // matter how far a finger travels, and it does not reproduce on desktop: xterm's own
    // translate-pixels-to-lines path is correct there, so the fault is in how Android WebView
    // delivers the gesture. Guessing at a fix would mean shipping blind, so the page reports what
    // it actually sees and this line shows it over the terminal. Removed once the cause is known.
    var scrollDiag by remember { mutableStateOf<String?>(null) }
    var environment by remember { mutableStateOf<TerminalEnvironment?>(null) }
    val attachmentId = remember(reconnect) { UUID.randomUUID().toString() }
    val input = remember { Channel<String>(64) }
    val colors = DsTheme.colors
    val background = "#%06x".format(colors.bgBase.toArgb() and 0xffffff)
    val foreground = "#%06x".format(colors.labelPrimary.toArgb() and 0xffffff)
    val view = remember {
        WebView.setWebContentsDebuggingEnabled(com.labteto.dshmobile.BuildConfig.DEBUG)
        WebView(context).apply {
            // Legacy WebViews can lose the containing Compose dialog's surface with GPU drawing.
            if (android.os.Build.VERSION.SDK_INT <= 30) setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false; settings.allowContentAccess = false
            settings.blockNetworkLoads = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = true
            }
            addJavascriptInterface(TerminalBridge { message ->
                scope.launch {
                    val json = runCatching { JSONObject(message) }.getOrNull() ?: return@launch
                    when (json.optString("type")) {
                        "ready" -> ready = true
                        // Scroll diagnostics carry no terminal state; they must never reach the
                        // input channel, which would try to parse them as keystrokes.
                        "diag" -> scrollDiag = json.optString("text").takeIf { it.isNotEmpty() }
                        else -> input.send(message)
                    }
                }
            }, "TerminalHost")
            val js = assets.js
            val fit = assets.fit
            val css = assets.css
            loadDataWithBaseURL("https://terminal.invalid/", """
                <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
                <style>html,body,#terminal{width:100%;height:100%;margin:0;overflow:hidden} $css</style></head><body><div id="terminal"></div>
                <script>
                // Android 11 can ship a WebView older than replaceChildren (Chrome 86).
                for (const proto of [Element.prototype,DocumentFragment.prototype]) {
                    if (!proto.replaceChildren) proto.replaceChildren=function(...children){
                        while(this.firstChild)this.removeChild(this.firstChild);
                        for(const child of children)this.appendChild(typeof child==='string'?document.createTextNode(child):child);
                    };
                }
                </script><script>$js</script><script>$fit</script><script>
                const term = new Terminal({fontSize:14,scrollback:1000,disableStdin:true});
                const fit = new FitAddon.FitAddon();term.loadAddon(fit);term.open(document.getElementById('terminal'));
                function post(value){TerminalHost.postMessage(JSON.stringify(value))}
                // Raw key injection for the on-screen D-pad. Deliberately NOT term.paste(): that
                // path runs the data through bracketTextForPaste and wraps it in \x1b[200~ / \x1b[201~,
                // so a shell in bracketed-paste mode sees the arrow sequence as pasted *text* and
                // the cursor never moves. Posting straight to the input channel is byte-identical
                // to what term.onData emits for a real keypress.
                window.sendKey=function(data){post({type:'input',data})};
                function measure(){
                    // Older Android WebViews resolve percentage/vh heights to zero in a dialog.
                    const height=Math.max(1,window.innerHeight)+'px';
                    document.documentElement.style.height=height;document.body.style.height=height;
                    document.getElementById('terminal').style.height=height;
                    const d=fit.proposeDimensions();if(d)post({type:'resize',cols:d.cols,rows:d.rows});
                }
                window.renderFrame=function(frame){if(frame.type==='snapshot'){term.reset();term.resize(frame.info.cols,frame.info.rows);term.write(frame.screen||'')}else if(frame.type==='output')term.write(frame.data||'')};
                window.setWritable=function(value){term.options.disableStdin=!value;if(value){measure();term.focus()}};
                window.setTheme=function(bg,fg){term.options.theme={background:bg,foreground:fg,cursor:fg};document.body.style.background=bg};
                term.onData(data=>post({type:'input',data}));window.addEventListener('resize',measure);
                // TEMPORARY scroll diagnostics. Watches the real touch stream on the terminal root
                // (a *passive* listener, so it cannot change what xterm itself does) and reports,
                // per gesture: which element the finger landed on, how far it moved, and how much
                // of that movement the viewport actually kept. A gesture whose `kept` stays at one
                // row while `moved` grows is the reported symptom; the hit element and the scroll
                // area's height then say whether the cause is a swallowed gesture or a short
                // scroll area. Remove together with the `scrollDiag` state once diagnosed.
                (function(){
                    const root=document.querySelector('.xterm');
                    let startY=0,moved=0,first=null,lastSent=0;
                    function snap(){
                        const vp=document.querySelector('.xterm-viewport');
                        const area=document.querySelector('.xterm-scroll-area');
                        const core=term._core;
                        return {
                            vp:vp, area:area,
                            scrollTop:vp?Math.round(vp.scrollTop):-1,
                            maxTop:vp?Math.round(vp.scrollHeight-vp.clientHeight):-1,
                            areaH:area?Math.round(parseFloat(area.style.height||'0')):-1,
                            ydisp:term.buffer.active.viewportY,
                            lines:term.buffer.active.length,
                            rowH:core&&core.viewport?Math.round(core.viewport._currentRowHeight):-1,
                        };
                    }
                    function report(phase){
                        const s=snap();
                        // Line the person can compare against what they see.
                        return phase+' hit='+(first||'?')
                            +' moved='+Math.round(moved)+'px'
                            +' kept='+s.scrollTop+'/'+s.maxTop
                            +' area='+s.areaH+' row='+s.rowH
                            +' y='+s.ydisp+'/'+s.lines;
                    }
                    root.addEventListener('touchstart',function(e){
                        const t=e.touches[0];
                        startY=t?t.pageY:0; moved=0;
                        const el=document.elementFromPoint(t?t.clientX:0,t?t.clientY:0);
                        first=el?(el.tagName.toLowerCase()+(el.className?('.'+String(el.className).split(' ')[0]):'')):'none';
                        post({type:'diag',text:report('start')});
                    },{passive:true});
                    root.addEventListener('touchmove',function(e){
                        const t=e.touches[0];
                        if(!t)return;
                        moved=startY-t.pageY;
                        // Throttle: the overlay is for a human to read, not a trace log.
                        const now=Date.now();
                        if(now-lastSent>120){lastSent=now;post({type:'diag',text:report('move')});}
                    },{passive:true});
                    root.addEventListener('touchend',function(){
                        post({type:'diag',text:report('end')});
                    },{passive:true});
                })();
                post({type:'ready'});
                </script></body></html>
            """.trimIndent(), "text/html", "utf-8", null)
        }
    }
    DisposableEffect(view) { onDispose { input.close(); view.removeJavascriptInterface("TerminalHost"); view.destroy() } }
    LaunchedEffect(ready, background, foreground) {
        if (ready) view.evaluateJavascript("setTheme(${JSONObject.quote(background)},${JSONObject.quote(foreground)})", null)
    }
    LaunchedEffect(ready, writable) { if (ready) view.evaluateJavascript("setWritable($writable)", null) }
    LaunchedEffect(ready, reconnect, connection, store.muxForHost(key.host)) {
        if (!ready) return@LaunchedEffect
        connected = false; writable = false; error = null
        while (input.tryReceive().isSuccess) { /* Discard input meant for the previous attachment. */ }
        try {
            val api = store.apiForHost(key.host) ?: error(context.getString(R.string.common_offline))
            val mux = store.muxForHost(key.host) ?: error(context.getString(R.string.common_offline))
            val env = api.terminalEnvironment(key.sessionId).requireValue()
            environment = env
            val attached = attachDuplex(mux, api, key, initial, env, view, input,
                onFrame = { frame, isWritable ->
                    if (frame.type == "snapshot") connected = true
                    frame.info?.let { info = it }
                    if (isWritable != null) writable = connected && info.state == "running" && isWritable
                },
                onInputRefused = { error = context.getString(R.string.panel_too_large) },
                gapMessage = context.getString(R.string.panel_changed),
            )
            if (!attached) {
                // A host before 0.2.1-alpha.3 has no duplex attach. It streams the screen on
                // `terminal/follow` and takes input as separate calls, with control decided by
                // which attachment id the host names as controller.
                followLegacy(mux, api, key, initial, attachmentId, env, view, input,
                    onFrame = { frame ->
                        if (frame.type == "snapshot") connected = true
                        frame.info?.let { info = it }
                        writable = connected && info.state == "running" && info.controllerId == attachmentId
                    },
                    isWritable = { writable },
                    onInputFailed = { message -> writable = false; error = message },
                    gapMessage = context.getString(R.string.panel_changed),
                    tooLargeMessage = context.getString(R.string.panel_too_large),
                )
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message }
        finally { connected = false; writable = false }
    }
    // Hold the terminal open for as long as this tab is on screen.
    //
    // Current-master hosts reclaim a terminal after a couple of hours of confirmed idle unless
    // some window is holding it, and `terminal/follow` deliberately does not count — it is a
    // subscription to the screen, not a claim on the process. Without this a terminal left open on
    // a phone is collected underneath the person while its tab still looks live.
    //
    // The hold is its own stream and carries no data: the first frame is the acknowledgement and
    // the rest of its life is just staying open. A host that predates the endpoint fails the
    // stream instead, which is the same "does not offer that" answer a 404 gives elsewhere and is
    // ignored for the same reason — there is nothing to hold and nothing to tell the person.
    LaunchedEffect(initial.id, attachmentId, reconnect, connection, store.muxForHost(key.host)) {
        val mux = store.muxForHost(key.host) ?: return@LaunchedEffect
        try {
            mux.openStream("terminal/retain", buildJsonObject {
                put("sessionId", JsonPrimitive(key.sessionId)); put("id", JsonPrimitive(initial.id))
            }).collect { /* The stream's existence is the hold; its frames carry nothing to read. */ }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* No retention service composed, or the terminal is already gone. */ }
    }

    Column(modifier.fillMaxWidth()) {
        if (!connected || !writable) Row {
            Text(if (connected) stringResource(R.string.terminal_readonly) else stringResource(R.string.common_offline), Modifier.weight(1f).padding(12.dp))
            TextButton(onClick = { reconnect++ }) { Text(stringResource(if (connected) R.string.terminal_control else R.string.common_retry)) }
        }
        if (info.state != "running") Text(stringResource(if (info.state == "failed") R.string.common_error else R.string.chat_stopped) + " (${info.exitCode ?: "—"})", Modifier.padding(12.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
        // The D-pad overlay sits in the top-right corner of the terminal content area. It sends
        // arrow keys and Enter to the shell — a phone has no physical keyboard, so this is the
        // primary way to navigate command-line UIs (fzf, htop, vim, etc.).
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
            VirtualDPad(
                // sendKey() posts to the same input channel term.onData uses. term.paste() is
                // wrong here: it brackets the payload as a paste, so the shell never sees a key.
                onKey = { data -> view.evaluateJavascript("sendKey(${JSONObject.quote(data)})", null) },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            )
            // TEMPORARY: the live scroll diagnostics. Top-start keeps it clear of the D-pad.
            scrollDiag?.let { text ->
                Text(
                    text,
                    style = DsType.small13,
                    color = colors.labelPrimary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(colors.bgLayer1.copy(alpha = 0.88f))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
        }
    }
}

/**
 * How long a direction has to be held before it starts repeating, and how fast it repeats after.
 *
 * The delay is what keeps a deliberate single tap from arriving twice; the interval is roughly a
 * keyboard's auto-repeat, which is what a cursor key feels like.
 */
private const val DPAD_REPEAT_DELAY_MS = 400L
private const val DPAD_REPEAT_INTERVAL_MS = 80L

/**
 * A virtual D-pad (directional pad) for navigating command-line UIs from a phone.
 *
 * A 128dp dial: Enter at the centre, four chevrons at the compass points.
 *
 * The geometry is deliberately loose. Enter is 48dp (radius 24) and a direction is 36dp (radius 18)
 * centred 51.2dp out, so a direction's inner edge sits at 33.2dp — 9.2dp clear of Enter. An earlier
 * 72dp Enter overlapped that ring by nearly 3dp and made Enter easy to hit while aiming for a
 * direction.
 *
 * Keys go out through `sendKey`, which posts to the same channel `term.onData` uses for a real
 * keypress:
 * - Up:    \\u001b[A
 * - Down:  \\u001b[B
 * - Right: \\u001b[C
 * - Left:  \\u001b[D
 * - Enter: \\r
 */
@Composable
private fun VirtualDPad(onKey: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = DsTheme.colors
    val size = 128.dp
    val buttonSize = 36.dp
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        DpadButton(
            glyph = "⏎",
            sequence = "\r",
            onKey = onKey,
            repeats = false,
            background = colors.bgLayer1.copy(alpha = 0.9f),
            textColor = colors.labelPrimary,
            modifier = Modifier.size(48.dp),
        )
        // Compass points. In Compose positive y is DOWN, so Up is -y and Down is +y.
        data class DpadKey(val x: Float, val y: Float, val key: String, val glyph: String)
        listOf(
            DpadKey(0f, -1f, "\u001b[A", "˄"),   // Up (top of screen)
            DpadKey(0f, 1f, "\u001b[B", "˅"),    // Down (bottom of screen)
            DpadKey(1f, 0f, "\u001b[C", "›"),    // Right
            DpadKey(-1f, 0f, "\u001b[D", "‹"),   // Left
        ).forEach { (x, y, key, glyph) ->
            DpadButton(
                glyph = glyph,
                sequence = key,
                onKey = onKey,
                repeats = true,
                background = colors.bgLayer1.copy(alpha = 0.7f),
                textColor = colors.labelTertiary,
                modifier = Modifier
                    .size(buttonSize)
                    .offset(x = (x * size.value / 2.5f).toInt().dp, y = (y * size.value / 2.5f).toInt().dp),
            )
        }
    }
}

/**
 * One button of the dial.
 *
 * The key fires on *press*, not on release, because a `clickable` reports its click only after the
 * finger lifts — which would make holding a direction do nothing at all. `collectIsPressedAsState`
 * gives the press immediately, and [repeats] keeps it firing while the finger stays down.
 */
@Composable
private fun DpadButton(
    glyph: String,
    sequence: String,
    onKey: (String) -> Unit,
    repeats: Boolean,
    background: Color,
    textColor: Color,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    LaunchedEffect(pressed, sequence, repeats) {
        if (!pressed) return@LaunchedEffect
        onKey(sequence)
        if (!repeats) return@LaunchedEffect
        delay(DPAD_REPEAT_DELAY_MS)
        while (true) {
            onKey(sequence)
            delay(DPAD_REPEAT_INTERVAL_MS)
        }
    }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (pressed) background.copy(alpha = 1f) else background)
            // Empty onClick on purpose: the press state above is the trigger. A real onClick would
            // fire the key a second time when the finger lifted.
            .clickable(interactionSource = interaction, indication = null, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = DsType.base16Strong, color = textColor)
    }
}

/** Gateway answers meaning the host has no such stream, as opposed to refusing this request. */
private val NO_DUPLEX = setOf(
    "gateway/method-unavailable",
    "gateway/invocation-unavailable",
    "gateway/arguments-invalid",
    "capability-unavailable",
)

/**
 * Run one `terminal/attach` attachment (harness 0.2.1-alpha.3 and later) until it ends.
 *
 * Output arrives on the stream's downlink and input leaves on its uplink as numbered commands;
 * [TerminalInputLedger] keeps those within the host's window and releases them on `ack`. Input
 * and resizes from the page are pumped onto the uplink by a sibling coroutine for as long as the
 * stream is open.
 *
 * Returns false, having done nothing visible, when the host has no duplex attach at all, so the
 * caller can fall back to the older follow-and-call protocol.
 */
private suspend fun attachDuplex(
    mux: com.labteto.dshmobile.core.wire.RemoteStreamMux,
    api: com.labteto.dshmobile.core.wire.DshApiClient,
    key: ComposerKey,
    terminal: WebTerminalInfo,
    env: TerminalEnvironment,
    view: WebView,
    input: Channel<String>,
    onFrame: (TerminalFrame, Boolean?) -> Unit,
    onInputRefused: () -> Unit,
    gapMessage: String,
): Boolean = coroutineScope {
    val stream = mux.open("terminal/attach", api.terminalAttachArgs(key.sessionId, terminal.id))
    val ledger = com.labteto.dshmobile.core.wire.TerminalInputLedger(env.maxInputBytes, env.maxBufferedBytes)
    var writable = false
    var cols = terminal.cols
    var rows = terminal.rows
    fun flush() {
        if (!writable) return
        for (command in ledger.drain()) {
            if (!stream.send(command)) error(gapMessage)
        }
    }
    var pump: Job? = null
    try {
        var outputSequence: Long? = null
        while (true) {
            val raw = try {
                stream.receive()
            } catch (e: com.labteto.dshmobile.core.wire.RemoteStreamException) {
                if (outputSequence == null && !e.carrier && e.error.code in NO_DUPLEX) return@coroutineScope false
                throw e
            } ?: break
            val frame = decodeFromJsonElement(TerminalFrame.serializer(), raw)
            when (frame.type) {
                "snapshot" -> {
                    outputSequence = frame.sequence
                    ledger.window = frame.inputWindow
                    frame.info?.let { cols = it.cols; rows = it.rows }
                    writable = frame.writable == true && frame.info?.state == "running"
                    onFrame(frame, frame.writable == true)
                    view.evaluateJavascript("renderFrame($raw)", null)
                    if (pump == null) pump = launch {
                        for (message in input) {
                            val json = runCatching { JSONObject(message) }.getOrNull() ?: continue
                            if (!writable) continue
                            val accepted = when (json.optString("type")) {
                                "input" -> ledger.write(json.optString("data"))
                                "resize" -> {
                                    val c = json.optInt("cols").coerceIn(2, env.maxCols)
                                    val r = json.optInt("rows").coerceIn(1, env.maxRows)
                                    if (c == cols && r == rows) {
                                        true
                                    } else {
                                        ledger.resize(c, r).also { ok ->
                                            if (ok) {
                                                cols = c; rows = r
                                                view.evaluateJavascript("term.resize($c,$r)", null)
                                            }
                                        }
                                    }
                                }
                                else -> true
                            }
                            if (!accepted) onInputRefused() else flush()
                        }
                    }
                }
                "output" -> {
                    val next = frame.sequence ?: error(gapMessage)
                    val previous = outputSequence ?: error(gapMessage)
                    if (next != previous + 1) error(gapMessage)
                    outputSequence = next
                    view.evaluateJavascript("renderFrame($raw)", null)
                }
                "state" -> {
                    writable = frame.writable == true && frame.info?.state == "running"
                    onFrame(frame, frame.writable == true)
                    flush()
                }
                "ack" -> {
                    if (!ledger.acknowledge(frame.sequence ?: -1)) error(gapMessage)
                    if (frame.refusal != null) {
                        writable = false
                        onFrame(frame, false)
                    }
                    flush()
                }
            }
        }
        true
    } finally {
        pump?.cancel()
        stream.cancel()
    }
}

/** The pre-alpha.3 terminal: `terminal/follow` for the screen, unary write and resize for input. */
private suspend fun followLegacy(
    mux: com.labteto.dshmobile.core.wire.RemoteStreamMux,
    api: com.labteto.dshmobile.core.wire.DshApiClient,
    key: ComposerKey,
    terminal: WebTerminalInfo,
    attachmentId: String,
    env: TerminalEnvironment,
    view: WebView,
    input: Channel<String>,
    onFrame: (TerminalFrame) -> Unit,
    isWritable: () -> Boolean,
    onInputFailed: (String?) -> Unit,
    gapMessage: String,
    tooLargeMessage: String,
) = coroutineScope {
    val pump = launch {
        for (message in input) {
            if (!isWritable()) continue
            try {
                val json = JSONObject(message)
                when (json.optString("type")) {
                    "input" -> {
                        val data = json.optString("data")
                        if (data.toByteArray(Charsets.UTF_8).size > env.maxInputBytes) error(tooLargeMessage)
                        api.terminalWrite(key.sessionId, terminal.id, attachmentId, data).requireValue()
                    }
                    "resize" -> {
                        val cols = json.optInt("cols").coerceIn(2, env.maxCols)
                        val rows = json.optInt("rows").coerceIn(1, env.maxRows)
                        api.terminalResize(key.sessionId, terminal.id, attachmentId, cols, rows).requireValue()
                        view.evaluateJavascript("term.resize($cols,$rows)", null)
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { onInputFailed(e.message) }
        }
    }
    try {
        var sequence: Long? = null
        mux.openStream("terminal/follow", api.terminalFollowArgs(key.sessionId, terminal.id, attachmentId)).collect { raw ->
            val frame = decodeFromJsonElement(TerminalFrame.serializer(), raw)
            if (frame.type == "snapshot") sequence = frame.sequence
            else if (frame.type == "output") {
                val next = frame.sequence ?: error(gapMessage)
                if (sequence == null || next != sequence!! + 1) error(gapMessage)
                sequence = next
            }
            onFrame(frame)
            view.evaluateJavascript("renderFrame($raw)", null)
        }
    } finally {
        pump.cancel()
    }
}
