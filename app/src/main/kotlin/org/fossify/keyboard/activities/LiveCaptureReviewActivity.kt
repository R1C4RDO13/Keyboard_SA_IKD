package org.fossify.keyboard.activities

import android.os.Bundle
import android.view.Menu
import androidx.activity.result.contract.ActivityResultContracts
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.keyboard.R
import org.fossify.keyboard.databinding.ActivityLiveCaptureReviewBinding
import org.fossify.keyboard.helpers.LiveCaptureSessionStore

/**
 * Phase 1.1: Developer review and export screen for live keyboard capture sessions.
 * 
 * This activity displays the latest IME typing session data captured by SimpleKeyboardIME
 * and allows exporting it to CSV format for validation and analysis.
 */
class LiveCaptureReviewActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityLiveCaptureReviewBinding::inflate)
    
    private val saveCsvLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        
        try {
            val timingEvents = LiveCaptureSessionStore.getTimingEvents()
            val sensorReadings = LiveCaptureSessionStore.getSensorReadings()
            
            contentResolver.openOutputStream(uri)?.use { stream ->
                stream.bufferedWriter().use { writer ->
                    // Phase 1.1 CSV format with corrected metrics
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
            toast(R.string.live_capture_export_success)
        } catch (e: Exception) {
            toast(R.string.live_capture_export_error)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        
        setupOptionsMenu()
        updateUI()
        
        setupEdgeToEdge(padBottomSystem = listOf(binding.liveCaptureNestedScrollview))
        setupMaterialScrollListener(binding.liveCaptureNestedScrollview, binding.liveCaptureAppbar)
    }
    
    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.liveCaptureAppbar, NavigationIcon.Arrow)
        updateUI()
    }
    
    private fun setupOptionsMenu() {
        binding.liveCaptureToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.live_capture_start_session -> {
                    startNewSession()
                    true
                }
                R.id.live_capture_stop_session -> {
                    stopSession()
                    true
                }
                R.id.live_capture_export_csv -> {
                    exportToCsv()
                    true
                }
                else -> false
            }
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_live_capture, menu)
        updateMenuItemStates(menu)
        return true
    }
    
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        updateMenuItemStates(menu)
        return super.onPrepareOptionsMenu(menu)
    }
    
    private fun updateMenuItemStates(menu: Menu) {
        val isCapturing = LiveCaptureSessionStore.isCapturing
        val hasData = LiveCaptureSessionStore.hasData()
        
        menu.findItem(R.id.live_capture_start_session)?.isEnabled = !isCapturing
        menu.findItem(R.id.live_capture_stop_session)?.isEnabled = isCapturing
        menu.findItem(R.id.live_capture_export_csv)?.isEnabled = hasData
    }
    
    private fun updateUI() {
        val isCapturing = LiveCaptureSessionStore.isCapturing
        val eventCount = LiveCaptureSessionStore.getEventCount()
        val sessionId = LiveCaptureSessionStore.currentSessionId
        
        // Update status
        if (isCapturing) {
            binding.liveCaptureStatusValue.text = getString(R.string.live_capture_status_active)
            binding.liveCaptureStatusValue.setTextColor(getProperPrimaryColor())
        } else if (eventCount > 0) {
            binding.liveCaptureStatusValue.text = getString(R.string.live_capture_status_stopped)
            binding.liveCaptureStatusValue.setTextColor(getProperTextColor())
        } else {
            binding.liveCaptureStatusValue.text = getString(R.string.live_capture_status_no_data)
            binding.liveCaptureStatusValue.setTextColor(getProperTextColor())
        }
        
        // Update event count
        binding.liveCaptureEventCountValue.text = eventCount.toString()
        
        // Update session ID (show first 8 chars for brevity)
        binding.liveCaptureSessionIdValue.text = if (sessionId.isNotEmpty()) {
            sessionId.take(8)
        } else {
            getString(R.string.live_capture_no_session)
        }
        
        // Show/hide instructions
        if (eventCount == 0 && !isCapturing) {
            binding.liveCaptureInstructions.beVisible()
            binding.liveCaptureDataPreview.beGone()
        } else {
            binding.liveCaptureInstructions.beGone()
            binding.liveCaptureDataPreview.beVisible()
        }
        
        // Update menu item states
        invalidateOptionsMenu()
    }
    
    private fun startNewSession() {
        LiveCaptureSessionStore.startSession()
        toast(R.string.live_capture_session_started)
        updateUI()
    }
    
    private fun stopSession() {
        LiveCaptureSessionStore.stopSession()
        toast(R.string.live_capture_session_stopped)
        updateUI()
    }
    
    private fun exportToCsv() {
        if (!LiveCaptureSessionStore.hasData()) {
            toast(R.string.live_capture_no_data_to_export)
            return
        }
        
        val sessionId = LiveCaptureSessionStore.currentSessionId.take(8)
        val filename = "ikd_live_${sessionId}.csv"
        saveCsvLauncher.launch(filename)
    }
}
