package android.content;

/** Minimal API fake: this is not Android SharedPreferences implementation coverage. */
public interface SharedPreferences {
    boolean getBoolean(String key, boolean fallback);
    String getString(String key, String fallback);
    void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener);
    void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener);
    interface OnSharedPreferenceChangeListener {
        void onSharedPreferenceChanged(SharedPreferences preferences, String key);
    }
}
