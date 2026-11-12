package com.gym.tracker

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.gym.tracker.ui.theme.GymThemeId
import com.gym.tracker.ui.theme.themeSpec
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Экран таймера во всех темах: огромные цифры ≥ 3:1, мелкий текст и подписи кнопок ≥ 4.5:1,
 * состояние «10 секунд» заметно периферийным зрением — меняет фон или крупную рамку и отличается по яркости.
 */
class TimerThemeTest {

    private fun contrast(fg: Color, bg: Color): Double {
        val a = fg.compositeOver(bg).luminance() + 0.05
        val b = bg.luminance() + 0.05
        return maxOf(a, b) / minOf(a, b)
    }

    @Test
    fun timerStatesReadableAndDistinct() {
        val failures = mutableListOf<String>()
        fun check(id: GymThemeId, what: String, c: Double, min: Double) {
            if (c < min) failures += "$id: $what = ${"%.2f".format(c)} < $min"
        }
        GymThemeId.entries.forEach { id ->
            val t = themeSpec(id).tokens
            val ts = t.timer
            val states = mapOf("работа" to ts.work, "10 секунд" to ts.warning, "отдых" to ts.rest)
            states.forEach { (name, s) ->
                val bg = s.background ?: t.background
                check(id, "$name: цифры", contrast(s.digits, bg), 3.0)
                check(id, "$name: текст", contrast(s.text, bg), 4.5)
                check(id, "$name: подпись кнопки", contrast(s.onButton, s.button.compositeOver(bg)), 4.5)
                check(id, "$name: кнопка на фоне", contrast(s.button, bg), 3.0)
            }
            val workBg = ts.work.background ?: t.background
            val warnBg = ts.warning.background ?: t.background
            val bgChanged = contrast(workBg, warnBg) >= 1.5
            val bigArea = ts.warning.frame != null || ts.warning.brackets != null
            if (!bgChanged && !bigArea) failures += "$id: предупреждение не меняет фон и не рисует рамку"
            // Не только оттенок: разная яркость фона или цифр.
            val lightness = maxOf(contrast(workBg, warnBg), contrast(ts.work.digits, ts.warning.digits))
            check(id, "предупреждение отличается по яркости", lightness, 1.5)
            // Отдых отличим от работы.
            val restBg = ts.rest.background ?: t.background
            if (ts.rest.digits == ts.work.digits && restBg == workBg) failures += "$id: отдых выглядит как работа"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
