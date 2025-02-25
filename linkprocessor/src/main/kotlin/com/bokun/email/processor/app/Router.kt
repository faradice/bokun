package com.bokun.email.processor.app

import com.bokun.email.processor.services.LinkCreationService
import com.bokun.email.processor.services.LinkService
import com.bokun.email.processor.services.RedirectService
import io.javalin.Javalin

object Router {
    fun registerRoutes(app: Javalin) {

        // post api
        app.post("/api/links", LinkCreationService::createShortLink)
        app.get("/api/r/{shortId}", RedirectService::trackAndRedirect)
        app.post("/api/process-email", LinkService::processEmail)

        // get api
        app.get("/api/analytics") {
            RedirectService.getClickAnalytics(it)
        }

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

        app.get(
            "/confirm/{shortId}", LinkService::getConfirmationPage
        )

        app.get("/test-email") { ctx ->
            ctx.contentType("text/html").result(this::class.java.getResource("/email_test_form.html")!!.readText())
        }
    }
}