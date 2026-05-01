package org.fossify.keyboard.helpers

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.keyboard.extensions.config
import org.fossify.keyboard.extensions.ikdDB

/**
 * Daily background worker that deletes IKD sessions older than the user-configured retention window.
 *
 * Behaviour:
 *  - If [Config.retentionDays] equals [RETENTION_FOREVER] the worker is a no-op and returns success.
 *  - Otherwise, all sessions with `started_at < (now - retentionDays * 24h)` are deleted; CASCADE on
 *    the foreign keys removes the associated events and sensor samples automatically.
 *
 * Scheduled in [org.fossify.keyboard.App.onCreate] via `enqueueUniquePeriodicWork(KEEP)` so app
 * launches don't churn the queue.
 */
class IkdRetentionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val retentionDays = applicationContext.config.retentionDays
            if (retentionDays == RETENTION_FOREVER) {
                return@withContext Result.success()
            }
            val cutoff = System.currentTimeMillis() - retentionDays * MS_PER_DAY
            applicationContext.ikdDB.SessionDao().deleteOlderThan(cutoff)
            Result.success()
        } catch (e: SQLiteException) {
            e.printStackTrace()
            Result.retry()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "ikd-retention"

        // 24 * 60 * 60 * 1000 — milliseconds in one day.
        private const val MS_PER_DAY: Long = 86_400_000L
    }
}
