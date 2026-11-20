package com.gym.tracker.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Настройки выгрузки в Google Таблицу: ссылка на скрипт, токен и состояние последней синхронизации.
 * Хранятся в DataStore (тот же файл настроек, что тема и таймер); токен никуда не логируется.
 */
class SyncPreferences(private val store: DataStore<Preferences>) {

    data class Config(val url: String, val token: String) {
        val isComplete: Boolean get() = url.isNotBlank() && token.isNotBlank()
    }

    /** [errorCode] — имя [com.gym.tracker.sync.SheetError.Kind], [errorDetail] — текст ошибки скрипта (если есть). */
    data class Status(val lastSyncAt: Long?, val errorCode: String?, val errorDetail: String?)

    private val safeData = store.data.catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    val config: Flow<Config> = safeData
        .map { Config(it[KEY_URL].orEmpty(), it[KEY_TOKEN].orEmpty()) }
        .distinctUntilChanged()

    val status: Flow<Status> = safeData
        .map { Status(it[KEY_LAST_SYNC], it[KEY_ERROR], it[KEY_ERROR_DETAIL]) }
        .distinctUntilChanged()

    suspend fun setConfig(url: String, token: String) {
        store.edit {
            it[KEY_URL] = url.trim()
            it[KEY_TOKEN] = token.trim()
        }
    }

    /** Успешная отправка: время и сброс ошибки. */
    suspend fun recordSuccess(at: Long) {
        store.edit {
            it[KEY_LAST_SYNC] = at
            it.remove(KEY_ERROR)
            it.remove(KEY_ERROR_DETAIL)
        }
    }

    suspend fun recordError(code: String, detail: String?) {
        store.edit {
            it[KEY_ERROR] = code
            if (detail != null) it[KEY_ERROR_DETAIL] = detail else it.remove(KEY_ERROR_DETAIL)
        }
    }

    private companion object {
        val KEY_URL = stringPreferencesKey("sync_url")
        val KEY_TOKEN = stringPreferencesKey("sync_token")
        val KEY_LAST_SYNC = longPreferencesKey("sync_last_at")
        val KEY_ERROR = stringPreferencesKey("sync_error")
        val KEY_ERROR_DETAIL = stringPreferencesKey("sync_error_detail")
    }
}
