package com.bokun.email.processor.config

import org.slf4j.LoggerFactory
import java.io.FileInputStream
import java.nio.file.*
import java.util.Properties
import kotlin.concurrent.thread

object ConfigLoader {
    private val logger = LoggerFactory.getLogger(ConfigLoader::class.java)
    const val CONFIG_FILE = "config.properties"
    val config = Properties()

    fun loadConfig() {
        try {
            FileInputStream(CONFIG_FILE).use { config.load(it) }
            logger.info("Configuration loaded successfully.")
        } catch (e: Exception) {
            logger.error("Failed to load configuration file", e)
        }
    }

    // If we like to watch the file we can call this to start a watcher thread
    fun watchConfigFile() {
        thread {
            try {
                val path = Paths.get(CONFIG_FILE).toAbsolutePath().parent
                val watchService = FileSystems.getDefault().newWatchService()
                path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY)

                while (true) {
                    val key = watchService.take()
                    for (event in key.pollEvents()) {
                        val changed = event.context() as Path
                        if (changed.toString() == CONFIG_FILE) {
                            logger.info("Configuration file changed. Reloading...")
                            loadConfig()
                        }
                    }
                    key.reset()
                }
            } catch (e: Exception) {
                logger.error("Failed to watch configuration file", e)
            }
        }
    }
}
