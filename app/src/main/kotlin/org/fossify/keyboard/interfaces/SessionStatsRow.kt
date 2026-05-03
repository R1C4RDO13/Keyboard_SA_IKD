package org.fossify.keyboard.interfaces

import androidx.room.ColumnInfo

/**
 * Result row for the per-session aggregation query on `ikd_events`.
 *
 * Averages are nullable because `AVG(CASE WHEN col >= 0 THEN col END)` returns
 * `NULL` when every event in the session is sentinel `-1` (e.g. a session
 * containing only its first keypress). Callers must surface that as `"—"`
 * rather than zero. `firstTimestamp` / `lastTimestamp` are nullable for the
 * same reason: a session may have its row in `sessions` but no event rows yet.
 */
data class SessionStatsRow(
    @ColumnInfo(name = "eventCount") val eventCount: Int,
    @ColumnInfo(name = "correctionCount") val correctionCount: Int,
    @ColumnInfo(name = "avgIkdMs") val avgIkdMs: Double?,
    @ColumnInfo(name = "avgHoldMs") val avgHoldMs: Double?,
    @ColumnInfo(name = "avgFlightMs") val avgFlightMs: Double?,
    @ColumnInfo(name = "firstTimestamp") val firstTimestamp: Long?,
    @ColumnInfo(name = "lastTimestamp") val lastTimestamp: Long?,
)
