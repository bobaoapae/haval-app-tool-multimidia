#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HEADUNIT_SH="$SCRIPT_DIR/headunit.sh"
LABEL="${1:-snapshot}"
SAFE_LABEL="$(printf '%s' "$LABEL" | tr -c 'A-Za-z0-9_.-' '_')"
STAMP="$(date '+%Y%m%d-%H%M%S')"
OUTPUT_DIR="$SCRIPT_DIR/output/carplay-bounds-${STAMP}-${SAFE_LABEL}"
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

printf '[HavalDev] Collecting read-only CarPlay cluster bounds diagnostics: %s\n' "$OUTPUT_DIR"

run_remote_optional "date" "date"
run_remote_optional "getprop-carplay-video-height" "getprop persist.haval.carplay.video.height"
run_remote_optional "getprop-filtered" "getprop | grep -Ei 'carplay|avm|scene|pui|display|video' || true"
run_remote_optional "am-stack-list" "am stack list"
run_remote_optional "dumpsys-activity-activities" "dumpsys activity activities"
run_remote_optional "dumpsys-window-displays" "dumpsys window displays"
run_remote_optional "dumpsys-window-windows" "dumpsys window windows"
run_remote_optional "dumpsys-display" "dumpsys display"
run_remote_optional "surfaceflinger-list-filtered" "dumpsys SurfaceFlinger --list | grep -Ei 'carplay|SurfaceView|haval|redesurftank' || true"
run_remote_optional "logcat-last-2000" "logcat -d -v time -t 2000"
run_remote_optional "wm-size" "wm size || true"
run_remote_optional "wm-density" "wm density || true"
run_remote_optional "wm-overscan" "wm overscan || true"

filter_file "$OUTPUT_DIR/am-stack-list.txt" "am-stack-list-carplay-filtered" -Ei 'CarPlayDisplayActivity|displayId=3|displayId=0|bounds|Stack|task'
filter_file "$OUTPUT_DIR/dumpsys-activity-activities.txt" "dumpsys-activity-carplay-filtered" -Ei -A 8 -B 4 'displayId=3|displayId=0|CarPlayDisplayActivity|Stack #|taskId|bounds'
filter_file "$OUTPUT_DIR/dumpsys-window-windows.txt" "dumpsys-window-carplay-filtered" -Ei -A 12 -B 6 'CarPlayDisplayActivity|mDisplayId=3|mFrame=|Requested|Surface|mAppBounds|mAttrs|mHasSurface'
filter_file "$OUTPUT_DIR/logcat-last-2000.txt" "logcat-carplay-cluster-filtered" -Ei 'DisplayAppLauncher|CARPLAY_CLUSTER_WATCHDOG|CARPLAY_CLUSTER_FULLSCREEN_REPAIR|WINDOW_CHANGE|AVM|HVAC|scene_notify|preview_status|CarPlayManager|cpScreen|NdkMediaCodec|jsurface|SurfaceView|WindowManager|ActivityTaskManager|ActivityManager|InstrumentProjector2'

{
  printf '# CarPlay Cluster Bounds Diagnostic\n\n'
  printf 'Generated at: `%s`\n\n' "$(date '+%Y-%m-%d %H:%M:%S')"
  printf 'Label: `%s`\n\n' "$LABEL"
  printf 'Read-only: `yes`\n\n'
  printf 'Output directory: `%s`\n\n' "$OUTPUT_DIR"
  printf '## Required Checks\n\n'
  printf -- '- Verify CarPlay display 3 stack bounds are `[0,0][1920,720]`.\n'
  printf -- '- Verify window requested size and Surface size are `1920x720`.\n'
  printf -- '- If black screen occurs, run this script before rebooting.\n\n'
  printf '## Files\n\n'
  find "$OUTPUT_DIR" -maxdepth 1 -type f | sed "s#^$OUTPUT_DIR/##" | sort | sed 's#^#- `#; s#$#`#'
} > "$REPORT_FILE"

printf '[HavalDev] Saved diagnostics to %s\n' "$OUTPUT_DIR"
