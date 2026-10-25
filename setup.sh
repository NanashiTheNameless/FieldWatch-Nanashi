#!/usr/bin/env bash
# Finds the JDK named in .java-version and an Android SDK with the packages this
# project needs, installs only what is missing, and writes local.properties.
#
#   ./setup.sh                    check and report
#   eval "$(./setup.sh --env)"    same, and export JAVA_HOME / ANDROID_HOME into this shell
#                                 (any shell; build.sh does this for you)
#
# An existing SDK is reused (ANDROID_HOME, ANDROID_SDK_ROOT, local.properties,
# ~/Android/Sdk), preferring one that already has every package.
# ./.android-sdk is only created when none of those exist.

_fw_setup() {
    local root_dir sdk_fallback java_major java_home sdk_dir candidate sdkmanager
    root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    sdk_fallback="$root_dir/.android-sdk"
    java_major="$(tr -d '[:space:]' < "$root_dir/.java-version")"
    local -a sdk_packages=("platform-tools" "platforms;android-37.0" "build-tools;37.0.0")

    say() { [ -n "${FW_SETUP_QUIET:-}" ] || echo "$@"; }

    java_version_of() {
        "$1/bin/java" -XshowSettings:properties -version 2>&1 \
            | sed -n 's/^ *java\.specification\.version = //p' | head -n 1
    }

    java_home=""
    for candidate in \
        "${JAVA_HOME:-}" \
        "/usr/lib/jvm/zulu${java_major}" \
        "/usr/lib/jvm/zulu${java_major}-ca-amd64" \
        "/usr/lib/jvm/zing-jdk${java_major}" \
        "/usr/lib/jvm/java-${java_major}-openjdk-amd64" \
        "/usr/lib/jvm/temurin-${java_major}-jdk-amd64" \
        "/usr/lib/jvm/openjdk-${java_major}" \
        "/usr/lib/jvm/java-${java_major}-openjdk"
    do
        [ -n "$candidate" ] && [ -x "$candidate/bin/java" ] || continue
        if [ "$(java_version_of "$candidate")" = "$java_major" ]; then
            java_home="$candidate"
            break
        fi
    done
    if [ -z "$java_home" ]; then
        echo "error: JDK ${java_major} not found. Install it or set JAVA_HOME to a JDK ${java_major}." >&2
        return 1
    fi

    sdk_has_all() {
        [ -d "$1/platform-tools" ] && [ -d "$1/platforms/android-37.0" ] && [ -d "$1/build-tools/37.0.0" ]
    }

    # First SDK that already has every package wins; otherwise the first existing SDK
    # gets the missing packages installed.
    local -a sdk_candidates=(
        "${ANDROID_HOME:-}"
        "${ANDROID_SDK_ROOT:-}"
        "$( [ -f "$root_dir/local.properties" ] && sed -n 's/^sdk\.dir=//p' "$root_dir/local.properties" | head -n 1)"
        "$HOME/Android/Sdk"
        "$sdk_fallback"
    )
    sdk_dir=""
    for candidate in "${sdk_candidates[@]}"; do
        if [ -n "$candidate" ] && sdk_has_all "$candidate"; then
            sdk_dir="$candidate"
            break
        fi
    done
    if [ -z "$sdk_dir" ]; then
        for candidate in "${sdk_candidates[@]}"; do
            if [ -n "$candidate" ] && { [ -d "$candidate/platforms" ] || [ -d "$candidate/cmdline-tools" ]; }; then
                sdk_dir="$candidate"
                break
            fi
        done
    fi
    [ -n "$sdk_dir" ] || sdk_dir="$sdk_fallback"

    local -a missing=()
    [ -d "$sdk_dir/platform-tools" ] || missing+=("platform-tools")
    [ -d "$sdk_dir/platforms/android-37.0" ] || missing+=("platforms;android-37.0")
    [ -d "$sdk_dir/build-tools/37.0.0" ] || missing+=("build-tools;37.0.0")

    if [ "${#missing[@]}" -gt 0 ]; then
        sdkmanager=""
        for candidate in \
            "$sdk_dir/cmdline-tools/latest/bin/sdkmanager" \
            "$(command -v sdkmanager 2>/dev/null || true)"
        do
            if [ -n "$candidate" ] && [ -x "$candidate" ]; then
                sdkmanager="$candidate"
                break
            fi
        done
        if [ -z "$sdkmanager" ]; then
            echo "error: missing SDK packages (${missing[*]}) in $sdk_dir and no sdkmanager found." >&2
            echo "Install the Android command-line tools into $sdk_dir/cmdline-tools/latest and re-run." >&2
            return 1
        fi
        mkdir -p "$sdk_dir"
        echo "Installing ${missing[*]} into $sdk_dir"
        yes | JAVA_HOME="$java_home" "$sdkmanager" --sdk_root="$sdk_dir" --licenses >/dev/null || true
        JAVA_HOME="$java_home" "$sdkmanager" --sdk_root="$sdk_dir" "${sdk_packages[@]}"
    fi

    printf 'sdk.dir=%s\n' "$sdk_dir" > "$root_dir/local.properties"

    export JAVA_HOME="$java_home"
    export ANDROID_HOME="$sdk_dir"
    export ANDROID_SDK_ROOT="$sdk_dir"
    case ":$PATH:" in
        *":$JAVA_HOME/bin:"*) ;;
        *) export PATH="$JAVA_HOME/bin:$sdk_dir/platform-tools:$PATH" ;;
    esac

    say "JDK      $JAVA_HOME ($(java_version_of "$JAVA_HOME"))"
    say "SDK      $ANDROID_HOME"
    say "Packages ${sdk_packages[*]}"
}

if [ "${BASH_SOURCE[0]}" != "$0" ]; then
    _fw_setup
elif [ "${1:-}" = "--env" ]; then
    set -eu
    FW_SETUP_QUIET=1 _fw_setup >&2
    printf 'export JAVA_HOME=%q\nexport ANDROID_HOME=%q\nexport ANDROID_SDK_ROOT=%q\nexport PATH=%q\n' \
        "$JAVA_HOME" "$ANDROID_HOME" "$ANDROID_SDK_ROOT" "$PATH"
else
    set -eu
    _fw_setup
fi
