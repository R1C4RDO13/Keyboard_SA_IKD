package org.fossify.keyboard.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor

/**
 * Phase 14 §7.3: the Daily-devotion "mini-keyboard" strip. Renders the
 * last 14 local days, oldest→newest, as small rounded key-like cells.
 * A qualifying day (≥ 3 intentional mood logs) is filled with the
 * user's primary tone; a non-qualifying day is the dim locked-surface
 * tint. The rightmost cell (today) gets a primary outline so the user
 * can read exactly where a strict consecutive streak broke.
 *
 * Privacy: it visualises only a boolean-per-day derived in Kotlin from
 * existing `mood_entries` timestamps — no counts, no text, nothing new
 * stored. Same theme-aware discipline as `IkdHeatmapView`: no hardcoded
 * colour literals, all paints sourced from theme tokens / colour
 * resources that have a `values-night` override.
 */
class IkdBadgeDayStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val rect = RectF()

    /** Last 14 days, oldest→newest; true = qualifying day (≥ 3 logs). */
    private var days: List<Boolean> = emptyList()

    fun setData(recentDayQualified: List<Boolean>) {
        days = recentDayQualified
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (days.isEmpty()) return

        val n = days.size
        val gapPx = dp(CELL_GAP_DP)
        val totalGap = gapPx * (n - 1)
        val cellW = (width - totalGap) / n
        val cellH = height.toFloat()
        val radius = dp(CELL_RADIUS_DP)
        val outlineW = dp(OUTLINE_DP)
        outlinePaint.strokeWidth = outlineW

        val primary = context.getProperPrimaryColor()
        val dim = context.getProperTextColor().adjustAlpha(DIM_CELL_ALPHA)
        val lastIndex = n - 1

        for (i in 0 until n) {
            val left = i * (cellW + gapPx)
            rect.set(
                left + outlineW,
                outlineW,
                left + cellW - outlineW,
                cellH - outlineW,
            )
            cellPaint.color = if (days[i]) primary else dim
            canvas.drawRoundRect(rect, radius, radius, cellPaint)
            if (i == lastIndex) {
                outlinePaint.color = primary
                canvas.drawRoundRect(rect, radius, radius, outlinePaint)
            }
        }
    }

    private fun dp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        resources.displayMetrics,
    )

    companion object {
        private const val CELL_GAP_DP = 4f
        private const val CELL_RADIUS_DP = 4f
        private const val OUTLINE_DP = 2f
        private const val DIM_CELL_ALPHA = 0.22f
    }
}
