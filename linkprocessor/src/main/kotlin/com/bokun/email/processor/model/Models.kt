package com.bokun.email.processor.model

import java.time.LocalDateTime

data class Link(
    val shortId: String,
    val originalUrl: String,
    val expiration: LocalDateTime = LocalDateTime.now().plusDays(10),  // ✅ Default expiration: 30 days
    val clickCount: Int = 0
)

data class Click(
    val shortId: String,
    val userAgent: String,
    val ipAddress: String,
    val timestamp: LocalDateTime
)

data class LinkRequest(
    val originalUrl: String,
    val expiration: String?
)
