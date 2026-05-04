package br.com.redesurftank.havalshisuku.managers;

import android.content.Context;

/**
 * Production no-op stub. The real implementation lives in the emulator flavor
 * source set and registers an ADB-driven broadcast receiver for steering-wheel
 * key simulation. This stub ensures the production build has zero emulator
 * footprint: no receiver is registered and no extra broadcast action is exposed.
 */
public class EmulatorInputBridge {
    public static final String ACTION_SIM_KEY =
            "br.com.redesurftank.havalshisuku.SIM_STEERING_KEY";
    public static final String EXTRA_KEYCODE = "keycode";

    public EmulatorInputBridge(Context context) {}
    public void start() {}
    public void stop() {}
}
