package org.fossify.keyboard.helpers

import android.content.Context
import android.util.Log
import org.fossify.keyboard.extensions.ikdDB
import org.fossify.keyboard.models.IkdEvent
import org.fossify.keyboard.models.KeyTimingEvent
import org.fossify.keyboard.models.SensorReadingEvent
import org.fossify.keyboard.models.SensorSample
import org.fossify.keyboard.models.SessionRecord
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

fun interface OnTimingEventListener {
    fun onTimingEventRecorded(event: KeyTimingEvent)
}

@Suppress("TooManyFunctions", "TooGenericExceptionCaught")
object LiveCaptureSessionStore {
    private const val TAG = "LiveCaptureSessionStore"
    private const val FLUSH_INTERVAL_MS = 500L
    private const val FLUSH_THRESHOLD = 100

    private val lock = ReentrantReadWriteLock()
    private val timingEvents = mutableListOf<KeyTimingEvent>()
    private val sensorReadings = mutableListOf<SensorReadingEvent>()

    // Staging buffers — drained by the flusher, separate from live in-memory lists.
    private val pendingTimingEvents = mutableListOf<KeyTimingEvent>()
    private val pendingSensorReadings = mutableListOf<SensorReadingEvent>()

    @Volatile private var timingEventListener: OnTimingEventListener? = null

    fun setTimingEventListener(listener: OnTimingEventListener?) {
        timingEventListener = listener
    }

    var currentSessionId: String = ""
        private set

    var isCapturing: Boolean = false
        private set

    // Persistence init — call once from App.onCreate()
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // Background flusher — single thread, never blocks the IME.
    private val flushExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var flushTask: ScheduledFuture<*>? = null

    /**
     * Start a new live capture session. Clears previous session data and
     * persists a new SessionRecord asynchronously.
     */
    fun startSession(orientation: Int, locale: String): String {
        val newSessionId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()

        lock.write {
            currentSessionId = newSessionId
            timingEvents.clear()
            sensorReadings.clear()
            pendingTimingEvents.clear()
            pendingSensorReadings.clear()
            isCapturing = true
        }

        // Insert SessionRecord off the IME thread.
        flushExecutor.submit {
            try {
                appContext.ikdDB.SessionDao().insertSession(
                    SessionRecord(
                        sessionId = newSessionId,
                        startedAt = startedAt,
                        endedAt = null,
                        eventCount = 0,
                        sensorCount = 0,
                        deviceOrientation = orientation,
                        locale = locale,
                    )
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to insert SessionRecord for $newSessionId", t)
            }
        }

        flushTask = flushExecutor.scheduleAtFixedRate(
            ::flushPending, FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, MILLISECONDS
        )

        return newSessionId
    }

    /**
     * Stop the current live capture session. Drains the pending buffer and
     * finalizes the SessionRecord (endedAt + counts) asynchronously.
     * Data remains available for review.
     */
    fun stopSession() {
        val finishedSessionId: String
        val finalEventCount: Int
        val finalSensorCount: Int

        lock.write {
            // Don't interrupt — let the final scheduled run finish if mid-flight.
            flushTask?.cancel(false)
            flushTask = null
            finishedSessionId = currentSessionId
            finalEventCount = timingEvents.size
            finalSensorCount = sensorReadings.size
            isCapturing = false
        }

        if (finishedSessionId.isEmpty()) {
            return
        }

        flushExecutor.submit {
            try {
                flushPending()
                val dao = appContext.ikdDB.SessionDao()
                val existing = dao.getSession(finishedSessionId)
                if (existing != null) {
                    existing.endedAt = System.currentTimeMillis()
                    existing.eventCount = finalEventCount
                    existing.sensorCount = finalSensorCount
                    dao.updateSession(existing)
                } else {
                    Log.e(TAG, "SessionRecord missing for $finishedSessionId at stop")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to finalize SessionRecord for $finishedSessionId", t)
            }
        }
    }

    /**
     * Record a key timing event from the IME.
     */
    fun recordTimingEvent(event: KeyTimingEvent) {
        var listener: OnTimingEventListener? = null
        var pendingSize = 0
        lock.write {
            if (isCapturing && event.sessionId == currentSessionId) {
                timingEvents.add(event)
                pendingTimingEvents.add(event)
                pendingSize = pendingTimingEvents.size
                listener = timingEventListener
            }
        }
        listener?.onTimingEventRecorded(event)
        if (pendingSize >= FLUSH_THRESHOLD) {
            flushExecutor.submit(::flushPending)
        }
    }

    /**
     * Record a sensor reading from the IME.
     */
    fun recordSensorReading(reading: SensorReadingEvent) {
        var pendingSize = 0
        lock.write {
            if (isCapturing && reading.sessionId == currentSessionId) {
                sensorReadings.add(reading)
                pendingSensorReadings.add(reading)
                pendingSize = pendingSensorReadings.size
            }
        }
        if (pendingSize >= FLUSH_THRESHOLD) {
            flushExecutor.submit(::flushPending)
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
        pendingTimingEvents.clear()
        pendingSensorReadings.clear()
        isCapturing = false
    }

    /**
     * Drain the pending buffers and write batches to Room.
     * MUST run on flushExecutor only — never call synchronously from a user-facing thread.
     */
    private fun flushPending() {
        try {
            val (eventsToFlush, samplesToFlush) = lock.write {
                val e = pendingTimingEvents.toList()
                val s = pendingSensorReadings.toList()
                pendingTimingEvents.clear()
                pendingSensorReadings.clear()
                e to s
            }
            if (eventsToFlush.isNotEmpty()) {
                val ikdEvents = eventsToFlush.map { ev ->
                    IkdEvent(
                        id = null,
                        sessionId = ev.sessionId,
                        timestamp = ev.timestamp,
                        eventCategory = ev.eventCategory,
                        ikdMs = ev.ikdMs,
                        holdTimeMs = ev.holdTimeMs,
                        flightTimeMs = ev.flightTimeMs,
                        isCorrection = ev.isCorrection,
                    )
                }
                appContext.ikdDB.IkdEventDao().insertAll(ikdEvents)
            }
            if (samplesToFlush.isNotEmpty()) {
                val sensorSamples = samplesToFlush.map { s ->
                    SensorSample(
                        id = null,
                        sessionId = s.sessionId,
                        timestamp = s.timestamp,
                        sensorType = s.sensorType,
                        x = s.x,
                        y = s.y,
                        z = s.z,
                    )
                }
                appContext.ikdDB.SensorSampleDao().insertAll(sensorSamples)
            }
        } catch (t: Throwable) {
            // Swallow to keep the scheduled executor alive.
            Log.e(TAG, "flushPending failed", t)
        }
    }
}
