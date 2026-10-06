"""
Deploy Patched AndroidAutoApp to Car
=====================================
Pushes a signed APK to the car via ADB and configures bind mounts
via Telnet so the patched APK replaces the stock vendor APK.

Usage:
    # Deploy the default build (build_v19/AndroidAutoApp_v19_signed.apk)
    python scripts/milestones/v19_focus_bypass/deploy_to_car.py [--ip 192.168.33.139]

    # Deploy a specific APK (e.g. a v2.x build with its own filename)
    python scripts/milestones/v19_focus_bypass/deploy_to_car.py \\
        --ip <CAR_IP> --apk build_v19/AndroidAutoApp_v21_signed.apk

Reusable across milestones:
    Despite living in the v19 milestone folder, this script is generic —
    it just pushes whatever APK you point it at and bind-mounts it over
    /vendor/app/AndroidAutoApp/AndroidAutoApp.apk. Use --apk to target a
    different build (v2.1, v2.2, ...).

Prerequisites:
    - A signed APK (default: build_v19/AndroidAutoApp_v19_signed.apk).
    - Car connected via Wi-Fi (ADB port 5555, Telnet port 23).
    - ADB in PATH or at C:\\Users\\vanes\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe.

Safety note:
    This script only touches the App APK mount. If the Haval companion
    app is installed, its AndroidAutoPatchManager.ensureMounted() will
    re-mount the v1.19 APK from assets on the next reboot, automatically
    reverting any v2.x test build. Useful safety net during iteration.
"""

import os
import sys
import socket
import time
import subprocess
import argparse

# --- Configuration ---
DEFAULT_IP = "192.168.33.139"
ADB_PORT = 5555
TELNET_PORT = 23
DEFAULT_APK_PATH = "build_v19/AndroidAutoApp_v19_signed.apk"
REMOTE_PATH = "/data/local/tmp/v19_app.apk"
VENDOR_APK = "/vendor/app/AndroidAutoApp/AndroidAutoApp.apk"
VENDOR_OAT = "/vendor/app/AndroidAutoApp/oat"

def run_adb(ip, cmd):
    """Run an ADB command against the car."""
    adb_exe = r"C:\Users\vanes\AppData\Local\Android\Sdk\platform-tools\adb.exe"
    if not os.path.exists(adb_exe):
        adb_exe = "adb"  # Fall back to PATH

    full_cmd = f'"{adb_exe}" -s {ip}:{ADB_PORT} {cmd}'
    print(f"  ADB> {cmd}")
    try:
        result = subprocess.run(full_cmd, shell=True, capture_output=True, text=True, timeout=30)
        if result.stdout.strip():
            print(f"       {result.stdout.strip()}")
        if result.returncode != 0 and result.stderr.strip():
            print(f"  ERR> {result.stderr.strip()}")
        return result.returncode == 0
    except subprocess.TimeoutExpired:
        print("  ERR> Command timed out")
        return False
    except Exception as e:
        print(f"  ERR> {e}")
        return False

def run_telnet(ip, commands):
    """Execute a sequence of commands via Telnet (root shell on car)."""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(10)
        s.connect((ip, TELNET_PORT))
        print(f"  Connected to Telnet at {ip}:{TELNET_PORT}")

        # Consume banner
        time.sleep(1)
        try:
            s.recv(4096)
        except socket.timeout:
            pass

        for cmd in commands:
            print(f"  TEL> {cmd}")
            s.sendall(cmd.encode("ascii") + b"\n")
            time.sleep(1.5)
            try:
                data = s.recv(16384)
                output = data.decode("ascii", errors="ignore").strip()
                if output:
                    for line in output.split("\n"):
                        print(f"       {line}")
            except socket.timeout:
                pass

        s.close()
        print("  Telnet connection closed.")
        return True
    except Exception as e:
        print(f"  Telnet Error: {e}")
        return False

def main():
    parser = argparse.ArgumentParser(description="Deploy patched AA APK to car")
    parser.add_argument("--ip", default=DEFAULT_IP, help=f"Car MMI IP (default: {DEFAULT_IP})")
    parser.add_argument(
        "--apk",
        default=DEFAULT_APK_PATH,
        help=(
            f"Signed APK to deploy (default: {DEFAULT_APK_PATH}). "
            "Use this to push v2.x builds without renaming the output file."
        ),
    )
    args = parser.parse_args()

    apk_path = args.apk

    print("=" * 60)
    print("  Deploy Patched AndroidAutoApp to Car")
    print("=" * 60)
    print()

    # Check APK exists
    if not os.path.exists(apk_path):
        print(f"ERROR: Signed APK not found at {apk_path}")
        print("Build it first — see scripts/aa-patches/README.md")
        sys.exit(1)

    apk_size = os.path.getsize(apk_path)
    print(f"APK: {apk_path} ({apk_size:,} bytes)")
    print(f"Car: {args.ip}")
    print()

    # Step 1: Connect ADB
    print("[1/3] Connecting ADB...")
    run_adb(args.ip, "connect " + args.ip + ":" + str(ADB_PORT))
    print()

    # Step 2: Push APK
    print("[2/3] Pushing APK to car...")
    if not run_adb(args.ip, f"push {apk_path} {REMOTE_PATH}"):
        print("ERROR: Failed to push APK. Is ADB connected?")
        sys.exit(1)
    print()

    # Step 3: Configure mounts via Telnet
    print("[3/3] Configuring bind mounts via Telnet...")
    telnet_commands = [
        # Cleanup existing mounts
        f"umount {VENDOR_APK}",
        f"umount {VENDOR_OAT}",

        # Clear Dalvik cache
        "rm /data/dalvik-cache/arm64/vendor@app@AndroidAutoApp@AndroidAutoApp.apk@classes.dex",
        "rm /data/dalvik-cache/arm64/vendor@app@AndroidAutoApp@AndroidAutoApp.apk@classes.vdex",

        # SELinux and permissions
        f"chcon u:object_r:vendor_app_file:s0 {REMOTE_PATH}",
        f"chmod 644 {REMOTE_PATH}",

        # Bind mount patched APK over vendor APK
        f"mount --bind {REMOTE_PATH} {VENDOR_APK}",

        # Hide OAT (forces system to use our classes.dex instead of pre-optimized)
        "mkdir -p /data/local/tmp/empty_oat",
        f"mount --bind /data/local/tmp/empty_oat {VENDOR_OAT}",

        # Restart Android Auto
        "am force-stop com.ts.androidauto.app",
        "am start -n com.ts.androidauto.app/com.ts.androidauto.app.display.AapActivity",

        # Verify
        f"ls -lZ {VENDOR_APK}",
        f"mount | grep AndroidAutoApp",
    ]

    if not run_telnet(args.ip, telnet_commands):
        print("ERROR: Telnet configuration failed.")
        sys.exit(1)

    print()
    print("=" * 60)
    print("  DEPLOYMENT COMPLETE")
    print("=" * 60)
    print()
    print("Test on the car:")
    print("  1. Android Auto should launch and display normally")
    print("  2. Switch to another app and back — AA should keep focus")
    print("  3. Wait 5+ minutes — no flashing or crashes")
    print()
    print("If validated, promote to milestone:")
    print("  python -c \"import shutil; shutil.copytree('build_v19', 'scripts/milestones/v19_focus_bypass')\"")

if __name__ == "__main__":
    main()
