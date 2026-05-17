package org.fossify.keyboard.activities.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.updateTextColors
import org.fossify.keyboard.R
import org.fossify.keyboard.databinding.FragmentDashboardTrendsBinding
import org.fossify.keyboard.helpers.WidgetInfo
import org.fossify.keyboard.helpers.attachWidgetInfo

/**
 * Phase 9.11: Trends tab. Three IKD line charts (Speed / IKD / Error
 * rate) plus the Phase 9.2 gyro / accel global trend charts. Each chart
 * card hides itself + its title when its data is empty; the tab renders
 * an empty placeholder when *every* chart is empty.
 */
class TrendsFragment : DashboardFragment() {

    private var _binding: FragmentDashboardTrendsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDashboardTrendsBinding.inflate(inflater, container, false)
        attachWidgetInfoButtons()
        return binding.root
    }

    /**
     * Phase 9.13: bind tap-to-explain dialogs to each chart's info icon.
     * Each [WidgetInfo] points at copy in `strings_widget_info.xml` that
     * mirrors the actual aggregator + DAO formula.
     */
    private fun attachWidgetInfoButtons() {
        binding.dashboardChartSpeedInfo.attachWidgetInfo(
            WidgetInfo(
                titleRes = R.string.info_trends_speed_title,
                descriptionRes = R.string.info_trends_speed_desc,
                interpretationRes = R.string.info_trends_speed_interpretation,
                formulaRes = R.string.info_trends_speed_formula,
            ),
        )
        binding.dashboardChartIkdInfo.attachWidgetInfo(
            WidgetInfo(
                titleRes = R.string.info_trends_ikd_title,
                descriptionRes = R.string.info_trends_ikd_desc,
                interpretationRes = R.string.info_trends_ikd_interpretation,
                formulaRes = R.string.info_trends_ikd_formula,
            ),
        )
        binding.dashboardChartErrorInfo.attachWidgetInfo(
            WidgetInfo(
                titleRes = R.string.info_trends_error_rate_title,
                descriptionRes = R.string.info_trends_error_rate_desc,
                interpretationRes = R.string.info_trends_error_rate_interpretation,
                formulaRes = R.string.info_trends_error_rate_formula,
            ),
        )
        binding.dashboardChartGyroInfo.attachWidgetInfo(
            WidgetInfo(
                titleRes = R.string.info_trends_gyro_title,
                descriptionRes = R.string.info_trends_gyro_desc,
                interpretationRes = R.string.info_trends_gyro_interpretation,
                formulaRes = R.string.info_trends_gyro_formula,
            ),
        )
        binding.dashboardChartAccelInfo.attachWidgetInfo(
            WidgetInfo(
                titleRes = R.string.info_trends_accel_title,
                descriptionRes = R.string.info_trends_accel_desc,
                interpretationRes = R.string.info_trends_accel_interpretation,
                formulaRes = R.string.info_trends_accel_formula,
            ),
        )
        // Phase 15: Avg session duration relocated from the dropped Habits
        // tab. Info copy reuses the existing info_habits_session_duration_*
        // keys (plan §3 — keep it simple, reuse keys).
        binding.dashboardChartHabitsSessionDurationInfo.attachWidgetInfo(
            WidgetInfo(
                titleRes = R.string.info_habits_session_duration_title,
                descriptionRes = R.string.info_habits_session_duration_desc,
                interpretationRes = R.string.info_habits_session_duration_interpretation,
                formulaRes = R.string.info_habits_session_duration_formula,
            ),
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun renderPayload(payload: DashboardPayload) {
        val ctx = context ?: return
        val view = _binding ?: return

        // Phase 9.12: re-theme on every render so chart titles and the
        // empty placeholder pick up the user's Fossify theme. The
        // IkdLineChartView already pulls its own axis colours from the
        // theme on construction.
        activity?.updateTextColors(view.root)

        val snap = payload.ikd
        val sensor = payload.sensor

        val labels = snap.buckets.map { DashboardLabelFormat.formatBucketLabel(ctx, it.label, snap.range) }
        val wpmValues = snap.buckets.map { it.wpm?.toFloat() }
        val ikdValues = snap.buckets.map { it.avgIkdMs?.toFloat() }
        val errorValues = snap.buckets.map { it.errorRatePct?.toFloat() }

        view.dashboardChartSpeed.setData(labels, wpmValues, getString(R.string.dashboard_chart_speed))
        view.dashboardChartIkd.setData(labels, ikdValues, getString(R.string.dashboard_chart_ikd))
        view.dashboardChartError.setData(labels, errorValues, getString(R.string.dashboard_chart_error))

        // Phase 9.2: gyro / accel global trend charts. Hidden along with
        // their titles when no buckets carry that sensor (e.g., user
        // disabled gyro or all sessions in range are sensor-free).
        val sensorLabels = sensor.buckets.map {
            DashboardLabelFormat.formatBucketLabel(ctx, it.label, sensor.range)
        }
        val gyroValues = sensor.buckets.map { it.gyroMag?.toFloat() }
        val accelValues = sensor.buckets.map { it.accelMag?.toFloat() }

        val hasGyro = gyroValues.any { it != null }
        val hasAccel = accelValues.any { it != null }

        view.dashboardChartGyroTitleRow.beVisibleIf(hasGyro)
        view.dashboardChartGyro.beVisibleIf(hasGyro)
        if (hasGyro) {
            view.dashboardChartGyro.setData(
                sensorLabels,
                gyroValues,
                getString(R.string.dashboard_chart_gyro_y_label),
            )
        }

        view.dashboardChartAccelTitleRow.beVisibleIf(hasAccel)
        view.dashboardChartAccel.beVisibleIf(hasAccel)
        if (hasAccel) {
            view.dashboardChartAccel.setData(
                sensorLabels,
                accelValues,
                getString(R.string.dashboard_chart_accel_y_label),
            )
        }

        // Phase 15: Avg session duration, relocated from the dropped
        // Habits tab. Reuses the IkdHabitsAggregator output already in
        // the payload — no aggregator/DAO change. Hidden (with its title
        // row) when no bucket carries a duration, mirroring the original
        // Habits-tab behaviour.
        val habits = payload.habits
        val habitsLabels = habits.buckets.map {
            DashboardLabelFormat.formatBucketLabel(ctx, it.label, habits.range)
        }
        val durationMinutes = habits.buckets.map {
            it.avgSessionDurationMs?.toFloat()?.div(MS_PER_MINUTE)
        }
        val hasDuration = durationMinutes.any { it != null }
        view.dashboardChartHabitsSessionDurationTitleRow.beVisibleIf(hasDuration)
        view.dashboardChartHabitsSessionDuration.beVisibleIf(hasDuration)
        if (hasDuration) {
            view.dashboardChartHabitsSessionDuration.setData(
                habitsLabels,
                durationMinutes,
                getString(R.string.dashboard_chart_habits_session_duration_y_label),
            )
        }

        // Trends tab empty placeholder: only when *all* of speed / ikd /
        // error / gyro / accel have no data. Speed / IKD / error always
        // render labels even if values are null (line breaks); the
        // empty placeholder fires only when the snapshot has no buckets
        // at all, which already implies the global empty state on the
        // host activity is taking over.
        val anyData = snap.buckets.isNotEmpty()
        view.fragmentTrendsEmptyMessage.beVisibleIf(!anyData)
    }

    companion object {
        private const val MS_PER_MINUTE = 60_000L
    }
}
