#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
ADB_BIN="${ADB:-$ANDROID_HOME/platform-tools/adb}"

ARTIFACT_DIR=""
ADB_SERIAL="${ADB_SERIAL:-}"
MANUAL_WINDOW_SECONDS=0
RECHECK_SECONDS=10
SEND_PAUSE=0
STOP_GEARHEAD=0
MEDIA_KEY=""

usage() {
  cat <<'EOF'
Usage:
  scripts/android-auto-sim/capture_phone_spotify_pause_baseline.sh
  scripts/android-auto-sim/capture_phone_spotify_pause_baseline.sh --stop-gearhead
  scripts/android-auto-sim/capture_phone_spotify_pause_baseline.sh --manual-window 20
  scripts/android-auto-sim/capture_phone_spotify_pause_baseline.sh --send-media-key play
  scripts/android-auto-sim/capture_phone_spotify_pause_baseline.sh --send-pause --recheck-seconds 15
  scripts/android-auto-sim/capture_phone_spotify_pause_baseline.sh --artifact-dir DIR

Environment:
  ADB         Optional adb binary override.
  ADB_SERIAL Optional phone serial. Required if more than one ADB device exists.

Notes:
  - Captures the phone-only Spotify/Android Auto media baseline before DHU.
  - Default mode is read-only and does not dispatch media commands.
  - --manual-window gives time to press pause in Spotify on the phone while
    the script captures before/after state.
  - --send-pause dispatches a phone-side media_session pause command. It does
    not open DHU and does not touch the Haval central.
  - --send-media-key dispatches a phone-side media_session key. Supported keys:
    play, pause, play-pause, stop, next, previous.
  - --stop-gearhead force-stops Android Auto/Gearhead on the phone before the
    capture to clear a stuck DHU/head-unit-server focus state. It does not
    clear data and does not touch the Haval central.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --artifact-dir)
      shift
      ARTIFACT_DIR="${1:-}"
      ;;
    --adb-serial)
      shift
      ADB_SERIAL="${1:-}"
      ;;
    --manual-window)
      shift
      MANUAL_WINDOW_SECONDS="${1:-}"
      ;;
    --recheck-seconds)
      shift
      RECHECK_SECONDS="${1:-}"
      ;;
    --send-pause)
      SEND_PAUSE=1
      ;;
    --send-media-key)
      shift
      MEDIA_KEY="${1:-}"
      ;;
    --stop-gearhead)
      STOP_GEARHEAD=1
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

if [[ "$SEND_PAUSE" -eq 1 ]]; then
  if [[ -n "$MEDIA_KEY" && "$MEDIA_KEY" != "pause" ]]; then
    echo "--send-pause cannot be combined with a different --send-media-key" >&2
    exit 1
  fi
  MEDIA_KEY="pause"
fi

case "$MEDIA_KEY" in
  ""|play|pause|play-pause|stop|next|previous)
    ;;
  *)
    echo "--send-media-key must be one of: play, pause, play-pause, stop, next, previous" >&2
    exit 1
    ;;
esac

for value_name in MANUAL_WINDOW_SECONDS RECHECK_SECONDS; do
  value="${!value_name}"
  if ! [[ "$value" =~ ^[0-9]+$ ]]; then
    case "$value_name" in
      MANUAL_WINDOW_SECONDS) option_name="--manual-window" ;;
      RECHECK_SECONDS) option_name="--recheck-seconds" ;;
      *) option_name="$value_name" ;;
    esac
    echo "$option_name must be an integer number of seconds" >&2
    exit 1
  fi
done

if [[ -z "$ARTIFACT_DIR" ]]; then
  ARTIFACT_DIR="$ROOT_DIR/test-artifacts/aa-phone-spotify-baseline-$(date +%Y%m%d-%H%M%S)"
