package com.gym.tracker.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutTemplateEntity::class,
        TemplateSlotEntity::class,
        SlotOptionEntity::class,
        WorkoutSessionEntity::class,
        SessionEntryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class GymDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun templateDao(): TemplateDao
    abstract fun sessionDao(): SessionDao

    companion object {
        const val NAME = "gym.db"

        /**
         * Все миграции схемы. При КАЖДОМ изменении схемы: version + 1, новая Migration здесь
         * и тест в MigrationTest. Деструктивные миграции запрещены.
         */
        val MIGRATIONS: Array<Migration> = arrayOf()

        fun build(context: Context, inMemory: Boolean = false): GymDatabase {
            val builder = if (inMemory) {
                Room.inMemoryDatabaseBuilder(context, GymDatabase::class.java)
            } else {
                Room.databaseBuilder(context, GymDatabase::class.java, NAME)
            }
            return builder
                .addMigrations(*MIGRATIONS)
                .addCallback(SeedCallback)
                .build()
        }
    }

    /** Заполняет БД начальными данными при первом создании (в той же транзакции, что и схема). */
    object SeedCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            insertSeed(db, SeedData.build(System.currentTimeMillis()))
        }

        fun insertSeed(db: SupportSQLiteDatabase, seed: SeedData.Bundle) {
            val abort = SQLiteDatabase.CONFLICT_ABORT
            seed.exercises.forEach {
                db.insert("exercises", abort, ContentValues().apply {
                    put("id", it.id); put("name", it.name)
                    put("defaultSets", it.defaultSets); put("defaultReps", it.defaultReps)
                    put("createdAt", it.createdAt); put("updatedAt", it.updatedAt)
                })
            }
            seed.templates.forEach {
                db.insert("workout_templates", abort, ContentValues().apply {
                    put("id", it.id); put("name", it.name); put("sortOrder", it.sortOrder)
                    put("createdAt", it.createdAt); put("updatedAt", it.updatedAt)
                })
            }
            seed.slots.forEach {
                db.insert("template_slots", abort, ContentValues().apply {
                    put("id", it.id); put("templateId", it.templateId); put("name", it.name)
                    put("sortOrder", it.sortOrder); put("favoriteExerciseId", it.favoriteExerciseId)
                    put("createdAt", it.createdAt); put("updatedAt", it.updatedAt)
                })
            }
            seed.options.forEach {
                db.insert("slot_options", abort, ContentValues().apply {
                    put("slotId", it.slotId); put("exerciseId", it.exerciseId); put("sortOrder", it.sortOrder)
                    put("createdAt", it.createdAt); put("updatedAt", it.updatedAt)
                })
            }
        }
    }
}
