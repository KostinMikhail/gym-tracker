package com.gym.tracker.ui.settings

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** «Поделиться историей (CSV)»: файл в кэше (cache/exports) → системное окно «Поделиться». */
object CsvShare {
    /** Имя файла: gym-history-yyyy-MM-dd.csv. */
    fun fileName(today: LocalDate): String = "gym-history-$today.csv"

    suspend fun share(context: Context, csv: String, chooserTitle: String, today: LocalDate = LocalDate.now()) {
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            // Старые выгрузки не копим.
            dir.listFiles()?.forEach { it.delete() }
            File(dir, fileName(today)).apply { writeText(csv, Charsets.UTF_8) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, chooserTitle))
    }
}
