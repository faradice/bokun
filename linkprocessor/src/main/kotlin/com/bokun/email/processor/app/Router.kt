package com.bokun.email.processor.app

import com.bokun.email.processor.services.LinkService
import com.bokun.email.processor.services.TrackingService
import io.javalin.Javalin

object Router {
    fun registerRoutes(app: Javalin) {

        // API Endpoints
        app.get("/api/r/{shortId}", TrackingService::trackAndRedirect)
        app.get("/api/links") { ctx ->
            ctx.contentType("application/json").result(LinkService.getAllLinksJson())
        }
        app.get("/api/link/{shortId}") { ctx ->
            val shortId = ctx.pathParam("shortId")
            val linkJson = LinkService.getLinkJson(shortId)

            if (linkJson == "null") {
                ctx.status(404).result("Link not found")
            } else {
                ctx.contentType("application/json").result(linkJson)
            }
        }

        // User-Facing Pages
        app.post("/process-email", LinkService::processEmail)
        app.get("/analytics", TrackingService::getClickAnalytics)
        app.get("/test-email") { ctx ->
            ctx.contentType("text/html").result(this::class.java.getResource("/email_test_form.html")!!.readText())
        }
    }
}
