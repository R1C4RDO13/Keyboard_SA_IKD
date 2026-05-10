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

        // Phase 8.5: standing rating + activity-timestamp updates always
        // run, regardless of whether a `mood_entries` row gets written.
        // The standing rating is the user's current self-rating across
        // sessions; selecting *is* the strongest "user engaged" signal.
        context.config.lastMoodScore = score
        context.config.lastMoodActivityTimestamp = System.currentTimeMillis()

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

        // Phase 8.5: clear the standing rating alongside the in-flight
        // mood row — Decision #16. "Stop tracking me" includes the
        // standing self-rating. The activity timestamp is intentionally
        // NOT updated: privacy-on is itself an interaction, but the
        // semantic loss of leaving the timestamp is acceptable (the
        // staleness check skips when `lastMoodScore == SCORE_NONE`).
        context.config.lastMoodScore = MoodEmoji.SCORE_NONE

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

    /**
     * Phase 8.2: tap the highlighted 🛡️ a second time. Flips privacy off
     * without selecting a mood — capture is on, no rating. Inverse of
     * `enablePrivacyAndClearMood()` minus the session-stop path: capture
     * starts naturally on the next IME open via the existing Phase 2
     * `Config.privacyModeEnabled` gate in `SimpleKeyboardIME`.
     */
    suspend fun disablePrivacy() = withContext(Dispatchers.IO) {
        if (context.config.privacyModeEnabled) {
            context.config.privacyModeEnabled = false
        }
    }

    /**
     * Phase 8.2: tap a highlighted emotion a second time. Deletes the
     * `MoodEntry` row for the in-flight session — privacy stays off, the
     * session keeps recording, the user just retracts their rating.
     * No-op if no session is in flight (the bar's optimistic highlight is
     * the only state that flips, no row exists to remove).
     */
    suspend fun clearMoodForActiveSession() = withContext(Dispatchers.IO) {
        // Phase 8.5: also clear the standing rating — Decision #15. The
        // user explicitly retracted their self-rating; honour it across
        // sessions, not just the in-flight one. The activity timestamp
        // is NOT cleared (clearing is itself an interaction; if we
        // also reset the timestamp, the very next on-show staleness
        // check would compare against `0L` and no-op anyway, but
        // leaving the timestamp keeps the semantics tidy).
        context.config.lastMoodScore = MoodEmoji.SCORE_NONE

        val sessionId = LiveCaptureSessionStore.currentSessionId
        if (sessionId.isEmpty()) return@withContext
        try {
            db.MoodDao().clearForSession(sessionId)
        } catch (e: android.database.SQLException) {
            Log.w(TAG, "clearForSession($sessionId) failed", e)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "clearForSession($sessionId) failed (db closed?)", e)
        }
    }

    companion object {
        private const val TAG = "IkdMoodBarController"
    }
}
