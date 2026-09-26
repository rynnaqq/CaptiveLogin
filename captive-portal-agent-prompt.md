# AI Agent Prompt — Rooted-Android Hotspot Captive Portal App

> Copy everything below this line and paste it into your AI coding agent (Claude Code, Cursor, Windsurf, etc.).

---

## 0. ROLE & MISSION

You are a senior Android systems engineer specializing in networking, root-level tooling, and Wi-Fi tethering internals. Build a complete, production-quality **native Android app in Kotlin** called **HotspotPortal**.

**What the app does:** on a **rooted Android phone**, when the phone's Wi-Fi hotspot is enabled and any device (laptop, phone, tablet) connects to that hotspot, the connected device is automatically shown a **captive portal login page** served by the phone itself. The connected device **cannot access the internet until it logs in with a valid username + password** from a local user list managed inside the app. After successful login, that device gets full internet access until its session expires or an admin kicks it.

Everything runs **100% offline on the phone** — no external servers, no cloud, no analytics.

---

## 1. TARGET ENVIRONMENT & HARD CONSTRAINTS

- **Device:** rooted Android phone (Magisk or KernelSU providing `su`), ARM64.
- **SDK:** `minSdk 24` (Android 7.0), `targetSdk 34`, `compileSdk 34`, JVM 17, Kotlin (latest stable), AGP 8.x.
- **Build system:** single-module Gradle project with **Kotlin DSL** (`build.gradle.kts`, version catalog optional). `./gradlew assembleDebug` MUST succeed.
- **Root access:** use **libsu** (com.github.topjohnwu.libsu) for all privileged operations. Never shell out via `Runtime.getRuntime().exec("su")` string concatenation — use libsu's array-based exec to avoid injection.
- **Allowed dependencies:** Jetpack Compose (BOM), Material 3, libsu, NanoHTTPD (embedded HTTP server), kotlinx-coroutines, Room (users/sessions), DataStore/SharedPreferences (settings), BCrypt (jBCrypt or similar), kotlinx-serialization (optional). Keep the APK under ~8 MB. **No** Firebase, no Retrofit, no internet-capable SDKs.
- **Do not use hidden/restricted Android APIs** as a hard dependency; hotspot detection must work with public info + root shell (details in §5).

---

## 2. PRODUCT BEHAVIOR (USER STORIES)

