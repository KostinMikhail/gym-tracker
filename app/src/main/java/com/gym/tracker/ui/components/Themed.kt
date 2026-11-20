package com.gym.tracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gym.tracker.R
import com.gym.tracker.ui.theme.CardRole
import com.gym.tracker.ui.theme.Dither
import com.gym.tracker.ui.theme.DitherSwatch
import com.gym.tracker.ui.theme.Glow
import com.gym.tracker.ui.theme.LocalGymTokens
import com.gym.tracker.ui.theme.LocalOnCard
import com.gym.tracker.ui.theme.ThemeIcon
import com.gym.tracker.ui.theme.cornerBrackets
import com.gym.tracker.ui.theme.dashedBorder
import com.gym.tracker.ui.theme.hardShadow
import com.gym.tracker.ui.theme.leftEdge
import com.gym.tracker.ui.theme.neonGlow
import com.gym.tracker.ui.theme.outerOutline

/**
 * Карточка в стиле текущей темы. [role] выбирает фон (в Классической — ровно те цвета, что были раньше),
 * [emphasized] — акцентная рамка (рекомендуемая тренировка).
 * Внутри карточки действует схема цветов карточки (если тема её задаёт) и LocalOnCard = true.
 */
@Composable
fun GymCard(
    modifier: Modifier = Modifier,
    role: CardRole = CardRole.LIST,
    emphasized: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalGymTokens.current
    val c = t.card
    val container = when (role) {
        CardRole.LIST -> c.list
        CardRole.ACTIVE -> c.active
        CardRole.DONE -> c.done
        CardRole.HIGHLIGHT -> c.highlight
    }
    val shape = c.shape ?: CardDefaults.shape
    val dashedDone = role == CardRole.DONE && c.doneDashedBorder != null
    val border = when {
        emphasized -> c.highlightBorder
        dashedDone -> null
        else -> c.border
    }
    val scheme = c.scheme ?: MaterialTheme.colorScheme
    val contentColor = c.content ?: contentColorFor(container)
    val decorated = modifier
        .hardShadow(c.hardShadow, shape)
        .neonGlow(c.glow, shape)
        .outerOutline(c.outerOutline, shape)
        .then(if (dashedDone) Modifier.dashedBorder(c.doneDashedBorder, 2.dp, shape) else Modifier)
        .then(if (role == CardRole.ACTIVE) Modifier.leftEdge(c.activeLeftEdge, shape) else Modifier)
        .cornerBrackets(c.cornerBrackets)
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        CompositionLocalProvider(LocalOnCard provides true) {
            Card(
                modifier = decorated,
                shape = shape,
                border = border,
                colors = CardDefaults.cardColors(containerColor = container, contentColor = contentColor),
                content = content,
            )
        }
    }
}

/** Кнопка «Готово ✓» в стиле темы (в Классической — обычная Material-кнопка). */
@Composable
fun GymDoneButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val style = LocalGymTokens.current.doneButton
    if (style == null) {
        Button(onClick = onClick, modifier = modifier) { Text(text, style = MaterialTheme.typography.titleMedium) }
        return
    }
    Button(
        onClick = onClick,
        shape = style.shape,
        border = style.border,
        colors = ButtonDefaults.buttonColors(containerColor = style.container, contentColor = style.content),
        modifier = modifier
            .hardShadow(style.hardShadow, style.shape)
            .neonGlow(style.glow, style.shape, strokeWidth = 2.dp),
    ) {
        Text(
            if (style.uppercase) text.uppercase() else text,
            style = style.textStyle ?: MaterialTheme.typography.titleMedium,
        )
    }
}

/** Кнопка − / + у числового поля. */
@Composable
fun StepperButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    val s = LocalGymTokens.current.stepper
    if (s == null) {
        FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            Icon(icon, contentDescription = contentDescription)
        }
        return
    }
    Surface(
        onClick = onClick,
        shape = s.shape,
        color = s.container,
        contentColor = s.content,
        border = s.border?.let { BorderStroke(if (s.shape == RectangleShape) 2.dp else 1.5.dp, it) },
        modifier = Modifier.size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = contentDescription) }
    }
}

/** Квадратная кнопка-значок «Сменить тему оформления» (44 dp). */
@Composable
fun ThemeButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val b = LocalGymTokens.current.themeButton
    val description = stringResource(R.string.theme_button_description)
    Surface(
        onClick = onClick,
        shape = b.shape,
        color = b.container,
        contentColor = b.iconColor,
        border = b.border?.let { BorderStroke(1.5.dp, it) },
        modifier = modifier
            .size(44.dp)
            .neonGlow(b.glow, b.shape)
            .semantics { this.contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            when (b.icon) {
                ThemeIcon.PALETTE -> Icon(Icons.Outlined.Palette, contentDescription = null)
                ThemeIcon.BOLT -> Icon(Icons.Filled.Bolt, contentDescription = null)
                ThemeIcon.DITHER -> DitherSwatch(Dither.CHECKER, b.iconColor, size = 18.dp)
                ThemeIcon.RED_SQUARE -> Box(
                    Modifier
                        .size(12.dp)
                        .neonGlow(Glow(b.iconColor, 6.dp), RectangleShape)
                        .drawBehind { drawRect(b.iconColor) },
                )
            }
        }
    }
}

/** Цвет текста самочувствия с учётом того, на карточке он или на странице. */
@Composable
fun feelTextColor(feel: com.gym.tracker.data.db.Feel): androidx.compose.ui.graphics.Color {
    val t = LocalGymTokens.current
    val page = t.feelOnPage
    return if (!LocalOnCard.current && page != null) page.getValue(feel) else t.feel(feel).content
}
