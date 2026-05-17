package org.fossify.keyboard.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.keyboard.R
import org.fossify.keyboard.helpers.MoodEmoji

/**
 * Phase 9.9: theme-aware date×hour bubble chart powering the dashboard's
 * Usage Map card. Y axis = calendar date (newest at top), X axis = hour
 * of day (0..23). Bubble area is linearly proportional to the
 * `(date, hour)` keystroke count — clamped between
 * `bubble_map_min_radius_dp` and `bubble_map_max_radius_dp`.
 *
 * Tap-to-toast hit-testing: each bubble's pickable area is inflated to a
 * minimum 24 dp from centre regardless of the visual radius
 * (accessibility — sub-3 dp bubbles would otherwise be unhittable).
 *
 * No hardcoded colour literals — mood-classified bubbles use their
 * `mood_color_*` token (Phase 9.17), unclassified bubbles use a
 * theme-aware low-alpha neutral grey derived from `getProperTextColor()`,
 * axis labels use `getProperTextColor()`. Theme switches propagate on
 * the next `setData()` call.
 */
class IkdBubbleMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    /**
     * One `(day, hour)` bubble. `day` is `YYYY-MM-DD`.
     *
     * Phase 9.17: [dominantMood] is the `mood_score` (1..6) of the most
     * frequent mood among sessions in this cell, or `null` when no
     * session in the cell has a mood entry. `null` falls back to the
     * existing primary-tinted bubble — preserves the "no mood data"
     * visual identity (Decision #7 of the Phase 9.17 plan).
     */
    data class Bubble(
        val day: String,
        val hour: Int,
        val count: Int,
        val dominantMood: Int? = null,
    )

    /** Listener for tap-to-toast drilldown — passes back the closest bubble. */
    fun interface OnBubbleClickListener {
        fun onBubbleClick(bubble: Bubble)
    }

    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var fallbackBubbleColor: Int = Color.TRANSPARENT
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            LABEL_TEXT_SIZE_SP,
            resources.displayMetrics,
        )
    }
    private val yLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        textSize = labelPaint.textSize
    }

    private var bubbles: List<Bubble> = emptyList()
    private var orderedDays: List<String> = emptyList()
    private var maxCount: Int = 0
    private val minRadiusPx = resources.getDimension(R.dimen.bubble_map_min_radius_dp)
    private val maxRadiusPx = resources.getDimension(R.dimen.bubble_map_max_radius_dp)
    private val tapTargetPx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        TAP_TARGET_DP,
        resources.displayMetrics,
    )
    private var clickListener: OnBubbleClickListener? = null

    fun setData(rows: List<Bubble>) {
        this.bubbles = rows
        // Newest day at the top — sort descending.
        orderedDays = rows.map { it.day }.distinct().sortedDescending()
        maxCount = rows.maxOfOrNull { it.count } ?: 0
        applyTheme()
        invalidate()
        requestLayout()
    }

    fun setOnBubbleClickListener(listener: OnBubbleClickListener?) {
        this.clickListener = listener
    }

    private fun applyTheme() {
        labelPaint.color = context.getProperTextColor()
        yLabelPaint.color = context.getProperTextColor()
        // Unclassified (no dominant mood) bubbles render as a neutral
        // grey instead of the primary theme tint, so the primary colour
        // can't be mistaken for a mood. Derived from the theme text
        // colour at a low alpha: dark text faded on a light theme and
        // light text faded on a dark theme both read as a soft grey,
        // so it stays legible on either Fossify theme without a
        // hardcoded hex literal.
        fallbackBubbleColor = ColorUtils.setAlphaComponent(
            context.getProperTextColor(),
            UNCLASSIFIED_BUBBLE_ALPHA,
        )
        bubblePaint.color = fallbackBubbleColor
    }

    /**
     * Phase 9.17: resolve the fill colour for a single bubble. When the
     * cell has a dominant mood, look up the matching `mood_color_*`
     * token via [MoodEmoji.colorResFor] and apply the same per-bubble
     * alpha as the fallback. When the cell has no mood, fall back to
     * the cached theme-aware neutral grey.
     */
    private fun bubbleFillColor(score: Int?): Int = if (score == null) {
        fallbackBubbleColor
    } else {
        ColorUtils.setAlphaComponent(
            ContextCompat.getColor(context, MoodEmoji.colorResFor(score)),
            BUBBLE_ALPHA,
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val rowCount = orderedDays.size.coerceAtLeast(1)
        val rowHeight = ROW_HEIGHT_DP * resources.displayMetrics.density
        val labelHeightPx = labelPaint.textSize * X_LABEL_FACTOR
        val height = (rowCount * rowHeight + paddingTop + paddingBottom + labelHeightPx).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bubbles.isEmpty()) return

        val labelWidthPx = Y_LABEL_WIDTH_DP * resources.displayMetrics.density
        val labelHeightPx = labelPaint.textSize * X_LABEL_FACTOR
        val gridLeft = paddingLeft.toFloat() + labelWidthPx
        val gridTop = paddingTop.toFloat()
        val gridRight = width - paddingRight.toFloat()
        val gridBottom = height - paddingBottom.toFloat() - labelHeightPx
        val cellWidth = (gridRight - gridLeft) / HOURS_PER_DAY
        val rowCount = orderedDays.size.coerceAtLeast(1)
        val cellHeight = (gridBottom - gridTop) / rowCount

        val dayIndex = orderedDays.withIndex().associate { (idx, day) -> day to idx }

        for (bubble in bubbles) {
            val rowIdx = dayIndex[bubble.day] ?: continue
            val cx = gridLeft + cellWidth * (bubble.hour + HALF_OFFSET)
            val cy = gridTop + cellHeight * (rowIdx + HALF_OFFSET)
            val radius = computeBubbleRadius(bubble.count)
            bubblePaint.color = bubbleFillColor(bubble.dominantMood)
            canvas.drawCircle(cx, cy, radius, bubblePaint)
        }

        // X-axis hour labels every 6 hours.
        val xLabelY = gridBottom + labelPaint.textSize
        for (hour in HOUR_LABEL_TICKS) {
            val x = gridLeft + cellWidth * (hour + HALF_OFFSET)
            canvas.drawText("%02d".format(hour), x, xLabelY, labelPaint)
        }

        // Y-axis date labels (every Nth row to avoid clutter).
        val labelStride = (rowCount / Y_LABEL_TICKS).coerceAtLeast(1)
        for (idx in orderedDays.indices step labelStride) {
            val day = orderedDays[idx]
            val y = gridTop + cellHeight * (idx + HALF_OFFSET) + yLabelPaint.textSize / Y_LABEL_BASELINE_DIVISOR
            canvas.drawText(day, gridLeft - Y_LABEL_PADDING_PX, y, yLabelPaint)
        }
    }

    private fun computeBubbleRadius(count: Int): Float = bubbleRadius(
        count = count,
        max = maxCount,
        minDp = minRadiusPx,
        maxDp = maxRadiusPx,
    )

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return super.onTouchEvent(event)
        val listener = clickListener ?: return super.onTouchEvent(event)
        if (bubbles.isEmpty()) return super.onTouchEvent(event)

        val labelWidthPx = Y_LABEL_WIDTH_DP * resources.displayMetrics.density
        val labelHeightPx = labelPaint.textSize * X_LABEL_FACTOR
        val gridLeft = paddingLeft.toFloat() + labelWidthPx
        val gridTop = paddingTop.toFloat()
        val gridRight = width - paddingRight.toFloat()
        val gridBottom = height - paddingBottom.toFloat() - labelHeightPx

        if (event.x < gridLeft || event.x > gridRight || event.y < gridTop || event.y > gridBottom) {
            return super.onTouchEvent(event)
        }

        val cellWidth = (gridRight - gridLeft) / HOURS_PER_DAY
        val rowCount = orderedDays.size.coerceAtLeast(1)
        val cellHeight = (gridBottom - gridTop) / rowCount
        val dayIndex = orderedDays.withIndex().associate { (idx, day) -> day to idx }

        // Find the closest bubble whose pickable area covers the tap.
        val hit = bubbles.firstOrNull { bubble ->
            val rowIdx = dayIndex[bubble.day] ?: return@firstOrNull false
            val cx = gridLeft + cellWidth * (bubble.hour + HALF_OFFSET)
            val cy = gridTop + cellHeight * (rowIdx + HALF_OFFSET)
            val pickable = maxOf(computeBubbleRadius(bubble.count), tapTargetPx)
            val dx = event.x - cx
            val dy = event.y - cy
            dx * dx + dy * dy <= pickable * pickable
        } ?: return super.onTouchEvent(event)
        listener.onBubbleClick(hit)
        performClick()
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    init {
        setBackgroundColor(Color.TRANSPARENT)
        applyTheme()
    }

    companion object {
        private const val LABEL_TEXT_SIZE_SP: Float = 10f
        private const val X_LABEL_FACTOR: Float = 1.6f
        private const val Y_LABEL_WIDTH_DP: Float = 56f
        private const val ROW_HEIGHT_DP: Float = 14f
        private const val HALF_OFFSET: Float = 0.5f
        private const val Y_LABEL_BASELINE_DIVISOR: Float = 3f
        private const val Y_LABEL_PADDING_PX: Float = 4f
        private const val TAP_TARGET_DP: Float = 24f
        // 0xCC ≈ 80% alpha on the mood-classified bubble fill.
        private const val BUBBLE_ALPHA: Int = 0xCC
        // 0x3D ≈ 24% alpha — a soft neutral grey for unclassified
        // (no dominant mood) bubbles on either Fossify theme.
        private const val UNCLASSIFIED_BUBBLE_ALPHA: Int = 0x3D
        private const val HOURS_PER_DAY: Int = 24
        private const val Y_LABEL_TICKS: Int = 8
        private val HOUR_LABEL_TICKS: IntArray = intArrayOf(0, 6, 12, 18)

        /**
         * Pure helper for unit testing. Linearly interpolates the bubble
         * radius between `[minDp, maxDp]` based on `count / max`. Returns
         * `minDp` when `max == 0` (every cell is empty — visible scaffold).
         */
        fun bubbleRadius(count: Int, max: Int, minDp: Float, maxDp: Float): Float {
            if (max <= 0) return minDp
            val ratio = (count.toFloat() / max.toFloat()).coerceIn(0f, 1f)
            return minDp + (maxDp - minDp) * ratio
        }
    }
}
