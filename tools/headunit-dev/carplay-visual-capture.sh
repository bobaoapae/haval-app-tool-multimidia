#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
HEADUNIT_SH="$SCRIPT_DIR/headunit.sh"
PULL_FILE_SH="$SCRIPT_DIR/pull-remote-file.sh"
source "$SCRIPT_DIR/headunit-env.sh"

resolve_headunit_defaults

HEADUNIT_TMP="${HEADUNIT_TMP:-/data/local/tmp}"
HTTP_PORT="${HTTP_PORT:-8768}"
LABEL="${1:-carplay-visual}"
STAMP="$(date '+%Y%m%d-%H%M%S')"
SAFE_LABEL="$(printf '%s' "$LABEL" | tr -c 'A-Za-z0-9_.-' '_')"
OUTPUT_DIR="$SCRIPT_DIR/output/carplay-visual-${STAMP}-${SAFE_LABEL}"
REMOTE_DIR="$OUTPUT_DIR/remote"
SCREENSHOT_DIR="$OUTPUT_DIR/screenshots"
REPORT_FILE="$OUTPUT_DIR/report.md"

mkdir -p "$REMOTE_DIR" "$SCREENSHOT_DIR"

run_remote_file() {
  local name="$1"
  local command="$2"
  {
    printf '$ %s\n\n' "$command"
    "$HEADUNIT_SH" exec "$command"
  } > "$REMOTE_DIR/${name}.txt" 2>&1 || true
}

