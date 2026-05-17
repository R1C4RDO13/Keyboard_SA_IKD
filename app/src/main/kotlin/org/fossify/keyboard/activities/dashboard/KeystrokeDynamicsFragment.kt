package org.fossify.keyboard.activities.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.views.MyTextView
import org.fossify.keyboard.R
import org.fossify.keyboard.databinding.FragmentDashboardKeystrokeDynamicsBinding
import org.fossify.keyboard.helpers.IkdDistributionAggregator
import org.fossify.keyboard.helpers.WidgetInfo
import org.fossify.keyboard.helpers.attachWidgetInfo

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
        attachWidgetInfoButtons()
        return binding.root
    }

    /** Phase 9.13: bind tap-to-explain dialogs to each card's info icon. */
    private fun attachWidgetInfoButtons() {
        binding.dashboardIkdDistributionInfo.attachWidgetInfo(
            WidgetInfo(
                titleRes = R.string.info_kd_ikd_distribution_title,
                descriptionRes = R.string.info_kd_ikd_distribution_desc,
                interpretationRes = R.string.info_kd_ikd_distribution_interpretation,
                formulaRes = R.string.info_kd_ikd_distribution_formula,
            ),
        )
        binding.dashboardDwellDistributionInfo.attachWidgetInfo(
            WidgetInfo(
                titleRes = R.string.info_kd_dwell_distribution_title,
                descriptionRes = R.string.info_kd_dwell_distribution_desc,
                interpretationRes = R.string.info_kd_dwell_distribution_interpretation,
                formulaRes = R.string.info_kd_dwell_distribution_formula,
            ),
        )
        binding.dashboardFlightDistributionInfo.attachWidgetInfo(
            WidgetInfo(
                titleRes = R.string.info_kd_flight_distribution_title,
                descriptionRes = R.string.info_kd_flight_distribution_desc,
                interpretationRes = R.string.info_kd_flight_distribution_interpretation,
                formulaRes = R.string.info_kd_flight_distribution_formula,
            ),
        )
        // Phase 15: Avg flight time relocated from the dropped Habits tab.
        // Info copy reuses the existing info_habits_flight_* keys
        // (plan §3 — keep it simple, reuse keys).
        binding.dashboardChartHabitsFlightInfo.attachWidgetInfo(
            WidgetInfo(
                titleRes = R.string.info_habits_flight_title,
                descriptionRes = R.string.info_habits_flight_desc,
                interpretationRes = R.string.info_habits_flight_interpretation,
                formulaRes = R.string.info_habits_flight_formula,
            ),
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun renderPayload(payload: DashboardPayload) {
        val view = _binding ?: return
        val distribution = payload.distribution

        applyCardThemeColors()

        val labels = IkdDistributionAggregator.bucketLabels()
        val ikdHasData = distribution.ikdHistogram.buckets.any { it > 0 }
        val dwellHasData = distribution.holdHistogram.buckets.any { it > 0 }
        val flightHasData = distribution.flightHistogram.buckets.any { it > 0 }

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

        // Phase 15: Avg flight time, relocated from the dropped Habits
        // tab. Reuses the IkdHabitsAggregator output already carried in
        // the payload — no aggregator/DAO change. Card hidden when no
        // bucket carries a flight average.
        val ctx = context ?: return
        val habits = payload.habits
        val habitsLabels = habits.buckets.map {
            DashboardLabelFormat.formatBucketLabel(ctx, it.label, habits.range)
        }
        val flightMs = habits.buckets.map { it.avgFlightMs?.toFloat() }
        val flightHabitsHasData = flightMs.any { it != null }
        view.dashboardChartHabitsFlightCard.beVisibleIf(flightHabitsHasData)
        if (flightHabitsHasData) {
            view.dashboardChartHabitsFlight.setData(
                habitsLabels,
                flightMs,
                getString(R.string.dashboard_chart_habits_flight_y_label),
            )
        }

        val anyHasData = ikdHasData || dwellHasData || flightHasData ||
            flightHabitsHasData
        if (anyHasData) {
            view.fragmentKeystrokeDynamicsEmptyMessage.beGone()
        } else {
            view.fragmentKeystrokeDynamicsEmptyMessage.beVisible()
        }
    }

    private fun applyCardThemeColors() {
        val ctx = context ?: return
        val view = _binding ?: return
        // Phase 9.12: re-theme histograms titles, legend rows, donut
        // centre text and outlier labels.
        activity?.updateTextColors(view.root)
        val bg = ctx.getProperBackgroundColor()
        view.dashboardIkdDistributionCard.setCardBackgroundColor(bg)
        view.dashboardDwellDistributionCard.setCardBackgroundColor(bg)
        view.dashboardFlightDistributionCard.setCardBackgroundColor(bg)
        view.dashboardChartHabitsFlightCard.setCardBackgroundColor(bg)
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
}
