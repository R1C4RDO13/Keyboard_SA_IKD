package org.fossify.keyboard.helpers

import androidx.annotation.StringRes
import org.fossify.keyboard.R

/**
 * Phase 14: the static badge spectrum — the literal Kotlin transcription
 * of the v1 rows in `roadmap/Phase14/Phase14_BadgeCatalog.md` (28 badges
 * across 5 groups; groups 2 "Mood Diversity" and 6 "KB Sessions" are
 * catalogued there but deferred — not added here in v1).
 *
 * Pure data — no Room, no I/O, no Android context. The
 * `criteria` / `progress` lambdas fold a [BadgeSnapshot] (built by
 * [IkdBadgeEvaluator] on `Dispatchers.IO`) into a boolean unlock decision
 * and a `(current, target)` progress pair. All 28 v1 badges have numeric
 * progress; the boolean (`progress == null`) card variant is spec'd for
 * the deferred group 2 only.
 *
 * Privacy invariant: the DB stores only the integer `keystrokeCount`,
 * `moodCount`, day-bucketed counts and the badge_key once unlocked. The
 * emoji / title / description live here and in `strings.xml` exclusively.
 */
object IkdBadgeCatalog {

    /**
     * Read-model the evaluator materialises once per run from the existing
     * tables (≤ 5 small SQL queries in v1). Every field is derivable from
     * `mood_entries` / `sessions` / `ikd_events` with no new captured data.
     *
     * @property moodCount total rows in `mood_entries` (group 1).
     * @property bestDayLogs max intentional mood logs in any single local
     *   calendar day (group 3).
     * @property devotionStreak longest run of *consecutive* local days each
     *   with ≥ 3 intentional mood logs (group 4, strict streak).
     * @property recentDayQualified last 14 local days, oldest→newest, each
     *   `true` when that day had ≥ 3 intentional logs (group 4 day-strip).
     * @property keystrokeTotal lifetime keystroke count, AUTOCORRECT
     *   excluded (group 5).
     * @property sessionStreak longest run of consecutive local days with
     *   ≥ 1 session (group 7).
     */
    data class BadgeSnapshot(
        val moodCount: Int,
        val bestDayLogs: Int,
        val devotionStreak: Int,
        val recentDayQualified: List<Boolean>,
        val keystrokeTotal: Long,
        val sessionStreak: Int,
    )

    /**
     * The `(current, target)` pair the locked-card progress bar renders.
     * [unitKind] selects the caption format (`6,432 / 10,000` vs
     * `3 / 7 days`). `current` is clamped to `target` by the UI.
     */
    data class BadgeProgress(
        val current: Long,
        val target: Long,
        val unitKind: UnitKind,
    )

    /** Caption format selector for [BadgeProgress]. */
    enum class UnitKind { COUNT, DAYS }

    /**
     * One catalogued badge. [key] is the stable id stored in
     * `badges.badge_key` (never reused, never renamed). [group] places it
     * on the Achievements carousel; [emoji] is decorative.
     */
    data class BadgeDef(
        val key: String,
        val group: BadgeGroup,
        val emoji: String,
        @StringRes val titleRes: Int,
        @StringRes val descRes: Int,
        val criteria: (BadgeSnapshot) -> Boolean,
        val progress: (BadgeSnapshot) -> BadgeProgress?,
    )

    /**
     * The five v1 groups, in carousel order. `titleRes` is the uppercase
     * primary-tinted group header; `hasDayStrip` is true only for the
     * Daily-devotion group (renders the last-14-days mini-keyboard strip).
     */
    enum class BadgeGroup(
        @StringRes val titleRes: Int,
        val hasDayStrip: Boolean,
    ) {
        MOOD_VOLUME(R.string.badge_group_mood_volume, false),
        MOOD_DAILY_CHECKIN(R.string.badge_group_mood_checkin, false),
        MOOD_DAILY_DEVOTION(R.string.badge_group_mood_devotion, true),
        KB_KEYSTROKE_VOLUME(R.string.badge_group_kb_keystrokes, false),
        KB_SESSION_STREAK(R.string.badge_group_kb_streak, false),
    }

    private fun countProgress(current: Long, target: Long) =
        BadgeProgress(current, target, UnitKind.COUNT)

    private fun daysProgress(current: Long, target: Long) =
        BadgeProgress(current, target, UnitKind.DAYS)

