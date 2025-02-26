package com.bokun.email.processor.services

import com.bokun.email.processor.config.ConfigLoader
import com.bokun.email.processor.database.ClickDB
import com.bokun.email.processor.database.LinkDB
import com.bokun.email.processor.model.Click
import io.javalin.http.Context
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

object RedirectService {
    private val logger = LoggerFactory.getLogger(RedirectService::class.java)
    private val requestTracker = ConcurrentHashMap<String, MutableList<LocalDateTime>>()
    private val RATE_LIMIT = ConfigLoader.config.getProperty("rate.limit", "5").toInt()

    fun trackAndRedirect(ctx: Context) {
        val shortId = ctx.pathParam("shortId")
        val userAgent = ctx.header("User-Agent") ?: "Unknown"
        val ipAddress = ctx.ip()

        // Rate limiting: Allow only RATE_LIMIT clicks minutes per IP
        val now = LocalDateTime.now()
        val requestTimes = requestTracker.getOrDefault(ipAddress, mutableListOf())
        requestTimes.removeIf { it.plusMinutes(RATE_LIMIT.toLong()).isBefore(now) }
        if (requestTimes.size >= RATE_LIMIT) {
            logger.warn("Rate limit exceeded for IP: {}", ipAddress)
            ctx.status(429).result("Too many requests. Try again later.")
            return
        }
        requestTimes.add(now)
        requestTracker[ipAddress] = requestTimes

        val originalUrl = ClickDB.retrieveOriginalUrl(shortId)
        if (originalUrl != null) {
            if (ClickDB.isLinkExpired(shortId)) {
                logger.warn("Attempt to access expired link: {}", shortId)
                ctx.status(410).result("This link has expired.")
                return
            }
            ClickDB.storeClick(Click(shortId, userAgent, ipAddress, System.currentTimeMillis()))
            LinkDB.incrementClickCount(shortId)
            logger.info("Redirecting {} to {}", shortId, originalUrl)
            ctx.redirect(originalUrl)
        } else {
            logger.warn("Invalid link access attempt: {}", shortId)
            ctx.result("Invalid link")
        }
    }

    fun getClickAnalytics(ctx: Context) {
        val clickData = ClickDB.getGroupOfClickCounts()
        val linkData = LinkDB.getAllLinks()
        val clicksPerDay = ClickDB.getClicksPerDay()

        var totalClicks = 0
        val topLinks = mutableListOf<Pair<String, Int>>()

        // Main Analytics Table (Short Links Click Data)
        val analyticsTableRows = linkData.joinToString("") { link ->
            val clickCount = clickData[link.shortId] ?: 0
            totalClicks += clickCount
            topLinks.add(link.shortId to clickCount)

            val isExpired = ClickDB.isLinkExpired(link.shortId)
            val expirationStatus = if (isExpired) "<span style='color: red;'>Expired</span>" else "<span style='color: green;'>Active</span>"

            """
        <tr>
            <td>${link.shortId}</td>
            <td><a href="/confirm/${link.shortId}" target="_blank">${link.originalUrl}</a></td>
            <td>${clickCount}</td>
            <td>${expirationStatus}</td>
        </tr>
        """.trimIndent()
        }

        // Most Clicked Links Table
        val mostClickedRows = topLinks.sortedByDescending { it.second }.take(5).joinToString("") { (shortId, count) ->
            "<tr><td>${shortId}</td><td>${count}</td></tr>"
        }

        // Clicks Per Day Table
        val clicksPerDayRows = clicksPerDay.flatMap { (shortId, dateClicks) ->
            dateClicks.map { (date, count) ->
                "<tr><td>${shortId}</td><td>${date}</td><td>${count}</td></tr>"
            }
        }.joinToString("")

        // Load HTML Template & Replace Placeholders
        val template = this::class.java.getResource("/analytics.html")!!.readText()
        val analyticsHtml = template
            .replace("{{analyticsTableRows}}", analyticsTableRows)
            .replace("{{totalClicks}}", totalClicks.toString())
            .replace("{{mostClickedRows}}", mostClickedRows)
            .replace("{{clicksPerDayRows}}", clicksPerDayRows)

        ctx.contentType("text/html").result(analyticsHtml)
    }
}
