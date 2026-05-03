package org.fossify.keyboard.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.keyboard.R
import org.fossify.keyboard.databinding.ActivityDiagnosticsBinding
import org.fossify.keyboard.extensions.config
import org.fossify.keyboard.helpers.IkdCsvWriter
import org.fossify.keyboard.helpers.IkdCsvWriter.asSensorRow
import org.fossify.keyboard.helpers.IkdCsvWriter.asTimingRow
import org.fossify.keyboard.helpers.KinematicSensorHelper
import org.fossify.keyboard.helpers.LiveCaptureSessionStore
import org.fossify.keyboard.helpers.SENSOR_DISPLAY_MODE_AXES
import org.fossify.keyboard.helpers.SENSOR_DISPLAY_MODE_MAGNITUDE
import org.fossify.keyboard.models.KeyTimingEvent
import org.fossify.keyboard.models.SensorReadingEvent
import org.fossify.keyboard.models.magnitude

private const val GYRO_MAGNITUDE_RANGE = 10f
private const val ACCEL_MAGNITUDE_RANGE = 20f
private const val PERCENT_MAX = 100

// Magnitude bars are one-sided (always >= 0). Range stays 0–10 rad/s for gyro
// and 0–20 m/s² for accel — same upper bound as the per-axis mapping.
private fun gyroMagnitudeProgress(mag: Float): Int =
    (mag / GYRO_MAGNITUDE_RANGE * PERCENT_MAX).toInt().coerceIn(0, PERCENT_MAX)
private fun accelMagnitudeProgress(mag: Float): Int =
    (mag / ACCEL_MAGNITUDE_RANGE * PERCENT_MAX).toInt().coerceIn(0, PERCENT_MAX)

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
            contentResolver.openOutputStream(uri)?.use { stream ->
                stream.bufferedWriter().use { writer ->
                    IkdCsvWriter.writeSessionCsv(
                        writer,
                        LiveCaptureSessionStore.getTimingEvents().map { it.asTimingRow() },
                        LiveCaptureSessionStore.getSensorReadings().map { it.asSensorRow() },
                    )
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
        applyThemeColors()
        applySensorDisplayMode(config.sensorDisplayMode)
        sensorHelper.start()
        LiveCaptureSessionStore.setTimingEventListener { event ->
            runOnUiThread { onNewTimingEvent(event) }
        }
        refreshDisplayFromStore()
        statusRefreshHandler.post(statusRefreshRunnable)
    }

    private fun applyThemeColors() {
        updateTextColors(binding.diagnosticsNestedScrollview)
        val primary = getProperPrimaryColor()
        binding.diagnosticsTimingSectionLabel.setTextColor(primary)
        binding.diagnosticsGyroSectionLabel.setTextColor(primary)
        binding.diagnosticsAccelSectionLabel.setTextColor(primary)
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
                R.id.diagnostics_toggle_sensor_view -> {
                    val next = if (config.sensorDisplayMode == SENSOR_DISPLAY_MODE_MAGNITUDE) {
                        SENSOR_DISPLAY_MODE_AXES
                    } else {
                        SENSOR_DISPLAY_MODE_MAGNITUDE
                    }
                    config.sensorDisplayMode = next
                    applySensorDisplayMode(next)
                    true
                }
                else -> false
            }
        }
    }

    private fun applySensorDisplayMode(mode: String) {
        val isMagnitude = mode == SENSOR_DISPLAY_MODE_MAGNITUDE
        val axesVis = if (isMagnitude) View.GONE else View.VISIBLE
        val magVis = if (isMagnitude) View.VISIBLE else View.GONE
        binding.diagnosticsGyroAxesContainer.visibility = axesVis
        binding.diagnosticsGyroMagnitudeContainer.visibility = magVis
        binding.diagnosticsAccelAxesContainer.visibility = axesVis
        binding.diagnosticsAccelMagnitudeContainer.visibility = magVis

        val toggle = binding.diagnosticsToolbar.menu.findItem(R.id.diagnostics_toggle_sensor_view)
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
        // Always update both per-axis values and the magnitude — the visibility
        // flip handles which is shown. Cheap (~1 sqrt + 6 setText per ~50Hz tick).
        val mag = event.magnitude()
        when (event.sensorType) {
            "GYRO" -> {
                binding.diagnosticsGyroXBar.progress = toGyroProgress(event.x)
                binding.diagnosticsGyroYBar.progress = toGyroProgress(event.y)
                binding.diagnosticsGyroZBar.progress = toGyroProgress(event.z)
                binding.diagnosticsGyroXValue.text = "%.2f".format(event.x)
                binding.diagnosticsGyroYValue.text = "%.2f".format(event.y)
                binding.diagnosticsGyroZValue.text = "%.2f".format(event.z)
                binding.diagnosticsGyroMagnitudeBar.progress = gyroMagnitudeProgress(mag)
                binding.diagnosticsGyroMagnitudeValue.text = "%.2f".format(mag)
            }
            "ACCEL" -> {
                binding.diagnosticsAccelXBar.progress = toAccelProgress(event.x)
                binding.diagnosticsAccelYBar.progress = toAccelProgress(event.y)
                binding.diagnosticsAccelZBar.progress = toAccelProgress(event.z)
                binding.diagnosticsAccelXValue.text = "%.2f".format(event.x)
                binding.diagnosticsAccelYValue.text = "%.2f".format(event.y)
                binding.diagnosticsAccelZValue.text = "%.2f".format(event.z)
                binding.diagnosticsAccelMagnitudeBar.progress = accelMagnitudeProgress(mag)
                binding.diagnosticsAccelMagnitudeValue.text = "%.2f".format(mag)
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
