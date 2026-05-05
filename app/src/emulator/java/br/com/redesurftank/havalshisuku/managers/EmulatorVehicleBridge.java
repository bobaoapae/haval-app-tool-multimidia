package br.com.redesurftank.havalshisuku.managers;

import android.car.Car;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import br.com.redesurftank.havalshisuku.models.CarConstants;

/**
 * Bridges the AAOS emulator's VHAL (Vehicle Hardware Abstraction Layer) properties —
 * exposed via Extended Controls → Car sensor data — to the app's ServiceManager data bus.
 *
 * On real hardware the IIntelligentVehicleControlService provides all car data.
 * On the emulator that service is absent, so this class uses CarPropertyManager to
 * subscribe to the same VHAL properties the emulator's Extended Controls panel writes.
 *
 * Property coverage:
 *   PERF_VEHICLE_SPEED  → CAR_BASIC_VEHICLE_SPEED  (m/s → km/h)
 *   ENGINE_RPM          → CAR_BASIC_ENGINE_SPEED
 *   CURRENT_GEAR        → CAR_BASIC_GEAR_STATUS   (VHAL gear → app gear code)
 *   IGNITION_STATE      → CAR_BASIC_ENGINE_STATE  (On/Start=active, else -1)
 *   PARKING_BRAKE_ON    → CAR_BASIC_EPB_STATE + CAR_BASIC_HAND_BRAKE_STATUS
 *   NIGHT_MODE          → (broadcast only — not a CarConstants key)
 *   EV_BATTERY_LEVEL    → CAR_EV_INFO_CUR_BATTERY_POWER_PERCENTAGE
 *   FUEL_LEVEL          → CAR_BASIC_REMAIN_FUEL_PERCENTAGE
 *   OUTSIDE_TEMPERATURE → CAR_BASIC_OUTSIDE_TEMP
 */
public class EmulatorVehicleBridge {

    private static final String TAG = "EmulatorVehicleBridge";

    // VHAL property IDs — verified against this AAOS build via get-carpropertyconfig
    private static final int PROP_VEHICLE_SPEED    = 0x11600207; // PERF_VEHICLE_SPEED        (m/s, float)
    private static final int PROP_ENGINE_RPM       = 0x11600201; // ENGINE_RPM                (rpm, float) — may be absent
    private static final int PROP_CURRENT_GEAR     = 0x11400400; // GEAR_SELECTION            (int)
    private static final int PROP_IGNITION_STATE   = 0x11400409; // IGNITION_STATE            (int)
    private static final int PROP_PARKING_BRAKE    = 0x11200402; // PARKING_BRAKE_ON          (bool/int)
    private static final int PROP_NIGHT_MODE       = 0x11200407; // NIGHT_MODE                (bool/int)
    private static final int PROP_EV_BATTERY       = 0x11600309; // EV_BATTERY_LEVEL          (Wh, float)
    private static final int PROP_EV_BATTERY_CAP   = 0x11600106; // INFO_EV_BATTERY_CAPACITY  (Wh, float)
    private static final int PROP_FUEL_LEVEL       = 0x11600307; // FUEL_LEVEL                (mL, float)
    private static final int PROP_FUEL_CAPACITY    = 0x11600104; // INFO_FUEL_CAPACITY        (mL, float)
    private static final int PROP_OUTSIDE_TEMP     = 0x11600703; // ENV_OUTSIDE_TEMPERATURE   (°C, float)

    // VHAL ignition state values
    private static final int IGNITION_LOCK       = 1;
    private static final int IGNITION_OFF        = 2;
    private static final int IGNITION_ACCESSORY  = 3;
    private static final int IGNITION_ON         = 4;
    private static final int IGNITION_START      = 5;

    // VHAL gear values (android.car.VehicleGear)
    private static final int GEAR_NEUTRAL = 1;
    private static final int GEAR_REVERSE = 2;
    private static final int GEAR_PARK    = 8;
    private static final int GEAR_DRIVE   = 4;

