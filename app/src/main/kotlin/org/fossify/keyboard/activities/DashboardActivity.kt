package org.fossify.keyboard.activities

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
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
import org.fossify.keyboard.databinding.ActivityDashboardBinding
import org.fossify.keyboard.extensions.ikdActivityAggregator
import org.fossify.keyboard.extensions.ikdAggregator
import org.fossify.keyboard.extensions.ikdDistributionAggregator
import org.fossify.keyboard.extensions.ikdHabitsAggregator
import org.fossify.keyboard.extensions.ikdMoodAggregator
import org.fossify.keyboard.extensions.ikdOrientationAggregator
import org.fossify.keyboard.extensions.ikdQualityAggregator
import org.fossify.keyboard.extensions.ikdSensorAggregator
import org.fossify.keyboard.helpers.IkdAggregator
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

    /** Phase 9.4: per-screen-instance state — null means "All". */
    private var currentMoodFilter: Int? = null

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
        setupActiveFilterChip()
        setupPagerAndTabs(savedInstanceState)
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
            renderActiveFilterChip()
            renderBucketLabel()
            if (rangeChanged || moodChanged) {
                loadSnapshot()
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
        renderActiveFilterChip()
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

        // Active-filter chip: outlined silhouette in the user's primary
        // tone — body matches the activity background, the stroke and
        // text/close-icon use the primary token. Same discipline as the
        // Phase 9.4 mood chip-row colours.
        val chip = binding.dashboardActiveFilterChip
        chip.chipBackgroundColor = ColorStateList.valueOf(background)
        chip.chipStrokeColor = ColorStateList.valueOf(primary)
        chip.chipStrokeWidth = resources.getDimension(R.dimen.chip_stroke_width)
        chip.setTextColor(primary)
        chip.closeIconTint = ColorStateList.valueOf(primary)
    }

    private fun tabIconResFor(position: Int): Int = when (position) {
        DashboardPagerAdapter.TAB_SUMMARY -> R.drawable.ic_dashboard_summary_vector
        DashboardPagerAdapter.TAB_TRENDS -> R.drawable.ic_dashboard_trends_vector
        DashboardPagerAdapter.TAB_DAILY_ACTIVITY -> R.drawable.ic_dashboard_activity_vector
        DashboardPagerAdapter.TAB_KEYSTROKE_DYNAMICS -> R.drawable.ic_dashboard_keystrokes_vector
        DashboardPagerAdapter.TAB_HABITS -> R.drawable.ic_dashboard_habits_vector
        else -> R.drawable.ic_dashboard_summary_vector
    }

    private fun tabLabelResFor(position: Int): Int = when (position) {
        DashboardPagerAdapter.TAB_SUMMARY -> R.string.dashboard_tab_label_summary
        DashboardPagerAdapter.TAB_TRENDS -> R.string.dashboard_tab_label_trends
        DashboardPagerAdapter.TAB_DAILY_ACTIVITY -> R.string.dashboard_tab_label_daily_activity
        DashboardPagerAdapter.TAB_KEYSTROKE_DYNAMICS -> R.string.dashboard_tab_label_keystroke_dynamics
        DashboardPagerAdapter.TAB_HABITS -> R.string.dashboard_tab_label_habits
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
            startActivity(Intent(this, IkdSettingsActivity::class.java))
        }
    }

    /**
     * Phase 9.14.3: tap chip body → reopen the sheet at current
     * selection; tap × → reset both filters to defaults (Week + All)
     * and re-aggregate. The chip itself only renders when a non-default
     * filter is active (`renderActiveFilterChip`).
     */
    private fun setupActiveFilterChip() {
        binding.dashboardActiveFilterChip.setOnClickListener { openFiltersSheet() }
        binding.dashboardActiveFilterChip.setOnCloseIconClickListener {
            val rangeChanged = currentRange != IkdAggregator.Range.WEEK
            val moodChanged = currentMoodFilter != null
            currentRange = IkdAggregator.Range.WEEK
            currentMoodFilter = null
            renderActiveFilterChip()
            renderBucketLabel()
            if (rangeChanged || moodChanged) {
                loadSnapshot()
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
     * Phase 9.14.3: chip text rules.
     *  - range == Week, mood == null  → chip hidden
     *  - mood only                    → "😊 Happy"
     *  - range only                   → "Today" / "Month" / "All time"
     *  - both                         → "Month · 😊 Happy"
     */
    private fun renderActiveFilterChip() {
        val chip = binding.dashboardActiveFilterChip
        val hasRange = currentRange != IkdAggregator.Range.WEEK
        val mood = currentMoodFilter
        val hasMood = mood != null
        if (!hasRange && !hasMood) {
            chip.beVisibleIf(false)
            return
        }
        val rangeLabel = when (currentRange) {
            IkdAggregator.Range.TODAY -> getString(R.string.dashboard_range_today)
            IkdAggregator.Range.WEEK -> getString(R.string.dashboard_range_week)
            IkdAggregator.Range.MONTH -> getString(R.string.dashboard_range_month)
            IkdAggregator.Range.ALL_TIME -> getString(R.string.dashboard_range_all)
        }
        chip.text = when {
            hasRange && hasMood -> getString(
                R.string.insights_filters_chip_range_and_mood,
                rangeLabel,
                MoodEmoji.emojiFor(mood!!),
                getString(MoodEmoji.labelResFor(mood)),
            )
            hasMood -> getString(
                R.string.insights_filters_chip_mood_only,
                MoodEmoji.emojiFor(mood!!),
                getString(MoodEmoji.labelResFor(mood)),
            )
            else -> rangeLabel
        }
        chip.beVisibleIf(true)
    }

    /**
     * Phase 9.16: render the bucket-size hint below the active-filter chip.
     * Maps the current `Range` to the bucket unit the X axis uses on the
     * trend charts. Always visible; not gated on filter state.
     */
    private fun renderBucketLabel() {
        val unitRes = when (currentRange) {
            IkdAggregator.Range.TODAY -> R.string.dashboard_bucket_unit_hourly
            IkdAggregator.Range.WEEK,
            IkdAggregator.Range.MONTH -> R.string.dashboard_bucket_unit_daily
            IkdAggregator.Range.ALL_TIME -> R.string.dashboard_bucket_unit_weekly
        }
        binding.dashboardBucketLabel.text = getString(
            R.string.dashboard_bucket_label_format,
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
                )
            }
            renderPayload(payload)
        }
    }

    private fun renderPayload(payload: DashboardPayload) {
        latestPayload = payload
        val isEmpty = payload.ikd.totalSessions == 0
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

    companion object {
        private const val STATE_RANGE = "dashboard_range"
        private const val STATE_MOOD_FILTER = "dashboard_mood_filter"
        private const val STATE_TAB_INDEX = "dashboard_tab_index"
        private const val MOOD_FILTER_ALL_SENTINEL = -1
    }
}
