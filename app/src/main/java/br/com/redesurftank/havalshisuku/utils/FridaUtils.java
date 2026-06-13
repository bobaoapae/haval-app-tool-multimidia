package br.com.redesurftank.havalshisuku.utils;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import br.com.redesurftank.App;
import br.com.redesurftank.havalshisuku.BuildConfig;
import br.com.redesurftank.havalshisuku.R;
import br.com.redesurftank.havalshisuku.models.CommandListener;
import moe.shizuku.server.IShizukuService;
import rikka.shizuku.Shizuku;

public class FridaUtils {
    private static final String TAG = "FridaUtils";
    public static final String FRIDA_SERVER_PATH = "/data/local/tmp/fridaserver";
    public static final String FRIDA_INJECTOR_PATH = "/data/local/tmp/fridainjector";
    private static final String SCRIPT_DIR = "/data/local/tmp/";

    public enum InjectMode {
        NECESSARY,
        OPTIONAL,
        MANUAL
    }

    public enum ScriptProcess {
        INTELLIGENT_VEHICLE_CONTROL("com.beantechs.accountservice:remote", R.raw.com_beantechs_accountservice, InjectMode.OPTIONAL),
        SYSTEM_SERVER("system_server", R.raw.system_server, InjectMode.MANUAL),
        TS_CAR_POWER_CONTROLLER("com.ts.car.power.controller.core", R.raw.com_ts_car_power_controller_core, InjectMode.OPTIONAL),
        ;// Add more processes as needed

        private final String process;
        private final int resourceId;
        private final String baseName;
        private final String fileName;
        private final String scriptPath;
        private final InjectMode injectMode;

        ScriptProcess(String process, int resourceId, InjectMode injectMode) {
            this.process = process;
            this.resourceId = resourceId;
            this.baseName = process.substring(process.lastIndexOf('.') + 1).replace(":", "_");
            this.fileName = baseName + ".js";
            this.scriptPath = SCRIPT_DIR + fileName;
            this.injectMode = injectMode;
        }

        public String getFileName() {
            return fileName;
        }

        public String getScriptPath() {
            return scriptPath;
        }

        public String getProcess() {
            return process;
        }

        public int getResourceId() {
            return resourceId;
        }

        public InjectMode getInjectMode() {
            return injectMode;
        }

        public String getBaseName() {
            return baseName;
        }
    }

    public static boolean ensureFridaServerRunning() {
        if (!BuildConfig.EMBED_FRIDA_TOOLS) {
            Log.w(TAG, "Embedded Frida tools are disabled in this build variant");
            return false;
        }
        IShizukuService shizukuService = IShizukuService.Stub.asInterface(Shizuku.getBinder());
        try {
            if (!extractFridaFiles())
                return false;
            shizukuService.newProcess(new String[]{"setenforce", "0"}, null, null).waitFor();
            shizukuService.newProcess(new String[]{"chmod", "755", FRIDA_SERVER_PATH}, null, null).waitFor();
            shizukuService.newProcess(new String[]{"chmod", "755", FRIDA_INJECTOR_PATH}, null, null).waitFor();
            try {
                shizukuService.newProcess(new String[]{"pkill", "-f", "fridainjector"}, null, null).waitFor();
            } catch (Exception e) {
                Log.w(TAG, "Failed to pkill existing injectors: " + e.getMessage());
            }
            String isRunning = ShizukuUtils.runCommandAndGetOutput(new String[]{"pidof", "fridaserver"}).trim();
            if (!isRunning.isEmpty()) {
                Log.w(TAG, "Frida server is already running with pid: " + isRunning);
                return true;
            }
            String shellCmd = "setsid " + FRIDA_SERVER_PATH + " >/dev/null 2>&1 < /dev/null &";
            shizukuService.newProcess(new String[]{"/bin/sh", "-c", shellCmd}, null, null).waitFor();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error ensuring Frida server is running", e);
            return false;
        }
    }

    public static boolean injectAllScripts() {
        if (!extractFridaScripts())
            return false;
        for (ScriptProcess sp : ScriptProcess.values()) {
            switch (sp.getInjectMode()) {
                case NECESSARY:
                    if (!injectScript(sp.getScriptPath(), sp.getProcess(), sp.getBaseName(), true))
                        return false;
                    break;
                case OPTIONAL:
                    injectScript(sp.getScriptPath(), sp.getProcess(), sp.getBaseName(), false);
                    break;
                case MANUAL:
                    // Do nothing
                    break;
            }
        }
        return true;
    }

    public static boolean injectScript(ScriptProcess scriptProcess, boolean synchronous) {
        return injectScript(scriptProcess.getScriptPath(), scriptProcess.getProcess(), scriptProcess.getBaseName(), synchronous);
    }

    public static void injectSystemServer() {
        injectScript(ScriptProcess.SYSTEM_SERVER.getScriptPath(), ScriptProcess.SYSTEM_SERVER.getProcess(), ScriptProcess.SYSTEM_SERVER.getBaseName(), false);
    }


