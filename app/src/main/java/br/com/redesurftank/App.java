package br.com.redesurftank;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.webkit.WebView;

import br.com.redesurftank.havalshisuku.BuildConfig;
import br.com.redesurftank.havalshisuku.diagnostics.ClusterPersistentEventLogger;
import br.com.redesurftank.havalshisuku.services.ForegroundService;

public class App extends Application {

    private static Application sApplication;
    private static Context deviceProtectedContext;
    /** 1 = binds before Android Auto, 2 = process already up, leave it. */
    private volatile int earlyMountResult;

    public static Application getApplication() {
        return sApplication;
    }

    public static Context getContext() {
        return getApplication().getApplicationContext();
    }

    public synchronized static Context getDeviceProtectedContext() {
        if (deviceProtectedContext == null) {
            deviceProtectedContext = getApplication().createDeviceProtectedStorageContext();
        }
        return deviceProtectedContext;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        // Earliest app code. A background thread started in onCreate was still
        // queued when Android Auto opened the stock APK (~13 s). This call
        // runs on the main thread as the process is created (~11 s).
        sApplication = this;
        deviceProtectedContext = createDeviceProtectedStorageContext();
        try {
            if (autoMountEnabled()) {
                earlyMountResult = mountAndroidAutoThroughLocalTelnet();
            }
        } catch (Throwable ignored) {
            earlyMountResult = 0;
        }
        android.util.Log.e("ImpulseAaEarly", "attach mount=" + earlyMountResult);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sApplication = this;
        br.com.redesurftank.havalshisuku.utils.SessionApkCache.clear(
                getCacheDir(), getExternalFilesDir(null));
        if (earlyMountResult != 1 && earlyMountResult != 2) {
            new Thread(this::mountAndroidAutoAsEarlyAsShizukuAllows, "aa-early-mount").start();
        }
        if (BuildConfig.DEBUG) {
            // This process is directBootAware. WebView reads credential storage and
            // throws before the user is unlocked, which used to kill the process
            // and push the Android Auto mount until after AA had already started.
            try {
                WebView.setWebContentsDebuggingEnabled(true);
            } catch (Throwable ignored) {
            }
        }
        ClusterPersistentEventLogger.logText(
                "app_start",
                "versionCode=" + BuildConfig.VERSION_CODE + " versionName=" + BuildConfig.VERSION_NAME
        );
        br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.ensureDefaultDesktopShortcuts();

        // Before ForegroundService / cluster projector start: if the active theme is
        // legacy or contract-incompatible, fall back to the APK-bundled Default.
        //
        // This process is started by a directBootAware receiver, so onCreate can run while
        // credential-protected storage is still locked — and themes/ lives there. Every theme
        // then reads as missing and a perfectly valid one gets reset. The migration reports
        // that case instead of guessing, and we retry once the user is unlocked.
        if (!br.com.redesurftank.havalshisuku.managers.ThemeManager.getInstance(this).runStartupThemeMigrations()) {
            scheduleThemeMigrationOnUserUnlock();
        }

        var context = getContext();
        Intent serviceIntent = new Intent(context, ForegroundService.class);
        context.startForegroundService(serviceIntent);
    }

    /**
     * @return 1 when the binds landed before Android Auto started, 2 when the
     *         process was already up (do not swap under it), -1 when the staged
     *         APK is missing, 0 when telnet is not up yet.
     */
    private int mountAndroidAutoThroughLocalTelnet() {
        java.io.File script = new java.io.File(
                getDeviceProtectedContext().getFilesDir(), "aa-cluster-mount-early.sh");
        if (!copyAssetTo("aa_cluster/aa-cluster-mount-early.sh", script)) return 0;
        br.com.redesurftank.havalshisuku.utils.TelnetClientWrapper telnet =
                new br.com.redesurftank.havalshisuku.utils.TelnetClientWrapper();
        try {
            telnet.connect("127.0.0.1", 23, 400);
            String output = telnet.executeCommand("sh " + script.getAbsolutePath(), 3000);
            if (output == null) return 0;
            if (output.contains("MOUNT_EARLY")) return 1;
            if (output.contains("MOUNT_LATE")) return 2;
            if (output.contains("MOUNT_NO_SOURCE")) return -1;
            return 0;
        } catch (Throwable ignored) {
            return 0;
        } finally {
            try {
                telnet.disconnect();
            } catch (Throwable ignored) {
            }
        }
    }

    private boolean copyAssetTo(String assetPath, java.io.File script) {
        try (java.io.InputStream in = getAssets().open(assetPath);
             java.io.OutputStream out = new java.io.FileOutputStream(script)) {
            byte[] buf = new byte[4096];
            int read;
            while ((read = in.read(buf)) > 0) {
                out.write(buf, 0, read);
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean autoMountEnabled() {
        return getDeviceProtectedContext()
                .getSharedPreferences("haval_prefs", MODE_PRIVATE)
                .getBoolean("aaPatchAutoMount", false);
    }

    private void mountAndroidAutoAsEarlyAsShizukuAllows() {
        for (int i = 0; i < 40; i++) {
            try {
                if (!autoMountEnabled()) return;
                int mounted = mountAndroidAutoThroughLocalTelnet();
                if (mounted == 1 || mounted == 2) return;
                if (mounted < 0) break;
            } catch (Throwable ignored) {
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException stopped) {
                return;
            }
        }
        for (int i = 0; i < 200; i++) {
            try {
                if (rikka.shizuku.Shizuku.pingBinder()) {
                    boolean autoMount = getDeviceProtectedContext()
                            .getSharedPreferences("haval_prefs", MODE_PRIVATE)
                            .getBoolean("aaPatchAutoMount", false);
                    if (!autoMount) return;
                    br.com.redesurftank.havalshisuku.managers.AndroidAutoPatchManager.INSTANCE
                            .ensureMountedWithService();
                    if (br.com.redesurftank.havalshisuku.managers.AndroidAutoPatchManager.INSTANCE.isMounted()) {
                        return;
                    }
                }
            } catch (Throwable ignored) {
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException stopped) {
                return;
            }
        }
    }

    /**
     * Runs the theme migration again as soon as credential-protected storage unlocks.
     * Registered on the device-protected context because the app is still locked here.
     */
    private void scheduleThemeMigrationOnUserUnlock() {
        Context deviceContext = getDeviceProtectedContext();
        deviceContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context received, Intent intent) {
                br.com.redesurftank.havalshisuku.managers.ThemeManager
                        .getInstance(App.this)
                        .runStartupThemeMigrations();
                try {
                    deviceContext.unregisterReceiver(this);
                } catch (IllegalArgumentException alreadyGone) {
                    // Already unregistered - nothing to undo.
                }
            }
        }, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
    }
}