    /** Group 1 — Mood Volume (total rows in `mood_entries`). */
    private val moodVolume = listOf(
        moodVol("mood_vol_1", "🌱", R.string.badge_mood_vol_1_title, R.string.badge_mood_vol_1_desc, 1),
        moodVol("mood_vol_10", "🌿", R.string.badge_mood_vol_10_title, R.string.badge_mood_vol_10_desc, 10),
        moodVol("mood_vol_50", "🍀", R.string.badge_mood_vol_50_title, R.string.badge_mood_vol_50_desc, 50),
        moodVol("mood_vol_100", "🎯", R.string.badge_mood_vol_100_title, R.string.badge_mood_vol_100_desc, 100),
        moodVol("mood_vol_500", "🌳", R.string.badge_mood_vol_500_title, R.string.badge_mood_vol_500_desc, 500),
        moodVol(
            "mood_vol_1000", "🏔️",
            R.string.badge_mood_vol_1000_title, R.string.badge_mood_vol_1000_desc, 1000,
        ),
    )

    private fun moodVol(
        key: String,
        emoji: String,
        @StringRes titleRes: Int,
        @StringRes descRes: Int,
        target: Int,
    ) = BadgeDef(
        key = key,
        group = BadgeGroup.MOOD_VOLUME,
        emoji = emoji,
        titleRes = titleRes,
        descRes = descRes,
        criteria = { it.moodCount >= target },
        progress = { countProgress(it.moodCount.toLong(), target.toLong()) },
    )

    /** Group 3 — Daily check-in (best single calendar day ever). */
    private val moodCheckin = listOf(
        moodDay("mood_day_1", "📝", R.string.badge_mood_day_1_title, R.string.badge_mood_day_1_desc, 1),
        moodDay("mood_day_2", "✍️", R.string.badge_mood_day_2_title, R.string.badge_mood_day_2_desc, 2),
        moodDay("mood_day_3", "📔", R.string.badge_mood_day_3_title, R.string.badge_mood_day_3_desc, 3),
    )

    private fun moodDay(
        key: String,
        emoji: String,
        @StringRes titleRes: Int,
        @StringRes descRes: Int,
        target: Int,
    ) = BadgeDef(
        key = key,
        group = BadgeGroup.MOOD_DAILY_CHECKIN,
        emoji = emoji,
        titleRes = titleRes,
        descRes = descRes,
        criteria = { it.bestDayLogs >= target },
        progress = { countProgress(it.bestDayLogs.toLong(), target.toLong()) },
    )

    /** Group 4 — Daily devotion (strict consecutive ≥3-logs-per-day streak). */
    private val moodDevotion = listOf(
        moodDevotion("mood_3pd_1", "🌼", R.string.badge_mood_3pd_1_title, R.string.badge_mood_3pd_1_desc, 1),
        moodDevotion("mood_3pd_3", "💪", R.string.badge_mood_3pd_3_title, R.string.badge_mood_3pd_3_desc, 3),
        moodDevotion("mood_3pd_7", "🧗", R.string.badge_mood_3pd_7_title, R.string.badge_mood_3pd_7_desc, 7),
        moodDevotion(
            "mood_3pd_15", "🏵️",
            R.string.badge_mood_3pd_15_title, R.string.badge_mood_3pd_15_desc, 15,
        ),
        moodDevotion(
            "mood_3pd_30", "💎",
            R.string.badge_mood_3pd_30_title, R.string.badge_mood_3pd_30_desc, 30,
        ),
        moodDevotion(
            "mood_3pd_60", "🌠",
            R.string.badge_mood_3pd_60_title, R.string.badge_mood_3pd_60_desc, 60,
        ),
    )

    private fun moodDevotion(
        key: String,
        emoji: String,
        @StringRes titleRes: Int,
        @StringRes descRes: Int,
        target: Int,
    ) = BadgeDef(
        key = key,
        group = BadgeGroup.MOOD_DAILY_DEVOTION,
        emoji = emoji,
        titleRes = titleRes,
        descRes = descRes,
        criteria = { it.devotionStreak >= target },
        progress = { daysProgress(it.devotionStreak.toLong(), target.toLong()) },
    )

