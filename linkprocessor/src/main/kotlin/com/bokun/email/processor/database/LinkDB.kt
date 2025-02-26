package com.bokun.email.processor.database

import com.bokun.email.processor.database.DatabaseManager.getConnection
import com.bokun.email.processor.model.Link
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.time.LocalDateTime

object LinkDB {
    private val logger = LoggerFactory.getLogger(LinkDB::class.java)

    fun storeLinks(links: List<Link>) {
        var connection = getConnection()
        try {
            connection?.use { connection ->
                connection.autoCommit = false
                val query = "INSERT INTO links (shortId, originalUrl, expiration, clickCount) VALUES (?, ?, ?, ?)"

                connection.prepareStatement(query).use { pstmt ->
                    for (link in links) {
                        pstmt.setString(1, link.shortId)
                        pstmt.setString(2, link.originalUrl)
                        pstmt.setTimestamp(3, java.sql.Timestamp.valueOf(link.expiration))
                        pstmt.setInt(4, link.clickCount)
                        pstmt.addBatch()
                    }
                    pstmt.executeBatch()
                }
                connection.commit()
            }
        } catch (e: SQLException) {
            logger.error("Failed to store link", e)
        }
    }

    fun getAllLinks(): List<Link> {
        val links = mutableListOf<Link>()
        val query = "SELECT id, shortId, originalUrl, expiration, clickCount FROM links"

        try {
            getConnection()?.prepareStatement(query)?.use { pstmt ->
                pstmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val expirationTimestamp = rs.getTimestamp("expiration")
                        val expiration = expirationTimestamp?.toLocalDateTime() ?: LocalDateTime.now()
                            .plusDays(30) // Default if null

                        links.add(
                            Link(
                                id = rs.getInt("id"),
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
        val query = "SELECT id, shortId, originalUrl, expiration, clickCount FROM links WHERE shortId = ?"

        try {
            getConnection()?.prepareStatement(query)?.use { pstmt ->
                pstmt.setString(1, shortId)
                pstmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        val expirationTimestamp = rs.getTimestamp("expiration")
                        val expiration = expirationTimestamp?.toLocalDateTime() ?: LocalDateTime.now()
                            .plusDays(30) // Default if null

                        link = Link(
                            id = rs.getInt("id"),
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