    private Car car;
    private CarPropertyManager carPropertyManager;
    private final Context context;
    private final ServiceManager serviceManager;
    private ScheduledExecutorService statusLogger;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // Capacities read at startup for percentage calculations
    private float fuelCapacityMl   = 50000f; // fallback: 50 L
    private float batteryCapacityWh = 40000f; // fallback: 40 kWh

    public EmulatorVehicleBridge(Context context) {
        this.context = context;
        this.serviceManager = ServiceManager.getInstance();
    }

    public void start() {
        Log.w(TAG, "Starting EmulatorVehicleBridge — connecting to Car API...");
        Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                (car, ready) -> mainHandler.post(() -> onCarReady(car, ready)));
    }

    private void onCarReady(Car car, boolean ready) {
        if (!ready) {
            Log.e(TAG, "Car service not ready — VHAL data unavailable");
            return;
        }
        this.car = car;
        try {
            carPropertyManager = (CarPropertyManager) car.getCarManager(Car.PROPERTY_SERVICE);
            if (carPropertyManager == null) {
                Log.e(TAG, "CarPropertyManager unavailable");
                return;
            }
            Log.w(TAG, "CarPropertyManager connected — registering VHAL listeners");
            registerListeners();
            injectCurrentValues();
            startStatusLogger();
        } catch (Exception e) {
            Log.e(TAG, "Failed to connect CarPropertyManager", e);
        }
    }

    private void registerListeners() {
        int[] props = {
            PROP_VEHICLE_SPEED, PROP_ENGINE_RPM, PROP_CURRENT_GEAR,
            PROP_IGNITION_STATE, PROP_PARKING_BRAKE, PROP_NIGHT_MODE,
            PROP_EV_BATTERY, PROP_FUEL_LEVEL, PROP_OUTSIDE_TEMP
        };
        CarPropertyManager.CarPropertyEventCallback callback = new CarPropertyManager.CarPropertyEventCallback() {
            @Override
            public void onChangeEvent(CarPropertyValue value) {
                handlePropertyChange(value);
            }
            @Override
            public void onErrorEvent(int propertyId, int zone) {
                Log.w(TAG, "VHAL error on property 0x" + Integer.toHexString(propertyId));
            }
        };
        for (int prop : props) {
            try {
                carPropertyManager.registerCallback(callback, prop,
                        CarPropertyManager.SENSOR_RATE_ONCHANGE);
                Log.d(TAG, "Registered listener for VHAL 0x" + Integer.toHexString(prop));
            } catch (Exception e) {
                Log.w(TAG, "Cannot register VHAL 0x" + Integer.toHexString(prop) + ": " + e.getMessage());
            }
        }
    }

    /** Reads current VHAL values once on startup so cluster shows real state immediately. */
    private void injectCurrentValues() {
        // Read capacities first so percentage translators have correct denominators
        try { float c = getFloatProperty(PROP_FUEL_CAPACITY);    if (c > 0) fuelCapacityMl    = c; } catch (Exception ignored) {}
        try { float c = getFloatProperty(PROP_EV_BATTERY_CAP);   if (c > 0) batteryCapacityWh = c; } catch (Exception ignored) {}
        Log.w(TAG, "Capacities — fuel: " + fuelCapacityMl + " mL, battery: " + batteryCapacityWh + " Wh");

        try { injectSpeed(getFloatProperty(PROP_VEHICLE_SPEED)); } catch (Exception ignored) {}
        try { injectRpm(getFloatProperty(PROP_ENGINE_RPM)); } catch (Exception ignored) {}
        try { injectGear(getIntProperty(PROP_CURRENT_GEAR)); } catch (Exception ignored) {}
        try { injectIgnition(getIntProperty(PROP_IGNITION_STATE)); } catch (Exception ignored) {}
        try { injectParkingBrake(getBoolProperty(PROP_PARKING_BRAKE)); } catch (Exception ignored) {}
        try { injectBattery(getFloatProperty(PROP_EV_BATTERY)); } catch (Exception ignored) {}
        try { injectFuel(getFloatProperty(PROP_FUEL_LEVEL)); } catch (Exception ignored) {}
        try { injectOutsideTemp(getFloatProperty(PROP_OUTSIDE_TEMP)); } catch (Exception ignored) {}
    }

    private void handlePropertyChange(CarPropertyValue<?> value) {
        int prop = value.getPropertyId();
        Object v = value.getValue();
        try {
            switch (prop) {
                case PROP_VEHICLE_SPEED:   injectSpeed(((Number) v).floatValue());   break;
                case PROP_ENGINE_RPM:      injectRpm(((Number) v).floatValue());     break;
                case PROP_CURRENT_GEAR:    injectGear(((Number) v).intValue());      break;
                case PROP_IGNITION_STATE:  injectIgnition(((Number) v).intValue());  break;
                case PROP_PARKING_BRAKE:   injectParkingBrake((Boolean) v);         break;
                case PROP_EV_BATTERY:      injectBattery(((Number) v).floatValue()); break;
                case PROP_FUEL_LEVEL:      injectFuel(((Number) v).floatValue());    break;
                case PROP_OUTSIDE_TEMP:    injectOutsideTemp(((Number) v).floatValue()); break;
                default: break;
            }
        } catch (Exception e) {
            Log.w(TAG, "Error handling VHAL 0x" + Integer.toHexString(prop) + ": " + e.getMessage());
        }
    }

    // ── Property translators ──────────────────────────────────────────────────

    private void injectSpeed(float mps) {
        float kmh = mps * 3.6f;
        serviceManager.injectData(CarConstants.CAR_BASIC_VEHICLE_SPEED.getValue(),
                String.format(Locale.US, "%.1f", kmh));
    }

    private void injectRpm(float rpm) {
        serviceManager.injectData(CarConstants.CAR_BASIC_ENGINE_SPEED.getValue(),
                String.format(Locale.US, "%.0f", rpm));
    }

    private void injectGear(int vhalGear) {
        // App gear codes: 2=D, 3=P, 4=R, else=N
        String appGear;
        switch (vhalGear) {
            case GEAR_PARK:    appGear = "3"; break;
            case GEAR_REVERSE: appGear = "4"; break;
            case GEAR_DRIVE:   appGear = "2"; break;
            case GEAR_NEUTRAL: appGear = "0"; break;  // anything non-2/3/4 → N
            default:           appGear = "0"; break;
        }
        serviceManager.injectData(CarConstants.CAR_BASIC_GEAR_STATUS.getValue(), appGear);
    }

    private void injectIgnition(int state) {
        // Engine ON when ignition is ON or START; otherwise -1 (screen off)
        boolean running = (state == IGNITION_ON || state == IGNITION_START);
        serviceManager.injectData(CarConstants.CAR_BASIC_ENGINE_STATE.getValue(),
                running ? "4" : "-1");
        Log.w(TAG, "Ignition state=" + state + " → engine_state=" + (running ? "ON" : "OFF"));
    }

    private void injectParkingBrake(boolean on) {
        String v = on ? "1" : "0";
        serviceManager.injectData(CarConstants.CAR_BASIC_EPB_STATE.getValue(), v);
        serviceManager.injectData(CarConstants.CAR_BASIC_HAND_BRAKE_STATUS.getValue(), v);
    }

    private void injectBattery(float wh) {
        // VHAL EV_BATTERY_LEVEL is in Wh; normalise against INFO_EV_BATTERY_CAPACITY.
        float pct = Math.min(100f, Math.max(0f, (wh / batteryCapacityWh) * 100f));
        serviceManager.injectData(
                CarConstants.CAR_EV_INFO_CUR_BATTERY_POWER_PERCENTAGE.getValue(),
                String.format(Locale.US, "%.0f", pct));
    }

    private void injectFuel(float mL) {
        // VHAL FUEL_LEVEL is in mL; normalise against INFO_FUEL_CAPACITY.
        float pct = Math.min(100f, Math.max(0f, (mL / fuelCapacityMl) * 100f));
        serviceManager.injectData(CarConstants.CAR_BASIC_REMAIN_FUEL_PERCENTAGE.getValue(),
                String.format(Locale.US, "%.0f", pct));
    }

    private void injectOutsideTemp(float celsius) {
        serviceManager.injectData(CarConstants.CAR_BASIC_OUTSIDE_TEMP.getValue(),
                String.format(Locale.US, "%.1f", celsius));
    }

    // ── Vehicle status logger ─────────────────────────────────────────────────

    /**
     * Logs the full vehicle state to logcat every 5 seconds under tag "VehicleStatus".
     * Useful for verifying that speed-triggered actions fire at the right thresholds.
     */
    private void startStatusLogger() {
        statusLogger = Executors.newSingleThreadScheduledExecutor();
        statusLogger.scheduleAtFixedRate(() -> {
            try {
                String speed    = serviceManager.getData(CarConstants.CAR_BASIC_VEHICLE_SPEED.getValue());
                String rpm      = serviceManager.getData(CarConstants.CAR_BASIC_ENGINE_SPEED.getValue());
                String gear     = serviceManager.getData(CarConstants.CAR_BASIC_GEAR_STATUS.getValue());
                String engine   = serviceManager.getData(CarConstants.CAR_BASIC_ENGINE_STATE.getValue());
                String epb      = serviceManager.getData(CarConstants.CAR_BASIC_EPB_STATE.getValue());
                String fuel     = serviceManager.getData(CarConstants.CAR_BASIC_REMAIN_FUEL_PERCENTAGE.getValue());
                String battery  = serviceManager.getData(CarConstants.CAR_EV_INFO_CUR_BATTERY_POWER_PERCENTAGE.getValue());
                String outTemp  = serviceManager.getData(CarConstants.CAR_BASIC_OUTSIDE_TEMP.getValue());

                // Translate gear code → label for readability
                String gearLabel = gearCodeToLabel(gear);

                Log.i("VehicleStatus",
                    "speed=" + nvl(speed) + "km/h"
                    + " | rpm=" + nvl(rpm)
                    + " | gear=" + gearLabel + "(" + nvl(gear) + ")"
                    + " | engine=" + nvl(engine)
                    + " | epb=" + nvl(epb)
                    + " | fuel=" + nvl(fuel) + "%"
                    + " | battery=" + nvl(battery) + "%"
                    + " | outTemp=" + nvl(outTemp) + "°C"
                );
            } catch (Exception e) {
                Log.w("VehicleStatus", "Logger error: " + e.getMessage());
            }
        }, 2, 5, TimeUnit.SECONDS);
        Log.w(TAG, "Vehicle status logger started (every 5s, tag: VehicleStatus)");
    }

    private static String gearCodeToLabel(String code) {
        if (code == null) return "?";
        switch (code) {
            case "2": return "D";
            case "3": return "P";
            case "4": return "R";
            default:  return "N";
        }
    }

    private static String nvl(String v) { return v != null ? v : "--"; }

    // ── CarPropertyManager helpers ────────────────────────────────────────────

    private float getFloatProperty(int propId) {
        CarPropertyValue<Float> v = carPropertyManager.getProperty(Float.class, propId, 0);
        return v != null ? v.getValue() : 0f;
    }

    private int getIntProperty(int propId) {
        CarPropertyValue<Integer> v = carPropertyManager.getProperty(Integer.class, propId, 0);
        return v != null ? v.getValue() : 0;
    }

    private boolean getBoolProperty(int propId) {
        CarPropertyValue<Boolean> v = carPropertyManager.getProperty(Boolean.class, propId, 0);
        return v != null && v.getValue();
    }

    public void stop() {
        if (statusLogger != null) {
            statusLogger.shutdownNow();
            statusLogger = null;
        }
        if (car != null) {
            try { car.disconnect(); } catch (Exception ignored) {}
            car = null;
        }
        Log.w(TAG, "EmulatorVehicleBridge stopped");
    }
}
