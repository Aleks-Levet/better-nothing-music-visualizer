-keep class com.nothing.ketchum.** { public protected *; }
-dontwarn com.nothing.ketchum.**
-keep class com.nothing.thirdparty.** { public protected *; }
-dontwarn com.nothing.thirdparty.**

-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-keep class moe.shizuku.** { *; }
-dontwarn moe.shizuku.**

# Aggressive R8 Optimizations
-optimizationpasses 5
-repackageclasses ''
-allowaccessmodification

# Strip debug log output from release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

# Optimize annotations and attributes in DEX
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontnote **
