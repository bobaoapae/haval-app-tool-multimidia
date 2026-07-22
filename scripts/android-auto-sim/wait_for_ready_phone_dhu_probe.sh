#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT_DIR="$ROOT_DIR/scripts/android-auto-sim"
ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
CHECK_SCRIPT="$SCRIPT_DIR/check_sim_environment.sh"
PROBE_SCRIPT="$SCRIPT_DIR/capture_phone_dhu_play_pause_probe.sh"
CONFIG="$ANDROID_HOME/extras/google/auto/config/default_720p.ini"
PROBE_FILE="$SCRIPT_DIR/play_pause_probe.dhu"

ARTIFACT_DIR=""
ADB_SERIAL="${ADB_SERIAL:-}"
MODE="usb"
RESET_USB=1
INTERVAL_SECONDS=10
TIMEOUT_SECONDS=300
RECHECK_SECONDS=20

usage() {
  cat <<'EOF'
Usage:
  scripts/android-auto-sim/wait_for_ready_phone_dhu_probe.sh
  scripts/android-auto-sim/wait_for_ready_phone_dhu_probe.sh --timeout-seconds 120 --interval-seconds 5
  scripts/android-auto-sim/wait_for_ready_phone_dhu_probe.sh --artifact-dir DIR

Environment:
  ADB         Optional adb binary override, passed through to child scripts.
  ADB_SERIAL Optional phone serial. Required if more than one ADB device exists.

Notes:
  - Polls check_sim_environment.sh until dhu_phone_probe_ready=1.
  - Does not open DHU while Spotify is not PLAYING(3).
  - When ready, runs capture_phone_dhu_play_pause_probe.sh with --run-dhu and
    --require-spotify-playing.
  - Talks only to the USB phone and DHU; it does not connect to the Haval
    central, deploy, mount, rollback, restart, or force-stop anything.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --artifact-dir)
      shift
      ARTIFACT_DIR="${1:-}"
      ;;
    --adb)
      MODE="adb"
      ;;
    --usb)
      MODE="usb"
      ;;
    --adb-serial)
      shift
      ADB_SERIAL="${1:-}"
      ;;
    --config)
      shift
      CONFIG="${1:-}"
      ;;
    --probe-file)
      shift
      PROBE_FILE="${1:-}"
      ;;
    --interval-seconds)
      shift
      INTERVAL_SECONDS="${1:-}"
      ;;
    --timeout-seconds)
      shift
      TIMEOUT_SECONDS="${1:-}"
      ;;
    --recheck-seconds)
      shift
      RECHECK_SECONDS="${1:-}"
      ;;
    --no-reset-usb)
      RESET_USB=0
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

for value_name in INTERVAL_SECONDS TIMEOUT_SECONDS RECHECK_SECONDS; do
  value="${!value_name}"
  if ! [[ "$value" =~ ^[0-9]+$ ]]; then
    case "$value_name" in
      INTERVAL_SECONDS) option_name="--interval-seconds" ;;
      TIMEOUT_SECONDS) option_name="--timeout-seconds" ;;
      RECHECK_SECONDS) option_name="--recheck-seconds" ;;
      *) option_name="$value_name" ;;
    esac
    echo "$option_name must be an integer number of seconds" >&2
    exit 1
  fi
done

if [[ "$INTERVAL_SECONDS" -lt 1 ]]; then
  echo "--interval-seconds must be at least 1" >&2
  exit 1
fi

if [[ ! -x "$CHECK_SCRIPT" ]]; then
  echo "Preflight script is not executable: $CHECK_SCRIPT" >&2
  exit 1
fi

if [[ ! -x "$PROBE_SCRIPT" ]]; then
  echo "Probe script is not executable: $PROBE_SCRIPT" >&2
  exit 1
fi

if [[ -z "$ARTIFACT_DIR" ]]; then
  ARTIFACT_DIR="$ROOT_DIR/test-artifacts/aa-dhu-ready-probe-$(date +%Y%m%d-%H%M%S)"
fi
mkdir -p "$ARTIFACT_DIR"
SUMMARY="$ARTIFACT_DIR/summary.txt"
: >"$SUMMARY"

log() {
  printf '%s\n' "$*" | tee -a "$SUMMARY"
}

summary_value() {
  local file="$1"
  local key="$2"
  awk -F= -v key="$key" '$1 == key { print substr($0, length(key) + 2); exit }' "$file"
}

