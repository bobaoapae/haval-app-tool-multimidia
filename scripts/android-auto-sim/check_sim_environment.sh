#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT_DIR="$ROOT_DIR/scripts/android-auto-sim"
ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
ADB_BIN="${ADB:-$ANDROID_HOME/platform-tools/adb}"
DHU_BIN="${DHU_BIN:-$ANDROID_HOME/extras/google/auto/desktop-head-unit}"
CONFIG="${AA_DHU_CONFIG:-$ANDROID_HOME/extras/google/auto/config/default_720p.ini}"
HEADUNIT_HOST="${HEADUNIT_HOST:-172.20.10.2}"
ARTIFACT_DIR=""
ADB_SERIAL="${ADB_SERIAL:-}"

usage() {
  cat <<'EOF'
Usage:
  scripts/android-auto-sim/check_sim_environment.sh
  scripts/android-auto-sim/check_sim_environment.sh --artifact-dir DIR

Environment:
  ADB            Optional adb binary override.
  ADB_SERIAL     Optional phone serial. Required if more than one ADB device exists.
  DHU_BIN        Optional Desktop Head Unit binary override.
  AA_DHU_CONFIG  Optional DHU config override.
  HEADUNIT_HOST  Default: 172.20.10.2.

Notes:
  - This is a read-only preflight for the DHU/phone/native comparison harness.
  - It does not open DHU, dispatch media commands, adb-connect to the central,
    deploy, mount, rollback, restart, or force-stop anything.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --artifact-dir)
      shift
      ARTIFACT_DIR="${1:-}"
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

if [[ -z "$ARTIFACT_DIR" ]]; then
  ARTIFACT_DIR="$ROOT_DIR/test-artifacts/aa-sim-env-$(date +%Y%m%d-%H%M%S)"
fi
mkdir -p "$ARTIFACT_DIR"
SUMMARY="$ARTIFACT_DIR/summary.txt"
: >"$SUMMARY"

log() {
  printf '%s\n' "$*" | tee -a "$SUMMARY"
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

probe_tcp() {
  local host="$1"
  local port="$2"
  local name="$3"
  if command -v nc >/dev/null 2>&1 && nc -z -G 2 "$host" "$port" >"$ARTIFACT_DIR/$name.txt" 2>&1; then
    log "${name}=1"
  else
    log "${name}=0"
  fi
}

log "artifact=$ARTIFACT_DIR"
log "started=$(date '+%Y-%m-%d %H:%M:%S %z')"
log "headunit_host=$HEADUNIT_HOST"
log "adb_bin=$ADB_BIN"
log "dhu_bin=$DHU_BIN"
log "dhu_config=$CONFIG"

capture_host "mac-en0" ifconfig en0
if ifconfig en0 | grep -q "inet 172.20.10.3 "; then
  log "mac_expected_ip=1"
else
  log "mac_expected_ip=0"
fi

if [[ -x "$DHU_BIN" ]]; then
  "$DHU_BIN" --version >"$ARTIFACT_DIR/dhu-version.txt" 2>&1 || true
  log "dhu_ready=1"
  log "dhu_version=$(tr '\n' ' ' <"$ARTIFACT_DIR/dhu-version.txt" | sed 's/[[:space:]]*$//')"
else
  log "dhu_ready=0"
fi

if [[ -f "$CONFIG" ]]; then
  log "dhu_config_ready=1"
else
  log "dhu_config_ready=0"
fi

if [[ -x "$ADB_BIN" ]]; then
  log "adb_ready=1"
  "$ADB_BIN" devices -l >"$ARTIFACT_DIR/adb-devices.txt" 2>&1 || true
  devices="$("$ADB_BIN" devices | awk 'NR > 1 && $2 == "device" { print $1 }')"
  phone_count="$(printf '%s\n' "$devices" | sed '/^$/d' | wc -l | tr -d ' ')"
  log "phone_device_count=$phone_count"
  if [[ -z "$ADB_SERIAL" && "$phone_count" == "1" ]]; then
    ADB_SERIAL="$(printf '%s\n' "$devices" | sed -n '1p')"
  fi
else
  log "adb_ready=0"
  phone_count=0
fi

if [[ -n "$ADB_SERIAL" && -x "$ADB_BIN" ]]; then
  log "phone_serial=$ADB_SERIAL"
  "$ADB_BIN" -s "$ADB_SERIAL" shell \
    "getprop ro.product.model; getprop ro.build.version.release; dumpsys package com.google.android.projection.gearhead | grep -E 'versionCode|versionName' | head -n 8 || true" \
    >"$ARTIFACT_DIR/phone-info.txt" 2>&1 || true
  "$ADB_BIN" -s "$ADB_SERIAL" shell \
    "dumpsys media_session | grep -Ei 'com.spotify.music|com.google.android.projection.gearhead|package=|state=PlaybackState|metadata:|description=|Media button session' | tail -n 220 || true" \
    >"$ARTIFACT_DIR/phone-media-session.txt" 2>&1 || true
  spotify_state="$(extract_spotify_state "$ARTIFACT_DIR/phone-media-session.txt")"
  log "phone_info=$(tr '\n' ' ' <"$ARTIFACT_DIR/phone-info.txt" | sed 's/[[:space:]]*$//')"
  log "spotify_state=$spotify_state"
else
  spotify_state="missing"
  log "phone_serial="
  log "spotify_state=$spotify_state"
fi

capture_host "central-ping" ping -c 2 -t 2 "$HEADUNIT_HOST"
if grep -q ' 0.0% packet loss' "$ARTIFACT_DIR/central-ping.txt"; then
  log "central_ping_ready=1"
else
  log "central_ping_ready=0"
fi
probe_tcp "$HEADUNIT_HOST" 5555 "central_adb_tcp_ready"
probe_tcp "$HEADUNIT_HOST" 23 "central_telnet_ready"

if [[ -x "$SCRIPT_DIR/validate_pause_probe_analyzers.sh" ]]; then
  if "$SCRIPT_DIR/validate_pause_probe_analyzers.sh" >"$ARTIFACT_DIR/analyzer-validation.txt" 2>&1; then
    log "analyzer_validation=1"
  else
    log "analyzer_validation=0"
  fi
else
  log "analyzer_validation=missing"
fi

if grep -q '^dhu_ready=1$' "$SUMMARY" && grep -q '^dhu_config_ready=1$' "$SUMMARY" && [[ -n "$ADB_SERIAL" && "$spotify_state" == "PLAYING(3)" ]]; then
  log "dhu_phone_probe_ready=1"
else
  log "dhu_phone_probe_ready=0"
fi

if grep -q '^central_adb_tcp_ready=1$' "$SUMMARY"; then
  log "recommended_next=native_capture_without_send"
elif grep -q '^dhu_phone_probe_ready=1$' "$SUMMARY"; then
  log "recommended_next=dhu_phone_probe_ready"
elif grep -q '^dhu_ready=1$' "$SUMMARY" && grep -q '^dhu_config_ready=1$' "$SUMMARY" && [[ -n "$ADB_SERIAL" ]]; then
  log "recommended_next=dhu_phone_probe_when_spotify_playing"
else
  log "recommended_next=fix_local_sim_preflight"
fi

log "finished=$(date '+%Y-%m-%d %H:%M:%S %z')"
log "files:"
find "$ARTIFACT_DIR" -maxdepth 1 -type f -print | sort | sed "s#^$ROOT_DIR/##" | tee -a "$SUMMARY"
