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
import java.util.LinkedHashMap;
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
    private DisplayManager.DisplayListener displayListener;

    /**
     * O listener de dados do carro e registrado UMA vez so. Antes ele vinha carona na criacao das
     * Presentations, e como initialize() saia cedo sempre que algum projector ja existia, nunca
     * duplicava. Agora que os projetores dependem das preferencias, initialize() pode rodar
     * inteiro varias vezes sem criar nada (as duas prefs desligadas) — sem esta trava, cada
     * chamada empilharia mais um listener sobre o mesmo evento de ignicao.
     */
    private boolean dataChangedListenerRegistered = false;

    private final int maskDisplayId;
    private final int hudDisplayId;

    private final Map<Integer, BiConsumer<android.content.Context, Display>> projectorCreators = new LinkedHashMap<>();

    public static synchronized ProjectorManager getInstance() {
        if (instance == null) {
            instance = new ProjectorManager();
        }
        return instance;
    }

    private ProjectorManager() {
        sharedPreferences = App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE);

        maskDisplayId = br.com.redesurftank.havalshisuku.BuildConfig.SIMULATOR_MODE ? 0 : 3;
        hudDisplayId = br.com.redesurftank.havalshisuku.BuildConfig.SIMULATOR_MODE ? -1 : 1;

        populateCreators();
    }

    /**
     * Receita de como criar cada projector. Usada pelo construtor e pelo refresh().
     *
     * D1 (HUD wallpaper) is registered before D3 (masks) on purpose: LinkedHashMap iteration
     * order is insertion order, and ClusterBackgroundSync needs D1 attached/painted before D3
     * is allowed to show wallpaper-composited insets.
     */
    private void populateCreators() {
        projectorCreators.put(hudDisplayId, (ctx, disp) -> {
            instrumentProjector = new InstrumentProjector(ctx, disp);
            instrumentProjector.show();
            Log.w(TAG, "InstrumentProjector (HUD) initialized on Display " + disp.getDisplayId());
        });

        projectorCreators.put(maskDisplayId, (ctx, disp) -> {
            instrumentProjector2 = new InstrumentProjector2(ctx, disp);
            instrumentProjector2.show();
            Log.w(TAG, "InstrumentProjector2 (Mask) initialized on Display " + disp.getDisplayId());
        });
    }

    /**
     * A preferencia que decide se o projector daquele display deve EXISTIR.
     *
     * PORQUE AQUI E NAO LA DENTRO: uma Presentation viva nao e "so o desenho". Ela e uma JANELA
     * NOSSA ocupando o display do painel — enquanto existir, o painel e do app, mesmo que o
     * conteudo pintado dentro dela esteja vazio. Ate agora estas duas prefs so controlavam o que
     * era pintado; a janela era criada de qualquer jeito. Resultado: desligar o Virtual Cluster
     * nao devolvia o painel ao nativo.
     *
     * E POR QUE NAO DERRUBAR DEPOIS: derrubar (stopProjectors) e fragil por construcao — basta um
     * caminho chamar initialize()/refresh() de novo, ou o processo reiniciar, para a Presentation
     * voltar. Sempre escapa um caminho. A decisao tem que estar na origem, na criacao.
     *
     * Os defaults sao os MESMOS que o resto do app ja usa ao ler estas chaves
     * (ENABLE_VIRTUAL_CLUSTER: true, como em DisplayAppLauncher e InstrumentProjector2;
     * ENABLE_INSTRUMENT_PROJECTOR: false, como em ServiceManager e shouldShowProjector),
     * para que quem nunca mexeu nessas preferencias nao veja mudanca nenhuma.
     */
    private boolean isProjectorEnabled(int displayId) {
        try {
            if (displayId == maskDisplayId) {
                return sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_VIRTUAL_CLUSTER.getKey(), true);
            }
            if (displayId == hudDisplayId) {
                // O HUD fica de fora desta regra no uso normal, de proposito. A pref tem default
                // `false`, entao aplicar a checagem aqui deixaria de criar o projector do display 1
                // para TODO mundo — e ele existia (mesmo sem pintar) desde sempre. Nao ha ganho em
                // arriscar essa mudanca no dia a dia so para atender o Modo Concessionaria, que ja
                // e atendido pelo cluster. No modo, ai sim, ele nao sobe.
                return !StealthModeManager.isActive();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to read projector preference for display " + displayId + "; assuming enabled", e);
        }
        return true;
    }

    /**
     * A pref caiu com a janela ja no ar: derruba agora, senao ela fica por cima do painel ate o
     * proximo boot. Presentation.dismiss() exige a UI thread — os dois pontos que chamam
     * initialize()/refresh() ja postam no main looper.
     */
    private void dismissProjectorForDisplay(int displayId, String reason) {
        if (displayId == maskDisplayId && instrumentProjector2 != null) {
            Log.w(TAG, "Dismissing InstrumentProjector2 (Mask): " + reason);
            try {
                instrumentProjector2.dismiss();
            } catch (Exception e) {
                Log.e(TAG, "Error dismissing instrumentProjector2", e);
            }
            instrumentProjector2 = null;
        }
        if (displayId == hudDisplayId && instrumentProjector != null) {
            Log.w(TAG, "Dismissing InstrumentProjector (HUD): " + reason);
            try {
                instrumentProjector.dismiss();
            } catch (Exception e) {
                Log.e(TAG, "Error dismissing instrumentProjector", e);
            }
            instrumentProjector = null;
        }
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
     * Builds every enabled projector that is not currently live, one display at a time, and
     * returns the display ids that still have none.
     *
     * A projector disabled by preference is skipped outright - never built and never counted as
     * pending - so neither this pass nor the display listener can bring its window back.
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
            if (!isProjectorEnabled(displayId)) {
                Log.w(TAG, "Projector for display " + displayId + " is disabled by preference; not creating it");
                continue;
            }
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
            // Preferencia desligada = a janela nao pode existir. Se sobrou uma viva de antes
            // (a pref caiu com o app rodando), derruba antes de qualquer outra coisa.
            if (!isProjectorEnabled(maskDisplayId)) {
                dismissProjectorForDisplay(maskDisplayId, "ENABLE_VIRTUAL_CLUSTER desligado");
            }
            if (!isProjectorEnabled(hudDisplayId)) {
                dismissProjectorForDisplay(hudDisplayId, "ENABLE_INSTRUMENT_PROJECTOR desligado");
            }

            // NOTE: no "already initialized, bail out" guard. The old one returned as soon as
            // *either* projector existed, so a re-init could never repair the missing one.
            // ensureProjectors() is idempotent - it skips whatever is already live or disabled -
            // so running it again is always safe and is the only way a half-built state recovers.

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

            if (dataChangedListenerRegistered) {
                return;
            }
            dataChangedListenerRegistered = true;

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

        // Repopula as receitas; quem decide o que de fato nasce e o initialize(), consultando as
        // preferencias (ver isProjectorEnabled).
        populateCreators();

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
     * already arrive on the main looper, so they can rebuild in place. Rebuilds go through
     * ensureProjectors(), which re-reads the preferences, so a projector switched off while its
     * display was away does not come back here.
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
