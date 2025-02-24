package com.bokun.email.processor.app

import com.bokun.email.processor.services.LinkCreationService
import com.bokun.email.processor.services.RedirectService
import io.javalin.Javalin
import org.slf4j.LoggerFactory
import com.bokun.email.processor.config.ConfigLoader
import com.bokun.email.processor.services.LinkService

object App {
    private val logger = LoggerFactory.getLogger(App::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        ConfigLoader.loadConfig()
        val app = Javalin.create().start(8080)

        app.post("/api/links", LinkCreationService::createShortLink)
        app.get("/api/r/{shortId}", RedirectService::trackAndRedirect)
        app.get("/api/analytics", RedirectService::getClickAnalytics)
        app.post("/api/process-email", LinkService::processEmail)

        // Simple test form that calls the process-email api
        app.get("/test-email") { ctx ->
            ctx.contentType("text/html").result(this::class.java.getResource("/email_test_form.html")!!.readText())
        }

        logger.info("Server started on port 8080")
    }
}
