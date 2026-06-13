package br.com.redesurftank;

import android.app.Application;
import android.content.Context;
import android.content.Intent;

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
        
        // Keep projection patches available without remounting/restarting projection apps on
        // every app launch when the existing bind mounts are already healthy.
        br.com.redesurftank.havalshisuku.managers.AndroidAutoPatchManager.INSTANCE.ensureMounted();
        br.com.redesurftank.havalshisuku.managers.CarPlayPatchManager.INSTANCE.ensureMounted();
        br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.ensureDefaultDesktopShortcuts();

        var context = getContext();
        Intent serviceIntent = new Intent(context, ForegroundService.class);
        context.startForegroundService(serviceIntent);
    }
}
