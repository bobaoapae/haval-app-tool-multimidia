package br.com.redesurftank.havalshisuku.managers;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.IConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

import com.autolink.cluster.ClusterMsgData;
import com.autolink.clusterservice.IClusterCallback;
import com.autolink.clusterservice.IClusterService;
import com.beantechs.inputservice.IInputListener;
import com.beantechs.inputservice.IInputService;
import com.beantechs.intelligentvehiclecontrol.IIntelligentVehicleControlService;
import com.beantechs.intelligentvehiclecontrol.sdk.IListener;
import com.beantechs.voice.adapter.IBinderPool;
import com.beantechs.voice.adapter.IDvr;
import com.beantechs.voice.adapter.IVehicle;
import com.beantechs.voice.adapter.IVehicleModel;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import br.com.redesurftank.App;
import br.com.redesurftank.havalshisuku.listeners.IDataChanged;
import br.com.redesurftank.havalshisuku.listeners.IServiceManagerEvent;
import br.com.redesurftank.havalshisuku.models.CarConstants;
import br.com.redesurftank.havalshisuku.models.CarInfo;
import br.com.redesurftank.havalshisuku.models.ServiceManagerEventType;
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys;
import br.com.redesurftank.havalshisuku.models.SteeringWheelCustomActionType;
import br.com.redesurftank.havalshisuku.models.MainUiManager;
import br.com.redesurftank.havalshisuku.models.screens.Screen;
import br.com.redesurftank.havalshisuku.utils.FridaUtils;
import br.com.redesurftank.havalshisuku.utils.ShizukuUtils;
import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuBinderWrapper;

@SuppressLint("PrivateApi")
public class ServiceManager {
    private static final String TAG = "ServiceManager";
    public static final CarConstants[] DEFAULT_KEYS = {
            CarConstants.CAR_BASIC_ACCUMULATED_DIRVETIME,
            CarConstants.CAR_BASIC_GEAR_STATUS,
            CarConstants.CAR_BASIC_DOOR_STATUS,
            CarConstants.CAR_BASIC_DOOR_LOCK_STATUS,
            CarConstants.CAR_BASIC_DRIVING_READY_STATE,
            CarConstants.CAR_BASIC_INSIDE_TEMP,
            CarConstants.CAR_BASIC_MAINTENANCE_WARNING,
            CarConstants.CAR_BASIC_MAINTENANCE_WARNING_MILEAGE,
            CarConstants.CAR_BASIC_OUTSIDE_TEMP,
            CarConstants.CAR_BASIC_STEERING_RESET_REMIND_ENABLE,
            CarConstants.CAR_BASIC_STEERING_WHEEL_ANGLE,
            CarConstants.CAR_BASIC_TOTAL_ODOMETER,
            CarConstants.CAR_BASIC_VEHICLE_SPEED,
            CarConstants.CAR_BASIC_WINDOW_STATUS,
            CarConstants.CAR_DMS_WORK_STATE,
            CarConstants.CAR_EV_SETTING_AVAS_CONFIG,
            CarConstants.CAR_EV_SETTING_AVAS_ENABLE,
            CarConstants.CAR_EV_INFO_CUR_BATTERY_POWER_PERCENTAGE,
            CarConstants.CAR_EV_INFO_ENERGY_OUTPUT_PERCENTAGE,
            CarConstants.CAR_EV_INFO_POWER_BATTERY_VOLTAGE,
            CarConstants.CAR_FRS_SETTING_DISTRACTION_DETECTION_ENABLE,
            CarConstants.CAR_HVAC_ANION_ENABLE,
            CarConstants.CAR_HVAC_BLOWER_MODE,
            CarConstants.CAR_HVAC_CYCLE_MODE,
            CarConstants.CAR_HVAC_DRIVER_TEMPERATURE,
            CarConstants.CAR_HVAC_FAN_SPEED,
            CarConstants.CAR_HVAC_FRONT_DEFROST_ENABLE,
            CarConstants.CAR_HVAC_PASS_TEMPERATURE,
            CarConstants.CAR_HVAC_POWER_MODE,
            CarConstants.CAR_HVAC_SYNC_ENABLE,
            CarConstants.CAR_HVAC_AUTO_ENABLE,
            CarConstants.CAR_IPK_SETTING_BRIGHTNESS_CONFIG,
            CarConstants.SYS_AVM_AUTO_PREVIEW_ENABLE,
            CarConstants.SYS_AVM_PREVIEW_STATUS,
            CarConstants.SYS_SETTINGS_AUDIO_MEDIA_VOLUME,
            CarConstants.SYS_SETTINGS_DISPLAY_BACKLIGHT_STATE,
            CarConstants.SYS_SETTINGS_DISPLAY_BRIGHTNESS_LEVEL,
            CarConstants.CAR_DRIVE_SETTING_OUTSIDE_VIEW_MIRROR_FOLD_STATE,
            CarConstants.CAR_BASIC_ENGINE_STATE,
            CarConstants.CAR_DRIVE_SETTING_ESP_ENABLE,
            CarConstants.CAR_EV_SETTING_POWER_MODEL_CONFIG,
            CarConstants.CAR_DRIVE_SETTING_DRIVE_MODE,
            CarConstants.CAR_DRIVE_SETTING_STEERING_WHEEL_ASSIST_MODE,
            CarConstants.CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL,
            CarConstants.CAR_EV_INFO_FUEL_CONSUME_INFO,
            CarConstants.CAR_EV_INFO_CYCLE_FUEL_CONSUME_INFO,
            CarConstants.CAR_CONFIGURE_PEDAL_CONTROL_ENABLE,
            CarConstants.CAR_BASIC_INSTANT_FUEL_CONSUMPTION,
    };

    private static final CarConstants[] KEYS_TO_SAVE = {
            CarConstants.CAR_DRIVE_SETTING_DRIVE_MODE,
            CarConstants.CAR_DRIVE_SETTING_DRIVE_MODE_MEMORY,
            CarConstants.CAR_DRIVE_SETTING_DST_ENABLE,
            CarConstants.CAR_DRIVE_SETTING_ESP_ENABLE,
            CarConstants.CAR_DRIVE_SETTING_FATIGUE_MONITOR_STATE,
            CarConstants.CAR_DRIVE_SETTING_OUTSIDE_VIEW_MIRROR_ASTERN_MODE,
            CarConstants.CAR_DRIVE_SETTING_STEERING_WHEEL_ASSIST_MODE,
            CarConstants.CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL,
            CarConstants.CAR_CONFIGURE_PEDAL_CONTROL_ENABLE,
            CarConstants.CAR_HUD_SETTING_ADAS_DISPLAY_ENABLE,
            CarConstants.CAR_HUD_SETTING_ENABLE_STATE,
            CarConstants.CAR_HUD_SETTING_HEIGHT_CONFIG,
            CarConstants.CAR_HUD_SETTING_NAVIGATION_DISPLAY_ENABLE,
            CarConstants.CAR_HUD_SETTING_ROTATION_ANGLE,
            CarConstants.CAR_HUD_SETTING_ROTATION_DIRECTION,
            CarConstants.CAR_HUD_SETTING_SNOW_MODE_ENABLE,
            CarConstants.CAR_HUD_SETTING_VIBRATION_CORRN_ENABLE,
            CarConstants.CAR_INTELLIGENT_DRIVING_SETTING_CRUISING_SPEED_LIMIT,
            CarConstants.CAR_INTELLIGENT_DRIVING_SETTING_EAS_ASSIST_SENSITIVITY,
            CarConstants.CAR_INTELLIGENT_DRIVING_SETTING_EAS_CHANGE_LANE_ASSIST_STATE,
            CarConstants.CAR_INTELLIGENT_DRIVING_SETTING_EAS_HIGHWAY_ASSIST_SYSTEM_STATE,
            CarConstants.CAR_INTELLIGENT_DRIVING_SETTING_EAS_WARNING_WAY,
            CarConstants.CAR_INTELLIGENT_DRIVING_SETTING_FAS_AUTO_EMERGENCY_TURN,
            CarConstants.CAR_INTELLIGENT_DRIVING_SETTING_FAS_EARLY_WARNING_MODE,
            CarConstants.CAR_INTELLIGENT_DRIVING_SETTING_FAS_EARLY_WARNING_SENSITIVITY,
            CarConstants.CAR_INTELLIGENT_DRIVING_SETTING_FAS_FRONT_CROSS_LATERAL_BRAKE,
            CarConstants.CAR_INTELLIGENT_DRIVING_SETTING_FAS_FRONT_CROSS_LATERAL_WRANING,
            CarConstants.CAR_INTELLIGENT_DRIVING_SETTING_FAS_INTERSECTION_ASSIST_STATE,
            CarConstants.CAR_INTELLIGENT_DRIVING_SETTING_FAS_PCS_STATE,
            CarConstants.CAR_INTELLIGENT_DRIVING_SETTING_FAS_PPS_STATE,
            CarConstants.CAR_INTELLIGENT_DRIVING_SETTING_LAS_EARLY_WARNING_SENSITIVITY,
            CarConstants.CAR_INTELLIGENT_DRIVING_SETTING_LAS_ELK_STATE,
            CarConstants.CAR_INTELLIGENT_DRIVING_SETTING_LAS_ENABLE,
            CarConstants.CAR_INTELLIGENT_DRIVING_SETTING_LAS_LCA_STATE,
            CarConstants.CAR_INTELLIGENT_DRIVING_SETTING_LAS_LDW_STATE,
            CarConstants.CAR_INTELLIGENT_DRIVING_SETTING_LAS_LKA_STATE,
            CarConstants.CAR_INTELLIGENT_DRIVING_SETTING_LAS_TSI_STATE,
            CarConstants.CAR_INTELLIGENT_DRIVING_SETTING_OVER_SPEED_ALARM_SENSITIVITY,
            CarConstants.CAR_INTELLIGENT_DRIVING_SETTING_OVER_SPEED_WARNING_STATE,
            CarConstants.CAR_INTELLIGENT_DRIVING_SETTING_SMART_DODGE_STATE,
            CarConstants.CAR_INTELLIGENT_DRIVING_SETTING_SRAS_ALA_STATE,
            CarConstants.CAR_INTELLIGENT_DRIVING_SETTING_SRAS_DOOR_OPEN_WARNING,
            CarConstants.CAR_INTELLIGENT_DRIVING_SETTING_SRAS_RCW_STATE,
            CarConstants.CAR_INTELLIGENT_DRIVING_SETTING_SRAS_RSA_RSB_STATE,
            CarConstants.CAR_INTELLIGENT_DRIVING_SETTING_SRAS_RSA_RSB_WARNING_STATE,
    };
    private static ServiceManager instance;
    private final List<IDataChanged> dataChangedListeners;
    private final List<IServiceManagerEvent> serviceManagerEventListeners;
    private final Map<String, String> dataCache;
    private SharedPreferences sharedPreferences;
    private Boolean closeWindowDueToeSpeed = false;
    private Boolean closeSunroofDueToeSpeed = false;
    private HandlerThread handlerThread;
    private Handler backgroundHandler;
    private IListener.Stub listener;
    private IInputListener.Stub inputListener;
    private IClusterCallback.Stub clusterCallback;
    private boolean servicesInitialized = false;
    private boolean isFridaInitialized = false;
    private final List<Runnable> pendingTasks = new ArrayList<>();
    private static long timeBootReceived;
    private long timeStartInitialization;
    private long timeInitialized;
    private CarInfo carInfo;
    private IIntelligentVehicleControlService controlService;
    private IVehicle vehicle;
    private IDvr dvr;
    private boolean delayNextAVM = false;
    private IVehicleModel vehicleModel;
    private IClusterService clusterService;
    private ServiceConnection clusterServiceConnection;
    private IInputService inputService;
    private ServiceConnection inputServiceConnection;
    private IConnectivityManager connectivityManager;
    private boolean isClusterHeartbeatRunning = false;
    private int clusterHeartBeatCount = 0;
    private int clusterCardView = 0;
    private final Map<String, String> previousAcState = new HashMap<>();

