package br.com.redesurftank.havalshisuku.models;

/** Kotlin enum API stand-in; run.py checks each key against its production Kotlin source. */
public enum SharedPreferencesKeys {
    ENABLE_VIRTUAL_CLUSTER("enableVirtualCluster"),
    ENABLE_INSTRUMENT_PROJECTOR("enableInstrumentProjector"),
    DEFAULT_DISPLAY_APP_PACKAGE("defaultDisplayAppPackage");
    private final String key;
    SharedPreferencesKeys(String key) { this.key = key; }
    public String getKey() { return key; }
}
