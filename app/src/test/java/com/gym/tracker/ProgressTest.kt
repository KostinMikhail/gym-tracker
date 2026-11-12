package com.gym.tracker

import com.gym.tracker.data.db.Feel
import com.gym.tracker.data.db.SessionEntryEntity
import com.gym.tracker.domain.Format
import com.gym.tracker.domain.confirmedSlotCount
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ProgressTest {
    private fun entry(id: String, slot: String?) = SessionEntryEntity(id, "s", slot, "ex", 3, 12, 0.0, Feel.NORMAL, 0, 0)

    private val slots = listOf("a", "b", "c", "d", "e", "f", "g")

    @Test
    fun nothingDone() = assertEquals(0, confirmedSlotCount(slots, emptyList()))

    @Test
    fun countsSlotsWithEntries() {
        assertEquals(3, confirmedSlotCount(slots, listOf(entry("1", "a"), entry("2", "c"), entry("3", "g"))))
    }

    @Test
    fun ignoresDuplicatesForeignSlotsAndNullSlot() {
        val entries = listOf(entry("1", "a"), entry("2", "a"), entry("3", "zzz"), entry("4", null))
        assertEquals(1, confirmedSlotCount(slots, entries))
    }

    @Test
    fun allDone() = assertEquals(7, confirmedSlotCount(slots, slots.mapIndexed { i, s -> entry("$i", s) }))

    @Test
    fun homeHeaderDate() {
        assertEquals("ср, 11.11", Format.weekdayDate(LocalDate.of(2026, 11, 11)))
        assertEquals("пн, 05.01", Format.weekdayDate(LocalDate.of(2026, 1, 5)))
    }
}
