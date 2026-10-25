#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO="${COWBOY_RELEASE_REPO:-DefSteam/Cowboy}"
BRANCH="master"
VERSION_FILE="$ROOT_DIR/runtime/VERSION"
PRIVATE_KEY="${COWBOY_RUNTIME_SIGNING_KEY:-$ROOT_DIR/.secrets/runtime-signing-private.pem}"

for command_name in git gh openssl python3 zip sha256sum base64; do
    if ! command -v "$command_name" >/dev/null 2>&1; then
        echo "Required command not found: $command_name" >&2
        exit 1
    fi
done
if ! gh auth status >/dev/null 2>&1; then
    echo "GitHub CLI is not authenticated. Run 'gh auth login' first." >&2
    exit 1
fi
if [[ ! -f "$PRIVATE_KEY" ]]; then
    echo "Runtime signing key not found at $PRIVATE_KEY." >&2
    echo "Set COWBOY_RUNTIME_SIGNING_KEY to the private key used by this app's embedded public key." >&2
    exit 1
fi
if ! git -C "$ROOT_DIR" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    echo "Run ./release.sh runtime from a Git checkout." >&2
    exit 1
fi
if [[ "$(git -C "$ROOT_DIR" branch --show-current)" != "$BRANCH" ]]; then
    echo "Switch to the $BRANCH branch before releasing a runtime." >&2
    exit 1
fi

