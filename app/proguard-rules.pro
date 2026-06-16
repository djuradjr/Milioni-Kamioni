# MoreMoney ProGuard rules

# Keep Room entities
-keep class com.example.stayfree.data.local.entity.** { *; }

# Keep Hilt generated classes
-keepclasseswithmembernames class * { @dagger.* <fields>; }
-keep class dagger.hilt.** { *; }

# Strip android.util.Log calls from release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}
