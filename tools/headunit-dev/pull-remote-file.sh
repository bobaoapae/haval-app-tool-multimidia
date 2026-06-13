#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
TELNET_EXEC="$SCRIPT_DIR/telnet-exec.sh"
source "$SCRIPT_DIR/headunit-env.sh"

HEADUNIT_PORT="${HEADUNIT_PORT:-23}"
resolve_headunit_defaults
HTTP_PORT="${HTTP_PORT:-8768}"
HTTP_PORT_SEARCH_LIMIT="${HTTP_PORT_SEARCH_LIMIT:-20}"
OUTPUT_DIR="${OUTPUT_DIR:-$SCRIPT_DIR/output/pulled-files}"

usage() {
  cat <<EOF
Usage:
  ./tools/headunit-dev/pull-remote-file.sh <remote-path> [local-name]

Examples:
  ./tools/headunit-dev/pull-remote-file.sh /system/priv-app/BeanMultiDisplay-app-Release/BeanMultiDisplay-app-Release.apk
  ./tools/headunit-dev/headunit.sh pull-file /system/priv-app/BeanMultiDisplay-app-Release/BeanMultiDisplay-app-Release.apk BeanMultiDisplay.apk

Environment:
  HEADUNIT_HOST        Target headunit host (default: $HEADUNIT_HOST)
  HEADUNIT_LOCAL_HOST  Local IP reachable by headunit. Auto-detected when empty.
  HTTP_PORT           Local upload receiver port (default: $HTTP_PORT)
  OUTPUT_DIR          Local output directory (default: $OUTPUT_DIR)
EOF
}

local_http_host() {
  if [[ -n "$HEADUNIT_LOCAL_HOST" ]]; then
    printf '%s\n' "$HEADUNIT_LOCAL_HOST"
    return
  fi

  local route_interface
  route_interface="$(route get "$HEADUNIT_HOST" 2>/dev/null | awk '/interface:/{print $2; exit}')"
  if [[ -n "$route_interface" ]]; then
    ipconfig getifaddr "$route_interface" 2>/dev/null && return
  fi

  ipconfig getifaddr en0 2>/dev/null || hostname -I 2>/dev/null | awk '{print $1}'
}

