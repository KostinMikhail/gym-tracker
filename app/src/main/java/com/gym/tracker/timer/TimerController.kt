package com.gym.tracker.timer

import com.gym.tracker.domain.timer.IntervalTimer
import com.gym.tracker.domain.timer.TimerConfig
import com.gym.tracker.domain.timer.TimerPhase
import com.gym.tracker.domain.timer.TimerSignal
import com.gym.tracker.domain.timer.TimerSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Один таймер на процесс: экран и сервис смотрят на одно и то же состояние, поэтому переход между
 * вкладками, смена темы или возврат в приложение не сбрасывают таймер. Время — только по дедлайнам
 * [IntervalTimer] (часы — SystemClock.elapsedRealtime), цикл лишь будит машину состояний.
 *
 * Все методы вызываются в главном потоке (scope — Dispatchers.Main).
 */
class TimerController(
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    /** Запуск foreground-сервиса (звук, уведомление, работа при заблокированном экране). */
    private val startService: () -> Unit,
) {
    private var timer: IntervalTimer? = null
    private var loop: Job? = null

    private val _state = MutableStateFlow<TimerSnapshot?>(null)

    /** null — таймер не запущен (экран настроек). */
    val state: StateFlow<TimerSnapshot?> = _state.asStateFlow()

    private val _signals = MutableSharedFlow<TimerSignal>(extraBufferCapacity = 16)
    val signals: SharedFlow<TimerSignal> = _signals.asSharedFlow()

    fun start(config: TimerConfig) {
        timer = IntervalTimer(config, clock).also { it.start() }
        publish()
        runLoop()
        startService()
    }

    fun pause() {
        val t = timer ?: return
        emit(t.update())
        t.pause()
        loop?.cancel()
        publish()
    }

    fun resume() {
        val t = timer ?: return
        t.resume()
        publish()
        runLoop()
    }

    fun togglePause() {
        val t = timer ?: return
        if (t.phase == TimerPhase.DONE) return
        if (t.paused) resume() else pause()
    }

    /** «Раунд заново»: текущая фаза с начала, остаётся на паузе. */
    fun restartPhase() {
        val t = timer ?: return
        loop?.cancel()
        t.restartPhase()
        publish()
    }

    /** «Сбросить всё» / «Настройки» / «Остановить»: к экрану настроек, сервис останавливается сам. */
    fun reset() {
        loop?.cancel()
        timer = null
        _state.value = null
    }

    private fun runLoop() {
        loop?.cancel()
        loop = scope.launch {
            while (isActive) {
                val t = timer ?: break
                emit(t.update())
                publish()
                if (t.paused || t.phase == TimerPhase.DONE) break
                // До ближайшего события (смена секунды / «10 секунд» / конец фазы), но не реже 5 раз в секунду —
                // для плавной полосы прогресса.
                delay(t.msUntilNextChange().coerceIn(1, 200))
            }
        }
    }

    /** После долгой задержки могло накопиться несколько переходов — звучит только последний. */
    private fun emit(signals: List<TimerSignal>) {
        signals.lastOrNull()?.let { _signals.tryEmit(it) }
    }

    private fun publish() {
        _state.value = timer?.snapshot()
    }
}
