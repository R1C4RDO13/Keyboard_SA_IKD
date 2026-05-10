package org.fossify.keyboard.activities.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.keyboard.R
import org.fossify.keyboard.databinding.FragmentDashboardDailyActivityBinding
import org.fossify.keyboard.helpers.IkdActivityAggregator
import org.fossify.keyboard.interfaces.HourWeekdayRow
import org.fossify.keyboard.views.IkdBubbleMapView
import org.fossify.keyboard.views.IkdHeatmapView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Phase 9.11: Daily Activity tab. Calendar heatmap (9.5), daily keypress
 * bar (9.5), 24-hour bar (9.6), circadian heatmap (9.6) and the Usage
 * Map bubble chart (9.9). Each widget hides itself when its data is
 * empty; the tab renders an empty placeholder when *every* widget is
 * empty for the current range.
 *
 * TODAY range hides the calendar heatmap, daily keypress bar, and Usage
 * Map (all of which would degenerate to single-cell views) — that
 * decision is made on the host activity via `range.isHourly()` before
 * the buckets land in the snapshot.
 */
class DailyActivityFragment : DashboardFragment() {

    private var _binding: FragmentDashboardDailyActivityBinding? = null
    private val binding get() = _binding!!

    private val isoDayParser = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDashboardDailyActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun renderPayload(payload: DashboardPayload) {
        val ctx = context ?: return
        val view = _binding ?: return
        val activity = payload.activity

        // Phase 9.11: under the hourly TODAY range the calendar-heatmap,
        // daily-keypress bar and Usage Map (date-keyed) all degenerate
        // — hide them. Hourly + circadian (always all-time) still apply.
        val isHourly = activity.range.isHourly()
        val hasDaily = !isHourly && activity.dailyBuckets.isNotEmpty()
        val hasHourly = activity.hourlyBuckets.isNotEmpty()
        val hasCircadian = activity.circadianCells.isNotEmpty()
        val hasDayHour = !isHourly && activity.dayHourCells.isNotEmpty()

        applyCardThemeColors()

        view.dashboardCalendarHeatmapCard.beVisibleIf(hasDaily)
        view.dashboardDailyKeypressCard.beVisibleIf(hasDaily)
        if (hasDaily) {
            bindCalendarHeatmap(activity.dailyBuckets)
            bindDailyKeypressBar(activity.dailyBuckets)
        }

        view.dashboardHourlyCard.beVisibleIf(hasHourly)
        if (hasHourly) {
            bindHourlyBar(activity.hourlyBuckets)
        }

        view.dashboardCircadianCard.beVisibleIf(hasCircadian)
        if (hasCircadian) {
            bindCircadianHeatmap(activity.circadianCells)
        }

        view.dashboardUsageMapCard.beVisibleIf(hasDayHour)
        if (hasDayHour) {
            bindUsageMap(activity.dayHourCells)
        }

        val anyVisible = hasDaily || hasHourly || hasCircadian || hasDayHour
        view.fragmentDailyActivityEmptyMessage.beVisibleIf(!anyVisible)
    }

    private fun applyCardThemeColors() {
        val ctx = context ?: return
        val bg = ctx.getProperBackgroundColor()
        val view = _binding ?: return
        // Phase 9.12: re-theme card titles + DOW labels under each heatmap.
        activity?.updateTextColors(view.root)
        view.dashboardCalendarHeatmapCard.setCardBackgroundColor(bg)
        view.dashboardDailyKeypressCard.setCardBackgroundColor(bg)
        view.dashboardHourlyCard.setCardBackgroundColor(bg)
        view.dashboardCircadianCard.setCardBackgroundColor(bg)
        view.dashboardUsageMapCard.setCardBackgroundColor(bg)
    }

