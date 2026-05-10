package org.fossify.keyboard.helpers

import androidx.annotation.ColorRes
import org.fossify.keyboard.R

/**
 * Phase 8: UI-only mapping from `mood_score` ordinal valence (1..6) to the
 * emoji codepoint and label string-resource key shown in the keyboard mood
 * bar, the per-session KPI cell, the metadata chip, and the global
 * dashboard's Distribution panel + chart + Avg Mood KPI.
 *
 * The DB stores **only** the integer score — this is the only place where
 * the score → emoji / label mapping exists. Privacy invariant preserved
 * (decision #5 / section 6 of `roadmap/Phase8/Phase8_Plan.md`).
 *
 * Score taxonomy: Ekman's six basic emotions [Ekman 1972, 1992],
 * valence-ordered (1 = best, 6 = worst). Section 11 of the Phase 8 plan
 * cites the references; the best-to-worst ordering on the score id is a
 * deliberate Phase 8 product choice.
 */
object MoodEmoji {
    const val SHIELD_EMOJI: String = "🛡️" // 🛡️ — privacy slot
    const val HAPPINESS_EMOJI: String = "😊"    // 😊 score = 1
    const val SURPRISE_EMOJI: String = "😲"     // 😲 score = 2
    const val DISGUST_EMOJI: String = "🤢"      // 🤢 score = 3
    const val SADNESS_EMOJI: String = "😢"      // 😢 score = 4
    const val FEAR_EMOJI: String = "😨"         // 😨 score = 5
    const val ANGER_EMOJI: String = "😠"        // 😠 score = 6

    /** Ordinal valence ids in display order (best → worst). */
    fun displayOrder(): IntArray = intArrayOf(
        SCORE_HAPPINESS,
        SCORE_SURPRISE,
        SCORE_DISGUST,
        SCORE_SADNESS,
        SCORE_FEAR,
        SCORE_ANGER,
    )

    /**
     * Score → emoji codepoint. Caller is responsible for picking the right
     * cell on the bar / dashboard. Returns the blank-state placeholder for
     * out-of-range scores so the UI never crashes on a corrupt DB row.
     */
    fun emojiFor(score: Int): String = when (score) {
        SCORE_HAPPINESS -> HAPPINESS_EMOJI
        SCORE_SURPRISE -> SURPRISE_EMOJI
        SCORE_DISGUST -> DISGUST_EMOJI
        SCORE_SADNESS -> SADNESS_EMOJI
        SCORE_FEAR -> FEAR_EMOJI
        SCORE_ANGER -> ANGER_EMOJI
        else -> ""
    }

    /**
     * Score → label string-resource id. Callers resolve via `getString` to
     * pick up locale (Phase 10 rebrand concern; v1 is English only).
     */
    fun labelResFor(score: Int): Int = when (score) {
        SCORE_HAPPINESS -> R.string.mood_label_happiness
        SCORE_SURPRISE -> R.string.mood_label_surprise
        SCORE_DISGUST -> R.string.mood_label_disgust
        SCORE_SADNESS -> R.string.mood_label_sadness
        SCORE_FEAR -> R.string.mood_label_fear
        SCORE_ANGER -> R.string.mood_label_anger
        else -> R.string.mood_label_happiness
    }

    /**
     * Phase 9.17: score → `mood_color_*` colour-resource id. Single source
     * of truth for every per-mood tint on the dashboard (Mood Distribution
     * tiles, Mood Mix stacked-bar segments, mood-tinted Usage Map bubbles).
     * Callers resolve via `ContextCompat.getColor(...)` so the
     * `values-night` overrides pick up automatically. Out-of-range scores
     * fall back to the Happiness colour — same defensive shape as
     * [labelResFor].
     */
    @ColorRes
    fun colorResFor(score: Int): Int = when (score) {
        SCORE_HAPPINESS -> R.color.mood_color_happiness
        SCORE_SURPRISE -> R.color.mood_color_surprise
        SCORE_DISGUST -> R.color.mood_color_disgust
        SCORE_SADNESS -> R.color.mood_color_sadness
        SCORE_FEAR -> R.color.mood_color_fear
        SCORE_ANGER -> R.color.mood_color_anger
        else -> R.color.mood_color_happiness
    }

    /** True when [score] is a valid ordinal valence id (1..6). */
    fun isValidScore(score: Int): Boolean = score in SCORE_HAPPINESS..SCORE_ANGER

    /**
     * Phase 8.5: alias of [isValidScore] used by `Config.lastMoodScore`
     * read sites that want to express "is this a real standing rating"
     * rather than "is this a valid DB row score". Behaviourally identical
     * — the sentinel `SCORE_NONE = 0` is the only excluded value.
     */
    fun isStandingScore(score: Int): Boolean = isValidScore(score)

    /** Resource id for the privacy slot's accessibility label. */
    val privacyLabelRes: Int = R.string.privacy_label_on

    /**
     * Phase 8.5: sentinel for "no standing rating". Stored in
     * `Config.lastMoodScore` when (a) the user has never tapped a slot,
     * (b) the user explicitly deselected via Phase-8.2's
     * `clearMoodForActiveSession`, (c) Phase 8's `enablePrivacyAndClearMood`
     * fired, or (d) the inactivity-reset path zeroed it.
     */
    const val SCORE_NONE: Int = 0
    const val SCORE_HAPPINESS: Int = 1
    const val SCORE_SURPRISE: Int = 2
    const val SCORE_DISGUST: Int = 3
    const val SCORE_SADNESS: Int = 4
    const val SCORE_FEAR: Int = 5
    const val SCORE_ANGER: Int = 6
}
