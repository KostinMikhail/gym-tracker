package com.gym.tracker.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gym.tracker.R
import com.gym.tracker.data.db.Feel
import com.gym.tracker.ui.theme.GymTheme
import com.gym.tracker.ui.theme.GymThemeId
import com.gym.tracker.ui.theme.GymThemePreviewProvider
import com.gym.tracker.ui.theme.themeSpec

/**
 * Выбор темы оформления. Нажатие сразу применяет тему ко всему приложению;
 * лист остаётся открытым (закрывается свайпом, «Назад» или нажатием вне его).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemePickerSheet(current: GymThemeId, onSelect: (GymThemeId) -> Unit, onDismiss: () -> Unit) {
    // Открываем лист сразу целиком: все 7 тем видны без прокрутки.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        ThemePickerContent(current, onSelect, Modifier.padding(bottom = 24.dp))
    }
}

@Composable
fun ThemePickerContent(current: GymThemeId, onSelect: (GymThemeId) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        Text(
            stringResource(R.string.theme_picker_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        GymThemeId.entries.forEach { id ->
            ThemeRow(id, selected = id == current, onClick = { onSelect(id) })
        }
    }
}

@Composable
private fun ThemeRow(id: GymThemeId, selected: Boolean, onClick: () -> Unit) {
    val spec = themeSpec(id)
    val name = stringResource(id.titleRes)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            name,
            // Название — шрифтом самой темы.
            style = TextStyle(fontFamily = spec.tokens.displayFont ?: MaterialTheme.typography.titleMedium.fontFamily, fontSize = 17.sp),
            maxLines = 1,
            softWrap = false,
            // Широкие шрифты (Unbounded, Rubik Mono One) ужимаются, чтобы название помещалось целиком.
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 17.sp),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        ThemePreviewChip(id)
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (selected) Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.theme_selected), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Мини-образец темы её же токенами: фон, карточка с выбранным «Норм» и акцентная точка. */
@Composable
private fun ThemePreviewChip(id: GymThemeId) {
    val spec = themeSpec(id)
    val t = spec.tokens
    val normal = t.feel(Feel.NORMAL)
    val cardShape = t.card.shape ?: RoundedCornerShape(6.dp)
    Box(
        Modifier
            .size(width = 104.dp, height = 48.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(t.background)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(6.dp))
            .padding(6.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(t.card.active)
                .then(t.card.border?.let { Modifier.border(BorderStroke(1.dp, it.brush), cardShape) } ?: Modifier)
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                Modifier
                    .clip(t.feelShape)
                    .background(normal.selectedFill)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    stringResource(R.string.theme_preview_feel),
                    style = TextStyle(fontFamily = spec.typography.labelSmall.fontFamily, fontSize = 11.sp),
                    color = normal.selectedContent,
                    maxLines = 1,
                )
            }
            Box(Modifier.size(12.dp).clip(CircleShape).background((t.card.scheme ?: spec.colorScheme).primary))
        }
    }
}

@Preview(name = "Выбор темы — темы", widthDp = 360)
@Composable
private fun ThemePickerPreview(@PreviewParameter(GymThemePreviewProvider::class) themeId: GymThemeId) {
    GymTheme(themeId) {
        Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
            ThemePickerContent(current = themeId, onSelect = {})
        }
    }
}

