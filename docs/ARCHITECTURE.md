# Architecture

DSH Mobile is a three-module Gradle project (Kotlin 2.0, Compose, Hilt).

```
core/           pure JVM — no Android imports
  wire/         the DeepSeek Harness web-client protocol:
                  envelopes (client-request / server-response), the
                  lenient WireJson codec, RpcTransport (OkHttp),
                  WsChannel (the bidirectional /api/remote.mux socket),
                  RemoteStreamMux (logical streams over it),
                  DshApiClient (typed unary methods, namespace/method),
                  ConnectionLoop (readiness handshake: mux open + the
                  $events ready frame, exponential backoff)
  wire/dto/     kotlinx.serialization ports of the harness schemas
                  (sessions, session history/control, host, workspace,
                  skills, goals, settings, credentials, llm, subagents,
                  agent presets, events, stream protocol) — lenient,
                  merge-extensible
  session/      EventFold: raw session events → ConversationSnapshot
                  (turn/step/message/tool nodes, streaming block assembly,
                  interruption marking, gap detection);
                  ChunkRows: expands packed history delta runs
  notify/       CompletionClassifier: turn/goal/approval/question/idle
                  events with dedup keys

app/            Android UI
  connection/   HostsStore (remembered hosts + settings, DataStore),
                  DiscoveryEngine (Wi-Fi subnet sweep +
                  session/canOpenWorkspacePath probe), ConnectionManager
                  (owns the ConnectionLoop, exposes the host event flow
                  and the current generation), ConnectionService
                  (foreground service), KeepAliveWorker (15-min fallback)
  data/         SessionStore — the live mirror of the harness: session
                  list/workspaces/folds per session, queue/jobs/
                  projections, approvals/questions, subagent catalog
  notify/       NotificationObserver — classifier → channels, dedup,
                  deep links
  media/        AttachmentImages — LruCache + BitmapFactory decoding of
                  session attachments (no image library: the bytes arrive
                  through session.attachment, not a URL)
  ui/           theme (exact DSH design tokens + motion specs), components
                  (buttons, disclosure rows, state dots, tool cards,
                  markdown, overlays, bottom sheets, context meter),
                  screens (connect, main shell with Discord-style drawer +
                  details panel, chat, settings)

The chat surface is split by responsibility rather than living in one file:
ChatScreen (shell) · ChatTopBar (two-row chrome + Chat/Trajectory tabs) ·
ChatTranscript · ChatNodeItem · ToolRowModel (verb + cwd-relative summary) ·
Composer · Docks · TrajectoryTab · Sheet*.kt (commands, models, presets,
subagents, permission) · ChatProjections (defensive readers).

mock-harness/   Ktor implementation of the /api protocol for tests
tools/capture/  Node recorder of real harness traffic → conformance fixtures
```

## Data flow

1. `ConnectionManager` performs the readiness handshake and pumps the two
   WebSocket downlinks; frames fan out as SharedFlows.
2. `SessionStore` folds session events into `ConversationSnapshot`s
   (incremental) from that session's session/follow stream, keeps the
   workspace registry from workspace/follow, and merges queue/jobs/
   projection snapshots from session/control. Typed projection views
   (permissions, stats, usage, context, image limits) are *derived* from
   that snapshot rather than fetched, so they stay in lockstep with the
   transcript and cost no round trips.
2b. On first connect `baseline()` resolves a landing session
   (`data/InitialSession.kt`): the session last opened on this harness,
   else the most recently active one. Reconnects keep whatever was open.
3. Screens observe `StateFlow`s and render; user actions go back through
   `SessionStore` → `DshApiClient` (`POST /api/<namespace>/<method>`), and
   pending approvals/questions are answered through `$events/result`.
4. `NotificationObserver` classifies host events into completion events and
   posts channel-notifications that deep-link into sessions. Turn and goal
   completions reach it from `SessionStore`, which owns the only stream
   they travel on.

## Key invariants

- The wire layer never crashes on unknown data: unknown keys are ignored,
  unknown event/frame/card types fall back to `Unknown*` passthroughs.
- HTTP status is carrier-only; business failures arrive as `ok: false`
  with a typed error code (see `docs/PROTOCOL.md`).
- The mux socket is **bidirectional** — the client opens and cancels
  logical streams on it. (Its two predecessors were downlink-only.)
- Every `/api` request needs a harness browser session; 401 and 403 are
  different facts and are reported separately.
- There is no loopback-only method tier any more: harness 0.1.2 deleted it,
  and one authenticated caller reaches the whole API (see
  `docs/COMPATIBILITY.md`).
- Tool cards are derived in the app from raw call/result data; the host
  sends no render intent.
- Protocol baseline: harness `0.1.2-alpha.1` (`core.DshCore.PROTOCOL_BASELINE`).
