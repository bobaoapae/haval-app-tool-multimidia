package br.com.redesurftank.havalshisuku.models.screens;

import br.com.redesurftank.havalshisuku.managers.ServiceManager;
import br.com.redesurftank.havalshisuku.models.MainUiManager;
import br.com.redesurftank.havalshisuku.models.ServiceManagerEventType;
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys;

public class DisplaySelectionScreen implements Screen {

    private static final String TAG = "DisplaySelectionScreen";

    private ServiceManager serviceManager;
    private Screen previousScreen = this;
    
    // The items mirror the frontend displaySelection.js
    private static final String[] ITEM_IDS = {
        "mode_normal",
        "mode_esportivo",
        "mode_reduzido",
        "mode_clean"
    };
    private int focusedTemplateIndex = 1; // default focus on Esportivo
    private int focusedDisplayIndex = 1; // default selected display is Esportivo

    private static final String[] DISPLAYS = {"Normal", "Esportivo", "Reduzido", "Clean"};

    private int getDisplayIndex(String display) {
        for (int i = 0; i < DISPLAYS.length; i++) {
            if (DISPLAYS[i].equals(display)) {
                return i;
            }
        }
        return 0;
    }

    private String getCurrentDisplay() {
        return DISPLAYS[focusedDisplayIndex];
    }

    private void persistCurrentDisplay() {
        serviceManager.getSharedPreferences()
                .edit()
                .putString(SharedPreferencesKeys.CURRENT_CLUSTER_DISPLAY.getKey(), getCurrentDisplay())
                .apply();
    }

    private void dispatchCurrentDisplay() {
        serviceManager.dispatchServiceManagerEvent(
                ServiceManagerEventType.DISPLAY_SCREEN_SELECTION,
                "control('display', '" + getCurrentDisplay() + "')"
        );
    }

    @Override
    public String getJsName() {
        return "display_selection";
    }

    @Override
    public void processKey(Key key) {
        switch (key) {
            case DOWN:
                focusedTemplateIndex++;
                if (focusedTemplateIndex >= ITEM_IDS.length) {
                    focusedTemplateIndex = 0;
                }
                updateFocus();
                handleSelection(key);
                break;
            case UP:
                focusedTemplateIndex--;
                if (focusedTemplateIndex < 0) {
                    focusedTemplateIndex = ITEM_IDS.length - 1;
                }
                updateFocus();
                handleSelection(key);
                break;
            case BACK:
            case BACK_LONG:
                handleSelection(key);
                MainUiManager.getInstance().updateScreen(previousScreen);
                break;
            default:
                handleSelection(key);
                break;
        }
    }

    private void updateFocus() {
        serviceManager.dispatchServiceManagerEvent(ServiceManagerEventType.MENU_ITEM_NAVIGATION, ITEM_IDS[focusedTemplateIndex]);
    }

    private void handleSelection(Key key) {
        String selectedId = ITEM_IDS[focusedTemplateIndex];
        if (key == Key.ENTER) {
            switch (selectedId) {
                case "mode_normal":
                    focusedDisplayIndex = 0;
                    break;
                case "mode_esportivo":
                    focusedDisplayIndex = 1;
                    break;
                case "mode_reduzido":
                    focusedDisplayIndex = 2;
                    break;
                case "mode_clean":
                    if (focusedDisplayIndex == 3) { // if already in clean mode, exit
                        focusedDisplayIndex = 0;
                    } else {
                        focusedDisplayIndex = 3;
                    }
                    break;
            }
            persistCurrentDisplay();
            dispatchCurrentDisplay();
        } else {
            // if in clean mode, any key exits
            if (focusedDisplayIndex == 3) {
                focusedDisplayIndex = 0;
                persistCurrentDisplay();
                dispatchCurrentDisplay();

            }
        }
    }

    @Override
    public void initialize() {
        this.serviceManager = ServiceManager.getInstance();
        String savedDisplay = serviceManager.getSharedPreferences().getString(
                SharedPreferencesKeys.CURRENT_CLUSTER_DISPLAY.getKey(),
                DISPLAYS[focusedDisplayIndex]
        );
        focusedDisplayIndex = getDisplayIndex(savedDisplay);
        focusedTemplateIndex = focusedDisplayIndex;
        serviceManager.dispatchServiceManagerEvent(ServiceManagerEventType.UPDATE_SCREEN, this);
        dispatchCurrentDisplay();
        updateFocus();
    }

    @Override
    public void setReturnScreen(Screen previousScreen) {
        this.previousScreen = previousScreen;
    }
}
