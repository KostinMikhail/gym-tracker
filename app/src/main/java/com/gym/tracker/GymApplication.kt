package com.gym.tracker

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.gym.tracker.data.GymRepository
import com.gym.tracker.data.ThemePreferences
import com.gym.tracker.data.db.GymDatabase

/** Ручной DI: один экземпляр БД, репозитория и настроек на процесс. */
class GymApplication : Application() {
    val repository: GymRepository by lazy { GymRepository(GymDatabase.build(this)) }

    val themePreferences: ThemePreferences by lazy {
        ThemePreferences(PreferenceDataStoreFactory.create { preferencesDataStoreFile(ThemePreferences.FILE_NAME) })
    }
}
