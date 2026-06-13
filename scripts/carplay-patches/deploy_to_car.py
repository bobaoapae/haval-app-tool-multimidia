"""
Deploy Patched TsCarPlayApp to the head unit.

Uses the local headunit toolkit for transport and telnet execution.
Run from repository root after building/signing the patched APK:

    python3 scripts/carplay-patches/deploy_to_car.py \
      --apk build_carplay/TsCarPlayApp_signed.apk
"""

import argparse
import contextlib
import functools
import http.server
import os
import re
import shlex
import socket
import socketserver
import subprocess
import sys
import threading
import time
import urllib.parse

DEFAULT_APK = "build_carplay/TsCarPlayApp_signed.apk"
REMOTE_APK = "/data/local/tmp/carplay_patches/TsCarPlayApp.apk"
VENDOR_APK = "/system/app/TsCarPlayApp/TsCarPlayApp.apk"
VENDOR_OAT = "/system/app/TsCarPlayApp/oat"
HEADUNIT_HOST = os.environ.get("HEADUNIT_HOST", "172.20.10.2")
HTTP_PORT = int(os.environ.get("HTTP_PORT", "8767"))
HTTP_PORT_SEARCH_LIMIT = int(os.environ.get("HTTP_PORT_SEARCH_LIMIT", "20"))


def run(cmd):
    print("+", " ".join(cmd))
    subprocess.run(cmd, check=True)


def telnet_exec(command):
    result = subprocess.run(
        ["./tools/headunit-dev/headunit.sh", "exec", command],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )
    return result.stdout


def shell_quote(value):
    return shlex.quote(str(value))


def print_progress_bar(label, current, total):
    width = 32
    if total <= 0:
        percent = 0
    else:
        percent = min(100, int(current * 100 / total))
    filled = int(width * percent / 100)
    bar = "#" * filled + "." * (width - filled)
    sys.stdout.write(
        f"\r[CarPlayPatch] {label} [{bar}] {percent:3d}% {current}/{total} bytes"
    )
    sys.stdout.flush()


def local_http_host():
    explicit = os.environ.get("HEADUNIT_LOCAL_HOST")
    if explicit:
        return explicit

    with contextlib.closing(socket.socket(socket.AF_INET, socket.SOCK_DGRAM)) as sock:
        try:
            sock.connect((HEADUNIT_HOST, 23))
            return sock.getsockname()[0]
        except OSError:
            return "172.20.10.5"


def find_available_port(start_port, limit):
    for port in range(start_port, start_port + limit):
        with contextlib.closing(socket.socket(socket.AF_INET, socket.SOCK_STREAM)) as sock:
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            try:
                sock.bind(("0.0.0.0", port))
            except OSError:
                continue
            return port
    raise RuntimeError(f"No free HTTP port found from {start_port}")


class QuietThreadingTCPServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True


class QuietHandler(http.server.SimpleHTTPRequestHandler):
    def log_message(self, fmt, *args):
        print("[http] " + (fmt % args))


@contextlib.contextmanager
def serve_directory(directory):
    port = find_available_port(HTTP_PORT, HTTP_PORT_SEARCH_LIMIT)
    handler = functools.partial(QuietHandler, directory=directory)
    server = QuietThreadingTCPServer(("0.0.0.0", port), handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield port
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)


def remote_size(remote_path):
    output = telnet_exec(
        f"if [ -f {shell_quote(remote_path)} ]; then "
        f"wc -c < {shell_quote(remote_path)}; "
        f"elif [ -f {shell_quote(remote_path + '.tmp')} ]; then "
        f"printf 'tmp:'; wc -c < {shell_quote(remote_path + '.tmp')}; "
        "else echo 0; fi"
    )
    tmp_match = re.search(r"tmp:\s*(\d+)", output)
    if tmp_match:
        return int(tmp_match.group(1)), True

    numbers = [int(item) for item in re.findall(r"(?m)^\s*(\d+)\s*$", output)]
    if numbers:
        return numbers[-1], False

    return 0, False


