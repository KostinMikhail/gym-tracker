package com.gym.tracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gym.tracker.backup.GitHubBackup
import com.gym.tracker.backup.GitHubError
import com.gym.tracker.data.BackupPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val prefs: BackupPreferences,
    private val backup: GitHubBackup,
    /** Фоновая выгрузка (с паузой) — после смены настроек. */
    private val schedule: () -> Unit,
) : ViewModel() {

    data class Form(val repo: String, val token: String) {
        override fun toString(): String = "Form(repo=$repo, token=***)"
    }

    sealed interface Check {
        data object Idle : Check
        data object Checking : Check

        /** [tokenExpires] — дата окончания срока токена (yyyy-MM-dd), если GitHub её сообщил. */
        data class Connected(val tokenExpires: String?) : Check
        data class Failed(val kind: GitHubError.Kind) : Check
    }

    sealed interface Upload {
        data object Idle : Upload
        data object Working : Upload
        data class Done(val outcome: GitHubBackup.Outcome) : Upload
    }

    private val _form = MutableStateFlow<Form?>(null)

    /** null — настройки ещё читаются. */
    val form: StateFlow<Form?> = _form.asStateFlow()

    private val _check = MutableStateFlow<Check>(Check.Idle)
    val check: StateFlow<Check> = _check.asStateFlow()

    private val _upload = MutableStateFlow<Upload>(Upload.Idle)
    val upload: StateFlow<Upload> = _upload.asStateFlow()

    val status: StateFlow<BackupPreferences.Status?> =
        prefs.status.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            val c = prefs.config.first()
            _form.value = Form(c.repo, c.token)
        }
    }

    fun setRepo(repo: String) = update { it.copy(repo = repo) }

    fun setToken(token: String) = update { it.copy(token = token) }

    /** Сохраняется сразу (токен — зашифрованным). */
    private fun update(transform: (Form) -> Form) {
        val next = _form.value?.let(transform) ?: return
        _form.value = next
        _check.value = Check.Idle
        _upload.value = Upload.Idle
        viewModelScope.launch { prefs.setConfig(next.repo, next.token) }
    }

    fun checkConnection() {
        val f = _form.value ?: return
        if (_check.value == Check.Checking) return
        _check.value = Check.Checking
        viewModelScope.launch {
            _check.value = backup.check(f.repo, f.token).fold(
                onSuccess = { Check.Connected(it.tokenExpires?.take(10)) },
                onFailure = { Check.Failed((it as GitHubError).kind) },
            )
        }
    }

    /** «Выгрузить сейчас» — сразу, без паузы. */
    fun uploadNow() {
        val f = _form.value ?: return
        if (_upload.value == Upload.Working) return
        _upload.value = Upload.Working
        viewModelScope.launch {
            prefs.setConfig(f.repo, f.token)
            _upload.value = Upload.Done(backup.upload(verifyRemote = true))
        }
    }

    /** CSV для «Поделиться историей». */
    suspend fun csvSnapshot(): String = backup.snapshot()

    override fun onCleared() {
        // Репозиторий/токен могли поменять — пусть фоновая выгрузка попробует с новыми настройками.
        val f = _form.value
        if (f != null && f.repo.isNotBlank() && f.token.isNotBlank()) schedule()
    }
}
