package com.gym.tracker.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gym.tracker.data.GymRepository
import com.gym.tracker.data.db.ExerciseEntity
import com.gym.tracker.data.db.SessionSummary
import com.gym.tracker.ui.components.EmptyText
import com.gym.tracker.ui.home.SessionRow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(repo: GymRepository) : ViewModel() {
    val sessions: StateFlow<List<SessionSummary>?> = repo.observeSessionSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val exercises: StateFlow<List<ExerciseEntity>?> = repo.observeExercisesWithEntries()
        .map { list -> list.sortedBy { it.name.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun HistoryScreen(
    vm: HistoryViewModel,
    contentPadding: PaddingValues,
    onOpenSession: (String) -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val labels = listOf("По датам", "По упражнениям")

    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        Text("История", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            labels.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = tab == i,
                    onClick = { tab = i },
                    shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                ) { Text(label) }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (tab == 0) {
                val list = sessions
                if (list != null && list.isEmpty()) item { EmptyText("Тренировок пока нет") }
                items(list.orEmpty(), key = { it.id }) { s -> SessionRow(s, onClick = { onOpenSession(s.id) }) }
            } else {
                val list = exercises
                if (list != null && list.isEmpty()) item { EmptyText("Пока нет выполненных упражнений") }
                items(list.orEmpty(), key = { it.id }) { ex ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenExercise(ex.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Text(ex.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}