    private static boolean injectScript(String scriptPath, String targetProcess, String baseName, boolean synchronous) {
        if (!BuildConfig.EMBED_FRIDA_TOOLS) {
            Log.w(TAG, "Skipping Frida injection for " + targetProcess + ": embedded tools are disabled");
            return false;
        }
        Log.w(TAG, "Handling Frida script injection for: " + scriptPath + " into process: " + targetProcess);
        String pid = ShizukuUtils.runCommandAndGetOutput(new String[]{"sh", "-c", "ps -A | grep '" + targetProcess + "' | awk '{print $2}'"}).trim();
        if (pid.contains("\n")) {
            // Handle multiple PIDs (e.g. grep matching multiple processes)
            pid = pid.split("\n")[0].trim();
        }
        if (pid.isEmpty()) {
            Log.e(TAG, "Target process not found: " + targetProcess);
            return false;
        }
        Log.w(TAG, "Target process PID: " + pid);
        String logFile = SCRIPT_DIR + baseName + ".log";
        String injectorCmd = FRIDA_INJECTOR_PATH + " -D local -p " + pid + " -s " + scriptPath;
        String injectorPattern = "[f]ridainjector -D local -p " + pid + " -s " + scriptPath;
        String grepOutput = ShizukuUtils.runCommandAndGetOutput(new String[]{"sh", "-c", "ps -A -f | grep '" + injectorPattern + "'"});
        boolean isInjected = !grepOutput.trim().isEmpty();
        if (!isInjected) {
            Log.w(TAG, "InjectorPattern: " + injectorPattern);
            Log.w(TAG, "Injecting Frida script into " + targetProcess + " with command: " + injectorCmd);
            String shellCmd = "setsid " + injectorCmd + " > " + logFile + " 2>&1 < /dev/null &";
            ShizukuUtils.runCommandAndGetOutput(new String[]{"sh", "-c", shellCmd});
            ShizukuUtils.runCommandAndGetOutput(new String[]{"chmod", "666", logFile});
        } else {
            Log.w(TAG, "Frida script already injected into " + targetProcess);
        }
        CommandListener listener = new CommandListener() {
            @Override
            public void onStdout(String line) {
                Log.w(TAG, "[Target: " + targetProcess + "] Frida script output: " + line);
            }

            @Override
            public void onStderr(String line) {
                Log.e(TAG, "[Target: " + targetProcess + "] Frida script error: " + line);
            }

            @Override
            public void onFinished(int exitCode) {
                Log.w(TAG, "[Target: " + targetProcess + "] Tail finished with exit code: " + exitCode);
            }
        };
        // Only start tail if not already running for this file
        String tailPattern = "[t]ail -f " + logFile;
        String tailCheck = ShizukuUtils.runCommandAndGetOutput(new String[]{"sh", "-c", "ps -A -f | grep '" + tailPattern + "'"});
        if (tailCheck.trim().isEmpty()) {
            ShizukuUtils.runCommandOnBackground(new String[]{"tail", "-f", logFile}, listener);
            Log.w(TAG, "Started tail for " + logFile);
        } else {
            Log.w(TAG, "Tail already running for " + logFile);
        }
        return true;
    }

    private static boolean extractFridaScripts() {
        try {
            String destDir = App.getContext().getCacheDir().getAbsolutePath();

            for (ScriptProcess sp : ScriptProcess.values()) {
                InputStream in = App.getContext().getResources().openRawResource(sp.getResourceId());
                File outFile = new File(destDir, sp.getFileName());
                FileOutputStream out = new FileOutputStream(outFile);

                byte[] buffer = new byte[1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                in.close();
                out.flush();
                out.close();
                ShizukuUtils.runCommandAndGetOutput(new String[]{"cp", outFile.getAbsolutePath(), sp.getScriptPath()});
                Log.w(TAG, "Extracted Frida script: " + sp.getFileName() + " to " + SCRIPT_DIR);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error extracting Frida files", e);
            return false;
        }

        return true;
    }

    private static boolean extractFridaFiles() {
        try {
            // Check if fridaserver and fridainjector already exist in target destination to prevent "Text file busy"
            String checkServer = ShizukuUtils.runCommandAndGetOutput(new String[]{"ls", FRIDA_SERVER_PATH}).trim();
            String checkInject = ShizukuUtils.runCommandAndGetOutput(new String[]{"ls", FRIDA_INJECTOR_PATH}).trim();

            boolean serverExists = !checkServer.isEmpty() && !checkServer.contains("No such file");
            boolean injectExists = !checkInject.isEmpty() && !checkInject.contains("No such file");

            if (serverExists && injectExists) {
                return true;
            }

            String destDir = App.getContext().getCacheDir().getAbsolutePath();
            byte[] buffer = new byte[1024];
            int read;

            if (!serverExists) {
                InputStream in = App.getContext().getResources().openRawResource(R.raw.fridaserver);
                File outFile = new File(destDir, "fridaserver");
                FileOutputStream out = new FileOutputStream(outFile);
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                in.close();
                out.flush();
                out.close();
                ShizukuUtils.runCommandAndGetOutput(new String[]{"cp", outFile.getAbsolutePath(), FRIDA_SERVER_PATH});
            }

            if (!injectExists) {
                InputStream in = App.getContext().getResources().openRawResource(R.raw.fridainject);
                File outFile = new File(destDir, "fridainjector");
                FileOutputStream out = new FileOutputStream(outFile);
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                in.close();
                out.flush();
                out.close();
                ShizukuUtils.runCommandAndGetOutput(new String[]{"cp", outFile.getAbsolutePath(), FRIDA_INJECTOR_PATH});
            }
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error extracting Frida files", e);
        }

        return false;
    }
}