    private boolean isMaxAcActive = false;
    private Runnable maxAcTimeoutRunnable;

    // ===== SOCIMPULSE STATE =====
    private static final long SOCIMPULSE_MIN_CORRECTION_INTERVAL_MS = 10_000L;
    private static final long SOCIMPULSE_CORRECTION_WINDOW_MS = 5 * 60_000L;
    private static final int SOCIMPULSE_MAX_CORRECTIONS_PER_WINDOW = 3;

    private long savesocimpulselastImpulseWriteTime = 0L;
    private int savesocimpulselastImpulseWriteValue = -1;
    private long savesocimpulselastCorrectionTime = 0L;
    private long savesocimpulsecorrectionWindowStart = 0L;
    private int savesocimpulsecorrectionCount = 0;
    private int savesocimpulsetarget = -1;


    private ServiceManager() {
        dataChangedListeners = new ArrayList<>();
        dataCache = new HashMap<>();
        serviceManagerEventListeners = new ArrayList<>();
    }

    public static synchronized ServiceManager getInstance() {
        if (instance == null) {
            instance = new ServiceManager();
        }
        return instance;
    }

    public synchronized boolean initializeServices(Context context) {
        try {
            if (controlService != null) {
                if (controlService.asBinder().isBinderAlive()) {
                    try {
                        controlService.unRegisterDataChangedListener(context.getPackageName(), listener);
                        controlService = null;  // Disconnect binder
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
            if (vehicle != null) {
                vehicle = null;  // Disconnect binder
            }
            if (dvr != null) {
                dvr = null;  // Disconnect binder
            }
            if (vehicleModel != null) {
                vehicleModel = null;  // Disconnect binder
            }
            if (clusterService != null) {
                if (clusterService.asBinder().isBinderAlive()) {
                    try {
                        clusterService.unregisterCallback(clusterCallback);
                    } catch (Exception e) {
                        // ignore
                    }
                }
                if (clusterServiceConnection != null) {
                    context.unbindService(clusterServiceConnection);
                }
                clusterService = null;  // Disconnect binder
            }
            if (inputService != null) {
                if (inputService.asBinder().isBinderAlive()) {
                    try {
                        inputService.unregisterKeyEventListener(new int[]{-1}, inputListener);
                    } catch (Exception e) {
                        // ignore
                    }
                }
                if (inputServiceConnection != null) {
                    context.unbindService(inputServiceConnection);
                }
                inputService = null;  // Disconnect binder
            }
            if (handlerThread != null && handlerThread.isAlive()) {
                handlerThread.quitSafely();
                handlerThread = null;
                backgroundHandler = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during service cleanup", e);
        }
        timeStartInitialization = SystemClock.uptimeMillis();
        Log.w(TAG, "Initializing services with Shizuku");
        sharedPreferences = App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE);
        handlerThread = new HandlerThread("ServiceManagerHandlerThread");
        handlerThread.start();
        backgroundHandler = new Handler(handlerThread.getLooper());
        if (!Shizuku.pingBinder()) {
            Log.e(TAG, "Shizuku not available");
            return false;
        }

        try {
            IBinder controlBinder = new ShizukuBinderWrapper(getSystemService("com.beantechs.intelligentvehiclecontrol"));
            if (!controlBinder.pingBinder()) {
                Log.e(TAG, "IntelligentVehicleControlService binder not alive");
                return false;
            }
            controlService = IIntelligentVehicleControlService.Stub.asInterface(controlBinder);
            IBinder poolBinder = new ShizukuBinderWrapper(getSystemService("com.beantechs.voice.adapter.VoiceAdapterService"));
            if (!poolBinder.pingBinder()) {
                Log.e(TAG, "IBinderPool binder not alive");
                return false;
            }
            IBinderPool pool = IBinderPool.Stub.asInterface(poolBinder);
            IBinder vehicleBinder = pool.queryBinder(6);
            vehicle = IVehicle.Stub.asInterface(new ShizukuBinderWrapper(vehicleBinder));
            IBinder dvrBinder = pool.queryBinder(8);
            dvr = IDvr.Stub.asInterface(new ShizukuBinderWrapper(dvrBinder));
            IBinder vehicleModelBinder = pool.queryBinder(13);
            vehicleModel = IVehicleModel.Stub.asInterface(new ShizukuBinderWrapper(vehicleModelBinder));
            Intent clusterIntent = new Intent();
            clusterIntent.setComponent(new ComponentName("com.autolink.clusterservice", "com.autolink.clusterservice.ClusterService"));
            clusterCallback = new IClusterCallback.Stub() {
                @Override
                public void callbackMsg(int msgId, ClusterMsgData data) {
                    if (msgId == 133) {
                        int whichCard = data.getIntValue();
                        clusterCardView = whichCard;
                        dispatchServiceManagerEvent(ServiceManagerEventType.CLUSTER_CARD_CHANGED, clusterCardView);
                        if (whichCard == 1) {
                            MainUiManager.getInstance().updateScreen();
                        }
                        Log.w(TAG, "Cluster card changed: " + whichCard);
                    } else if (msgId == 134) {
                        if (sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION.getKey(), false)) {
                            int intValue = data.getIntValue();
                            if (intValue == 2) {
                                Log.w(TAG, "Cluster heartbeat reset requested");
                                sendHeartBeatToCluster();
                                startClusterHeartbeat();
                            }
                        }
                    } else if (msgId == 135) {
                        if (sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION.getKey(), false)) {
                            int intValue = data.getIntValue();
                            if (intValue == 1) {
                                Log.w(TAG, "Cluster ready to show");
                                sendClusterIntMsg(135, 1);
                            } else if (intValue == 2) {
                                Log.w(TAG, "Cluster ready to hide");
                                sendClusterIntMsg(135, 2);
                            } else if (intValue == 3 || intValue == 4) {
                                boolean show = (intValue == 3);
                                Log.w(TAG, "Cluster show or hide card: " + show);
                            }
                        }
                    }
                }
            };
            clusterServiceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    clusterService = IClusterService.Stub.asInterface(service);
                    try {
                        clusterService.registerCallback(clusterCallback);
                    } catch (Exception e) {
                        Log.e(TAG, "Error registering cluster callback", e);
                    }
                    if (sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION.getKey(), false)) {
                        startClusterHeartbeat();
                    }
                    Log.w(TAG, "ClusterService connected successfully");
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    clusterService = null;
                    Log.w(TAG, "ClusterService disconnected");
                }
            };


            // Initialize MainUiManager and respective menu management controls
            MainUiManager.getInstance();

