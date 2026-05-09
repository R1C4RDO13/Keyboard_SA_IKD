package org.fossify.keyboard.interfaces

import androidx.room.ColumnInfo

/**
 * Phase 9.2: result row for the bucketed `sensor_samples` *global* aggregation.
 *
 * Each row is one `(bucket, sensorType)` pair. `avgSquaredMagnitude` is null
 * when the bucket has no samples; the loader translates that into a missing
 * chart point (line break) rather than a fake zero. SQLite has no `sqrt`, so
 * we average the squared magnitude SQL-side and take `Math.sqrt` per row in
 * Kotlin (≤ 60 calls per snapshot — free).
 */
data class SensorBucketAggregateRow(
    @ColumnInfo(name = "bucket") val bucket: String,
    @ColumnInfo(name = "sensorType") val sensorType: String,
    @ColumnInfo(name = "avgSquaredMagnitude") val avgSquaredMagnitude: Double?,
    @ColumnInfo(name = "sampleCount") val sampleCount: Int,
)
