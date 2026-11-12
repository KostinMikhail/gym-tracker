package com.gym.tracker.domain.timer

/**
 * Настройки интервального таймера. Длительности — в секундах.
 * Всего = подготовка + раунды + отдых между раундами (после последнего раунда отдыха нет).
 */
data class TimerConfig(val rounds: Int, val roundSec: Int, val restSec: Int) {

    val totalMs: Long get() = PREP_MS + rounds * roundSec * 1000L + (rounds - 1) * restSec * 1000L

    /** Значения в допустимых пределах (например, после чтения настроек). */
    fun coerced(): TimerConfig = TimerConfig(
        rounds.coerceIn(ROUNDS_MIN, ROUNDS_MAX),
        roundSec.coerceIn(ROUND_MIN_SEC, ROUND_MAX_SEC),
        restSec.coerceIn(REST_MIN_SEC, REST_MAX_SEC),
    )

    companion object {
        const val PREP_MS = 5_000L
        const val WARNING_MS = 10_000L
        const val ROUNDS_MIN = 1
        const val ROUNDS_MAX = 99
        const val ROUND_MIN_SEC = 15
        const val ROUND_MAX_SEC = 20 * 60
        const val REST_MIN_SEC = 0
        const val REST_MAX_SEC = 10 * 60
        const val STEP_SEC = 15
        val DEFAULT = TimerConfig(rounds = 12, roundSec = 3 * 60, restSec = 60)
    }
}

enum class TimerPhase { PREP, ROUND, REST, DONE }

/** Звуковые сигналы (последовательности сэмплов проигрывает код). */
enum class TimerSignal {
    /** Начало раунда — один удар гонга. */
    BELL,

    /** Конец раунда, начинается отдых — два удара. */
    DOUBLE_BELL,

    /** Конец последнего раунда — три удара. */
    TRIPLE_BELL,

    /** 10 секунд до конца раунда — три деревянных стука. */
    KNOCKS,
}

/** Состояние для экрана и уведомления. */
data class TimerSnapshot(
    val config: TimerConfig,
    val phase: TimerPhase,
    /** Текущий раунд; во время отдыха — только что завершённый. */
    val round: Int,
    val remainingMs: Long,
    val phaseMs: Long,
    val paused: Boolean,
    /** Последние 10 секунд раунда (только если раунд длиннее 10 секунд). */
    val warning: Boolean,
) {
    /** Оставшиеся целые секунды (округление вверх: 3:00 в начале, 0:01 в последнюю секунду). */
    val remainingSec: Long get() = (remainingMs + 999) / 1000
}

/**
 * Машина состояний таймера: подготовка 5 с → раунд → отдых → … → готово.
 *
 * Время считается только по дедлайнам от [clock] (монотонные миллисекунды, на устройстве —
 * SystemClock.elapsedRealtime): каждая следующая фаза начинается ровно в момент окончания предыдущей,
 * а не «сейчас», поэтому задержки обработки не накапливаются и таймер не уплывает.
 * Без Android-зависимостей — полностью покрыт юнит-тестами.
 */
class IntervalTimer(val config: TimerConfig, private val clock: () -> Long) {

    var phase: TimerPhase = TimerPhase.PREP
        private set
    var round: Int = 1
        private set
    var paused: Boolean = false
        private set

    /** Конец текущей фазы (во время хода). */
    private var deadline: Long = 0

    /** Остаток фазы на паузе. */
    private var pausedRemaining: Long = 0

    /** Стук «10 секунд» в этом раунде уже прозвучал. */
    private var warned = false

    private val roundMs = config.roundSec * 1000L
    private val restMs = config.restSec * 1000L
    private val hasWarning = roundMs > TimerConfig.WARNING_MS

    /** Запуск с подготовки. Во время подготовки звука нет. */
    fun start() {
        phase = TimerPhase.PREP
        round = 1
        paused = false
        warned = false
        deadline = clock() + TimerConfig.PREP_MS
    }

