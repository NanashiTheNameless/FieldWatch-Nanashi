#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
parser="$root/scripts/apk_certificate_sha256.sh"
hash=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
other=abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789
verified='Verified using v3 scheme (APK Signature Scheme v3): true'
for prefix in 'V3 Signer' 'V3.0 Signer' 'V3.1 Signer' 'V3.2 Signer'; do
    actual=$(printf '%s\n%s: certificate SHA-256 digest: %s\n' "$verified" "$prefix" "$hash" | bash "$parser")
    test "$actual" = "$hash"
done
actual=$(printf '%s\nV2 Signer: certificate SHA-256 digest: %s\nV3.0 Signer: certificate SHA-256 digest: %s\n' "$verified" "$other" "$hash" | bash "$parser")
test "$actual" = "$hash"
actual=$(printf '%s\nV3.0 Signer: certificate SHA-256 digest: %s\nV3.1 Signer: certificate SHA-256 digest: %s\n' "$verified" "$hash" "$hash" | bash "$parser")
test "$actual" = "$hash"
# Reject V2-only APKs, missing/invalid digests, and ambiguous V3 certificates.
for output in \
    "V2 Signer: certificate SHA-256 digest: $hash" \
    "$verified" \
    "$(printf '%s\nV3.0 Signer: certificate SHA-256 digest: 1234\n' "$verified")" \
    "$(printf '%s\nV3.0 Signer: certificate SHA-256 digest: %s\nV3.1 Signer: certificate SHA-256 digest: %s\n' "$verified" "$hash" "$other")"; do
    if printf '%s\n' "$output" | bash "$parser" 2>/dev/null; then
        echo 'Invalid or missing V3 certificate must fail' >&2; exit 1
    fi
done
printf 'V3 certificate parser checks passed.\n'
