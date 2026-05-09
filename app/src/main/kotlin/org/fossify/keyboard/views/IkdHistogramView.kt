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
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor

/**
 * Phase 9.7: theme-aware histogram chart wrapping MPAndroidChart's
 * [BarChart]. Single setter [setData] takes parallel lists of bucket
 * labels + counts; the X axis is rendered every other tick to keep
 * dense log-scale labels readable.
 *
 * Outlier annotation (when `outlierCount > 0`) is the activity's
 * concern — handled via a small `MyTextView` overlay sibling, not on
 * the chart itself. This keeps the chart wrapper focused on plotting.
 */
class IkdHistogramView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : BarChart(context, attrs, defStyle) {

    init {
        applyStaticConfig()
    }

    private fun applyStaticConfig() {
        axisRight.isEnabled = false
        description.isEnabled = false
        legend.isEnabled = false

        setNoDataText("")
        setTouchEnabled(true)
        isDragEnabled = false
        setScaleEnabled(false)
        setPinchZoom(false)
        setDrawGridBackground(false)

        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f
        // Dense bucket labels — render every second tick to avoid overlap.
        xAxis.setLabelCount(LABEL_TICK_COUNT, true)

        applyTheme()
    }

    private fun applyTheme() {
        val textColor = context.getProperTextColor()
        val backgroundColor = context.getProperBackgroundColor()
        setBackgroundColor(Color.TRANSPARENT)
        xAxis.textColor = textColor
        axisLeft.textColor = textColor
        axisLeft.gridColor = textColor and ALPHA_LOW_GRID_COLOR_MASK
        axisLeft.axisLineColor = textColor
        xAxis.axisLineColor = textColor
        setNoDataTextColor(backgroundColor.coerceTextColor(textColor))
    }

    /**
     * Populate the histogram. `labels` and `counts` must be the same length.
     */
    fun setData(labels: List<String>, counts: List<Int>) {
        require(labels.size == counts.size) {
            "labels and counts must be the same length"
        }
        if (counts.all { it == 0 }) {
            data = null
            invalidate()
            return
        }

        val entries = counts.mapIndexed { index, value -> BarEntry(index.toFloat(), value.toFloat()) }
        val primary = context.getProperPrimaryColor()
        val dataSet = BarDataSet(entries, "").apply {
            color = primary
            setDrawValues(false)
            isHighlightEnabled = false
        }

        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        data = BarData(dataSet)
        notifyDataSetChanged()
        invalidate()
    }

    private fun Int.coerceTextColor(textColor: Int): Int = if (this == textColor) textColor else this

    companion object {
        private const val LABEL_TICK_COUNT = 5
        private const val ALPHA_LOW_GRID_COLOR_MASK = 0x33FFFFFF.toInt()
    }
}
