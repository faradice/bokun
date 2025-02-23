package com.bokun.email.processor.model

import java.time.LocalDateTime

data class Link(val shortId: String, val originalUrl: String)

data class ClickEvent(val shortId: String, val userAgent: String, val ipAddress: String, val timestamp: LocalDateTime)
