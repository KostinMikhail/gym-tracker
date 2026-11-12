package com.gym.tracker.ui.history

import androidx.compose.ui.res.stringResource
import com.gym.tracker.R
import com.gym.tracker.domain.Format
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import com.gym.tracker.ui.theme.LocalGymTokens
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
        Column(Modifier.padding(16.dp)) {
            ScreenKicker(stringResource(R.string.kicker_history))
            ScreenTitle("История", fallback = MaterialTheme.typography.headlineMedium)
        }
        HeaderDecor(
            hudText = stringResource(R.string.hud_strip, Format.weekdayDate(LocalDate.now())),
            modifier = Modifier.padding(bottom = 12.dp),
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            labels.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = tab == i,
                    onClick = { tab = i },
                    shape = segmentShape(i, labels.size),
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
                    GymCard(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenExercise(ex.id) },
                        role = CardRole.LIST,
                    ) {
                        Text(ex.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}

/** Форма сегмента: в темах — по форме чипов темы (квадрат/скос), в Классической — как раньше. */
@Composable
private fun segmentShape(index: Int, count: Int): Shape {
    val chip = LocalGymTokens.current.chipShape
    return when {
        LocalGymTokens.current.selectedOption == null -> SegmentedButtonDefaults.itemShape(index, count)
        chip is CornerBasedShape -> SegmentedButtonDefaults.itemShape(index, count, chip)
        else -> RectangleShape
    }
}
