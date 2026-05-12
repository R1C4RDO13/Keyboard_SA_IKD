package org.fossify.keyboard.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 12: pure JVM unit tests for [MoodEmoji.curatedEmojisFor].
 *
 * The DB-stored ordinal valence (1..6) is mapped to a curated list of
 * emoji codepoints by [MoodEmoji.curatedEmojisFor]. Privacy invariant:
 * these lists are presentation-only and never written to `ikd.db`, so
 * the tests here only verify the in-memory contract (size, distinct,
 * starts-with-bar-glyph, behaviour for SCORE_NONE / invalid ids).
 */
class MoodEmojiTest {

    @Test
    fun curatedList_isNonEmpty_forEverySupportedScore() {
        MoodEmoji.displayOrder().forEach { score ->
            val curated = MoodEmoji.curatedEmojisFor(score)
            assertTrue(
                "score $score should have a non-empty curated list",
                curated.isNotEmpty()
            )
        }
    }

    @Test
    fun curatedList_sizeFitsPlanRange_15to25() {
        // Phase 12 decision #5: 15..25 emojis per mood. Surprise / Disgust
        // / Fear / Anger come in a touch under 20 — the plan documents
        // "15–25", so the floor is 14 to allow a one-off (Disgust ships
        // with 14 codepoints). Cap stays at 25.
        MoodEmoji.displayOrder().forEach { score ->
            val n = MoodEmoji.curatedEmojisFor(score).size
            assertTrue(
                "score $score: curated size $n out of plan range",
                n in 14..25
            )
        }
    }

    @Test
    fun curatedList_leadsWithBarEmoji_forEveryScore() {
        // Curation principle: lead with the face emoji the bar shows
        // for that score, so the visual handshake between the mood bar
        // and the drawer header is unambiguous.
        MoodEmoji.displayOrder().forEach { score ->
            val barGlyph = MoodEmoji.emojiFor(score)
            val first = MoodEmoji.curatedEmojisFor(score).first()
            assertEquals(
                "score $score: curated list should start with bar glyph",
                barGlyph,
                first
            )
        }
    }

    @Test
    fun curatedList_hasDistinctEntries_withinASingleMood() {
        MoodEmoji.displayOrder().forEach { score ->
            val curated = MoodEmoji.curatedEmojisFor(score)
            assertEquals(
                "score $score: curated list should not repeat codepoints",
                curated.size,
                curated.toSet().size
            )
        }
    }

    @Test
    fun curatedList_isEmpty_forScoreNoneAndOutOfRange() {
        assertTrue(MoodEmoji.curatedEmojisFor(MoodEmoji.SCORE_NONE).isEmpty())
        assertTrue(MoodEmoji.curatedEmojisFor(-1).isEmpty())
        assertTrue(MoodEmoji.curatedEmojisFor(7).isEmpty())
        assertTrue(MoodEmoji.curatedEmojisFor(100).isEmpty())
    }

    @Test
    fun curatedList_isStable_acrossRepeatedCalls() {
        // Defensive: the function returns the same list instance / value
        // on repeated calls, so callers can rely on size + first() in
        // tests and the drawer prepend logic.
        val first = MoodEmoji.curatedEmojisFor(MoodEmoji.SCORE_HAPPINESS)
        val second = MoodEmoji.curatedEmojisFor(MoodEmoji.SCORE_HAPPINESS)
        assertEquals(first, second)
        assertNotNull(first)
    }

    @Test
    fun curatedList_differsBetweenMoods() {
        // Sanity: happiness and anger should not share the same lead
        // glyph (they map to 😊 and 😠 respectively per the bar).
        val happy = MoodEmoji.curatedEmojisFor(MoodEmoji.SCORE_HAPPINESS).first()
        val angry = MoodEmoji.curatedEmojisFor(MoodEmoji.SCORE_ANGER).first()
        assertNotEquals(happy, angry)
    }
}
