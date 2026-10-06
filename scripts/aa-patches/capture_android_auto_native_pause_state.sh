#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
ADB_BIN="${ADB:-$ANDROID_HOME/platform-tools/adb}"
ADB_CONNECT_TIMEOUT_SECONDS="${ADB_CONNECT_TIMEOUT_SECONDS:-2}"

HEADUNIT_HOST="${HEADUNIT_HOST:-172.20.10.2}"
ADB_SERIAL="${ADB_SERIAL:-$HEADUNIT_HOST:5555}"
APP_PACKAGE="br.com.redesurftank.havalshisuku"
DEBUG_SERVICE="$APP_PACKAGE/.services.BottomBarService"
DEBUG_ACTION="$APP_PACKAGE.DEBUG_MEDIA_COMMAND"
SERVICE_APK="/vendor/app/AndroidAutoService/AndroidAutoService.apk"
STAGING_APK="/data/local/tmp/aa_patches/AndroidAutoService.apk"

ARTIFACT_DIR=""
SEND_COMMAND=""
POST_WAIT_SECONDS=20
CLEAR_LOGCAT=0
VEHICLE_STOPPED=0

usage() {
  cat <<'EOF'
Usage:
  scripts/aa-patches/capture_android_auto_native_pause_state.sh
  scripts/aa-patches/capture_android_auto_native_pause_state.sh --send aa_hardkey_play_pause_d3 --vehicle-stopped
  scripts/aa-patches/capture_android_auto_native_pause_state.sh --send aa_pause_aap --post-wait 25 --vehicle-stopped

Environment:
  HEADUNIT_HOST  Default: 172.20.10.2
  ADB_SERIAL     Default: $HEADUNIT_HOST:5555
  ADB            Optional adb binary override
  ADB_CONNECT_TIMEOUT_SECONDS  Default: 2

Safety:
  - Default mode is capture-only.
  - --send dispatches one existing Impulse debug media command and then captures
    sustained state after the wait window.
  - --send requires --vehicle-stopped.
  - This script does not push, install, mount, unmount, rollback, force-stop, or
    restart Android Auto.

Allowed --send commands:
  aa_pause_aap
  aa_hardkey_pause_d0
  aa_hardkey_pause_d3
  aa_hardkey_play_pause_d0
  aa_hardkey_play_pause_d3
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --send)
      shift
      SEND_COMMAND="${1:-}"
      ;;
    --post-wait)
      shift
      POST_WAIT_SECONDS="${1:-}"
      ;;
    --artifact-dir)
      shift
      ARTIFACT_DIR="${1:-}"
      ;;
    --clear-logcat)
      CLEAR_LOGCAT=1
      ;;
    --vehicle-stopped)
      VEHICLE_STOPPED=1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
  shift
done

case "$SEND_COMMAND" in
  ""|aa_pause_aap|aa_hardkey_pause_d0|aa_hardkey_pause_d3|aa_hardkey_play_pause_d0|aa_hardkey_play_pause_d3)
    ;;
  *)
    echo "Unsupported --send command: $SEND_COMMAND" >&2
    usage >&2
    exit 1
    ;;
esac

if ! [[ "$POST_WAIT_SECONDS" =~ ^[0-9]+$ ]]; then
  echo "--post-wait must be an integer number of seconds" >&2
  exit 1
fi

if [[ -n "$SEND_COMMAND" && "$VEHICLE_STOPPED" -ne 1 ]]; then
  echo "Refusing --send without --vehicle-stopped." >&2
  echo "Capture-only mode does not need this flag." >&2
  exit 1
fi

if [[ -z "$ARTIFACT_DIR" ]]; then
  ARTIFACT_DIR="$ROOT_DIR/test-artifacts/aa-native-pause-$(date +%Y%m%d-%H%M%S)"
fi
mkdir -p "$ARTIFACT_DIR"
SUMMARY="$ARTIFACT_DIR/summary.txt"

log() {
  printf '%s\n' "$*" | tee -a "$SUMMARY"
}

