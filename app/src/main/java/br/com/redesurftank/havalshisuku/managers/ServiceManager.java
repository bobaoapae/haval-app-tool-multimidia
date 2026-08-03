package br.com.redesurftank.havalshisuku.managers;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.net.wifi.WifiManager;
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
import java.util.Set;

import br.com.redesurftank.App;
import br.com.redesurftank.havalshisuku.diagnostics.ClusterPersistentEventLogger;
import br.com.redesurftank.havalshisuku.listeners.IDataChanged;
import br.com.redesurftank.havalshisuku.listeners.IServiceManagerEvent;
import br.com.redesurftank.havalshisuku.models.CarConstants;
import br.com.redesurftank.havalshisuku.models.CarInfo;
import br.com.redesurftank.havalshisuku.models.MainUiManager;
import br.com.redesurftank.havalshisuku.models.ServiceManagerEventType;
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys;
import br.com.redesurftank.havalshisuku.models.SteeringWheelClimateCommandType;
import br.com.redesurftank.havalshisuku.models.SteeringWheelCustomActionType;
import br.com.redesurftank.havalshisuku.models.screens.Screen;
import br.com.redesurftank.havalshisuku.services.BottomBarService;
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
            CarConstants.CAR_HVAC_PANEL_DISPLAY_NOTIFY,
            CarConstants.CAR_HVAC_SETTING_COMFORT_CURVE,
            CarConstants.CAR_IPK_SETTING_BRIGHTNESS_CONFIG,
            CarConstants.SYS_AVM_AUTO_PREVIEW_ENABLE,
            CarConstants.SYS_AVM_PREVIEW_STATUS,
            CarConstants.SYS_BASIC_AUDIO_SOURCE_APP,
            CarConstants.SYS_RADIO_CUR_CHANNEL_INFO,
            CarConstants.SYS_RADIO_PLAY_STATE,
            CarConstants.SYS_RADIO_RDS_CUR_CHANNEL_INFO,
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
            CarConstants.CAR_IPK_LIGHT_FUEL_LOW
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
            CarConstants.CAR_EV_SETTING_POWER_RESERVE_CONFIG,
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
    // ===== Dados móveis do carro (controle: master + regras) =====
    private volatile Runnable mobileDataAutoblockRunnable;
    private static final long MOBILE_DATA_CHECK_MS = 15 * 1000L; // 15s: pega consumo/WiFi/projeção
    private android.net.ConnectivityManager.NetworkCallback mobileDataWifiCallback;
    private BroadcastReceiver mobileDataTetherReceiver;
    // Monitor periódico do % do HEV Prioritário: reaplica o valor salvo mesmo se algum evento
    // (power-on/entrar no modo/carro resetar) tiver sido perdido — ex.: app subiu tarde no boot.
    private static final long HEV_SOC_MONITOR_INTERVAL_MS = 30000L;
    private volatile Runnable hevSocMonitorRunnable;
    private IListener.Stub listener;
    private IInputListener.Stub inputListener;
    private IClusterCallback.Stub clusterCallback;
    private boolean servicesInitialized = false;
    private boolean isFridaInitialized = false;
    private final List<Runnable> pendingTasks = new ArrayList<>();
    private static long timeBootReceived;
    private long timeStartInitialization;
    private long timeInitialized;
    // Estado real processado do carro (power-off vs power-on). Usado pelos receivers de BT/hotspot
    // em vez do cache de driving_ready (que fica defasado no boot e fazia o BT ser re-desligado).
    private volatile boolean carPoweredOff = false;
    // Estado atual do hotspot (Wi-Fi AP), alimentado pelo receiver WIFI_AP_STATE_CHANGED e
    // semeado no init via getWifiApState(); usado pra saber se o hotspot estava ligado ao recolher/desligar.
    private volatile boolean wifiTetherEnabled = false;
    private static final long RADIO_RESTORE_RETRY_MS = 4000L;
    // 8 tentativas x 4s ≈ 28s: o tether (hotspot) pode demorar a subir no boot deste OEM.
    // O loop para assim que o rádio liga, então tentativas extras são de graça p/ o BT (rápido).
    private static final int RADIO_RESTORE_MAX_ATTEMPTS = 8;
    private CarInfo carInfo;
    private IIntelligentVehicleControlService controlService;
    // ===== Canal de controle resiliente =====
    // Sem isto, se o processo de controle do carro morre (reinicio do app OEM / OOM do
    // system_server), o app simplesmente PARA de falar com o carro - nada reconecta. Isto adiciona:
    // linkToDeath -> reconexao automatica; watchdog de 10s que faz ping e recupera se morto; e
    // recuperacao via re-registro de listener/chaves. PURAMENTE ADITIVO: o caminho normal de init
    // nao muda. Flag pra reversao de 1 linha.
    private static final boolean CONTROL_CHANNEL_RESILIENCE_ENABLED = true;
    private static final long CONTROL_CHANNEL_WATCHDOG_MS = 10000L;
    private volatile boolean recoveringControlChannel = false;
    private volatile int controlChannelRecoveryCount = 0;
    private IBinder.DeathRecipient controlDeathRecipient;
    private volatile Runnable controlChannelWatchdogRunnable;
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
    private static final int[] CLUSTER_CARD_SEQUENCE = new int[] {0, 1, 3};
    private long lastClusterInputAtMs = 0L;
    private int lastClusterInputKeyCode = -1;
    private String lastClusterInputKeyName = "";
    private static final long CLUSTER_INPUT_DEDUP_WINDOW_MS = 220L;
    private int lastHandledClusterInputKeyCode = -1;
    private long lastHandledClusterInputAtMs = 0L;
    private long lastSyntheticClusterCardNavigationAtMs = 0L;
    private int lastSyntheticClusterCardTarget = -1;
    private static final long STEERING_WHEEL_PROJECTION_TOGGLE_DEDUP_WINDOW_MS = 800L;
    private static final long STEERING_WHEEL_DASHBOARD_TOGGLE_DEDUP_WINDOW_MS = 800L;
    private static final long STEERING_WHEEL_CLIMATE_COMMAND_DEDUP_WINDOW_MS = 800L;
    private int lastDashboardToggleButton = -1;
    private long lastDashboardToggleAtMs = 0L;
    private static final int[] INPUT_LISTENER_KEY_CODES = new int[] {
            -1
    };
    private int lastProjectionDisplayToggleButton = -1;
    private long lastProjectionDisplayToggleAtMs = 0L;
    private int lastClimateCommandButton = -1;
    private long lastClimateCommandAtMs = 0L;
    private final Map<String, String> previousAcState = new HashMap<>();
    private boolean isMaxAcActive = false;
    private Runnable maxAcTimeoutRunnable;

    private void logPersistentClusterEvent(String event, String detail) {
        ClusterPersistentEventLogger.logText(event, detail);
    }

    private void logPersistentClusterEvent(String event, Map<String, ?> details) {
        ClusterPersistentEventLogger.log(event, details);
    }

    private Map<String, Object> persistentEventDetails(Object... values) {
        Map<String, Object> details = new HashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            Object key = values[i];
            if (key != null) {
                details.put(String.valueOf(key), values[i + 1]);
            }
        }
        return details;
    }

    // Counter-pulse for `bean.pui.scene_notify`:
    // the native Its_IntelligentVehicleControlService raises this signal to value 8
    // (SCENE_BACKCAMERA) when AVM/backup camera activates, which makes the CarPlay
    // host's VideoModel suspend the cluster decoder (priority 7 wins over CarPlay's
    // priority 1) and the cluster goes black even though the camera itself never
    // physically occupies display 3. The camera is shown on display 0 by a separate
    // channel (sys.avm.preview_status), so re-asserting scene_notify=0 keeps the
    // cluster's CarPlay video alive without affecting the camera feed on display 0.
    // We only counter-pulse while CarPlay is alive on cluster 3 and only react to
    // signals that didn't originate from our own write.
    private static final String BEAN_PUI_SCENE_BACKCAMERA = "8";
    private static final String BEAN_PUI_SCENE_NEUTRAL = "0";
    private static final long SCENE_NOTIFY_COUNTER_PULSE_DELAY_MS = 70;
    private volatile boolean isSelfWritingSceneNotify = false;

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
            IBinder rawControlBinder = getSystemService("com.beantechs.intelligentvehiclecontrol");
            if (rawControlBinder == null) {
                Log.e(TAG, "IntelligentVehicleControlService binder not available");
                return false;
            }
            IBinder controlBinder = new ShizukuBinderWrapper(rawControlBinder);
            if (!controlBinder.pingBinder()) {
                Log.e(TAG, "IntelligentVehicleControlService binder not alive");
                return false;
            }
            controlService = IIntelligentVehicleControlService.Stub.asInterface(controlBinder);
            registerControlDeathRecipient();

            IBinder rawPoolBinder = getSystemService("com.beantechs.voice.adapter.VoiceAdapterService");
            if (rawPoolBinder == null) {
                Log.w(TAG, "VoiceAdapterService binder unavailable; continuing without vehicle/dvr/model binder pool");
            } else {
                IBinder poolBinder = new ShizukuBinderWrapper(rawPoolBinder);
                if (!poolBinder.pingBinder()) {
                    Log.w(TAG, "IBinderPool binder not alive; continuing without vehicle/dvr/model binder pool");
                } else {
                    IBinderPool pool = IBinderPool.Stub.asInterface(poolBinder);
                    IBinder vehicleBinder = pool.queryBinder(6);
                    if (vehicleBinder != null) {
                        vehicle = IVehicle.Stub.asInterface(new ShizukuBinderWrapper(vehicleBinder));
                    }
                    IBinder dvrBinder = pool.queryBinder(8);
                    if (dvrBinder != null) {
                        dvr = IDvr.Stub.asInterface(new ShizukuBinderWrapper(dvrBinder));
                    }
                    IBinder vehicleModelBinder = pool.queryBinder(13);
                    if (vehicleModelBinder != null) {
                        vehicleModel = IVehicleModel.Stub.asInterface(new ShizukuBinderWrapper(vehicleModelBinder));
                    }
                }
            }

            Intent clusterIntent = new Intent();
            clusterIntent.setComponent(new ComponentName("com.autolink.clusterservice", "com.autolink.clusterservice.ClusterService"));
            clusterCallback = new IClusterCallback.Stub() {
                @Override
                public void callbackMsg(int msgId, ClusterMsgData data) {
                    if (DisplayAppLauncher.INSTANCE.shouldLogAndroidAutoClusterCallbackProbe(msgId)) {
                        Log.w(
                                TAG,
                                "Android Auto cluster callback probe msgId="
                                        + msgId
                                        + " intValue="
                                        + data.getIntValue()
                        );
                    }
                    if (msgId == 133) {
                        int whichCard = data.getIntValue();
                        int previousCard = clusterCardView;
                        long now = SystemClock.uptimeMillis();
                        long sinceInputMs =
                                lastClusterInputAtMs == 0L
                                        ? -1L
                                        : now - lastClusterInputAtMs;
                        long sinceSyntheticMs =
                                lastSyntheticClusterCardNavigationAtMs == 0L
                                        ? -1L
                                        : now - lastSyntheticClusterCardNavigationAtMs;
                        if (ClusterCardSyncPolicy.shouldIgnoreNativeClusterCardChanged(
                                previousCard,
                                whichCard,
                                sinceInputMs,
                                lastClusterInputKeyCode,
                                sinceSyntheticMs,
                                lastSyntheticClusterCardTarget
                        )) {
                            Log.w(
                                    TAG,
                                    "Ignoring stale native cluster card change: "
                                            + previousCard
                                            + " -> "
                                            + whichCard
                                            + " lastInputKey="
                                            + lastClusterInputKeyName
                                            + "("
                                            + lastClusterInputKeyCode
                                            + ") sinceInputMs="
                                            + sinceInputMs
                                            + " syntheticTarget="
                                            + lastSyntheticClusterCardTarget
                                            + " sinceSyntheticMs="
                                            + sinceSyntheticMs
                            );
                            logPersistentClusterEvent(
                                    "native_cluster_card_ignored",
                                    "from=" + previousCard
                                            + " to=" + whichCard
                                            + " lastInputKey=" + lastClusterInputKeyName
                                            + "(" + lastClusterInputKeyCode + ")"
                                            + " sinceInputMs=" + sinceInputMs
                                            + " syntheticTarget=" + lastSyntheticClusterCardTarget
                                            + " sinceSyntheticMs=" + sinceSyntheticMs
                            );
                            return;
                        }
                        clusterCardView = whichCard;
                        dispatchServiceManagerEvent(ServiceManagerEventType.CLUSTER_CARD_CHANGED, clusterCardView);
                        Log.w(
                                TAG,
                                "Cluster card changed: "
                                        + previousCard
                                        + " -> "
                                        + whichCard
                                        + " lastInputKey="
                                        + lastClusterInputKeyName
                                        + "("
                                        + lastClusterInputKeyCode
                                        + ") sinceInputMs="
                                        + sinceInputMs
                        );
                        logPersistentClusterEvent(
                                "native_cluster_card_changed",
                                "from=" + previousCard
                                        + " to=" + whichCard
                                        + " lastInputKey=" + lastClusterInputKeyName
                                        + "(" + lastClusterInputKeyCode + ")"
                                        + " sinceInputMs=" + sinceInputMs
                        );
                    } else if (msgId == 134) {
                        if (sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION.getKey(), false)) {
                            if (data.getIntValue() == 2) {
                                sendHeartBeatToCluster();
                                startClusterHeartbeat();
                            }
                        }
                    } else if (msgId == 135) {
                        int val = data.getIntValue();
                        Log.w(TAG, "Cluster media command msgId=135 value=" + val);
                        logPersistentClusterEvent(
                                "cluster_media_command",
                                "msgId=135 value=" + val
                        );
                        if (DisplayAppLauncher.INSTANCE.handleAndroidAutoClusterMediaCommand(val)) {
                            Log.w(TAG, "Android Auto handled cluster media command msgId=135 value=" + val);
                            return;
                        }
                        if (sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION.getKey(), false)) {
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
                    Log.w(
                            TAG,
                            "InputService dispatch key="
                                    + keyEvent.getKeyCode()
                                    + "("
                                    + KeyEvent.keyCodeToString(keyEvent.getKeyCode())
                                    + ") action="
                                    + keyEvent.getAction()
                    );
                    if (DisplayAppLauncher.INSTANCE.shouldLogAndroidAutoMediaInputProbe(keyEvent.getKeyCode())) {
                        Log.w(
                                TAG,
                                "Android Auto media input probe key="
                                        + keyEvent.getKeyCode()
                                        + " action="
                                        + keyEvent.getAction()
                        );
                    }
                    if (DisplayAppLauncher.INSTANCE.handleAndroidAutoSteeringMediaKey(keyEvent.getKeyCode(), keyEvent.getAction())) {
                        Log.w(TAG, "Android Auto handled steering media key: " + keyEvent.getKeyCode());
                        return;
                    }
                    if (sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_STEERING_WHEEL_CUSTOM_BUTTONS.getKey(), false)) {
                        switch (keyEvent.getKeyCode()) {
                            case 517: onSteeringCustomShortPress(1); break;   // botao 1 curto
                            case 1031: onSteeringCustomShortPress(2); break;  // botao 2 curto
                            case 518: onSteeringCustomLongPress(1); break;    // botao 1 longo
                            case 1032: onSteeringCustomLongPress(2); break;   // botao 2 longo
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
                            case 1026:
                                key = Screen.Key.LEFT;
                                break;
                            case 1027:
                                key = Screen.Key.RIGHT;
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
                        if (key != null) {
                            lastClusterInputAtMs = SystemClock.uptimeMillis();
                            lastClusterInputKeyCode = keyEvent.getKeyCode();
                            lastClusterInputKeyName = key.name();
                            Log.w(
                                    TAG,
                                    "Cluster input key: "
                                            + lastClusterInputKeyName
                                            + "("
                                            + lastClusterInputKeyCode
                                            + ") action="
                                            + keyEvent.getAction()
                            );
                            logPersistentClusterEvent(
                                    "cluster_input_key",
                                    "key=" + lastClusterInputKeyName
                                            + "(" + lastClusterInputKeyCode + ")"
                                            + " action=" + keyEvent.getAction()
                                            + " currentCard=" + clusterCardView
                            );
                            dispatchServiceManagerEvent(
                                    ServiceManagerEventType.CLUSTER_INPUT_KEY,
                                    lastClusterInputKeyName,
                                    lastClusterInputKeyCode,
                                    keyEvent.getAction()
                            );
                            long now = SystemClock.uptimeMillis();
                            boolean duplicateClusterInput =
                                    lastHandledClusterInputKeyCode == keyEvent.getKeyCode()
                                            && now - lastHandledClusterInputAtMs <= CLUSTER_INPUT_DEDUP_WINDOW_MS;
                            if (!duplicateClusterInput) {
                                lastHandledClusterInputKeyCode = keyEvent.getKeyCode();
                                lastHandledClusterInputAtMs = now;
                                if (key == Screen.Key.LEFT || key == Screen.Key.RIGHT) {
                                    handleClusterCardNavigationKey(key);
                                } else {
                                    MainUiManager.getInstance().handleGeneralKeyEvents(key);
                                    if (key == Screen.Key.BACK) {
                                        dispatchServiceManagerEvent(ServiceManagerEventType.DISMISS_WARNING);
                                    }
                                }
                            } else {
                                Log.w(
                                        TAG,
                                        "Cluster input duplicate ignored: "
                                                + lastClusterInputKeyName
                                                + "("
                                                + lastClusterInputKeyCode
                                                + ") action="
                                                + keyEvent.getAction()
                                );
                                logPersistentClusterEvent(
                                        "cluster_input_duplicate_ignored",
                                        "key=" + lastClusterInputKeyName
                                                + "(" + lastClusterInputKeyCode + ")"
                                                + " action=" + keyEvent.getAction()
                                                + " currentCard=" + clusterCardView
                                );
                            }
                        }
                    }
                }
            };
            inputServiceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    inputService = IInputService.Stub.asInterface(service);
                    final IInputService connectedInputService = inputService;
                    new Thread(
                                    () -> {
                                        try {
                                            connectedInputService.registerKeyEventListener(
                                                    INPUT_LISTENER_KEY_CODES,
                                                    inputListener
                                            );
                                            Log.w(
                                                    TAG,
                                                    "InputService listener registered keyCodes="
                                                            + Arrays.toString(INPUT_LISTENER_KEY_CODES)
                                            );
                                        } catch (Exception e) {
                                            Log.e(
                                                    TAG,
                                                    "InputService explicit listener registration failed; trying wildcard",
                                                    e
                                            );
                                            try {
                                                connectedInputService.registerKeyEventListener(
                                                        new int[]{-1},
                                                        inputListener
                                                );
                                                Log.w(TAG, "InputService wildcard listener registered");
                                            } catch (Exception fallbackError) {
                                                Log.e(TAG, "InputService wildcard listener registration failed", fallbackError);
                                            }
                                        }
                                    },
                                    "InputServiceRegister"
                            )
                            .start();
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
            // Localização: usada pelo brilho automático por nascer/pôr do sol (getLastKnownLocation).
            ShizukuUtils.runCommandAndGetOutput(new String[]{"pm", "grant", context.getPackageName(), "android.permission.ACCESS_FINE_LOCATION"});
            controlService.registerDataChangedListener(context.getPackageName(), listener);
            controlService.addListenerKey(App.getContext().getPackageName(), getCombinedKeys());
            startControlChannelWatchdog(); // #111: canal resiliente (ping 10s + recupera se o binder morre)
            initMobileDataGuard();         // #112: dados móveis (reaplica no boot + check do auto-bloqueio)

            IBinder rawConnectivityBinder = getSystemService(Context.CONNECTIVITY_SERVICE);
            if (rawConnectivityBinder != null) {
                IBinder connectivityBinder = new ShizukuBinderWrapper(rawConnectivityBinder);
                connectivityManager = IConnectivityManager.Stub.asInterface(connectivityBinder);
            } else {
                Log.w(TAG, "Connectivity service binder unavailable; tethering controls will be skipped");
            }

            IntentFilter bluetoothFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            bluetoothFilter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
            context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction() == null) return;
                    if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED) || intent.getAction().equals(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)) {
                        int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                        if (state == BluetoothAdapter.STATE_ON) {
                            // Só re-desliga se o carro está REALMENTE desligado (estado já processado),
                            // não pelo cache de driving_ready (que fica defasado no boot e matava o BT).
                            if (carPoweredOff && sharedPreferences.getBoolean(SharedPreferencesKeys.DISABLE_BLUETOOTH_ON_POWER_OFF.getKey(), false)) {
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
                        int apState = intent.getIntExtra("wifi_state", 0);
                        // 13 = WIFI_AP_STATE_ENABLED, 11 = WIFI_AP_STATE_DISABLED
                        if (apState == 13) {
                            wifiTetherEnabled = true;
                            if (carPoweredOff && sharedPreferences.getBoolean(SharedPreferencesKeys.DISABLE_HOTSPOT_ON_POWER_OFF.getKey(), false)) {
                                disableWifiTether();
                            }
                        } else if (apState == 11) {
                            wifiTetherEnabled = false;
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
        // Semeia o estado atual do hotspot (o receiver WIFI_AP só dispara em MUDANÇA; se já estava
        // ligado antes do serviço subir, a flag ficaria falsa).
        wifiTetherEnabled = currentWifiTetherState();
        Log.w(TAG, "Services initialized successfully");
        // HEV Prioritário: no boot o carro costuma resetar o % (ex.: 45->80) e o app pode subir
        // depois do evento -> reaplica o valor salvo no init e depois monitora continuamente.
        backgroundHandler.postDelayed(() -> applyHevSocTargetIfActive("INIT+8s"), 8000);
        backgroundHandler.postDelayed(() -> applyHevSocTargetIfActive("INIT+18s"), 18000);
        startHevSocMonitor();
        backgroundHandler.post(() -> {
            try {
                ShizukuUtils.runCommandAndGetOutput(new String[]{"sh", "-c", "settings put global enable_freeform_support 1"});
                ShizukuUtils.runCommandAndGetOutput(new String[]{"sh", "-c", "settings put global force_resizable_activities 1"});
            } catch (Exception e) {}
        });
        new Handler(Looper.getMainLooper()).post(() -> ProjectorManager.getInstance().initialize());
        return true;
    }

    public void ensureSteeringWheelButtonIntegration() {
        if (sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_STEERING_WHEEL_CUSTOM_BUTTONS.getKey(), false)) {
            // habilita o botao se QUALQUER toque (curto/duplo/longo) tiver acao configurada
            boolean button1Used = steeringActionConfigured(1, "SHORT") || steeringActionConfigured(1, "DOUBLE") || steeringActionConfigured(1, "LONG");
            boolean button2Used = steeringActionConfigured(2, "SHORT") || steeringActionConfigured(2, "DOUBLE") || steeringActionConfigured(2, "LONG");
            Log.w(TAG, "Ensuring steering wheel button integration. Button 1 used: " + button1Used + ", Button 2 used: " + button2Used);
            if (button1Used) {
                enableSteeringWheelButton1Integration();
            } else {
                disableNativeSteeringWheelButton1();
            }
            if (button2Used) {
                enableSteeringWheelButton2Integration();
            } else {
                disableNativeSteeringWheelButton2();
            }
        } else {
            Log.w(TAG, "Steering wheel button integration disabled, restoring native functions");
            disableNativeSteeringWheelButton1();
            disableNativeSteeringWheelButton2();
        }

    }

    // ===== Toques nos botoes personalizados do volante: curto / duplo / longo =====
    private static final long STEERING_DOUBLE_WINDOW_MS = 350L;    // janela pra detectar o 2o toque
    private static final long STEERING_PRESS_DEBOUNCE_MS = 120L;   // ignora repique do mesmo toque
    private static final long STEERING_LONG_OPEN_DELAY_MS = 350L;  // deixa a config do OEM abrir antes da nossa acao
    private final Runnable[] steeringPendingSingle = new Runnable[2];
    private final long[] steeringLastPressAtMs = {0L, 0L};

    private String steeringActionKey(int button, String tapType) {
        SharedPreferencesKeys key;
        if (button == 1) {
            key = tapType.equals("DOUBLE") ? SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_1_ACTION_DOUBLE
                    : tapType.equals("LONG") ? SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_1_ACTION_LONG
                    : SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_1_ACTION;
        } else {
            key = tapType.equals("DOUBLE") ? SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_2_ACTION_DOUBLE
                    : tapType.equals("LONG") ? SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_2_ACTION_LONG
                    : SharedPreferencesKeys.STEERING_WHEEL_CUSTOM_BUTON_2_ACTION;
        }
        return sharedPreferences.getString(key.getKey(), SteeringWheelCustomActionType.DEFAULT.getKey());
    }

    private String steeringOpenAppPackageKey(int button, String tapType) {
        if (button == 1) {
            return (tapType.equals("DOUBLE") ? SharedPreferencesKeys.STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_1_DOUBLE
                    : tapType.equals("LONG") ? SharedPreferencesKeys.STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_1_LONG
                    : SharedPreferencesKeys.STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_1).getKey();
        }
        return (tapType.equals("DOUBLE") ? SharedPreferencesKeys.STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_2_DOUBLE
                : tapType.equals("LONG") ? SharedPreferencesKeys.STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_2_LONG
                : SharedPreferencesKeys.STEERING_WHEEL_OPEN_APP_PACKAGE_BUTTON_2).getKey();
    }

    private boolean steeringActionConfigured(int button, String tapType) {
        String k = steeringActionKey(button, tapType);
        return k != null
                && !k.equals(SteeringWheelCustomActionType.DEFAULT.getKey())
                && !k.equals(SteeringWheelCustomActionType.DEFAULT.name());
    }

    // Toque curto. Se houver acao de DUPLO configurada, espera ~350ms pra ver se vem um 2o toque
    // (senao dispara o curto na hora, sem atraso). O 2o toque dentro da janela vira DUPLO.
    private void onSteeringCustomShortPress(int button) {
        final int idx = button - 1;
        long now = System.currentTimeMillis();
        synchronized (steeringPendingSingle) {
            if (now - steeringLastPressAtMs[idx] < STEERING_PRESS_DEBOUNCE_MS) {
                return; // repique do mesmo toque
            }
            steeringLastPressAtMs[idx] = now;
            if (steeringPendingSingle[idx] != null) {
                backgroundHandler.removeCallbacks(steeringPendingSingle[idx]);
                steeringPendingSingle[idx] = null;
                handleSteeringWheelCustomButton(steeringActionKey(button, "DOUBLE"), button, "DOUBLE");
                return;
            }
            if (!steeringActionConfigured(button, "DOUBLE")) {
                handleSteeringWheelCustomButton(steeringActionKey(button, "SHORT"), button, "SHORT");
                return;
            }
            Runnable r = () -> {
                synchronized (steeringPendingSingle) {
                    steeringPendingSingle[idx] = null;
                }
                handleSteeringWheelCustomButton(steeringActionKey(button, "SHORT"), button, "SHORT");
            };
            steeringPendingSingle[idx] = r;
            backgroundHandler.postDelayed(r, STEERING_DOUBLE_WINDOW_MS);
        }
    }

    // Toque longo. O OEM abre a tela de config do volante; esperamos um pouco e executamos a acao.
    // Se for OPEN_APP, o app abre POR CIMA da config; senao mandamos BACK pra fechar a config.
    private void onSteeringCustomLongPress(int button) {
        if (!steeringActionConfigured(button, "LONG")) return; // sem acao de longo -> deixa a config do OEM
        final String actionKey = steeringActionKey(button, "LONG");
        final boolean isOpenApp =
                SteeringWheelCustomActionType.Companion.fromKey(actionKey) == SteeringWheelCustomActionType.OPEN_APP;
        backgroundHandler.postDelayed(() -> {
            handleSteeringWheelCustomButton(actionKey, button, "LONG");
            if (!isOpenApp) {
                try {
                    ShizukuUtils.runCommandAndGetOutput(new String[]{"input", "keyevent", "4"});
                } catch (Exception ignored) {
                }
            }
        }, STEERING_LONG_OPEN_DELAY_MS);
    }

    private void handleSteeringWheelCustomButton(String string, int button, String tapType) {
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
                String packageName = sharedPreferences.getString(steeringOpenAppPackageKey(button, tapType), "");
                if (!packageName.isEmpty()) {
                    Intent launchIntent = App.getContext().getPackageManager().getLaunchIntentForPackage(packageName);
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        App.getContext().startActivity(launchIntent);
                        Log.w(TAG, "Launching app: " + packageName);
                        DisplayAppLauncher.INSTANCE.preserveCarPlayClusterContract("SERVICE_OPEN_APP_" + packageName);
                        DisplayAppLauncher.INSTANCE.preserveAndroidAutoClusterContract("SERVICE_OPEN_APP_" + packageName);
                    } else {
                        Log.e(TAG, "App not found: " + packageName);
                    }
                }
                break;
            case CLIMATE_COMMAND:
                handleSteeringWheelClimateCommand(button);
                break;
            case TOGGLE_PROJECTION_DISPLAY:
                handleSteeringWheelProjectionDisplayToggle(button);
                break;
            case TOGGLE_IMPULSE_DASHBOARD:
                handleSteeringWheelImpulseDashboardToggle(button);
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
                        if (DisplayAppLauncher.INSTANCE.isAndroidAutoOnDisplay(3)) {
                            Log.w(TAG, "Skipping projection guard for OPEN_AVM_ONCE_OPEN because Android Auto is active on D3");
                            DisplayAppLauncher.INSTANCE.pulseAndroidAutoFocusDuringNativePanel("OPEN_AVM_ONCE_OPEN");
                        } else {
                            DisplayAppLauncher.INSTANCE.preserveCarPlayClusterContract("OPEN_AVM_ONCE_OPEN");
                            DisplayAppLauncher.INSTANCE.preserveAndroidAutoNativePanelContract("OPEN_AVM_ONCE_OPEN");
                        }
                    } else {
                        delayNextAVM = false;
                        dvr.setAVM(0);
                        Log.w(TAG, "Camera AVM closed");
                        if (DisplayAppLauncher.INSTANCE.isAndroidAutoOnDisplay(3)) {
                            Log.w(TAG, "Skipping projection guard for OPEN_AVM_ONCE_CLOSE because Android Auto is active on D3");
                            DisplayAppLauncher.INSTANCE.pulseAndroidAutoFocusAfterNativePanelExit("OPEN_AVM_ONCE_CLOSE");
                        } else {
                            DisplayAppLauncher.INSTANCE.preserveCarPlayClusterContract("OPEN_AVM_ONCE_CLOSE");
                            DisplayAppLauncher.INSTANCE.preserveAndroidAutoNativePanelContract("OPEN_AVM_ONCE_CLOSE");
                        }
                    }
                } catch (RemoteException e) {
                    Log.w(TAG, "Error to launch AVM camera");
                }
                break;
        }
    }

    private void handleSteeringWheelClimateCommand(int button) {
        long now = SystemClock.uptimeMillis();
        boolean duplicateCommand =
                lastClimateCommandButton == button
                        && now - lastClimateCommandAtMs <= STEERING_WHEEL_CLIMATE_COMMAND_DEDUP_WINDOW_MS;
        if (duplicateCommand) {
            Log.w(TAG, "Ignoring duplicate climate command from steering wheel button " + button);
            return;
        }

        lastClimateCommandButton = button;
        lastClimateCommandAtMs = now;

        String commandKey = sharedPreferences.getString(
                button == 1
                        ? SharedPreferencesKeys.STEERING_WHEEL_CLIMATE_COMMAND_BUTTON_1.getKey()
                        : SharedPreferencesKeys.STEERING_WHEEL_CLIMATE_COMMAND_BUTTON_2.getKey(),
                SteeringWheelClimateCommandType.TOGGLE_AC.getKey()
        );
        if (commandKey == null) {
            commandKey = SteeringWheelClimateCommandType.TOGGLE_AC.getKey();
        }
        SteeringWheelClimateCommandType command = SteeringWheelClimateCommandType.Companion.fromKey(commandKey);
        if (command == null) {
            command = SteeringWheelClimateCommandType.TOGGLE_AC;
        }

        Log.w(TAG, "Executing steering wheel climate command from button " + button + ": " + command.getKey());
        cancelMaxAcMode();
        switch (command) {
            case TOGGLE_AC:
                toggleHvacBinaryValue(CarConstants.CAR_HVAC_AC_ENABLE.getValue(), "AC");
                break;
            case TOGGLE_AUTO:
                toggleHvacBinaryValue(CarConstants.CAR_HVAC_AUTO_ENABLE.getValue(), "Auto AC");
                break;
            case TOGGLE_POWER:
                toggleHvacBinaryValue(CarConstants.CAR_HVAC_POWER_MODE.getValue(), "HVAC power");
                break;
            case FRONT_DEFROST:
                toggleFrontDefrostAirflow(button);
                break;
        }
    }

    private void toggleFrontDefrostAirflow(int button) {
        String frontDefrostState = getUpdatedData(CarConstants.CAR_HVAC_FRONT_DEFROST_ENABLE.getValue());
        String blowerMode = getUpdatedData(CarConstants.CAR_HVAC_BLOWER_MODE.getValue());
        boolean frontDefrostActive = "1".equals(frontDefrostState) || "4".equals(blowerMode);

        if (frontDefrostActive) {
            updateData(CarConstants.CAR_HVAC_FRONT_DEFROST_ENABLE.getValue(), "0");
            if ("4".equals(blowerMode)) {
                updateData(CarConstants.CAR_HVAC_BLOWER_MODE.getValue(), "0");
            }
            Log.w(TAG, "Front defrost airflow disabled from steering wheel button " + button);
            return;
        }

        updateData(CarConstants.CAR_HVAC_POWER_MODE.getValue(), "1");
        updateData(CarConstants.CAR_HVAC_FRONT_DEFROST_ENABLE.getValue(), "1");
        updateData(CarConstants.CAR_HVAC_BLOWER_MODE.getValue(), "4");
        Log.w(TAG, "Front defrost airflow enabled from steering wheel button " + button);
    }

    private void toggleHvacBinaryValue(String key, String label) {
        String currentState = getUpdatedData(key);
        if (currentState == null) {
            Log.e(TAG, "Unable to toggle " + label + ": current value is null");
            return;
        }
        boolean enabled = currentState.equals("1");
        updateData(key, enabled ? "0" : "1");
        Log.w(TAG, label + " state changed to: " + !enabled);
    }

    private void handleClusterCardNavigationKey(Screen.Key key) {
        int currentCard = clusterCardView;
        if (!isKnownClusterCard(currentCard)) {
            currentCard = MainUiManager.getInstance().getCurrentCard();
        }
        if (!isKnownClusterCard(currentCard)) {
            currentCard = 0;
        }

        int currentIndex = indexOfClusterCard(currentCard);
        int direction = key == Screen.Key.RIGHT ? 1 : -1;
        int nextIndex = (currentIndex + direction + CLUSTER_CARD_SEQUENCE.length) % CLUSTER_CARD_SEQUENCE.length;
        int nextCard = CLUSTER_CARD_SEQUENCE[nextIndex];
        int previousCard = clusterCardView;
        clusterCardView = nextCard;
        lastSyntheticClusterCardNavigationAtMs = SystemClock.uptimeMillis();
        lastSyntheticClusterCardTarget = nextCard;

        Log.w(
                TAG,
                "Synthetic cluster card navigation: "
                        + currentCard
                        + " -> "
                        + nextCard
                        + " key="
                        + key
                        + " previousServiceCard="
                        + previousCard
        );
        logPersistentClusterEvent(
                "synthetic_cluster_card_navigation",
                "from=" + currentCard
                        + " to=" + nextCard
                        + " key=" + key
                        + " previousServiceCard=" + previousCard
        );
        dispatchServiceManagerEvent(ServiceManagerEventType.CLUSTER_CARD_CHANGED, clusterCardView);
    }

    private boolean isKnownClusterCard(int card) {
        return indexOfClusterCard(card) >= 0;
    }

    private int indexOfClusterCard(int card) {
        for (int i = 0; i < CLUSTER_CARD_SEQUENCE.length; i++) {
            if (CLUSTER_CARD_SEQUENCE[i] == card) {
                return i;
            }
        }
        return -1;
    }

    private void handleSteeringWheelProjectionDisplayToggle(int button) {
        long now = SystemClock.uptimeMillis();
        boolean duplicateToggle =
                lastProjectionDisplayToggleButton == button
                        && now - lastProjectionDisplayToggleAtMs <= STEERING_WHEEL_PROJECTION_TOGGLE_DEDUP_WINDOW_MS;
        if (duplicateToggle) {
            Log.w(TAG, "Ignoring duplicate projection display toggle from steering wheel button " + button);
            return;
        }

        lastProjectionDisplayToggleButton = button;
        lastProjectionDisplayToggleAtMs = now;
        DisplayAppLauncher.INSTANCE.toggleActiveProjectionDisplayFromSteeringWheel(
                "STEERING_WHEEL_TOGGLE_PROJECTION_BUTTON_" + button
        );
    }

    private void handleSteeringWheelImpulseDashboardToggle(int button) {
        long now = SystemClock.uptimeMillis();
        boolean duplicateToggle =
                lastDashboardToggleButton == button
                        && now - lastDashboardToggleAtMs <= STEERING_WHEEL_DASHBOARD_TOGGLE_DEDUP_WINDOW_MS;
        if (duplicateToggle) {
            Log.w(TAG, "Ignoring duplicate Impulse dashboard toggle from steering wheel button " + button);
            return;
        }

        lastDashboardToggleButton = button;
        lastDashboardToggleAtMs = now;
        boolean handled = BottomBarService.requestImpulseDashboardToggleFromSteeringWheel(
                "STEERING_WHEEL_TOGGLE_DASHBOARD_BUTTON_" + button
        );
        Log.w(TAG, "Impulse dashboard toggle from steering wheel button " + button + " handled=" + handled);
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
        if (!isControlServiceAlive()) return;
        try {
            String[] allKeys = getCombinedKeys();
            String[] currentValues = controlService.fetchDatas(allKeys);
            for (int i = 0; i < currentValues.length; i++) {
                OnDataChanged(allKeys[i], currentValues[i]);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error dispatching data", e);
        }
    }

    private boolean isControlServiceAlive() {
        IIntelligentVehicleControlService svc = controlService;
        if (svc == null) return false;
        try {
            if (!Shizuku.pingBinder()) return false;
            IBinder binder = svc.asBinder();
            return binder != null && binder.isBinderAlive();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Liga o linkToDeath no binder do servico de controle. Se o processo do controle do carro morre
     * (reinicio do app OEM / OOM do system_server), o binderDied dispara e agenda a recuperacao no
     * backgroundHandler (fora da main). Remove um recipient anterior antes, pra nao empilhar.
     */
    private void registerControlDeathRecipient() {
        if (!CONTROL_CHANNEL_RESILIENCE_ENABLED) return;
        try {
            IIntelligentVehicleControlService svc = controlService;
            if (svc == null) return;
            final IBinder binder = svc.asBinder();
            if (binder == null) return;
            if (controlDeathRecipient != null) {
                try { binder.unlinkToDeath(controlDeathRecipient, 0); } catch (Throwable ignored) {}
            }
            controlDeathRecipient = new IBinder.DeathRecipient() {
                @Override public void binderDied() {
                    logPersistentClusterEvent("control_channel_binder_died", persistentEventDetails("trigger", "binderDied"));
                    if (backgroundHandler != null) {
                        backgroundHandler.post(() -> recoverControlChannel("BINDER_DIED"));
                    } else {
                        recoverControlChannel("BINDER_DIED");
                    }
                }
            };
            binder.linkToDeath(controlDeathRecipient, 0);
        } catch (Throwable t) {
            Log.w(TAG, "registerControlDeathRecipient falhou: " + t.getMessage());
        }
    }

    /**
     * Re-adquire o binder do servico de controle do zero e re-registra listener + chaves + re-dispatch.
     * synchronized + guarda de reentrancia (recoveringControlChannel) pra nao rodar duas recuperacoes
     * ao mesmo tempo (watchdog + binderDied podem coincidir). Desregistra o listener antigo antes de
     * registrar o novo, pra nao duplicar. Cada etapa vai pro log persistente (sobrevive ao R8).
     */
    public synchronized void recoverControlChannel(String reason) {
        if (!CONTROL_CHANNEL_RESILIENCE_ENABLED) return;
        if (recoveringControlChannel) return;
        recoveringControlChannel = true;
        long t0 = SystemClock.uptimeMillis();
        try {
            logPersistentClusterEvent("control_channel_recover_start",
                    persistentEventDetails("reason", reason, "attempt", String.valueOf(controlChannelRecoveryCount + 1)));

            if (!Shizuku.pingBinder()) {
                throw new IllegalStateException("Shizuku indisponivel durante recuperacao do canal de controle");
            }
            Context context = App.getContext();

            // desregistra o listener antigo se o binder velho ainda responde (evita listener duplicado)
            IIntelligentVehicleControlService old = controlService;
            if (old != null && listener != null) {
                try {
                    IBinder oldBinder = old.asBinder();
                    if (oldBinder != null && oldBinder.isBinderAlive()) {
                        old.unRegisterDataChangedListener(context.getPackageName(), listener);
                    }
                } catch (Throwable ignored) {}
            }

            IBinder rawControlBinder = getSystemService("com.beantechs.intelligentvehiclecontrol");
            if (rawControlBinder == null) throw new IllegalStateException("binder de controle indisponivel");
            IBinder controlBinder = new ShizukuBinderWrapper(rawControlBinder);
            if (!controlBinder.pingBinder()) throw new IllegalStateException("binder de controle morto");
            controlService = IIntelligentVehicleControlService.Stub.asInterface(controlBinder);

            registerControlDeathRecipient();

            if (listener == null) {
                listener = new IListener.Stub() {
                    @Override public void onDataChanged(String key, String value) { OnDataChanged(key, value); }
                };
            }
            controlService.registerDataChangedListener(context.getPackageName(), listener);
            controlService.addListenerKey(context.getPackageName(), getCombinedKeys());
            dispatchAllData();

            controlChannelRecoveryCount++;
            logPersistentClusterEvent("control_channel_recover_ok",
                    persistentEventDetails("reason", reason,
                            "totalRecoveries", String.valueOf(controlChannelRecoveryCount),
                            "elapsedMs", String.valueOf(SystemClock.uptimeMillis() - t0)));
        } catch (Throwable e) {
            logPersistentClusterEvent("control_channel_recover_fail",
                    persistentEventDetails("reason", reason, "error", String.valueOf(e.getMessage())));
        } finally {
            recoveringControlChannel = false;
        }
    }

    /**
     * Watchdog: a cada 10s faz ping no binder do controle; se estiver morto (e nenhuma recuperacao em
     * curso), dispara recoverControlChannel. Idempotente - cancela o runnable anterior antes de reagendar,
     * e so se reagenda enquanto ainda for o runnable ativo (evita duplicar o loop em re-init).
     */
    private void startControlChannelWatchdog() {
        if (!CONTROL_CHANNEL_RESILIENCE_ENABLED) return;
        if (backgroundHandler == null) return;
        if (controlChannelWatchdogRunnable != null) {
            backgroundHandler.removeCallbacks(controlChannelWatchdogRunnable);
        }
        controlChannelWatchdogRunnable = new Runnable() {
            @Override public void run() {
                try {
                    if (!recoveringControlChannel && !isControlServiceAlive()) {
                        logPersistentClusterEvent("control_channel_watchdog_dead", persistentEventDetails("trigger", "watchdog"));
                        recoverControlChannel("WATCHDOG");
                    }
                } catch (Throwable ignored) {
                } finally {
                    if (backgroundHandler != null && controlChannelWatchdogRunnable == this) {
                        backgroundHandler.postDelayed(this, CONTROL_CHANNEL_WATCHDOG_MS);
                    }
                }
            }
        };
        backgroundHandler.postDelayed(controlChannelWatchdogRunnable, CONTROL_CHANNEL_WATCHDOG_MS);
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
        if (!isControlServiceAlive()) {
            Log.e(TAG, "ControlService not initialized");
            return null;
        }
        try {
            String value = controlService.fetchData(key);
            dataCache.put(key, value);
            return value;
        } catch (Exception e) {
            Log.e(TAG, "Error fetching data", e);
            return null;
        }
    }

    public String getUpdatedData(String key) {
        if (!isControlServiceAlive()) {
            Log.e(TAG, "ControlService not initialized");
            return null;
        }
        try {
            String value = controlService.fetchData(key);
            dataCache.put(key, value);
            return value;
        } catch (Exception e) {
            Log.e(TAG, "Error fetching data", e);
            return null;
        }
    }

    private void maybeCounterPulseSceneNotify(String value) {
        // Ignore re-entrant notifications produced by our own counter-pulse write.
        if (isSelfWritingSceneNotify) {
            Log.w(TAG, "scene_notify=" + value + " ignored (self-write echo)");
            return;
        }
        // Only counter-pulse the BACKCAMERA scene that suspends the cluster decoder.
        if (!BEAN_PUI_SCENE_BACKCAMERA.equals(value)) {
            return;
        }
        // Only act when CarPlay is actually live on cluster 3 — otherwise the system
        // signal is meaningful and we shouldn't interfere with backup-camera audio
        // routing or other coordinated behavior.
        if (!DisplayAppLauncher.INSTANCE.isCarPlayOnDisplay(3)) {
            Log.w(TAG, "scene_notify=8 received but CarPlay not on cluster 3; no counter-pulse");
            return;
        }
        Log.w(TAG, "scene_notify=8 (SCENE_BACKCAMERA) intercepted with CarPlay on cluster 3; scheduling counter-pulse to 0");
        if (backgroundHandler == null) {
            // Fallback: synchronous write if no background handler yet.
            writeSceneNotifyNeutral();
            return;
        }
        backgroundHandler.postDelayed(this::writeSceneNotifyNeutral, SCENE_NOTIFY_COUNTER_PULSE_DELAY_MS);
    }

    private void writeSceneNotifyNeutral() {
        if (!isControlServiceAlive()) {
            Log.e(TAG, "scene_notify counter-pulse skipped: control service not initialized");
            return;
        }
        // Double-check CarPlay is still on cluster 3 by the time the pulse fires —
        // backup-camera may have closed already, in which case the natural exit
        // event (value=0) is on its way and we shouldn't race it.
        if (!DisplayAppLauncher.INSTANCE.isCarPlayOnDisplay(3)) {
            Log.w(TAG, "scene_notify counter-pulse aborted: CarPlay no longer on cluster 3");
            return;
        }
        isSelfWritingSceneNotify = true;
        try {
            controlService.request("cmd.common.request.set",
                    CarConstants.BEAN_PUI_SCENE_NOTIFY.getValue(),
                    BEAN_PUI_SCENE_NEUTRAL);
            Log.w(TAG, "scene_notify counter-pulse written (=0) to keep CarPlay video alive on cluster 3");
        } catch (Exception e) {
            Log.e(TAG, "Error writing scene_notify counter-pulse", e);
        } finally {
            // Release the re-entrancy flag on the same thread shortly after the
            // write so the echo from OnDataChanged passes through harmlessly.
            if (backgroundHandler != null) {
                backgroundHandler.postDelayed(() -> isSelfWritingSceneNotify = false, 250);
            } else {
                isSelfWritingSceneNotify = false;
            }
        }
    }

    public void updateData(String key, String value) {
        boolean isHvacCommand = hvacKeysToSuspend.contains(key);
        if (!isControlServiceAlive()) {
            Log.e(TAG, "ControlService not initialized");
            if (isHvacCommand) {
                logPersistentClusterEvent(
                        "hvac_update_skipped",
                        persistentEventDetails(
                                "key", key,
                                "value", value,
                                "reason", "control_service_not_alive"
                        )
                );
            }
            return;
        }

        boolean shouldSuspend = isHvacCommand;
        if (shouldSuspend) {
            ensureHvacSuspended(key);
        }

        try {
            if (isHvacCommand) {
                logPersistentClusterEvent(
                        "hvac_update_request",
                        persistentEventDetails(
                                "key", key,
                                "value", value
                        )
                );
            }
            controlService.request("cmd.common.request.set", key, value);
            if (isHvacCommand) {
                publishOptimisticHvacValue(key, value);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating data", e);
            if (isHvacCommand) {
                logPersistentClusterEvent(
                        "hvac_update_failed",
                        persistentEventDetails(
                                "key", key,
                                "value", value,
                                "error", e.getClass().getSimpleName(),
                                "message", e.getMessage()
                        )
                );
            }
        }

        if (shouldSuspend) {
            scheduleHvacResumption();
        }
    }

    private void publishOptimisticHvacValue(String key, String value) {
        String previous = dataCache.put(key, value);
        if (value != null && value.equals(previous)) {
            return;
        }
        logPersistentClusterEvent(
                "hvac_update_optimistic",
                persistentEventDetails(
                        "key", key,
                        "previous", previous,
                        "value", value
                )
        );
        for (IDataChanged listener : new ArrayList<>(dataChangedListeners)) {
            try {
                listener.onDataChanged(key, value);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying optimistic HVAC listener", e);
            }
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
            // Check if the HVAC app is currently resumed via lightweight am stack list
            String topApp = br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.INSTANCE.getTopPackageOnDisplay(0);
            boolean isForeground = HVAC_PACKAGE_NAME.equals(topApp);
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
        broadcastIntent.setPackage(App.getContext().getPackageName());
        App.getContext().sendBroadcast(broadcastIntent);
        broadcastIntent = new Intent("android.intent.haval." + key + "_" + value);
        broadcastIntent.setPackage(App.getContext().getPackageName());
        App.getContext().sendBroadcast(broadcastIntent);
        for (IDataChanged listener : new ArrayList<>(dataChangedListeners)) {
            try {
                listener.onDataChanged(key, value);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener", e);
            }
        }
        dataCache.put(key, value);
        if (!servicesInitialized) {
            return;
        }
        try {
            if (key.equals(CarConstants.SYS_AVM_PREVIEW_STATUS.getValue())) {
                if (DisplayAppLauncher.INSTANCE.isAndroidAutoOnDisplay(3)) {
                    Log.w(TAG, "Skipping projection guard for AVM_PREVIEW_STATUS_" + value + " because Android Auto is active on D3");
                    if (value.equals("1")) {
                        DisplayAppLauncher.INSTANCE.pulseAndroidAutoFocusDuringNativePanel("AVM_PREVIEW_STATUS_" + value);
                    } else if (value.equals("0")) {
                        DisplayAppLauncher.INSTANCE.pulseAndroidAutoFocusAfterNativePanelExit("AVM_PREVIEW_STATUS_" + value);
                    }
                } else {
                    DisplayAppLauncher.INSTANCE.preserveCarPlayClusterContract("AVM_PREVIEW_STATUS_" + value);
                    DisplayAppLauncher.INSTANCE.preserveAndroidAutoNativePanelContract("AVM_PREVIEW_STATUS_" + value);
                }
            }
            if (key.equals(CarConstants.CAR_HVAC_PANEL_DISPLAY_NOTIFY.getValue())) {
                if (DisplayAppLauncher.INSTANCE.isAndroidAutoOnDisplay(3)) {
                    Log.w(TAG, "Skipping projection guard for HVAC_PANEL_DISPLAY_" + value + " because Android Auto is active on D3");
                } else {
                    DisplayAppLauncher.INSTANCE.preserveCarPlayClusterContract("HVAC_PANEL_DISPLAY_" + value);
                    DisplayAppLauncher.INSTANCE.preserveAndroidAutoNativePanelContract("HVAC_PANEL_DISPLAY_" + value);
                }
            }
            if (key.equals(CarConstants.BEAN_PUI_SCENE_NOTIFY.getValue())) {
                maybeCounterPulseSceneNotify(value);
            }
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
                // Desligar BT/hotspot ao recolher retrovisores (salvam o estado p/ religar ao ligar o carro).
                if (sharedPreferences.getBoolean(SharedPreferencesKeys.DISABLE_BLUETOOTH_ON_FOLD_MIRROR.getKey(), false)) {
                    shutdownBluetoothForRestore();
                }
                if (sharedPreferences.getBoolean(SharedPreferencesKeys.DISABLE_HOTSPOT_ON_FOLD_MIRROR.getKey(), false)) {
                    shutdownWifiTetherForRestore();
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
                    carPoweredOff = true;
                    if (sharedPreferences.getBoolean(SharedPreferencesKeys.DISABLE_BLUETOOTH_ON_POWER_OFF.getKey(), false)) {
                        shutdownBluetoothForRestore();
                    }
                    if (sharedPreferences.getBoolean(SharedPreferencesKeys.DISABLE_HOTSPOT_ON_POWER_OFF.getKey(), false)) {
                        shutdownWifiTetherForRestore();
                    }
                    if (isMaxAcActive) {
                        cancelMaxAcMode();
                    }
                } else {
                    carPoweredOff = false;
                    // Religa BT/hotspot que NÓS desligamos (por power-off OU ao recolher retrovisor),
                    // com delay+retry: no power-on o adapter/serviços podem não estar prontos ainda.
                    restoreBluetoothIfWasDisabled();
                    restoreWifiTetherIfWasDisabled();
                    if (sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_MAX_AC_ON_UNLOCK.getKey(), false)) {
                        if (!isMaxAcActive) enableMaxAcOn();
                    }
                    if (sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_OPEN_SUNROOF_CURTAIN_ON_START.getKey(), false)) {
                        autoOpenSunroofCurtain();
                    }
                    // Ao ligar o carro, reaplica o % de bateria do HEV Prioritario (o carro costuma resetar).
                    applyHevSocTargetIfActive("POWER_ON");
                }
            } else if (key.equals(CarConstants.CAR_HVAC_POWER_MODE.getValue()) && value.equals("1") && sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_SEAT_VENTILATION_ON_AC_ON.getKey(), false)) {
                updateData(CarConstants.CAR_COMFORT_SETTING_DRIVER_SEAT_VENTILATION_LEVEL.getValue(), "3");
            } else if (key.equals(CarConstants.CAR_HVAC_POWER_MODE.getValue()) && value.equals("0") && sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_SEAT_VENTILATION_ON_AC_ON.getKey(), false)) {
                updateData(CarConstants.CAR_COMFORT_SETTING_DRIVER_SEAT_VENTILATION_LEVEL.getValue(), "0");
            } else if (key.equals(CarConstants.CAR_BASIC_INSIDE_TEMP.getValue()) && sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_MAX_AC_ON_UNLOCK.getKey(), false)) {
                if (isMaxAcActive) updateMaxAcSmoothing();
            } else if (key.equals(CarConstants.CAR_EV_SETTING_CHARGE_SOC_TARGET_CONFIG.getValue())) {
                // O carro (ou o usuario) alterou o % alvo de bateria do HEV. Se a persistencia estiver
                // ligada e o sub-modo for Prioritario, reaplica o valor que o usuario escolheu.
                applyHevSocTargetIfActive("SOC_CHANGED");
            } else if (key.equals(CarConstants.CAR_EV_SETTING_POWER_RESERVE_CONFIG.getValue()) && value.trim().equals("2")) {
                // Entrou em HEV Prioritario -> aplica o % desejado.
                applyHevSocTargetIfActive("ENTER_PRIORITARIO");
            } else if (key.equals(CarConstants.CAR_EV_SETTING_POWER_MODEL_CONFIG.getValue()) && value.trim().equals("0")) {
                // Mudou de EV para HEV -> reaplica o % desejado (o carro costuma cair pra 20%).
                // applyHevSocTargetIfActive se auto-gateia (so atua em HEV Prioritario + persistencia ON).
                applyHevSocTargetIfActive("ENTER_HEV");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in OnDataChanged", e);
        }
    }

    // Reaplica o % de bateria escolhido pelo usuario no HEV Prioritario, caso o carro o tenha
    // alterado sozinho. So atua quando a persistencia esta ligada E o carro JA esta em HEV
    // Prioritario (modo HEV + sub-modo Prioritario). NUNCA escreve o modo nem o sub-modo: se nao
    // estiver exatamente em HEV Prioritario, sai sem fazer nada (o modo quem define e o usuario no
    // carro). O eco da propria escrita nao re-dispara (current == desired).
    // Monitor periódico: reaplica o % salvo enquanto o carro estiver em HEV Prioritário. É a rede de
    // segurança para eventos perdidos (power-on com app subindo tarde, corrida ao entrar no modo,
    // carro resetando o valor). Auto-gateado por applyHevSocTargetIfActive (retorna rápido se a
    // persistência estiver desligada ou fora de HEV Prioritário) e idempotente (só escreve se drift).
    private void startHevSocMonitor() {
        if (backgroundHandler == null) return;
        if (hevSocMonitorRunnable != null) {
            backgroundHandler.removeCallbacks(hevSocMonitorRunnable);
        }
        hevSocMonitorRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    applyHevSocTargetIfActive("MONITOR");
                } catch (Exception e) {
                    Log.e(TAG, "HEV SOC monitor tick failed", e);
                } finally {
                    // Só o runnable corrente reagenda (no restart o handler é recriado e este é trocado).
                    if (backgroundHandler != null && hevSocMonitorRunnable == this) {
                        backgroundHandler.postDelayed(this, HEV_SOC_MONITOR_INTERVAL_MS);
                    }
                }
            }
        };
        backgroundHandler.postDelayed(hevSocMonitorRunnable, HEV_SOC_MONITOR_INTERVAL_MS);
    }

    public void applyHevSocTargetIfActive(String reason) {
        try {
            if (!sharedPreferences.getBoolean(SharedPreferencesKeys.ENABLE_PERSIST_HEV_SOC_TARGET.getKey(), false)) {
                return;
            }
            // 1) tem que estar em HEV (power_model_config == 0). Em EV/Prioridade EV, nao mexe em nada.
            String driveMode = getUpdatedData(CarConstants.CAR_EV_SETTING_POWER_MODEL_CONFIG.getValue());
            if (driveMode == null || !driveMode.trim().equals("0")) {
                return;
            }
            // 2) e o sub-modo tem que ser Prioritario (power_reserve_config == 2).
            String subMode = getUpdatedData(CarConstants.CAR_EV_SETTING_POWER_RESERVE_CONFIG.getValue());
            if (subMode == null || !subMode.trim().equals("2")) {
                return;
            }
            int desired = sharedPreferences.getInt(SharedPreferencesKeys.HEV_SOC_TARGET_VALUE.getKey(), 50);
            String currentStr = getUpdatedData(CarConstants.CAR_EV_SETTING_CHARGE_SOC_TARGET_CONFIG.getValue());
            // Sem leitura válida do valor atual, NÃO escreve às cegas (evita reescrever quando o
            // control service piscou). Mesmo padrão dos gates de driveMode/subMode acima.
            if (currentStr == null) {
                return;
            }
            int current = Integer.MIN_VALUE;
            try { current = Integer.parseInt(currentStr.trim()); } catch (Exception ignored) {}
            if (current != desired) {
                updateData(CarConstants.CAR_EV_SETTING_CHARGE_SOC_TARGET_CONFIG.getValue(), String.valueOf(desired));
                Log.w(TAG, "[HEV-SOC " + reason + "] alvo estava " + current + ", reaplicado " + desired);
            }
        } catch (Exception e) {
            Log.e(TAG, "applyHevSocTargetIfActive falhou", e);
        }
    }

    // Define o % alvo do HEV Prioritario a partir da UI (barra estendida/config): salva a pref
    // e escreve no carro. Clampa em 20..80.
    public void setHevSocTargetValue(int value) {
        int v = Math.max(20, Math.min(80, value));
        sharedPreferences.edit().putInt(SharedPreferencesKeys.HEV_SOC_TARGET_VALUE.getKey(), v).apply();
        updateData(CarConstants.CAR_EV_SETTING_CHARGE_SOC_TARGET_CONFIG.getValue(), String.valueOf(v));
        Log.w(TAG, "[HEV-SOC UI_SET] alvo definido = " + v);
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
        if (!isControlServiceAlive()) {
            Log.e(TAG, "ControlService not initialized");
            return;
        }
        try {
            controlService.request("cmd.common.request.set", CarConstants.CAR_FRS_SETTING_DISTRACTION_DETECTION_ENABLE.getValue(), b ? "1" : "0");
            Log.w(TAG, "Distraction detection monitoring set to: " + b);
        } catch (Exception e) {
            Log.e(TAG, "Error setting monitoring", e);
        }
    }

    public void setAvasEnabled(boolean b) {
        if (!isControlServiceAlive()) {
            Log.e(TAG, "ControlService not initialized");
            return;
        }
        try {
            controlService.request("cmd.common.request.set", CarConstants.CAR_EV_SETTING_AVAS_ENABLE.getValue(), b ? "1" : "0");
            Log.w(TAG, "AVAS enabled: " + b);
        } catch (Exception e) {
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
        if (connectivityManager == null) return;
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
        if (connectivityManager == null) return;
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

    // ---- BT/Hotspot: estado, desligamento com tracking e restauração com retry ----

    private boolean currentWifiTetherState() {
        try {
            WifiManager wifiManager = (WifiManager) App.getContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                Object r = wifiManager.getClass().getMethod("getWifiApState").invoke(wifiManager);
                if (r instanceof Integer) {
                    return ((Integer) r) == 13; // 13 = WIFI_AP_STATE_ENABLED
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error reading Wi-Fi AP state", t);
        }
        return wifiTetherEnabled;
    }

    // Estado real "no ar" do SoftAP pelo sysfs (lido via Shizuku=shell, que consegue ler sysfs_net).
    // "1"=no ar (beaconando), "0"=iface up mas sem BSS (quebrado/meio-ligado), ""=desconhecido/down.
    private String softApCarrier() {
        try {
            String out = ShizukuUtils.runCommandAndGetOutput(new String[]{"sh", "-c",
                    "i=$(getprop sys.wifi.softap_interface_name); [ -z \"$i\" ] && i=wlan2; cat /sys/class/net/$i/carrier 2>/dev/null"});
            if (out != null) {
                String v = out.trim();
                if (v.equals("1") || v.equals("0")) return v;
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    // Hotspot (Wi-Fi AP) REALMENTE no ar? Prefere o carrier do sysfs (real, via wlan2); em OEMs onde o
    // getWifiApState() retorna -1, cai pro cache do receiver WIFI_AP_STATE_CHANGED. Usado pelo status do
    // HotRouter pra não mostrar "Ativo" com o hotspot desligado (o daemon reporta WLAN/4G de qualquer jeito).
    public boolean isHotspotOnAir() {
        String carrier = softApCarrier();
        return "1".equals(carrier) || (carrier.isEmpty() && currentWifiTetherState());
    }

    // Desliga o BT salvando que estava ligado (p/ religar no próximo power-on). Não sobrescreve um
    // "estava ligado" anterior com false (caso recolher-retrovisor + power-off no mesmo ciclo).
    private void shutdownBluetoothForRestore() {
        if (currentBluetoothState()) {
            sharedPreferences.edit().putBoolean(SharedPreferencesKeys.BLUETOOTH_STATE_ON_POWER_OFF.getKey(), true).apply();
            disableBluetooth();
        }
    }

    private void shutdownWifiTetherForRestore() {
        if (currentWifiTetherState()) {
            sharedPreferences.edit().putBoolean(SharedPreferencesKeys.HOTSPOT_STATE_ON_POWER_OFF.getKey(), true).apply();
            disableWifiTether();
        }
    }

    private void restoreBluetoothIfWasDisabled() {
        if (sharedPreferences.getBoolean(SharedPreferencesKeys.BLUETOOTH_STATE_ON_POWER_OFF.getKey(), false)) {
            attemptRestoreBluetooth(0);
        }
    }

    private void attemptRestoreBluetooth(int attempt) {
        if (!sharedPreferences.getBoolean(SharedPreferencesKeys.BLUETOOTH_STATE_ON_POWER_OFF.getKey(), false)) {
            return; // flag limpa por outra restauração
        }
        if (carPoweredOff) {
            return; // carro desligou no meio: mantém a intenção p/ o próximo power-on
        }
        if (currentBluetoothState()) {
            sharedPreferences.edit().putBoolean(SharedPreferencesKeys.BLUETOOTH_STATE_ON_POWER_OFF.getKey(), false).apply();
            return; // já ligou: sucesso
        }
        enableBluetooth();
        if (attempt + 1 < RADIO_RESTORE_MAX_ATTEMPTS) {
            backgroundHandler.postDelayed(() -> attemptRestoreBluetooth(attempt + 1), RADIO_RESTORE_RETRY_MS);
        } else {
            sharedPreferences.edit().putBoolean(SharedPreferencesKeys.BLUETOOTH_STATE_ON_POWER_OFF.getKey(), false).apply();
        }
    }

    private void restoreWifiTetherIfWasDisabled() {
        if (sharedPreferences.getBoolean(SharedPreferencesKeys.HOTSPOT_STATE_ON_POWER_OFF.getKey(), false)) {
            attemptRestoreWifiTether(0);
        }
    }

    private void attemptRestoreWifiTether(int attempt) {
        if (!sharedPreferences.getBoolean(SharedPreferencesKeys.HOTSPOT_STATE_ON_POWER_OFF.getKey(), false)) {
            return;
        }
        if (carPoweredOff) {
            return;
        }
        if (currentWifiTetherState()) {
            sharedPreferences.edit().putBoolean(SharedPreferencesKeys.HOTSPOT_STATE_ON_POWER_OFF.getKey(), false).apply();
            return;
        }
        enableWifiTether();
        if (attempt + 1 < RADIO_RESTORE_MAX_ATTEMPTS) {
            backgroundHandler.postDelayed(() -> attemptRestoreWifiTether(attempt + 1), RADIO_RESTORE_RETRY_MS);
        } else {
            sharedPreferences.edit().putBoolean(SharedPreferencesKeys.HOTSPOT_STATE_ON_POWER_OFF.getKey(), false).apply();
        }
    }

    public void cancelMaxAcMode() {

        if (!isMaxAcActive) return;
        isMaxAcActive = false;
        if (maxAcTimeoutRunnable != null) {
            backgroundHandler.removeCallbacks(maxAcTimeoutRunnable);
            maxAcTimeoutRunnable = null;
        }

        // Restores previous AC settings (excluding power/enable keys so they can be restored last)
        for (Map.Entry<String, String> entry : previousAcState.entrySet()) {
            if (entry.getValue() != null &&
                !entry.getKey().equals(CarConstants.CAR_HVAC_POWER_MODE.getValue()) &&
                !entry.getKey().equals(CarConstants.CAR_HVAC_AC_ENABLE.getValue())) {
                updateData(entry.getKey(), entry.getValue());
            }
        }

        // Restore AC power mode and AC enable last to ensure the system is correctly turned ON/OFF
        String restoredPower = previousAcState.get(CarConstants.CAR_HVAC_POWER_MODE.getValue());
        String restoredAcEnable = previousAcState.get(CarConstants.CAR_HVAC_AC_ENABLE.getValue());

        updateData(CarConstants.CAR_HVAC_AC_ENABLE.getValue(), restoredAcEnable != null ? restoredAcEnable : "1");
        updateData(CarConstants.CAR_HVAC_POWER_MODE.getValue(), restoredPower != null ? restoredPower : "1");

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

            if (currentTemp >= 85.0f || currentTemp <= -40.0f) {
                if (retryCount < 5) {
                    Log.w(TAG, "Invalid temp " + currentTemp + " at startup, delaying Max AC check... retry: " + retryCount);
                    backgroundHandler.postDelayed(() -> enableMaxAcOnWithRetry(retryCount + 1), 1000);
                } else {
                    Log.e(TAG, "Invalid temp " + currentTemp + " after max retries, aborting Max AC activation");
                }
                return;
            }

            float threshold = sharedPreferences.getFloat(SharedPreferencesKeys.MAX_AC_ON_UNLOCK_THRESHOLD.getKey(), 35.0f);
            if (currentTemp >= threshold && !isMaxAcActive) {

                tryRestoreMaxAcState();
                if (previousAcState.isEmpty()) {
                    String prevPower = getUpdatedData(CarConstants.CAR_HVAC_POWER_MODE.getValue());
                    String prevAcEnable = getUpdatedData(CarConstants.CAR_HVAC_AC_ENABLE.getValue());
                    String prevFan = getUpdatedData(CarConstants.CAR_HVAC_FAN_SPEED.getValue());
                    String prevDriverTemp = getUpdatedData(CarConstants.CAR_HVAC_DRIVER_TEMPERATURE.getValue());
                    String prevPassTemp = getUpdatedData(CarConstants.CAR_HVAC_PASS_TEMPERATURE.getValue());
                    String prevAuto = getUpdatedData(CarConstants.CAR_HVAC_AUTO_ENABLE.getValue());
                    String prevAnion = getUpdatedData(CarConstants.CAR_HVAC_ANION_ENABLE.getValue());
                    String prevAQS = getUpdatedData(CarConstants.CAR_HVAC_AQS_ENABLE.getValue());
                    String prevSync = getUpdatedData(CarConstants.CAR_HVAC_SYNC_ENABLE.getValue());
                    String prevComfortCurve = getUpdatedData(CarConstants.CAR_HVAC_SETTING_COMFORT_CURVE.getValue());
                    String prevCycleMode = getUpdatedData(CarConstants.CAR_HVAC_CYCLE_MODE.getValue());

                    previousAcState.put(CarConstants.CAR_HVAC_POWER_MODE.getValue(), prevPower);
                    previousAcState.put(CarConstants.CAR_HVAC_AC_ENABLE.getValue(), prevAcEnable);
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
            return br.com.redesurftank.havalshisuku.models.EngineState.isMainScreenOn(engineState);
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
            Object service = getService.invoke(null, serviceName);
            if (service == null) {
                Log.e(TAG, "System service not found: " + serviceName);
                return null;
            }
            return (IBinder) service;
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

    private void initMobileDataGuard() {
        try {
            android.content.Context ctx = App.getContext();
            MobileDataManager.INSTANCE.ensureUsageStatsPermission(ctx); // pro NetworkStatsManager (best-effort)
            MobileDataManager.INSTANCE.recomputeAndApply(ctx); // aplica as regras salvas no boot
            MobileDataManager.INSTANCE.applyDatatrackState(); // reaplica o congelamento do datatrack salvo no boot
            registerMobileDataWifiCallback(ctx); // reavalia na hora quando o WiFi conecta/cai
            registerMobileDataTetherReceiver(ctx); // reforça o bloqueio na hora que o hotspot liga/desliga
        } catch (Throwable t) {
            Log.w(TAG, "initMobileDataGuard: " + t.getMessage());
        }
        if (backgroundHandler == null || mobileDataAutoblockRunnable != null) return;
        mobileDataAutoblockRunnable = new Runnable() {
            @Override public void run() {
                try {
                    MobileDataManager.INSTANCE.recomputeAndApply(App.getContext());
                } catch (Throwable ignored) {
                } finally {
                    if (backgroundHandler != null && mobileDataAutoblockRunnable == this) {
                        backgroundHandler.postDelayed(this, MOBILE_DATA_CHECK_MS);
                    }
                }
            }
        };
        backgroundHandler.postDelayed(mobileDataAutoblockRunnable, MOBILE_DATA_CHECK_MS);
    }

    // Callback de WiFi: quando conecta/cai, reavalia as regras na hora (a regra "bloquear no WiFi"
    // precisa reagir rápido; o periódico de 15s é só a rede de segurança pra consumo/projeção).
    private void registerMobileDataWifiCallback(android.content.Context ctx) {
        try {
            if (mobileDataWifiCallback != null) return;
            android.net.ConnectivityManager cm =
                    (android.net.ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;
            android.net.NetworkRequest req = new android.net.NetworkRequest.Builder()
                    .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                    .build();
            mobileDataWifiCallback = new android.net.ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(android.net.Network network) {
                    MobileDataManager.INSTANCE.recomputeAndApply(App.getContext());
                }
                @Override public void onLost(android.net.Network network) {
                    MobileDataManager.INSTANCE.recomputeAndApply(App.getContext());
                }
            };
            cm.registerNetworkCallback(req, mobileDataWifiCallback);
        } catch (Throwable t) {
            Log.w(TAG, "registerMobileDataWifiCallback: " + t.getMessage());
        }
    }

    // Hotspot (tether) ligou/desligou -> reforça o bloqueio NA HORA. O hotspot religa o dado móvel pra
    // ter uplink; sem este gatilho, o vazamento ficaria aberto até o próximo tick de 15s. O broadcast
    // android.net.conn.TETHER_STATE_CHANGED não é protegido (qualquer app registra).
    private void registerMobileDataTetherReceiver(android.content.Context ctx) {
        try {
            if (mobileDataTetherReceiver != null) return;
            mobileDataTetherReceiver = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    try {
                        MobileDataManager.INSTANCE.recomputeAndApply(App.getContext());
                    } catch (Throwable ignored) {
                    }
                }
            };
            ctx.registerReceiver(mobileDataTetherReceiver,
                    new IntentFilter("android.net.conn.TETHER_STATE_CHANGED"));
        } catch (Throwable t) {
            Log.w(TAG, "registerMobileDataTetherReceiver: " + t.getMessage());
        }
    }
}
