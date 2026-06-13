package br.com.redesurftank.havalshisuku.managers;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import br.com.redesurftank.App;
import br.com.redesurftank.havalshisuku.models.CarConstants;
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys;
import br.com.redesurftank.havalshisuku.projectors.InstrumentProjector;
import br.com.redesurftank.havalshisuku.projectors.InstrumentProjector2;
import br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher;

public class ProjectorManager {
    private static final String TAG = "ProjectorManager";

    private static ProjectorManager instance;

    private SharedPreferences sharedPreferences;
    private DisplayManager displayManager;
    private InstrumentProjector instrumentProjector;
    private InstrumentProjector2 instrumentProjector2;
    private boolean initialized = false;

    private final Map<Integer, BiConsumer<android.content.Context, Display>> projectorCreators = new HashMap<>();

    public static synchronized ProjectorManager getInstance() {
        if (instance == null) {
            instance = new ProjectorManager();
        }
        return instance;
    }

    private ProjectorManager() {
        sharedPreferences = App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE);

        int maskDisplayId = 3;
        int hudDisplayId = 1;

        projectorCreators.put(maskDisplayId, (ctx, disp) -> {
            instrumentProjector2 = new InstrumentProjector2(ctx, disp);
            instrumentProjector2.show();
            Log.w(TAG, "InstrumentProjector2 (Mask) initialized on Display " + disp.getDisplayId());
        });

        projectorCreators.put(hudDisplayId, (ctx, disp) -> {
            instrumentProjector = new InstrumentProjector(ctx, disp);
            instrumentProjector.show();
            Log.w(TAG, "InstrumentProjector (HUD) initialized on Display " + disp.getDisplayId());
        });
    }

    public void initialize() {
        Log.w(TAG, "Initializing ProjectorManager");
        try {
            if (initialized && (instrumentProjector != null || instrumentProjector2 != null)) {
                Log.w(TAG, "ProjectorManager already initialized; skipping duplicate presentations");
                return;
            }

            displayManager = App.getContext().getSystemService(DisplayManager.class);

            for (Display display : displayManager.getDisplays()) {
                Log.w(TAG, "Display found: " + display.getName() + " (ID: " + display.getDisplayId() + ")");
            }

            Set<Integer> pending = new HashSet<>(projectorCreators.keySet());

            for (Integer id : projectorCreators.keySet()) {
                Display display = getDisplayById(id);
                if (display != null) {
                    projectorCreators.get(id).accept(App.getContext(), display);
                    pending.remove(id);
                }
            }

            if (!pending.isEmpty()) {
                registerDisplayListener(pending);
            }

            initialized = true;

            ServiceManager.getInstance().addDataChangedListener((key, value) -> {
                if (key.equals(CarConstants.CAR_BASIC_ENGINE_STATE.getValue())) {
                    if (!br.com.redesurftank.havalshisuku.models.EngineState.isMainScreenOn(value)) {
                        if (instrumentProjector != null) {
                            instrumentProjector.carMainScreenOff();
                        }
                        if (instrumentProjector2 != null) {
                            instrumentProjector2.carMainScreenOff();
                        }
                        
                        // Kill all secondary display apps when the main screen turns off.
                        java.util.List<br.com.redesurftank.havalshisuku.models.DisplayAppConfig> configs = DisplayAppLauncher.INSTANCE.getAllConfigs();
                        for (br.com.redesurftank.havalshisuku.models.DisplayAppConfig config : configs) {
                             DisplayAppLauncher.TaskInfo task = DisplayAppLauncher.INSTANCE.findTaskForPackage(config.getPackageName());
                             if (task != null && (task.getDisplayId() == 1 || task.getDisplayId() == 3)) {
                                 Log.w(TAG, "Shutting down: killing app " + config.getPackageName() + " on display " + task.getDisplayId());
                                 DisplayAppLauncher.killAppAsync(config.getPackageName());
                             }
                        }

                        String defaultPackage = sharedPreferences.getString(SharedPreferencesKeys.DEFAULT_DISPLAY_APP_PACKAGE.getKey(), "");
                        if (!defaultPackage.isEmpty()) {
                            DisplayAppLauncher.killAppAsync(defaultPackage);
                        }
                    } else {
                        if (instrumentProjector != null) {
                            instrumentProjector.carMainScreenOn();
                        }
                        if (instrumentProjector2 != null) {
                            instrumentProjector2.carMainScreenOn();
                        }
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize ProjectorManager", e);
        }
    }

    public void stopProjectors() {
        Log.w(TAG, "Stopping all projectors");
        if (instrumentProjector != null) {
            try {
                instrumentProjector.dismiss();
            } catch (Exception e) {
                Log.e(TAG, "Error dismissing instrumentProjector", e);
            }
            instrumentProjector = null;
        }
        if (instrumentProjector2 != null) {
            try {
                instrumentProjector2.dismiss();
            } catch (Exception e) {
                Log.e(TAG, "Error dismissing instrumentProjector2", e);
            }
            instrumentProjector2 = null;
        }
        projectorCreators.clear();
        initialized = false;
    }

    public void refresh() {
        Log.w(TAG, "Refreshing ProjectorManager");
        stopProjectors();
        
        // Re-read preferences and re-populate creators
        int maskDisplayId = 3;
        int hudDisplayId = 1;

        projectorCreators.put(maskDisplayId, (ctx, disp) -> {
            instrumentProjector2 = new InstrumentProjector2(ctx, disp);
            instrumentProjector2.show();
            Log.w(TAG, "InstrumentProjector2 (Mask) refreshed on Display " + disp.getDisplayId());
        });

        projectorCreators.put(hudDisplayId, (ctx, disp) -> {
            instrumentProjector = new InstrumentProjector(ctx, disp);
            instrumentProjector.show();
            Log.w(TAG, "InstrumentProjector (HUD) refreshed on Display " + disp.getDisplayId());
        });

        initialize();
    }

    private Display getDisplayById(int id) {
        for (Display display : displayManager.getDisplays()) {
            if (display.getDisplayId() == id) {
                return display;
            }
        }
        return null;
    }

    private void registerDisplayListener(Set<Integer> pending) {
        DisplayManager.DisplayListener listener = new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
                Log.w(TAG, "Display added: " + displayId);
                if (pending.contains(displayId)) {
                    Display display = displayManager.getDisplay(displayId);
                    if (display != null) {
                        projectorCreators.get(displayId).accept(App.getContext(), display);
                        pending.remove(displayId);
                        if (pending.isEmpty()) {
                            displayManager.unregisterDisplayListener(this);
                        }
                    }
                }
            }

            @Override
            public void onDisplayRemoved(int displayId) {
                // Handle if needed
                Log.w(TAG, "Display removed: " + displayId);
            }

            @Override
            public void onDisplayChanged(int displayId) {
                // Handle if needed
                Log.w(TAG, "Display changed: " + displayId);
            }
        };
        displayManager.registerDisplayListener(listener, new Handler(Looper.getMainLooper()));
        Log.w(TAG, "Registered listener for missing displays: " + pending);
    }
}
