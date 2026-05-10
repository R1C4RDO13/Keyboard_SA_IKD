package org.fossify.keyboard.activities.dashboard

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.views.MyTextView
import org.fossify.keyboard.R
import org.fossify.keyboard.databinding.FragmentDashboardKeystrokeDynamicsBinding
import org.fossify.keyboard.databinding.ItemOrientationLegendBinding
import org.fossify.keyboard.helpers.IkdDistributionAggregator
import org.fossify.keyboard.helpers.IkdOrientationAggregator

/**
 * Phase 9.11: Keystroke Dynamics tab. Three log-scale histograms over
 * IKD / dwell / flight times (9.7) plus the orientation breakdown
 * donut (9.8).
 */
class KeystrokeDynamicsFragment : DashboardFragment() {

    private var _binding: FragmentDashboardKeystrokeDynamicsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDashboardKeystrokeDynamicsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun renderPayload(payload: DashboardPayload) {
        val view = _binding ?: return
        val distribution = payload.distribution
        val orientation = payload.orientation

        applyCardThemeColors()

        val labels = IkdDistributionAggregator.bucketLabels()
        val ikdHasData = distribution.ikdHistogram.buckets.any { it > 0 }
        val dwellHasData = distribution.holdHistogram.buckets.any { it > 0 }
        val flightHasData = distribution.flightHistogram.buckets.any { it > 0 }
        val orientationHasData = orientation.slices.any { it.sessionCount > 0 }

        view.dashboardIkdDistributionCard.beVisibleIf(ikdHasData)
        if (ikdHasData) {
            view.dashboardIkdDistributionChart.setData(labels, distribution.ikdHistogram.buckets)
            bindOutlierLabel(view.dashboardIkdDistributionOutliers, distribution.ikdHistogram.outlierCount)
        }

        view.dashboardDwellDistributionCard.beVisibleIf(dwellHasData)
        if (dwellHasData) {
            view.dashboardDwellDistributionChart.setData(labels, distribution.holdHistogram.buckets)
            bindOutlierLabel(view.dashboardDwellDistributionOutliers, distribution.holdHistogram.outlierCount)
        }

        view.dashboardFlightDistributionCard.beVisibleIf(flightHasData)
        if (flightHasData) {
            view.dashboardFlightDistributionChart.setData(labels, distribution.flightHistogram.buckets)
            bindOutlierLabel(view.dashboardFlightDistributionOutliers, distribution.flightHistogram.outlierCount)
        }

        view.dashboardOrientationCard.beVisibleIf(orientationHasData)
        if (orientationHasData) {
            bindOrientationDonut(orientation.slices)
        }

        val anyHasData = ikdHasData || dwellHasData || flightHasData || orientationHasData
        if (anyHasData) {
            view.fragmentKeystrokeDynamicsEmptyMessage.beGone()
        } else {
            view.fragmentKeystrokeDynamicsEmptyMessage.beVisible()
        }
    }

    private fun applyCardThemeColors() {
        val ctx = context ?: return
        val view = _binding ?: return
        val bg = ctx.getProperBackgroundColor()
        view.dashboardIkdDistributionCard.setCardBackgroundColor(bg)
        view.dashboardDwellDistributionCard.setCardBackgroundColor(bg)
        view.dashboardFlightDistributionCard.setCardBackgroundColor(bg)
        view.dashboardOrientationCard.setCardBackgroundColor(bg)
    }

    private fun bindOrientationDonut(slices: List<IkdOrientationAggregator.OrientationSlice>) {
        val ctx = context ?: return
        val view = _binding ?: return
        val chart = view.dashboardOrientationChart
        val totalSessions = slices.sumOf { it.sessionCount }
        val totalDurationMs = slices.sumOf { it.totalDurationMs }

        val visible = slices.filter { it.sessionCount > 0 }
        val entries = visible.map { slice ->
            PieEntry(slice.sessionCount.toFloat(), labelForOrientation(slice.orientation))
        }
        val colors = visible.map { ContextCompat.getColor(ctx, colorResForOrientation(it.orientation)) }
        val dataSet = PieDataSet(entries, "").apply {
            this.colors = colors
            sliceSpace = ORIENTATION_SLICE_SPACE_PX
            setDrawValues(false)
        }
        chart.apply {
            data = PieData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            isDrawHoleEnabled = true
            holeRadius = ORIENTATION_HOLE_RADIUS
            transparentCircleRadius = 0f
            setUsePercentValues(false)
            setEntryLabelColor(ctx.getProperTextColor())
            setEntryLabelTextSize(ORIENTATION_LABEL_TEXT_SIZE_SP)
            setHoleColor(android.graphics.Color.TRANSPARENT)
            setCenterTextColor(ctx.getProperTextColor())
            centerText = "${getString(R.string.dashboard_orientation_center_sessions, totalSessions)}\n" +
                getString(
                    R.string.dashboard_orientation_center_minutes,
                    totalDurationMs.toDouble() / MS_PER_MINUTE,
                )
            invalidate()
        }

        val legend = view.dashboardOrientationLegend
        legend.removeAllViews()
        val inflater = LayoutInflater.from(ctx)
        val textColor = ctx.getProperTextColor()
        for (slice in visible) {
            val row = ItemOrientationLegendBinding.inflate(inflater, legend, false)
            row.orientationLegendSwatch.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(ctx, colorResForOrientation(slice.orientation))
            )
            row.orientationLegendLabel.text = labelForOrientation(slice.orientation)
            row.orientationLegendLabel.setTextColor(textColor)
            row.orientationLegendCount.text = slice.sessionCount.toString()
            row.orientationLegendCount.setTextColor(textColor)
            legend.addView(row.root)
        }
    }

    private fun labelForOrientation(orientation: Int): String = when (orientation) {
        Configuration.ORIENTATION_PORTRAIT -> getString(R.string.orientation_portrait)
        Configuration.ORIENTATION_LANDSCAPE -> getString(R.string.orientation_landscape)
        ORIENTATION_NOT_CAPTURED -> getString(R.string.orientation_not_captured)
        else -> getString(R.string.orientation_unknown)
    }

    @ColorRes
    private fun colorResForOrientation(orientation: Int): Int = when (orientation) {
        Configuration.ORIENTATION_PORTRAIT -> R.color.orientation_color_portrait
        Configuration.ORIENTATION_LANDSCAPE -> R.color.orientation_color_landscape
        ORIENTATION_NOT_CAPTURED -> R.color.orientation_color_unknown
        else -> R.color.orientation_color_unknown
    }

    private fun bindOutlierLabel(label: MyTextView, outlierCount: Int) {
        val ctx = context ?: return
        if (outlierCount <= 0) {
            label.beGone()
            return
        }
        label.beVisible()
        label.text = getString(R.string.dashboard_distribution_outliers_label, outlierCount)
        label.setTextColor(ctx.getProperPrimaryColor())
    }

    companion object {
        private const val ORIENTATION_NOT_CAPTURED = -1
        private const val ORIENTATION_HOLE_RADIUS = 60f
        private const val ORIENTATION_LABEL_TEXT_SIZE_SP = 10f
        private const val ORIENTATION_SLICE_SPACE_PX = 2f
        private const val MS_PER_MINUTE = 60_000L
    }
}
