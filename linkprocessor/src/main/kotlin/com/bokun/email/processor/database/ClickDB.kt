package com.bokun.email.processor.database

import com.bokun.email.processor.config.ConfigLoader
import com.bokun.email.processor.model.Click
import org.slf4j.LoggerFactory
import java.sql.SQLException

object ClickDB {
    private val logger = LoggerFactory.getLogger(ClickDB::class.java)

    fun storeClick(clickEvent: Click) {
        try {
            DatabaseManager.getConnection()
                ?.prepareStatement("INSERT INTO clicks (shortId, userAgent, ipAddress, timestamp) VALUES (?, ?, ?, ?)")
                ?.use { pstmt ->
                    pstmt.setString(1, clickEvent.shortId)
                    pstmt.setString(2, clickEvent.userAgent)
                    pstmt.setString(3, clickEvent.ipAddress)
                    pstmt.setLong(4, clickEvent.timestamp)
                    pstmt.executeUpdate()
                }
            logger.info("Stored click event: {} from IP {}", clickEvent.shortId, clickEvent.ipAddress)
        } catch (e: SQLException) {
            logger.error("Failed to store click event", e)
        }
    }

    fun isLinkExpired(shortId: String): Boolean {
        return try {
            DatabaseManager.getConnection()?.prepareStatement("SELECT expiration FROM links WHERE shortId = ?")
                ?.use { pstmt ->
                    pstmt.setString(1, shortId)
                    pstmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            val expirationMillis = rs.getLong("expiration")
                            expirationMillis < System.currentTimeMillis() // Compare against current time in millis
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

    fun retrieveOriginalUrl(shortId: String): String? {
        return try {
            DatabaseManager.getConnection()?.prepareStatement("SELECT originalUrl FROM links WHERE shortId = ?")
                ?.use { pstmt ->
                    pstmt.setString(1, shortId)
                    pstmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            rs.getString("originalUrl")
                        } else {
                            null
                        }

                    }
                }
        } catch (e: SQLException) {
            logger.error("Failed to retrieve original URL for {}", shortId, e)
            null
        }
    }

    fun getGroupOfClickCounts(): Map<String, Int> {
        val analytics = mutableMapOf<String, Int>()
        try {
            DatabaseManager.getConnection()
                ?.prepareStatement("SELECT shortId, COUNT(*) as clickCount FROM clicks GROUP BY shortId")
                ?.use { pstmt ->
                    pstmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            analytics[rs.getString("shortId")] = rs.getInt("clickCount")
                        }
                    }
                }
            logger.info("Retrieved click analytics")
        } catch (e: SQLException) {
            logger.error("Failed to fetch click count", e)
        }
        return analytics
    }

    fun hasExceededRateLimit(shortId: String): Boolean {
        return try {
            DatabaseManager.getConnection()?.prepareStatement("SELECT COUNT(*) FROM clicks WHERE shortId = ? AND timestamp > ?")
                ?.use { pstmt ->
                    pstmt.setString(1, shortId)
                    pstmt.setLong(2, System.currentTimeMillis() - 60_000) // Last minute
                    pstmt.executeQuery().use { rs ->
                        rs.next() && rs.getInt(1) >= ConfigLoader.config.getProperty("rate.limit", "5").toInt()
                    }
                } ?: false
        } catch (e: SQLException) {
            logger.error("Failed to check rate limit for {}", shortId, e)
            false
        }
    }

    fun getClicksPerDay(): Map<String, Map<String, Int>> {
        val analytics = mutableMapOf<String, MutableMap<String, Int>>()

        try {
            DatabaseManager.getConnection()
                ?.prepareStatement(
                    "SELECT shortId, DATE(timestamp / 1000, 'unixepoch') as clickDate, COUNT(*) as clickCount " +
                            "FROM clicks GROUP BY shortId, clickDate ORDER BY clickDate DESC"
                )
                ?.use { pstmt ->
                    pstmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val shortId = rs.getString("shortId")
                            val date = rs.getString("clickDate")
                            val count = rs.getInt("clickCount")

                            analytics.computeIfAbsent(shortId) { mutableMapOf() }[date] = count
                        }
                    }
                }
            logger.info("Retrieved click counts per day")
        } catch (e: SQLException) {
            logger.error("Failed to fetch click counts per day", e)
        }
        return analytics
    }
}
