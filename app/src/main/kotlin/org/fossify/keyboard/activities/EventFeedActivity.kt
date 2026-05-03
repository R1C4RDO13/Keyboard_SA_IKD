package org.fossify.keyboard.activities

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyTextView
import org.fossify.keyboard.R
import org.fossify.keyboard.databinding.ActivityEventFeedBinding
import org.fossify.keyboard.databinding.ItemSensorReadingBinding
import org.fossify.keyboard.databinding.ItemTimingEventBinding
import org.fossify.keyboard.extensions.config
import org.fossify.keyboard.extensions.ikdDB
import org.fossify.keyboard.extensions.ikdSessionChartLoader
import org.fossify.keyboard.extensions.ikdSessionStatsLoader
import org.fossify.keyboard.helpers.IkdCsvWriter
import org.fossify.keyboard.helpers.IkdCsvWriter.asSensorRow
import org.fossify.keyboard.helpers.IkdCsvWriter.asTimingRow
import org.fossify.keyboard.helpers.IkdFormatters
import org.fossify.keyboard.helpers.IkdSessionChartLoader
import org.fossify.keyboard.helpers.IkdSessionStatsLoader
import org.fossify.keyboard.helpers.LiveCaptureSessionStore
import org.fossify.keyboard.helpers.SENSOR_DISPLAY_MODE_AXES
import org.fossify.keyboard.helpers.SENSOR_DISPLAY_MODE_MAGNITUDE
import org.fossify.keyboard.models.KeyTimingEvent
import org.fossify.keyboard.models.SensorReadingEvent
import org.fossify.keyboard.models.SessionRecord
import org.fossify.keyboard.models.magnitude
import org.fossify.keyboard.views.IkdLineChartView
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val HEADER_DATE_PATTERN = "MMM d yyyy, HH:mm"

private val headerDateFormatter = SimpleDateFormat(HEADER_DATE_PATTERN, Locale.getDefault())

private fun Context.placeholder(): String = getString(R.string.session_detail_value_placeholder)

private fun Context.formatNullableDuration(durationMs: Long?): String =
    durationMs?.let { IkdFormatters.formatDuration(it) } ?: placeholder()

private fun Context.formatNullableValue(value: Double?, formatRes: Int): String =
    value?.let { getString(formatRes, it) } ?: placeholder()

/**
 * Compact one-liner for the session dashboard's metadata chip:
 * `Started · Duration · Orientation · Locale`. Sentinel `-1` orientation
 * (capture disabled) and empty locales are dropped from the line rather
 * than rendered as "—" — matches plan section 3.
 */
private fun Context.buildSessionMetadataLine(record: SessionRecord, separator: String): String {
    val parts = mutableListOf<String>()
    parts += headerDateFormatter.format(Date(record.startedAt))

    val durationMs = record.endedAt?.let { it - record.startedAt }
    if (durationMs != null && durationMs > 0L) {
        parts += IkdFormatters.formatDuration(durationMs)
    }
    if (record.deviceOrientation >= 0) {
        parts += IkdFormatters.orientationLabel(this, record.deviceOrientation)
    }
    if (record.locale.isNotEmpty()) {
        parts += record.locale
    }
    return parts.joinToString(separator)
}

class EventFeedActivity : SimpleActivity() {
    companion object {
        const val EXTRA_SESSION_ID = "session_id"
        private const val SESSION_ID_SHORT_LENGTH = 8
        private const val EXPORT_DATE_PATTERN = "yyyyMMdd"
    }

    private val binding by viewBinding(ActivityEventFeedBinding::inflate)

    private lateinit var timingAdapter: TimingAdapter
    private lateinit var sensorAdapter: SensorAdapter

    private val isDbBackedMode: Boolean
        get() = intent.hasExtra(EXTRA_SESSION_ID)

