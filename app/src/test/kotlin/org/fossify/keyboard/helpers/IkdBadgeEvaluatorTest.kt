package org.fossify.keyboard.helpers

import org.fossify.keyboard.helpers.IkdBadgeCatalog.BadgeSnapshot
import org.fossify.keyboard.helpers.IkdBadgeCatalog.UnitKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * Phase 14: pure-math tests for [IkdBadgeEvaluator.Companion.buildSnapshot]
 * and [IkdBadgeEvaluator.Companion.evaluateBadges]. SQL is exercised
 * on-device; this suite covers one fixture per criterion family plus
 * all-locked / all-unlocked baselines and the streak helper.
 *
 * All timestamps use a fixed UTC zone + a fixed `now` so the 14-day
 * window and per-day bucketing are deterministic.
 */
class IkdBadgeEvaluatorTest {

    private val utc: ZoneId = ZoneId.of("UTC")

    // 2026-05-17T12:00:00Z
    private val nowMs = 1_779_364_800_000L
    private val msPerDay = 86_400_000L

    /** Epoch millis at noon UTC, `daysAgo` before `nowMs`. */
    private fun dayTs(daysAgo: Int): Long = nowMs - daysAgo.toLong() * msPerDay

    private fun snapshot(
        moodTimestamps: List<Long> = emptyList(),
        keystrokeTotal: Long = 0L,
        sessionDays: List<String> = emptyList(),
    ): BadgeSnapshot = IkdBadgeEvaluator.buildSnapshot(
        moodCount = moodTimestamps.size,
        moodTimestamps = moodTimestamps,
        keystrokeTotal = keystrokeTotal,
        sessionDays = sessionDays,
        zone = utc,
        nowMs = nowMs,
    )

    // ---------- streak helper ----------

    @Test
    fun streak_emptyList_returnsZero() {
        assertEquals(0, IkdBadgeEvaluator.computeLongestStreak(emptyList()))
    }

    @Test
    fun streak_singleDay_returnsOne() {
        assertEquals(1, IkdBadgeEvaluator.computeLongestStreak(listOf("2026-05-01")))
    }

    @Test
    fun streak_consecutiveRun_acrossMonthBoundary() {
        val days = listOf("2026-04-29", "2026-04-30", "2026-05-01", "2026-05-02")
        assertEquals(4, IkdBadgeEvaluator.computeLongestStreak(days))
    }

    @Test
    fun streak_brokenRun_takesLongestSegment() {
        // run of 3, gap, run of 2
        val days = listOf(
            "2026-05-01", "2026-05-02", "2026-05-03",
            "2026-05-09", "2026-05-10",
        )
        assertEquals(3, IkdBadgeEvaluator.computeLongestStreak(days))
    }

    // ---------- group 1: mood volume ----------

    @Test
    fun moodVolume_firstMoodUnlocks_atOneEntry() {
        val snap = snapshot(moodTimestamps = listOf(dayTs(0)))
        val result = IkdBadgeEvaluator.evaluateBadges(snap, emptySet(), nowMs)
        assertTrue(result.allUnlocked.contains("mood_vol_1"))
        assertFalse(result.allUnlocked.contains("mood_vol_10"))
        val prog = result.progressByKey.getValue("mood_vol_10")
        assertEquals(1L, prog.current)
        assertEquals(10L, prog.target)
        assertEquals(UnitKind.COUNT, prog.unitKind)
    }

    @Test
    fun moodVolume_tieredUnlocks_at100() {
        val ts = (0 until 100).map { dayTs(it % 30) }
        val snap = snapshot(moodTimestamps = ts)
        val result = IkdBadgeEvaluator.evaluateBadges(snap, emptySet(), nowMs)
        assertTrue(result.allUnlocked.contains("mood_vol_1"))
        assertTrue(result.allUnlocked.contains("mood_vol_10"))
        assertTrue(result.allUnlocked.contains("mood_vol_50"))
        assertTrue(result.allUnlocked.contains("mood_vol_100"))
        assertFalse(result.allUnlocked.contains("mood_vol_500"))
    }

    // ---------- group 3: daily check-in (today's count, daily reset) ----------

    @Test
    fun dailyCheckin_unlocksTieredByTodaysLogCount() {
        // 2 logs today, plus 5 logs spread across older days that should
        // NOT contribute to today's tiered unlock (no max-ever fallback).
        val ts = listOf(dayTs(0), dayTs(0)) + (1..5).map { dayTs(it) }
        val snap = snapshot(moodTimestamps = ts)
        assertEquals(2, snap.todayLogs)
        val result = IkdBadgeEvaluator.evaluateBadges(
            snap, emptySet(), nowMs, zone = utc,
        )
        assertTrue(result.allUnlocked.contains("mood_day_1"))
        assertTrue(result.allUnlocked.contains("mood_day_2"))
        assertFalse(result.allUnlocked.contains("mood_day_3"))
    }

