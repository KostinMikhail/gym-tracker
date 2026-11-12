package com.gym.tracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gym.tracker.R
import com.gym.tracker.data.db.Feel
import com.gym.tracker.domain.Format
import com.gym.tracker.ui.theme.Dither
import com.gym.tracker.ui.theme.DitherSwatch
import com.gym.tracker.ui.theme.LocalGymTokens
import com.gym.tracker.ui.theme.LocalOnCard
import com.gym.tracker.ui.theme.WeightDecor
import com.gym.tracker.ui.theme.dashedBorder
import com.gym.tracker.ui.theme.neonGlow
import com.gym.tracker.ui.theme.GymTheme

/**
 * Число с кнопками − / + и вводом с цифровой клавиатуры.
 * [labelAbove] — подпись отдельной строкой над кнопками (для узких диалогов, где сбоку ей не хватает места).
 */
@Composable
fun NumberStepper(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    decimal: Boolean,
    modifier: Modifier = Modifier,
    labelAbove: Boolean = false,
    emphasized: Boolean = false,
) {
    if (labelAbove) {
        Column(modifier = modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StepperControls(label, value, onValueChange, onMinus, onPlus, decimal, emphasized)
            }
        }
    } else {
        Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            StepperControls(label, value, onValueChange, onMinus, onPlus, decimal, emphasized)
        }
    }
}

@Composable
private fun StepperControls(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    decimal: Boolean,
    emphasized: Boolean,
) {
    // Вызывается внутри Row: элементы размещаются в ряд родителя.
    val weight = LocalGymTokens.current.weight
    // Главное поле (вес) в темах может иметь свой шрифт/цвет и украшения вместо рамки.
    val themed = emphasized && weight.textStyle != null
    val decorated = themed && weight.decor != WeightDecor.NONE
    var textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center)
    if (themed) textStyle = weight.textStyle.copy(textAlign = TextAlign.Center, color = weight.color ?: Color.Unspecified)
    StepperButton(Icons.Filled.Remove, "Уменьшить: $label", onMinus)
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            val allowed = if (decimal) input.filter { it.isDigit() || it == ',' || it == '.' } else input.filter { it.isDigit() }
            onValueChange(allowed.take(6))
        },
        singleLine = true,
        textStyle = textStyle,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        colors = if (decorated) {
            OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent)
        } else {
            OutlinedTextFieldDefaults.colors()
        },
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .width(96.dp)
            .then(if (decorated) Modifier.weightDecor(weight.decor, weight.decorColor) else Modifier),
    )
    StepperButton(Icons.Filled.Add, "Увеличить: $label", onPlus)
}

/** Пунктир сверху и снизу значения веса или подчёркивание. */
private fun Modifier.weightDecor(decor: WeightDecor, color: Color): Modifier = drawBehind {
    val y1 = 4.dp.toPx()
    val y2 = size.height - 4.dp.toPx()
    when (decor) {
        WeightDecor.DASHED_LINES -> {
            val effect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
            val w = 2.dp.toPx()
            drawLine(color, Offset(0f, y1), Offset(size.width, y1), strokeWidth = w, pathEffect = effect)
            drawLine(color, Offset(0f, y2), Offset(size.width, y2), strokeWidth = w, pathEffect = effect)
        }
        WeightDecor.UNDERLINE -> drawRect(color, Offset(8.dp.toPx(), y2 - 2.dp.toPx()), Size(size.width - 16.dp.toPx(), 2.dp.toPx()))
        WeightDecor.NONE -> Unit
    }
}

/** Изменение целого значения кнопкой (минимум 1). */
fun stepInt(text: String, delta: Int): String {
    val v = text.trim().toIntOrNull() ?: 0
    return (v + delta).coerceAtLeast(1).toString()
}

/** Изменение веса кнопкой (шаг 2,5, минимум 0). */
fun stepWeight(text: String, delta: Double): String {
    val v = Format.parseWeight(text) ?: 0.0
    val next = (v + delta).coerceAtLeast(0.0)
    return if (next == 0.0) "" else Format.number(next)
}

/** Три крупные кнопки самочувствия одинаковой ширины; подпись всегда в одну строку. */
@Composable
fun FeelSelector(selected: Feel?, onSelect: (Feel) -> Unit, modifier: Modifier = Modifier) {
    // Стандартный отступ кнопки (24 dp по бокам) не оставлял места для «Тяжело» на узком экране.
    val contentPadding = PaddingValues(horizontal = 4.dp)
    val t = LocalGymTokens.current
    // Вне карточки (в диалогах) — цвета для фона страницы, если тема их задаёт.
    val pageColors = t.feelOnPage.takeIf { !LocalOnCard.current }
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Feel.entries.forEach { feel ->
            val f = t.feel(feel).let { s -> pageColors?.get(feel)?.let { c -> s.copy(content = c, border = c) } ?: s }
            val isSelected = feel == selected
            if (isSelected) {
                Button(
                    onClick = { onSelect(feel) },
                    shape = t.feelShape,
                    colors = ButtonDefaults.buttonColors(containerColor = f.selectedFill, contentColor = f.selectedContent),
                    contentPadding = contentPadding,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .neonGlow(f.selectedGlow, t.feelShape),
                ) { FeelLabel(feel.label, bold = true, swatch = f.swatch, ink = f.selectedContent) }
            } else {
                OutlinedButton(
                    onClick = { onSelect(feel) },
                    shape = t.feelShape,
                    // Монохромные темы (с образцами дизеринга) — рамка 2 dp.
                    border = BorderStroke(if (f.swatch != null) 2.dp else 1.dp, f.border),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = f.content),
                    contentPadding = contentPadding,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                ) { FeelLabel(feel.label, bold = false, swatch = f.swatch, ink = f.content) }
            }
        }
    }
}

