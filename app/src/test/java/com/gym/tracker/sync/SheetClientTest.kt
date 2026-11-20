package com.gym.tracker.sync

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** HTTP-обмен с Apps Script на MockWebServer: POST → 302 → GET JSON и разбор ошибок. */
class SheetClientTest {
    private lateinit var server: MockWebServer
    private val client = SheetClient()
    private val request = SheetRequest("secret", listOf(SheetOp.PING))

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun url() = server.url("/macros/s/X/exec").toString()

    private fun kindOf(block: () -> Unit): SheetError.Kind {
        try {
            block()
        } catch (e: SheetError) {
            return e.kind
        }
        fail("ожидалась SheetError")
        error("unreachable")
    }

    @Test
    fun postThenRedirectIsFollowedWithSingleGet() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/macros/echo?user_content_key=abc"))
        server.enqueue(MockResponse().setBody("""{"ok":true,"applied":["a","b"],"sheet":"Журнал"}"""))
        val r = client.send(url(), request)
        assertEquals(listOf("a", "b"), r.applied)
        assertEquals(2, server.requestCount)
        val post = server.takeRequest()
        assertEquals("POST", post.method)
        assertTrue(post.body.readUtf8().contains("\"token\":\"secret\""))
        val get = server.takeRequest()
        assertEquals("GET", get.method) // результат забирается GET, POST не повторяется
        assertEquals("/macros/echo?user_content_key=abc", get.path)
        assertEquals(0L, get.bodySize)
    }

    @Test
    fun redirectToAbsoluteUrlAndChainedGetRedirects() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/echo1").toString()))
        server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", "/echo2"))
        server.enqueue(MockResponse().setBody("""{"ok":true,"applied":[]}"""))
        assertTrue(client.send(url(), request).ok)
        assertEquals(listOf("POST", "GET", "GET"), List(3) { server.takeRequest().method })
    }

    @Test
    fun directJsonWithoutRedirect() {
        server.enqueue(MockResponse().setBody("""{"ok":true,"applied":["x"],"extra":1}"""))
        assertEquals(listOf("x"), client.send(url(), request).applied)
    }

    @Test
    fun unauthorized() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/echo"))
        server.enqueue(MockResponse().setBody("""{"ok":false,"error":"unauthorized"}"""))
        assertEquals(SheetError.Kind.UNAUTHORIZED, kindOf { client.send(url(), request) })
    }

    @Test
    fun scriptErrorKeepsDetail() {
        server.enqueue(MockResponse().setBody("""{"ok":false,"error":"busy"}"""))
        try {
            client.send(url(), request)
            fail()
        } catch (e: SheetError) {
            assertEquals(SheetError.Kind.SCRIPT, e.kind)
            assertEquals("busy", e.detail)
        }
    }

    @Test
    fun htmlPageMeansWrongUrl() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/login"))
        server.enqueue(MockResponse().setBody("<html><body>Sign in</body></html>"))
        assertEquals(SheetError.Kind.BAD_URL, kindOf { client.send(url(), request) })
    }

    @Test
    fun notFoundMeansWrongUrl() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("Not found"))
        assertEquals(SheetError.Kind.BAD_URL, kindOf { client.send(url(), request) })
    }

    @Test
    fun serverErrorIsTemporary() {
        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(SheetError.Kind.NETWORK, kindOf { client.send(url(), request) })
    }

    @Test
    fun noConnectionIsNetworkError() {
        val dead = url()
        server.shutdown()
        assertEquals(SheetError.Kind.NETWORK, kindOf { client.send(dead, request) })
    }

    @Test
    fun malformedUrl() {
        assertEquals(SheetError.Kind.BAD_URL, kindOf { client.send("not a url", request) })
    }
}