1. **Admin** opens HotspotPortal → sees dashboard → enables hotspot themselves (or it's already on) → taps **"Activate portal"**. The app detects the hotspot interface, starts the portal engines, and installs firewall rules. A persistent notification shows portal state + connected client count.
2. **Guest** connects to the hotspot. Within seconds their device pops up the OS captive-portal window (Android: "Sign in to network"; iOS/macOS: captive network assistant; Windows: auto-opened browser) showing the login page hosted on the phone.
3. **Guest** enters a username + password issued by the admin. If valid → device is whitelisted and gets full internet; the portal window closes itself on each OS. If invalid → error + remaining attempts.
4. **Admin** opens the **Clients** tab → sees every device (IP, MAC, state: Blocked / Logged in, time remaining) → can manually authorize, kick, or revoke.
5. **Admin** opens the **Users** tab → creates/disables/deletes user accounts, sets per-user device limits and optional expiry dates. Passwords stored hashed.
6. **Admin** stops the portal or reboots the phone → all firewall rules created by the app are cleanly removed. The phone's networking is NEVER left broken.

---

## 3. SYSTEM ARCHITECTURE

One foreground **`PortalService`** hosts all engines:

```
┌────────────────────────────── PortalService (foreground, START_STICKY) ─────────────────────────────┐
│                                                                                                      │
│  HotspotDetector ──► RootShellManager (libsu) ──► FirewallManager (iptables/ip6tables chain owner)   │
│        │                                                                                             │
│        ▼                                                                                             │
│  DnsInterceptor (UDP:5353, answers every A query with gateway IP, empty AAAA)                        │
│  PortalServer (NanoHTTPD on 8080: probe router + portal pages + login API)                           │
│  TlsResetter (raw ServerSocket on 8443: accept TLS then immediately close → fast probe failure)      │
│  ClientMonitor (polls `ip neigh` / /proc/net/arp → IP↔MAC map, presence/liveness)                    │
│  SessionManager (expiry timer, idle revoke, per-user device limits)                                  │
│  AuthStore (Room: users w/ BCrypt hashes) + SessionStore (active MAC↔user↔token)                     │
│                                                                                                      │
└──────────────────────────────────────────────────────────────────────────────────────────────────────┘
        ▲
        │ (Bound / ViewModel + StateFlow)
┌───────┴──────────────────────────────────────────────────────────┐
│ Compose UI: Dashboard • Clients • Users • Settings • Logs        │
└──────────────────────────────────────────────────────────────────┘
```

The flow for a new client:
```
client connects → hotspot DHCP gives phone as gateway+DNS
  → client DNS probe  (udp/53)  ──REDIRECT──► DnsInterceptor:5353 → answers "portal IP = phone"
  → client HTTP probe (tcp/80)  ──REDIRECT──► PortalServer:8080 → HTTP 200 + portal HTML (NOT 204)
  → OS sees 200 instead of 204/success → opens captive portal window on the client
  → guest POSTs /api/login → AuthStore validates → FirewallManager inserts MAC whitelist rule
  → probes now return "online" responses → OS marks network online, portal window closes
```

---

## 4. CAPTIVE PORTAL TRIGGER — EXACT PROBE HANDLING (IMPLEMENT PRECISELY)

All client DNS is hijacked (§5), so every probe URL resolves to the phone. `PortalServer` must route by **request path and return body** exactly as below. Serving 200-with-HTML *instead of* the expected "I'm online" response is precisely what makes each OS pop its captive-portal window. **Any other GET on port 80 (any Host header) → 302 redirect to `/` (the portal page).**

| Client OS | Probe path (any host) | Unauthorized (trigger portal) | After login (mark online) |
|---|---|---|---|
| Android 9+ | `/generate_204` | `200` + portal HTML (**never** 204) | `204 No Content` |
| Android ≤8 | `/gen_204`, `/generate_204` | `200` + portal HTML | `204 No Content` |
| iOS / iPadOS / macOS | `/hotspot-detect.html` | `200` + HTML that does **not** contain the string `Success` | `200`, body exactly `Success` |
| Windows 10/11 | `/connecttest.txt` | `200` + portal HTML | `200`, body `Microsoft Connect Test` |
| Windows (legacy) | `/ncsi.txt` | `200` + portal HTML | `200`, body `Microsoft NCSI` |
| Firefox | `/success.txt` | `200` + portal HTML | `200`, body `success` |
| Ubuntu/Linux | `/` on `connectivity-check.ubuntu.com` | `200` + portal HTML | `204 No Content` |

Rules:
- Match these paths on **any** Host header (DNS hijack makes hostnames irrelevant).
- After login, the client's **next** probe must return the "online" response → OS closes the portal window automatically. On the post-login "You're connected" page, immediately `fetch('/generate_204')` or `window.location = '/generate_204'` (and for Apple UAs `/hotspot-detect.html`) so the mini-browser closes itself.
- Detect client OS for this step via `User-Agent` sniffing (contains `iPhone|iPad|Macintosh` → Apple path; `Windows` → connecttest.txt; else generate_204).
- Responses must include `Cache-Control: no-cache, no-store` so probes aren't cached.

---

## 5. NETWORKING IMPLEMENTATION (ROOT)

### 5.1 Hotspot detection
- Poll every 5 s (while portal armed) via libsu: `ip -4 addr show`. Identify the hotspot interface by name regex `(wlan1|wlan2|swlan0|swlan1|ap0|softap0|swlan\d)` that has an IPv4 — OEMs differ (AOSP: `wlan1`, Samsung: `swlan0`, some Qualcomm: `ap0`). Record **interface name + gateway IP + prefix** (do **NOT** hardcode `192.168.43.1`; Android 11+ randomizes the hotspot subnet).
- Fallback: Settings tab lets the admin pick the interface manually; also parse `dumpsys wifi | grep -i tether` as a secondary signal. `HotspotDetector` exposes `StateFlow<HotspotState?>`.

### 5.2 Server ports (bind rules)
- App runs unprivileged → SELinux forbids binding ports <1024. **Always bind high ports** and REDIRECT with iptables:
  - HTTP portal: `0.0.0.0:8080`
  - DNS: `0.0.0.0:5353/udp`
  - TLS resetter: `0.0.0.0:8443/tcp`

### 5.3 IPv4 firewall (nat table — chain `PORTAL_AUTH`)
On portal activation (idempotent — safe to re-run):
```
iptables -t nat -N PORTAL_AUTH                       # create if missing (ignore "exists")
iptables -t nat -F PORTAL_AUTH                       # flush ONLY our chain
iptables -t nat -A PREROUTING -i <hotspot_if> -j PORTAL_AUTH
# chain contents for UNAUTHENTICATED clients:
iptables -t nat -A PORTAL_AUTH -p udp --dport 67 -j RETURN            # never break DHCP
iptables -t nat -A PORTAL_AUTH -p udp --dport 53 -j REDIRECT --to-ports 5353
iptables -t nat -A PORTAL_AUTH -p tcp --dport 80 -j REDIRECT --to-ports 8080
iptables -t nat -A PORTAL_AUTH -p tcp --dport 443 -j REDIRECT --to-ports 8443   # TLS resetter
iptables -t nat -A PORTAL_AUTH -p tcp -j REDIRECT --to-ports 8080     # blackhole all other TCP → blocked
iptables -t nat -A PORTAL_AUTH -p udp -j REDIRECT --to-ports 5353     # blackhole QUIC/NTP/etc → blocked
```
On **login success** (MAC learned via `ip neigh` for the client's source IP):
```
iptables -t nat -I PORTAL_AUTH 1 -m mac --mac-source <MAC> -j RETURN
```
- The catch-all TCP/UDP REDIRECTs are essential: without them a blocked client could still reach QUIC (UDP/443), mail ports, etc. by IP. After the catch-all, unauthorized devices can only talk to the portal + DNS.
- Track each inserted MAC rule so removal is exact: `iptables -t nat -D PORTAL_AUTH -m mac --mac-source <MAC> -j RETURN`.
- Use `-w 5` (wait for xtables lock) on every iptables call.

### 5.4 IPv6 firewall (filter table — chain `PORTAL_AUTH6`) — **MANDATORY**
IPv6 is a classic portal bypass; without this, modern clients go straight online over v6:
```
ip6tables -w 5 -t filter -N PORTAL_AUTH6
ip6tables -w 5 -t filter -F PORTAL_AUTH6
ip6tables -w 5 -t filter -I FORWARD 1 -i <hotspot_if> -j PORTAL_AUTH6
# in chain: (authorized MAC RETURN rules inserted at position 1 on login)
ip6tables -w 5 -t filter -A PORTAL_AUTH6 -m mac --mac-source <MAC> -j RETURN
ip6tables -w 5 -t filter -A PORTAL_AUTH6 -j REJECT
```
Also: `DnsInterceptor` answers **AAAA queries with NOERROR + zero records** so clients don't prefer v6 while blocked. Never touch link-local/ND — the filter FORWARD hook doesn't affect it.

### 5.5 `TlsResetter`
A raw `ServerSocket` on 8443 that accepts and **immediately closes** the socket. This makes HTTPS probes fail *fast* (connection reset instead of a 30 s hang), pushing clients to their HTTP probe → portal appears quickly. Log each reset (IP, timestamp) at debug level.

### 5.6 `DnsInterceptor` (hand-rolled, no DNS library)
Minimal UDP DNS responder (~200 lines, unit-tested):
- Parse the query; echo back the question; `QR=1, RD/RA copy`; for **A** → one A record = hotspot gateway IP, TTL 10; for **AAAA** → NOERROR with 0 answers; anything else → NOERROR 0 answers.
- Tolerate malformed packets (validate, drop silently — remember catch-all UDP traffic also lands on 5353).
- Run on a dedicated thread with a small thread pool; bound to `0.0.0.0:5353`.

### 5.7 Client identification
- `ClientMonitor` polls `ip neigh show dev <if>` every 10 s (+ on-demand) → map `IP → MAC → hostname?` (best-effort via `getent`/reverse-DNS skip). ARP fallback: `/proc/net/arp`.
- When `/api/login` arrives, resolve the client's MAC from its source IP using this map. If unknown, reject login with a clear error.

---

## 6. PORTAL WEB APP (SERVED BY THE PHONE)

Single self-contained page (inline CSS/JS, **no CDN/external assets** — captive webviews have no real internet). Must be <100 KB, responsive, work in Android's "Sign-in network" view, iOS Captive Network Assistant, and Windows' mini browser (all restricted webviews: test with no JS modules, no fancy APIs; plain `XMLHttpRequest`/`fetch` + form POST).

Endpoints (NanoHTTPD):
| Endpoint | Method | Behavior |
|---|---|---|
| `/` | GET | Portal page: logo/title, welcome text (customizable), username+password form, error area, status area |
| `/api/login` | POST | form-encoded `username`,`password` → see below |
| `/api/status` | GET | JSON `{authorized: bool, username?, expiresAt?, remainingSeconds?}` (keyed by MAC via client IP + cookie token) |
| `/api/logout` | POST | revoke own session (remove whitelist rule), return to blocked |
| probe paths | GET | per §4 matrix |

`/api/login` semantics:
- Validate against `AuthStore` (BCrypt verify). Checks: user exists, enabled, not expired (`expiresAt`), active device count for user < `deviceLimit` (default 1).
- **Rate limit per client MAC: 5 failed attempts / 5 minutes → HTTP 429** (prevents LAN brute force).
- On success: create session (`token = SecureRandom 32B`, cookie `HttpOnly; SameSite=Lax; Path=/`), insert MAC whitelist rules (v4 + v6), log event, respond `{status:"ok", expiresAt}`. Page then shows "Connected ✓" and redirects to the OS-appropriate online-probe path (§4) so the portal window self-closes.
- On failure: `401 {status:"invalid", remainingAttempts:n}`.
- Never log plaintext passwords. Escape all user-visible strings (XSS).

Portal texts (title, welcome message, footer) configurable in Settings; store in DataStore; re-render page from templates.

---

## 7. USERS, SESSIONS & EXPIRY

**Room entity `PortalUser`:** `id, username (unique, case-insensitive), passwordHash (BCrypt), enabled: Bool, deviceLimit: Int = 1, expiresAt: Long?, note: String, createdAt`. First launch: if no users exist, Settings shows a "create first admin-issued user" card (no hardcoded credentials anywhere).

**Session = (MAC, user, token, createdAt, expiresAt).** Defaults (configurable in Settings): session duration **8 h**, idle auto-revoke **30 min** (client absent from `ip neigh` / stale+failed for the window → revoke whitelist rules).
- `SessionManager` coroutine ticks every 60 s: expire sessions → remove their iptables rules (v4+v6) → update UI/logs.
- Manual kick / revoke from Clients tab removes rules immediately and blocks the device again (portal reappears on their next HTTP request).
- On portal stop: revoke ALL sessions and remove ALL our rules.

---

## 8. ADMIN UI (JETPACK COMPOSE, MATERIAL 3)

- **Dashboard:** portal ON/OFF button, hotspot status (interface, SSID if readable via `dumpsys wifi`, gateway IP, subnet), connected-client count, logged-in count, session config summary, big status card.
- **Clients:** list from `ClientMonitor` + sessions — IP, MAC, hostname, state chip (Blocked / Logged-in-as <user>), time remaining; row actions: **Authorize** (manual whitelist), **Kick**, **Details** (traffic-ish info optional).
- **Users:** CRUD list; create/edit sheet (username, password generator field, device limit, optional expiry date, note); toggle enabled; delete confirm. Copy-able list for sharing credentials.
- **Settings:** ports (advanced), session & idle durations, hotspot interface override, portal title/welcome/footer text, "activate portal on boot if hotspot is on" toggle (BOOT_COMPLETED receiver + root), theme.
- **Logs:** reverse-chronological events (login ok/fail, rate-limit, kick, expire, rule add/remove, errors) with copy/share; keep last 1000 in Room.

Architecture: MVVM, `StateFlow` everywhere, Hilt optional (manual DI is fine). All root operations funnel through `RootShellManager` (single libsu shell).

---

## 9. RELIABILITY, LIFECYCLE & SAFETY (CRITICAL)

1. `PortalService` is a **foreground service** (dataSync type) with a persistent notification: "Portal active • N devices • tap to open"; actions: Stop.
2. **Cleanup on every exit path:** `onDestroy()`, `onTaskRemoved()`, and explicit Stop → for each session remove MAC rules → flush+delete `PORTAL_AUTH`/`PORTAL_AUTH6` → remove PREROUTING/FORWARD jump rules. Idempotent (ignore "no such chain/rule" errors). On next activation, always flush-then-recreate our own chains (self-healing).
3. **Never** run `iptables -F` globally, never touch built-in chains except inserting our two jumps; always `--wait`. Tag every rule we own; if a jump rule already exists, don't duplicate (check with `iptables -t nat -C` first).
4. If the root shell dies or any setup step fails → stop the service, deactivate, show a clear error (never leave half-installed rules running unattended).
5. Handle hotspot going down (interface disappears) → tear down rules automatically, keep service armed; re-arm when hotspot returns.
6. Boot receiver: if enabled in Settings, after BOOT_COMPLETED wait for hotspot-on, then auto-activate (root makes this possible).
7. Optional watchdog: on activation, schedule an `AlarmManager` re-check every 15 min verifying our jump rules exist (re-add if a third party flushed them).

---

## 10. SECURITY REQUIREMENTS

- BCrypt (cost ≥ 10) for passwords; secure-random tokens; constant-time comparison.
- All iptables args passed as **arrays** through libsu (no string interpolation of user data into shell).
- Admin UI is local-only (no network listener for admin functions; it's the app itself).
- Rate limiting per §6; lock log spam behind debug flag.
- Android `NETWORK` security: portal is HTTP-only on the LAN (document this limitation + that it's standard for captive portals).
- Do not request or hold any permission beyond what's needed; `POST_NOTIFICATIONS` for the FGS notification; no storage permission needed (app-private Room/DataStore).

---

## 11. KNOWN ANDROID GOTCHAS (READ BEFORE CODING)

1. **Do not hardcode `192.168.43.1`** — Android 11+ randomizes hotspot subnets. Always read the live interface address.
2. **Hotspot interface names vary by OEM** (see §5.1 regex). Detect, never assume.
3. **Binding ports <1024 fails** for apps under SELinux → high ports + REDIRECT (already specced).
4. **`REJECT` target is invalid in nat `PREROUTING`** — that's why HTTPS is handled via the 8443 accept-and-close resetter instead.
5. **DHCP must never be redirected** (udp/67 RETURN at chain top) or clients can't join/renew.
6. **iOS CNA requires the exact body `Success`** (200) to mark online; any other 200 body opens the captive sheet. Android requires **non-204** on `/generate_204` to open the portal. Windows checks `/connecttest.txt` body. (Full matrix §4.)
7. **Client MAC randomization** is fine — the MAC is stable *for our network*, which is what we whitelist.
8. **Android Private DNS (DoT/DoH):** unauthorized clients fail (853/443 blocked) and fall back to plaintext DNS in opportunistic mode; strict-mode clients simply stay blocked until auth — both acceptable and expected; document it.
9. **`ip neigh` states:** treat `REACHABLE/DELAY/PROBE` as present; `STALE` alone is not absence; require `STALE/FAILED` + full idle window before auto-revoke.
10. Some OEMs strip `iptables` — verify with `iptables --version` at activation; error clearly if missing (suggest Magisk module `iptables`/busybox as fallback path in README).

---

## 12. SUGGESTED PROJECT STRUCTURE

```
HotspotPortal/
├── build.gradle.kts / settings.gradle.kts / gradle.properties
└── app/src/main/
    ├── AndroidManifest.xml                     (FGS, BOOT receiver, POST_NOTIFICATIONS)
    └── java/com/example/hotspotportal/
        ├── PortalApp.kt                        (Application, DI root)
        ├── service/PortalService.kt            (foreground orchestrator)
        ├── root/RootShellManager.kt            (libsu lifecycle, availability check)
        ├── net/HotspotDetector.kt
        ├── net/FirewallManager.kt              (v4+v6 chains, MAC whitelist, cleanup)
        ├── net/DnsInterceptor.kt               (+ DnsPacket.kt parser/serializer)
        ├── net/TlsResetter.kt
        ├── server/PortalServer.kt              (NanoHTTPD, probe router §4)
        ├── server/PortalPages.kt               (HTML templates, inline CSS/JS)
        ├── server/LoginApi.kt                  (rate limiter, login/logout/status)
        ├── clients/ClientMonitor.kt            (ip neigh poller, IP→MAC)
        ├── auth/AuthStore.kt / SessionManager.kt (Room, BCrypt, expiry ticker)
        ├── store/ (Room db, DAOs, DataStore settings)
        ├── ui/ (dashboard/, clients/, users/, settings/, logs/, theme/)
        └── util/ (Logs, Extensions)
    └── test/                                   (unit tests, §13)
```

---

## 13. MILESTONES, TESTS & ACCEPTANCE CRITERIA

Implement in this order, keeping the project compiling at each step:

- **M1** Gradle skeleton + Compose app shell + navigation.
- **M2** libsu root check + `RootShellManager`; Dashboard shows root status.
- **M3** `HotspotDetector` + `ClientMonitor` (real device data on screen).
- **M4** `DnsInterceptor` + `PortalServer` + `TlsResetter`; portal page served.
- **M5** `FirewallManager` full chain install/remove; block-until-login works.
- **M6** Auth: users Room + BCrypt, `/api/login`, MAC whitelisting, sessions/expiry.
- **M7** Clients/Users/Settings/Logs tabs complete; boot auto-start; notifications.
- **M8** Hardening, README, screenshots-instructions, final checklist run.

**Unit tests (must pass, `./gradlew test`):**
1. DNS packet round-trip: parse real `dig`-style query bytes → correct A/AAAA/empty responses (hardcode 2–3 real captured query fixtures).
2. Probe router: for each row of the §4 matrix assert unauthorized vs. authorized response (status + exact body).
3. Rate limiter: 5 failures → 429, reset after window.
4. Session expiry logic (virtual clock): expiry + idle revoke remove the right rules.
5. Firewall command builder: MAC whitelist add/remove produce exactly the expected arg arrays (validate no injection).

**Manual acceptance checklist (put in README):**
- [ ] Android client: connects → "Sign in to network" → portal → login → internet works, window closes.
- [ ] iPhone: CNA pops automatically → login → `Success` path closes it.
- [ ] Windows 11: browser auto-opens → login → internet works.
- [ ] Blocked client **cannot** reach the internet (test HTTPS site, QUIC/YouTube, `ping 1.1.1.1` fails or non-HTTP protocols blocked) but portal opens.
- [ ] Wrong password 5× → locked 5 min. Kick → device re-blocked instantly, portal reappears.
- [ ] Session expiry → auto re-block. App "Stop portal" → all rules gone (`iptables -t nat -L` clean), other device's normal Wi-Fi unaffected.
- [ ] Phone reboot with portal stopped → no leftover rules.
- [ ] `./gradlew assembleDebug` + `./gradlew test` green.

---

## 14. DELIVERABLES

1. **Complete Gradle project source** (all modules, resources, manifest) implementing everything above.
2. **README.md:** prerequisites (rooted device, Magisk, Android 7–14), build & install steps, first-run walkthrough (create user → enable hotspot → activate portal), how the portal works (1 diagram), troubleshooting (SELinux denials → `dmesg | grep avc`, OEM interface names, "iptables missing", Private DNS note), limitations (HTTP-only portal, strict Private DNS, VPN apps on blocked clients), and the acceptance checklist.
3. Inline comments on every tricky part (probe matrix, iptables layout, DNS parser, MAC resolution, cleanup logic).
4. Sample portal page screenshot instructions (how to open `http://<gateway-ip>/` directly for testing).

---

## 15. WORKING RULES FOR YOU (THE AGENT)

- If anything is ambiguous or device-specific (e.g., `cmd wifi` capabilities on a particular Android build), implement the **runtime capability check + safe fallback** pattern rather than asking; note the decision in code comments and README.
- Never stub the networking internals with TODOs. Every component in §3 must be fully implemented — this prompt is the complete spec.
- Prefer small, composable classes with injected dependencies; no global singletons with mutable state except the DI graph.
- All user-facing strings in `strings.xml`.
- When you finish, output: project tree, build/test results, any device-specific caveats you found while implementing, and the fastest path to first success on a real rooted phone (exact tap-by-tap order).