/** Подпись кнопки самочувствия: одна строка, по центру; при нехватке места шрифт уменьшается, а не обрезается. */
@Composable
private fun FeelLabel(text: String, bold: Boolean, swatch: Dither? = null, ink: Color = Color.Unspecified) {
    val style = MaterialTheme.typography.labelLarge
    if (swatch == null) {
        Text(
            text,
            style = style,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = style.fontSize),
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    // Монохромные темы: образец дизеринга 12 dp перед подписью отличает варианты без цвета.
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
        DitherSwatch(swatch, ink)
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = style,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = style.fontSize),
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/**
 * Компактная плашка «3 × 12» с карандашом. Нажатие раскрывает/сворачивает степперы подходов и повторов.
 * [highlighted] — значения отличаются от настроек упражнения (подсвечивается акцентом).
 */
@Composable
fun SetsRepsPill(
    sets: String,
    reps: String,
    highlighted: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val t = LocalGymTokens.current
    val value = stringResource(R.string.sets_reps_value, sets.trim().ifEmpty { "–" }, reps.trim().ifEmpty { "–" })
    val action = stringResource(if (expanded) R.string.sets_reps_collapse else R.string.sets_reps_expand, value)
    val description = if (highlighted) "$action, ${stringResource(R.string.sets_reps_non_default)}" else action
    Surface(
        onClick = onClick,
        shape = t.chipShape,
        color = if (highlighted) colors.primaryContainer else colors.surfaceContainerHighest,
        contentColor = if (highlighted) colors.onPrimaryContainer else colors.onSurfaceVariant,
        border = if (highlighted && !t.pillDashed) BorderStroke(1.dp, colors.primary) else null,
        modifier = modifier
            .then(if (t.pillDashed) Modifier.dashedBorder(colors.outline, 1.5.dp, t.chipShape, dash = 4.dp) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Spacer(Modifier.width(6.dp))
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.Edit,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Preview(name = "Самочувствие — 360 dp, шрифт ×1.3", widthDp = 360, fontScale = 1.3f, showBackground = true, backgroundColor = 0xFF121316)
@Composable
private fun FeelSelectorPreview() {
    GymTheme {
        Column(Modifier.padding(horizontal = 36.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FeelSelector(selected = null, onSelect = {})
            FeelSelector(selected = Feel.HARD, onSelect = {})
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Отмена",
    onDismissButton: () -> Unit = onDismiss,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismissButton) { Text(dismissLabel) } },
    )
}

/** Диалог названия упражнения + подходы/повторы по умолчанию (создание и редактирование). */
@Composable
fun ExerciseDialog(
    title: String,
    initialName: String,
    initialSets: Int,
    initialReps: Int,
    confirmLabel: String,
    error: String?,
    onConfirm: (name: String, sets: Int, reps: Int) -> Unit,
    onDismiss: () -> Unit,
    extraButton: (@Composable () -> Unit)? = null,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var sets by rememberSaveable { mutableStateOf(initialSets.toString()) }
    var reps by rememberSaveable { mutableStateOf(initialReps.toString()) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; localError = null },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                NumberStepper("Подходы", sets, { sets = it }, { sets = stepInt(sets, -1) }, { sets = stepInt(sets, 1) }, decimal = false, labelAbove = true)
                NumberStepper("Повторы", reps, { reps = it }, { reps = stepInt(reps, -1) }, { reps = stepInt(reps, 1) }, decimal = false, labelAbove = true)
                val shownError = localError ?: error
                if (shownError != null) {
                    Text(shownError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                if (extraButton != null) {
                    Spacer(Modifier.height(4.dp))
                    extraButton()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = sets.toIntOrNull()
                val r = reps.toIntOrNull()
                when {
                    name.isBlank() -> localError = "Введите название"
                    s == null || s < 1 || r == null || r < 1 -> localError = "Подходы и повторы — не меньше 1"
                    else -> onConfirm(name, s, r)
                }
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Подпись самочувствия цветом. */
@Composable
fun FeelText(feel: Feel, modifier: Modifier = Modifier, singleLine: Boolean = false) {
    val color = feelTextColor(feel)
    val swatch = LocalGymTokens.current.feel(feel).swatch
    if (swatch == null) {
        ShrinkText(feel.label, singleLine, color = color, fontWeight = FontWeight.SemiBold, modifier = modifier)
    } else {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            DitherSwatch(swatch, color, size = 10.dp)
            Spacer(Modifier.width(4.dp))
            ShrinkText(feel.label, singleLine, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Текст ячейки таблицы: при [singleLine] — в одну строку, а если не помещается (широкие моноширинные
 * шрифты тем), шрифт уменьшается вместо переноса посреди слова. Когда текст помещается — выглядит как обычно.
 */
@Composable
fun ShrinkText(
    text: String,
    singleLine: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    style: androidx.compose.ui.text.TextStyle = androidx.compose.material3.LocalTextStyle.current,
) {
    if (!singleLine) {
        Text(text, color = color, fontWeight = fontWeight, style = style, modifier = modifier)
    } else {
        Text(
            text,
            color = color,
            fontWeight = fontWeight,
            style = style,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = style.fontSize),
            modifier = modifier,
        )
    }
}

/** Пустое состояние списка. */
@Composable
fun EmptyText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
    )
}
