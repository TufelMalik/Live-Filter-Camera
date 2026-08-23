# Proguard / R8 rules for Live Filter Camera

# Keep GPUImage filters and Native methods
-keep class jp.co.cyberagent.android.gpuimage.** { *; }
-dontwarn jp.co.cyberagent.android.gpuimage.**

# Keep Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Keep Models
-keep class com.techquantum.livefiltercamera.model.** { *; }
-keep class com.techquantum.livefiltercamera.gallery.** { *; }

# Strip android.util.Log calls in release builds (Phase 14 requirement)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
