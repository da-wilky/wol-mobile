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

Install, launch and stream the app's log on one device (serial from `adb devices`; omit it with a single device):

```sh
nix run .#debug -- pixel9.netbird.selfhosted:41111
```

To keep adb working after the phone leaves Wi-Fi (e.g. NetBird over mobile data), pair and connect once over Wireless debugging, then pin adbd to a port until the next reboot:

```sh
nix run .#adb-listen -- pixel9.netbird.selfhosted:41111 <port>
```

## Release

The keystore lives in Infisical (project `037393a2-6c04-42e4-b0d6-a16985200880`, folder `/wolmobile`) as `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS=wolmobile` and `KEY_PASSWORD` (same as `KEYSTORE_PASSWORD`; PKCS12 has no separate key password). Updates must be signed with the same key, so create it once:

```sh
cd "$(mktemp -d)"
nix develop ~/workspace/mine/wol-mobile -c keytool -genkeypair -v -keystore wolmobile.p12 -storetype PKCS12 \
  -alias wolmobile -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 wolmobile.p12
shred -u wolmobile.p12
```

Infisical is only reachable over NetBird, so the release workflow uses copies in the repository secrets. Copy them after creating or changing the key (needs a one-time `nix run nixpkgs#gh -- auth login`):

```sh
nix run .#sync-secrets
```

Pushing a tag like `v1.0.0` builds the signed APK and attaches it to a GitHub release. [Obtainium](https://github.com/ImranR98/Obtainium) can track the releases for updates.

Local signed build: export `WOL_KEYSTORE` (path), `WOL_KEYSTORE_PASSWORD`, `WOL_KEY_ALIAS`, `WOL_KEY_PASSWORD`, then `./gradlew assembleRelease -PappVersion=1.0.0`.

## Setup

1. **Keys** tab: generate a key, copy the public key into `~/.ssh/authorized_keys` on the server. To limit a key to one command:
   ```
   command="sudo /usr/sbin/poweroff",restrict ssh-ed25519 AAAA… wolmobile-poweroff
   ```
   with a matching sudoers rule, e.g. `user ALL=(root) NOPASSWD: /usr/sbin/poweroff`.
2. **SSH** tab: add targets (address, user, key). The host key is pinned on first connect.
3. **Hosts** tab, network header (a fresh install has "Home"):
   - *Presence*: an address only reachable on that LAN, e.g. the Pi's LAN IP. Reachable over Wi-Fi (bypassing the VPN) → you're on that network → direct magic packet.
   - *Wake*: broadcast address and the relay command, e.g. `wakeonlan {mac}` (install `wakeonlan` on the relays).
   - *Relays*: SSH targets on that LAN, tried in order until one runs the command successfully. A `command=`-restricted key ignores the requested command, so the relay key needs either no restriction or a wrapper that validates `$SSH_ORIGINAL_COMMAND`.
4. **Hosts** tab: MAC, network, and optionally a TCP port for status checks (e.g. 22). After a wake, a host that doesn't answer within 2 minutes is reported as failed.
5. **Settings** → *Quick Settings tiles*: assign actions to the three tile slots, then add the tiles from the Quick Settings edit panel.
6. **Actions** tab: wake or SSH actions; optionally require unlock or confirmation, or add to app shortcuts (long-press the app icon).
7. Long-press the home screen → Widgets → *WoL action* (1×1) or *WoL actions* (resizable grid).

## Notes

- If the VPN app forbids bypassing it, home detection fails and wakes always go through the relay.
- Status checks are TCP connects; a refused connection counts as up. With a widget's status enabled, checks run every ~15 minutes.
- SSH commands time out after 60 s. A dropped connection without exit status (e.g. `poweroff`) counts as success.
