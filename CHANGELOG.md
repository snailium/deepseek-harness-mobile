## [0.17.0] - unreleased

A full visual redesign: the app stops looking like flat iOS Settings and earns a
deliberate DeepSeek identity, the message transcript renders agent markdown
properly, and the home page gets the information architecture it deserves.

### Added

- **Brand-forward theme.** DeepSeek blue as a real identity: gradient primitives
  (heros, buttons, bubbles), elevated `surfaceRaised` cards with soft shadows,
  and new tokens (`assistantCard`, `chipSurface`, `noteSurface`, strong/soft
  dividers) wired through the Ds palette and both Material 3 schemes.
- **Bundled Inter display font** (OFL) for heros, nav titles, section headings
  and markdown headings; the system font stays on body copy for platform
  legibility and RTL coverage. Rebuilt type scale with `brandDisplay` (34sp),
  `title2`/`title3`, and a phone-tuned markdown scale.
- **Real markdown tables.** Agent replies that include tables now render as
  aligned tables with a header band and cell separators (previously raw pipe
  text). Wide tables scroll horizontally.
- **Syntax-highlighted code blocks.** Fenced code is colorised by language
  (kotlin/java, python, typescript/js, json, yaml, bash, sql, xml) using the
  Ds `Syntax*` palette, with horizontal scrolling for long lines.
- **Homepage host status strip.** Connection health (host, connected/reconnecting/
  offline, tap to switch) is always visible on the home page.
- **'Needs your attention' section.** Live sessions awaiting approval, a
  question, or a finished goal are pinned above the chat list with one-tap
  jump-in.

### Changed

- **Home page is now two tabs: Chats + Settings.** The 'Active' tab is removed.
  Per-session live state (approvals, questions, goal, queue, context) lives in
  the chat itself; the session's facts and controls (model, preset, export,
  copy, context, host) moved into a **Session details** screen pushed from the
  chat overflow, with back navigation.
- **Elevated surfaces.** `DsCard`/`DsGroupCard` now float on `surfaceRaised`
  with a soft shadow instead of flat hairline borders; the settings groups and
  details cards inherit it.
- **Gradient actions.** Primary buttons use a brand gradient (new `Large` hero
  size), the segmented control's selected thumb is gradient, the composer's
  send and `+` buttons are gradient, and the user bubble gets a gradient fill.
- **Elevated chat.** Assistant responses render on raised cards; inline `code`
  is a tinted chip; headings use the display voice with proper spacing.

### Fixed

- Markdown robustness: anything the parser cannot handle degrades to plain
  text instead of showing raw markers.
## [0.16.0] - unreleased

The Material 3 standardization continues: the app's chrome now inherits the
DeepSeek palette end-to-end, and the first-run experience and information
architecture get the polish that direction called for.

### Changed

- **Full Material 3 palette mapping.** Every M3 colour role — primary, secondary,
  tertiary, error, surface, surfaceContainer and the inverse/scrim roles — is now
  mapped onto the Ds tokens, so stock M3 chrome (app bars, sheets, dialogs,
  switches, tabs, chips, the bottom navigation bar) inherits the DeepSeek look
  instead of the default purple-scaled palette. Dynamic color continues to
  override only the Material chrome, never the content tokens.
- **One app-bar voice.** New `DsTopAppBar` wraps Material 3's `TopAppBar` with the
  Ds title style, container colour and pinned inset policy; Chats, Active and
  Settings all route through it. Settings' bar no longer scrolls away — it pins
  like the others.
- **Guided first-run connect.** A fresh install (no remembered harnesses) now gets
  an explainer of what DSH Mobile is and a prominent Scan-my-network CTA before
  the manual form, instead of a wall of sections with no orientation.
- **Dismissible security banner.** The connect screen's security reminder can be
  dismissed once read, instead of occupying a permanent strip.
- **Active tab de-duplicated.** The goal and queue cards are gone from Active:
  goal and queue live in the chat dock strip where the turn runs, and Active is
  the session's facts and controls (context, plan mode, jobs, subagents,
  workflows, export, host info).
- **Empty chat list offers a start.** The Chats empty state now carries a New
  session action instead of pointing at the FAB.
- **`DsTextField` primitive.** A shared M3 text-field wrapper with the Ds field
  colours and a configurable min-height, so the compact field policy is one place
  instead of per-call workarounds.

### Fixed

- Settings pushed from Connect (no home shell yet) keeps the status-bar inset,
  so the pinned bar does not draw under the system chrome.
## [0.15.0] - 2026-08-21

The chat screen's hot path is reworked so streaming a turn stays smooth and
long sessions no longer risk running out of memory.

### Changed

- **Transcript folding is now incremental.** A streamed turn used to re-fold the
  whole session log on every display tick (up to 20 times a second), which made
  the cost quadratic in the session's length and eventually exhausted the heap
  on long sessions. The open session's events are now folded through a driver
  that carries its state across events: a burst of chunk deltas re-folds only
  the deltas, and the transcript republishes only when something renderable
  actually changed. History paging, reconnect re-delivery and out-of-order
  events still take the bounded full-refold path, exactly as before.
- **Streaming no longer animates every row.** The transcript gated its per-row
  layout animation behind the running flag: while a turn streams, rows land
  without the animate-item pass (the source of the rubber-band feel); once the
  turn settles, the animation plays once for whatever moved.
- **Tool-result lookup is precomputed.** Chat and trajectory rows used to scan
  the whole node list per tool card per composition to find the matching result;
  the result map is now built once per snapshot.
- **Typing recomposes only the composer.** The composer's draft text moved into
  its own saveable state holder, so a keystroke no longer invalidates the whole
  conversation surface (chrome, transcript, docks and footer).
