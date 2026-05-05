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
#
# NOTE on the AAOS stock cluster (com.android.car.cluster.osdouble /
# com.android.car.cluster.home):
#
#   The stock cluster CANNOT be rendered onto our overlay display by simply
#   launching its activity — ClusterOsDoubleActivity is a mirror Presentation
#   whose source virtual display is wired up by ClusterRenderingService at
#   boot. Launched ad-hoc, its SurfaceView is opaque black.
#
#   We also CANNOT disable these packages — `car_service` keeps trying to
#   bind ClusterOsDoubleApplication for user 10, and a disabled package
#   makes that bind crash-loop, dragging systemui down with it.
#
#   The right answer is to leave them enabled. They render to their own
#   internal cluster virtual displays (4 and 6) which are off-screen, so
#   the Haval WebView is the only thing visible on the overlay anyway.

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
echo "==> Ensuring AAOS stock cluster packages are enabled (user 10)..."
# Do NOT pm-disable these. AAOS car_service expects the cluster framework
# to be present and will repeatedly try to bind ClusterOsDoubleApplication
# for user 10 (the car driver profile). If the package is disabled FOR THAT
# USER, the bind fails and the cluster process crash-loops at ~10 Hz,
# dragging systemui down with it and stealing focus from our main app.
#
# Note: `pm enable <pkg>` without --user only affects user 0. We must
# explicitly enable for user 10 too, because that is the user car_service
# binds against.
for PKG in com.android.car.cluster.osdouble com.android.car.cluster.home; do
    R0=$($ADB shell pm enable --user 0  "$PKG" 2>&1 || true)
    R10=$($ADB shell pm enable --user 10 "$PKG" 2>&1 || true)
    echo "    $PKG: u0=$R0 | u10=$R10"
done

echo ""
echo "==> Working around two AAOS emulator systemui bugs..."
# Bug 1: KeyguardSliceProvider.onCreateSliceProvider NPEs because
#        NotificationMediaManager isn't ready at provider install time.
#        We don't use keyguard media hints — disable just that provider.
# Bug 2: SystemUIService crashes with SecurityException because systemui
#        is missing the BLUETOOTH_CONNECT runtime permission on this image.
#        Granting it unblocks SystemUIService.create().
# Without these two fixes, systemui crash-loops ~4/sec, which causes
# the visible status/nav-bar flicker on the main display.
for U in 0 10; do
    $ADB shell "pm disable --user $U com.android.systemui/com.android.systemui.keyguard.KeyguardSliceProvider" 2>&1 | sed 's/^/    /'
    $ADB shell "pm grant   --user $U com.android.systemui android.permission.BLUETOOTH_CONNECT" 2>&1 | sed 's/^/    /'
done
$ADB shell "killall -9 com.android.systemui" 2>/dev/null || true

echo ""
echo "==> Granting AAOS Car API runtime permissions (user 10)..."
PKG="br.com.redesurftank.havalshisuku"
for PERM in android.car.permission.CAR_SPEED \
            android.car.permission.CAR_ENGINE_DETAILED \
            android.car.permission.CAR_INFO \
            android.car.permission.CAR_POWERTRAIN \
            android.car.permission.CAR_ENERGY \
            android.car.permission.CAR_EXTERIOR_ENVIRONMENT; do
    RESULT=$($ADB shell pm grant --user 10 "$PKG" "$PERM" 2>&1 || true)
    echo "    $PERM: ${RESULT:-OK}"
done

echo ""
echo "==> Launching the Haval Shisuku app (user 10, SplashActivity)..."
# AAOS runs the driver profile as user 10. Without --user 10 the activity
# launches into user 0's stack, which is not the foreground occupant zone, so
# nothing visible appears on display 0. SplashActivity is the manifest entry
# point (LAUNCHER intent-filter); MainActivity is internal.
$ADB shell am start --user 10 -n br.com.redesurftank.havalshisuku/.SplashActivity
echo "    App launched."

echo ""
echo "================================================================"
echo " Emulator setup complete."
echo ""
echo " Cluster overlay: Display $OVERLAY_ID (1920x720)"
echo " AAOS cluster:    enabled (renders to its own internal display, off-screen)"
echo " Haval cluster:   the only renderer on the overlay display"
echo " VHAL bridge:     active (speed / gear / ignition / EPB / fuel / battery / temp)"
echo ""
echo " Drive vehicle data:    Extended Controls -> Car sensor data"
echo " Drive cluster keys:    ./cluster-keys.sh   (interactive keyboard)"
echo " Monitor live state:    adb logcat -s VehicleStatus:I"
echo " Re-run setup:          ./emulator-setup.sh"
echo "================================================================"
