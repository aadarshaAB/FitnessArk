# Add project specific ProGuard rules here.

# Keep Room entities
-keep class com.fitnessark.data.local.entity.** { *; }

# Keep Koin
-keep class org.koin.** { *; }
-keepnames class org.koin.** { *; }

# Keep MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }

# Keep Coil
-keep class coil.** { *; }

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep data classes used in JSON serialization (ZipUtils)
-keepclassmembers class com.fitnessark.** {
    public <init>(...);
    public ** get*();
    public void set*(...);
}

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Glance (F6 widget): generates RemoteViews via reflection
-keep class androidx.glance.** { *; }
-dontwarn androidx.glance.**
-keep class com.fitnessark.ui.widget.** { *; }
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker { *; }
