package org.fossify.keyboard.helpers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.keyboard.BuildConfig
import org.fossify.keyboard.databases.IkdDatabase
import org.fossify.keyboard.extensions.config
import org.fossify.keyboard.models.MoodEntry
import kotlin.system.measureTimeMillis

/**
 * Phase 8: glue between the keyboard mood bar (`MyKeyboardView`), the
 * privacy-default settings row (`IkdSettingsActivity`), and the
 * `mood_entries` table.
 *
 * The controller is intentionally thin — capture lifecycle stays in
 * `LiveCaptureSessionStore` and the IME, untouched. The controller only:
 *   1. Reads `LiveCaptureSessionStore.currentSessionId` to key writes.
 *   2. Flips `Config.privacyModeEnabled` (Phase 2 flag, unchanged).
 *   3. Writes / clears `MoodEntry` rows on `Dispatchers.IO`.
 *
 * Threading: every public method is a `suspend` running on
 * `Dispatchers.IO`. Callers can launch from any coroutine context, or
 * (from a `View` like `MyKeyboardView`) wrap in
 * `kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { ... }` and
 * marshal UI updates back via `runOnUiThread` / `View.post`.
 *
 * Ownership: the keyboard view does **not** finalise the in-flight session
 * here — that is `LiveCaptureSessionStore.stopSession()`'s job, called by
 * the Phase 2 privacy-flip path that this controller still leans on. We
 * expose `enablePrivacyAndClearMood()` so the bar's 🛡️ slot has a single
 * call site that mirrors the old standalone-button behaviour.
 */
class IkdMoodBarController(
    private val context: Context,
    private val db: IkdDatabase,
) {

    /**
     * Tap one of the six emotion buttons.
     *
     * 1. If privacy mode is on, flip it off (`Config.privacyModeEnabled = false`).
     *    The next time the IME sees `onStartInputView`, capture starts naturally;
     *    we do not start a session here ourselves (the IME owns that path).
     * 2. If a session is currently in flight, write or replace the
     *    `MoodEntry` keyed on its id. If no session is in flight, no row
     *    is written — the next session that does start will be unannotated
     *    until the user taps again. (Tapping while no session is active is
     *    only possible if the user opened a non-text field; the bar is on
     *    every IME open but the session only starts when capture is on.)
     */
    suspend fun setMoodForActiveSession(score: Int) = withContext(Dispatchers.IO) {
        if (!MoodEmoji.isValidScore(score)) {
            Log.w(TAG, "ignoring out-of-range mood score: $score")
            return@withContext
        }

        // Flip privacy off if needed. Persisted before the row write so a
        // racing capture-start sees the right config.
        if (context.config.privacyModeEnabled) {
            context.config.privacyModeEnabled = false
        }

        val sessionId = LiveCaptureSessionStore.currentSessionId
        if (sessionId.isEmpty()) {
            // No session in flight — nothing to annotate yet. The user
            // can re-tap once the keyboard is on a real field; until then
            // the highlight is a UI-only optimistic state owned by the bar.
            return@withContext
        }

        val durationMs = measureTimeMillis {
            db.MoodDao().insertOrReplace(
                MoodEntry(
                    id = null,
                    sessionId = sessionId,
                    timestamp = System.currentTimeMillis(),
                    moodScore = score,
                )
            )
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "setMoodForActiveSession(score=$score) took ${durationMs}ms")
        }
    }

    /**
     * Tap the 🛡️ slot. Mirrors the Phase 2 privacy-toggle-button behaviour
     * verbatim:
     *   1. Flip `Config.privacyModeEnabled = true`.
     *   2. If a session is in flight, finalise it via
     *      `LiveCaptureSessionStore.stopSession()` (delegating to the same
     *      Phase 2 path the standalone shield used).
     *   3. Delete any in-flight `MoodEntry` for the soon-to-be-finalised
     *      session — finalised sessions can still own a mood row, but the
     *      user has explicitly said "don't keep the rating", so honour that.
     *
     * Step 2 must run before step 3 reads `currentSessionId` because
     * `stopSession()` clears it.
     */
    suspend fun enablePrivacyAndClearMood() = withContext(Dispatchers.IO) {
        // Capture the session id BEFORE we tell the store to stop, since
        // `stopSession()` clears `currentSessionId`.
        val sessionId = LiveCaptureSessionStore.currentSessionId

        if (!context.config.privacyModeEnabled) {
            context.config.privacyModeEnabled = true
        }

        if (LiveCaptureSessionStore.isCapturing) {
            LiveCaptureSessionStore.stopSession()
        }

        if (sessionId.isNotEmpty()) {
            try {
                db.MoodDao().clearForSession(sessionId)
            } catch (e: android.database.SQLException) {
                Log.w(TAG, "clearForSession($sessionId) failed", e)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "clearForSession($sessionId) failed (db closed?)", e)
            }
        }
    }

    /**
     * Read the in-flight session's mood, if any. Used by the keyboard bar
     * on `onStartInputView` to restore the highlighted slot — under the
     * spec a fresh session has no mood row until the user taps, so this
     * is defensive: it only returns non-null if a previous re-open into
     * the same session id was racy enough to have one.
     */
    suspend fun getMoodForActiveSession(): MoodEntry? = withContext(Dispatchers.IO) {
        val sessionId = LiveCaptureSessionStore.currentSessionId
        if (sessionId.isEmpty()) return@withContext null
        db.MoodDao().getForSession(sessionId)
    }

    companion object {
        private const val TAG = "IkdMoodBarController"
    }
}
