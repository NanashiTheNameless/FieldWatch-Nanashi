# Release builds are not debuggable. R8 optimizes and shrinks, but does not rename:
# crash notes are read on the phone, so stack traces must stay readable.
-dontobfuscate

# Entry points Android constructs by name. Everything else may be optimized or removed.
-keep class dev.namelessnanashi.fieldwatch.FieldwatchApp { *; }
-keep class dev.namelessnanashi.fieldwatch.MainActivity { *; }
-keep class dev.namelessnanashi.fieldwatch.radio.ScanService { *; }
-keep class dev.namelessnanashi.fieldwatch.elevated.FieldwatchAdminReceiver { *; }
-keep class dev.namelessnanashi.fieldwatch.elevated.ShellService { *; }
-keep class dev.namelessnanashi.fieldwatch.elevated.IShellService { *; }
-keep class dev.namelessnanashi.fieldwatch.elevated.IShellService$* { *; }
# Entry class named in META-INF/xposed/java_init.list, loaded by the framework.
-keep class dev.namelessnanashi.fieldwatch.elevated.xposed.FieldwatchXposedModule { *; }
# Read by the module through reflection inside the Bluetooth process.
-keep class dev.namelessnanashi.fieldwatch.elevated.xposed.XposedTargets { *; }

# @Serializable classes: keep their fields and generated serializers.
-keepclassmembers @kotlinx.serialization.Serializable class dev.namelessnanashi.fieldwatch.** {
    <fields>;
    *** Companion;
    *** serializer(...);
}
-keepclasseswithmembers class dev.namelessnanashi.fieldwatch.** {
    public static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

-keepattributes RuntimeVisibleAnnotations, AnnotationDefault, InnerClasses, Signature, Exception, EnclosingMethod, SourceFile, LineNumberTable

# config.json / signature packs
-dontwarn kotlinx.serialization.**
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}

-dontwarn androidx.compose.**

# Shizuku user service and binder glue.
-keep class rikka.shizuku.** { *; }
# The Vector/libxposed API is supplied by the framework at runtime, not packaged.
-dontwarn io.github.libxposed.**