fi
mkdir -p "$ARTIFACT_DIR"
SUMMARY="$ARTIFACT_DIR/summary.txt"
ANALYSIS="$ARTIFACT_DIR/analysis.txt"
: >"$SUMMARY"
: >"$ANALYSIS"

log() {
  printf '%s\n' "$*" | tee -a "$SUMMARY"
}

alog() {
  printf '%s\n' "$*" | tee -a "$ANALYSIS" | tee -a "$SUMMARY" >/dev/null
}

if [[ ! -x "$ADB_BIN" ]]; then
  echo "adb not found at $ADB_BIN. Set ADB=/path/to/adb." >&2
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
      window = 140
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

session_count() {
  local file="$1"
  awk '/Sessions Stack - have/ { print $5; exit }' "$file"
}

media_button_session() {
  local file="$1"
  awk -F'is ' '/Media button session is/ { print $2; exit }' "$file"
}

music_active_ms() {
  local file="$1"
  awk -F= '/mMusicActiveMs=/ { print $2; exit }' "$file"
}

current_spotify_player_state() {
  local file="$1"
  awk '
    BEGIN {
      in_players = 0
      found = "missing"
    }
    /^  players:/ {
      in_players = 1
      next
    }
    /^  ducked players/ {
      in_players = 0
    }
    in_players && (/uid\/pid:10079\// || /u\/pid:10079\//) {
      if ($0 ~ /state:started/) {
        found = "started"
        exit
      }
      if ($0 ~ /state:paused/) {
        found = "paused"
      } else if ($0 ~ /state:idle/) {
        found = "idle"
      } else if ($0 ~ /state:stopped/) {
        found = "stopped"
      } else {
        found = "present"
      }
    }
    END {
      print found
    }
  ' "$file"
}

capture_phase() {
  local phase="$1"
  capture_adb "$phase-adb-devices" devices -l
  capture_shell "$phase-phone-info" \
    "getprop ro.product.model; getprop ro.build.version.release; dumpsys package com.spotify.music | grep -E 'versionCode|versionName' | head -n 8 || true; dumpsys package com.google.android.projection.gearhead | grep -E 'versionCode|versionName' | head -n 8 || true"
  capture_shell "$phase-media-session-full" "dumpsys media_session"
  capture_shell "$phase-audio-full" "dumpsys audio"
  capture_shell "$phase-activity-filtered" \
    "dumpsys activity processes | grep -Ei 'spotify|projection|gearhead' || true"
  capture_shell "$phase-notification-filtered" \
    "dumpsys notification --noredact | grep -Ei 'spotify|media|playback|transport|projection|gearhead' | tail -n 260 || true"
  capture_shell "$phase-logcat-filtered" \
    "logcat -d -v time | grep -Ei 'spotify|MediaSession|MediaFocusControl|AudioFocus|projection|gearhead|Playback|pause|play' | tail -n 800 || true"
}

analyze_phase() {
  local phase="$1"
  local media_file="$ARTIFACT_DIR/$phase-media-session-full.txt"
  local audio_file="$ARTIFACT_DIR/$phase-audio-full.txt"

  local spotify_state sessions media_button gearhead_focus spotify_focus active_ms player_state
  spotify_state="$(extract_spotify_state "$media_file")"
  sessions="$(session_count "$media_file")"
  media_button="$(media_button_session "$media_file")"
  active_ms="$(music_active_ms "$audio_file")"
  player_state="$(current_spotify_player_state "$audio_file")"

  if grep -q 'pack: com.google.android.projection.gearhead' "$audio_file"; then
    gearhead_focus=1
  else
    gearhead_focus=0
  fi

  if grep -q 'pack: com.spotify.music' "$audio_file"; then
    spotify_focus=1
  else
    spotify_focus=0
  fi

  alog "${phase}_spotify_state=${spotify_state:-missing}"
  alog "${phase}_media_session_count=${sessions:-missing}"
  alog "${phase}_media_button_session=${media_button:-missing}"
  alog "${phase}_gearhead_focus_present=$gearhead_focus"
  alog "${phase}_spotify_focus_present=$spotify_focus"
  alog "${phase}_music_active_ms=${active_ms:-missing}"
  alog "${phase}_current_spotify_player_state=${player_state:-missing}"
}

