package com.bokun.email.processor.app

import com.bokun.email.processor.config.ConfigLoader
import com.bokun.email.processor.database.DatabaseManager
import com.bokun.email.processor.services.LinkCreationService
import com.bokun.email.processor.services.LinkService
import com.bokun.email.processor.services.RedirectService
import io.javalin.Javalin
import org.slf4j.LoggerFactory

object App {
    private val logger = LoggerFactory.getLogger(App::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        ConfigLoader.loadConfig()
        DatabaseManager.initializeDatabase()
        val app = Javalin.create().start(8080)

        app.post("/api/links", LinkCreationService::createShortLink)

        app.get("/api/r/{shortId}", RedirectService::trackAndRedirect)
        app.get("/api/analytics", RedirectService::getClickAnalytics)

        app.post("/api/process-email", LinkService::processEmail)

        // Simple test form that calls the process-email api
        app.get("/test-email") { ctx ->
            ctx.contentType("text/html").result(this::class.java.getResource("/email_test_form.html")!!.readText())
        }

        app.get("/api/links") { ctx ->
            val links = LinkService.getAllLinks()
            ctx.json(links)
        }

        app.get("/confirm/{shortId}") { ctx ->
            val shortId = ctx.pathParam("shortId")
            val link = LinkService.getLinkByShortId(shortId)

            if (link == null) {
                ctx.status(404).result("Link not found")
            } else {
                ctx.contentType("text/html").result("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Confirm Redirect</title>
            </head>
            <body>
                <h2>Redirect Confirmation</h2>
                <p>Do you want to proceed to: <strong>${link["originalUrl"]}</strong>?</p>
                <button onclick="window.location.href='/api/r/$shortId'">Yes, Proceed</button>
                <button onclick="window.location.href='/'">No, Cancel</button>
            </body>
            </html>
        """.trimIndent())
            }
        }


        logger.info("Server started on port 8080")
    }
}
