-keep class com.autolink.** { *; }
-keepnames class com.autolink.**
-keepclassmembers class com.autolink.** { *; }
-keepclassmembers enum com.autolink.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public *;
}
-keepclassmembers class com.autolink.** implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
# SDK vendorizado do host do Android Auto (com.ts.androidauto.sdk.aidl.data.*): mantém o CREATOR
# dos Parcelables de navegação p/ o wire-format do LinkCallback bater com o do host.
-keepclassmembers class com.ts.androidauto.** implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Preview/release builds must not emit diagnostic logcat output. The debug and internalDebug
# variants keep logs available for bench/central diagnostics.
-maximumremovedandroidloglevel 7
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
    public static *** println(...);
    public static *** isLoggable(...);
    public static *** getStackTraceString(...);
}
