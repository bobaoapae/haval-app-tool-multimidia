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

    private final Map<Integer, BiConsumer<android.content.Context, Display>> projectorCreators = new HashMap<>();

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

    /** Receita de como criar cada projector. Usada pelo construtor e pelo refresh(). */
    private void populateCreators() {
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

            if (initialized && (instrumentProjector != null || instrumentProjector2 != null)) {
                Log.w(TAG, "ProjectorManager already initialized; skipping duplicate presentations");
                return;
            }

            displayManager = App.getContext().getSystemService(DisplayManager.class);

            for (Display display : displayManager.getDisplays()) {
                Log.w(TAG, "Display found: " + display.getName() + " (ID: " + display.getDisplayId() + ")");
            }

            Set<Integer> pending = new HashSet<>();

            for (Integer id : projectorCreators.keySet()) {
                // A pref e consultada ANTES de criar: o que esta desligado simplesmente nao nasce,
                // e nem entra na fila de espera do display (senao voltaria pelo listener).
                if (!isProjectorEnabled(id)) {
                    Log.w(TAG, "Projector for display " + id + " is disabled by preference; not creating it");
                    continue;
                }
                Display display = getDisplayById(id);
                if (display != null) {
                    projectorCreators.get(id).accept(App.getContext(), display);
                } else {
                    pending.add(id);
                }
            }

            if (!pending.isEmpty()) {
                registerDisplayListener(pending);
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

    private void registerDisplayListener(Set<Integer> pending) {
        DisplayManager.DisplayListener listener = new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
                Log.w(TAG, "Display added: " + displayId);
                if (pending.contains(displayId)) {
                    // O display pode aparecer muito depois; a pref pode ter caido nesse meio tempo.
                    // Sem esta checagem, a janela voltaria por aqui mesmo com a feature desligada.
                    BiConsumer<android.content.Context, Display> creator = projectorCreators.get(displayId);
                    if (creator == null || !isProjectorEnabled(displayId)) {
                        Log.w(TAG, "Display " + displayId + " appeared, but its projector is disabled/unknown; ignoring");
                        pending.remove(displayId);
                        if (pending.isEmpty()) {
                            displayManager.unregisterDisplayListener(this);
                        }
                        return;
                    }
                    Display display = displayManager.getDisplay(displayId);
                    if (display != null) {
                        creator.accept(App.getContext(), display);
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
