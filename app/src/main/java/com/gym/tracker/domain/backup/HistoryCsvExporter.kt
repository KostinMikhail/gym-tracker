package com.gym.tracker.domain.backup

import com.gym.tracker.data.db.HistoryRow
import java.math.BigDecimal
import java.security.MessageDigest

/**
 * Полный снимок журнала тренировок в CSV (history.csv): UTF-8, запятая, кавычки по RFC 4180,
 * одна строка на запись, порядок — дата → тренировка → порядок слота. Чистый Kotlin, без Android.
 */
object HistoryCsvExporter {
    const val HEADER = "date,workout,slot,exercise,sets,reps,weight_kg,feel,session_id,entry_id"

    private val ORDER = compareBy<HistoryRow>(
        { it.date },
        { it.sessionCreatedAt },
        { it.sessionId },
        { it.slotOrder ?: Int.MAX_VALUE },
        { it.entryCreatedAt },
        { it.entryId },
    )

    fun build(rows: List<HistoryRow>): String = buildString {
        append(HEADER).append('\n')
        for (r in rows.sortedWith(ORDER)) {
            listOf(
                r.date.toString(), // ISO: yyyy-MM-dd
                r.workout.orEmpty(),
                r.slot.orEmpty(),
                r.exercise,
                r.sets.toString(),
                r.reps.toString(),
                weight(r.weightKg),
                r.feel.label,
                r.sessionId,
                r.entryId,
            ).joinTo(this, ",") { field(it) }
            append('\n')
        }
    }

    /** Вес с точкой и без лишних нулей: 22.5, 45, 0. */
    fun weight(kg: Double): String = BigDecimal.valueOf(kg).stripTrailingZeros().toPlainString()

    /** RFC 4180: поле в кавычках, если в нём запятая, кавычка или перевод строки; кавычки удваиваются. */
    fun field(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    /** SHA-256 содержимого (hex) — чтобы не выгружать неизменившийся снимок. */
    fun sha256(content: String): String =
        MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** SHA-1 git-блоба — совпадает с `sha` файла в GitHub, если содержимое то же. */
    fun gitBlobSha(content: String): String {
        val bytes = content.toByteArray(Charsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-1")
        md.update("blob ${bytes.size}\u0000".toByteArray(Charsets.UTF_8))
        md.update(bytes)
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
