#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
DHU_BIN="${DHU_BIN:-$ANDROID_HOME/extras/google/auto/desktop-head-unit}"
ADB_BIN="${ADB:-$ANDROID_HOME/platform-tools/adb}"

MODE="adb"
PORT="${AA_DHU_PORT:-5277}"
CONFIG="$SCRIPT_DIR/haval_dhu_basic.ini"
NO_FORWARD="0"
USB_DEVICE=""
EXTRA_ARGS=()

usage() {
  cat <<EOF
Usage: $0 [--adb|--usb] [--config FILE] [--port PORT] [--no-forward] [--headless] [-- ARGS...]

Default mode:
  --adb with adb forward tcp:5277 tcp:5277 and haval_dhu_basic.ini.

Examples:
  $0 --adb
  $0 --adb --config scripts/android-auto-sim/haval_dhu_probe.ini
  $0 --usb
  $0 --adb --headless < scripts/android-auto-sim/play_pause_probe.dhu
EOF
}

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --adb)
      MODE="adb"
      ;;
    --usb)
      MODE="usb"
      ;;
    --usb-device)
      shift
      USB_DEVICE="${1:-}"
      ;;
    --config|-c)
      shift
      CONFIG="${1:-}"
      ;;
    --port)
      shift
      PORT="${1:-}"
      ;;
    --no-forward)
      NO_FORWARD="1"
      ;;
    --headless)
      EXTRA_ARGS+=("--headless")
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    --)
      shift
      EXTRA_ARGS+=("$@")
      break
      ;;
    *)
      EXTRA_ARGS+=("$1")
      ;;
  esac
  shift
done

if [[ ! -x "$DHU_BIN" ]]; then
  cat <<EOF
DHU binary not found or not executable:
  $DHU_BIN

Run:
  scripts/android-auto-sim/install_dhu.sh
EOF
  exit 1
fi

DHU_ARGS=("-c" "$CONFIG")

if [[ "$MODE" == "adb" ]]; then
  if [[ ! -x "$ADB_BIN" ]]; then
    ADB_BIN="$(command -v adb || true)"
  fi
  if [[ -z "$ADB_BIN" || ! -x "$ADB_BIN" ]]; then
    echo "adb not found. Set ADB=/path/to/adb or install Android platform-tools." >&2
    exit 1
  fi
  if [[ "$NO_FORWARD" != "1" ]]; then
    "$ADB_BIN" forward "tcp:$PORT" "tcp:$PORT"
  fi
  set +u
  exec "$DHU_BIN" --adb="127.0.0.1:$PORT" "${DHU_ARGS[@]}" "${EXTRA_ARGS[@]}"
fi

if [[ "$MODE" == "usb" ]]; then
  if [[ -n "$USB_DEVICE" ]]; then
    set +u
    exec "$DHU_BIN" --usb="$USB_DEVICE" "${DHU_ARGS[@]}" "${EXTRA_ARGS[@]}"
  fi
  set +u
  exec "$DHU_BIN" --usb "${DHU_ARGS[@]}" "${EXTRA_ARGS[@]}"
fi

echo "Unknown mode: $MODE" >&2
exit 1
