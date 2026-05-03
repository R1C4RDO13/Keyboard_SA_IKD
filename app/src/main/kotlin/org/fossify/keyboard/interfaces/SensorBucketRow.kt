package org.fossify.keyboard.interfaces

import androidx.room.ColumnInfo

/**
 * Result row for the per-session bucketed `sensor_samples` aggregation query (Phase 5).
 *
 * Rows are grouped by `(bucketIndex, sensorType)` so a single query covers
 * both gyro and accelerometer data — the loader splits them by `sensorType`
 * post-query.
 *
 * `avgSquaredMagnitude` is `AVG(x*x + y*y + z*z)` — SQLite has no `sqrt`
 * built-in, so the loader takes `Math.sqrt` per row in Kotlin (≤ 400 calls
 * per session load, negligible cost).
 *
 * `avgX/Y/Z` are returned by the same `GROUP BY` for free and are reserved
 * for the Phase 6 multi-series axis chart; v1 ignores them.
 */
data class SensorBucketRow(
    @ColumnInfo(name = "bucketIndex") val bucketIndex: Long,
    @ColumnInfo(name = "sensorType") val sensorType: String,
    @ColumnInfo(name = "avgSquaredMagnitude") val avgSquaredMagnitude: Double?,
    @ColumnInfo(name = "avgX") val avgX: Double?,
    @ColumnInfo(name = "avgY") val avgY: Double?,
    @ColumnInfo(name = "avgZ") val avgZ: Double?,
    @ColumnInfo(name = "sampleCount") val sampleCount: Int,
)
