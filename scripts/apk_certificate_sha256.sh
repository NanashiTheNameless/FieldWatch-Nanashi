#!/usr/bin/env bash
# Reads verbose, verified apksigner output and requires one V3 signer certificate.
set -euo pipefail
certificate_info=$(cat)
if ! printf '%s\n' "$certificate_info" | grep -Eq '^Verified using v3(\.[0-9]+)? scheme .*: true$'; then
    echo 'error: APK Signature Scheme v3 verification is required.' >&2
    exit 1
fi
hashes=$(printf '%s\n' "$certificate_info" | awk '
    /^V3(\.[0-9]+)? Signer.*certificate SHA-256 digest: / {
        sub(/^.*certificate SHA-256 digest: /, "")
        gsub(/[[:space:]:]/, "")
        print tolower($0)
    }
' | sort -u)
if [[ ! "$hashes" =~ ^[0-9a-f]{64}$ ]]; then
    echo 'error: apksigner did not report exactly one valid V3 signing certificate SHA-256 digest.' >&2
    exit 1
fi
printf '%s\n' "$hashes"
