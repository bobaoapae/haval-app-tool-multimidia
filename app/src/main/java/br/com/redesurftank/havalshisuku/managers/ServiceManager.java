package br.com.redesurftank.havalshisuku.managers;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
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
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import br.com.redesurftank.App;
import br.com.redesurftank.havalshisuku.listeners.IDataChanged;
import br.com.redesurftank.havalshisuku.listeners.IServiceManagerEvent;
import br.com.redesurftank.havalshisuku.models.CarConstants;
import br.com.redesurftank.havalshisuku.models.CarInfo;
import br.com.redesurftank.havalshisuku.models.MainUiManager;
import br.com.redesurftank.havalshisuku.models.ServiceManagerEventType;
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys;
import br.com.redesurftank.havalshisuku.models.SteeringWheelCustomActionType;
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
            CarConstants.CAR_HVAC_SETTING_COMFORT_CURVE,
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
            CarConstants.CAR_EV_INFO_CUR_CHARGE_CURRENT,
            CarConstants.CAR_EV_INFO_POWER_BATTERY_VOLTAGE,
            CarConstants.CAR_BASIC_REMAIN_FUEL_PERCENTAGE,
            CarConstants.CAR_EV_INFO_FUEL_MODE_REMAIN_ODOMETER,
            CarConstants.CAR_EV_INFO_ELECTRIC_MODE_REMAIN_ODOMETER,
            CarConstants.CAR_BASIC_COOLANT_TEMP_WARNING,
            CarConstants.CAR_BASIC_ENGINE_OIL_LOW_PRESSURE_WARNING,
            CarConstants.CAR_BASIC_FATIGUE_WARNING,
            CarConstants.CAR_BASIC_MAINTENANCE_WARNING,
            CarConstants.CAR_BASIC_OIL_LOW_WARNING,
            CarConstants.CAR_BASIC_SEAT_BELT_WARNING,
            CarConstants.CAR_BASIC_TIREPRESS_WARNING,
            CarConstants.CAR_BASIC_TIRETEMP_WARNING,
            CarConstants.CAR_BASIC_TPMS_WARNING,
            CarConstants.CAR_IPK_INFO_BSD_LCA_WARNING_REQLEFT,
            CarConstants.CAR_IPK_INFO_BSD_LCA_WARNING_REQRIGHT,
            CarConstants.CAR_IPK_INFO_DOW_WARNING_REQLEFT,
            CarConstants.CAR_IPK_INFO_DOW_WARNING_REQRIGHT,
            CarConstants.CAR_IPK_INFO_FCTA_WARNING,
            CarConstants.CAR_IPK_INFO_FCW_WARNING,
            CarConstants.CAR_IPK_INFO_WARNING_TTS_NOTIFY,
            CarConstants.CAR_IPK_LIGHT_DOOR_WARNING,
            CarConstants.CAR_IPK_LIGHT_ENGINE_OIL_LOW_PRESSURE_WARNING,
            CarConstants.CAR_IPK_LIGHT_SEAT_BELT_WARNING_INDICATOR,
            CarConstants.CAR_IPK_LIGHT_TPMS_WARNING,
            CarConstants.CAR_BASIC_ENGINE_SPEED,
            CarConstants.CAR_EV_INFO_INSTANT_ENERGY_CONSUMPTION,
            CarConstants.CAR_IPK_LIGHT_FUEL_LOW,
            CarConstants.SYS_BASIC_AUDIO_SOURCE_APP,
            CarConstants.SYS_RADIO_CUR_CHANNEL_INFO,
            CarConstants.SYS_RADIO_PLAY_STATE,
            CarConstants.SYS_RADIO_FM_FAVORITES_STATION_LIST,
            CarConstants.SYS_RADIO_SEARCH_STATE,
            CarConstants.SYS_RADIO_RDS_CUR_CHANNEL_INFO,
            CarConstants.SYS_RADIO_RDS_REGIONAL_INFO,
            CarConstants.SYS_RADIO_RDS_TRAFFIC_ANNOUNCEMENT_ACTIVE_STATE,
            CarConstants.SYS_OTHER_KEYEVENT_NOTIFY
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
            CarConstants.CAR_EV_SETTING_CHARGE_SOC_TARGET_CONFIG,
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

    private static final String HVAC_PACKAGE_NAME = "com.beantechs.hvac";
    private static final long HVAC_RESUME_DELAY_MS = 300;
    private boolean isHvacSuspended = false;
    private Runnable resumeHvacRunnable;
    private final Set<String> hvacKeysToSuspend = new HashSet<>(Arrays.asList(
            CarConstants.CAR_HVAC_ANION_ENABLE.getValue(),
            CarConstants.CAR_HVAC_BLOWER_MODE.getValue(),
            CarConstants.CAR_HVAC_CYCLE_MODE.getValue(),
            CarConstants.CAR_HVAC_DRIVER_TEMPERATURE.getValue(),
            CarConstants.CAR_HVAC_FAN_SPEED.getValue(),
            CarConstants.CAR_HVAC_FRONT_DEFROST_ENABLE.getValue(),
            CarConstants.CAR_HVAC_PASS_TEMPERATURE.getValue(),
            CarConstants.CAR_HVAC_POWER_MODE.getValue(),
            CarConstants.CAR_HVAC_SYNC_ENABLE.getValue(),
            CarConstants.CAR_HVAC_AUTO_ENABLE.getValue(),
            CarConstants.CAR_HVAC_SETTING_COMFORT_CURVE.getValue()
    ));


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
        if (timeBootReceived <= 0) {
            timeBootReceived = SystemClock.uptimeMillis();
            Log.w(TAG, "[HavalDev] timeBootReceived fallback set during initializeServices");
        }

        try {
            if (controlService != null) {
                if (controlService.asBinder().isBinderAlive()) {
                    try {
                        controlService.unRegisterDataChangedListener(context.getPackageName(), listener);
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
            controlService = null;
            if (vehicle != null) {
                vehicle = null;
            }
            if (dvr != null) {
                dvr = null;
            }
            if (vehicleModel != null) {
                vehicleModel = null;
            }
                if (clusterService != null) {
                    try {
                        clusterService.unregisterCallback(clusterCallback);
                    } catch (Exception e) {
                        // ignore
                    }
                }
            clusterService = null;
            if (clusterServiceConnection != null) {
                context.unbindService(clusterServiceConnection);
            }
            if (inputServiceConnection != null) {
                context.unbindService(inputServiceConnection);
            }
            inputService = null;
            if (handlerThread != null && handlerThread.isAlive()) {
                handlerThread.quitSafely();
            }
            handlerThread = null;
            backgroundHandler = null;
        } catch (Exception e) {
            Log.e(TAG, "Error during service cleanup", e);
        }

        timeStartInitialization = SystemClock.uptimeMillis();
        Log.w(TAG, "Initializing services");
        sharedPreferences = App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE);
        handlerThread = new HandlerThread("ServiceManagerHandlerThread");
        handlerThread.start();
        backgroundHandler = new Handler(handlerThread.getLooper());

        int shizukuRetry = 0;
        while (!Shizuku.pingBinder() && shizukuRetry < 3) {
            shizukuRetry++;
            Log.w(TAG, "Shizuku not available, retrying... (" + shizukuRetry + "/3)");
            try { Thread.sleep(500); } catch (InterruptedException e) {}
        }

        if (!Shizuku.pingBinder()) {
            Log.e(TAG, "Shizuku not available after retries");
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
                        Log.w(TAG, "Cluster card changed: " + whichCard);
                    } else if (msgId == 134) {
                        if (sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION.getKey(), false)) {
                            if (data.getIntValue() == 2) {
                                sendHeartBeatToCluster();
                                startClusterHeartbeat();
                            }
                        }
                    } else if (msgId == 135) {
                        if (sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION.getKey(), false)) {
                            int val = data.getIntValue();
                            if (val == 1) sendClusterIntMsg(135, 1);
                            else if (val == 2) sendClusterIntMsg(135, 2);
                        }
                    }
                }
            };
            clusterServiceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    clusterService = IClusterService.Stub.asInterface(service);
                    try { clusterService.registerCallback(clusterCallback); } catch (Exception e) {}
                    if (sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION.getKey(), false)) {
                        startClusterHeartbeat();
                    }
                }
                @Override public void onServiceDisconnected(ComponentName name) { clusterService = null; }
            };
            context.bindService(clusterIntent, clusterServiceConnection, Context.BIND_AUTO_CREATE);

            Intent inputIntent = new Intent("com.beantechs.inputservice.service_init");
            inputIntent.setPackage("com.beantechs.inputservice");
            inputListener = new IInputListener.Stub() {
                @Override
                public void dispatchKeyEvent(KeyEvent keyEvent) {
                    if (keyEvent.getAction() == KeyEvent.ACTION_UP &&
                            sharedPreferences.getBoolean(SharedPreferencesKeys.STEERING_WHEEL_MEDIA_ARROWS_NAVIGATE_FAVORITES.getKey(), false)) {
                        if (keyEvent.getKeyCode() == 87) {
                            dispatchServiceManagerEvent(ServiceManagerEventType.RADIO_NAVIGATE, "next");
                        } else if (keyEvent.getKeyCode() == 88) {
                            dispatchServiceManagerEvent(ServiceManagerEventType.RADIO_NAVIGATE, "prev");
                        }
                    }
                    if (sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_STEERING_WHEEL_CUSTOM_BUTTONS.getKey(), false)) {
                        switch (keyEvent.getKeyCode()) {
                            case 517: handleSteeringWheelCustomButton(sharedPreferences.getString(SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_1_ACTION.getKey(), SteeringWheelCustomActionType.DEFAULT.name()), 1); break;
                            case 1031: handleSteeringWheelCustomButton(sharedPreferences.getString(SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_2_ACTION.getKey(), SteeringWheelCustomActionType.DEFAULT.name()), 2); break;
                        }
                    }
                    if (sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_CUSTOM_MENU.getKey(), false)) {
                        Screen.Key key = null;
                        switch (keyEvent.getKeyCode()) {
                            case 1024:
                                key = Screen.Key.UP;
                                break;
                            case 1025:
                                key = Screen.Key.DOWN;
                                break;
                            case 1028:
                                key = Screen.Key.ENTER;
                                break;
                            case 1029:
                                key = Screen.Key.HOME;
                                break;
                            case 1030:
                                key = Screen.Key.BACK;
                                break;
                            case 1033:
                                key = Screen.Key.UP_LONG;
                                break;
                            case 1034:
                                key = Screen.Key.DOWN_LONG;
                                break;
                            case 1037:
                                key = Screen.Key.ENTER_LONG;
                                break;
                            case 1039:
                                key = Screen.Key.BACK_LONG;
                                break;
                        }
                        if (key != null) MainUiManager.getInstance().handleGeneralKeyEvents(key);
                        if (key == Screen.Key.BACK) {
                            dispatchServiceManagerEvent(ServiceManagerEventType.DISMISS_WARNING);
                        }
                    }
                }
            };
            inputServiceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    inputService = IInputService.Stub.asInterface(service);
                    try { inputService.registerKeyEventListener(new int[]{-1}, inputListener); } catch (Exception e) {}
                }
                @Override public void onServiceDisconnected(ComponentName name) { inputService = null; }
            };
            context.bindService(inputIntent, inputServiceConnection, Context.BIND_AUTO_CREATE);

            listener = new IListener.Stub() {
                @Override public void onDataChanged(String key, String value) { OnDataChanged(key, value); }
            };
            ShizukuUtils.runCommandAndGetOutput(new String[]{"settings", "put", "secure", "enabled_accessibility_services", "br.com.redesurftank.havalshisuku/.services.AccessibilityService"});
            ShizukuUtils.runCommandAndGetOutput(new String[]{"settings", "put", "secure", "accessibility_enabled", "1"});
            ShizukuUtils.runCommandAndGetOutput(new String[]{"pm", "grant", context.getPackageName(), "android.permission.WRITE_SECURE_SETTINGS"});
            String nlComponent = context.getPackageName() + "/br.com.redesurftank.havalshisuku.services.MediaNotificationListenerService";
            String existingNl = ShizukuUtils.runCommandAndGetOutput(new String[]{"settings", "get", "secure", "enabled_notification_listeners"}).trim();
            if (!existingNl.contains(context.getPackageName())) {
                String newNl = (existingNl.isEmpty() || existingNl.equals("null")) ? nlComponent : existingNl + ":" + nlComponent;
                ShizukuUtils.runCommandAndGetOutput(new String[]{"settings", "put", "secure", "enabled_notification_listeners", newNl});
            }
            controlService.registerDataChangedListener(context.getPackageName(), listener);
            controlService.addListenerKey(App.getContext().getPackageName(), getCombinedKeys());

            IBinder connectivityBinder = new ShizukuBinderWrapper(getSystemService(Context.CONNECTIVITY_SERVICE));
            connectivityManager = IConnectivityManager.Stub.asInterface(connectivityBinder);

            IntentFilter bluetoothFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            bluetoothFilter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
            context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction() == null) return;
                    if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED) || intent.getAction().equals(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)) {
                        int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                        if (state == BluetoothAdapter.STATE_ON) {
                            String drivingReady = getUpdatedData(CarConstants.CAR_BASIC_DRIVING_READY_STATE.getValue());
                            if ((drivingReady.equals("-1") || drivingReady.equals("0")) && sharedPreferences.getBoolean(SharedPreferencesKeys.DISABLE_BLUETOOTH_ON_POWER_OFF.getKey(), false)) {
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
                    if ("android.net.wifi.WIFI_AP_STATE_CHANGED".equals(intent.getAction())) {
                        if (intent.getIntExtra("wifi_state", 0) == 13) {
                            String drivingReady = getUpdatedData(CarConstants.CAR_BASIC_DRIVING_READY_STATE.getValue());
                            if ((drivingReady.equals("-1") || drivingReady.equals("0")) && sharedPreferences.getBoolean(SharedPreferencesKeys.DISABLE_HOTSPOT_ON_POWER_OFF.getKey(), false)) {
                                disableWifiTether();
                            }
                        }
                    }
                }
            }, wifiFilter);

            dispatchAllData();
            if (sharedPreferences.getBoolean(SharedPreferencesKeys.SET_STARTUP_VOLUME.getKey(), false)) {
                int vol = sharedPreferences.getInt(SharedPreferencesKeys.STARTUP_VOLUME.getKey(), -1);
                if (vol != -1) controlService.request("cmd.common.request.set", CarConstants.SYS_SETTINGS_AUDIO_MEDIA_VOLUME.getValue(), String.valueOf(vol));
            }
            if (sharedPreferences.getBoolean(SharedPreferencesKeys.DISABLE_MONITORING.getKey(), false)) setMonitoringEnabled(false);
            if (sharedPreferences.getBoolean(SharedPreferencesKeys.DISABLE_AVAS.getKey(), false)) setAvasEnabled(false);
            if (sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_AUTO_BRIGHTNESS.getKey(), false)) AutoBrightnessManager.Companion.getInstance().setEnabled(true);
            if (sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_FRIDA_HOOKS.getKey(), false)) pendingTasks.add(this::initializeFrida);
            if (sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_SEAT_VENTILATION_ON_AC_ON.getKey(), false) && "1".equals(getUpdatedData(CarConstants.CAR_HVAC_POWER_MODE.getValue()))) {
                updateData(CarConstants.CAR_COMFORT_SETTING_DRIVER_SEAT_VENTILATION_LEVEL.getValue(), "3");
            }
            ensureSteeringWheelButtonIntegration();
            ensureSystemApps();
            TripConsistencyManager.Companion.getInstance().initialize();
        } catch (RemoteException e) {
            Log.e(TAG, "Error during initialization", e);
            return false;
        }

        servicesInitialized = true;
        synchronized (pendingTasks) {
            for (Runnable task : pendingTasks) backgroundHandler.post(task);
            pendingTasks.clear();
        }
        MainUiManager.getInstance().updateScreen();
        timeInitialized = SystemClock.uptimeMillis();
        Log.w(TAG, "Services initialized successfully");
        backgroundHandler.post(() -> {
            try {
                ShizukuUtils.runCommandAndGetOutput(new String[]{"sh", "-c", "settings put global enable_freeform_support 1"});
                ShizukuUtils.runCommandAndGetOutput(new String[]{"sh", "-c", "settings put global force_resizable_activities 1"});
            } catch (Exception e) {}
        });
        ProjectorManager.getInstance().initialize();
        return true;
    }

    public void ensureSteeringWheelButtonIntegration() {
        if (sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_STEERING_WHEEL_CUSTOM_BUTTONS.getKey(), false)) {
            String button1Action = sharedPreferences.getString(SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_1_ACTION.getKey(), SteeringWheelCustomActionType.DEFAULT.getKey());
            String button2Action = sharedPreferences.getString(SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_2_ACTION.getKey(), SteeringWheelCustomActionType.DEFAULT.getKey());
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
                int carEvPowerMode = Integer.parseInt(getUpdatedData(CarConstants.CAR_EV_SETTING_POWER_MODEL_CONFIG.getValue()));
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
                int regenLevel = Integer.parseInt(getUpdatedData(CarConstants.CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL.getValue()));
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
                String anionState = getUpdatedData(CarConstants.CAR_HVAC_ANION_ENABLE.getValue());
                if (anionState != null) {
                    boolean anion = anionState.equals("1");
                    anion = !anion;
                    updateData(CarConstants.CAR_HVAC_ANION_ENABLE.getValue(), anion ? "1" : "0");
                    Log.w(TAG, "Anion state changed to: " + anion);
                }
                break;
            case TOGGLE_ESP:
                var espState = getUpdatedData(CarConstants.CAR_DRIVE_SETTING_ESP_ENABLE.getValue());
                if (espState != null) {
                    boolean esp = espState.equals("1");
                    esp = !esp;
                    updateData(CarConstants.CAR_DRIVE_SETTING_ESP_ENABLE.getValue(), esp ? "1" : "0");
                    Log.w(TAG, "ESP state changed to: " + esp);
                }
                break;
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
                String packageName = sharedPreferences.getString(button == 1 ? SharedPreferencesKeys.STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_1.getKey() : SharedPreferencesKeys.STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_2.getKey(), "");
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
            String currentConfig = ShizukuUtils.runCommandAndGetOutput(new String[]{"settings", "get", "system", "bean_sw_custom_key1_config"}).trim();
            Log.w(TAG, "Current steering wheel button 1 config: " + currentConfig);
            saveOriginalSteeringWheelButtonConfig(
                    SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_1_ACTION_ORIGINAL,
                    currentConfig,
                    "1"
            );
            ShizukuUtils.runCommandAndGetOutput(new String[]{"settings", "put", "system", "bean_sw_custom_key1_config", "99"});
        } catch (Exception e) {
            Log.e(TAG, "Error disabling native steering wheel custom buttons", e);
        }
    }

    public void enableSteeringWheelButton2Integration() {
        try {
            String currentConfig = ShizukuUtils.runCommandAndGetOutput(new String[]{"settings", "get", "system", "bean_sw_custom_key2_config"}).trim();
            Log.w(TAG, "Current steering wheel button 2 config: " + currentConfig);
            saveOriginalSteeringWheelButtonConfig(
                    SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_2_ACTION_ORIGINAL,
                    currentConfig,
                    "2"
            );
            ShizukuUtils.runCommandAndGetOutput(new String[]{"settings", "put", "system", "bean_sw_custom_key2_config", "99"});
        } catch (Exception e) {
            Log.e(TAG, "Error disabling native steering wheel custom buttons", e);
        }
    }

    public void disableNativeSteeringWheelButton1() {
        try {
            String originalConfig = getOriginalSteeringWheelButtonConfig(
                    SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_1_ACTION_ORIGINAL,
                    "1"
            );
            if (originalConfig == null)
                return;
            Log.w(TAG, "Restoring steering wheel button 1 config to: " + originalConfig);
            ShizukuUtils.runCommandAndGetOutput(new String[]{"settings", "put", "system", "bean_sw_custom_key1_config", originalConfig});
        } catch (Exception e) {
            Log.e(TAG, "Error restoring native steering wheel custom button 1", e);
        }
    }

    public void disableNativeSteeringWheelButton2() {
        try {
            String originalConfig = getOriginalSteeringWheelButtonConfig(
                    SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_2_ACTION_ORIGINAL,
                    "2"
            );
            if (originalConfig == null)
                return;
            Log.w(TAG, "Restoring steering wheel button 2 config to: " + originalConfig);
            ShizukuUtils.runCommandAndGetOutput(new String[]{"settings", "put", "system", "bean_sw_custom_key2_config", originalConfig});
        } catch (Exception e) {
            Log.e(TAG, "Error restoring native steering wheel custom button 2", e);
        }
    }

    private void saveOriginalSteeringWheelButtonConfig(
            SharedPreferencesKeys key,
            String currentConfig,
            String buttonLabel
    ) {
        if (!isNativeSteeringWheelButtonConfig(currentConfig)) {
            Log.w(TAG, "Skipping original steering wheel button " + buttonLabel + " capture for integration config: " + currentConfig);
            return;
        }

        sharedPreferences.edit().putString(key.getKey(), currentConfig).apply();
        Log.w(TAG, "Saved original steering wheel button " + buttonLabel + " config: " + currentConfig);
    }

    private String getOriginalSteeringWheelButtonConfig(SharedPreferencesKeys key, String buttonLabel) {
        if (!sharedPreferences.contains(key.getKey())) {
            Log.w(TAG, "No original steering wheel button " + buttonLabel + " config saved; keeping current native setting");
            return null;
        }

        String originalConfig = sharedPreferences.getString(key.getKey(), null);
        if (!isNativeSteeringWheelButtonConfig(originalConfig)) {
            Log.w(TAG, "Ignoring invalid original steering wheel button " + buttonLabel + " config: " + originalConfig);
            return null;
        }

        return originalConfig;
    }

    private boolean isNativeSteeringWheelButtonConfig(String config) {
        if (config == null) return false;

        String normalized = config.trim();
        if (normalized.isEmpty() || normalized.equals("99")) return false;
        if (normalized.equalsIgnoreCase("null") || normalized.equalsIgnoreCase("undefined")) return false;

        try {
            int value = Integer.parseInt(normalized);
            return value >= 0 && value < 99;
        } catch (NumberFormatException e) {
            return false;
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
            ClusterMsgData msg = new ClusterMsgData();
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
        ClusterMsgData msg = new ClusterMsgData();
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
            String[] allKeys = getCombinedKeys();
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

        boolean shouldSuspend = hvacKeysToSuspend.contains(key);
        if (shouldSuspend) {
            ensureHvacSuspended(key);
        }

        try {
            controlService.request("cmd.common.request.set", key, value);
        } catch (RemoteException e) {
            Log.e(TAG, "Error updating data", e);
        }

        if (shouldSuspend) {
            scheduleHvacResumption();
        }
    }

    public void injectCarKeyEvent(int keyCode, int injectMode) {
        if (inputService == null) {
            Log.e(TAG, "InputService not initialized, cannot inject key event");
            return;
        }
        try {
            long now = android.os.SystemClock.uptimeMillis();
            android.view.KeyEvent down = new android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN, keyCode, 0);
            inputService.injectKeyEvent(down, injectMode);
            android.view.KeyEvent up = new android.view.KeyEvent(now, now + 50, android.view.KeyEvent.ACTION_UP, keyCode, 0);
            inputService.injectKeyEvent(up, injectMode);
            Log.w(TAG, "Injected car key event: " + keyCode);
        } catch (RemoteException e) {
            Log.e(TAG, "Error injecting key event", e);
        }
    }

    private void ensureHvacSuspended(String triggerKey) {
        if (resumeHvacRunnable != null) {
            backgroundHandler.removeCallbacks(resumeHvacRunnable);
            resumeHvacRunnable = null;
        }

        if (!isHvacSuspended) {
            if (isHvacAppInForeground()) {
                Log.w(TAG, "HVAC app is in foreground, skipping suspension for key: " + triggerKey);
                return;
            }

            Log.w(TAG, "Suspending HVAC app due to key: " + triggerKey);
            ShizukuUtils.runCommandAndGetOutput(new String[]{"pm", "disable-user", "--user", "0", HVAC_PACKAGE_NAME});
            ShizukuUtils.runCommandAndGetOutput(new String[]{"am", "force-stop", HVAC_PACKAGE_NAME});
            isHvacSuspended = true;
            // Short sleep to ensure the app is fully stopped before the car command is sent
            SystemClock.sleep(150);
        }
    }

    private boolean isHvacAppInForeground() {
        try {
            // Check if the HVAC app is currently resumed (more reliable than window focus on some units)
            String output = ShizukuUtils.runCommandAndGetOutput(new String[]{"sh", "-c", "dumpsys activity activities | grep ResumedActivity"});
            boolean isForeground = output != null && output.contains(HVAC_PACKAGE_NAME);
            if (isForeground) {
                Log.w(TAG, "Detection: HVAC app IS resumed in foreground");
            }
            return isForeground;
        } catch (Exception e) {
            Log.e(TAG, "Error checking HVAC visibility", e);
        }
        return false;
    }

    private void scheduleHvacResumption() {
        if (resumeHvacRunnable != null) {
            backgroundHandler.removeCallbacks(resumeHvacRunnable);
        }

        resumeHvacRunnable = () -> {
            Log.w(TAG, "Resuming HVAC app after inactivity");
            ShizukuUtils.runCommandAndGetOutput(new String[]{"pm", "enable", HVAC_PACKAGE_NAME});
            isHvacSuspended = false;
            resumeHvacRunnable = null;
        };

        backgroundHandler.postDelayed(resumeHvacRunnable, HVAC_RESUME_DELAY_MS);
    }

    public Map<String, String> getAllCurrentCachedData() {
        return new HashMap<>(dataCache);
    }

    private void OnDataChanged(String key, String value) {
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
                float speedValue = Float.parseFloat(getUpdatedData(CarConstants.CAR_BASIC_VEHICLE_SPEED.getValue()));
                String currentGear = getUpdatedData(CarConstants.CAR_BASIC_GEAR_STATUS.getValue());
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
                if (currentSpeed <= 10 && (closeWindowDueToeSpeed || closeSunroofDueToeSpeed)) {
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
                    if (isMaxAcActive) {
                        cancelMaxAcMode();
                    }
                } else {
                    boolean disableBluetoothOnPowerOff = sharedPreferences.getBoolean(SharedPreferencesKeys.DISABLE_BLUETOOTH_ON_POWER_OFF.getKey(), false);
                    boolean bluetoothStateOnPowerOff = sharedPreferences.getBoolean(SharedPreferencesKeys.BLUETOOTH_STATE_ON_POWER_OFF.getKey(), false);
                    if (disableBluetoothOnPowerOff && bluetoothStateOnPowerOff && !currentBluetoothState()) {
                        enableBluetooth();
                    }
                    if (sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_MAX_AC_ON_UNLOCK.getKey(), false)) {
                        if (!isMaxAcActive) enableMaxAcOn();
                    }
                    if (sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_OPEN_SUNROOF_CURTAIN_ON_START.getKey(), false)) {
                        autoOpenSunroofCurtain();
                    }
                }
            } else if (key.equals(CarConstants.CAR_HVAC_POWER_MODE.getValue()) && value.equals("1") && sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_SEAT_VENTILATION_ON_AC_ON.getKey(), false)) {
                updateData(CarConstants.CAR_COMFORT_SETTING_DRIVER_SEAT_VENTILATION_LEVEL.getValue(), "3");
            } else if (key.equals(CarConstants.CAR_HVAC_POWER_MODE.getValue()) && value.equals("0") && sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_SEAT_VENTILATION_ON_AC_ON.getKey(), false)) {
                updateData(CarConstants.CAR_COMFORT_SETTING_DRIVER_SEAT_VENTILATION_LEVEL.getValue(), "0");
            } else if (key.equals(CarConstants.CAR_BASIC_INSIDE_TEMP.getValue()) && sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_MAX_AC_ON_UNLOCK.getKey(), false)) {
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
            int sunRoofStatus = vehicle.getSkylightLevel(0);
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
            int sunRoofBlockStatus = vehicle.getShadeScreensLevel(0);
            if (sunRoofBlockStatus != 0) {
                vehicle.setShadeScreensLevel(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing shade screens", e);
        }
    }

    public void openSunRoofShade() {
        try {
            int sunRoofBlockStatus = vehicle.getShadeScreensLevel(0);
            if (sunRoofBlockStatus != 100) {
                vehicle.setShadeScreensLevel(100);
                Log.w(TAG, "Opening sunroof curtain");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error opening shade screens", e);
        }
    }

    private void autoOpenSunroofCurtain() {
        Calendar now = Calendar.getInstance();
        float outsideTemp = 99;
        int currentHour = now.get(Calendar.HOUR_OF_DAY);
        int currentMinute = now.get(Calendar.MINUTE);
        int currentTime = currentHour * 60 + currentMinute;

        int startHour = sharedPreferences.getInt(SharedPreferencesKeys.OPEN_SUNROOF_CURTAIN_START_HOUR.getKey(), 18);
        int startMinute = sharedPreferences.getInt(SharedPreferencesKeys.OPEN_SUNROOF_CURTAIN_START_MINUTE.getKey(), 0);
        int startTime = startHour * 60 + startMinute;

        int endHour = sharedPreferences.getInt(SharedPreferencesKeys.OPEN_SUNROOF_CURTAIN_END_HOUR.getKey(), 9);
        int endMinute = sharedPreferences.getInt(SharedPreferencesKeys.OPEN_SUNROOF_CURTAIN_END_MINUTE.getKey(), 0);
        int endTime = endHour * 60 + endMinute;

        boolean isTimeInRange = false;
        if (startTime < endTime) {
            isTimeInRange = currentTime >= startTime && currentTime < endTime;
        } else {
            // Wraps around midnight
            isTimeInRange = currentTime >= startTime || currentTime < endTime;
        }

        float maxTemp = sharedPreferences.getFloat(SharedPreferencesKeys.OPEN_SUNROOF_CURTAIN_MAX_TEMP.getKey(), -1f);
        if (maxTemp != -1f) {
            String outsideTempStr = getUpdatedData(CarConstants.CAR_BASIC_OUTSIDE_TEMP.getValue());
            if (outsideTempStr != null) {
                try {
                    outsideTemp = Float.parseFloat(outsideTempStr);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Error parsing outside temp for curtain check. Aborting curtain opening. ", e);
                    return;
                }
            }
        }

        if ((isTimeInRange) || (outsideTemp <= maxTemp)) {
            // Delay slightly to ensure services are fully ready or just triggering command
            backgroundHandler.postDelayed(this::openSunRoofShade, 2000);
        } else {
            if (!isTimeInRange) {
                Log.d(TAG, "Current time " + currentHour + ":" + currentMinute + " not in range for opening curtain");
            } else if (outsideTemp > maxTemp) {
                Log.w(TAG, "Outside temp " + outsideTemp + " > max configured " + maxTemp + ", not opening curtain");
            }
        }
    }

    public boolean isTurnLightOn() {
        String leftTurnLight = getData(CarConstants.CAR_BASIC_LEFT_TURN_SWITCH_STATUS.getValue());
        String rightTurnLight = getData(CarConstants.CAR_BASIC_RIGHT_TURN_SWITCH_STATUS.getValue());
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
            BluetoothManager bluetoothManager = (BluetoothManager) App.getContext().getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter bluetoothAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;
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
        } catch (NoSuchMethodError e) {
            // Fallback for Android versions where stopTethering(int, String) doesn't exist
            try {
                java.lang.reflect.Method m = connectivityManager.getClass().getMethod("stopTethering", int.class);
                m.invoke(connectivityManager, 0);
            } catch (Exception e2) {
                Log.e(TAG, "Error disabling Wi-Fi tether (fallback)", e2);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error disabling Wi-Fi", e);
        }
    }

    public void enableWifiTether() {
        try {
            ResultReceiver receiver = new ResultReceiver(new Handler(Looper.getMainLooper())) {
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
        isMaxAcActive = false;
        if (maxAcTimeoutRunnable != null) {
            backgroundHandler.removeCallbacks(maxAcTimeoutRunnable);
            maxAcTimeoutRunnable = null;
        }

        // Force POWER as 1 (ON) to ensure it stays ON after MAX AC finishes
        updateData(CarConstants.CAR_HVAC_POWER_MODE.getValue(), "1");
        updateData(CarConstants.CAR_HVAC_AC_ENABLE.getValue(), "1");

        // Restores previous AC state
        for (Map.Entry<String, String> entry : previousAcState.entrySet()) {
            if (entry.getValue() != null) {
                updateData(entry.getKey(), entry.getValue());
            }
        }
        previousAcState.clear();
        clearPersistedMaxAcState();
        dispatchServiceManagerEvent(ServiceManagerEventType.MAX_AUTO_AC_STATUS_CHANGED, 0);

    }

    public boolean isMaxAcActive() {
        return isMaxAcActive;
    }

    private void enableMaxAcOn() {
        enableMaxAcOnWithRetry(0);
    }

    private void enableMaxAcOnWithRetry(int retryCount) {
        try {
            String tempStr = getUpdatedData(CarConstants.CAR_BASIC_INSIDE_TEMP.getValue());
            if (tempStr == null) return;
            float currentTemp = Float.parseFloat(tempStr);
            
            if ((currentTemp >= 85.0f || currentTemp <= -40.0f) && retryCount < 5) {
                Log.w(TAG, "Invalid temp " + currentTemp + " at startup, delaying Max AC check... retry: " + retryCount);
                backgroundHandler.postDelayed(() -> enableMaxAcOnWithRetry(retryCount + 1), 1000);
                return;
            }

            float threshold = sharedPreferences.getFloat(SharedPreferencesKeys.MAX_AC_ON_UNLOCK_THRESHOLD.getKey(), 35.0f);
            if (currentTemp >= threshold && !isMaxAcActive) {

                tryRestoreMaxAcState();
                if (previousAcState.isEmpty()) {
                    String prevFan = getUpdatedData(CarConstants.CAR_HVAC_FAN_SPEED.getValue());
                    String prevDriverTemp = getUpdatedData(CarConstants.CAR_HVAC_DRIVER_TEMPERATURE.getValue());
                    String prevPassTemp = getUpdatedData(CarConstants.CAR_HVAC_PASS_TEMPERATURE.getValue());
                    String prevAuto = getUpdatedData(CarConstants.CAR_HVAC_AUTO_ENABLE.getValue());
                    String prevAnion = getUpdatedData(CarConstants.CAR_HVAC_ANION_ENABLE.getValue());
                    String prevAQS = getUpdatedData(CarConstants.CAR_HVAC_AQS_ENABLE.getValue());
                    String prevSync = getUpdatedData(CarConstants.CAR_HVAC_SYNC_ENABLE.getValue());
                    String prevComfortCurve = getUpdatedData(CarConstants.CAR_HVAC_SETTING_COMFORT_CURVE.getValue());
                    String prevCycleMode = getUpdatedData(CarConstants.CAR_HVAC_CYCLE_MODE.getValue());

                    previousAcState.put(CarConstants.CAR_HVAC_FAN_SPEED.getValue(), prevFan);
                    previousAcState.put(CarConstants.CAR_HVAC_DRIVER_TEMPERATURE.getValue(), prevDriverTemp);
                    previousAcState.put(CarConstants.CAR_HVAC_PASS_TEMPERATURE.getValue(), prevPassTemp);
                    previousAcState.put(CarConstants.CAR_HVAC_AUTO_ENABLE.getValue(), prevAuto);
                    previousAcState.put(CarConstants.CAR_HVAC_ANION_ENABLE.getValue(), prevAnion);
                    previousAcState.put(CarConstants.CAR_HVAC_AQS_ENABLE.getValue(), prevAQS);
                    previousAcState.put(CarConstants.CAR_HVAC_SYNC_ENABLE.getValue(), prevSync);
                    previousAcState.put(CarConstants.CAR_HVAC_SETTING_COMFORT_CURVE.getValue(), prevComfortCurve);
                    previousAcState.put(CarConstants.CAR_HVAC_CYCLE_MODE.getValue(), prevCycleMode);
                    persistMaxAcState();
                }

                updateData(CarConstants.CAR_HVAC_POWER_MODE.getValue(), "1");
                updateData(CarConstants.CAR_HVAC_AUTO_ENABLE.getValue(), "0");
                updateData(CarConstants.CAR_HVAC_FAN_SPEED.getValue(), "7");
                updateData(CarConstants.CAR_HVAC_DRIVER_TEMPERATURE.getValue(), "16.0");
                updateData(CarConstants.CAR_HVAC_PASS_TEMPERATURE.getValue(), "16.0");
                updateData(CarConstants.CAR_HVAC_SYNC_ENABLE.getValue(), "1");
                updateData(CarConstants.CAR_HVAC_SETTING_COMFORT_CURVE.getValue(), "2"); // Max Cold
                isMaxAcActive = true;
                dispatchServiceManagerEvent(ServiceManagerEventType.MAX_AUTO_AC_STATUS_CHANGED, 1);

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

    private void setOptimalAcCycleMode() {
        try {
            String inTempStr = getUpdatedData(CarConstants.CAR_BASIC_INSIDE_TEMP.getValue());
            String outTempStr = getUpdatedData(CarConstants.CAR_BASIC_OUTSIDE_TEMP.getValue());
            if (inTempStr == null || outTempStr == null) return;
            float inTemp = Float.parseFloat(inTempStr);
            float outTemp = Float.parseFloat(outTempStr);
            String desiredMode = (inTemp < outTemp) ? "1" : "0";
            updateData(CarConstants.CAR_HVAC_CYCLE_MODE.getValue(), desiredMode);
        } catch (Exception e) {
            Log.d(TAG, "Error trying to set optimal cycle mode: " + e.getMessage());
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
                String prevPassKey = CarConstants.CAR_HVAC_PASS_TEMPERATURE.getValue();
                float minTemp = 16.0f;
                float prevDriverTemp = (previousAcState.get(prevDriverKey) != null) ? Float.parseFloat(previousAcState.get(prevDriverKey)) : 22.0f;
                float prevPassTemp = (previousAcState.get(prevPassKey) != null) ? Float.parseFloat(previousAcState.get(prevPassKey)) : 22.0f;
                
                float newDriverTemp = prevDriverTemp - ((prevDriverTemp - minTemp) * factor);
                newDriverTemp = Math.min(20, newDriverTemp);
                
                float newPassTemp = prevPassTemp - ((prevPassTemp - minTemp) * factor);
                newPassTemp = Math.min(20, newPassTemp);

                updateData(CarConstants.CAR_HVAC_FAN_SPEED.getValue(), String.valueOf(newFan));
                updateData(prevDriverKey, String.format(Locale.US, "%.1f", newDriverTemp));
                updateData(prevPassKey, String.format(Locale.US, "%.1f", newPassTemp));
                
                // Enforce Power and AC Enable to ensure they stay ON during the process
                updateData(CarConstants.CAR_HVAC_POWER_MODE.getValue(), "1");
                updateData(CarConstants.CAR_HVAC_AC_ENABLE.getValue(), "1");

                setOptimalAcCycleMode();

                Log.d(TAG, "Max AC Smoothing: Temp=" + currentTemp + ", Factor=" + factor + ", Fan=" + newFan + ", DriverTemp=" + newDriverTemp);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in Max AC Smoothing logic", e);
        }
    }

    private void persistMaxAcState() {
        try {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("MAX_AC_ACTIVE_PERSISTED", true);
            JsonObject jsonObject = new JsonObject();
            for (Map.Entry<String, String> entry : previousAcState.entrySet()) {
                if (entry.getValue() != null) {
                    jsonObject.addProperty(entry.getKey(), entry.getValue());
                }
            }
            editor.putString("MAX_AC_PREVIOUS_STATE", jsonObject.toString());
            editor.apply();
            Log.w(TAG, "Persisted AC MAX state");
        } catch (Exception e) {
            Log.e(TAG, "Error persisting AC MAX state", e);
        }
    }

    private void clearPersistedMaxAcState() {
        try {
            sharedPreferences.edit()
                    .remove("MAX_AC_ACTIVE_PERSISTED")
                    .remove("MAX_AC_PREVIOUS_STATE")
                    .apply();
            Log.w(TAG, "Cleared persisted AC MAX state");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing persisted AC MAX state", e);
        }
    }

    private void tryRestoreMaxAcState() {
        try {
            if (sharedPreferences.getBoolean("MAX_AC_ACTIVE_PERSISTED", false)) {
                String jsonStr = sharedPreferences.getString("MAX_AC_PREVIOUS_STATE", null);
                if (jsonStr != null) {
                    JsonObject jsonObject = new Gson().fromJson(jsonStr, JsonObject.class);
                    previousAcState.clear();
                    for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                        previousAcState.put(entry.getKey(), entry.getValue().getAsString());
                    }
                    isMaxAcActive = true;
                    Log.w(TAG, "Restored AC MAX state from persistence");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error restoring AC MAX state", e);
            clearPersistedMaxAcState(); // Clear corrupted state
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
            String currentUser = sharedPreferences.getString(SharedPreferencesKeys.CURRENT_USER.getKey(), "");

            Log.w(TAG, "Switching user to: " + userId);

            if (!currentUser.equals(userId)) {
                try {
                    saveCarSettingsForUser(currentUser);
                } catch (Exception e) {
                    Log.e(TAG, "Error saving settings for user: " + currentUser, e);
                }
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

    public void saveCarSettingsForUser(String userId) {
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
        JsonObject jsonObject = new JsonObject();
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
        String totalOdometer = getData(CarConstants.CAR_BASIC_TOTAL_ODOMETER.getValue());
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
                String[] allKeys = getCombinedKeys();
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
            String engineState = getData(CarConstants.CAR_BASIC_ENGINE_STATE.getValue());
            if (engineState == null || engineState.isEmpty()) {
                Log.w(TAG, "[HavalDev] Engine state unavailable during visibility check; defaulting main screen to ON");
                return true;
            }

            return !engineState.equals("-1") &&
                    !engineState.equals("10") &&
                    !engineState.equals("14") &&
                    !engineState.equals("15");
        } catch (Exception e) {
            Log.w(TAG, "[HavalDev] Failed to read engine state during visibility check; defaulting main screen to ON", e);
            return true;
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
}
