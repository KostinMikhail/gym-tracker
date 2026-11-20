package com.gym.tracker.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

/** Ошибка обмена с GitHub. [kind] — для понятного сообщения в настройках и решения «повторять ли». */
class GitHubError(val kind: Kind) : Exception(kind.name) {
    enum class Kind(val retryable: Boolean) {
        /** Нет интернета / таймаут. */
        NETWORK(true),

        /** GitHub временно недоступен (5xx, 429, исчерпан лимит запросов). */
        SERVER(true),

        /** 401: токен неверный или отозван. */
        BAD_TOKEN(false),

        /** 401 с сообщением об истёкшем сроке действия. */
        EXPIRED(false),

        /** 403/404: у токена нет доступа к репозиторию (или репозитория нет). */
        NO_ACCESS(false),

        /** Репозиторий доступен, но токен не может в него писать (нет права Contents: Read and write). */
        NO_WRITE(false),

        /** Строка репозитория не в формате «владелец/имя». */
        BAD_REPO(false),

        /** sha файла не совпал и после повторного запроса (файл меняют параллельно). */
        CONFLICT(true),
    }
}

/**
 * GitHub REST API (Contents) для резервной копии журнала.
 * Токен передаётся только в заголовке Authorization и нигде не логируется; в исключения попадает
 * лишь [GitHubError.Kind] — без текста ответа и заголовков.
 * Все вызовы блокирующие — выполнять в Dispatchers.IO.
 */
class GitHubClient(
    private val http: OkHttpClient = defaultHttp(),
    private val baseUrl: HttpUrl = "https://api.github.com/".toHttpUrl(),
) {
    /** Результат «Проверить связь». [tokenExpires] — из заголовка GitHub, если у токена есть срок. */
    data class Access(val tokenExpires: String?)

    /** Проверка: репозиторий виден токену и токен может в него писать. */
    fun checkAccess(repo: String, token: String): Access {
        val request = authorized(repoUrl(repo).build(), token).get().build()
        return call(request) { r ->
            when {
                r.isSuccessful -> {
                    val info = decode(RepoInfo.serializer(), r.body?.string().orEmpty())
                    if (info.permissions?.push != true) throw GitHubError(GitHubError.Kind.NO_WRITE)
                    Access(r.header(EXPIRATION_HEADER))
                }
                else -> throw errorFor(r)
            }
        }
    }

    /**
     * Записывает файл целиком: GET → текущий sha (404 — файла ещё нет), PUT нового содержимого с этим sha.
     * При 409/422 (sha устарел) — один повтор с заново полученным sha.
     */
    fun putFile(repo: String, token: String, path: String, content: String, message: String) {
        val base64 = Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8))
        var sha = fileSha(repo, token, path)
        repeat(2) { attempt ->
            val body = Json.encodeToString(PutRequest.serializer(), PutRequest(message, base64, sha))
            val request = authorized(contentsUrl(repo, path), token)
                .put(body.toRequestBody(JSON))
                .build()
            val conflict = call(request) { r ->
                when {
                    r.isSuccessful -> false
                    r.code == 409 || r.code == 422 -> true
                    else -> throw errorFor(r)
                }
            }
            if (!conflict) return
            if (attempt == 0) sha = fileSha(repo, token, path)
        }
        throw GitHubError(GitHubError.Kind.CONFLICT)
    }

    /** sha файла в репозитории или null, если файла нет. */
    fun fileSha(repo: String, token: String, path: String): String? {
        val request = authorized(contentsUrl(repo, path), token).get().build()
        return call(request) { r ->
            when {
                r.isSuccessful -> decode(FileInfo.serializer(), r.body?.string().orEmpty()).sha
                r.code == 404 -> null
                else -> throw errorFor(r)
            }
        }
    }

    private fun <T> call(request: Request, handle: (Response) -> T): T = try {
        http.newCall(request).execute().use(handle)
    } catch (e: GitHubError) {
        throw e
    } catch (_: IOException) {
        throw GitHubError(GitHubError.Kind.NETWORK)
    }

    private fun errorFor(r: Response): GitHubError = GitHubError(
        when {
            r.code == 401 -> {
                val message = runCatching { decode(ErrorBody.serializer(), r.body?.string().orEmpty()).message }.getOrNull()
                if (message?.contains("expired", ignoreCase = true) == true) GitHubError.Kind.EXPIRED else GitHubError.Kind.BAD_TOKEN
            }
            r.code == 429 || r.code >= 500 -> GitHubError.Kind.SERVER
            r.code == 403 && r.header("x-ratelimit-remaining") == "0" -> GitHubError.Kind.SERVER
            r.code == 403 || r.code == 404 -> GitHubError.Kind.NO_ACCESS
            else -> GitHubError.Kind.SERVER
        },
    )

    private fun <T> decode(serializer: kotlinx.serialization.KSerializer<T>, body: String): T = try {
        JSON_PARSER.decodeFromString(serializer, body)
    } catch (_: SerializationException) {
        throw GitHubError(GitHubError.Kind.SERVER)
    } catch (_: IllegalArgumentException) {
        throw GitHubError(GitHubError.Kind.SERVER)
    }

    private fun repoUrl(repo: String): HttpUrl.Builder {
        val (owner, name) = parseRepo(repo) ?: throw GitHubError(GitHubError.Kind.BAD_REPO)
        return baseUrl.newBuilder().addPathSegment("repos").addPathSegment(owner).addPathSegment(name)
    }

    private fun contentsUrl(repo: String, path: String): HttpUrl =
        repoUrl(repo).addPathSegment("contents").addPathSegments(path).build()

    private fun authorized(url: HttpUrl, token: String): Request.Builder = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $token")
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")

    @Serializable
    private data class RepoInfo(val permissions: Permissions? = null)

    @Serializable
    private data class Permissions(val push: Boolean = false)

    @Serializable
    private data class FileInfo(val sha: String)

    @Serializable
    private data class ErrorBody(val message: String? = null)

    @Serializable
    private data class PutRequest(
        val message: String,
        val content: String,
        val sha: String? = null, // null не сериализуется (encodeDefaults = false) — создание файла
    )

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val JSON_PARSER = Json { ignoreUnknownKeys = true }
        private const val EXPIRATION_HEADER = "github-authentication-token-expiration"
        private val NAME = Regex("[A-Za-z0-9._-]+")

        /** «владелец/имя» (допускается ссылка https://github.com/владелец/имя) → пара или null. */
        fun parseRepo(input: String): Pair<String, String>? {
            val cleaned = input.trim()
                .removePrefix("https://").removePrefix("http://").removePrefix("github.com/")
                .removeSuffix("/").removeSuffix(".git")
            val parts = cleaned.split('/')
            if (parts.size != 2 || parts.any { !NAME.matches(it) }) return null
            return parts[0] to parts[1]
        }

        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
