package org.fossify.keyboard.activities.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import com.github.mikephil.charting.charts.ScatterChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.ScatterData
import com.github.mikephil.charting.data.ScatterDataSet
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.views.MyTextView
import org.fossify.keyboard.R
import org.fossify.keyboard.databinding.FragmentDashboardHabitsBinding
import org.fossify.keyboard.helpers.IkdQualityAggregator
import org.fossify.keyboard.views.IkdLineChartView

/**
 * Phase 9.11/9.12: Habits tab. Phase 9.12 dropped the internal Habits
 * KPI strip — all four cells now live on the global KPI strip at the
 * activity level (Avg session and Longest streak were the new additions;
 * Sessions and Typing time were already there). The tab now contains
 * only the four Phase 9.3 trend charts and the Phase 9.10 backspaces-
 * vs-autocorrects scatter.
 *
 * The entire section is hidden when `totalSessions == 0`.
 */
class HabitsFragment : DashboardFragment() {

    private var _binding: FragmentDashboardHabitsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDashboardHabitsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun renderPayload(payload: DashboardPayload) {
        val ctx = context ?: return
        val view = _binding ?: return
        val habits = payload.habits
        val quality = payload.quality

        applyThemeColors()

        val visible = habits.totalSessions > 0
        if (!visible) {
            view.dashboardChartHabitsSessionDurationTitle.beGone()
            view.dashboardChartHabitsSessionDuration.beGone()
            view.dashboardChartHabitsSessionsTitle.beGone()
            view.dashboardChartHabitsSessions.beGone()
            view.dashboardChartHabitsErrorRateTitle.beGone()
            view.dashboardChartHabitsErrorRate.beGone()
            view.dashboardChartHabitsFlightTitle.beGone()
            view.dashboardChartHabitsFlight.beGone()
            view.dashboardActivityQualityCard.beGone()
            view.fragmentHabitsEmptyMessage.beVisible()
            return
        }
        view.fragmentHabitsEmptyMessage.beGone()

        val labels = habits.buckets.map { DashboardLabelFormat.formatBucketLabel(ctx, it.label, habits.range) }
        val durationMinutes = habits.buckets.map { it.avgSessionDurationMs?.toFloat()?.div(MS_PER_MINUTE) }
        val sessionCounts = habits.buckets.map { it.sessionCount.toFloat() }
        val errorPct = habits.buckets.map { it.errorRatePct?.toFloat() }
        val flightMs = habits.buckets.map { it.avgFlightMs?.toFloat() }

        bindHabitsChart(
            title = view.dashboardChartHabitsSessionDurationTitle,
            chart = view.dashboardChartHabitsSessionDuration,
            labels = labels,
            values = durationMinutes,
            yLabel = getString(R.string.dashboard_chart_habits_session_duration_y_label),
        )
        bindHabitsChart(
            title = view.dashboardChartHabitsSessionsTitle,
            chart = view.dashboardChartHabitsSessions,
            labels = labels,
            // sessionCount: zero is meaningful; render as zeros, never null.
            values = sessionCounts.map<Float, Float?> { it },
            yLabel = getString(R.string.dashboard_chart_habits_sessions_y_label),
            showAsZeros = true,
        )
        bindHabitsChart(
            title = view.dashboardChartHabitsErrorRateTitle,
            chart = view.dashboardChartHabitsErrorRate,
            labels = labels,
            values = errorPct,
            yLabel = getString(R.string.dashboard_chart_habits_error_rate_y_label),
        )
        bindHabitsChart(
            title = view.dashboardChartHabitsFlightTitle,
            chart = view.dashboardChartHabitsFlight,
            labels = labels,
            values = flightMs,
            yLabel = getString(R.string.dashboard_chart_habits_flight_y_label),
        )

        bindActivityQualityScatter(quality)
    }

    /**
     * Phase 9.12: theming pass — push the user-selected Fossify theme
     * onto every TextView/MaterialCardView under this fragment. Mirrors
     * the discipline `EventFeedActivity.applyThemeColors()` uses.
     */
    private fun applyThemeColors() {
        val ctx = context ?: return
        val view = _binding ?: return
        val activity = activity ?: return
        activity.updateTextColors(view.root)
        view.dashboardActivityQualityCard.setCardBackgroundColor(ctx.getProperBackgroundColor())
    }

    private fun bindActivityQualityScatter(quality: IkdQualityAggregator.QualitySnapshot) {
        val ctx = context ?: return
        val view = _binding ?: return
        val card = view.dashboardActivityQualityCard
        val chart = view.dashboardActivityQualityChart
        val visible = quality.points.any { it.backspaceCount > 0 || it.autocorrectionCount > 0 }
        card.beVisibleIf(visible)
        if (!visible) return

        val primary = ctx.getProperPrimaryColor()
        val maxIndex = quality.points.size - 1
        val entries = quality.points.map { point ->
            Entry(
                point.backspaceCount.toFloat(),
                point.autocorrectionCount.toFloat(),
                point,
            )
        }
        val colors = quality.points.map { point ->
            val alphaInt = computePointAlpha(point.dayIndex, maxIndex)
            ColorUtils.setAlphaComponent(primary, alphaInt)
        }
        val dataSet = ScatterDataSet(entries, "").apply {
            this.colors = colors
            setScatterShape(ScatterChart.ScatterShape.CIRCLE)
            scatterShapeSize = QUALITY_BASE_SCATTER_SIZE
            setDrawValues(false)
            isHighlightEnabled = false
        }
        chart.data = ScatterData(dataSet)
        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.axisRight.isEnabled = false
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.textColor = ctx.getProperTextColor()
        chart.axisLeft.textColor = ctx.getProperTextColor()
        chart.xAxis.axisMinimum = 0f
        chart.axisLeft.axisMinimum = 0f
        chart.invalidate()

        chart.setOnChartValueSelectedListener(
            object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    val point = e?.data as? IkdQualityAggregator.DayQualityPoint ?: return
                    Toast.makeText(
                        ctx,
                        getString(
                            R.string.dashboard_activity_quality_tooltip,
                            point.day,
                            point.backspaceCount,
                            point.autocorrectionCount,
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                override fun onNothingSelected() = Unit
            }
        )
    }

    private fun computePointAlpha(dayIndex: Int, maxIndex: Int): Int {
        if (dayIndex == 0) return ALPHA_FULL
        if (maxIndex <= 0) return ALPHA_FULL
        val ratio = (dayIndex - 1).toFloat() / maxIndex.toFloat()
        val alphaFloat = FADE_MAX_ALPHA - (FADE_MAX_ALPHA - FADE_MIN_ALPHA) * ratio
        return alphaFloat.toInt().coerceIn(FADE_MIN_ALPHA, ALPHA_FULL)
    }

    private fun bindHabitsChart(
        title: MyTextView,
        chart: IkdLineChartView,
        labels: List<String>,
        values: List<Float?>,
        yLabel: String,
        showAsZeros: Boolean = false,
    ) {
        val hasData = showAsZeros && labels.isNotEmpty() || values.any { it != null }
        title.beVisibleIf(hasData)
        chart.beVisibleIf(hasData)
        if (hasData) {
            chart.setData(labels, values, yLabel)
        }
    }

    companion object {
        private const val MS_PER_MINUTE = 60_000L
        private const val ALPHA_FULL = 255
        private const val FADE_MAX_ALPHA = 204
        private const val FADE_MIN_ALPHA = 51
        private const val QUALITY_BASE_SCATTER_SIZE = 14f
    }
}
