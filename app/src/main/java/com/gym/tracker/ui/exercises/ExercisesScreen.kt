package com.gym.tracker.ui.exercises

import androidx.compose.ui.res.stringResource
import com.gym.tracker.R
import com.gym.tracker.ui.components.GymCard
import com.gym.tracker.ui.theme.CardRole
import com.gym.tracker.ui.theme.HeaderDecor
import com.gym.tracker.ui.theme.ScreenKicker
import com.gym.tracker.ui.theme.ScreenTitle
import java.time.LocalDate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gym.tracker.data.GymRepository
import com.gym.tracker.data.db.ExerciseEntity
import com.gym.tracker.domain.Format
import com.gym.tracker.ui.components.EmptyText
import com.gym.tracker.ui.components.ExerciseDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExercisesViewModel(private val repo: GymRepository) : ViewModel() {
    val exercises: StateFlow<List<ExerciseEntity>?> = repo.observeExercises()
        .map { list -> list.sortedBy { it.name.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val editError = MutableStateFlow<String?>(null)
    /** Сигнал «сохранено» для закрытия диалога. */
    val saved = MutableStateFlow(false)

    fun save(id: String, name: String, sets: Int, reps: Int) {
        viewModelScope.launch {
            when (repo.updateExercise(id, name, sets, reps)) {
                GymRepository.UpdateResult.OK -> { editError.value = null; saved.value = true }
                GymRepository.UpdateResult.NAME_TAKEN -> editError.value = "Упражнение с таким названием уже есть"
                GymRepository.UpdateResult.INVALID -> editError.value = "Проверьте название, подходы и повторы"
            }
        }
    }

    fun onSavedHandled() {
        saved.value = false
    }

    fun clearError() {
        editError.value = null
    }
}

@Composable
fun ExercisesScreen(vm: ExercisesViewModel, contentPadding: PaddingValues, onOpenHistory: (String) -> Unit) {
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val error by vm.editError.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(saved) { if (saved) { editingId = null; vm.onSavedHandled() } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ScreenKicker(stringResource(R.string.kicker_exercises))
            ScreenTitle("Упражнения", fallback = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 8.dp))
            HeaderDecor(
                hudText = stringResource(R.string.hud_strip, Format.weekdayDate(LocalDate.now())),
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        val list = exercises
        if (list != null && list.isEmpty()) item { EmptyText("Упражнений нет") }
        items(list.orEmpty(), key = { it.id }) { ex ->
            GymCard(
                modifier = Modifier.fillMaxWidth().clickable { vm.clearError(); editingId = ex.id },
                role = CardRole.LIST,
            ) {
                Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(ex.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "По умолчанию: ${Format.setsReps(ex.defaultSets, ex.defaultReps)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onOpenHistory(ex.id) }) {
                        Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = "История")
                    }
                }
            }
        }
    }

    val editing = exercises?.firstOrNull { it.id == editingId }
    if (editing != null) {
        ExerciseDialog(
            title = "Упражнение",
            initialName = editing.name,
            initialSets = editing.defaultSets,
            initialReps = editing.defaultReps,
            confirmLabel = "Сохранить",
            error = error,
            onConfirm = { name, sets, reps -> vm.save(editing.id, name, sets, reps) },
            onDismiss = { editingId = null },
            extraButton = {
                OutlinedButton(onClick = { editingId = null; onOpenHistory(editing.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text("История")
                }
            },
        )
    }
}
