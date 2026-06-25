#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT_DIR="$ROOT_DIR/scripts/android-auto-sim"
ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
ADB_BIN="${ADB:-$ANDROID_HOME/platform-tools/adb}"
CONFIG="$ANDROID_HOME/extras/google/auto/config/default_720p.ini"
PROBE_FILE="$SCRIPT_DIR/play_pause_probe.dhu"

ARTIFACT_DIR=""
ADB_SERIAL="${ADB_SERIAL:-}"
RUN_DHU=0
MODE="usb"
RECHECK_SECONDS=20
RESET_USB=0
ANALYZE_ONLY=0
REQUIRE_SPOTIFY_PLAYING=0

usage() {
  cat <<'EOF'
Usage:
  scripts/android-auto-sim/capture_phone_dhu_play_pause_probe.sh
  scripts/android-auto-sim/capture_phone_dhu_play_pause_probe.sh --run-dhu
  scripts/android-auto-sim/capture_phone_dhu_play_pause_probe.sh --run-dhu --reset-usb
  scripts/android-auto-sim/capture_phone_dhu_play_pause_probe.sh --run-dhu --reset-usb --require-spotify-playing
  scripts/android-auto-sim/capture_phone_dhu_play_pause_probe.sh --run-dhu --adb
  scripts/android-auto-sim/capture_phone_dhu_play_pause_probe.sh --analyze-only --artifact-dir DIR

Environment:
  ADB         Optional adb binary override.
  ADB_SERIAL Optional phone serial. Required if more than one ADB device exists.

Notes:
  - Default mode captures and classifies the current phone state only.
  - --run-dhu starts DHU with play_pause_probe.dhu. Keep the phone unlocked and
    Spotify already playing in Android Auto before running it.
  - --require-spotify-playing refuses to open DHU if the pre-probe phone-side
    Spotify media session is not PLAYING(3).
  - --analyze-only re-runs the local classifier against an existing artifact.
  - This script talks only to the USB phone; it does not touch the Haval central.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --run-dhu)
      RUN_DHU=1
      ;;
    --adb)
      MODE="adb"
      ;;
    --usb)
      MODE="usb"
      ;;
    --reset-usb)
      RESET_USB=1
      ;;
    --analyze-only)
      ANALYZE_ONLY=1
      ;;
    --require-spotify-playing)
      REQUIRE_SPOTIFY_PLAYING=1
      ;;
    --artifact-dir)
      shift
      ARTIFACT_DIR="${1:-}"
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
    --recheck-seconds)
      shift
      RECHECK_SECONDS="${1:-}"
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

if ! [[ "$RECHECK_SECONDS" =~ ^[0-9]+$ ]]; then
  echo "--recheck-seconds must be an integer number of seconds" >&2
  exit 1
fi

if [[ "$ANALYZE_ONLY" -eq 1 && -z "$ARTIFACT_DIR" ]]; then
  echo "--analyze-only requires --artifact-dir" >&2
  exit 1
fi

if [[ -z "$ARTIFACT_DIR" ]]; then
  ARTIFACT_DIR="$ROOT_DIR/test-artifacts/aa-dhu-phone-probe-$(date +%Y%m%d-%H%M%S)"
fi
mkdir -p "$ARTIFACT_DIR"
SUMMARY="$ARTIFACT_DIR/summary.txt"
if [[ "$ANALYZE_ONLY" -ne 1 ]]; then
  : >"$SUMMARY"
fi

log() {
  printf '%s\n' "$*" | tee -a "$SUMMARY"
}

if [[ "$ANALYZE_ONLY" -eq 1 ]]; then
  if [[ ! -f "$SUMMARY" ]]; then
    echo "summary.txt not found in artifact directory: $ARTIFACT_DIR" >&2
    exit 1
  fi
elif [[ ! -x "$ADB_BIN" ]]; then
  echo "adb not found at $ADB_BIN. Set ADB=/path/to/adb." >&2
  exit 1
fi

if [[ "$ANALYZE_ONLY" -ne 1 && ! -f "$CONFIG" ]]; then
  echo "DHU config not found: $CONFIG" >&2
  exit 1
