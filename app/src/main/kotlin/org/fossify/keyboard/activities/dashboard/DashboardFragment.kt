package org.fossify.keyboard.activities.dashboard

import androidx.fragment.app.Fragment
import org.fossify.keyboard.activities.DashboardActivity

/**
 * Phase 9.11: base for the five Insights tab fragments.
 *
 * Lifecycle contract:
 *  - the host activity owns the data hop (one `Dispatchers.IO` round-trip
 *    per `loadSnapshot()`); when the resulting [DashboardPayload] arrives
 *    the activity calls [renderPayload] on every attached fragment.
 *  - on the fragment side, `onResume` re-asks the host for the current
 *    payload — handles the case where a tab becomes visible for the
 *    first time after navigation but before a fresh load fires.
 *
 * Each fragment owns its own view binding. Theme tints and per-card
 * background colour are reapplied on every render — same pattern as the
 * Phase 6 / 9.1 polish.
 */
abstract class DashboardFragment : Fragment() {

    /**
     * Render the latest aggregator outputs into this tab's views. Always
     * called on the main thread. The host activity is the sole caller in
     * production; subclasses may also call it themselves from `onResume`.
     */
    abstract fun renderPayload(payload: DashboardPayload)

    /**
     * Convenience: pull the host activity's current payload (may be
     * `null` until the first `loadSnapshot` finishes) and render. Safe
     * to call before the view is attached — short-circuits when the view
     * binding is gone.
     */
    protected fun renderFromHostIfReady() {
        val host = activity as? DashboardActivity ?: return
        val payload = host.latestPayload ?: return
        if (view == null) return
        renderPayload(payload)
    }

    override fun onResume() {
        super.onResume()
        renderFromHostIfReady()
    }
}
