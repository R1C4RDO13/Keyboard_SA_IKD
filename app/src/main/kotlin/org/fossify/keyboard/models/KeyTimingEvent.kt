package org.fossify.keyboard.models

data class KeyTimingEvent(
    val sessionId: String,
    val timestamp: Long,
    val ikdMs: Long,    // -1 = first event
    val dwellMs: Long,  // -1 = unknown
    val flightMs: Long  // -1 = first event
)
