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
import java.security.MessageDigest;

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

    private static final long WATCHDOG_INTERVAL_MS = 60000L;
    private static final long START_GRACE_MS = 20000L;

    // UI-facing status modes
    public static final String MODE_OFF = "OFF";
    public static final String MODE_STARTING = "STARTING";
    public static final String MODE_WLAN = "WLAN";
    public static final String MODE_4G = "4G";
    public static final String MODE_ERROR = "ERROR";
    // Se o statefile não é reescrito há mais que isto, o daemon está vivo mas travado (loop pendurado):
    // trata como ERRO em vez de mostrar o último modo congelado. O daemon reescreve a cada ~5s.
    private static final long STALE_STATE_MAX_AGE_SEC = 30L;

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
    private volatile HandlerThread handlerThread;
    private volatile Handler bgHandler;
    private volatile boolean watchdogRunning = false;
    private volatile long enableTimeMs = 0L;

    private HotRouterManager() {
        prefs = App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE);
    }

    private synchronized Handler workerHandler() {
        if (bgHandler == null) {
            handlerThread = new HandlerThread("HotRouterThread");
            handlerThread.start();
            bgHandler = new Handler(handlerThread.getLooper());
        }
        return bgHandler;
    }

    private boolean isEnabled() {
        return prefs.getBoolean(SharedPreferencesKeys.ENABLE_HOT_ROUTER.getKey(), false);
    }

    /** Called from the settings toggle. Persistence is done by the UI before this call. */
    public void setEnabled(boolean enabled) {
        Handler handler = workerHandler();
        if (enabled) {
            enableTimeMs = SystemClock.elapsedRealtime();
            handler.post(() -> {
                if (pushScript()) startDaemon();
            });
            startWatchdog();
        } else {
            handler.post(() -> {
                handler.removeCallbacks(watchdogRunnable);
                watchdogRunning = false;
                if (pushScript()) stopDaemon();
            });
        }
    }

    /** Called by ForegroundService once Shizuku is ready (covers boot autostart). */
    public void onServicesReady() {
        if (isEnabled()) {
            enableTimeMs = SystemClock.elapsedRealtime();
            workerHandler().post(() -> {
                if (!pushScript()) return;
                String alive = readDaemonStatus();
                if (!isAliveStatusForTest(alive)) {
                    startDaemon();
                }
            });
            startWatchdog();
        }
    }

    private boolean pushScript() {
        try {
            byte[] asset = readAssetBytes();
            if (asset.length == 0) {
                Log.e(TAG, "Empty hotrouter.sh asset, aborting push");
                return false;
            }
            String b64 = Base64.encodeToString(asset, Base64.NO_WRAP);
            String expectedHash = sha256Hex(asset);
            String temporary = SCRIPT + ".tmp";
            String writeCmd = "echo " + b64 + " | base64 -d > " + temporary
                    + " && chmod 755 " + temporary
                    + " && sha256sum " + temporary + " | awk '{print $1}'";
            String temporaryHash = ShizukuUtils.runCommandAndGetOutput(
                    new String[]{"sh", "-c", writeCmd}).trim();
            if (!expectedHash.equalsIgnoreCase(temporaryHash)) {
                ShizukuUtils.runCommandAndGetOutput(new String[]{"rm", "-f", temporary});
                Log.e(TAG, "hotrouter.sh hash mismatch before install");
                return false;
            }
            String installedHash = ShizukuUtils.runCommandAndGetOutput(
                    new String[]{"sh", "-c", "mv " + temporary + " " + SCRIPT
                            + " && sha256sum " + SCRIPT + " | awk '{print $1}'"}).trim();
            if (!expectedHash.equalsIgnoreCase(installedHash)) {
                Log.e(TAG, "hotrouter.sh hash mismatch after install");
                return false;
            }
            Log.w(TAG, "hotrouter.sh pushed to " + SCRIPT);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to push hotrouter.sh: " + e.getMessage(), e);
            return false;
        }
    }

    private byte[] readAssetBytes() {
        try (InputStream is = App.getContext().getAssets().open(ASSET_NAME)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return baos.toByteArray();
        } catch (Exception e) {
            Log.e(TAG, "Failed to read asset " + ASSET_NAME + ": " + e.getMessage(), e);
            return new byte[0];
        }
    }

    private String sha256Hex(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) hex.append(String.format("%02x", value & 0xff));
        return hex.toString();
    }

    private String readDaemonStatus() {
        return ShizukuUtils.runCommandAndGetOutput(new String[]{"sh", SCRIPT, "status"}).trim();
    }

    static boolean isAliveStatusForTest(String raw) {
        if (raw == null) return false;
        String[] parts = raw.trim().split("\\|");
        if (parts.length != 2 || !"ALIVE".equals(parts[0])) return false;
        try {
            return Long.parseLong(parts[1]) > 1L;
        } catch (NumberFormatException ignored) {
            return false;
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
            String output = ShizukuUtils.runCommandAndGetOutput(new String[]{"sh", SCRIPT, "stop"}).trim();
            if ("STOPPED".equals(output)) {
                Log.w(TAG, "hotrouter daemon stopped with verified teardown");
            } else {
                Log.e(TAG, "hotrouter daemon teardown was not confirmed: " + output);
            }
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
        workerHandler().post(() -> {
            if (watchdogRunning) {
                return;
            }
            watchdogRunning = true;
            workerHandler().postDelayed(watchdogRunnable, WATCHDOG_INTERVAL_MS);
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
                String alive = readDaemonStatus();
                if (!isAliveStatusForTest(alive)) {
                    Log.w(TAG, "Watchdog: daemon dead while enabled, relaunching");
                    enableTimeMs = SystemClock.elapsedRealtime();
                    if (pushScript()) startDaemon();
                }
            } catch (Exception e) {
                Log.e(TAG, "Watchdog error: " + e.getMessage(), e);
            }
            Handler handler = bgHandler;
            if (handler != null && isEnabled()) handler.postDelayed(this, WATCHDOG_INTERVAL_MS);
        }
    };

    /**
     * Blocking status read (runs shell via Shizuku). Call off the main thread.
     */
    public Status readStatusBlocking() {
        if (!isEnabled()) {
            return new Status(MODE_OFF, 0L);
        }
        String alive = readDaemonStatus();
        if (isAliveStatusForTest(alive)) {
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
                // Ligado != funcionando: se a última escrita do statefile é muito antiga, o daemon
                // travou (vivo mas sem iterar) -> reporta ERRO em vez do modo congelado.
                long ageSec = (System.currentTimeMillis() / 1000L) - epoch;
                if (epoch > 0L && ageSec > STALE_STATE_MAX_AGE_SEC) {
                    return new Status(MODE_ERROR, epoch);
                }
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
