#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO="${COWBOY_RELEASE_REPO:-DefSteam/Cowboy}"
SOURCE_BRANCH="master"
MODE="${1:-auto}"
AUTO_MODE=0
AUTO_RUNTIME_AFTER_APP=0
if [[ "$MODE" == "runtime" ]]; then
    shift
    exec "$ROOT_DIR/release-runtime.sh" "$@"
fi
if [[ "$MODE" == "auto" ]]; then
    AUTO_MODE=1
    if [[ "${1:-}" == "auto" ]]; then shift; fi
elif [[ "$MODE" == "app" || "$MODE" == "apk" ]]; then
    if [[ "${1:-}" == "$MODE" ]]; then shift; fi
elif [[ "$MODE" == "--purge-existing-releases" ]]; then
    MODE="app"
else
    echo "Usage: $0 [app|runtime|--purge-existing-releases]" >&2
    exit 2
fi
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

latest_release_tag() {
    local prefix="$1"
    gh release list --repo "$REPO" --limit 1000 --json tagName,isDraft,isPrerelease \
        --jq ".[] | select(.isDraft == false and .isPrerelease == false) | .tagName | select(test(\"^${prefix}[0-9]+[.][0-9]+[.][0-9]+$\"))" \
        | sort -V | tail -n 1
}

changed_files_since_tag() {
    local tag="$1"
    local ref="$tag"
    if ! git -C "$ROOT_DIR" rev-parse "$tag^{commit}" >/dev/null 2>&1; then
        git -C "$ROOT_DIR" fetch --quiet origin "refs/tags/$tag:refs/tags/$tag"
    fi
    ref="$(git -C "$ROOT_DIR" rev-parse "$tag^{commit}")"
    {
        git -C "$ROOT_DIR" diff --name-only "$ref"...HEAD
        git -C "$ROOT_DIR" diff --name-only
        git -C "$ROOT_DIR" diff --cached --name-only
        git -C "$ROOT_DIR" ls-files --others --exclude-standard
    } | sort -u
}

if [[ "$AUTO_MODE" == "1" ]]; then
    if ! git -C "$ROOT_DIR" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
        echo "Cannot auto-detect release type without a Git checkout. Use ./release.sh app or ./release.sh runtime."
        exit 2
    fi
    APP_TAG="$(latest_release_tag v)"
    RUNTIME_TAG="$(latest_release_tag runtime-v)"
    NEED_APP=0
    NEED_RUNTIME=0
    if [[ -z "$APP_TAG" ]]; then
        echo "No previous APK release was found; an app release is required for the first rollout."
        NEED_APP=1
    else
        APP_CHANGES="$(changed_files_since_tag "$APP_TAG")"
        if printf '%s\n' "$APP_CHANGES" | grep -Eq '^(app/|build\.sh$|build\.gradle\.kts$|settings\.gradle\.kts$|gradle\.properties$|gradle/|gradlew$|gradlew\.bat$)'; then
            NEED_APP=1
        fi
        if [[ -n "$APP_CHANGES" ]] && printf '%s\n' "$APP_CHANGES" | grep -Eq '^web/'; then NEED_RUNTIME=1; fi
    fi
    if [[ -z "$RUNTIME_TAG" ]]; then
        NEED_RUNTIME=1
    else
        RUNTIME_CHANGES="$(changed_files_since_tag "$RUNTIME_TAG")"
        if printf '%s\n' "$RUNTIME_CHANGES" | grep -Eq '^web/'; then NEED_RUNTIME=1; fi
    fi

    if [[ "$NEED_APP" == "1" ]]; then
        if [[ ! -t 0 ]]; then
            echo "Android shell changes require an APK release. Run this command in a terminal to approve or cancel." >&2
            exit 1
        fi
        read -r -p "Android shell changes detected. Build and publish an updated APK? [y/N] " answer
        case "$answer" in
            y|Y|yes|YES|Yes) MODE="app"; AUTO_RUNTIME_AFTER_APP="$NEED_RUNTIME" ;;
            *)
                if [[ "$NEED_RUNTIME" == "1" ]]; then
                    echo "Skipping the APK. Publishing the runtime update instead."
                    exec "$ROOT_DIR/release-runtime.sh"
                fi
                echo "Release cancelled. No runtime changes were detected."
                exit 1
                ;;
        esac
    elif [[ "$NEED_RUNTIME" == "1" ]]; then
        echo "Only runtime files changed since the latest release; publishing a runtime update."
        exec "$ROOT_DIR/release-runtime.sh"
    else
        echo "No Android shell or runtime changes were found since the latest releases."
        exit 0
    fi
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
    git -C "$PUSH_WORKTREE" fetch origin "refs/heads/$SOURCE_BRANCH:refs/remotes/origin/$SOURCE_BRANCH"
    if ! git -C "$PUSH_WORKTREE" rebase "refs/remotes/origin/$SOURCE_BRANCH"; then
        echo "Could not rebase local changes onto origin/$SOURCE_BRANCH. Resolve the rebase conflicts, then rerun release.sh." >&2
        exit 1
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
    OLD_RELEASE_TAGS_OUTPUT="$(gh release list --repo "$REPO" --limit 1000 --json tagName --jq '.[].tagName' | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' || true)"
    OLD_RELEASE_TAGS=()
    if [[ -n "$OLD_RELEASE_TAGS_OUTPUT" ]]; then
        mapfile -t OLD_RELEASE_TAGS <<< "$OLD_RELEASE_TAGS_OUTPUT"
    fi
    if [[ "${#OLD_RELEASE_TAGS[@]}" -gt 0 ]]; then
        bump_version
    fi
    echo "Existing releases will be removed only after the replacement APK builds and passes validation."
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

