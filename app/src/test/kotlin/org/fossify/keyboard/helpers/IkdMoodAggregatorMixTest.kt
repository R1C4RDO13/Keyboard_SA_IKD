package org.fossify.keyboard.helpers

import org.fossify.keyboard.helpers.IkdAggregator.Range
import org.fossify.keyboard.interfaces.MoodCategoryBucketRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 8.3: pure-math tests for [IkdMoodAggregator.buildMixSnapshot].
 * Mirrors the [IkdMoodAggregatorTest] pattern — the SQL is exercised
 * end-to-end on-device, this suite covers the Kotlin-side fold logic
 * for the new stacked-bar chart payload.
 *
 * `buildMixSnapshot` is responsible for:
 *  - Zero-filling counts across the six Ekman scores so the chart
 *    layer can iterate in display order without null checks.
 *  - Folding multiple rows for the same `(bucket, score)` pair (the
 *    SQL never produces duplicates, but the helper is defensive).
 *  - Dropping out-of-range scores silently (matches `buildSnapshot`'s
 *    behaviour).
 *  - Emitting `total = sum of all bucket totals` so the activity's
 *    empty-state check (`moodMix.total == 0`) is consistent with
 *    `MoodSnapshot.total`.
 */
class IkdMoodAggregatorMixTest {

    @Test
    fun emptyInputs_yieldZeroTotalAndEmptyBuckets() {
        val snap = IkdMoodAggregator.buildMixSnapshot(
            range = Range.WEEK,
            rows = emptyList(),
        )

        assertEquals(Range.WEEK, snap.range)
        assertEquals(0, snap.total)
        assertTrue(snap.buckets.isEmpty())
    }

    @Test
    fun singleBucket_oneCategory_isAllInThatScore() {
        // A single tap on `Happiness` for a single day. Bucket total = 1;
        // counts map has Happiness = 1 and the other five at 0.
        val rows = listOf(
            MoodCategoryBucketRow(bucket = MAY_3, score = MoodEmoji.SCORE_HAPPINESS, entryCount = 1),
        )

        val snap = IkdMoodAggregator.buildMixSnapshot(Range.WEEK, rows)

        assertEquals(1, snap.total)
        assertEquals(1, snap.buckets.size)
        val bucket = snap.buckets[0]
        assertEquals(MAY_3, bucket.label)
        assertEquals(1, bucket.total)
        assertEquals(1, bucket.counts[MoodEmoji.SCORE_HAPPINESS])
        assertEquals(0, bucket.counts[MoodEmoji.SCORE_ANGER])
        assertEquals(0, bucket.counts[MoodEmoji.SCORE_SADNESS])
        // Map is always six entries, no matter how few rows the bucket has.
        assertEquals(SIX_SCORES, bucket.counts.size)
    }

    @Test
    fun mixedBucket_multipleCategories_summedPerScore() {
        // A day with 3 Happiness + 2 Sadness + 1 Anger taps. The chart
        // layer renders these as 3/6 = 50%, 2/6 ≈ 33.3%, 1/6 ≈ 16.7%.
        val rows = listOf(
            MoodCategoryBucketRow(bucket = MAY_3, score = MoodEmoji.SCORE_HAPPINESS, entryCount = 3),
            MoodCategoryBucketRow(bucket = MAY_3, score = MoodEmoji.SCORE_SADNESS, entryCount = 2),
            MoodCategoryBucketRow(bucket = MAY_3, score = MoodEmoji.SCORE_ANGER, entryCount = 1),
        )

        val snap = IkdMoodAggregator.buildMixSnapshot(Range.WEEK, rows)

        assertEquals(MIXED_TOTAL, snap.total)
        val bucket = snap.buckets.single()
        assertEquals(MIXED_TOTAL, bucket.total)
        assertEquals(MIXED_HAPPINESS, bucket.counts[MoodEmoji.SCORE_HAPPINESS])
        assertEquals(MIXED_SADNESS, bucket.counts[MoodEmoji.SCORE_SADNESS])
        assertEquals(MIXED_ANGER, bucket.counts[MoodEmoji.SCORE_ANGER])
        // Surprise / Disgust / Fear all stay at 0 — segments render as
        // zero-height stacked bars on the chart.
        assertEquals(0, bucket.counts[MoodEmoji.SCORE_SURPRISE])
        assertEquals(0, bucket.counts[MoodEmoji.SCORE_DISGUST])
        assertEquals(0, bucket.counts[MoodEmoji.SCORE_FEAR])
    }

