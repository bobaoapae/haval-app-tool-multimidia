package br.com.redesurftank.havalshisuku.models.screens;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import br.com.redesurftank.havalshisuku.managers.ServiceManager;
import br.com.redesurftank.havalshisuku.models.CarConstants;
import br.com.redesurftank.havalshisuku.models.MainUiManager;
import br.com.redesurftank.havalshisuku.models.ServiceManagerEventType;
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys;

public class MainMenu implements Screen {
    private ServiceManager serviceManager;

    private int currentMenuItemIndex = 0;

    private Screen previousScreen = this;

    private List<MenuItem> menuItems;

    // Car related control values, these should match the car constants in the server
    public static class EspOptions {
        public static final int ON = 1;
        public static final int OFF = 0;
        public static String getLabel(String value) {
            int val = Integer.parseInt(value);
            switch (val) {
                case 1: return "ON";
                case 0: return "OFF";
            }
            return "--";
        }
    }

    public static class EvModeOptions {
        public static final int HEV = 0;
        public static final int EVP = 1;
        public static final int EV = 3;

        public static String getLabel(String value) {
            int val = Integer.parseInt(value);
            switch (val) {
                case 0:
                    return "HEV";
                case 1:
                    return "EVP";
                case 3:
                    return "EV";
            }
            return "--";
        }
    }

    public static class DrivingModeOptions {
        public static final int NORMAL = 0;
        public static final int ECO = 2;
        public static final int SPORT = 1;
        public static String getLabel(String value) {
            int val = Integer.parseInt(value);
            switch (val) {
                case 0: return "Normal";
                case 1: return "Sport";
                case 2: return "Eco";
                case 3: return "Neve";
                case 4: return "Areia";
                case 5: return "Lama";
                case 11: return "AWD";
            }
            return "--";
        }
    }

    public static class SteerModeOptions {
        public static final int COMFORT = 2;
        public static final int NORMAL = 0;
        public static final int SPORT = 1;
        public static String getLabel(String value) {
            int val = Integer.parseInt(value);
            switch (val) {
                case 2: return "Conforto";
                case 0: return "Normal";
                case 1: return "Esportiva";
            }
            return "--";
        }
    }

    @Override
    public String getJsName() {
        return "main_menu";
    }

    @Override
    public void initialize() {

        if (this.serviceManager == null) this.serviceManager = ServiceManager.getInstance();

        if (menuItems == null) {
            Screen acControlScreen = new AcControlScreen();
            acControlScreen.setReturnScreen(this);
            Screen regenScreen = new RegenScreen();
            regenScreen.setReturnScreen(this);
            acControlScreen.initialize();
            regenScreen.initialize();
            Screen graphScreen = new GraphicsScreen();
            graphScreen.setReturnScreen(this);
            graphScreen.initialize();
            Screen displaySelectionScreen = new DisplaySelectionScreen();
            displaySelectionScreen.setReturnScreen(this);
            displaySelectionScreen.initialize();
            Screen mediaScreen = new MediaScreen();
            mediaScreen.setReturnScreen(this);
            mediaScreen.initialize();

            // Define menu structure and options available
            menuItems = Arrays.asList(
                    new MenuItem(
                            MenuItem.MENU_ID_ESP,
                            new MenuAction.CycleValues(Arrays.asList(EspOptions.ON, EspOptions.OFF),
                            CarConstants.CAR_DRIVE_SETTING_ESP_ENABLE)
                    ),
                    new MenuItem(
                            MenuItem.MENU_ID_EVMODE,
                            new MenuAction.CycleValues(Arrays.asList(EvModeOptions.EV, EvModeOptions.EVP, EvModeOptions.HEV),
                            CarConstants.CAR_EV_SETTING_POWER_MODEL_CONFIG)
                    ),
                    new MenuItem(
                            MenuItem.MENU_ID_DRIVING_MODE,
                            new MenuAction.CycleValues(Arrays.asList(DrivingModeOptions.NORMAL, DrivingModeOptions.ECO, DrivingModeOptions.SPORT),
                                    CarConstants.CAR_DRIVE_SETTING_DRIVE_MODE)
                    ),
                    new MenuItem(
                            MenuItem.MENU_ID_STATS,
                            new MenuAction.NavigateTo(graphScreen)
                    ),
                    new MenuItem(
                            MenuItem.MENU_ID_STEER_MODE,
                            new MenuAction.CycleValues(Arrays.asList(SteerModeOptions.COMFORT, SteerModeOptions.NORMAL, SteerModeOptions.SPORT),
                            CarConstants.CAR_DRIVE_SETTING_STEERING_WHEEL_ASSIST_MODE)
                    ),
                    new MenuItem(
                            MenuItem.MENU_ID_REGENERATION_MODE,
                            new MenuAction.NavigateTo(regenScreen)
                    ),
                    new MenuItem(
                            MenuItem.MENU_ID_AC_CONTROL,
                            new MenuAction.NavigateTo(displaySelectionScreen)
                    ),
                    new MenuItem(
                            MenuItem.MENU_ID_MEDIA,
                            new MenuAction.NavigateTo(mediaScreen)
                    )
            );
        }

        // Send update event to make sure screen is displayed
        serviceManager.dispatchServiceManagerEvent(br.com.redesurftank.havalshisuku.models.ServiceManagerEventType.UPDATE_SCREEN, this);

        // Set default initial position as middle of the menu
        String lastMenuOption = ServiceManager.getInstance().getSharedPreferences().getString(
                SharedPreferencesKeys.LAST_CLUSTER_MENU_ITEM.getKey(), "option_7");
        this.currentMenuItemIndex = IntStream.range(0, menuItems.size())
                .filter(i -> menuItems.get(i).getId().equals(lastMenuOption))
                .findFirst()
                .orElse(menuItems.size() / 2);
        serviceManager.dispatchServiceManagerEvent(ServiceManagerEventType.MENU_ITEM_NAVIGATION, menuItems.get(currentMenuItemIndex).getId());

    }