- **The Active tab's flows are lifecycle-aware.** `collectAsStateWithLifecycle`
  replaces the always-on `collectAsState`, so the details screen stops doing
  work while the app is in the background.
- **The stream frame hot path avoids a JSON round trip.** The raw wire
  `session/event` payload feeds the fold directly instead of being decoded to a
  typed event and re-encoded; the typed path remains as the fallback.
- **Release builds are R8-minified and resource-shrunk.** The release APK drops
  from ~15 MB to ~3 MB, with the code-size and startup benefits that come with
  dead-code elimination. Debug builds are unchanged.

### Fixed

- The app could run out of memory and die shortly after a long session kept
  streaming — the quadratic re-fold described above. The incremental fold keeps
  per-event work bounded, so a session's memory use no longer grows with every
  tick.
- Scrolling back through a long transcript while a turn streams no longer
  rubber-bands: the auto-scroll follows the newest sequence number as before,
  but the settled rows no longer re-animate on every chunk.

## [0.14.0] - 2026-08-21

The app is re-architected onto Jetpack Navigation Compose and re-cut to a
Material 3 information architecture: a bottom navigation bar (Chats · Active ·
Settings) with the chat list as the landing screen, the conversation as a
pushed destination, and the session-details surface consolidated into the
Active tab. Material You dynamic color is now available behind a setting.

### Changed

