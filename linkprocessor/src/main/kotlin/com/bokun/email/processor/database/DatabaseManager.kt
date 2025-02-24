package com.bokun.email.processor.database

import com.bokun.email.processor.config.ConfigLoader
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.SQLException
import java.sql.Timestamp
import java.util.Properties

object DatabaseManager {
    private val logger = LoggerFactory.getLogger(DatabaseManager::class.java)
    private var connection: Connection? = null

    fun initializeDatabase() {
        reconnectDatabase()
    }

    fun reconnectDatabase() {
        try {
            connection?.close()
            val props = Properties().apply {
                setProperty("foreign_keys", "true")
            }
            connection = DriverManager.getConnection(ConfigLoader.config.getProperty("database.url"), props)
            connection?.createStatement()?.use { stmt ->
                stmt.execute("CREATE TABLE IF NOT EXISTS links (id INTEGER PRIMARY KEY AUTOINCREMENT, shortId TEXT, originalUrl TEXT, expiration TIMESTAMP, clickCount INTEGER DEFAULT 0)")
                stmt.execute("CREATE TABLE IF NOT EXISTS clicks (id INTEGER PRIMARY KEY AUTOINCREMENT, shortId TEXT, userAgent TEXT, ipAddress TEXT, timestamp TEXT)")
            }
            logger.info("Database schema updated successfully.")
        } catch (e: SQLException) {
            logger.error("Database schema update failed", e)
        }
    }

    fun getConnection(): Connection? {
        if (connection == null || connection!!.isClosed) {
            connection = DriverManager.getConnection("jdbc:sqlite:email_processor.db")
        }
        return connection
    }

    fun createLink(shortId: String, originalUrl: String, expiration: Timestamp?): Boolean {
        return try {
            val query = "INSERT INTO links (shortId, originalUrl, expiration, clickCount) VALUES (?, ?, ?, 0)"
            connection?.prepareStatement(query)?.use { pstmt ->
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
}
