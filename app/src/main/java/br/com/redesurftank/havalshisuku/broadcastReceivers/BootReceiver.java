package br.com.redesurftank.havalshisuku.broadcastReceivers;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import br.com.redesurftank.havalshisuku.services.ForegroundService;
import br.com.redesurftank.havalshisuku.managers.ServiceManager;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    @Override
    public void onReceive(Context context, Intent intent) {
        // On AAOS the boot broadcast fires once per user. The app is instantiated for
        // both the headless system user (user 0) and the car profile user (user 10+).
        // Running ForegroundService in the system user's process is wasteful and causes
        // a duplicate ProjectorManager and WebView. Skip for the headless system user.
        // On AAOS, userId = myUid() / 100000. User 0 is the headless system user.
        int currentUserId = android.os.Process.myUid() / 100000;
        if (currentUserId == 0) {
            Log.w(TAG, "Boot received for headless system user (user 0) — skipping service start.");
            return;
        }

        ServiceManager.getInstance().setTimeBootReceived(SystemClock.uptimeMillis());
        Log.w(TAG, "Boot completed received (user " + currentUserId + "), starting service...");
        Intent serviceIntent = new Intent(context, ForegroundService.class);
        context.startForegroundService(serviceIntent);
    }
}