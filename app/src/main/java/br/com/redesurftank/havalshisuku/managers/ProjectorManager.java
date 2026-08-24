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
    private int maskDisplayId;
    private int hudDisplayId;
    private DisplayManager.DisplayListener displayListener;

    private final Map<Integer, BiConsumer<android.content.Context, Display>> projectorCreators = new HashMap<>();

    public static synchronized ProjectorManager getInstance() {
        if (instance == null) {
            instance = new ProjectorManager();
        }
        return instance;
    }

    private ProjectorManager() {
        sharedPreferences = App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE);

        registerProjectorCreators("INIT");
    }

    /** (Re)populates the creator map. Split out so {@link #refresh()} cannot drift from here. */
    private void registerProjectorCreators(String reason) {
        maskDisplayId = br.com.redesurftank.havalshisuku.BuildConfig.SIMULATOR_MODE ? 0 : 3;
        hudDisplayId = br.com.redesurftank.havalshisuku.BuildConfig.SIMULATOR_MODE ? -1 : 1;

        projectorCreators.put(maskDisplayId, (ctx, disp) -> {
            instrumentProjector2 = new InstrumentProjector2(ctx, disp);
            instrumentProjector2.show();
            Log.w(TAG, "InstrumentProjector2 (Mask) " + reason + " on Display " + disp.getDisplayId());
        });

        projectorCreators.put(hudDisplayId, (ctx, disp) -> {
            instrumentProjector = new InstrumentProjector(ctx, disp);
            instrumentProjector.show();
            Log.w(TAG, "InstrumentProjector (HUD) " + reason + " on Display " + disp.getDisplayId());
        });
    }

    private android.app.Presentation projectorForDisplay(int displayId) {
        if (displayId == maskDisplayId) return instrumentProjector2;
        if (displayId == hudDisplayId) return instrumentProjector;
        return null;
    }

    /**
     * A projector counts as live only while its Presentation is actually showing.
     *
     * The field staying non-null is not enough: the framework cancels a Presentation by itself
     * when its display is removed or when the display metrics change underneath it, and the
     * reference we hold survives that. Checking isShowing() is what lets a cancelled surface be
     * rebuilt instead of being mistaken for a healthy one.
     */
    private boolean isProjectorLive(int displayId) {
        android.app.Presentation presentation = projectorForDisplay(displayId);
        return presentation != null && presentation.isShowing();
    }

    private void logProjectorEvent(String event, String reason, int displayId, String detail) {
        java.util.Map<String, Object> details = new HashMap<>();
        details.put("reason", reason);
        details.put("displayId", displayId);
        if (detail != null) details.put("detail", detail);
        br.com.redesurftank.havalshisuku.diagnostics.ClusterPersistentEventLogger.log(event, details);
    }

    /**
     * Builds every projector that is not currently live, one display at a time, and returns the
     * display ids that still have none.
     *
     * Each creator is guarded on its own on purpose. Previously all of them ran inside a single
     * try block, so one throw aborted the loop and took the other display down with it — and
     * because nothing retried, the cluster stayed dead for the rest of the session. Observed
     * 2026-08-20: display 1 came up, display 3 was never built, and no durable trace said why.
     */
    private Set<Integer> ensureProjectors(String reason) {
        Set<Integer> stillPending = new HashSet<>();

        for (Map.Entry<Integer, BiConsumer<android.content.Context, Display>> entry : projectorCreators.entrySet()) {
            int displayId = entry.getKey();
            if (isProjectorLive(displayId)) continue;

            Display display = getDisplayById(displayId);
            if (display == null) {
                stillPending.add(displayId);
                logProjectorEvent("projector_display_absent", reason, displayId, null);
                continue;
            }

            // A cancelled Presentation is still referenced by its field; drop it before
            // replacing so the old window cannot linger behind the new one.
            android.app.Presentation stale = projectorForDisplay(displayId);
            if (stale != null) {
                try {
                    stale.dismiss();
                } catch (Exception ignored) {
                    // Already torn down by the framework - nothing to undo.
                }
            }

            try {
                entry.getValue().accept(App.getContext(), display);
                logProjectorEvent("projector_created", reason, displayId, null);
            } catch (Throwable t) {
                stillPending.add(displayId);
                Log.e(TAG, "Failed to create projector for display " + displayId, t);
                logProjectorEvent("projector_create_failed", reason, displayId, t.getClass().getName());
            }
        }

        return stillPending;
    }

    public void initialize() {
        Log.w(TAG, "Initializing ProjectorManager");
        try {
            // NOTE: no "already initialized, bail out" guard any more. The old one returned as
            // soon as *either* projector existed, so a re-init could never repair the missing
            // one. ensureProjectors() is idempotent - it skips whatever is already live - so
            // running it again is always safe and is the only way a half-built state recovers.

            displayManager = App.getContext().getSystemService(DisplayManager.class);

            for (Display display : displayManager.getDisplays()) {
                Log.w(TAG, "Display found: " + display.getName() + " (ID: " + display.getDisplayId() + ")");
            }

            // Register BEFORE the first scan. Registering afterwards left a window where a
            // display that appeared between the scan and the registration fired an
            // onDisplayAdded nobody was listening for, and was then never built at all.
            ensureDisplayListener();

            Set<Integer> pending = ensureProjectors("INITIALIZE");
            if (!pending.isEmpty()) {
                Log.w(TAG, "Projectors still pending a display: " + pending);
            }

            if (initialized) {
                return;
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
        if (displayListener != null && displayManager != null) {
            try {
                displayManager.unregisterDisplayListener(displayListener);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering display listener", e);
            }
        }
        displayListener = null;
        projectorCreators.clear();
        initialized = false;
    }

    public void refresh() {
        Log.w(TAG, "Refreshing ProjectorManager");
        stopProjectors();
        // Re-read preferences and re-populate creators
        registerProjectorCreators("refreshed");
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

    /**
     * Registers a single, permanent display listener.
     *
     * The previous one was torn down as soon as the last missing display turned up, which meant
     * the only recovery path existed exactly until it was first used. Anything that killed a
     * Presentation afterwards - the display going away, or its metrics changing, both of which
     * make the framework cancel a Presentation on its own - went unnoticed forever. Callbacks
     * already arrive on the main looper, so they can rebuild in place.
     */
    private void ensureDisplayListener() {
        if (displayListener != null) return;

        displayListener = new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
                Log.w(TAG, "Display added: " + displayId);
                if (projectorCreators.containsKey(displayId) && !isProjectorLive(displayId)) {
                    ensureProjectors("DISPLAY_ADDED");
                }
            }

            @Override
            public void onDisplayRemoved(int displayId) {
                Log.w(TAG, "Display removed: " + displayId);
                if (projectorCreators.containsKey(displayId)) {
                    // The Presentation is already cancelled by the framework at this point.
                    // Record it so a blank cluster can be told apart from one that was never
                    // built, then wait for the display to come back.
                    logProjectorEvent("projector_display_removed", "DISPLAY_REMOVED", displayId, null);
                }
            }

            @Override
            public void onDisplayChanged(int displayId) {
                if (projectorCreators.containsKey(displayId) && !isProjectorLive(displayId)) {
                    // Metrics changed under a live Presentation, so the framework cancelled it.
                    Log.w(TAG, "Display changed and projector no longer showing; rebuilding: " + displayId);
                    ensureProjectors("DISPLAY_CHANGED");
                }
            }
        };

        displayManager.registerDisplayListener(displayListener, new Handler(Looper.getMainLooper()));
        Log.w(TAG, "Registered persistent display listener");
    }
}
