package com.gym.tracker.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gym.tracker.ui.theme.GymThemeId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ThemePreferencesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    /** DataStore в памяти — для проверки логики без файловой системы. */
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
    fun missingValueIsClassic() = runTest {
        assertEquals(GymThemeId.CLASSIC, ThemePreferences(InMemoryStore()).theme.first())
    }

    @Test
    fun writeThenRead() = runTest {
        val prefs = ThemePreferences(InMemoryStore())
        prefs.setTheme(GymThemeId.CYBER_HUD)
        assertEquals(GymThemeId.CYBER_HUD, prefs.theme.first())
        prefs.setTheme(GymThemeId.DITHER_GAMEBOY)
        assertEquals(GymThemeId.DITHER_GAMEBOY, prefs.theme.first())
    }

    @Test
    fun unknownStoredValueFallsBackToClassic() = runTest {
        val store = InMemoryStore()
        store.edit { it[stringPreferencesKey("theme")] = "removed_theme" }
        assertEquals(GymThemeId.CLASSIC, ThemePreferences(store).theme.first())
    }

    /** Настоящий файл: выбор переживает «перезапуск» (новый экземпляр DataStore на тот же файл). */
    @Test
    fun persistsAcrossRestart() = runBlocking {
        val file = tmp.root.resolve("settings.preferences_pb")
        val scope1 = CoroutineScope(Dispatchers.IO + SupervisorJob())
        ThemePreferences(PreferenceDataStoreFactory.create(scope = scope1) { file }).setTheme(GymThemeId.NEON_ACID)
        scope1.coroutineContext[kotlinx.coroutines.Job]!!.cancelAndJoin()

        val scope2 = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val restored = ThemePreferences(PreferenceDataStoreFactory.create(scope = scope2) { file }).theme.first()
        scope2.coroutineContext[kotlinx.coroutines.Job]!!.cancelAndJoin()
        assertEquals(GymThemeId.NEON_ACID, restored)
    }

    @Test
    fun storageKeysAreStableAndUnique() {
        assertEquals(
            listOf("classic", "dither_1bit", "dither_gameboy", "neon_synthwave", "neon_acid", "cyber_yellow", "cyber_hud"),
            GymThemeId.entries.map { it.storageKey },
        )
        assertEquals(GymThemeId.CLASSIC, GymThemeId.fromStorageKey(null))
        GymThemeId.entries.forEach { assertEquals(it, GymThemeId.fromStorageKey(it.storageKey)) }
    }
}
