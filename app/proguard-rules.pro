# Room: сгенерированные реализации БД и DAO создаются через рефлексию.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class **_Impl { *; }
# Enum'ы хранятся в БД по имени (Feel.name) — имена не должны меняться.
-keepclassmembers enum com.gym.tracker.** { *; }
