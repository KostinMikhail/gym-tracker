package com.gym.tracker.backup

import com.gym.tracker.data.db.Feel
import com.gym.tracker.data.db.HistoryRow
import com.gym.tracker.domain.backup.HistoryCsvExporter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HistoryCsvExporterTest {

    private fun row(
        entryId: String,
        date: String = "2026-11-20",
        sessionId: String = "s1",
        sessionCreatedAt: Long = 1,
        slotOrder: Int? = 0,
        entryCreatedAt: Long = 1,
        exercise: String = "Жим лёжа",
        slot: String? = "Грудь",
        workout: String? = "Фулбади А",
        weight: Double = 45.0,
        feel: Feel = Feel.NORMAL,
    ) = HistoryRow(
        date = LocalDate.parse(date), workout = workout, slot = slot, exercise = exercise,
        sets = 3, reps = 12, weightKg = weight, feel = feel, sessionId = sessionId, entryId = entryId,
        sessionCreatedAt = sessionCreatedAt, slotOrder = slotOrder, entryCreatedAt = entryCreatedAt,
    )

    private fun lines(csv: String) = csv.trimEnd('\n').split('\n')

    @Test
    fun emptyHistoryIsHeaderOnly() {
        assertEquals("date,workout,slot,exercise,sets,reps,weight_kg,feel,session_id,entry_id\n", HistoryCsvExporter.build(emptyList()))
    }

    @Test
    fun rowHasAllColumnsInOrder() {
        val csv = HistoryCsvExporter.build(listOf(row("e1", weight = 22.5, feel = Feel.HARD)))
        assertEquals("2026-11-20,Фулбади А,Грудь,Жим лёжа,3,12,22.5,Тяжело,s1,e1", lines(csv)[1])
    }

    @Test
    fun weightUsesDotAndNoTrailingZeros() {
        assertEquals("22.5", HistoryCsvExporter.weight(22.5))
        assertEquals("45", HistoryCsvExporter.weight(45.0))
        assertEquals("0", HistoryCsvExporter.weight(0.0))
        assertEquals("102.5", HistoryCsvExporter.weight(102.5))
    }

    @Test
    fun feelLabelsAreRussian() {
        val csv = HistoryCsvExporter.build(
            listOf(row("a", feel = Feel.EASY, slotOrder = 0), row("b", feel = Feel.NORMAL, slotOrder = 1), row("c", feel = Feel.HARD, slotOrder = 2)),
        )
        assertEquals(listOf("Легко", "Норм", "Тяжело"), lines(csv).drop(1).map { it.split(',')[7] })
    }

    @Test
    fun namesWithCommasQuotesAndNewlinesAreQuoted() {
        assertEquals("\"Жим, узкий\"", HistoryCsvExporter.field("Жим, узкий"))
        assertEquals("\"Тяга \"\"Т\"\"\"", HistoryCsvExporter.field("Тяга \"Т\""))
        assertEquals("\"две\nстроки\"", HistoryCsvExporter.field("две\nстроки"))
        assertEquals("Присед", HistoryCsvExporter.field("Присед"))
        val csv = HistoryCsvExporter.build(listOf(row("e1", exercise = "Жим, \"узкий\"", slot = null, workout = null)))
        assertEquals("2026-11-20,,,\"Жим, \"\"узкий\"\"\",3,12,45,Норм,s1,e1", lines(csv)[1])
    }

    @Test
    fun sortedByDateThenSessionThenSlotOrder() {
        val rows = listOf(
            row("late-day", date = "2026-11-21"),
            row("s2-slot0", sessionId = "s2", sessionCreatedAt = 5, slotOrder = 0),
            row("s1-noslot", sessionId = "s1", sessionCreatedAt = 2, slotOrder = null),
            row("s1-slot2", sessionId = "s1", sessionCreatedAt = 2, slotOrder = 2),
            row("s1-slot0", sessionId = "s1", sessionCreatedAt = 2, slotOrder = 0, entryCreatedAt = 9),
            row("early-day", date = "2026-11-01"),
        )
        val ids = lines(HistoryCsvExporter.build(rows)).drop(1).map { it.substringAfterLast(',') }
        assertEquals(listOf("early-day", "s1-slot0", "s1-slot2", "s1-noslot", "s2-slot0", "late-day"), ids)
    }

    @Test
    fun gitBlobShaMatchesGit() {
        // `printf 'hello\n' | git hash-object --stdin`
        assertEquals("ce013625030ba8dba906f756967f9e9ca394464a", HistoryCsvExporter.gitBlobSha("hello\n"))
    }
}