run_adb() {
  "$ADB_BIN" -s "$ADB_SERIAL" "$@"
}

adb_shell() {
  run_adb shell "$@"
}

capture_host() {
  local name="$1"
  shift
  {
    printf '$'
    printf ' %q' "$@"
    printf '\n'
    "$@"
  } >"$ARTIFACT_DIR/$name.txt" 2>&1 || true
}

capture_shell() {
  local name="$1"
  local command="$2"
  {
    printf '$ adb -s %s shell %s\n' "$ADB_SERIAL" "$command"
    adb_shell "$command"
  } >"$ARTIFACT_DIR/$name.txt" 2>&1 || true
}

precheck_tcp_adb_serial() {
  if [[ ! "$ADB_SERIAL" =~ ^([^:]+):([0-9]+)$ ]]; then
    return 0
  fi
  if ! command -v nc >/dev/null 2>&1; then
    return 0
  fi

  local host="${BASH_REMATCH[1]}"
  local port="${BASH_REMATCH[2]}"
  local nc_output nc_rc
  if nc -z -G "$ADB_CONNECT_TIMEOUT_SECONDS" "$host" "$port" >"$ARTIFACT_DIR/adb-tcp-precheck.txt" 2>&1; then
    log "adb_tcp_ready=1"
    return 0
  else
    nc_rc=$?
  fi

  nc_output="$(cat "$ARTIFACT_DIR/adb-tcp-precheck.txt" 2>/dev/null || true)"
  if [[ -z "$nc_output" ]]; then
    nc_output="nc exit=$nc_rc"
  fi
  if [[ "$nc_rc" -ne 0 ]]; then
    log "adb_tcp_ready=0"
    log "adb_tcp_precheck=$(printf '%s' "$nc_output" | tr '\n' ' ' | sed 's/[[:space:]]*$//')"
    log "central_unreachable=1"
    log "No media command was dispatched."
    finish_with_exit 2
  fi
}

ensure_adb_ready() {
  local connect_output state_output
  precheck_tcp_adb_serial
  connect_output="$("$ADB_BIN" connect "$ADB_SERIAL" 2>&1 || true)"
  printf '%s\n' "$connect_output" >"$ARTIFACT_DIR/adb-connect.txt"
  log "adb_connect=$(printf '%s' "$connect_output" | tr '\n' ' ' | sed 's/[[:space:]]*$//')"

  if ! state_output="$(run_adb get-state 2>&1)"; then
    printf '%s\n' "$state_output" >"$ARTIFACT_DIR/adb-get-state.txt"
    log "adb_ready=0"
    log "adb_get_state=$(printf '%s' "$state_output" | tr '\n' ' ' | sed 's/[[:space:]]*$//')"
    log "central_unreachable=1"
    log "No media command was dispatched."
    finish_with_exit 2
  fi

  printf '%s\n' "$state_output" >"$ARTIFACT_DIR/adb-get-state.txt"
  if [[ "$state_output" != "device" ]]; then
    log "adb_ready=0"
    log "adb_state=$state_output"
    log "central_unreachable=1"
    log "No media command was dispatched."
    finish_with_exit 2
  fi

  log "adb_ready=1"
  log "adb_state=$state_output"
}

debug_media_command() {
  local command="$1"
  adb_shell "run-as '$APP_PACKAGE' am startservice --user 0 -n '$DEBUG_SERVICE' -a '$DEBUG_ACTION' --es command '$command'"
}

capture_debug_state() {
  local phase="$1"
  {
    printf '$ debug command state\n'
    debug_media_command state
  } >"$ARTIFACT_DIR/$phase-debug-state-command.txt" 2>&1 || true
  sleep 2
}

