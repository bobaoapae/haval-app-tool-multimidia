package br.com.redesurftank.havalshisuku.managers;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import br.com.redesurftank.App;
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys;
import br.com.redesurftank.havalshisuku.utils.ShizukuUtils;

public class HotRouterManager {

    private static final String TAG = "HotRouterManager";

    private static final String BASE = "/data/local/tmp";
    private static final String SCRIPT = BASE + "/hotrouter.sh";
    private static final String PIDFILE = BASE + "/hotrouter.pid";
    private static final String STATEFILE = BASE + "/hotrouter.state";
    private static final String ASSET_NAME = "hotrouter.sh";

    private static final String PID_ALIVE_CMD =
            "kill -0 $(cat " + PIDFILE + " 2>/dev/null) 2>/dev/null && echo ALIVE || echo DEAD";

    private static final long WATCHDOG_INTERVAL_MS = 60000L;
    private static final long START_GRACE_MS = 20000L;

    // UI-facing status modes
    public static final String MODE_OFF = "OFF";
    public static final String MODE_STARTING = "STARTING";
    public static final String MODE_WLAN = "WLAN";
    public static final String MODE_4G = "4G";
    public static final String MODE_ERROR = "ERROR";

    public static class Status {
        public final String mode;
        public final long epochSeconds;

        public Status(String mode, long epochSeconds) {
            this.mode = mode;
            this.epochSeconds = epochSeconds;
        }
    }

    private static volatile HotRouterManager INSTANCE;

    public static HotRouterManager getInstance() {
        if (INSTANCE == null) {
            synchronized (HotRouterManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new HotRouterManager();
                }
            }
        }
        return INSTANCE;
    }

    private final SharedPreferences prefs;
    private final Handler bgHandler;
    private volatile boolean watchdogRunning = false;
    private volatile long enableTimeMs = 0L;

    private HotRouterManager() {
        prefs = App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE);
        HandlerThread thread = new HandlerThread("HotRouterThread");
        thread.start();
        bgHandler = new Handler(thread.getLooper());
    }

    private boolean isEnabled() {
        return prefs.getBoolean(SharedPreferencesKeys.ENABLE_HOT_ROUTER.getKey(), false);
    }

    /** Called from the settings toggle. Persistence is done by the UI before this call. */
    public void setEnabled(boolean enabled) {
        if (enabled) {
            enableTimeMs = SystemClock.elapsedRealtime();
            bgHandler.post(() -> {
                pushScript();
                startDaemon();
            });
            startWatchdog();
        } else {
            bgHandler.post(this::stopDaemon);
        }
    }

    /** Called by ForegroundService once Shizuku is ready (covers boot autostart). */
    public void onServicesReady() {
        if (isEnabled()) {
            enableTimeMs = SystemClock.elapsedRealtime();
            bgHandler.post(() -> {
                pushScript();
                String alive = ShizukuUtils.runCommandAndGetOutput(new String[]{"sh", "-c", PID_ALIVE_CMD});
                if (!alive.contains("ALIVE")) {
                    startDaemon();
                }
            });
            startWatchdog();
        }
    }

    private void pushScript() {
        try {
            String b64 = readAssetBase64();
            if (b64.isEmpty()) {
                Log.e(TAG, "Empty hotrouter.sh asset, aborting push");
                return;
            }
            String cmd = "echo " + b64 + " | base64 -d > " + SCRIPT + " && chmod 755 " + SCRIPT;
            ShizukuUtils.runCommandAndGetOutput(new String[]{"sh", "-c", cmd});
            Log.w(TAG, "hotrouter.sh pushed to " + SCRIPT);
        } catch (Exception e) {
            Log.e(TAG, "Failed to push hotrouter.sh: " + e.getMessage(), e);
        }
    }

    private String readAssetBase64() {
        try (InputStream is = App.getContext().getAssets().open(ASSET_NAME)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "Failed to read asset " + ASSET_NAME + ": " + e.getMessage(), e);
            return "";
        }
    }

    private void startDaemon() {
        try {
            // Detach with the project's proven idiom (see FridaUtils): setsid + redirect
            // + `< /dev/null` so the daemon fully reparents and the Shizuku controlling
            // process returns immediately (otherwise runCommandAndGetOutput's waitFor()
            // could wedge the single HotRouterThread looper on the infinite-loop daemon).
            String cmd = "setsid sh " + SCRIPT + " start >" + BASE + "/hotrouter.out 2>&1 < /dev/null &";
            ShizukuUtils.runCommandAndGetOutput(new String[]{"sh", "-c", cmd});
            Log.w(TAG, "hotrouter daemon start requested");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start hotrouter daemon: " + e.getMessage(), e);
        }
    }

    private void stopDaemon() {
        try {
            ShizukuUtils.runCommandAndGetOutput(new String[]{"sh", "-c", "sh " + SCRIPT + " stop"});
            Log.w(TAG, "hotrouter daemon stop requested");
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop hotrouter daemon: " + e.getMessage(), e);
        }
    }

    /**
     * Arms the watchdog. Posted to bgHandler so the watchdogRunning check-then-set runs
     * on the single looper thread (no race between setEnabled and onServicesReady). The
     * loop self-terminates the next time it finds the feature disabled.
     */
    private void startWatchdog() {
        bgHandler.post(() -> {
            if (watchdogRunning) {
                return;
            }
            watchdogRunning = true;
            bgHandler.postDelayed(watchdogRunnable, WATCHDOG_INTERVAL_MS);
        });
    }

    private final Runnable watchdogRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isEnabled()) {
                watchdogRunning = false;
                return;
            }
            try {
                String alive = ShizukuUtils.runCommandAndGetOutput(new String[]{"sh", "-c", PID_ALIVE_CMD});
                if (!alive.contains("ALIVE")) {
                    Log.w(TAG, "Watchdog: daemon dead while enabled, relaunching");
                    enableTimeMs = SystemClock.elapsedRealtime();
                    pushScript();
                    startDaemon();
                }
            } catch (Exception e) {
                Log.e(TAG, "Watchdog error: " + e.getMessage(), e);
            }
            bgHandler.postDelayed(this, WATCHDOG_INTERVAL_MS);
        }
    };

    /**
     * Blocking status read (runs shell via Shizuku). Call off the main thread.
     */
    public Status readStatusBlocking() {
        if (!isEnabled()) {
            return new Status(MODE_OFF, 0L);
        }
        String alive = ShizukuUtils.runCommandAndGetOutput(new String[]{"sh", "-c", PID_ALIVE_CMD});
        if (alive.contains("ALIVE")) {
            String raw = ShizukuUtils.runCommandAndGetOutput(
                    new String[]{"sh", "-c", "cat " + STATEFILE + " 2>/dev/null"}).trim();
            if (raw.isEmpty()) {
                return new Status(MODE_STARTING, 0L);
            }
            String[] parts = raw.split("\\|");
            String mode = parts[0].trim();
            long epoch = 0L;
            if (parts.length > 1) {
                try {
                    epoch = Long.parseLong(parts[1].trim());
                } catch (NumberFormatException ignored) {
                }
            }
            if (MODE_OFF.equals(mode)) {
                return new Status(MODE_STARTING, epoch);
            }
            if (MODE_WLAN.equals(mode) || MODE_4G.equals(mode)) {
                return new Status(mode, epoch);
            }
            return new Status(MODE_STARTING, epoch);
        }
        long since = SystemClock.elapsedRealtime() - enableTimeMs;
        if (since < START_GRACE_MS) {
            return new Status(MODE_STARTING, 0L);
        }
        return new Status(MODE_ERROR, 0L);
    }
}
