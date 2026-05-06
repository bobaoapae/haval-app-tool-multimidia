package br.com.redesurftank.havalshisuku.services;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.util.regex.Pattern;

import br.com.redesurftank.App;
import br.com.redesurftank.havalshisuku.broadcastReceivers.DispatchAllDatasReceiver;
import br.com.redesurftank.havalshisuku.broadcastReceivers.RestartReceiver;
import br.com.redesurftank.havalshisuku.managers.EmulatorInputBridge;
import br.com.redesurftank.havalshisuku.managers.EmulatorVehicleBridge;
import br.com.redesurftank.havalshisuku.managers.ProjectorManager;
import br.com.redesurftank.havalshisuku.managers.ServiceManager;
import br.com.redesurftank.havalshisuku.models.CommandListener;
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys;
import br.com.redesurftank.havalshisuku.utils.BuildUtils;
import br.com.redesurftank.havalshisuku.utils.IPTablesUtils;
import br.com.redesurftank.havalshisuku.utils.ShizukuUtils;
import br.com.redesurftank.havalshisuku.utils.TelnetClientWrapper;
import br.com.redesurftank.havalshisuku.utils.TermuxUtils;
import rikka.shizuku.Shizuku;

public class ForegroundService extends Service implements Shizuku.OnBinderDeadListener {

    private static final String TAG = "ForegroundService";
    private static final String CHANNEL_ID = "ForegroundServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    private HandlerThread handlerThread;
    private Handler backgroundHandler;
    private Boolean isShizukuInitialized = false;
    private Boolean isServiceRunning = false;
    private EmulatorVehicleBridge emulatorVehicleBridge;
    private EmulatorInputBridge emulatorInputBridge;

