#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO="${COWBOY_RELEASE_REPO:-DefSteam/Cowboy}"
SOURCE_BRANCH="master"
APK="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
PURGE_EXISTING_RELEASES=0
case "${1:-}" in
    "") ;;
    --purge-existing-releases) PURGE_EXISTING_RELEASES=1 ;;
    *) echo "Usage: $0 [--purge-existing-releases]" >&2; exit 2 ;;
esac

if ! command -v gh >/dev/null 2>&1; then
    echo "GitHub CLI (gh) is required. Install it and run 'gh auth login' first." >&2
    exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
    echo "GitHub CLI is not authenticated. Run 'gh auth login' and try again." >&2
    exit 1
fi

push_local_changes() {
    if git -C "$ROOT_DIR" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
        PUSH_WORKTREE="$ROOT_DIR"
    else
        echo "No Git checkout found here; preparing a temporary clone of $REPO."
        TEMP_PUSH_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/cowboy-release.XXXXXX")"
        PUSH_WORKTREE="$TEMP_PUSH_ROOT/repo"
        gh repo clone "$REPO" "$PUSH_WORKTREE" -- --branch "$SOURCE_BRANCH"
        while IFS= read -r -d '' tracked_file; do
            if [[ ! -e "$ROOT_DIR/$tracked_file" ]]; then
                git -C "$PUSH_WORKTREE" rm -q -- "$tracked_file"
            fi
        done < <(git -C "$PUSH_WORKTREE" ls-files -z)
        tar -C "$ROOT_DIR" \
            --exclude='./.git' --exclude='./.gradle' --exclude='./build' \
            --exclude='*/build' --exclude='./local.properties' --exclude='./.idea' \
            --exclude='./.aws' --exclude='./.codex' --exclude='./.agents' \
            --exclude='./.ssh' --exclude='./.config' --exclude='./.env*' \
            -cf - . | tar -C "$PUSH_WORKTREE" -xf -
    fi
    if ! git -C "$PUSH_WORKTREE" remote get-url origin >/dev/null 2>&1; then
        echo "Git remote 'origin' is not configured." >&2
        exit 1
    fi

    git -C "$PUSH_WORKTREE" add -A
    if ! git -C "$PUSH_WORKTREE" diff --cached --quiet; then
        git -C "$PUSH_WORKTREE" commit -m "Prepare Cowboy $VERSION_NAME release"
    fi
    git -C "$PUSH_WORKTREE" push origin "HEAD:$SOURCE_BRANCH"
    RELEASE_TARGET="$(git -C "$PUSH_WORKTREE" rev-parse HEAD)"
    echo "Pushed source to origin/$SOURCE_BRANCH at $RELEASE_TARGET."
    SOURCE_PUSHED=1
}

SOURCE_PUSHED=0
TEMP_PUSH_ROOT=""
trap 'if [[ -n "$TEMP_PUSH_ROOT" ]]; then rm -rf -- "$TEMP_PUSH_ROOT"; fi' EXIT

VERSION_NAME="$(sed -n 's/^[[:space:]]*versionName = "\([^"]*\)".*/\1/p' "$ROOT_DIR/app/build.gradle.kts" | head -n 1)"
if [[ -z "$VERSION_NAME" ]]; then
    echo "Could not read versionName from app/build.gradle.kts." >&2
    exit 1
fi
TAG="v$VERSION_NAME"

bump_version() {
    if [[ ! "$VERSION_NAME" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
        echo "Automatic bumping requires a versionName in major.minor.patch format." >&2
        exit 1
    fi
    local major="${BASH_REMATCH[1]}"
    local minor="${BASH_REMATCH[2]}"
    local patch="${BASH_REMATCH[3]}"
    local version_code
    version_code="$(sed -n 's/^[[:space:]]*versionCode = \([0-9][0-9]*\).*/\1/p' "$ROOT_DIR/app/build.gradle.kts" | head -n 1)"
    if [[ ! "$version_code" =~ ^[0-9]+$ ]]; then
        echo "Could not read versionCode from app/build.gradle.kts." >&2
        exit 1
    fi
    local next_version_name="$major.$minor.$((patch + 1))"
    local next_version_code="$((version_code + 1))"
    local gradle_file="$ROOT_DIR/app/build.gradle.kts"
    sed -i.bak \
        -e "s/^\([[:space:]]*versionCode = \)[0-9][0-9]*/\1$next_version_code/" \
        -e "s/^\([[:space:]]*versionName = \)\"[^\"]*\"/\1\"$next_version_name\"/" \
        "$gradle_file"
    rm -f "$gradle_file.bak"
    VERSION_NAME="$next_version_name"
    TAG="v$VERSION_NAME"
    echo "Updated app/build.gradle.kts to version $VERSION_NAME (versionCode $next_version_code)."
}

if [[ "$PURGE_EXISTING_RELEASES" == "1" ]]; then
    OLD_RELEASE_TAGS_OUTPUT="$(gh release list --repo "$REPO" --limit 1000 --json tagName --jq '.[].tagName')"
    OLD_RELEASE_TAGS=()
    if [[ -n "$OLD_RELEASE_TAGS_OUTPUT" ]]; then
        mapfile -t OLD_RELEASE_TAGS <<< "$OLD_RELEASE_TAGS_OUTPUT"
    fi
    if [[ "${#OLD_RELEASE_TAGS[@]}" -gt 0 ]]; then
        bump_version
    fi
    push_local_changes
    for old_tag in "${OLD_RELEASE_TAGS[@]}"; do
        echo "Deleting release $old_tag and its Git tag..."
        gh release delete "$old_tag" --repo "$REPO" --cleanup-tag --yes
    done
    echo "Removed ${#OLD_RELEASE_TAGS[@]} existing releases."
fi

if gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1; then
    echo "Release $TAG already exists in $REPO."
    if [[ ! -t 0 ]]; then
        echo "Run this script in a terminal to choose whether to bump the version." >&2
        exit 1
    fi
    read -r -p "Bump to the next patch version and increment versionCode [y], or cancel [N]? " answer
    case "$answer" in
        y|Y|yes|YES|Yes) ;;
        *) echo "Release cancelled. No version changes were made."; exit 1 ;;
    esac

    bump_version
    if gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1; then
        echo "Release $TAG already exists too. Choose a higher versionName and try again." >&2
        exit 1
    fi
fi

if [[ "$SOURCE_PUSHED" == "0" ]]; then push_local_changes; fi
"$ROOT_DIR/build.sh" :app:assembleDebug
if [[ ! -f "$APK" ]]; then
    echo "Build finished but APK was not found at $APK." >&2
    exit 1
fi

VERSIONED_APK="$ROOT_DIR/app/build/outputs/apk/debug/Cowboy-$VERSION_NAME.apk"
cp -- "$APK" "$VERSIONED_APK"
gh release create "$TAG" "$VERSIONED_APK" \
    --repo "$REPO" \
    --target "$RELEASE_TARGET" \
    --title "Cowboy $VERSION_NAME" \
    --generate-notes
rm -f "$VERSIONED_APK"

echo "Published Cowboy $VERSION_NAME to https://github.com/$REPO/releases/tag/$TAG"
