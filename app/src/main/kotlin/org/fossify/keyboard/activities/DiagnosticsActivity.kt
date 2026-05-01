package org.fossify.keyboard.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.keyboard.R
import org.fossify.keyboard.databinding.ActivityDiagnosticsBinding
import org.fossify.keyboard.helpers.KinematicSensorHelper
import org.fossify.keyboard.models.KeyTimingEvent
import org.fossify.keyboard.models.SensorReadingEvent
import java.util.UUID

class DiagnosticsActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityDiagnosticsBinding::inflate)

    private val sessionEvents = mutableListOf<KeyTimingEvent>()
    private val sensorReadings = mutableListOf<SensorReadingEvent>()
    private var currentSessionId: String = UUID.randomUUID().toString()

    private var pressDownTime = 0L
    private var lastReleaseTime = 0L

    private lateinit var sensorHelper: KinematicSensorHelper

    companion object {
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_PRESS_DOWN_TIME = "press_down_time"
        private const val KEY_LAST_RELEASE_TIME = "last_release_time"
        private const val KEY_EVENT_COUNT = "event_count"
    }

    private val saveCsvLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { stream ->
                stream.bufferedWriter().use { writer ->
                    writer.write("session_id,timestamp_ms,event_category,ikd_ms,hold_time_ms,flight_time_ms,is_correction\n")
                    for (event in sessionEvents) {
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

        savedInstanceState?.let { state ->
            currentSessionId = state.getString(KEY_SESSION_ID, currentSessionId)
            pressDownTime = state.getLong(KEY_PRESS_DOWN_TIME, 0L)
            lastReleaseTime = state.getLong(KEY_LAST_RELEASE_TIME, 0L)
            val savedCount = state.getInt(KEY_EVENT_COUNT, 0)
            binding.diagnosticsEventCountValue.text = savedCount.toString()
        }

        sensorHelper = KinematicSensorHelper(this, { currentSessionId }) { event ->
            sensorReadings.add(event)
            runOnUiThread { updateSensorDisplay(event) }
        }

        setupGyroVisibility()
        setupTouchListener()
        setupOptionsMenu()
        setupEdgeToEdge(padBottomSystem = listOf(binding.diagnosticsNestedScrollview))
        setupMaterialScrollListener(binding.diagnosticsNestedScrollview, binding.diagnosticsAppbar)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.diagnosticsAppbar, NavigationIcon.Arrow)
        sensorHelper.start()
    }

    override fun onPause() {
        super.onPause()
        sensorHelper.stop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SESSION_ID, currentSessionId)
        outState.putLong(KEY_PRESS_DOWN_TIME, pressDownTime)
        outState.putLong(KEY_LAST_RELEASE_TIME, lastReleaseTime)
        outState.putInt(KEY_EVENT_COUNT, sessionEvents.size)
    }

    private fun setupGyroVisibility() {
        if (!sensorHelper.hasGyro) {
            binding.diagnosticsGyroSectionLabel.beGone()
            binding.diagnosticsGyroContainer.beGone()
        }
    }

    private fun setupTouchListener() {
        binding.diagnosticsEditText.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    pressDownTime = SystemClock.uptimeMillis()
                }
                MotionEvent.ACTION_UP -> {
                    val now = SystemClock.uptimeMillis()
                    val dwell = now - pressDownTime
                    val flight = if (lastReleaseTime > 0L) pressDownTime - lastReleaseTime else -1L
                    val ikd = if (lastReleaseTime > 0L) now - lastReleaseTime else -1L
                    lastReleaseTime = now
                    recordEvent(ikd, dwell, flight)
                    updateTimingDisplay(ikd, dwell, flight)
                }
            }
            false
        }
    }

    private fun setupOptionsMenu() {
        binding.diagnosticsToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.diagnostics_new_session -> {
                    startNewSession()
                    true
                }
                R.id.diagnostics_save_csv -> {
                    saveAsCsv()
                    true
                }
                else -> false
            }
        }
    }

    private fun recordEvent(ikd: Long, dwell: Long, flight: Long) {
        val event = KeyTimingEvent(
            sessionId = currentSessionId,
            timestamp = SystemClock.uptimeMillis(),
            eventCategory = "TOUCH",  // Phase 1 diagnostics use touch events, not keyboard
            ikdMs = ikd,
            holdTimeMs = dwell,
            flightTimeMs = flight,
            isCorrection = false  // Phase 1 diagnostics don't track corrections
        )
        sessionEvents.add(event)
        binding.diagnosticsEventCountValue.text = sessionEvents.size.toString()
        updateSaveMenuItemState()
    }

    private fun updateTimingDisplay(ikd: Long, dwell: Long, flight: Long) {
        binding.diagnosticsIkdValue.text = if (ikd >= 0) getString(R.string.diagnostics_ms_format, ikd) else getString(R.string.diagnostics_value_none)
        binding.diagnosticsDwellValue.text = getString(R.string.diagnostics_ms_format, dwell)
        binding.diagnosticsFlightValue.text = if (flight >= 0) getString(R.string.diagnostics_ms_format, flight) else getString(R.string.diagnostics_value_none)
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

    private fun startNewSession() {
        currentSessionId = UUID.randomUUID().toString()
        sessionEvents.clear()
        sensorReadings.clear()
        pressDownTime = 0L
        lastReleaseTime = 0L
        binding.diagnosticsEditText.text?.clear()
        binding.diagnosticsIkdValue.text = getString(R.string.diagnostics_value_none)
        binding.diagnosticsDwellValue.text = getString(R.string.diagnostics_value_none)
        binding.diagnosticsFlightValue.text = getString(R.string.diagnostics_value_none)
        binding.diagnosticsEventCountValue.text = "0"
        updateSaveMenuItemState()
    }

    private fun saveAsCsv() {
        if (sessionEvents.isEmpty()) {
            toast(R.string.diagnostics_no_events)
            return
        }
        val filename = "ikd_session_${currentSessionId.take(8)}.csv"
        saveCsvLauncher.launch(filename)
    }

    private fun updateSaveMenuItemState() {
        val saveItem = binding.diagnosticsToolbar.menu.findItem(R.id.diagnostics_save_csv)
        saveItem?.isEnabled = sessionEvents.isNotEmpty()
    }

    private fun toGyroProgress(v: Float): Int = ((v + 10f) / 20f * 100).toInt().coerceIn(0, 100)
    private fun toAccelProgress(v: Float): Int = (v / 20f * 100).toInt().coerceIn(0, 100)
}
