package com.gym.tracker.sync

import com.gym.tracker.data.db.SheetRowSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/*
 * Протокол Web App на Google Apps Script (tools/apps-script/Code.gs):
 * POST {"token": "...", "ops": [{"op":"upsert","row":{…}} | {"op":"delete","entry_id":"…"} | {"op":"ping"}]}
 * → {"ok": true, "applied": [entry_id…], "sheet": "Журнал"} или {"ok": false, "error": "…"}.
 */

/** Строка листа «Журнал». Ключи совпадают с KEYS в Code.gs. */
@Serializable
data class SheetRow(
    @SerialName("entry_id") val entryId: String,
    @SerialName("session_id") val sessionId: String,
    /** yyyy-MM-dd (в таблице — текст). */
    val date: String,
    val workout: String,
    val group: String,
    val exercise: String,
    val sets: Int,
    val reps: Int,
    @SerialName("weight_kg") val weightKg: Double,
    /** «Легко» / «Норм» / «Тяжело». */
    val feel: String,
    /** ISO-8601 со смещением: когда запись последний раз менялась. */
    val updated: String,
)

@Serializable
data class SheetOp(
    val op: String,
    val row: SheetRow? = null,
    @SerialName("entry_id") val entryId: String? = null,
) {
    companion object {
        fun upsert(row: SheetRow) = SheetOp("upsert", row = row)
        fun delete(entryId: String) = SheetOp("delete", entryId = entryId)
        val PING = SheetOp("ping")
    }
}

@Serializable
data class SheetRequest(val token: String, val ops: List<SheetOp>)

@Serializable
data class SheetResponse(
    val ok: Boolean,
    val applied: List<String> = emptyList(),
    val sheet: String? = null,
    val error: String? = null,
)

object SheetProtocol {
    /** Сериализаторы указываются явно (без рефлексии) — безопасно для R8. */
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun encode(request: SheetRequest): String = json.encodeToString(SheetRequest.serializer(), request)

    fun decode(body: String): SheetResponse = json.decodeFromString(SheetResponse.serializer(), body)

    /** Строка таблицы из данных БД (названия — текущие на момент отправки). */
    fun row(src: SheetRowSource, zone: ZoneId = ZoneId.systemDefault()): SheetRow = SheetRow(
        entryId = src.entryId,
        sessionId = src.sessionId,
        date = src.date.format(DateTimeFormatter.ISO_LOCAL_DATE),
        workout = src.templateName.orEmpty(),
        group = src.slotName.orEmpty(),
        exercise = src.exerciseName,
        sets = src.sets,
        reps = src.reps,
        weightKg = src.weightKg,
        feel = src.feel.label,
        updated = OffsetDateTime.ofInstant(Instant.ofEpochMilli(src.updatedAt), zone)
            .truncatedTo(ChronoUnit.SECONDS)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
    )
}
