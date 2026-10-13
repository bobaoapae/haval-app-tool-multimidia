#!/system/bin/sh
#
# Bind-mount a patched Android Auto APK over the OEM file, as uid 0.
# Same rules as AutoPanel assets/aa_cluster/aa-cluster-mount.sh (v1.18.9):
# capture the stock mtime before the bind, skip the copy when the stage is
# already current, and stop when the vendor MD5 already matches.
#
# Does not restart Android Auto. am force-stop on the projection service drops
# USB accessory mode, and nothing on this head unit redoes that handshake.
#
#   sh <script> <src-apk> <token> <src-md5|none> <vendor-apk> <stage-filename>
#
# Hash with md5sum on stdin and cut. toybox awk on this unit reports
# "illegal jump type" instead of failing usefully.

SRC="$1"
TOKEN="${2:-none}"
SRC_HASH="${3:-}"
VENDOR="${4:-/vendor/app/AndroidAutoService/AndroidAutoService.apk}"
STAGE_NAME="${5:-AndroidAutoService.apk}"
STAGE=/data/local/tmp/aa_patches
DEST="$STAGE/$STAGE_NAME"
VENDOR_OAT="$(dirname "$VENDOR")/oat"
RESULT=/data/local/tmp/aa-cluster-mount.result
MTIME_REF="$STAGE/stock-mtime-$STAGE_NAME.ref"
DALVIK_NAME="${STAGE_NAME%.apk}"

report() {
    echo "$1"
    echo "$1 $TOKEN" > "$RESULT"
    chmod 644 "$RESULT" 2>/dev/null
    exit "$2"
}

hash_of() {
    md5sum < "$1" 2>/dev/null | cut -d' ' -f1
}

[ -f "$SRC" ] || report "no-source" 1
[ -f "$VENDOR" ] || report "no-vendor-apk" 1

# Swapping the Service APK under a process that already opened it makes
# startGal read the new file with the old handshake and fail the client
# certificate. The visual App holds no GAL state: it is remounted and
# restarted while the projection service keeps running.
case "$VENDOR" in
    */AndroidAutoService/*)
        if pidof com.ts.androidauto >/dev/null 2>&1; then
            report "process-running" 0
        fi
        ;;
esac

mkdir -p "$STAGE/empty_oat" || report "stage-mkdir-failed" 1
chmod 755 "$STAGE" "$STAGE/empty_oat"

if [ -z "$SRC_HASH" ] || [ "$SRC_HASH" = "none" ]; then
    SRC_HASH=$(hash_of "$SRC")
fi

# Read the stock mtime only while this path is not our bind. Once it is, the
# vendor path shows the staged file.
if ! grep -q " $VENDOR " /proc/mounts 2>/dev/null; then
    touch -r "$VENDOR" "$MTIME_REF" || report "stage-mtime-failed" 1
fi
[ -f "$MTIME_REF" ] || report "stage-mtime-failed" 1

# Never cat a file onto itself: that truncates the staged APK.
if [ "$SRC" != "$DEST" ]; then
    if [ "$(hash_of "$DEST")" != "$SRC_HASH" ]; then
        cat "$SRC" > "$DEST" || report "stage-copy-failed" 1
    fi
fi
chmod 644 "$DEST"
# Stock mtime, or PackageManager re-verifies android.uid.system and deletes
# the package. Same failure recorded on the CarPlay patch, 2026-10-04.
touch -r "$MTIME_REF" "$DEST" || report "stage-mtime-failed" 1
chcon u:object_r:vendor_app_file:s0 "$DEST" 2>/dev/null

if [ "$(hash_of "$VENDOR")" = "$SRC_HASH" ]; then
    report "already-mounted" 0
fi

mount --bind "$DEST" "$VENDOR" 2>/dev/null || report "mount-failed" 1
[ -d "$VENDOR_OAT" ] && mount --bind "$STAGE/empty_oat" "$VENDOR_OAT" 2>/dev/null
rm -f /data/dalvik-cache/*/*"$DALVIK_NAME"* 2>/dev/null

if [ "$(hash_of "$VENDOR")" != "$SRC_HASH" ]; then
    report "verify-failed" 1
fi

report "mounted" 0
