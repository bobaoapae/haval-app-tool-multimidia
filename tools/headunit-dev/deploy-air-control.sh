#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
TELNET_EXEC="$SCRIPT_DIR/telnet-exec.sh"
source "$SCRIPT_DIR/headunit-env.sh"

AIR_CONTROL_DIR="${AIR_CONTROL_DIR:-$ROOT_DIR/cluster-widgets/air-control}"
REMOTE_HTML_PATH="${REMOTE_HTML_PATH:-/data/local/tmp/app.html}"
resolve_headunit_defaults
HTTP_PORT="${HTTP_PORT:-8766}"
HTTP_PORT_SEARCH_LIMIT="${HTTP_PORT_SEARCH_LIMIT:-20}"
AIR_CONTROL_REMOTE_URL="${AIR_CONTROL_REMOTE_URL:-}"
AIR_CONTROL_SKIP_BUILD="${AIR_CONTROL_SKIP_BUILD:-0}"
AIR_CONTROL_AUTO_RESTART="${AIR_CONTROL_AUTO_RESTART:-1}"
AIR_CONTROL_RESTART_MODE="${AIR_CONTROL_RESTART_MODE:-activity-init-clean}" # activity-init-clean | activity-init | activity | force-stop-start | reboot
AIR_CONTROL_MIN_BYTES="${AIR_CONTROL_MIN_BYTES:-100000}"
APP_PACKAGE="${APP_PACKAGE:-br.com.redesurftank.havalshisuku}"
APP_ACTIVITY="${APP_ACTIVITY:-br.com.redesurftank.havalshisuku/.SplashActivity}"

require_file() {
  local file="$1"
  [[ -f "$file" ]] || {
    echo "[HavalDev] File not found: $file" >&2
    exit 1
  }
}

