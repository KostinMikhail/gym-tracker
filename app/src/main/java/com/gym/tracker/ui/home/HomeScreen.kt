package com.gym.tracker.ui.home

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gym.tracker.data.GymRepository
import com.gym.tracker.data.db.SessionSummary
import com.gym.tracker.data.db.TemplateWithLastDate
import com.gym.tracker.domain.Format
import com.gym.tracker.domain.suggestTemplateId
import com.gym.tracker.ui.components.EmptyText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HomeUiState(
    val templates: List<TemplateWithLastDate> = emptyList(),
    val suggestedId: String? = null,
    val recent: List<SessionSummary> = emptyList(),
)

class HomeViewModel(private val repo: GymRepository) : ViewModel() {
    val state: StateFlow<HomeUiState> = combine(
        repo.observeTemplatesWithLastDate(),
        repo.observeLatestSession(),
        repo.observeSessionSummaries(),
    ) { templates, latest, sessions ->
        HomeUiState(templates, suggestTemplateId(templates, latest), sessions.take(RECENT_LIMIT))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** id созданной тренировки для перехода на экран тренировки. */
    val startedSessionId = MutableStateFlow<String?>(null)

    fun start(templateId: String) {
        viewModelScope.launch {
            startedSessionId.value = repo.startSession(templateId, LocalDate.now())
        }
    }

    fun onStartedHandled() {
        startedSessionId.value = null
    }

    companion object {
        const val RECENT_LIMIT = 20
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    contentPadding: PaddingValues,
    onStartWorkout: (String) -> Unit,
    onOpenSession: (String) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val started by vm.startedSessionId.collectAsStateWithLifecycle()
    var showSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(started) {
        started?.let {
            vm.onStartedHandled()
            onStartWorkout(it)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Gym", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { showSheet = true },
                modifier = Modifier.fillMaxWidth().height(72.dp),
            ) { Text("Начать тренировку", style = MaterialTheme.typography.titleLarge) }
            Spacer(Modifier.height(24.dp))
            Text("Последние тренировки", style = MaterialTheme.typography.titleMedium)
        }
        if (state.recent.isEmpty()) {
            item { EmptyText("Тренировок пока нет") }
        }
        items(state.recent, key = { it.id }) { s ->
            SessionRow(s, onClick = { onOpenSession(s.id) })
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Выберите тренировку", style = MaterialTheme.typography.titleLarge)
                state.templates.forEach { t ->
                    val suggested = t.template.id == state.suggestedId
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            showSheet = false
                            vm.start(t.template.id)
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (suggested) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        border = if (suggested) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    ) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.template.name, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    t.lastDate?.let { "последний раз: ${Format.date(it)}" } ?: "ещё не выполнялась",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (suggested) {
                                Text("Следующая",style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SessionRow(s: SessionSummary, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(Format.date(s.date), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 16.dp))
            Column(Modifier.weight(1f)) {
                Text(s.templateName ?: "Тренировка", style = MaterialTheme.typography.bodyLarge)
                Text(
                    Format.exercisesCount(s.entryCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
