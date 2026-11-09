package com.gym.tracker

import android.app.Application
import com.gym.tracker.data.GymRepository
import com.gym.tracker.data.db.GymDatabase

/** Ручной DI: один экземпляр БД и репозитория на процесс. */
class GymApplication : Application() {
    val repository: GymRepository by lazy { GymRepository(GymDatabase.build(this)) }
}
