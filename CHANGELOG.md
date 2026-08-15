# Changelog

All notable changes to DSH Mobile are documented here. Format based on
[Keep a Changelog](https://keepachangelog.com/); the project uses SemVer.

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
