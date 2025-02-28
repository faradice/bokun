package com.bokun.email.processor.services

import com.bokun.email.processor.database.LinkDB

import com.bokun.email.processor.model.Link
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule

import org.slf4j.LoggerFactory
import java.util.UUID
import io.javalin.http.Context
import io.javalin.http.bodyAsClass

object LinkService {
    private val logger = LoggerFactory.getLogger(LinkService::class.java)

    fun processEmail(ctx: Context) {
        val requestBody = ctx.bodyAsClass<Map<String, String>>()
        val emailContent = requestBody["content"] ?: ""

        val linksToStore = mutableListOf<Link>()

        val processedContent = emailContent.replace(Regex("(https?://[\\w./?=]+)")) { match ->
            val shortId = UUID.randomUUID().toString().substring(0, 8)
            val link = Link(0, shortId, match.groupValues[1])
            linksToStore.add(link)
            "<a href=\"#\" class=\"shortened-link\" data-shortid=\"$shortId\">${match.groupValues[1]}</a>"
        }

        if (linksToStore.isNotEmpty()) {
            LinkDB.storeLinks(linksToStore)
        }

        val template = this::class.java.getResource("/processed_email.html")!!.readText()
        val formattedEmail = template.replace("{{processedContent}}", processedContent)

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