    @Test
    fun dailyCheckin_doesNotUnlock_whenAllLogsAreFromEarlierDays() {
        // 3 logs ten days ago, none today: best-day-ever no longer matters.
        val ts = listOf(dayTs(10), dayTs(10), dayTs(10))
        val snap = snapshot(moodTimestamps = ts)
        assertEquals(0, snap.todayLogs)
        val result = IkdBadgeEvaluator.evaluateBadges(
            snap, emptySet(), nowMs, zone = utc,
        )
        assertFalse(result.allUnlocked.contains("mood_day_1"))
        assertFalse(result.allUnlocked.contains("mood_day_2"))
        assertFalse(result.allUnlocked.contains("mood_day_3"))
    }

    @Test
    fun dailyCheckin_relocks_whenYesterdaysUnlockHasNoLogsToday() {
        // Persisted: unlocked yesterday. Today: no logs at all.
        val snap = snapshot(moodTimestamps = emptyList())
        val result = IkdBadgeEvaluator.evaluateBadges(
            snapshot = snap,
            alreadyUnlocked = setOf("mood_day_1"),
            nowMs = nowMs,
            persistedUnlockAt = mapOf("mood_day_1" to dayTs(1)),
            zone = utc,
        )
        assertFalse(result.allUnlocked.contains("mood_day_1"))
        assertFalse(result.unlockedAtByKey.containsKey("mood_day_1"))
        assertTrue(result.newlyUnlocked.none { it.key == "mood_day_1" })
    }

    @Test
    fun dailyCheckin_reUnlocksFresh_whenLogsCrossOverIntoNewDay() {
        // Persisted: unlocked yesterday with a stale `unlocked_at`. Today
        // the user logs again — should re-fire as a newlyUnlocked entry
        // and overwrite the timestamp to today.
        val snap = snapshot(moodTimestamps = listOf(dayTs(0)))
        val result = IkdBadgeEvaluator.evaluateBadges(
            snapshot = snap,
            alreadyUnlocked = setOf("mood_day_1"),
            nowMs = nowMs,
            persistedUnlockAt = mapOf("mood_day_1" to dayTs(1)),
            zone = utc,
        )
        assertTrue(result.allUnlocked.contains("mood_day_1"))
        assertEquals(nowMs, result.unlockedAtByKey["mood_day_1"])
        assertTrue(result.newlyUnlocked.any { it.key == "mood_day_1" })
    }

    @Test
    fun dailyCheckin_alreadyUnlockedToday_doesNotReNotify() {
        // Persisted: today's earlier log already wrote the row. A later
        // evaluation in the same day must not re-fire the snackbar.
        val snap = snapshot(moodTimestamps = listOf(dayTs(0), dayTs(0)))
        val priorTodayMs = nowMs - 60_000L // a minute earlier, same UTC day
        val result = IkdBadgeEvaluator.evaluateBadges(
            snapshot = snap,
            alreadyUnlocked = setOf("mood_day_1"),
            nowMs = nowMs,
            persistedUnlockAt = mapOf("mood_day_1" to priorTodayMs),
            zone = utc,
        )
        assertTrue(result.allUnlocked.contains("mood_day_1"))
        assertEquals(priorTodayMs, result.unlockedAtByKey["mood_day_1"])
        assertTrue(result.newlyUnlocked.none { it.key == "mood_day_1" })
    }

    // ---------- group 4: devotion strict streak ----------

    @Test
    fun devotion_threeConsecutiveQualifyingDays_unlocksEngaged() {
        // 3 logs each on days 4,3,2 ago (consecutive, all qualifying);
        // day 6 ago has only 1 log (non-qualifying — irrelevant to run).
        val ts = buildList {
            repeat(3) { add(dayTs(4)) }
            repeat(3) { add(dayTs(3)) }
            repeat(3) { add(dayTs(2)) }
            add(dayTs(6))
        }
        val snap = snapshot(moodTimestamps = ts)
        assertEquals(3, snap.devotionStreak)
        val result = IkdBadgeEvaluator.evaluateBadges(snap, emptySet(), nowMs)
        assertTrue(result.allUnlocked.contains("mood_3pd_1"))
        assertTrue(result.allUnlocked.contains("mood_3pd_3"))
        assertFalse(result.allUnlocked.contains("mood_3pd_7"))
    }

    @Test
    fun devotion_missedDayResetsRun() {
        // 3 logs on day 5, none on day 4, 3 logs on days 3 and 2.
        val ts = buildList {
            repeat(3) { add(dayTs(5)) }
            repeat(3) { add(dayTs(3)) }
            repeat(3) { add(dayTs(2)) }
        }
        val snap = snapshot(moodTimestamps = ts)
        // Longest consecutive qualifying run is days 3 & 2 = 2.
        assertEquals(2, snap.devotionStreak)
    }

