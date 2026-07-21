// Grants NotificationListenerService access via NotificationManagerService internal API.
// Runs inside system_server via fridainject. Uses setTimeout to wait for Frida Java bridge.
setTimeout(function () {
    Java.perform(function () {
        try {
            var ComponentName = Java.use('android.content.ComponentName');
            var comp = ComponentName.$new(
                'br.com.redesurftank.havalshisuku',
                'br.com.redesurftank.havalshisuku.services.MediaNotificationListenerService'
            );
            android.util.Log.w('NLS_GRANT', 'Searching NMS...');
            Java.choose('com.android.server.notification.NotificationManagerService', {
                onMatch: function (nms) {
                    android.util.Log.w('NLS_GRANT', 'Found NMS');
                    var ok = false;
                    try { nms.setNotificationListenerAccessGranted(comp, true, 0); ok = true; android.util.Log.w('NLS_GRANT', 'Grant OK v1'); } catch (e1) {}
                    if (!ok) {
                        try { nms.setNotificationListenerAccessGrantedForUser(comp, 0, true); ok = true; android.util.Log.w('NLS_GRANT', 'Grant OK v2'); } catch (e2) {}
                    }
                    if (!ok) android.util.Log.w('NLS_GRANT', 'Grant failed');
                },
                onComplete: function () { android.util.Log.w('NLS_GRANT', 'Done'); }
            });
        } catch (e) {
            android.util.Log.w('NLS_GRANT', 'Error: ' + e);
        }
    });
}, 2000);
