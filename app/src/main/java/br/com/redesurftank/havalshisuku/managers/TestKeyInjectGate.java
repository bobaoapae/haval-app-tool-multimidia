package br.com.redesurftank.havalshisuku.managers;

import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Arms test-only cluster key inject. Never auto-armed on boot.
 *
 * <p>Enable from a privileged shell (adb / telnet root) — rewrite the token file
 * <em>after</em> boot so its mtime is newer than boot:
 * <pre>
 *   TOKEN=$(tr -dc a-f0-9 &lt;/dev/urandom | head -c 32)
 *   echo -n "$TOKEN" &gt; /data/local/tmp/impulse_key_inject.token
 *   echo "$TOKEN"
 * </pre>
 *
 * <p>A leftover file from a previous boot is rejected (mtime before boot).
 * {@link #disarm()} also runs on boot when the app can delete the file.
 */
public final class TestKeyInjectGate {
    private static final String TAG = "TestKeyInjectGate";
    public static final String TOKEN_PATH = "/data/local/tmp/impulse_key_inject.token";
    /** Allow small clock skew between file mtime and boot wall-clock estimate. */
    private static final long BOOT_SKEW_MS = 15_000L;

    private TestKeyInjectGate() {}

    public static File tokenFile() {
        return new File(TOKEN_PATH);
    }

    public static boolean isArmed() {
        return matchesToken(readToken());
    }

    public static String readToken() {
        File file = tokenFile();
        if (!file.isFile()) {
            return null;
        }
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            if (line == null) {
                return null;
            }
            return line.trim();
        } catch (IOException e) {
            Log.w(TAG, "Failed to read inject token file", e);
            return null;
        }
    }

    public static boolean matchesToken(String provided) {
        if (provided == null || provided.isEmpty()) {
            return false;
        }
        File file = tokenFile();
        if (!file.isFile() || file.length() == 0L) {
            return false;
        }
        if (!isTokenFileFromCurrentBoot(file)) {
            Log.w(TAG, "Rejecting stale inject token (mtime before this boot)");
            return false;
        }
        String expected = readToken();
        if (expected == null || expected.isEmpty()) {
            return false;
        }
        if (expected.length() != provided.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < expected.length(); i++) {
            diff |= expected.charAt(i) ^ provided.charAt(i);
        }
        return diff == 0;
    }

    /**
     * Token file must have been written after this boot so cold start never
     * inherits a previous session's arming file.
     */
    static boolean isTokenFileFromCurrentBoot(File file) {
        return isTokenFileFromCurrentBoot(
                file.lastModified(),
                System.currentTimeMillis(),
                SystemClock.elapsedRealtime()
        );
    }

    /** Visible for unit tests. */
    static boolean isTokenFileFromCurrentBoot(long modifiedMs, long nowMs, long elapsedRealtimeMs) {
        long bootWallMs = nowMs - elapsedRealtimeMs;
        return modifiedMs + BOOT_SKEW_MS >= bootWallMs;
    }

    /** Delete the arming token. Safe to call when absent. */
    public static boolean disarm() {
        File file = tokenFile();
        if (!file.exists()) {
            return true;
        }
        boolean deleted = file.delete();
        if (!deleted) {
            // Best-effort via Shizuku when the app uid cannot unlink a shell-owned file.
            try {
                String out =
                        br.com.redesurftank.havalshisuku.utils.ShizukuUtils.runCommandAndGetOutput(
                                new String[] {"rm", "-f", TOKEN_PATH});
                deleted = !tokenFile().exists();
                Log.w(TAG, "Shizuku disarm attempt deleted=" + deleted + " out=" + out);
            } catch (Exception e) {
                Log.w(TAG, "Failed to delete inject token at " + TOKEN_PATH, e);
            }
        } else {
            Log.w(TAG, "Disarmed test key inject (token removed)");
        }
        return deleted;
    }
}
