package com.gym.tracker.backup

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

/** Фоновая выгрузка history.csv в GitHub (только при наличии сети, с экспоненциальной паузой между повторами). */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as GymApplication
        return when (app.gitHubBackup.upload()) {
            GitHubBackup.Outcome.UPLOADED, GitHubBackup.Outcome.UNCHANGED, GitHubBackup.Outcome.NOT_CONFIGURED -> Result.success()
            GitHubBackup.Outcome.RETRY -> Result.retry()
            GitHubBackup.Outcome.FAILED -> Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "github-backup"

        /** Пауза перед выгрузкой: серия правок за это время даёт одну выгрузку (REPLACE сдвигает срок). */
        private const val DEBOUNCE_SECONDS = 30L

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<BackupWorker>()
                .setInitialDelay(DEBOUNCE_SECONDS, TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
