package com.bokun.email.processor.database

import com.bokun.email.processor.config.ConfigLoader
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

object DatabaseManager {
    private val logger = LoggerFactory.getLogger(DatabaseManager::class.java)
    private var connection: Connection? = null

    fun initializeDatabase() {
        reconnectDatabase()
    }

    fun getConnection(): Connection? {
        if (connection == null || connection!!.isClosed) {
           reconnectDatabase()
        }
        return connection
    }

    private fun reconnectDatabase() {
        try {
            connection?.close()
            connection = DriverManager.getConnection(ConfigLoader.config.getProperty("database.url"))
            connection?.createStatement()?.use { stmt ->
                stmt.execute("CREATE TABLE IF NOT EXISTS links (id INTEGER PRIMARY KEY AUTOINCREMENT, shortId TEXT NOT NULL, originalUrl TEXT NOT NULL, expiration INTEGER NOT NULL, clickCount INTEGER DEFAULT 0)")
                stmt.execute("CREATE TABLE IF NOT EXISTS clicks (id INTEGER PRIMARY KEY AUTOINCREMENT, shortId TEXT, userAgent TEXT, ipAddress TEXT, timestamp INTEGER NOT NULL)")
            }
            logger.info("Database schema updated successfully.")
        } catch (e: SQLException) {
            logger.error("Database schema update failed", e)
        }
    }

}
