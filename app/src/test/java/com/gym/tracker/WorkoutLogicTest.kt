package com.gym.tracker

import com.gym.tracker.data.db.ExerciseEntity
import com.gym.tracker.data.db.Feel
import com.gym.tracker.data.db.SessionEntryEntity
import com.gym.tracker.data.db.TemplateWithLastDate
import com.gym.tracker.data.db.WorkoutSessionEntity
import com.gym.tracker.data.db.WorkoutTemplateEntity
import com.gym.tracker.domain.Format
import com.gym.tracker.domain.Prefill
import com.gym.tracker.domain.normalizeName
import com.gym.tracker.domain.prefillFor
import com.gym.tracker.domain.roundWeight
import com.gym.tracker.domain.suggestTemplateId
import com.gym.tracker.ui.components.stepInt
import com.gym.tracker.ui.components.stepWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WorkoutLogicTest {
    private val exercise = ExerciseEntity("ex", "Жим", 4, 10, 0, 0)

    private fun entry(weight: Double) = SessionEntryEntity("e", "s", "slot", "ex", 3, 8, weight, Feel.HARD, 0, 0)

    @Test
    fun prefillWithoutHistoryUsesDefaultsAndZeroWeight() {
        assertEquals(Prefill(4, 10, 0.0), prefillFor(exercise, null))
    }

    @Test
    fun prefillTakesWeightFromLastEntryButSetsRepsFromExercise() {
        assertEquals(Prefill(4, 10, 42.5), prefillFor(exercise, entry(42.5)))
    }

    @Test
    fun weightRounding() {
        assertEquals(42.5, roundWeight(42.4), 0.0)
        assertEquals(42.0, roundWeight(42.2), 0.0)
        assertEquals(0.0, roundWeight(-3.0), 0.0)
    }

    @Test
    fun weightFormatting() {
        assertEquals("без веса", Format.weight(0.0))
        assertEquals("45 кг", Format.weight(45.0))
        assertEquals("42,5 кг", Format.weight(42.5))
        assertEquals("3×12 · 45 кг · Норм", Format.entry(3, 12, 45.0, Feel.NORMAL))
        assertEquals("3×40 · без веса · Легко", Format.entry(3, 40, 0.0, Feel.EASY))
    }

    @Test
    fun weightParsing() {
        assertEquals(0.0, Format.parseWeight("")!!, 0.0)
        assertEquals(42.5, Format.parseWeight("42,5")!!, 0.0)
        assertEquals(42.5, Format.parseWeight("42.5")!!, 0.0)
        assertNull(Format.parseWeight("4,2,5"))
    }

    @Test
    fun dateFormatting() {
        val today = LocalDate.of(2026, 11, 9)
        assertEquals("05.11", Format.date(LocalDate.of(2026, 11, 5), today))
        assertEquals("28.12.2025", Format.date(LocalDate.of(2025, 12, 28), today))
    }

    @Test
    fun steppers() {
        assertEquals("4", stepInt("3", 1))
        assertEquals("1", stepInt("1", -1))
        assertEquals("47,5", stepWeight("45", 2.5))
        assertEquals("", stepWeight("2,5", -2.5))
        assertEquals("", stepWeight("", -2.5))
        assertEquals("2,5", stepWeight("", 2.5))
    }

    @Test
    fun russianPlurals() {
        assertEquals("1 упражнение", Format.exercisesCount(1))
        assertEquals("3 упражнения", Format.exercisesCount(3))
        assertEquals("5 упражнений", Format.exercisesCount(5))
        assertEquals("11 упражнений", Format.exercisesCount(11))
        assertEquals("21 упражнение", Format.exercisesCount(21))
    }

    @Test
    fun nameNormalization() {
        assertEquals(normalizeName("Жим  лёжа "), normalizeName("жим лёжа"))
    }

    private fun t(id: String, order: Int, last: LocalDate?) =
        TemplateWithLastDate(WorkoutTemplateEntity(id, id, order, 0, 0), last)

    private fun session(templateId: String, date: LocalDate) =
        WorkoutSessionEntity("s", templateId, date, null, 0, 0)

    @Test
    fun suggestFirstTemplateWhenNothingDone() {
        assertEquals("A", suggestTemplateId(listOf(t("A", 0, null), t("B", 1, null)), null))
    }

    @Test
    fun suggestAlternates() {
        val d1 = LocalDate.of(2026, 11, 3)
        val d2 = LocalDate.of(2026, 11, 5)
        assertEquals("B", suggestTemplateId(listOf(t("A", 0, d1), t("B", 1, null)), session("A", d1)))
        assertEquals("A", suggestTemplateId(listOf(t("A", 0, d1), t("B", 1, d2)), session("B", d2)))
        assertEquals("B", suggestTemplateId(listOf(t("A", 0, d2), t("B", 1, d1)), session("A", d2)))
    }

    @Test
    fun suggestNullForNoTemplates() {
        assertNull(suggestTemplateId(emptyList(), null))
    }
}
