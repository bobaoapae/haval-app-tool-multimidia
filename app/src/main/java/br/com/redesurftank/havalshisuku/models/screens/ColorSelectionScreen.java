package br.com.redesurftank.havalshisuku.models.screens;

import br.com.redesurftank.havalshisuku.managers.ServiceManager;
import br.com.redesurftank.havalshisuku.models.MainUiManager;
import br.com.redesurftank.havalshisuku.models.ServiceManagerEventType;
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys;

/** Native steering-wheel menu for the seven palettes implemented by the Sport themes. */
public class ColorSelectionScreen implements Screen {
    private static final String[] ITEM_IDS = {
            "color_red_sport",
            "color_red_gt",
            "color_ocean_blue",
            "color_green",
            "color_dark_blue",
            "color_amber_gold",
            "color_purple_gt"
    };
    private static final String[] COLORS = {
            "Red Sport", "Red GT", "Ocean Blue", "Green", "Dark Blue", "Amber Gold", "Purple GT"
    };

    private ServiceManager serviceManager;
    private Screen previousScreen = this;
    private int focusedColorIndex = 0;

    static String normalizeColor(String color) {
        if (color != null) {
            for (String allowed : COLORS) {
                if (allowed.equals(color)) {
                    return allowed;
                }
            }
        }
        return "Red Sport";
    }

    private int getColorIndex(String color) {
        String normalized = normalizeColor(color);
        for (int i = 0; i < COLORS.length; i++) {
            if (COLORS[i].equals(normalized)) {
                return i;
            }
        }
        return 0;
    }

    private void persistAndDispatch() {
        String color = COLORS[focusedColorIndex];
        serviceManager.getSharedPreferences()
                .edit()
                .putString(SharedPreferencesKeys.CURRENT_CLUSTER_COLOR.getKey(), color)
                .apply();
        serviceManager.dispatchServiceManagerEvent(
                ServiceManagerEventType.DISPLAY_SCREEN_SELECTION,
                "control('colorTheme', '" + color + "')");
    }

    private void updateFocus() {
        serviceManager.dispatchServiceManagerEvent(
                ServiceManagerEventType.MENU_ITEM_NAVIGATION,
                ITEM_IDS[focusedColorIndex]);
    }

    @Override
    public String getJsName() {
        return "color_selection";
    }

    @Override
    public void initialize() {
        serviceManager = ServiceManager.getInstance();
        focusedColorIndex = getColorIndex(
                serviceManager.getSharedPreferences().getString(
                        SharedPreferencesKeys.CURRENT_CLUSTER_COLOR.getKey(), "Red Sport"));
        serviceManager.dispatchServiceManagerEvent(ServiceManagerEventType.UPDATE_SCREEN, this);
        persistAndDispatch();
        updateFocus();
    }

    @Override
    public void processKey(Key key) {
        switch (key) {
            case DOWN:
                focusedColorIndex = (focusedColorIndex + 1) % ITEM_IDS.length;
                updateFocus();
                break;
            case UP:
                focusedColorIndex = (focusedColorIndex - 1 + ITEM_IDS.length) % ITEM_IDS.length;
                updateFocus();
                break;
            case ENTER:
                persistAndDispatch();
                break;
            case BACK:
            case BACK_LONG:
                MainUiManager.getInstance().updateScreen(previousScreen);
                break;
            default:
                break;
        }
    }

    @Override
    public void setReturnScreen(Screen previousScreen) {
        this.previousScreen = previousScreen;
    }
}
