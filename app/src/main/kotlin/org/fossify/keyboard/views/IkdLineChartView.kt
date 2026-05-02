package org.fossify.keyboard.views

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor

/**
 * Theme-aware wrapper around MPAndroidChart's [LineChart] used by the dashboard.
 *
 * Single setter [setData] takes parallel lists of labels + values. Values may be `null`
 * to represent a missing bucket; the chart renders these as line breaks rather than
 * fake zeros.
 */
class IkdLineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LineChart(context, attrs, defStyle) {

    init {
        applyStaticConfig()
    }

    private fun applyStaticConfig() {
        // Right-axis is noise for single-series trend charts.
        axisRight.isEnabled = false

        // Description label takes a strip of vertical space for no benefit when the
        // section header above the chart already names it.
        description.isEnabled = false

        // Hide the legend on small screens (sw < 600dp). Single series → label is
        // redundant, and the legend eats height we'd rather use for the line.
        legend.isEnabled = resources.configuration.smallestScreenWidthDp >= SMALL_SCREEN_DP

        // No animation on first render — every range switch otherwise replays.
        setNoDataText("")
        setTouchEnabled(true)
        isDragEnabled = true
        setScaleEnabled(false)
        setPinchZoom(false)
        setDrawGridBackground(false)

        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f

        applyTheme()
    }

    private fun applyTheme() {
        val textColor = context.getProperTextColor()
        val backgroundColor = context.getProperBackgroundColor()

        setBackgroundColor(Color.TRANSPARENT)
        xAxis.textColor = textColor
        axisLeft.textColor = textColor
        legend.textColor = textColor

        axisLeft.gridColor = textColor and ALPHA_LOW_GRID_COLOR_MASK
        axisLeft.axisLineColor = textColor
        xAxis.axisLineColor = textColor

        // Touch the background color so the lint baseline doesn't grow an unused-import
        // warning if it's later trimmed; also lets a future dark-mode-only override land
        // here without re-deriving the colour.
        setNoDataTextColor(backgroundColor.coerceTextColor(textColor))
    }

    /**
     * Populate the chart. `labels` and `values` must be the same length.
     * `null` entries in `values` are skipped to produce a line break.
     */
    fun setData(labels: List<String>, values: List<Float?>, yAxisLabel: String) {
        require(labels.size == values.size) {
            "labels and values must be the same length (was ${labels.size} vs ${values.size})"
        }

        val entries = values.mapIndexedNotNull { index, value ->
            value?.let { Entry(index.toFloat(), it) }
        }

        if (entries.isEmpty()) {
            data = null
            invalidate()
            return
        }

        val primary = context.getProperPrimaryColor()
        val dataSet = LineDataSet(entries, yAxisLabel).apply {
            color = primary
            setCircleColor(primary)
            lineWidth = LINE_WIDTH_DP
            circleRadius = CIRCLE_RADIUS_DP
            setDrawValues(false)
            setDrawCircleHole(false)
            // Tell MPAndroidChart to break the line on missing X positions so a gap
            // in `values` shows as a discontinuity rather than a vertical drop to 0.
            isHighlightEnabled = false
            mode = LineDataSet.Mode.LINEAR
        }

        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        xAxis.labelCount = labels.size.coerceAtMost(MAX_X_LABELS)

        data = LineData(dataSet)
        notifyDataSetChanged()
        invalidate()
    }

    /**
     * Coerce the no-data text color away from a value indistinguishable from
     * the background, so the (rare) empty-data path stays legible. Falls back
     * to the foreground text color when background and foreground are too close.
     */
    private fun Int.coerceTextColor(textColor: Int): Int = if (this == textColor) textColor else this

    companion object {
        private const val SMALL_SCREEN_DP = 600
        private const val LINE_WIDTH_DP = 2f
        private const val CIRCLE_RADIUS_DP = 3f
        private const val MAX_X_LABELS = 8
        // 0x33 ≈ 20% alpha — soft grid line in both light and dark themes.
        private const val ALPHA_LOW_GRID_COLOR_MASK = 0x33FFFFFF.toInt()
    }
}
