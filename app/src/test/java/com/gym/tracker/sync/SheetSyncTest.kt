package com.gym.tracker.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gym.tracker.data.GymRepository
import com.gym.tracker.data.SyncPreferences
import com.gym.tracker.data.db.Feel
import com.gym.tracker.data.db.GymDatabase
import com.gym.tracker.data.db.SyncOpEntity
import com.gym.tracker.data.db.SyncOpType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/** Отправка очереди: сборка строк из БД, удаление пропавших записей, частичный ответ, ошибки. */
@RunWith(AndroidJUnit4::class)
class SheetSyncTest {
    private class InMemoryStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val next = transform(state.value)
            state.value = (next as? MutablePreferences)?.toPreferences() ?: next
            return state.value
        }
    }

    private lateinit var db: GymDatabase
    private lateinit var repo: GymRepository
    private lateinit var prefs: SyncPreferences
    private var time = 1_763_640_000_000L // 2025-11-20T12:00:00Z
    private val requests = mutableListOf<Pair<String, SheetRequest>>()

    /** Ответ «сервера»: по умолчанию подтверждает все операции. */
    private var server: (SheetRequest) -> SheetResponse = { req ->
        SheetResponse(ok = true, applied = req.ops.mapNotNull { it.row?.entryId ?: it.entryId }, sheet = "Журнал")
    }

    private fun sync() = SheetSync(db.syncDao(), prefs, { url, req -> requests += url to req; server(req) }, clock = { time })

    @Before
    fun setUp() = runTest {
        db = GymDatabase.build(ApplicationProvider.getApplicationContext(), inMemory = true)
        repo = GymRepository(db, clock = { time++ })
        prefs = SyncPreferences(InMemoryStore())
        prefs.setConfig("https://script.google.com/macros/s/X/exec", "secret")
    }

    @After
    fun tearDown() = db.close()

    private suspend fun ops(): List<SyncOpEntity> = db.syncDao().pending(1000)

    private suspend fun entry(weight: Double = 45.5, feel: Feel = Feel.NORMAL): String {
        val t = db.templateDao().getTemplates().first { it.name == "Фулбади А" }
        val slot = db.templateDao().getSlots(t.id).first { it.name == "Грудь" }
        val s = repo.startSession(t.id, LocalDate.of(2026, 11, 20))
        return repo.saveEntry(null, s, slot.id, slot.favoriteExerciseId!!, 3, 12, weight, feel)
    }

    @Test
    fun rowPayloadHasCurrentNamesDateAndFeelLabel() = runTest {
        val id = entry(feel = Feel.HARD)
        val src = db.syncDao().rowSources(listOf(id)).single()
        val row = SheetProtocol.row(src, ZoneId.of("Europe/Moscow"))
        assertEquals(id, row.entryId)
        assertEquals("2026-11-20", row.date)
        assertEquals("Фулбади А", row.workout)
        assertEquals("Грудь", row.group)
        assertEquals("Жим лёжа в тренажёре", row.exercise)
        assertEquals(3, row.sets)
        assertEquals(12, row.reps)
        assertEquals(45.5, row.weightKg, 0.0)
        assertEquals("Тяжело", row.feel)
        assertTrue(row.updated, Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\+03:00""").matches(row.updated))
        // JSON-ключи — как в Code.gs.
        val json = SheetProtocol.encode(SheetRequest("t", listOf(SheetOp.upsert(row), SheetOp.delete("x"), SheetOp.PING)))
        listOf("\"entry_id\"", "\"session_id\"", "\"date\"", "\"workout\"", "\"group\"", "\"exercise\"", "\"sets\"", "\"reps\"",
            "\"weight_kg\"", "\"feel\"", "\"updated\"", "{\"op\":\"ping\"}", "{\"op\":\"delete\",\"entry_id\":\"x\"}")
            .forEach { assertTrue("$it в $json", json.contains(it)) }
        assertEquals("Норм", Feel.NORMAL.label)
        assertEquals("Легко", Feel.EASY.label)
    }

    @Test
    fun sendsQueueAndClearsIt() = runTest {
        val a = entry()
        val b = entry()
        assertEquals(SheetSync.Outcome.DONE, sync().syncPending())
        val req = requests.single().second
        assertEquals("secret", req.token)
        assertEquals(setOf(a, b), req.ops.map { it.row!!.entryId }.toSet())
        assertTrue(ops().isEmpty())
        val status = prefs.status.first()
        assertEquals(time, status.lastSyncAt)
        assertNull(status.errorCode)
    }

    @Test
    fun upsertForVanishedEntryIsSentAsDelete() = runTest {
        val id = entry()
        db.sessionDao().deleteEntry(id) // запись исчезла в обход очереди — в очереди всё ещё UPSERT
        assertEquals(SyncOpType.UPSERT, ops().single().op)
        sync().syncPending()
        val op = requests.single().second.ops.single()
        assertEquals("delete", op.op)
        assertEquals(id, op.entryId)
        assertTrue(ops().isEmpty())
    }

    @Test
    fun partialAppliedKeepsTheRestQueued() = runTest {
        val a = entry()
        val b = entry()
        server = { SheetResponse(ok = true, applied = listOf(a)) }
        assertEquals(SheetSync.Outcome.RETRY, sync().syncPending())
        val left = ops().single()
        assertEquals(b, left.entryId)
        assertEquals(1, left.attempts)
        assertEquals(SheetSync.NOT_APPLIED, left.lastError)
    }

    @Test
    fun editDuringSendIsNotLost() = runTest {
        val id = entry(weight = 40.0)
        server = { req ->
            // Пока запрос «в пути», запись правят — в очереди появляется новая версия операции.
            kotlinx.coroutines.runBlocking { repo.saveEntry(id, db.sessionDao().getEntry(id)!!.sessionId, null, db.sessionDao().getEntry(id)!!.exerciseId, 3, 12, 50.0, Feel.EASY) }
            server = { r -> SheetResponse(ok = true, applied = r.ops.map { it.row!!.entryId }) }
            SheetResponse(ok = true, applied = req.ops.map { it.row!!.entryId })
        }
        assertEquals(SheetSync.Outcome.DONE, sync().syncPending())
        // Вторая отправка — с новыми данными; очередь пуста.
        assertEquals(2, requests.size)
        assertEquals(50.0, requests[1].second.ops.single().row!!.weightKg, 0.0)
        assertTrue(ops().isEmpty())
    }

    @Test
    fun sendsInBatchesOf200() = runTest {
        val ids = (1..250).map { "missing-$it" }
        db.syncDao().insertAll(ids.map { SyncOpEntity(entryId = it, op = SyncOpType.DELETE, createdAt = 1) })
        assertEquals(SheetSync.Outcome.DONE, sync().syncPending())
        assertEquals(listOf(200, 50), requests.map { it.second.ops.size })
        assertTrue(ops().isEmpty())
    }

    @Test
    fun wrongTokenStopsAndKeepsQueue() = runTest {
        entry()
        server = { throw SheetError(SheetError.Kind.UNAUTHORIZED) }
        assertEquals(SheetSync.Outcome.STOP, sync().syncPending())
        val op = ops().single()
        assertEquals(1, op.attempts)
        assertEquals("UNAUTHORIZED", op.lastError)
        assertEquals("UNAUTHORIZED", prefs.status.first().errorCode)
    }

    @Test
    fun networkErrorRetries() = runTest {
        entry()
        server = { throw SheetError(SheetError.Kind.NETWORK) }
        assertEquals(SheetSync.Outcome.RETRY, sync().syncPending())
        assertEquals(1, ops().size)
    }

    @Test
    fun notConfiguredSendsNothing() = runTest {
        entry()
        prefs.setConfig("", "")
        assertEquals(SheetSync.Outcome.NOT_CONFIGURED, sync().syncPending())
        assertTrue(requests.isEmpty())
        assertEquals(1, ops().size)
    }

    @Test
    fun pingReportsErrors() = runTest {
        assertNull(sync().ping("https://x/exec", "secret"))
        assertEquals("ping", requests.single().second.ops.single().op)
        server = { throw SheetError(SheetError.Kind.UNAUTHORIZED) }
        assertEquals(SheetError.Kind.UNAUTHORIZED, sync().ping("https://x/exec", "bad")?.kind)
    }
}
