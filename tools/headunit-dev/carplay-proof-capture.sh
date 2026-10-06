#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

usage() {
  cat <<EOF
Usage:
  ./tools/headunit-dev/carplay-proof-capture.sh <label>

Captures the proof bundle required for CarPlay D0/D3 regression checks:
- baseline logs, stacks, SurfaceFlinger and direct screenshots;
- raw full-screen D0 and D3 screenshots converted locally to PNG.

For D0 -> D3 proof, run the preflight first: open CarPlay on D0, wait for a clean D0 feed,
then send to D3 through the Impulse/app flow. Direct am start --display 3 is diagnostic only.
Camera/AVM must be captured only after the manual physical camera step.
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ $# -lt 1 ]]; then
  usage >&2
  exit 1
fi

LABEL="$1"

printf '[HavalDev] Capturing CarPlay proof evidence for %s\n' "$LABEL"
SCREENSHOT_DISPLAYS="${SCREENSHOT_DISPLAYS:-0 4 3}" \
  "$SCRIPT_DIR/carplay-baseline-capture.sh" "$LABEL"
"$SCRIPT_DIR/carplay-visual-capture.sh" "$LABEL"

printf '[HavalDev] CarPlay proof capture completed for %s\n' "$LABEL"
