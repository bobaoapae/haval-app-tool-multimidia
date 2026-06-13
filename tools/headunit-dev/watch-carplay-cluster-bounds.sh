#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HEADUNIT_SH="$SCRIPT_DIR/headunit.sh"
LABEL="${1:-watch}"
DURATION_SECONDS="${2:-15}"
INTERVAL_SECONDS="${3:-1}"
SAFE_LABEL="$(printf '%s' "$LABEL" | tr -c 'A-Za-z0-9_.-' '_')"
STAMP="$(date '+%Y%m%d-%H%M%S')"
OUTPUT_DIR="$SCRIPT_DIR/output/carplay-bounds-watch-${STAMP}-${SAFE_LABEL}"
SUMMARY_FILE="$OUTPUT_DIR/watch-summary.txt"

mkdir -p "$OUTPUT_DIR"

iterations="$(python3 - "$DURATION_SECONDS" "$INTERVAL_SECONDS" <<'PY'
import math
import sys
try:
    duration = float(sys.argv[1])
    interval = float(sys.argv[2])
    if duration <= 0 or interval <= 0:
        raise ValueError
except ValueError:
    print(15)
else:
    print(max(1, int(math.ceil(duration / interval))))
PY
)"

run_remote_sample() {
  local sample_id="$1"
  local file="$OUTPUT_DIR/sample-${sample_id}.txt"
  local command="echo REMOTE_DATE=\$(date '+%Y-%m-%d %H:%M:%S.%3N' 2>/dev/null || date '+%Y-%m-%d %H:%M:%S'); echo __AM_STACK_LIST__; am stack list; echo __DUMPSYS_WINDOW_WINDOWS__; dumpsys window windows; echo __SURFACEFLINGER_LIST__; dumpsys SurfaceFlinger --list"

  {
    printf '$ %s\n\n' "$command"
    "$HEADUNIT_SH" exec "$command"
  } > "$file" 2>&1
}

printf '[HavalDev] Checking headunit connectivity...\n'
if ! "$HEADUNIT_SH" exec "echo ok; date" > "$OUTPUT_DIR/connectivity.txt" 2>&1; then
  printf '[HavalDev] Unable to reach headunit. Check HEADUNIT_HOST/HEADUNIT_LOCAL_HOST/HEADUNIT_TELNET_WAIT and try again.\n' >&2
  printf '[HavalDev] Partial output: %s\n' "$OUTPUT_DIR/connectivity.txt" >&2
  exit 2
fi

{
  printf '# CarPlay Cluster Bounds Watch\n\n'
  printf 'Local start: %s\n' "$(date '+%Y-%m-%d %H:%M:%S')"
  printf 'Label: %s\n' "$LABEL"
  printf 'Duration seconds: %s\n' "$DURATION_SECONDS"
  printf 'Interval seconds: %s\n' "$INTERVAL_SECONDS"
  printf 'Read-only: yes\n\n'
} > "$SUMMARY_FILE"

printf '[HavalDev] Watching CarPlay cluster bounds for %s samples every %ss: %s\n' "$iterations" "$INTERVAL_SECONDS" "$OUTPUT_DIR"

for ((i = 1; i <= iterations; i++)); do
  sample_id="$(printf '%03d' "$i")"
  sample_file="$OUTPUT_DIR/sample-${sample_id}.txt"
  printf '[HavalDev] Sample %s/%s\n' "$i" "$iterations"

  if ! run_remote_sample "$sample_id"; then
    printf '[HavalDev] Sample %s failed; keeping partial output.\n' "$sample_id" >&2
  fi

  {
    printf '\n## Sample %s local=%s\n' "$sample_id" "$(date '+%Y-%m-%d %H:%M:%S')"
    grep -Ei 'REMOTE_DATE|CarPlayDisplayActivity|displayId=3|displayId=0|bounds=|mDisplayId=3|mFrame=|Requested|Surface|mAppBounds|mAttrs|mHasSurface|SurfaceView|carplay|redesurftank|haval' "$sample_file" 2>/dev/null || true
  } >> "$SUMMARY_FILE"

  if (( i < iterations )); then
    sleep "$INTERVAL_SECONDS"
  fi
done

printf '[HavalDev] Saved watch output to %s\n' "$OUTPUT_DIR"
