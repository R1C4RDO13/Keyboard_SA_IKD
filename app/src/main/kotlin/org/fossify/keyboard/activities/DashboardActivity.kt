package org.fossify.keyboard.activities

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.keyboard.R
import org.fossify.keyboard.databinding.ActivityDashboardBinding
import org.fossify.keyboard.extensions.ikdAggregator
import org.fossify.keyboard.helpers.IkdAggregator
import java.text.SimpleDateFormat
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
        loadSnapshot()
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
            val snap = ikdAggregator.snapshot(currentRange)
            render(snap)
        }
    }

    private fun render(snap: IkdAggregator.Snapshot) {
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
    }
}
