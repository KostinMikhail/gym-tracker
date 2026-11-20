package com.gym.tracker.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gym.tracker.backup.TokenCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Настройки резервной копии в GitHub (тот же файл DataStore, что тема и таймер).
 * Токен хранится только зашифрованным ([TokenCipher], ключ в Android Keystore) и нигде не логируется.
 */
class BackupPreferences(private val store: DataStore<Preferences>, private val cipher: TokenCipher) {

    data class Config(val repo: String, val token: String) {
        val isComplete: Boolean get() = repo.isNotBlank() && token.isNotBlank()

        /** Не печатать токен даже случайно (data class по умолчанию выводит все поля). */
        override fun toString(): String = "Config(repo=$repo, token=${if (token.isEmpty()) "" else "***"})"
    }

    /** [errorCode] — имя [com.gym.tracker.backup.GitHubError.Kind] последней неудачной выгрузки. */
    data class Status(val lastUploadAt: Long?, val lastCount: Int?, val errorCode: String?)

    private val safeData = store.data.catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    val config: Flow<Config> = safeData
        .map { p ->
            Config(
                repo = p[KEY_REPO] ?: DEFAULT_REPO,
                token = p[KEY_TOKEN]?.let(cipher::decrypt).orEmpty(),
            )
        }
        .distinctUntilChanged()

    val status: Flow<Status> = safeData
        .map { Status(it[KEY_LAST_AT], it[KEY_LAST_COUNT], it[KEY_ERROR]) }
        .distinctUntilChanged()

    suspend fun lastUploadedHash(): String? = safeData.first()[KEY_LAST_HASH]

    suspend fun setConfig(repo: String, token: String) {
        val cleanToken = token.trim()
        val encrypted = if (cleanToken.isEmpty()) null else cipher.encrypt(cleanToken)
        store.edit {
            val oldRepo = it[KEY_REPO] ?: DEFAULT_REPO
            it[KEY_REPO] = repo.trim()
            if (encrypted == null) it.remove(KEY_TOKEN) else it[KEY_TOKEN] = encrypted
            // Другой репозиторий — снимок нужно выгрузить туда заново.
            if (oldRepo != repo.trim()) it.remove(KEY_LAST_HASH)
        }
    }

    suspend fun recordSuccess(hash: String, at: Long, count: Int) {
        store.edit {
            it[KEY_LAST_HASH] = hash
            it[KEY_LAST_AT] = at
            it[KEY_LAST_COUNT] = count
            it.remove(KEY_ERROR)
        }
    }

    /** Снимок не изменился (выгружать нечего) — только сброс прошлой ошибки. */
    suspend fun clearError() {
        store.edit { it.remove(KEY_ERROR) }
    }

    suspend fun recordError(code: String) {
        store.edit { it[KEY_ERROR] = code }
    }

    companion object {
        /** Приватный репозиторий владельца для журнала (подставляется в поле «Репозиторий»). */
        const val DEFAULT_REPO = "KostinMikhail/gym-data"

        private val KEY_REPO = stringPreferencesKey("backup_repo")
        private val KEY_TOKEN = stringPreferencesKey("backup_token_enc")
        private val KEY_LAST_HASH = stringPreferencesKey("backup_last_hash")
        private val KEY_LAST_AT = longPreferencesKey("backup_last_at")
        private val KEY_LAST_COUNT = intPreferencesKey("backup_last_count")
        private val KEY_ERROR = stringPreferencesKey("backup_error")
    }
}
