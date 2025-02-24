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
        val linksToStore = mutableListOf<Link>()

        val processedContent = emailContent.replace(Regex("(https?://[\\w./?=]+)")) { match ->
            val shortId = UUID.randomUUID().toString().substring(0, 8)
            val link = Link(shortId, match.groupValues[1])
            linksToStore.add(link)
            "${com.bokun.email.processor.config.ConfigLoader.config.getProperty("server.url")}/api/r/$shortId"
        }

        if (linksToStore.isNotEmpty()) {
            storeLinks(linksToStore)
        }

        ctx.result(processedContent)
        logger.info("Processed email content and replaced links.")
    }

    fun storeLinks(links: List<Link>) {
        try {
            DatabaseManager.getConnection()?.use { connection ->
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
            println("Error storing links: ${e.message}")
        }
    }

    fun getAllLinks(): List<Map<String, Any>> {
        val links = mutableListOf<Map<String, Any>>()

        try {
            val query = "SELECT id, shortId, originalUrl FROM links"
            DatabaseManager.getConnection()?.prepareStatement(query)?.use { pstmt ->
                pstmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        links.add(
                            mapOf(
                                "id" to rs.getInt("id"),
                                "shortId" to rs.getString("shortId"),
                                "originalUrl" to rs.getString("originalUrl")
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            println("Error fetching links: ${e.message}")
        }

        return links
    }

    fun getLinkByShortId(shortId: String): Map<String, Any>? {
        var link: Map<String, Any>? = null

        try {
            DatabaseManager.getConnection()?.use { connection ->
                val query = "SELECT id, shortId, originalUrl FROM links WHERE shortId = ?"
                connection.prepareStatement(query).use { pstmt ->
                    pstmt.setString(1, shortId)
                    pstmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            link = mapOf(
                                "id" to rs.getInt("id"),
                                "shortId" to rs.getString("shortId"),
                                "originalUrl" to rs.getString("originalUrl")
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("Error fetching link: ${e.message}")
        }

        return link
    }

}