    private val saveSessionCsvLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return@registerForActivityResult
        exportSessionToUri(sessionId, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        timingAdapter = TimingAdapter()
        binding.eventFeedTimingList.apply {
            layoutManager = LinearLayoutManager(this@EventFeedActivity)
            adapter = timingAdapter
        }

        sensorAdapter = SensorAdapter()
        binding.eventFeedSensorList.apply {
            layoutManager = LinearLayoutManager(this@EventFeedActivity)
            adapter = sensorAdapter
        }

        binding.eventFeedToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.event_feed_refresh -> { loadData(); true }
                R.id.event_feed_save_csv -> { startSessionExport(); true }
                R.id.event_feed_toggle_sensor_view -> {
                    val next = if (config.sensorDisplayMode == SENSOR_DISPLAY_MODE_MAGNITUDE) {
                        SENSOR_DISPLAY_MODE_AXES
                    } else {
                        SENSOR_DISPLAY_MODE_MAGNITUDE
                    }
                    config.sensorDisplayMode = next
                    applySensorDisplayMode(next)
                    sensorAdapter.notifyDataSetChanged()
                    true
                }
                else -> false
            }
        }

        // Pre-flip container visibility so the very first frame shows the
        // correct layout — avoids a flash of the wrong content while the
        // data hop is in flight.
        if (isDbBackedMode) {
            binding.eventFeedSessionDashboard.beVisible()
            binding.eventFeedLiveContainer.beGone()
        } else {
            binding.eventFeedSessionDashboard.beGone()
            binding.eventFeedLiveContainer.beVisible()
        }

        setupEdgeToEdge(padBottomSystem = listOf(binding.eventFeedNestedScrollview))
        setupMaterialScrollListener(binding.eventFeedNestedScrollview, binding.eventFeedAppbar)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.eventFeedAppbar, NavigationIcon.Arrow)
        // Hide the live-mode-only sensor toggle when in DB-backed dashboard mode.
        binding.eventFeedToolbar.menu.findItem(R.id.event_feed_toggle_sensor_view)
            ?.isVisible = !isDbBackedMode
        // Save as CSV is only meaningful for the persisted-session view.
        binding.eventFeedToolbar.menu.findItem(R.id.event_feed_save_csv)
            ?.isVisible = isDbBackedMode
        applyThemeColors()
        applySensorDisplayMode(config.sensorDisplayMode)
        loadData()
    }

    private fun loadData() {
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
        if (sessionId != null) {
            loadDataFromDb(sessionId)
        } else {
            loadDataFromLiveStore()
        }
    }

    private fun applySensorDisplayMode(mode: String) {
        // Only the live-mode raw lists honor this toggle; the dashboard charts
        // always show magnitude in v1 (Phase 6 design call to overlay axes).
        if (isDbBackedMode) return

        val isMagnitude = mode == SENSOR_DISPLAY_MODE_MAGNITUDE
        sensorAdapter.displayMode = mode
        binding.eventFeedSensorHeaderAxes.visibility =
            if (isMagnitude) View.GONE else View.VISIBLE
        binding.eventFeedSensorHeaderMagnitude.visibility =
            if (isMagnitude) View.VISIBLE else View.GONE

        val toggle = binding.eventFeedToolbar.menu.findItem(R.id.event_feed_toggle_sensor_view)
        if (toggle != null) {
            val iconRes = if (isMagnitude) {
                R.drawable.ic_view_axes_vector
            } else {
                R.drawable.ic_view_magnitude_vector
            }
            val cdRes = if (isMagnitude) {
                R.string.sensor_view_magnitude_cd
            } else {
                R.string.sensor_view_axes_cd
            }
            toggle.setIcon(iconRes)
            toggle.contentDescription = getString(cdRes)
        }
    }

    private fun applyThemeColors() {
        updateTextColors(binding.eventFeedNestedScrollview)
        val primary = getProperPrimaryColor()
        binding.eventFeedTimingLabel.setTextColor(primary)
        binding.eventFeedSensorLabel.setTextColor(primary)
        binding.sessionDashboardIkdTitle.setTextColor(primary)
        binding.sessionDashboardGyroTitle.setTextColor(primary)
        binding.sessionDashboardAccelTitle.setTextColor(primary)
        // MaterialCardView's default ?attr/colorSurface does not track Fossify's
        // runtime background color, so on a custom theme the cards stand out as
        // unthemed slabs. Tint them to the activity's background color and let
        // cardElevation's shadow demarcate the card silhouette.
        val background = getProperBackgroundColor()
        binding.sessionDashboardKpiCard.setCardBackgroundColor(background)
        binding.sessionDashboardIkdCard.setCardBackgroundColor(background)
        binding.sessionDashboardGyroCard.setCardBackgroundColor(background)
        binding.sessionDashboardAccelCard.setCardBackgroundColor(background)
    }

    /**
     * Binds chart data onto an [org.fossify.keyboard.views.IkdLineChartView],
     * or hides the chart and shows an "No data" overlay when the data is
     * empty / null. The overlay sits inside the same `MaterialCardView` as
     * the chart so the card retains its silhouette either way.
     */
    private fun bindChartOrEmpty(
        chart: IkdLineChartView,
        empty: MyTextView,
        labels: List<String>?,
        values: List<Float?>?,
        yAxisLabel: String,
    ) {
        if (labels.isNullOrEmpty() || values.isNullOrEmpty()) {
            chart.beGone()
            empty.beVisible()
            return
        }
        chart.beVisible()
        empty.beGone()
        chart.setData(labels, values, yAxisLabel)
    }

    private fun loadDataFromLiveStore() {
        val timingEvents = LiveCaptureSessionStore.getTimingEvents().asReversed()
        val sensorReadings = LiveCaptureSessionStore.getSensorReadings().asReversed()
        binding.eventFeedNotFoundText.beGone()
        populateLiveLists(timingEvents, sensorReadings)
    }

    private fun startSessionExport() {
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return
        val shortId = sessionId.take(SESSION_ID_SHORT_LENGTH)
        val datePart = SimpleDateFormat(EXPORT_DATE_PATTERN, Locale.getDefault()).format(Date())
        saveSessionCsvLauncher.launch("ikd_session_${shortId}_$datePart.csv")
    }

    private fun exportSessionToUri(sessionId: String, uri: Uri) {
        ensureBackgroundThread {
            try {
                val events = ikdDB.IkdEventDao().getEventsForSession(sessionId)
                val samples = ikdDB.SensorSampleDao().getSamplesForSession(sessionId)
                contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.bufferedWriter().use { writer ->
                        IkdCsvWriter.writeSessionCsv(
                            writer,
                            events.map { it.asTimingRow() },
                            samples.map { it.asSensorRow() },
                        )
                    }
                }
                runOnUiThread { toast(R.string.ikd_export_session_success) }
            } catch (e: IOException) {
                e.printStackTrace()
                runOnUiThread { toast(R.string.ikd_export_session_error) }
            } catch (e: SecurityException) {
                e.printStackTrace()
                runOnUiThread { toast(R.string.ikd_export_session_error) }
            }
        }
    }

    private fun loadDataFromDb(sessionId: String) {
        val statsLoader = ikdSessionStatsLoader
        val chartLoader = ikdSessionChartLoader
        // One background hop covers both Room reads — keeps StrictMode quiet
        // and gives us a single round-trip per onResume.
        CoroutineScope(Dispatchers.Main).launch {
            val (stats, chartData) = withContext(Dispatchers.IO) {
                val stats = statsLoader.load(sessionId)
                val chartData = chartLoader.load(sessionId)
                stats to chartData
            }
            renderSessionDashboard(sessionId, stats, chartData)
        }
    }

    private fun renderSessionDashboard(
        sessionId: String,
        stats: IkdSessionStatsLoader.SessionStats?,
        chartData: IkdSessionChartLoader.SessionChartData?,
    ) {
        val titleSuffix = sessionId.takeLast(SESSION_ID_SHORT_LENGTH)
        binding.eventFeedToolbar.title = "Session $titleSuffix"

        if (stats == null) {
            binding.eventFeedSessionDashboard.beGone()
            binding.eventFeedNotFoundText.beVisible()
            return
        }
        binding.eventFeedNotFoundText.beGone()
        binding.eventFeedEmptyText.beGone()
        binding.eventFeedSessionDashboard.beVisible()

        // KPI strip (4 headline cells + 3 secondary chips).
        val msFmt = R.string.session_detail_ms_format
        binding.sessionDashboardEventsValue.text = stats.record.eventCount.toString()
        binding.sessionDashboardTypingTimeValue.text = formatNullableDuration(stats.durationMs)
        binding.sessionDashboardWpmValue.text =
            formatNullableValue(stats.wpm, R.string.session_detail_wpm_format)
        binding.sessionDashboardErrorRateValue.text =
            formatNullableValue(stats.errorRatePct, R.string.session_detail_error_rate_format)
        binding.sessionDashboardAvgIkdValue.text = formatNullableValue(stats.avgIkdMs, msFmt)
        binding.sessionDashboardAvgDwellValue.text = formatNullableValue(stats.avgHoldMs, msFmt)
        binding.sessionDashboardAvgFlightValue.text = formatNullableValue(stats.avgFlightMs, msFmt)

        // Metadata one-liner.
        val separator = getString(R.string.session_dashboard_metadata_separator)
        binding.sessionDashboardMetadataLine.text = buildSessionMetadataLine(stats.record, separator)

        // Three single-series charts — IKD over time, gyro magnitude, accel magnitude.
        // Null buckets render as line breaks (no fake zeros); empty lists swap the
        // chart for a centered "No data" MyTextView inside the same MaterialCardView.
        bindChartOrEmpty(
            chart = binding.sessionDashboardIkdChart,
            empty = binding.sessionDashboardIkdEmpty,
            labels = chartData?.timing?.map { it.label },
            values = chartData?.timing?.map { it.avgIkdMs?.toFloat() },
            yAxisLabel = getString(R.string.session_dashboard_chart_ikd),
        )
        bindChartOrEmpty(
            chart = binding.sessionDashboardGyroChart,
            empty = binding.sessionDashboardGyroEmpty,
            labels = chartData?.gyro?.map { it.label },
            values = chartData?.gyro?.map { it.avgMagnitude },
            yAxisLabel = getString(R.string.session_dashboard_chart_gyro),
        )
        bindChartOrEmpty(
            chart = binding.sessionDashboardAccelChart,
            empty = binding.sessionDashboardAccelEmpty,
            labels = chartData?.accel?.map { it.label },
            values = chartData?.accel?.map { it.avgMagnitude },
            yAxisLabel = getString(R.string.session_dashboard_chart_accel),
        )

        // Re-apply text colors on the freshly-shown subtree.
        updateTextColors(binding.eventFeedSessionDashboard)
        val primary = getProperPrimaryColor()
        binding.sessionDashboardIkdTitle.setTextColor(primary)
        binding.sessionDashboardGyroTitle.setTextColor(primary)
        binding.sessionDashboardAccelTitle.setTextColor(primary)
    }

    private fun populateLiveLists(
        timingEvents: List<KeyTimingEvent>,
        sensorReadings: List<SensorReadingEvent>,
    ) {
        val hasAnyData = timingEvents.isNotEmpty() || sensorReadings.isNotEmpty()

        if (hasAnyData) {
            binding.eventFeedEmptyText.beGone()
            binding.eventFeedTimingLabel.beVisible()
            binding.eventFeedTimingHeader.beVisible()
            binding.eventFeedSensorLabel.beVisible()
            binding.eventFeedSensorHeader.beVisible()
        } else {
            binding.eventFeedEmptyText.beVisible()
            binding.eventFeedTimingLabel.beGone()
            binding.eventFeedTimingHeader.beGone()
            binding.eventFeedSensorLabel.beGone()
            binding.eventFeedSensorHeader.beGone()
        }

        binding.eventFeedToolbar.title = if (timingEvents.isNotEmpty()) {
            "${getString(R.string.event_feed_title)} (${timingEvents.size})"
        } else {
            getString(R.string.event_feed_title)
        }

        timingAdapter.setEvents(timingEvents)
        sensorAdapter.setReadings(sensorReadings)

        binding.eventFeedTimingList.post {
            updateTextColors(binding.eventFeedNestedScrollview)
        }
    }

    private inner class TimingAdapter : RecyclerView.Adapter<TimingAdapter.VH>() {
        private val events = mutableListOf<KeyTimingEvent>()

        fun setEvents(newEvents: List<KeyTimingEvent>) {
            events.clear()
            events.addAll(newEvents)
            notifyDataSetChanged()
        }

        inner class VH(private val b: ItemTimingEventBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(event: KeyTimingEvent) {
                val ms = { v: Long -> if (v >= 0L) "${v}ms" else "—" }
                b.eventCategory.text = event.eventCategory
                b.eventIkd.text = ms(event.ikdMs)
                b.eventHold.text = ms(event.holdTimeMs)
                b.eventFlight.text = ms(event.flightTimeMs)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemTimingEventBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(events[position])
        override fun getItemCount() = events.size
    }

    private inner class SensorAdapter : RecyclerView.Adapter<SensorAdapter.VH>() {
        private val readings = mutableListOf<SensorReadingEvent>()
        var displayMode: String = SENSOR_DISPLAY_MODE_MAGNITUDE

        fun setReadings(newReadings: List<SensorReadingEvent>) {
            readings.clear()
            readings.addAll(newReadings)
            notifyDataSetChanged()
        }

        inner class VH(private val b: ItemSensorReadingBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(reading: SensorReadingEvent) {
                b.sensorType.text = reading.sensorType
                val isMagnitude = displayMode == SENSOR_DISPLAY_MODE_MAGNITUDE
                b.sensorAxesContainer.visibility = if (isMagnitude) View.GONE else View.VISIBLE
                b.sensorMagnitude.visibility = if (isMagnitude) View.VISIBLE else View.GONE
                if (isMagnitude) {
                    b.sensorMagnitude.text = "%.3f".format(reading.magnitude())
                } else {
                    b.sensorX.text = "%.3f".format(reading.x)
                    b.sensorY.text = "%.3f".format(reading.y)
                    b.sensorZ.text = "%.3f".format(reading.z)
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemSensorReadingBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(readings[position])
        override fun getItemCount() = readings.size
    }
}
