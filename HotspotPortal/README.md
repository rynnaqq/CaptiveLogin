# HotspotPortal

A native Android captive portal for **rooted** phones. Turn on the phone's
Wi-Fi hotspot, tap **Activate portal**, and every device that joins is shown a
login page served by the phone itself. No device reaches the internet until it
signs in with a username and password you created in the app.

Everything runs offline on the phone. No servers, no cloud, no analytics.

---

## How it works

```
   client joins hotspot
          │
          ├─ DHCP  (udp/67) ────────────────► PASSED THROUGH  (never redirect)
          │
          ├─ DNS   (udp/53) ──REDIRECT──► 5353 ──► DnsInterceptor
          │                                        answers every A query with the
          │                                        phone's gateway IP; AAAA gets
          │                                        NOERROR / 0 answers
          │
          ├─ HTTP  (tcp/80) ──REDIRECT──► 8080 ──► PortalServer  (probe matrix §4)
          │                                        serves portal HTML instead of the
          │                                        OS's expected "I'm online" answer
          │                                        → the OS opens its portal window
          │
          ├─ HTTPS (tcp/443) ─REDIRECT──► 8443 ─► TlsResetter
          │                                        accept + immediate close = fast RST
          │
          └─ any other TCP / UDP ─────────► 8080 / 5353  (blackhole)

   guest submits /api/login
          └─► BCrypt verify ─► resolve IP→MAC via `ip neigh`
                 ─► iptables -I PORTAL_AUTH 1 -m mac --mac-source <MAC> -j RETURN
                 ─► page fires the OS's own probe so its window closes itself
```

Everything the app owns lives in two private chains — `PORTAL_AUTH` (nat) and
`PORTAL_AUTH6` (filter) — reached by a single jump each. Teardown is therefore
exact and never touches a built-in chain's contents.

**Why IPv6 gets its own chain:** without it, modern clients simply go online
over v6 and never see the portal. The v6 chain REJECTs anything not explicitly
whitelisted, and the DNS interceptor returns zero AAAA records so a blocked
client never prefers v6 in the first place.

---

## Prerequisites

| | |
|---|---|
| Device | Rooted Android phone (Magisk or KernelSU) |
| Android | 7.0 (API 24) through 14 |
| Arch | ARM64 |
| Build | JDK 17, Android SDK, no other tooling |

Root is not optional. The app installs iptables rules, and Android gives an
ordinary app no way to do that.

---

## Build and install

```bash
# from the project root
./gradlew assembleDebug        # debug APK
./gradlew test                 # the five spec-13 unit tests
./gradlew assembleRelease      # minified and installable
```

Then install over USB:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

### Installing updates without uninstalling

Every build — local, debug, release, and the APKs produced by CI — is signed
with the same committed key in `keystore/`. So `adb install -r` (or tapping the
new APK on the phone) replaces the installed app in place: your **users,
passwords, sessions, event log and settings all survive**.

Verify a build's signer at any time:

```bash
$ANDROID_HOME/build-tools/34.0.0/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-release.apk | grep SHA-256
# every build must print e85c6622a9aacc9d16b9682c050bb31b9b27c56db17348bb44a03bb843c3834
```

> **One-time caveat.** Builds made *before* this change were signed with an
> auto-generated debug key (CI minted a new one per job), so the version already
> on your phone has a different certificate. Android refuses to replace an app
> signed by a different key, so you must uninstall that one copy once. Do it
> before creating any users you care about — the uninstall clears the database.
> Every install after that is an in-place update.

`local.properties` must point at your SDK (AGP creates it on first build):

```properties
sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk
```

---

## First run — exact tap order

1. **Install and open** HotspotPortal. Grant the notification permission.
2. The **Users** tab opens on a first-run card. Tap **New user**, enter a
   username, tap **Generate** to fill a strong password, copy it down, tap
   **Save**. There are no default credentials in the app.
3. Turn on **Settings → Hotspot** on the phone. Leave it on.
4. Go back to **Dashboard**. Wait until Interface / Gateway fill in
   (e.g. `wlan1` / `192.168.43.1`).
5. Tap **Activate portal**. The notification appears: "Portal active".
6. Connect a phone or laptop to the hotspot. Within a few seconds its screen
   shows "Sign in to network" / the Captive Network Assistant / a browser.
7. Sign in with the credentials from step 2. The window closes on its own and
   the device has internet.
8. **Clients** shows the device, its IP/MAC, and who is signed in. **Kick**
   blocks it again immediately.

### Testing the portal page without a second device

On a machine joined to the hotspot, open:

```
http://<gateway-ip>:<http-port>/     # e.g. http://192.168.43.1:8080/
```

Any other HTTP GET lands on the same page, and the OS probe paths
(`/generate_204`, `/hotspot-detect.html`, `/connecttest.txt`) answer
correctly for manual inspection.

---

## Project layout

