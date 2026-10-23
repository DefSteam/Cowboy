#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
TASK="${1:-:app:assembleDebug}"

if ! command -v gradle >/dev/null 2>&1; then
    echo "Gradle is required. Install Gradle or add the Gradle wrapper." >&2
    exit 1
fi

if [[ -z "${ANDROID_HOME:-}${ANDROID_SDK_ROOT:-}" ]]; then
    echo "Set ANDROID_HOME or ANDROID_SDK_ROOT to your Android SDK directory." >&2
    exit 1
fi

cd "$ROOT_DIR"
exec gradle --no-daemon "$TASK" "${@:2}"
