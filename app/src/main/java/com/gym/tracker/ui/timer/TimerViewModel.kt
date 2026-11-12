package com.gym.tracker.ui.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gym.tracker.data.TimerPreferences
import com.gym.tracker.domain.timer.TimerConfig
import com.gym.tracker.domain.timer.TimerPhase
import com.gym.tracker.domain.timer.TimerSnapshot
import com.gym.tracker.timer.TimerController
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Таймер идёт или на паузе (не настройки и не «Готово»). */
fun TimerSnapshot?.isActive(): Boolean = this != null && phase != TimerPhase.DONE

class TimerViewModel(
    private val controller: TimerController,
    private val prefs: TimerPreferences,
) : ViewModel() {

    val timer: StateFlow<TimerSnapshot?> = controller.state

    private val _config = MutableStateFlow<TimerConfig?>(null)

    /** null — настройки ещё читаются из DataStore. */
    val config: StateFlow<TimerConfig?> = _config.asStateFlow()

    val notificationsAsked: StateFlow<Boolean> =
        prefs.notificationsAsked.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    init {
        viewModelScope.launch { _config.value = prefs.config.first() }
        // Сохранение после изменения (с задержкой — долгое нажатие меняет значение много раз в секунду).
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            _config.filterNotNull().drop(1).debounce(SAVE_DELAY_MS).collect { prefs.save(it) }
        }
    }

    fun changeRounds(delta: Int) = change { it.copy(rounds = it.rounds + delta) }

    fun changeRound(deltaSec: Int) = change { it.copy(roundSec = it.roundSec + deltaSec) }

    fun changeRest(deltaSec: Int) = change { it.copy(restSec = it.restSec + deltaSec) }

    private fun change(transform: (TimerConfig) -> TimerConfig) {
        _config.update { c -> c?.let(transform)?.coerced() }
    }

    fun start() {
        val c = _config.value ?: return
        viewModelScope.launch { prefs.save(c) }
        controller.start(c)
    }

    fun notificationsAskedNow() {
        viewModelScope.launch { prefs.setNotificationsAsked() }
    }

    fun pause() = controller.pause()

    fun resume() = controller.resume()

    fun restartPhase() = controller.restartPhase()

    fun reset() = controller.reset()

    /** «Ещё раз» — те же настройки, с подготовки. */
    fun again() {
        val c = controller.state.value?.config ?: _config.value ?: return
        controller.start(c)
    }

    private companion object {
        const val SAVE_DELAY_MS = 400L
    }
}
