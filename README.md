# FieldWatch-Nanashi

> [!WARNING]
> **Unofficial, modified, personal fork. Not for general use.**
>
> FieldWatch-Nanashi is Nanashi's personal modification of Fieldwatch. The source is public, but the fork exists for one user and is tuned to Nanashi's own preferences. It is not made, reviewed, or endorsed by Off Grid Pete LLC or the upstream Fieldwatch project. Expect it to be unstable: features change or disappear without notice, builds can break, and saved data may not survive updates. English only. It is not meant for adoption by anyone but Nanashi. If you want Fieldwatch, use the upstream project at <https://github.com/OffGridPete/Fieldwatch>. Please do not file issues about this fork upstream.

Wi-Fi access-point and Bluetooth LE watcher for Android, with optional active Bluetooth Classic discovery. No account, no backend, works offline.

Application id `dev.namelessnanashi.fieldwatch`. It installs alongside upstream `app.fieldwatch` and does not update or replace it.

## Safety & disclaimer

Provided as-is under the MIT License.

- Use at your own risk. To the maximum extent permitted by law, the authors (Off Grid Pete LLC and NanashiTheNameless) are not liable for indirect, incidental, special, consequential, or punitive damages arising from its use.
- There is no guarantee that trackers, cameras, tags, access points, or any other device will be found, named, or reported. Radios that are off, cellular-only, asleep, randomized, quiet, or outside what the handset's OS exposes will not appear.
- Pattern matches, GPS co-travel ("Moving with you" / "possible tail"), Debrief language, and AI Export output are hypotheses — not identity, not a legal finding, and not a complete RF capture. You are responsible for how you use the app and for complying with local law.
- Location stamps are this phone at hear-time, not the other radio. They stay on the handset until you share them. Logs keep full coordinates even when Privacy mode masks the screen. Debrief, Share log, AI Export, and Share as text can take that data off the phone. Online place names use the system geocoder (often the OEM / Google network); map tiles come from the selected tile provider.

## Install

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium%3A%2F%2Fapp%2F%257B%2522id%2522%253A%2522dev.namelessnanashi.fieldwatch%2522%252C%2522url%2522%253A%2522https%253A%252F%252Fgithub.com%252FNanashiTheNameless%252FFieldWatch-Nanashi%2522%252C%2522author%2522%253A%2522NanashiTheNameless%2522%252C%2522name%2522%253A%2522FieldWatch-Nanashi%2522%252C%2522additionalSettings%2522%253A%2522%257B%255C%2522includePrereleases%255C%2522%253Afalse%252C%255C%2522filterReleaseTitlesByRegEx%255C%2522%253A%255C%2522%255ENightly-Rolling%255C%2522%252C%255C%2522apkFilterRegEx%255C%2522%253A%255C%2522%255EFieldWatch-Nanashi-nightly%255C%255C%255C%255C.apk%2524%255C%2522%252C%255C%2522useLatestAssetDateAsReleaseDate%255C%2522%253Atrue%252C%255C%2522releaseDateAsVersion%255C%2522%253Atrue%252C%255C%2522versionDetection%255C%2522%253Afalse%257D%2522%257D"><img src="docs/assets/badge_obtainium.png" alt="Add to Obtainium" height="64"></a>

