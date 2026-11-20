package com.gym.tracker

import android.app.Application
import android.os.SystemClock
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.gym.tracker.data.GymRepository
import com.gym.tracker.data.SyncPreferences
import com.gym.tracker.data.ThemePreferences
import com.gym.tracker.data.TimerPreferences
import com.gym.tracker.data.db.GymDatabase
import com.gym.tracker.sync.SheetClient
import com.gym.tracker.sync.SheetSync
import com.gym.tracker.sync.SyncWorker
import com.gym.tracker.timer.TimerController
import com.gym.tracker.timer.TimerService
import kotlinx.coroutines.MainScope

/** Ручной DI: один экземпляр БД, репозитория, настроек, таймера и выгрузки в таблицу на процесс. */
class GymApplication : Application() {
    private val database: GymDatabase by lazy { GymDatabase.build(this) }

    /** Изменения записей ставят операции в очередь выгрузки и запускают отправку. */
    val repository: GymRepository by lazy {
        GymRepository(database, onOutboxChanged = { SyncWorker.schedule(this) })
    }

    /** Один DataStore на файл настроек (тема и таймер — в одном файле). */
    private val settingsStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create { preferencesDataStoreFile(ThemePreferences.FILE_NAME) }
    }

    val themePreferences: ThemePreferences by lazy { ThemePreferences(settingsStore) }

    val timerPreferences: TimerPreferences by lazy { TimerPreferences(settingsStore) }

    val syncPreferences: SyncPreferences by lazy { SyncPreferences(settingsStore) }

    val sheetSync: SheetSync by lazy {
        val client = SheetClient()
        SheetSync(database.syncDao(), syncPreferences, client::send)
    }

    val timerController: TimerController by lazy {
        TimerController(MainScope(), SystemClock::elapsedRealtime) { TimerService.start(this) }
    }
}
