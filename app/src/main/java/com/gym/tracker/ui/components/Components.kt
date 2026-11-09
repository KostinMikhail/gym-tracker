package com.gym.tracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gym.tracker.data.db.Feel
import com.gym.tracker.domain.Format
import com.gym.tracker.ui.theme.FeelColors

/** Число с кнопками − / + и вводом с цифровой клавиатуры. */
@Composable
fun NumberStepper(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    decimal: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = onMinus, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Remove, contentDescription = "Уменьшить: $label")
        }
        OutlinedTextField(
            value = value,
            onValueChange = { input ->
                val allowed = if (decimal) input.filter { it.isDigit() || it == ',' || it == '.' } else input.filter { it.isDigit() }
                onValueChange(allowed.take(6))
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .width(96.dp),
        )
        FilledTonalIconButton(onClick = onPlus, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Add, contentDescription = "Увеличить: $label")
        }
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

/** Три крупные кнопки самочувствия. */
@Composable
fun FeelSelector(selected: Feel?, onSelect: (Feel) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Feel.entries.forEach { feel ->
            val color = FeelColors.of(feel)
            val isSelected = feel == selected
            if (isSelected) {
                Button(
                    onClick = { onSelect(feel) },
                    colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color(0xFF111111)),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                ) { Text(feel.label, fontWeight = FontWeight.Bold) }
            } else {
                OutlinedButton(
                    onClick = { onSelect(feel) },
                    border = BorderStroke(1.dp, color),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                ) { Text(feel.label) }
            }
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
                NumberStepper("Подходы", sets, { sets = it }, { sets = stepInt(sets, -1) }, { sets = stepInt(sets, 1) }, decimal = false)
                NumberStepper("Повторы", reps, { reps = it }, { reps = stepInt(reps, -1) }, { reps = stepInt(reps, 1) }, decimal = false)
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
fun FeelText(feel: Feel, modifier: Modifier = Modifier) {
    Text(feel.label, color = FeelColors.of(feel), fontWeight = FontWeight.SemiBold, modifier = modifier)
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
