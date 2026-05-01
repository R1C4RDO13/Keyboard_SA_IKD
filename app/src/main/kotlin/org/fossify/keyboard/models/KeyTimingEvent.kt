package org.fossify.keyboard.models

data class KeyTimingEvent(
    val sessionId: String,
    val timestamp: Long,
    val eventCategory: String,    // "ALPHA", "DIGIT", "SPACE", "BACKSPACE", "ENTER", "OTHER"
    val ikdMs: Long,              // -1 = first event
    val holdTimeMs: Long,         // -1 = unknown (renamed from dwellMs)
    val flightTimeMs: Long,       // -1 = first event (renamed from flightMs)
    val isCorrection: Boolean     // true for backspace/delete events
)
