package com.gym.tracker.data

import androidx.room.withTransaction
import com.gym.tracker.data.db.EntryWithDate
import com.gym.tracker.data.db.ExerciseEntity
import com.gym.tracker.data.db.Feel
import com.gym.tracker.data.db.GymDatabase
import com.gym.tracker.data.db.SessionEntryEntity
import com.gym.tracker.data.db.SlotOptionEntity
import com.gym.tracker.data.db.SlotOptionExercise
import com.gym.tracker.data.db.WorkoutSessionEntity
import com.gym.tracker.domain.cleanName
import com.gym.tracker.domain.normalizeName
import com.gym.tracker.domain.roundWeight
import java.time.LocalDate
import java.util.UUID

/** Единственная точка доступа UI к данным. */
class GymRepository(
    private val db: GymDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val exerciseDao = db.exerciseDao()
    private val templateDao = db.templateDao()
    private val sessionDao = db.sessionDao()

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
    suspend fun updateSessionDate(id: String, date: LocalDate) = sessionDao.updateDate(id, date, clock())

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

    suspend fun deleteSession(id: String) = sessionDao.deleteSession(id)
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
        return entry.id
    }

    suspend fun getEntry(id: String) = sessionDao.getEntry(id)
    suspend fun deleteEntry(id: String) = sessionDao.deleteEntry(id)

    // ---- Упражнения ----
    fun observeExercises() = exerciseDao.observeAll()
    fun observeExercise(id: String) = exerciseDao.observeById(id)
    fun observeExercisesWithEntries() = exerciseDao.observeWithEntries()
    fun observeExerciseHistory(exerciseId: String) = sessionDao.observeExerciseHistory(exerciseId)

    enum class UpdateResult { OK, NAME_TAKEN, INVALID }

    suspend fun updateExercise(id: String, name: String, sets: Int, reps: Int): UpdateResult {
        val clean = cleanName(name)
        if (clean.isEmpty() || sets < 1 || reps < 1) return UpdateResult.INVALID
        return db.withTransaction {
            val other = findExerciseByName(clean)
            if (other != null && other.id != id) {
                UpdateResult.NAME_TAKEN
            } else {
                exerciseDao.update(id, clean, sets, reps, clock())
                UpdateResult.OK
            }
        }
    }
}
