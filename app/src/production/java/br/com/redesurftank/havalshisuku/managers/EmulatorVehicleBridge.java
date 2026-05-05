package br.com.redesurftank.havalshisuku.managers;

import android.content.Context;

/**
 * Production no-op stub. The real implementation lives in the emulator flavor
 * source set and depends on the AAOS Car API (android.car). This stub ensures
 * the production build compiles and links without that dependency, and that no
 * emulator-specific code ever runs on real hardware.
 */
public class EmulatorVehicleBridge {
    public EmulatorVehicleBridge(Context context) {}
    public void start() {}
    public void stop() {}
}
