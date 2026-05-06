#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
TELNET_EXEC="$SCRIPT_DIR/telnet-exec.sh"

HEADUNIT_HOST="${HEADUNIT_HOST:-172.20.10.2}"
HEADUNIT_PORT="${HEADUNIT_PORT:-23}"
HEADUNIT_TMP="${HEADUNIT_TMP:-/data/local/tmp}"
APK_DEST="${APK_DEST:-/data/local/tmp/haval-tool-dev.apk}"
APP_PACKAGE="${APP_PACKAGE:-br.com.redesurftank.havalshisuku}"
APP_ACTIVITY="${APP_ACTIVITY:-br.com.redesurftank.havalshisuku/.SplashActivity}"

usage() {
  cat <<EOF
Usage:
  ./tools/headunit-dev/headunit.sh ping
  ./tools/headunit-dev/headunit.sh shell
  ./tools/headunit-dev/headunit.sh exec <remote command>
  ./tools/headunit-dev/headunit.sh push-apk <path.apk>
  ./tools/headunit-dev/headunit.sh install-apk <remote.apk>
  ./tools/headunit-dev/headunit.sh deploy-apk
  ./tools/headunit-dev/headunit.sh deploy-air-control [http-url]
  ./tools/headunit-dev/headunit.sh logcat
  ./tools/headunit-dev/headunit.sh logcat-app
  ./tools/headunit-dev/headunit.sh dump-info
  ./tools/headunit-dev/headunit.sh discover
  ./tools/headunit-dev/headunit.sh list-packages
  ./tools/headunit-dev/headunit.sh list-services
  ./tools/headunit-dev/headunit.sh list-props
  ./tools/headunit-dev/headunit.sh dumpsys
  ./tools/headunit-dev/headunit.sh pull-debug
  ./tools/headunit-dev/headunit.sh pull-file <remote-path> [local-name]
  ./tools/headunit-dev/headunit.sh offline-bundle

Defaults:
  HEADUNIT_HOST=$HEADUNIT_HOST
  HEADUNIT_TMP=$HEADUNIT_TMP
  APK_DEST=$APK_DEST

Deploy air-control via remote curl:
  If [http-url] is provided, headunit downloads app.html from this URL:
  curl -L -o /data/local/tmp/app.html <http-url>; chmod 644 /data/local/tmp/app.html
  Then deploy-air-control auto-restarts by default:
  AIR_CONTROL_RESTART_MODE=activity-init-clean (default, force-stop + BootReceiver + SplashActivity)
  AIR_CONTROL_RESTART_MODE=activity-init      (BootReceiver + SplashActivity)
  AIR_CONTROL_RESTART_MODE=activity         (start activity only)
  AIR_CONTROL_RESTART_MODE=force-stop-start (more aggressive)
  AIR_CONTROL_RESTART_MODE=reboot           (reboot headunit)
EOF
}

require_file() {
  local file="$1"
  [[ -f "$file" ]] || {
    echo "[HavalDev] File not found: $file" >&2
    exit 1
  }
}

remote_exec() {
  "$TELNET_EXEC" "$*"
}

base64_single_line() {
  local file="$1"
  if command -v openssl >/dev/null 2>&1; then
    openssl base64 -A -in "$file"
  else
    base64 < "$file" | tr -d '\n'
  fi
}

push_apk() {
  local apk_path="$1"
  local remote_dest="${2:-$APK_DEST}"
  local remote_b64="${remote_dest}.b64"
  local chunk

  require_file "$apk_path"

  echo "[HavalDev] Uploading $apk_path to $HEADUNIT_HOST:$remote_dest"
  remote_exec "rm -f '$remote_b64' '$remote_dest'"

  while IFS= read -r chunk; do
    remote_exec "printf '%s' '$chunk' >> '$remote_b64'"
  done < <(base64_single_line "$apk_path" | fold -w 700)

  remote_exec "if command -v base64 >/dev/null 2>&1; then base64 -d '$remote_b64' > '$remote_dest'; elif command -v busybox >/dev/null 2>&1; then busybox base64 -d '$remote_b64' > '$remote_dest'; else echo 'missing base64 on target' >&2; exit 1; fi"
  remote_exec "chmod 644 '$remote_dest' && ls -l '$remote_dest' && rm -f '$remote_b64'"
}

