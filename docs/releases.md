# Nightly releases and signing

FieldWatch-Nanashi has nightly builds only. There are no stable releases or version tags.

## How it works

`.github/workflows/nightly.yml` runs on every push to `main` (and on manual dispatch):

1. Runs the unit tests and writes the stock signature pack from `DefaultCatalog`.
2. Builds `assembleRelease` with `versionName` `<x.y.z>-<sha7>` and the time-based `versionCode`.
3. Signs with the release key from repository secrets. The decoded keystore lives in the runner's temp directory and is deleted right after the build.
4. Refuses to publish unless `apksigner` confirms the APK is signed by the certificate pinned in the `RELEASE_CERT_SHA256` repository variable. Every official build uses that one key.
5. Force-moves the `Nightly-Rolling` tag and updates the single `Nightly-Rolling` GitHub release (a normal release marked latest, not a pre-release) with:
   - `FieldWatch-Nanashi-nightly-<yyyy.mm.dd>-<sha7>.apk` (the last 4 are kept)
   - `FieldWatch-Nanashi-nightly.apk` (always the newest; stable download URL)
   - `fieldwatch-signatures-v2.json` (used by **Update stock catalog from GitHub** when Settings → Signatures → **Stock catalog source** is *FieldWatch-Nanashi nightly*, the default; *Upstream Fieldwatch* downloads upstream's `dist/` pack instead)
   - release notes with the APK SHA-256, the certificate SHA-256, and the commits since the previous Nightly-Rolling build

`.github/workflows/android.yml` runs tests and a debug build on pull requests.

Install and update with Obtainium: add `https://github.com/NanashiTheNameless/FieldWatch-Nanashi` (no pre-release setting needed).

## The release key

One key for every official build, forever. Android refuses an update signed by a different key, so losing or replacing it means uninstalling (and losing app data) on every phone. Keep at least two offline backups of the keystore and its password. Never commit it.

### 1. Generate it (once)

```bash
mkdir -p ~/.android-keys && chmod 700 ~/.android-keys
keytool -genkeypair -v -storetype PKCS12 \
  -keystore ~/.android-keys/fieldwatch-nanashi-release.jks \
  -alias fieldwatch-nanashi -keyalg RSA -keysize 4096 -validity 36500 \
  -dname "CN=NamelessNanashi"
```

With PKCS12 the key password is the keystore password.

### 2. Read the certificate fingerprint

```bash
keytool -list -v -keystore ~/.android-keys/fieldwatch-nanashi-release.jks \
  -alias fieldwatch-nanashi | grep 'SHA256:'
```

The fingerprint is public. Colons and case do not matter to the workflow.

### 3. Store it in GitHub

Run these yourself in a terminal. `gh secret set` without `--body` prompts for the value without echoing it.

```bash
REPO=NanashiTheNameless/FieldWatch-Nanashi
base64 -w 0 ~/.android-keys/fieldwatch-nanashi-release.jks | gh secret set RELEASE_KEYSTORE -R "$REPO"
gh secret set RELEASE_KEYSTORE_PASSWORD -R "$REPO"
gh secret set RELEASE_KEY_PASSWORD -R "$REPO"
gh secret set RELEASE_KEY_ALIAS -R "$REPO" --body fieldwatch-nanashi
gh variable set RELEASE_CERT_SHA256 -R "$REPO" --body '<SHA256 fingerprint from step 2>'
```

If `RELEASE_CERT_SHA256` is missing, the workflow fails and prints the fingerprint it found, so you can check it and set the variable.

### 4. Local signed builds (optional)

Create `../fieldwatch-nanashi-keystore.properties` next to the repository (outside it):

```properties
storeFile=/home/<you>/.android-keys/fieldwatch-nanashi-release.jks
storePassword=...
keyAlias=fieldwatch-nanashi
keyPassword=...
```

`./build.sh release` then signs with it and prints the certificate. Environment variables `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` override the file. With neither, the release build is unsigned. Debug builds always use the local debug key and install separately as `dev.namelessnanashi.fieldwatch.debug`.

`./build.sh installRelease` runs the unit tests, builds the release APK, verifies and prints its signing certificate, then installs it on the connected phone with `adb install -r`. It requires a configured signing key and an authorized adb device. Android requires the same signing key as any installed release to preserve its data. Extra Gradle arguments are passed through, for example `./build.sh installRelease --offline`.

## Version numbers

- `versionCode` is seconds since 2026-01-01T00:00:00Z, read at build time. It always increases and fits in an `Int` until 2094-01-19. Do not move that epoch backwards.
- `versionName` is `<x.y.z>-localbuild` locally and `<x.y.z>-<sha7>` in CI (`-PversionSuffix=`). Debug adds `-debug`.
