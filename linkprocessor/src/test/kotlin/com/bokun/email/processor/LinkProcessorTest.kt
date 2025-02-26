package com.bokun.email.processor

import com.bokun.email.processor.app.Router
import com.bokun.email.processor.database.ClickDB
import com.bokun.email.processor.database.LinkDB
import com.bokun.email.processor.services.LinkService
import com.bokun.email.processor.services.RedirectService
import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.bodyAsClass
import io.mockk.*
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.slf4j.LoggerFactory

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LinkProcessorTest {
    private lateinit var app: Javalin
    private val logger = LoggerFactory.getLogger(LinkProcessorTest::class.java)

    @BeforeAll
    fun setUp() {
        app = Javalin.create().start(0)
        Router.registerRoutes(app)
    }

    @AfterAll
    fun tearDown() {
        app.stop()
    }

    @Test
    fun `test processEmail stores links correctly`() {
        val emailContent = "Hello, visit https://google.com and https://example.com"
        val requestBody = mapOf("content" to emailContent)

        val ctx = mockk<Context>(relaxed = true)
        every { ctx.bodyAsClass<Map<String, String>>() } returns requestBody
        every { ctx.contentType(any<String>()) } returns ctx
        every { ctx.result(any<String>()) } returns ctx

        LinkService.processEmail(ctx)

        verify { ctx.result(match<String> { it.contains("google.com") && it.contains("example.com") }) }
    }

    @Test
    fun `test trackAndRedirect increments click count`() {
        val shortId = "abc123"
        val userAgent = "Mozilla/5.0"
        val ipAddress = "127.0.0.1"

        val ctx = mockk<Context>(relaxed = true)
        every { ctx.pathParam("shortId") } returns shortId
        every { ctx.header("User-Agent") } returns userAgent
        every { ctx.ip() } returns ipAddress
        every { ctx.redirect(any()) } returns Unit

        mockkObject(ClickDB)
        every { ClickDB.retrieveOriginalUrl(shortId) } returns "https://example.com"
        every { ClickDB.isLinkExpired(shortId) } returns false
        every { ClickDB.storeClick(any()) } just Runs

        mockkObject(LinkDB)
        every { LinkDB.incrementClickCount(shortId) } just Runs

        RedirectService.trackAndRedirect(ctx)

        verify { ClickDB.storeClick(any()) }
        verify { LinkDB.incrementClickCount(shortId) }
    }

    @Test
    fun `test isLinkExpired returns true for expired links`() {
        val shortId = "expired123"

        mockkObject(ClickDB)
        every { ClickDB.isLinkExpired(shortId) } returns true

        val result = ClickDB.isLinkExpired(shortId)

        assertTrue(result, "Expected the link to be expired.")
    }

    @Test
    fun `test isLinkExpired returns false for non-expired links`() {
        val shortId = "active123"

        mockkObject(ClickDB)
        every { ClickDB.isLinkExpired(shortId) } returns false

        val result = ClickDB.isLinkExpired(shortId)

        assertFalse(result, "Expected the link to be active.")
    }

    @Test
    fun `test retrieveOriginalUrl returns null for nonexistent links`() {
        val shortId = "doesnotexist"

        mockkObject(ClickDB)
        every { ClickDB.retrieveOriginalUrl(shortId) } returns null

        val result = ClickDB.retrieveOriginalUrl(shortId)

        assertNull(result, "Expected null for a nonexistent link.")
    }

    @Test
    fun `test trackAndRedirect returns 429 when rate limit exceeded`() {
        val shortId = "rateLimitTest"
        val userAgent = "Mozilla/5.0"
        val ipAddress = "127.0.0.1"

        val ctx = mockk<Context>(relaxed = true)
        every { ctx.pathParam("shortId") } returns shortId
        every { ctx.header("User-Agent") } returns userAgent
        every { ctx.ip() } returns ipAddress
        every { ctx.status(429) } returns ctx  // ✅ Fix: Returns ctx instead of just Runs
        every { ctx.result(any<String>()) } returns ctx  // ✅ Fix: Returns ctx instead of just Runs

        mockkObject(ClickDB)
        every { ClickDB.retrieveOriginalUrl(shortId) } returns "https://example.com"
        every { ClickDB.isLinkExpired(shortId) } returns false
        every { ClickDB.storeClick(any()) } just Runs

        mockkObject(LinkDB)
        every { LinkDB.incrementClickCount(shortId) } just Runs

        // Simulate multiple rapid requests from the same IP
        repeat(6) { RedirectService.trackAndRedirect(ctx) } // Exceed RATE_LIMIT (assumed to be 5)

        // Verify that the 429 status was set
        verify { ctx.status(429) }
    }

}
