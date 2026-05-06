#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C
export LANG=C

HEADUNIT_HOST="${HEADUNIT_HOST:-172.20.10.2}"
HEADUNIT_PORT="${HEADUNIT_PORT:-23}"
HEADUNIT_TELNET_WAIT="${HEADUNIT_TELNET_WAIT:-1}"
DONE_MARKER="__HAVALDEV_DONE__"

usage() {
  cat <<'EOF'
Usage:
  ./tools/headunit-dev/telnet-exec.sh "command to run"

Environment:
  HEADUNIT_HOST   Target host (default: 172.20.10.2)
  HEADUNIT_PORT   Target port (default: 23)
  HEADUNIT_TELNET_WAIT Seconds to keep the socket open after sending command (default: 1)
EOF
}

strip_ansi() {
  perl -pe 's/\e\[[0-9;?]*[ -\/]*[@-~]//g; s/\xFF[\x00-\xFF]{2}//g; s/[\x00-\x08\x0B-\x1F\x7F]//g'
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ $# -eq 0 ]]; then
  usage >&2
  exit 1
fi

COMMAND="$*"
PAYLOAD="${COMMAND}"$'\r\n'"echo ${DONE_MARKER}"$'\r\n'

if command -v python3 >/dev/null 2>&1; then
  RAW_OUTPUT="$(
    HEADUNIT_HOST="$HEADUNIT_HOST" \
    HEADUNIT_PORT="$HEADUNIT_PORT" \
    HEADUNIT_TELNET_WAIT="$HEADUNIT_TELNET_WAIT" \
    TELNET_PAYLOAD="$PAYLOAD" \
    TELNET_DONE_MARKER="$DONE_MARKER" \
      python3 - <<'PY' || true
import os
import socket
import sys
import time

host = os.environ["HEADUNIT_HOST"]
port = int(os.environ["HEADUNIT_PORT"])
wait = float(os.environ["HEADUNIT_TELNET_WAIT"])
payload = os.environ["TELNET_PAYLOAD"].encode("utf-8", "replace")
done = os.environ["TELNET_DONE_MARKER"].encode("utf-8", "replace")
deadline = time.monotonic() + max(wait, 1.0) + 2.0
chunks = []

try:
    with socket.create_connection((host, port), timeout=max(wait, 1.0)) as sock:
        sock.settimeout(0.25)
        time.sleep(0.2)
        for line in payload.splitlines(keepends=True):
            sock.sendall(line)
            time.sleep(0.08)
        while time.monotonic() < deadline:
            try:
                chunk = sock.recv(4096)
            except socket.timeout:
                continue
            if not chunk:
                break
            chunks.append(chunk)
            if done in b"".join(chunks):
                break
except OSError:
    pass

sys.stdout.buffer.write(b"".join(chunks))
PY
  )"
else
  if command -v nc >/dev/null 2>&1; then
    NETCAT_BIN="nc"
  elif command -v netcat >/dev/null 2>&1; then
    NETCAT_BIN="netcat"
  else
    echo "python3 or netcat (nc) is required for telnet-exec.sh" >&2
    exit 1
  fi

  RAW_OUTPUT="$(
    {
      printf '%s' "$PAYLOAD"
      sleep "$HEADUNIT_TELNET_WAIT"
    } | "$NETCAT_BIN" -w "$HEADUNIT_TELNET_WAIT" "$HEADUNIT_HOST" "$HEADUNIT_PORT" 2>/dev/null || true
  )"
fi

CLEAN_OUTPUT="$(printf '%s' "$RAW_OUTPUT" | LC_ALL=C tr -d '\r' | strip_ansi)"
CLEAN_OUTPUT="${CLEAN_OUTPUT%%${DONE_MARKER}*}"

printf '%s\n' "$CLEAN_OUTPUT" | awk -v cmd="$COMMAND" '
  {
    line = $0
    sub(/^.*:\/ # /, "", line)
    if (line == cmd) next
    if (line == ":/ #") next
    if (line == "") {
      if (printed_blank) next
      printed_blank = 1
      print ""
      next
    }
    printed_blank = 0
    print line
  }
' | sed '/^[[:space:]]*echo[[:space:]]*$/d;/^[[:space:]]*$/N;/^\n$/D'
