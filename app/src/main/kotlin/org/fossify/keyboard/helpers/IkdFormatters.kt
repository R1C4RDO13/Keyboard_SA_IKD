package org.fossify.keyboard.helpers

import android.content.Context
import org.fossify.keyboard.R

/**
 * Small, allocation-free formatters shared across IKD UI surfaces. Lifted
 * here so the sessions list summary and the session detail header card
 * agree on duration / "—" rendering without any duplication.
 */
object IkdFormatters {

    private const val MS_PER_SECOND = 1000L
    private const val SECONDS_PER_MINUTE = 60L

    // Orientation enum values stored in `SessionRecord.deviceOrientation`,
    // mirroring `android.view.Surface.ROTATION_*`. The sentinel `-1` means
    // "capture disabled" and falls through to the placeholder.
    private const val ORIENTATION_PORTRAIT = 0
    private const val ORIENTATION_LANDSCAPE = 1
    private const val ORIENTATION_REVERSE_PORTRAIT = 2
    private const val ORIENTATION_REVERSE_LANDSCAPE = 3

    /**
     * Formats a duration as `Xm Ys` (or `Ys` when minutes are zero). Used by
     * both [org.fossify.keyboard.adapters.SessionsAdapter] and the session
     * detail header card so that "1m 5s" looks identical in both places.
     */
    fun formatDuration(ms: Long): String {
        val totalSeconds = ms / MS_PER_SECOND
        val minutes = totalSeconds / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
    }

    /**
     * Resolves the human-readable label for a stored orientation int. Returns
     * the placeholder dash for the `-1` sentinel ("capture disabled") or any
     * unrecognised value.
     */
    fun orientationLabel(context: Context, orientation: Int): String = when (orientation) {
        ORIENTATION_PORTRAIT -> context.getString(R.string.session_detail_orientation_portrait)
        ORIENTATION_LANDSCAPE -> context.getString(R.string.session_detail_orientation_landscape)
        ORIENTATION_REVERSE_PORTRAIT -> context.getString(R.string.session_detail_orientation_reverse_portrait)
        ORIENTATION_REVERSE_LANDSCAPE -> context.getString(R.string.session_detail_orientation_reverse_landscape)
        else -> context.getString(R.string.session_detail_value_placeholder)
    }
}
