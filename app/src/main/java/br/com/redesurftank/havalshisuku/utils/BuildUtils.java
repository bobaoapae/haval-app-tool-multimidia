package br.com.redesurftank.havalshisuku.utils;

import android.os.Build;

public final class BuildUtils {

    private BuildUtils() {}

    /**
     * Returns true when the app is running inside an Android emulator.
     * Checked against well-known fingerprint/hardware values used by the
     * AOSP/AAOS emulator images (goldfish, ranchu) and generic SDK images.
     * Cuttlefish (aosp_cf_*) uses "unknown" FINGERPRINT so it is also caught.
     */
    public static boolean isEmulator() {
        return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.PRODUCT.startsWith("sdk")
                || Build.HARDWARE.equals("goldfish")
                || Build.HARDWARE.equals("ranchu");
    }
}
