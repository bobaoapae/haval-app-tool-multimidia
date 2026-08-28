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
import android.content.SharedPreferences;
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
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

import br.com.redesurftank.App;
import br.com.redesurftank.havalshisuku.ambientlight.AmbientLightService;
import br.com.redesurftank.havalshisuku.diagnostics.ClusterPersistentEventLogger;
import br.com.redesurftank.havalshisuku.broadcastReceivers.DispatchAllDatasReceiver;
import br.com.redesurftank.havalshisuku.broadcastReceivers.RestartReceiver;
import br.com.redesurftank.havalshisuku.managers.AndroidAutoPatchManager;
import br.com.redesurftank.havalshisuku.managers.CarPlayPatchManager;
import br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher;
import br.com.redesurftank.havalshisuku.managers.ServiceManager;
import br.com.redesurftank.havalshisuku.managers.StealthModeManager;
import br.com.redesurftank.havalshisuku.managers.HotRouterManager;
import br.com.redesurftank.havalshisuku.models.CommandListener;
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys;
import br.com.redesurftank.havalshisuku.utils.IPTablesUtils;
import br.com.redesurftank.havalshisuku.utils.ShizukuUtils;
import br.com.redesurftank.havalshisuku.utils.TelnetClientWrapper;
import br.com.redesurftank.havalshisuku.utils.TermuxUtils;
import rikka.shizuku.Shizuku;

public class ForegroundService extends Service implements Shizuku.OnBinderDeadListener {

    private static final String TAG = "ForegroundService";
    private static final String CHANNEL_ID = "ForegroundServiceChannel";
    /**
     * Canal do Modo Concessionaria. IMPORTANCE_MIN e o mais discreto que o Android 9 aceita para
     * um foreground service: sem som, sem vibracao e sem icone na barra de status. Um FGS NAO pode
     * ficar sem notificacao nenhuma nesta versao do Android, entao o alvo e "nao chamar atencao e
     * nao dizer o nome do app", nao "sumir".
     *
     * Canal SEPARADO de proposito: a importancia de um NotificationChannel nao pode ser rebaixada
     * pelo app depois de criado (so o usuario mexe), entao reaproveitar o canal normal deixaria a
     * notificacao presa em IMPORTANCE_LOW.
     */
    private static final String QUIET_CHANNEL_ID = "ForegroundServiceChannelQuiet";
    private static final int NOTIFICATION_ID = 1;

    /**
     * Instancia viva do servico, para o StealthModeManager pedir a troca da notificacao no
     * instante em que o modo entra ou sai. volatile: quem chama vem de outra thread.
     */
    private static volatile ForegroundService runningInstance;
    private static final int MAX_AUTOMATIC_SHIZUKU_BOOTSTRAP_UID = 10999;
    private static final long SHIZUKU_BINDER_RECEIVE_TIMEOUT_MS = 15000L;
    private static final int SHIZUKU_BINDER_TIMEOUTS_BEFORE_RESTART = 3;
    private static final String CARPLAY_PATCH_VERSION_KEY = "carPlayPatchAutoMountPatchVersion";
    private static final String CARPLAY_HVAC_FOCUS_PATCH_VERSION = "app_visual_d0_focus_service_conditional_camera_native1904x704_v14";

    private HandlerThread handlerThread;
    private Handler backgroundHandler;
    private final Object lifecycleLock = new Object();
    private volatile boolean isShizukuInitialized = false;
    private volatile boolean isServiceRunning = false;
    private volatile int shizukuBinderTimeoutCount = 0;

    private final Runnable timeoutRunnable = () -> {
        if (!isShizukuInitialized) {
            if (Shizuku.pingBinder()) {
                backgroundHandler.post(ForegroundService.this::shizukuBinderReceived);
                return;
            }

            int timeoutCount;
            synchronized (lifecycleLock) {
                if (isShizukuInitialized) return;
                shizukuBinderTimeoutCount++;
                timeoutCount = shizukuBinderTimeoutCount;
            }

            if (shouldRestartAfterShizukuBinderTimeoutForTest(
                    timeoutCount,
                    SHIZUKU_BINDER_TIMEOUTS_BEFORE_RESTART
            )) {
                Log.w(TAG, "Shizuku initialization timed out after " + timeoutCount + " checks. Restarting service...");
                ClusterPersistentEventLogger.logText(
                        "foreground_service_shizuku_timeout",
                        "restarting=true timeoutCount=" + timeoutCount
                );
                restart();
                return;
            }

            Log.w(TAG, "Shizuku binder not received yet after timeout " + timeoutCount + "; keeping service alive.");
            ClusterPersistentEventLogger.logText(
                    "foreground_service_shizuku_timeout",
                    "restarting=false timeoutCount=" + timeoutCount
            );
            armShizukuBinderListenerWithTimeout();
        }
    };

