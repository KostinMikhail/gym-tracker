package com.gym.tracker.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gym.tracker.data.db.Feel
import com.gym.tracker.data.db.GymDatabase
import com.gym.tracker.domain.backup.HistoryCsvExporter
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Снимок истории из настоящей БД и вызовы «журнал изменился» из репозитория. */
@RunWith(AndroidJUnit4::class)
class HistoryBackupRepositoryTest {
    private lateinit var db: GymDatabase
    private lateinit var repo: GymRepository
    private var time = 1_000_000L
    private var changes = 0

    @Before
    fun setUp() {
        db = GymDatabase.build(ApplicationProvider.getApplicationContext(), inMemory = true)
        repo = GymRepository(db, clock = { time++ }, onHistoryChanged = { changes++ })
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun historyRowsReflectCurrentDataAndChangesAreSignalled() = runTest {
        val template = db.templateDao().getTemplates().first { it.name == "Фулбади А" }
        val slots = db.templateDao().getSlots(template.id)
        val chest = slots.first { it.name == "Грудь" }
        val legs = slots.first { it.name == "Жим ногами" }
        val session = repo.startSession(template.id, LocalDate.of(2026, 11, 20))
        assertEquals(0, changes) // пустая тренировка журнал не меняет

        val e1 = repo.saveEntry(null, session, chest.id, chest.favoriteExerciseId!!, 3, 12, 22.5, Feel.EASY)
        repo.saveEntry(null, session, legs.id, legs.favoriteExerciseId!!, 4, 10, 100.0, Feel.HARD)
        assertEquals(2, changes)

        var csv = HistoryCsvExporter.build(repo.historyRows()).trimEnd().lines()
        assertEquals(3, csv.size)
        // Жим ногами — первый слот шаблона, поэтому идёт раньше груди, хотя записан позже.
        assertTrue(csv[1].startsWith("2026-11-20,Фулбади А,Жим ногами,"))
        assertTrue(csv[2].contains(",Грудь,") && csv[2].contains(",22.5,Легко,$session,$e1"))

        // Переименование упражнения попадает в снимок.
        assertEquals(GymRepository.UpdateResult.OK, repo.updateExercise(chest.favoriteExerciseId!!, "Жим в Хаммере", 3, 12))
        assertEquals(3, changes)
        csv = HistoryCsvExporter.build(repo.historyRows()).trimEnd().lines()
        assertTrue(csv[2].contains(",Жим в Хаммере,"))

        repo.saveEntry(e1, session, chest.id, chest.favoriteExerciseId!!, 3, 12, 25.0, Feel.NORMAL)
        repo.finishSession(session)
        repo.deleteEntry(e1)
        assertEquals(6, changes)
        assertEquals(1, repo.historyRows().size)

        repo.deleteSession(session)
        assertEquals(7, changes)
        assertEquals(0, repo.historyRows().size)
    }
}
