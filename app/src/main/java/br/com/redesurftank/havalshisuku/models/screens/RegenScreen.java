package br.com.redesurftank.havalshisuku.models.screens;

import android.util.Log;

import java.util.Arrays;
import java.util.Objects;

import br.com.redesurftank.havalshisuku.managers.ServiceManager;
import br.com.redesurftank.havalshisuku.models.CarConstants;
import br.com.redesurftank.havalshisuku.models.MainUiManager;
import br.com.redesurftank.havalshisuku.models.ServiceManagerEventType;
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys;
import br.com.redesurftank.havalshisuku.models.SteeringWheelAcControlType;


public class RegenScreen implements Screen {

    private static final String TAG = "RegenScreen";


    public static class RegenOptions {
        public static final int NORMAL = 2;
        public static final int MEDIUM = 0;
        public static final int HIGH = 1;
        public static final String REGEN_GRAPH_STATE_NAME = "lastRegenValue";

        public static String getLabel(String value) {
            int val = Integer.parseInt(value);
            switch (val) {
                case 2: return "Baixo";
                case 0: return "Normal";
                case 1: return "Alto";
            }
            return "--";
        }
    }
    private static final int[] regenValueMap = {RegenOptions.NORMAL, RegenOptions.MEDIUM, RegenOptions.HIGH};
    private int currentRegenIndex = 0;
    private boolean isOnePedalEnabled = false;

    private ServiceManager serviceManager;
    private Screen previousScreen = this;

    @Override
    public String getJsName() {
        return "regen";
    }

    @Override
    public void processKey(Key key) {
        String valueToSend;
        switch (key) {
            case ENTER: // Enter
                currentRegenIndex = 1;
                valueToSend = Integer.toString(regenValueMap[currentRegenIndex]);
                serviceManager.updateData(CarConstants.CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL.getValue(), valueToSend);
                Log.i(TAG, "Regen state set to value: " + valueToSend);
                break;
            case UP:
                if (currentRegenIndex < regenValueMap.length - 1) currentRegenIndex++;
                valueToSend = Integer.toString(regenValueMap[currentRegenIndex]);
                serviceManager.updateData(CarConstants.CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL.getValue(), valueToSend);
                Log.i(TAG, "Regen state set to value: " + valueToSend);
                break;
            case DOWN:
                if (currentRegenIndex > 0) currentRegenIndex--;
                valueToSend = Integer.toString(regenValueMap[currentRegenIndex]);
                serviceManager.updateData(CarConstants.CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL.getValue(), valueToSend);
                Log.i(TAG, "Regen state set to value: " + valueToSend);
                break;
            case BACK:
            case BACK_LONG:
                MainUiManager.getInstance().updateScreen(previousScreen);
                break;
            case ENTER_LONG:
                isOnePedalEnabled = !isOnePedalEnabled;
                serviceManager.updateData(CarConstants.CAR_CONFIGURE_PEDAL_CONTROL_ENABLE.getValue(), isOnePedalEnabled ? "1" : "0");
                Log.w(TAG, "One Pedal Driving state changed to: " + isOnePedalEnabled);
                break;
        }

    }

    @Override
    public void initialize() {
        this.serviceManager = ServiceManager.getInstance();
        String regenFromCar = ServiceManager.getInstance().getData(CarConstants.CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL.getValue());
        this.currentRegenIndex = findIndexFromValue(Integer.parseInt(regenFromCar));
        String onePedalFromCar = ServiceManager.getInstance().getData(CarConstants.CAR_CONFIGURE_PEDAL_CONTROL_ENABLE.getValue());
        if (onePedalFromCar != null) this.isOnePedalEnabled = onePedalFromCar.equals("1");
        else this.isOnePedalEnabled = false;
        serviceManager.dispatchServiceManagerEvent(ServiceManagerEventType.UPDATE_SCREEN,this);
    }

    public int findIndexFromValue(int carValue) {
        for (int i = 0; i < regenValueMap.length; i++) {
            if (regenValueMap[i] == carValue) {
                return i;
            }
        }
        return 0;
    }

    @Override
    public void setReturnScreen(Screen previousScreen) {
        this.previousScreen = previousScreen;
    }


}
