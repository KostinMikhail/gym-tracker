package com.gym.tracker

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gym.tracker.ui.GymApp
import com.gym.tracker.ui.theme.GymTheme
import com.gym.tracker.ui.theme.GymThemeId
import com.gym.tracker.ui.theme.themeSpec
import com.gym.tracker.ui.timer.isActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    /** Счётчик запросов «открыть вкладку Таймер» (нажатие на уведомление таймера). */
    private val openTimerRequests = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = (application as GymApplication).themePreferences
        // Тема читается до первого кадра (маленький файл настроек) — без мигания темы по умолчанию.
        val initialTheme = runBlocking { prefs.theme.first() }
        applySystemBars(initialTheme)
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleOpenTimer(intent)
        val timer = (application as GymApplication).timerController
        setContent {
            val themeId by prefs.theme.collectAsStateWithLifecycle(initialValue = initialTheme)
            val scope = rememberCoroutineScope()
            val timerState by timer.state.collectAsStateWithLifecycle()
            LaunchedEffect(themeId) { applySystemBars(themeId) }
            GymTheme(themeId) {
                GymApp(
                    currentTheme = themeId,
                    onThemeSelected = { id -> scope.launch { prefs.setTheme(id) } },
                    timerActive = timerState.isActive(),
                    openTimerRequests = openTimerRequests.intValue,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpenTimer(intent)
    }

    private fun handleOpenTimer(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_TIMER, false) == true) {
            intent.removeExtra(EXTRA_OPEN_TIMER)
            openTimerRequests.intValue++
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

    companion object {
        const val EXTRA_OPEN_TIMER = "open_timer"
    }
}
