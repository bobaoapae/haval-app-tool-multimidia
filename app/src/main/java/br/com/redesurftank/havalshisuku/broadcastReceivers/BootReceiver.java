package br.com.redesurftank.havalshisuku.broadcastReceivers;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import br.com.redesurftank.havalshisuku.services.ForegroundService;
import br.com.redesurftank.havalshisuku.managers.ServiceManager;
import br.com.redesurftank.havalshisuku.managers.ViewerAutostartManager;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        if (Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {
            Log.w(TAG, "Locked boot completed received; waiting for full boot before starting service.");
            return;
        }

        ServiceManager.getInstance().setTimeBootReceived(SystemClock.uptimeMillis());
        Log.w(TAG, "Boot event received (" + action + "), starting service...");
        // Start the BackgroundService
        Intent serviceIntent = new Intent(context, ForegroundService.class);
        context.startForegroundService(serviceIntent);

        // Fired here rather than from the service so the first startActivity lands as early as
        // possible - the OEM launcher is still settling at this point. The manager schedules its
        // own retries and is guarded by a per-boot token, so calling it twice is harmless.
        try {
            ViewerAutostartManager.INSTANCE.onBootCompleted("boot_receiver");
        } catch (Exception e) {
            Log.e(TAG, "Viewer autostart failed: " + e.getMessage(), e);
        }
    }
}