capture_logcat() {
  local phase="$1"
  local full="$ARTIFACT_DIR/$phase-logcat-full.txt"
  local filtered="$ARTIFACT_DIR/$phase-logcat-filtered.txt"
  run_adb logcat -d -v time >"$full" 2>&1 || true
  grep -Ei \
    'AA_DASHBOARD_PLAYBACK|AAHardKeyPolicy|AndroidAutoNowPlaying|AapController|HardKeyModel|mIsMediaFocus|mCurrentScene|USAGE_AAUTO_MEDIA|AudioFocus|currentSource|onMediaPlayStateChange|onMediaPlayInfoChange|InputService dispatch key|AndroidRuntime|FATAL|ANR|Spotify|MediaSession|PlaybackState|command=aa_' \
    "$full" >"$filtered" 2>/dev/null || true
}

run_artifact_analysis() {
  local analyzer="$ROOT_DIR/scripts/aa-patches/analyze_android_auto_native_pause_artifact.sh"
  if [[ -x "$analyzer" ]]; then
    "$analyzer" "$ARTIFACT_DIR" | tee "$ARTIFACT_DIR/analysis.txt" || true
  fi
}

finish_with_exit() {
  local rc="$1"
  run_artifact_analysis
  exit "$rc"
}

capture_phase() {
  local phase="$1"

  capture_shell "$phase-package" \
    "dumpsys package '$APP_PACKAGE' | grep -E 'versionCode|versionName|lastUpdateTime' | head -n 12 || true"
  capture_shell "$phase-android-auto-native-state" \
    "printf 'mounts='; mount | grep AndroidAutoService.apk || true; printf 'vendor_md5='; md5sum '$SERVICE_APK' 2>/dev/null || true; printf 'staging_md5='; md5sum '$STAGING_APK' 2>/dev/null || true; printf 'pids='; pidof '$APP_PACKAGE' com.ts.androidauto.app com.ts.androidauto com.ts.androidauto.projectionservice 2>/dev/null || true; printf '\n'"
  capture_shell "$phase-audio" \
    "dumpsys audio | grep -Ei 'USAGE_AAUTO_MEDIA|usage=28|AudioFocus|mMusicActiveMs|state:started|clientId|pack=' | tail -n 220 || true"
  capture_shell "$phase-media-session" \
    "dumpsys media_session | grep -Ei 'com.spotify.music|com.ts.androidauto|package=|state=PlaybackState|metadata:|description=|Media button session' | tail -n 220 || true"
  capture_shell "$phase-usb" \
    "dumpsys usb | grep -Ei 'current_functions|configured=|connected=|host_connected|ACCESSORY|USB_STATE|aaUsbState' | tail -n 140 || true"
  capture_debug_state "$phase"
  capture_logcat "$phase"
}

if [[ ! -x "$ADB_BIN" ]]; then
  echo "adb not found at $ADB_BIN. Set ADB=/path/to/adb." >&2
  exit 1
fi

: >"$SUMMARY"
log "artifact=$ARTIFACT_DIR"
log "started=$(date '+%Y-%m-%d %H:%M:%S %z')"
log "adb_serial=$ADB_SERIAL"
log "send_command=${SEND_COMMAND:-capture_only}"
log "post_wait_seconds=$POST_WAIT_SECONDS"
log "vehicle_stopped=$VEHICLE_STOPPED"

ensure_adb_ready
capture_host "adb-devices" "$ADB_BIN" devices -l

if [[ "$CLEAR_LOGCAT" -eq 1 ]]; then
  log "clear_logcat=1"
  run_adb logcat -c >>"$SUMMARY" 2>&1 || true
else
  log "clear_logcat=0"
fi

capture_phase "before"

if [[ -n "$SEND_COMMAND" ]]; then
  log "dispatching=$SEND_COMMAND"
  {
    printf '$ debug command %s\n' "$SEND_COMMAND"
    debug_media_command "$SEND_COMMAND"
  } >"$ARTIFACT_DIR/send-command.txt" 2>&1 || true
  sleep "$POST_WAIT_SECONDS"
  capture_phase "after"
else
  log "dispatching=none"
fi

log "finished=$(date '+%Y-%m-%d %H:%M:%S %z')"
run_artifact_analysis
log "files:"
find "$ARTIFACT_DIR" -maxdepth 1 -type f -print | sort | sed "s#^$ROOT_DIR/##" | tee -a "$SUMMARY"
