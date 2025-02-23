package com.bokun.email.processor.services

import com.bokun.email.processor.database.DatabaseManager
import com.bokun.email.processor.model.Link
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.util.UUID
import io.javalin.http.Context

object LinkService {
    private val logger = LoggerFactory.getLogger(LinkService::class.java)

    fun processEmail(ctx: Context) {
        val emailContent = ctx.body()
        val processedContent = emailContent.replace(Regex("(https?://[\\w./?=]+)")) {
            val shortId = UUID.randomUUID().toString().substring(0, 8)
            storeLink(Link(shortId, it.groupValues[1]))
            "${com.bokun.email.processor.config.ConfigLoader.config.getProperty("server.url")}/api/r/$shortId"
        }
        ctx.result(processedContent)
        logger.info("Processed email content and replaced links.")
    }

    private fun storeLink(link: Link) {
        try {
            DatabaseManager.getConnection()?.prepareStatement("INSERT INTO links (shortId, originalUrl) VALUES (?, ?)")?.use { pstmt ->
                pstmt.setString(1, link.shortId)
                pstmt.setString(2, link.originalUrl)
                pstmt.executeUpdate()
            }
            logger.info("Stored link: {} -> {}", link.shortId, link.originalUrl)
        } catch (e: SQLException) {
            logger.error("Failed to store link", e)
        }
    }
}
