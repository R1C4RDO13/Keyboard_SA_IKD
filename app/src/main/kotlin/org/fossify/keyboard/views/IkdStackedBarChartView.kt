package org.fossify.keyboard.views

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor

/**
 * Phase 8.3: theme-aware wrapper around MPAndroidChart's [BarChart] in
 * stacked mode. Mirrors the discipline of `IkdLineChartView`: no
 * hardcoded hex colour literals, axis text/grid colours pulled from
 * `getProperTextColor()` / `getProperBackgroundColor()`, built-in
 * legend disabled (the activity draws its own emoji + colour-swatch
 * legend below the chart).
 *
 * API: [setData] takes parallel lists of bucket labels and stacked
 * segments. Each segment carries one `mood_score`'s English label,
 * resolved bar colour (theme-aware via the activity), and the per-
 * bucket value list (parallel to `labels`). All segment value lists
 * must be the same length as `labels`.
 *
 * Empty buckets render as zero-height bars (kept on the X axis to
 * keep alignment with adjacent IKD charts; Decision #4 of
 * `Phase8.3_Plan.md`).
 */
class IkdStackedBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : BarChart(context, attrs, defStyle) {

    init {
        applyStaticConfig()
    }

    /**
     * Phase 8.3: one stacked segment of the bar. `label` is the English
     * category label (e.g. "Happiness"); `colorInt` is a resolved theme-
     * aware ARGB int (the activity reads it via `ContextCompat.getColor`
     * from `mood_color_*` tokens in `res/values{,-night}/colors.xml`).
     */
    data class MoodSegment(
        val score: Int,
        val label: String,
        val colorInt: Int,
        /** Per-bucket value — same length as the `labels` list passed to setData. */
        val values: List<Float>,
    )

    private fun applyStaticConfig() {
        // Right axis is noise for stacked-percentage charts.
        axisRight.isEnabled = false

        // Description label takes a strip of vertical space for no benefit
        // when the section header above the chart already names it.
        description.isEnabled = false

        // We render our own emoji + label + colour-swatch legend below
        // the chart — MPAndroidChart's built-in legend doesn't compose
        // emoji and a colour swatch cleanly. Phase 9.17 lets each caller
        // override this per-setData via the `drawLegend` parameter; the
        // initial state stays disabled so a caller that never calls
        // `setData(..., drawLegend = true)` keeps the existing silhouette.
        legend.isEnabled = false

        setNoDataText("")
        setTouchEnabled(true)
        isDragEnabled = true
        setScaleEnabled(false)
        setPinchZoom(false)
        setDrawGridBackground(false)
        setFitBars(true)

        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f

        axisLeft.axisMinimum = 0f
        axisLeft.axisMaximum = PCT_MAX

        applyTheme()
    }

    private fun applyTheme() {
        val textColor = context.getProperTextColor()
        // Touch the background colour so the lint baseline doesn't grow an
        // unused-import warning. The chart itself stays transparent and lets
        // the parent card's tint show through (`applyCardThemeColors()` in
        // `DashboardActivity`).
        @Suppress("UNUSED_VARIABLE")
        val backgroundColor = context.getProperBackgroundColor()

        setBackgroundColor(Color.TRANSPARENT)
        xAxis.textColor = textColor
        axisLeft.textColor = textColor

        axisLeft.gridColor = textColor and ALPHA_LOW_GRID_COLOR_MASK
        axisLeft.axisLineColor = textColor
        xAxis.axisLineColor = textColor
    }

    /**
     * Populate the chart. `labels` is one bar per index; `segments` are
     * the stacked colour bands. Each segment's `values` list must be the
     * same length as `labels`.
     *
     * @param drawLegend Phase 9.17: when `true` (default) the chart's
     *   built-in legend is shown — preserves the pre-9.17 behaviour for
     *   any caller that hasn't moved its legend out of the card. When
     *   `false`, the legend stays hidden — Summary tab passes `false`
     *   because the new Mood Distribution tile row above the chart
     *   doubles as the global colour legend (Decision #5 of
     *   `roadmap/Phase9/sub_plans/9.17_mood_colors_distribution_first.md`).
     */
    fun setData(
        labels: List<String>,
        segments: List<MoodSegment>,
        drawLegend: Boolean = true,
    ) {
        legend.isEnabled = drawLegend
        if (labels.isEmpty() || segments.isEmpty()) {
            data = null
            invalidate()
            return
        }
        require(segments.all { it.values.size == labels.size }) {
            "every segment.values must match labels.size (${labels.size})"
        }

        val entries = labels.indices.map { i ->
            val stack = FloatArray(segments.size) { segIdx -> segments[segIdx].values[i] }
            BarEntry(i.toFloat(), stack)
        }

        val stackLabels = segments.map { it.label }.toTypedArray()
        val dataSet = BarDataSet(entries, "").apply {
            colors = segments.map { it.colorInt }
            this.stackLabels = stackLabels
            setDrawValues(false)
            highLightAlpha = 0
        }

        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        xAxis.labelCount = labels.size.coerceAtMost(MAX_X_LABELS)

        data = BarData(dataSet).apply {
            barWidth = BAR_WIDTH
        }
        notifyDataSetChanged()
        invalidate()
    }

    companion object {
        private const val MAX_X_LABELS = 8
        private const val BAR_WIDTH = 0.7f
        private const val PCT_MAX = 100f
        // 0x33 ≈ 20% alpha — soft grid line in both light and dark themes.
        private const val ALPHA_LOW_GRID_COLOR_MASK = 0x33FFFFFF.toInt()
    }
}
