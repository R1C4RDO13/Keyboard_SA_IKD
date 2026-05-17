package org.fossify.keyboard.helpers

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.keyboard.BuildConfig
import org.fossify.keyboard.databases.IkdDatabase
import org.fossify.keyboard.helpers.IkdBadgeCatalog.BadgeProgress
import org.fossify.keyboard.helpers.IkdBadgeCatalog.BadgeSnapshot
import org.fossify.keyboard.models.Badge
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.system.measureTimeMillis

/**
 * Phase 14: lazy, read-side badge evaluator. Runs once per
 * `DashboardActivity.loadSnapshot()` on `Dispatchers.IO`, alongside the
 * Phase 9 aggregators on the same hop. Reads only the existing tables
 * (`mood_entries`, `ikd_events`, `sessions`) plus the `badges` cache; the
 * capture path stays fully frozen.
 *
 * Pure derivation lives on [Companion.evaluateBadges] for JVM unit
 * testing without standing up Room — same pattern as
 * `IkdAggregator.Companion.buildSnapshot` /
 * `IkdHabitsAggregator.Companion.computeLongestStreak`.
 */
class IkdBadgeEvaluator(private val db: IkdDatabase) {

    /**
     * @property allUnlocked every currently-unlocked badge key (the
     *   pre-existing set ∪ the deltas just persisted this run).
     * @property newlyUnlocked the badges that crossed their criterion on
     *   *this* evaluation — the snackbar / notification source. Empty on a
     *   no-op re-evaluation.
     * @property progressByKey per-badge `(current, target)` for the
     *   locked-card progress bars (every v1 badge has progress).
     * @property snapshot the materialised read-model (carries the
     *   `recentDayQualified` strip for the devotion day-strip UI).
     */
    data class EvaluationResult(
        val allUnlocked: Set<String>,
        val newlyUnlocked: List<UnlockedBadge>,
        val progressByKey: Map<String, BadgeProgress>,
        val snapshot: BadgeSnapshot,
    )

    /** One badge that unlocked this run, with its persisted timestamp. */
    data class UnlockedBadge(val key: String, val unlockedAt: Long)

