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
        private const val LATEST_VERSION = 1
    }
}
