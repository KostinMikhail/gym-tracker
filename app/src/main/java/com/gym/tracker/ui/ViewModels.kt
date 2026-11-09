package com.gym.tracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gym.tracker.GymApplication
import com.gym.tracker.data.GymRepository

/** ViewModel с репозиторием из Application и SavedStateHandle (в нём же аргументы навигации). */
@Composable
inline fun <reified VM : ViewModel> gymViewModel(crossinline create: (GymRepository, SavedStateHandle) -> VM): VM {
    val repo = (LocalContext.current.applicationContext as GymApplication).repository
    return viewModel(factory = viewModelFactory { initializer { create(repo, createSavedStateHandle()) } })
}