find_available_http_port() {
  local start_port="$1"
  local max_tries="$2"
  local candidate

  for ((candidate=start_port; candidate<start_port+max_tries; candidate++)); do
    if python3 - "$candidate" <<'PY' >/dev/null 2>&1
import socket
import sys

port = int(sys.argv[1])
sock = socket.socket()
sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
try:
    sock.bind(("0.0.0.0", port))
except OSError:
    sys.exit(1)
finally:
    sock.close()
PY
    then
      printf '%s\n' "$candidate"
      return 0
    fi
  done

  return 1
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" || $# -lt 1 ]]; then
  usage
  [[ $# -lt 1 ]] && exit 1 || exit 0
fi

REMOTE_PATH="$1"
LOCAL_NAME="${2:-$(basename "$REMOTE_PATH")}"
mkdir -p "$OUTPUT_DIR"

LOCAL_PATH="$OUTPUT_DIR/$LOCAL_NAME"
SERVER_SCRIPT="$(mktemp)"
SERVER_LOG="$(mktemp)"
REMOTE_LOG="/data/local/tmp/haval-pull-$(date '+%Y%m%d-%H%M%S').log"

cat > "$SERVER_SCRIPT" <<'PY'
import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--output", required=True)
parser.add_argument("--host", default="0.0.0.0")
parser.add_argument("--port", type=int, required=True)
args = parser.parse_args()
output = Path(args.output)
output.parent.mkdir(parents=True, exist_ok=True)

class Handler(BaseHTTPRequestHandler):
    def do_PUT(self):
        self._receive()

    def do_POST(self):
        self._receive()

    def _receive(self):
        length = int(self.headers.get("Content-Length", "0"))
        tmp = output.with_suffix(output.suffix + ".tmp")
        remaining = length
        with tmp.open("wb") as fh:
            while remaining > 0:
                chunk = self.rfile.read(min(1024 * 1024, remaining))
                if not chunk:
                    break
                fh.write(chunk)
                remaining -= len(chunk)
        tmp.replace(output)
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b"OK\n")

    def log_message(self, fmt, *args):
        print("%s - %s" % (self.client_address[0], fmt % args), flush=True)

ThreadingHTTPServer((args.host, args.port), Handler).serve_forever()
PY

cleanup() {
  if [[ -n "${SERVER_PID:-}" ]]; then
    kill "$SERVER_PID" >/dev/null 2>&1 || true
  fi
  rm -f "$SERVER_SCRIPT" "$SERVER_LOG"
}
trap cleanup EXIT

HOST_IP="$(local_http_host)"
if [[ -z "$HOST_IP" ]]; then
  echo "[HavalDev] Could not determine local host IP for pull receiver" >&2
  exit 1
fi

SERVER_PORT="$(find_available_http_port "$HTTP_PORT" "$HTTP_PORT_SEARCH_LIMIT")" || {
  echo "[HavalDev] Could not find a free local HTTP port starting at $HTTP_PORT" >&2
  exit 1
}

if [[ "$SERVER_PORT" != "$HTTP_PORT" ]]; then
  echo "[HavalDev] HTTP port $HTTP_PORT busy, using $SERVER_PORT"
fi

rm -f "$LOCAL_PATH" "$LOCAL_PATH.tmp"

python3 "$SERVER_SCRIPT" --port "$SERVER_PORT" --output "$LOCAL_PATH" > "$SERVER_LOG" 2>&1 &
SERVER_PID="$!"
sleep 1

if ! kill -0 "$SERVER_PID" >/dev/null 2>&1; then
  echo "[HavalDev] Local upload receiver failed to start" >&2
  sed -n '1,80p' "$SERVER_LOG" >&2
  exit 1
fi

REMOTE_SIZE="$("$TELNET_EXEC" "wc -c '$REMOTE_PATH' 2>/dev/null | awk '{print \$1}'" | awk '/^[0-9]+$/ {print $1; exit}')"
if [[ -z "$REMOTE_SIZE" ]]; then
  echo "[HavalDev] Could not read remote file size: $REMOTE_PATH" >&2
  exit 1
fi
if (( REMOTE_SIZE == 0 )); then
  echo "[HavalDev] Remote file is empty: $REMOTE_PATH"
  : > "$LOCAL_PATH"
  echo "[HavalDev] Saved empty file to $LOCAL_PATH"
  exit 0
fi

URL="http://${HOST_IP}:${SERVER_PORT}/${LOCAL_NAME}"
echo "[HavalDev] Pulling $REMOTE_PATH ($REMOTE_SIZE bytes)"
echo "[HavalDev] Receiver: $URL"

"$TELNET_EXEC" "nohup sh -c \"curl -fsS -T '$REMOTE_PATH' '$URL'\" > '$REMOTE_LOG' 2>&1 &" >/dev/null || true

last_percent=-1
for _ in $(seq 1 300); do
  if [[ -f "$LOCAL_PATH" ]]; then
    local_size="$(wc -c < "$LOCAL_PATH" | tr -d '[:space:]')"
  elif [[ -f "$LOCAL_PATH.tmp" ]]; then
    local_size="$(wc -c < "$LOCAL_PATH.tmp" | tr -d '[:space:]')"
  else
    local_size=0
  fi

  percent=$(( local_size * 100 / REMOTE_SIZE ))
  if [[ "$percent" -ne "$last_percent" ]]; then
    printf '[HavalDev] Progress: %s%% (%s/%s bytes)\n' "$percent" "$local_size" "$REMOTE_SIZE"
    last_percent="$percent"
  fi

  if [[ -f "$LOCAL_PATH" && "$local_size" == "$REMOTE_SIZE" ]]; then
    echo "[HavalDev] Saved to $LOCAL_PATH"
    exit 0
  fi

  sleep 1
done

echo "[HavalDev] Pull did not complete before timeout" >&2
"$TELNET_EXEC" "cat '$REMOTE_LOG' 2>/dev/null | tail -n 40" >&2 || true
sed -n '1,80p' "$SERVER_LOG" >&2 || true
exit 1
