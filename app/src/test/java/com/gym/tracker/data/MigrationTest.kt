package com.gym.tracker.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gym.tracker.data.db.GymDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Проверка миграций по экспортированным схемам (app/schemas).
 * При каждом повышении версии БД добавляйте сюда тест «вставить данные в старую версию →
 * мигрировать → проверить, что данные на месте».
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        GymDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun allMigrationsFromV1ProduceCurrentSchema() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO exercises (id, name, defaultSets, defaultReps, createdAt, updatedAt) VALUES ('x', 'Тест', 3, 12, 1, 1)")
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, LATEST_VERSION, true, *GymDatabase.MIGRATIONS)
        db.query("SELECT name FROM exercises WHERE id = 'x'").use {
            it.moveToFirst()
            assertEquals("Тест", it.getString(0))
        }
        db.close()
    }

    /** 1 → 2 (очередь выгрузки): все данные тренировок на месте, новая таблица пуста и рабочая. */
    @Test
    fun migration1To2KeepsWorkoutData() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO exercises VALUES ('ex1', 'Жим лёжа в тренажёре', 3, 12, 100, 100)")
            execSQL("INSERT INTO exercises VALUES ('ex2', 'Тяга верхнего блока', 4, 10, 100, 200)")
            execSQL("INSERT INTO workout_templates VALUES ('t1', 'Фулбади А', 0, 100, 100)")
            execSQL("INSERT INTO template_slots VALUES ('sl1', 't1', 'Грудь', 1, 'ex1', 100, 100)")
            execSQL("INSERT INTO slot_options VALUES ('sl1', 'ex1', 0, 100, 100)")
            execSQL("INSERT INTO workout_sessions VALUES ('s1', 't1', '2026-11-18', 1763480000000, 100, 100)")
            execSQL("INSERT INTO workout_sessions VALUES ('s2', 't1', '2026-11-20', NULL, 300, 300)")
            execSQL("INSERT INTO session_entries VALUES ('e1', 's1', 'sl1', 'ex1', 3, 12, 45.5, 'NORMAL', 110, 110)")
            execSQL("INSERT INTO session_entries VALUES ('e2', 's1', NULL, 'ex2', 4, 10, 0.0, 'HARD', 120, 130)")
            execSQL("INSERT INTO session_entries VALUES ('e3', 's2', 'sl1', 'ex1', 3, 10, 50.0, 'EASY', 310, 310)")
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, *GymDatabase.MIGRATIONS)
        fun count(table: String) = db.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
        assertEquals(2, count("exercises"))
        assertEquals(1, count("workout_templates"))
        assertEquals(1, count("template_slots"))
        assertEquals(1, count("slot_options"))
        assertEquals(2, count("workout_sessions"))
        assertEquals(3, count("session_entries"))
        assertEquals(0, count("sync_ops"))
        db.query("SELECT sessionId, slotId, exerciseId, sets, reps, weightKg, feel, createdAt, updatedAt FROM session_entries WHERE id = 'e2'").use {
            it.moveToFirst()
            assertEquals("s1", it.getString(0))
            assertEquals(true, it.isNull(1))
            assertEquals("ex2", it.getString(2))
            assertEquals(4, it.getInt(3))
            assertEquals(10, it.getInt(4))
            assertEquals(0.0, it.getDouble(5), 0.0)
            assertEquals("HARD", it.getString(6))
            assertEquals(120L, it.getLong(7))
            assertEquals(130L, it.getLong(8))
        }
        db.query("SELECT date, finishedAt FROM workout_sessions WHERE id = 's1'").use {
            it.moveToFirst()
            assertEquals("2026-11-18", it.getString(0))
            assertEquals(1763480000000L, it.getLong(1))
        }
        // Новая таблица работает: уникальность по entryId.
        db.execSQL("INSERT INTO sync_ops (entryId, op, createdAt, attempts) VALUES ('e1', 'UPSERT', 1, 0)")
        db.execSQL("INSERT OR REPLACE INTO sync_ops (entryId, op, createdAt, attempts) VALUES ('e1', 'DELETE', 2, 0)")
        assertEquals(1, count("sync_ops"))
        db.close()
    }

    @Test
    fun currentRoomOpensDatabaseCreatedFromV1Schema() {
        helper.createDatabase(TEST_DB, 1).close()
        val room = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), GymDatabase::class.java, TEST_DB)
            .addMigrations(*GymDatabase.MIGRATIONS)
            .build()
        room.openHelper.writableDatabase // бросит исключение, если схема не совпадает или нет миграции
        room.close()
    }

    companion object {
        /** Абсолютный путь: на Windows Room не сопоставляет короткое имя с путём через «\». */
        private val TEST_DB: String
            get() = ApplicationProvider.getApplicationContext<android.content.Context>()
                .getDatabasePath("migration-test").absolutePath
        private const val LATEST_VERSION = 2
    }
}
