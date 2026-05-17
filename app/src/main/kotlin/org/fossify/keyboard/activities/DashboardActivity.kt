package org.fossify.keyboard.activities

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.keyboard.R
import org.fossify.keyboard.activities.dashboard.DashboardFragment
import org.fossify.keyboard.activities.dashboard.DashboardPagerAdapter
import org.fossify.keyboard.activities.dashboard.DashboardPayload
import org.fossify.keyboard.activities.dashboard.SummaryFragment
import org.fossify.keyboard.databinding.ActivityDashboardBinding
import org.fossify.keyboard.extensions.ikdActivityAggregator
import org.fossify.keyboard.extensions.ikdAggregator
import org.fossify.keyboard.extensions.ikdBadgeEvaluator
import org.fossify.keyboard.extensions.ikdDistributionAggregator
import org.fossify.keyboard.extensions.ikdHabitsAggregator
import org.fossify.keyboard.extensions.ikdMoodAggregator
import org.fossify.keyboard.extensions.ikdOrientationAggregator
import org.fossify.keyboard.extensions.ikdQualityAggregator
import org.fossify.keyboard.extensions.ikdSensorAggregator
import org.fossify.keyboard.extensions.config
import org.fossify.keyboard.helpers.IkdAggregator
import org.fossify.keyboard.helpers.IkdBadgeCatalog
import org.fossify.keyboard.helpers.IkdBadgeNotifier
import org.fossify.keyboard.helpers.InsightsFiltersBottomSheet
import org.fossify.keyboard.helpers.MoodEmoji

/**
 * Phase 9.11/9.12/9.14: the Insights screen is a thin host. The activity owns
 *  - the global header chrome (TabLayout + active-filter chip),
 *  - the data hop (one `Dispatchers.IO` round-trip per `loadSnapshot`),
 *  - the empty-state view,
 *
 * and the rest lives in five `DashboardFragment` subclasses driven by a
 * [DashboardPagerAdapter] backing a `ViewPager2` (Phase 9.15 dropped
 * the Mood tab — its widgets moved into Summary).
 *
 * Phase 9.14.1 added the **Summary** tab as the new index 0 (KPI grid
 * lifted out of the activity chrome). Phase 9.14.2 swapped the Phase
 * 9.12 `MaterialButtonToggleGroup` for a Material 3 `TabLayout` wired
 * via [TabLayoutMediator], with one vector icon + short label per tab.
 * Phase 9.14.3 collapsed the standalone range toggle and mood-filter
 * chip strip into [InsightsFiltersBottomSheet] — the toolbar Filters
 * action launches it; the active-filter chip surfaces non-default state
 * and reopens the sheet when tapped.
 *
 * Range, mood filter and active tab persist via `onSaveInstanceState`
 * (no new pref keys — Phase 9 Decision #2 / Phase 9.14 Decision #10).
 */
class DashboardActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityDashboardBinding::inflate)
    private var currentRange: IkdAggregator.Range = IkdAggregator.Range.WEEK

    /**
     * Phase 9.4: per-screen-instance state — null means "All".
     *
     * Phase 9.18: getter is package-visible so `SummaryFragment` can sync
     * its Distribution-tile highlight after every `renderPayload` round.
     * Mutations stay private — they flow exclusively through
     * [onMoodTileTapped], the bottom-sheet result listener, and the
     * active-filter chip ✕ handler.
     */
    internal var currentMoodFilter: Int? = null
        private set

    /**
     * Phase 9.11: latest payload from the most recent `loadSnapshot` run.
     * Fragments pull this on `onResume` via `renderFromHostIfReady` to
     * cover the swipe-to-tab case where a fragment view is created after
     * the data has already loaded.
     */
    var latestPayload: DashboardPayload? = null
        private set

    private lateinit var pagerAdapter: DashboardPagerAdapter

    /**
     * Phase 14: lazy `POST_NOTIFICATIONS` request. Registered
     * unconditionally (the contract must be registered before
     * `onCreate` returns); launched once per activity create when badge
     * notifications are enabled and the grant is still missing on
     * Android 13+. Denial is silent and non-blocking — the snackbar
     * still fires (graceful degradation, Decision #11).
     */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* silent */ }

    private var notificationPermissionAsked = false

    /**
     * Phase 9.12: re-theme freshly attached fragment views. Material
     * cards inside fragments inherit `?attr/colorSurface` by default,
     * which on a dark Fossify theme renders as a near-white slab
     * against a dark background. Re-running `applyThemeColors` once a
     * fragment view is attached pushes the user-selected text/background
     * tokens onto every `MyTextView` and `MaterialCardView` in the tab.
     */
    private val fragmentLifecycleCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentViewCreated(
            fm: FragmentManager,
            f: Fragment,
            v: android.view.View,
            savedInstanceState: Bundle?,
        ) {
            if (f is DashboardFragment) {
                (v as? android.view.ViewGroup)?.let { updateTextColors(it) }
                latestPayload?.let { f.renderPayload(it) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        currentRange = savedInstanceState?.getString(STATE_RANGE)
            ?.let { runCatching { IkdAggregator.Range.valueOf(it) }.getOrNull() }
            ?: IkdAggregator.Range.WEEK

        // Phase 9.4: restore mood filter from instance state. -1 sentinel = null (All).
        if (savedInstanceState != null && savedInstanceState.containsKey(STATE_MOOD_FILTER)) {
            val stored = savedInstanceState.getInt(STATE_MOOD_FILTER, MOOD_FILTER_ALL_SENTINEL)
            currentMoodFilter = if (stored == MOOD_FILTER_ALL_SENTINEL) null else stored
        }

        binding.apply {
            setupEdgeToEdge(padBottomSystem = listOf(dashboardViewPager))
        }

        supportFragmentManager.registerFragmentLifecycleCallbacks(fragmentLifecycleCallbacks, false)
        registerFiltersResultListener()

        setupListeners()
        setupPagerAndTabs(savedInstanceState)
        maybeRequestNotificationPermission()
        maybeOpenAchievementsFromIntent(intent)
    }

    /**
     * Phase 14: ask for `POST_NOTIFICATIONS` once, lazily, the first time
     * the dashboard opens with badge notifications enabled and the grant
     * missing. Skipped entirely below Android 13 (no runtime permission)
     * or when the user turned the toggle off.
     */
    private fun maybeRequestNotificationPermission() {
        if (notificationPermissionAsked) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (!config.badgeNotificationsEnabled) return
        val granted = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return
        notificationPermissionAsked = true
        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * Phase 9.14.3: listen for the bottom sheet's
     * [InsightsFiltersBottomSheet.REQUEST_KEY] result. Wired in
     * `onCreate` so it survives configuration changes — the FragmentManager
     * keeps the listener alive while the activity is recreated.
     */
    private fun registerFiltersResultListener() {
        supportFragmentManager.setFragmentResultListener(
            InsightsFiltersBottomSheet.REQUEST_KEY,
            this,
        ) { _, bundle ->
            val newRange = bundle.getString(InsightsFiltersBottomSheet.KEY_RANGE)
                ?.let { runCatching { IkdAggregator.Range.valueOf(it) }.getOrNull() }
                ?: currentRange
            val storedMood = bundle.getInt(
                InsightsFiltersBottomSheet.KEY_MOOD_FILTER,
                InsightsFiltersBottomSheet.MOOD_FILTER_NONE,
            )
            val newMood = if (storedMood == InsightsFiltersBottomSheet.MOOD_FILTER_NONE) {
                null
            } else {
                storedMood
            }
            val rangeChanged = newRange != currentRange
            val moodChanged = newMood != currentMoodFilter
            currentRange = newRange
            currentMoodFilter = newMood
            renderBucketLabel()
            if (rangeChanged || moodChanged) {
                loadSnapshot()
            }
            // Phase 9.18: keep the Summary-tab tile highlight in sync with
            // changes made through the bottom sheet (the secondary entry
            // point). Without this, picking a mood via the sheet would
            // leave the tiles flat until the IO hop returned.
            if (moodChanged) {
                summaryFragment()?.applyMoodFilterHighlight(newMood)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.dashboardAppbar, NavigationIcon.Arrow)
        binding.apply {
            updateTextColors(dashboardGlobalHeader)
        }
        binding.dashboardEmptyMessage.setTextColor(getProperTextColor())
        applyChromeColors()
        renderBucketLabel()
        loadSnapshot()
    }

    override fun onDestroy() {
        super.onDestroy()
        supportFragmentManager.unregisterFragmentLifecycleCallbacks(fragmentLifecycleCallbacks)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_RANGE, currentRange.name)
        outState.putInt(STATE_MOOD_FILTER, currentMoodFilter ?: MOOD_FILTER_ALL_SENTINEL)
        outState.putInt(STATE_TAB_INDEX, binding.dashboardViewPager.currentItem)
    }

    /**
     * Phase 9.14.2: wire the new Material 3 [TabLayout] to the
     * ViewPager2 via [TabLayoutMediator]. The mediator owns the
     * two-way sync — tab taps drive `setCurrentItem`, pager scrolls
     * drive `selectTab`. Each tab gets a vector icon and a short label;
     * in `MODE_FIXED` with `tabMinWidth=0` Material distributes the five
     * tabs evenly across the screen at 360 dp / 5 = 72 dp per tab,
     * which fits comfortably at default font size (Phase 9.15: Mood tab dropped).
     */
    private fun setupPagerAndTabs(savedInstanceState: Bundle?) {
        pagerAdapter = DashboardPagerAdapter(this)
        binding.dashboardViewPager.adapter = pagerAdapter
        // Keep every fragment in memory so swipe re-renders are instant
        // and chart caches survive page changes.
        binding.dashboardViewPager.offscreenPageLimit = DashboardPagerAdapter.TAB_COUNT - 1

        TabLayoutMediator(binding.dashboardTabLayout, binding.dashboardViewPager) { tab, position ->
            tab.setIcon(tabIconResFor(position))
            tab.setText(tabLabelResFor(position))
            tab.contentDescription = getString(tabLabelResFor(position))
        }.attach()

        // Phase 9.14.1: cold-launch default is the new Summary tab.
        val initialTab = savedInstanceState?.getInt(STATE_TAB_INDEX, DashboardPagerAdapter.TAB_SUMMARY)
            ?: DashboardPagerAdapter.TAB_SUMMARY
        binding.dashboardViewPager.setCurrentItem(initialTab, false)
    }

    /**
     * Phase 9.14.2 + 9.14.3 theming: Fossify's "primary color" is a
     * runtime-stored int on `BaseConfig` rather than a static theme
     * attribute, so the static `?attr/colorPrimary` references in
     * `activity_dashboard.xml` and `dashboard_tab_icon_tint.xml` resolve
     * to Material's default purple instead of the user-selected tone.
     * This pass forces every chrome surface (tab indicator, tab text /
     * icon tint, active-filter chip) onto the same `getProperPrimaryColor()`
     * / `getProperTextColor()` pair every time `onResume` fires.
     */
    private fun applyChromeColors() {
        val primary = getProperPrimaryColor()
        val textColor = getProperTextColor()
        val background = getProperBackgroundColor()

        // Phase 9.14 follow-up: Material 3 components (TabLayout, the
        // LinearLayouts hosting them) default to `?attr/colorSurface` /
        // unset, neither of which tracks Fossify's runtime background tone.
        // Tint the chrome surfaces below the AppBar so the strip between the
        // toolbar and the first chart matches the user's theme on every
        // Fossify variant.
        binding.dashboardRootContainer.setBackgroundColor(background)
        binding.dashboardGlobalHeader.setBackgroundColor(background)
        binding.dashboardTabLayout.setBackgroundColor(background)

        binding.dashboardTabLayout.setSelectedTabIndicatorColor(primary)
        binding.dashboardTabLayout.setTabTextColors(textColor, primary)
        // Static color-state-list selectors do not see Fossify's runtime
        // primary, so build the icon tint at runtime too.
        val iconTintStates = arrayOf(
            intArrayOf(android.R.attr.state_selected),
            intArrayOf(-android.R.attr.state_selected),
        )
        binding.dashboardTabLayout.tabIconTint = ColorStateList(
            iconTintStates,
            intArrayOf(primary, textColor),
        )

    }

    private fun tabIconResFor(position: Int): Int = when (position) {
        DashboardPagerAdapter.TAB_SUMMARY -> R.drawable.ic_dashboard_summary_vector
        DashboardPagerAdapter.TAB_ACHIEVEMENTS -> R.drawable.ic_dashboard_achievements_vector
        DashboardPagerAdapter.TAB_ACTIVITY -> R.drawable.ic_dashboard_activity_vector
        DashboardPagerAdapter.TAB_TRENDS -> R.drawable.ic_dashboard_trends_vector
        DashboardPagerAdapter.TAB_KEYS -> R.drawable.ic_dashboard_keystrokes_vector
        else -> R.drawable.ic_dashboard_summary_vector
    }

    private fun tabLabelResFor(position: Int): Int = when (position) {
        DashboardPagerAdapter.TAB_SUMMARY -> R.string.dashboard_tab_label_summary
        DashboardPagerAdapter.TAB_ACHIEVEMENTS -> R.string.dashboard_tab_label_achievements
        DashboardPagerAdapter.TAB_ACTIVITY -> R.string.dashboard_tab_label_daily_activity
        DashboardPagerAdapter.TAB_TRENDS -> R.string.dashboard_tab_label_trends
        DashboardPagerAdapter.TAB_KEYS -> R.string.dashboard_tab_label_keystroke_dynamics
        else -> R.string.dashboard_tab_label_summary
    }

    private fun setupListeners() {
        binding.dashboardToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.dashboard_filters -> {
                    openFiltersSheet()
                    true
                }
                R.id.dashboard_refresh -> {
                    loadSnapshot()
                    true
                }
                R.id.dashboard_sessions_history -> {
                    startActivity(Intent(this, SessionsListActivity::class.java))
                    true
                }
                else -> false
            }
        }
        binding.dashboardEmptyMessage.setOnClickListener {
            // Phase 9.18 follow-up: when a mood filter has narrowed the range
            // to zero sessions, the empty-state TextView is the *only*
            // affordance the user can see (chip is now range-only and hidden
            // for mood; tiles are inside the hidden ViewPager). Tapping the
            // empty message clears the mood filter — same toggle path as
            // tapping the active tile would. Otherwise (genuinely no data)
            // it still redirects to the data-collection settings as before.
            val activeMood = currentMoodFilter
            if (activeMood != null) {
                onMoodTileTapped(activeMood)
            } else {
                startActivity(Intent(this, IkdSettingsActivity::class.java))
            }
        }
    }

    private fun openFiltersSheet() {
        if (supportFragmentManager.findFragmentByTag(InsightsFiltersBottomSheet.TAG) != null) return
        InsightsFiltersBottomSheet
            .newInstance(currentRange, currentMoodFilter)
            .show(supportFragmentManager, InsightsFiltersBottomSheet.TAG)
    }

    /**
     * Phase 9.16: render the scope + bucket-size hint below the active-
     * filter chip. Combines two pieces — the range scope ("Past 7 days")
     * and the bucket unit ("grouped by day") — into one italic line so
     * the user sees both *what* time window the charts cover and *how*
     * the X axis is bucketed. Always visible; not gated on filter state.
     */
    private fun renderBucketLabel() {
        val scopeRes = when (currentRange) {
            IkdAggregator.Range.TODAY -> R.string.dashboard_bucket_scope_today
            IkdAggregator.Range.WEEK -> R.string.dashboard_bucket_scope_week
            IkdAggregator.Range.MONTH -> R.string.dashboard_bucket_scope_month
            IkdAggregator.Range.ALL_TIME -> R.string.dashboard_bucket_scope_all
        }
        val unitRes = when (currentRange) {
            IkdAggregator.Range.TODAY -> R.string.dashboard_bucket_unit_hourly
            IkdAggregator.Range.WEEK,
            IkdAggregator.Range.MONTH -> R.string.dashboard_bucket_unit_daily
            IkdAggregator.Range.ALL_TIME -> R.string.dashboard_bucket_unit_weekly
        }
        binding.dashboardBucketLabel.text = getString(
            R.string.dashboard_bucket_label_format,
            getString(scopeRes),
            getString(unitRes),
        )
    }

    private fun loadSnapshot() {
        lifecycleScope.launch {
            val agg = ikdAggregator
            val moodAgg = ikdMoodAggregator
            val sensorAgg = ikdSensorAggregator
            val habitsAgg = ikdHabitsAggregator
            val activityAgg = ikdActivityAggregator
            val distAgg = ikdDistributionAggregator
            val orientationAgg = ikdOrientationAggregator
            val qualityAgg = ikdQualityAggregator
            // Phase 14: the badge evaluator rides the same IO hop as every
            // aggregator (Decision #5 — lazy, on dashboard open). It is
            // always all-time and ignores Range/Mood (Decision #13).
            val badgeEvaluator = ikdBadgeEvaluator
            val range = currentRange
            val moodFilter = currentMoodFilter
            val payload = withContext(Dispatchers.IO) {
                DashboardPayload(
                    ikd = agg.snapshot(range, moodFilter),
                    mood = moodAgg.snapshot(range),
                    moodMix = moodAgg.mixSnapshot(range),
                    sensor = sensorAgg.snapshot(range, moodFilter),
                    habits = habitsAgg.snapshot(range, moodFilter),
                    activity = activityAgg.snapshot(range, moodFilter),
                    distribution = distAgg.snapshot(range, moodFilter),
                    orientation = orientationAgg.snapshot(range, moodFilter),
                    quality = qualityAgg.snapshot(range, moodFilter),
                    badges = badgeEvaluator.evaluate(),
                )
            }
            renderPayload(payload)
        }
    }

    private fun renderPayload(payload: DashboardPayload) {
        latestPayload = payload

        // Phase 14 §7.4/§7.5: fire the in-app snackbar + the local
        // notification for badges that crossed their criterion on *this*
        // evaluation. `newlyUnlocked` is the evaluator's delta — a refresh
        // within the same session re-runs evaluate() but the badge is now
        // persisted, so it won't re-fire (no spam). Done before the
        // empty-state early return: a badge can unlock from mood entries
        // alone, with zero typing sessions.
        dispatchBadgeUnlockFeedback(payload)

        // Task B: defensive recovery. A mood filter that resolves to zero
        // sessions (e.g. a stale filter restored from instance state, or a
        // mood whose only sessions were deleted) would otherwise strand the
        // user on the blank empty-state screen — the tiles that let them
        // recover live inside the now-hidden ViewPager. If the *unfiltered*
        // DB still has mood data, silently drop the filter and re-aggregate
        // rather than show a dead-end. The Summary tile gate (Task B, in
        // SummaryFragment) makes this path unreachable from the UI; this is
        // the belt-and-suspenders backstop.
        if (currentMoodFilter != null &&
            payload.ikd.totalSessions == 0 &&
            payload.mood.total > 0
        ) {
            currentMoodFilter = null
            summaryFragment()?.applyMoodFilterHighlight(null)
            loadSnapshot()
            return
        }

        val isEmpty = payload.ikd.totalSessions == 0
        // Phase 9.18 follow-up: distinct empty-state copy when a mood
        // filter narrowed the range to zero sessions. The empty message
        // itself becomes the escape hatch — tapping it clears the mood
        // filter (handled in `setupListeners` via the click listener).
        val emptyWithFilter = isEmpty && currentMoodFilter != null
        binding.dashboardEmptyMessage.setText(
            if (emptyWithFilter) R.string.dashboard_empty_for_mood_filter
            else R.string.dashboard_empty_message
        )
        binding.dashboardEmptyMessage.beVisibleIf(isEmpty)
        binding.dashboardViewPager.beVisibleIf(!isEmpty)
        binding.dashboardGlobalHeader.beVisibleIf(!isEmpty)
        if (isEmpty) return

        // Phase 9.14.1: the global KPI strip is gone — its six cells are
        // now the Summary tab's 2x3 grid, populated by `SummaryFragment`
        // off the same payload below.

        // Dispatch the payload to every attached fragment. Fragments that
        // are not currently visible still render so a swipe to them is
        // instant. Fragments not yet created (offscreenPageLimit overflow)
        // pick the latest payload up via `renderFromHostIfReady` on
        // `onResume` / their first lifecycle attach.
        for (fragment in supportFragmentManager.fragments) {
            if (fragment is DashboardFragment && fragment.view != null) {
                fragment.renderPayload(payload)
            }
        }
    }

    /**
     * Phase 9.14.1: public entry point for KPI tile clicks on the
     * Summary tab (Decision #7). Delegates to the underlying ViewPager2
     * with a smooth-scroll so the [TabLayoutMediator] keeps the tab
     * indicator in sync.
     */
    fun goToTab(position: Int) {
        if (position < 0 || position >= DashboardPagerAdapter.TAB_COUNT) return
        binding.dashboardViewPager.setCurrentItem(position, true)
    }

    /**
     * Phase 14 §7.4/§7.5: snackbar (anchored to the ViewPager so it
     * floats above the bottom nav, action → Achievements tab) + a local
     * notification in parallel via [IkdBadgeNotifier]. Multiple
     * simultaneous unlocks collapse to "first title +N more". Already-
     * unlocked badges never reach here — they're filtered out of
     * `newlyUnlocked` before persistence.
     */
    private fun dispatchBadgeUnlockFeedback(payload: DashboardPayload) {
        val newly = payload.badges.newlyUnlocked
        if (newly.isEmpty()) return

        val firstDef = IkdBadgeCatalog.defFor(newly.first().key) ?: return
        val emoji = firstDef.emoji
        val title = getString(firstDef.titleRes)
        val extra = newly.size - 1
        val message = if (extra == 0) {
            getString(R.string.badge_snackbar_single, emoji, title)
        } else {
            getString(R.string.badge_snackbar_multi, emoji, title, extra)
        }
        Snackbar
            .make(binding.dashboardViewPager, message, Snackbar.LENGTH_LONG)
            .setAnchorView(binding.dashboardViewPager)
            .setAction(R.string.badge_snackbar_action) {
                goToTab(DashboardPagerAdapter.TAB_ACHIEVEMENTS)
            }
            .show()

        IkdBadgeNotifier.notify(this, newly)
    }

    /**
     * Phase 14: a badge-unlock notification tap carries
     * [EXTRA_OPEN_TAB] — jump straight to the Achievements tab. Handled
     * from both `onCreate` (cold start via the PendingIntent) and
     * `onNewIntent` (the activity was already in the back stack).
     */
    private fun maybeOpenAchievementsFromIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_TAB, false) == true) {
            binding.dashboardViewPager.post {
                goToTab(DashboardPagerAdapter.TAB_ACHIEVEMENTS)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        maybeOpenAchievementsFromIntent(intent)
    }

    /**
     * Phase 9.18: Distribution-tile click handler. Each Summary-tab tile
     * doubles as a one-tap shortcut for the global Mood Filter. Toggle
     * semantics mirror the Phase 8.2 mood-bar (Decision #3 of the 9.18
     * plan):
     *  - Tap the *active* tile → revert to All (`currentMoodFilter = null`).
     *  - Tap any other tile → switch to that score in one tap.
     *
     * After the state flip, re-render the active-filter chip + scope
     * label, re-run the IO-bound aggregation hop, and notify the
     * SummaryFragment immediately so the tile highlight snaps without
     * waiting for `loadSnapshot` to come back.
     */
    fun onMoodTileTapped(score: Int) {
        val newFilter = if (currentMoodFilter == score) null else score
        if (newFilter == currentMoodFilter) return
        currentMoodFilter = newFilter
        renderBucketLabel()
        loadSnapshot()
        summaryFragment()?.applyMoodFilterHighlight(newFilter)
    }

    /**
     * Locate the live [SummaryFragment] instance, if any. The pager keeps
     * every page in memory (`offscreenPageLimit = TAB_COUNT - 1`), so this
     * succeeds as soon as the fragment view has been created. Returns
     * `null` during early `onCreate` or after the fragment is destroyed.
     */
    private fun summaryFragment(): SummaryFragment? =
        supportFragmentManager.fragments.filterIsInstance<SummaryFragment>().firstOrNull()

    companion object {
        private const val STATE_RANGE = "dashboard_range"
        private const val STATE_MOOD_FILTER = "dashboard_mood_filter"
        private const val STATE_TAB_INDEX = "dashboard_tab_index"
        private const val MOOD_FILTER_ALL_SENTINEL = -1

        /**
         * Phase 14: set by [org.fossify.keyboard.helpers.IkdBadgeNotifier]'s
         * `PendingIntent` so tapping a badge-unlock notification jumps
         * straight to the Achievements tab.
         */
        const val EXTRA_OPEN_TAB = "dashboard_open_achievements"
    }
}
