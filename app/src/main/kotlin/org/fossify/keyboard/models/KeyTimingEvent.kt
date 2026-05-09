package org.fossify.keyboard.models

data class KeyTimingEvent(
    val sessionId: String,
    val timestamp: Long,
    val eventCategory: String,    // "ALPHA", "DIGIT", "SPACE", "BACKSPACE", "ENTER", "OTHER", "EMOJI", "AUTOCORRECT"
    val ikdMs: Long,              // -1 = first event
    val holdTimeMs: Long,         // -1 = unknown (renamed from dwellMs)
    val flightTimeMs: Long,       // -1 = first event (renamed from flightMs)
    val isCorrection: Boolean,    // true for backspace/delete events and AUTOCORRECT rows
    // Phase 7.1: weight of the correction. 1 for BACKSPACE; replaced-span
    // length for AUTOCORRECT (coerced to ≥ 1); 0 otherwise. Mirrors the new
    // `correction_weight` column on `IkdEvent`.
    val correctionWeight: Int = 0,
)
