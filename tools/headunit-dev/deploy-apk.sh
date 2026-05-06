#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
HEADUNIT_SCRIPT="$SCRIPT_DIR/headunit.sh"
TELNET_EXEC="$SCRIPT_DIR/telnet-exec.sh"

HEADUNIT_HOST="${HEADUNIT_HOST:-172.20.10.2}"
HEADUNIT_LOCAL_HOST="${HEADUNIT_LOCAL_HOST:-172.20.10.5}"
APK_DEST="${APK_DEST:-/data/local/tmp/haval-tool-dev.apk}"
APP_PACKAGE="${APP_PACKAGE:-br.com.redesurftank.havalshisuku}"
APP_ACTIVITY="${APP_ACTIVITY:-br.com.redesurftank.havalshisuku/.SplashActivity}"
HTTP_PORT="${HTTP_PORT:-8765}"
HTTP_PORT_SEARCH_LIMIT="${HTTP_PORT_SEARCH_LIMIT:-20}"

require_file() {
  local file="$1"
  [[ -f "$file" ]] || {
    echo "[HavalDev] File not found: $file" >&2
    exit 1
  }
}

local_http_host() {
  if [[ -n "${HEADUNIT_LOCAL_HOST:-}" ]]; then
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

download_apk_via_http() {
  local apk_path="$1"
  local remote_path="$2"
  local host_ip
  local server_log
  local server_pid
  local server_port
  local url
  local remote_cmd
  local expected_size
  local remote_size

  host_ip="$(local_http_host)"
  if [[ -z "$host_ip" ]]; then
    echo "[HavalDev] Could not determine local host IP for HTTP deploy" >&2
    return 1
  fi

  server_port="$(find_available_http_port "$HTTP_PORT" "$HTTP_PORT_SEARCH_LIMIT")" || {
    echo "[HavalDev] Could not find a free local HTTP port starting at $HTTP_PORT" >&2
    return 1
  }

  if [[ "$server_port" != "$HTTP_PORT" ]]; then
    echo "[HavalDev] HTTP port $HTTP_PORT busy, using $server_port"
  fi

  server_log="$(mktemp)"
  python3 -m http.server "$server_port" --bind 0.0.0.0 --directory "$(dirname "$apk_path")" > "$server_log" 2>&1 &
  server_pid="$!"
  trap 'if [[ -n "${server_pid:-}" ]]; then kill "$server_pid" >/dev/null 2>&1 || true; fi; if [[ -n "${server_log:-}" ]]; then rm -f "$server_log"; fi' RETURN

  sleep 1
  if ! kill -0 "$server_pid" >/dev/null 2>&1; then
    echo "[HavalDev] Local HTTP server failed to start" >&2
    sed -n '1,80p' "$server_log" >&2
    return 1
  fi

  url="http://${host_ip}:${server_port}/$(basename "$apk_path")"
  expected_size="$(wc -c < "$apk_path" | tr -d '[:space:]')"
  echo "[HavalDev] Asking headunit to download $url"

  remote_cmd="rm -f '${remote_path}.tmp' '${remote_path}' '${remote_path}.download.log'; nohup sh -c \"(curl -fsSL '$url' -o '${remote_path}.tmp' || wget -O '${remote_path}.tmp' '$url' || toybox wget -O '${remote_path}.tmp' '$url' || busybox wget -O '${remote_path}.tmp' '$url') && mv '${remote_path}.tmp' '${remote_path}' && chmod 644 '${remote_path}'\" > '${remote_path}.download.log' 2>&1 &"
  "$TELNET_EXEC" "$remote_cmd" >/dev/null || true

  for _ in $(seq 1 120); do
    remote_size="$("$TELNET_EXEC" "ls -l '$remote_path' 2>/dev/null" | awk -v path="$remote_path" '$NF == path { print $5; exit }')"
    if [[ "$remote_size" == "$expected_size" ]]; then
      echo "[HavalDev] Headunit downloaded APK via HTTP ($remote_size bytes)"
      return 0
    fi
    sleep 1
  done

  echo "[HavalDev] APK download did not complete via HTTP" >&2
  echo "[HavalDev] Expected size: $expected_size, remote size: ${remote_size:-0}" >&2
  sed -n '1,80p' "$server_log" >&2
  "$TELNET_EXEC" "cat '${remote_path}.download.log' 2>/dev/null | tail -n 40" >&2 || true
  return 1
}

install_remote_apk() {
  local remote_apk="$1"

  echo "[HavalDev] Installing $remote_apk"
  "$TELNET_EXEC" "pm install -r '$remote_apk' || cmd package install -r '$remote_apk'"
}

restart_app() {
  echo "[HavalDev] Restarting app"
  "$TELNET_EXEC" "am force-stop '$APP_PACKAGE'; am start -n '$APP_ACTIVITY'" >/dev/null || true
}

cd "$ROOT_DIR"

echo "[HavalDev] Building debug APK"
./gradlew :app:assembleDebug

APK_PATH="$(find "$ROOT_DIR/app/build/outputs/apk/debug" -maxdepth 1 -type f -name '*.apk' | sort | tail -n 1)"
require_file "$APK_PATH"

echo "[HavalDev] Deploying $APK_PATH"
if ! download_apk_via_http "$APK_PATH" "$APK_DEST"; then
  echo "[HavalDev] HTTP deploy failed, falling back to Telnet upload"
  "$HEADUNIT_SCRIPT" push-apk "$APK_PATH" "$APK_DEST"
fi

install_remote_apk "$APK_DEST"
restart_app

echo "[HavalDev] Recent application logs"
"$HEADUNIT_SCRIPT" logcat-app || true