convert_raw_screencap() {
  local raw_gz_file="$1"
  local png_file="$2"
  local meta_file="$3"

  python3 - "$raw_gz_file" "$png_file" "$meta_file" <<'PY' || true
import gzip
import struct
import sys
import zlib
from pathlib import Path

raw_gz = Path(sys.argv[1])
png_path = Path(sys.argv[2])
meta_path = Path(sys.argv[3])

def png_chunk(kind, data):
    return (
        len(data).to_bytes(4, "big")
        + kind
        + data
        + zlib.crc32(kind + data).to_bytes(4, "big")
    )

try:
    raw = gzip.decompress(raw_gz.read_bytes())
    if len(raw) < 16:
        raise ValueError(f"raw screencap too small: {len(raw)} bytes")

    width, height, pixel_format, dataspace = struct.unpack("<IIII", raw[:16])
    expected_pixels = width * height * 4
    pixels = raw[16:]
    original_pixels = len(pixels)
    padded_bytes = 0

    if original_pixels < expected_pixels:
        padded_bytes = expected_pixels - original_pixels
        pixels += bytes([0, 0, 0, 255]) * (padded_bytes // 4)
        if padded_bytes % 4:
            pixels += bytes([0, 0, 0, 255])[:padded_bytes % 4]
    elif original_pixels > expected_pixels:
        pixels = pixels[:expected_pixels]

    rows = [
        b"\x00" + pixels[y * width * 4:(y + 1) * width * 4]
        for y in range(height)
    ]
    ihdr = (
        width.to_bytes(4, "big")
        + height.to_bytes(4, "big")
        + bytes([8, 6, 0, 0, 0])
    )
    png = (
        b"\x89PNG\r\n\x1a\n"
        + png_chunk(b"IHDR", ihdr)
        + png_chunk(b"IDAT", zlib.compress(b"".join(rows), 6))
        + png_chunk(b"IEND", b"")
    )
    png_path.write_bytes(png)
    meta_path.write_text(
        "\n".join(
            [
                f"width={width}",
                f"height={height}",
                f"pixel_format={pixel_format}",
                f"dataspace={dataspace}",
                f"raw_bytes={len(raw)}",
                f"expected_raw_bytes={16 + expected_pixels}",
                f"pixel_bytes={original_pixels}",
                f"expected_pixel_bytes={expected_pixels}",
                f"padded_bytes={padded_bytes}",
                f"png_bytes={len(png)}",
            ]
        )
        + "\n"
    )
except Exception as exc:
    meta_path.write_text(f"conversion_error={type(exc).__name__}: {exc}\n")
    raise
PY
}

capture_one() {
  local display_id="$1"
  local alias="$2"
  local safe_alias
  safe_alias="$(printf '%s' "$alias" | tr -c 'A-Za-z0-9_.-' '_' | cut -c1-24)"
  local remote_file="$HEADUNIT_TMP/cv-${safe_alias}.raw"
  local remote_gzip_file="${remote_file}.gz"
  local screencap_command

  if [[ "$display_id" == "default" ]]; then
    screencap_command="screencap '$remote_file'"
  else
    screencap_command="screencap -d $display_id '$remote_file'"
  fi

  # Keep remote commands short; this telnet shell echoes and can drop output for
  # long compound commands.
  run_remote_file "screencap-${alias}-cleanup-before" "rm -f '$remote_file' '$remote_gzip_file'"
  HEADUNIT_TELNET_WAIT="${CARPLAY_VISUAL_CAPTURE_WAIT:-25}" \
    run_remote_file "screencap-${alias}-capture" "$screencap_command"
  HEADUNIT_TELNET_WAIT="${CARPLAY_VISUAL_CAPTURE_WAIT:-25}" \
    run_remote_file "screencap-${alias}-gzip" "gzip -1 -f '$remote_file' || gzip -f '$remote_file'"
  run_remote_file "screencap-${alias}-size" "wc -c '$remote_gzip_file'; ls -l '$remote_gzip_file'"

  OUTPUT_DIR="$SCREENSHOT_DIR" HTTP_PORT="$HTTP_PORT" HEADUNIT_TELNET_WAIT="${HEADUNIT_TELNET_WAIT:-5}" "$PULL_FILE_SH" "$remote_gzip_file" "${alias}.raw.gz" \
    > "$SCREENSHOT_DIR/pull-${alias}.txt" 2>&1 || true

  if [[ -f "$SCREENSHOT_DIR/${alias}.png" ]] && command -v file >/dev/null 2>&1; then
    file "$SCREENSHOT_DIR/${alias}.png" > "$SCREENSHOT_DIR/file-${alias}.txt" 2>&1 || true
  fi

  if [[ -f "$SCREENSHOT_DIR/${alias}.raw.gz" ]]; then
    convert_raw_screencap \
      "$SCREENSHOT_DIR/${alias}.raw.gz" \
      "$SCREENSHOT_DIR/${alias}.png" \
      "$SCREENSHOT_DIR/raw-meta-${alias}.txt"
  fi

  if [[ -f "$SCREENSHOT_DIR/${alias}.png" ]] && command -v file >/dev/null 2>&1; then
    file "$SCREENSHOT_DIR/${alias}.png" > "$SCREENSHOT_DIR/file-${alias}.txt" 2>&1 || true
  fi

  if [[ -f "$SCREENSHOT_DIR/${alias}.png" ]] && command -v sips >/dev/null 2>&1; then
    sips -s format jpeg "$SCREENSHOT_DIR/${alias}.png" --out "$SCREENSHOT_DIR/${alias}.jpg" \
      > "$SCREENSHOT_DIR/sips-${alias}.txt" 2>&1 || true
  fi

  run_remote_file "cleanup-${alias}" "rm -f '$remote_file' '$remote_gzip_file'"
}

printf '[HavalDev] Capturing CarPlay visual evidence: %s\n' "$OUTPUT_DIR"

run_remote_file "display-map" "dumpsys display | grep -E 'DisplayDeviceInfo|mDisplayId=|mLayerStack=|mPrimaryDisplayDevice|mBaseDisplayInfo|uniqueId|mPhysicalId' -A3 -B2 | head -260"
run_remote_file "stack" "am stack list"
run_remote_file "window-carplay" "dumpsys window windows | grep -A28 -B8 -E 'CarPlayDisplayActivity|br.com.redesurftank.havalshisuku|mDisplayId=3|mDisplayId=0' | head -320"
run_remote_file "surface-carplay" "dumpsys SurfaceFlinger | grep -A18 'SurfaceView - com.ts.carplay.app'"
run_remote_file "props" "getprop persist.haval.carplay.desired_display; getprop persist.haval.carplay.video.height"

# On this headunit, logical display 3 maps to physical display local:4.
# `screencap -d 3` captures physical local:3 (1280x720) and is kept only
# as a sanity check. The primary cluster capture is `d3-cluster-physical`.
# The main display is captured through physical display 0. The default
# `screencap -p` path is kept only as a sanity capture because this build
# can return a 49-byte PNG header without real pixels.
capture_one 0 "d0-main"
capture_one "default" "d0-default-sanity"
capture_one 4 "d3-cluster-physical"
capture_one 3 "physical-local3-sanity"

{
  printf '# CarPlay Visual Capture\n\n'
  printf 'Generated: `%s`\n\n' "$(date '+%Y-%m-%d %H:%M:%S %z')"
  printf 'Label: `%s`\n\n' "$LABEL"
  printf 'Headunit host: `%s`\n\n' "$HEADUNIT_HOST"
  printf 'Headunit local host: `%s`\n\n' "${HEADUNIT_LOCAL_HOST:-auto}"
  printf '## Display Mapping\n\n'
  printf -- '- Primary D0 capture: raw `screencap -d 0`, gzipped on the headunit and converted locally to PNG\n'
  printf -- '- D0 sanity capture: raw `screencap` without `-d`\n'
  printf -- '- Primary D3 cluster capture: raw `screencap -d 4` (`local:4`, logical display 3), converted locally to PNG\n'
  printf -- '- Sanity-only capture: raw `screencap -d 3` (`local:3`, not the CarPlay D3 target)\n\n'
  printf '## Screenshot Files\n\n'
  find "$SCREENSHOT_DIR" -maxdepth 1 -type f | sort | sed "s#^$OUTPUT_DIR/##" | sed 's#^#- `#; s#$#`#'
  printf '\n## Remote Evidence\n\n'
  find "$REMOTE_DIR" -maxdepth 1 -type f | sort | sed "s#^$OUTPUT_DIR/##" | sed 's#^#- `#; s#$#`#'
  printf '\n## Surface Rule\n\n'
  printf 'If `remote/surface-carplay.txt` shows `activeBuffer=[   0x   0:   0,Unknown/None]` for the CarPlay `SurfaceView`, no complete software screenshot can exist because the native video Surface has no frame to composite. If the CarPlay video layer uses protected buffers, the RAW screenshot can still show only the app overlay even when the physical display shows CarPlay.\n'
} > "$REPORT_FILE"

printf '[HavalDev] CarPlay visual evidence saved to %s\n' "$OUTPUT_DIR"
