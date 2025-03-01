package com.bokun.email.processor.database

import com.bokun.email.processor.config.ConfigLoader
import com.bokun.email.processor.database.DatabaseManager.getConnection
import com.bokun.email.processor.model.Click
import com.bokun.email.processor.model.Link
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException

object LinkDB {
    private val logger = LoggerFactory.getLogger(LinkDB::class.java)

    fun storeLinks(links: List<Link>) {
        try {
            val connection = getConnection()
            connection?.autoCommit = false

            val validDays = ConfigLoader.config.getProperty("link.valid.days", "5").toLong()
            val expirationTime = System.currentTimeMillis() + (validDays * 24 * 60 * 60 * 1000)

            connection?.prepareStatement("INSERT INTO links (shortId, originalUrl, expiration, clickCount) VALUES (?, ?, ?, ?)")
                ?.use { pstmt ->
                    for (link in links) {
                        pstmt.setString(1, link.shortId)
                        pstmt.setString(2, link.originalUrl)
                        pstmt.setLong(3, expirationTime)
                        pstmt.setInt(4, link.clickCount)
                        pstmt.addBatch()
                    }
                    pstmt.executeBatch()
                }
            connection?.commit()
        } catch (e: SQLException) {
            logger.error("Failed to store links", e)
            getConnection()?.rollback()
        } finally {
            getConnection()?.autoCommit = true
        }
    }

    fun getAllLinks(): List<Link> {
        val links = mutableListOf<Link>()
        val query = "SELECT id, shortId, originalUrl, expiration, clickCount FROM links"

        try {
            getConnection()?.prepareStatement(query)?.use { pstmt ->
                pstmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        links.add(
                            Link(
                                id = rs.getInt("id"),
                                shortId = rs.getString("shortId"),
                                originalUrl = rs.getString("originalUrl"),
                                expiration = rs.getLong("expiration"),
                                clickCount = rs.getInt("clickCount")
                            )
                        )
                    }
                }
            }
        } catch (e: SQLException) {
            logger.error("Failed to fetch links from database", e)
        }

        return links
    }

    fun getLinkByShortId(shortId: String): Link? {
        var link: Link? = null
        val query = "SELECT id, shortId, originalUrl, expiration, clickCount FROM links WHERE shortId = ?"

        try {
            getConnection()?.prepareStatement(query)?.use { pstmt ->
                pstmt.setString(1, shortId)
                pstmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        link = Link(
                            id = rs.getInt("id"),
                            shortId = rs.getString("shortId"),
                            originalUrl = rs.getString("originalUrl"),
                            expiration = rs.getLong("expiration"),
                            clickCount = rs.getInt("clickCount")
                        )
                    }
                }
            }
        } catch (e: SQLException) {
            logger.error("Error fetching link by shortId: ${e.message}")
        }

        return link
    }

    fun incrementClickCount(shortId: String) {
        try {
            getConnection()
                ?.prepareStatement("UPDATE links SET clickCount = clickCount + 1 WHERE shortId = ?")?.use { pstmt ->
                    pstmt.setString(1, shortId)
                    pstmt.executeUpdate()
                }
            logger.info("Incremented click count for {}", shortId)
        } catch (e: SQLException) {
            logger.error("Failed to update click count for {}", shortId, e)
        }
    }

    fun retrieveOriginalUrl(shortId: String): String? {
        return try {
            getConnection()?.prepareStatement("SELECT originalUrl FROM links WHERE shortId = ?")
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

    fun isLinkExpired(shortId: String): Boolean {
        return try {
            getConnection()?.prepareStatement("SELECT expiration FROM links WHERE shortId = ?")
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

    fun processClickTransaction(click: Click) {
        val connection: Connection? = getConnection()
        if (connection == null) {
            logger.error("Database connection is null! Cannot process click transaction.")
            return
        }

        try {
            connection.autoCommit = false

            // Store the click event in clicks table
            connection.prepareStatement(
                "INSERT INTO clicks (shortId, userAgent, ipAddress, timestamp) VALUES (?, ?, ?, ?)"
            ).use { pstmt ->
                pstmt.setString(1, click.shortId)
                pstmt.setString(2, click.userAgent)
                pstmt.setString(3, click.ipAddress)
                pstmt.setLong(4, click.timestamp)
                pstmt.executeUpdate()
            }

            // Increment click count in links table
            connection.prepareStatement(
                "UPDATE links SET clickCount = clickCount + 1 WHERE shortId = ?"
            ).use { pstmt ->
                pstmt.setString(1, click.shortId)
                pstmt.executeUpdate()
            }

            connection.commit()
            logger.info("Transaction committed: Click stored and count updated for {}", click.shortId)
        } catch (e: SQLException) {
            connection.rollback()
            logger.error("Transaction failed! Rolling back. Error: {}", e.message)
        } finally {
            connection.autoCommit = true
        }
    }
}