    @Test
    fun multipleBuckets_emittedInBucketOrder() {
        // Three days, each with a different mood. The X axis must render
        // them in bucket-key order (the SQL ORDER BY ensures the rows
        // already arrive sorted; the helper preserves that with
        // LinkedHashMap).
        val rows = listOf(
            MoodCategoryBucketRow(bucket = MAY_3, score = MoodEmoji.SCORE_HAPPINESS, entryCount = 1),
            MoodCategoryBucketRow(bucket = MAY_4, score = MoodEmoji.SCORE_DISGUST, entryCount = 1),
            MoodCategoryBucketRow(bucket = MAY_5, score = MoodEmoji.SCORE_ANGER, entryCount = 1),
        )

        val snap = IkdMoodAggregator.buildMixSnapshot(Range.MONTH, rows)

        assertEquals(THREE, snap.total)
        assertEquals(THREE, snap.buckets.size)
        assertEquals(MAY_3, snap.buckets[0].label)
        assertEquals(MAY_4, snap.buckets[1].label)
        assertEquals(MAY_5, snap.buckets[2].label)
    }

    @Test
    fun outOfRangeScores_areSilentlyDropped() {
        // A corrupt row with score = 7 must not bump the total nor add a
        // segment to the bar. The remaining valid row stays.
        val rows = listOf(
            MoodCategoryBucketRow(bucket = MAY_3, score = OUT_OF_RANGE_HIGH, entryCount = 99),
            MoodCategoryBucketRow(bucket = MAY_3, score = MoodEmoji.SCORE_HAPPINESS, entryCount = 1),
            MoodCategoryBucketRow(bucket = MAY_3, score = OUT_OF_RANGE_LOW, entryCount = 99),
        )

        val snap = IkdMoodAggregator.buildMixSnapshot(Range.WEEK, rows)

        assertEquals(1, snap.total)
        val bucket = snap.buckets.single()
        assertEquals(1, bucket.total)
        assertEquals(1, bucket.counts[MoodEmoji.SCORE_HAPPINESS])
    }

    @Test
    fun bucketWithOnlyInvalidRows_isAbsentFromOutput() {
        // A bucket whose every row has an out-of-range score yields no
        // valid mood data — emit zero buckets, total = 0. (The activity's
        // empty-state check still triggers correctly via `total == 0`.)
        val rows = listOf(
            MoodCategoryBucketRow(bucket = MAY_3, score = OUT_OF_RANGE_HIGH, entryCount = 5),
        )

        val snap = IkdMoodAggregator.buildMixSnapshot(Range.WEEK, rows)

        assertEquals(0, snap.total)
        assertTrue(snap.buckets.isEmpty())
    }

    @Test
    fun totalEqualsSumOfBucketTotals() {
        // Sanity check that the snapshot-level total agrees with the per-
        // bucket totals. This is what the activity uses to gate the chart
        // card's visibility, so a drift here would produce a bar with no
        // data behind it.
        val rows = listOf(
            MoodCategoryBucketRow(bucket = MAY_3, score = MoodEmoji.SCORE_HAPPINESS, entryCount = 4),
            MoodCategoryBucketRow(bucket = MAY_4, score = MoodEmoji.SCORE_SADNESS, entryCount = 3),
            MoodCategoryBucketRow(bucket = MAY_5, score = MoodEmoji.SCORE_ANGER, entryCount = 2),
            MoodCategoryBucketRow(bucket = MAY_5, score = MoodEmoji.SCORE_FEAR, entryCount = 1),
        )

        val snap = IkdMoodAggregator.buildMixSnapshot(Range.MONTH, rows)

        assertEquals(SUM_TOTAL, snap.total)
        val bucketSum = snap.buckets.sumOf { it.total }
        assertEquals(SUM_TOTAL, bucketSum)
    }

    @Test
    fun countsMap_isAlwaysSixEntries_evenForEmptyBuckets() {
        // Edge case: a bucket with one Happiness tap should still produce
        // a six-entry counts map. The chart layer relies on this so it
        // can iterate `displayOrder()` without null checks.
        val rows = listOf(
            MoodCategoryBucketRow(bucket = MAY_3, score = MoodEmoji.SCORE_HAPPINESS, entryCount = 1),
        )

        val snap = IkdMoodAggregator.buildMixSnapshot(Range.WEEK, rows)

        val bucket = snap.buckets.single()
        for (score in MoodEmoji.displayOrder()) {
            assertTrue(
                "score $score must be present in counts map",
                bucket.counts.containsKey(score),
            )
        }
        assertEquals(SIX_SCORES, bucket.counts.size)
    }

    companion object {
        private const val MAY_3 = "2026-05-03"
        private const val MAY_4 = "2026-05-04"
        private const val MAY_5 = "2026-05-05"

        // Mixed-bucket fixture: 3 Happiness + 2 Sadness + 1 Anger.
        private const val MIXED_HAPPINESS = 3
        private const val MIXED_SADNESS = 2
        private const val MIXED_ANGER = 1
        private const val MIXED_TOTAL = MIXED_HAPPINESS + MIXED_SADNESS + MIXED_ANGER

        // Three-bucket fixture: one tap per day.
        private const val THREE = 3

        // Sum-total fixture: 4 + 3 + 2 + 1 across three days.
        private const val SUM_TOTAL = 10

        // Six Ekman scores; the counts map is always this size.
        private const val SIX_SCORES = 6

        // Out-of-range scores that must be dropped.
        private const val OUT_OF_RANGE_LOW = 0
        private const val OUT_OF_RANGE_HIGH = 7
    }
}
