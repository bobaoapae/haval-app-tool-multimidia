#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
HEADUNIT_SH="$SCRIPT_DIR/headunit.sh"
PULL_FILE_SH="$SCRIPT_DIR/pull-remote-file.sh"
source "$SCRIPT_DIR/headunit-env.sh"

HEADUNIT_PORT="${HEADUNIT_PORT:-23}"
resolve_headunit_defaults

HEADUNIT_TMP="${HEADUNIT_TMP:-/data/local/tmp}"
SCREENSHOT_DISPLAYS="${SCREENSHOT_DISPLAYS:-0 4 3}"
SCREENSHOT_TIMEOUT_SEC="${SCREENSHOT_TIMEOUT_SEC:-12}"
SCREENSHOT_PULL_TIMEOUT_SEC="${SCREENSHOT_PULL_TIMEOUT_SEC:-12}"
LOGCAT_LINES="${LOGCAT_LINES:-4000}"

usage() {
  cat <<EOF
Usage:
  ./tools/headunit-dev/carplay-baseline-capture.sh <label>

Examples:
  ./tools/headunit-dev/carplay-baseline-capture.sh cp-02-d3-clean
  BASELINE_EXPECTED="CarPlay permanece visivel no D3 com AC aberto" \\
    ./tools/headunit-dev/carplay-baseline-capture.sh cp-03-ac-open

Environment:
  HEADUNIT_HOST          Target headunit host (default: $HEADUNIT_HOST)
  HEADUNIT_LOCAL_HOST    Local IP reachable by headunit (default: $HEADUNIT_LOCAL_HOST)
  HEADUNIT_TMP           Remote temp directory for screenshots (default: $HEADUNIT_TMP)
  SCREENSHOT_DISPLAYS    Space-separated display ids to capture (default: "$SCREENSHOT_DISPLAYS")
  SCREENSHOT_TIMEOUT_SEC Per-display screencap timeout (default: $SCREENSHOT_TIMEOUT_SEC)
  SCREENSHOT_PULL_TIMEOUT_SEC Per-display pull timeout (default: $SCREENSHOT_PULL_TIMEOUT_SEC)
  LOGCAT_LINES           Number of logcat lines to inspect before filtering (default: $LOGCAT_LINES)
  BASELINE_SCENARIO      Optional explicit scenario id/name
  BASELINE_EXPECTED      Optional expected result summary
  BASELINE_NOTES         Optional notes stored in the manifest/report
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ $# -lt 1 ]]; then
  usage
  exit 1
fi

LABEL="$1"
SCENARIO="${BASELINE_SCENARIO:-$LABEL}"
EXPECTED="${BASELINE_EXPECTED:-A confirmar}"
NOTES="${BASELINE_NOTES:-}"
STAMP="$(date '+%Y%m%d-%H%M%S')"
STAMP_HUMAN="$(date '+%Y-%m-%d %H:%M:%S %z')"
SAFE_LABEL="$(printf '%s' "$LABEL" | tr -c 'A-Za-z0-9_.-' '_')"

OUTPUT_DIR="$SCRIPT_DIR/output/carplay-baseline-${STAMP}-${SAFE_LABEL}"
LOCAL_DIR="$OUTPUT_DIR/local"
REMOTE_DIR="$OUTPUT_DIR/remote"
FILTERED_DIR="$OUTPUT_DIR/filtered"
SCREENSHOT_DIR="$OUTPUT_DIR/screenshots"
REPORT_FILE="$OUTPUT_DIR/report.md"
MANIFEST_FILE="$OUTPUT_DIR/manifest.txt"

mkdir -p "$LOCAL_DIR" "$REMOTE_DIR" "$FILTERED_DIR" "$SCREENSHOT_DIR"

safe_md5() {
  local file="$1"
  if [[ ! -f "$file" ]]; then
    printf 'missing\n'
    return
  fi

  if command -v md5 >/dev/null 2>&1; then
    md5 -q "$file"
    return
  fi

  if command -v md5sum >/dev/null 2>&1; then
    md5sum "$file" | awk '{print $1}'
    return
  fi

  printf 'unavailable\n'
}

run_local_file() {
  local name="$1"
  local command="$2"
  {
    printf '$ %s\n\n' "$command"
    (
      cd "$ROOT_DIR"
      bash -lc "$command"
    )
  } > "$LOCAL_DIR/${name}.txt" 2>&1 || true
}

run_remote_file() {
  local name="$1"
  local command="$2"
  {
    printf '$ %s\n\n' "$command"
    "$HEADUNIT_SH" exec "$command"
  } > "$REMOTE_DIR/${name}.txt" 2>&1 || true
}

filter_file() {
  local source_file="$1"
  local output_name="$2"
  shift 2

  if [[ -f "$source_file" ]]; then
    grep "$@" "$source_file" > "$FILTERED_DIR/${output_name}.txt" 2>/dev/null || true
  fi
}

run_with_timeout() {
  local timeout_sec="$1"
  shift

  "$@" &
  local pid="$!"
  local elapsed=0

  while kill -0 "$pid" >/dev/null 2>&1; do
    if (( elapsed >= timeout_sec )); then
      kill "$pid" >/dev/null 2>&1 || true
      sleep 1
      kill -9 "$pid" >/dev/null 2>&1 || true
      wait "$pid" >/dev/null 2>&1 || true
      return 124
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done

  wait "$pid"
}

capture_screenshot() {
  local display_id="$1"
  local alias="$2"
  local remote_file="$HEADUNIT_TMP/haval-baseline-${STAMP}-${alias}.png"

  {
    printf '$ %s\n\n' "rm -f '$remote_file'; screencap -d $display_id -p '$remote_file'"
    if ! run_with_timeout "$SCREENSHOT_TIMEOUT_SEC" "$HEADUNIT_SH" exec "rm -f '$remote_file'; screencap -d $display_id -p '$remote_file'"; then
      printf '[HavalDev] Screenshot command for display %s timed out or failed.\n' "$display_id"
    fi
  } > "$REMOTE_DIR/screenshot-${alias}.txt" 2>&1 || true

  if ! grep -qiE '\.png|[0-9]+x[0-9]+|bytes|screencap' "$REMOTE_DIR/screenshot-${alias}.txt"; then
    printf '[HavalDev] Screenshot command for display %s may have failed; keeping command log only.\n' "$display_id" \
      >> "$REMOTE_DIR/screenshot-${alias}.txt"
  fi

  (
    export OUTPUT_DIR="$SCREENSHOT_DIR"
    if ! run_with_timeout "$SCREENSHOT_PULL_TIMEOUT_SEC" "$PULL_FILE_SH" "$remote_file" "${alias}.png"; then
      printf '[HavalDev] Pull for display %s timed out or failed.\n' "$display_id"
    fi
  ) > "$SCREENSHOT_DIR/pull-${alias}.txt" 2>&1 || true

  {
    printf '$ %s\n\n' "rm -f '$remote_file'"
    if ! run_with_timeout "$SCREENSHOT_TIMEOUT_SEC" "$HEADUNIT_SH" exec "rm -f '$remote_file'"; then
      printf '[HavalDev] Screenshot cleanup for display %s timed out or failed.\n' "$display_id"
    fi
  } > "$REMOTE_DIR/screenshot-cleanup-${alias}.txt" 2>&1 || true
}

printf '[HavalDev] Capturing CarPlay baseline evidence: %s\n' "$OUTPUT_DIR"

{
  printf '$ %s\n\n' "echo ok; date"
  "$HEADUNIT_SH" exec "echo ok; date"
} > "$REMOTE_DIR/connectivity.txt" 2>&1 || true

if ! grep -q '^ok$' "$REMOTE_DIR/connectivity.txt"; then
  printf '[HavalDev] Unable to confirm headunit connectivity. Inspect %s\n' "$REMOTE_DIR/connectivity.txt" >&2
  exit 2
fi

LOCAL_APP_PATCH_MD5="$(safe_md5 "$ROOT_DIR/app/src/main/assets/carplay_patches/TsCarPlayApp.apk")"
LOCAL_SERVICE_PATCH_MD5="$(safe_md5 "$ROOT_DIR/app/src/main/assets/carplay_patches/TsCarPlayService.apk")"
LOCAL_MAIN_APK_MD5="$(safe_md5 "$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk")"
GIT_BRANCH="$(cd "$ROOT_DIR" && git branch --show-current 2>/dev/null || true)"
GIT_COMMIT="$(cd "$ROOT_DIR" && git rev-parse HEAD 2>/dev/null || true)"
DIRTY_COUNT="$(cd "$ROOT_DIR" && git status --short 2>/dev/null | wc -l | tr -d '[:space:]')"

run_local_file "git-status" "git status --short"
run_local_file "git-diff-stat" "git diff --stat"
run_local_file "verify-regression-lock" "python3 scripts/carplay-patches/verify_regression_lock.py"
run_local_file "local-apk-size" "if [[ -f app/build/outputs/apk/debug/app-debug.apk ]]; then wc -c app/build/outputs/apk/debug/app-debug.apk; else echo 'missing app/build/outputs/apk/debug/app-debug.apk'; fi"

VERIFY_RESULT="fail"
if grep -q 'CarPlay regression lock OK\.' "$LOCAL_DIR/verify-regression-lock.txt"; then
  VERIFY_RESULT="pass"
fi

run_remote_file "projection-props" "getprop | grep -Ei 'persist.haval.carplay|carplay|androidauto|projection|scene|preview_status|panel_display_notify' || true"
run_remote_file "package-info" "dumpsys package br.com.redesurftank.havalshisuku com.ts.carplay com.ts.carplay.app | grep -Ei 'Package \\[|versionName|versionCode|lastUpdateTime|firstInstallTime|codePath|resourcePath|legacyNativeLibraryDir' || true"
run_remote_file "md5-mounts" "toybox md5sum /vendor/app/TsCarPlayService/TsCarPlayService.apk /system/app/TsCarPlayApp/TsCarPlayApp.apk /data/local/tmp/carplay_patches/TsCarPlayService.apk /data/local/tmp/carplay_patches/TsCarPlayApp.apk 2>/dev/null || md5sum /vendor/app/TsCarPlayService/TsCarPlayService.apk /system/app/TsCarPlayApp/TsCarPlayApp.apk /data/local/tmp/carplay_patches/TsCarPlayService.apk /data/local/tmp/carplay_patches/TsCarPlayApp.apk 2>/dev/null || true"
run_remote_file "mounts-carplay" "mount | grep -Ei 'TsCarPlay|carplay_patches' || true"
run_remote_file "ps-projection" "ps -A | grep -Ei 'redesurftank|carplay|androidauto' || true"
run_remote_file "am-stack-list" "am stack list"
run_remote_file "dumpsys-activity-activities" "dumpsys activity activities"
run_remote_file "dumpsys-window-windows" "dumpsys window windows"
run_remote_file "dumpsys-display" "dumpsys display"
run_remote_file "surfaceflinger-list" "dumpsys SurfaceFlinger --list"
run_remote_file "surfaceflinger-carplay" "dumpsys SurfaceFlinger | grep -A20 -F 'SurfaceView - com.ts.carplay.app' || true"
run_remote_file "usb" "dumpsys usb"
run_remote_file "haval-prefs" "cat /data/user_de/0/br.com.redesurftank.havalshisuku/shared_prefs/haval_prefs.xml 2>/dev/null || true"
run_remote_file "logcat-last" "logcat -d -v time -t ${LOGCAT_LINES}"

filter_file "$REMOTE_DIR/am-stack-list.txt" "am-stack-filtered" -Ei 'CarPlayDisplayActivity|AapActivity|com\.ts\.carplay|displayId=3|displayId=0|bounds|Stack|task'
filter_file "$REMOTE_DIR/dumpsys-activity-activities.txt" "activity-filtered" -Ei -A 10 -B 5 'CarPlayDisplayActivity|AapActivity|com\.ts\.carplay|displayId=3|displayId=0|Stack #|taskId|bounds'
filter_file "$REMOTE_DIR/dumpsys-window-windows.txt" "window-filtered" -Ei -A 14 -B 7 'CarPlayDisplayActivity|AapActivity|com\.ts\.carplay|mDisplayId=3|mDisplayId=0|mFrame=|Requested|Surface|mAppBounds|mAttrs|mHasSurface'
filter_file "$REMOTE_DIR/surfaceflinger-list.txt" "surfaceflinger-filtered" -Ei 'carplay|SurfaceView|redesurftank'
filter_file "$REMOTE_DIR/surfaceflinger-carplay.txt" "surfaceflinger-carplay-filtered" -Ei 'SurfaceView|CarPlayDisplayActivity|activeBuffer|sourceCrop|frame|size|visible|VisibleRegion|transform'
filter_file "$REMOTE_DIR/logcat-last.txt" "logcat-filtered" -Ei 'DisplayAppLauncher|InstrumentProjector2|CarPlay|carplay|requestVideoFocus|requestVideoFocusChange|ScreenResource|VideoModel|cpScreen|NdkMediaCodec|MediaCodec|jsurface|surface hide|BufferQueue|camera|AVM|HVAC|uiNotification|FINISH_ACTIVITY|panel_display_notify|preview_status'

for display_id in $SCREENSHOT_DISPLAYS; do
  capture_screenshot "$display_id" "display-${display_id}"
done

{
  printf 'generated_at=%s\n' "$STAMP_HUMAN"
  printf 'label=%s\n' "$LABEL"
  printf 'scenario=%s\n' "$SCENARIO"
  printf 'expected=%s\n' "$EXPECTED"
  printf 'notes=%s\n' "$NOTES"
  printf 'headunit_host=%s\n' "$HEADUNIT_HOST"
  printf 'headunit_local_host=%s\n' "${HEADUNIT_LOCAL_HOST:-}"
  printf 'git_branch=%s\n' "$GIT_BRANCH"
  printf 'git_commit=%s\n' "$GIT_COMMIT"
  printf 'workspace_dirty_count=%s\n' "$DIRTY_COUNT"
  printf 'verify_regression_lock=%s\n' "$VERIFY_RESULT"
  printf 'local_main_apk_md5=%s\n' "$LOCAL_MAIN_APK_MD5"
  printf 'local_carplay_app_patch_md5=%s\n' "$LOCAL_APP_PATCH_MD5"
  printf 'local_carplay_service_patch_md5=%s\n' "$LOCAL_SERVICE_PATCH_MD5"
  printf 'screenshot_displays=%s\n' "$SCREENSHOT_DISPLAYS"
} > "$MANIFEST_FILE"

{
  printf '# CarPlay Baseline Capture\n\n'
  printf 'Generated at: `%s`\n\n' "$STAMP_HUMAN"
  printf 'Label: `%s`\n\n' "$LABEL"
  printf 'Scenario: `%s`\n\n' "$SCENARIO"
  printf 'Expected: `%s`\n\n' "$EXPECTED"
  if [[ -n "$NOTES" ]]; then
    printf 'Notes: `%s`\n\n' "$NOTES"
  fi
  printf 'Headunit host: `%s`\n\n' "$HEADUNIT_HOST"
  printf 'Headunit local host: `%s`\n\n' "${HEADUNIT_LOCAL_HOST:-}"
  printf 'Git branch: `%s`\n\n' "$GIT_BRANCH"
  printf 'Git commit: `%s`\n\n' "$GIT_COMMIT"
  printf 'Workspace dirty entries: `%s`\n\n' "$DIRTY_COUNT"
  printf 'Regression lock: `%s`\n\n' "$VERIFY_RESULT"
  printf '## Local Checksums\n\n'
  printf -- '- Main APK (`app/build/outputs/apk/debug/app-debug.apk`): `%s`\n' "$LOCAL_MAIN_APK_MD5"
  printf -- '- `TsCarPlayApp.apk`: `%s`\n' "$LOCAL_APP_PATCH_MD5"
  printf -- '- `TsCarPlayService.apk`: `%s`\n' "$LOCAL_SERVICE_PATCH_MD5"
  printf '\n## Required Review Order\n\n'
  printf -- '- `manifest.txt`\n'
  printf -- '- `local/verify-regression-lock.txt`\n'
  printf -- '- `filtered/am-stack-filtered.txt`\n'
  printf -- '- `filtered/surfaceflinger-carplay-filtered.txt`\n'
  printf -- '- `filtered/window-filtered.txt`\n'
  printf -- '- `filtered/logcat-filtered.txt`\n'
  printf -- '- `screenshots/` (must include complete D0 and D3 captures; display `4` is the primary physical capture for logical D3 on this headunit)\n'
  printf '\n## CarPlay Surface Classification Hints\n\n'
  printf -- '- Dirty/grey physical D3 with stack/window fullscreen must be checked against `filtered/surfaceflinger-carplay-filtered.txt`.\n'
  printf -- '- Treat duplicate `SurfaceView - com.ts.carplay.app` layers, visible stale `1x1` buffers, missing `activeBuffer`, or PAUSED D3 Activity as real evidence, not as screenshot noise.\n'
  printf -- '- If physical D3 is clean but only `screencap -d 4` is washed/grey and stack/window/Surface are valid, classify as screenshot/readback limitation.\n'
  printf '\n## Remote Files\n\n'
  find "$REMOTE_DIR" -maxdepth 1 -type f | sed "s#^$OUTPUT_DIR/##" | sort | sed 's#^#- `#; s#$#`#'
  printf '\n## Filtered Files\n\n'
  find "$FILTERED_DIR" -maxdepth 1 -type f | sed "s#^$OUTPUT_DIR/##" | sort | sed 's#^#- `#; s#$#`#'
  printf '\n## Screenshot Files\n\n'
  find "$SCREENSHOT_DIR" -maxdepth 1 -type f | sed "s#^$OUTPUT_DIR/##" | sort | sed 's#^#- `#; s#$#`#'
  printf '\n## Compare Against Another Capture\n\n'
  printf '```bash\n'
  printf './tools/headunit-dev/headunit.sh carplay-compare \\\n'
  printf '  %s \\\n' "$OUTPUT_DIR"
  printf '  <other-capture-dir>\n'
  printf '```\n'
} > "$REPORT_FILE"

printf '[HavalDev] CarPlay baseline evidence saved to %s\n' "$OUTPUT_DIR"
