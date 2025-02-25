package com.bokun.email.processor.services

import com.bokun.email.processor.database.DatabaseManager
import com.bokun.email.processor.model.Click
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import io.javalin.http.Context

object RedirectService {
    private val logger = LoggerFactory.getLogger(RedirectService::class.java)
    private val requestTracker = ConcurrentHashMap<String, MutableList<LocalDateTime>>()
    private const val RATE_LIMIT = 5 // Max clicks per minute per IP

    fun trackAndRedirect(ctx: Context) {
        val shortId = ctx.pathParam("shortId")
        val userAgent = ctx.header("User-Agent") ?: "Unknown"
        val ipAddress = ctx.ip()

        // Rate limiting: Allow only RATE_LIMIT clicks per minute per IP
        val now = LocalDateTime.now()
        val requestTimes = requestTracker.getOrDefault(ipAddress, mutableListOf())
        requestTimes.removeIf { it.plusMinutes(1).isBefore(now) }
        if (requestTimes.size >= RATE_LIMIT) {
            logger.warn("Rate limit exceeded for IP: {}", ipAddress)
            ctx.status(429).result("Too many requests. Try again later.")
            return
        }
        requestTimes.add(now)
        requestTracker[ipAddress] = requestTimes

        val originalUrl = retrieveOriginalUrl(shortId)
        if (originalUrl != null) {
            if (isLinkExpired(shortId)) {
                logger.warn("Attempt to access expired link: {}", shortId)
                ctx.status(410).result("This link has expired.")
                return
            }
            storeClick(Click(shortId, userAgent, ipAddress, now))
            incrementClickCount(shortId)
            logger.info("Redirecting {} to {}", shortId, originalUrl)
            ctx.redirect(originalUrl)
        } else {
            logger.warn("Invalid link access attempt: {}", shortId)
            ctx.result("Invalid link")
        }
    }

    private fun retrieveOriginalUrl(shortId: String): String? {
        return try {
                DatabaseManager.getConnection()?.prepareStatement("SELECT originalUrl FROM links WHERE shortId = ?")?.use { pstmt ->
                pstmt.setString(1, shortId)
                pstmt.executeQuery().use { rs ->
                    if (rs.next())  {
                        rs.getString("originalUrl")
                    } else  {
                        null
                    }

                }
            }
        } catch (e: SQLException) {
            logger.error("Failed to retrieve original URL for {}", shortId, e)
            null
        }
    }

    private fun storeClick(clickEvent: Click) {
        try {
            DatabaseManager.getConnection()?.prepareStatement("INSERT INTO clicks (shortId, userAgent, ipAddress, timestamp) VALUES (?, ?, ?, ?)")?.use { pstmt ->
                pstmt.setString(1, clickEvent.shortId)
                pstmt.setString(2, clickEvent.userAgent)
                pstmt.setString(3, clickEvent.ipAddress)
                pstmt.setString(4, clickEvent.timestamp.toString())
                pstmt.executeUpdate()
            }
            logger.info("Stored click event: {} from IP {}", clickEvent.shortId, clickEvent.ipAddress)
        } catch (e: SQLException) {
            logger.error("Failed to store click event", e)
        }
    }

    private fun incrementClickCount(shortId: String) {
        try {
            DatabaseManager.getConnection()?.prepareStatement("UPDATE links SET clickCount = clickCount + 1 WHERE shortId = ?")?.use { pstmt ->
                pstmt.setString(1, shortId)
                pstmt.executeUpdate()
            }
            logger.info("Incremented click count for {}", shortId)
        } catch (e: SQLException) {
            logger.error("Failed to update click count for {}", shortId, e)
        }
    }

    private fun isLinkExpired(shortId: String): Boolean {
        return try {
            DatabaseManager.getConnection()?.prepareStatement("SELECT expiration FROM links WHERE shortId = ?")?.use { pstmt ->
                pstmt.setString(1, shortId)
                pstmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        val expiration = rs.getTimestamp("expiration")?.toLocalDateTime()
                        expiration?.isBefore(LocalDateTime.now()) ?: false  // ✅ Ensures a non-null Boolean
                    } else {
                        false
                    }
                }
            } ?: false
        } catch (e: SQLException) {
            logger.error("Failed to check expiration for {}", shortId, e)
            false
        }
    }

    fun getClickAnalytics(ctx: Context) {
        val analytics = mutableMapOf<String, Int>()
        try {
            DatabaseManager.getConnection()?.prepareStatement("SELECT shortId, COUNT(*) as clickCount FROM clicks GROUP BY shortId")?.use { pstmt ->
                pstmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        analytics[rs.getString("shortId")] = rs.getInt("clickCount")
                    }
                }
            }
            logger.info("Retrieved click analytics")
            ctx.json(analytics)
        } catch (e: SQLException) {
            logger.error("Failed to fetch click analytics", e)
            ctx.status(500).result("Failed to retrieve analytics")
        }
    }
}
