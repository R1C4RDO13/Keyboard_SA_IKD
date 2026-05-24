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
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.keyboard.R
import org.fossify.keyboard.databinding.FragmentDashboardTrendsBinding
import org.fossify.keyboard.databinding.ItemOrientationLegendBinding
import org.fossify.keyboard.helpers.IkdOrientationAggregator
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
        // Avg flight time relocated here from the Keystroke Dynamics tab
        // (owner directive). Info copy reuses the existing
        // info_habits_flight_* keys.
        binding.dashboardChartHabitsFlightInfo.attachWidgetInfo(
            WidgetInfo(
                titleRes = R.string.info_habits_flight_title,
                descriptionRes = R.string.info_habits_flight_desc,
                interpretationRes = R.string.info_habits_flight_interpretation,
                formulaRes = R.string.info_habits_flight_formula,
            ),
        )
        // Phase 9.8 orientation donut relocated here from the Keystroke
        // Dynamics tab (owner directive). Info copy reuses the existing
        // info_kd_orientation_* keys.
        binding.dashboardOrientationInfo.attachWidgetInfo(
            WidgetInfo(
                titleRes = R.string.info_kd_orientation_title,
                descriptionRes = R.string.info_kd_orientation_desc,
                interpretationRes = R.string.info_kd_orientation_interpretation,
                formulaRes = R.string.info_kd_orientation_formula,
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

        // Phase 15 polish: every chart now sits in its own
        // MaterialCardView (matching the other tabs). Tint every card
        // with the runtime Fossify background so they don't inherit
        // Material's default ?attr/colorSurface.
        val cardBg = ctx.getProperBackgroundColor()
        view.dashboardChartSpeedCard.setCardBackgroundColor(cardBg)
        view.dashboardChartIkdCard.setCardBackgroundColor(cardBg)
        view.dashboardChartErrorCard.setCardBackgroundColor(cardBg)
        view.dashboardChartGyroCard.setCardBackgroundColor(cardBg)
        view.dashboardChartAccelCard.setCardBackgroundColor(cardBg)
        view.dashboardChartHabitsSessionDurationCard.setCardBackgroundColor(cardBg)
        view.dashboardChartHabitsFlightCard.setCardBackgroundColor(cardBg)

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

        view.dashboardChartGyroCard.beVisibleIf(hasGyro)
        if (hasGyro) {
            view.dashboardChartGyro.setData(
                sensorLabels,
                gyroValues,
                getString(R.string.dashboard_chart_gyro_y_label),
            )
        }

        view.dashboardChartAccelCard.beVisibleIf(hasAccel)
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
        view.dashboardChartHabitsSessionDurationCard.beVisibleIf(hasDuration)
        if (hasDuration) {
            view.dashboardChartHabitsSessionDuration.setData(
                habitsLabels,
                durationMinutes,
                getString(R.string.dashboard_chart_habits_session_duration_y_label),
            )
        }

        // Avg flight time, relocated from the Keystroke Dynamics tab.
        // Reuses the IkdHabitsAggregator output already in the payload —
        // no aggregator/DAO change. Card hidden when no bucket carries a
        // flight average.
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

        // Trends tab empty placeholder: only when *all* of speed / ikd /
        // error / gyro / accel have no data. Speed / IKD / error always
        // render labels even if values are null (line breaks); the
        // empty placeholder fires only when the snapshot has no buckets
        // at all, which already implies the global empty state on the
        // host activity is taking over.
        // Phase 9.8: Orientation breakdown donut, relocated here from
        // the Keystroke Dynamics tab (owner directive). Backed by the
        // same IkdOrientationAggregator output already carried in the
        // payload — no aggregator/DAO change.
        val orientation = payload.orientation
        val orientationHasData = orientation.slices.any { it.sessionCount > 0 }
        view.dashboardOrientationCard.setCardBackgroundColor(ctx.getProperBackgroundColor())
        view.dashboardOrientationCard.beVisibleIf(orientationHasData)
        if (orientationHasData) {
            bindOrientationDonut(orientation.slices)
        }

        val anyData = snap.buckets.isNotEmpty()
        view.fragmentTrendsEmptyMessage.beVisibleIf(!anyData)
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

    companion object {
        private const val MS_PER_MINUTE = 60_000L
        private const val ORIENTATION_NOT_CAPTURED = -1
        private const val ORIENTATION_HOLE_RADIUS = 60f
        private const val ORIENTATION_LABEL_TEXT_SIZE_SP = 10f
        private const val ORIENTATION_SLICE_SPACE_PX = 2f
    }
}