def upload_via_remote_curl(apk_path, remote_path):
    apk_path = os.path.abspath(apk_path)
    expected_size = os.path.getsize(apk_path)
    host_ip = local_http_host()
    remote_tmp = remote_path + ".tmp"
    remote_log = remote_path + ".download.log"
    filename = os.path.basename(apk_path)

    telnet_exec(
        "mkdir -p /data/local/tmp/carplay_patches; "
        f"rm -f {shell_quote(remote_path)} {shell_quote(remote_tmp)} "
        f"{shell_quote(remote_log)} {shell_quote(remote_path + '.b64')}"
    )

    with serve_directory(os.path.dirname(apk_path)) as port:
        url = f"http://{host_ip}:{port}/{urllib.parse.quote(filename)}"
        print(f"[CarPlayPatch] Headunit downloading {url}")

        remote_cmd = (
            f"rm -f {shell_quote(remote_tmp)} {shell_quote(remote_log)}; "
            "nohup sh -c "
            + shell_quote(
                "("
                f"curl -fsSL {shell_quote(url)} -o {shell_quote(remote_tmp)} "
                f"|| wget -O {shell_quote(remote_tmp)} {shell_quote(url)} "
                f"|| toybox wget -O {shell_quote(remote_tmp)} {shell_quote(url)} "
                f"|| busybox wget -O {shell_quote(remote_tmp)} {shell_quote(url)}"
                ") && "
                f"size=$(wc -c < {shell_quote(remote_tmp)}); "
                f"if [ \"$size\" = {shell_quote(expected_size)} ]; then "
                f"mv {shell_quote(remote_tmp)} {shell_quote(remote_path)} && chmod 644 {shell_quote(remote_path)}; "
                "else "
                f"echo size-mismatch expected={expected_size} actual=$size; "
                "exit 2; "
                "fi"
            )
            + f" > {shell_quote(remote_log)} 2>&1 &"
        )
        telnet_exec(remote_cmd)

        deadline = time.monotonic() + 120
        last_size = -1
        printed_progress = False
        while time.monotonic() < deadline:
            size, is_tmp = remote_size(remote_path)
            if size != last_size:
                print_progress_bar("Downloading APK", size, expected_size)
                printed_progress = True
                last_size = size
            if size == expected_size and not is_tmp:
                if printed_progress:
                    print()
                print(f"[CarPlayPatch] Download complete via curl ({size} bytes)")
                return
            time.sleep(1)

    if printed_progress:
        print()
    log = telnet_exec(f"cat {shell_quote(remote_log)} 2>/dev/null | tail -n 80")
    raise RuntimeError(
        f"Headunit did not finish APK download. Expected {expected_size} bytes.\n{log}"
    )


def main():
    parser = argparse.ArgumentParser(description="Deploy patched TS CarPlay APK")
    parser.add_argument("--apk", default=DEFAULT_APK)
    args = parser.parse_args()

    if not os.path.exists(args.apk):
        print(f"APK not found: {args.apk}", file=sys.stderr)
        sys.exit(1)

    upload_via_remote_curl(args.apk, REMOTE_APK)
    run(
        [
            "./tools/headunit-dev/headunit.sh",
            "exec",
            (
                f"umount -l {VENDOR_APK}; "
                f"[ -d {VENDOR_OAT} ] && umount -l {VENDOR_OAT}; "
                f"chmod 644 {REMOTE_APK}; "
                f"chcon u:object_r:system_file:s0 {REMOTE_APK}; "
                f"mount --bind {REMOTE_APK} {VENDOR_APK}; "
                "mkdir -p /data/local/tmp/carplay_patches/empty_oat; "
                f"[ -d {VENDOR_OAT} ] && mount --bind /data/local/tmp/carplay_patches/empty_oat {VENDOR_OAT}; "
                "rm -f /data/dalvik-cache/arm64/*TsCarPlayApp* "
                "/data/dalvik-cache/arm64/*CarPlayApp*; "
                "setprop persist.haval.carplay.video.height 720; "
                "am force-stop com.ts.carplay.app; "
                "am startservice -n com.ts.carplay/.CarPlayService; "
                "am startservice -n com.ts.carplay.app/.service.CarPlayRemoteService; "
                f"ls -lZ {VENDOR_APK}; "
                "mount | grep TsCarPlayApp"
            ),
        ]
    )


if __name__ == "__main__":
    main()
