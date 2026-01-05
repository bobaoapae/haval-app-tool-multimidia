package br.com.redesurftank.havalshisuku.models;

import android.content.SharedPreferences;
import android.util.Log;
import br.com.redesurftank.havalshisuku.listeners.IServiceManagerEvent;
import br.com.redesurftank.havalshisuku.models.ServiceManagerEventType;
import br.com.redesurftank.havalshisuku.managers.ServiceManager;
import br.com.redesurftank.havalshisuku.models.screens.MainMenu;
import br.com.redesurftank.havalshisuku.models.screens.Screen;

public class MainUiManager implements IServiceManagerEvent {

    // These fields are not declared in the original file. I'm declaring them here to make the code compile.
    private SharedPreferences sharedPreferences;

    private static volatile MainUiManager INSTANCE;

    // Estado atual da tela (MainMenu ou um sub-menu)
    private Screen currentScreen;

    private MainUiManager() {
        MainMenu initialMenu = new MainMenu();
        initialMenu.initialize();
        this.currentScreen = initialMenu.setInitialScreen(this);
        this.sharedPreferences = ServiceManager.getInstance().getSharedPreferences();
        ServiceManager.getInstance().addServiceManagerEventListener(this);
    }

    public void updateScreen() {
        this.currentScreen.initialize();
        if (sharedPreferences != null) sharedPreferences.edit().putString(SharedPreferencesKeys.LAST_CLUSTER_SCREEN.getKey(), this.currentScreen.getJsName()).apply();
    }

    public void updateScreen(Screen newScreen) {
        newScreen.initialize();
        this.currentScreen = newScreen;
        if (sharedPreferences != null) sharedPreferences.edit().putString(SharedPreferencesKeys.LAST_CLUSTER_SCREEN.getKey(), this.currentScreen.getJsName()).apply();
    }

    public static MainUiManager getInstance() {
        if (INSTANCE == null) {
            synchronized (MainUiManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new MainUiManager();
                }
            }
        }
        return INSTANCE;
    }



    public void handleGeneralKeyEvents(Screen.Key key) {
        this.currentScreen.processKey(key);
    }

    @Override
    public void onEvent(ServiceManagerEventType event, Object... args) {
        if (event == ServiceManagerEventType.SAVE_SOC_IMPULSE_CHANGED) {
            Log.w("SOCIMPULSE", "SAVE_SOC_IMPULSE_CHANGED received in MainUiManager");

            // Re-render current screen to reflect updated SOC Impulse state
            if (currentScreen != null) {
                currentScreen.initialize();
            }
        }
    }
}
