#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
source "$SCRIPT_DIR/headunit-env.sh"

resolve_headunit_defaults

ADB_BIN="${ADB_BIN:-adb}"
ADB_PORT="${ADB_PORT:-5555}"
ADB_SERIAL="${ADB_SERIAL:-${HEADUNIT_HOST}:${ADB_PORT}}"
APK_DEST="${APK_DEST:-/data/local/tmp/haval-tool-dev.apk}"
APP_PACKAGE="${APP_PACKAGE:-br.com.redesurftank.havalshisuku}"
APP_ACTIVITY="${APP_ACTIVITY:-br.com.redesurftank.havalshisuku/.SplashActivity}"
SKIP_BUILD="${SKIP_BUILD:-0}"
DEV_APP_VERSION_CODE="${DEV_APP_VERSION_CODE:-84}"
DEV_APP_VERSION_NAME="${DEV_APP_VERSION_NAME:-0.0.1}"

require_file() {
  local file="$1"
  [[ -f "$file" ]] || {
    echo "[HavalDev][ADB] File not found: $file" >&2
    exit 1
  }
}

print_progress_bar() {
  local label="$1"
  local current="${2:-0}"
  local total="${3:-0}"
  local width=32
  local percent=0
  local filled=0
  local bar=""
  local i

  [[ "$current" =~ ^[0-9]+$ ]] || current=0
  [[ "$total" =~ ^[0-9]+$ ]] || total=0

  if (( total > 0 )); then
    percent=$(( current * 100 / total ))
    (( percent > 100 )) && percent=100
    filled=$(( width * percent / 100 ))
  fi

  for ((i=0; i<width; i++)); do
    if (( i < filled )); then
      bar+="#"
    else
      bar+="."
    fi
  done

  printf '\r[HavalDev][ADB] %s [%s] %3d%% %s/%s bytes' "$label" "$bar" "$percent" "$current" "$total"
}

adb_cmd() {
  "$ADB_BIN" -s "$ADB_SERIAL" "$@"
}

ensure_adb_device() {
  echo "[HavalDev][ADB] Connecting to $ADB_SERIAL"
  "$ADB_BIN" connect "$ADB_SERIAL" >/dev/null || true

  local state
  state="$(adb_cmd get-state 2>&1 || true)"
  case "$state" in
    device)
      return 0
      ;;
    *unauthorized*)
      echo "[HavalDev][ADB] Device is unauthorized." >&2
      echo "[HavalDev][ADB] Authorize this Mac on the headunit, or add ~/.android/adbkey.pub to /data/misc/adb/adb_keys." >&2
      exit 2
      ;;
    *)
      echo "[HavalDev][ADB] Device is not ready: $state" >&2
      exit 1
      ;;
  esac
}

remote_size() {
  adb_cmd shell "if [ -f '$1' ]; then wc -c < '$1'; else echo 0; fi" |
    tr -d '\r' |
    awk '/^[0-9]+$/ {print $1; found=1; exit} END { if (!found) print 0 }'
}

push_with_progress() {
  local apk_path="$1"
  local remote_path="$2"
  local remote_tmp="${remote_path}.tmp"
  local expected_size
  local start_ts
  local end_ts
  local push_log
  local push_pid
  local current_size

  expected_size="$(wc -c < "$apk_path" | tr -d '[:space:]')"
  push_log="$(mktemp)"
  start_ts="$(date +%s)"

  echo "[HavalDev][ADB] Pushing $(basename "$apk_path") to $remote_path"
  adb_cmd shell "rm -f '$remote_tmp' '$remote_path'" >/dev/null || true

  adb_cmd push "$apk_path" "$remote_tmp" >"$push_log" 2>&1 &
  push_pid="$!"

  while kill -0 "$push_pid" >/dev/null 2>&1; do
    current_size="$(remote_size "$remote_tmp")"
    print_progress_bar "Pushing APK" "$current_size" "$expected_size"
    sleep 1
  done

  if ! wait "$push_pid"; then
    printf '\n'
    echo "[HavalDev][ADB] adb push failed" >&2
    sed -n '1,120p' "$push_log" >&2
    rm -f "$push_log"
    exit 1
  fi

  current_size="$(remote_size "$remote_tmp")"
  print_progress_bar "Pushing APK" "$current_size" "$expected_size"
  printf '\n'

  adb_cmd shell "mv '$remote_tmp' '$remote_path' && chmod 644 '$remote_path'"
  end_ts="$(date +%s)"
  echo "[HavalDev][ADB] Push complete in $((end_ts - start_ts))s"
  sed -n '1,40p' "$push_log" | sed 's/^/[HavalDev][ADB] adb: /'
  rm -f "$push_log"
}

install_remote_apk() {
  local remote_apk="$1"
  local start_ts
  local end_ts

  start_ts="$(date +%s)"
  echo "[HavalDev][ADB] Installing $remote_apk"
  adb_cmd shell "pm install -r '$remote_apk'"
  end_ts="$(date +%s)"
  echo "[HavalDev][ADB] Install complete in $((end_ts - start_ts))s"
}

restart_app() {
  echo "[HavalDev][ADB] Restarting $APP_PACKAGE"
  adb_cmd shell "am force-stop '$APP_PACKAGE'"
  adb_cmd shell "am start -n '$APP_ACTIVITY'"
}

cd "$ROOT_DIR"

if [[ "$SKIP_BUILD" != "1" ]]; then
  echo "[HavalDev][ADB] Building debug APK (versionCode=$DEV_APP_VERSION_CODE versionName=$DEV_APP_VERSION_NAME)"
  ./gradlew :app:assembleDebug \
    -PappVersionCode="$DEV_APP_VERSION_CODE" \
    -PappVersionName="$DEV_APP_VERSION_NAME"
fi

APK_PATH="$(find "$ROOT_DIR/app/build/outputs/apk/debug" -maxdepth 1 -type f -name '*.apk' | sort | tail -n 1)"
require_file "$APK_PATH"

ensure_adb_device
push_with_progress "$APK_PATH" "$APK_DEST"
install_remote_apk "$APK_DEST"
restart_app

echo "[HavalDev][ADB] Installed package info"
adb_cmd shell "dumpsys package '$APP_PACKAGE' | grep -E 'versionName|lastUpdateTime|firstInstallTime'"
