package com.gym.tracker.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gym.tracker.data.db.Feel
import com.gym.tracker.data.db.GymDatabase
import com.gym.tracker.data.db.SyncOpEntity
import com.gym.tracker.data.db.SyncOpType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Правила постановки в очередь выгрузки (`sync_ops`) в репозитории. */
@RunWith(AndroidJUnit4::class)
class SyncOutboxTest {
    private lateinit var db: GymDatabase
    private lateinit var repo: GymRepository
    private var time = 1_000_000L
    private var notified = 0

    @Before
    fun setUp() {
        db = GymDatabase.build(ApplicationProvider.getApplicationContext(), inMemory = true)
        repo = GymRepository(db, clock = { time++ }, onOutboxChanged = { notified++ })
    }

    @After
    fun tearDown() = db.close()

    private suspend fun ops(): List<SyncOpEntity> = db.syncDao().pending(1000)

    private suspend fun session(): String {
        val t = db.templateDao().getTemplates().first()
        return repo.startSession(t.id, LocalDate.of(2026, 11, 20))
    }

    private suspend fun exercise(name: String) = db.exerciseDao().getAll().first { it.name == name }.id

    private suspend fun save(sessionId: String, exerciseId: String, entryId: String? = null, weight: Double = 40.0) =
        repo.saveEntry(entryId, sessionId, null, exerciseId, 3, 12, weight, Feel.NORMAL)

    @Test
    fun startingSessionAloneQueuesNothing() = runTest {
        session()
        assertTrue(ops().isEmpty())
        assertEquals(0, notified)
    }

    @Test
    fun saveQueuesUpsertAndEditsCollapseToLatest() = runTest {
        val s = session()
        val ex = exercise("Жим лёжа в тренажёре")
        val id = save(s, ex)
        val first = ops().single()
        assertEquals(id, first.entryId)
        assertEquals(SyncOpType.UPSERT, first.op)
        assertEquals(1, notified)

        save(s, ex, entryId = id, weight = 45.0)
        save(s, ex, entryId = id, weight = 47.5)
        val after = ops().single()
        assertEquals(SyncOpType.UPSERT, after.op)
        // Новая версия операции — новый id (отправитель не удалит её по старому id).
        assertTrue(after.id > first.id)
        assertEquals(3, notified)
    }

    @Test
    fun deleteEntryReplacesPendingUpsertWithDelete() = runTest {
        val s = session()
        val id = save(s, exercise("Жим лёжа в тренажёре"))
        repo.deleteEntry(id)
        val op = ops().single()
        assertEquals(id, op.entryId)
        assertEquals(SyncOpType.DELETE, op.op)
    }

    @Test
    fun deleteSessionQueuesDeleteForEveryEntry() = runTest {
        val s = session()
        val a = save(s, exercise("Жим лёжа в тренажёре"))
        val b = save(s, exercise("Жим гантелей лёжа"))
        db.syncDao().deleteByIds(ops().map { it.id }) // как будто уже отправлено
        repo.deleteSession(s)
        val q = ops()
        assertEquals(setOf(a, b), q.map { it.entryId }.toSet())
        assertTrue(q.all { it.op == SyncOpType.DELETE })
        assertEquals(0, db.sessionDao().countEntries(s))
    }

    @Test
    fun renamingExerciseQueuesUpsertForItsEntriesOnly() = runTest {
        val s = session()
        val press = exercise("Жим лёжа в тренажёре")
        val other = exercise("Жим гантелей лёжа")
        val a = save(s, press)
        val b = save(session(), press)
        save(s, other)
        db.syncDao().deleteByIds(ops().map { it.id })

        // Только подходы/повторы по умолчанию — названия в таблице не меняются.
        assertEquals(GymRepository.UpdateResult.OK, repo.updateExercise(press, "Жим лёжа в тренажёре", 4, 10))
        assertTrue(ops().isEmpty())

        assertEquals(GymRepository.UpdateResult.OK, repo.updateExercise(press, "Жим в тренажёре", 4, 10))
        val q = ops()
        assertEquals(setOf(a, b), q.map { it.entryId }.toSet())
        assertTrue(q.all { it.op == SyncOpType.UPSERT })
    }

    @Test
    fun changingSessionDateQueuesUpsertForItsEntries() = runTest {
        val s = session()
        val a = save(s, exercise("Жим лёжа в тренажёре"))
        db.syncDao().deleteByIds(ops().map { it.id })
        repo.updateSessionDate(s, LocalDate.of(2026, 11, 19))
        assertEquals(listOf(a), ops().map { it.entryId })
    }

    @Test
    fun fullHistoryExportIsIdempotent() = runTest {
        val s = session()
        save(s, exercise("Жим лёжа в тренажёре"))
        save(s, exercise("Жим гантелей лёжа"))
        save(session(), exercise("Жим лёжа в тренажёре"))
        assertEquals(3, repo.enqueueFullHistory())
        assertEquals(3, repo.enqueueFullHistory())
        val q = ops()
        assertEquals(3, q.size)
        assertTrue(q.all { it.op == SyncOpType.UPSERT })
        assertEquals(3, repo.outboxCount())
    }
}
