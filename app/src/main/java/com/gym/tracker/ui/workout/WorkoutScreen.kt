package com.gym.tracker.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gym.tracker.data.db.Feel
import com.gym.tracker.domain.Format
import com.gym.tracker.ui.components.ConfirmDialog
import com.gym.tracker.ui.components.ExerciseDialog
import com.gym.tracker.ui.components.FeelSelector
import com.gym.tracker.ui.components.NumberStepper
import com.gym.tracker.ui.components.stepInt
import com.gym.tracker.ui.components.stepWeight
import com.gym.tracker.ui.theme.FeelColors
import java.time.LocalDate

private const val DAY_MS = 86_400_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    vm: WorkoutViewModel,
    onOpenSummary: (String) -> Unit,
    onGoHome: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val dialog by vm.dialog.collectAsStateWithLifecycle()
    val nav by vm.nav.collectAsStateWithLifecycle()
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(nav) {
        when (val n = nav) {
            is WorkoutNav.Summary -> { vm.onNavHandled(); onOpenSummary(n.sessionId) }
            WorkoutNav.Home -> { vm.onNavHandled(); onGoHome() }
            null -> Unit
        }
    }

    BackHandler { vm.requestExit() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { vm.requestExit() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = {
                    Column(modifier = Modifier.clickable { showDatePicker = true }) {
                        Text(state.templateName, style = MaterialTheme.typography.titleLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                Format.date(state.date),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.cards, key = { it.slot.id }) { card ->
                val draft = card.draft
                if (draft == null) {
                    Card(Modifier.fillMaxWidth()) {
                        Text(card.slot.name, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                    }
                } else if (draft.editing) {
                    OpenSlotCard(card, draft, vm)
                } else {
                    DoneSlotCard(card, draft, onClick = { vm.reopen(card.slot.id) })
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.finish() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("Завершить тренировку", style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = state.date.toEpochDay() * DAY_MS)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { vm.changeDate(LocalDate.ofEpochDay(it / DAY_MS)) }
                    showDatePicker = false
                }) { Text("ОК") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Отмена") } },
        ) {
            DatePicker(state = pickerState, title = { Text("Дата тренировки", modifier = Modifier.padding(16.dp)) })
        }
    }

    when (val d = dialog) {
        WorkoutDialog.ConfirmExit -> ConfirmDialog(
            title = "Завершить тренировку?",
            text = "Сохранённые упражнения уже записаны. Несохранённый ввод в открытых карточках пропадёт.",
            confirmLabel = "Завершить",
            onConfirm = { vm.finish() },
            onDismiss = { vm.dismissDialog() },
            dismissLabel = "Продолжить",
        )
        WorkoutDialog.DiscardEmpty -> ConfirmDialog(
            title = "Пустая тренировка",
            text = "Ни одно упражнение не отмечено «Готово». Удалить эту тренировку?",
            confirmLabel = "Удалить",
            onConfirm = { vm.discardEmpty() },
            onDismiss = { vm.dismissDialog() },
            dismissLabel = "Оставить",
            onDismissButton = { vm.keepEmpty() },
        )
        is WorkoutDialog.AddOption -> ExerciseDialog(
            title = "Новое упражнение",
            initialName = "",
            initialSets = 3,
            initialReps = 12,
            confirmLabel = "Добавить",
            error = null,
            onConfirm = { name, sets, reps -> vm.addOption(d.slotId, name, sets, reps) },
            onDismiss = { vm.dismissDialog() },
        )
        null -> Unit
    }
}

@Composable
private fun OpenSlotCard(card: SlotCardUi, draft: CardDraft, vm: WorkoutViewModel) {
    val slotId = card.slot.id
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    card.slot.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.requestAddOption(slotId) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Добавить упражнение в слот")
                }
            }

            card.options.forEach { exercise ->
                val selected = exercise.id == draft.selectedExerciseId
                val favorite = exercise.id == card.slot.favoriteExerciseId
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = selected, role = Role.RadioButton) { vm.selectExercise(slotId, exercise.id) },
                ) {
                    RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(end = 8.dp))
                    Text(
                        exercise.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { vm.setFavorite(slotId, exercise.id) }) {
                        if (favorite) {
                            Icon(Icons.Filled.Star, contentDescription = "Избранное", tint = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Outlined.StarBorder, contentDescription = "Сделать избранным")
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                lastTimeText(card.last),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
            Spacer(Modifier.height(8.dp))

            Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberStepper(
                    label = "Подходы",
                    value = draft.sets,
                    onValueChange = { vm.onSetsChange(slotId, it) },
                    onMinus = { vm.onSetsChange(slotId, stepInt(draft.sets, -1)) },
                    onPlus = { vm.onSetsChange(slotId, stepInt(draft.sets, 1)) },
                    decimal = false,
                )
                NumberStepper(
                    label = "Повторы",
                    value = draft.reps,
                    onValueChange = { vm.onRepsChange(slotId, it) },
                    onMinus = { vm.onRepsChange(slotId, stepInt(draft.reps, -1)) },
                    onPlus = { vm.onRepsChange(slotId, stepInt(draft.reps, 1)) },
                    decimal = false,
                )
                NumberStepper(
                    label = "Вес (кг)",
                    value = draft.weight,
                    onValueChange = { vm.onWeightChange(slotId, it) },
                    onMinus = { vm.onWeightChange(slotId, stepWeight(draft.weight, -2.5)) },
                    onPlus = { vm.onWeightChange(slotId, stepWeight(draft.weight, 2.5)) },
                    decimal = true,
                )
                FeelSelector(selected = draft.feel, onSelect = { vm.onFeel(slotId, it) })
                if (draft.error != null) {
                    Text(draft.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                Button(
                    onClick = { vm.done(slotId) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Готово ✓", style = MaterialTheme.typography.titleMedium) }
                if (draft.entryId != null) {
                    OutlinedButton(onClick = { vm.reopenCancel(slotId) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Отменить изменения")
                    }
                }
            }
        }
    }
}

@Composable
private fun DoneSlotCard(card: SlotCardUi, draft: CardDraft, onClick: () -> Unit) {
    val exerciseName = card.selectedExercise?.name.orEmpty()
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = "Выполнено", tint = FeelColors.easy)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(card.slot.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(doneSummary(exerciseName, draft), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private fun doneSummary(exerciseName: String, d: CardDraft) = buildAnnotatedString {
    val sets = d.sets.toIntOrNull() ?: 0
    val reps = d.reps.toIntOrNull() ?: 0
    val weight = Format.parseWeight(d.weight) ?: 0.0
    append("$exerciseName · ${Format.setsReps(sets, reps)} · ${Format.weight(weight)}")
    val feel: Feel? = d.feel
    if (feel != null) {
        append(" · ")
        withStyle(SpanStyle(color = FeelColors.of(feel), fontWeight = FontWeight.SemiBold)) { append(feel.label) }
    }
}

private fun lastTimeText(last: LastInfo?): String {
    if (last == null) return "В прошлый раз: …"
    val e = last.entry ?: return "Ещё не выполнялось"
    return "В прошлый раз (${Format.date(e.date)}): ${Format.entry(e.entry.sets, e.entry.reps, e.entry.weightKg, e.entry.feel)}"
}
