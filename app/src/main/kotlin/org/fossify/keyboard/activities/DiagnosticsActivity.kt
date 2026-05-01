package org.fossify.keyboard.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContracts
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.keyboard.R
import org.fossify.keyboard.databinding.ActivityDiagnosticsBinding
import org.fossify.keyboard.helpers.KinematicSensorHelper
import org.fossify.keyboard.helpers.LiveCaptureSessionStore
import org.fossify.keyboard.models.KeyTimingEvent
import org.fossify.keyboard.models.SensorReadingEvent

class DiagnosticsActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityDiagnosticsBinding::inflate)

    private lateinit var sensorHelper: KinematicSensorHelper
    private var displayedSessionId = ""

    private val statusRefreshHandler = Handler(Looper.getMainLooper())
    private val statusRefreshRunnable = object : Runnable {
        override fun run() {
            updateStatusDisplay()
            statusRefreshHandler.postDelayed(this, 250)
        }
    }

    private val saveCsvLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val timingEvents = LiveCaptureSessionStore.getTimingEvents()
            val sensorReadings = LiveCaptureSessionStore.getSensorReadings()
            contentResolver.openOutputStream(uri)?.use { stream ->
                stream.bufferedWriter().use { writer ->
                    writer.write("session_id,timestamp_ms,event_category,ikd_ms,hold_time_ms,flight_time_ms,is_correction\n")
                    for (event in timingEvents) {
                        writer.write("${event.sessionId},${event.timestamp},${event.eventCategory},${event.ikdMs},${event.holdTimeMs},${event.flightTimeMs},${event.isCorrection}\n")
                    }
                    writer.write("\n#sensor_readings\n")
                    writer.write("session_id,timestamp_ms,sensor_type,x,y,z\n")
                    for (reading in sensorReadings) {
                        writer.write("${reading.sessionId},${reading.timestamp},${reading.sensorType},${reading.x},${reading.y},${reading.z}\n")
                    }
                }
            }
            toast(R.string.diagnostics_export_success)
        } catch (e: Exception) {
            toast(R.string.diagnostics_export_error)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        sensorHelper = KinematicSensorHelper(
            context = this,
            getSessionId = { LiveCaptureSessionStore.currentSessionId }
        ) { event ->
            runOnUiThread { updateSensorDisplay(event) }
        }

        setupGyroVisibility()
        setupOptionsMenu()
        binding.diagnosticsViewLogHolder.setOnClickListener {
            startActivity(Intent(this, EventFeedActivity::class.java))
        }
        setupEdgeToEdge(padBottomSystem = listOf(binding.diagnosticsNestedScrollview))
        setupMaterialScrollListener(binding.diagnosticsNestedScrollview, binding.diagnosticsAppbar)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.diagnosticsAppbar, NavigationIcon.Arrow)
        sensorHelper.start()
        LiveCaptureSessionStore.setTimingEventListener { event ->
            runOnUiThread { onNewTimingEvent(event) }
        }
        refreshDisplayFromStore()
        statusRefreshHandler.post(statusRefreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        sensorHelper.stop()
        LiveCaptureSessionStore.setTimingEventListener(null)
        statusRefreshHandler.removeCallbacks(statusRefreshRunnable)
    }

    private fun setupGyroVisibility() {
        if (!sensorHelper.hasGyro) {
            binding.diagnosticsGyroSectionLabel.beGone()
            binding.diagnosticsGyroContainer.beGone()
        }
    }

    private fun setupOptionsMenu() {
        binding.diagnosticsToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.diagnostics_save_csv -> { saveAsCsv(); true }
                else -> false
            }
        }
    }

    private fun onNewTimingEvent(event: KeyTimingEvent) {
        // Detect IME-initiated session reset (keyboard re-opened)
        if (event.sessionId != displayedSessionId) {
            displayedSessionId = event.sessionId
            binding.diagnosticsEditText.text?.clear()
            resetMetricsDisplay()
        }

        val count = LiveCaptureSessionStore.getEventCount()
        binding.diagnosticsEventCountValue.text = count.toString()
        updateTimingDisplay(event.ikdMs, event.holdTimeMs, event.flightTimeMs)
        updateComputedMetrics(LiveCaptureSessionStore.getTimingEvents())
        updateViewLogCount(count)
        updateMenuItemStates()
    }

    private fun refreshDisplayFromStore() {
        val events = LiveCaptureSessionStore.getTimingEvents()
        displayedSessionId = LiveCaptureSessionStore.currentSessionId

        binding.diagnosticsEventCountValue.text = events.size.toString()
        updateStatusDisplay()

        if (events.isNotEmpty()) {
            val last = events.last()
            updateTimingDisplay(last.ikdMs, last.holdTimeMs, last.flightTimeMs)
            updateComputedMetrics(events)
        } else {
            resetMetricsDisplay()
        }

        updateViewLogCount(events.size)
        updateMenuItemStates()
    }

    private fun updateStatusDisplay() {
        val isCapturing = LiveCaptureSessionStore.isCapturing
        val hasEvents = LiveCaptureSessionStore.hasData()
        val statusText = when {
            isCapturing -> getString(R.string.diagnostics_capture_status_capturing)
            hasEvents -> getString(R.string.diagnostics_capture_status_stopped)
            else -> getString(R.string.diagnostics_capture_status_no_data)
        }
        binding.diagnosticsCaptureStatusValue.text = statusText
        binding.diagnosticsCaptureStatusValue.setTextColor(
            if (isCapturing) getProperPrimaryColor() else getProperTextColor()
        )
    }

    private fun resetMetricsDisplay() {
        binding.diagnosticsIkdValue.text = getString(R.string.diagnostics_value_none)
        binding.diagnosticsDwellValue.text = getString(R.string.diagnostics_value_none)
        binding.diagnosticsFlightValue.text = getString(R.string.diagnostics_value_none)
        binding.diagnosticsEventCountValue.text = "0"
        binding.diagnosticsTypingSpeedValue.text = getString(R.string.diagnostics_value_none)
        binding.diagnosticsErrorRateValue.text = getString(R.string.diagnostics_value_none)
    }

    private fun updateTimingDisplay(ikd: Long, dwell: Long, flight: Long) {
        binding.diagnosticsIkdValue.text = if (ikd >= 0) getString(R.string.diagnostics_ms_format, ikd) else getString(R.string.diagnostics_value_none)
        binding.diagnosticsDwellValue.text = if (dwell >= 0) getString(R.string.diagnostics_ms_format, dwell) else getString(R.string.diagnostics_value_none)
        binding.diagnosticsFlightValue.text = if (flight >= 0) getString(R.string.diagnostics_ms_format, flight) else getString(R.string.diagnostics_value_none)
    }

    private fun updateComputedMetrics(events: List<KeyTimingEvent>) {
        // Typing speed in keys per minute
        binding.diagnosticsTypingSpeedValue.text = if (events.size >= 2) {
            val durationMs = events.last().timestamp - events.first().timestamp
            if (durationMs > 0) {
                val kpm = (events.size * 60_000L / durationMs).toInt()
                getString(R.string.diagnostics_kpm_format, kpm)
            } else getString(R.string.diagnostics_value_none)
        } else getString(R.string.diagnostics_value_none)

        // Error rate as % of backspace events
        binding.diagnosticsErrorRateValue.text = if (events.isNotEmpty()) {
            val rate = events.count { it.isCorrection } * 100.0 / events.size
            getString(R.string.diagnostics_error_rate_format, rate)
        } else getString(R.string.diagnostics_value_none)
    }

    private fun updateViewLogCount(count: Int) {
        binding.diagnosticsViewLogCount.text = if (count > 0) {
            getString(R.string.diagnostics_view_log_count, count)
        } else {
            getString(R.string.diagnostics_value_none)
        }
    }

    private fun updateSensorDisplay(event: SensorReadingEvent) {
        when (event.sensorType) {
            "GYRO" -> {
                binding.diagnosticsGyroXBar.progress = toGyroProgress(event.x)
                binding.diagnosticsGyroYBar.progress = toGyroProgress(event.y)
                binding.diagnosticsGyroZBar.progress = toGyroProgress(event.z)
                binding.diagnosticsGyroXValue.text = "%.2f".format(event.x)
                binding.diagnosticsGyroYValue.text = "%.2f".format(event.y)
                binding.diagnosticsGyroZValue.text = "%.2f".format(event.z)
            }
            "ACCEL" -> {
                binding.diagnosticsAccelXBar.progress = toAccelProgress(event.x)
                binding.diagnosticsAccelYBar.progress = toAccelProgress(event.y)
                binding.diagnosticsAccelZBar.progress = toAccelProgress(event.z)
                binding.diagnosticsAccelXValue.text = "%.2f".format(event.x)
                binding.diagnosticsAccelYValue.text = "%.2f".format(event.y)
                binding.diagnosticsAccelZValue.text = "%.2f".format(event.z)
            }
        }
    }

    private fun saveAsCsv() {
        if (!LiveCaptureSessionStore.hasData()) {
            toast(R.string.diagnostics_no_events)
            return
        }
        val sessionId = LiveCaptureSessionStore.currentSessionId.take(8)
        saveCsvLauncher.launch("ikd_live_${sessionId}.csv")
    }

    private fun updateMenuItemStates() {
        binding.diagnosticsToolbar.menu.findItem(R.id.diagnostics_save_csv)
            ?.isEnabled = LiveCaptureSessionStore.hasData()
    }

    private fun toGyroProgress(v: Float): Int = ((v + 10f) / 20f * 100).toInt().coerceIn(0, 100)
    private fun toAccelProgress(v: Float): Int = (v / 20f * 100).toInt().coerceIn(0, 100)
}
