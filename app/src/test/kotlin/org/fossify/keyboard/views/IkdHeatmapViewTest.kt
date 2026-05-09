package org.fossify.keyboard.views

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 9.5: pure-math tests for [IkdHeatmapView.Companion.cellAlpha].
 */
class IkdHeatmapViewTest {

    @Test
    fun zeroCount_returnsFloorAlpha() {
        assertEquals(IkdHeatmapView.FLOOR_ALPHA, IkdHeatmapView.cellAlpha(count = 0, max = 10), 0.0001f)
    }

    @Test
    fun maxCount_returnsOne() {
        assertEquals(1.0f, IkdHeatmapView.cellAlpha(count = 10, max = 10), 0.0001f)
    }

    @Test
    fun halfCount_returnsHalf() {
        assertEquals(0.5f, IkdHeatmapView.cellAlpha(count = 5, max = 10), 0.0001f)
    }

    @Test
    fun zeroMax_returnsFloorAlpha() {
        assertEquals(IkdHeatmapView.FLOOR_ALPHA, IkdHeatmapView.cellAlpha(count = 100, max = 0), 0.0001f)
    }

    @Test
    fun lowCountClampedToFloor() {
        // 1 / 1000 = 0.001, below FLOOR_ALPHA (0.05) → floor.
        assertEquals(IkdHeatmapView.FLOOR_ALPHA, IkdHeatmapView.cellAlpha(count = 1, max = 1000), 0.0001f)
    }

    @Test
    fun overshootClampedToOne() {
        // count > max shouldn't happen in practice but defensive clamp keeps alpha in range.
        assertEquals(1.0f, IkdHeatmapView.cellAlpha(count = 100, max = 10), 0.0001f)
    }
}
