package com.bokun.email.processor.app

import com.bokun.email.processor.config.ConfigLoader
import com.bokun.email.processor.database.DatabaseManager
import io.javalin.Javalin
import org.slf4j.LoggerFactory

object App {
    private val logger = LoggerFactory.getLogger(App::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        ConfigLoader.loadConfig()
        DatabaseManager.initializeDatabase()
        val app = Javalin.create().start(8080)
        Router.registerRoutes(app)
        logger.info("Server started on port 8080")
    }
}
