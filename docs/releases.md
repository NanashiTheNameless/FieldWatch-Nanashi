# Nightly releases and signing

FieldWatch-Nanashi publishes nightly APK builds. The separate `Data-Assets` release holds downloadable data; it is neither latest nor a pre-release.

## How it works

`.github/workflows/nightly.yml` runs on every push to `main` (and on manual dispatch):

1. Runs the unit tests.
2. Builds `assembleRelease` with `versionName` `<x.y.z>-<sha7>` and the time-based `versionCode`.
3. Signs with the release key from repository secrets. The decoded keystore lives in the runner's temp directory and is deleted right after the build.
4. Refuses to publish unless `apksigner` verifies APK Signature Scheme v3 and its V3 signer certificate matches the SHA-256 pinned in the `RELEASE_CERT_SHA256` repository variable. Every official build uses that one key.
5. Force-moves the `Nightly-Rolling` tag and updates the single `Nightly-Rolling` GitHub release (a normal release marked latest, not a pre-release) with:
   - `FieldWatch-Nanashi-nightly-<yyyy.mm.dd>-<sha7>.apk` (the current build and five previous dated APKs are kept)
   - `FieldWatch-Nanashi-nightly.apk` (always the newest; stable download URL)
   - `FieldWatch-Nanashi-obtainium.json` (app-specific Obtainium import configuration)
   - release notes with a headerless APK/hash table and no generated commit list

`.github/workflows/data-assets.yml` independently regenerates the fork catalog, refreshes the optional upstream exports, and publishes the Obtainium config to `Data-Assets`. It runs daily at 04:17 UTC, on relevant source changes, and on manual dispatch. Unchanged assets retain their IDs and download dates. The data release is neither latest nor a pre-release. See [Data assets](data-assets.md).

The certificate parser accepts build-tools 37's `V3.0 Signer` label, requires a successful v3 verification result, and refuses missing or conflicting V3 digests. Local release verification uses the same parser.

`.github/workflows/android.yml` runs tests and a debug build on pull requests.

Install and update with Obtainium: add `https://github.com/NanashiTheNameless/FieldWatch-Nanashi` (no pre-release setting needed).

## The release key

One key for every official build, forever. Android refuses an update signed by a different key, so losing or replacing it means uninstalling (and losing app data) on every phone. Keep at least two offline backups of the keystore and its password. Never commit it.

The steps below set up a key for a repository you maintain, such as your own fork. Replace `<owner>/<repo>`, `<alias>`, and `<name>` with your own values.

### 1. Generate it (once)

```bash
mkdir -p ~/.android-keys && chmod 700 ~/.android-keys
keytool -genkeypair -v -storetype PKCS12 \
  -keystore ~/.android-keys/release.jks \
  -alias <alias> -keyalg RSA -keysize 4096 -validity 36500 \
  -dname "CN=<name>"
```

With PKCS12 the key password is the keystore password.

### 2. Read the certificate fingerprint

```bash
keytool -list -v -keystore ~/.android-keys/release.jks \
  -alias <alias> | grep 'SHA256:'
```

The fingerprint is public. Colons and case do not matter to the workflow.

### 3. Store it in GitHub

Run these in a terminal. `gh secret set` without `--body` prompts for the value without echoing it.

```bash
REPO=<owner>/<repo>
base64 -w 0 ~/.android-keys/release.jks | gh secret set RELEASE_KEYSTORE -R "$REPO"
gh secret set RELEASE_KEYSTORE_PASSWORD -R "$REPO"
gh secret set RELEASE_KEY_PASSWORD -R "$REPO"
gh secret set RELEASE_KEY_ALIAS -R "$REPO" --body <alias>
gh variable set RELEASE_CERT_SHA256 -R "$REPO" --body '<SHA256 fingerprint from step 2>'
```

If `RELEASE_CERT_SHA256` is missing, the workflow fails and prints the fingerprint it found, so you can check it and set the variable.

### 4. Local signed builds (optional)

Create `../fieldwatch-nanashi-keystore.properties` next to the repository (outside it). The build reads that exact filename:

```properties
storeFile=/home/<user>/.android-keys/release.jks
storePassword=...
keyAlias=<alias>
keyPassword=...
```

`./build.sh release` then signs with it and prints the certificate. Environment variables `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` override the file. With neither, the release build is unsigned. Debug builds always use the local debug key and install separately as `dev.namelessnanashi.fieldwatch.debug`.

`./build.sh installRelease` runs the unit tests, builds the release APK, verifies and prints its signing certificate, then installs it on the connected phone with `adb install -r`. It requires a configured signing key and an authorized adb device. Android requires the same signing key as any installed release to preserve its data. Extra Gradle arguments are passed through, for example `./build.sh installRelease --offline`.

## Version numbers

- `versionCode` is seconds since 2026-01-01T00:00:00Z, read at build time. It always increases and fits in an `Int` until 2094-01-19. Do not move that epoch backwards.
- `versionName` is `<x.y.z>-localbuild` locally and `<x.y.z>-<sha7>` in CI (`-PversionSuffix=`). Debug adds `-debug`.

## Dependency updates

`.github/dependabot.yml` combines scheduled Gradle and GitHub Actions dependency updates into one weekly `dependencies` pull request using a multi-ecosystem group. Security alerts remain enabled and can trigger separate security update pull requests. Updates are not automatically merged.

Some AGP build tools pull in libraries that Dependabot cannot update when they are only transitive dependencies. Their patched versions are declared explicitly in the root `buildscript` classpath and constrained separately in the app's `androidLintTool` configuration. These are build-tool dependencies, not additions to the APK runtime. Keep dependency alerts enabled; a refreshed dependency graph is needed after changes. GitHub attributes submitted Gradle dependencies to `settings.gradle.kts` regardless of where they are declared.
