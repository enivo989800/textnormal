#!/usr/bin/env sh
set -eu

if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle was not found. Open this folder in Android Studio or install Gradle 8.x." >&2
  exit 127
fi

if [ -z "${ANDROID_HOME:-}" ] && [ -z "${ANDROID_SDK_ROOT:-}" ]; then
  echo "ANDROID_HOME/ANDROID_SDK_ROOT is not set. Install the Android SDK and set its path." >&2
  exit 2
fi

exec gradle :app:assembleDebug "$@"
