#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
HEADUNIT_TOOL="$ROOT_DIR/tools/headunit-dev/headunit.sh"

HEADUNIT_HOST="${HEADUNIT_HOST:-172.20.10.2}"
ADB_SERIAL="${ADB_SERIAL:-$HEADUNIT_HOST:5555}"
LOCAL_APK="${LOCAL_APK:-$ROOT_DIR/scripts/.build/AndroidAutoService_report_action_stock48ff_signed.apk}"
REMOTE_UPLOAD="${REMOTE_UPLOAD:-/data/local/tmp/AndroidAutoService_report_action_stock48ff_signed.apk}"
PATCH_DIR="/data/local/tmp/aa_patches"
SERVICE_APK="AndroidAutoService.apk"
VENDOR_SERVICE="/vendor/app/AndroidAutoService/AndroidAutoService.apk"
VENDOR_SERVICE_OAT="/vendor/app/AndroidAutoService/oat"
EMPTY_OAT="$PATCH_DIR/empty_oat"
STOCK_BACKUP="$PATCH_DIR/AndroidAutoService-stock-48ff-20260624.apk"
PREV_BACKUP="$PATCH_DIR/AndroidAutoService-prev-54df-20260624.apk"
EXPECTED_STOCK_MD5="48ffded64e9b485521e3174dcd70db27"
EXPECTED_STAGING_MD5="54df14713bf26466af55a76382a67ce6"
EXPECTED_CANDIDATE_MD5="27c62526636f6b67cd6724d0caebb06e"
EXPECTED_CANDIDATE_SHA256="d33fea28fd794eae7b7d155227f2709961c884ad5a212ce6b1db7e54f92433db"
KNOWN_BAD_STOCK48FF_REASON="2026-06-24 deploy failed before play/pause validation: GalReceiver Failed to set client certificate / SSL initialization failed"
SERVICE_PACKAGE="com.ts.androidauto.projectionservice"
LEGACY_SERVICE_PACKAGE="com.ts.androidauto"
APP_PACKAGE="com.ts.androidauto.app"
APP_SERVICE_ACTION="com.ts.androidauto.action.AndroidAutoService"
APP_SERVICE_COMPONENT="com.ts.androidauto.projectionservice/.AndroidAutoService"

ACTION="${1:-preflight}"
EXECUTE=0
VEHICLE_STOPPED=0

usage() {
  cat <<'EOF'
Usage:
  scripts/aa-patches/deploy_android_auto_service_stock48ff.sh preflight
  scripts/aa-patches/deploy_android_auto_service_stock48ff.sh deploy --execute --vehicle-stopped
  scripts/aa-patches/deploy_android_auto_service_stock48ff.sh rollback --execute
  scripts/aa-patches/deploy_android_auto_service_stock48ff.sh postcheck

Environment:
  HEADUNIT_HOST  Default: 172.20.10.2
  ADB_SERIAL     Default: $HEADUNIT_HOST:5555
  LOCAL_APK      Default: scripts/.build/AndroidAutoService_report_action_stock48ff_signed.apk

Safety:
  - preflight and postcheck are read-only.
  - deploy is disabled by default because this stock48ff candidate is known bad.
  - forensic deploy requires --execute --vehicle-stopped and ALLOW_KNOWN_BAD_STOCK48FF=1.
  - rollback refuses to run without --execute.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    preflight|deploy|rollback|postcheck)
      ACTION="$1"
      ;;
    --execute)
      EXECUTE=1
      ;;
    --vehicle-stopped)
      VEHICLE_STOPPED=1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
  shift
done

md5_file() {
  local file="$1"
  if command -v md5 >/dev/null 2>&1; then
    md5 -q "$file"
  else
    md5sum "$file" | awk '{print $1}'
  fi
}

sha256_file() {
  local file="$1"
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$file" | awk '{print $1}'
  else
    sha256sum "$file" | awk '{print $1}'
  fi
}

require_tool() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required tool: $1" >&2
    exit 1
  }
}

adb_cmd() {
  adb -s "$ADB_SERIAL" "$@"
}

adb_shell() {
  adb_cmd shell "$@"
}

root_exec() {
  local command="$*"
  command="$(printf '%s' "$command" | tr '\n' ';' | sed 's/^[;[:space:]]*//; s/[;[:space:]]*$//')"
  HEADUNIT_HOST="$HEADUNIT_HOST" "$HEADUNIT_TOOL" exec "$command"
}

candidate_size() {
  stat -f '%z' "$LOCAL_APK" 2>/dev/null || stat -c '%s' "$LOCAL_APK"
}

