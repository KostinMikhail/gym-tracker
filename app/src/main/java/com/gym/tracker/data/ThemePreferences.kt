package com.gym.tracker.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gym.tracker.ui.theme.GymThemeId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Настройка оформления (не данные тренировок — хранится в DataStore, а не в Room). */
class ThemePreferences(private val store: DataStore<Preferences>) {

    val theme: Flow<GymThemeId> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { GymThemeId.fromStorageKey(it[KEY_THEME]) }
        .distinctUntilChanged()

    suspend fun setTheme(id: GymThemeId) {
        store.edit { it[KEY_THEME] = id.storageKey }
    }

    companion object {
        const val FILE_NAME = "settings"
        private val KEY_THEME = stringPreferencesKey("theme")
    }
}