    fun phaseDuration(p: TimerPhase = phase): Long = when (p) {
        TimerPhase.PREP -> TimerConfig.PREP_MS
        TimerPhase.ROUND -> roundMs
        TimerPhase.REST -> restMs
        TimerPhase.DONE -> 0
    }

    fun remaining(): Long = when {
        phase == TimerPhase.DONE -> 0
        paused -> pausedRemaining
        else -> (deadline - clock()).coerceAtLeast(0)
    }

    /**
     * Продвигает таймер к текущему моменту и возвращает сигналы, которые нужно проиграть (по порядку).
     * Если с прошлого вызова прошло несколько фаз (например, долгая задержка), переходы выполняются
     * все — от дедлайна к дедлайну.
     */
    fun update(): List<TimerSignal> {
        if (paused || phase == TimerPhase.DONE) return emptyList()
        val now = clock()
        val signals = mutableListOf<TimerSignal>()
        while (phase != TimerPhase.DONE && now >= deadline) {
            val endedAt = deadline
            when (phase) {
                TimerPhase.PREP -> enterRound(1, endedAt, signals)
                TimerPhase.ROUND -> when {
                    round >= config.rounds -> {
                        phase = TimerPhase.DONE
                        signals += TimerSignal.TRIPLE_BELL
                    }
                    restMs > 0 -> {
                        phase = TimerPhase.REST
                        deadline = endedAt + restMs
                        signals += TimerSignal.DOUBLE_BELL
                    }
                    else -> enterRound(round + 1, endedAt, signals)
                }
                TimerPhase.REST -> enterRound(round + 1, endedAt, signals)
                TimerPhase.DONE -> Unit
            }
        }
        if (phase == TimerPhase.ROUND && hasWarning && !warned && deadline - now <= TimerConfig.WARNING_MS) {
            warned = true
            signals += TimerSignal.KNOCKS
        }
        return signals
    }

    private fun enterRound(n: Int, startAt: Long, signals: MutableList<TimerSignal>) {
        phase = TimerPhase.ROUND
        round = n
        warned = false
        deadline = startAt + roundMs
        signals += TimerSignal.BELL
    }

    /** Пауза: остаток фазы замораживается. */
    fun pause() {
        if (paused || phase == TimerPhase.DONE) return
        pausedRemaining = remaining()
        paused = true
    }

    /** Продолжить с того же места (подготовка не повторяется, если уже прошла). */
    fun resume() {
        if (!paused) return
        paused = false
        deadline = clock() + pausedRemaining
    }

    /** «Раунд заново»: текущая фаза — с полной длительности, таймер остаётся на паузе. */
    fun restartPhase() {
        if (phase == TimerPhase.DONE) return
        if (!paused) pause()
        pausedRemaining = phaseDuration()
        warned = false
    }

    /** Через сколько миллисекунд что-то изменится: конец фазы, сигнал «10 секунд» или смена секунды на экране. */
    fun msUntilNextChange(): Long {
        if (paused || phase == TimerPhase.DONE) return Long.MAX_VALUE
        val rem = remaining()
        var next = rem
        if (phase == TimerPhase.ROUND && hasWarning && !warned) {
            val toWarning = rem - TimerConfig.WARNING_MS
            if (toWarning in 0 until next) next = toWarning
        }
        // На экране — секунды с округлением вверх: показание меняется, когда остаток проходит кратное 1000.
        val toSecond = if (rem % 1000 == 0L) 1000 else rem % 1000
        if (toSecond < next) next = toSecond
        return next.coerceAtLeast(0)
    }

    fun snapshot(): TimerSnapshot {
        val rem = remaining()
        return TimerSnapshot(
            config = config,
            phase = phase,
            round = round,
            remainingMs = rem,
            phaseMs = phaseDuration(),
            paused = paused,
            warning = phase == TimerPhase.ROUND && hasWarning && rem <= TimerConfig.WARNING_MS,
        )
    }
}