derive_verdict() {
  local phase="$1"
  local spotify_state sessions media_button gearhead_focus player_state
  spotify_state="$(awk -F= -v key="${phase}_spotify_state" '$1 == key { print substr($0, length(key) + 2); exit }' "$ANALYSIS")"
  sessions="$(awk -F= -v key="${phase}_media_session_count" '$1 == key { print substr($0, length(key) + 2); exit }' "$ANALYSIS")"
  media_button="$(awk -F= -v key="${phase}_media_button_session" '$1 == key { print substr($0, length(key) + 2); exit }' "$ANALYSIS")"
  gearhead_focus="$(awk -F= -v key="${phase}_gearhead_focus_present" '$1 == key { print substr($0, length(key) + 2); exit }' "$ANALYSIS")"
  player_state="$(awk -F= -v key="${phase}_current_spotify_player_state" '$1 == key { print substr($0, length(key) + 2); exit }' "$ANALYSIS")"

  if [[ "$spotify_state" == "PLAYING(3)" || "$player_state" == "started" ]]; then
    alog "verdict=phone_spotify_playing_detected"
  elif [[ "$spotify_state" == "PAUSED(2)" || "$player_state" == "paused" ]]; then
    alog "verdict=phone_spotify_paused_detected"
  elif [[ "$sessions" == "0" && "$media_button" == "null" && "$gearhead_focus" == "1" ]]; then
    alog "verdict=blocked_no_spotify_session_gearhead_focus"
  elif [[ "$sessions" == "0" && "$media_button" == "null" ]]; then
    alog "verdict=blocked_no_media_session"
  else
    alog "verdict=inconclusive_phone_spotify_state"
  fi
}

log "artifact=$ARTIFACT_DIR"
log "started=$(date '+%Y-%m-%d %H:%M:%S %z')"
log "adb_bin=$ADB_BIN"
log "manual_window_seconds=$MANUAL_WINDOW_SECONDS"
log "recheck_seconds=$RECHECK_SECONDS"
log "send_media_key=${MEDIA_KEY:-}"
log "stop_gearhead=$STOP_GEARHEAD"

detect_serial
log "phone_serial=$ADB_SERIAL"

if [[ "$STOP_GEARHEAD" -eq 1 ]]; then
  log "gearhead_force_stop=1"
  capture_shell "gearhead-force-stop" "am force-stop com.google.android.projection.gearhead"
  sleep 3
fi

capture_phase "before"
analyze_phase "before"

if [[ -n "$MEDIA_KEY" ]]; then
  log "phone_media_key_dispatch=cmd_media_session_${MEDIA_KEY}"
  safe_key_name="${MEDIA_KEY//-/_}"
  capture_shell "${safe_key_name}-dispatch" "cmd media_session dispatch $MEDIA_KEY"
fi

if [[ "$MANUAL_WINDOW_SECONDS" -gt 0 ]]; then
  log "manual_window_started=1"
  log "manual_window_note=press_pause_in_spotify_on_phone_now"
  sleep "$MANUAL_WINDOW_SECONDS"
  log "manual_window_finished=1"
fi

if [[ -n "$MEDIA_KEY" || "$MANUAL_WINDOW_SECONDS" -gt 0 ]]; then
  sleep "$RECHECK_SECONDS"
  capture_phase "after"
  analyze_phase "after"
  derive_verdict "after"
else
  derive_verdict "before"
fi

log "finished=$(date '+%Y-%m-%d %H:%M:%S %z')"
log "files:"
find "$ARTIFACT_DIR" -maxdepth 1 -type f -print | sort | sed "s#^$ROOT_DIR/##" | tee -a "$SUMMARY"