fi

if [[ "$ANALYZE_ONLY" -ne 1 && ! -f "$PROBE_FILE" ]]; then
  echo "Probe file not found: $PROBE_FILE" >&2
  exit 1
fi

detect_serial() {
  if [[ -n "$ADB_SERIAL" ]]; then
    return 0
  fi

  local devices count
  devices="$("$ADB_BIN" devices | awk 'NR > 1 && $2 == "device" { print $1 }')"
  count="$(printf '%s\n' "$devices" | sed '/^$/d' | wc -l | tr -d ' ')"

  if [[ "$count" == "1" ]]; then
    ADB_SERIAL="$(printf '%s\n' "$devices" | sed -n '1p')"
    return 0
  fi

  echo "Unable to auto-select phone. Set ADB_SERIAL or pass --adb-serial." >&2
  "$ADB_BIN" devices -l >&2
  exit 1
}

run_adb() {
  "$ADB_BIN" -s "$ADB_SERIAL" "$@"
}

capture_adb() {
  local name="$1"
  shift
  {
    printf '$ adb -s %s' "$ADB_SERIAL"
    printf ' %q' "$@"
    printf '\n'
    run_adb "$@"
  } >"$ARTIFACT_DIR/$name.txt" 2>&1 || true
}

capture_shell() {
  local name="$1"
  local command="$2"
  {
    printf '$ adb -s %s shell %s\n' "$ADB_SERIAL" "$command"
    run_adb shell "$command"
  } >"$ARTIFACT_DIR/$name.txt" 2>&1 || true
}

wait_for_phone() {
  local i
  for i in $(seq 1 30); do
    if run_adb get-state >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  return 1
}

capture_phone_phase() {
  local phase="$1"
  capture_adb "$phase-adb-devices" devices -l
  capture_shell "$phase-phone-info" \
    "getprop ro.product.model; getprop ro.build.version.release; dumpsys package com.google.android.projection.gearhead | grep -E 'versionCode|versionName' | head -n 8 || true"
  capture_shell "$phase-media-session" \
    "dumpsys media_session | grep -Ei 'com.spotify.music|com.google.android.projection.gearhead|package=|state=PlaybackState|metadata:|description=|Media button session' | tail -n 220 || true"
  capture_shell "$phase-audio" \
    "dumpsys audio | grep -Ei 'USAGE_MEDIA|USAGE_ASSISTANCE_NAVIGATION_GUIDANCE|AudioFocus|mMusicActiveMs|state:started|clientId|pack=' | tail -n 220 || true"
}

extract_spotify_state() {
  local file="$1"
  if [[ ! -f "$file" ]]; then
    printf 'missing\n'
    return
  fi

  awk '
    BEGIN {
      window = 0
      found = 0
    }
    /package=com\.spotify\.music/ {
      window = 80
    }
    window > 0 && /state=PlaybackState/ {
      if ($0 ~ /PAUSED\(2\)/) {
        print "PAUSED(2)"
        found = 1
        exit
      }
      if ($0 ~ /PLAYING\(3\)/) {
        print "PLAYING(3)"
        found = 1
        exit
      }
      if ($0 ~ /STOPPED\(1\)/) {
        print "STOPPED(1)"
        found = 1
        exit
      }
      print "other"
      found = 1
      exit
    }
    {
      if (window > 0) {
        window--
      }
    }
    END {
      if (!found) {
        print "unknown"
      }
    }
  ' "$file"
}

