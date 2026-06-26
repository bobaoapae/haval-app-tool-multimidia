package br.com.redesurftank.havalshisuku.services;

import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher;

public class AccessibilityService extends android.accessibilityservice.AccessibilityService {

    private static final String TAG = "AccessibilityService";

    private static volatile AccessibilityService instance;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    // BACK global IN-PROCESS (instantaneo) — bem mais rapido que spawnar "input keyevent 4"
    // via Shizuku (~300ms). Usado para fechar o menu de config do volante da OEM sem flash.
    public static boolean globalBack() {
        AccessibilityService s = instance;
        if (s == null) return false;
        try {
            return s.performGlobalAction(GLOBAL_ACTION_BACK);
        } catch (Exception e) {
            return false;
        }
    }

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

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        Log.w(TAG, "onKeyEvent: " + event.getKeyCode());
        if (DisplayAppLauncher.INSTANCE.shouldConsumeAndroidAutoAccessibilityMediaKey(
                event.getKeyCode(),
                event.getAction()
        )) {
            Log.w(
                    TAG,
                    "Consumed Android Auto toggle media key: "
                            + event.getKeyCode()
                            + " action="
                            + event.getAction()
            );
            return true;
        }
        return false;
    }
}
