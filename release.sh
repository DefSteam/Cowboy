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
    echo "Release $TAG already exists in $REPO."
    if [[ ! -t 0 ]]; then
        echo "Run this script in a terminal to add a compatibility APK or bump the version." >&2
        exit 1
    fi
    read -r -p "Add Cowboy.apk to this release for older app builds [a], bump version [b], or cancel [c]? " answer
    case "$answer" in
        a|A)
            "$ROOT_DIR/build.sh" :app:assembleDebug
            if [[ ! -f "$APK" ]]; then
                echo "Build finished but APK was not found at $APK." >&2
                exit 1
            fi
            gh release upload "$TAG" "$APK#Cowboy.apk" --repo "$REPO"
            echo "Added Cowboy.apk compatibility asset to release $TAG."
            exit 0
            ;;
        b|B|y|Y|yes|YES|Yes) ;;
        *) echo "Release cancelled. No version changes were made."; exit 1 ;;
    esac

    if [[ ! "$VERSION_NAME" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
        echo "Automatic bumping requires a versionName in major.minor.patch format." >&2
        exit 1
    fi
    MAJOR="${BASH_REMATCH[1]}"
    MINOR="${BASH_REMATCH[2]}"
    PATCH="${BASH_REMATCH[3]}"
    VERSION_CODE="$(sed -n 's/^[[:space:]]*versionCode = \([0-9][0-9]*\).*/\1/p' "$ROOT_DIR/app/build.gradle.kts" | head -n 1)"
    if [[ ! "$VERSION_CODE" =~ ^[0-9]+$ ]]; then
        echo "Could not read versionCode from app/build.gradle.kts." >&2
        exit 1
    fi
    NEXT_VERSION_NAME="$MAJOR.$MINOR.$((PATCH + 1))"
    NEXT_VERSION_CODE="$((VERSION_CODE + 1))"
    GRADLE_FILE="$ROOT_DIR/app/build.gradle.kts"
    sed -i.bak \
        -e "s/^\([[:space:]]*versionCode = \)[0-9][0-9]*/\1$NEXT_VERSION_CODE/" \
        -e "s/^\([[:space:]]*versionName = \)\"[^\"]*\"/\1\"$NEXT_VERSION_NAME\"/" \
        "$GRADLE_FILE"
    rm -f "$GRADLE_FILE.bak"
    VERSION_NAME="$NEXT_VERSION_NAME"
    TAG="v$VERSION_NAME"
    echo "Updated app/build.gradle.kts to version $VERSION_NAME (versionCode $NEXT_VERSION_CODE)."
    if gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1; then
        echo "Release $TAG already exists too. Choose a higher versionName and try again." >&2
        exit 1
    fi
fi

"$ROOT_DIR/build.sh" :app:assembleDebug
if [[ ! -f "$APK" ]]; then
    echo "Build finished but APK was not found at $APK." >&2
    exit 1
fi

gh release create "$TAG" "$APK#Cowboy-$VERSION_NAME.apk" "$APK#Cowboy.apk" \
    --repo "$REPO" \
    --target main \
    --title "Cowboy $VERSION_NAME" \
    --generate-notes

echo "Published Cowboy $VERSION_NAME to https://github.com/$REPO/releases/tag/$TAG"