write_analysis() {
  local before after recheck verdict reason
  before="$(extract_spotify_state "$ARTIFACT_DIR/before-media-session.txt")"
  after="$(extract_spotify_state "$ARTIFACT_DIR/after-media-session.txt")"
  recheck="$(extract_spotify_state "$ARTIFACT_DIR/recheck-media-session.txt")"

  verdict="needs_review"
  reason="phone media session state is not conclusive"

  if [[ "$RUN_DHU" -ne 1 ]]; then
    verdict="capture_only"
    reason="phone state was captured without running the DHU probe"
  elif [[ "$before" != "PLAYING(3)" ]]; then
    verdict="setup_not_playing"
    reason="spotify was not playing before the probe"
  elif [[ "$after" == "PAUSED(2)" && "$recheck" == "PAUSED(2)" ]]; then
    verdict="pass_pause_sustained"
    reason="spotify stayed paused through the DHU probe and recheck window"
  elif [[ "$after" == "PLAYING(3)" || "$recheck" == "PLAYING(3)" ]]; then
    verdict="failure_still_or_resumed_playing"
    reason="spotify was playing after the DHU probe or resumed by recheck"
  fi

  {
    printf 'artifact=%s\n' "$ARTIFACT_DIR"
    printf 'verdict=%s\n' "$verdict"
    printf 'reason=%s\n' "$reason"
    printf 'run_dhu=%s\n' "$RUN_DHU"
    printf 'require_spotify_playing=%s\n' "$REQUIRE_SPOTIFY_PLAYING"
    printf 'mode=%s\n' "$MODE"
    printf 'adb_serial=%s\n' "$ADB_SERIAL"
    printf 'probe_file=%s\n' "$PROBE_FILE"
    printf 'recheck_seconds=%s\n' "$RECHECK_SECONDS"
    printf 'before_spotify_state=%s\n' "$before"
    printf 'after_spotify_state=%s\n' "$after"
    printf 'recheck_spotify_state=%s\n' "$recheck"
  } | tee "$ARTIFACT_DIR/analysis.txt"
}

finish_artifact() {
  log "finished=$(date '+%Y-%m-%d %H:%M:%S %z')"
  write_analysis
  log "files:"
  find "$ARTIFACT_DIR" -maxdepth 1 -type f -print | sort | sed "s#^$ROOT_DIR/##" | tee -a "$SUMMARY"
}

if [[ "$ANALYZE_ONLY" -eq 1 ]]; then
  write_analysis
  exit 0
fi

detect_serial

log "artifact=$ARTIFACT_DIR"
log "started=$(date '+%Y-%m-%d %H:%M:%S %z')"
log "adb_serial=$ADB_SERIAL"
log "run_dhu=$RUN_DHU"
log "require_spotify_playing=$REQUIRE_SPOTIFY_PLAYING"
log "mode=$MODE"
log "config=$CONFIG"
log "probe_file=$PROBE_FILE"
log "recheck_seconds=$RECHECK_SECONDS"

if [[ "$RESET_USB" -eq 1 ]]; then
  log "reset_usb=1"
  run_adb shell svc usb resetUsbGadget >>"$SUMMARY" 2>&1 || true
  "$ADB_BIN" kill-server >>"$SUMMARY" 2>&1 || true
  "$ADB_BIN" start-server >>"$SUMMARY" 2>&1 || true
  wait_for_phone || {
    log "phone_ready_after_reset=0"
    write_analysis
    exit 2
  }
else
  log "reset_usb=0"
fi

capture_phone_phase "before"
before_state="$(extract_spotify_state "$ARTIFACT_DIR/before-media-session.txt")"
log "before_spotify_state=$before_state"

if [[ "$RUN_DHU" -eq 1 && "$REQUIRE_SPOTIFY_PLAYING" -eq 1 && "$before_state" != "PLAYING(3)" ]]; then
  log "dhu_probe=refused_setup_not_playing"
  finish_artifact
  exit 3
fi

if [[ "$RUN_DHU" -eq 1 ]]; then
  log "dhu_probe=starting"
  "$SCRIPT_DIR/run_dhu.sh" --"$MODE" --config "$CONFIG" <"$PROBE_FILE" >"$ARTIFACT_DIR/dhu-output.txt" 2>&1 || log "dhu_exit=$?"
  log "dhu_probe=finished"
  "$ADB_BIN" kill-server >>"$SUMMARY" 2>&1 || true
  "$ADB_BIN" start-server >>"$SUMMARY" 2>&1 || true
  wait_for_phone || log "phone_ready_after_dhu=0"
else
  log "dhu_probe=skipped"
fi

capture_phone_phase "after"
sleep "$RECHECK_SECONDS"
capture_phone_phase "recheck"
finish_artifact
