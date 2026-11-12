package com.gym.tracker.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gym.tracker.R
import com.gym.tracker.data.db.ExerciseEntity
import com.gym.tracker.data.db.Feel
import com.gym.tracker.data.db.TemplateSlotEntity
import com.gym.tracker.domain.Format
import com.gym.tracker.domain.isDefaultSetsReps
import com.gym.tracker.ui.components.ConfirmDialog
import com.gym.tracker.ui.components.ExerciseDialog
import com.gym.tracker.ui.components.FeelSelector
import com.gym.tracker.ui.components.GymCard
import com.gym.tracker.ui.components.GymDoneButton
import com.gym.tracker.ui.components.NumberStepper
import com.gym.tracker.ui.components.SetsRepsPill
import com.gym.tracker.ui.components.stepInt
import com.gym.tracker.ui.components.stepWeight
import com.gym.tracker.ui.theme.CardRole
import com.gym.tracker.ui.theme.GymTheme
import com.gym.tracker.ui.theme.GymThemeId
import com.gym.tracker.ui.theme.GymThemePreviewProvider
import com.gym.tracker.ui.theme.HeaderDecor
import com.gym.tracker.ui.theme.LocalGymTokens
import com.gym.tracker.ui.theme.ScreenTitle
import com.gym.tracker.ui.theme.neonGlow
import java.time.LocalDate

