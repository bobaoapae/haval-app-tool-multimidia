#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
OUTPUT_ROOT="$SCRIPT_DIR/output"
STAMP="$(date '+%Y%m%d-%H%M%S')"
BUNDLE_DIR="$OUTPUT_ROOT/offline-analysis-$STAMP"
ARCHIVE_PATH="$BUNDLE_DIR/offline-analysis-$STAMP.tar.gz"

mkdir -p "$BUNDLE_DIR"

write_index() {
  local index_file="$BUNDLE_DIR/index.md"

  {
    printf '# Offline Analysis Bundle\n\n'
    printf 'Generated at: `%s`\n\n' "$(date '+%Y-%m-%d %H:%M:%S')"
    printf '## Purpose\n\n'
    printf 'Local package for offline review of headunit diagnostics, AVM investigation artifacts, and current source changes.\n\n'
    printf '## Important Safety Note\n\n'
    printf 'This bundle is for diagnosis and source review only. Do not use it to execute vehicle-control, ADAS, brake, airbag, or powertrain changes.\n\n'
    printf '## Included Files\n\n'
    printf -- '- `git-status.txt`: current worktree status.\n'
    printf -- '- `git-diff-stat.txt`: summary of local code changes.\n'
    printf -- '- `git-diff.patch`: current tracked-file diff.\n'
    printf -- '- `output-files.txt`: inventory of existing diagnostic/discovery files.\n'
    printf -- '- `output-sizes.txt`: size summary for diagnostic/discovery folders.\n'
    printf -- '- `offline-analysis-*.tar.gz`: archive containing docs, tooling, selected app/frontend source, and existing output artifacts.\n\n'
    printf '## Suggested Offline Review Order\n\n'
    printf '1. Read `tools/headunit-dev/output/avm-vendor-investigation-20260425-005015/report.md`.\n'
    printf '2. Review `git-diff.patch` for current local changes.\n'
    printf '3. Inspect `drive-log-*` and `avm-discovery-*` logs for event timing.\n'
    printf '4. Use the vendor investigation files only as evidence; avoid executing inferred Binder/vendor commands without a separate safety review.\n'
    printf '\n## Local Analysis Produced\n\n'
    printf -- '- `tools/headunit-dev/output/offline-analysis-*/offline-avm-analysis.md`: consolidated conclusion and next safe steps for AVM/cluster investigation, when present.\n'
  } > "$index_file"
}

cd "$ROOT_DIR"

git status --short > "$BUNDLE_DIR/git-status.txt" || true
git diff --stat > "$BUNDLE_DIR/git-diff-stat.txt" || true
git diff > "$BUNDLE_DIR/git-diff.patch" || true

find "$OUTPUT_ROOT" -maxdepth 3 -type f \
  ! -path "$BUNDLE_DIR/*" \
  -print | sort > "$BUNDLE_DIR/output-files.txt" || true

du -sh "$OUTPUT_ROOT"/* 2>/dev/null | sort -h > "$BUNDLE_DIR/output-sizes.txt" || true

write_index

tar \
  --exclude='tools/headunit-dev/output/offline-analysis-*/*.tar.gz' \
  -czf "$ARCHIVE_PATH" \
  DOCS \
  docs \
  tools/headunit-dev \
  app/src/main/AndroidManifest.xml \
  app/src/main/java/br/com/redesurftank/havalshisuku/projectors/InstrumentProjector2.kt \
  app/src/main/java/br/com/redesurftank/havalshisuku/models/SharedPreferencesKeys.kt \
  app/src/main/res/raw/app.html \
  cluster-widgets/air-control/src \
  2>/dev/null

printf '%s\n' "$BUNDLE_DIR"
printf '%s\n' "$ARCHIVE_PATH"
