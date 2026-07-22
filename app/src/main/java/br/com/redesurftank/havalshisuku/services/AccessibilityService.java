package br.com.redesurftank.havalshisuku.services;

import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher;

public class AccessibilityService extends android.accessibilityservice.AccessibilityService {

    private static final String TAG = "AccessibilityService";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence pkg = event.getPackageName();
            if (pkg != null) {
                DisplayAppLauncher.INSTANCE.onAppWindowChanged(pkg.toString());
            }
        }
    }

    @Override
    public void onInterrupt() {

    }

    // Key codes that may be consumed for Android Auto media control.
    // All other keys pass through immediately without any processing.
    private static final int[] AA_MEDIA_KEY_CODES = {
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,  // 85
        KeyEvent.KEYCODE_MEDIA_PLAY,        // 126
        KeyEvent.KEYCODE_MEDIA_PAUSE,       // 127
        KeyEvent.KEYCODE_MUTE,              // 91
        KeyEvent.KEYCODE_VOLUME_MUTE,       // 164
        0x3ec                               // OEM play/pause
    };

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();

        // Fast-path: return immediately for keys that can never be AA media keys.
        // This avoids blocking the main thread on carousel/steering navigation keys.
        boolean isCandidateKey = false;
        for (int code : AA_MEDIA_KEY_CODES) {
            if (keyCode == code) { isCandidateKey = true; break; }
        }
        if (!isCandidateKey) return false;

        if (DisplayAppLauncher.INSTANCE.shouldConsumeAndroidAutoAccessibilityMediaKey(
                keyCode,
                event.getAction()
        )) {
            Log.w(TAG, "Consumed AA media key: keyCode=" + keyCode + " action=" + event.getAction());
            return true;
        }
        return false;
    }
}
