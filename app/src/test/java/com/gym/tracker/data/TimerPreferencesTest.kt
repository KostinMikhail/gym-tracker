package com.gym.tracker.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gym.tracker.domain.timer.TimerConfig
import com.gym.tracker.ui.theme.GymThemeId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerPreferencesTest {

    private class InMemoryStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val next = transform(state.value)
            state.value = (next as? MutablePreferences)?.toPreferences() ?: next
            return state.value
        }
    }

    @Test
    fun defaultsWhenNothingSaved() = runTest {
        assertEquals(TimerConfig(12, 180, 60), TimerPreferences(InMemoryStore()).config.first())
    }

    @Test
    fun saveAndRestore() = runTest {
        val store = InMemoryStore()
        TimerPreferences(store).save(TimerConfig(5, 120, 0))
        // Новый экземпляр (как после перезапуска приложения) видит сохранённое.
        assertEquals(TimerConfig(5, 120, 0), TimerPreferences(store).config.first())
    }

    @Test
    fun outOfRangeValuesAreCoerced() = runTest {
        val store = InMemoryStore()
        store.edit {
            it[intPreferencesKey("timer_rounds")] = 0
            it[intPreferencesKey("timer_round_sec")] = 99_999
            it[intPreferencesKey("timer_rest_sec")] = -1
        }
        assertEquals(TimerConfig(1, 1200, 0), TimerPreferences(store).config.first())
    }

    @Test
    fun sharesFileWithThemeWithoutClobbering() = runTest {
        val store = InMemoryStore()
        ThemePreferences(store).setTheme(GymThemeId.NEON_ACID)
        TimerPreferences(store).save(TimerConfig(3, 60, 30))
        assertEquals(GymThemeId.NEON_ACID, ThemePreferences(store).theme.first())
        assertEquals("neon_acid", store.data.first()[stringPreferencesKey("theme")])
        assertEquals(TimerConfig(3, 60, 30), TimerPreferences(store).config.first())
    }

    @Test
    fun notificationsAskedOnce() = runTest {
        val prefs = TimerPreferences(InMemoryStore())
        assertFalse(prefs.notificationsAsked.first())
        prefs.setNotificationsAsked()
        assertTrue(prefs.notificationsAsked.first())
    }
}