private const val DAY_MS = 86_400_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    vm: WorkoutViewModel,
    /** Отступы внешнего Scaffold (нижняя панель навигации) — чтобы кнопка «Завершить» не пряталась под ней. */
    outerPadding: PaddingValues,
    /** Показать/скрыть нижнюю панель навигации (скрывается при прокрутке вниз). */
    onBottomBarVisible: (Boolean) -> Unit,
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

    // Шапка уезжает при прокрутке вниз и сразу возвращается при прокрутке вверх («enter always»).
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val setBottomBar by rememberUpdatedState(onBottomBarVisible)
    val bottomBarConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -1f) setBottomBar(false) else if (available.y > 1f) setBottomBar(true)
                return Offset.Zero
            }
        }
    }
    val progress = stringResource(R.string.workout_progress, state.templateName, state.confirmedCount, state.cards.size)

    Scaffold(
        modifier = Modifier
            .nestedScroll(bottomBarConnection)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            Column {
                TopAppBar(
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                    navigationIcon = {
                        IconButton(onClick = { vm.requestExit() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                    title = {
                        Column(modifier = Modifier.clickable { showDatePicker = true }) {
                            ScreenTitle(state.templateName, fallback = MaterialTheme.typography.titleLarge, maxLines = 1)
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
                // Закреплённая полоса: видна всегда, даже когда шапка уехала.
                if (!state.loading) {
                    Text(
                        progress,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                HeaderDecor(hudText = stringResource(R.string.hud_strip, "${Format.date(state.date)} · ${state.confirmedCount} / ${state.cards.size}"))
            }
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            // imePadding: при edge-to-edge окно не сжимается под клавиатуру — поле ввода не должно прятаться под ней.
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .imePadding(),
            // Снизу — место под нижнюю панель навигации, чтобы «Завершить тренировку» всегда можно было нажать.
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp + outerPadding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.cards, key = { it.slot.id }) { card ->
                val draft = card.draft
                if (draft == null) {
                    GymCard(Modifier.fillMaxWidth(), role = CardRole.LIST) {
                        Text(card.slot.name, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                    }
                } else if (draft.editing) {
                    OpenSlotCard(card, draft, vm.cardActions)
                } else {
                    DoneSlotCard(card, draft, onClick = { vm.reopen(card.slot.id) })
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.finish() },
                    shape = LocalGymTokens.current.buttonShape ?: ButtonDefaults.shape,
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

/** Действия карточки слота (лямбды вместо ViewModel — чтобы карточку можно было показать в @Preview). */
class SlotCardActions(
    val onAddOption: (slotId: String) -> Unit,
    val onSelect: (slotId: String, exerciseId: String) -> Unit,
    val onFavorite: (slotId: String, exerciseId: String) -> Unit,
    val onSetsChange: (slotId: String, value: String) -> Unit,
    val onRepsChange: (slotId: String, value: String) -> Unit,
    val onWeightChange: (slotId: String, value: String) -> Unit,
    val onFeel: (slotId: String, feel: Feel) -> Unit,
    val onDone: (slotId: String) -> Unit,
    val onCancelEdit: (slotId: String) -> Unit,
) {
    companion object {
        val Noop = SlotCardActions({}, { _, _ -> }, { _, _ -> }, { _, _ -> }, { _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {})
    }
}

@Composable
private fun OpenSlotCard(card: SlotCardUi, draft: CardDraft, actions: SlotCardActions, initiallyExpanded: Boolean = false) {
    val slotId = card.slot.id
    val t = LocalGymTokens.current
    GymCard(modifier = Modifier.fillMaxWidth(), role = CardRole.ACTIVE) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (t.groupUppercase) card.slot.name.uppercase() else card.slot.name,
                    style = t.groupStyle ?: MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { actions.onAddOption(slotId) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Добавить упражнение в слот")
                }
            }

            card.options.forEach { exercise ->
                val selected = exercise.id == draft.selectedExerciseId
                val favorite = exercise.id == card.slot.favoriteExerciseId
                // В темах выбранный вариант — залитый «чип»; в Классической — как раньше, только радиокнопка.
                val sel = t.selectedOption?.takeIf { selected }
                val rowModifier = if (sel != null) {
                    Modifier
                        .padding(vertical = 2.dp)
                        .neonGlow(sel.glow, t.chipShape)
                        .background(sel.fill, t.chipShape)
                        .then(if (sel.border != null) Modifier.border(BorderStroke(1.5.dp, sel.border), t.chipShape) else Modifier)
                } else {
                    Modifier
                }
                CompositionLocalProvider(LocalContentColor provides (sel?.content ?: LocalContentColor.current)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(rowModifier)
                            .selectable(selected = selected, role = Role.RadioButton) { actions.onSelect(slotId, exercise.id) },
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = null,
                            colors = if (sel != null) RadioButtonDefaults.colors(selectedColor = sel.content) else RadioButtonDefaults.colors(),
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(
                            exercise.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { actions.onFavorite(slotId, exercise.id) }) {
                            if (favorite) {
                                Icon(
                                    Icons.Filled.Star,
                                    contentDescription = "Избранное",
                                    tint = sel?.content ?: MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Icon(Icons.Outlined.StarBorder, contentDescription = "Сделать избранным")
                            }
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

            // Подходы/повторы свёрнуты при каждом открытии карточки (новой или повторно открытой выполненной).
            var setsRepsExpanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
            val exercise = card.selectedExercise
            CardInputs(
                draft = draft,
                setsRepsIsDefault = exercise == null ||
                    isDefaultSetsReps(draft.sets, draft.reps, exercise.defaultSets, exercise.defaultReps),
                setsRepsExpanded = setsRepsExpanded,
                onToggleSetsReps = { setsRepsExpanded = !setsRepsExpanded },
                onSetsChange = { actions.onSetsChange(slotId, it) },
                onRepsChange = { actions.onRepsChange(slotId, it) },
                onWeightChange = { actions.onWeightChange(slotId, it) },
                onFeel = { actions.onFeel(slotId, it) },
                onDone = {
                    // Сворачиваем, если подходы/повторы корректны; иначе оставляем открытыми, чтобы было видно ошибку.
                    if ((draft.sets.trim().toIntOrNull() ?: 0) >= 1 && (draft.reps.trim().toIntOrNull() ?: 0) >= 1) {
                        setsRepsExpanded = false
                    }
                    actions.onDone(slotId)
                },
                onCancelEdit = { actions.onCancelEdit(slotId) },
            )
        }
    }
}

/** Поля ввода открытой карточки (без ViewModel — чтобы работали @Preview). */
@Composable
private fun CardInputs(
    draft: CardDraft,
    setsRepsIsDefault: Boolean,
    setsRepsExpanded: Boolean,
    onToggleSetsReps: () -> Unit,
    onSetsChange: (String) -> Unit,
    onRepsChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onFeel: (Feel) -> Unit,
    onDone: () -> Unit,
    onCancelEdit: () -> Unit,
) {
    Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.sets_reps_label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            SetsRepsPill(
                sets = draft.sets,
                reps = draft.reps,
                highlighted = !setsRepsIsDefault,
                expanded = setsRepsExpanded,
                onClick = onToggleSetsReps,
            )
        }
        AnimatedVisibility(visible = setsRepsExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberStepper(
                    label = "Подходы",
                    value = draft.sets,
                    onValueChange = onSetsChange,
                    onMinus = { onSetsChange(stepInt(draft.sets, -1)) },
                    onPlus = { onSetsChange(stepInt(draft.sets, 1)) },
                    decimal = false,
                )
                NumberStepper(
                    label = "Повторы",
                    value = draft.reps,
                    onValueChange = onRepsChange,
                    onMinus = { onRepsChange(stepInt(draft.reps, -1)) },
                    onPlus = { onRepsChange(stepInt(draft.reps, 1)) },
                    decimal = false,
                )
            }
        }
        NumberStepper(
            label = "Вес (кг)",
            value = draft.weight,
            onValueChange = onWeightChange,
            onMinus = { onWeightChange(stepWeight(draft.weight, -2.5)) },
            onPlus = { onWeightChange(stepWeight(draft.weight, 2.5)) },
            decimal = true,
            emphasized = true,
        )
        FeelSelector(selected = draft.feel, onSelect = onFeel)
        if (draft.error != null) {
            Text(draft.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        GymDoneButton("Готово ✓", onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp))
        if (draft.entryId != null) {
            OutlinedButton(
                onClick = onCancelEdit,
                shape = LocalGymTokens.current.buttonShape ?: ButtonDefaults.outlinedShape,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Отменить изменения")
            }
        }
    }
}

@Composable
private fun DoneSlotCard(card: SlotCardUi, draft: CardDraft, onClick: () -> Unit) {
    val exerciseName = card.selectedExercise?.name.orEmpty()
    val t = LocalGymTokens.current
    GymCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), role = CardRole.DONE) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = "Выполнено", tint = t.doneIcon)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(card.slot.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val feelColor = draft.feel?.let { t.feel(it).content } ?: Color.Unspecified
                Text(doneSummary(exerciseName, draft, feelColor), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private fun doneSummary(exerciseName: String, d: CardDraft, feelColor: Color) = buildAnnotatedString {
    val sets = d.sets.toIntOrNull() ?: 0
    val reps = d.reps.toIntOrNull() ?: 0
    val weight = Format.parseWeight(d.weight) ?: 0.0
    append("$exerciseName · ${Format.setsReps(sets, reps)} · ${Format.weight(weight)}")
    val feel: Feel? = d.feel
    if (feel != null) {
        append(" · ")
        withStyle(SpanStyle(color = feelColor, fontWeight = FontWeight.SemiBold)) { append(feel.label) }
    }
}

private fun lastTimeText(last: LastInfo?): String {
    if (last == null) return "В прошлый раз: …"
    val e = last.entry ?: return "Ещё не выполнялось"
    return "В прошлый раз (${Format.date(e.date)}): ${Format.entry(e.entry.sets, e.entry.reps, e.entry.weightKg, e.entry.feel)}"
}

// ---- Preview карточки (360 dp — узкий экран) ----

private val previewDraft = CardDraft(
    selectedExerciseId = "ex",
    sets = "3",
    reps = "12",
    weight = "45",
    feel = null,
    editing = true,
    entryId = null,
)

private val previewCard = SlotCardUi(
    slot = TemplateSlotEntity("slot", "t", "Грудь", 0, "ex", 0, 0),
    options = listOf(
        ExerciseEntity("ex", "Жим лёжа в тренажёре", 3, 12, 0, 0),
        ExerciseEntity("ex2", "Жим гантелей лёжа", 3, 12, 0, 0),
    ),
    draft = previewDraft,
    last = LastInfo(null),
)

@Composable
private fun PreviewCard(draft: CardDraft, isDefault: Boolean, expanded: Boolean) {
    GymTheme {
        GymCard(modifier = Modifier.fillMaxWidth().padding(12.dp), role = CardRole.ACTIVE) {
            Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 16.dp)) {
                CardInputs(draft, isDefault, expanded, {}, {}, {}, {}, {}, {}, {})
            }
        }
    }
}

@Preview(name = "Свёрнуто, 3 × 12 (обычно)", widthDp = 360, showBackground = true, backgroundColor = 0xFF121316)
@Composable
private fun CardCollapsedDefaultPreview() = PreviewCard(previewDraft, isDefault = true, expanded = false)

@Preview(name = "Свёрнуто, 3 × 40 (нестандартно)", widthDp = 360, showBackground = true, backgroundColor = 0xFF121316)
@Composable
private fun CardCollapsedNonDefaultPreview() =
    PreviewCard(previewDraft.copy(reps = "40", weight = "", feel = Feel.NORMAL), isDefault = false, expanded = false)

@Preview(name = "Раскрыто", widthDp = 360, showBackground = true, backgroundColor = 0xFF121316)
@Composable
private fun CardExpandedPreview() = PreviewCard(previewDraft.copy(feel = Feel.HARD), isDefault = true, expanded = true)

@Preview(name = "Свёрнуто, 360 dp, шрифт ×1.3", widthDp = 360, fontScale = 1.3f, showBackground = true, backgroundColor = 0xFF121316)
@Composable
private fun CardLargeFontPreview() = PreviewCard(previewDraft.copy(feel = Feel.HARD), isDefault = true, expanded = false)

/** Карточки во всех 7 темах: предстоящая (свёрнута), раскрытая и выполненная. */
@Preview(name = "Карточки — темы", widthDp = 360, heightDp = 1500)
@Composable
private fun CardsThemedPreview(@PreviewParameter(GymThemePreviewProvider::class) themeId: GymThemeId) {
    GymTheme(themeId) {
        com.gym.tracker.ui.theme.GymBackground {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OpenSlotCard(previewCard, previewDraft, SlotCardActions.Noop)
                OpenSlotCard(previewCard, previewDraft.copy(feel = Feel.NORMAL, reps = "40"), SlotCardActions.Noop, initiallyExpanded = true)
                DoneSlotCard(previewCard, previewDraft.copy(feel = Feel.HARD, editing = false, entryId = "e"), onClick = {})
            }
        }
    }
}
