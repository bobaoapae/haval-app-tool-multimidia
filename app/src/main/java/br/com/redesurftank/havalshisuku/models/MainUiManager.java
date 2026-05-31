package br.com.redesurftank.havalshisuku.models;

import android.content.SharedPreferences;

import br.com.redesurftank.havalshisuku.managers.ServiceManager;
import br.com.redesurftank.havalshisuku.models.screens.AcControlScreen;
import br.com.redesurftank.havalshisuku.models.screens.MainMenu;
import br.com.redesurftank.havalshisuku.models.screens.Screen;

public class MainUiManager {

    // These fields are not declared in the original file. I'm declaring them here to make the code compile.
    private SharedPreferences sharedPreferences;

    private static volatile MainUiManager INSTANCE;

    // Estado atual da tela (MainMenu ou um sub-menu)
    private Screen currentScreenCard1;
    private Screen currentScreenCard3;

    private int currentCard = 1;


    private MainUiManager() {
        MainMenu initialMenu = new MainMenu();
        initialMenu.initialize();
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
        if (this.currentCard == 1) this.currentScreenCard1.processKey(key);
        else if (this.currentCard == 3) this.currentScreenCard3.processKey(key);
    }

}
