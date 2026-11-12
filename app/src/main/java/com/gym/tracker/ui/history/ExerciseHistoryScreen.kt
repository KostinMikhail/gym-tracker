package com.gym.tracker.ui.history

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.gym.tracker.R
import com.gym.tracker.ui.components.GymCard
import com.gym.tracker.ui.theme.CardRole
import com.gym.tracker.ui.theme.HeaderDecor
import com.gym.tracker.ui.theme.ScreenTitle
import java.time.LocalDate
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gym.tracker.data.GymRepository
import com.gym.tracker.data.db.ExerciseEntity
import com.gym.tracker.data.db.ExerciseHistoryRow
import com.gym.tracker.domain.Format
import com.gym.tracker.ui.components.EmptyText
import com.gym.tracker.ui.components.FeelText
import com.gym.tracker.ui.components.ShrinkText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ExerciseHistoryUi(
    val loaded: Boolean = false,
    val exercise: ExerciseEntity? = null,
    val rows: List<ExerciseHistoryRow> = emptyList(),
) {
    /** Личный рекорд — запись с максимальным весом (самая ранняя из равных). */
    val best: ExerciseHistoryRow? get() = rows.maxWithOrNull(compareBy<ExerciseHistoryRow> { it.entry.weightKg }.thenByDescending { it.date })
}

class ExerciseHistoryViewModel(repo: GymRepository, savedState: SavedStateHandle) : ViewModel() {
    private val exerciseId: String = checkNotNull(savedState.get<String>(ARG_EXERCISE_ID))

    val state: StateFlow<ExerciseHistoryUi> = combine(
        repo.observeExercise(exerciseId),
        repo.observeExerciseHistory(exerciseId),
    ) { ex, rows -> ExerciseHistoryUi(true, ex, rows) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseHistoryUi())

    companion object {
        const val ARG_EXERCISE_ID = "exerciseId"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseHistoryScreen(vm: ExerciseHistoryViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                },
                title = { ScreenTitle(state.exercise?.name ?: "Упражнение", fallback = LocalTextStyle.current, sizeOverride = 22.sp) },
            )
            HeaderDecor(hudText = stringResource(R.string.hud_strip, Format.weekdayDate(LocalDate.now())))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.loaded && state.rows.isEmpty()) {
                item { EmptyText("Это упражнение ещё не выполнялось") }
                return@LazyColumn
            }
            state.best?.let { best ->
                item {
                    GymCard(role = CardRole.HIGHLIGHT) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Личный рекорд", style = MaterialTheme.typography.labelLarge)
                            Text(
                                Format.weight(best.entry.weightKg),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "${Format.date(best.date)} · ${Format.setsReps(best.entry.sets, best.entry.reps)}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            item {
                Text("Вес по датам", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                WeightChart(state.rows, Modifier.fillMaxWidth().height(200.dp).padding(vertical = 8.dp))
            }
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    HeaderCell("Дата", 0.9f)
                    HeaderCell("Тренировка", 1.3f)
                    HeaderCell("Объём", 0.8f)
                    HeaderCell("Вес", 0.9f)
                    HeaderCell("Ощущение", 1f)
                }
                HorizontalDivider()
            }
            items(state.rows, key = { it.entry.id }) { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    ShrinkText(Format.date(r.date), true, Modifier.weight(0.9f).padding(end = 4.dp), style = MaterialTheme.typography.bodyMedium)
                    ShrinkText(r.templateName.orEmpty(), true, Modifier.weight(1.3f).padding(end = 4.dp), style = MaterialTheme.typography.bodyMedium)
                    ShrinkText(Format.setsReps(r.entry.sets, r.entry.reps), true, Modifier.weight(0.8f).padding(end = 4.dp), style = MaterialTheme.typography.bodyMedium)
                    ShrinkText(Format.weight(r.entry.weightKg), true, Modifier.weight(0.9f).padding(end = 4.dp), style = MaterialTheme.typography.bodyMedium)
                    FeelText(r.entry.feel, Modifier.weight(1f), singleLine = true)
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(text: String, weight: Float) {
    ShrinkText(
        text,
        true,
        Modifier.weight(weight),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Линейный график веса по датам (Canvas, без библиотек). */
@Composable
private fun WeightChart(rows: List<ExerciseHistoryRow>, modifier: Modifier) {
    // Для одной даты берём максимальный вес.
    val points = rows.groupBy { it.date }
        .map { (date, list) -> date to list.maxOf { it.entry.weightKg } }
        .sortedBy { it.first }
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = labelColor, fontSize = 11.sp)

    if (points.isEmpty()) return
    Canvas(modifier) {
        val maxW = points.maxOf { it.second }
        val minW = points.minOf { it.second }
        val top = if (maxW == minW) maxW + 5 else maxW
        val bottom = if (maxW == minW) (minW - 5).coerceAtLeast(0.0) else minW
        val range = (top - bottom).takeIf { it > 0 } ?: 1.0

        val leftPad = 44.dp.toPx()
        val bottomPad = 20.dp.toPx()
        val topPad = 8.dp.toPx()
        val w = size.width - leftPad - 8.dp.toPx()
        val h = size.height - bottomPad - topPad

        val firstDay = points.first().first.toEpochDay()
        val lastDay = points.last().first.toEpochDay()
        val daySpan = (lastDay - firstDay).toFloat()

        fun x(i: Int): Float = leftPad + if (daySpan == 0f) w / 2 else w * (points[i].first.toEpochDay() - firstDay) / daySpan
        fun y(v: Double): Float = topPad + (h * (1 - (v - bottom) / range)).toFloat()

        // Сетка и подписи осей.
        listOf(top, bottom).forEach { v ->
            drawLine(gridColor, Offset(leftPad, y(v)), Offset(size.width, y(v)), strokeWidth = 1f)
            val t = measurer.measure(Format.number(v), labelStyle)
            drawText(t, topLeft = Offset(leftPad - t.size.width - 6.dp.toPx(), y(v) - t.size.height / 2))
        }
        val firstLabel = measurer.measure(Format.date(points.first().first), labelStyle)
        drawText(firstLabel, topLeft = Offset(leftPad, size.height - firstLabel.size.height))
        if (points.size > 1) {
            val lastLabel = measurer.measure(Format.date(points.last().first), labelStyle)
            drawText(lastLabel, topLeft = Offset(size.width - lastLabel.size.width, size.height - lastLabel.size.height))
        }

        val path = Path()
        points.forEachIndexed { i, p ->
            if (i == 0) path.moveTo(x(i), y(p.second)) else path.lineTo(x(i), y(p.second))
        }
        drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx()))
        points.forEachIndexed { i, p -> drawCircle(lineColor, radius = 4.dp.toPx(), center = Offset(x(i), y(p.second))) }
    }
}