install_apk() {
  local remote_apk="${1:-$APK_DEST}"
  echo "[HavalDev] Installing $remote_apk"
  remote_exec "pm install -r '$remote_apk' || cmd package install -r '$remote_apk'"
}

pull_debug_bundle() {
  local output_dir="$SCRIPT_DIR/output/pull-debug-$(date '+%Y%m%d-%H%M%S')"
  local local_archive="$output_dir/debug-bundle.tar.gz"
  local raw_capture="$output_dir/debug-bundle.base64"

  mkdir -p "$output_dir"

  echo "[HavalDev] Pulling remote debug bundle from $HEADUNIT_TMP"
  "$TELNET_EXEC" "tar -czf - '$HEADUNIT_TMP' 2>/dev/null | base64" > "$raw_capture"

  if [[ ! -s "$raw_capture" ]]; then
    echo "[HavalDev] No data returned from remote tar/base64 command" >&2
    exit 1
  fi

  if command -v openssl >/dev/null 2>&1; then
    openssl base64 -d -A -in "$raw_capture" -out "$local_archive"
  else
    base64 -d < "$raw_capture" > "$local_archive"
  fi

  echo "[HavalDev] Saved bundle to $local_archive"
}

case "${1:-}" in
  ping)
    ping -c 1 "$HEADUNIT_HOST"
    ;;
  shell)
    if command -v telnet >/dev/null 2>&1; then
      exec telnet "$HEADUNIT_HOST" "$HEADUNIT_PORT"
    elif command -v nc >/dev/null 2>&1; then
      exec nc "$HEADUNIT_HOST" "$HEADUNIT_PORT"
    else
      echo "Neither telnet nor nc is available locally" >&2
      exit 1
    fi
    ;;
  exec)
    shift
    [[ $# -gt 0 ]] || { usage >&2; exit 1; }
    remote_exec "$*"
    ;;
  push-apk)
    shift
    [[ $# -ge 1 ]] || { usage >&2; exit 1; }
    push_apk "$1" "${2:-$APK_DEST}"
    ;;
  install-apk)
    shift
    install_apk "${1:-$APK_DEST}"
    ;;
  deploy-apk)
    exec "$SCRIPT_DIR/deploy-apk.sh"
    ;;
  deploy-air-control)
    shift
    if [[ $# -ge 1 ]]; then
      AIR_CONTROL_REMOTE_URL="$1" exec "$SCRIPT_DIR/deploy-air-control.sh"
    else
      exec "$SCRIPT_DIR/deploy-air-control.sh"
    fi
    ;;
  logcat)
    remote_exec "logcat -d -v time | tail -n 400"
    ;;
  logcat-app)
    remote_exec "logcat -d -v time | grep -Ei '${APP_PACKAGE}|HavalShisuku|InstrumentProjector2|ServiceManager|HavalDev' | tail -n 400"
    ;;
  dump-info)
    exec "$SCRIPT_DIR/collect-diagnostics.sh" diagnostics
    ;;
  discover)
    exec "$SCRIPT_DIR/collect-diagnostics.sh" discover
    ;;
  list-packages)
    remote_exec "pm list packages"
    ;;
  list-services)
    remote_exec "service list"
    ;;
  list-props)
    remote_exec "getprop"
    ;;
  dumpsys)
    remote_exec "dumpsys"
    ;;
  pull-debug)
    pull_debug_bundle
    ;;
  pull-file)
    shift
    [[ $# -ge 1 ]] || { usage >&2; exit 1; }
    exec "$SCRIPT_DIR/pull-remote-file.sh" "$@"
    ;;
  offline-bundle)
    exec "$SCRIPT_DIR/package-offline-analysis.sh"
    ;;
  -h|--help|help|"")
    usage
    ;;
  *)
    echo "Unknown command: $1" >&2
    usage >&2
    exit 1
    ;;
esac
