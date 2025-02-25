package com.bokun.email.processor.services

import com.bokun.email.processor.database.DatabaseManager
import com.bokun.email.processor.database.LinkDB
import com.bokun.email.processor.model.LinkRequest
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import java.util.UUID
import io.javalin.http.Context

object LinkCreationService {
    private val logger = LoggerFactory.getLogger(LinkCreationService::class.java)

    fun createShortLink(ctx: Context) {
        val requestBody = ctx.bodyAsClass(LinkRequest::class.java)
        val shortId = UUID.randomUUID().toString().substring(0, 8)
        
        if (!isValidUrl(requestBody.originalUrl)) {
            ctx.status(400).result("Invalid URL format")
            return
        }
        
        val expiration = parseExpiration(requestBody.expiration)
        val success = LinkDB.createLink(shortId, requestBody.originalUrl, expiration)
        
        if (success) {
            ctx.status(201).json(mapOf("shortId" to shortId, "originalUrl" to requestBody.originalUrl, "expiration" to expiration))
        } else {
            ctx.status(500).result("Failed to create short link")
        }
    }

    private fun isValidUrl(url: String): Boolean {
        return url.matches(Regex("^(https?://)[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}(/.*)?$"))
    }

    private fun parseExpiration(expiration: String?): Timestamp? {
        return try {
            expiration?.let { Timestamp.valueOf(LocalDateTime.parse(it)) }
        } catch (e: DateTimeParseException) {
            logger.warn("Invalid expiration date format: {}", expiration)
            null
        }
    }
}
