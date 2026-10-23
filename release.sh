#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO="${COWBOY_RELEASE_REPO:-DefSteam/Cowboy}"
APK="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"

if ! command -v gh >/dev/null 2>&1; then
    echo "GitHub CLI (gh) is required. Install it and run 'gh auth login' first." >&2
    exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
    echo "GitHub CLI is not authenticated. Run 'gh auth login' and try again." >&2
    exit 1
fi

VERSION_NAME="$(sed -n 's/^[[:space:]]*versionName = "\([^"]*\)".*/\1/p' "$ROOT_DIR/app/build.gradle.kts" | head -n 1)"
if [[ -z "$VERSION_NAME" ]]; then
    echo "Could not read versionName from app/build.gradle.kts." >&2
    exit 1
fi
TAG="v$VERSION_NAME"

if gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1; then
    echo "Release $TAG already exists in $REPO. Bump versionName in app/build.gradle.kts first." >&2
    exit 1
fi

"$ROOT_DIR/build.sh" :app:assembleDebug
if [[ ! -f "$APK" ]]; then
    echo "Build finished but APK was not found at $APK." >&2
    exit 1
fi

gh release create "$TAG" "$APK#Cowboy.apk" \
    --repo "$REPO" \
    --target main \
    --title "Cowboy $VERSION_NAME" \
    --generate-notes

echo "Published Cowboy $VERSION_NAME to https://github.com/$REPO/releases/tag/$TAG"