    public Screen setInitialScreen(MainUiManager uiManager) {
        String lastScreenKey = ServiceManager.getInstance().getSharedPreferences().getString(SharedPreferencesKeys.LAST_CLUSTER_SCREEN.getKey(), "main_menu");
        Screen lastScreen = this;
        if (!lastScreenKey.equals("main_menu")) {
            try {
                lastScreen = ((MenuAction.NavigateTo)menuItems.get(currentMenuItemIndex).getAction()).getScreen();
            } catch (Exception e) {
                e.printStackTrace();
            }
            uiManager.updateScreen(lastScreen);
}
        return lastScreen;
    }

    @Override
    public void setReturnScreen(Screen previousScreen) {
        this.previousScreen = previousScreen;
    }

    public void processKey(Key key) {
        switch (key) {
            case UP: // Up
                currentMenuItemIndex--;
                if (currentMenuItemIndex < 0) {
                    currentMenuItemIndex = menuItems.size() - 1;
                }
                serviceManager.dispatchServiceManagerEvent(ServiceManagerEventType.MENU_ITEM_NAVIGATION, menuItems.get(currentMenuItemIndex).getId());
                serviceManager.getSharedPreferences().edit().putString(SharedPreferencesKeys.LAST_CLUSTER_MENU_ITEM.getKey(), menuItems.get(currentMenuItemIndex).getId()).apply();

                break;

            case DOWN: // Down
                currentMenuItemIndex++;
                currentMenuItemIndex = currentMenuItemIndex % menuItems.size();
                serviceManager.dispatchServiceManagerEvent(ServiceManagerEventType.MENU_ITEM_NAVIGATION, menuItems.get(currentMenuItemIndex).getId());
                serviceManager.getSharedPreferences().edit().putString(SharedPreferencesKeys.LAST_CLUSTER_MENU_ITEM.getKey(), menuItems.get(currentMenuItemIndex).getId()).apply();
                break;

            case ENTER: // Enter
                MenuAction action = menuItems.get(currentMenuItemIndex).getAction();
                if (menuItems.get(currentMenuItemIndex).getAction() instanceof MenuAction.NavigateTo) {
                    MainUiManager.getInstance().updateScreen(((MenuAction.NavigateTo) action).getScreen());
                } else if (action instanceof MenuAction.CycleValues) {
                    serviceManager.updateData(((MenuAction.CycleValues) action).carOptionID.getValue(), ((MenuAction.CycleValues) action).cycleNext());
                }
                break;
        }
    }

    // Interface base para as ações do menu
    private interface MenuAction {
        class NavigateTo implements MenuAction {
            private final Screen screen;
            public NavigateTo(Screen screen) { this.screen = screen; }
            public Screen getScreen() { return screen; }
        }

        class CycleValues implements MenuAction {
            private final List<Object> values;
            private int currentOptionIndex;
            private final CarConstants carOptionID;

            public CycleValues(List<Object> values, CarConstants carOptionID) {
                this.values = values;
                String fromCar = ServiceManager.getInstance().getData(carOptionID.getValue());
                this.currentOptionIndex = this.values.indexOf(Integer.parseInt(fromCar));
                if (this.currentOptionIndex == -1) this.currentOptionIndex = 0;
                this.carOptionID = carOptionID;
            }

            public String cycleNext() {
                if (!values.isEmpty()) {
                    this.currentOptionIndex = (this.currentOptionIndex + 1) % values.size();
                }
                return getCurrentValue();
            }

            public String getCurrentValue() {
                if (!values.isEmpty()) {
                    Object currentValue = values.get(currentOptionIndex);
                    if (currentValue instanceof Integer) {
                        return currentValue.toString();
                    }
                }
                return "";
            }

            public CarConstants getCarOptionID() {
                return carOptionID;
            }
        }
    }

    private static class MenuItem {
        public static final String MENU_ID_ESP = "option_1";
        public static final String MENU_ID_EVMODE = "option_2";
        public static final String MENU_ID_DRIVING_MODE = "option_3";
        public static final String MENU_ID_AC_CONTROL = "option_4";
        public static final String MENU_ID_STEER_MODE = "option_5";
        public static final String MENU_ID_REGENERATION_MODE = "option_6";
        public static final String MENU_ID_STATS = "option_7";
        public static final String MENU_ID_MEDIA = "option_8";
        private final String id;
        private final MenuAction action;

        public MenuItem(String id, MenuAction action) {
            this.id = id;
            this.action = action;
        }

        public String getId() { return id; }
        public MenuAction getAction() { return action; }
    }
}