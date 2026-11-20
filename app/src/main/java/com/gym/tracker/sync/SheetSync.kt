package com.gym.tracker.sync

import com.gym.tracker.data.SyncPreferences
import com.gym.tracker.data.db.SyncDao
import com.gym.tracker.data.db.SyncOpType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Отправка очереди `sync_ops` в Google Таблицу (одностороннее зеркало: приложение → таблица).
 * Строки собираются из текущих данных БД в момент отправки; если записи уже нет — отправляется удаление.
 * После ответа удаляются только отправленные версии операций (по id), подтверждённые в `applied`.
 */
class SheetSync(
    private val dao: SyncDao,
    private val prefs: SyncPreferences,
    private val send: (url: String, request: SheetRequest) -> SheetResponse,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    enum class Outcome {
        /** Очередь пуста (всё отправлено). */
        DONE,

        /** Ссылка или токен не заданы — синхронизация выключена. */
        NOT_CONFIGURED,

        /** Временная ошибка — повторить позже. */
        RETRY,

        /** Ошибка настройки (токен/ссылка) — ждать изменения настроек. */
        STOP,
    }

    suspend fun syncPending(): Outcome {
        val config = prefs.config.first()
        if (!config.isComplete) return Outcome.NOT_CONFIGURED
        while (true) {
            val ops = dao.pending(BATCH)
            if (ops.isEmpty()) return Outcome.DONE
            val upsertIds = ops.filter { it.op == SyncOpType.UPSERT }.map { it.entryId }
            val sources = if (upsertIds.isEmpty()) emptyMap() else dao.rowSources(upsertIds).associateBy { it.entryId }
            val sheetOps = ops.map { op ->
                val src = sources[op.entryId]
                if (op.op == SyncOpType.UPSERT && src != null) SheetOp.upsert(SheetProtocol.row(src)) else SheetOp.delete(op.entryId)
            }
            val response = try {
                withContext(Dispatchers.IO) { send(config.url, SheetRequest(config.token, sheetOps)) }
            } catch (e: SheetError) {
                dao.markFailed(ops.map { it.id }, e.kind.name)
                prefs.recordError(e.kind.name, e.detail)
                return if (e.kind.retryable) Outcome.RETRY else Outcome.STOP
            }
            val applied = response.applied.toSet()
            val (done, notDone) = ops.partition { it.entryId in applied }
            if (done.isNotEmpty()) {
                dao.deleteByIds(done.map { it.id })
                prefs.recordSuccess(clock())
            }
            if (notDone.isNotEmpty()) {
                // Скрипт принял запрос, но не подтвердил часть операций — оставляем их в очереди.
                dao.markFailed(notDone.map { it.id }, NOT_APPLIED)
                prefs.recordError(SheetError.Kind.SCRIPT.name, NOT_APPLIED)
                return Outcome.RETRY
            }
        }
    }

    /** «Проверить связь»: ping с указанными (ещё, возможно, не сохранёнными) ссылкой и токеном. null — подключено. */
    suspend fun ping(url: String, token: String): SheetError? = try {
        withContext(Dispatchers.IO) { send(url, SheetRequest(token.trim(), listOf(SheetOp.PING))) }
        null
    } catch (e: SheetError) {
        e
    }

    companion object {
        const val BATCH = 200
        const val NOT_APPLIED = "not_applied"
    }
}
