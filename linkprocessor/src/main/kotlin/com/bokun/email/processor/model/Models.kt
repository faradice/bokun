package com.bokun.email.processor.model

data class Link(
    val id: Int,
    val shortId: String,
    val originalUrl: String,
    val expiration: Long = System.currentTimeMillis(),
    val clickCount: Int = 0
)

data class Click(
    val shortId: String,
    val userAgent: String,
    val ipAddress: String,
    val timestamp: Long
)

data class LinkRequest(
    val originalUrl: String,
    val expiration: String?
)
