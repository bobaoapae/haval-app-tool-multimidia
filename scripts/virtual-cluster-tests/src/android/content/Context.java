package android.content;

import android.hardware.display.DisplayManager;

public class Context {
    public static final int MODE_PRIVATE = 0;
    private final SharedPreferences preferences;
    private final DisplayManager displays;
    public Context(SharedPreferences preferences, DisplayManager displays) {
        this.preferences = preferences;
        this.displays = displays;
    }
    public SharedPreferences getSharedPreferences(String name, int mode) { return preferences; }
    public <T> T getSystemService(Class<T> type) { return type.cast(displays); }
}
