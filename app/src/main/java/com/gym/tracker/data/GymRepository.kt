package com.gym.tracker.data

import androidx.room.withTransaction
import com.gym.tracker.data.db.EntryWithDate
import com.gym.tracker.data.db.ExerciseEntity
import com.gym.tracker.data.db.Feel
import com.gym.tracker.data.db.GymDatabase
import com.gym.tracker.data.db.SessionEntryEntity
import com.gym.tracker.data.db.SlotOptionEntity
import com.gym.tracker.data.db.SlotOptionExercise
import com.gym.tracker.data.db.SyncOpEntity
import com.gym.tracker.data.db.SyncOpType
import com.gym.tracker.data.db.WorkoutSessionEntity
import com.gym.tracker.domain.cleanName
import com.gym.tracker.domain.normalizeName
import com.gym.tracker.domain.roundWeight
import java.time.LocalDate
import java.util.UUID

/**
 * Единственная точка доступа UI к данным.
 * Изменения записей тренировок ставят операции в очередь выгрузки в Google Таблицу (`sync_ops`)
 * в той же транзакции; после фиксации вызывается [onOutboxChanged] (запуск отправки).
 */
class GymRepository(
    private val db: GymDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val onOutboxChanged: () -> Unit = {},
) {
    private val exerciseDao = db.exerciseDao()
    private val templateDao = db.templateDao()
    private val sessionDao = db.sessionDao()
    private val syncDao = db.syncDao()

    /** Ставит операции в очередь (внутри транзакции вызывающего). Прежние операции этих записей заменяются. */
    private suspend fun enqueue(entryIds: List<String>, op: SyncOpType) {
        if (entryIds.isEmpty()) return
        val now = clock()
        syncDao.insertAll(entryIds.map { SyncOpEntity(entryId = it, op = op, createdAt = now) })
    }

    // ---- Шаблоны ----
    fun observeTemplatesWithLastDate() = templateDao.observeTemplatesWithLastDate()
    fun observeLatestSession() = sessionDao.observeLatestSession()
    suspend fun getTemplate(id: String) = templateDao.getTemplate(id)
    fun observeSlots(templateId: String) = templateDao.observeSlots(templateId)
    fun observeOptions(templateId: String) = templateDao.observeOptionsForTemplate(templateId)
    suspend fun getOptionsForSlot(slotId: String): List<SlotOptionExercise> = templateDao.getOptionsForSlot(slotId)

    /** Делает упражнение избранным в слоте. Избранное в слоте всегда одно (одна колонка). */
    suspend fun setFavorite(slotId: String, exerciseId: String) =
        templateDao.setFavorite(slotId, exerciseId, clock())

    /**
     * Добавляет вариант упражнения в слот. Если упражнение с таким названием уже есть — переиспользует его
     * (его подходы/повторы не меняются). Возвращает id упражнения.
     */
    suspend fun addOptionToSlot(slotId: String, name: String, defaultSets: Int, defaultReps: Int): String =
        db.withTransaction {
            val now = clock()
            val exercise = findExerciseByName(name) ?: ExerciseEntity(
                id = newId(),
                name = cleanName(name),
                defaultSets = defaultSets,
                defaultReps = defaultReps,
                createdAt = now,
                updatedAt = now,
            ).also { exerciseDao.insert(it) }
            val order = templateDao.maxOptionOrder(slotId) + 1
            templateDao.insertOption(SlotOptionEntity(slotId, exercise.id, order, now, now))
            exercise.id
        }

    suspend fun findExerciseByName(name: String): ExerciseEntity? {
        val key = normalizeName(name)
        return exerciseDao.getAll().firstOrNull { normalizeName(it.name) == key }
    }

    // ---- Тренировки ----
    suspend fun startSession(templateId: String, date: LocalDate): String {
        val now = clock()
        val id = newId()
        sessionDao.insertSession(WorkoutSessionEntity(id, templateId, date, null, now, now))
        return id
    }

    suspend fun getSession(id: String) = sessionDao.getSession(id)
    fun observeSession(id: String) = sessionDao.observeSession(id)
    /** Меняет дату тренировки; строки её записей в таблице обновятся (в них есть дата). */
    suspend fun updateSessionDate(id: String, date: LocalDate) {
        val queued = db.withTransaction {
            sessionDao.updateDate(id, date, clock())
            syncDao.entryIdsForSession(id).also { enqueue(it, SyncOpType.UPSERT) }
        }
        if (queued.isNotEmpty()) onOutboxChanged()
    }

    enum class FinishResult { FINISHED, EMPTY }

    /** Завершает тренировку. Если в ней нет ни одного упражнения — ничего не меняет и возвращает EMPTY. */
    suspend fun finishSession(id: String): FinishResult {
        if (sessionDao.countEntries(id) == 0) return FinishResult.EMPTY
        sessionDao.finish(id, clock())
        return FinishResult.FINISHED
    }

    /** Помечает тренировку завершённой без проверок (пользователь решил оставить пустую). */
    suspend fun markFinished(id: String) = sessionDao.finish(id, clock())

    /** Удаляет пустую тренировку. Тренировку с записями не трогает. Возвращает true, если удалила. */
    suspend fun discardIfEmpty(id: String): Boolean = db.withTransaction {
        if (sessionDao.countEntries(id) == 0) {
            sessionDao.deleteSession(id); true
        } else {
            false
        }
    }

    /** Удаляет тренировку с записями; их строки будут удалены из таблицы. */
    suspend fun deleteSession(id: String) {
        val queued = db.withTransaction {
            val entryIds = syncDao.entryIdsForSession(id)
            sessionDao.deleteSession(id)
            enqueue(entryIds, SyncOpType.DELETE)
            entryIds
        }
        if (queued.isNotEmpty()) onOutboxChanged()
    }

    fun observeSessionSummaries() = sessionDao.observeSessionSummaries()
    fun observeSessionSummary(id: String) = sessionDao.observeSessionSummary(id)
    fun observeEntryDetails(sessionId: String) = sessionDao.observeEntryDetails(sessionId)
    fun observeEntriesForSession(sessionId: String) = sessionDao.observeEntriesForSession(sessionId)

    // ---- Записи ----
    /** Последняя запись упражнения в любой другой тренировке. */
    suspend fun lastEntry(exerciseId: String, excludeSessionId: String = ""): EntryWithDate? =
        sessionDao.lastEntryForExercise(exerciseId, excludeSessionId)

    /** Сохраняет запись (создаёт или обновляет ту же по [entryId]). Возвращает id записи. */
    suspend fun saveEntry(
        entryId: String?,
        sessionId: String,
        slotId: String?,
        exerciseId: String,
        sets: Int,
        reps: Int,
        weightKg: Double,
        feel: Feel,
    ): String {
        val id = db.withTransaction {
            val now = clock()
            val existing = entryId?.let { sessionDao.getEntry(it) }
            val entry = SessionEntryEntity(
                id = existing?.id ?: entryId ?: newId(),
                sessionId = sessionId,
                slotId = slotId,
                exerciseId = exerciseId,
                sets = sets,
                reps = reps,
                weightKg = roundWeight(weightKg),
                feel = feel,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
            sessionDao.upsertEntry(entry)
            enqueue(listOf(entry.id), SyncOpType.UPSERT)
            entry.id
        }
        onOutboxChanged()
        return id
    }

    suspend fun getEntry(id: String) = sessionDao.getEntry(id)

    suspend fun deleteEntry(id: String) {
        db.withTransaction {
            sessionDao.deleteEntry(id)
            enqueue(listOf(id), SyncOpType.DELETE)
        }
        onOutboxChanged()
    }

    // ---- Выгрузка в Google Таблицу ----
    fun observeOutboxCount() = syncDao.observeCount()
    suspend fun outboxCount() = syncDao.count()

    /** «Выгрузить всю историю»: UPSERT для каждой записи (повторное нажатие безопасно). Возвращает число записей. */
    suspend fun enqueueFullHistory(): Int {
        val ids = db.withTransaction { syncDao.allEntryIds().also { enqueue(it, SyncOpType.UPSERT) } }
        if (ids.isNotEmpty()) onOutboxChanged()
        return ids.size
    }

    // ---- Упражнения ----
    fun observeExercises() = exerciseDao.observeAll()
    fun observeExercise(id: String) = exerciseDao.observeById(id)
    fun observeExercisesWithEntries() = exerciseDao.observeWithEntries()
    fun observeExerciseHistory(exerciseId: String) = sessionDao.observeExerciseHistory(exerciseId)

    enum class UpdateResult { OK, NAME_TAKEN, INVALID }

    suspend fun updateExercise(id: String, name: String, sets: Int, reps: Int): UpdateResult {
        val clean = cleanName(name)
        if (clean.isEmpty() || sets < 1 || reps < 1) return UpdateResult.INVALID
        var queued = false
        return db.withTransaction {
            val other = findExerciseByName(clean)
            if (other != null && other.id != id) {
                UpdateResult.NAME_TAKEN
            } else {
                val renamed = exerciseDao.getById(id)?.name != clean
                exerciseDao.update(id, clean, sets, reps, clock())
                // Новое название — во всех строках таблицы с этим упражнением.
                if (renamed) {
                    val entryIds = syncDao.entryIdsForExercise(id)
                    enqueue(entryIds, SyncOpType.UPSERT)
                    queued = entryIds.isNotEmpty()
                }
                UpdateResult.OK
            }
        }.also { if (queued) onOutboxChanged() }
    }
}
