package org.fossify.keyboard.activities

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.keyboard.R
import org.fossify.keyboard.databinding.ActivityEventFeedBinding
import org.fossify.keyboard.databinding.ItemSensorReadingBinding
import org.fossify.keyboard.databinding.ItemTimingEventBinding
import org.fossify.keyboard.extensions.ikdDB
import org.fossify.keyboard.extensions.ikdSessionStatsLoader
import org.fossify.keyboard.helpers.IkdFormatters
import org.fossify.keyboard.helpers.IkdSessionStatsLoader
import org.fossify.keyboard.helpers.LiveCaptureSessionStore
import org.fossify.keyboard.models.KeyTimingEvent
import org.fossify.keyboard.models.SensorReadingEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val HEADER_DATE_PATTERN = "MMM d yyyy, HH:mm:ss"

private val headerDateFormatter = SimpleDateFormat(HEADER_DATE_PATTERN, Locale.getDefault())

private fun Context.placeholder(): String = getString(R.string.session_detail_value_placeholder)

private fun Context.formatNullableDate(timestampMs: Long?): String =
    timestampMs?.let { headerDateFormatter.format(Date(it)) } ?: placeholder()

private fun Context.formatNullableDuration(durationMs: Long?): String =
    durationMs?.let { IkdFormatters.formatDuration(it) } ?: placeholder()

private fun Context.formatNullableValue(value: Double?, formatRes: Int): String =
    value?.let { getString(formatRes, it) } ?: placeholder()

private fun Context.formatLocale(locale: String): String =
    if (locale.isNotEmpty()) locale else placeholder()

class EventFeedActivity : SimpleActivity() {
    companion object {
        const val EXTRA_SESSION_ID = "session_id"
        private const val SESSION_ID_SHORT_LENGTH = 8
    }

    private val binding by viewBinding(ActivityEventFeedBinding::inflate)

