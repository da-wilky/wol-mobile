# WoL Mobile

Android app that wakes LAN hosts and runs SSH commands from home-screen widgets, Quick Settings tiles and app shortcuts. At home it sends the Wake-on-LAN magic packet directly; elsewhere it SSHes into a relay on the LAN (e.g. a Pi reachable over NetBird) that sends it.

## Build

```sh
nix develop -c ./gradlew testDebugUnitTest assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Headless emulator (Android 17), e.g. over SSH:

```sh
nix run .#emulator           # creates the AVD on first run, returns once booted, prints how to connect
nix develop -c ./gradlew installDebug
nix run .#emulator -- stop
```

## Release

Create a signing key once and keep it safe — updates must be signed with the same key:

```sh
nix develop -c keytool -genkeypair -v -keystore release.jks -alias wolmobile -keyalg RSA -keysize 4096 -validity 10000
```

Add these repository secrets: `KEYSTORE_BASE64` (`base64 -w0 release.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Pushing a tag like `v1.0.0` builds the signed APK and attaches it to a GitHub release. [Obtainium](https://github.com/ImranR98/Obtainium) can track the releases for updates.

Local signed build: export `WOL_KEYSTORE` (path), `WOL_KEYSTORE_PASSWORD`, `WOL_KEY_ALIAS`, `WOL_KEY_PASSWORD`, then `./gradlew assembleRelease -PappVersion=1.0.0`.

## Setup

1. **Keys** tab: generate a key, copy the public key into `~/.ssh/authorized_keys` on the server. To limit a key to one command:
   ```
   command="sudo /usr/sbin/poweroff",restrict ssh-ed25519 AAAA… wolmobile-poweroff
   ```
   with a matching sudoers rule, e.g. `user ALL=(root) NOPASSWD: /usr/sbin/poweroff`.
2. **SSH** tab: add targets (address, user, key). The host key is pinned on first connect.
3. **Hosts** tab: MAC, broadcast address, and optionally a TCP port for status checks (e.g. 22).
4. **Settings**:
   - *Home detection*: the Pi's LAN IP. Reachable over Wi-Fi (bypassing the VPN) → you're home → direct magic packet.
   - *Remote wake*: the Pi as relay target and the command, e.g. `wakeonlan {mac}` (install `wakeonlan` on the Pi). A `command=`-restricted key ignores the requested command, so the relay key needs either no restriction or a wrapper that validates `$SSH_ORIGINAL_COMMAND`.
   - *Quick Settings tiles*: assign actions to the three tile slots, then add the tiles from the Quick Settings edit panel.
5. **Actions** tab: wake or SSH actions; optionally require confirmation or add to app shortcuts (long-press the app icon).
6. Long-press the home screen → Widgets → *WoL action* (1×1) or *WoL actions* (resizable grid).

## Notes

- If the VPN app forbids bypassing it, home detection fails and wakes always go through the relay.
- Status checks are TCP connects; a refused connection counts as up. With a widget's status enabled, checks run every ~15 minutes.
- SSH commands time out after 60 s. A dropped connection without exit status (e.g. `poweroff`) counts as success.
