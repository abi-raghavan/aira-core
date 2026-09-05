# Room reads generated schema metadata at runtime.
-keep class * extends androidx.room.RoomDatabase
-keep class com.example.calmcompanion.AlertDeliveryWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.example.calmcompanion.AlertEscalationWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-dontwarn org.conscrypt.**
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy
