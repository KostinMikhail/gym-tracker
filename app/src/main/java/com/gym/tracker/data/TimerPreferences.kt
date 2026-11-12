package com.gym.tracker.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.gym.tracker.domain.timer.TimerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Последние настройки таймера (UI-настройка — в DataStore, не в Room). Тот же файл настроек, что и тема. */
class TimerPreferences(private val store: DataStore<Preferences>) {

    private val safeData = store.data.catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    val config: Flow<TimerConfig> = safeData
        .map {
            val d = TimerConfig.DEFAULT
            TimerConfig(it[KEY_ROUNDS] ?: d.rounds, it[KEY_ROUND_SEC] ?: d.roundSec, it[KEY_REST_SEC] ?: d.restSec).coerced()
        }
        .distinctUntilChanged()

    /** Разрешение на уведомления уже спрашивали (спрашиваем один раз — при первом «Старт»). */
    val notificationsAsked: Flow<Boolean> = safeData.map { it[KEY_NOTIFICATIONS_ASKED] ?: false }

    suspend fun save(config: TimerConfig) {
        val c = config.coerced()
        store.edit {
            it[KEY_ROUNDS] = c.rounds
            it[KEY_ROUND_SEC] = c.roundSec
            it[KEY_REST_SEC] = c.restSec
        }
    }

    suspend fun setNotificationsAsked() {
        store.edit { it[KEY_NOTIFICATIONS_ASKED] = true }
    }

    private companion object {
        val KEY_ROUNDS = intPreferencesKey("timer_rounds")
        val KEY_ROUND_SEC = intPreferencesKey("timer_round_sec")
        val KEY_REST_SEC = intPreferencesKey("timer_rest_sec")
        val KEY_NOTIFICATIONS_ASKED = booleanPreferencesKey("timer_notifications_asked")
    }
}
