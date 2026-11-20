package com.gym.tracker.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gym.tracker.GymApplication
import java.util.concurrent.TimeUnit

/** Фоновая отправка очереди в Google Таблицу (только при наличии сети, с экспоненциальной паузой между повторами). */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as GymApplication
        return when (app.sheetSync.syncPending()) {
            SheetSync.Outcome.DONE, SheetSync.Outcome.NOT_CONFIGURED -> Result.success()
            SheetSync.Outcome.RETRY -> Result.retry()
            SheetSync.Outcome.STOP -> Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "sheet-sync"

        /**
         * Запускает отправку. REPLACE: новая операция в очереди не ждёт паузы прежнего неудачного запуска,
         * а прерванная отправка безопасна (повтор того же upsert/delete ничего не дублирует).
         */
        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