    private final Runnable timeoutRunnable = () -> {
        if (!isShizukuInitialized) {
            Log.w(TAG, "Shizuku initialization timed out. Restarting service...");
            restart();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        handlerThread = new HandlerThread("BackgroundThread");
        handlerThread.start();
        backgroundHandler = new Handler(handlerThread.getLooper());
    }

    @Override
    public synchronized int onStartCommand(Intent intent, int flags, int startId) {
        var sharedPreferences = App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE);
        

        if (isServiceRunning) {
            Log.w(TAG, "Service is already running, skipping start.");
            return START_STICKY; // Retorna imediatamente se o serviço já estiver rodando
        }
        try {
            isServiceRunning = true; // Marca o serviço como rodando
            Log.w(TAG, "Service started");
            
            // Clear any pending background tasks (retry loops) from previous starts
            backgroundHandler.removeCallbacksAndMessages(null);
            
            var context = getApplicationContext();
            // Criar notificação para o Foreground Service
            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("Aplicação em execução").setContentText("Seu app está rodando em segundo plano").setSmallIcon(android.R.drawable.ic_notification_overlay) // Ícone de notificação
                    .build();

            startForeground(NOTIFICATION_ID, notification);

            // Start bottom bar as early as possible if enabled
            if (sharedPreferences.getBoolean(SharedPreferencesKeys.PERSISTENT_BOTTOM_BAR.getKey(), false)) {
                if (android.provider.Settings.canDrawOverlays(this)) {
                    Log.w(TAG, "Starting persistent bottom bar...");
                    Intent bottomBarIntent = new Intent(this, br.com.redesurftank.havalshisuku.services.BottomBarService.class);
                    startService(bottomBarIntent);
                } else {
                    Log.e(TAG, "Overlay permission not granted, skipping persistent bottom bar.");
                }
            }

            // On emulator hardware, Telnet/Shizuku infrastructure is unavailable.
            // Skip ALL hardware-specific checks (UID, Shizuku, Telnet) to prevent freezing the VM.
            // This guard runs first, before anything that touches real-hardware-only paths.
            if (BuildUtils.isEmulator()) {
                Log.w(TAG, "Running on emulator — skipping Shizuku/Telnet initialization.");
                // The full ServiceManager.initializeServices() is too heavy for the
                // emulator (it binds proprietary Beantechs/Autolink services that
                // don't exist), but a few of its side-effects ARE needed here:
                // attach the shared SharedPreferences instance so screens that
                // read prefs in processKey() don't NPE on the simulated key path.
                try {
                    ServiceManager.getInstance().ensureSharedPreferencesForEmulator();
                } catch (Exception e) {
                    Log.e(TAG, "ensureSharedPreferencesForEmulator failed: " + e.getMessage(), e);
                }
                try {
                    ProjectorManager.getInstance().initialize();
                    Log.w(TAG, "ProjectorManager initialized for emulator cluster simulation.");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to initialize ProjectorManager on emulator: " + e.getMessage(), e);
                }
                try {
                    emulatorVehicleBridge = new EmulatorVehicleBridge(getApplicationContext());
                    emulatorVehicleBridge.start();
                    Log.w(TAG, "EmulatorVehicleBridge started — VHAL data will feed the cluster.");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to start EmulatorVehicleBridge: " + e.getMessage(), e);
                }
                try {
                    emulatorInputBridge = new EmulatorInputBridge(getApplicationContext());
                    emulatorInputBridge.start();
                    Log.w(TAG, "EmulatorInputBridge started — steering-wheel keys can be driven via ADB broadcast.");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to start EmulatorInputBridge: " + e.getMessage(), e);
                }
                return START_STICKY;
            }

            // Checar se precisa resetar dados (rollback preview→estável)
            var pendingResetTarget = sharedPreferences.getString(SharedPreferencesKeys.PENDING_RESET_TARGET_VERSION.getKey(), "");
            if (pendingResetTarget != null && !pendingResetTarget.isEmpty()) {
                try {
                    var currentVersion = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
                    if (currentVersion != null && currentVersion.equals(pendingResetTarget)) {
                        // Versão atual bate com o alvo — install deu certo, resetar dados
                        Log.w(TAG, "Pending data reset confirmed (current=" + currentVersion + " matches target=" + pendingResetTarget + "), clearing all SharedPreferences...");
                        sharedPreferences.edit().clear().apply();
                        Log.w(TAG, "SharedPreferences cleared successfully, app will behave as first run.");
                    } else {
                        // Install falhou ou versão diferente — limpar a flag sem resetar dados
                        Log.w(TAG, "Pending data reset skipped (current=" + currentVersion + " != target=" + pendingResetTarget + "), removing flag.");
                        sharedPreferences.edit().remove(SharedPreferencesKeys.PENDING_RESET_TARGET_VERSION.getKey()).apply();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error checking pending data reset: " + e.getMessage(), e);
                    sharedPreferences.edit().remove(SharedPreferencesKeys.PENDING_RESET_TARGET_VERSION.getKey()).apply();
                }
            }

            if (!sharedPreferences.getBoolean(SharedPreferencesKeys.SELF_INSTALLATION_INTEGRITY_CHECK.getKey(), false) && !sharedPreferences.getBoolean(SharedPreferencesKeys.BYPASS_SELF_INSTALLATION_INTEGRITY_CHECK.getKey(), false)) {
                try {
                    var selfPackageInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), 0);
                    if (selfPackageInfo.uid > 10999) {
                        // Se o UID for maior que 10999, significa que o aplicativo não tem acesso direto a conectar via telnet. O que impossibilita o start automatizado do Shizuku.
                        Log.w(TAG, "Application UID is greater than 10999, Shizuku cannot be started automatically.");
                        //show a toast to inform the user
                        Toast.makeText(context, "O aplicativo não foi instalado utilizando o exploit corretamente, por favor, reinstale o aplicativo utilizando o exploit correto para que o Shizuku possa ser iniciado automaticamente.", Toast.LENGTH_LONG).show();
                        return START_NOT_STICKY;
                    } else {
                        sharedPreferences.edit().putBoolean(SharedPreferencesKeys.SELF_INSTALLATION_INTEGRITY_CHECK.getKey(), true).apply();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to get application info: " + e.getMessage(), e);
                }
            }


            backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        var sharedPreferences = App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE);
                        String shizukuLibLocation = sharedPreferences.getString("shizuku_lib_location", "");

                        var telnetClient = new TelnetClientWrapper();
                        
                        
                        telnetClient.connect("127.0.0.1", 23);
                        

                        String filePath = "";
                        if (shizukuLibLocation.isEmpty()) {
                            String findCommand = "find /data/app -name libshizuku.so";
                            filePath = telnetClient.executeCommand(findCommand);

                            if (filePath.isEmpty()) {
                                throw new RuntimeException("libshizuku.so not found");
                            }

                            sharedPreferences.edit().putString("shizuku_lib_location", filePath).apply();
                            Log.w(TAG, "libshizuku.so found at: " + filePath);
                        } else {
                            Log.w(TAG, "Using already configured Shizuku lib location: " + shizukuLibLocation);
                            filePath = shizukuLibLocation;
                        }

                        String executeCommand = filePath;
                        Log.w(TAG, "Executing command: " + executeCommand);
                        String result = telnetClient.executeCommand(executeCommand);

                        if (Pattern.compile("killed \\d+ \\(shizuku_server\\)").matcher(result).find()) {
                            Log.w(TAG, "Old process killed, statically waiting 5 seconds to avoid bind on an already dead Shizuku process");
                            // Espera o Shizuku reiniciar
                            Thread.sleep(5000);
                        }
                        Log.w(TAG, "Command executed successfully: " + result);

                        telnetClient.disconnect();
                        Shizuku.addBinderReceivedListenerSticky(ForegroundService.this::shizukuBinderReceived);
                        backgroundHandler.postDelayed(timeoutRunnable, 5000);
                    } catch (Exception e) {
                        Log.e(TAG, "Error executing shell commands: " + e.getMessage(), e);
                        backgroundHandler.postDelayed(this, 1000);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in onStartCommand: " + e.getMessage(), e);
            isServiceRunning = false; // Marca o serviço como não rodando em caso de erro
            stopSelf(); // Para o serviço em caso de erro
            return START_NOT_STICKY; // Não reinicia o serviço automaticamente
        }

