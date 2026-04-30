#!/usr/bin/env bash
# cluster-keys.sh
#
# Live keyboard driver for the Haval cluster's steering-wheel buttons on the
# AAOS emulator. Reads single-character keypresses from this terminal and
# broadcasts the corresponding vendor keycode to the running Haval app via:
#
#   adb shell am broadcast --user 10 -p br.com.redesurftank.havalshisuku \
#       -a br.com.redesurftank.havalshisuku.SIM_STEERING_KEY --ei keycode <N>
#
# The Haval app's EmulatorInputBridge receives that broadcast and routes the
# keycode through the same handler chain as the real Beantechs IInputService
# would on hardware. The cluster reacts (UPDATE_SCREEN, focus highlights,
# menu navigation, etc.) exactly as if the steering-wheel button were
# pressed.
#
# Run this in a terminal window FOCUSED on this terminal (not the emulator
# window) so keystrokes go to bash, not Android.
#
# Key map:
#   w / k / Up arrow      -> UP            (1024)
#   s / j / Down arrow    -> DOWN          (1025)
#   <space> / <enter>     -> ENTER         (1028)
#   h                     -> HOME          (1029)
#   b / <backspace>       -> BACK          (1030)
#   W                     -> UP_LONG       (1033)
#   S                     -> DOWN_LONG     (1034)
#   E                     -> ENTER_LONG    (1037)
#   B                     -> BACK_LONG     (1039)
#   1                     -> CUSTOM #1     (517)
#   2                     -> CUSTOM #2     (1031)
#   ?                     -> print this map
#   q                     -> quit
#
# Usage:
#   ./cluster-keys.sh                  # connect to the only running emulator
#   ./cluster-keys.sh emulator-5554    # specific serial

set -euo pipefail

ADB="adb"
SERIAL="${1:-}"
if [[ -n "$SERIAL" ]]; then
    ADB="adb -s $SERIAL"
else
    ADB="adb -e"
fi

PKG="br.com.redesurftank.havalshisuku"
ACTION="$PKG.SIM_STEERING_KEY"

send_key() {
    local code="$1"
    local label="$2"
    # Suppress the verbose "Broadcasting:" stdout; we print our own line.
    $ADB shell "am broadcast --user 10 -p $PKG -a $ACTION --ei keycode $code" \
        > /dev/null 2>&1 &
    printf "  -> %s (keycode %d)\n" "$label" "$code"
}

print_map() {
    cat <<'EOF'

  CLUSTER STEERING-WHEEL KEY MAP
  ================================
   w / k / Up        UP            (1024)
   s / j / Down      DOWN          (1025)
   space / enter     ENTER         (1028)
   h                 HOME          (1029)
   b / backspace     BACK          (1030)
   W                 UP_LONG       (1033)
   S                 DOWN_LONG     (1034)
   E                 ENTER_LONG    (1037)
   B                 BACK_LONG     (1039)
   1                 CUSTOM #1     (517)
   2                 CUSTOM #2     (1031)
   ?                 show this map
   q                 quit
  ================================
EOF
}

print_map
echo ""
echo "Press any mapped key to drive the cluster (q to quit)."
echo ""

# Single-character read loop. -n1 reads one byte; -s suppresses echo. We
# manually decode ANSI arrow-key sequences (ESC + [ + A/B/C/D).
while true; do
    IFS= read -rsn1 ch || break

    # ANSI arrow sequence: ESC followed by [ followed by A/B/C/D
    if [[ "$ch" == $'\x1b' ]]; then
        IFS= read -rsn1 -t 0.05 ch2 || true
        if [[ "${ch2:-}" == "[" ]]; then
            IFS= read -rsn1 -t 0.05 ch3 || true
            case "${ch3:-}" in
                A) send_key 1024 "UP (Up arrow)";    continue ;;
                B) send_key 1025 "DOWN (Down arrow)"; continue ;;
            esac
        fi
        # Bare ESC -> BACK (matches editor convention)
        send_key 1030 "BACK (Esc)"
        continue
    fi

    case "$ch" in
        w|k)        send_key 1024 "UP" ;;
        s|j)        send_key 1025 "DOWN" ;;
        ' '|'')     send_key 1028 "ENTER" ;;       # space, enter (\n empty after read)
        h)          send_key 1029 "HOME" ;;
        b)          send_key 1030 "BACK" ;;
        $'\x7f')    send_key 1030 "BACK (Backspace)" ;;
        W)          send_key 1033 "UP_LONG" ;;
        S)          send_key 1034 "DOWN_LONG" ;;
        E)          send_key 1037 "ENTER_LONG" ;;
        B)          send_key 1039 "BACK_LONG" ;;
        1)          send_key 517  "CUSTOM #1" ;;
        2)          send_key 1031 "CUSTOM #2" ;;
        '?')        print_map ;;
        q|Q)        echo "  bye."; exit 0 ;;
        *)          : ;;  # ignore unmapped keys silently
    esac
done
