package com.gym.tracker

import com.gym.tracker.data.db.SeedData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedDataTest {
    private val seed = SeedData.build(now = 1000L)

    @Test
    fun twoTemplatesInOrder() {
        assertEquals(listOf("Фулбади А", "Фулбади Б"), seed.templates.sortedBy { it.sortOrder }.map { it.name })
    }

    @Test
    fun slotCounts() {
        val a = seed.templates.first { it.name == "Фулбади А" }
        val b = seed.templates.first { it.name == "Фулбади Б" }
        assertEquals(6, seed.slots.count { it.templateId == a.id })
        assertEquals(7, seed.slots.count { it.templateId == b.id })
    }

    @Test
    fun everySlotHasOptionsAndValidFavorite() {
        seed.slots.forEach { slot ->
            val options = seed.options.filter { it.slotId == slot.id }
            assertTrue("Слот ${slot.name} без вариантов", options.isNotEmpty())
            assertNotNull("Слот ${slot.name} без избранного", slot.favoriteExerciseId)
            assertTrue(
                "Избранное слота ${slot.name} не среди вариантов",
                options.any { it.exerciseId == slot.favoriteExerciseId },
            )
            // Избранное — первый вариант.
            assertEquals(slot.favoriteExerciseId, options.minBy { it.sortOrder }.exerciseId)
        }
    }

    @Test
    fun allReferencesValid() {
        val exerciseIds = seed.exercises.map { it.id }.toSet()
        val slotIds = seed.slots.map { it.id }.toSet()
        seed.options.forEach {
            assertTrue(it.exerciseId in exerciseIds)
            assertTrue(it.slotId in slotIds)
        }
    }

    @Test
    fun sameNameIsSameExercise() {
        assertEquals(seed.exercises.size, seed.exercises.map { it.name }.toSet().size)
        val lever = seed.exercises.single { it.name == "Скручивание рычажное" }
        val slotsWithLever = seed.options.filter { it.exerciseId == lever.id }.map { it.slotId }
        val templatesWithLever = seed.slots.filter { it.id in slotsWithLever }.map { it.templateId }.toSet()
        assertEquals(2, templatesWithLever.size)
    }

    @Test
    fun defaultsSetsReps() {
        fun ex(name: String) = seed.exercises.single { it.name == name }
        assertEquals(3 to 40, ex("Скручивания на полу").let { it.defaultSets to it.defaultReps })
        assertEquals(3 to 10, ex("Pallof press").let { it.defaultSets to it.defaultReps })
        assertEquals(3 to 12, ex("Жим ногами в тренажёре").let { it.defaultSets to it.defaultReps })
        assertEquals(3 to 12, ex("Скручивание рычажное").let { it.defaultSets to it.defaultReps })
    }

    @Test
    fun timestampsSet() {
        assertTrue(seed.exercises.all { it.createdAt == 1000L && it.updatedAt == 1000L })
        assertTrue(seed.slots.all { it.createdAt == 1000L })
    }
}
