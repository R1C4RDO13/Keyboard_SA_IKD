package org.fossify.keyboard.helpers

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.keyboard.BuildConfig
import org.fossify.keyboard.databases.IkdDatabase
import org.fossify.keyboard.models.MoodEntry
import kotlin.system.measureTimeMillis

/**
 * Phase 8: per-session mood loader. Sibling of [IkdSessionStatsLoader] —
 * kept separate so that the Phase 4 stats surface stays unchanged.
 *
 * Threading: [load] runs entirely on `Dispatchers.IO`. Callers may invoke
 * it from any coroutine context (typically alongside the existing stats
 * + chart loaders inside `EventFeedActivity.loadDataFromDb` so all three
 * Room reads happen on the same single hop).
 *
 * The pure derivation `Companion.compute` is just a passthrough today,
 * but follows the established pattern (also see `IkdSessionChartLoader`)
 * so Phase 9-style extensions (e.g. mood-history-per-session if Decision
 * #3 is ever revisited) get a JVM-testable seam.
 */
class IkdMoodLoader(private val db: IkdDatabase) {

    suspend fun load(sessionId: String): MoodEntry? = withContext(Dispatchers.IO) {
        var result: MoodEntry? = null
        val durationMs = measureTimeMillis {
            result = Companion.compute(db.MoodDao().getForSession(sessionId))
        }
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "load(${sessionId.take(LOG_ID_LEN)}) took ${durationMs}ms")
        }
        result
    }

    companion object {
        private const val LOG_TAG = "IkdMoodLoader"
        private const val LOG_ID_LEN = 8

        /**
         * Pure derivation. Kept on the Companion for symmetry with
         * `IkdSessionStatsLoader.Companion.compute` — today this is a
         * passthrough; Phase 9 / Decision-#3 revisits could add a lookup
         * by timestamp, label translation, or fallback policy without
         * standing up a Room DB to test.
         */
        internal fun compute(entry: MoodEntry?): MoodEntry? = entry
    }
}
