package com.bokun.email.processor.database

import com.bokun.email.processor.config.ConfigLoader
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.*

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
}
