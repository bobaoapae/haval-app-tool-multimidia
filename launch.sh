#!/usr/bin/env bash
# launch.sh
#
# One-shot bring-up: boot the AAOS AVD if it isn't already running, wait for
# the system to finish booting, then run ./emulator-setup.sh against it.
#
# Usage:
#   ./launch.sh                      # auto-pick the first AVD whose name
#                                    # contains "haval" or "gcar", else
#                                    # whichever AVD lists first
#   ./launch.sh haval_h6_infotainment   # explicit AVD name
#   AVD=sdk_gcar_arm64 ./launch.sh    # ditto via env var
#
# Flags:
#   FRESH=1 ./launch.sh   # boot from clean state (passes -no-snapshot-load)
#   WIPE=1  ./launch.sh   # nuke userdata before booting (passes -wipe-data)

set -euo pipefail

SDK="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
EMULATOR_BIN="$SDK/emulator/emulator"
ADB_BIN="$SDK/platform-tools/adb"

if [[ ! -x "$EMULATOR_BIN" ]]; then
    echo "ERROR: emulator binary not found at $EMULATOR_BIN" >&2
    echo "       set ANDROID_SDK_ROOT if your SDK is elsewhere." >&2
    exit 1
fi
if [[ ! -x "$ADB_BIN" ]]; then
    echo "ERROR: adb not found at $ADB_BIN" >&2
    exit 1
fi

cd "$(dirname "$0")"

AVD="${1:-${AVD:-}}"

# Snapshot the list of installed AVDs once — used for validation below.
AVD_LIST=$("$EMULATOR_BIN" -list-avds 2>/dev/null \
    | grep -E '^[A-Za-z0-9_.-]+$' || true)

if [[ -z "$AVD" ]]; then
    # Auto-pick: prefer haval / gcar AVDs, else fall back to the first listed.
    AVD=$(echo "$AVD_LIST" | grep -iE 'haval|gcar' | head -1 || true)
    if [[ -z "$AVD" ]]; then
        AVD=$(echo "$AVD_LIST" | head -1 || true)
    fi
fi

if [[ -z "$AVD" ]]; then
    echo "ERROR: no AVD found. Run '$EMULATOR_BIN -list-avds' to see options," >&2
    echo "       then call: ./launch.sh <avd-name>" >&2
    exit 1
fi

# Validate: the resolved AVD name must actually exist. This catches stray
# arguments like '#' that some shells (interactive zsh on macOS without
# `setopt interactivecomments`) leak into $1 when you paste a `# comment`
# after the command.
if ! echo "$AVD_LIST" | grep -qx "$AVD"; then
    echo "ERROR: '$AVD' is not a known AVD on this machine." >&2
    echo "       Available AVDs:" >&2
    echo "$AVD_LIST" | sed 's/^/         - /' >&2
    echo "       Pick one with: ./launch.sh <avd-name>" >&2
    exit 1
fi

# ── Step 1: launch emulator if needed ─────────────────────────────────────
# An emulator counts as "running" only if (a) there's a real qemu process
# AND (b) `adb devices` reports `emulator-XXXX device` (not `offline`).
# A phantom `offline` entry can linger after a crashed/killed emulator
# because adb keeps a stale TCP state at 127.0.0.1:5555 even when nothing
# is listening — we treat that as "not running" and reset adb to clear it.

EMU_PROC_RUNNING=0
if pgrep -f "qemu-system" >/dev/null 2>&1; then
    EMU_PROC_RUNNING=1
fi

ADB_HAS_LIVE_DEVICE=0
if "$ADB_BIN" devices 2>/dev/null \
    | awk '/^emulator-[0-9]+/ && $2 == "device" { found=1 } END { exit !found }'; then
    ADB_HAS_LIVE_DEVICE=1
fi

# If adb shows offline phantoms but no real qemu is alive, those are stale.
# Reset the adb server so the bogus entry doesn't fool wait-for-device.
if [[ "$EMU_PROC_RUNNING" == "0" && "$ADB_HAS_LIVE_DEVICE" == "0" ]]; then
    if "$ADB_BIN" devices 2>/dev/null | grep -q "^emulator-.*offline"; then
        echo "==> Clearing stale 'offline' emulator entry from adb..."
        "$ADB_BIN" disconnect emulator-5554 >/dev/null 2>&1 || true
        "$ADB_BIN" kill-server >/dev/null 2>&1 || true
        sleep 1
        "$ADB_BIN" start-server >/dev/null 2>&1 || true
        sleep 1
    fi
fi

if [[ "$EMU_PROC_RUNNING" == "1" && "$ADB_HAS_LIVE_DEVICE" == "1" ]]; then
    echo "==> An emulator is already running — skipping boot."
else
    echo "==> Booting AVD: $AVD"
    EMU_FLAGS=()
    [[ "${FRESH:-0}" == "1" ]] && EMU_FLAGS+=(-no-snapshot-load)
    [[ "${WIPE:-0}"  == "1" ]] && EMU_FLAGS+=(-wipe-data)

    # Detached so this script keeps control of the terminal. nohup keeps the
    # emulator alive after this script exits; output goes to emulator.log.
    # The "${EMU_FLAGS[@]+"${EMU_FLAGS[@]}"}" form expands safely under
    # `set -u` when the array is empty (plain "${EMU_FLAGS[@]}" trips
    # "unbound variable" on bash 4.x even though the array was declared).
    nohup "$EMULATOR_BIN" -avd "$AVD" \
        ${EMU_FLAGS[@]+"${EMU_FLAGS[@]}"} \
        > emulator.log 2>&1 &
    EMU_PID=$!
    echo "    emulator PID: $EMU_PID  (logs: ./emulator.log)"
fi

# ── Step 2: wait for a LIVE device to show up ─────────────────────────────
# We can't use `adb -e` here because a phantom `emulator-5554 offline` may
# be parked alongside our real, freshly-booted emulator (which often lands
# on emulator-5556 in that case). `adb wait-for-device` would also choke
# with "more than one device". Instead, poll until we see ANY emulator-XXXX
# whose status is `device`, and capture that serial.
echo "==> Waiting for a live emulator to appear..."
SERIAL=""
TRIES=0
while [[ -z "$SERIAL" ]]; do
    SERIAL=$("$ADB_BIN" devices 2>/dev/null \
        | awk '/^emulator-[0-9]+/ && $2 == "device" { print $1; exit }')
    [[ -n "$SERIAL" ]] && break
    TRIES=$((TRIES + 1))
    if (( TRIES > 180 )); then
        echo "ERROR: no live emulator after ~3 minutes. Check emulator.log." >&2
        exit 1
    fi
    sleep 1
done
echo "    Live device: $SERIAL"

echo "==> Waiting for sys.boot_completed (this can take a couple of minutes)..."
TRIES=0
while true; do
    BOOTED=$("$ADB_BIN" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
    if [[ "$BOOTED" == "1" ]]; then
        break
    fi
    TRIES=$((TRIES + 1))
    if (( TRIES > 180 )); then
        echo "ERROR: boot didn't complete after ~3 minutes. Check emulator.log." >&2
        exit 1
    fi
    sleep 1
done
echo "    Boot completed."

# ── Step 3: hand off to the existing setup script with the explicit serial ─
# emulator-setup.sh accepts the serial as its first positional arg. Passing
# it explicitly avoids the "more than one device" trap caused by phantom
# offline entries that adb sometimes leaves behind.
echo ""
echo "==> Running ./emulator-setup.sh $SERIAL"
echo ""
exec ./emulator-setup.sh "$SERIAL"
