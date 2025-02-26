package com.bokun.email.processor.services

import com.bokun.email.processor.database.DatabaseManager
import com.bokun.email.processor.database.LinkDB

import com.bokun.email.processor.model.Link
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule

import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.util.UUID
import io.javalin.http.Context
import io.javalin.http.bodyAsClass

object LinkService {
    private val logger = LoggerFactory.getLogger(LinkService::class.java)

    fun processEmailOLD(ctx: Context) {
        val emailContent = ctx.body()
        val linksToStore = mutableListOf<Link>()

        // Replace links in email content and generate shortened links
        val processedContent = emailContent.replace(Regex("(https?://[\\w./?=]+)")) { match ->
            val shortId = UUID.randomUUID().toString().substring(0, 8)
            val link = Link(0, shortId, match.groupValues[1])
            linksToStore.add(link)
            "${com.bokun.email.processor.config.ConfigLoader.config.getProperty("server.url")}/api/r/$shortId"
        }

        // Store all links in batch
        if (linksToStore.isNotEmpty()) {
            LinkDB.storeLinks(linksToStore)
        }

        // Return JSON response so frontend can update correctly
        ctx.json(mapOf("processedContent" to processedContent))

        logger.info("Processed email content and replaced links.")
    }

    fun processEmail(ctx: Context) {
        val requestBody = ctx.bodyAsClass<Map<String, String>>()
        val emailContent = requestBody["content"] ?: ""

        val linksToStore = mutableListOf<Link>()

        // Replace links with shortened versions
        val processedContent = emailContent.replace(Regex("(https?://[\\w./?=]+)")) { match ->
            val shortId = UUID.randomUUID().toString().substring(0, 8)
            val link = Link(0, shortId, match.groupValues[1])
            linksToStore.add(link)
            "<a href=\"/api/confirm/$shortId\" target=\"_blank\">${match.groupValues[1]}</a>"
        }

        // Store links in the database
        if (linksToStore.isNotEmpty()) {
            LinkDB.storeLinks(linksToStore)
        }

        // Return HTML email format
        val formattedEmail = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Processed Email</title>
        </head>
        <body>
            <h2>Processed Email</h2>
            <p style="white-space: pre-line;">$processedContent</p>
        </body>
        </html>
    """.trimIndent()

        ctx.contentType("text/html").result(formattedEmail)
    }

    fun getAllLinksJson(): String {
        val links = LinkDB.getAllLinks()
        // Jackson does not support LocalDateTime by default
        val mapper = jacksonObjectMapper().registerModule(JavaTimeModule())
        return mapper.writeValueAsString(links)
    }

    fun getLinkJson(shortId: String): String {
        val link = LinkDB.getLinkByShortId(shortId)
        val mapper = jacksonObjectMapper().registerModule(JavaTimeModule())
        return mapper.writeValueAsString(link)
    }

    fun getLinkObject(shortId: String): Link? {
        return LinkDB.getLinkByShortId(shortId)
    }

    fun getConfirmationPage(ctx: Context) {
        val shortId = ctx.pathParam("shortId")
        val link = getLinkObject(shortId)

        if (link == null) {
            ctx.status(404).result("Link not found")
        } else {
            var html = this::class.java.getResource("/confirm.html")!!.readText()
            html = html.replace("{{shortId}}", shortId)
                .replace("{{originalUrl}}", link.originalUrl)

            ctx.contentType("text/html").result(html)
        }
    }
}
