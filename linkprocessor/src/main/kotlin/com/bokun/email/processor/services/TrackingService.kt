package com.bokun.email.processor.services

import com.bokun.email.processor.database.ClickDB
import com.bokun.email.processor.database.LinkDB
import com.bokun.email.processor.model.Click
import io.javalin.http.Context
import org.slf4j.LoggerFactory

object TrackingService {
    private val logger = LoggerFactory.getLogger(TrackingService::class.java)

    fun trackAndRedirect(ctx: Context) {
        val shortId = ctx.pathParam("shortId")
        val userAgent = ctx.header("User-Agent") ?: "Unknown"
        val ipAddress = ctx.ip()

        if (ClickDB.isRateLimited(shortId, ipAddress)) {
            logger.warn("Rate limit exceeded for IP: {}", ipAddress)
            ctx.status(429).result("Too many requests. Try again later.")
            return
        }

        val originalUrl = LinkDB.retrieveOriginalUrl(shortId)
        if (originalUrl != null) {
            if (LinkDB.isLinkExpired(shortId)) {
                logger.warn("Attempt to access expired link: {}", shortId)
                ctx.status(410).result("This link has expired.")
                return
            }

            LinkDB.processClickTransaction(Click(shortId, userAgent, ipAddress, System.currentTimeMillis()))

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
        val uniqueVisitors = ClickDB.getUniqueVisitorsPerLink()
        val frequentVisitors = ClickDB.getFrequentVisitors()
        val hourlyTrends = ClickDB.getClicksPerHour()

        var totalClicks = 0
        var expiredCount = 0
        var rateLimitedCount = 0
        val topLinks = mutableListOf<Pair<String, Int>>()

        // Generate the Detailed Link Analytics Table
        val analyticsTableRows = linkData.joinToString("") { link ->
            val clickCount = clickData[link.shortId] ?: 0
            val visitorCount = uniqueVisitors[link.shortId] ?: 0
            totalClicks += clickCount
            topLinks.add(link.shortId to clickCount)

            val isExpired = LinkDB.isLinkExpired(link.shortId)
            if (isExpired) expiredCount++

            val hasExceededRateLimit = ClickDB.hasExceededRateLimit(link.shortId)
            if (hasExceededRateLimit) rateLimitedCount++

            val expirationStatus = if (isExpired) "<span class='expired'>Expired</span>" else "<span class='active'>Active</span>"
            val rateLimitStatus = if (hasExceededRateLimit) "<span class='rate-limit'>Rate Limit Exceeded</span>" else "<span class='active'>OK</span>"

            """
        <tr>
            <td>${link.shortId}</td>
            <td>
                <a href="#" class="shortened-link" data-shortid="${link.shortId}">
                    ${link.originalUrl}
                </a>
            </td>
            <td>${clickCount}</td>
            <td>${visitorCount}</td>
            <td>${expirationStatus}</td>
            <td>${rateLimitStatus}</td>
        </tr>
        """.trimIndent()
        }

        // Generate the Most Clicked Links Table
        val mostClickedRows = topLinks.sortedByDescending { it.second }
            .take(5)
            .joinToString("") { (shortId, count) ->
                "<tr><td>${shortId}</td><td>${count}</td></tr>"
            }

        // Generate the Clicks Per Day Table
        val clicksPerDayRows = clicksPerDay.flatMap { (shortId, dateClicks) ->
            dateClicks.map { (date, count) ->
                "<tr><td>${shortId}</td><td>${date}</td><td>${count}</td></tr>"
            }
        }.joinToString("")

        // Generate the Hourly Click Trends Table
        val hourlyTrendsRows = hourlyTrends.flatMap { (shortId, hourClicks) ->
            hourClicks.map { (hour, count) ->
                "<tr><td>${shortId}</td><td>${hour}:00</td><td>${count}</td></tr>"
            }
        }.joinToString("")

        // Generate the Most Frequent Visitors Table
        val frequentVisitorsRows = frequentVisitors.take(5).joinToString("") { (ip, count) ->
            "<tr><td>${ip}</td><td>${count}</td></tr>"
        }

        // Load and Replace Template Placeholders
        val template = this::class.java.getResource("/analytics.html")!!.readText()
        val analyticsHtml = template
            .replace("{{analyticsTableRows}}", analyticsTableRows)
            .replace("{{totalClicks}}", totalClicks.toString())
            .replace("{{expiredCount}}", expiredCount.toString())
            .replace("{{rateLimitedCount}}", rateLimitedCount.toString())
            .replace("{{mostClickedRows}}", mostClickedRows)
            .replace("{{clicksPerDayRows}}", clicksPerDayRows)
            .replace("{{hourlyTrendsRows}}", hourlyTrendsRows)
            .replace("{{frequentVisitorsRows}}", frequentVisitorsRows)

        ctx.contentType("text/html").result(analyticsHtml)
    }
}
