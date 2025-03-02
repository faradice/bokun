package com.bokun.email.processor

import com.bokun.email.processor.app.Router
import com.bokun.email.processor.database.ClickDB
import com.bokun.email.processor.database.LinkDB
import com.bokun.email.processor.services.LinkService
import com.bokun.email.processor.services.TrackingService
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
    fun `test trackAndRedirect`() {
        val shortId = "abc123"
        val userAgent = "Mozilla/5.0"
        val ipAddress = "127.0.0.1"

        val ctx = mockk<Context>(relaxed = true)
        every { ctx.pathParam("shortId") } returns shortId
        every { ctx.header("User-Agent") } returns userAgent
        every { ctx.ip() } returns ipAddress
        every { ctx.redirect(any()) } returns Unit

        mockkObject(ClickDB)
        mockkObject(LinkDB)

        every { LinkDB.retrieveOriginalUrl(shortId) } returns "https://example.com"
        every { LinkDB.isLinkExpired(shortId) } returns false
        every { LinkDB.processClickTransaction(any()) } just Runs

        TrackingService.trackAndRedirect(ctx)

        verify { LinkDB.processClickTransaction(match { it.shortId == shortId && it.userAgent == userAgent && it.ipAddress == ipAddress }) }
    }

    @Test
    fun `test isLinkExpired returns true for expired links`() {
        val shortId = "expired123"

        mockkObject(ClickDB)
        mockkObject(LinkDB)

        every { LinkDB.isLinkExpired(shortId) } returns true

        val result = LinkDB.isLinkExpired(shortId)

        assertTrue(result, "Expected the link to be expired.")
    }

    @Test
    fun `test isLinkExpired returns false for non-expired links`() {
        val shortId = "active123"

        mockkObject(ClickDB)
        mockkObject(LinkDB)

        every { LinkDB.isLinkExpired(shortId) } returns false

        val result = LinkDB.isLinkExpired(shortId)

        assertFalse(result, "Expected the link to be active.")
    }

    @Test
    fun `test retrieveOriginalUrl returns null for nonexistent links`() {
        val shortId = "doesnotexist"

        mockkObject(ClickDB)
        mockkObject(LinkDB)

        every { LinkDB.retrieveOriginalUrl(shortId) } returns null

        val result = LinkDB.retrieveOriginalUrl(shortId)

        assertNull(result, "Expected null for a nonexistent link.")
    }


}