    suspend fun evaluate(): EvaluationResult = withContext(Dispatchers.IO) {
        var result: EvaluationResult? = null
        val durationMs = measureTimeMillis {
            val moodDao = db.MoodDao()
            val eventDao = db.IkdEventDao()
            val sessionDao = db.SessionDao()
            val badgeDao = db.BadgeDao()

            val alreadyUnlocked = badgeDao.getAllUnlockedKeys().toSet()
            val moodTimestamps = moodDao.getMoodTimestampsOrdered()
            val keystrokeTotal = eventDao.getKeystrokeCountTotal()
            val sessionDays = sessionDao.getSessionCalendarDays()

            val snapshot = buildSnapshot(
                moodCount = moodTimestamps.size,
                moodTimestamps = moodTimestamps,
                keystrokeTotal = keystrokeTotal,
                sessionDays = sessionDays,
            )

            val nowMs = System.currentTimeMillis()
            val computed = evaluateBadges(snapshot, alreadyUnlocked, nowMs)

            if (computed.newlyUnlocked.isNotEmpty()) {
                badgeDao.upsert(
                    computed.newlyUnlocked.map {
                        Badge(badgeKey = it.key, unlockedAt = it.unlockedAt)
                    }
                )
            }
            result = computed
        }
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "evaluate() took ${durationMs}ms")
        }
        result!!
    }

    companion object {
        private const val LOG_TAG = "IkdBadgeEvaluator"
        private const val DEVOTION_MIN_LOGS_PER_DAY = 3
        private const val RECENT_DAY_WINDOW = 14
        private val DAY_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd")

        /**
         * Pure read-model construction. No I/O, no time read beyond the
         * caller-supplied lists. Buckets the ordered mood timestamps by
         * local ISO day, then derives:
         *  - `bestDayLogs`  = max per-day count (group 3),
         *  - `devotionStreak` = longest run of consecutive days with
         *    ≥ 3 logs, via [computeLongestStreak] over the qualifying-day
         *    list (group 4),
         *  - `recentDayQualified` = last 14 local days (oldest→newest),
         *    each true iff that day had ≥ 3 logs (group 4 strip UI).
         *
         * `nowMs` defaults to the current time but is injectable so the
         * 14-day window is deterministic in unit tests.
         */
        internal fun buildSnapshot(
            moodCount: Int,
            moodTimestamps: List<Long>,
            keystrokeTotal: Long,
            sessionDays: List<String>,
            zone: ZoneId = ZoneId.systemDefault(),
            nowMs: Long = System.currentTimeMillis(),
        ): BadgeSnapshot {
            val perDayCounts: Map<String, Int> = moodTimestamps
                .groupingBy { localDayKey(it, zone) }
                .eachCount()

            val bestDayLogs = perDayCounts.values.maxOrNull() ?: 0

            // Qualifying days, ascending, for the strict consecutive streak.
            val qualifyingDays = perDayCounts
                .filterValues { it >= DEVOTION_MIN_LOGS_PER_DAY }
                .keys
                .sorted()
            val devotionStreak = computeLongestStreak(qualifyingDays)

            val recentDayQualified = lastNDaysQualified(
                perDayCounts = perDayCounts,
                zone = zone,
                nowMs = nowMs,
                window = RECENT_DAY_WINDOW,
            )

            val sessionStreak = computeLongestStreak(sessionDays.sorted())

            return BadgeSnapshot(
                moodCount = moodCount,
                bestDayLogs = bestDayLogs,
                devotionStreak = devotionStreak,
                recentDayQualified = recentDayQualified,
                keystrokeTotal = keystrokeTotal,
                sessionStreak = sessionStreak,
            )
        }

        /**
         * Pure folding step. For every catalogued v1 badge: compute its
         * progress, then — if it is not already unlocked and its criterion
         * passes — record it as newly unlocked at [nowMs]. Already-unlocked
         * badges keep their progress entry for completeness (the card
         * renders unlocked) but never re-notify.
         */
        internal fun evaluateBadges(
            snapshot: BadgeSnapshot,
            alreadyUnlocked: Set<String>,
            nowMs: Long,
        ): EvaluationResult {
            val progressByKey = LinkedHashMap<String, BadgeProgress>()
            val newlyUnlocked = ArrayList<UnlockedBadge>()
            val allUnlocked = HashSet(alreadyUnlocked)

            for (def in IkdBadgeCatalog.ALL) {
                def.progress(snapshot)?.let { progressByKey[def.key] = it }
                if (def.key in alreadyUnlocked) continue
                if (def.criteria(snapshot)) {
                    newlyUnlocked += UnlockedBadge(def.key, nowMs)
                    allUnlocked += def.key
                }
            }

            return EvaluationResult(
                allUnlocked = allUnlocked,
                newlyUnlocked = newlyUnlocked,
                progressByKey = progressByKey,
                snapshot = snapshot,
            )
        }

        /**
         * Longest run of *calendar-consecutive* days in an
         * ascending-sorted list of `yyyy-MM-dd` keys. Mirrors
         * `IkdHabitsAggregator.computeLongestStreak`'s contract (parse
         * adjacent labels, only extend the run when exactly one day apart)
         * but takes the qualifying-day list directly so the evaluator is
         * independent of the Habits aggregator module. Returns 0 for an
         * empty list, 1 for a single isolated day.
         */
        internal fun computeLongestStreak(daysAscending: List<String>): Int {
            if (daysAscending.isEmpty()) return 0
            var longest = 1
            var current = 1
            for (i in 1 until daysAscending.size) {
                val consecutive = runCatching {
                    val prev = java.time.LocalDate.parse(daysAscending[i - 1], DAY_FORMAT)
                    val curr = java.time.LocalDate.parse(daysAscending[i], DAY_FORMAT)
                    java.time.temporal.ChronoUnit.DAYS.between(prev, curr) == 1L
                }.getOrDefault(false)
                if (consecutive) {
                    current++
                    if (current > longest) longest = current
                } else {
                    current = 1
                }
            }
            return longest
        }

        private fun localDayKey(epochMs: Long, zone: ZoneId): String =
            Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate().format(DAY_FORMAT)

        private fun lastNDaysQualified(
            perDayCounts: Map<String, Int>,
            zone: ZoneId,
            nowMs: Long,
            window: Int,
        ): List<Boolean> {
            val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
            return (window - 1 downTo 0).map { back ->
                val day = today.minusDays(back.toLong()).format(DAY_FORMAT)
                (perDayCounts[day] ?: 0) >= DEVOTION_MIN_LOGS_PER_DAY
            }
        }
    }
}
