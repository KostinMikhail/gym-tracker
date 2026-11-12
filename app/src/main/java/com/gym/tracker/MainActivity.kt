package com.gym.tracker

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gym.tracker.ui.GymApp
import com.gym.tracker.ui.theme.GymTheme
import com.gym.tracker.ui.theme.GymThemeId
import com.gym.tracker.ui.theme.themeSpec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = (application as GymApplication).themePreferences
        // Тема читается до первого кадра (маленький файл настроек) — без мигания темы по умолчанию.
        val initialTheme = runBlocking { prefs.theme.first() }
        applySystemBars(initialTheme)
        super.onCreate(savedInstanceState)
        setContent {
            val themeId by prefs.theme.collectAsStateWithLifecycle(initialValue = initialTheme)
            val scope = rememberCoroutineScope()
            LaunchedEffect(themeId) { applySystemBars(themeId) }
            GymTheme(themeId) {
                GymApp(
                    currentTheme = themeId,
                    onThemeSelected = { id -> scope.launch { prefs.setTheme(id) } },
                )
            }
        }
    }

    /** Значки системных панелей: светлая тема → тёмные значки, тёмная → светлые. Фон окна — цвет темы. */
    private fun applySystemBars(themeId: GymThemeId) {
        val tokens = themeSpec(themeId).tokens
        val style = if (tokens.isLight) {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        } else {
            SystemBarStyle.dark(Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        window.setBackgroundDrawable(tokens.background.toArgb().toDrawable())
    }
}