base64_single_line() {
  local file="$1"
  if command -v openssl >/dev/null 2>&1; then
    openssl base64 -A -in "$file"
  else
    base64 < "$file" | tr -d '\n'
  fi
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

push_html_via_http() {
  local html_path="$1"
  local remote_path="$2"
  local host_ip
  local server_log
  local server_pid
  local server_port
  local url
  local remote_cmd

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
  python3 -m http.server "$server_port" --bind 0.0.0.0 --directory "$(dirname "$html_path")" > "$server_log" 2>&1 &
  server_pid="$!"
  trap 'if [[ -n "${server_pid:-}" ]]; then kill "$server_pid" >/dev/null 2>&1 || true; fi; if [[ -n "${server_log:-}" ]]; then rm -f "$server_log"; fi' RETURN

  sleep 1
  if ! kill -0 "$server_pid" >/dev/null 2>&1; then
    echo "[HavalDev] Local HTTP server failed to start" >&2
    sed -n '1,80p' "$server_log" >&2
    return 1
  fi

  url="http://${host_ip}:${server_port}/$(basename "$html_path")"
  echo "[HavalDev] Asking headunit to download $url"

  remote_cmd="rm -f '${remote_path}.tmp' '${remote_path}' '${remote_path}.download.log'; nohup sh -c \"(curl -fsSL '$url' -o '${remote_path}.tmp' || wget -O '${remote_path}.tmp' '$url' || toybox wget -O '${remote_path}.tmp' '$url' || busybox wget -O '${remote_path}.tmp' '$url') && mv '${remote_path}.tmp' '${remote_path}' && chmod 644 '${remote_path}'\" > '${remote_path}.download.log' 2>&1 &"
  "$TELNET_EXEC" "$remote_cmd" >/dev/null || true

  local expected_size
  expected_size="$(wc -c < "$html_path" | tr -d ' ')"
  for _ in $(seq 1 60); do
    local remote_size
    remote_size="$("$TELNET_EXEC" "wc -c '$remote_path' 2>/dev/null | awk '{print \\$1}'" 2>/dev/null | tr -cd '0-9' | tail -c 20)"
    if [[ "$remote_size" == "$expected_size" ]]; then
      echo "[HavalDev] Headunit downloaded app.html via HTTP"
      return 0
    fi
    sleep 1
  done

  echo "[HavalDev] Headunit did not complete app.html download from local HTTP server" >&2
  sed -n '1,80p' "$server_log" >&2
  "$TELNET_EXEC" "cat '${remote_path}.download.log' 2>/dev/null | tail -n 40" >&2 || true
  return 1
}

push_html_from_remote_url() {
  local remote_url="$1"
  local remote_path="$2"
  local remote_tmp="${remote_path}.tmp"
  local min_bytes="$AIR_CONTROL_MIN_BYTES"
  local remote_size_raw
  local remote_size

  echo "[HavalDev] Sending app.html to $HEADUNIT_HOST using remote curl"
  echo "[HavalDev] URL: $remote_url"
  "$TELNET_EXEC" "rm -f '$remote_tmp'; curl -fL -o '$remote_tmp' '$remote_url' || exit 1; size=\$(wc -c '$remote_tmp' | awk '{print \$1}'); if [ \"\$size\" -lt '$min_bytes' ]; then echo '[HavalDev] downloaded file too small:' \"\$size\" >&2; rm -f '$remote_tmp'; exit 2; fi; mv '$remote_tmp' '$remote_path'; chmod 644 '$remote_path'; wc -c '$remote_path'; ls -l '$remote_path'"

  remote_size_raw="$("$TELNET_EXEC" "wc -c '$remote_path' 2>/dev/null | awk '{print \\$1}'" 2>/dev/null || true)"
  remote_size="$(printf '%s' "$remote_size_raw" | tr -cd '0-9' | head -c 20)"
  if [[ -z "$remote_size" || "$remote_size" -lt "$min_bytes" ]]; then
    echo "[HavalDev] Remote app.html validation failed (size=$remote_size, min=$min_bytes). Aborting restart." >&2
    return 1
  fi

  echo "[HavalDev] Remote app.html validated (size=$remote_size)"
}

restart_after_deploy() {
  if [[ "$AIR_CONTROL_AUTO_RESTART" != "1" ]]; then
    echo "[HavalDev] Auto-restart disabled (AIR_CONTROL_AUTO_RESTART=$AIR_CONTROL_AUTO_RESTART)"
    return 0
  fi

  if [[ "$AIR_CONTROL_RESTART_MODE" == "reboot" ]]; then
    echo "[HavalDev] Restarting headunit (reboot)"
    "$TELNET_EXEC" "reboot" || true
    return 0
  fi

  if [[ "$AIR_CONTROL_RESTART_MODE" == "force-stop-start" ]]; then
    echo "[HavalDev] Restarting app (force-stop + start)"
    "$TELNET_EXEC" "am force-stop '$APP_PACKAGE'; am start -n '$APP_ACTIVITY'" || true
    return 0
  fi

  if [[ "$AIR_CONTROL_RESTART_MODE" == "activity-init-clean" ]]; then
    echo "[HavalDev] Restarting with clean init sequence (force-stop + service start + SplashActivity)"
    "$TELNET_EXEC" "am force-stop '$APP_PACKAGE'; am start-foreground-service -n '$APP_PACKAGE/.services.ForegroundService' || am startservice -n '$APP_PACKAGE/.services.ForegroundService'; sleep 3; am start -W -n '$APP_ACTIVITY'" || true
    return 0
  fi

  if [[ "$AIR_CONTROL_RESTART_MODE" == "activity-init" ]]; then
    echo "[HavalDev] Restarting with init sequence (service start + SplashActivity)"
    "$TELNET_EXEC" "am start-foreground-service -n '$APP_PACKAGE/.services.ForegroundService' || am startservice -n '$APP_PACKAGE/.services.ForegroundService'; sleep 2; am start -W -n '$APP_ACTIVITY'" || true
    return 0
  fi

  echo "[HavalDev] Restarting activity only (safe mode)"
  "$TELNET_EXEC" "am start -n '$APP_ACTIVITY' || monkey -p '$APP_PACKAGE' -c android.intent.category.LAUNCHER 1" || true
}

push_html() {
  local html_path="$1"
  local remote_path="$2"
  local remote_b64="${remote_path}.b64"
  local chunk
  local batch_file
  local batch_output
  local netcat_bin

  echo "[HavalDev] Sending app.html to $HEADUNIT_HOST:$remote_path"

  if push_html_via_http "$html_path" "$remote_path"; then
    return
  fi

  if [[ "${AIR_CONTROL_NO_BASE64_FALLBACK:-0}" == "1" ]]; then
    echo "[HavalDev] HTTP deploy failed and AIR_CONTROL_NO_BASE64_FALLBACK=1, aborting without Telnet/base64 upload" >&2
    exit 1
  fi

  echo "[HavalDev] HTTP deploy failed, falling back to Telnet chunk upload"

  if command -v nc >/dev/null 2>&1; then
    netcat_bin="nc"
  elif command -v netcat >/dev/null 2>&1; then
    netcat_bin="netcat"
  else
    echo "[HavalDev] netcat not found, falling back to telnet-exec chunk mode"
    "$TELNET_EXEC" "rm -f '$remote_b64' '$remote_path'"
    while IFS= read -r chunk; do
      "$TELNET_EXEC" "printf '%s' '$chunk' >> '$remote_b64'"
    done < <(base64_single_line "$html_path" | fold -w 700)
    "$TELNET_EXEC" "if command -v base64 >/dev/null 2>&1; then base64 -d '$remote_b64' > '$remote_path'; elif command -v busybox >/dev/null 2>&1; then busybox base64 -d '$remote_b64' > '$remote_path'; else echo 'missing base64 on target' >&2; exit 1; fi"
    "$TELNET_EXEC" "chmod 644 '$remote_path' && ls -l '$remote_path' && rm -f '$remote_b64'"
    return
  fi

  batch_file="$(mktemp)"
  batch_output="$(mktemp)"
  trap 'rm -f "$batch_file" "$batch_output"' RETURN

  {
    printf "rm -f '%s' '%s'\n" "$remote_b64" "$remote_path"
    while IFS= read -r chunk; do
      printf "printf '%%s' '%s' >> '%s'\n" "$chunk" "$remote_b64"
    done < <(base64_single_line "$html_path" | fold -w 700)
    printf "if command -v base64 >/dev/null 2>&1; then base64 -d '%s' > '%s'; elif command -v busybox >/dev/null 2>&1; then busybox base64 -d '%s' > '%s'; else echo 'missing base64 on target' >&2; exit 1; fi\n" "$remote_b64" "$remote_path" "$remote_b64" "$remote_path"
    printf "chmod 644 '%s' && ls -l '%s' && rm -f '%s'\n" "$remote_path" "$remote_path" "$remote_b64"
    printf "echo __HAVALDEV_DONE__\n"
  } > "$batch_file"

  if [[ "$netcat_bin" == "nc" ]]; then
    {
      sed 's/$/\r/' "$batch_file"
      sleep 1
    } | "$netcat_bin" -w 3 "$HEADUNIT_HOST" 23 > "$batch_output" 2>/dev/null || true
  else
    {
      sed 's/$/\r/' "$batch_file"
      sleep 1
    } | "$netcat_bin" -w 3 "$HEADUNIT_HOST" 23 > "$batch_output" 2>/dev/null || true
  fi

  if ! LC_ALL=C grep -q "__HAVALDEV_DONE__" "$batch_output"; then
    echo "[HavalDev] Upload command batch did not confirm completion" >&2
    echo "[HavalDev] Retrying with telnet-exec chunk mode"
    "$TELNET_EXEC" "rm -f '$remote_b64' '$remote_path'"
    while IFS= read -r chunk; do
      "$TELNET_EXEC" "printf '%s' '$chunk' >> '$remote_b64'"
    done < <(base64_single_line "$html_path" | fold -w 700)
    "$TELNET_EXEC" "if command -v base64 >/dev/null 2>&1; then base64 -d '$remote_b64' > '$remote_path'; elif command -v busybox >/dev/null 2>&1; then busybox base64 -d '$remote_b64' > '$remote_path'; else echo 'missing base64 on target' >&2; exit 1; fi"
    "$TELNET_EXEC" "chmod 644 '$remote_path' && ls -l '$remote_path' && rm -f '$remote_b64'"
  fi
}

echo "[HavalDev] Building air-control frontend"
if [[ -n "$AIR_CONTROL_REMOTE_URL" ]]; then
  push_html_from_remote_url "$AIR_CONTROL_REMOTE_URL" "$REMOTE_HTML_PATH"
  restart_after_deploy
  echo "[HavalDev] Hot deploy complete. APK was not reinstalled."
  exit 0
fi

if [[ "$AIR_CONTROL_SKIP_BUILD" == "1" ]]; then
  echo "[HavalDev] Skipping build (AIR_CONTROL_SKIP_BUILD=1)"
else
  cd "$AIR_CONTROL_DIR"

  if command -v bun >/dev/null 2>&1; then
    bun run build
  else
    echo "[HavalDev] bun not found, using npm run build"
    npm run build
  fi
fi

HTML_PATH="$AIR_CONTROL_DIR/dist/app.html"
if [[ ! -f "$HTML_PATH" ]]; then
  HTML_PATH="$(find "$AIR_CONTROL_DIR" -maxdepth 3 -type f -name 'app.html' | sort | head -n 1)"
fi

require_file "$HTML_PATH"
push_html "$HTML_PATH" "$REMOTE_HTML_PATH"
restart_after_deploy

echo "[HavalDev] Hot deploy complete. APK was not reinstalled."
