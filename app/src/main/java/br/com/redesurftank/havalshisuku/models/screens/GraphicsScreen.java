package br.com.redesurftank.havalshisuku.models.screens;

import android.util.Log;

import br.com.redesurftank.havalshisuku.managers.ServiceManager;
import br.com.redesurftank.havalshisuku.models.CarConstants;
import br.com.redesurftank.havalshisuku.models.MainUiManager;
import br.com.redesurftank.havalshisuku.models.ServiceManagerEventType;
import br.com.redesurftank.havalshisuku.models.SteeringWheelAcControlType;


public class GraphicsScreen implements Screen {

    private static final String TAG = "GraphicsScreen";


    public static class GraphOptions {
        public static final String EV_CONSUMPTION = "evConsumption";
        public static final String GAS_CONSUMPTION = "gasConsumption";
        public static final String CAR_SPEED = "carSpeed";
        public static final String EV_POWER_FACTOR = "evPowerFactor";
        public static final String EV_POWER_KW = "evPowerKw";
        public static final String GAS_CONSUMPTION_IDLE = "gasConsumptionIdle";
        public static final String GAS_CONSUMPTION_METRIC_IDLE = "gasConsumptionMetricIdle";
        public static final String GAS_CONSUMPTION_MODE = "gasConsumptionMode";
        public static final String FUEL_PERCENT = "fuelPercent";
        public static final String BATTERY_PERCENT = "batteryPercent";
        public static final String MASK_VISIBLE = "maskVisible";
        private static final String[] graphsValueMap = {EV_CONSUMPTION, GAS_CONSUMPTION, CAR_SPEED};
    }
    private int currentGraphIndex = 0;

    private ServiceManager serviceManager;
    private Screen previousScreen = this;

    @Override
    public String getJsName() {
        return "graph";
    }

    @Override
    public void processKey(Key key) {
        switch (key) {
            case UP:
                currentGraphIndex--;
                if (currentGraphIndex < 0) currentGraphIndex = GraphOptions.graphsValueMap.length - 1;
                break;
            case ENTER:
            case DOWN:
                currentGraphIndex++;
                if (currentGraphIndex >= GraphOptions.graphsValueMap.length) currentGraphIndex = 0;
                break;
            case BACK:
                MainUiManager.getInstance().updateScreen(previousScreen);
                break;
            case BACK_LONG:
                MainUiManager.getInstance().updateScreen(previousScreen);
                break;
        }

        String valueToSend = GraphOptions.graphsValueMap[currentGraphIndex];
        serviceManager.dispatchServiceManagerEvent(ServiceManagerEventType.GRAPH_SCREEN_NAVIGATION, valueToSend);
        Log.i(TAG, "Graph changed to screen: " + valueToSend);

    }

    @Override
    public void initialize() {
        this.serviceManager = ServiceManager.getInstance();
        serviceManager.dispatchServiceManagerEvent(ServiceManagerEventType.UPDATE_SCREEN,this);
    }

    @Override
    public void setReturnScreen(Screen previousScreen) {
        this.previousScreen = previousScreen;
    }


}