    @Test
    fun devotion_recentDayQualified_isFourteenDayBooleanStrip() {
        val ts = buildList {
            repeat(3) { add(dayTs(0)) } // today qualifies
            repeat(3) { add(dayTs(1)) } // yesterday qualifies
            add(dayTs(2)) // 2 days ago: only 1 log, not qualifying
        }
        val snap = snapshot(moodTimestamps = ts)
        assertEquals(14, snap.recentDayQualified.size)
        // oldest→newest: last element is today.
        assertTrue(snap.recentDayQualified.last())
        assertTrue(snap.recentDayQualified[12]) // yesterday
        assertFalse(snap.recentDayQualified[11]) // 2 days ago
        assertFalse(snap.recentDayQualified.first()) // 13 days ago
    }

    // ---------- group 5: keystroke volume ----------

    @Test
    fun keystrokeVolume_unlocksTiersByLifetimeTotal() {
        val snap = snapshot(keystrokeTotal = 42_310L)
        val result = IkdBadgeEvaluator.evaluateBadges(snap, emptySet(), nowMs)
        assertTrue(result.allUnlocked.contains("kb_keys_1k"))
        assertTrue(result.allUnlocked.contains("kb_keys_10k"))
        assertFalse(result.allUnlocked.contains("kb_keys_100k"))
        val prog = result.progressByKey.getValue("kb_keys_100k")
        assertEquals(42_310L, prog.current)
        assertEquals(100_000L, prog.target)
    }

    // ---------- group 7: session streak ----------

    @Test
    fun sessionStreak_unlocksByLongestConsecutiveSessionDays() {
        val days = listOf(
            "2026-05-10", "2026-05-11", "2026-05-12",
            "2026-05-13", "2026-05-14", "2026-05-15", "2026-05-16",
        )
        val snap = snapshot(sessionDays = days)
        assertEquals(7, snap.sessionStreak)
        val result = IkdBadgeEvaluator.evaluateBadges(snap, emptySet(), nowMs)
        assertTrue(result.allUnlocked.contains("kb_streak_3"))
        assertTrue(result.allUnlocked.contains("kb_streak_7"))
        assertFalse(result.allUnlocked.contains("kb_streak_14"))
        val prog = result.progressByKey.getValue("kb_streak_14")
        assertEquals(7L, prog.current)
        assertEquals(14L, prog.target)
        assertEquals(UnitKind.DAYS, prog.unitKind)
    }

    // ---------- baselines ----------

    @Test
    fun allLocked_emptySnapshot_unlocksNothing_butHasProgressForEvery() {
        val snap = snapshot()
        val result = IkdBadgeEvaluator.evaluateBadges(snap, emptySet(), nowMs)
        assertTrue(result.newlyUnlocked.isEmpty())
        assertTrue(result.allUnlocked.isEmpty())
        // Every v1 badge has numeric progress (Decision #10).
        assertEquals(IkdBadgeCatalog.ALL.size, result.progressByKey.size)
    }

    @Test
    fun alreadyUnlocked_isNotReNotified() {
        val snap = snapshot(moodTimestamps = listOf(dayTs(0)))
        val result = IkdBadgeEvaluator.evaluateBadges(
            snap,
            alreadyUnlocked = setOf("mood_vol_1"),
            nowMs = nowMs,
        )
        assertTrue(result.newlyUnlocked.none { it.key == "mood_vol_1" })
        assertTrue(result.allUnlocked.contains("mood_vol_1"))
        // Progress still computed for completeness.
        assertTrue(result.progressByKey.containsKey("mood_vol_1"))
    }

    @Test
    fun allUnlocked_richSnapshot_unlocksTopTiers() {
        val moodTs = (0 until 1200).map { dayTs(it % 60) }
        val sessionDays = (0 until 400).map {
            "2025-01-01".let { _ ->
                java.time.LocalDate.of(2025, 1, 1).plusDays(it.toLong()).toString()
            }
        }
        val snap = snapshot(
            moodTimestamps = moodTs,
            keystrokeTotal = 6_000_000L,
            sessionDays = sessionDays,
        )
        val result = IkdBadgeEvaluator.evaluateBadges(snap, emptySet(), nowMs)
        assertTrue(result.allUnlocked.contains("mood_vol_1000"))
        assertTrue(result.allUnlocked.contains("kb_keys_5m"))
        assertTrue(result.allUnlocked.contains("kb_streak_365"))
        assertEquals(IkdBadgeCatalog.ALL.size, result.allUnlocked.size)
    }
}
