package com.gym.tracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.gym.tracker.data.db.Feel

private val GymColors = darkColorScheme(
    primary = Color(0xFFFFB74D),
    onPrimary = Color(0xFF2B1700),
    primaryContainer = Color(0xFF5A3A00),
    onPrimaryContainer = Color(0xFFFFDDB3),
    secondary = Color(0xFF90CAF9),
    onSecondary = Color(0xFF00243F),
    secondaryContainer = Color(0xFF2A3A4A),
    onSecondaryContainer = Color(0xFFD1E4FF),
    background = Color(0xFF121316),
    onBackground = Color(0xFFE4E2E6),
    surface = Color(0xFF121316),
    onSurface = Color(0xFFE4E2E6),
    surfaceVariant = Color(0xFF2A2C31),
    onSurfaceVariant = Color(0xFFC4C6CF),
    surfaceContainerLowest = Color(0xFF0D0E11),
    surfaceContainerLow = Color(0xFF1A1B1F),
    surfaceContainer = Color(0xFF1E2024),
    surfaceContainerHigh = Color(0xFF292A2F),
    surfaceContainerHighest = Color(0xFF34353A),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44464E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

/** Цвета самочувствия: зелёный / нейтральный / оранжево-красный. */
object FeelColors {
    val easy = Color(0xFF66BB6A)
    val normal = Color(0xFF90A4AE)
    val hard = Color(0xFFFF7043)

    fun of(feel: Feel): Color = when (feel) {
        Feel.EASY -> easy
        Feel.NORMAL -> normal
        Feel.HARD -> hard
    }
}

/** Только тёмная тема — системная настройка игнорируется. */
@Composable
fun GymTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GymColors, content = content)
}
