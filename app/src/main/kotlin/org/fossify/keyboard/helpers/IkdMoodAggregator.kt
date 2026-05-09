package org.fossify.keyboard.helpers

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.keyboard.BuildConfig
import org.fossify.keyboard.databases.IkdDatabase
import org.fossify.keyboard.helpers.IkdAggregator.Range
import org.fossify.keyboard.interfaces.MoodBucketRow
import org.fossify.keyboard.interfaces.MoodCategoryBucketRow
import org.fossify.keyboard.interfaces.MoodDistributionRow
import java.util.Calendar
import java.util.TimeZone
import kotlin.system.measureTimeMillis

/**
 * Phase 8: read-only aggregator over `mood_entries` for the global
 * dashboard. Sibling of [IkdAggregator] — IKD aggregations stay there
 * unchanged; mood aggregations live here.
 *
 * Reuses [IkdAggregator.Range] (Week / Month / All Time) and the same
 * `strftime` bucket-format selection so the two snapshots share the same
 * X axis on the dashboard.
 *
 * Threading: [snapshot] runs entirely on `Dispatchers.IO`. Callers may
 * invoke it from any coroutine context (typically alongside
 * `IkdAggregator.snapshot(range)` on the same single hop in
 * `DashboardActivity.onResume`).
 *
 * Pure derivation lives on [Companion.buildSnapshot] for JVM unit testing
 * (no Room standup required) — same pattern as Phase 3 / 4 / 5 loaders.
 */
class IkdMoodAggregator(private val db: IkdDatabase) {

    /**
     * One bucket on the Mood-over-Time line chart. `avgScore` is nullable
     * because a bucket inside the range may have no entries even when
     * adjacent buckets do — the chart layer renders that as a missing
     * point (line break), not zero.
     */
    data class MoodBucket(
        val label: String,
        val avgScore: Double?,
        val entryCount: Int,
    )

    /**
     * Top-level dashboard payload. `total == 0` means no entries in the
     * selected range — DashboardActivity hides the chart card, the
     * Distribution panel, and the Avg Mood KPI in that case.
     *
     * Phase 8.3: `averageScore` is no longer surfaced on the global
     * dashboard (the Avg Mood KPI cell + Mood-over-Time line chart were
     * dropped — averaging an ordinal valence id over six categorical
     * labels produces interpretively vague numbers). The field is kept
     * on the data class for backwards compatibility with the old test
     * fixtures (Decision #11 of `Phase8.3_Plan.md`).
     */
    data class MoodSnapshot(
        val range: Range,
        val buckets: List<MoodBucket>,
        /** Map of `mood_score (1..6)` → count, always six entries. */
        val counts: Map<Int, Int>,
        val total: Int,
        val averageScore: Double?,
    )

    /**
     * Phase 8.3: one bar on the "Mood Mix over Time" stacked-bar chart.
     * `counts` is always six entries (one per Ekman score, zero-defaulted)
     * so the chart layer can iterate in display order without null checks.
     * `total` is the sum of `counts.values` — used to render percentages
     * when present, or zero-height bars when absent.
     */
    data class MoodMixBucket(
        val label: String,
        /** Map of `mood_score (1..6)` → count, always six entries. */
        val counts: Map<Int, Int>,
        val total: Int,
    )

    /**
     * Phase 8.3: stacked-bar dashboard payload. `total == 0` means no
     * entries in the selected range — DashboardActivity hides the chart
     * card and the Distribution panel in that case (the Avg Mood KPI cell
     * and Mood-over-Time line chart from Phase 8 are gone entirely).
     */
    data class MoodMixSnapshot(
        val range: Range,
        val buckets: List<MoodMixBucket>,
        /** Total entries across all buckets — same number `MoodSnapshot.total` produces. */
        val total: Int,
    )

