package com.gym.tracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Timer
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gym.tracker.GymApplication
import com.gym.tracker.R
import com.gym.tracker.ui.exercises.ExercisesScreen
import com.gym.tracker.ui.exercises.ExercisesViewModel
import com.gym.tracker.ui.history.ExerciseHistoryScreen
import com.gym.tracker.ui.history.ExerciseHistoryViewModel
import com.gym.tracker.ui.history.HistoryScreen
import com.gym.tracker.ui.history.HistoryViewModel
import com.gym.tracker.ui.history.SessionDetailScreen
import com.gym.tracker.ui.history.SessionDetailViewModel
import com.gym.tracker.ui.home.HomeScreen
import com.gym.tracker.ui.home.HomeViewModel
import com.gym.tracker.ui.theme.GymBackground
import com.gym.tracker.ui.theme.GymThemeId
import com.gym.tracker.ui.theme.LocalGymTokens
import com.gym.tracker.ui.timer.TimerScreen
import com.gym.tracker.ui.timer.TimerViewModel
import com.gym.tracker.ui.workout.WorkoutScreen
import com.gym.tracker.ui.workout.WorkoutViewModel

object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val EXERCISES = "exercises"
    const val TIMER = "timer"
    const val WORKOUT = "workout/{${WorkoutViewModel.ARG_SESSION_ID}}"
    const val SESSION = "session/{${SessionDetailViewModel.ARG_SESSION_ID}}?${SessionDetailViewModel.ARG_SUMMARY}={${SessionDetailViewModel.ARG_SUMMARY}}"
    const val EXERCISE_HISTORY = "exercise/{${ExerciseHistoryViewModel.ARG_EXERCISE_ID}}"

    fun workout(sessionId: String) = "workout/$sessionId"
    fun session(sessionId: String, summary: Boolean = false) = "session/$sessionId?summary=$summary"
    fun exerciseHistory(exerciseId: String) = "exercise/$exerciseId"
}

private data class Tab(val route: String, @param:StringRes val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, R.string.tab_workout, Icons.Filled.FitnessCenter),
    Tab(Routes.HISTORY, R.string.tab_history, Icons.Filled.History),
    Tab(Routes.EXERCISES, R.string.tab_exercises, Icons.AutoMirrored.Filled.List),
    Tab(Routes.TIMER, R.string.tab_timer, Icons.Filled.Timer),
)

