package br.com.redesurftank.havalshisuku.utils;

import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.concurrent.CountDownLatch;

import br.com.redesurftank.havalshisuku.models.CommandListener;
import moe.shizuku.server.IRemoteProcess;
import moe.shizuku.server.IShizukuService;
import rikka.shizuku.Shizuku;

public class ShizukuUtils {

    private static final String TAG = "ShizukuUtils";

    public static boolean isShizukuAvailable() {
        return Shizuku.pingBinder();
    }

    public static String runCommandAndGetOutput(String[] command) {
        IBinder binder = Shizuku.getBinder();
        if (binder == null) {
            Log.e(TAG, "Shizuku binder is null. Is Shizuku running?");
            return "";
        }
        IShizukuService shizukuService = IShizukuService.Stub.asInterface(binder);
        if (shizukuService == null) {
            Log.e(TAG, "Shizuku service is null. Is Shizuku running?");
            return "";
        }
        IRemoteProcess process = null;
        try {
            int retries = 0;
            while (retries < 3) {
                try {
                    process = shizukuService.newProcess(command, null, null);
                    if (process != null) break;
                } catch (Exception e) {
                    if (retries == 2) throw e;
                    Thread.sleep(500);
                    IBinder b = Shizuku.getBinder();
                    if (b != null) shizukuService = IShizukuService.Stub.asInterface(b);
                }
                retries++;
            }

            if (process == null) {
                throw new Exception("Failed to create remote process for command: " + String.join(" ", command));
            }

            StringBuilder output = new StringBuilder();
            StringBuilder errorOutput = new StringBuilder();

            // Fetch and close stdin immediately (prevents FD leak on Shizuku server)
            try (ParcelFileDescriptor pfdOut = process.getOutputStream()) {
                // just close it
            } catch (Exception ignored) {}

            // Read stderr in a background thread to prevent deadlocks
            ParcelFileDescriptor pfdErr = process.getErrorStream();
            Thread errThread = null;
            if (pfdErr != null) {
                errThread = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(pfdErr.getFileDescriptor())))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            synchronized (errorOutput) {
                                errorOutput.append(line).append("\n");
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error reading stderr", e);
                    } finally {
                        try { pfdErr.close(); } catch (Exception ignored) {}
                    }
                });
                errThread.start();
            }

            // Read stdout
            ParcelFileDescriptor pfdIn = process.getInputStream();
            if (pfdIn != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(pfdIn.getFileDescriptor())))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error reading stdout", e);
                } finally {
                    try { pfdIn.close(); } catch (Exception ignored) {}
                }
            }

            if (errThread != null) {
                try {
                    errThread.join(5000); // wait for stderr thread to finish, max 5s
                } catch (InterruptedException ignored) {}
            }

            int exitCode = process.waitFor();
            String cmdString = String.join(" ", command);
            
            // exit code 1 for grep just means "no matches found", not an error
            boolean isGrepNoMatch = exitCode == 1 && cmdString.contains("grep");

            if (exitCode != 0 && !isGrepNoMatch) {
                Log.e(TAG, "Command failed: " + cmdString + " (exit code: " + exitCode + ")");
                synchronized (errorOutput) {
                    if (errorOutput.length() > 0) {
                        Log.e(TAG, "Error output:\n" + errorOutput.toString().trim());
                    }
                }
                if (output.length() > 0) {
                    Log.d(TAG, "Standard output:\n" + output.toString().trim());
                }
            } else if (exitCode == 0) {
                // Only log successful completions at debug level
                // Log.d(TAG, "Command finished: " + cmdString);
            }

            return output.toString().trim();

        } catch (Exception e) {
            Log.e(TAG, "Error running command: " + String.join(" ", command), e);
            return "";
        } finally {
            if (process != null) {
                try {
                    process.destroy();
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }

    public static String runCommandAndWaitForString(String[] command, String... targetStrings) {
        IBinder binder = Shizuku.getBinder();
        if (binder == null) {
            Log.e(TAG, "Shizuku binder is null. Is Shizuku running?");
            return "";
        }
        IShizukuService shizukuService = IShizukuService.Stub.asInterface(binder);
        if (shizukuService == null) {
            Log.e(TAG, "Shizuku service is null. Is Shizuku running?");
            return "";
        }
        IRemoteProcess process = null;
        try {
            process = shizukuService.newProcess(command, null, null);
            if (process == null) {
                throw new Exception("Failed to create remote process for command: " + String.join(" ", command));
            }

            IRemoteProcess finalProcess = process;
            final StringBuilder output = new StringBuilder();
            final CountDownLatch latch = new CountDownLatch(1);
            final Object lock = new Object();
            final boolean[] found = {false};

            // Fetch and close stdin immediately (prevents FD leak on Shizuku server)
            try (ParcelFileDescriptor pfdOut = finalProcess.getOutputStream()) {
                // just close it
            } catch (Exception ignored) {}

            // Drain stderr to prevent deadlock
            ParcelFileDescriptor pfdErr = finalProcess.getErrorStream();
            if (pfdErr != null) {
                new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(pfdErr.getFileDescriptor())))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            // discard
                        }
                    } catch (Exception ignored) {
                    } finally {
                        try { pfdErr.close(); } catch (Exception ignored) {}
                    }
                }).start();
            }

            // Thread for monitoring output
            new Thread(() -> {
                try (ParcelFileDescriptor pfd = finalProcess.getInputStream()) {
                    if (pfd != null) {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(pfd.getFileDescriptor())))) {
                            String line;
                            boolean continueAppending = true;
                            while ((line = reader.readLine()) != null) {
                                synchronized (lock) {
                                    if (continueAppending) {
                                        output.append(line).append("\n");
                                        for (String target : targetStrings) {
                                            if (line.contains(target)) {
                                                found[0] = true;
                                                latch.countDown();
                                                continueAppending = false;
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error monitoring output for command: " + String.join(" ", command), e);
                } finally {
                    // If reached end without finding, unblock and return whatever
                    synchronized (lock) {
                        if (!found[0]) {
                            latch.countDown();
                        }
                    }
                }
            }).start();

            // Thread for waiting process to finish and cleanup
            new Thread(() -> {
                try {
                    int exitCode = finalProcess.waitFor();
                    if (exitCode != 0) {
                        Log.e(TAG, "Command failed: " + String.join(" ", command) + " (exit code: " + exitCode + ")");
                    } else {
                        Log.d(TAG, "Process finished with code: " + exitCode);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error waiting for process: " + String.join(" ", command), e);
                } finally {
                    try {
                        finalProcess.destroy();
                    } catch (Exception ignored) {
                    }
                    synchronized (lock) {
                        if (!found[0]) {
                            latch.countDown();
                        }
                    }
                }
            }).start();

            // Block until found or end
            latch.await();

            synchronized (lock) {
                if (found[0]) {
                    return output.toString().trim();
                } else {
                    Log.e(TAG, "String not found for command: " + String.join(" ", command) +
                            ". Output was: " + output.toString().trim());
                    return "";
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error starting command: " + String.join(" ", command), e);
            return "";
        } finally {
            if (process != null) {
                try {
                    process.destroy();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static IRemoteProcess startStreamingProcess(String[] command) {
        IBinder binder = Shizuku.getBinder();
        if (binder == null) {
            Log.e(TAG, "Shizuku binder is null");
            return null;
        }
        IShizukuService shizukuService = IShizukuService.Stub.asInterface(binder);
        if (shizukuService == null) {
            Log.e(TAG, "Shizuku service is null");
            return null;
        }
        try {
            return shizukuService.newProcess(command, null, null);
        } catch (Exception e) {
            Log.e(TAG, "Error starting streaming process: " + String.join(" ", command), e);
            return null;
        }
    }

    public static void runCommandOnBackground(String[] command, CommandListener listener) {
        IBinder binder = Shizuku.getBinder();
        if (binder == null) {
            Log.e(TAG, "Shizuku binder is null. Is Shizuku running?");
            if (listener != null) listener.onFinished(-1);
            return;
        }
        IShizukuService shizukuService = IShizukuService.Stub.asInterface(binder);
        if (shizukuService == null) {
            Log.e(TAG, "Shizuku service is null. Is Shizuku running?");
            if (listener != null) listener.onFinished(-1);
            return;
        }
        IRemoteProcess process = null;
        try {
            process = shizukuService.newProcess(command, null, null);
            if (process == null) {
                throw new Exception("Failed to create remote process for command: " + String.join(" ", command));
            }

            IRemoteProcess finalProcess = process;

            // Fetch and close stdin immediately (prevents FD leak on Shizuku server)
            try (ParcelFileDescriptor pfdOut = finalProcess.getOutputStream()) {
                // just close it
            } catch (Exception ignored) {}

            // Thread for stdout
            new Thread(() -> {
                try (ParcelFileDescriptor pfd = finalProcess.getInputStream()) {
                    if (pfd != null) {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(pfd.getFileDescriptor())))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (listener != null) {
                                    listener.onStdout(line);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error reading stdout for command: " + String.join(" ", command), e);
                }
            }).start();

            // Thread for stderr
            new Thread(() -> {
                try (ParcelFileDescriptor pfd = finalProcess.getErrorStream()) {
                    if (pfd != null) {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(pfd.getFileDescriptor())))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (listener != null) {
                                    listener.onStderr(line);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error reading stderr for command: " + String.join(" ", command), e);
                }
            }).start();

            // Thread for waitFor and cleanup
            new Thread(() -> {
                try {
                    int exitCode = finalProcess.waitFor();
                    if (exitCode != 0) {
                        Log.e(TAG, "Background command failed: " + String.join(" ", command) + " (exit code: " + exitCode + ")");
                    }
                    if (listener != null) {
                        listener.onFinished(exitCode);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error waiting for command: " + String.join(" ", command), e);
                } finally {
                    try {
                        finalProcess.destroy();
                    } catch (Exception ignored) {
                    }
                }
            }).start();

        } catch (Exception e) {
            Log.e(TAG, "Error starting command: " + String.join(" ", command), e);
            if (process != null) {
                try {
                    process.destroy();
                } catch (Exception ignored) {
                }
            }
        }
    }

}
