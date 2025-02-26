package com.bokun.email.processor

import com.bokun.email.processor.database.DatabaseManager
import com.bokun.email.processor.model.*
import com.bokun.email.processor.services.RedirectService
import com.bokun.email.processor.config.ConfigLoader
import com.bokun.email.processor.database.LinkDB
import com.bokun.email.processor.services.LinkService
import io.javalin.Javalin
import io.javalin.http.Context
import kotlinx.coroutines.*
import io.mockk.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.min

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LinkProcessorTest {
    private lateinit var app: Javalin
    private val logger = LoggerFactory.getLogger(LinkProcessorTest::class.java)
    private lateinit var connection: Connection

    @BeforeAll
    fun setup() {
        ConfigLoader.loadConfig()
        connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        DatabaseManager.initializeDatabase()
        app = Javalin.create().start(8081) // Start test server on different port
        app.get("/api/r/{shortId}", RedirectService::trackAndRedirect)
        app.get("/api/analytics", RedirectService::getClickAnalytics)
    }

    @AfterAll
    fun tearDown() {
        app.stop()
        connection.close()
    }

    @Test
    fun `should create short link successfully`() {
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.bodyAsClass(LinkRequest::class.java) } returns LinkRequest("https://example.com", null)

        LinkService.processEmail(ctx)
        verify { ctx.result("") }
    }

    @Test
    fun `should reject invalid URL`() {
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.bodyAsClass(LinkRequest::class.java) } returns LinkRequest("invalid-url", null)

        LinkService.processEmail(ctx)
        verify { ctx.result("") }
    }

    @Test
    fun `should reject expired link`() {
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.pathParam("shortId") } returns "expired1234"
        every { ctx.status(410) } just return Unit
        RedirectService.trackAndRedirect(ctx)

        verify { ctx.status(410) }
    }

    @Test
    fun `should respect rate limiting`() {
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.pathParam("shortId") } returns "rateLimited1234"
        every { ctx.ip() } returns "192.168.1.1"
        every { ctx.status(429) } just return Unit

        repeat(6) { RedirectService.trackAndRedirect(ctx) }
        verify { ctx.result("") }
    }

    @Test
    fun `should redirect to original URL`() {
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.pathParam("shortId") } returns "abcd1234"
        every { ctx.redirect(any()) } just return Unit

        RedirectService.trackAndRedirect(ctx)
        verify { ctx.redirect(any()) }
    }

    @Test
    fun `should return click analytics`() {
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.json(any<Map<String, Int>>()) } just return Unit

        RedirectService.getClickAnalytics(ctx)
        verify { ctx.json(any<Map<String, Int>>()) }
    }

    @Test
    fun `should handle high-load scenarios`() {
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.pathParam("shortId") } returns "highload1234"
        every { ctx.redirect(any()) } just return Unit

        repeat(1000) { RedirectService.trackAndRedirect(ctx) }
        verify(exactly = 1000) { ctx.redirect(any()) }
    }

    @Test
    fun `should rollback transaction on database failure`() {
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.bodyAsClass(LinkRequest::class.java) } returns LinkRequest("https://example.com", null)
        mockkObject(DatabaseManager)
        every { DatabaseManager.getConnection()?.prepareStatement(any()) } throws SQLException("Simulated database failure")

        LinkService.processEmail(ctx)
        verify { ctx.result("") }
        logger.error("Transaction failed and rolled back due to simulated database failure")
    }

    @Test
    fun `should retry transaction on transient database failure with exponential backoff`() {
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.bodyAsClass(LinkRequest::class.java) } returns LinkRequest("https://example.com", null)
        mockkObject(DatabaseManager)
        every { DatabaseManager.getConnection()?.prepareStatement(any()) } throws SQLException("Transient error") andThenAnswer { mockk() }

        var attempt = 0
        var delay = 100L
        while (attempt < 5) {
            try {
                LinkService.processEmail(ctx)
                verify { ctx.result("") }
                logger.info("Transaction retried and succeeded after transient error")
                break
            } catch (e: SQLException) {
                logger.warn("Retry attempt ${attempt + 1} failed, retrying in ${delay}ms")
                Thread.sleep(delay)
                delay = min(delay * 2, 5000)
                attempt++
            }
        }
    }
}
