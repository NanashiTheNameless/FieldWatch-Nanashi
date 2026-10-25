#!/usr/bin/env bash
# Sets up the JDK and SDK (see setup.sh), then runs Gradle.
#
#   ./build.sh            unit tests + debug APK
#   ./build.sh install    unit tests + install the debug build on the connected phone
#   ./build.sh release    unit tests + release APK, then show its signing certificate
#   ./build.sh installRelease  unit tests + signed release APK + install on the connected phone
#   ./build.sh <tasks>    any Gradle tasks and flags, passed through
#
# The Gradle daemon stays running between builds, so repeat builds are much faster.

set -eu

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT_DIR"

FW_SETUP_QUIET=1 . "$ROOT_DIR/setup.sh"

BUILD_TOOLS_DIR="$ANDROID_HOME/build-tools/37.0.0"

case "${1:-}" in
    "")
        exec ./gradlew testDebugUnitTest assembleDebug
        ;;
    install)
        shift
        if ! adb get-state >/dev/null 2>&1; then
            echo "error: no phone connected over adb (adb devices shows nothing ready)." >&2
            exit 1
        fi
        exec ./gradlew testDebugUnitTest installDebug "$@"
        ;;
    release|installRelease)
        action="$1"
        shift
        if [ "$action" = installRelease ] && ! adb get-state >/dev/null 2>&1; then
            echo "error: no phone connected over adb (adb devices shows nothing ready)." >&2
            exit 1
        fi
        ./gradlew testDebugUnitTest assembleRelease "$@"
        apk="app/build/outputs/apk/release/app-release.apk"
        if [ ! -f "$apk" ]; then
            if [ "$action" = installRelease ]; then
                echo "error: release APK is unsigned; configure a signing key (see docs/releases.md)." >&2
                exit 1
            fi
            echo "Release APK is unsigned: no signing key configured (see docs/releases.md)."
            ls app/build/outputs/apk/release/*.apk
            exit 0
        fi
        echo "APK: $apk"
        echo "APK SHA-256: $(sha256sum "$apk" | cut -d' ' -f1)"
        certificate_info=$("$BUILD_TOOLS_DIR/apksigner" -J-enable-native-access=ALL-UNNAMED verify --min-sdk-version 31 --print-certs "$apk")
        printf '%s\n' "$certificate_info" | grep -E 'certificate (DN|SHA-256)'
        if [ "$action" = installRelease ]; then
            adb install -r "$apk"
        fi
        ;;
    *)
        exec ./gradlew "$@"
        ;;
esac