connect_adb() {
  adb connect "$ADB_SERIAL" >/dev/null 2>&1 || true
  adb_cmd get-state >/dev/null
}

verify_local_candidate() {
  [[ -f "$LOCAL_APK" ]] || {
    echo "Missing local candidate: $LOCAL_APK" >&2
    exit 1
  }

  local md5 sha size
  md5="$(md5_file "$LOCAL_APK")"
  sha="$(sha256_file "$LOCAL_APK")"
  size="$(candidate_size)"

  echo "local_apk=$LOCAL_APK"
  echo "local_size=$size"
  echo "local_md5=$md5"
  echo "local_sha256=$sha"

  [[ "$md5" == "$EXPECTED_CANDIDATE_MD5" ]] || {
    echo "Unexpected candidate MD5: $md5" >&2
    exit 1
  }
  [[ "$sha" == "$EXPECTED_CANDIDATE_SHA256" ]] || {
    echo "Unexpected candidate SHA-256: $sha" >&2
    exit 1
  }

  local apksigner="${APKSIGNER:-$HOME/Library/Android/sdk/build-tools/36.0.0/apksigner}"
  if [[ -x "$apksigner" ]]; then
    "$apksigner" verify --verbose "$LOCAL_APK" | sed 's/^/apksigner: /'
  else
    echo "apksigner: skipped, not found at $apksigner"
  fi
}

remote_state() {
  connect_adb
  adb_shell "
    printf 'mounts='
    mount | grep AndroidAutoService.apk || true
    printf 'vendor_md5='
    md5sum '$VENDOR_SERVICE' 2>/dev/null || true
    printf 'staging_md5='
    md5sum '$PATCH_DIR/$SERVICE_APK' 2>/dev/null || true
    printf 'stock_backup_md5='
    md5sum '$STOCK_BACKUP' 2>/dev/null || true
    printf 'prev_backup_md5='
    md5sum '$PREV_BACKUP' 2>/dev/null || true
    printf 'remote_candidate_md5='
    md5sum '$REMOTE_UPLOAD' 2>/dev/null || true
    printf 'pids='
    pidof br.com.redesurftank.havalshisuku '$APP_PACKAGE' '$LEGACY_SERVICE_PACKAGE' '$SERVICE_PACKAGE' 2>/dev/null || true
    printf '\npackage='
    dumpsys package br.com.redesurftank.havalshisuku | grep -E 'versionCode|versionName|lastUpdateTime' | head -n 8 || true
  "
}

assert_remote_ready_for_deploy() {
  local mount_lines vendor_md5
  mount_lines="$(adb_shell "mount | grep AndroidAutoService.apk || true" | tr -d '\r')"
  vendor_md5="$(adb_shell "md5sum '$VENDOR_SERVICE' 2>/dev/null | awk '{print \$1}'" | tr -d '\r[:space:]')"

  if [[ -n "$mount_lines" ]]; then
    echo "Refusing deploy: AndroidAutoService mount is already active:" >&2
    printf '%s\n' "$mount_lines" >&2
    exit 1
  fi
  if [[ "$vendor_md5" != "$EXPECTED_STOCK_MD5" ]]; then
    echo "Refusing deploy: vendor MD5 is $vendor_md5, expected $EXPECTED_STOCK_MD5" >&2
    exit 1
  fi
}

preflight() {
  require_tool adb
  verify_local_candidate
  remote_state
  assert_remote_ready_for_deploy
  echo "preflight=ok"
}

