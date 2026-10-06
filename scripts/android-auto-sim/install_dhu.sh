#!/usr/bin/env bash
set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
SDKMANAGER="${SDKMANAGER:-$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager}"
DHU_BIN="$ANDROID_HOME/extras/google/auto/desktop-head-unit"

if [[ -x "$DHU_BIN" ]]; then
  echo "DHU already installed: $DHU_BIN"
  "$DHU_BIN" --version || true
  exit 0
fi

if [[ ! -x "$SDKMANAGER" ]]; then
  SDKMANAGER="$ANDROID_HOME/tools/bin/sdkmanager"
fi

if [[ ! -x "$SDKMANAGER" ]]; then
  cat <<EOF
sdkmanager not found.

Install Android SDK Command-line Tools in Android Studio, or set:
  ANDROID_HOME=/path/to/android/sdk
  SDKMANAGER=/path/to/sdkmanager
EOF
  exit 1
fi

if [[ "$SDKMANAGER" == *"/tools/bin/sdkmanager" ]]; then
  JAVA8_HOME="$(/usr/libexec/java_home -v 1.8 2>/dev/null || true)"
  if [[ -z "$JAVA8_HOME" ]]; then
    cat <<EOF
The legacy sdkmanager at:
  $SDKMANAGER
needs Java 8 on this machine.

Install current Android SDK Command-line Tools, or install/use Java 8 and rerun.
EOF
    exit 1
  fi
  export JAVA_HOME="$JAVA8_HOME"
fi

set +o pipefail
yes | "$SDKMANAGER" "extras;google;auto"
sdkmanager_status="${PIPESTATUS[1]}"
set -o pipefail

if [[ "$sdkmanager_status" -ne 0 ]]; then
  exit "$sdkmanager_status"
fi

chmod +x "$DHU_BIN"

echo "DHU installed: $DHU_BIN"
"$DHU_BIN" --version || true
