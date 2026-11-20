# WoL Mobile — Plan

Android app to wake LAN hosts (directly or via a relay over NetBird) and run SSH commands, triggered from home-screen widgets, Quick Settings tiles and app shortcuts. Sideloaded via GitHub Releases.

## Decisions

| Area | Decision |
|---|---|
| Stack | Kotlin, Jetpack Compose, Glance (widgets), Room, WorkManager, sshj |
| SDK | `minSdk 31` (Android 12), target latest stable |
| Distribution | Signed APK attached to GitHub Releases by GitHub Actions on tag push; keystore in repo secrets; updates via Obtainium |
| Local build | Nix flake (`androidenv`) + Gradle |
| SSH auth | Keys only. Ed25519 generated in-app, public key shown for copying. Private key encrypted with an Android Keystore AES key (not the deprecated `security-crypto`). Host key TOFU-pinned; mismatch fails |
| Backups | None |

## Domain

- **Host**: name, MAC, broadcast address, WoL port (default 9), status probe address + port.
- **SshTarget**: address, port, user, key, pinned host key. The home Pi is one of these.
- **Action**:
  - `Wake(host)` — auto-routed (below).
  - `Ssh(target, command)` — arbitrary command (e.g. poweroff).
  - Flag: `confirmBeforeRun`.
- **Routing config** (global): home probe = Pi LAN IP:22; remote wake = SshTarget (Pi) + command template, e.g. `wakeonlan {mac}`.

## Auto-routing for `Wake`

1. If on Wi-Fi: TCP connect to the Pi's LAN IP:22, socket bound to the Wi-Fi `Network` (bypasses NetBird), short timeout.
2. Reachable → home → UDP broadcast magic packet, bound to the Wi-Fi network.
3. Otherwise → SSH to the Pi over NetBird and run the wake command template.

**Risk:** binding to Wi-Fi while NetBird is up only works if NetBird's `VpnService` allows bypass. Verify first in milestone 2. Fallback: when NetBird is up, always go via the Pi.

## Triggers

- **1×1 widget**: one action.
- **Resizable widget**: grid of the actions picked in its config screen.
- **Quick Settings tile**: one action per tile; pick which in the app (Android only offers static tile slots, so ship a fixed number, e.g. 3).
- **App shortcuts**: dynamic shortcuts for pinned actions.
- All triggers run the action as an expedited WorkManager job. `confirmBeforeRun` opens a translucent dialog activity first.

## Feedback

- The widget shows running → ✓/✗ for a few seconds → idle.
- Notification on failure only, with the error or command output.
- In-app log of the last N runs, including stdout and stderr.

## Host status

- Checked by TCP-probing the host's configured port: over Wi-Fi at home, otherwise directly over NetBird (the Pi routes the home subnet).
- After a wake: poll until the host answers or ~2 min pass. After a poweroff: poll until it stops answering.
- Refreshed on app open and with pull-to-refresh.
- Widgets: off by default, can be turned on per widget. Polls every 15 min (the WorkManager minimum; Doze stretches it while idle) and shows how long ago it last checked.

## Status

All milestones are implemented. Deviations from the original plan:

- The SSH key belongs to the SSH target. For several keys on one server, create one target per key.
- The relay (target and command template) is configured globally in Settings, not per action.
- SSH actions can name an "affected host" so its status is followed after the command (e.g. poweroff).

Verified: unit tests (MAC parsing, magic packet over UDP, key format parsed by sshj, fingerprints, relay template); the SSH client against a local OpenSSH sshd (first-use pinning, pinned key, changed host key, timeout, dropped connection, rejected key); debug and R8 release builds; lint.

Emulator (Android 17): all screens, local-network permission prompt, SSH actions, confirmation dialog, failure notification, wake at home (UDP packet received) and away (relay command ran with placeholders filled), Quick Settings tiles, app shortcut, 1×1 and grid widgets with status and run states.

Not verified: Wi-Fi binding while NetBird is running (needs the real phone).