    private void armShizukuBinderListenerWithTimeout() {
        backgroundHandler.removeCallbacks(timeoutRunnable);
        backgroundHandler.postDelayed(timeoutRunnable, SHIZUKU_BINDER_RECEIVE_TIMEOUT_MS);
        Shizuku.addBinderReceivedListenerSticky(ForegroundService.this::shizukuBinderReceived);
        if (Shizuku.pingBinder()) {
            backgroundHandler.post(ForegroundService.this::shizukuBinderReceived);
        }
    }

    public static boolean shouldRestartAfterShizukuBinderTimeoutForTest(
            int timeoutCount,
            int maxTimeoutsBeforeRestart
    ) {
        return timeoutCount >= maxTimeoutsBeforeRestart;
    }

    private List<String> getLocalTelnetBootstrapHosts() {
        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        List<String> interfaceHosts = new ArrayList<>();

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) continue;

                Enumeration<java.net.InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        interfaceHosts.add(address.getHostAddress());
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to enumerate local telnet bootstrap hosts: " + e.getMessage());
        }

        for (String host : interfaceHosts) {
            if (host.startsWith("192.168.33.")) {
                hosts.add(host);
            }
        }
        hosts.add("192.168.33.11");
        hosts.add("192.168.33.1");

        for (String host : interfaceHosts) {
            if (!host.startsWith("192.168.33.")) {
                hosts.add(host);
            }
        }

        hosts.add("127.0.0.1");
        hosts.add("localhost");

        return new ArrayList<>(hosts);
    }

    private TelnetClientWrapper connectToLocalTelnetForBootstrap() throws IOException {
        IOException lastError = null;
        for (String host : getLocalTelnetBootstrapHosts()) {
            TelnetClientWrapper telnetClient = new TelnetClientWrapper();
            try {
                telnetClient.connect(host, 23);
                Log.w(TAG, "Connected to Shizuku bootstrap telnet at " + host);
                return telnetClient;
            } catch (IOException e) {
                lastError = e;
                Log.w(TAG, "Shizuku bootstrap telnet unavailable at " + host + ": " + e.getMessage());
                try {
                    telnetClient.disconnect();
                } catch (Exception ignored) {}
            }
        }

        if (lastError != null) throw lastError;
        throw new IOException("No local telnet bootstrap hosts available");
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private boolean validateSelfInstallationForAutomaticShizuku(Context context) {
        var sharedPreferences = App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE);
        boolean bypassIntegrityCheck = sharedPreferences.getBoolean(SharedPreferencesKeys.BYPASS_SELF_INSTALLATION_INTEGRITY_CHECK.getKey(), false);

        try {
            var selfPackageInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), 0);
            int uid = selfPackageInfo.uid;
            if (uid > MAX_AUTOMATIC_SHIZUKU_BOOTSTRAP_UID) {
                sharedPreferences.edit()
                        .putBoolean(SharedPreferencesKeys.SELF_INSTALLATION_INTEGRITY_CHECK.getKey(), false)
                        .apply();
                String message = "Application UID " + uid + " is greater than " + MAX_AUTOMATIC_SHIZUKU_BOOTSTRAP_UID + ", Shizuku cannot be started automatically from Impulse.";
                if (bypassIntegrityCheck) {
                    Log.w(TAG, message + " Bypass is enabled; attempting bootstrap anyway.");
                    return true;
                }
                Log.e(TAG, message);
                Toast.makeText(context, "Instalação sem UID correto. Reinstale pelo exploit para iniciar Shizuku automaticamente.", Toast.LENGTH_LONG).show();
                return false;
            }

            if (!sharedPreferences.getBoolean(SharedPreferencesKeys.SELF_INSTALLATION_INTEGRITY_CHECK.getKey(), false)) {
                Log.w(TAG, "Automatic Shizuku bootstrap UID validated: " + uid);
                sharedPreferences.edit()
                        .putBoolean(SharedPreferencesKeys.SELF_INSTALLATION_INTEGRITY_CHECK.getKey(), true)
                        .apply();
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to validate application UID for Shizuku bootstrap: " + e.getMessage(), e);
            return false;
        }
    }

    private String resolveShizukuLibPath(TelnetClientWrapper telnetClient) throws Exception {
        var sharedPreferences = App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE);
        String shizukuLibLocation = sharedPreferences.getString("shizuku_lib_location", "").trim();

        if (!shizukuLibLocation.isEmpty()) {
            String cachedStatus = telnetClient.executeCommand(
                    "if [ -f " + shellQuote(shizukuLibLocation) + " ]; then echo OK; else echo MISSING; fi"
            );
            if (cachedStatus.contains("OK")) {
                Log.w(TAG, "Using validated Shizuku lib location: " + shizukuLibLocation);
                return shizukuLibLocation;
            }
            Log.w(TAG, "Cached Shizuku lib location is stale: " + shizukuLibLocation);
        }

        String filePath = telnetClient.executeCommand("find /data/app -name libshizuku.so 2>/dev/null | head -n 1").trim();

        if (filePath.isEmpty()) {
            sharedPreferences.edit().remove("shizuku_lib_location").apply();
            throw new RuntimeException("libshizuku.so not found");
        }

        sharedPreferences.edit().putString("shizuku_lib_location", filePath).apply();
        Log.w(TAG, "libshizuku.so found at: " + filePath);
        return filePath;
    }

    private boolean waitForShizukuServer(TelnetClientWrapper telnetClient) throws Exception {
        for (int i = 0; i < 10; i++) {
            if (Shizuku.pingBinder()) {
                return true;
            }

            String pid = telnetClient.executeCommand("pidof shizuku_server 2>/dev/null || true").trim();
            if (!pid.isEmpty()) {
                Log.w(TAG, "shizuku_server is running with pid=" + pid);
                return true;
            }

            Thread.sleep(500);
        }
        return Shizuku.pingBinder();
    }

    private void ensureAdbTcpPort() throws Exception {
        String tcpPort = ShizukuUtils
                .runCommandAndGetOutput(new String[]{"getprop", "service.adb.tcp.port"})
                .trim();
        if ("5555".equals(tcpPort)) {
            Log.w(TAG, "ADB TCP already configured on port 5555");
            return;
        }

        Log.w(TAG, "Configuring ADB TCP on port 5555. Current service.adb.tcp.port=" + tcpPort);
        ShizukuUtils.runCommandAndGetOutput(new String[]{"setprop", "service.adb.tcp.port", "5555"});
        ShizukuUtils.runCommandAndGetOutput(new String[]{"stop", "adbd"});
        Thread.sleep(500);
        ShizukuUtils.runCommandAndGetOutput(new String[]{"start", "adbd"});
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ClusterPersistentEventLogger.logText("foreground_service_on_create", "created=true");
        runningInstance = this;
        createNotificationChannel();
        handlerThread = new HandlerThread("BackgroundThread");
        handlerThread.start();
        backgroundHandler = new Handler(handlerThread.getLooper());
    }

    private void scheduleCarPlayPatchAutoMount(String reason) {
        if (backgroundHandler == null) {
            Log.e(TAG, "Cannot schedule CarPlay patch auto-mount: backgroundHandler is null");
            return;
        }

        backgroundHandler.post(() -> {
            try {
                var prefs = App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE);
                if (!CARPLAY_HVAC_FOCUS_PATCH_VERSION.equals(prefs.getString(CARPLAY_PATCH_VERSION_KEY, ""))) {
                    Log.i(TAG, "Enabling CarPlay HVAC focus patch auto-mount for version " + CARPLAY_HVAC_FOCUS_PATCH_VERSION + " (" + reason + ")");
                    prefs.edit()
                            .putBoolean(SharedPreferencesKeys.CARPLAY_PATCH_AUTO_MOUNT.getKey(), true)
                            .putString(CARPLAY_PATCH_VERSION_KEY, CARPLAY_HVAC_FOCUS_PATCH_VERSION)
                            .apply();
                }

                boolean shouldAutoMountCarPlay = prefs.getBoolean(SharedPreferencesKeys.CARPLAY_PATCH_AUTO_MOUNT.getKey(), true);
                if (shouldAutoMountCarPlay) {
                    Log.i(TAG, "Checking CarPlay patch auto-mount (" + reason + ")...");
                    CarPlayPatchManager.INSTANCE.ensureMounted();
                } else {
                    Log.d(TAG, "CarPlay patch auto-mount is disabled in settings (" + reason + ").");
                }
            } catch (Exception e) {
                Log.e(TAG, "CarPlay patch auto-mount check failed (" + reason + "): " + e.getMessage(), e);
            }
        });
    }

    private void startCarPlaySystemUiIconWatchdogSafely(String reason) {
        try {
            DisplayAppLauncher.INSTANCE.startCarPlaySystemUiIconWatchdog();
            // Verifica UMA vez por partida se saiu versao nova (espera a rede aparecer sozinho).
            br.com.redesurftank.havalshisuku.managers.UpdateNoticeManager.checkOnStart();
        } catch (Exception e) {
            Log.e(TAG, "CarPlay SystemUI icon watchdog scheduling failed (" + reason + "): " + e.getMessage(), e);
        }
    }

    private void ensurePersistentBottomBarStarted(SharedPreferences sharedPreferences, String reason) {
        if (!sharedPreferences.getBoolean(SharedPreferencesKeys.PERSISTENT_BOTTOM_BAR.getKey(), false)) {
            Log.d(TAG, "Persistent bottom bar disabled (" + reason + ").");
            return;
        }

        if (!android.provider.Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Overlay permission not granted, skipping persistent bottom bar (" + reason + ").");
            return;
        }

        Log.w(TAG, "Ensuring persistent bottom bar (" + reason + ")...");
        Intent bottomBarIntent = new Intent(this, br.com.redesurftank.havalshisuku.services.BottomBarService.class);
        startService(bottomBarIntent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (br.com.redesurftank.havalshisuku.BuildConfig.SIMULATOR_MODE) {
            return onStartCommandSimulated(intent, flags, startId);
        }

        var sharedPreferences = App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE);


        synchronized (lifecycleLock) {
            if (isServiceRunning) {
                Log.w(TAG, "Service is already running, skipping start.");
                ClusterPersistentEventLogger.logText(
                        "foreground_service_already_running",
                        "flags=" + flags + " startId=" + startId
                );
                startCarPlaySystemUiIconWatchdogSafely("already-running");
                ensurePersistentBottomBarStarted(sharedPreferences, "already-running");
                AmbientLightService.startIfEnabled(this);
                return START_STICKY; // Retorna imediatamente se o serviço já estiver rodando
            }
            isServiceRunning = true; // Marca o serviço como rodando
        }
        try {
            Log.w(TAG, "Service started");
            ClusterPersistentEventLogger.logText(
                    "foreground_service_started",
                    "flags=" + flags + " startId=" + startId
            );

            // Clear any pending background tasks (retry loops) from previous starts
            backgroundHandler.removeCallbacksAndMessages(null);

            var context = getApplicationContext();
            // Criar notificacao para o Foreground Service (discreta no Modo Concessionaria).
            Notification notification = buildServiceNotification();

            startForeground(NOTIFICATION_ID, notification);

            // Start bottom bar as early as possible if enabled
            ensurePersistentBottomBarStarted(sharedPreferences, "service-start");
            AmbientLightService.startIfEnabled(this);

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

            if (!validateSelfInstallationForAutomaticShizuku(context)) {
                synchronized (lifecycleLock) {
                    isShizukuInitialized = false;
                    isServiceRunning = false;
                }
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            }


            backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (Shizuku.pingBinder()) {
                            Log.w(TAG, "Shizuku binder already available, skipping telnet bootstrap.");
                            armShizukuBinderListenerWithTimeout();
                            return;
                        }

                        // Mirrors the safe part of Haval Tool install.sh: execute libshizuku.so from
                        // a root telnet context, without reinstalling APKs or touching system files.
                        var telnetClient = connectToLocalTelnetForBootstrap();

                        String filePath = resolveShizukuLibPath(telnetClient);

                        String executeCommand = shellQuote(filePath) + " 2>&1";
                        Log.w(TAG, "Executing command: " + executeCommand);
                        String result = telnetClient.executeCommand(executeCommand);

                        if (Pattern.compile("killed \\d+ \\(shizuku_server\\)").matcher(result).find()) {
                            Log.w(TAG, "Old process killed, statically waiting 5 seconds to avoid bind on an already dead Shizuku process");
                            // Espera o Shizuku reiniciar
                            Thread.sleep(5000);
                        }
                        Log.w(TAG, "Command executed successfully: " + result);

                        if (result.contains("fatal:") || result.contains("Can't find service")) {
                            throw new IOException("Shizuku starter failed: " + result);
                        }

                        if (!waitForShizukuServer(telnetClient)) {
                            throw new IOException("Shizuku server did not become available after starter command");
                        }

                        // Deploy and start the low-overhead native iptables watchdog script
                        try {
                            Log.w(TAG, "Deploying native iptables watchdog script...");
                            telnetClient.executeCommand("echo '#!/system/bin/sh' > /data/local/tmp/iptables_watchdog.sh");
                            telnetClient.executeCommand("echo 'while true; do' >> /data/local/tmp/iptables_watchdog.sh");
                            telnetClient.executeCommand("echo '    iptables -C OUTPUT -j ACCEPT 2>/dev/null || iptables -I OUTPUT 1 -j ACCEPT' >> /data/local/tmp/iptables_watchdog.sh");
                            telnetClient.executeCommand("echo '    iptables -C INPUT -j ACCEPT 2>/dev/null || iptables -I INPUT 1 -j ACCEPT' >> /data/local/tmp/iptables_watchdog.sh");
                            telnetClient.executeCommand("echo '    sleep 5' >> /data/local/tmp/iptables_watchdog.sh");
                            telnetClient.executeCommand("echo 'done' >> /data/local/tmp/iptables_watchdog.sh");

                            telnetClient.executeCommand("chmod 755 /data/local/tmp/iptables_watchdog.sh");
                            telnetClient.executeCommand("pkill -9 -f /data/local/tmp/iptables_watchdog.sh");
                            telnetClient.executeCommand("nohup /data/local/tmp/iptables_watchdog.sh >/dev/null 2>&1 &");
                            Log.w(TAG, "Native iptables watchdog script deployed and started.");
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to deploy native iptables watchdog: " + e.getMessage(), e);
                        }

                        telnetClient.disconnect();
                        armShizukuBinderListenerWithTimeout();
                    } catch (Exception e) {
                        Log.e(TAG, "Error executing shell commands: " + e.getMessage(), e);
                        backgroundHandler.postDelayed(this, 1000);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in onStartCommand: " + e.getMessage(), e);
            synchronized (lifecycleLock) {
                isServiceRunning = false; // Marca o serviço como não rodando em caso de erro
            }
            stopSelf(); // Para o serviço em caso de erro
            return START_NOT_STICKY; // Não reinicia o serviço automaticamente
        }

        return START_STICKY; // Garante que o serviço seja reiniciado se for morto
    }

    private int onStartCommandSimulated(Intent intent, int flags, int startId) {
        synchronized (lifecycleLock) {
            if (isServiceRunning) {
                Log.w(TAG, "Service is already running (Simulator), skipping start.");
                return START_STICKY;
            }
            isServiceRunning = true;
        }
        Log.w(TAG, "Simulator Service started");

        Notification notification = buildServiceNotification();

        startForeground(NOTIFICATION_ID, notification);

        // We can safely initialize services immediately in simulator mode
        ServiceManager.getInstance().initializeServicesSimulated(getApplicationContext());

        return START_STICKY;
    }

    private void shizukuBinderReceived() {
        synchronized (lifecycleLock) {
            if (!isServiceRunning) return;
            if (isShizukuInitialized) return;
            isShizukuInitialized = true;
            shizukuBinderTimeoutCount = 0;
        }
        Shizuku.removeBinderReceivedListener(this::shizukuBinderReceived);
        Log.w(TAG, "Shizuku binder received");
        Shizuku.addBinderDeadListener(this);
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
        scheduleCarPlayPatchAutoMount("shizuku-ready");

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
                    ensureAdbTcpPort();
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

        try {
            if (IPTablesUtils.unlockInputOutputAll()) {
                Log.w(TAG, "IPTables unlocked successfully");
            } else {
                Log.e(TAG, "Failed to unlock IPTables");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error unlocking IPTables: " + e.getMessage(), e);
        }
        try {
            DisplayAppLauncher.INSTANCE.startCarPlayClusterContractWatchdog();
            DisplayAppLauncher.INSTANCE.startCarPlayMainDisplayBootAutostart();
        } catch (Exception e) {
            Log.e(TAG, "Projection watchdog/autostart scheduling failed: " + e.getMessage(), e);
        }
        boolean initSuccess = ServiceManager.getInstance().initializeServices(getApplicationContext());
        if (!initSuccess) {
            Log.e(TAG, "Service initialization failed, restarting...");
            restart();
            return;
        }
        startCarPlaySystemUiIconWatchdogSafely("post-service-init");
        try {
            DisplayAppLauncher.INSTANCE.startAndroidAutoSteeringMediaFocusKeepAlive();
        } catch (Exception e) {
            Log.e(TAG, "Android Auto steering media focus keepalive scheduling failed: " + e.getMessage(), e);
        }

        // Auto-mount Android Auto patches if installed but not yet mounted
        backgroundHandler.post(() -> {
            try {
                var prefs = App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE);

                // Initialize default if not set
                if (!prefs.contains(SharedPreferencesKeys.AA_PATCH_AUTO_MOUNT.getKey())) {
                    boolean aaInstalled = false;
                    try {
                        getPackageManager().getPackageInfo("com.ts.androidauto.app", 0);
                        aaInstalled = true;
                    } catch (Exception ignored) {}

                    Log.i(TAG, "AA Patch auto-mount preference not set. Defaulting to " + aaInstalled + " (AA installed: " + aaInstalled + ")");
                    prefs.edit().putBoolean(SharedPreferencesKeys.AA_PATCH_AUTO_MOUNT.getKey(), aaInstalled).apply();
                }

                boolean shouldAutoMount = prefs.getBoolean(SharedPreferencesKeys.AA_PATCH_AUTO_MOUNT.getKey(), false);
                if (shouldAutoMount) {
                    Log.i(TAG, "Checking Android Auto patch auto-mount...");
                    AndroidAutoPatchManager.INSTANCE.ensureMounted();
                } else {
                    Log.d(TAG, "AA patch auto-mount is disabled in settings.");
                }

            } catch (Exception e) {
                Log.e(TAG, "Projection patch auto-mount check failed: " + e.getMessage(), e);
            }
        });

        try {
            // Quem usava o gatilho do retrovisor (retirado) precisa acordar com a funcao ligada no
            // gatilho novo, nao sem ela. Idempotente: apaga as chaves antigas ao migrar.
            br.com.redesurftank.havalshisuku.managers.FoldMirrorTriggerMigration.run(
                    App.getDeviceProtectedContext()
                            .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE),
                    StealthModeManager.INSTANCE.isActive()
            );
        } catch (Exception e) {
            Log.e(TAG, "Error migrating fold-mirror trigger: " + e.getMessage(), e);
        }

        try {
            HotRouterManager.getInstance().onServicesReady();
        } catch (Exception e) {
            Log.e(TAG, "Error starting HotRouter: " + e.getMessage(), e);
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
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) {
            Log.e(TAG, "NotificationManager indisponivel; nao da pra criar os canais");
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Background Service Channel", NotificationManager.IMPORTANCE_LOW);
        manager.createNotificationChannel(channel);

        // Criado junto do normal: o modo pode entrar a qualquer momento e o canal precisa existir
        // antes da primeira notificacao postada nele.
        NotificationChannel quiet = new NotificationChannel(QUIET_CHANNEL_ID, "Servico do sistema", NotificationManager.IMPORTANCE_MIN);
        quiet.setShowBadge(false);
        quiet.enableLights(false);
        quiet.enableVibration(false);
        quiet.setSound(null, null);
        quiet.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        manager.createNotificationChannel(quiet);
    }

    /**
     * O UNICO lugar que monta a notificacao do foreground service. Antes ela era construida em
     * dois pontos (o start normal e o do simulador), o que faria a decisao do Modo Concessionaria
     * valer num e no outro nao.
     *
     * No modo, a notificacao nao pode DIZER que o Impulse esta rodando: titulo neutro, sem
     * subtitulo, sem relogio, no canal IMPORTANCE_MIN.
     */
    private Notification buildServiceNotification() {
        boolean stealth = false;
        try {
            stealth = StealthModeManager.isActive();
        } catch (Throwable t) {
            Log.e(TAG, "Falha lendo a flag do Modo Concessionaria; usando a notificacao normal", t);
        }

        if (stealth) {
            return new NotificationCompat.Builder(this, QUIET_CHANNEL_ID)
                    .setContentTitle("Servico do sistema")
                    .setSmallIcon(android.R.drawable.ic_notification_overlay)
                    .setPriority(NotificationCompat.PRIORITY_MIN)
                    .setShowWhen(false)
                    .setSilent(true)
                    .setOngoing(true)
                    .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                    .build();
        }

        String title = br.com.redesurftank.havalshisuku.BuildConfig.SIMULATOR_MODE
                ? "Haval App (Simulator)"
                : "Aplicacao em execucao";
        String text = br.com.redesurftank.havalshisuku.BuildConfig.SIMULATOR_MODE
                ? "Simulador em execucao"
                : "Seu app esta rodando em segundo plano";
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_notification_overlay)
                .build();
    }

    /**
     * Reemite a notificacao com o mesmo NOTIFICATION_ID para a troca de canal/conteudo valer na
     * hora — sem isso, a notificacao antiga ficaria na tela ate o proximo start do servico.
     * Chamado pelo StealthModeManager ao entrar e ao sair do modo.
     *
     * Estatico e tolerante a servico morto: se nao ha instancia viva, nao ha notificacao para
     * trocar, e o proximo onStartCommand ja monta a certa.
     */
    public static void refreshNotificationForStealth() {
        ForegroundService service = runningInstance;
        if (service == null) {
            Log.w(TAG, "Sem ForegroundService vivo; nada a reemitir");
            return;
        }
        try {
            NotificationManager manager = service.getSystemService(NotificationManager.class);
            if (manager == null) {
                return;
            }
            manager.notify(NOTIFICATION_ID, service.buildServiceNotification());
            Log.w(TAG, "Notificacao do foreground service reemitida (stealth=" + StealthModeManager.isActive() + ")");
        } catch (Throwable t) {
            Log.e(TAG, "Falha ao reemitir a notificacao do foreground service", t);
        }
    }

    @Override
    public void onDestroy() {
        ServiceManager.getInstance().stopManagedBackgroundGuards(getApplicationContext());
        // Drop our cluster callback before the process goes away. The car's
        // ClusterService otherwise keeps dispatching to a dead binder and throws
        // DeadObjectException, which can stop card reports reaching the next instance.
        try {
            ServiceManager.getInstance().releaseClusterCallback();
        } catch (Exception e) {
            Log.e(TAG, "Failed to release cluster callback on destroy", e);
        }
        try {
            ServiceManager.getInstance().releaseInputListener();
        } catch (Exception e) {
            Log.e(TAG, "Failed to release input listener on destroy", e);
        }
        if (handlerThread != null) {
            handlerThread.quitSafely();
        }
        synchronized (lifecycleLock) {
            isServiceRunning = false;
        }
        Shizuku.removeBinderReceivedListener(this::shizukuBinderReceived);
        Shizuku.removeBinderDeadListener(this);
        if (runningInstance == this) {
            runningInstance = null;
        }
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

    private void restart() {
        synchronized (lifecycleLock) {
            isShizukuInitialized = false;
            isServiceRunning = false;
            shizukuBinderTimeoutCount = 0;
        }
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