            context.bindService(clusterIntent, clusterServiceConnection, Context.BIND_AUTO_CREATE);
            Intent inputIntent = new Intent("com.beantechs.inputservice.service_init");
            inputIntent.setPackage("com.beantechs.inputservice");
            inputListener = new IInputListener.Stub() {
                @Override
                public void dispatchKeyEvent(KeyEvent keyEvent) {
                    if (sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_STEERING_WHEEL_CUSTOM_BUTTONS.getKey(), false)) {
                        Log.w(TAG, "Key event received: " + keyEvent);
                        switch (keyEvent.getKeyCode()) {
                            case 517://button 1
                                handleSteeringWheelCustomButton(sharedPreferences.getString(SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_1_ACTION.getKey(), SteeringWheelCustomActionType.DEFAULT.name()), 1);
                                break;
                            case 1031://button 2
                                handleSteeringWheelCustomButton(sharedPreferences.getString(SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_2_ACTION.getKey(), SteeringWheelCustomActionType.DEFAULT.name()), 2);
                                break;
                        }
                    }
                    if (sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_CUSTOM_MENU.getKey(), false)) {
                        if (clusterCardView == 1) {
                            Screen.Key key = null;
                            switch (keyEvent.getKeyCode()) {
                                case 1024: key = Screen.Key.UP; break;
                                case 1025: key = Screen.Key.DOWN; break;
                                case 1028: key = Screen.Key.ENTER; break;
                                case 1029: key = Screen.Key.HOME; break;
                                case 1030: key = Screen.Key.BACK; break;
                                case 1033: key = Screen.Key.UP_LONG; break;
                                case 1034: key = Screen.Key.DOWN_LONG; break;
                                case 1037: key = Screen.Key.ENTER_LONG; break;
                                case 1039: key = Screen.Key.BACK_LONG; break;
                            }
                            if (key != null) MainUiManager.getInstance().handleGeneralKeyEvents(key);
                        }
                    }
                }
            };
            inputServiceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    Log.w(TAG, "InputService connected");
                    inputService = IInputService.Stub.asInterface(service);
                    try {
                        inputService.registerKeyEventListener(new int[]{-1}, inputListener);
                        Log.w(TAG, "InputService connected and listener registered successfully");
                    } catch (Exception e) {
                        Log.e(TAG, "Error registering key event listener", e);
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    inputService = null;
                    Log.w(TAG, "InputService disconnected");
                }
            };
            context.bindService(inputIntent, inputServiceConnection, Context.BIND_AUTO_CREATE);
            Log.w(TAG, "Services bound successfully");
            listener = new IListener.Stub() {
                @Override
                public void onDataChanged(String key, String value) {
                    OnDataChanged(key, value);
                }
            };
            ShizukuUtils.runCommandAndGetOutput(new String[]{
                    "settings", "put", "secure", "enabled_accessibility_services",
                    "br.com.redesurftank.havalshisuku/.services.AccessibilityService"
            });
            ShizukuUtils.runCommandAndGetOutput(new String[]{
                    "settings", "put", "secure", "accessibility_enabled", "1"
            });
            //enable write secure settings
            ShizukuUtils.runCommandAndGetOutput(new String[]{
                    "pm", "grant", context.getPackageName(), "android.permission.WRITE_SECURE_SETTINGS"
            });
            controlService.registerDataChangedListener(context.getPackageName(), listener);
            Log.w(TAG, "Listener registered successfully");
            controlService.addListenerKey(App.getContext().getPackageName(), getCombinedKeys());
            Log.w(TAG, "Listener keys added successfully");
            IBinder connectivityBinder = new ShizukuBinderWrapper(getSystemService(Context.CONNECTIVITY_SERVICE));
            connectivityManager = IConnectivityManager.Stub.asInterface(connectivityBinder);
            IntentFilter bluetoothFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            bluetoothFilter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
            context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction() == null) return;
                    String action = intent.getAction();
                    if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED) || action.equals(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)) {
                        var state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                        if (state == BluetoothAdapter.STATE_ON) {
                            var drivingReady = getUpdatedData(CarConstants.CAR_BASIC_DRIVING_READY_STATE.getValue());
                            boolean disableBluetoothWhenPowerOff = sharedPreferences.getBoolean(SharedPreferencesKeys.DISABLE_BLUETOOTH_ON_POWER_OFF.getKey(), false);
                            if ((drivingReady.equals("-1") || drivingReady.equals("0")) && disableBluetoothWhenPowerOff) {
                                disableBluetooth();
                            }
                        }
                    }
                }
            }, bluetoothFilter);
            IntentFilter wifiFilter = new IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED");
            context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction() == null) return;
                    String action = intent.getAction();
                    if (action.equals("android.net.wifi.WIFI_AP_STATE_CHANGED")) {
                        int state = intent.getIntExtra("wifi_state", 0);
                        if (state == 13) { // WIFI_AP_STATE_ENABLED
                            Log.w(TAG, "Wi-Fi Hotspot turned on");
                            var drivingReady = getUpdatedData(CarConstants.CAR_BASIC_DRIVING_READY_STATE.getValue());
                            boolean disableHotspotWhenPowerOff = sharedPreferences.getBoolean(SharedPreferencesKeys.DISABLE_HOTSPOT_ON_POWER_OFF.getKey(), false);
                            if ((drivingReady.equals("-1") || drivingReady.equals("0")) && disableHotspotWhenPowerOff) {
                                disableWifiTether();
                            }
                        }
                    }
                }
            }, wifiFilter);
            dispatchAllData();
            if (isSocImpulseEnabled()) {
                int target = getSocImpulseTarget();
                if (target >= 20 && target <= 80) {
                    dispatchServiceManagerEvent(
                        ServiceManagerEventType.SAVE_SOC_IMPULSE_CHANGED,
                        target
                    );
                }
            }
            if (sharedPreferences.getBoolean(SharedPreferencesKeys.SET_STARTUP_VOLUME.getKey(), false)) {
                int startupVolume = sharedPreferences.getInt(SharedPreferencesKeys.STARTUP_VOLUME.getKey(), -1);
                if (startupVolume != -1) {
                    controlService.request("cmd.common.request.set", CarConstants.SYS_SETTINGS_AUDIO_MEDIA_VOLUME.getValue(), String.valueOf(startupVolume));
                    Log.w(TAG, "Startup volume set to: " + startupVolume);
                }
            }
            boolean isForceDisableMonitoring = sharedPreferences.getBoolean(SharedPreferencesKeys.DISABLE_MONITORING.getKey(), false);
            if (isForceDisableMonitoring) {
                setMonitoringEnabled(false);
                Log.w(TAG, "Distraction detection monitoring disabled by user preference");
            }
            boolean isForceDisableAVAS = sharedPreferences.getBoolean(SharedPreferencesKeys.DISABLE_AVAS.getKey(), false);
            if (isForceDisableAVAS) {
                setAvasEnabled(false);
                Log.w(TAG, "AVAS disabled by user preference");
            }
            if (sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_AUTO_BRIGHTNESS.getKey(), false)) {
                AutoBrightnessManager.Companion.getInstance().setEnabled(true);
            }
            if (sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_FRIDA_HOOKS.getKey(), false)) {
                pendingTasks.add(this::initializeFrida);
            }
            if (sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_SEAT_VENTILATION_ON_AC_ON.getKey(), false) && getUpdatedData(CarConstants.CAR_HVAC_POWER_MODE.getValue()).equals("1")) {
                updateData(CarConstants.CAR_COMFORT_SETTING_DRIVER_SEAT_VENTILATION_LEVEL.getValue(), "3");
                updateData(CarConstants.CAR_COMFORT_SETTING_PASSENGER_SEAT_VENTILATION_LEVEL.getValue(), "3");
            }

            ensureSteeringWheelButtonIntegration();
            ensureSystemApps();

            servicesInitialized = true;
            synchronized (pendingTasks) {
                for (Runnable task : pendingTasks) {
                    backgroundHandler.post(task);
                }
                pendingTasks.clear();
            }

            MainUiManager.getInstance().updateScreen();

            timeInitialized = SystemClock.uptimeMillis();
            Log.w(TAG, "Services initialized successfully");
            ProjectorManager.getInstance().initialize();
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, "Error during initialization", e);
            return false;
        }
    }

        public void ensureSteeringWheelButtonIntegration() {
        if (sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_STEERING_WHEEL_CUSTOM_BUTTONS.getKey(), false)) {
            var button1Action = sharedPreferences.getString(SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_1_ACTION.getKey(), SteeringWheelCustomActionType.DEFAULT.getKey());
            var button2Action = sharedPreferences.getString(SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_2_ACTION.getKey(), SteeringWheelCustomActionType.DEFAULT.getKey());
            Log.w(TAG, "Ensuring steering wheel button integration. Button 1 action: " + button1Action + ", Button 2 action: " + button2Action);
            if (button1Action.equals(SteeringWheelCustomActionType.DEFAULT.getKey())) {
                disableNativeSteeringWheelButton1();
            } else {
                enableSteeringWheelButton1Integration();
            }
            if (button2Action.equals(SteeringWheelCustomActionType.DEFAULT.getKey())) {
                disableNativeSteeringWheelButton2();
            } else {
                enableSteeringWheelButton2Integration();
            }
        } else {
            Log.w(TAG, "Steering wheel button integration disabled, restoring native functions");
            disableNativeSteeringWheelButton1();
            disableNativeSteeringWheelButton2();
        }

    }

    private void handleSteeringWheelCustomButton(String string, int button) {
        SteeringWheelCustomActionType action = SteeringWheelCustomActionType.Companion.fromKey(string);
        if (action == null || action == SteeringWheelCustomActionType.DEFAULT) {
            return;
        }
        switch (action) {
            case CHANGE_POWER_MODE:
                var carEvPowerMode = Integer.parseInt(getUpdatedData(CarConstants.CAR_EV_SETTING_POWER_MODEL_CONFIG.getValue()));
                Log.w(TAG, "Current EV Power Mode: " + carEvPowerMode);
                if (carEvPowerMode == 0) {
                    carEvPowerMode = 1;
                } else if (carEvPowerMode == 1) {
                    carEvPowerMode = 3;
                } else if (carEvPowerMode == 3) {
                    carEvPowerMode = 0;
                }
                updateData(CarConstants.CAR_EV_SETTING_POWER_MODEL_CONFIG.getValue(), String.valueOf(carEvPowerMode));
                Log.w(TAG, "New EV Power Mode: " + carEvPowerMode);
                break;
            case CHANGE_REGENERATION_LEVEL:
                var regenLevel = Integer.parseInt(getUpdatedData(CarConstants.CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL.getValue()));
                Log.w(TAG, "Current Regeneration Level: " + regenLevel);
                //low 2
                //normal 0
                //high 1
                if (regenLevel == 0) {
                    regenLevel = 1;
                } else if (regenLevel == 1) {
                    regenLevel = 2;
                } else if (regenLevel == 2) {
                    regenLevel = 0;
                }
                updateData(CarConstants.CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL.getValue(), String.valueOf(regenLevel));
                Log.w(TAG, "New Regeneration Level: " + regenLevel);
                break;
            case TOGGLE_ANION:
                var anionState = getUpdatedData(CarConstants.CAR_HVAC_ANION_ENABLE.getValue());
                if (anionState != null) {
                    boolean anion = anionState.equals("1");
                    anion = !anion;
                    updateData(CarConstants.CAR_HVAC_ANION_ENABLE.getValue(), anion ? "1" : "0");
                    Log.w(TAG, "Anion state changed to: " + anion);
                }
                break;
            /*case TOGGLE_ESP:
                var espState = getUpdatedData(CarConstants.CAR_DRIVE_SETTING_ESP_ENABLE.getValue());
                if (espState != null) {
                    boolean esp = espState.equals("1");
                    esp = !esp;
                    updateData(CarConstants.CAR_DRIVE_SETTING_ESP_ENABLE.getValue(), esp ? "1" : "0");
                    Log.w(TAG, "ESP state changed to: " + esp);
                }
                break;*/
            case TOGGLE_ONE_PEDAL_DRIVING:
                var onePedalState = getUpdatedData(CarConstants.CAR_CONFIGURE_PEDAL_CONTROL_ENABLE.getValue());
                if (onePedalState != null) {
                    boolean onePedal = onePedalState.equals("1");
                    onePedal = !onePedal;
                    updateData(CarConstants.CAR_CONFIGURE_PEDAL_CONTROL_ENABLE.getValue(), onePedal ? "1" : "0");
                    Log.w(TAG, "One Pedal Driving state changed to: " + onePedal);
                }
                break;
            case OPEN_APP:
                var packageName = sharedPreferences.getString(button == 1 ? SharedPreferencesKeys.STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_1.getKey() : SharedPreferencesKeys.STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_2.getKey(), "");
                if (!packageName.isEmpty()) {
                    Intent launchIntent = App.getContext().getPackageManager().getLaunchIntentForPackage(packageName);
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        App.getContext().startActivity(launchIntent);
                        Log.w(TAG, "Launching app: " + packageName);
                    } else {
                        Log.e(TAG, "App not found: " + packageName);
                    }
                }
                break;
            case TOGGLE_CAMERA_AVM:
                boolean cameraAVM = sharedPreferences.getBoolean(SharedPreferencesKeys.DISABLE_AVM_CAR_STOPPED.getKey(), false);
                cameraAVM = !cameraAVM;
                sharedPreferences.edit().putBoolean(SharedPreferencesKeys.DISABLE_AVM_CAR_STOPPED.getKey(), cameraAVM).apply();
                Log.w(TAG, "Camera AVM state changed to: " + cameraAVM);
                break;
            case OPEN_AVM_ONCE:
                try {
                    if (getData(CarConstants.SYS_AVM_PREVIEW_STATUS.getValue()).equals("0")) {
                        delayNextAVM = true;
                        dvr.setAVM(1);
                        Log.w(TAG, "Camera AVM temporarily triggered");
                    } else {
                        delayNextAVM = false;
                        dvr.setAVM(0);
                        Log.w(TAG, "Camera AVM closed");
                    }
                } catch (RemoteException e) {
                    Log.w(TAG, "Error to launch AVM camera");
                }
                break;
        }
    }

    public void enableSteeringWheelButton1Integration() {
        try {
            var currentConfig = ShizukuUtils.runCommandAndGetOutput(new String[]{"settings", "get", "system", "bean_sw_custom_key1_config"}).trim();
            Log.w(TAG, "Current steering wheel button 1 config: " + currentConfig);
            sharedPreferences.edit().putString(SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_1_ACTION_ORIGINAL.getKey(), currentConfig).apply();
            ShizukuUtils.runCommandAndGetOutput(new String[]{"settings", "put", "system", "bean_sw_custom_key1_config", "99"});
        } catch (Exception e) {
            Log.e(TAG, "Error disabling native steering wheel custom buttons", e);
        }
    }

    public void enableSteeringWheelButton2Integration() {
        try {
            var currentConfig = ShizukuUtils.runCommandAndGetOutput(new String[]{"settings", "get", "system", "bean_sw_custom_key2_config"}).trim();
            Log.w(TAG, "Current steering wheel button 2 config: " + currentConfig);
            sharedPreferences.edit().putString(SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_2_ACTION_ORIGINAL.getKey(), currentConfig).apply();
            ShizukuUtils.runCommandAndGetOutput(new String[]{"settings", "put", "system", "bean_sw_custom_key2_config", "99"});
        } catch (Exception e) {
            Log.e(TAG, "Error disabling native steering wheel custom buttons", e);
        }
    }

    public void disableNativeSteeringWheelButton1() {
        try {
            var originalConfig = sharedPreferences.getString(SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_1_ACTION_ORIGINAL.getKey(), "0");
            if (originalConfig.equals("99"))
                return;
            Log.w(TAG, "Restoring steering wheel button 1 config to: " + originalConfig);
            ShizukuUtils.runCommandAndGetOutput(new String[]{"settings", "put", "system", "bean_sw_custom_key1_config", originalConfig});
        } catch (Exception e) {
            Log.e(TAG, "Error restoring native steering wheel custom button 1", e);
        }
    }

    public void disableNativeSteeringWheelButton2() {
        try {
            var originalConfig = sharedPreferences.getString(SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_2_ACTION_ORIGINAL.getKey(), "0");
            if (originalConfig.equals("99"))
                return;
            Log.w(TAG, "Restoring steering wheel button 2 config to: " + originalConfig);
            ShizukuUtils.runCommandAndGetOutput(new String[]{"settings", "put", "system", "bean_sw_custom_key2_config", originalConfig});
        } catch (Exception e) {
            Log.e(TAG, "Error restoring native steering wheel custom button 2", e);
        }
    }

    private void sendClusterIntMsg(int type, int value) {
        if (clusterService == null) {
            Log.e(TAG, "ClusterService not initialized");
            return;
        }
        ClusterMsgData msg = new ClusterMsgData();
        msg.setIntValue(value);
        try {
            clusterService.setMsg(type, msg);
        } catch (RemoteException e) {
            Log.e(TAG, "Error sending message to cluster service", e);
        }
    }

    private void sendAndroidReadyToCluster() {
        try {
            var msg = new ClusterMsgData();
            msg.setIntValue(1);
            clusterService.setMsg(75, msg);
        } catch (Exception e) {
            Log.e(TAG, "Error setting cluster service message", e);
        }
    }

    public synchronized void startClusterHeartbeat() {
        if (isClusterHeartbeatRunning)
            return;
        isClusterHeartbeatRunning = true;
        sendAndroidReadyToCluster();
        backgroundHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION.getKey(), false)) {
                    isClusterHeartbeatRunning = false;
                    return;
                }
                sendHeartBeatToCluster();
                backgroundHandler.postDelayed(this, 1000);

            }
        }, 1000);
    }

    private void sendHeartBeatToCluster() {
        if (clusterHeartBeatCount > 32767) {
            clusterHeartBeatCount = 0; // Reset to avoid overflow
        }
        var msg = new ClusterMsgData();
        msg.setIntValue(clusterHeartBeatCount++);
        try {
            clusterService.setMsg(134, msg);
        } catch (RemoteException e) {
            Log.e(TAG, "Error sending heartbeat to cluster service", e);
        }
    }

    public void dispatchAllData() {
        if (controlService == null) return;
        try {
            var allKeys = getCombinedKeys();
            String[] currentValues = controlService.fetchDatas(allKeys);
            for (int i = 0; i < currentValues.length; i++) {
                OnDataChanged(allKeys[i], currentValues[i]);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Error dispatching data", e);
        }
    }

    public void addDataChangedListener(IDataChanged listener) {
        if (listener == null) {
            Log.e(TAG, "Cannot add null listener");
            return;
        }
        if (!dataChangedListeners.contains(listener)) {
            dataChangedListeners.add(listener);
            Log.w(TAG, "Listener added: " + listener.getClass().getName());
        } else {
            Log.w(TAG, "Listener already exists: " + listener.getClass().getName());
        }
    }

    public void removeDataChangedListener(IDataChanged listener) {
        if (listener == null) {
            Log.e(TAG, "Cannot remove null listener");
            return;
        }
        if (dataChangedListeners.remove(listener)) {
            Log.w(TAG, "Listener removed: " + listener.getClass().getName());
        } else {
            Log.w(TAG, "Listener not found: " + listener.getClass().getName());
        }
    }


    public void setSaveSocImpulseEnabled(boolean enabled) {
        sharedPreferences.edit()
                .putBoolean(SharedPreferencesKeys.SAVE_SOC_IMPULSE_ENABLED.getKey(), enabled)
                .apply();

        dispatchServiceManagerEvent(
                ServiceManagerEventType.SAVE_SOC_IMPULSE_CHANGED
        );
    }

    public void applySaveSocImpulseTarget(int target) {

        // ============================
        // VALIDAÇÃO BÁSICA
        // ============================
        if (target < 20 || target > 80) {
            Log.w(TAG, "SOCIMPULSE: target fora do range permitido: " + target);
            return;
        }

        long now = System.currentTimeMillis();

        // ============================
        // SALVA ESTADO INTERNO
        // ============================
        savesocimpulsetarget = target;
        savesocimpulselastImpulseWriteValue = target;
        savesocimpulselastImpulseWriteTime = now;

        // Persist to SharedPreferences
        sharedPreferences.edit()
                .putInt(SharedPreferencesKeys.SAVE_SOC_IMPULSE_TARGET.getKey(), target)
                .apply();

        // Reset de ciclo
        savesocimpulsecorrectionCount = 0;
        savesocimpulsecorrectionWindowStart = now;
        savesocimpulselastCorrectionTime = 0;

        Log.i(TAG, "SOCIMPULSE: Aplicado alvo " + target + "%");
        dispatchServiceManagerEvent(
                ServiceManagerEventType.SAVE_SOC_IMPULSE_CHANGED
        );

        // ============================
        // CONDIÇÕES DE ATIVAÇÃO
        // ============================

        // 1. Função precisa estar habilitada
        if (!isSocImpulseEnabled()) {
            Log.d(TAG, "SOCIMPULSE: função desativada");
            return;
        }

        // 2. Carro precisa estar em partida (DRIVING_READY_STATE == 1)
        String drivingReady = getData(CarConstants.CAR_BASIC_DRIVING_READY_STATE.getValue());
        if (!"1".equals(drivingReady)) {
            Log.d(TAG, "SOCIMPULSE: carro não está em partida");
            return;
        }

        // 3. Modo precisa ser HEV
        String powerMode = getData(CarConstants.CAR_EV_SETTING_POWER_MODEL_CONFIG.getValue());
        if (!"0".equals(powerMode)) {
            Log.d(TAG, "SOCIMPULSE: não está em HEV (power_model=" + powerMode + ")");
            return;
        }

        // 4. Precisa estar em HEV PRIORITÁRIO
        String reserveMode = getData(CarConstants.CAR_EV_SETTING_POWER_RESERVE_CONFIG.getValue());
        if (!"2".equals(reserveMode)) {
            Log.d(TAG, "SOCIMPULSE: não está em HEV prioritário (reserve=" + reserveMode + ")");
            return;
        }

        // ============================
        // VERIFICA SAVE SOC ATUAL
        // ============================
        String currentSaveSocStr = getData(
                CarConstants.CAR_EV_SETTING_CHARGE_SOC_TARGET_CONFIG.getValue()
        );

        if (currentSaveSocStr == null) {
            Log.w(TAG, "SOCIMPULSE: save SOC atual não disponível");
            return;
        }

        int currentSaveSoc;
        try {
            currentSaveSoc = Integer.parseInt(currentSaveSocStr);
        } catch (Exception e) {
            Log.e(TAG, "SOCIMPULSE: erro ao parsear save SOC", e);
            return;
        }

        // ============================
        // CORREÇÃO IMEDIATA (SE NECESSÁRIO)
        // ============================
        if (currentSaveSoc != target) {

            Log.i(TAG, "SOCIMPULSE: corrigindo imediatamente "
                    + currentSaveSoc + "% → " + target + "%");

            updateData(
                    CarConstants.CAR_EV_SETTING_CHARGE_SOC_TARGET_CONFIG.getValue(),
                    String.valueOf(target)
            );
            dispatchServiceManagerEvent(
                ServiceManagerEventType.SAVE_SOC_IMPULSE_CHANGED
            );

            savesocimpulsecorrectionCount = 1;
            savesocimpulselastCorrectionTime = now;
        } else {
            Log.d(TAG, "SOCIMPULSE: valor já está correto, nenhuma ação necessária");
        }
    }
    public void addServiceManagerEventListener(IServiceManagerEvent listener) {
        if (listener == null) {
            Log.e(TAG, "Cannot add null service manager event listener");
            return;
        }
        if (!serviceManagerEventListeners.contains(listener)) {
            serviceManagerEventListeners.add(listener);
            Log.w(TAG, "Service manager event listener added: " + listener.getClass().getName());
        } else {
            Log.w(TAG, "Service manager event listener already exists: " + listener.getClass().getName());
        }
    }

    public void removeServiceManagerEventListener(IServiceManagerEvent listener) {
        if (listener == null) {
            Log.e(TAG, "Cannot remove null service manager event listener");
            return;
        }
        if (serviceManagerEventListeners.remove(listener)) {
            Log.w(TAG, "Service manager event listener removed: " + listener.getClass().getName());
        } else {
            Log.w(TAG, "Service manager event listener not found: " + listener.getClass().getName());
        }
    }

    public void dispatchServiceManagerEvent(ServiceManagerEventType event, Object... args) {
        Log.w(TAG, "Dispatching service manager event: " + event);
        for (IServiceManagerEvent listener : new ArrayList<>(serviceManagerEventListeners)) {
            try {
                listener.onEvent(event, args);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying service manager event listener", e);
            }
        }
    }

    public String getData(String key) {
        if (dataCache.containsKey(key)) {
            return dataCache.get(key);
        }
        if (controlService == null) {
            Log.e(TAG, "ControlService not initialized");
            return null;
        }
        try {
            String value = controlService.fetchData(key);
            dataCache.put(key, value);
            return value;
        } catch (RemoteException e) {
            Log.e(TAG, "Error fetching data", e);
            return null;
        }
    }

    public String getUpdatedData(String key) {
        if (controlService == null) {
            Log.e(TAG, "ControlService not initialized");
            return null;
        }
        try {
            String value = controlService.fetchData(key);
            dataCache.put(key, value);
            return value;
        } catch (RemoteException e) {
            Log.e(TAG, "Error fetching data", e);
            return null;
        }
    }

    public void updateData(String key, String value) {
        if (controlService == null) {
            Log.e(TAG, "ControlService not initialized");
            return;
        }
        try {
            controlService.request("cmd.common.request.set", key, value);
        } catch (RemoteException e) {
            Log.e(TAG, "Error updating data", e);
        }
    }

    public Map<String, String> getAllCurrentCachedData() {
        return new HashMap<>(dataCache);
    }

    private void OnDataChanged(String key, String value) {
        // ===== SOCIMPULSE CORE LOGIC =====
        try {
            if (key.equals(CarConstants.CAR_EV_SETTING_CHARGE_SOC_TARGET_CONFIG.getValue())) {

                int currentValue = Integer.parseInt(value);
                int target = getSocImpulseTarget();

                // Detect if change came from car (not echo of our own write)
                boolean changeCameFromCar =
                        currentValue != savesocimpulselastImpulseWriteValue;

                if (isSocImpulseActive() && target >= 20 && target <= 80) {

                    long now = SystemClock.uptimeMillis();

                    if (currentValue == target) {
                        // carro obedeceu
                        return;
                    }

                    // Cooldown only applies to self-written echoes
                    if (!changeCameFromCar && isSocImpulseCooldown()) {
                        return;
                    }

                    if (now - savesocimpulsecorrectionWindowStart > SOCIMPULSE_CORRECTION_WINDOW_MS) {
                        resetSocImpulseWindow();
                    }

                    if (savesocimpulsecorrectionCount >= SOCIMPULSE_MAX_CORRECTIONS_PER_WINDOW) {
                        return;
                    }

                    updateData(
                        CarConstants.CAR_EV_SETTING_CHARGE_SOC_TARGET_CONFIG.getValue(),
                        String.valueOf(target)
                    );
                    // After writing, update last impulse write value/time
                    savesocimpulselastImpulseWriteValue = target;
                    savesocimpulselastImpulseWriteTime = now;
                    dispatchServiceManagerEvent(
                        ServiceManagerEventType.SAVE_SOC_IMPULSE_CHANGED
                    );

                    savesocimpulselastCorrectionTime = now;
                    savesocimpulsecorrectionCount++;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "SOCIMPULSE error", e);
        }

        Intent broadcastIntent = new Intent("android.intent.haval." + key);
        broadcastIntent.putExtra("value", value);
        App.getContext().sendBroadcast(broadcastIntent);
        broadcastIntent = new Intent("android.intent.haval." + key + "_" + value);
        App.getContext().sendBroadcast(broadcastIntent);
        for (IDataChanged listener : new ArrayList<>(dataChangedListeners)) {
            try {
                listener.onDataChanged(key, value);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener", e);
            }
        }
        dataCache.put(key, value);
        try {
            if (key.equals(CarConstants.CAR_FRS_SETTING_DISTRACTION_DETECTION_ENABLE.getValue()) && value.equals("1")) {
                boolean isForceDisableMonitoring = sharedPreferences.getBoolean(SharedPreferencesKeys.DISABLE_MONITORING.getKey(), false);
                if (isForceDisableMonitoring) {
                    setMonitoringEnabled(false);
                    Log.w(TAG, "Distraction detection monitoring disabled by user preference");
                }
            }
            if (key.equals(CarConstants.CAR_EV_SETTING_AVAS_ENABLE.getValue()) && value.equals("1")) {
                boolean isForceDisableAVAS = sharedPreferences.getBoolean(SharedPreferencesKeys.DISABLE_AVAS.getKey(), false);
                if (isForceDisableAVAS) {
                    setAvasEnabled(false);
                    Log.w(TAG, "AVAS disabled by user preference");
                }
            } else if ((key.equals(CarConstants.CAR_DMS_WORK_STATE.getValue()) && value.equals("0"))) {
                boolean closeWindowOnPowerOff = sharedPreferences.getBoolean(SharedPreferencesKeys.CLOSE_WINDOW_ON_POWER_OFF.getKey(), false);
                if (closeWindowOnPowerOff) {
                    closeAllWindow();
                }
                boolean closeSunRoofOnPowerOff = sharedPreferences.getBoolean(SharedPreferencesKeys.CLOSE_SUNROOF_ON_POWER_OFF.getKey(), false);
                if (closeSunRoofOnPowerOff) {
                    closeSunRoof(true);
                }
            } else if ((key.equals(CarConstants.CAR_DRIVE_SETTING_OUTSIDE_VIEW_MIRROR_FOLD_STATE.getValue()) && value.equals("0"))) {
                var speedValue = Float.parseFloat(getUpdatedData(CarConstants.CAR_BASIC_VEHICLE_SPEED.getValue()));
                var currentGear = getUpdatedData(CarConstants.CAR_BASIC_GEAR_STATUS.getValue());
                if (speedValue > 0 || !currentGear.equals("3")) {
                    Log.w(TAG, "Ignoring mirror fold event due to speed or gear state");
                    return;
                }
                boolean closeWindowOnFoldMirror = sharedPreferences.getBoolean(SharedPreferencesKeys.CLOSE_WINDOW_ON_FOLD_MIRROR.getKey(), false);
                if (closeWindowOnFoldMirror) {
                    closeAllWindow();
                }
                boolean closeSunRoofOnFoldMirror = sharedPreferences.getBoolean(SharedPreferencesKeys.CLOSE_SUNROOF_ON_FOLD_MIRROR.getKey(), false);
                if (closeSunRoofOnFoldMirror) {
                    closeSunRoof(true);
                }
            } else if (key.equals(CarConstants.CAR_BASIC_VEHICLE_SPEED.getValue())) {
                float currentSpeed = Float.parseFloat(value);
                boolean closeWindowOnSpeed = sharedPreferences.getBoolean(SharedPreferencesKeys.CLOSE_WINDOWS_ON_SPEED.getKey(), false);
                boolean closeSunRoofOnSpeed = sharedPreferences.getBoolean(SharedPreferencesKeys.CLOSE_SUNROOF_ON_SPEED.getKey(), false);
                if (currentSpeed > sharedPreferences.getFloat(SharedPreferencesKeys.SPEED_THRESHOLD.getKey(), 15f)) {
                    if (!closeWindowDueToeSpeed) {
                        if (closeWindowOnSpeed) {
                            closeAllWindow();
                        }
                        closeWindowDueToeSpeed = true;
                    }
                }
                if (currentSpeed > sharedPreferences.getFloat(SharedPreferencesKeys.SUNROOF_SPEED_THRESHOLD.getKey(), 15f)) {
                    if (!closeSunroofDueToeSpeed) {
                        if (closeSunRoofOnSpeed) {
                            closeSunRoof(false);
                        }
                        closeSunroofDueToeSpeed = true;
                    }
                }
                float speedThreshold = sharedPreferences.getFloat(SharedPreferencesKeys.SPEED_THRESHOLD.getKey(), 15f);

                if (currentSpeed < (speedThreshold - 5f) && (closeWindowDueToeSpeed || closeSunroofDueToeSpeed)) {
                    closeWindowDueToeSpeed = false;
                    closeSunroofDueToeSpeed = false;
                }
                if (currentSpeed <= 0 & sharedPreferences.getBoolean(SharedPreferencesKeys.DISABLE_AVM_CAR_STOPPED.getKey(), false) && !getData(CarConstants.CAR_BASIC_GEAR_STATUS.getValue()).equals("4")) {
                    if (!delayNextAVM) dvr.setAVM(0);
                }
            } else if (key.equals(CarConstants.SYS_AVM_PREVIEW_STATUS.getValue()) && sharedPreferences.getBoolean(SharedPreferencesKeys.DISABLE_AVM_CAR_STOPPED.getKey(), false) && Float.parseFloat(getData(CarConstants.CAR_BASIC_VEHICLE_SPEED.getValue())) <= 0f && !getData(CarConstants.CAR_BASIC_GEAR_STATUS.getValue()).equals("4")) {
                if (value.equals("1")) {
                    if (!delayNextAVM) dvr.setAVM(0);
                } else {
                    delayNextAVM = false;
                }
            } else if (key.equals(CarConstants.CAR_BASIC_DRIVING_READY_STATE.getValue())) {
                // ===== SOCIMPULSE STARTUP LOGIC =====
                if (value.equals("1")) {
                    backgroundHandler.postDelayed(() -> {
                        try {
                            if (!isSocImpulseActive()) return;

                            int target = getSocImpulseTarget();
                            if (target < 20 || target > 80) return;

                            String current = getUpdatedData(
                                CarConstants.CAR_EV_SETTING_CHARGE_SOC_TARGET_CONFIG.getValue()
                            );
                            if (current == null) return;

                            if (!current.equals(String.valueOf(target))) {
                                resetSocImpulseWindow();
                                updateData(
                                    CarConstants.CAR_EV_SETTING_CHARGE_SOC_TARGET_CONFIG.getValue(),
                                    String.valueOf(target)
                                );
                                dispatchServiceManagerEvent(
                                    ServiceManagerEventType.SAVE_SOC_IMPULSE_CHANGED,
                                    target
                                );
                                savesocimpulselastCorrectionTime = SystemClock.uptimeMillis();
                                savesocimpulsecorrectionCount++;
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "SOCIMPULSE startup correction error", e);
                        }
                    }, 60_000L);
                }
                if ((value.equals("-1") || value.equals("0"))) {
                    boolean disableBluetoothOnPowerOff = sharedPreferences.getBoolean(SharedPreferencesKeys.DISABLE_BLUETOOTH_ON_POWER_OFF.getKey(), false);
                    boolean currentBluetoothState = currentBluetoothState();
                    if (currentBluetoothState && disableBluetoothOnPowerOff) {
                        sharedPreferences.edit().putBoolean(SharedPreferencesKeys.BLUETOOTH_STATE_ON_POWER_OFF.getKey(), true).apply();
                        disableBluetooth();
                    }
                    boolean disableHotspotOnPowerOff = sharedPreferences.getBoolean(SharedPreferencesKeys.DISABLE_HOTSPOT_ON_POWER_OFF.getKey(), false);
                    if (disableHotspotOnPowerOff) {
                        disableWifiTether();
                    }
                } else {
                    boolean disableBluetoothOnPowerOff = sharedPreferences.getBoolean(SharedPreferencesKeys.DISABLE_BLUETOOTH_ON_POWER_OFF.getKey(), false);
                    boolean bluetoothStateOnPowerOff = sharedPreferences.getBoolean(SharedPreferencesKeys.BLUETOOTH_STATE_ON_POWER_OFF.getKey(), false);
                    if (disableBluetoothOnPowerOff && bluetoothStateOnPowerOff && !currentBluetoothState()) {
                        enableBluetooth();
                    }
                    if (sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_MAX_AC_ON_UNLOCK.getKey(), true)) {
                        if (!isMaxAcActive) enableMaxAcOn();
                    }
                }
            } else if (key.equals(CarConstants.CAR_HVAC_POWER_MODE.getValue()) && value.equals("1") && sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_SEAT_VENTILATION_ON_AC_ON.getKey(), false)) {
                updateData(CarConstants.CAR_COMFORT_SETTING_DRIVER_SEAT_VENTILATION_LEVEL.getValue(), "3");
                updateData(CarConstants.CAR_COMFORT_SETTING_PASSENGER_SEAT_VENTILATION_LEVEL.getValue(), "3");
            } else if (key.equals(CarConstants.CAR_HVAC_POWER_MODE.getValue()) && value.equals("0") && sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_SEAT_VENTILATION_ON_AC_ON.getKey(), false)) {
                updateData(CarConstants.CAR_COMFORT_SETTING_DRIVER_SEAT_VENTILATION_LEVEL.getValue(), "0");
                updateData(CarConstants.CAR_COMFORT_SETTING_PASSENGER_SEAT_VENTILATION_LEVEL.getValue(), "0");
            } else if (key.equals(CarConstants.CAR_BASIC_INSIDE_TEMP.getValue()) && sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_MAX_AC_ON_UNLOCK.getKey(), true)) {
                if (isMaxAcActive) updateMaxAcSmoothing();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in OnDataChanged", e);
        }
    }

    public boolean closeAllWindow() {
        try {
            int[] windowsStatus = vehicle.getWindowsStatus(0);
            for (int i = 0; i < windowsStatus.length; i++) {
                if (windowsStatus[i] != 1) {
                    vehicle.setWindowStatus(i, 1);
                }
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error closing all windows", e);
            return false;
        }
    }

    public void closeSunRoof(boolean checkCloseShade) {
        try {
            var sunRoofStatus = vehicle.getSkylightLevel(0);
            if (sunRoofStatus != 0) {
                vehicle.setSkylightLevel(0);
            }
            if (checkCloseShade && sharedPreferences.getBoolean(SharedPreferencesKeys.CLOSE_SUNROOF_SUN_SHADE_ON_CLOSE_SUNROOF.getKey(), false)) {
                backgroundHandler.postDelayed(this::closeSunRoofShade, 5000);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing sunroof", e);
        }
    }

    public void closeSunRoofShade() {
        try {
            var sunRoofBlockStatus = vehicle.getShadeScreensLevel(0);
            if (sunRoofBlockStatus != 0) {
                vehicle.setShadeScreensLevel(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing shade screens", e);
        }
    }

    public boolean isTurnLightOn() {
        var leftTurnLight = getData(CarConstants.CAR_BASIC_LEFT_TURN_SWITCH_STATUS.getValue());
        var rightTurnLight = getData(CarConstants.CAR_BASIC_RIGHT_TURN_SWITCH_STATUS.getValue());
        return (leftTurnLight != null && leftTurnLight.equals("1")) || (rightTurnLight != null && rightTurnLight.equals("1"));
    }

    public void setMonitoringEnabled(boolean b) {
        if (controlService == null) {
            Log.e(TAG, "ControlService not initialized");
            return;
        }
        try {
            controlService.request("cmd.common.request.set", CarConstants.CAR_FRS_SETTING_DISTRACTION_DETECTION_ENABLE.getValue(), b ? "1" : "0");
            Log.w(TAG, "Distraction detection monitoring set to: " + b);
        } catch (RemoteException e) {
            Log.e(TAG, "Error setting monitoring", e);
        }
    }

    public void setAvasEnabled(boolean b) {
        if (controlService == null) {
            Log.e(TAG, "ControlService not initialized");
            return;
        }
        try {
            controlService.request("cmd.common.request.set", CarConstants.CAR_EV_SETTING_AVAS_ENABLE.getValue(), b ? "1" : "0");
            Log.w(TAG, "AVAS enabled: " + b);
        } catch (RemoteException e) {
            Log.e(TAG, "Error setting AVAS", e);
        }
    }

    private boolean currentBluetoothState() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
        } catch (Exception e) {
            Log.e(TAG, "Error checking Bluetooth state", e);
            return false;
        }
    }

    public void disableBluetooth() {
        try {
            ShizukuUtils.runCommandAndGetOutput(new String[]{"svc", "bluetooth", "disable"});
        } catch (Exception e) {
            Log.e(TAG, "Error disabling Bluetooth", e);
        }
    }

    public void enableBluetooth() {
        try {
            ShizukuUtils.runCommandAndGetOutput(new String[]{"svc", "bluetooth", "enable"});
        } catch (Exception e) {
            Log.e(TAG, "Error enabling Bluetooth", e);
        }
    }

    public void disableWifiTether() {
        try {
            connectivityManager.stopTethering(0, "br.com.redesurftank.havalshisuku");
        } catch (Exception e) {
            Log.e(TAG, "Error disabling Wi-Fi", e);
        }
    }

    public void enableWifiTether() {
        try {
            var receiver = new ResultReceiver(new Handler(Looper.getMainLooper())) {
                @Override
                protected void onReceiveResult(int resultCode, Bundle resultData) {
                    if (resultCode == 0) {
                        Log.w(TAG, "Wi-Fi tethering started successfully");
                    } else {
                        Log.e(TAG, "Failed to start Wi-Fi tethering with result code: " + resultCode);
                    }
                }
            };
            connectivityManager.startTethering(0, receiver, false, "br.com.redesurftank.havalshisuku");
        } catch (Exception e) {
            Log.e(TAG, "Error enabling Wi-Fi", e);
        }
    }

    public void cancelMaxAcMode() {

        if (!isMaxAcActive) return;

        for (Map.Entry<String, String> entry : previousAcState.entrySet()) {
            if (entry.getValue() != null) {
                updateData(entry.getKey(), entry.getValue());
            }
        }
        isMaxAcActive = false;
        previousAcState.clear();
        if (maxAcTimeoutRunnable != null) {
            backgroundHandler.removeCallbacks(maxAcTimeoutRunnable);
            maxAcTimeoutRunnable = null;
        }
        dispatchServiceManagerEvent(ServiceManagerEventType.MAX_AUTO_AC_STATUS_CHANGED, 0);

    }

    public boolean isMaxAcActive() {
        return isMaxAcActive;
    }

    private void enableMaxAcOn() {
        try {
            String tempStr = getUpdatedData(CarConstants.CAR_BASIC_INSIDE_TEMP.getValue());
            if (tempStr == null) return;
            float currentTemp = Float.parseFloat(tempStr);
            float threshold = sharedPreferences.getFloat(SharedPreferencesKeys.MAX_AC_ON_UNLOCK_THRESHOLD.getKey(), 35.0f);

            if (currentTemp >= threshold && !isMaxAcActive) {
                String prevPower = getUpdatedData(CarConstants.CAR_HVAC_POWER_MODE.getValue());
                String prevEnabled = getUpdatedData(CarConstants.CAR_HVAC_AC_ENABLE.getValue());
                String prevFan = getUpdatedData(CarConstants.CAR_HVAC_FAN_SPEED.getValue());
                String prevDriverTemp = getUpdatedData(CarConstants.CAR_HVAC_DRIVER_TEMPERATURE.getValue());
                String prevPassTemp = getUpdatedData(CarConstants.CAR_HVAC_PASS_TEMPERATURE.getValue());
                String prevAuto = getUpdatedData(CarConstants.CAR_HVAC_AUTO_ENABLE.getValue());
                String prevAnion = getUpdatedData(CarConstants.CAR_HVAC_ANION_ENABLE.getValue());
                String prevAQS = getUpdatedData(CarConstants.CAR_HVAC_AQS_ENABLE.getValue());
                String prevSync = getUpdatedData(CarConstants.CAR_HVAC_SYNC_ENABLE.getValue());

                previousAcState.put(CarConstants.CAR_HVAC_POWER_MODE.getValue(), prevPower);
                previousAcState.put(CarConstants.CAR_HVAC_AC_ENABLE.getValue(), prevEnabled);
                previousAcState.put(CarConstants.CAR_HVAC_FAN_SPEED.getValue(), prevFan);
                previousAcState.put(CarConstants.CAR_HVAC_DRIVER_TEMPERATURE.getValue(), prevDriverTemp);
                previousAcState.put(CarConstants.CAR_HVAC_PASS_TEMPERATURE.getValue(), prevPassTemp);
                previousAcState.put(CarConstants.CAR_HVAC_AUTO_ENABLE.getValue(), prevAuto);
                previousAcState.put(CarConstants.CAR_HVAC_ANION_ENABLE.getValue(), prevAnion);
                previousAcState.put(CarConstants.CAR_HVAC_AQS_ENABLE.getValue(), prevAQS);
                previousAcState.put(CarConstants.CAR_HVAC_SYNC_ENABLE.getValue(), prevSync);

                updateData(CarConstants.CAR_HVAC_POWER_MODE.getValue(), "1");
                updateData(CarConstants.CAR_HVAC_AUTO_ENABLE.getValue(), "0");
                updateData(CarConstants.CAR_HVAC_FAN_SPEED.getValue(), "7");
                updateData(CarConstants.CAR_HVAC_DRIVER_TEMPERATURE.getValue(), "16.0");
                updateData(CarConstants.CAR_HVAC_PASS_TEMPERATURE.getValue(), "16.0");
                updateData(CarConstants.CAR_HVAC_SYNC_ENABLE.getValue(), "1");
                updateData(CarConstants.CAR_HVAC_AC_ENABLE.getValue(), "1");

                isMaxAcActive = true;

                int timeoutMinutes = sharedPreferences.getInt(SharedPreferencesKeys.MAX_AC_TIMEOUT.getKey(), 0);
                 if (timeoutMinutes > 0) {
                    if (maxAcTimeoutRunnable != null) {
                        backgroundHandler.removeCallbacks(maxAcTimeoutRunnable);
                    }
                    maxAcTimeoutRunnable = () -> {
                         Log.w(TAG, "Max AC timeout reached, aborting");
                         cancelMaxAcMode();
                    };
                    backgroundHandler.postDelayed(maxAcTimeoutRunnable, timeoutMinutes * 60 * 1000L);
                    Log.w(TAG, "Max AC timeout scheduled for " + timeoutMinutes + " minutes");
                }

                Log.w(TAG, "Max AC activated power on and high temp: " + currentTemp);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in Max AC Activation logic", e);
        }
    }

    private void updateMaxAcSmoothing() {
        if (!isMaxAcActive) return;
        try {
            String tempStr = getUpdatedData(CarConstants.CAR_BASIC_INSIDE_TEMP.getValue());
            if (tempStr == null) return;
            float currentTemp = Float.parseFloat(tempStr);
            float targetTemp = sharedPreferences.getFloat(SharedPreferencesKeys.MAX_AC_TARGET_TEMP.getKey(), 28.0f);
            float smoothingRange = 2.0f;
            float startSmoothingTemp = targetTemp + smoothingRange;

            if (currentTemp <= targetTemp) {
                cancelMaxAcMode();
                Log.w(TAG, "Max AC deactivated, temperature reached target: " + targetTemp);
            } else if (currentTemp < startSmoothingTemp) {
                float factor = (currentTemp - targetTemp) / smoothingRange;
                factor = Math.max(0f, Math.min(1f, factor));

                String prevFanStr = previousAcState.get(CarConstants.CAR_HVAC_FAN_SPEED.getValue());
                int prevFan = (prevFanStr != null) ? Integer.parseInt(prevFanStr) : 3;
                int maxFan = 7;
                int newFan = prevFan + Math.round((maxFan - prevFan) * factor);
                newFan = Math.max(3, newFan);

                String prevDriverKey = CarConstants.CAR_HVAC_DRIVER_TEMPERATURE.getValue();
                float minTemp = 16.0f;
                float prevDriverTemp = (previousAcState.get(prevDriverKey) != null) ? Float.parseFloat(previousAcState.get(prevDriverKey)) : 22.0f;
                float newDriverTemp = prevDriverTemp - ((prevDriverTemp - minTemp) * factor);
                newDriverTemp = Math.min(20, newDriverTemp);

                updateData(CarConstants.CAR_HVAC_FAN_SPEED.getValue(), String.valueOf(newFan));
                updateData(prevDriverKey, String.format(java.util.Locale.US, "%.1f", newDriverTemp));

                Log.d(TAG, "Max AC Smoothing: Temp=" + currentTemp + ", Factor=" + factor + ", Fan=" + newFan + ", DriverTemp=" + newDriverTemp);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in Max AC Smoothing logic", e);
        }
    }

    public void executeWithServicesRunning(Runnable task) {
        Runnable wrapperWithCatch = () -> {
            try {
                task.run();
            } catch (Exception e) {
                Log.e(TAG, "Error executing task", e);
            }
        };
        if (servicesInitialized) {
            wrapperWithCatch.run();
        } else {
            synchronized (pendingTasks) {
                pendingTasks.add(wrapperWithCatch);
            }
        }
    }

    public void switchUser(String userId) {
        if (userId == null || userId.isEmpty()) {
            Log.e(TAG, "Invalid user ID provided for switchUser");
            return;
        }
        executeWithServicesRunning(() -> {
            var currentUser = sharedPreferences.getString(SharedPreferencesKeys.CURRENT_USER.getKey(), "");
            if (currentUser.equals(userId)) {
                Log.w(TAG, "Current user is already: " + userId);
                return;
            }

            Log.w(TAG, "Switching user to: " + userId);

            try {
                saveCarSettingsForUser(currentUser);
            } catch (Exception e) {
                Log.e(TAG, "Error saving settings for user: " + currentUser, e);
            }

            try {
                restoreCarSettingsForUser(userId);
            } catch (Exception e) {
                Log.e(TAG, "Error restoring settings for user: " + userId, e);
            }

            sharedPreferences.edit()
                    .putString(SharedPreferencesKeys.CURRENT_USER.getKey(), userId)
                    .apply();
        });
    }

    private void restoreCarSettingsForUser(String userId) {
        File file = new File(App.getContext().getFilesDir(), userId + ".settings.json");
        if (!file.exists()) {
            Log.w(TAG, "No saved settings found for user: " + userId);
            return;
        }
        Gson gson = new Gson();
        try (FileReader reader = new FileReader(file)) {
            JsonElement userSettingsMap = gson.fromJson(reader, JsonElement.class);
            if (!(userSettingsMap instanceof JsonObject)) {
                Log.e(TAG, "Error parsing user settings JSON for user: " + userId);
                return;
            }

            JsonObject userSettings = (JsonObject) userSettingsMap;
            for (Map.Entry<String, JsonElement> entry : userSettings.entrySet()) {
                String key = entry.getKey();
                if (Arrays.stream(KEYS_TO_SAVE).noneMatch(k -> k.getValue().equals(key))) {
                    continue;
                }
                String value = entry.getValue().getAsString();
                if (value.isEmpty()) {
                    Log.w(TAG, "Skipping empty value for key: " + key);
                    continue;
                }
                try {
                    updateData(key, value);
                    Log.w(TAG, "Restored setting for user " + userId + ": " + key + " = " + value);
                } catch (Exception e) {
                    Log.e(TAG, "Error restoring setting for user " + userId + ": " + key, e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading settings file for user: " + userId, e);
        }
    }

    private void saveCarSettingsForUser(String userId) {
        Map<String, String> settingsToSave = new HashMap<>();

        for (CarConstants key : KEYS_TO_SAVE) {
            String value = getUpdatedData(key.getValue());
            settingsToSave.put(key.getValue(), value);
        }

        if (settingsToSave.isEmpty()) {
            Log.w(TAG, "No settings to save for user: " + userId);
            return;
        }

        Gson gson = new Gson();
        JsonObject jsonObject = new com.google.gson.JsonObject();
        for (Map.Entry<String, String> entry : settingsToSave.entrySet()) {
            jsonObject.addProperty(entry.getKey(), entry.getValue());
        }

        File file = new File(App.getContext().getFilesDir(), userId + ".settings.json");
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(jsonObject, writer);
            Log.w(TAG, "Saved settings for user: " + userId);
        } catch (Exception e) {
            Log.e(TAG, "Error writing settings file for user: " + userId, e);
        }
    }

    public int getTotalOdometer() {
        var totalOdometer = getData(CarConstants.CAR_BASIC_TOTAL_ODOMETER.getValue());
        if (totalOdometer == null || totalOdometer.isEmpty()) {
            Log.w(TAG, "Total odometer data is not available");
            return 0;
        }
        try {
            return Integer.parseInt(totalOdometer);
        } catch (NumberFormatException e) {
            Log.e(TAG, "Error parsing total odometer value: " + totalOdometer, e);
            return 0;
        }
    }

    public void updateMonitoringProperties() {
        executeWithServicesRunning(() -> {
            try {
                var allKeys = getCombinedKeys();
                controlService.addListenerKey(App.getContext().getPackageName(), allKeys);
                for (String s : new HashSet<>(dataCache.keySet())) {
                    dataCache.remove(s);
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Error updating monitoring properties", e);
            }
            dispatchAllData();
        });
    }

    public String[] getCombinedKeys() {
        List<String> keys = new ArrayList<>();
        keys.addAll(List.of(CarConstants.FromArray(DEFAULT_KEYS)));
        keys.addAll(sharedPreferences.getStringSet(SharedPreferencesKeys.CAR_MONITOR_PROPERTIES.getKey(), new HashSet<>()));
        return keys.toArray(new String[0]);
    }

    public void initializeFrida() {
        if (isFridaInitialized)
            return;
        isFridaInitialized = true;

        if (!tryInitializeFrida()) {
            sharedPreferences.edit()
                    .putBoolean(SharedPreferencesKeys.ENABLE_FRIDA_HOOKS.getKey(), false)
                    .apply();
            Log.e(TAG, "Frida initialization failed, disabling Frida hooks");
        }
    }

    private boolean tryInitializeFrida() {
        try {
            if (!FridaUtils.ensureFridaServerRunning()) {
                Log.e(TAG, "Failed to ensure Frida server is running");
                return false;
            }
            Log.w(TAG, "Frida server is running, injecting scripts...");
            if (!FridaUtils.injectAllScripts())
                return false;
            if (sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_FRIDA_HOOK_SYSTEM_SERVER.getKey(), false)) {
                backgroundHandler.postDelayed(() -> {
                    try {
                        FridaUtils.injectSystemServer();
                    } catch (Exception e) {
                        Log.e(TAG, "Error injecting into system_server", e);
                    }
                }, 10000);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during Frida script injection", e);
            return false;
        }

        Log.w(TAG, "Frida initialization completed successfully");
        return true;
    }

    public void ensureSystemApps() {
        if (sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_INSTRUMENT_PROJECTOR.getKey(), false) && sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION.getKey(), false)) {
            disableSystemApp("com.beantechs.multidisplay");
        } else {
            enableSystemApp("com.beantechs.multidisplay");
        }
    }

    public void disableSystemApp(String packageName) {
        try {
            ShizukuUtils.runCommandAndGetOutput(new String[]{"pm", "uninstall", "--user", "0", packageName});
            ShizukuUtils.runCommandAndGetOutput(new String[]{"pkill", "-9", "-f", packageName});
        } catch (Exception e) {
            Log.e(TAG, "Error disabling system app: " + packageName, e);
        }
    }

    public void enableSystemApp(String packageName) {
        try {
            ShizukuUtils.runCommandAndGetOutput(new String[]{"pm", "install-existing", packageName});
        } catch (Exception e) {
            Log.e(TAG, "Error enabling system app: " + packageName, e);
        }
    }

    public boolean isServicesInitialized() {
        return servicesInitialized;
    }

    public void setTimeBootReceived(long l) {
        if (timeBootReceived != 0)
            return;
        timeBootReceived = l;
    }

    public long getTimeInitialized() {
        return timeInitialized;
    }

    public long getTimeBootReceived() {
        return timeBootReceived;
    }

    public long getTimeStartInitialization() {
        return timeStartInitialization;
    }

    public boolean isMainScreenOn() {
        try {
            var engineState = getData(CarConstants.CAR_BASIC_ENGINE_STATE.getValue());
            return engineState != null && !engineState.equals("-1") && !engineState.equals("15");
        } catch (Exception e) {
            return false;
        }
    }

    public CarInfo getCarInfo() {
        try {
            if (carInfo == null) {
                carInfo = new CarInfo(vehicleModel.getCarBrand(), vehicleModel.getVehicleModel(), vehicleModel.getVehicleType());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting car info", e);
            return new CarInfo("Unknown", "Unknown", "Unknown");
        }

        return carInfo;
    }

    public int getClusterCardView() {
        return clusterCardView;
    }

    private static IBinder getSystemService(String serviceName) {
        try {
            return (IBinder) Objects.requireNonNull(getService.invoke(null, serviceName));
        } catch (IllegalAccessException | InvocationTargetException e) {
            Log.e(TAG, "Error getting system service: " + serviceName, e);
            throw new RuntimeException(e);
        }
    }

    private static Method getService;

    static {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            getService = sm.getMethod("getService", String.class);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            Log.w(TAG, Log.getStackTraceString(e));
        }
    }

    public SharedPreferences getSharedPreferences() {
        return sharedPreferences;
    }

// ===== SOCIMPULSE HELPERS =====

    private boolean isSocImpulseEnabled() {
        return sharedPreferences.getBoolean(
                SharedPreferencesKeys.SAVE_SOC_IMPULSE_ENABLED.getKey(),
                false
        );
    }

    private int getSocImpulseTarget() {
        return sharedPreferences.getInt(
                SharedPreferencesKeys.SAVE_SOC_IMPULSE_TARGET.getKey(),
                -1
        );
    }

    private boolean isSocImpulseCooldown() {
        long now = SystemClock.uptimeMillis();
        return (now - savesocimpulselastCorrectionTime)
                < SOCIMPULSE_MIN_CORRECTION_INTERVAL_MS;
    }

    private void resetSocImpulseWindow() {
        savesocimpulsecorrectionWindowStart = SystemClock.uptimeMillis();
        savesocimpulsecorrectionCount = 0;
    }

    private boolean isSocImpulseActive() {
        if (!isSocImpulseEnabled()) return false;

        String drivingReady =
                getUpdatedData(CarConstants.CAR_BASIC_DRIVING_READY_STATE.getValue());
        if (!"1".equals(drivingReady)) return false;

        String powerMode =
                getUpdatedData(CarConstants.CAR_EV_SETTING_POWER_MODEL_CONFIG.getValue());
        if (!"0".equals(powerMode)) return false; // HEV

        String reserveMode =
                getUpdatedData(CarConstants.CAR_EV_SETTING_POWER_RESERVE_CONFIG.getValue());
        if (!"2".equals(reserveMode)) return false; // HEV PRIORITÁRIO

        return true;
    }

} // <<< FECHA A CLASSE ServiceManager