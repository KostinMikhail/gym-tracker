package com.gym.tracker.backup

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.Base64

class GitHubClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: GitHubClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = GitHubClient(baseUrl = server.url("/"))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun json(code: Int, body: String) = MockResponse().setResponseCode(code).setBody(body)

    private fun expectError(kind: GitHubError.Kind, block: () -> Unit) {
        try {
            block()
            fail("ожидалась ошибка $kind")
        } catch (e: GitHubError) {
            assertEquals(kind, e.kind)
        }
    }

    @Test
    fun missingFileIsCreatedWithoutSha() {
        server.enqueue(json(404, """{"message":"Not Found"}"""))
        server.enqueue(json(201, """{"content":{"sha":"new"}}"""))

        client.putFile("me/gym-data", "tok", "history.csv", "a,b\n", "Журнал: 20.11.2026 18:30, записей: 0")

        val get = server.takeRequest()
        assertEquals("GET", get.method)
        assertEquals("/repos/me/gym-data/contents/history.csv", get.path)
        assertEquals("Bearer tok", get.getHeader("Authorization"))
        assertEquals("application/vnd.github+json", get.getHeader("Accept"))
        assertEquals("2022-11-28", get.getHeader("X-GitHub-Api-Version"))
        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        val body = put.body.readUtf8()
        assertFalse(body.contains("\"sha\""))
        assertTrue(body.contains("\"message\":\"Журнал: 20.11.2026 18:30, записей: 0\""))
        assertTrue(body.contains(Base64.getEncoder().encodeToString("a,b\n".toByteArray())))
    }

    @Test
    fun existingFileIsUpdatedWithItsSha() {
        server.enqueue(json(200, """{"sha":"abc123","name":"history.csv","size":10}"""))
        server.enqueue(json(200, """{"content":{"sha":"def"}}"""))

        client.putFile("me/gym-data", "tok", "history.csv", "x", "m")

        server.takeRequest()
        assertTrue(server.takeRequest().body.readUtf8().contains("\"sha\":\"abc123\""))
    }

    @Test
    fun conflictRefetchesShaAndRetriesOnce() {
        server.enqueue(json(200, """{"sha":"old"}"""))
        server.enqueue(json(409, """{"message":"is at newer but expected old"}"""))
        server.enqueue(json(200, """{"sha":"newer"}"""))
        server.enqueue(json(200, """{"content":{}}"""))

        client.putFile("me/gym-data", "tok", "history.csv", "x", "m")

        assertEquals(4, server.requestCount)
        server.takeRequest(); server.takeRequest(); server.takeRequest()
        assertTrue(server.takeRequest().body.readUtf8().contains("\"sha\":\"newer\""))
    }

    @Test
    fun secondConflictGivesUp() {
        server.enqueue(json(200, """{"sha":"a"}"""))
        server.enqueue(json(422, """{"message":"sha wasn't supplied"}"""))
        server.enqueue(json(200, """{"sha":"b"}"""))
        server.enqueue(json(409, "{}"))
        expectError(GitHubError.Kind.CONFLICT) { client.putFile("me/gym-data", "tok", "history.csv", "x", "m") }
        assertEquals(4, server.requestCount)
    }

    @Test
    fun httpErrorsAreMapped() {
        server.enqueue(json(401, """{"message":"Bad credentials"}"""))
        expectError(GitHubError.Kind.BAD_TOKEN) { client.fileSha("me/gym-data", "tok", "history.csv") }

        server.enqueue(json(401, """{"message":"Token expired"}"""))
        expectError(GitHubError.Kind.EXPIRED) { client.checkAccess("me/gym-data", "tok") }

        server.enqueue(json(403, """{"message":"Resource not accessible by personal access token"}"""))
        expectError(GitHubError.Kind.NO_ACCESS) { client.putFile("me/gym-data", "tok", "history.csv", "x", "m") }

        server.enqueue(json(404, """{"message":"Not Found"}"""))
        expectError(GitHubError.Kind.NO_ACCESS) { client.checkAccess("me/gym-data", "tok") }

        server.enqueue(json(403, "{}").addHeader("x-ratelimit-remaining", "0"))
        expectError(GitHubError.Kind.SERVER) { client.checkAccess("me/gym-data", "tok") }

        server.enqueue(json(503, ""))
        expectError(GitHubError.Kind.SERVER) { client.checkAccess("me/gym-data", "tok") }
    }

    @Test
    fun checkAccessRequiresPush() {
        server.enqueue(
            json(200, """{"full_name":"me/gym-data","permissions":{"admin":false,"push":true,"pull":true}}""")
                .addHeader("github-authentication-token-expiration", "2027-11-20 12:00:00 +0300"),
        )
        assertEquals("2027-11-20 12:00:00 +0300", client.checkAccess("me/gym-data", "tok").tokenExpires)

        server.enqueue(json(200, """{"full_name":"me/gym-data","permissions":{"push":false,"pull":true}}"""))
        expectError(GitHubError.Kind.NO_WRITE) { client.checkAccess("me/gym-data", "tok") }
        assertEquals("/repos/me/gym-data", server.takeRequest().path)
    }

    @Test
    fun noConnectionIsNetworkError() {
        server.shutdown()
        expectError(GitHubError.Kind.NETWORK) { client.checkAccess("me/gym-data", "tok") }
    }

    @Test
    fun repoFormat() {
        assertEquals("me" to "gym-data", GitHubClient.parseRepo(" me/gym-data "))
        assertEquals("me" to "gym-data", GitHubClient.parseRepo("https://github.com/me/gym-data.git"))
        assertNull(GitHubClient.parseRepo("gym-data"))
        assertNull(GitHubClient.parseRepo("me/gym data"))
        expectError(GitHubError.Kind.BAD_REPO) { client.checkAccess("gym-data", "tok") }
    }
}
