package com.gym.tracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.gym.tracker.data.db.Feel

/** Цвета самочувствия Классической темы: зелёный / нейтральный / оранжево-красный. */
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

/**
 * Тема приложения. Системная светлая/тёмная настройка игнорируется: вид задаёт только выбранная тема.
 * По умолчанию — Классическая (исходная тёмная тема).
 */
@Composable
fun GymTheme(themeId: GymThemeId = GymThemeId.DEFAULT, content: @Composable () -> Unit) {
    val spec = themeSpec(themeId)
    CompositionLocalProvider(LocalGymTokens provides spec.tokens) {
        MaterialTheme(
            colorScheme = spec.colorScheme,
            typography = spec.typography,
            shapes = spec.shapes,
            content = content,
        )
    }
}
