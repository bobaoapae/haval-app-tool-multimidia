#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HEADUNIT_SH="$SCRIPT_DIR/headunit.sh"
LABEL="${1:-projection-focus}"
SAFE_LABEL="$(printf '%s' "$LABEL" | tr -c 'A-Za-z0-9_.-' '_')"
STAMP="$(date '+%Y%m%d-%H%M%S')"
OUTPUT_DIR="$SCRIPT_DIR/output/projection-focus-${STAMP}-${SAFE_LABEL}"
REPORT_FILE="$OUTPUT_DIR/report.md"

mkdir -p "$OUTPUT_DIR"

run_remote_file() {
  local name="$1"
  local command="$2"
  local file="$OUTPUT_DIR/${name}.txt"

  {
    printf '$ %s\n\n' "$command"
    "$HEADUNIT_SH" exec "$command"
  } > "$file" 2>&1
}

run_remote_optional() {
  local name="$1"
  local command="$2"

  if ! run_remote_file "$name" "$command"; then
    printf '[HavalDev] Optional diagnostic command failed: %s\n' "$command" >&2
    printf '\n[HavalDev] Optional command failed; keeping partial output.\n' >> "$OUTPUT_DIR/${name}.txt"
  fi
}

filter_file() {
  local source_file="$1"
  local output_name="$2"
  shift 2

  if [[ -f "$source_file" ]]; then
    grep "$@" "$source_file" > "$OUTPUT_DIR/${output_name}.txt" 2>/dev/null || true
  fi
}

printf '[HavalDev] Checking headunit connectivity...\n'
if ! run_remote_file "connectivity" "echo ok; date"; then
  printf '[HavalDev] Unable to reach headunit. Check HEADUNIT_HOST/HEADUNIT_LOCAL_HOST/HEADUNIT_TELNET_WAIT and try again.\n' >&2
  printf '[HavalDev] Partial output: %s\n' "$OUTPUT_DIR/connectivity.txt" >&2
  exit 2
fi

printf '[HavalDev] Collecting read-only projection focus comparison diagnostics: %s\n' "$OUTPUT_DIR"

run_remote_optional "date" "date"
run_remote_optional "projection-props" "getprop | grep -Ei 'carplay|androidauto|projection|avm|scene|pui|display|video' || true"
run_remote_optional "carplay-video-height" "getprop persist.haval.carplay.video.height"
run_remote_optional "packages" "dumpsys package br.com.redesurftank.havalshisuku com.ts.carplay.app com.ts.androidauto.app | grep -Ei 'Package \\[|versionName|versionCode|lastUpdateTime|firstInstallTime' || true"
run_remote_optional "ps-projection" "ps -A | grep -Ei 'redesurftank|carplay|androidauto' || true"
run_remote_optional "am-stack-list" "am stack list"
run_remote_optional "dumpsys-activity-activities" "dumpsys activity activities"
run_remote_optional "dumpsys-window-windows" "dumpsys window windows"
run_remote_optional "dumpsys-display" "dumpsys display"
run_remote_optional "surfaceflinger-list" "dumpsys SurfaceFlinger --list"
run_remote_optional "logcat-last-4000" "logcat -d -v time -t 4000"

filter_file "$OUTPUT_DIR/am-stack-list.txt" "am-stack-projection-filtered" -Ei 'CarPlayDisplayActivity|AapActivity|com\.ts\.carplay|com\.ts\.androidauto|displayId=3|displayId=0|bounds|Stack|task'
filter_file "$OUTPUT_DIR/dumpsys-activity-activities.txt" "activity-projection-filtered" -Ei -A 10 -B 5 'CarPlayDisplayActivity|AapActivity|com\.ts\.carplay|com\.ts\.androidauto|displayId=3|displayId=0|Stack #|taskId|bounds'
filter_file "$OUTPUT_DIR/dumpsys-window-windows.txt" "window-projection-filtered" -Ei -A 14 -B 7 'CarPlayDisplayActivity|AapActivity|com\.ts\.carplay|com\.ts\.androidauto|mDisplayId=3|mDisplayId=0|mFrame=|Requested|Surface|mAppBounds|mAttrs|mHasSurface'
filter_file "$OUTPUT_DIR/surfaceflinger-list.txt" "surfaceflinger-projection-filtered" -Ei 'carplay|androidauto|SurfaceView|haval|redesurftank'
filter_file "$OUTPUT_DIR/logcat-last-4000.txt" "logcat-projection-focus-filtered" -Ei 'DisplayAppLauncher|InstrumentProjector2|ServiceManager|CarPlayManager|requestVideoFocusChange|requestVideoFocus|AndroidAutoService|VideoResource|ScreenResource|cpScreen|NdkMediaCodec|MediaCodec|jsurface|SurfaceView|WINDOW_CHANGE|AVM|HVAC|scene_notify|preview_status|panel_display_notify|ActivityTaskManager|ActivityManager'

{
  printf '# Projection Focus Comparison Diagnostic\n\n'
  printf 'Generated at: `%s`\n\n' "$(date '+%Y-%m-%d %H:%M:%S')"
  printf 'Label: `%s`\n\n' "$LABEL"
  printf 'Read-only: `yes`\n\n'
  printf 'Output directory: `%s`\n\n' "$OUTPUT_DIR"
  printf '## Use\n\n'
  printf -- '- Run this for Android Auto and CarPlay with equivalent labels, for example `aa-before-hvac`, `aa-after-hvac`, `cp-before-hvac`, `cp-after-hvac`.\n'
  printf -- '- Do not reboot or disconnect before collecting the failed state.\n'
  printf -- '- Compare `logcat-projection-focus-filtered.txt`, `am-stack-projection-filtered.txt`, `window-projection-filtered.txt`, and `surfaceflinger-projection-filtered.txt`.\n\n'
  printf '## Files\n\n'
  find "$OUTPUT_DIR" -maxdepth 1 -type f | sed "s#^$OUTPUT_DIR/##" | sort | sed 's#^#- `#; s#$#`#'
} > "$REPORT_FILE"

printf '[HavalDev] Saved projection focus diagnostics to %s\n' "$OUTPUT_DIR"
