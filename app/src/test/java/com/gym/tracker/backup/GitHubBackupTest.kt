package com.gym.tracker.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.gym.tracker.data.BackupPreferences
import com.gym.tracker.data.db.Feel
import com.gym.tracker.data.db.HistoryRow
import com.gym.tracker.domain.backup.HistoryCsvExporter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class GitHubBackupTest {
    private class InMemoryStore : DataStore<Preferences> {
        val state = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val next = transform(state.value)
            state.value = (next as? MutablePreferences)?.toPreferences() ?: next
            return state.value
        }
    }

    /** Обратимое «шифрование» для JVM-тестов (Android Keystore в Robolectric нет). */
    private class FakeCipher : TokenCipher {
        override fun encrypt(plain: String) = "enc:" + plain.reversed()
        override fun decrypt(cipherText: String) = cipherText.removePrefix("enc:").reversed().takeIf { cipherText.startsWith("enc:") }
    }

    private lateinit var server: MockWebServer
    private val store = InMemoryStore()
    private val prefs = BackupPreferences(store, FakeCipher())
    private var rows = listOf(row("e1", "Жим лёжа"))
    private lateinit var backup: GitHubBackup

    private fun row(id: String, exercise: String) = HistoryRow(
        LocalDate.of(2026, 11, 20), "Фулбади А", "Грудь", exercise, 3, 12, 45.0, Feel.NORMAL,
        "s1", id, 1, 0, 1,
    )

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        backup = GitHubBackup(
            loadRows = { rows },
            prefs = prefs,
            client = GitHubClient(baseUrl = server.url("/")),
            clock = { 1_763_660_000_000 }, // 20.11.2025 17:33 UTC
            zone = ZoneOffset.UTC,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun ok(body: String = """{"content":{}}""") = MockResponse().setResponseCode(200).setBody(body)

    @Test
    fun notConfiguredDoesNothing() = runTest {
        assertEquals(GitHubBackup.Outcome.NOT_CONFIGURED, backup.upload())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun uploadsThenSkipsUnchangedThenUploadsChange() = runTest {
        prefs.setConfig("me/gym-data", "tok")
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(ok())
        assertEquals(GitHubBackup.Outcome.UPLOADED, backup.upload())
        assertEquals(2, server.requestCount)
        server.takeRequest()
        assertTrue(server.takeRequest().body.readUtf8().contains("\"message\":\"Журнал: 20.11.2025 17:33, записей: 1\""))
        val status = prefs.status.first()
        assertEquals(1_763_660_000_000, status.lastUploadAt)
        assertEquals(1, status.lastCount)

        // Ничего не изменилось — ни одного запроса, коммита нет.
        assertEquals(GitHubBackup.Outcome.UNCHANGED, backup.upload())
        assertEquals(2, server.requestCount)

        // Переименование упражнения — новый снимок с новым названием.
        rows = listOf(row("e1", "Жим лёжа в тренажёре"))
        server.enqueue(ok("""{"sha":"old"}"""))
        server.enqueue(ok())
        assertEquals(GitHubBackup.Outcome.UPLOADED, backup.upload())
        server.takeRequest()
        val put = server.takeRequest().body.readUtf8()
        val content = Regex("\"content\":\"([^\"]+)\"").find(put)!!.groupValues[1]
        assertTrue(String(java.util.Base64.getDecoder().decode(content)).contains("Жим лёжа в тренажёре"))
    }

    @Test
    fun manualUploadVerifiesRemoteFile() = runTest {
        prefs.setConfig("me/gym-data", "tok")
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(ok())
        backup.upload()
        val csv = HistoryCsvExporter.build(rows)

        // В репозитории тот же файл — коммита нет (только GET).
        server.enqueue(ok("""{"sha":"${HistoryCsvExporter.gitBlobSha(csv)}"}"""))
        assertEquals(GitHubBackup.Outcome.UNCHANGED, backup.upload(verifyRemote = true))
        assertEquals(3, server.requestCount)

        // Файл удалили вручную — выгружается заново.
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(ok())
        assertEquals(GitHubBackup.Outcome.UPLOADED, backup.upload(verifyRemote = true))
        assertEquals(6, server.requestCount)
    }

    @Test
    fun errorsAreRecordedAndClassified() = runTest {
        prefs.setConfig("me/gym-data", "bad")
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Bad credentials"}"""))
        assertEquals(GitHubBackup.Outcome.FAILED, backup.upload())
        assertEquals(GitHubError.Kind.BAD_TOKEN.name, prefs.status.first().errorCode)
        assertNull(prefs.lastUploadedHash())

        server.enqueue(MockResponse().setResponseCode(502))
        assertEquals(GitHubBackup.Outcome.RETRY, backup.upload())
        assertEquals(GitHubError.Kind.SERVER.name, prefs.status.first().errorCode)

        // Успех сбрасывает ошибку.
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(ok())
        assertEquals(GitHubBackup.Outcome.UPLOADED, backup.upload())
        assertNull(prefs.status.first().errorCode)
    }

    @Test
    fun tokenIsStoredOnlyEncrypted() = runTest {
        prefs.setConfig(" me/gym-data ", " github_pat_SECRET ")
        assertEquals(BackupPreferences.Config("me/gym-data", "github_pat_SECRET"), prefs.config.first())
        val raw = store.state.value.asMap().values.joinToString()
        assertFalse(raw.contains("github_pat_SECRET"))
        assertFalse(prefs.config.first().toString().contains("SECRET"))
    }

    @Test
    fun defaultRepoAndRepoChangeResetsHash() = runTest {
        assertEquals(BackupPreferences.DEFAULT_REPO, prefs.config.first().repo)
        prefs.setConfig("me/gym-data", "tok")
        prefs.recordSuccess("h", 1, 1)
        prefs.setConfig("me/gym-data", "tok2")
        assertEquals("h", prefs.lastUploadedHash())
        prefs.setConfig("me/other", "tok2")
        assertNotEquals("h", prefs.lastUploadedHash())
    }
}
