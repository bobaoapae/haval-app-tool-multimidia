#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

PACKAGES=(
  "com.neusoft.na.navigation"
  "com.iflytek.cutefly.speechclient.hmi"
  "com.beantechs.voice.adapter"
  "com.beantechs.voiceclient"
  "com.beantechs.voiceprintservice"
)

REMOTE_PACKAGES="${PACKAGES[*]}"

"$SCRIPT_DIR/telnet-exec.sh" "for pkg in $REMOTE_PACKAGES; do cmd package install-existing --user 0 \"\$pkg\" 2>/dev/null || true; pm enable --user 0 \"\$pkg\" 2>/dev/null || pm enable \"\$pkg\" 2>/dev/null || true; done"

"$SCRIPT_DIR/telnet-exec.sh" "pm list packages | grep -E 'neusoft.na.navigation|iflytek|voice' || true; pm list packages -d | grep -E 'neusoft.na.navigation|iflytek|voice' || true; ps -A | grep -E 'neusoft.na.navigation|iflytek|voice' || true"
