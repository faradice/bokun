package com.bokun.email.processor.app

import com.bokun.email.processor.services.LinkCreationService
import com.bokun.email.processor.services.RedirectService
import io.javalin.Javalin
import org.slf4j.LoggerFactory
import com.bokun.email.processor.config.ConfigLoader

object App {
    private val logger = LoggerFactory.getLogger(App::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        ConfigLoader.loadConfig()
        val app = Javalin.create().start(8080)

        app.post("/api/links", LinkCreationService::createShortLink)
        app.get("/api/r/{shortId}", RedirectService::trackAndRedirect)
        app.get("/api/analytics", RedirectService::getClickAnalytics)

        logger.info("Server started on port 8080")
    }
}
