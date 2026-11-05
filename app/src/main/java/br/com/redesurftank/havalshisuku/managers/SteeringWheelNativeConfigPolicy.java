package br.com.redesurftank.havalshisuku.managers;

/** Native-choice preservation only. No key replay or vehicle-function execution. */
public final class SteeringWheelNativeConfigPolicy {
    private SteeringWheelNativeConfigPolicy() {}

    public interface Store {
        String readCurrent() throws Exception;
        String readBackup();
        /** Must persist successfully before capture can replace the current native choice. */
        void saveBackup(String value) throws Exception;
        void writeCurrent(String value) throws Exception;
    }

    public enum Result { PRESERVED, CAPTURED, RESTORED, ALREADY_CAPTURED, UNKNOWN, CHANGED }

    /** Opaque native selection, not a list of vehicle actions we implement or execute. */
    public static String nativeValue(String value) {
        if (value == null) return null;
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed >= -1 && parsed != 99 ? Integer.toString(parsed) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static boolean shouldCapture(boolean enabled, boolean customShort) {
        // Custom double/long actions do not make native DEFAULT short exclusive.
        return enabled && customShort;
    }

    public static Result reconcile(Store store, boolean capture) throws Exception {
        String current = store.readCurrent();
        String nativeChoice = nativeValue(current);
        if (nativeChoice != null) {
            // The live OEM setting wins over every historical backup, including after reboot.
            store.saveBackup(nativeChoice);
            if (!capture) return Result.PRESERVED;
            if (!nativeChoice.equals(nativeValue(store.readCurrent()))) return Result.CHANGED;
            store.writeCurrent("99");
            return Result.CAPTURED;
        }
        if (current == null || !"99".equals(current.trim())) return Result.UNKNOWN;
        if (capture) return Result.ALREADY_CAPTURED;
        String backup = nativeValue(store.readBackup());
        if (backup == null) return Result.UNKNOWN;
        // Re-read just before restoring; never overwrite an observed newer OEM choice.
        // Settings has no compare-and-set API: an external write can still race this write.
        String latest = store.readCurrent();
        if (latest == null || !"99".equals(latest.trim())) return Result.CHANGED;
        store.writeCurrent(backup);
        return Result.RESTORED;
    }
}
