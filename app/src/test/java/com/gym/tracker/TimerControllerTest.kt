package com.gym.tracker

import com.gym.tracker.domain.timer.TimerConfig
import com.gym.tracker.domain.timer.TimerPhase
import com.gym.tracker.domain.timer.TimerSignal
import com.gym.tracker.timer.TimerController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Цикл контроллера на виртуальном времени: сигналы, пауза, сброс, запуск сервиса. */
@OptIn(ExperimentalCoroutinesApi::class)
class TimerControllerTest {

    private fun TestScope.controller(onStart: () -> Unit = {}) =
        TimerController(backgroundScope, { testScheduler.currentTime }, onStart)

    @Test
    fun runsSequenceAndEmitsSignals() = runTest {
        var started = 0
        val c = controller { started++ }
        val signals = mutableListOf<Pair<Long, TimerSignal>>()
        backgroundScope.launch { c.signals.collect { signals += testScheduler.currentTime to it } }
        runCurrent()
        val t0 = testScheduler.currentTime
        c.start(TimerConfig(2, 15, 15))
        assertEquals(1, started)
        assertEquals(TimerPhase.PREP, c.state.value?.phase)
        advanceTimeBy(5_001)
        assertEquals(TimerPhase.ROUND, c.state.value?.phase)
        advanceTimeBy(60_000)
        assertEquals(TimerPhase.DONE, c.state.value?.phase)
        assertEquals(
            listOf(
                t0 + 5_000 to TimerSignal.BELL,
                t0 + 10_000 to TimerSignal.KNOCKS,
                t0 + 20_000 to TimerSignal.DOUBLE_BELL,
                t0 + 35_000 to TimerSignal.BELL,
                t0 + 40_000 to TimerSignal.KNOCKS,
                t0 + 50_000 to TimerSignal.TRIPLE_BELL,
            ),
            signals,
        )
    }

    @Test
    fun pauseFreezesAndResetGoesIdle() = runTest {
        val c = controller()
        c.start(TimerConfig(1, 60, 0))
        advanceTimeBy(15_000)
        c.pause()
        val frozen = c.state.value!!.remainingMs
        assertTrue(c.state.value!!.paused)
        advanceTimeBy(120_000)
        assertEquals(frozen, c.state.value!!.remainingMs)
        c.resume()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(frozen - 1_000, c.state.value!!.remainingMs)
        c.togglePause()
        assertTrue(c.state.value!!.paused)
        c.restartPhase()
        assertEquals(60_000L, c.state.value!!.remainingMs)
        c.reset()
        assertNull(c.state.value)
    }
}
