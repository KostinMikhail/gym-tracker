package com.gym.tracker.domain

import com.gym.tracker.data.db.ExerciseEntity
import com.gym.tracker.data.db.SessionEntryEntity
import com.gym.tracker.data.db.TemplateWithLastDate
import com.gym.tracker.data.db.WorkoutSessionEntity
import kotlin.math.roundToLong

/** Значения, которыми заполняется карточка упражнения. */
data class Prefill(val sets: Int, val reps: Int, val weightKg: Double)

/** Подходы и повторы — из настроек упражнения, вес — из последней записи (0, если записей нет). */
fun prefillFor(exercise: ExerciseEntity, lastEntry: SessionEntryEntity?): Prefill =
    Prefill(
        sets = exercise.defaultSets,
        reps = exercise.defaultReps,
        weightKg = lastEntry?.weightKg ?: 0.0,
    )

/**
 * Совпадают ли введённые подходы/повторы с настройками упражнения по умолчанию.
 * Нестандартные значения (и некорректный ввод) подсвечиваются в карточке.
 */
fun isDefaultSetsReps(sets: String, reps: String, defaultSets: Int, defaultReps: Int): Boolean =
    sets.trim().toIntOrNull() == defaultSets && reps.trim().toIntOrNull() == defaultReps

/** Округление веса до 0,5 кг, не меньше 0. */
fun roundWeight(kg: Double): Double = ((kg * 2).roundToLong() / 2.0).coerceAtLeast(0.0)

/** Нормализация названия для поиска дубликатов («Жим  лёжа» == «жим лёжа»). */
fun normalizeName(name: String): String = name.trim().replace(Regex("\\s+"), " ").lowercase()

/** Аккуратное название: без лишних пробелов. */
fun cleanName(name: String): String = name.trim().replace(Regex("\\s+"), " ")

/**
 * Какой шаблон предложить: не тот, что делали в самой свежей тренировке
 * (шаблоны чередуются). Среди остальных — тот, что делали давнее всего (никогда — в приоритете).
 */
fun suggestTemplateId(templates: List<TemplateWithLastDate>, latestSession: WorkoutSessionEntity?): String? {
    if (templates.isEmpty()) return null
    val candidates = templates.filter { it.template.id != latestSession?.templateId }.ifEmpty { templates }
    return candidates
        .sortedWith(compareBy<TemplateWithLastDate, java.time.LocalDate?>(nullsFirst()) { it.lastDate }.thenBy { it.template.sortOrder })
        .first().template.id
}

/** Сколько слотов тренировки уже отмечены «Готово» (есть запись с этим слотом). */
fun confirmedSlotCount(slotIds: Collection<String>, entries: Collection<SessionEntryEntity>): Int {
    val done = entries.mapNotNullTo(HashSet()) { it.slotId }
    return slotIds.count { it in done }
}
