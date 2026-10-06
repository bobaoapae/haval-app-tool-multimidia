#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
TELNET_EXEC="$SCRIPT_DIR/telnet-exec.sh"

HEADUNIT_HOST="${HEADUNIT_HOST:-172.20.10.2}"
HEADUNIT_TMP="${HEADUNIT_TMP:-/data/local/tmp}"
APP_PACKAGE="${APP_PACKAGE:-br.com.redesurftank.havalshisuku}"
MODE="${1:-diagnostics}"

timestamp() {
  date '+%Y%m%d-%H%M%S'
}

run_remote() {
  local name="$1"
  local command="$2"
  {
    printf '$ %s\n\n' "$command"
    "$TELNET_EXEC" "$command"
  } > "$OUTPUT_DIR/$name.txt" || true
}

append_report() {
  printf '%s\n' "$1" >> "$REPORT_FILE"
}

case "$MODE" in
  diagnostics)
    OUTPUT_DIR="$SCRIPT_DIR/output/diagnostics-$(timestamp)"
    ;;
  discover)
    OUTPUT_DIR="$SCRIPT_DIR/output/discovery-$(timestamp)"
    ;;
  *)
    echo "Unsupported mode: $MODE" >&2
    echo "Use: diagnostics or discover" >&2
    exit 1
    ;;
esac

mkdir -p "$OUTPUT_DIR"
REPORT_FILE="$OUTPUT_DIR/report.md"

COMMANDS=(
  "getprop::getprop"
  "pm-list-packages::pm list packages"
  "dumpsys-activity-services::dumpsys activity services"
  "dumpsys-activity-broadcasts::dumpsys activity broadcasts"
  "dumpsys-window::dumpsys window"
  "dumpsys-input::dumpsys input"
  "dumpsys-display::dumpsys display"
  "dumpsys-package-app::dumpsys package ${APP_PACKAGE}"
  "logcat-app::logcat -d -v time | grep -Ei '${APP_PACKAGE}|HavalShisuku|InstrumentProjector2|ServiceManager|HavalDev' | tail -n 400"
  "processes::ps"
  "tmp-project-files::ls -la ${HEADUNIT_TMP}"
)

if [[ "$MODE" == "discover" ]]; then
  COMMANDS+=(
    "service-list::service list"
    "broadcasts::dumpsys activity broadcasts"
    "app-permissions::dumpsys package ${APP_PACKAGE} | grep -i -A 20 -B 5 permission"
    "dumpsys-bluetooth::dumpsys bluetooth_manager"
    "dumpsys-audio::dumpsys audio"
    "dumpsys-location::dumpsys location"
    "dumpsys-sensor::dumpsys sensorservice"
  )
fi

for item in "${COMMANDS[@]}"; do
  name="${item%%::*}"
  command="${item#*::}"
  run_remote "$name" "$command"
done

if [[ "$MODE" == "discover" ]]; then
  "$SCRIPT_DIR/static-discovery.sh" "$OUTPUT_DIR"
fi

append_report "# $(tr '[:lower:]' '[:upper:]' <<< "${MODE:0:1}")${MODE:1} Report"
append_report ""
append_report "Generated at: \`$(date '+%Y-%m-%d %H:%M:%S')\`"
append_report ""
append_report "Host: \`$HEADUNIT_HOST\`"
append_report ""
append_report "## Commands Executed"
append_report ""
for item in "${COMMANDS[@]}"; do
  append_report "- \`${item#*::}\`"
done
if [[ "$MODE" == "discover" ]]; then
  append_report "- local static discovery via \`./tools/headunit-dev/static-discovery.sh\`"
fi
append_report ""
append_report "## Files Generated"
append_report ""
find "$OUTPUT_DIR" -maxdepth 1 -type f -printf -- '- `%f`\n' | sort >> "$REPORT_FILE"
append_report ""
append_report "## Possible Data Sources"
append_report ""
append_report "- Android properties from \`getprop.txt\`"
append_report "- System services from \`service-list.txt\` or \`dumpsys-activity-services.txt\`"
append_report "- App permissions and runtime state from \`dumpsys-package-app.txt\`"
append_report "- Recent logs from \`logcat-app.txt\`"
append_report "- JS/Kotlin bridge hits from \`bridge-summary.txt\` and \`static-discovery.md\`"
append_report ""
append_report "## Candidate Variables"
append_report ""
append_report "- HVAC: \`fan\`, \`temp\`, \`power\`, \`recycle\`, \`auto\`, \`aion\`"
append_report "- Vehicle: \`carSpeed\`, \`gearState\`, \`engineRPM\`, \`odometer\`"
append_report "- EV/energy: \`batteryPercent\`, \`batteryRange\`, \`evMode\`, \`regenMode\`, \`evPowerKw\`"
append_report "- UI/navigation: \`cardId\`, \`currentGraph\`, \`warningActive\`, \`display\`"
append_report ""
append_report "## Next Recommended Tests"
append_report ""
append_report "- Validate which properties update continuously in \`logcat-app.txt\` and \`getprop.txt\`."
append_report "- Compare \`service-list.txt\` with \`ServiceManager.java\` bindings before adding new listeners."
append_report "- Use the static bridge summary to decide what can be exposed to the WebView without changing safety-critical code."
append_report "- For frontend-only tests, use local Parcel/Bun build before any APK redeploy."

printf '%s\n' "$OUTPUT_DIR"
