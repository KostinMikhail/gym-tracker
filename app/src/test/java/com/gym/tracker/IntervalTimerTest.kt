package com.gym.tracker

import com.gym.tracker.domain.timer.IntervalTimer
import com.gym.tracker.domain.timer.TimerConfig
import com.gym.tracker.domain.timer.TimerPhase
import com.gym.tracker.domain.timer.TimerSignal
import com.gym.tracker.domain.timer.TimerSignal.BELL
import com.gym.tracker.domain.timer.TimerSignal.DOUBLE_BELL
import com.gym.tracker.domain.timer.TimerSignal.KNOCKS
import com.gym.tracker.domain.timer.TimerSignal.TRIPLE_BELL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntervalTimerTest {

    private var now = 1_000_000L
    private fun timer(rounds: Int, roundSec: Int, restSec: Int) =
        IntervalTimer(TimerConfig(rounds, roundSec, restSec)) { now }.also { it.start() }

    /** Двигает часы шагом [step] до [until] (абсолютное время) и собирает сигналы с моментами. */
    private fun IntervalTimer.runUntil(until: Long, step: Long = 50): List<Pair<Long, TimerSignal>> {
        val out = mutableListOf<Pair<Long, TimerSignal>>()
        while (now < until) {
            now = minOf(now + step, until)
            update().forEach { out += now to it }
        }
        return out
    }

    @Test
    fun totalDuration() {
        // 5 с + 12 × 3:00 + 11 × 1:00
        assertEquals((5 + 12 * 180 + 11 * 60) * 1000L, TimerConfig.DEFAULT.totalMs)
        assertEquals((5 + 60) * 1000L, TimerConfig(1, 60, 30).totalMs)
        assertEquals((5 + 3 * 60) * 1000L, TimerConfig(3, 60, 0).totalMs)
    }

    @Test
    fun fullSequence() {
        val start = now
        val t = timer(rounds = 2, roundSec = 30, restSec = 15)
        assertEquals(TimerPhase.PREP, t.phase)
        assertEquals(5, t.snapshot().remainingSec)
        val events = t.runUntil(start + 100_000, step = 10)
        val expected = listOf(
            start + 5_000 to BELL, // раунд 1
            start + 25_000 to KNOCKS, // 10 с до конца раунда 1
            start + 35_000 to DOUBLE_BELL, // отдых
            start + 50_000 to BELL, // раунд 2
            start + 70_000 to KNOCKS,
            start + 80_000 to TRIPLE_BELL, // готово
        )
        assertEquals(expected, events)
        assertEquals(TimerPhase.DONE, t.phase)
    }

    @Test
    fun phasesAndRoundCounter() {
        val start = now
        val t = timer(rounds = 3, roundSec = 20, restSec = 10)
        now = start + 4_000
        t.update()
        assertEquals(TimerPhase.PREP, t.phase)
        now = start + 5_000
        t.update()
        assertEquals(TimerPhase.ROUND, t.phase)
        assertEquals(1, t.round)
        assertEquals(20, t.snapshot().remainingSec)
        now = start + 25_500
        t.update()
        assertEquals(TimerPhase.REST, t.phase)
        assertEquals(1, t.round) // во время отдыха — завершённый раунд, на экране «далее 2 / 3»
        assertEquals(10, t.snapshot().remainingSec)
        now = start + 35_000
        t.update()
        assertEquals(TimerPhase.ROUND, t.phase)
        assertEquals(2, t.round)
    }

    @Test
    fun warningAtTenSeconds() {
        val start = now
        val t = timer(rounds = 1, roundSec = 60, restSec = 0)
        now = start + 5_000 + 49_999
        t.update()
        assertFalse(t.snapshot().warning)
        now = start + 5_000 + 50_000
        assertEquals(listOf(KNOCKS), t.update())
        assertTrue(t.snapshot().warning)
        assertEquals(10, t.snapshot().remainingSec)
        // Стук звучит один раз.
        now += 1_000
        assertEquals(emptyList<TimerSignal>(), t.update())
    }

    @Test
    fun restZeroStartsNextRoundWithSingleBell() {
        val start = now
        val t = timer(rounds = 3, roundSec = 15, restSec = 0)
        val events = t.runUntil(start + 60_000)
        assertEquals(
            listOf(BELL, KNOCKS, BELL, KNOCKS, BELL, KNOCKS, TRIPLE_BELL),
            events.map { it.second },
        )
        assertFalse(events.any { it.second == DOUBLE_BELL })
        assertEquals(start + 5_000 + 15_000, events[2].first)
    }

    @Test
    fun singleRound() {
        val start = now
        val t = timer(rounds = 1, roundSec = 30, restSec = 60)
        val events = t.runUntil(start + 120_000)
        assertEquals(listOf(BELL, KNOCKS, TRIPLE_BELL), events.map { it.second })
        assertEquals(start + 35_000, events.last().first)
        assertEquals(TimerPhase.DONE, t.phase)
    }

    @Test
    fun shortRoundHasNoWarning() {
        val start = now
        // Раунд 10 с (и короче) — без «10 секунд». (Минимум в настройках 15 с, но логика это допускает.)
        val t = IntervalTimer(TimerConfig(2, 10, 15)) { now }.also { it.start() }
        val events = t.runUntil(start + 60_000)
        assertEquals(listOf(BELL, DOUBLE_BELL, BELL, TRIPLE_BELL), events.map { it.second })
        val t2 = IntervalTimer(TimerConfig(1, 8, 0)) { now }.also { it.start() }
        now += 4_000
        t2.update()
        now += 3_000
        t2.update()
        assertFalse(t2.snapshot().warning)
    }

    @Test
    fun pauseResumePreservesRemaining() {
        val start = now
        val t = timer(rounds = 2, roundSec = 60, restSec = 30)
        now = start + 5_000 + 12_345
        t.update()
        t.pause()
        val frozen = t.snapshot().remainingMs
        assertEquals(60_000 - 12_345L, frozen)
        // Пауза 10 минут — ничего не меняется и звуков нет.
        now += 600_000
        assertEquals(emptyList<TimerSignal>(), t.update())
        assertEquals(frozen, t.snapshot().remainingMs)
        assertTrue(t.snapshot().paused)
        t.resume()
        assertEquals(frozen, t.snapshot().remainingMs)
        assertEquals(TimerPhase.ROUND, t.phase) // подготовка не повторяется
        now += frozen
        assertEquals(listOf(DOUBLE_BELL), t.update())
    }

    @Test
    fun pauseDuringPrepResumesPrep() {
        val start = now
        val t = timer(rounds = 1, roundSec = 30, restSec = 0)
        now = start + 2_000
        t.pause()
        now += 5_000
        t.resume()
        assertEquals(TimerPhase.PREP, t.phase)
        assertEquals(3, t.snapshot().remainingSec)
        now += 3_000
        assertEquals(listOf(BELL), t.update())
    }

    @Test
    fun restartPhaseRestoresFullDurationAndStaysPaused() {
        val start = now
        val t = timer(rounds = 3, roundSec = 60, restSec = 30)
        now = start + 5_000 + 55_000 // 5 с до конца раунда 1, стук уже был
        assertEquals(listOf(BELL, KNOCKS), t.update())
        t.pause()
        t.restartPhase()
        assertTrue(t.paused)
        assertEquals(TimerPhase.ROUND, t.phase)
        assertEquals(1, t.round)
        assertEquals(60_000L, t.snapshot().remainingMs)
        assertFalse(t.snapshot().warning)
        now += 30_000
        assertEquals(60_000L, t.snapshot().remainingMs) // всё ещё на паузе
        t.resume()
        // Стук «10 секунд» звучит снова в перезапущенном раунде.
        now += 50_000
        assertEquals(listOf(KNOCKS), t.update())
        // Отдых заново.
        now += 10_000
        assertEquals(listOf(DOUBLE_BELL), t.update())
        now += 12_000
        t.pause()
        t.restartPhase()
        assertEquals(TimerPhase.REST, t.phase)
        assertEquals(30_000L, t.snapshot().remainingMs)
    }

    @Test
    fun restartFromRunningPauses() {
        val t = timer(rounds = 1, roundSec = 30, restSec = 0)
        now += 2_000
        t.restartPhase()
        assertTrue(t.paused)
        assertEquals(5_000L, t.snapshot().remainingMs)
    }

    @Test
    fun resetByStartingAgain() {
        val start = now
        val t = timer(rounds = 2, roundSec = 30, restSec = 15)
        t.runUntil(start + 200_000)
        assertEquals(TimerPhase.DONE, t.phase)
        // «Ещё раз» / «Сбросить всё» — новый запуск с подготовки.
        t.start()
        assertEquals(TimerPhase.PREP, t.phase)
        assertEquals(1, t.round)
        assertFalse(t.paused)
        assertEquals(5_000L, t.snapshot().remainingMs)
    }

    @Test
    fun catchUpAfterLongDelayKeepsSchedule() {
        val start = now
        val t = timer(rounds = 3, roundSec = 60, restSec = 30)
        // Обработчик «проспал» 100 с: переходы выполнены от дедлайна к дедлайну.
        now = start + 100_000
        val s = t.update()
        assertEquals(listOf(BELL, DOUBLE_BELL, BELL), s.filter { it != KNOCKS })
        assertEquals(TimerPhase.ROUND, t.phase)
        assertEquals(2, t.round)
        // Раунд 2 начался в 5 + 60 + 30 = 95 с.
        assertEquals(55_000L, t.snapshot().remainingMs)
    }

    @Test
    fun noDriftOverSixtyMinutes() {
        val start = now
        val config = TimerConfig(rounds = 12, roundSec = 4 * 60, restSec = 60) // 5 с + 48 + 11 мин ≈ 59 мин
        val t = IntervalTimer(config) { now }.also { it.start() }
        val events = mutableListOf<Pair<Long, TimerSignal>>()
        // Неровные шаги обработчика (как у реального планировщика), с редкими большими задержками.
        val rnd = java.util.Random(42)
        while (t.phase != TimerPhase.DONE) {
            val step = t.msUntilNextChange().coerceAtMost(1_000) + rnd.nextInt(40) + if (rnd.nextInt(200) == 0) 700 else 0
            now += step
            t.update().forEach { events += now to it }
        }
        val bells = events.filter { it.second == BELL }
        assertEquals(12, bells.size)
        bells.forEachIndexed { i, (at, _) ->
            val expected = start + 5_000 + i * (240_000L + 60_000L)
            assertTrue("раунд ${i + 1}: опоздание ${at - expected} мс", at - expected in 0..800)
        }
        val done = events.single { it.second == TRIPLE_BELL }.first
        assertTrue("конец: ${done - start - config.totalMs} мс", done - (start + config.totalMs) in 0..800)
        assertEquals(12, events.count { it.second == KNOCKS })
        assertEquals(11, events.count { it.second == DOUBLE_BELL })
    }

    @Test
    fun nextChangeTargetsSecondBoundaryAndWarning() {
        val start = now
        val t = timer(rounds = 1, roundSec = 30, restSec = 0)
        assertEquals(1_000L, t.msUntilNextChange()) // 5,000 → 4,000
        now = start + 300
        assertEquals(700L, t.msUntilNextChange())
        now = start + 5_000 + 19_600 // до «10 секунд» 400 мс
        t.update()
        assertEquals(400L, t.msUntilNextChange())
        t.pause()
        assertEquals(Long.MAX_VALUE, t.msUntilNextChange())
    }

    @Test
    fun configCoercedToLimits() {
        assertEquals(TimerConfig(1, 15, 0), TimerConfig(0, 1, -5).coerced())
        assertEquals(TimerConfig(99, 1200, 600), TimerConfig(500, 5000, 5000).coerced())
    }
}