- **Bottom navigation.** The hand-rolled push stack (MainScreen's
  PushDestination enum and AppRoot's showConnect/showSettings booleans) is
  replaced by one type-safe NavHost: Chats · Active · Settings on a Material 3
  NavigationBar, with Connect and the conversation pushed above it. Back is
  handled by the NavHost, and predictive back is enabled in the manifest.
- **M3 top app bars.** The Chats, Active and Settings tab headers become
  Material 3 TopAppBar surfaces (WindowInsets(0) so the home scaffold's inset is
  not applied twice), replacing the hand-built iOS-style header rows.
- **M3 component migration.** Cards and icon buttons move onto Material 3
  primitives — DsCard/DsGroupCard become a flat Material Card (hairline border,
  zero elevation) and DsIconButton a Material IconButton — alongside the
  existing ModalBottomSheet/DropdownMenu/Switch wrappers. Compact brand-styled
  components (ink buttons, pills, the segmented toggle) stay custom, because
  Material's 40dp minimums would bloat the dense phone layout.
- **Chats is the landing screen.** The session/workspace/subagent list (formerly
  the pushed Sessions screen) is now the home tab; tapping a row pushes the
  conversation (SessionRoute) rather than closing a drawer.
- **Active tab.** The session-details screen becomes the Active tab — the
  mission-control surface for goal, plan, queue, jobs, subagents, context and
  host, and now also for pending approvals and questions, which are never
  missed while the conversation is scrolled away. Reachable from the bottom
  bar instead of a push from the chat.
- **Conversation top bar.** The hamburger becomes a back arrow; the details
  button becomes an overflow (Presets · Subagents · Switch harness).
- **Model picker prominence.** The model selector moves from a small strip
  above the composer to a first-class chip on the conversation top bar, with
  the non-routable warning dot carried over.
- **Notification deep links.** Tapping a notification now opens the exact
  session it was about, on both cold start and warm start (onNewIntent), via
  the session id carried in the intent.
- **Dynamic color.** Settings → Appearance gains "Dynamic color", enabling
  Material You's wallpaper palette on Android 12+; the DeepSeek brand-seeded
  palette remains the baseline (and the fallback below Android 12).

### Verified

- 164 unit tests pass; lintDebug 0 errors; all eleven locales updated with the
  new strings; debug APK assembles.

## [0.13.1] - 2026-08-21

Hotfix for the Sessions screen: every non-selected chat row was showing a
red background at rest.

SwipeToDismissBox composes its swipe background *behind* the row content
at all times — the red Archive fill was never meant to be visible until a
row is actually swiped. The row content had no opaque background of its
own, so the red showed straight through every transparent row. The current
session only looked different because its gray selection fill happened to
cover it.

### Fixed

- The row content now paints the screen background, so the red Archive
  action appears only while a row is being swiped, the way iOS swipe
  actions do.

### Verified

- 164 unit tests pass; lintDebug 0 errors; no new strings.
- Signed release APK (CN=DSH Mobile), SHA256SUMS.txt updated; end-to-end
  verified against the GitHub release.

## [0.13.0] - 2026-08-21

The Sessions screen is rebuilt after a review round: one font scale, one
header line, one search field that is not cut off, and one compose button
floating the way Messages floats it. The five complaints — font chaos,
clipped search, the host switcher overlapping the sort chip, oversized
row titles, and a heavy layout — each get a specific fix, all cited
against the Apple HIG.

### Changed

- **One-line navigation bar.** The 28sp large title and the two-line
  header stack (title, host line, sort chip floating between them) are
  gone. The chrome is now a single iOS bar: back chevron, centered
  "Chats" in the 17sp navigation size, and on the trailing side a compact
  host chip (dot · hostname · chevron — tap for Switch harness /
  Settings) and an icon-only sort button (⇅ — Manual order / Sort by last
  update). Everything shares one baseline, so nothing overlaps or floats
  unaligned.
- **Search capsule rebuilt.** The field was a Material TextField forced to
  44dp while Material's own minimum is 56dp — the text was being clipped.
  It is now a hand-built 40dp iOS capsule (magnifier, clear button, accent
  cursor) with the Cancel button animating in beside it while focused.
- **One list size.** Session titles drop from 17 to 16sp Medium; workspace
  headers stay as proper 13sp semibold iOS section headers. The three-way
  font fight (28 / 17 / 13) is over.
- **iOS selection, not a tint.** The current session row keeps a
  persistent highlight — as a navigation list should — but it is now the
  neutral system-gray selection fill. The accent rail and tinted
  background are gone, and the redundant amber "Needs you" pill is removed
  (the row's status dot already turns amber).
- **Floating compose button.** The full-width bottom toolbar and its
  hairline are deleted; the + button now floats at the trailing bottom
  corner over the list, Messages-style, with the list padded so no row
  hides behind it.
- Dividers are inset to the title's leading edge so they never run under
  the icons.

### Verified

- 164 unit tests pass; lintDebug 0 errors; LocalizedStringsTest green —
  every string already existed in all eleven locales.
- Signed release APK (CN=DSH Mobile), SHA256SUMS.txt updated; end-to-end
  verified against the GitHub release.

## [0.12.0] - 2026-08-21

The two "sidebars" are gone. Apple's HIG is explicit that sidebars don't
belong on an iPhone, so the modal drawer and the edge-swipe details panel
become full-screen pushed screens, the way Messages and Settings navigate:
a Sessions screen over the chat, and a Details screen over the chat.

### Added

- **Sessions screen (was the drawer).** Pushed full-screen from the
  hamburger or the title: back chevron, large title with the host beneath
  it, and a plain hairline-separated list — the iOS list style instead of
  standalone cards.
- **Swipe-to-archive.** A trailing swipe on any session row reveals the
  red Archive action; archiving is irreversible in this UI, so the swipe
  hands over to the same confirmation the context menu uses.
- **Compose toolbar.** A single + button in a bottom toolbar opens the
  action sheet for New Session and New Workspace.
- **Host menu.** The host name under the title is the anchor for Switch
  harness and Settings — the connected-to card and the footer rows are
  gone, replaced by one menu.
- **Search Cancel.** The iOS Cancel button appears beside the search field
  while it is focused and clears the search.
- **Details screen (was the edge-swipe panel).** Pushed full-screen with a
  back chevron; same collapsible cards, export and copy.

### Changed

- The custom right-edge swipe gesture, the drawer scrim and its width
  math are deleted — MainScreen is now a small push stack with
  system-back popping and slide transitions (RTL-mirrored).
- Session rows read as an iOS plain list: full-width hairlines inset from
  the leading edge, the current session tinted with its accent rail.
- The chat keeps every state (scroll, draft, composer) when covered,
  because it stays composed underneath the pushed screens.

### Verified

- 164 unit tests pass (incl. the subagent-tree and session-search suites);
  lintDebug 0 errors; LocalizedStringsTest green — no new strings were
  needed.
- Signed release APK (CN=DSH Mobile), SHA256SUMS.txt updated; end-to-end
  verified against the GitHub release.

## [0.11.0] - 2026-08-21

The whole interface is re-cut to an Apple-grade visual language. The app
keeps every function, gesture and navigation it had, but the surfaces,
type, chrome and controls are rebuilt on iOS design principles: a grouped
gray canvas for support screens with white plates on top, a calm white
chat canvas with filled user bubbles, a large-title navigation row, and an
iOS type scale throughout.

### Added

- **Large-title chat chrome.** The session header renders its title at 28sp
  with the host beneath it, collapsing to the 17sp navigation size on
  scroll — the iOS large-title pattern, reusing the existing fold state.
- **iOS alerts for confirmations.** Destructive and cancel/confirm pairs
  now present as small centered plates with a hairline-separated button
  row; text entry keeps the form dialog.
- **iOS sheets.** Bottom sheets round only the top corners, carry the
  36x5dp grey grabber, and title themselves at 17sp semibold.
- **iOS type scale.** New roles — large title, nav title, row title, body
  17, footnote 13 — with the app's densest captions stepped up to it.

### Changed

- **Two-canvas surfaces.** The chat keeps its white (light) / black (dark)
  canvas via a new `bgChat` token; connect, drawer, settings and the
  details panel sit on the iOS grouped gray with white cards on top.
- **Filled user bubbles.** The user's messages are now solid brand-blue
  bubbles with white text — the iOS 18 Messages arrangement — replacing the
  tinted fill; the assistant keeps its grey card, so the two sides of the
  conversation read instantly.
- **Inset-grouped lists.** The chat drawer, settings groups, connect
  sections and the details panel become grouped white plates with hairline
  borders on gray; session rows step up to 17sp titles with 13sp
  subtitles.
- **Quieter composer.** Softer shadow, 36dp send/stop circles and 17sp
  input text on the same floating card.
- **iOS search field** in the drawer: a compact grey capsule with no
  underline, the placeholder doing the labelling.

### Verified

- 164 unit tests pass; lintDebug 0 errors; LocalizedStringsTest green.
- Signed release APK (CN=DSH Mobile), SHA256SUMS.txt updated; end-to-end
  verified against the GitHub release.

# Changelog

All notable changes to DSH Mobile are documented here. Format based on
[Keep a Changelog](https://keepachangelog.com/); the project uses SemVer.

## [0.10.2] - 2026-08-21

The model picker leaves the top bar for the composer. A model choice
configures the next turn, so the trigger now sits above the input where the
choice takes effect — the same pattern Gemini's 2025 prompt-bar redesign and
ChatGPT's composer use — and the header's identity row is left with identity
only.

### Changed

- **Model selector above the input.** A text-style trigger with a chevron
  sits at the top of the composer card and opens the model sheet; the
  non-routable warning dot carried over. The header no longer carries the
  model pill, and the session title regains the space it used.

## [0.10.1] - 2026-08-21

The header's third row disappears: the session-meta chips and the Chat /
Trajectory switcher now share one utility line, so the chrome stops at two
rows and the transcript starts ~40dp higher.

### Changed

- **One utility row.** The agent preset and subagent chips ride the trailing
  space of the tab row instead of owning a row of their own. The Chat /
  Trajectory switcher never folds — switching views is navigation, not
  configuration — while the chips still fold away once the reader scrolls.
- **Chips scroll instead of wrapping.** A long preset label can no longer
  push the chips onto a second line; the strip scrolls within its own row.

## [0.10.0] - 2026-09-02

A pass over the chat surface itself: the composer stops being the biggest
thing on screen, a turn's thinking and tool calls fold into one disclosure
that collapses when the work is done, and the transcript and header earn
better reading contrast and a tighter chrome.

### Added

- **Process rows.** Consecutive reasoning blocks and their tool calls now
  render as a single disclosure — "Thinking" with a shimmer while the turn
  streams, "Thought · N tool calls" once it settles. One tap reveals the
  whole stretch of work, and the group folds itself back to the summary when
  the turn moves on, unless you opened it by hand. (The old layout gave the
  thinking and every tool its own disconnected chevron, so expanding the
  thinking never showed the tools that followed it.)
- **Send on the keyboard.** The composer's IME action is Send, so the
  keyboard's send key fires the same path as the button.

### Changed

- **The composer is a field, not a slab.** The message input drops Material's
  56dp minimum-height field for a compact one (single line ≈ 38dp), caps
  growth at five lines, and pins the send button to the field's last line;
  the permission chip and context meter move to a slim second row that only
  appears when the harness offers them. The card sits ~66dp at rest instead
  of ~148dp.
- **The header is two rows, not three.** Title and host move up to the
  always-visible row next to the model chip (which drops its inline reasoning
  effort — it lives in the model sheet), and only the preset / subagent chips
  fold away on scroll.
- **Readability.** The assistant bubble steps one rung off the page
  background in both themes with a stronger hairline, code and tool panels
  get the same treatment, and assistant bubbles are width-capped like user
  bubbles so lines stay in the comfortable band on wide screens. Disclosure
  rows grow to ~44dp tap targets.

## [0.9.0] - 2026-09-02


A pass over the two sidebars: the chat list drawer gets the anatomy a chat list
is expected to have, and the details panel gets chrome that stays put.

### Added

- **Search is always there.** The chat-list drawer keeps a permanent search
  field under its title (with a clear button) instead of hiding one behind an
  icon — the field a chat-list app's eye looks for first is no longer a
  discoverability puzzle.
- **The current session's actions are visible.** The row you are in carries a
  quiet `⋯` that opens the rename / fork / archive sheet, so the most-acted-on
  row no longer hides its verbs behind long-press. Long-press still works
  everywhere, now with haptic feedback and a TalkBack label of its own.
- **Settings moves to the drawer's footer.** The M3 convention of secondary
  destinations at the bottom frees the header for what the drawer is actually
  for, next to the existing "New workspace" row.
- **Workspace headers identify themselves.** A folder glyph leads each
  workspace header, and every drawer row — header and session alike — grows to
  a 48dp touch target.

### Changed

- **The details panel's header stays pinned.** The back arrow and title no
  longer scroll away with the cards; the panel's leading edge gains a hairline
  so it reads as a sheet over the chat in light mode; card expansion resets
  when you switch sessions.
- **RTL-correct details panel.** The edge swipe, its drag direction, and the
  slide animation now follow the reading direction instead of assuming the
  panel lives on the right.

## [0.8.0] - 2026-08-21

A UI pass over components and chrome: one icon family everywhere, strings that
actually translate, thumb-sized touch targets, and a transcript that gives its
chrome back to the reader.

### Added

- **Markdown links open.** A `[link](https://…)` in an assistant message is now
  tappable (accent + underline) and opens in the browser — http(s) only, so a
  harness reply cannot smuggle another scheme out. The URL gate is unit-tested.
- **Scroll-aware chrome.** Scrolling the transcript folds the session-meta row
  of the top bar away (title, preset and subagent chips); returning to the top
  brings it back. Programmatic scrolling — session open, paging, tail-follow —
  never counts, so the bar only folds when a reader actually scrolls.
- **Message actions are findable.** The newest assistant message carries a quiet
  `⋯` affordance for the copy / branch / feedback row, and long-pressing any
  bubble opens the row directly.
- **Toned toasts.** Toasts carry an icon and a tone (info / success / error),
  slide in and out, and errors hold a moment longer than the rest.
- **Icon unification on Feather.** All 37 previously Material glyphs (settings,
  arrows, chevrons, thumbs, copy, shield, users, …) are now Feather, ported onto
  the same 24-unit grid as the rest of the set. Directional glyphs mirror under
  RTL. `material-icons-extended` is dropped from the dependency graph.
- **Localization guard test.** UI sources are scanned for sentence-like string
  literals, so the review gap that let tool-card copy ship untranslated cannot
  reopen silently.

### Changed

- Tool card summaries and section labels (`IN`/`OUT`/`LOCATIONS`, block and
  file counts, `killed by`, `exit`, `HTTP`, `showing N of M`, `+N more`) now
  resolve through string resources in all 11 locales, with proper plurals.
- Touch targets: menu anchors, message actions, attachment remove, the composer's
  `+` / send / stop, the session-tree chevron, section-header actions, and
  tappable pills all grew toward thumb-sized hit areas.
- Selection and button semantics: model / preset / permission / plugin chips and
  question options announce as buttons or radio/checkbox choices to TalkBack.
- `labelCaption` steps one rung darker in the light theme (~2.4:1 → ~3.0:1) and
  danger surfaces (Danger buttons, the error banner) use a darker fill, both
  documented divergences for phone readability.
- `ConnectionBanner` swaps its warning triangle for a refresh glyph on the
  reconnecting tone, and its action is a compact text target instead of a
  40dp-tall button.
- Settings groups render on the same hairline-bordered card as the rest of the
  app — in the light theme the old fill-only card was invisible against the page.
- The trajectory ledger no longer shows a call as `running` once its turn has
  ended; only a live turn earns the chasing dot.
- The details panel caps at 88% of a narrow screen instead of a fixed 300dp; the
  model chip in the top bar ellipsizes instead of overflowing; dialogs cap at
  560dp on tablets.
- Text-field styling is one shared helper instead of three copies.

### Removed

- `StatsLine` and its helpers (dead code).
- The stale `screen.png` at the repo root.

## [0.7.0] - 2026-08-21

The transcript stops looking like a console log and starts looking like a chat.
Assistant turns now render as left-aligned cards with a "DS" avatar chip, the
same way user turns already rendered as right-aligned bubbles; a typing
indicator with three animated dots shows while a turn streams; the composer
floats above the transcript with a soft shadow; and message rows get real
breathing room. Nothing about the underlying conversation changed — this pass
is purely visual, and the harness web UI's container-less assistant turns are
deliberately diverged from here, the same way `userBubble` already was.

## [0.6.0] - 2026-08-21

A pass over the screens you touch every day, aimed at the two questions a phone
user keeps asking: *what will this connect to?* and *where am I right now?* The
connect form now takes one address instead of three fields and says what it will
do with it before you tap Connect; the chat chrome names the harness you are on,
the session title opens the chat list, and a dropped connection offers a Retry
instead of an unanswerable red strip.

### Added

- **One address field.** Scheme and port are derived from what you type —
  `ds.example.com` means HTTPS on 443, a bare IP means the harness's own
  http on 3080, and a pasted full URL wins outright — with an **Advanced**
  section to override either. A live caption states the effective endpoint
  before Connect.
- **Authentication (optional) section.** The Bearer token and Cloudflare
  Access fields are tucked behind one disclosure instead of always filling
  the form.
- **Edit on a Recent host.** The manual form re-fills with that host's
  address, scheme, port and credentials, so a mistyped secret is corrected
  without deleting the host.
- **Resume card.** With a remembered host, the connect screen leads with a
  one-tap Connect button for the most recent one.
- **Where am I?** The chat top bar shows the connected harness under the
  session title; the chat-list drawer names it too, with a **Switch** button
  that returns to the connect screen without the Settings detour. The session
  title itself opens the chat list.
- **Connection notices split.** A hard failure gets the red strip plus a
  **Retry** button (drops the backoff wait and re-handshakes); routine
  reconnecting is the calm blue notice.
- **Suggestion chips** on an empty transcript — tap one to prefill the
  composer.
- **Help card in Settings** linking to the wiki's connecting guide and the
  issue tracker.
- **Status dots speak**: the running/online/offline dots now carry a content
  description for screen readers.

### Changed

- The connect screen's redundant **Auto-connect toggles** moved out; they
  live in Settings only. The security banner is calmer and info-toned.
- The hardcoded "Preview" pill is gone from empty states (it was never
  translatable).

## [0.5.0] - 2026-08-20

DSH Mobile learns to reach a harness that is **not on your Wi-Fi**. The connect
screen no longer assumes a LAN: you can enter any host (IP or domain), choose
`http` or `https`, and — for a harness published behind an edge proxy — set the
credentials that proxy expects. The port can stay empty to use the protocol's
default. Connecting to an address outside your local network asks for one
confirmation first; after that the attempt, the failure diagnosis, and the
connected experience are exactly what a LAN connection gives you.

### Added

- **Manual connect to any address.** The local-subnet gate is gone: a public IP
  or a domain like `ds.example.com` is probed instead of being rejected as "a
  different network". A scheme toggle (HTTP/HTTPS) sits above the host/port
  fields, and a pasted full URL in the host field sets the scheme for you.
- **Optional per-host credentials**, stored app-private with the address and
  sent on every exchange — the unary POSTs, the session-export download, and
  both WebSocket upgrades:
  - an **access token**, sent as `Authorization: Bearer …`;
  - a **Cloudflare Access service token**, sent as
    `CF-Access-Client-Id` / `CF-Access-Client-Secret` — the supported way for
    an app to pass a Cloudflare Access application without a browser login.
- **Remote-connect confirmation.** The first manual connect to an address that
  is not an IPv4 literal on this phone's own /24 pauses for a one-time
  "this is outside your local network" confirmation; the choice is remembered
  per endpoint. LAN hosts and loopback are unaffected.
- **Edge-proxy diagnosis.** A refusal that carries a `WWW-Authenticate`
  challenge — Cloudflare Access answering 302/401/403 — is reported as
  "the access service rejected the request — check the token and the Cloudflare
  Client ID/Secret", instead of a bare "carrier returned HTTP 302" or a
  trust-fence hint. The harness's own 403 trust fence keeps its own message.
- **Scheme-aware display.** Remembered and connected hosts show an `https://`
  prefix when they are https.

### Changed

- Remembered hosts are probed (and auto-reconnect re-checks them) with their
  own stored scheme and credentials, so a remote host's Recent card shows its
  real liveness instead of an always-off dot.
- The connect-screen security banner now says the harness itself has no login
  and recommends protecting remote access with a token, VPN, or Cloudflare
  Access.

### Removed

- The "not on this phone's network" hard failure (`connect_fail_subnet`) and
  its `DifferentSubnet` diagnosis — remote addresses are now attempted, with
  confirmation, rather than refused.

## [0.4.0] - 2026-08-18

Two threads run through this release. The first is the question the agent asks
you: the card it arrives in can now be folded away while you decide, and — less
happily — the answer you type into it now actually reaches the model, which until
this release it did not. The second is chips, which turn out to have been
invisible on a light background since the beginning: the reasoning tiers in the
model picker, and every other plain chip in the app.

### Fixed

- **Every free-text answer this app has ever sent was discarded in transit.** The
  harness puts `custom` on the answer it belongs to; this client wrote it one
  level out, beside the list of answers rather than on one of them. The host
  parses that payload with a schema that *strips* keys it does not declare rather
  than objecting to them, so nothing failed: the answer went out, came back
  accepted, and reached the model with the typed text simply gone. Nobody could
  have noticed from this end. The batch is now built from a type whose shape is
  the wire's, and the mock harness enforces the host's real acceptance rules
  rather than acknowledging whatever arrives — which is what would have caught it.
- Even had it arrived, only one of them would have. A batch carried a single
  `custom` for all its questions, taken from whichever one happened to be
  answered first, so a second free-text answer overwrote nothing and went
  nowhere. Each question now carries its own.
- Paging back to check an earlier answer showed it blank, and paging forward
  again overwrote the real one with the blank. The panel kept its selection
  keyed on the page number, so leaving the page discarded it; the batch is now
  one list of drafts that paging only moves a cursor through.
- A batch that contained a plan review **alongside other questions** answered
  only the plan review and left the rest unsent. The host compares an answer
  batch against the request it resolves and refuses one of a different length
  outright, so the response was rejected, the harness's wait stayed open, and the
  `ask_user_question` call never unblocked — a hung session with nothing on
  screen to explain it. The decision card now claims a request only when it can
  answer all of it: one question, a plan to show, a binary choice, and an approve
  label naming a real option. Everything else takes the ordinary flow, where
  every answer is still reachable.
- Dismissing a question was not a dismissal. **Cancel** answered every question
  with an empty selection, which is a perfectly valid answer that the model reads
  as "no preference". It now fails the request the way the harness's own client
  does, and the host settles the tool call as cancelled.
- **Chat about it** on a plan review answered *Decline* and then cleared the
  draft, which told the agent something you had not said. It dismisses the
  request instead — wanting to talk it over first is not one of the options on
  offer. A plan review that offers no second option no longer draws a Decline
  button that had nothing to send.
- An option the model marks as its recommendation arrives with `(Recommended)`
  appended to the label — the tool's own schema tells it to write that — and the
  card showed the marker as part of the choice. It is now a badge beside the
  label, in both the English and Chinese forms and both widths of parenthesis,
  while the wire keeps the label whole, because the host checks a selection
  against the labels it sent.
- A question's supporting detail rendered as plain text, so a plan or a table in
  it arrived as markup.
- **Every plain chip in the app was invisible in light mode.** `DsPill` fills
  itself with `bgLayer2`, faithfully to the harness — but in the harness's light
  theme `bg-base` and all three `bg-layer` rungs are the same pure white, so a chip
  on any of them is white on white. The web never notices: `:hover` paints the chip
  the moment a pointer nears it. A touchscreen has no pointer to near it with, so
  the model and preset triggers in the details panel, the subagent counts in the
  drawer, the goal phase, the workflow status and the suggestion chips on the empty
  session were all just runs of grey text, two of them tappable with nothing to say
  so. Chips now rest on `bgModulePlatform`, which steps off every surface in both
  themes; a chip that does something takes a hairline as well, that being what is
  left to distinguish a trigger from a badge once both have a fill. This is the
  third time this app has had to relearn that a hover-revealed affordance is an
  invisible one — the chat-bar chips and the disclosure chevron were the first two.

### Added

- **The question card folds up.** A chevron in its header collapses it to the
  title strip, so you can read the conversation you are being asked about and
  then come back to it; the draft, the choice and the position in the batch all
  survive. This is the harness's own rc.7 addition, and it earns its place twice
  over here: the web card replaces the input bar in a fixed-height column, while
  this one sits between the transcript and the composer, where a question with a
  long detail and six options otherwise buries everything above it.
- Options are numbered on a single choice and carry a check box on a multiple
  one, so which kind of question you are looking at is visible before you tap.
- The card says when an answer is incomplete, and jumps to the question that is
  missing, rather than silently submitting empty answers. If the harness refuses
  the answer outright it now says so; before, the card simply stayed put.

### Changed

- The model picker reads as a set of choices rather than a list of words. Each
  model is a card now, the live one carrying the accent wash and border instead of
  only a blue name and a tick stranded at the far edge, and the reasoning tiers sit
  **inside** that card under a label that says what they set. They used to appear
  under every model in the list — a dead control beneath each row nobody had
  chosen, tripling the height of the sheet — and they were drawn as pills whose
  unselected fill is `bgLayer2`, which is the sheet's own colour, so three of the
  four tiers were invisible and the row read as a caption rather than a control.
  The tiers now use the segmented track the Chat / Trajectory tabs already use,
  lifted into `DsSegmented` so there is one such control rather than two. It gained
  an outline on the way: the track's fill is a step off `bgLayer1`, but in dark mode
  it is the *same* colour as `bgLayer2`, so on a sheet the fill alone showed nothing.
- The card no longer grows without limit. It takes at most a fixed share of the
  column and scrolls its options inside that, keeping the header and the actions
  reachable. It was previously measured before the composer below it, so a long
  batch could push the composer off the bottom of the screen entirely.
- Protocol baseline moves to harness **0.1.0-rc.7**. Nothing on the wire moved
  between rc.5 and rc.7 — no method, no event type, no projection key, no slash
  command — so this is a re-verification rather than a migration. The one label
  that did change: the `code` agent preset is **PTC mode** in English now, as it
  already was in Chinese. The preset's id is untouched.
- `docs/COMPATIBILITY.md` stops claiming the app compares the harness's version
  against the baseline and warns on a mismatch. It never did, and it should not:
  the harness releases far more often than this client and nearly always without
  touching the client surface, so the warning would fire on almost every session
  while still saying nothing about the changes that matter. The document now
  describes what the app actually relies on, which is degrading on shape.
- `docs/PROTOCOL.md` records how a question request is settled, including the
  rules the host checks an answer against. It is the one shape in this protocol
  where getting it wrong is silent.

## [0.3.1] - 2026-08-17

### Fixed

- Only the first button in any row was drawn. `DsButton` laid its content out
  with `fillMaxSize`, so the content claimed the whole width on offer and took
  the button with it, leaving nothing for whatever came next — the details
  panel showed **Rename** but not Fork or Archive, the export row showed
  **Download session log** but not Copy, the disconnect dialog showed no
  Cancel, and the update dialog added in 0.3.0 showed no **Later**. The content
  now fills only the height; a button that wants to span its parent still says
  so through its own modifier, as several already did.

## [0.3.0] - 2026-08-17

The theme of this release is the difference between a control that exists and a
control you can find: a scan that finishes, a search that answers, a session list
you can navigate, and buttons that look like buttons. It also fixes a crash that
took the app down on any session with a long log.

### Added

- Settings gained a **Plugins** section: one row carrying the count, opening a
  sheet that lists the harness's composed plugins by short module name with
  their enabled state and mount phase, the raw loader entry id behind a
  disclosure, and a filter. A sheet rather than an inline list because a real
  deployment mounts a hundred and fifty of them, which no settings page should
  try to hold. Read-only, because that is the whole of what the harness offers a
  client — `pluginInventory/list` has no counterpart that changes anything, and
  the `settings.*` calls behind the web UI's plugin configuration are
  loopback-pinned and answer 403 over a network.
- The app offers a new release when GitHub has one: a dialog naming the version,
  a link to the release page, and nothing else — it cannot install anything
  itself. Offered once per release; declining it stays declined until a later
  one appears. This is the only request the app makes to anything other than the
  harness, so Settings → About can switch it off.
- Subagent sessions nest under the session that spawned them in the chat list,
  each parent collapsible and carrying a count, to whatever depth the run went.
  They were previously dumped into one flat "Subagents" heading per workspace,
  which said nothing about which run produced which.
- The details panel can now change the model and the agent preset, and shows
  the current model at all.
- A sweep can be cancelled while it runs, and hosts appear as they are found
  rather than all at once when it finishes.
- A harness that is running but rejects this device is now listed and explained
  rather than dropped, since it is the most recoverable thing a scan can find.

### Changed

- **Scan network** is roughly an order of magnitude faster. The sweep now knocks
  each address with a bare TCP connect and only pays for `host.describe` where a
  socket opens, with a flat 128-wide fan-out over every address/port pair. It
  previously sent a full HTTP request to all 254 addresses, tried known ports in
  series, and synchronised every 32 probes so each batch cost its slowest member
  — the better part of a minute on one port, and minutes across several.
- The model, preset and subagent chips in the chat bar are drawn as pills rather
  than bare text. The harness's own triggers are transparent because they have a
  hover state; a touch screen does not, so nothing indicated they were tappable.
- The session-order control names the order it is in and offers the other one,
  instead of being an unlabelled ⇅ icon. The choice now persists.
- Plan mode is a labelled switch in the details panel rather than a card whose
  title stated one state and whose button stated the other.
- Loading earlier messages no longer fights the reader. Two things were wrong:
  decoding a page and re-folding the transcript ran on the main thread, because
  the call was launched from a composition scope and nothing moved it off; and
  the auto-scroll was keyed on the *item count*, so a page arriving at the top
  threw the view down to the newest message — the opposite of what asking for
  older messages means. The work now runs on a background dispatcher, and the
  scroll follows the newest `seq` instead, so only growth at the tail moves the
  view.
- The language picker is a dropdown instead of a grid of twelve cells. The grid
  spent four rows of the settings page on a choice made once, and at three per
  row the longer endonyms had to be ellipsised — so it was both the largest
  thing on the screen and unable to spell out its own options.

### Fixed

- The app ran out of memory and died shortly after opening a session with a long
  log. Two causes, both of which grew with the length of the session:
  - The transcript pulled history without limit. Automatic paging ran while the
    list was shorter than the screen, but a page is counted in *events* and most
    events — chunk deltas, tool traffic, turn boundaries — render nothing, so a
    session whose log is mostly machinery never filled the screen however much
    was loaded. It pulled four thousand events at a time until the heap gave
    out. The fill is now worth one page, after which the head of the list offers
    to fetch more; scrolling to the top still pages back as far as wanted.
  - Every streamed event re-folded the whole transcript and republished it. A
    turn arrives as a long run of deltas, so this was quadratic in the length of
    the session and allocated hundreds of megabytes a second. Rebuilds are now
    coalesced to one per display frame, and an in-order event no longer re-sorts
    the event list.
- Changing the app language flashed a black screen. Applying a locale is a
  configuration change, and the default response is to destroy and rebuild the
  activity — between the two there is no window at all, so the screen showed
  what is behind one, which is black. `MainActivity` now declares
  `configChanges="locale|layoutDirection"`, so the framework delivers the change
  instead of tearing the activity down: Compose re-reads its resources, the text
  swaps in place, and the transcript and scroll position survive. Verified by
  sampling frames through a switch — the frame that used to come back pure black
  no longer occurs, and right-to-left still mirrors correctly in Arabic.
- Two smaller things the same investigation turned up, both of which would have
  shown as a flash of the wrong colour once the black one was gone:
  `android:windowBackground` was transparent and the launch theme's background
  was a hardcoded white, and both now use one token with a `values-night`
  variant; and that token resolved against the *device's* dark-mode setting
  rather than the app's own Appearance, so an app set to Dark on a light phone
  had a white window behind it. The scheme is now applied to the resource layer
  from `Application.onCreate`, where it costs no extra activity restart.
- The chat bar named the session's agent preset with its raw wire id
  (`standard`) rather than a readable name, because the preset roster is
  host-scoped and nothing fetched it until the chip was tapped. It is now
  fetched on connect, and a shipped preset id resolves to its localized name
  even before the roster lands.
- Search did nothing. Its only source of results was `session.search`, which is
  full-text over message *content* and is off in the shipped harness
  configuration (`session-query-sqlite` at `openAt: never`) — so the call failed,
  the drawer swallowed the error behind itself, and the list never changed.
  Session titles and workspace names are now matched locally, as the harness's
  own sidebar does under the same configuration, with content hits merged in
  where the host provides them. When content search is unavailable the drawer
  says so once, quietly, instead of failing.
- Built-in agent presets displayed in Chinese whatever language the app was set
  to. The harness reads their names from `preset.yml` files written in Chinese
  and its web client overrides them with its own translations; this client
  trusted the wire name. The four shipped presets now read Standard / Code /
  Minimal / Creator mode in all eleven languages.
- The per-app language did not reach bottom sheets and dialogs on Android 12 and
  below. The app manages its own locale storage but only applied it after
  `onCreate`, by which point windows built from an earlier context had already
  taken the device language. AppCompat's `autoStoreLocales` now restores it in
  `attachBaseContext`, and `android:localeConfig` declares the shipped set.
- Plan mode could be turned on but never off: both directions of the toggle sent
  `/plan`, which only ever enters plan mode. Leaving requires `/plan off`.
- The user message bubble was still hard to see. It now sits a step darker than
  the web token with a stronger edge, and its width tracks the screen the way
  the harness's `min(525px, 82%)` does rather than a flat 320dp.
- A malformed session lineage could make a subagent its own parent, rendering
  neither it nor its children.

### Security

- `docs/SECURITY.md` gained a "What DSH Mobile connects to" section. The update
  check is the first request the app makes to anything other than the harness,
  so the document no longer claims every connection is a user-initiated LAN
  endpoint, and it names the switch that turns the check off.

## [0.2.0]

### Added

- History pages itself: scrolling back through a transcript fetches the next
  page automatically instead of asking for a tap, and a session that opens on
  fewer messages than the screen holds keeps pulling until it is full.
- "Connect manually" reports what it is doing — checking the address, reaching
  the host, opening the event streams, verifying the harness — rather than
  greying the button out and saying nothing.
- A failed connection now names its cause and the fix: a dropped connection
  (firewall or router client-isolation), a refused one (harness still bound to
  loopback), a trust-fence rejection, a name that does not resolve, a port
  serving something that is not a harness, or an address outside the phone's
  own subnet — which is checked before probing, and also explains why
  **Scan network** finds nothing.
- `harness/README.md` gained a Troubleshooting section covering each of those,
  including the Windows firewall rule and how to confirm the harness is bound
  to `0.0.0.0` rather than `127.0.0.1`.
- Cancel a connection attempt that is backing off and retrying.

### Fixed

- User messages rendered as plain text. The bubble was drawn every time and was
  invisible: its fill sits at a 1.06:1 contrast ratio against the white
  transcript background. It now carries a hairline border in both themes.
- A failed connection left the Connect button disabled indefinitely with no
  error. The failure watchdog polled for a connection phase the loop leaves
  within milliseconds of starting, so it could never fire.
- The connect pre-flight probe advertised a 700 ms budget that the transport
  discarded, so a manual connect could block for 30 seconds — and a subnet
  sweep for minutes — before reporting anything.
- A trust-fence rejection (HTTP 403) was reported as "could not reach a
  harness", sending people after a network problem while the harness was
  running and healthy. Rejections of the WebSocket upgrade were likewise
  unclassifiable.
- The address typed into the manual fields was lost on rotation.
- A validation failure reported the empty field rather than the address tried.
- A user turn whose content arrives as a bare string, or in a block kind this
  client does not recognise, no longer disappears from the transcript.

## [0.1.0] - unreleased

Initial release.

### Added

- Connection to a DeepSeek Harness (v0.1.0-rc.5) over the web `/api` protocol
  (HTTP unary + dual WebSocket event streams, reconnect with backoff).
- Discovery: manual host entry, active Wi-Fi subnet scan, remembered hosts,
  loopback (same-device) connection, auto-connect toggles.
- Discord-style navigation: swipe from the left edge opens the workspace-
  grouped chat list; right-edge swipe opens the session details panel.
- Chat: streamed turns, reasoning disclosure, markdown, tool cards
  (terminal/diff/read/search/web/generic), queue dock (edit/remove/steer),
  history paging, image attachments.
- Feature modules: goals, plan mode + plan review, approvals, user
  questions, todo dock, subagents, background jobs, workflow runs, skills,
  model selection, agent presets, settings (read-only over LAN), trajectory
  ledger, session export, message feedback.
- Notifications: turn complete, goal complete/blocked, review/question
  requested; foreground service for background connection.
- DeepSeek Harness visual design system (colors, typography, radii,
  components) with light/dark/system themes.
- Localization: en, zh-Hans, hi, es, fr, ar, bn, pt, ru, ur, th (RTL aware).
- Harness-side LAN companion (`harness/`) and developer tooling
  (`mock-harness/`, `tools/capture/`).