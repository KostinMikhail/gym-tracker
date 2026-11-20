package com.gym.tracker.data.db

import androidx.room.Embedded
import java.time.LocalDate

/** Шаблон + дата последней тренировки по нему. */
data class TemplateWithLastDate(
    @Embedded val template: WorkoutTemplateEntity,
    val lastDate: LocalDate?,
)

/** Вариант упражнения в слоте (в порядке sortOrder варианта). */
data class SlotOptionExercise(
    val slotId: String,
    val optionOrder: Int,
    @Embedded val exercise: ExerciseEntity,
)

/** Запись + дата тренировки (для «В прошлый раз»). */
data class EntryWithDate(
    @Embedded val entry: SessionEntryEntity,
    val date: LocalDate,
)

/** Строка списка тренировок. */
data class SessionSummary(
    val id: String,
    val templateId: String,
    val date: LocalDate,
    val finishedAt: Long?,
    val templateName: String?,
    val entryCount: Int,
)

/** Запись тренировки с названиями упражнения и слота. */
data class EntryDetails(
    @Embedded val entry: SessionEntryEntity,
    val exerciseName: String,
    val slotName: String?,
)

/** Запись в истории упражнения. */
data class ExerciseHistoryRow(
    @Embedded val entry: SessionEntryEntity,
    val date: LocalDate,
    val templateName: String?,
)
