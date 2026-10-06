#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUTPUT_DIR="${1:-}"

usage() {
  cat <<'EOF'
Usage:
  ./tools/headunit-dev/static-discovery.sh <output-dir>

Creates static discovery files based on code search only.
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ -z "$OUTPUT_DIR" ]]; then
  usage >&2
  exit 1
fi

mkdir -p "$OUTPUT_DIR"

KEYWORDS=(
  "evaluateJavascript"
  "JavascriptInterface"
  "window\\."
  "control"
  "focus"
  "showScreen"
  "cleanup"
  "serviceManager"
  "vehicle"
  "speed"
  "rpm"
  "gear"
  "battery"
  "gps"
  "media"
  "ac"
  "can"
  "sensor"
  "status"
  "event"
)

SEARCH_TARGETS=(
  "app/src/main/java"
  "app/src/main/res/raw"
  "cluster-widgets/air-control"
  "docs"
  "DOCS"
)

printf '# Static Discovery\n\n' > "$OUTPUT_DIR/static-discovery.md"
printf 'Root: `%s`\n\n' "$ROOT_DIR" >> "$OUTPUT_DIR/static-discovery.md"

for keyword in "${KEYWORDS[@]}"; do
  printf '## %s\n\n' "$keyword" >> "$OUTPUT_DIR/static-discovery.md"
  rg -n -i "$keyword" "${SEARCH_TARGETS[@]/#/$ROOT_DIR/}" \
    > "$OUTPUT_DIR/$(printf '%s' "$keyword" | tr -cs '[:alnum:]' '_').txt" || true
  if [[ -s "$OUTPUT_DIR/$(printf '%s' "$keyword" | tr -cs '[:alnum:]' '_').txt" ]]; then
    printf 'Arquivo: `%s`\n\n' "$(basename "$(printf '%s' "$keyword" | tr -cs '[:alnum:]' '_').txt")" >> "$OUTPUT_DIR/static-discovery.md"
    sed -n '1,40p' "$OUTPUT_DIR/$(printf '%s' "$keyword" | tr -cs '[:alnum:]' '_').txt" >> "$OUTPUT_DIR/static-discovery.md"
    printf '\n\n' >> "$OUTPUT_DIR/static-discovery.md"
  else
    printf 'Sem resultados.\n\n' >> "$OUTPUT_DIR/static-discovery.md"
  fi
done

rg -n "addJavascriptInterface|evaluateJavascript|window\.showScreen|window\.focus|window\.control|window\.cleanup|window\.updateWarning|window\.clearWarnings" \
  "$ROOT_DIR/app/src/main/java" "$ROOT_DIR/cluster-widgets/air-control/src" \
  > "$OUTPUT_DIR/bridge-summary.txt" || true

printf '## Bridge Summary\n\n' >> "$OUTPUT_DIR/static-discovery.md"
if [[ -s "$OUTPUT_DIR/bridge-summary.txt" ]]; then
  sed -n '1,120p' "$OUTPUT_DIR/bridge-summary.txt" >> "$OUTPUT_DIR/static-discovery.md"
  printf '\n' >> "$OUTPUT_DIR/static-discovery.md"
else
  printf 'Sem resultados.\n' >> "$OUTPUT_DIR/static-discovery.md"
fi
