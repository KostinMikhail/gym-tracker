package com.gym.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises")
    suspend fun getAll(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: String): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observeById(id: String): Flow<ExerciseEntity?>

    @Query("SELECT * FROM exercises WHERE id IN (SELECT DISTINCT exerciseId FROM session_entries)")
    fun observeWithEntries(): Flow<List<ExerciseEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(exercise: ExerciseEntity)

    @Query("UPDATE exercises SET name = :name, defaultSets = :sets, defaultReps = :reps, updatedAt = :now WHERE id = :id")
    suspend fun update(id: String, name: String, sets: Int, reps: Int, now: Long)
}

@Dao
interface TemplateDao {
    @Query(
        """
        SELECT t.*, (SELECT MAX(s.date) FROM workout_sessions s WHERE s.templateId = t.id) AS lastDate
        FROM workout_templates t ORDER BY t.sortOrder
        """,
    )
    fun observeTemplatesWithLastDate(): Flow<List<TemplateWithLastDate>>

    @Query("SELECT * FROM workout_templates ORDER BY sortOrder")
    suspend fun getTemplates(): List<WorkoutTemplateEntity>

    @Query("SELECT * FROM workout_templates WHERE id = :id")
    suspend fun getTemplate(id: String): WorkoutTemplateEntity?

    @Query("SELECT * FROM template_slots WHERE templateId = :templateId ORDER BY sortOrder")
    fun observeSlots(templateId: String): Flow<List<TemplateSlotEntity>>

    @Query("SELECT * FROM template_slots WHERE templateId = :templateId ORDER BY sortOrder")
    suspend fun getSlots(templateId: String): List<TemplateSlotEntity>

    @Query("SELECT * FROM template_slots WHERE id = :slotId")
    suspend fun getSlot(slotId: String): TemplateSlotEntity?

    @Query(
        """
        SELECT o.slotId AS slotId, o.sortOrder AS optionOrder, x.*
        FROM slot_options o JOIN exercises x ON x.id = o.exerciseId
        WHERE o.slotId IN (SELECT id FROM template_slots WHERE templateId = :templateId)
        ORDER BY o.sortOrder
        """,
    )
    fun observeOptionsForTemplate(templateId: String): Flow<List<SlotOptionExercise>>

    @Query(
        """
        SELECT o.slotId AS slotId, o.sortOrder AS optionOrder, x.*
        FROM slot_options o JOIN exercises x ON x.id = o.exerciseId
        WHERE o.slotId = :slotId ORDER BY o.sortOrder
        """,
    )
    suspend fun getOptionsForSlot(slotId: String): List<SlotOptionExercise>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM slot_options WHERE slotId = :slotId")
    suspend fun maxOptionOrder(slotId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOption(option: SlotOptionEntity): Long

    @Query("UPDATE template_slots SET favoriteExerciseId = :exerciseId, updatedAt = :now WHERE id = :slotId")
    suspend fun setFavorite(slotId: String, exerciseId: String, now: Long)
}

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: WorkoutSessionEntity)

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getSession(id: String): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    fun observeSession(id: String): Flow<WorkoutSessionEntity?>

    @Query("UPDATE workout_sessions SET date = :date, updatedAt = :now WHERE id = :id")
    suspend fun updateDate(id: String, date: LocalDate, now: Long)

    @Query("UPDATE workout_sessions SET finishedAt = :finishedAt, updatedAt = :finishedAt WHERE id = :id")
    suspend fun finish(id: String, finishedAt: Long)

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query(
        """
        SELECT s.id, s.templateId, s.date, s.finishedAt, t.name AS templateName,
            (SELECT COUNT(*) FROM session_entries e WHERE e.sessionId = s.id) AS entryCount
        FROM workout_sessions s LEFT JOIN workout_templates t ON t.id = s.templateId
        ORDER BY s.date DESC, s.createdAt DESC
        """,
    )
    fun observeSessionSummaries(): Flow<List<SessionSummary>>

    @Query(
        """
        SELECT s.id, s.templateId, s.date, s.finishedAt, t.name AS templateName,
            (SELECT COUNT(*) FROM session_entries e WHERE e.sessionId = s.id) AS entryCount
        FROM workout_sessions s LEFT JOIN workout_templates t ON t.id = s.templateId
        WHERE s.id = :id
        """,
    )
    fun observeSessionSummary(id: String): Flow<SessionSummary?>

    /** Самая свежая тренировка (для подсказки, какой шаблон делать). */
    @Query("SELECT * FROM workout_sessions ORDER BY date DESC, createdAt DESC LIMIT 1")
    fun observeLatestSession(): Flow<WorkoutSessionEntity?>

    @Upsert
    suspend fun upsertEntry(entry: SessionEntryEntity)

    @Query("SELECT * FROM session_entries WHERE id = :id")
    suspend fun getEntry(id: String): SessionEntryEntity?

    @Query("DELETE FROM session_entries WHERE id = :id")
    suspend fun deleteEntry(id: String)

    @Query("SELECT COUNT(*) FROM session_entries WHERE sessionId = :sessionId")
    suspend fun countEntries(sessionId: String): Int

    @Query("SELECT * FROM session_entries WHERE sessionId = :sessionId")
    fun observeEntriesForSession(sessionId: String): Flow<List<SessionEntryEntity>>

    @Query(
        """
        SELECT e.*, x.name AS exerciseName, sl.name AS slotName
        FROM session_entries e
        JOIN exercises x ON x.id = e.exerciseId
        LEFT JOIN template_slots sl ON sl.id = e.slotId
        WHERE e.sessionId = :sessionId
        ORDER BY COALESCE(sl.sortOrder, 1000000), e.createdAt
        """,
    )
    fun observeEntryDetails(sessionId: String): Flow<List<EntryDetails>>

    /**
     * Последняя запись упражнения в любой тренировке (по любому шаблону), кроме [excludeSessionId].
     * Сортировка: по дате тренировки, затем по времени создания записи.
     */
    @Query(
        """
        SELECT e.*, s.date AS date
        FROM session_entries e JOIN workout_sessions s ON s.id = e.sessionId
        WHERE e.exerciseId = :exerciseId AND e.sessionId != :excludeSessionId
        ORDER BY s.date DESC, s.createdAt DESC, e.createdAt DESC
        LIMIT 1
        """,
    )
    suspend fun lastEntryForExercise(exerciseId: String, excludeSessionId: String): EntryWithDate?

    @Query(
        """
        SELECT e.*, s.date AS date, t.name AS templateName
        FROM session_entries e
        JOIN workout_sessions s ON s.id = e.sessionId
        LEFT JOIN workout_templates t ON t.id = s.templateId
        WHERE e.exerciseId = :exerciseId
        ORDER BY s.date DESC, s.createdAt DESC, e.createdAt DESC
        """,
    )
    fun observeExerciseHistory(exerciseId: String): Flow<List<ExerciseHistoryRow>>
}
