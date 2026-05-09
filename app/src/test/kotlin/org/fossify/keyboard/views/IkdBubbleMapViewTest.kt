package org.fossify.keyboard.views

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 9.9: pure-math tests for [IkdBubbleMapView.Companion.bubbleRadius].
 */
class IkdBubbleMapViewTest {

    @Test
    fun zeroCount_returnsMinDp() {
        // count = 0 → ratio 0 → minDp.
        assertEquals(MIN_DP, IkdBubbleMapView.bubbleRadius(count = 0, max = 100, MIN_DP, MAX_DP), 0.0001f)
    }

    @Test
    fun maxCount_returnsMaxDp() {
        assertEquals(MAX_DP, IkdBubbleMapView.bubbleRadius(count = 100, max = 100, MIN_DP, MAX_DP), 0.0001f)
    }

    @Test
    fun midCount_linearlyInterpolates() {
        // count=50, max=100, ratio=0.5 → minDp + (maxDp-minDp)*0.5 = 1 + 11*0.5 = 6.5
        assertEquals(6.5f, IkdBubbleMapView.bubbleRadius(count = 50, max = 100, MIN_DP, MAX_DP), 0.0001f)
    }

    @Test
    fun zeroMax_returnsMinDp() {
        assertEquals(MIN_DP, IkdBubbleMapView.bubbleRadius(count = 100, max = 0, MIN_DP, MAX_DP), 0.0001f)
    }

    @Test
    fun overshoot_clampedToMaxDp() {
        // count > max — defensive clamp.
        assertEquals(MAX_DP, IkdBubbleMapView.bubbleRadius(count = 200, max = 100, MIN_DP, MAX_DP), 0.0001f)
    }

    @Test
    fun lowCount_lowRatio_lowRadius() {
        // count=1, max=100, ratio=0.01 → minDp + 11*0.01 = 1.11
        val expected = MIN_DP + (MAX_DP - MIN_DP) * 0.01f
        assertEquals(expected, IkdBubbleMapView.bubbleRadius(count = 1, max = 100, MIN_DP, MAX_DP), 0.0001f)
    }

    companion object {
        // Test-only "dp" values; in production these come from the
        // bubble_map_min_radius_dp / bubble_map_max_radius_dp dimens.
        private const val MIN_DP = 1f
        private const val MAX_DP = 12f
    }
}