    private lateinit var timingAdapter: TimingAdapter
    private lateinit var sensorAdapter: SensorAdapter

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
            if (item.itemId == R.id.event_feed_refresh) { loadData(); true } else false
        }

        setupEdgeToEdge(padBottomSystem = listOf(binding.eventFeedNestedScrollview))
        setupMaterialScrollListener(binding.eventFeedNestedScrollview, binding.eventFeedAppbar)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.eventFeedAppbar, NavigationIcon.Arrow)
        applyThemeColors()
        loadData()
    }

    private fun applyThemeColors() {
        updateTextColors(binding.eventFeedNestedScrollview)
        val primary = getProperPrimaryColor()
        binding.eventFeedTimingLabel.setTextColor(primary)
        binding.eventFeedSensorLabel.setTextColor(primary)
        binding.sessionDetailSectionLabel.setTextColor(primary)
    }

    private fun loadData() {
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
        if (sessionId != null) {
            loadDataFromDb(sessionId)
        } else {
            loadDataFromLiveStore()
        }
    }

    private fun loadDataFromLiveStore() {
        val timingEvents = LiveCaptureSessionStore.getTimingEvents().asReversed()
        val sensorReadings = LiveCaptureSessionStore.getSensorReadings().asReversed()
        // Live mode never shows the session detail header — the data is in flux
        // and there is no canonical SessionRecord to query.
        binding.eventFeedSessionHeader.beGone()
        binding.eventFeedNotFoundText.beGone()
        populateUI(timingEvents, sensorReadings)
    }

    private fun loadDataFromDb(sessionId: String) {
        ensureBackgroundThread {
            val events = ikdDB.IkdEventDao().getEventsForSession(sessionId)
            val samples = ikdDB.SensorSampleDao().getSamplesForSession(sessionId)
            val timingEvents = events.map { e ->
                KeyTimingEvent(
                    sessionId = e.sessionId,
                    timestamp = e.timestamp,
                    eventCategory = e.eventCategory,
                    ikdMs = e.ikdMs,
                    holdTimeMs = e.holdTimeMs,
                    flightTimeMs = e.flightTimeMs,
                    isCorrection = e.isCorrection,
                )
            }.asReversed()
            val sensorReadings = samples.map { s ->
                SensorReadingEvent(
                    sessionId = s.sessionId,
                    timestamp = s.timestamp,
                    sensorType = s.sensorType,
                    x = s.x,
                    y = s.y,
                    z = s.z,
                )
            }.asReversed()
            val label = "Session ${sessionId.takeLast(SESSION_ID_SHORT_LENGTH)} (${timingEvents.size})"
            runOnUiThread {
                populateUI(timingEvents, sensorReadings, label)
            }
        }
        // Loader handles its own background hop via Dispatchers.IO.
        loadSessionStats(sessionId)
    }

    private fun loadSessionStats(sessionId: String) {
        val loader = ikdSessionStatsLoader
        CoroutineScope(Dispatchers.Main).launch {
            val stats = loader.load(sessionId)
            renderSessionHeader(stats)
        }
    }

    private fun renderSessionHeader(stats: IkdSessionStatsLoader.SessionStats?) {
        if (stats == null) {
            binding.eventFeedSessionHeader.beGone()
            binding.eventFeedNotFoundText.beVisible()
            return
        }
        binding.eventFeedNotFoundText.beGone()
        binding.eventFeedSessionHeader.beVisible()

        val record = stats.record
        val msFmt = R.string.session_detail_ms_format
        binding.sessionDetailStartedAtValue.text = formatNullableDate(record.startedAt)
        binding.sessionDetailEndedAtValue.text = formatNullableDate(record.endedAt)
        binding.sessionDetailDurationValue.text = formatNullableDuration(stats.durationMs)
        binding.sessionDetailOrientationValue.text =
            IkdFormatters.orientationLabel(this, record.deviceOrientation)
        binding.sessionDetailLocaleValue.text = formatLocale(record.locale)
        binding.sessionDetailEventsValue.text = record.eventCount.toString()
        binding.sessionDetailSensorsValue.text = record.sensorCount.toString()
        binding.sessionDetailWpmValue.text =
            formatNullableValue(stats.wpm, R.string.session_detail_wpm_format)
        binding.sessionDetailErrorRateValue.text =
            formatNullableValue(stats.errorRatePct, R.string.session_detail_error_rate_format)
        binding.sessionDetailAvgIkdValue.text = formatNullableValue(stats.avgIkdMs, msFmt)
        binding.sessionDetailAvgDwellValue.text = formatNullableValue(stats.avgHoldMs, msFmt)
        binding.sessionDetailAvgFlightValue.text = formatNullableValue(stats.avgFlightMs, msFmt)

        // Re-apply theme colors to the freshly-shown header.
        updateTextColors(binding.eventFeedSessionHeader)
        binding.sessionDetailSectionLabel.setTextColor(getProperPrimaryColor())
    }

    private fun populateUI(
        timingEvents: List<KeyTimingEvent>,
        sensorReadings: List<SensorReadingEvent>,
        sessionLabel: String? = null,
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

        binding.eventFeedToolbar.title = when {
            sessionLabel != null -> sessionLabel
            timingEvents.isNotEmpty() -> "${getString(R.string.event_feed_title)} (${timingEvents.size})"
            else -> getString(R.string.event_feed_title)
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

        fun setReadings(newReadings: List<SensorReadingEvent>) {
            readings.clear()
            readings.addAll(newReadings)
            notifyDataSetChanged()
        }

        inner class VH(private val b: ItemSensorReadingBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(reading: SensorReadingEvent) {
                b.sensorType.text = reading.sensorType
                b.sensorX.text = "%.3f".format(reading.x)
                b.sensorY.text = "%.3f".format(reading.y)
                b.sensorZ.text = "%.3f".format(reading.z)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemSensorReadingBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(readings[position])
        override fun getItemCount() = readings.size
    }
}