VERSION="$(cat "$VERSION_FILE")"
if [[ ! "$VERSION" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    echo "runtime/VERSION must use major.minor.patch format." >&2
    exit 1
fi
MAJOR="${BASH_REMATCH[1]}"
MINOR="${BASH_REMATCH[2]}"
PATCH="${BASH_REMATCH[3]}"
VERSION="$MAJOR.$MINOR.$((PATCH + 1))"
TAG="runtime-v$VERSION"
if gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1; then
    echo "Runtime release $TAG already exists. Update runtime/VERSION and try again." >&2
    exit 1
fi
printf '%s\n' "$VERSION" > "$VERSION_FILE"

git -C "$ROOT_DIR" add -A
if ! git -C "$ROOT_DIR" diff --cached --quiet; then
    git -C "$ROOT_DIR" commit -m "Prepare Cowboy runtime $VERSION"
fi
git -C "$ROOT_DIR" fetch origin "refs/heads/$BRANCH:refs/remotes/origin/$BRANCH"
git -C "$ROOT_DIR" rebase "refs/remotes/origin/$BRANCH"
RELEASE_TARGET="$(git -C "$ROOT_DIR" rev-parse HEAD)"

TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/cowboy-runtime.XXXXXX")"
trap 'rm -rf -- "$TEMP_DIR"' EXIT
PACKAGE_DIR="$TEMP_DIR/package"
mkdir -p "$PACKAGE_DIR" "$ROOT_DIR/build/runtime"
cp -a "$ROOT_DIR/web/." "$PACKAGE_DIR/"
rm -f "$PACKAGE_DIR/README.md" "$PACKAGE_DIR/serve.sh"
printf '{"version":"%s","shellApi":1}\n' "$VERSION" > "$PACKAGE_DIR/runtime-version.json"
BUNDLE="$ROOT_DIR/build/runtime/Cowboy-runtime-$VERSION.zip"
(cd "$PACKAGE_DIR" && zip -qr "$BUNDLE" .)
BUNDLE_HASH="$(sha256sum "$BUNDLE" | cut -d ' ' -f 1)"
BUNDLE_URL="https://github.com/$REPO/releases/download/$TAG/Cowboy-runtime-$VERSION.zip"
VERSIONED_MANIFEST="$ROOT_DIR/build/runtime/Cowboy-runtime-$VERSION.json"
VERSIONED_SIGNATURE="$ROOT_DIR/build/runtime/Cowboy-runtime-$VERSION.json.sig"
INSTALL_PACKAGE="$ROOT_DIR/build/runtime/Cowboy-runtime-$VERSION.cowboy-runtime"
RAW_SIGNATURE="$TEMP_DIR/manifest.sig"

python3 - "$VERSIONED_MANIFEST" "$VERSION" "$BUNDLE_URL" "$BUNDLE_HASH" <<'PY'
import json
import sys

path, version, bundle_url, bundle_hash = sys.argv[1:]
manifest = {
    "bundleUrl": bundle_url,
    "format": "cowboy-runtime",
    "minimumShellApi": 1,
    "sha256": bundle_hash,
    "version": version,
}
with open(path, "w", encoding="utf-8", newline="\n") as output:
    output.write(json.dumps(manifest, sort_keys=True, separators=(",", ":")) + "\n")
PY

openssl dgst -sha256 -sign "$PRIVATE_KEY" -out "$RAW_SIGNATURE" "$VERSIONED_MANIFEST"
base64 -w 0 "$RAW_SIGNATURE" > "$VERSIONED_SIGNATURE"
printf '\n' >> "$VERSIONED_SIGNATURE"

INSTALL_DIR="$TEMP_DIR/install-package"
mkdir -p "$INSTALL_DIR"
cp "$BUNDLE" "$INSTALL_DIR/bundle.zip"
cp "$VERSIONED_MANIFEST" "$INSTALL_DIR/manifest.json"
cp "$VERSIONED_SIGNATURE" "$INSTALL_DIR/manifest.sig"
(cd "$INSTALL_DIR" && zip -q "$INSTALL_PACKAGE" bundle.zip manifest.json manifest.sig)

PUBLIC_KEY="$TEMP_DIR/runtime-signing-public.pem"
openssl pkey -in "$PRIVATE_KEY" -pubout -out "$PUBLIC_KEY" >/dev/null
if ! cmp -s "$PUBLIC_KEY" "$ROOT_DIR/app/src/main/res/raw/runtime_signing_public.pem"; then
    echo "The selected runtime signing key does not match the public key embedded in the app." >&2
    echo "Refusing to publish a runtime that installed apps cannot verify." >&2
    exit 1
fi
openssl dgst -sha256 -verify "$PUBLIC_KEY" -signature "$RAW_SIGNATURE" "$VERSIONED_MANIFEST"
python3 - "$BUNDLE" "$VERSIONED_MANIFEST" "$VERSIONED_SIGNATURE" "$INSTALL_PACKAGE" "$VERSION" "$BUNDLE_HASH" <<'PY'
import hashlib
import json
import sys
import zipfile

bundle_path, manifest_path, signature_path, package_path, version, expected_hash = sys.argv[1:]
manifest_bytes = open(manifest_path, "rb").read()
manifest = json.loads(manifest_bytes)
assert manifest["version"] == version, "manifest version mismatch"
assert manifest["sha256"] == expected_hash, "manifest checksum mismatch"
assert hashlib.sha256(open(bundle_path, "rb").read()).hexdigest() == expected_hash, "runtime ZIP checksum mismatch"
with zipfile.ZipFile(bundle_path) as bundle:
    assert bundle.testzip() is None, "runtime ZIP is corrupt"
    names = {name[2:] if name.startswith("./") else name for name in bundle.namelist()}
    for required in ("index.html", "styles.css", "app.js"):
        assert required in names, f"runtime is missing {required}"
with zipfile.ZipFile(package_path) as package:
    assert package.testzip() is None, "install package is corrupt"
    files = set(package.namelist())
    assert files == {"bundle.zip", "manifest.json", "manifest.sig"}, "install package contents are incomplete"
    assert package.read("bundle.zip") == open(bundle_path, "rb").read(), "install package bundle mismatch"
    assert package.read("manifest.json") == manifest_bytes, "install package manifest mismatch"
    assert package.read("manifest.sig") == open(signature_path, "rb").read(), "install package signature mismatch"
PY

git -C "$ROOT_DIR" push origin "HEAD:$BRANCH"

gh release create "$TAG" "$BUNDLE" "$VERSIONED_MANIFEST" "$VERSIONED_SIGNATURE" "$INSTALL_PACKAGE" \
    --repo "$REPO" --target "$RELEASE_TARGET" \
    --title "Cowboy Runtime $VERSION" --generate-notes

cp "$VERSIONED_MANIFEST" "$ROOT_DIR/runtime/latest.json"
cp "$VERSIONED_SIGNATURE" "$ROOT_DIR/runtime/latest.sig"
git -C "$ROOT_DIR" add runtime/latest.json runtime/latest.sig
git -C "$ROOT_DIR" commit -m "Publish Cowboy runtime $VERSION update manifest"
git -C "$ROOT_DIR" fetch origin "refs/heads/$BRANCH:refs/remotes/origin/$BRANCH"
git -C "$ROOT_DIR" rebase "refs/remotes/origin/$BRANCH"
git -C "$ROOT_DIR" push origin "HEAD:$BRANCH"

echo "Published signed Cowboy runtime $VERSION and its device-installable package. No APK was built or uploaded."
echo "Release: https://github.com/$REPO/releases/tag/$TAG"
