package br.com.redesurftank.havalshisuku.managers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

/**
 * Emulator-only host-driven input bridge for the steering-wheel buttons.
 *
 * On real hardware the Beantechs IInputService delivers vendor key codes
 * (517, 1024..1039) to ServiceManager's inputListener. The emulator does not
 * have that proprietary service. This bridge lets a host-side script
 * substitute for it via a broadcast intent:
 *
 *   adb shell am broadcast -a br.com.redesurftank.havalshisuku.SIM_STEERING_KEY \
 *       --ei keycode 1024
 *
 * Any process can broadcast (the action is registered without an export
 * permission since this only ships to emulator builds anyway). The receiver
 * routes the keycode through ServiceManager.dispatchSimulatedInputKey, which
 * runs the same handler chain as the real-hardware path — driving the
 * cluster's UI navigation and any custom-button-bound actions.
 */
public class EmulatorInputBridge {

    private static final String TAG = "EmulatorInputBridge";
    public static final String ACTION_SIM_KEY =
            "br.com.redesurftank.havalshisuku.SIM_STEERING_KEY";
    public static final String EXTRA_KEYCODE = "keycode";

    private final Context context;
    private BroadcastReceiver receiver;

    public EmulatorInputBridge(Context context) {
        this.context = context.getApplicationContext();
    }

    public void start() {
        if (receiver != null) {
            Log.w(TAG, "EmulatorInputBridge already started, skipping.");
            return;
        }
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (!ACTION_SIM_KEY.equals(intent.getAction())) return;
                int keycode = intent.getIntExtra(EXTRA_KEYCODE, -1);
                if (keycode < 0) {
                    Log.w(TAG, "Broadcast missing 'keycode' int extra");
                    return;
                }
                Log.w(TAG, "Received simulated steering-wheel keycode: " + keycode);
                try {
                    ServiceManager.getInstance().dispatchSimulatedInputKey(keycode);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to dispatch keycode " + keycode + ": " + e.getMessage(), e);
                }
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_SIM_KEY);
        // RECEIVER_EXPORTED so `adb shell am broadcast` can reach us.
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        Log.w(TAG, "EmulatorInputBridge listening on action " + ACTION_SIM_KEY);
    }

    public void stop() {
        if (receiver != null) {
            try { context.unregisterReceiver(receiver); } catch (Exception ignored) {}
            receiver = null;
            Log.w(TAG, "EmulatorInputBridge stopped");
        }
    }
}
