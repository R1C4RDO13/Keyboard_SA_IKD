package org.fossify.keyboard.helpers

import org.fossify.keyboard.models.KeyTimingEvent
import org.fossify.keyboard.models.SensorReadingEvent
import java.util.UUID
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Phase 1.1: In-memory store for live keyboard capture validation sessions.
 * 
 * This singleton holds the most recent IME typing session's data for developer
 * review and export. Data is ephemeral and cleared when a new session starts.
 * 
 * Thread-safe for concurrent IME callbacks and UI access.
 */
object LiveCaptureSessionStore {
    private val lock = ReentrantReadWriteLock()
    private val timingEvents = mutableListOf<KeyTimingEvent>()
    private val sensorReadings = mutableListOf<SensorReadingEvent>()
    
    var currentSessionId: String = ""
        private set
    
    var isCapturing: Boolean = false
        private set
    
    /**
     * Start a new live capture session. Clears previous session data.
     */
    fun startSession(): String = lock.write {
        currentSessionId = UUID.randomUUID().toString()
        timingEvents.clear()
        sensorReadings.clear()
        isCapturing = true
        return currentSessionId
    }
    
    /**
     * Stop the current live capture session. Data remains available for review.
     */
    fun stopSession() = lock.write {
        isCapturing = false
    }
    
    /**
     * Record a key timing event from the IME.
     */
    fun recordTimingEvent(event: KeyTimingEvent) = lock.write {
        if (isCapturing && event.sessionId == currentSessionId) {
            timingEvents.add(event)
        }
    }
    
    /**
     * Record a sensor reading from the IME.
     */
    fun recordSensorReading(reading: SensorReadingEvent) = lock.write {
        if (isCapturing && reading.sessionId == currentSessionId) {
            sensorReadings.add(reading)
        }
    }
    
    /**
     * Get a snapshot of the current session's timing events.
     */
    fun getTimingEvents(): List<KeyTimingEvent> = lock.read {
        timingEvents.toList()
    }
    
    /**
     * Get a snapshot of the current session's sensor readings.
     */
    fun getSensorReadings(): List<SensorReadingEvent> = lock.read {
        sensorReadings.toList()
    }
    
    /**
     * Get the count of events in the current session.
     */
    fun getEventCount(): Int = lock.read {
        timingEvents.size
    }
    
    /**
     * Check if there is data available for review/export.
     */
    fun hasData(): Boolean = lock.read {
        timingEvents.isNotEmpty()
    }
    
    /**
     * Clear all session data (for testing or explicit reset).
     */
    fun clear() = lock.write {
        currentSessionId = ""
        timingEvents.clear()
        sensorReadings.clear()
        isCapturing = false
    }
}
