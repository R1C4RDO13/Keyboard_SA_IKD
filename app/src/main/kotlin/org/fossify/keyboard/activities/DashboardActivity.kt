package org.fossify.keyboard.activities

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.keyboard.R
import org.fossify.keyboard.databinding.ActivityDashboardBinding
import org.fossify.keyboard.databinding.ItemMoodDistributionRowBinding
import org.fossify.keyboard.databinding.ItemMoodLegendSwatchBinding
import org.fossify.keyboard.extensions.ikdActivityAggregator
import org.fossify.keyboard.extensions.ikdAggregator
import org.fossify.keyboard.extensions.ikdHabitsAggregator
import org.fossify.keyboard.extensions.ikdMoodAggregator
import org.fossify.keyboard.extensions.ikdSensorAggregator
import org.fossify.keyboard.helpers.IkdActivityAggregator
import org.fossify.keyboard.helpers.IkdAggregator
import org.fossify.keyboard.helpers.IkdHabitsAggregator
import org.fossify.keyboard.helpers.IkdMoodAggregator
import org.fossify.keyboard.helpers.IkdSensorAggregator
import org.fossify.keyboard.helpers.MoodEmoji
import org.fossify.keyboard.views.IkdHeatmapView
import android.widget.Toast
import org.fossify.keyboard.views.IkdStackedBarChartView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DashboardActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityDashboardBinding::inflate)
    private var currentRange: IkdAggregator.Range = IkdAggregator.Range.WEEK
    private val isoDayParser = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        currentRange = savedInstanceState?.getString(STATE_RANGE)
            ?.let { runCatching { IkdAggregator.Range.valueOf(it) }.getOrNull() }
            ?: IkdAggregator.Range.WEEK

        binding.apply {
            setupEdgeToEdge(padBottomSystem = listOf(dashboardNestedScrollview))
            setupMaterialScrollListener(dashboardNestedScrollview, dashboardAppbar)
        }

        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.dashboardAppbar, NavigationIcon.Arrow)
        binding.apply {
            updateTextColors(dashboardNestedScrollview)
        }
        // The empty-state message sits as a sibling of the scrollview under the
        // CoordinatorLayout, so updateTextColors above does not reach it.
        binding.dashboardEmptyMessage.setTextColor(getProperTextColor())
        applyRangeToggleColors()
        applyCardThemeColors()
        loadSnapshot()
    }

    /**
     * MaterialCardView's default `?attr/colorSurface` does not track Fossify's
     * runtime background color, so on a custom theme the Phase 8 mood cards
     * render as unthemed white slabs. Tint them to the activity background
     * and let `cardElevation`'s shadow demarcate the card silhouette — same
     * pattern as `EventFeedActivity.applyThemeColors`.
     *
     * Phase 8.3: tints the new stacked-bar card alongside the Distribution
     * card. The Phase 8 line-chart card is gone.
     *
     * Phase 9.1: also tints the new section headers with the primary color
     * so the dashboard reads as labelled sections.
     */
    private fun applyCardThemeColors() {
        val background = getProperBackgroundColor()
        binding.dashboardMoodStackedChartCard.setCardBackgroundColor(background)
        binding.dashboardMoodDistributionCard.setCardBackgroundColor(background)

        val primary = getProperPrimaryColor()
        binding.dashboardSectionHeaderTrends.setTextColor(primary)
        binding.dashboardSectionHeaderMood.setTextColor(primary)
        binding.dashboardSectionHeaderHabits.setTextColor(primary)
        binding.dashboardSectionHeaderDailyActivity.setTextColor(primary)
        // Phase 9.5: tint the activity-section card backgrounds too.
        val backgroundColor = getProperBackgroundColor()
        binding.dashboardCalendarHeatmapCard.setCardBackgroundColor(backgroundColor)
        binding.dashboardDailyKeypressCard.setCardBackgroundColor(backgroundColor)
    }

    private fun applyRangeToggleColors() {
        val primary = getProperPrimaryColor()
        val onPrimary = primary.getContrastColor()
        val checkedState = intArrayOf(android.R.attr.state_checked)
        val uncheckedState = intArrayOf(-android.R.attr.state_checked)
        val states = arrayOf(checkedState, uncheckedState)

        val textColors = ColorStateList(states, intArrayOf(onPrimary, primary))
        val bgColors = ColorStateList(states, intArrayOf(primary, Color.TRANSPARENT))
        val strokeColors = ColorStateList(states, intArrayOf(primary, primary))

        listOf(
            binding.dashboardRangeWeek,
            binding.dashboardRangeMonth,
            binding.dashboardRangeAll,
        ).forEach { button: MaterialButton ->
            button.setTextColor(textColors)
            button.backgroundTintList = bgColors
            button.strokeColor = strokeColors
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_RANGE, currentRange.name)
    }

    private fun setupListeners() {
        binding.dashboardRangeGroup.check(rangeButtonId(currentRange))
        binding.dashboardRangeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val newRange = idToRange(checkedId)
            if (newRange != currentRange) {
                currentRange = newRange
                loadSnapshot()
            }
        }
        binding.dashboardToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.dashboard_refresh -> {
                    loadSnapshot()
                    true
                }
                else -> false
            }
        }
        binding.dashboardEmptyMessage.setOnClickListener {
            startActivity(Intent(this, IkdSettingsActivity::class.java))
        }
    }

    private fun loadSnapshot() {
        lifecycleScope.launch {
            // Phase 8: fold the mood snapshot into the same Dispatchers.IO
            // hop that already serves the IKD snapshot, so onResume runs
            // a single round-trip per range.
            // Phase 8.3: also fold in the new per-bucket-per-category mix
            // snapshot for the stacked-bar chart.
            // Phase 9.2: add the sensor magnitude aggregator on the same hop.
            // Phase 9.3: add the habits aggregator on the same hop.
            // Phase 9.5: add the activity aggregator on the same hop.
            val agg = ikdAggregator
            val moodAgg = ikdMoodAggregator
            val sensorAgg = ikdSensorAggregator
            val habitsAgg = ikdHabitsAggregator
            val activityAgg = ikdActivityAggregator
            val range = currentRange
            val payload = withContext(Dispatchers.IO) {
                DashboardPayload(
                    ikd = agg.snapshot(range),
                    mood = moodAgg.snapshot(range),
                    moodMix = moodAgg.mixSnapshot(range),
                    sensor = sensorAgg.snapshot(range),
                    habits = habitsAgg.snapshot(range),
                    activity = activityAgg.snapshot(range),
                )
            }
            render(
                payload.ikd,
                payload.mood,
                payload.moodMix,
                payload.sensor,
                payload.habits,
                payload.activity,
            )
        }
    }

    private data class DashboardPayload(
        val ikd: IkdAggregator.Snapshot,
        val mood: IkdMoodAggregator.MoodSnapshot,
        val moodMix: IkdMoodAggregator.MoodMixSnapshot,
        val sensor: IkdSensorAggregator.Snapshot,
        val habits: IkdHabitsAggregator.HabitsSnapshot,
        val activity: IkdActivityAggregator.ActivitySnapshot,
    )

    private fun render(
        snap: IkdAggregator.Snapshot,
        moodSnap: IkdMoodAggregator.MoodSnapshot,
        moodMix: IkdMoodAggregator.MoodMixSnapshot,
        sensor: IkdSensorAggregator.Snapshot,
        habits: IkdHabitsAggregator.HabitsSnapshot,
        activity: IkdActivityAggregator.ActivitySnapshot,
    ) {
        val isEmpty = snap.totalSessions == 0
        binding.dashboardEmptyMessage.beVisibleIf(isEmpty)
        binding.dashboardNestedScrollview.beVisibleIf(!isEmpty)

        if (isEmpty) return

        val placeholder = getString(R.string.dashboard_value_placeholder)
        val locale = Locale.getDefault()
        val minutes = snap.totalTypingTimeMs.toDouble() / MS_PER_MINUTE

        binding.dashboardKpiSessionsValue.text = snap.totalSessions.toString()
        binding.dashboardKpiTypingTimeValue.text = if (snap.totalTypingTimeMs <= 0L) placeholder
        else getString(R.string.dashboard_kpi_typing_time_value, String.format(locale, "%.1f", minutes))
        binding.dashboardKpiWpmValue.text = snap.avgWpm
            ?.let { getString(R.string.dashboard_kpi_wpm_value, it) } ?: placeholder
        binding.dashboardKpiErrorRateValue.text = snap.avgErrorRatePct
            ?.let { getString(R.string.dashboard_kpi_error_rate_value, it) } ?: placeholder

        renderCharts(snap)
        renderSensorTrendCharts(sensor)
        renderDailyActivitySection(activity)
        renderMoodSection(snap, moodSnap, moodMix)
        renderHabitsSection(habits)
    }

    private fun renderCharts(snap: IkdAggregator.Snapshot) {
        val labels = snap.buckets.map { formatBucketLabel(it.label, snap.range) }
        val wpmValues = snap.buckets.map { it.wpm?.toFloat() }
        val ikdValues = snap.buckets.map { it.avgIkdMs?.toFloat() }
        val errorValues = snap.buckets.map { it.errorRatePct?.toFloat() }

        binding.dashboardChartSpeed.setData(labels, wpmValues, getString(R.string.dashboard_chart_speed))
        binding.dashboardChartIkd.setData(labels, ikdValues, getString(R.string.dashboard_chart_ikd))
        binding.dashboardChartError.setData(labels, errorValues, getString(R.string.dashboard_chart_error))
    }

    /**
     * Phase 9.2: bind the gyro and accel global trend charts. Each chart
     * is hidden along with its title when no buckets carry that sensor
     * (e.g., user disabled gyro or all sessions in range are sensor-free).
     * Empty buckets render as line breaks via `IkdLineChartView`'s null
     * convention from Phase 5.
     */
    private fun renderSensorTrendCharts(sensor: IkdSensorAggregator.Snapshot) {
        val labels = sensor.buckets.map { formatBucketLabel(it.label, sensor.range) }
        val gyroValues = sensor.buckets.map { it.gyroMag?.toFloat() }
        val accelValues = sensor.buckets.map { it.accelMag?.toFloat() }

        val hasGyro = gyroValues.any { it != null }
        val hasAccel = accelValues.any { it != null }

        binding.dashboardChartGyroTitle.beVisibleIf(hasGyro)
        binding.dashboardChartGyro.beVisibleIf(hasGyro)
        if (hasGyro) {
            binding.dashboardChartGyro.setData(
                labels,
                gyroValues,
                getString(R.string.dashboard_chart_gyro_y_label),
            )
        }

        binding.dashboardChartAccelTitle.beVisibleIf(hasAccel)
        binding.dashboardChartAccel.beVisibleIf(hasAccel)
        if (hasAccel) {
            binding.dashboardChartAccel.setData(
                labels,
                accelValues,
                getString(R.string.dashboard_chart_accel_y_label),
            )
        }
    }

    /**
     * Phase 8.3: render the Mood Mix over Time stacked-bar chart and the
     * Mood Distribution panel. Both are gated on `total > 0` — sessions
     * without a tap stay invisible (no synthetic Neutral, per Phase 8
     * Decisions #10 + #11). When `total == 0` both cards are `View.GONE`.
     *
     * Phase 8.3 dropped the Phase 8 Avg Mood KPI cell + Mood-over-Time
     * line chart entirely (averaging an ordinal valence over six
     * categorical labels produces interpretively vague numbers). The
     * KPI strip now sits at four cells unconditionally.
     */
    private fun renderMoodSection(
        ikdSnap: IkdAggregator.Snapshot,
        moodSnap: IkdMoodAggregator.MoodSnapshot,
        moodMix: IkdMoodAggregator.MoodMixSnapshot,
    ) {
        if (moodSnap.total == 0) {
            binding.dashboardMoodStackedChartCard.beGone()
            binding.dashboardMoodDistributionCard.beGone()
            // Phase 9.1: hide the section header when no mood cards render.
            binding.dashboardSectionHeaderMood.beGone()
            return
        }

        // Phase 9.1: section header visible alongside the mood widgets.
        binding.dashboardSectionHeaderMood.beVisible()
        binding.dashboardMoodStackedChartCard.beVisible()
        bindStackedChart(ikdSnap, moodMix)
        bindMoodLegend()

        // Distribution panel. Six rows in display order (Happiness → Anger).
        // Kept verbatim from Phase 8.
        binding.dashboardMoodDistributionCard.beVisible()
        renderMoodDistribution(moodSnap)
    }

    /**
     * Phase 8.3: feed the stacked-bar chart. X axis bucket labels are
     * lifted from the IKD snapshot so the bars align with the existing
     * IKD charts' columns (same date keys, same `formatBucketLabel`). For
     * each Ekman category, build a parallel list of percentages
     * (`count * 100 / bucketTotal`); empty buckets emit zero-height
     * segments so the X axis stays aligned.
     */
    private fun bindStackedChart(
        ikdSnap: IkdAggregator.Snapshot,
        moodMix: IkdMoodAggregator.MoodMixSnapshot,
    ) {
        val mixByBucket = moodMix.buckets.associateBy { it.label }
        val labels = ikdSnap.buckets.map { formatBucketLabel(it.label, ikdSnap.range) }
        val segments = MoodEmoji.displayOrder().map { score ->
            val values = ikdSnap.buckets.map { ikdBucket ->
                val mixBucket = mixByBucket[ikdBucket.label]
                if (mixBucket == null || mixBucket.total <= 0) {
                    0f
                } else {
                    val count = mixBucket.counts[score] ?: 0
                    count.toFloat() * PCT_MAX_FLOAT / mixBucket.total.toFloat()
                }
            }
            IkdStackedBarChartView.MoodSegment(
                score = score,
                label = getString(MoodEmoji.labelResFor(score)),
                colorInt = ContextCompat.getColor(this, moodColorResFor(score)),
                values = values,
            )
        }
        binding.dashboardChartMoodStacked.setData(labels, segments)
    }

    /**
     * Phase 8.3: build the legend strip below the stacked-bar chart.
     * Six rows, each emoji + label + tinted swatch. Idempotent — the
     * legend is rebuilt from scratch on every range switch so theme
     * changes (light → dark) and locale changes propagate cleanly.
     */
    private fun bindMoodLegend() {
        val legend = binding.dashboardMoodStackedLegend
        legend.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val textColor = getProperTextColor()
        for (score in MoodEmoji.displayOrder()) {
            val item = ItemMoodLegendSwatchBinding.inflate(inflater, legend, false)
            item.moodLegendSwatch.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, moodColorResFor(score))
            )
            val emoji = MoodEmoji.emojiFor(score)
            val label = getString(MoodEmoji.labelResFor(score))
            item.moodLegendLabel.text = "$emoji $label"
            item.moodLegendLabel.setTextColor(textColor)
            legend.addView(item.root)
        }
    }

    @ColorRes
    private fun moodColorResFor(score: Int): Int = when (score) {
        MoodEmoji.SCORE_HAPPINESS -> R.color.mood_color_happiness
        MoodEmoji.SCORE_SURPRISE -> R.color.mood_color_surprise
        MoodEmoji.SCORE_DISGUST -> R.color.mood_color_disgust
        MoodEmoji.SCORE_SADNESS -> R.color.mood_color_sadness
        MoodEmoji.SCORE_FEAR -> R.color.mood_color_fear
        MoodEmoji.SCORE_ANGER -> R.color.mood_color_anger
        else -> R.color.mood_color_happiness
    }

    private fun renderMoodDistribution(moodSnap: IkdMoodAggregator.MoodSnapshot) {
        val rows = listOf(
            ItemMoodDistributionRowBinding.bind(binding.dashboardMoodRowHappiness.root) to MoodEmoji.SCORE_HAPPINESS,
            ItemMoodDistributionRowBinding.bind(binding.dashboardMoodRowSurprise.root) to MoodEmoji.SCORE_SURPRISE,
            ItemMoodDistributionRowBinding.bind(binding.dashboardMoodRowDisgust.root) to MoodEmoji.SCORE_DISGUST,
            ItemMoodDistributionRowBinding.bind(binding.dashboardMoodRowSadness.root) to MoodEmoji.SCORE_SADNESS,
            ItemMoodDistributionRowBinding.bind(binding.dashboardMoodRowFear.root) to MoodEmoji.SCORE_FEAR,
            ItemMoodDistributionRowBinding.bind(binding.dashboardMoodRowAnger.root) to MoodEmoji.SCORE_ANGER,
        )
        val total = moodSnap.total
        for ((rowBinding, score) in rows) {
            val count = moodSnap.counts[score] ?: 0
            rowBinding.moodRowEmoji.text = MoodEmoji.emojiFor(score)
            rowBinding.moodRowLabel.setText(MoodEmoji.labelResFor(score))
            rowBinding.moodRowCount.text = count.toString()
            rowBinding.moodRowProgress.progress = if (total <= 0) {
                0
            } else {
                (count * PCT_MAX / total)
            }
        }
    }

    /**
     * Phase 9.5: render the Daily Activity section's calendar heatmap +
     * daily keypress bar chart. Both widgets are hidden when their
     * respective lists are empty. Section header is hidden when no
     * widget renders.
     */
    private fun renderDailyActivitySection(activity: IkdActivityAggregator.ActivitySnapshot) {
        val hasDaily = activity.dailyBuckets.isNotEmpty()
        binding.dashboardCalendarHeatmapCard.beVisibleIf(hasDaily)
        binding.dashboardDailyKeypressCard.beVisibleIf(hasDaily)
        binding.dashboardSectionHeaderDailyActivity.beVisibleIf(hasDaily)
        if (!hasDaily) return

        bindCalendarHeatmap(activity.dailyBuckets)
        bindDailyKeypressBar(activity.dailyBuckets)
    }

    /**
     * Phase 9.5: render the calendar heatmap. Cells laid out by ISO
     * week-of-year (rows) × day-of-week (columns Mon..Sun). Each non-empty
     * day contributes a `Cell` at intensity `count / max`. Tap-to-toast
     * drilldown surfaces the date + count.
     */
    private fun bindCalendarHeatmap(daily: List<IkdActivityAggregator.DailyBucket>) {
        val heatmap = binding.dashboardCalendarHeatmap
        val parsed = daily.mapNotNull { bucket ->
            runCatching {
                val date = isoDayParser.parse(bucket.day) ?: return@mapNotNull null
                val cal = Calendar.getInstance()
                cal.time = date
                Triple(bucket.day, cal, bucket.keystrokeCount)
            }.getOrNull()
        }
        if (parsed.isEmpty()) {
            binding.dashboardCalendarHeatmapCard.beGone()
            return
        }
        val firstWeek = parsed.minOf { it.second.get(Calendar.WEEK_OF_YEAR) }
        val lastWeek = parsed.maxOf { it.second.get(Calendar.WEEK_OF_YEAR) }
        val rows = (lastWeek - firstWeek + 1).coerceIn(1, MAX_HEATMAP_ROWS)
        val maxCount = daily.maxOf { it.keystrokeCount }

        val cells = parsed.map { (day, cal, count) ->
            val rowIdx = (cal.get(Calendar.WEEK_OF_YEAR) - firstWeek).coerceIn(0, rows - 1)
            // Calendar.DAY_OF_WEEK: 1=Sun … 7=Sat. Reorder to Mon..Sun (col 0..6).
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            val col = (dow + DOW_MON_OFFSET) % DOW_COUNT // Mon=0, Sun=6
            IkdHeatmapView.Cell(column = col, row = rowIdx, count = count, label = day)
        }
        heatmap.setData(cells, columns = DOW_COUNT, rows = rows, maxIntensity = maxCount)
        heatmap.setAxisLabels(
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
        heatmap.setOnCellClickListener { cell ->
            if (cell.label.isNotEmpty()) {
                Toast.makeText(
                    this,
                    getString(R.string.dashboard_calendar_cell_toast, cell.label, cell.count),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    /**
     * Phase 9.5: daily keypress bar chart. Reuses [IkdLineChartView] —
     * each day is one point. Empty days within the range render as
     * line breaks (Phase 5 convention) since `setData` skips null entries.
     * If the daily-buckets list is dense (one row per day) zero-keystroke
     * days won't appear; this matches the bar-chart spec — the heatmap
     * covers the empty-day pattern view.
     */
    private fun bindDailyKeypressBar(daily: List<IkdActivityAggregator.DailyBucket>) {
        val labels = daily.map { it.day.substring(it.day.lastIndexOf('-') + 1) } // "DD"
        val values = daily.map { it.keystrokeCount.toFloat() }
        binding.dashboardDailyKeypressChart.setData(
            labels,
            values,
            getString(R.string.dashboard_chart_daily_keypress_y_label),
        )
    }

    /**
     * Phase 9.3: render the Habits KPI strip + four trend charts. The
     * entire section is hidden when `totalSessions == 0` (no data to show).
     * Each chart's title and view are visibility-flipped together so the
     * "no data" state is one chart hiding (not a header floating above an
     * empty card).
     */
    private fun renderHabitsSection(habits: IkdHabitsAggregator.HabitsSnapshot) {
        val visible = habits.totalSessions > 0
        binding.dashboardSectionHeaderHabits.beVisibleIf(visible)
        binding.dashboardHabitsKpiStrip.beVisibleIf(visible)
        if (!visible) {
            // Hide every chart card alongside the section header.
            binding.dashboardChartHabitsSessionDurationTitle.beGone()
            binding.dashboardChartHabitsSessionDuration.beGone()
            binding.dashboardChartHabitsSessionsTitle.beGone()
            binding.dashboardChartHabitsSessions.beGone()
            binding.dashboardChartHabitsErrorRateTitle.beGone()
            binding.dashboardChartHabitsErrorRate.beGone()
            binding.dashboardChartHabitsFlightTitle.beGone()
            binding.dashboardChartHabitsFlight.beGone()
            return
        }

        // KPI strip
        val placeholder = getString(R.string.dashboard_value_placeholder)
        val locale = Locale.getDefault()
        binding.dashboardHabitsKpiSessionsValue.text = habits.totalSessions.toString()
        val typingMinutes = habits.totalTypingTimeMs.toDouble() / MS_PER_MINUTE
        binding.dashboardHabitsKpiTypingTimeValue.text = if (habits.totalTypingTimeMs <= 0L) {
            placeholder
        } else {
            getString(R.string.dashboard_kpi_typing_time_value, String.format(locale, "%.1f", typingMinutes))
        }
        binding.dashboardHabitsKpiAvgSessionValue.text = habits.avgSessionDurationMs?.let {
            getString(R.string.dashboard_habits_avg_session_value, it / MS_PER_SECOND)
        } ?: placeholder
        val streakSuffixRes = when (habits.streakUnit) {
            IkdHabitsAggregator.StreakUnit.DAYS -> R.string.dashboard_habits_streak_days
            IkdHabitsAggregator.StreakUnit.WEEKS -> R.string.dashboard_habits_streak_weeks
        }
        binding.dashboardHabitsKpiStreakValue.text = getString(streakSuffixRes, habits.longestStreak)

        // Charts
        val labels = habits.buckets.map { formatBucketLabel(it.label, habits.range) }
        val durationMinutes = habits.buckets.map { it.avgSessionDurationMs?.toFloat()?.div(MS_PER_MINUTE) }
        // sessionCount: zero is meaningful (Phase 9.3 plan §"Chart-empty rules"), render as zeros not nulls.
        val sessionCounts = habits.buckets.map { it.sessionCount.toFloat() }
        val errorPct = habits.buckets.map { it.errorRatePct?.toFloat() }
        val flightMs = habits.buckets.map { it.avgFlightMs?.toFloat() }

        bindHabitsChart(
            title = binding.dashboardChartHabitsSessionDurationTitle,
            chart = binding.dashboardChartHabitsSessionDuration,
            labels = labels,
            values = durationMinutes,
            yLabel = getString(R.string.dashboard_chart_habits_session_duration_y_label),
        )
        bindHabitsChart(
            title = binding.dashboardChartHabitsSessionsTitle,
            chart = binding.dashboardChartHabitsSessions,
            labels = labels,
            values = sessionCounts,
            yLabel = getString(R.string.dashboard_chart_habits_sessions_y_label),
            // sessions chart: zeros are meaningful; show whenever any bucket exists.
            showAsZeros = true,
        )
        bindHabitsChart(
            title = binding.dashboardChartHabitsErrorRateTitle,
            chart = binding.dashboardChartHabitsErrorRate,
            labels = labels,
            values = errorPct,
            yLabel = getString(R.string.dashboard_chart_habits_error_rate_y_label),
        )
        bindHabitsChart(
            title = binding.dashboardChartHabitsFlightTitle,
            chart = binding.dashboardChartHabitsFlight,
            labels = labels,
            values = flightMs,
            yLabel = getString(R.string.dashboard_chart_habits_flight_y_label),
        )
    }

    /**
     * Phase 9.3: bind a Habits trend chart. When all values are null and
     * `showAsZeros == false`, hide the chart + its title so the section
     * doesn't show a label above an empty card.
     */
    private fun bindHabitsChart(
        title: org.fossify.commons.views.MyTextView,
        chart: org.fossify.keyboard.views.IkdLineChartView,
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

    /**
     * Bucket labels arrive as raw `strftime` keys (e.g., `2026-05-02` or `2026-18`).
     * Reformat them to something readable on a tight X axis. Falls back to the raw
     * key when parsing fails — no crash, just less prettiness.
     */
    private fun formatBucketLabel(rawKey: String, range: IkdAggregator.Range): String {
        return when (range) {
            IkdAggregator.Range.WEEK,
            IkdAggregator.Range.MONTH -> runCatching {
                val date = isoDayParser.parse(rawKey) ?: return@runCatching rawKey
                SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
            }.getOrDefault(rawKey)

            IkdAggregator.Range.ALL_TIME -> {
                // SQLite emits `%Y-%W` as e.g. "2026-18". Strip the year for tighter labels.
                rawKey.substringAfter('-', missingDelimiterValue = rawKey)
                    .let { week -> "W$week" }
            }
        }
    }

    private fun rangeButtonId(range: IkdAggregator.Range): Int = when (range) {
        IkdAggregator.Range.WEEK -> R.id.dashboard_range_week
        IkdAggregator.Range.MONTH -> R.id.dashboard_range_month
        IkdAggregator.Range.ALL_TIME -> R.id.dashboard_range_all
    }

    private fun idToRange(id: Int): IkdAggregator.Range = when (id) {
        R.id.dashboard_range_week -> IkdAggregator.Range.WEEK
        R.id.dashboard_range_month -> IkdAggregator.Range.MONTH
        R.id.dashboard_range_all -> IkdAggregator.Range.ALL_TIME
        else -> IkdAggregator.Range.WEEK
    }

    companion object {
        private const val STATE_RANGE = "dashboard_range"
        private const val MS_PER_MINUTE = 60_000L
        private const val MS_PER_SECOND = 1_000.0

        // Phase 9.5: calendar heatmap reorders Calendar.DAY_OF_WEEK
        // (1=Sun..7=Sat) to Mon..Sun for Western convention.
        private const val DOW_COUNT = 7
        private const val DOW_MON_OFFSET = 5 // (DAY_OF_WEEK + 5) % 7 → Mon=0, Sun=6
        private const val MAX_HEATMAP_ROWS = 53

        // Phase 8: Distribution-panel ProgressBar tops out at 100 (`max`
        // attribute on the row layout). Each row's progress is its share
        // of the total, scaled to that range.
        private const val PCT_MAX = 100

        // Phase 8.3: same scaling for the stacked-bar chart segments.
        // Float so the per-bucket percentage stays as a Float through
        // MPAndroidChart's BarEntry.
        private const val PCT_MAX_FLOAT = 100f
    }
}