Nightly APKs only, no stable app releases: download `FieldWatch-Nanashi-nightly.apk` from the [Nightly-Rolling release](https://github.com/NanashiTheNameless/FieldWatch-Nanashi/releases/tag/Nightly-Rolling), or add this repository to Obtainium. Every official build is signed with the same key; its SHA-256 is in the release notes. The Obtainium button is preconfigured for rolling nightly updates and selects the latest APK. [Import configuration](docs/obtainium.json).

Downloadable signature catalogs and optional sensor exports live in the separate [Data assets release](https://github.com/NanashiTheNameless/FieldWatch-Nanashi/releases/tag/Data-Assets), which is neither latest nor a pre-release. See [asset details](docs/data-assets.md).

## Build

Runs on Android 12 through Android 17 (API 31–37); targets API 36. Builds with the JDK named in `.java-version` and Android SDK platform 37.

```bash
./build.sh                # unit tests + debug APK
./build.sh install        # unit tests + install the debug build on the connected phone
./build.sh release        # unit tests + release APK, then show its signing certificate
./build.sh installRelease # unit tests + signed release APK + install on the connected phone
./build.sh <gradle args>  # anything else goes straight to Gradle
```

`build.sh` finds the JDK from `.java-version` and an existing Android SDK (installing only missing packages), so nothing needs exporting first. `./setup.sh` does just that check; `eval "$(./setup.sh --env)"` exports the same environment into your shell for running `./gradlew` directly.

The debug build installs as `dev.namelessnanashi.fieldwatch.debug` ("FieldWatch-Nanashi (DEBUG)") beside a release build and is signed with the local Android debug key. `versionCode` is seconds since 2026-01-01 UTC, so each build installs over the last. Signed nightly builds are published automatically; see [docs/releases.md](docs/releases.md).

| File | What it is |
|---|---|
| `docs/releases.md` | Nightly pipeline, release key, versioning |
| `LICENSE` | MIT License |
| `NOTICE` | Third-party attribution |

### Moving from upstream `app.fieldwatch`

Nothing carries over by itself. In upstream: Settings → **Export signatures** and **Export settings**. In this app: **Import signatures** and **Import settings**.

## Elevated access (optional)

Settings → **Elevated access** uses whatever the phone offers to lift Android limits that make scanning unreliable. Nothing runs until you tap it.

- **Shizuku or root** (`su`, e.g. Magisk, KernelSU, APatch): *Apply reliability fixes* turns off Wi-Fi scan throttling, exempts the app from battery optimization and background limits, stops Android removing its permissions when unused, grants its radio permissions, and grants secure-settings access so the app can keep throttling off by itself after that. *Turn on Wi-Fi, Bluetooth, and Location* does what it says.
- **Device admin:** an active admin (no policies requested) is placed in the exempted standby bucket, so background scans are not deferred. Deactivate it before uninstalling.
- **Vector module** (modern Xposed): enable FieldWatch-Nanashi for the Bluetooth app and this app, then restart Bluetooth or reboot. It lifts three BLE scan limits for this app only: "scanning too frequently", the 30-minute downgrade to opportunistic scanning, and screen-off suspension of unfiltered scans.

## Optional discovery and unattended scanning

All switches below are off by default in Settings → Scanning.

- **Bluetooth Classic discovery:** periodically runs an active inquiry while the screen is on. Discoverable older headsets, speakers, car kits, and serial modules appear in the Bluetooth list using the existing Class of Device decoder. Inquiry shares radio time with BLE and costs extra battery; devices must answer inquiry to appear.
- **HCI snoop-log reader (root):** first apply **Bluetooth HCI snoop log** in Elevated access, then enable the reader in Scanning settings. It reads new legacy/extended LE advertising reports and Classic inquiry results from `/data/misc/bluetooth/logs/btsnoop_hci.log`, using the existing payload and Class of Device decoders. It handles partial records and file rotation, skips older capture history, and shows reader status in Scanning settings. Other HCI traffic is discarded. Fragmented or truncated extended advertising payloads are skipped. Phones using another log path or format are not supported. Root is required for reading; Shizuku's shell identity cannot access this log. Disabling the reader leaves system logging enabled; undo the elevated snoop item to stop logging and delete its capture. The system capture can contain sensitive Bluetooth traffic, including pairing keys.
- **Continue scanning in background:** keeps the foreground scan notification after dismissing the app from Recents and allows Android to restart the service after memory reclamation. Stop it from the notification.
- **Start scanning after boot:** starts after first unlock and app updates. Grant radio permissions first, then apply **Location permission for boot scanning** through Shizuku or root in Elevated access (or grant **Allow all the time** in Android's app location settings). Use the existing battery exemption and background-running items if needed. These permissions persist across reboots; Shizuku need not be running at boot. Device owner is not required. Force stop blocks automatic starts until the app is opened again, and manufacturer background restrictions can still prevent operation. Continue-after-dismissal and boot startup are separate preferences.

The Live screen has separate **Start scan** and **Stop scan** buttons. **Pause** freezes only the display; scanning and alerts continue. **Stop scan** stops scanning and removes the scan notification, and stays stopped across app resumes and reboot until you press **Start scan**. The notification's Stop action has the same effect. Scan preferences and saved observations are retained.

Snoop capture read interruptions retry up to three times with increasing delays. A damaged or unsupported capture waits for log rotation; denied root access requires checking root and toggling the reader.

Snoop reading and unattended-start preferences stay local to this phone when importing settings.

## Not supported

- Wi-Fi clients, probe-only stations, or 802.11 monitor mode
- Cellular
- Direction finding

## Support this fork

- [GitHub Sponsors](https://github.com/sponsors/NanashiTheNameless)
- [Buy Me a Coffee](https://buymeacoffee.com/NamelessNanashi)
- [Ko-fi](https://ko-fi.com/NanashiTheNameless)
- [Liberapay](https://liberapay.com/NamelessNanashi)
- [Throne](https://throne.com/NamelessNanashi)

## Copyright and license

Copyright (c) 2026 Off Grid Pete LLC.
Modifications copyright (c) 2026 NanashiTheNameless.

Licensed under the [MIT License](LICENSE). AndroidX, Kotlin, and related libraries remain Apache-2.0. IEEE and Bluetooth SIG assigned-number tables in `radiodb.bin` are subject to those organizations' terms. See [NOTICE](NOTICE).