    /** Group 5 — Keystroke volume (cumulative lifetime keystrokes). */
    private val kbKeystrokes = listOf(
        kbKeys("kb_keys_1k", "🔤", R.string.badge_kb_keys_1k_title, R.string.badge_kb_keys_1k_desc, 1_000),
        kbKeys("kb_keys_10k", "📝", R.string.badge_kb_keys_10k_title, R.string.badge_kb_keys_10k_desc, 10_000),
        kbKeys(
            "kb_keys_100k", "📚",
            R.string.badge_kb_keys_100k_title, R.string.badge_kb_keys_100k_desc, 100_000,
        ),
        kbKeys(
            "kb_keys_500k", "🖋️",
            R.string.badge_kb_keys_500k_title, R.string.badge_kb_keys_500k_desc, 500_000,
        ),
        kbKeys(
            "kb_keys_1m", "⌨️",
            R.string.badge_kb_keys_1m_title, R.string.badge_kb_keys_1m_desc, 1_000_000,
        ),
        kbKeys(
            "kb_keys_5m", "🗿",
            R.string.badge_kb_keys_5m_title, R.string.badge_kb_keys_5m_desc, 5_000_000,
        ),
    )

    private fun kbKeys(
        key: String,
        emoji: String,
        @StringRes titleRes: Int,
        @StringRes descRes: Int,
        target: Long,
    ) = BadgeDef(
        key = key,
        group = BadgeGroup.KB_KEYSTROKE_VOLUME,
        emoji = emoji,
        titleRes = titleRes,
        descRes = descRes,
        criteria = { it.keystrokeTotal >= target },
        progress = { countProgress(it.keystrokeTotal, target) },
    )

    /** Group 7 — Session streak (longest run of consecutive session days). */
    private val kbStreak = listOf(
        kbStreak("kb_streak_3", "🔥", R.string.badge_kb_streak_3_title, R.string.badge_kb_streak_3_desc, 3),
        kbStreak("kb_streak_7", "🔥", R.string.badge_kb_streak_7_title, R.string.badge_kb_streak_7_desc, 7),
        kbStreak(
            "kb_streak_14", "🔥",
            R.string.badge_kb_streak_14_title, R.string.badge_kb_streak_14_desc, 14,
        ),
        kbStreak(
            "kb_streak_30", "🏆",
            R.string.badge_kb_streak_30_title, R.string.badge_kb_streak_30_desc, 30,
        ),
        kbStreak(
            "kb_streak_60", "🏅",
            R.string.badge_kb_streak_60_title, R.string.badge_kb_streak_60_desc, 60,
        ),
        kbStreak(
            "kb_streak_100", "💯",
            R.string.badge_kb_streak_100_title, R.string.badge_kb_streak_100_desc, 100,
        ),
        kbStreak(
            "kb_streak_365", "👑",
            R.string.badge_kb_streak_365_title, R.string.badge_kb_streak_365_desc, 365,
        ),
    )

    private fun kbStreak(
        key: String,
        emoji: String,
        @StringRes titleRes: Int,
        @StringRes descRes: Int,
        target: Int,
    ) = BadgeDef(
        key = key,
        group = BadgeGroup.KB_SESSION_STREAK,
        emoji = emoji,
        titleRes = titleRes,
        descRes = descRes,
        criteria = { it.sessionStreak >= target },
        progress = { daysProgress(it.sessionStreak.toLong(), target.toLong()) },
    )

    /**
     * All 28 v1 badges in carousel order. Iteration order is stable —
     * the evaluator and the UI both depend on "first not-yet-unlocked in
     * catalog order" being the focus card.
     */
    val ALL: List<BadgeDef> =
        moodVolume + moodCheckin + moodDevotion + kbKeystrokes + kbStreak

    /** v1 groups in carousel order. */
    val GROUPS: List<BadgeGroup> = listOf(
        BadgeGroup.MOOD_VOLUME,
        BadgeGroup.MOOD_DAILY_CHECKIN,
        BadgeGroup.MOOD_DAILY_DEVOTION,
        BadgeGroup.KB_KEYSTROKE_VOLUME,
        BadgeGroup.KB_SESSION_STREAK,
    )

    fun badgesFor(group: BadgeGroup): List<BadgeDef> = ALL.filter { it.group == group }

    private val byKey: Map<String, BadgeDef> = ALL.associateBy { it.key }

    /** Catalog lookup by stable badge key, or null if unknown. */
    fun defFor(key: String): BadgeDef? = byKey[key]
}
