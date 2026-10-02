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
    public void onCreate() {
        super.onCreate();
        sApplication = this;
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true);
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
