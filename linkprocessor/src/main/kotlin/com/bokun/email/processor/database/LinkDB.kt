package com.bokun.email.processor.database

import com.bokun.email.processor.database.DatabaseManager.getConnection
import com.bokun.email.processor.model.Link
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.sql.Timestamp
import java.time.LocalDateTime


object LinkDB {
    private val logger = LoggerFactory.getLogger(LinkDB::class.java)

    fun createLink(shortId: String, originalUrl: String, expiration: Timestamp?): Boolean {
        return try {
            val query = "INSERT INTO links (shortId, originalUrl, expiration, clickCount) VALUES (?, ?, ?, 0)"
            getConnection()?.prepareStatement(query)?.use { pstmt ->
                pstmt.setString(1, shortId)
                pstmt.setString(2, originalUrl)
                pstmt.setTimestamp(3, expiration)
                pstmt.executeUpdate()
            }
            logger.info("Created new link: {} -> {} with expiration: {}", shortId, originalUrl, expiration)
            true
        } catch (e: SQLException) {
            logger.error("Failed to create link", e)
            false
        }
    }

    fun getAllLinks(): List<Link> {
        val links = mutableListOf<Link>()
        val query = "SELECT shortId, originalUrl, expiration, clickCount FROM links"

        try {
            getConnection()?.prepareStatement(query)?.use { pstmt ->
                pstmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val expirationTimestamp = rs.getTimestamp("expiration")
                        val expiration = expirationTimestamp?.toLocalDateTime() ?: LocalDateTime.now().plusDays(30) // Default if null

                        links.add(
                            Link(
                                shortId = rs.getString("shortId"),
                                originalUrl = rs.getString("originalUrl"),
                                expiration = expiration,
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
        val query = "SELECT shortId, originalUrl, expiration, clickCount FROM links WHERE shortId = ?"

        try {
            getConnection()?.prepareStatement(query)?.use { pstmt ->
                pstmt.setString(1, shortId)
                pstmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        val expirationTimestamp = rs.getTimestamp("expiration")
                        val expiration = expirationTimestamp?.toLocalDateTime() ?: LocalDateTime.now().plusDays(30) // Default if null

                        link = Link(
                            shortId = rs.getString("shortId"),
                            originalUrl = rs.getString("originalUrl"),
                            expiration = expiration,
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

}
