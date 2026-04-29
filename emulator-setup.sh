#!/usr/bin/env bash
# emulator-setup.sh
#
# Prepares the AAOS emulator (haval_h6_infotainment / sdk_gcar_arm64) for
# testing the Haval Shisuku app with instrument cluster simulation.
#
# Run once after each emulator cold boot, before launching the app.
#
# Usage:
#   ./emulator-setup.sh              # targets the only running emulator
#   ./emulator-setup.sh emulator-5554  # target a specific serial

set -euo pipefail

ADB="adb"
SERIAL="${1:-}"

if [[ -n "$SERIAL" ]]; then
    ADB="adb -s $SERIAL"
else
    ADB="adb -e"
fi

echo "==> Waiting for device to be ready..."
$ADB wait-for-device
$ADB shell "while [[ \$(getprop sys.boot_completed) != '1' ]]; do sleep 1; done"
echo "    Device ready."

echo ""
echo "==> Gaining root access..."
$ADB root
sleep 2

echo ""
echo "==> Creating virtual overlay display (1920x720, 160 dpi)..."
$ADB shell "settings put global overlay_display_devices '1920x720/160'"
sleep 2

# Print the display ID that was assigned (for reference)
OVERLAY_ID=$($ADB shell "dumpsys display | grep '\"Overlay #1\"' | grep -o 'displayId [0-9]*' | awk '{print \$2}' | head -1" 2>/dev/null || echo "unknown")
echo "    Overlay display ID: $OVERLAY_ID"

echo ""
echo "==> Disabling AAOS stock cluster packages..."
for PKG in com.android.car.cluster.osdouble com.android.car.cluster.home; do
    RESULT=$($ADB shell pm disable "$PKG" 2>&1 || true)
    echo "    $PKG: $RESULT"
    $ADB shell am force-stop "$PKG" 2>/dev/null || true
done

echo ""
echo "==> Launching the Haval Shisuku app..."
$ADB shell am start -n br.com.redesurftank.havalshisuku/.MainActivity
echo "    App launched."

echo ""
echo "================================================================"
echo " Emulator setup complete."
echo ""
echo " Cluster overlay: Display $OVERLAY_ID (1920x720)"
echo " ClusterHome:     disabled"
echo ""
echo " The app will auto-detect the overlay display by name."
echo " To re-run at any time: ./emulator-setup.sh"
echo "================================================================"
