package com.gym.tracker.domain

import com.gym.tracker.data.db.Feel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object Format {
    private val shortDate = DateTimeFormatter.ofPattern("dd.MM")
    private val fullDate = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    /** dd.MM для текущего года, иначе dd.MM.yyyy. */
    fun date(date: LocalDate, today: LocalDate = LocalDate.now()): String =
        if (date.year == today.year) date.format(shortDate) else date.format(fullDate)

    /** Число кг: «45», «42,5». */
    fun number(kg: Double): String {
        val r = roundWeight(kg)
        return if (r % 1.0 == 0.0) r.toLong().toString() else r.toString().replace('.', ',')
    }

    /** «45 кг» или «без веса» для 0. */
    fun weight(kg: Double): String = if (roundWeight(kg) == 0.0) "без веса" else "${number(kg)} кг"

    fun setsReps(sets: Int, reps: Int): String = "$sets×$reps"

    /** «3×12 · 45 кг · Норм». */
    fun entry(sets: Int, reps: Int, weightKg: Double, feel: Feel): String =
        "${setsReps(sets, reps)} · ${weight(weightKg)} · ${feel.label}"

    fun exercisesCount(n: Int): String {
        val mod10 = n % 10
        val mod100 = n % 100
        val word = when {
            mod10 == 1 && mod100 != 11 -> "упражнение"
            mod10 in 2..4 && mod100 !in 12..14 -> "упражнения"
            else -> "упражнений"
        }
        return "$n $word"
    }

    /** Разбор ввода веса: «42,5» / «42.5» / пусто → 0. null — если ввод некорректен. */
    fun parseWeight(text: String): Double? {
        val t = text.trim().replace(',', '.')
        if (t.isEmpty()) return 0.0
        val v = t.toDoubleOrNull() ?: return null
        if (v < 0 || v.isNaN() || v.isInfinite()) return null
        return roundWeight(v)
    }
}
