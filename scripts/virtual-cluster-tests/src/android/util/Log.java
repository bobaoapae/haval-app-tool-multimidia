package android.util;

public final class Log {
    public static int w(String tag, String message) { return 0; }
    public static int e(String tag, String message, Throwable error) {
        System.err.println(tag + ": " + message + ": " + error);
        return 0;
    }
}
