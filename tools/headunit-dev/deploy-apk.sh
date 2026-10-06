#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
HEADUNIT_SCRIPT="$SCRIPT_DIR/headunit.sh"
TELNET_EXEC="$SCRIPT_DIR/telnet-exec.sh"
source "$SCRIPT_DIR/headunit-env.sh"

resolve_headunit_defaults
APK_DEST="${APK_DEST:-/data/local/tmp/haval-tool-dev.apk}"
APP_PACKAGE="${APP_PACKAGE:-br.com.redesurftank.havalshisuku}"
APP_ACTIVITY="${APP_ACTIVITY:-br.com.redesurftank.havalshisuku/.SplashActivity}"
DEV_APP_VERSION_CODE="${DEV_APP_VERSION_CODE:-84}"
DEV_APP_VERSION_NAME="${DEV_APP_VERSION_NAME:-0.0.1}"
HTTP_PORT="${HTTP_PORT:-8765}"
HTTP_PORT_SEARCH_LIMIT="${HTTP_PORT_SEARCH_LIMIT:-20}"
HTTP_DOWNLOAD_WAIT_ATTEMPTS="${HTTP_DOWNLOAD_WAIT_ATTEMPTS:-120}"
APK_INSTALLER_PACKAGE="${APK_INSTALLER_PACKAGE:-}"

require_file() {
  local file="$1"
  [[ -f "$file" ]] || {
    echo "[HavalDev] File not found: $file" >&2
    exit 1
  }
}

print_progress_bar() {
  local label="$1"
  local current="${2:-0}"
  local total="${3:-0}"
  local width=32
  local percent=0
  local filled=0
  local bar=""
  local i

  [[ "$current" =~ ^[0-9]+$ ]] || current=0
  [[ "$total" =~ ^[0-9]+$ ]] || total=0

  if (( total > 0 )); then
    percent=$(( current * 100 / total ))
    (( percent > 100 )) && percent=100
    filled=$(( width * percent / 100 ))
  fi

  for ((i=0; i<width; i++)); do
    if (( i < filled )); then
      bar+="#"
    else
      bar+="."
    fi
  done

  printf '\r[HavalDev] %s [%s] %3d%% %s/%s bytes' "$label" "$bar" "$percent" "$current" "$total"
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
  local remote_state
  local remote_stat
  local printed_progress=0

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

  remote_cmd="rm -f '${remote_path}.tmp' '${remote_path}' '${remote_path}.download.log'; nohup sh -c \"curl -fsSL '$url' -o '${remote_path}.tmp' && mv '${remote_path}.tmp' '${remote_path}' && chmod 644 '${remote_path}'\" > '${remote_path}.download.log' 2>&1 &"
  "$TELNET_EXEC" "$remote_cmd" >/dev/null || true

  for _ in $(seq 1 "$HTTP_DOWNLOAD_WAIT_ATTEMPTS"); do
    remote_stat="$(
      "$TELNET_EXEC" "if [ -f '$remote_path' ]; then printf 'final '; wc -c < '$remote_path'; elif [ -f '${remote_path}.tmp' ]; then printf 'tmp '; wc -c < '${remote_path}.tmp'; else echo 'none 0'; fi" |
        awk '/^(final|tmp|none)[[:space:]]+[0-9]+$/ { state=$1; size=$2 } END { if (state != "") print state, size }'
    )"
    read -r remote_state remote_size <<< "${remote_stat:-none 0}"
    [[ "$remote_size" =~ ^[0-9]+$ ]] || remote_size=0

    print_progress_bar "Downloading APK" "$remote_size" "$expected_size"
    printed_progress=1

    if [[ "$remote_state" == "final" && "$remote_size" == "$expected_size" ]]; then
      printf '\n'
      echo "[HavalDev] Headunit downloaded APK via HTTP ($remote_size bytes)"
      return 0
    fi
    sleep 1
  done

  if (( printed_progress )); then
    printf '\n'
  fi
  echo "[HavalDev] APK download did not complete via HTTP" >&2
  echo "[HavalDev] Expected size: $expected_size, remote size: ${remote_size:-0}" >&2
  sed -n '1,80p' "$server_log" >&2
  "$TELNET_EXEC" "cat '${remote_path}.download.log' 2>/dev/null | tail -n 40" >&2 || true
  return 1
}

install_remote_apk() {
  local remote_apk="$1"
  local output
  local install_args="-r"

  if [[ -n "$APK_INSTALLER_PACKAGE" ]]; then
    install_args="$install_args -i '$APK_INSTALLER_PACKAGE'"
  fi

  echo "[HavalDev] Installing $remote_apk"
  output="$(
    HEADUNIT_TELNET_WAIT="${HEADUNIT_INSTALL_WAIT:-120}" \
      "$TELNET_EXEC" "pm install $install_args '$remote_apk' || cmd package install $install_args '$remote_apk'"
  )"
  printf '%s\n' "$output"
  if printf '%s\n' "$output" | grep -Eiq 'Failure|Exception|Error'; then
    echo "[HavalDev] APK install reported an error" >&2
    return 1
  fi
}

restart_app() {
  echo "[HavalDev] Restarting app"
  "$TELNET_EXEC" "am force-stop '$APP_PACKAGE'; am start -n '$APP_ACTIVITY'" >/dev/null || true
}

cd "$ROOT_DIR"

echo "[HavalDev] Building debug APK (versionCode=$DEV_APP_VERSION_CODE versionName=$DEV_APP_VERSION_NAME)"
./gradlew :app:assembleDebug \
  -PappVersionCode="$DEV_APP_VERSION_CODE" \
  -PappVersionName="$DEV_APP_VERSION_NAME"

APK_PATH="$(find "$ROOT_DIR/app/build/outputs/apk/debug" -maxdepth 1 -type f -name '*.apk' | sort | tail -n 1)"
require_file "$APK_PATH"

echo "[HavalDev] Deploying $APK_PATH"
download_apk_via_http "$APK_PATH" "$APK_DEST"

install_remote_apk "$APK_DEST"
restart_app

echo "[HavalDev] Recent application logs"
"$HEADUNIT_SCRIPT" logcat-app || true
