package com.gym.tracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gym.tracker.data.GymRepository
import com.gym.tracker.data.SyncPreferences
import com.gym.tracker.sync.SheetError
import com.gym.tracker.sync.SheetSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repo: GymRepository,
    private val prefs: SyncPreferences,
    private val sync: SheetSync,
    /** Запуск фоновой отправки очереди. */
    private val schedule: () -> Unit,
) : ViewModel() {

    data class Form(val url: String, val token: String)

    sealed interface Check {
        data object Idle : Check
        data object Checking : Check
        data object Connected : Check
        data class Failed(val error: SheetError) : Check
    }

    sealed interface Export {
        data object Idle : Export
        data object Working : Export
        data class Done(val count: Int) : Export
    }

    data class Status(val lastSyncAt: Long?, val errorCode: String?, val errorDetail: String?, val queued: Int)

    private val _form = MutableStateFlow<Form?>(null)

    /** null — настройки ещё читаются. */
    val form: StateFlow<Form?> = _form.asStateFlow()

    private val _check = MutableStateFlow<Check>(Check.Idle)
    val check: StateFlow<Check> = _check.asStateFlow()

    private val _export = MutableStateFlow<Export>(Export.Idle)
    val export: StateFlow<Export> = _export.asStateFlow()

    val status: StateFlow<Status?> = combine(prefs.status, repo.observeOutboxCount()) { s, n ->
        Status(s.lastSyncAt, s.errorCode, s.errorDetail, n)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            val c = prefs.config.first()
            _form.value = Form(c.url, c.token)
        }
    }

    fun setUrl(url: String) = update { it.copy(url = url) }

    fun setToken(token: String) = update { it.copy(token = token) }

    /** Сохраняется сразу (DataStore), отправка запускается после проверки связи или при уходе с экрана. */
    private fun update(transform: (Form) -> Form) {
        val next = _form.value?.let(transform) ?: return
        _form.value = next
        _check.value = Check.Idle
        viewModelScope.launch { prefs.setConfig(next.url, next.token) }
    }

    fun checkConnection() {
        val f = _form.value ?: return
        if (_check.value == Check.Checking) return
        _check.value = Check.Checking
        viewModelScope.launch {
            prefs.setConfig(f.url, f.token)
            val error = sync.ping(f.url, f.token)
            _check.value = if (error == null) Check.Connected else Check.Failed(error)
            if (error == null) schedule()
        }
    }

    fun exportAll() {
        if (_export.value == Export.Working) return
        _export.value = Export.Working
        viewModelScope.launch { _export.value = Export.Done(repo.enqueueFullHistory()) }
    }

    override fun onCleared() {
        // Ссылку/токен могли поменять — пусть очередь попробует уйти с новыми настройками.
        val f = _form.value
        if (f != null && f.url.isNotBlank() && f.token.isNotBlank()) schedule()
    }
}
