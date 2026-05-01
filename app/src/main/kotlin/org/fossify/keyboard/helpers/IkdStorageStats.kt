package org.fossify.keyboard.helpers

import android.content.Context
import org.fossify.keyboard.extensions.ikdDB

data class IkdStorageStats(
    val dbSizeBytes: Long,
    val sessionCount: Int,
    val eventCount: Int,
    val sensorCount: Int,
)

/**
 * Compute the on-disk size of `ikd.db` plus row counts for sessions, events,
 * and sensor samples.
 *
 * Issues three synchronous SQL `COUNT(*)` queries — callers MUST run this off
 * the main thread (e.g. `ensureBackgroundThread { ... }`).
 */
fun Context.computeIkdStorageStats(): IkdStorageStats {
    val dbFile = getDatabasePath("ikd.db")
    val dbSize = if (dbFile.exists()) dbFile.length() else 0L
    val db = ikdDB
    return IkdStorageStats(
        dbSizeBytes = dbSize,
        sessionCount = db.SessionDao().count(),
        eventCount = db.IkdEventDao().count(),
        sensorCount = db.SensorSampleDao().count(),
    )
}
