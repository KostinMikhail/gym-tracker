package com.gym.tracker.sync

import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Ошибка обмена с Apps Script. [kind] — для понятного сообщения в настройках и решения «повторять ли». */
class SheetError(val kind: Kind, val detail: String? = null) : Exception(kind.name) {
    enum class Kind(val retryable: Boolean) {
        /** Нет интернета / таймаут / сервер Google временно недоступен. */
        NETWORK(true),

        /** Скрипт ответил «unauthorized»: токен не совпадает со свойством TOKEN. */
        UNAUTHORIZED(false),

        /** Ссылка не ведёт на скрипт (404, HTML-страница, нет доступа «Все»). */
        BAD_URL(false),

        /** Скрипт вернул ошибку ({ok:false}). */
        SCRIPT(true),
    }
}

/**
 * HTTP-клиент Web App на Google Apps Script.
 *
 * Особенность Apps Script: на POST приходит 302 на script.googleusercontent.com, а JSON-ответ отдаётся
 * по GET на адрес из Location. doPost к этому моменту уже выполнен — повторный POST привёл бы к повторной
 * записи, поэтому редиректы обрабатываются вручную: POST один раз, затем только GET.
 * Ничего не логирует (в запросе — токен).
 */
class SheetClient(private val http: OkHttpClient = defaultHttp()) {

    /** Блокирующий вызов — выполнять в Dispatchers.IO. */
    fun send(url: String, request: SheetRequest): SheetResponse {
        val target = url.trim().toHttpUrlOrNull() ?: throw SheetError(SheetError.Kind.BAD_URL)
        val post = Request.Builder()
            .url(target)
            .post(SheetProtocol.encode(request).toRequestBody(JSON))
            .build()
        val body = try {
            http.newCall(post).execute().use { first ->
                if (first.isRedirect) followWithGet(first) else readBody(first)
            }
        } catch (e: SheetError) {
            throw e
        } catch (_: IOException) {
            throw SheetError(SheetError.Kind.NETWORK)
        }
        val response = try {
            SheetProtocol.decode(body)
        } catch (_: SerializationException) {
            // Не JSON (страница входа Google, «Script function not found» и т. п.) — это не наш скрипт.
            throw SheetError(SheetError.Kind.BAD_URL)
        } catch (_: IllegalArgumentException) {
            throw SheetError(SheetError.Kind.BAD_URL)
        }
        if (!response.ok) {
            throw if (response.error == "unauthorized") {
                SheetError(SheetError.Kind.UNAUTHORIZED)
            } else {
                SheetError(SheetError.Kind.SCRIPT, response.error)
            }
        }
        return response
    }

    /** Результат POST забирается GET-запросом по Location (с переходом по дальнейшим GET-редиректам). */
    private fun followWithGet(redirect: Response): String {
        var current: Response = redirect
        repeat(MAX_REDIRECTS) {
            val next = location(current)
            val get = http.newCall(Request.Builder().url(next).get().build()).execute()
            if (!get.isRedirect) return get.use { readBody(it) }
            current.close()
            current = get
        }
        current.close()
        throw SheetError(SheetError.Kind.BAD_URL)
    }

    private fun location(r: Response): HttpUrl {
        val header = r.header("Location") ?: throw SheetError(SheetError.Kind.BAD_URL)
        return r.request.url.resolve(header) ?: throw SheetError(SheetError.Kind.BAD_URL)
    }

    private fun readBody(r: Response): String = when {
        r.isSuccessful -> r.body?.string().orEmpty()
        r.code == 429 || r.code >= 500 -> throw SheetError(SheetError.Kind.NETWORK)
        else -> throw SheetError(SheetError.Kind.BAD_URL) // 401/403/404 …: не тот адрес или нет доступа
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val MAX_REDIRECTS = 5

        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
