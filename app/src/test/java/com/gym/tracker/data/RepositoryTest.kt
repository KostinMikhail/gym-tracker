package com.gym.tracker.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gym.tracker.data.db.Feel
import com.gym.tracker.data.db.GymDatabase
import com.gym.tracker.data.db.TemplateSlotEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** DAO + репозиторий на настоящем SQLite (Robolectric), БД заполнена сид-коллбэком. */
@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: GymDatabase
    private lateinit var repo: GymRepository
    private var time = 1_000_000L

    @Before
    fun setUp() {
        db = GymDatabase.build(ApplicationProvider.getApplicationContext(), inMemory = true)
        repo = GymRepository(db, clock = { time++ })
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun templateId(name: String) =
        db.templateDao().getTemplates().first { it.name == name }.id

    private suspend fun slot(templateName: String, slotName: String): TemplateSlotEntity =
        db.templateDao().getSlots(templateId(templateName)).first { it.name == slotName }

    private suspend fun exerciseId(name: String) = db.exerciseDao().getAll().first { it.name == name }.id

    @Test
    fun freshInstallIsSeeded() = runTest {
        val templates = db.templateDao().getTemplates()
        assertEquals(listOf("Фулбади А", "Фулбади Б"), templates.map { it.name })
        val slotsA = db.templateDao().getSlots(templates[0].id)
        assertEquals(
            listOf("Жим ногами", "Грудь", "Спина (вертикальная тяга)", "Задняя поверхность бедра", "Плечи", "Пресс"),
            slotsA.map { it.name },
        )
        val chest = slotsA[1]
        val options = repo.getOptionsForSlot(chest.id).map { it.exercise.name }
        assertEquals(listOf("Жим лёжа в тренажёре", "Жим гантелей лёжа", "Жим штанги лёжа"), options)
        assertEquals(exerciseId("Жим лёжа в тренажёре"), chest.favoriteExerciseId)
        // Все слоты: есть варианты, избранное среди них.
        templates.forEach { t ->
            db.templateDao().getSlots(t.id).forEach { s ->
                val opts = repo.getOptionsForSlot(s.id)
                assertTrue(opts.isNotEmpty())
                assertTrue(opts.any { it.exercise.id == s.favoriteExerciseId })
            }
        }
        // Общее упражнение — одна строка.
        assertEquals(1, db.exerciseDao().getAll().count { it.name == "Скручивание рычажное" })
    }

    @Test
    fun lastEntryIsMostRecentAcrossTemplates() = runTest {
        val lever = exerciseId("Скручивание рычажное")
        val pressA = slot("Фулбади А", "Пресс")
        val coreB = slot("Фулбади Б", "Кор")

        assertNull(repo.lastEntry(lever))

        val s1 = repo.startSession(templateId("Фулбади А"), LocalDate.of(2026, 11, 1))
        repo.saveEntry(null, s1, pressA.id, lever, 3, 12, 30.0, Feel.NORMAL)
        val s2 = repo.startSession(templateId("Фулбади Б"), LocalDate.of(2026, 11, 3))
        repo.saveEntry(null, s2, coreB.id, lever, 3, 15, 35.0, Feel.HARD)
        // Тренировка задним числом не должна стать «последней».
        val s0 = repo.startSession(templateId("Фулбади А"), LocalDate.of(2026, 10, 20))
        repo.saveEntry(null, s0, pressA.id, lever, 3, 10, 20.0, Feel.EASY)

        val last = repo.lastEntry(lever)!!
        assertEquals(LocalDate.of(2026, 11, 3), last.date)
        assertEquals(35.0, last.entry.weightKg, 0.0)
        assertEquals(Feel.HARD, last.entry.feel)

        // Текущая тренировка исключается.
        val current = repo.startSession(templateId("Фулбади А"), LocalDate.of(2026, 11, 5))
        repo.saveEntry(null, current, pressA.id, lever, 3, 12, 40.0, Feel.EASY)
        assertEquals(35.0, repo.lastEntry(lever, excludeSessionId = current)!!.entry.weightKg, 0.0)
        assertEquals(40.0, repo.lastEntry(lever)!!.entry.weightKg, 0.0)
    }

    @Test
    fun favoriteSwitchingKeepsExactlyOne() = runTest {
        val chest = slot("Фулбади А", "Грудь")
        val db2 = exerciseId("Жим гантелей лёжа")
        repo.setFavorite(chest.id, db2)
        val updated = db.templateDao().getSlot(chest.id)!!
        assertEquals(db2, updated.favoriteExerciseId)
        // Избранное — одно значение на слот; другие слоты не затронуты.
        assertEquals(exerciseId("Жим ногами в тренажёре"), slot("Фулбади А", "Жим ногами").favoriteExerciseId)
        val barbell = exerciseId("Жим штанги лёжа")
        repo.setFavorite(chest.id, barbell)
        assertEquals(barbell, db.templateDao().getSlot(chest.id)!!.favoriteExerciseId)
    }

    @Test
    fun emptySessionDiscard() = runTest {
        val id = repo.startSession(templateId("Фулбади А"), LocalDate.of(2026, 11, 9))
        assertEquals(GymRepository.FinishResult.EMPTY, repo.finishSession(id))
        assertNull(repo.getSession(id)!!.finishedAt)
        assertTrue(repo.discardIfEmpty(id))
        assertNull(repo.getSession(id))
    }

    @Test
    fun nonEmptySessionFinishesAndIsNotDiscarded() = runTest {
        val id = repo.startSession(templateId("Фулбади А"), LocalDate.of(2026, 11, 9))
        val legs = slot("Фулбади А", "Жим ногами")
        repo.saveEntry(null, id, legs.id, legs.favoriteExerciseId!!, 3, 12, 100.0, Feel.NORMAL)
        assertEquals(GymRepository.FinishResult.FINISHED, repo.finishSession(id))
        assertNotNull(repo.getSession(id)!!.finishedAt)
        assertFalse(repo.discardIfEmpty(id))
        assertNotNull(repo.getSession(id))
    }

    @Test
    fun editingEntryUpdatesSameRowWithNewExercise() = runTest {
        val id = repo.startSession(templateId("Фулбади А"), LocalDate.of(2026, 11, 9))
        val chest = slot("Фулбади А", "Грудь")
        val entryId = repo.saveEntry(null, id, chest.id, chest.favoriteExerciseId!!, 3, 12, 50.0, Feel.NORMAL)
        val newEx = exerciseId("Жим гантелей лёжа")
        val again = repo.saveEntry(entryId, id, chest.id, newEx, 4, 10, 22.5, Feel.HARD)
        assertEquals(entryId, again)
        val entries = repo.observeEntriesForSession(id).first()
        assertEquals(1, entries.size)
        assertEquals(newEx, entries[0].exerciseId)
        assertEquals(22.5, entries[0].weightKg, 0.0)
    }

    @Test
    fun addOptionReusesExistingExerciseByName() = runTest {
        val legs = slot("Фулбади А", "Жим ногами")
        val before = db.exerciseDao().getAll().size
        val reused = repo.addOptionToSlot(legs.id, "  гоблет-присед ", 5, 5)
        assertEquals(exerciseId("Гоблет-присед"), reused)
        assertEquals(before, db.exerciseDao().getAll().size)
        val created = repo.addOptionToSlot(legs.id, "Приседания со штангой", 5, 5)
        assertEquals(before + 1, db.exerciseDao().getAll().size)
        val names = repo.getOptionsForSlot(legs.id).map { it.exercise.name }
        assertEquals(listOf("Жим ногами в тренажёре", "Гоблет-присед", "Приседания со штангой"), names)
        val ex = db.exerciseDao().getById(created)!!
        assertEquals(5, ex.defaultSets)
        // Новое упражнение появляется в истории после использования.
        val s = repo.startSession(templateId("Фулбади А"), LocalDate.of(2026, 11, 9))
        repo.saveEntry(null, s, legs.id, created, 5, 5, 80.0, Feel.NORMAL)
        assertTrue(repo.observeExercisesWithEntries().first().any { it.id == created })
        assertEquals(1, repo.observeExerciseHistory(created).first().size)
    }

    @Test
    fun renameExerciseKeepsHistory() = runTest {
        val legs = slot("Фулбади А", "Жим ногами")
        val ex = legs.favoriteExerciseId!!
        val s = repo.startSession(templateId("Фулбади А"), LocalDate.of(2026, 11, 9))
        repo.saveEntry(null, s, legs.id, ex, 3, 12, 100.0, Feel.NORMAL)
        assertEquals(GymRepository.UpdateResult.OK, repo.updateExercise(ex, "Жим ногами", 4, 10))
        assertEquals(GymRepository.UpdateResult.NAME_TAKEN, repo.updateExercise(ex, "Гоблет-присед", 4, 10))
        val history = repo.observeExerciseHistory(ex).first()
        assertEquals(1, history.size)
        assertEquals("Фулбади А", history[0].templateName)
        assertEquals("Жим ногами", db.exerciseDao().getById(ex)!!.name)
    }

    @Test
    fun deleteSessionCascadesEntriesAndSummaries() = runTest {
        val legs = slot("Фулбади А", "Жим ногами")
        val s = repo.startSession(templateId("Фулбади А"), LocalDate.of(2026, 11, 9))
        val e = repo.saveEntry(null, s, legs.id, legs.favoriteExerciseId!!, 3, 12, 100.0, Feel.NORMAL)
        val summary = repo.observeSessionSummaries().first().single()
        assertEquals(1, summary.entryCount)
        assertEquals("Фулбади А", summary.templateName)
        repo.deleteSession(s)
        assertNull(repo.getEntry(e))
        assertTrue(repo.observeSessionSummaries().first().isEmpty())
    }

    @Test
    fun templatesLastDate() = runTest {
        val a = templateId("Фулбади А")
        repo.startSession(a, LocalDate.of(2026, 11, 5))
        repo.startSession(a, LocalDate.of(2026, 11, 1))
        val list = repo.observeTemplatesWithLastDate().first()
        assertEquals(LocalDate.of(2026, 11, 5), list.first { it.template.id == a }.lastDate)
        assertNull(list.first { it.template.name == "Фулбади Б" }.lastDate)
    }
}