"$ROOT_DIR/build.sh" :app:assembleDebug
if [[ ! -f "$APK" ]]; then
    echo "Build finished but APK was not found at $APK." >&2
    exit 1
fi

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK_ROOT" || ! -d "$SDK_ROOT/build-tools" ]]; then
    echo "Could not find Android SDK Build Tools to validate the APK. Set ANDROID_HOME or ANDROID_SDK_ROOT." >&2
    exit 1
fi
BUILD_TOOLS_DIR="$(find "$SDK_ROOT/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"
APKSIGNER="$BUILD_TOOLS_DIR/apksigner"
APK_ANALYZER="$(find "$SDK_ROOT/cmdline-tools" -type f -path '*/bin/apkanalyzer' 2>/dev/null | sort -V | tail -n 1)"
if [[ ! -r "$APKSIGNER" || -z "$APK_ANALYZER" || ! -r "$APK_ANALYZER" ]]; then
    echo "Android SDK is missing readable apksigner or apkanalyzer tools; refusing to publish an unverified APK." >&2
    exit 1
fi
if [[ -x "$APKSIGNER" ]]; then
    APKSIGNER_COMMAND=("$APKSIGNER")
else
    # Some shared SDK installs leave the shell wrapper non-executable. It only needs Bash and its adjacent JAR.
    APKSIGNER_COMMAND=(bash "$APKSIGNER")
fi
"${APKSIGNER_COMMAND[@]}" verify "$APK" >/dev/null
VERSION_CODE="$(sed -n 's/^[[:space:]]*versionCode = \([0-9][0-9]*\).*/\1/p' "$ROOT_DIR/app/build.gradle.kts" | head -n 1)"
APK_PACKAGE="$(sh "$APK_ANALYZER" manifest application-id "$APK")"
APK_VERSION_NAME="$(sh "$APK_ANALYZER" manifest version-name "$APK")"
APK_VERSION_CODE="$(sh "$APK_ANALYZER" manifest version-code "$APK")"
if [[ "$APK_PACKAGE" != "com.cowboy.app" || "$APK_VERSION_NAME" != "$VERSION_NAME" || "$APK_VERSION_CODE" != "$VERSION_CODE" ]]; then
    echo "Built APK identity/version does not match app/build.gradle.kts; refusing to publish." >&2
    exit 1
fi

VERSIONED_APK="$ROOT_DIR/app/build/outputs/apk/debug/Cowboy-$VERSION_NAME.apk"
cp -- "$APK" "$VERSIONED_APK"
if [[ "$SOURCE_PUSHED" == "0" ]]; then push_local_changes; fi
if [[ "$PURGE_EXISTING_RELEASES" == "1" ]]; then
    for old_tag in "${OLD_RELEASE_TAGS[@]}"; do
        echo "Deleting release $old_tag and its Git tag..."
        gh release delete "$old_tag" --repo "$REPO" --cleanup-tag --yes
    done
    echo "Removed ${#OLD_RELEASE_TAGS[@]} existing releases."
fi
gh release create "$TAG" "$VERSIONED_APK" \
    --repo "$REPO" \
    --target "$RELEASE_TARGET" \
    --title "Cowboy $VERSION_NAME" \
    --generate-notes
rm -f "$VERSIONED_APK"

echo "Published Cowboy $VERSION_NAME to https://github.com/$REPO/releases/tag/$TAG"
if [[ "$AUTO_RUNTIME_AFTER_APP" == "1" ]]; then
    echo "Web runtime changes were also detected; publishing the matching runtime bundle."
    exec "$ROOT_DIR/release-runtime.sh"
fi
