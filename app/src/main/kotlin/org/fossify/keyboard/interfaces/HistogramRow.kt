package org.fossify.keyboard.interfaces

import androidx.room.ColumnInfo

/**
 * Phase 9.7: result row for a single histogram bucket.
 *
 * `bucketIndex` is `0..(BUCKET_EDGES_MS.size - 2)` for in-range buckets and
 * `BUCKET_EDGES_MS.size - 1` for the outlier overflow. Aggregator folds the
 * overflow into a separate `outlierCount` field on the histogram model.
 * Sub-zero indices (sentinel pre-10ms rows) are dropped SQL-side.
 */
data class HistogramRow(
    @ColumnInfo(name = "bucketIndex") val bucketIndex: Int,
    @ColumnInfo(name = "count") val count: Int,
)
