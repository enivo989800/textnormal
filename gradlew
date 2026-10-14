#!/usr/bin/env sh
# Local-Gradle fallback wrapper. The official Gradle wrapper JAR is intentionally
# not included because this build environment has no network access to obtain it.
set -eu
if [ -n "${GRADLE_HOME:-}" ] && [ -x "$GRADLE_HOME/bin/gradle" ]; then
  exec "$GRADLE_HOME/bin/gradle" "$@"
fi
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
echo "Gradle is not installed. Install Gradle 8.9 or open this project in Android Studio with a configured Gradle distribution." >&2
exit 127