    suspend fun snapshot(range: Range): MoodSnapshot = withContext(Dispatchers.IO) {
        var result: MoodSnapshot? = null
        val durationMs = measureTimeMillis {
            val nowMs = System.currentTimeMillis()
            val (fromMs, toMs) = computeRangeWindow(range, nowMs)

            val buckets = db.MoodDao().getMoodBuckets(range.bucketFormat, fromMs, toMs)
            val distribution = db.MoodDao().getMoodDistribution(fromMs, toMs)

            result = Companion.buildSnapshot(range, buckets, distribution)
        }
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "snapshot(${range.name}) took ${durationMs}ms")
        }
        result!!
    }

    /**
     * Phase 8.3: per-bucket-per-category aggregation for the new "Mood Mix
     * over Time" stacked bar chart. Composed alongside [snapshot] in the
     * same `Dispatchers.IO` hop on `DashboardActivity.onResume`.
     */
    suspend fun mixSnapshot(range: Range): MoodMixSnapshot = withContext(Dispatchers.IO) {
        var result: MoodMixSnapshot? = null
        val durationMs = measureTimeMillis {
            val nowMs = System.currentTimeMillis()
            val (fromMs, toMs) = computeRangeWindow(range, nowMs)
            val rows = db.MoodDao().getMoodCategoryBuckets(range.bucketFormat, fromMs, toMs)
            result = Companion.buildMixSnapshot(range, rows)
        }
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "mixSnapshot(${range.name}) took ${durationMs}ms")
        }
        result!!
    }

    private fun computeRangeWindow(range: Range, nowMs: Long): Pair<Long, Long> {
        val days = range.days
        val fromMs = if (days != null) {
            startOfLocalDayMillis(nowMs) - (days - 1).toLong() * MS_PER_DAY
        } else {
            // ALL_TIME: from earliest session, falling back to "everything"
            // when the table is empty (in which case the queries will
            // return zero rows anyway).
            db.SessionDao().getEarliestSessionStart() ?: 0L
        }
        return fromMs to (nowMs + 1L)
    }

    private fun startOfLocalDayMillis(epochMs: Long): Long {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.timeInMillis = epochMs
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    companion object {
        private const val LOG_TAG = "IkdMoodAggregator"
        private const val MS_PER_DAY = 86_400_000L

        /**
         * Pure aggregation. Lives on the companion so it can be unit-tested
         * without standing up a Room DB. No I/O, no time read, no
         * dependency on instance state.
         *
         * - Out-of-range scores (e.g. a corrupt row with score = 7) are
         *   silently dropped — they neither bump the count nor pull the
         *   overall average. The rest of the panel renders normally.
         * - The `counts` map is always six entries (1..6), with missing
         *   scores defaulting to zero so the Distribution panel can iterate
         *   in display order without null checks.
         * - `averageScore` is `null` when `total == 0` (no entries).
         */
        internal fun buildSnapshot(
            range: Range,
            buckets: List<MoodBucketRow>,
            distribution: List<MoodDistributionRow>,
        ): MoodSnapshot {
            val moodBuckets = buckets.map {
                MoodBucket(
                    label = it.bucket,
                    avgScore = if (it.entryCount == 0) null else it.avgScore,
                    entryCount = it.entryCount,
                )
            }

            val counts = MoodEmoji.displayOrder().associateWith { 0 }.toMutableMap()
            for (row in distribution) {
                if (MoodEmoji.isValidScore(row.score)) {
                    counts[row.score] = row.entryCount
                }
            }

            val total = counts.values.sum()
            val averageScore = if (total == 0) {
                null
            } else {
                // Reconstruct from `counts` so out-of-range rows are excluded
                // — bucket-level avg already includes them via SQLite's AVG
                // and that's tolerable for the line chart, but the KPI cell
                // wants the same number the Distribution panel implies.
                counts.entries.sumOf { (score, count) -> score.toDouble() * count } /
                    total.toDouble()
            }

            return MoodSnapshot(
                range = range,
                buckets = moodBuckets,
                counts = counts,
                total = total,
                averageScore = averageScore,
            )
        }

        /**
         * Phase 8.3: pure derivation for the stacked-bar chart. Fold the
         * `(bucket, score)` rows into one `MoodMixBucket` per bucket key,
         * with the inner `counts` map zero-defaulted across all six
         * Ekman scores so callers can iterate in display order without
         * null checks.
         *
         * - Out-of-range scores (e.g. a corrupt row with score = 7) are
         *   silently dropped, matching `buildSnapshot`'s behaviour.
         * - Buckets are emitted in `bucket` ascending order (the SQL
         *   already orders this way; `LinkedHashMap` preserves insertion
         *   order).
         * - `total` is the sum of bucket totals — equivalent to
         *   `MoodSnapshot.total` produced from the same time window.
         */
        internal fun buildMixSnapshot(
            range: Range,
            rows: List<MoodCategoryBucketRow>,
        ): MoodMixSnapshot {
            // LinkedHashMap so the bucket-key insertion order survives the
            // group-by in Kotlin even though we're not relying on the SQL
            // ordering directly.
            val grouped = linkedMapOf<String, MutableMap<Int, Int>>()
            for (row in rows) {
                if (!MoodEmoji.isValidScore(row.score)) continue
                val perScore = grouped.getOrPut(row.bucket) {
                    MoodEmoji.displayOrder().associateWith { 0 }.toMutableMap()
                }
                perScore[row.score] = (perScore[row.score] ?: 0) + row.entryCount
            }
            val mixBuckets = grouped.map { (label, perScore) ->
                MoodMixBucket(
                    label = label,
                    counts = perScore.toMap(),
                    total = perScore.values.sum(),
                )
            }
            val total = mixBuckets.sumOf { it.total }
            return MoodMixSnapshot(
                range = range,
                buckets = mixBuckets,
                total = total,
            )
        }
    }
}