deploy() {
  if [[ "$EXECUTE" -ne 1 || "$VEHICLE_STOPPED" -ne 1 ]]; then
    echo "Refusing deploy. Re-run with: deploy --execute --vehicle-stopped" >&2
    exit 1
  fi
  if [[ "${ALLOW_KNOWN_BAD_STOCK48FF:-0}" != "1" ]]; then
    echo "Refusing deploy: stock48ff candidate is known bad." >&2
    echo "$KNOWN_BAD_STOCK48FF_REASON" >&2
    echo "Set ALLOW_KNOWN_BAD_STOCK48FF=1 only for a deliberate forensic reproduction." >&2
    exit 1
  fi

  preflight

  echo "push=$REMOTE_UPLOAD"
  adb_cmd push "$LOCAL_APK" "$REMOTE_UPLOAD"

  local remote_md5
  remote_md5="$(adb_shell "md5sum '$REMOTE_UPLOAD' 2>/dev/null | awk '{print \$1}'" | tr -d '\r[:space:]')"
  [[ "$remote_md5" == "$EXPECTED_CANDIDATE_MD5" ]] || {
    echo "Remote upload MD5 mismatch: $remote_md5" >&2
    exit 1
  }

  adb_cmd logcat -c || true

  root_exec "
    set -e
    mkdir -p '$PATCH_DIR' '$EMPTY_OAT'
    chmod 755 '$PATCH_DIR' '$EMPTY_OAT'
    [ -f '$STOCK_BACKUP' ] || cp '$VENDOR_SERVICE' '$STOCK_BACKUP'
    [ -f '$PREV_BACKUP' ] || cp '$PATCH_DIR/$SERVICE_APK' '$PREV_BACKUP' 2>/dev/null || true
    cp '$REMOTE_UPLOAD' '$PATCH_DIR/$SERVICE_APK'
    chmod 644 '$PATCH_DIR/$SERVICE_APK'
    chcon u:object_r:vendor_app_file:s0 '$PATCH_DIR/$SERVICE_APK' || true
    umount -l '$VENDOR_SERVICE' 2>/dev/null || true
    [ -d '$VENDOR_SERVICE_OAT' ] && umount -l '$VENDOR_SERVICE_OAT' 2>/dev/null || true
    rm -f /data/dalvik-cache/arm64/*AndroidAutoService* 2>/dev/null || true
    rm -f /data/dalvik-cache/arm64/*com.ts.androidauto* 2>/dev/null || true
    rm -f /data/resource-cache/*AndroidAutoService* 2>/dev/null || true
    mount --bind '$PATCH_DIR/$SERVICE_APK' '$VENDOR_SERVICE'
    [ -d '$VENDOR_SERVICE_OAT' ] && mount --bind '$EMPTY_OAT' '$VENDOR_SERVICE_OAT' || true
    am force-stop '$SERVICE_PACKAGE' || true
    am force-stop '$LEGACY_SERVICE_PACKAGE' || true
    am force-stop '$APP_PACKAGE' || true
    am startservice --user 0 -a '$APP_SERVICE_ACTION' -n '$APP_SERVICE_COMPONENT' || true
    md5sum '$VENDOR_SERVICE'
    mount | grep AndroidAutoService.apk || true
  "

  postcheck
}

rollback() {
  if [[ "$EXECUTE" -ne 1 ]]; then
    echo "Refusing rollback. Re-run with: rollback --execute" >&2
    exit 1
  fi

  require_tool adb
  connect_adb

  root_exec "
    set -e
    umount -l '$VENDOR_SERVICE' 2>/dev/null || true
    [ -d '$VENDOR_SERVICE_OAT' ] && umount -l '$VENDOR_SERVICE_OAT' 2>/dev/null || true
    if [ -f '$PREV_BACKUP' ]; then
      cp '$PREV_BACKUP' '$PATCH_DIR/$SERVICE_APK'
      chmod 644 '$PATCH_DIR/$SERVICE_APK'
      chcon u:object_r:vendor_app_file:s0 '$PATCH_DIR/$SERVICE_APK' || true
    fi
    rm -f /data/dalvik-cache/arm64/*AndroidAutoService* 2>/dev/null || true
    rm -f /data/dalvik-cache/arm64/*com.ts.androidauto* 2>/dev/null || true
    rm -f /data/resource-cache/*AndroidAutoService* 2>/dev/null || true
    am force-stop '$SERVICE_PACKAGE' || true
    am force-stop '$LEGACY_SERVICE_PACKAGE' || true
    am force-stop '$APP_PACKAGE' || true
    am startservice --user 0 -a '$APP_SERVICE_ACTION' -n '$APP_SERVICE_COMPONENT' || true
    md5sum '$VENDOR_SERVICE'
    md5sum '$PATCH_DIR/$SERVICE_APK' 2>/dev/null || true
    mount | grep AndroidAutoService.apk || true
  "

  postcheck
}

postcheck() {
  require_tool adb
  connect_adb
  remote_state
  echo "recent_crash_filter="
  adb_cmd logcat -d -v time |
    tail -n 3000 |
    grep -E 'AndroidRuntime|FATAL|ANR|VerifyError|GalReceiver|IMPULSE_MEDIA_PATCH|Scheduling restart|com.ts.androidauto.projectionservice' |
    tail -n 220 || true
}

case "$ACTION" in
  preflight)
    preflight
    ;;
  deploy)
    deploy
    ;;
  rollback)
    rollback
    ;;
  postcheck)
    postcheck
    ;;
  *)
    echo "Unknown action: $ACTION" >&2
    usage >&2
    exit 1
    ;;
esac
