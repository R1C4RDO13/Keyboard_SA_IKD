package org.fossify.keyboard.activities.dashboard

import android.content.Context
import org.fossify.keyboard.R
import org.fossify.keyboard.helpers.IkdAggregator
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Phase 9.11: shared bucket-label formatter used by every tab fragment so
 * the X axis on Trends, Mood, and Habits agrees on how each `Range`'s
 * raw `strftime` keys (`%Y-%m-%d`, `%Y-%W`, `%Y-%m-%d %H`) are rendered.
 *
 * Falls back to the raw key on parse failure — no crash, just less
 * prettiness.
 */
object DashboardLabelFormat {

    private val isoDayParser = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun formatBucketLabel(context: Context, rawKey: String, range: IkdAggregator.Range): String {
        return when (range) {
            // Phase 9.11: TODAY emits "%Y-%m-%d %H" (e.g. "2026-05-10 09");
            // render the trailing hour as "HH:00" to match the 24-hour bar
            // axis. Falls back to the raw key when parsing fails.
            IkdAggregator.Range.TODAY -> {
                val hourPart = rawKey.substringAfterLast(' ', missingDelimiterValue = rawKey)
                val hourInt = hourPart.toIntOrNull()
                if (hourInt != null) {
                    context.getString(R.string.dashboard_hour_bucket_label, hourInt)
                } else {
                    rawKey
                }
            }

            IkdAggregator.Range.WEEK,
            IkdAggregator.Range.MONTH -> runCatching {
                val date = isoDayParser.parse(rawKey) ?: return@runCatching rawKey
                SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
            }.getOrDefault(rawKey)

            IkdAggregator.Range.ALL_TIME -> {
                // SQLite emits `%Y-%W` as e.g. "2026-18". Strip the year for tighter labels.
                rawKey.substringAfter('-', missingDelimiterValue = rawKey)
                    .let { week -> "W$week" }
            }
        }
    }
}