    private fun bindUsageMap(cells: List<IkdActivityAggregator.DayHourCell>) {
        val view = _binding ?: return
        val ctx = context ?: return
        val bubbles = cells.map {
            IkdBubbleMapView.Bubble(day = it.day, hour = it.hour, count = it.keystrokeCount)
        }
        view.dashboardUsageMap.setData(bubbles)
        view.dashboardUsageMap.setOnBubbleClickListener { bubble ->
            Toast.makeText(
                ctx,
                getString(
                    R.string.dashboard_usage_map_tooltip_format,
                    bubble.day,
                    bubble.hour,
                    bubble.count,
                ),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun bindHourlyBar(hourly: List<IkdActivityAggregator.HourlyBucket>) {
        val view = _binding ?: return
        val byHour = hourly.associateBy { it.hour }
        val labels = (0 until HOURS_PER_DAY).map { hourLabel(it) }
        val values = (0 until HOURS_PER_DAY).map { (byHour[it]?.keystrokeCount ?: 0).toFloat() }
        view.dashboardHourlyChart.setData(
            labels,
            values,
            getString(R.string.dashboard_chart_hourly_y_label),
        )
    }

    private fun bindCircadianHeatmap(rows: List<HourWeekdayRow>) {
        val view = _binding ?: return
        val ctx = context ?: return
        if (rows.isEmpty()) return
        val maxCount = rows.maxOf { it.keystrokeCount }
        val cells = rows.map { row ->
            // SQLite %w: 0=Sun..6=Sat. Map to Mon=0..Sun=6.
            val rowIdx = (row.dow + DOW_SQL_TO_MON_OFFSET) % DOW_COUNT
            val weekdayLabel = weekdayLabelForRow(rowIdx)
            IkdHeatmapView.Cell(
                column = row.hour,
                row = rowIdx,
                count = row.keystrokeCount,
                label = "$weekdayLabel ${row.hour}",
            )
        }
        view.dashboardCircadianHeatmap.setData(
            cells,
            columns = HOURS_PER_DAY,
            rows = DOW_COUNT,
            maxIntensity = maxCount,
        )
        val xLabels = (0 until HOURS_PER_DAY).map { hourLabel(it) }
        val yLabels = listOf(
            getString(R.string.dashboard_dow_mon),
            getString(R.string.dashboard_dow_tue),
            getString(R.string.dashboard_dow_wed),
            getString(R.string.dashboard_dow_thu),
            getString(R.string.dashboard_dow_fri),
            getString(R.string.dashboard_dow_sat),
            getString(R.string.dashboard_dow_sun),
        )
        view.dashboardCircadianHeatmap.setAxisLabels(x = xLabels, y = yLabels)
        view.dashboardCircadianHeatmap.setOnCellClickListener { cell ->
            if (cell.count > 0) {
                val weekdayLabel = weekdayLabelForRow(cell.row)
                Toast.makeText(
                    ctx,
                    getString(
                        R.string.dashboard_circadian_cell_toast,
                        weekdayLabel,
                        cell.column,
                        cell.count,
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private fun bindCalendarHeatmap(daily: List<IkdActivityAggregator.DailyBucket>) {
        val view = _binding ?: return
        val ctx = context ?: return
        val parsed = daily.mapNotNull { bucket ->
            runCatching {
                val date = isoDayParser.parse(bucket.day) ?: return@mapNotNull null
                val cal = Calendar.getInstance()
                cal.time = date
                Triple(bucket.day, cal, bucket.keystrokeCount)
            }.getOrNull()
        }
        if (parsed.isEmpty()) {
            view.dashboardCalendarHeatmapCard.beGone()
            return
        }
        val firstWeek = parsed.minOf { it.second.get(Calendar.WEEK_OF_YEAR) }
        val lastWeek = parsed.maxOf { it.second.get(Calendar.WEEK_OF_YEAR) }
        val rows = (lastWeek - firstWeek + 1).coerceIn(1, MAX_HEATMAP_ROWS)
        val maxCount = daily.maxOf { it.keystrokeCount }

        val cells = parsed.map { (day, cal, count) ->
            val rowIdx = (cal.get(Calendar.WEEK_OF_YEAR) - firstWeek).coerceIn(0, rows - 1)
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            val col = (dow + DOW_MON_OFFSET) % DOW_COUNT
            IkdHeatmapView.Cell(column = col, row = rowIdx, count = count, label = day)
        }
        view.dashboardCalendarHeatmap.setData(cells, columns = DOW_COUNT, rows = rows, maxIntensity = maxCount)
        view.dashboardCalendarHeatmap.setAxisLabels(
            x = listOf(
                getString(R.string.dashboard_dow_mon),
                getString(R.string.dashboard_dow_tue),
                getString(R.string.dashboard_dow_wed),
                getString(R.string.dashboard_dow_thu),
                getString(R.string.dashboard_dow_fri),
                getString(R.string.dashboard_dow_sat),
                getString(R.string.dashboard_dow_sun),
            ),
            y = emptyList(),
        )
        view.dashboardCalendarHeatmap.setOnCellClickListener { cell ->
            if (cell.label.isNotEmpty()) {
                Toast.makeText(
                    ctx,
                    getString(R.string.dashboard_calendar_cell_toast, cell.label, cell.count),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private fun bindDailyKeypressBar(daily: List<IkdActivityAggregator.DailyBucket>) {
        val view = _binding ?: return
        val labels = daily.map { it.day.substring(it.day.lastIndexOf('-') + 1) }
        val values = daily.map { it.keystrokeCount.toFloat() }
        view.dashboardDailyKeypressChart.setData(
            labels,
            values,
            getString(R.string.dashboard_chart_daily_keypress_y_label),
        )
    }

    private fun hourLabel(hour: Int): String = when (hour) {
        HOUR_LABEL_0 -> getString(R.string.dashboard_hour_label_00)
        HOUR_LABEL_6 -> getString(R.string.dashboard_hour_label_06)
        HOUR_LABEL_12 -> getString(R.string.dashboard_hour_label_12)
        HOUR_LABEL_18 -> getString(R.string.dashboard_hour_label_18)
        else -> ""
    }

    private fun weekdayLabelForRow(row: Int): String = when (row) {
        DOW_ROW_MON -> getString(R.string.dashboard_dow_mon)
        DOW_ROW_TUE -> getString(R.string.dashboard_dow_tue)
        DOW_ROW_WED -> getString(R.string.dashboard_dow_wed)
        DOW_ROW_THU -> getString(R.string.dashboard_dow_thu)
        DOW_ROW_FRI -> getString(R.string.dashboard_dow_fri)
        DOW_ROW_SAT -> getString(R.string.dashboard_dow_sat)
        DOW_ROW_SUN -> getString(R.string.dashboard_dow_sun)
        else -> ""
    }

    companion object {
        private const val DOW_COUNT = 7
        private const val DOW_MON_OFFSET = 5
        private const val MAX_HEATMAP_ROWS = 53
        private const val HOURS_PER_DAY = 24
        private const val HOUR_LABEL_0 = 0
        private const val HOUR_LABEL_6 = 6
        private const val HOUR_LABEL_12 = 12
        private const val HOUR_LABEL_18 = 18
        private const val DOW_SQL_TO_MON_OFFSET = 6
        private const val DOW_ROW_MON = 0
        private const val DOW_ROW_TUE = 1
        private const val DOW_ROW_WED = 2
        private const val DOW_ROW_THU = 3
        private const val DOW_ROW_FRI = 4
        private const val DOW_ROW_SAT = 5
        private const val DOW_ROW_SUN = 6
    }
}
