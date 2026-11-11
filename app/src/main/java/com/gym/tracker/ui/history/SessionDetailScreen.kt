package com.gym.tracker.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gym.tracker.data.GymRepository
import com.gym.tracker.data.db.EntryDetails
import com.gym.tracker.data.db.ExerciseEntity
import com.gym.tracker.data.db.Feel
import com.gym.tracker.data.db.SessionSummary
import com.gym.tracker.domain.Format
import com.gym.tracker.ui.components.ConfirmDialog
import com.gym.tracker.ui.components.EmptyText
import com.gym.tracker.ui.components.FeelSelector
import com.gym.tracker.ui.components.FeelText
import com.gym.tracker.ui.components.NumberStepper
import com.gym.tracker.ui.components.stepInt
import com.gym.tracker.ui.components.stepWeight
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SessionDetailUi(
    val loaded: Boolean = false,
    val session: SessionSummary? = null,
    val entries: List<EntryDetails> = emptyList(),
)

class SessionDetailViewModel(private val repo: GymRepository, savedState: SavedStateHandle) : ViewModel() {
    val sessionId: String = checkNotNull(savedState.get<String>(ARG_SESSION_ID))
    val isSummary: Boolean = savedState.get<Boolean>(ARG_SUMMARY) ?: false

    val state: StateFlow<SessionDetailUi> = combine(
        repo.observeSessionSummary(sessionId),
        repo.observeEntryDetails(sessionId),
    ) { s, e -> SessionDetailUi(true, s, e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionDetailUi())

    /** Варианты упражнений слота для диалога редактирования. */
    val editOptions = MutableStateFlow<List<ExerciseEntity>>(emptyList())
    val deleted = MutableStateFlow(false)

    fun loadOptions(slotId: String?) {
        editOptions.value = emptyList()
        if (slotId == null) return
        viewModelScope.launch { editOptions.value = repo.getOptionsForSlot(slotId).map { it.exercise } }
    }

    fun saveEntry(e: EntryDetails, exerciseId: String, sets: Int, reps: Int, weight: Double, feel: Feel) {
        viewModelScope.launch {
            repo.saveEntry(e.entry.id, e.entry.sessionId, e.entry.slotId, exerciseId, sets, reps, weight, feel)
        }
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch { repo.deleteEntry(id) }
    }

    fun deleteSession() {
        viewModelScope.launch {
            repo.deleteSession(sessionId)
            deleted.value = true
        }
    }

    companion object {
        const val ARG_SESSION_ID = "sessionId"
        const val ARG_SUMMARY = "summary"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(vm: SessionDetailViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val deleted by vm.deleted.collectAsStateWithLifecycle()
    val options by vm.editOptions.collectAsStateWithLifecycle()
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDeleteSession by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(deleted) { if (deleted) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = if (vm.isSummary) onDone else onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = {
                    val s = state.session
                    Column {
                        Text(if (vm.isSummary) "Итоги тренировки" else (s?.templateName ?: "Тренировка"))
                        if (s != null) {
                            Text(
                                if (vm.isSummary) "${s.templateName.orEmpty()} · ${Format.date(s.date)}" else Format.date(s.date),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (!vm.isSummary && state.session != null) {
                        IconButton(onClick = { confirmDeleteSession = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Удалить тренировку")
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.loaded && state.session == null) {
                item { EmptyText("Тренировка не найдена") }
            } else if (state.loaded && state.entries.isEmpty()) {
                item { EmptyText("В этой тренировке нет упражнений") }
            }
            if (vm.isSummary && state.entries.isNotEmpty()) {
                item {
                    Text(
                        "Выполнено: ${Format.exercisesCount(state.entries.size)}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            items(state.entries, key = { it.entry.id }) { e ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        vm.loadOptions(e.entry.slotId)
                        editingId = e.entry.id
                    },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            if (e.slotName != null) {
                                Text(e.slotName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(e.exerciseName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Row {
                                Text("${Format.setsReps(e.entry.sets, e.entry.reps)} · ${Format.weight(e.entry.weightKg)} · ")
                                FeelText(e.entry.feel)
                            }
                        }
                        IconButton(onClick = { deletingEntryId = e.entry.id }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Удалить запись")
                        }
                    }
                }
            }
            if (vm.isSummary) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("На главную") }
                }
            }
        }
    }

    val editing = state.entries.firstOrNull { it.entry.id == editingId }
    if (editing != null) {
        EntryEditDialog(
            entry = editing,
            options = options,
            onSave = { exId, sets, reps, w, feel ->
                vm.saveEntry(editing, exId, sets, reps, w, feel)
                editingId = null
            },
            onDismiss = { editingId = null },
        )
    }

    deletingEntryId?.let { id ->
        ConfirmDialog(
            title = "Удалить запись?",
            text = "Запись упражнения будет удалена из этой тренировки.",
            confirmLabel = "Удалить",
            onConfirm = { vm.deleteEntry(id); deletingEntryId = null },
            onDismiss = { deletingEntryId = null },
        )
    }

    if (confirmDeleteSession) {
        ConfirmDialog(
            title = "Удалить тренировку?",
            text = "Тренировка и все её записи будут удалены. Это нельзя отменить.",
            confirmLabel = "Удалить",
            onConfirm = { confirmDeleteSession = false; vm.deleteSession() },
            onDismiss = { confirmDeleteSession = false },
        )
    }
}

/** Редактирование записи: упражнение (из вариантов слота), подходы, повторы, вес, самочувствие. */
@Composable
fun EntryEditDialog(
    entry: EntryDetails,
    options: List<ExerciseEntity>,
    onSave: (exerciseId: String, sets: Int, reps: Int, weight: Double, feel: Feel) -> Unit,
    onDismiss: () -> Unit,
) {
    val e = entry.entry
    var exerciseId by rememberSaveable(e.id) { mutableStateOf(e.exerciseId) }
    var sets by rememberSaveable(e.id) { mutableStateOf(e.sets.toString()) }
    var reps by rememberSaveable(e.id) { mutableStateOf(e.reps.toString()) }
    var weight by rememberSaveable(e.id) { mutableStateOf(if (e.weightKg == 0.0) "" else Format.number(e.weightKg)) }
    var feel by rememberSaveable(e.id) { mutableStateOf(e.feel) }
    var error by rememberSaveable(e.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (options.size > 1) "Изменить запись" else entry.exerciseName) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (options.size > 1) {
                    options.forEach { ex ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = ex.id == exerciseId, role = Role.RadioButton) { exerciseId = ex.id },
                        ) {
                            RadioButton(selected = ex.id == exerciseId, onClick = null, modifier = Modifier.padding(end = 8.dp))
                            Text(ex.name)
                        }
                    }
                }
                NumberStepper("Подходы", sets, { sets = it }, { sets = stepInt(sets, -1) }, { sets = stepInt(sets, 1) }, decimal = false, labelAbove = true)
                NumberStepper("Повторы", reps, { reps = it }, { reps = stepInt(reps, -1) }, { reps = stepInt(reps, 1) }, decimal = false, labelAbove = true)
                NumberStepper("Вес (кг)", weight, { weight = it }, { weight = stepWeight(weight, -2.5) }, { weight = stepWeight(weight, 2.5) }, decimal = true, labelAbove = true)
                FeelSelector(selected = feel, onSelect = { feel = it })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = sets.toIntOrNull()
                val r = reps.toIntOrNull()
                val w = Format.parseWeight(weight)
                if (s == null || s < 1 || r == null || r < 1) {
                    error = "Подходы и повторы — не меньше 1"
                } else if (w == null) {
                    error = "Неверный вес"
                } else {
                    onSave(exerciseId, s, r, w, feel)
                }
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
