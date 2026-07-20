# ── Hermes Agent for Android ── ProGuard / R8 Rules ──────────────────

# Keep Hermes Agent core classes
-keep class com.nousresearch.hermes.agent.** { *; }

# Keep JavaScript interface methods accessible from WebView
-keepclassmembers class com.nousresearch.hermes.agent.bridge.HermesBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Gson serialization for policy enforcer
-keep class com.nousresearch.hermes.agent.model.PolicyEnforcer { *; }
-keep class kotlinx.serialization.** { *; }
-keep class com.google.gson.** { *; }

# Chaquopy Python runtime
-keep class com.chaquo.python.** { *; }

# OkHttp (WebSocket client)
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# WebView / AndroidX
-keep class android.webkit.** { *; }
-keep class androidx.webkit.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep enum values for serialized config
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
