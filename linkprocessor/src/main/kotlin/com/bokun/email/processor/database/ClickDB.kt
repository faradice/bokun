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

    fun getUniqueVisitorsPerLink(): Map<String, Int> {
        val visitors = mutableMapOf<String, Int>()
        try {
            DatabaseManager.getConnection()?.prepareStatement(
                "SELECT shortId, COUNT(DISTINCT ipAddress) as visitorCount FROM clicks GROUP BY shortId"
            )?.use { pstmt ->
                pstmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        visitors[rs.getString("shortId")] = rs.getInt("visitorCount")
                    }
                }
            }
        } catch (e: SQLException) {
            logger.error("Failed to fetch unique visitors", e)
        }
        return visitors
    }

    fun getClicksPerHour(): Map<String, Map<String, Int>> {
        val clicksPerHour = mutableMapOf<String, MutableMap<String, Int>>()
        try {
            DatabaseManager.getConnection()?.prepareStatement(
                "SELECT shortId, strftime('%H', timestamp / 1000, 'unixepoch') as hour, COUNT(*) as clickCount " +
                        "FROM clicks GROUP BY shortId, hour"
            )?.use { pstmt ->
                pstmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val shortId = rs.getString("shortId")
                        val hour = rs.getString("hour")
                        val count = rs.getInt("clickCount")

                        clicksPerHour.computeIfAbsent(shortId) { mutableMapOf() }[hour] = count
                    }
                }
            }
        } catch (e: SQLException) {
            logger.error("Failed to fetch hourly clicks", e)
        }
        return clicksPerHour
    }

    fun getFrequentVisitors(): List<Pair<String, Int>> {
        val visitors = mutableListOf<Pair<String, Int>>()
        try {
            DatabaseManager.getConnection()?.prepareStatement(
                "SELECT ipAddress, COUNT(*) as clickCount FROM clicks GROUP BY ipAddress ORDER BY clickCount DESC"
            )?.use { pstmt ->
                pstmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        visitors.add(rs.getString("ipAddress") to rs.getInt("clickCount"))
                    }
                }
            }
        } catch (e: SQLException) {
            logger.error("Failed to fetch frequent visitors", e)
        }
        return visitors
    }

}
