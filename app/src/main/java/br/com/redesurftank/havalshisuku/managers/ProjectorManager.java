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
import br.com.redesurftank.havalshisuku.utils.BuildUtils;

public class ProjectorManager {
    private static final String TAG = "ProjectorManager";

    private static ProjectorManager instance;

    private SharedPreferences sharedPreferences;
    private DisplayManager displayManager;
    private InstrumentProjector instrumentProjector;
    private InstrumentProjector2 instrumentProjector2;

    private final Map<Integer, BiConsumer<android.content.Context, Display>> projectorCreators = new HashMap<>();
    private DisplayManager.DisplayListener pendingOverlayListener;

    public static synchronized ProjectorManager getInstance() {
        if (instance == null) {
            instance = new ProjectorManager();
        }
        return instance;
    }

    private ProjectorManager() {
        sharedPreferences = App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE);

        if (!BuildUtils.isEmulator()) {
            // Real hardware: Display 3 is the physical 1280×480 cluster.
            int maskDisplayId = 3;
            projectorCreators.put(maskDisplayId, (ctx, disp) -> {
                instrumentProjector2 = new InstrumentProjector2(ctx, disp);
                instrumentProjector2.show();
                Log.w(TAG, "InstrumentProjector2 (Mask) initialized on Display " + disp.getDisplayId());
            });

            /* [DEPRECATED] Moved InstrumentProjector to Display 1 (HUD) as intended
               Disabled to save resources as logic moved to WebView.
            projectorCreators.put(1, (ctx, disp) -> {
                instrumentProjector = new InstrumentProjector(ctx, disp);
                instrumentProjector.show();
                Log.w(TAG, "InstrumentProjector (HUD) initialized on Display " + disp.getDisplayId());
            });
            */
        }
        // On emulator: display ID is resolved dynamically in initialize() by looking
        // for Display.TYPE_OVERLAY, so no hardcoded ID is needed.
        // Prerequisite: adb shell settings put global overlay_display_devices "1920x720/160"
    }

    public void initialize() {
        Log.w(TAG, "Initializing ProjectorManager");
        try {
            displayManager = App.getContext().getSystemService(DisplayManager.class);

            for (Display display : displayManager.getDisplays()) {
                Log.w(TAG, "Display found: " + display.getName() + " (ID: " + display.getDisplayId() + ")");
            }

            if (BuildUtils.isEmulator()) {
                // On emulator the overlay display ID changes whenever overlay_display_devices
                // is updated. Find it dynamically by type instead of hardcoding an ID.
                Display overlayDisplay = findOverlayDisplay();
                if (overlayDisplay != null) {
                    initializeClusterOnDisplay(overlayDisplay);
                } else {
                    Log.w(TAG, "No overlay display found yet — registering listener for TYPE_OVERLAY");
                    registerOverlayDisplayListener();
                }
            } else {
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
            }

            ServiceManager.getInstance().addDataChangedListener((key, value) -> {
                if (key.equals(CarConstants.CAR_BASIC_ENGINE_STATE.getValue())) {
                    if ("-1".equals(value) || "15".equals(value) || "14".equals(value) || "10".equals(value)) {
                        if (instrumentProjector != null) {
                            instrumentProjector.carMainScreenOff();
                        }
                        if (instrumentProjector2 != null) {
                            instrumentProjector2.carMainScreenOff();
                        }
                        
                        // Kill all apps on display 1 and 3
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
        if (pendingOverlayListener != null) {
            try { displayManager.unregisterDisplayListener(pendingOverlayListener); } catch (Exception ignored) {}
            pendingOverlayListener = null;
        }
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
    }

    public void refresh() {
        Log.w(TAG, "Refreshing ProjectorManager");
        stopProjectors();

        // Real-hardware creators are re-populated here; emulator resolves dynamically in initialize().
        if (!BuildUtils.isEmulator()) {
            projectorCreators.put(3, (ctx, disp) -> {
                instrumentProjector2 = new InstrumentProjector2(ctx, disp);
                instrumentProjector2.show();
                Log.w(TAG, "InstrumentProjector2 (Mask) refreshed on Display " + disp.getDisplayId());
            });

            /* Disabled to save resources as logic moved to WebView.
            projectorCreators.put(1, (ctx, disp) -> {
                instrumentProjector = new InstrumentProjector(ctx, disp);
                instrumentProjector.show();
                Log.w(TAG, "InstrumentProjector (HUD) refreshed on Display " + disp.getDisplayId());
            });
            */
        }

        initialize();
    }

    /** Finds the first display of type OVERLAY (emulator virtual overlay display). */
    private Display findOverlayDisplay() {
        for (Display display : displayManager.getDisplays()) {
            if (display.getName() != null && display.getName().startsWith("Overlay")) {
                Log.w(TAG, "Found overlay display: " + display.getName() + " (ID: " + display.getDisplayId() + ")");
                return display;
            }
        }
        return null;
    }

    /** Creates and shows InstrumentProjector2 on the given display. */
    private void initializeClusterOnDisplay(Display display) {
        instrumentProjector2 = new InstrumentProjector2(App.getContext(), display);
        instrumentProjector2.show();
        Log.w(TAG, "InstrumentProjector2 (Mask) initialized on Display " + display.getDisplayId());
    }

    /**
     * Registers a DisplayListener that waits for the first TYPE_OVERLAY display to appear.
     * Used on the emulator when the overlay virtual display hasn't been created yet.
     */
    private void registerOverlayDisplayListener() {
        if (pendingOverlayListener != null) {
            displayManager.unregisterDisplayListener(pendingOverlayListener);
            pendingOverlayListener = null;
        }
        pendingOverlayListener = new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
                Display display = displayManager.getDisplay(displayId);
                if (display != null && display.getName() != null && display.getName().startsWith("Overlay")) {
                    Log.w(TAG, "Overlay display added dynamically: " + displayId);
                    new Handler(Looper.getMainLooper()).post(() -> initializeClusterOnDisplay(display));
                    displayManager.unregisterDisplayListener(this);
                    pendingOverlayListener = null;
                }
            }

            @Override
            public void onDisplayRemoved(int displayId) {
                Log.w(TAG, "Display removed: " + displayId);
            }

            @Override
            public void onDisplayChanged(int displayId) {
                Log.w(TAG, "Display changed: " + displayId);
            }
        };
        displayManager.registerDisplayListener(pendingOverlayListener, new Handler(Looper.getMainLooper()));
        Log.w(TAG, "Registered overlay display listener (waiting for TYPE_OVERLAY)");
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