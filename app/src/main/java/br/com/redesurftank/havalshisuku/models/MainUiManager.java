package br.com.redesurftank.havalshisuku.models;

import android.content.SharedPreferences;

import br.com.redesurftank.havalshisuku.managers.ServiceManager;
import br.com.redesurftank.havalshisuku.models.screens.AcControlScreen;
import br.com.redesurftank.havalshisuku.models.screens.MainMenu;
import br.com.redesurftank.havalshisuku.models.screens.Screen;

public class MainUiManager {

    private SharedPreferences sharedPreferences;

    private static volatile MainUiManager INSTANCE;

    // Estado atual da tela (MainMenu ou um sub-menu)
    private Screen currentScreenCard1;
    private Screen currentScreenCard3;
    // Kept around so HOME can always navigate the user back to the top-level
    // menu regardless of how deep into a sub-screen they currently are.
    private MainMenu rootMenuCard1;

    private int currentCard = 1;


    private MainUiManager() {
        MainMenu initialMenu = new MainMenu();
        initialMenu.initialize();
        this.rootMenuCard1 = initialMenu;
        Screen acScreen = new AcControlScreen();
        acScreen.initialize();
        this.currentScreenCard1 = initialMenu.setInitialScreen(this);
        this.currentScreenCard3 = acScreen;
        this.sharedPreferences = ServiceManager.getInstance().getSharedPreferences();
    }

    public void updateScreen() {
        if (this.currentCard == 1) {
            this.currentScreenCard1.initialize();
            if (sharedPreferences != null)
                sharedPreferences.edit().putString(SharedPreferencesKeys.LAST_CLUSTER_SCREEN.getKey(), this.currentScreenCard1.getJsName()).apply();
        } else if (this.currentCard == 3) {
            this.currentScreenCard3.initialize();
        }
    }

    public void updateScreen(Screen newScreen) {
        newScreen.initialize();
        if (this.currentCard == 1) {
            this.currentScreenCard1 = newScreen;
            if (sharedPreferences != null)
                sharedPreferences.edit().putString(SharedPreferencesKeys.LAST_CLUSTER_SCREEN.getKey(), this.currentScreenCard1.getJsName()).apply();
        } else if (this.currentCard == 3) {
            this.currentScreenCard3 = newScreen;
        }
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

    public void handleCardChange(int cardID) {
        this.currentCard = cardID;
        updateScreen();
    }

    public void handleGeneralKeyEvents(Screen.Key key) {
        // HOME is universal: always pops you back to the top-level menu on
        // card 1, regardless of how deep into a sub-screen you currently are.
        // No screen's processKey() implements HOME today, so without this
        // intercept the key would be a no-op once the user enters a submenu.
        if (key == Screen.Key.HOME && this.currentCard == 1 && this.rootMenuCard1 != null) {
            updateScreen(this.rootMenuCard1);
            return;
        }
        if (this.currentCard == 1) this.currentScreenCard1.processKey(key);
        else if (this.currentCard == 3) this.currentScreenCard3.processKey(key);
    }

}
