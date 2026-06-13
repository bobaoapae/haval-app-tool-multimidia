#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

usage() {
  cat <<EOF
Usage:
  ./tools/headunit-dev/carplay-baseline-compare.sh <capture-a> <capture-b>

Examples:
  ./tools/headunit-dev/carplay-baseline-compare.sh \\
    tools/headunit-dev/output/carplay-baseline-20260529-111800-cp-02-d3-clean \\
    tools/headunit-dev/output/carplay-baseline-20260529-112300-cp-03-ac-open

You can pass either absolute paths or names relative to tools/headunit-dev/output.
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ $# -lt 2 ]]; then
  usage
  exit 1
fi

resolve_capture_dir() {
  local candidate="$1"

  if [[ -d "$candidate" ]]; then
    cd "$candidate" >/dev/null 2>&1 && pwd
    return
  fi

  if [[ -d "$SCRIPT_DIR/output/$candidate" ]]; then
    cd "$SCRIPT_DIR/output/$candidate" >/dev/null 2>&1 && pwd
    return
  fi

  printf '[HavalDev] Capture directory not found: %s\n' "$candidate" >&2
  exit 1
}

LEFT_DIR="$(resolve_capture_dir "$1")"
RIGHT_DIR="$(resolve_capture_dir "$2")"
LEFT_NAME="$(basename "$LEFT_DIR")"
RIGHT_NAME="$(basename "$RIGHT_DIR")"
STAMP="$(date '+%Y%m%d-%H%M%S')"
OUTPUT_DIR="$SCRIPT_DIR/output/carplay-baseline-compare-${STAMP}-${LEFT_NAME}-vs-${RIGHT_NAME}"
REPORT_FILE="$OUTPUT_DIR/report.md"

mkdir -p "$OUTPUT_DIR"

compare_file() {
  local rel_path="$1"
  local diff_name="$2"
  local left_file="$LEFT_DIR/$rel_path"
  local right_file="$RIGHT_DIR/$rel_path"
  local diff_file="$OUTPUT_DIR/${diff_name}.diff"

  if [[ ! -f "$left_file" || ! -f "$right_file" ]]; then
    printf -- '- Missing in at least one capture: `%s`\n' "$rel_path" >> "$REPORT_FILE"
    return
  fi

  if cmp -s "$left_file" "$right_file"; then
    printf -- '- Identical: `%s`\n' "$rel_path" >> "$REPORT_FILE"
    return
  fi

  diff -u "$left_file" "$right_file" > "$diff_file" || true
  printf -- '- Changed: `%s` -> `%s`\n' "$rel_path" "$(basename "$diff_file")" >> "$REPORT_FILE"
}

{
  printf '# CarPlay Baseline Compare\n\n'
  printf 'Generated at: `%s`\n\n' "$(date '+%Y-%m-%d %H:%M:%S %z')"
  printf 'Left capture: `%s`\n\n' "$LEFT_DIR"
  printf 'Right capture: `%s`\n\n' "$RIGHT_DIR"
  printf '## Summary\n\n'
} > "$REPORT_FILE"

compare_file "manifest.txt" "manifest"
compare_file "local/verify-regression-lock.txt" "verify-regression-lock"
compare_file "local/git-status.txt" "git-status"
compare_file "remote/projection-props.txt" "projection-props"
compare_file "remote/package-info.txt" "package-info"
compare_file "remote/md5-mounts.txt" "md5-mounts"
compare_file "remote/mounts-carplay.txt" "mounts-carplay"
compare_file "remote/ps-projection.txt" "ps-projection"
compare_file "remote/usb.txt" "usb"
compare_file "remote/haval-prefs.txt" "haval-prefs"
compare_file "filtered/am-stack-filtered.txt" "am-stack-filtered"
compare_file "filtered/activity-filtered.txt" "activity-filtered"
compare_file "filtered/window-filtered.txt" "window-filtered"
compare_file "filtered/surfaceflinger-filtered.txt" "surfaceflinger-filtered"
compare_file "filtered/logcat-filtered.txt" "logcat-filtered"

{
  printf '\n## Screenshot Presence\n\n'
  if [[ -d "$LEFT_DIR/screenshots" ]]; then
    printf 'Left:\n'
    find "$LEFT_DIR/screenshots" -maxdepth 1 -type f | sed "s#^$LEFT_DIR/screenshots/##" | sort | sed 's#^#- `#; s#$#`#'
  fi
  if [[ -d "$RIGHT_DIR/screenshots" ]]; then
    printf '\nRight:\n'
    find "$RIGHT_DIR/screenshots" -maxdepth 1 -type f | sed "s#^$RIGHT_DIR/screenshots/##" | sort | sed 's#^#- `#; s#$#`#'
  fi
} >> "$REPORT_FILE"

printf '[HavalDev] Baseline comparison saved to %s\n' "$OUTPUT_DIR"
