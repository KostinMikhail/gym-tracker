package com.gym.tracker.backup

import com.gym.tracker.data.BackupPreferences
import com.gym.tracker.data.db.HistoryRow
import com.gym.tracker.domain.backup.HistoryCsvExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Односторонняя копия журнала в GitHub: Room → history.csv в приватном репозитории.
 * Снимок собирается целиком из текущих данных; неизменившийся снимок не выгружается (без пустых коммитов).
 */
class GitHubBackup(
    private val loadRows: suspend () -> List<HistoryRow>,
    private val prefs: BackupPreferences,
    private val client: GitHubClient,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    enum class Outcome {
        /** Снимок выгружен. */
        UPLOADED,

        /** Содержимое то же, что уже выгружено, — коммита нет. */
        UNCHANGED,

        /** Репозиторий или токен не указаны — копия выключена. */
        NOT_CONFIGURED,

        /** Временная ошибка — повторить позже. */
        RETRY,

        /** Ошибка, которую исправит только владелец (токен/доступ). */
        FAILED,
    }

    private val mutex = Mutex()

    /** Текущий снимок CSV (он же — для «Поделиться историей»). */
    suspend fun snapshot(): String = HistoryCsvExporter.build(loadRows())

    /**
     * Выгружает снимок. [verifyRemote] (кнопка «Выгрузить сейчас»): при совпадении хеша дополнительно сверяет
     * файл в репозитории — если его удалили или изменили вручную, снимок выгружается заново.
     */
    suspend fun upload(verifyRemote: Boolean = false): Outcome = mutex.withLock {
        val config = prefs.config.first()
        if (!config.isComplete) return@withLock Outcome.NOT_CONFIGURED
        val rows = loadRows()
        val csv = HistoryCsvExporter.build(rows)
        val hash = HistoryCsvExporter.sha256(csv)
        try {
            withContext(Dispatchers.IO) {
                if (hash == prefs.lastUploadedHash()) {
                    val sameRemote = !verifyRemote ||
                        client.fileSha(config.repo, config.token, PATH) == HistoryCsvExporter.gitBlobSha(csv)
                    if (sameRemote) {
                        prefs.clearError()
                        return@withContext Outcome.UNCHANGED
                    }
                }
                val now = clock()
                client.putFile(config.repo, config.token, PATH, csv, commitMessage(now, rows.size))
                prefs.recordSuccess(hash, now, rows.size)
                Outcome.UPLOADED
            }
        } catch (e: GitHubError) {
            prefs.recordError(e.kind.name)
            if (e.kind.retryable) Outcome.RETRY else Outcome.FAILED
        }
    }

    /** «Проверить связь»: репозиторий виден и токен может писать. */
    suspend fun check(repo: String, token: String): Result<GitHubClient.Access> = withContext(Dispatchers.IO) {
        try {
            Result.success(client.checkAccess(repo, token.trim()))
        } catch (e: GitHubError) {
            Result.failure(e)
        }
    }

    internal fun commitMessage(at: Long, count: Int): String =
        "Журнал: ${COMMIT_TIME.format(Instant.ofEpochMilli(at).atZone(zone))}, записей: $count"

    companion object {
        const val PATH = "history.csv"
        private val COMMIT_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    }
}