        return START_STICKY; // Garante que o serviço seja reiniciado se for morto
    }

    private synchronized void shizukuBinderReceived() {
        if (!isServiceRunning) return;
        Shizuku.removeBinderReceivedListener(this::shizukuBinderReceived);
        Log.w(TAG, "Shizuku binder received");
        isShizukuInitialized = true;
        backgroundHandler.removeCallbacksAndMessages(null); // Remove any pending timeouts
        checkService();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Não é necessário para um serviço não vinculado
    }

    private void checkService() {
        if (!isShizukuInitialized) {
            Log.w(TAG, "Shizuku not initialized yet, retrying...");
            return;
        }

        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Shizuku permission not granted, requesting permission...");
            Shizuku.addRequestPermissionResultListener((requestCode, grantResult) -> {
                if (requestCode == 0 && grantResult == PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "Shizuku permission granted");
                    checkService();
                } else {
                    Log.e(TAG, "Shizuku permission denied");
                }
            });
            Shizuku.requestPermission(0);
            return;
        }

        Log.w(TAG, "Shizuku initialized/bypassed, starting services...");

        // Start SSH check and start in background with retry
        backgroundHandler.post(new Runnable() {
            int retryCount = 0;
            final int MAX_RETRIES = 5;

            @Override
            public void run() {
                try {
                    var isTermuxInstalled = !ShizukuUtils.runCommandAndGetOutput(new String[]{"pm", "list", "packages", "com.termux"}).trim().isEmpty();
                    if (isTermuxInstalled) {
                        var isSSHRunning = !TermuxUtils.runCommandAndGetOutput("pgrep sshd").trim().isEmpty();
                        if (!isSSHRunning) {
                            Log.w(TAG, "SSHD is not running, starting it now...");
                            TermuxUtils.runCommandOnBackground("sshd", new CommandListener() {
                                @Override
                                public void onStdout(String line) {
                                    Log.w(TAG, "SSHD Output: " + line);
                                }

                                @Override
                                public void onStderr(String line) {
                                    Log.e(TAG, "SSHD Error: " + line);
                                }

                                @Override
                                public void onFinished(int exitCode) {
                                    Log.w(TAG, "SSHD finished with exit code: " + exitCode);
                                }
                            });
                        } else {
                            Log.w(TAG, "SSHD is already running");
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error checking Termux installation: " + e.getMessage(), e);
                    if (retryCount < MAX_RETRIES) {
                        retryCount++;
                        Log.w(TAG, "Retrying SSH check/start, attempt " + retryCount);
                        backgroundHandler.postDelayed(this, 1000);
                    }
                }
            }
        });

        // Start ADB check and start in background with retry
        backgroundHandler.post(new Runnable() {
            int retryCount = 0;
            final int MAX_RETRIES = 5;

            @Override
            public void run() {
                try {
                    var isADBRunning = !ShizukuUtils.runCommandAndGetOutput(new String[]{"pgrep", "adbd"}).trim().isEmpty();
                    if (!isADBRunning) {
                        Log.w(TAG, "ADB is not running, starting it now...");
                        ShizukuUtils.runCommandOnBackground(new String[]{"start", "adbd"}, new CommandListener() {
                            @Override
                            public void onStdout(String line) {
                                Log.w(TAG, "ADB Output: " + line);
                            }

                            @Override
                            public void onStderr(String line) {
                                Log.e(TAG, "ADB Error: " + line);
                            }

                            @Override
                            public void onFinished(int exitCode) {
                                Log.w(TAG, "ADB finished with exit code: " + exitCode);
                            }
                        });
                    } else {
                        Log.w(TAG, "ADB is already running");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error checking ADB status: " + e.getMessage(), e);
                    if (retryCount < MAX_RETRIES) {
                        retryCount++;
                        Log.w(TAG, "Retrying ADB check/start, attempt " + retryCount);
                        backgroundHandler.postDelayed(this, 1000);
                    }
                }
            }
        });

        try {
            ShizukuUtils.runCommandAndGetOutput(new String[]{"echo", "60", ">", "/proc/sys/vm/swappiness"});
        } catch (Exception e) {
            Log.e(TAG, "Error setting swappiness: " + e.getMessage(), e);
        }

        var successFirstUnlockIpTables = false;

        try {
            if (IPTablesUtils.unlockInputOutputAll()) {
                Log.w(TAG, "IPTables unlocked successfully");
                successFirstUnlockIpTables = true;
            } else {
                Log.e(TAG, "Failed to unlock IPTables");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error unlocking IPTables: " + e.getMessage(), e);
        }

        var finalSuccessFirstUnlockIpTables = successFirstUnlockIpTables;

        try {
            backgroundHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Log.w(TAG, "Background to keep unlocking iptables");
                    try {
                        var isSuccess = IPTablesUtils.unlockInputOutputAll();
                        if (!finalSuccessFirstUnlockIpTables) {
                            if (isSuccess) {
                                Log.w(TAG, "IPTables unlocked successfully on retry");
                            } else {
                                Log.e(TAG, "Failed to unlock IPTables on retry");
                            }
                        }
                        if (isSuccess) {
                            backgroundHandler.postDelayed(this, 15000);
                        } else {
                            backgroundHandler.postDelayed(this, 5000);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error unlocking iptables: " + e.getMessage(), e);
                        backgroundHandler.postDelayed(this, 5000);
                    }
                }
            }, 1000);
        } catch (Exception e) {
            Log.e(TAG, "Error unlocking iptables: " + e.getMessage(), e);
        }
        boolean initSuccess = ServiceManager.getInstance().initializeServices(getApplicationContext());
        if (!initSuccess) {
            Log.e(TAG, "Service initialization failed, restarting...");
            restart();
            return;
        }

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("com.beantechs.intelligentvehiclecontrol.INIT_COMPLETED");

        ContextCompat.registerReceiver(App.getContext(), new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (isServiceRunning) {
                    // service already running bug intelligentvehiclecontrol restarted
                    // restart self
                    Log.w(TAG, "Received com.beantechs.intelligentvehiclecontrol.INIT_COMPLETED after service started, restarting service...");
                    restart();
                    return;
                }
                checkService();
            }
        }, intentFilter, ContextCompat.RECEIVER_NOT_EXPORTED);

        DispatchAllDatasReceiver.registerToBroadcast(App.getContext());
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Background Service Channel", NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    @Override
    public void onDestroy() {
        if (handlerThread != null) {
            handlerThread.quitSafely();
        }
        isServiceRunning = false;
        if (emulatorVehicleBridge != null) {
            try { emulatorVehicleBridge.stop(); } catch (Exception ignored) {}
            emulatorVehicleBridge = null;
        }
        if (emulatorInputBridge != null) {
            try { emulatorInputBridge.stop(); } catch (Exception ignored) {}
            emulatorInputBridge = null;
        }
        Shizuku.removeBinderReceivedListener(this::shizukuBinderReceived);
        Shizuku.removeBinderDeadListener(this);
        Log.w(TAG, "Service destroyed");
        super.onDestroy();
    }

    @Override
    public void onBinderDead() {
        Shizuku.removeBinderReceivedListener(this::shizukuBinderReceived);
        Shizuku.removeBinderDeadListener(this);
        Log.w(TAG, "Shizuku binder is dead, stopping service");
        restart();
    }

    private synchronized void restart() {
        isShizukuInitialized = false;
        isServiceRunning = false;
        Shizuku.removeBinderReceivedListener(this::shizukuBinderReceived);
        Shizuku.removeBinderDeadListener(this);
        Log.w(TAG, "Restarting service...");
        Intent broadcastIntent = new Intent(this, RestartReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, broadcastIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        long triggerTime = SystemClock.elapsedRealtime() + 1000; // 1 segundo
        alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent);
        stopSelf();
    }

}