@Composable
fun GymApp(
    currentTheme: GymThemeId,
    onThemeSelected: (GymThemeId) -> Unit,
    timerActive: Boolean = false,
    openTimerRequests: Int = 0,
) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val onWorkout = currentRoute == Routes.WORKOUT
    // Нижняя панель: на вкладках — всегда (кроме идущего таймера); на экране тренировки — прячется при прокрутке вниз.
    val showBottomBar = onWorkout || (tabs.any { it.route == currentRoute } && !(currentRoute == Routes.TIMER && timerActive))
    // Нажатие на уведомление таймера открывает вкладку «Таймер».
    LaunchedEffect(openTimerRequests) {
        if (openTimerRequests > 0 && nav.currentDestination?.route != Routes.TIMER) nav.switchTab(Routes.TIMER)
    }
    // Пока таймер идёт или на паузе, экран не гаснет.
    val view = LocalView.current
    DisposableEffect(timerActive) {
        view.keepScreenOn = timerActive
        onDispose { view.keepScreenOn = false }
    }
    var barVisibleOnWorkout by remember { mutableStateOf(true) }
    LaunchedEffect(currentRoute) { barVisibleOnWorkout = true }
    val navStyle = LocalGymTokens.current.nav

    GymBackground {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                val bar: @Composable () -> Unit = {
                    NavigationBar(containerColor = navStyle?.container ?: NavigationBarDefaults.containerColor) {
                        tabs.forEach { tab ->
                            // Экран тренировки относится к вкладке «Тренировка».
                            val selected = currentRoute == tab.route || (onWorkout && tab.route == Routes.HOME)
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    // На тренировке «Тренировка» — уже текущая вкладка: ничего не делаем.
                                    if (!(onWorkout && tab.route == Routes.HOME)) nav.switchTab(tab.route)
                                },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = {
                                    // Четыре вкладки: подпись в одну строку, в широких шрифтах тем — мельче, без переноса.
                                    Text(
                                        stringResource(tab.label),
                                        maxLines = 1,
                                        softWrap = false,
                                        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = MaterialTheme.typography.labelMedium.fontSize),
                                    )
                                },
                                colors = if (navStyle != null) {
                                    NavigationBarItemDefaults.colors(
                                        selectedIconColor = navStyle.selectedIcon,
                                        selectedTextColor = navStyle.selectedText,
                                        indicatorColor = navStyle.indicator,
                                        unselectedIconColor = navStyle.unselected,
                                        unselectedTextColor = navStyle.unselected,
                                    )
                                } else {
                                    NavigationBarItemDefaults.colors()
                                },
                            )
                        }
                    }
                }
                if (onWorkout) {
                    // Только на тренировке панель прячется при прокрутке вниз и возвращается при прокрутке вверх.
                    AnimatedVisibility(visible = barVisibleOnWorkout, enter = expandVertically(), exit = shrinkVertically()) { bar() }
                } else if (showBottomBar) {
                    bar()
                }
            },
        ) { padding ->
            NavHost(navController = nav, startDestination = Routes.HOME) {
                composable(Routes.HOME) {
                    HomeScreen(
                        vm = gymViewModel { repo, _ -> HomeViewModel(repo) },
                        contentPadding = padding,
                        currentTheme = currentTheme,
                        onThemeSelected = onThemeSelected,
                        onStartWorkout = { nav.navigate(Routes.workout(it)) },
                        onOpenSession = { nav.navigate(Routes.session(it)) },
                    )
                }
                composable(Routes.HISTORY) {
                    HistoryScreen(
                        vm = gymViewModel { repo, _ -> HistoryViewModel(repo) },
                        contentPadding = padding,
                        onOpenSession = { nav.navigate(Routes.session(it)) },
                        onOpenExercise = { nav.navigate(Routes.exerciseHistory(it)) },
                    )
                }
                composable(Routes.EXERCISES) {
                    ExercisesScreen(
                        vm = gymViewModel { repo, _ -> ExercisesViewModel(repo) },
                        contentPadding = padding,
                        onOpenHistory = { nav.navigate(Routes.exerciseHistory(it)) },
                    )
                }
                composable(Routes.TIMER) {
                    val app = LocalContext.current.applicationContext as GymApplication
                    TimerScreen(
                        vm = viewModel(
                            factory = viewModelFactory { initializer { TimerViewModel(app.timerController, app.timerPreferences) } },
                        ),
                        contentPadding = padding,
                        onExit = { nav.popBackStack() },
                    )
                }
                composable(
                    Routes.WORKOUT,
                    arguments = listOf(navArgument(WorkoutViewModel.ARG_SESSION_ID) { type = NavType.StringType }),
                ) {
                    WorkoutScreen(
                        vm = gymViewModel { repo, state -> WorkoutViewModel(repo, state) },
                        outerPadding = padding,
                        onBottomBarVisible = { barVisibleOnWorkout = it },
                        onOpenSummary = { id ->
                            nav.navigate(Routes.session(id, summary = true)) {
                                popUpTo(Routes.HOME)
                            }
                        },
                        onGoHome = { nav.popBackStack(Routes.HOME, inclusive = false) },
                    )
                }
                composable(
                    Routes.SESSION,
                    arguments = listOf(
                        navArgument(SessionDetailViewModel.ARG_SESSION_ID) { type = NavType.StringType },
                        navArgument(SessionDetailViewModel.ARG_SUMMARY) {
                            type = NavType.BoolType
                            defaultValue = false
                        },
                    ),
                ) {
                    SessionDetailScreen(
                        vm = gymViewModel { repo, state -> SessionDetailViewModel(repo, state) },
                        onBack = { nav.popBackStack() },
                        onDone = { nav.popBackStack(Routes.HOME, inclusive = false) },
                    )
                }
                composable(
                    Routes.EXERCISE_HISTORY,
                    arguments = listOf(navArgument(ExerciseHistoryViewModel.ARG_EXERCISE_ID) { type = NavType.StringType }),
                ) {
                    ExerciseHistoryScreen(
                        vm = gymViewModel { repo, state -> ExerciseHistoryViewModel(repo, state) },
                        onBack = { nav.popBackStack() },
                    )
                }
            }
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
