#!/system/bin/sh
# Bind both Android Auto APKs before the process opens them.
# No md5: hashing the Service made the last boot finish after the process
# had already started, and the later swap broke the phone certificate.
STAGE=/data/local/tmp/aa_patches
SVC=$STAGE/AndroidAutoService.apk
APP=$STAGE/AndroidAutoApp.apk
VSVC=/vendor/app/AndroidAutoService/AndroidAutoService.apk
VAPP=/vendor/app/AndroidAutoApp/AndroidAutoApp.apk
note() {
    echo "$1 $(cut -d. -f1 /proc/uptime)" >> /data/local/tmp/aa-cluster-early.log
    echo "$1"
}
if ! [ -f $SVC ]; then note MOUNT_NO_SOURCE; exit 0; fi
if ! [ -f $APP ]; then note MOUNT_NO_SOURCE; exit 0; fi
if pidof com.ts.androidauto >/dev/null 2>&1; then note MOUNT_LATE; exit 0; fi
mkdir -p $STAGE/empty_oat
chmod 755 $STAGE $STAGE/empty_oat
chmod 644 $SVC $APP
if ! grep -F $VSVC /proc/mounts >/dev/null 2>&1; then
    touch -r $VSVC $STAGE/stock-mtime-AndroidAutoService.apk.ref
fi
if ! grep -F $VAPP /proc/mounts >/dev/null 2>&1; then
    touch -r $VAPP $STAGE/stock-mtime-AndroidAutoApp.apk.ref
fi
touch -r $STAGE/stock-mtime-AndroidAutoService.apk.ref $SVC || { note MOUNT_NO_SOURCE; exit 0; }
touch -r $STAGE/stock-mtime-AndroidAutoApp.apk.ref $APP || { note MOUNT_NO_SOURCE; exit 0; }
chcon u:object_r:vendor_app_file:s0 $SVC $APP
if ! grep -F $VSVC /proc/mounts >/dev/null 2>&1; then
    mount --bind $SVC $VSVC || { note MOUNT_NO_SOURCE; exit 0; }
fi
if ! grep -F $VAPP /proc/mounts >/dev/null 2>&1; then
    mount --bind $APP $VAPP || { note MOUNT_NO_SOURCE; exit 0; }
fi
if [ -d /vendor/app/AndroidAutoService/oat ]; then
    mount --bind $STAGE/empty_oat /vendor/app/AndroidAutoService/oat || true
fi
if [ -d /vendor/app/AndroidAutoApp/oat ]; then
    mount --bind $STAGE/empty_oat /vendor/app/AndroidAutoApp/oat || true
fi
if pidof com.ts.androidauto >/dev/null 2>&1; then note MOUNT_LATE; exit 0; fi
rm -f /data/dalvik-cache/arm64/*AndroidAutoService* /data/dalvik-cache/arm64/*AndroidAutoApp*
note MOUNT_EARLY
