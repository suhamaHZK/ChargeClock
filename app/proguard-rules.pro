# Charge Clock — R8 / ProGuard keep rules (Compose + DataStore)

# Keep Compose runtime metadata
-keep class androidx.compose.runtime.** { *; }

# Kotlin metadata / coroutines often needed with minify
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler

# DataStore Preferences (reflection / serializers)
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}
-keep class androidx.datastore.** { *; }

# App model / settings used via reflection-free Flow — keep public API surface
-keep class com.kmmm_engineering.chargeclock.** { *; }

# Enums used from DataStore string keys
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# HoloColorPicker (View-based, accessed via AndroidView)
-keep class com.larswerkman.holocolorpicker.** { *; }
-dontwarn com.larswerkman.holocolorpicker.**
