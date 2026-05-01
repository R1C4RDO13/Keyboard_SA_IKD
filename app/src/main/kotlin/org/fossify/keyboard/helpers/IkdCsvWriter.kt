package org.fossify.keyboard.helpers

import org.fossify.keyboard.models.IkdEvent
import org.fossify.keyboard.models.KeyTimingEvent
import org.fossify.keyboard.models.SensorReadingEvent
import org.fossify.keyboard.models.SensorSample
import java.io.BufferedWriter

/**
 * Single source of truth for the dual-block IKD CSV format.
 *
 * Format:
 * ```
 * session_id,timestamp_ms,event_category,ikd_ms,hold_time_ms,flight_time_ms,is_correction
 * <timing rows>
 *
 * #sensor_readings
 * session_id,timestamp_ms,sensor_type,x,y,z
 * <sensor rows>
 * ```
 *
 * Used by:
 * - Phase 1.1 single-session live export (DiagnosticsActivity)
 * - Phase 2 per-session export from sessions browser (SessionsListActivity)
 * - Phase 2 bulk "export all" (Step 13)
 *
 * Output is byte-identical for the same input regardless of source (in-memory store or Room).
 */
object IkdCsvWriter {

    fun writeSessionCsv(
        writer: BufferedWriter,
        timingRows: List<TimingRow>,
        sensorRows: List<SensorRow>,
    ) {
        writer.write(TIMING_HEADER)
        for (row in timingRows) {
            writer.write(
                "${row.sessionId},${row.timestamp},${row.eventCategory}," +
                    "${row.ikdMs},${row.holdTimeMs},${row.flightTimeMs},${row.isCorrection}\n"
            )
        }
        writer.write("\n#sensor_readings\n")
        writer.write(SENSOR_HEADER)
        for (row in sensorRows) {
            writer.write(
                "${row.sessionId},${row.timestamp},${row.sensorType}," +
                    "${row.x},${row.y},${row.z}\n"
            )
        }
    }

    private const val TIMING_HEADER =
        "session_id,timestamp_ms,event_category,ikd_ms,hold_time_ms,flight_time_ms,is_correction\n"
    private const val SENSOR_HEADER = "session_id,timestamp_ms,sensor_type,x,y,z\n"

    data class TimingRow(
        val sessionId: String,
        val timestamp: Long,
        val eventCategory: String,
        val ikdMs: Long,
        val holdTimeMs: Long,
        val flightTimeMs: Long,
        val isCorrection: Boolean
    )

    data class SensorRow(
        val sessionId: String,
        val timestamp: Long,
        val sensorType: String,
        val x: Float,
        val y: Float,
        val z: Float
    )

    fun KeyTimingEvent.asTimingRow() = TimingRow(
        sessionId = sessionId,
        timestamp = timestamp,
        eventCategory = eventCategory,
        ikdMs = ikdMs,
        holdTimeMs = holdTimeMs,
        flightTimeMs = flightTimeMs,
        isCorrection = isCorrection,
    )

    fun IkdEvent.asTimingRow() = TimingRow(
        sessionId = sessionId,
        timestamp = timestamp,
        eventCategory = eventCategory,
        ikdMs = ikdMs,
        holdTimeMs = holdTimeMs,
        flightTimeMs = flightTimeMs,
        isCorrection = isCorrection,
    )

    fun SensorReadingEvent.asSensorRow() = SensorRow(
        sessionId = sessionId,
        timestamp = timestamp,
        sensorType = sensorType,
        x = x,
        y = y,
        z = z,
    )

    fun SensorSample.asSensorRow() = SensorRow(
        sessionId = sessionId,
        timestamp = timestamp,
        sensorType = sensorType,
        x = x,
        y = y,
        z = z,
    )
}