log "artifact=$ARTIFACT_DIR"
log "started=$(date '+%Y-%m-%d %H:%M:%S %z')"
log "mode=$MODE"
log "config=$CONFIG"
log "probe_file=$PROBE_FILE"
log "interval_seconds=$INTERVAL_SECONDS"
log "timeout_seconds=$TIMEOUT_SECONDS"
log "recheck_seconds=$RECHECK_SECONDS"
log "reset_usb=$RESET_USB"

start_epoch="$(date +%s)"
attempt=1

while true; do
  preflight_dir="$ARTIFACT_DIR/preflight-$attempt"
  preflight_output="$ARTIFACT_DIR/preflight-$attempt-output.txt"

  log "preflight_attempt=$attempt"

  set +e
  if [[ -n "$ADB_SERIAL" ]]; then
    ADB_SERIAL="$ADB_SERIAL" "$CHECK_SCRIPT" --artifact-dir "$preflight_dir" >"$preflight_output" 2>&1
  else
    "$CHECK_SCRIPT" --artifact-dir "$preflight_dir" >"$preflight_output" 2>&1
  fi
  preflight_rc="$?"
  set -e
  log "preflight_${attempt}_rc=$preflight_rc"

  preflight_summary="$preflight_dir/summary.txt"
  if [[ ! -f "$preflight_summary" ]]; then
    log "preflight_summary_missing=1"
    log "preflight_failed=1"
    log "preflight_output=$preflight_output"
    log "latest_preflight=$preflight_dir"
    log "finished=$(date '+%Y-%m-%d %H:%M:%S %z')"
    exit 2
  fi

  spotify_state="$(summary_value "$preflight_summary" "spotify_state")"
  ready="$(summary_value "$preflight_summary" "dhu_phone_probe_ready")"
  recommended_next="$(summary_value "$preflight_summary" "recommended_next")"
  central_adb="$(summary_value "$preflight_summary" "central_adb_tcp_ready")"
  log "preflight_${attempt}_spotify_state=${spotify_state:-missing}"
  log "preflight_${attempt}_dhu_phone_probe_ready=${ready:-missing}"
  log "preflight_${attempt}_recommended_next=${recommended_next:-missing}"
  log "preflight_${attempt}_central_adb_tcp_ready=${central_adb:-missing}"

  if [[ "$preflight_rc" -ne 0 ]]; then
    log "preflight_failed=1"
    log "preflight_output=$preflight_output"
    log "latest_preflight=$preflight_dir"
    log "finished=$(date '+%Y-%m-%d %H:%M:%S %z')"
    exit 2
  fi

  if [[ "$ready" == "1" ]]; then
    probe_dir="$ARTIFACT_DIR/probe"
    probe_output="$ARTIFACT_DIR/probe-output.txt"
    probe_args=(
      "$PROBE_SCRIPT"
      "--run-dhu"
      "--require-spotify-playing"
      "--$MODE"
      "--config" "$CONFIG"
      "--probe-file" "$PROBE_FILE"
      "--recheck-seconds" "$RECHECK_SECONDS"
      "--artifact-dir" "$probe_dir"
    )

    if [[ "$RESET_USB" -eq 1 ]]; then
      probe_args+=("--reset-usb")
    fi
    if [[ -n "$ADB_SERIAL" ]]; then
      probe_args+=("--adb-serial" "$ADB_SERIAL")
    fi

    log "probe_starting=1"
    set +e
    "${probe_args[@]}" >"$probe_output" 2>&1
    probe_rc="$?"
    set -e
    log "probe_rc=$probe_rc"
    if [[ -f "$probe_dir/analysis.txt" ]]; then
      verdict="$(summary_value "$probe_dir/analysis.txt" "verdict")"
      log "probe_verdict=${verdict:-missing}"
    else
      log "probe_verdict=missing"
    fi
    log "finished=$(date '+%Y-%m-%d %H:%M:%S %z')"
    exit "$probe_rc"
  fi

  now_epoch="$(date +%s)"
  elapsed=$((now_epoch - start_epoch))
  if [[ "$elapsed" -ge "$TIMEOUT_SECONDS" ]]; then
    log "timeout_reached=1"
    log "elapsed_seconds=$elapsed"
    log "latest_preflight=$preflight_dir"
    log "finished=$(date '+%Y-%m-%d %H:%M:%S %z')"
    exit 4
  fi

  sleep "$INTERVAL_SECONDS"
  attempt=$((attempt + 1))
done
