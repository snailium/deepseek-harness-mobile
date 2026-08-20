# Security

DSH Mobile is a remote control for the **DeepSeek Harness**. Understand the
trust model before using it.

## The harness has no authentication

The harness web server (`dsh web`) serves plain HTTP with a *trust fence*,
not authentication:

- Every `/api` request is accepted only when its `Host` header is loopback or
  a configured trusted authority (LAN IP literals are auto-derived when the
  server binds `0.0.0.0`).
- There are no tokens, cookies, or TLS. Any device on the same network can
  send requests with a trusted `Host` and drive the agent — including
  running commands on the host computer.

**Consequences:**

- Only bind the harness to `0.0.0.0` on networks you fully trust (home
  network, your own lab). Never on public or guest Wi-Fi.
- DSH Mobile shows a warning banner whenever you connect to a non-loopback
  host.
- Sensitive surfaces (settings, credentials, agent-preset authoring, host
  file pickers) remain loopback-only by harness design and are shown
  read-only over the network.

## Remote connections and edge authentication

The app can connect to any endpoint you enter, not just your LAN. The harness
itself still has no login, so a remote connection is only as safe as the edge
in front of it:

- **Keep the port closed** and publish the harness through a tunnel or reverse
  proxy. A bare `http://` endpoint on the public internet hands agent control —
  including command execution on the host — to anyone who finds it.
- The app supports optional per-host credentials, stored app-private with the
  address and sent on every exchange (POSTs, the session-export download, and
  both WebSocket upgrades):
  - an **access token** (`Authorization: Bearer …`) for a bearer-gated proxy;
  - a **Cloudflare Access service token** (`CF-Access-Client-Id` /
    `CF-Access-Client-Secret`) for a Cloudflare Access application — the
    non-interactive counterpart to the browser login page.
- The first connect to an address outside your local network asks for a
  confirmation; the app re-states it by reporting an edge-proxy refusal
  ("the access service rejected the request") separately from the harness
  trust fence.

## What DSH Mobile stores

- Remembered host addresses (host, port, display name), optional edge
  credentials (access token, Cloudflare Client ID/Secret) and app preferences,
  in app-private storage only.
- No session content is persisted to disk in v1 (chat history lives in
  memory and is re-fetched on connect).
- The app allows cleartext HTTP by necessity (the harness serves plain
  HTTP); see `app/src/main/res/xml/network_security_config.xml`.

## What DSH Mobile connects to

Every connection is to an endpoint you entered or picked from a scan, with
one exception:

- **The update check.** On start the app asks
  `api.github.com` for this repository's latest release, over HTTPS, so it can
  tell you when a newer APK exists. It sends no identifying information beyond
  what any HTTPS request carries, and it is the only request that leaves your
  network. Turn it off in **Settings → About → Check for updates**; nothing else
  in the app contacts anything but the harness and the edge proxies in front of
  it.
- **Scanning** probes only your own device's IPv4 /24, and only with a TCP
  connect followed by the harness's own `host.describe`. Remote hosts are never
  scanned — they are entered manually.

## Reporting a vulnerability

Report security issues privately, either through **Report a vulnerability** on
the repository's Security tab or by email to **sor@zyphite.com**. Do not open a
public issue for a vulnerability. Include what an attacker can do, the steps to
reproduce it, the app and harness versions, and how the app was connected.

The harness having no authentication is the documented model above, not a
vulnerability report worth sending. A way around the trust fence is.

## Roadmap

Upstream harness improvements that would materially harden this setup are
tracked as issues in this repository:

1. An authentication layer (pairing token) on the web server.
2. An explicit `--lan` flag (currently the CLI blocks `--host 0.0.0.0`).
3. mDNS advertisement for zero-touch, authenticated discovery.
