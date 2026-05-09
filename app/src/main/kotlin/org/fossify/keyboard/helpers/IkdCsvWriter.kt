package org.fossify.keyboard.helpers

import android.content.Context
import android.net.Uri
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.keyboard.extensions.ikdDB
import org.fossify.keyboard.helpers.IkdCsvWriter.asMoodRow
import org.fossify.keyboard.helpers.IkdCsvWriter.asSensorRow
import org.fossify.keyboard.helpers.IkdCsvWriter.asTimingRow
import org.fossify.keyboard.models.IkdEvent
import org.fossify.keyboard.models.KeyTimingEvent
import org.fossify.keyboard.models.MoodEntry
import org.fossify.keyboard.models.SensorReadingEvent
import org.fossify.keyboard.models.SensorSample
import java.io.BufferedWriter
import java.io.IOException

/**
 * Single source of truth for the triple-block IKD CSV format.
 *
 * Format:
 * ```
 * session_id,timestamp_ms,event_category,ikd_ms,hold_time_ms,flight_time_ms,is_correction,correction_weight
 * <timing rows>
 *
 * #sensor_readings
 * session_id,timestamp_ms,sensor_type,x,y,z
 * <sensor rows>
 *
 * #mood_entries
 * session_id,timestamp_ms,mood_score
 * <mood rows>
 * ```
 *
 * Used by:
 * - Phase 1.1 single-session live export (DiagnosticsActivity) — emits the
 *   mood block with no rows when no mood is recorded yet.
 * - Phase 2 per-session export from sessions browser (SessionsListActivity)
 * - Phase 2 bulk "export all" (Step 13)
 * - Phase 8 mood entries (third block, strictly additive)
 *
 * Output is byte-identical for the same input regardless of source
 * (in-memory store or Room). Parsers reading only the first two blocks
 * remain backwards-compatible — the `#mood_entries` header is treated as
 * trailing content by the existing parser.
 *
 * Phase 7.1: timing block gains an eighth `correction_weight` column.
 * Strictly additive — parsers reading the original seven columns ignore
 * the eighth.
 */
object IkdCsvWriter {

    fun writeSessionCsv(
        writer: BufferedWriter,
        timingRows: List<TimingRow>,
        sensorRows: List<SensorRow>,
        moodRows: List<MoodRow> = emptyList(),
    ) {
        writer.write(TIMING_HEADER)
        for (row in timingRows) {
            writer.write(
                "${row.sessionId},${row.timestamp},${row.eventCategory}," +
                    "${row.ikdMs},${row.holdTimeMs},${row.flightTimeMs}," +
                    "${row.isCorrection},${row.correctionWeight}\n"
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
        // Phase 8: third dual-block segment for mood annotations. The block
        // header + column header are always emitted (even with zero rows)
        // so downstream parsers can rely on a stable column shape.
        writer.write("\n#mood_entries\n")
        writer.write(MOOD_HEADER)
        for (row in moodRows) {
            writer.write("${row.sessionId ?: ""},${row.timestamp},${row.moodScore}\n")
        }
    }

    private const val TIMING_HEADER =
        "session_id,timestamp_ms,event_category,ikd_ms,hold_time_ms,flight_time_ms," +
            "is_correction,correction_weight\n"
    private const val SENSOR_HEADER = "session_id,timestamp_ms,sensor_type,x,y,z\n"
    private const val MOOD_HEADER = "session_id,timestamp_ms,mood_score\n"

    data class TimingRow(
        val sessionId: String,
        val timestamp: Long,
        val eventCategory: String,
        val ikdMs: Long,
        val holdTimeMs: Long,
        val flightTimeMs: Long,
        val isCorrection: Boolean,
        val correctionWeight: Int,
    )

    data class SensorRow(
        val sessionId: String,
        val timestamp: Long,
        val sensorType: String,
        val x: Float,
        val y: Float,
        val z: Float
    )

    /**
     * Phase 8: per-mood CSV row. `sessionId` is nullable to mirror the DB —
     * the keyboard always writes a non-null value, but the writer honours
     * the schema's permissiveness for forward-compat.
     */
    data class MoodRow(
        val sessionId: String?,
        val timestamp: Long,
        val moodScore: Int,
    )

    fun MoodEntry.asMoodRow() = MoodRow(
        sessionId = sessionId,
        timestamp = timestamp,
        moodScore = moodScore,
    )

    fun KeyTimingEvent.asTimingRow() = TimingRow(
        sessionId = sessionId,
        timestamp = timestamp,
        eventCategory = eventCategory,
        ikdMs = ikdMs,
        holdTimeMs = holdTimeMs,
        flightTimeMs = flightTimeMs,
        isCorrection = isCorrection,
        correctionWeight = correctionWeight,
    )

    fun IkdEvent.asTimingRow() = TimingRow(
        sessionId = sessionId,
        timestamp = timestamp,
        eventCategory = eventCategory,
        ikdMs = ikdMs,
        holdTimeMs = holdTimeMs,
        flightTimeMs = flightTimeMs,
        isCorrection = isCorrection,
        correctionWeight = correctionWeight,
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

/**
 * Streams all sessions' timing rows + sensor rows to the given SAF [uri] as a single mega-CSV
 * in the same dual-block format used by per-session export. Runs on a background thread.
 *
 * Callers should invoke [onSuccess] / [onError] callbacks themselves on the UI thread
 * (typically via `runOnUiThread { toast(...) }`) — this helper invokes the callbacks from
 * the background thread.
 */
fun Context.exportAllIkdSessions(
    uri: Uri,
    onSuccess: () -> Unit,
    onError: () -> Unit,
) {
    ensureBackgroundThread {
        try {
            val timingRows = ikdDB.IkdEventDao().getAllOrderedBySession().map { it.asTimingRow() }
            val sensorRows = ikdDB.SensorSampleDao().getAllOrderedBySession().map { it.asSensorRow() }
            val moodRows = ikdDB.MoodDao().getAllOrderedBySession().map { it.asMoodRow() }
            contentResolver.openOutputStream(uri)?.use { stream ->
                stream.bufferedWriter().use { writer ->
                    IkdCsvWriter.writeSessionCsv(writer, timingRows, sensorRows, moodRows)
                }
            }
            onSuccess()
        } catch (e: IOException) {
            e.printStackTrace()
            onError()
        } catch (e: SecurityException) {
            e.printStackTrace()
            onError()
        }
    }
}