```
app/src/main/java/com/example/hotspotportal/
├── PortalApp.kt              Application + manual DI graph
├── service/                  PortalService (foreground), Boot, Watchdog
├── root/                     libsu shell, argv-only exec
├── net/                      HotspotDetector, FirewallManager, DnsInterceptor,
│                             TlsResetter, DnsCodec, FirewallCommands
├── server/                   PortalServer (NanoHTTPD), ProbeRouter, LoginApi, PortalPages
├── clients/                  ClientMonitor (ip neigh / /proc/net/arp)
├── auth/                     AuthStore (BCrypt), SessionManager, RateLimiter
├── store/                    Room DB + DAOs, SettingsStore (DataStore), EventLog
└── ui/                       Compose: Dashboard, Clients, Users, Settings, Logs
```

---

## The probe matrix

The portal opens because the phone answers the OS's connectivity check with a
**page instead of the "I'm online" answer**. The bodies are exact — iOS will
not close its sheet unless the body is literally `Success`.

| Client OS | Probe path | Blocked | Signed in |
|---|---|---|---|
| Android 9+ | `/generate_204` | `200` + portal HTML | `204` |
| Android ≤8 | `/gen_204` | `200` + portal HTML | `204` |
| iOS / macOS | `/hotspot-detect.html` | `200` + portal HTML | `200` body `Success` |
| Windows 10/11 | `/connecttest.txt` | `200` + portal HTML | `200` body `Microsoft Connect Test` |
| Windows legacy | `/ncsi.txt` | `200` + portal HTML | `200` body `Microsoft NCSI` |
| Firefox | `/success.txt` | `200` + portal HTML | `200` body `success` |
| Ubuntu | `/canonical.html` | `200` + portal HTML | `204` |

All probe responses carry `Cache-Control: no-cache, no-store`, or the OS can
act on a stale answer.

---

## Troubleshooting

**No root / root check fails**
Grant the app superuser in Magisk (or KernelSU). The shell is libsu's; no
manual `su` grant prompt appears inside the app.

**"iptables is not available"**
Some OEMs strip it. Install a Magisk `iptables` or `busybox` module, then tap
**Settings → Flush and rebuild all rules**. `iptables --version` in any root
shell confirms the fix.

**No hotspot interface detected**
Interface names are OEM-specific. `dumpsys wifi | grep -i tether` in a root
shell shows the real name — put it in **Settings → Hotspot interface
override**. Known names: `wlan1` (AOSP), `swlan0` (Samsung), `ap0` (some
Qualcomm), `softap0`.

**SELinux denials**
```bash
dmesg | grep avc
```
If the shell itself is denied, the app logs `setup_failed` and removes any
partial rules rather than leaving the network half-broken.

**Portal never appears on the client**
- Confirm the client is blocked, not already authorised: check **Clients**.
- A client with Android **Private DNS in strict mode** cannot fall back to
  plaintext DNS. It stays blocked until it authenticates. Expected behaviour.
- Force a re-probe: toggle Wi-Fi on the client, or `airplane` mode on/off.

**HTTPS hangs instead of failing fast**
The TLS resetter binds 8443. If another app holds that port the reset never
lands; change the port in **Settings → Advanced**.

---

## Limitations

- **HTTP-only portal.** Standard for captive portals — the guest has no
  internet yet, so there is nothing to bootstrap TLS from. The admin app
  itself is local-only and never listens on a network interface.
- **Client VPN apps** can bypass portal rules via their own tunnels. Out of
  scope.
- **MAC randomisation** is fine: the MAC is stable for our network, and that
  is what gets whitelisted.
- **No per-user bandwidth accounting.** The Clients tab shows presence and
  session time, not traffic.
- Sessions live in memory only, so a portal restart signs everyone out. This
  is deliberate: a stale session surviving a reboot would be a security hole.

---

## Build-environment deviation from spec

The spec asks for `compileSdk 34`. Google's repository has **withdrawn the
base `platform-34_r0X.zip` artifacts** — only extension-level variants remain
downloadable — so this build uses:

- `compileSdk = 35`
- `targetSdk = 34` (unchanged — runtime behaviour still targets Android 14)
- `minSdk = 24` (unchanged)

Everything else matches the spec: Kotlin DSL, single module, JVM 17, AGP 8.x,
Compose BOM, libsu, NanoHTTPD, Room, DataStore, jBCrypt, coroutines. No
Firebase, no Retrofit, no internet-capable SDK.

---

## Manual acceptance checklist

- [ ] Android client: connects → "Sign in to network" → portal → login → internet works, window closes.
- [ ] iPhone: Captive Network Assistant pops automatically → login → `Success` path closes it.
- [ ] Windows 11: browser auto-opens → login → internet works.
- [ ] Blocked client **cannot** reach the internet (HTTPS site, QUIC/YouTube, `ping 1.1.1.1`) but the portal opens.
- [ ] Wrong password 5× → locked for 5 minutes.
- [ ] Kick → device re-blocked instantly, portal reappears.
- [ ] Session expiry → auto re-block.
- [ ] "Stop portal" → all rules gone (`iptables -t nat -L` shows no `PORTAL_AUTH`), other Wi-Fi unaffected.
- [ ] Reboot with the portal stopped → no leftover rules.
- [ ] `./gradlew assembleDebug` and `./gradlew test` both green.
