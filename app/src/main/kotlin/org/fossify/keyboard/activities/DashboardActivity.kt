package org.fossify.keyboard.activities

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.keyboard.R
import org.fossify.keyboard.databinding.ActivityDashboardBinding
import org.fossify.keyboard.databinding.ItemMoodDistributionRowBinding
import org.fossify.keyboard.extensions.ikdAggregator
import org.fossify.keyboard.extensions.ikdMoodAggregator
import org.fossify.keyboard.helpers.IkdAggregator
import org.fossify.keyboard.helpers.IkdMoodAggregator
import org.fossify.keyboard.helpers.MoodEmoji
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

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
        loadSnapshot()
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
            val agg = ikdAggregator
            val moodAgg = ikdMoodAggregator
            val range = currentRange
            val pair = withContext(Dispatchers.IO) {
                agg.snapshot(range) to moodAgg.snapshot(range)
            }
            render(pair.first, pair.second)
        }
    }

    private fun render(snap: IkdAggregator.Snapshot, moodSnap: IkdMoodAggregator.MoodSnapshot) {
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
        renderMood(snap, moodSnap)
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
     * Phase 8: render the Mood-over-Time line chart, the Mood Distribution
     * panel, and the Avg Mood KPI. All three are gated on `total > 0` —
     * sessions without a tap stay invisible (no synthetic Neutral, per
     * Decisions #10 + #11). When `total == 0` the three views are
     * `View.GONE` and the KPI strip falls back to four cells.
     *
     * The Mood-over-Time chart uses the same X-axis bucket labels as the
     * IKD charts (same `Range.bucketFormat`), so users can correlate
     * mood with typing speed at a glance.
     */
    private fun renderMood(
        ikdSnap: IkdAggregator.Snapshot,
        moodSnap: IkdMoodAggregator.MoodSnapshot,
    ) {
        if (moodSnap.total == 0) {
            binding.dashboardKpiAvgMoodCell.beGone()
            binding.dashboardMoodChartCard.beGone()
            binding.dashboardMoodDistributionCard.beGone()
            return
        }

        // Avg Mood KPI: rounded emoji + precise number (e.g. "🤢 3.2"). The
        // round-to-nearest is clamped to 1..6 so MoodEmoji.emojiFor never
        // sees an out-of-range score from a value at the edges.
        val avg = moodSnap.averageScore
        if (avg != null) {
            val rounded = avg.roundToInt().coerceIn(MoodEmoji.SCORE_HAPPINESS, MoodEmoji.SCORE_ANGER)
            val emoji = MoodEmoji.emojiFor(rounded)
            binding.dashboardKpiAvgMoodValue.text =
                getString(R.string.dashboard_avg_mood_value_format, emoji, avg)
            binding.dashboardKpiAvgMoodCell.beVisible()
        } else {
            binding.dashboardKpiAvgMoodCell.beGone()
        }

        // Mood-over-Time line chart. Align bucket keys with the IKD chart's
        // X axis so the same dates render identically left-to-right.
        binding.dashboardMoodChartCard.beVisible()
        val moodByBucket = moodSnap.buckets.associateBy { it.label }
        val moodLabels = ikdSnap.buckets.map { formatBucketLabel(it.label, ikdSnap.range) }
        val moodValues = ikdSnap.buckets.map { ikdBucket ->
            moodByBucket[ikdBucket.label]?.avgScore?.toFloat()
        }
        binding.dashboardChartMood.setData(
            moodLabels,
            moodValues,
            getString(R.string.dashboard_chart_mood_y_label),
        )

        // Distribution panel. Six rows in display order (Happiness → Anger).
        binding.dashboardMoodDistributionCard.beVisible()
        renderMoodDistribution(moodSnap)
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

        // Phase 8: Distribution-panel ProgressBar tops out at 100 (`max`
        // attribute on the row layout). Each row's progress is its share
        // of the total, scaled to that range.
        private const val PCT_MAX = 100
    }
}
