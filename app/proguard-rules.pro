# Room: сгенерированные реализации БД и DAO создаются через рефлексию.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class **_Impl { *; }
# Enum'ы хранятся в БД по имени (Feel.name) — имена не должны меняться.
-keepclassmembers enum com.gym.tracker.** { *; }
# kotlinx.serialization (ответы GitHub API): сгенерированные сериализаторы и serializer() у классов.
-keepclassmembers @kotlinx.serialization.Serializable class com.gym.tracker.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.gym.tracker.**$$serializer { *; }
