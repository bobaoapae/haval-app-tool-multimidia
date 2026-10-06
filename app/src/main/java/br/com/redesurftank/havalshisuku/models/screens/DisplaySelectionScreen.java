package br.com.redesurftank.havalshisuku.models.screens;

import br.com.redesurftank.havalshisuku.managers.ServiceManager;
import br.com.redesurftank.havalshisuku.models.MainUiManager;
import br.com.redesurftank.havalshisuku.models.ServiceManagerEventType;
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys;

public class DisplaySelectionScreen implements Screen {

    private static final String[] LEGACY_ITEM_IDS = {
            "mode_normal", "mode_reduzido", "mode_clean", "mode_mapa"
    };
    private static final String[] LEGACY_DISPLAYS = {"Normal", "Reduzido", "Clean", "Mapa"};

    private static final String[] SPORT_ITEM_IDS = {
            "mode_analogico",
            "mode_analogico_v2",
            "mode_digital",
            "mode_mapa",
            "mode_mapa_graduado",
            "mode_mapa_limpo"
    };
    private static final String[] SPORT_DISPLAYS = {
            "Normal", "Analógico V2", "Digital", "Mapa", "Mapa Graduado", "Mapa Limpo"
    };

    private ServiceManager serviceManager;
    private Screen previousScreen = this;
    private String[] itemIds = LEGACY_ITEM_IDS;
    private String[] displays = LEGACY_DISPLAYS;
    private int focusedTemplateIndex = 0;
    private int selectedDisplayIndex = 0;
    private boolean configuredForSportTheme = false;

    private void configureForActiveTheme() {
        String activeTheme = serviceManager.getSharedPreferences().getString(
                SharedPreferencesKeys.ACTIVE_CUSTOM_THEME.getKey(), "");
        boolean sportTheme = isSportTheme(activeTheme);
        configuredForSportTheme = sportTheme;
        itemIds = sportTheme ? SPORT_ITEM_IDS : LEGACY_ITEM_IDS;
        displays = sportTheme ? SPORT_DISPLAYS : LEGACY_DISPLAYS;
    }

    static boolean isSportTheme(String activeTheme) {
        return "SportRed".equalsIgnoreCase(activeTheme)
                || "SportRedLite".equalsIgnoreCase(activeTheme);
    }

    private int getDisplayIndex(String display) {
        for (int i = 0; i < displays.length; i++) {
            if (displays[i].equals(display)) {
                return i;
            }
        }
        return 0;
    }

    private String getCurrentDisplay() {
        return displays[selectedDisplayIndex];
    }

    private void persistAndDispatch() {
        String display = getCurrentDisplay();
        serviceManager.getSharedPreferences()
                .edit()
                .putString(SharedPreferencesKeys.CURRENT_CLUSTER_DISPLAY.getKey(), display)
                .apply();
        serviceManager.dispatchServiceManagerEvent(
                ServiceManagerEventType.DISPLAY_SCREEN_SELECTION,
                "control('display', '" + display + "')");
        // Re-aplica os bounds do AA no cluster: os 3 displays de mapa deslocam o AA 135px pra
        // direita automaticamente; os demais voltam a tela cheia. No-op se o AA nao estiver no D3.
        try {
            br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.INSTANCE
                    .reapplyAndroidAutoClusterBounds();
        } catch (Throwable ignored) {
        }
    }

    private void updateFocus() {
        serviceManager.dispatchServiceManagerEvent(
                ServiceManagerEventType.MENU_ITEM_NAVIGATION,
                itemIds[focusedTemplateIndex]);
    }

    private void exitLegacyCleanIfNeeded() {
        if (displays == LEGACY_DISPLAYS && "Clean".equals(getCurrentDisplay())) {
            selectedDisplayIndex = 0;
            persistAndDispatch();
        }
    }

    @Override
    public String getJsName() {
        return "display_selection";
    }

    @Override
    public void processKey(Key key) {
        String activeTheme = serviceManager.getSharedPreferences().getString(
                SharedPreferencesKeys.ACTIVE_CUSTOM_THEME.getKey(), "");
        if (configuredForSportTheme != isSportTheme(activeTheme)) {
            initialize();
        }
        switch (key) {
            case DOWN:
                focusedTemplateIndex = (focusedTemplateIndex + 1) % itemIds.length;
                updateFocus();
                exitLegacyCleanIfNeeded();
                break;
            case UP:
                focusedTemplateIndex =
                        (focusedTemplateIndex - 1 + itemIds.length) % itemIds.length;
                updateFocus();
                exitLegacyCleanIfNeeded();
                break;
            case ENTER:
                if (displays == LEGACY_DISPLAYS
                        && "Clean".equals(displays[focusedTemplateIndex])
                        && selectedDisplayIndex == focusedTemplateIndex) {
                    selectedDisplayIndex = 0;
                } else {
                    selectedDisplayIndex = focusedTemplateIndex;
                }
                persistAndDispatch();
                break;
            case BACK:
            case BACK_LONG:
                exitLegacyCleanIfNeeded();
                MainUiManager.getInstance().updateScreen(previousScreen);
                break;
            default:
                exitLegacyCleanIfNeeded();
                break;
        }
    }

    @Override
    public void initialize() {
        serviceManager = ServiceManager.getInstance();
        configureForActiveTheme();
        String savedDisplay = serviceManager.getSharedPreferences().getString(
                SharedPreferencesKeys.CURRENT_CLUSTER_DISPLAY.getKey(), "Normal");
        selectedDisplayIndex = getDisplayIndex(savedDisplay);
        focusedTemplateIndex = selectedDisplayIndex;
        serviceManager.dispatchServiceManagerEvent(ServiceManagerEventType.UPDATE_SCREEN, this);
        persistAndDispatch();
        updateFocus();
    }

    @Override
    public void setReturnScreen(Screen previousScreen) {
        this.previousScreen = previousScreen;
    }
}
