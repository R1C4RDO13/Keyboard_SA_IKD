package org.fossify.keyboard.activities

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getContrastColor
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
import org.fossify.keyboard.extensions.moodDB
import org.fossify.keyboard.helpers.IkdAggregator
import org.fossify.keyboard.helpers.MoodEmoji
import org.fossify.keyboard.helpers.WidgetInfo
import org.fossify.keyboard.helpers.attachWidgetInfo

/**
 * Phase 9.11/9.12/9.14: the Insights screen is a thin host. The activity owns
 *  - the global header (range toggle + mood-filter chip row),
 *  - the data hop (one `Dispatchers.IO` round-trip per `loadSnapshot`),
 *  - the empty-state view,
 *
 * and the rest lives in six `DashboardFragment` subclasses driven by a
 * [DashboardPagerAdapter] backing a `ViewPager2`.
 *
 * Phase 9.14.1 added the **Summary** tab as the new index 0 (KPI grid
 * lifted out of the activity chrome); 9.14.2 swapped the Phase 9.12
 * `MaterialButtonToggleGroup` for a Material 3 `TabLayout` wired via
 * [TabLayoutMediator], with one vector icon + short label per tab. The
 * tab bar is `MODE_FIXED` + `tabMinWidth=0` so all six tabs distribute
 * across a 360 dp screen with no horizontal scroll.
 *
 * Range, mood filter and active tab are persisted via
 * `onSaveInstanceState` (no new pref keys — Phase 9 Decision #2).
 */
class DashboardActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityDashboardBinding::inflate)
    private var currentRange: IkdAggregator.Range = IkdAggregator.Range.WEEK

    /** Phase 9.4: per-screen-instance state — null means "All". */
    private var currentMoodFilter: Int? = null

    /** Phase 9.4: handles to the seven chips so the click handler can hand-roll mutual exclusivity. */
    private val moodFilterChips: MutableList<Chip> = mutableListOf()

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

        setupListeners()
        setupMoodFilterChips()
        setupPagerAndToggle(savedInstanceState)
        setupWidgetInfo()
    }

    /**
     * Phase 9.13: wire the header info icons to themed
     * [WidgetInfoDialog] popups. Phase 9.14.1: the KPI strip info icon is
     * gone — its info copy now belongs on the Summary tab's KPI grid (a
     * future per-tile follow-up; the existing strip-level copy is kept in
     * `strings_widget_info.xml` for reuse). The range and mood-filter
     * icons stay on the activity chrome.
     */
    private fun setupWidgetInfo() {
        binding.dashboardRangeInfoButton.attachWidgetInfo(
            WidgetInfo(
                titleRes = R.string.info_global_range_title,
                descriptionRes = R.string.info_global_range_desc,
                interpretationRes = R.string.info_global_range_interpretation,
                formulaRes = null,
            ),
        )
        binding.dashboardMoodFilterInfoButton.attachWidgetInfo(
            WidgetInfo(
                titleRes = R.string.info_global_mood_filter_title,
                descriptionRes = R.string.info_global_mood_filter_desc,
                interpretationRes = R.string.info_global_mood_filter_interpretation,
                formulaRes = null,
            ),
        )
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.dashboardAppbar, NavigationIcon.Arrow)
        binding.apply {
            updateTextColors(dashboardGlobalHeader)
        }
        binding.dashboardEmptyMessage.setTextColor(getProperTextColor())
        applyToggleGroupColors(binding.dashboardRangeGroup)
        applyTabLayoutColors()
        applyChipColors()
        // Phase 9.4: refresh chip-row visibility on every onResume — the
        // user may have just recorded their first mood entry.
        refreshMoodFilterAvailability()
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
     * drive `selectTab` — replacing the manual hand-roll from Phase
     * 9.12. Each tab gets a vector icon and a short label; in
     * `MODE_FIXED` with `tabMinWidth=0` Material distributes the six
     * tabs evenly across the screen at 360 dp / 6 = 60 dp per tab,
     * which fits at default font size (Section 4 — width verification).
     */
    private fun setupPagerAndToggle(savedInstanceState: Bundle?) {
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
     * Phase 9.14.2: TabLayout indicator + selected/unselected text
     * colours follow the user's Fossify primary token. The icon tint
     * comes from the `dashboard_tab_icon_tint.xml` selector — runtime
     * theming for the indicator/text is set per-onResume to track theme
     * changes.
     */
    private fun applyTabLayoutColors() {
        val primary = getProperPrimaryColor()
        binding.dashboardTabLayout.setSelectedTabIndicatorColor(primary)
        binding.dashboardTabLayout.setTabTextColors(getProperTextColor(), primary)
    }

    private fun tabIconResFor(position: Int): Int = when (position) {
        DashboardPagerAdapter.TAB_SUMMARY -> R.drawable.ic_dashboard_summary_vector
        DashboardPagerAdapter.TAB_TRENDS -> R.drawable.ic_dashboard_trends_vector
        DashboardPagerAdapter.TAB_DAILY_ACTIVITY -> R.drawable.ic_dashboard_activity_vector
        DashboardPagerAdapter.TAB_MOOD -> R.drawable.ic_dashboard_mood_vector
        DashboardPagerAdapter.TAB_KEYSTROKE_DYNAMICS -> R.drawable.ic_dashboard_keystrokes_vector
        DashboardPagerAdapter.TAB_HABITS -> R.drawable.ic_dashboard_habits_vector
        else -> R.drawable.ic_dashboard_summary_vector
    }

    private fun tabLabelResFor(position: Int): Int = when (position) {
        DashboardPagerAdapter.TAB_SUMMARY -> R.string.dashboard_tab_label_summary
        DashboardPagerAdapter.TAB_TRENDS -> R.string.dashboard_tab_label_trends
        DashboardPagerAdapter.TAB_DAILY_ACTIVITY -> R.string.dashboard_tab_label_daily_activity
        DashboardPagerAdapter.TAB_MOOD -> R.string.dashboard_tab_label_mood
        DashboardPagerAdapter.TAB_KEYSTROKE_DYNAMICS -> R.string.dashboard_tab_label_keystroke_dynamics
        DashboardPagerAdapter.TAB_HABITS -> R.string.dashboard_tab_label_habits
        else -> R.string.dashboard_tab_label_summary
    }

    /**
     * Phase 9.12: shared colour application for both
     * [MaterialButtonToggleGroup]s on the screen (range toggle + tab
     * toggle). Unchecked: theme primary text + transparent background;
     * checked: contrast text on a primary-tinted background.
     */
    private fun applyToggleGroupColors(group: MaterialButtonToggleGroup) {
        val primary = getProperPrimaryColor()
        val onPrimary = primary.getContrastColor()
        val checkedState = intArrayOf(android.R.attr.state_checked)
        val uncheckedState = intArrayOf(-android.R.attr.state_checked)
        val states = arrayOf(checkedState, uncheckedState)

        val textColors = ColorStateList(states, intArrayOf(onPrimary, primary))
        val bgColors = ColorStateList(states, intArrayOf(primary, Color.TRANSPARENT))
        val strokeColors = ColorStateList(states, intArrayOf(primary, primary))

        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i) as? MaterialButton ?: continue
            child.setTextColor(textColors)
            child.backgroundTintList = bgColors
            child.strokeColor = strokeColors
        }
    }

    private fun setupListeners() {
        binding.dashboardRangeGroup.check(rangeButtonId(currentRange))
        binding.dashboardRangeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val newRange = idToRange(checkedId)
            if (newRange != currentRange) {
                currentRange = newRange
                loadSnapshot()
            }
        }
        binding.dashboardToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.dashboard_refresh -> {
                    loadSnapshot()
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
     * Phase 9.4: inflate the seven mood-filter chips (All + six emoji) and
     * wire their click handlers. Hand-rolled mutual exclusivity. State
     * machine: tap the active chip to toggle back to All.
     */
    private fun setupMoodFilterChips() {
        val container = binding.dashboardMoodFilterContainer
        val inflater = LayoutInflater.from(this)
        moodFilterChips.clear()
        container.removeAllViews()

        val allChip = inflater.inflate(R.layout.item_mood_filter_chip, container, false) as Chip
        allChip.text = getString(R.string.dashboard_mood_filter_all)
        allChip.contentDescription = getString(R.string.dashboard_mood_filter_all)
        allChip.setOnClickListener {
            if (currentMoodFilter != null) {
                currentMoodFilter = null
                refreshChipsCheckedState()
                applyChipColors()
                loadSnapshot()
            } else {
                allChip.isChecked = true
            }
        }
        container.addView(allChip)
        moodFilterChips.add(allChip)

        for (score in MoodEmoji.displayOrder()) {
            val chip = inflater.inflate(R.layout.item_mood_filter_chip, container, false) as Chip
            val emoji = MoodEmoji.emojiFor(score)
            val label = getString(MoodEmoji.labelResFor(score))
            chip.text = "$emoji $label"
            chip.contentDescription = label
            chip.setOnClickListener {
                currentMoodFilter = if (currentMoodFilter == score) null else score
                refreshChipsCheckedState()
                applyChipColors()
                loadSnapshot()
            }
            container.addView(chip)
            moodFilterChips.add(chip)
        }
        refreshChipsCheckedState()
    }

    private fun refreshChipsCheckedState() {
        if (moodFilterChips.isEmpty()) return
        moodFilterChips[0].isChecked = currentMoodFilter == null
        val displayOrder = MoodEmoji.displayOrder()
        for (idx in displayOrder.indices) {
            val chipIdx = idx + 1
            if (chipIdx < moodFilterChips.size) {
                moodFilterChips[chipIdx].isChecked = currentMoodFilter == displayOrder[idx]
            }
        }
    }

    private fun applyChipColors() {
        val primary = getProperPrimaryColor()
        val background = getProperBackgroundColor()
        val onPrimary = primary.getContrastColor()
        val textColor = getProperTextColor()
        val checkedState = intArrayOf(android.R.attr.state_checked)
        val uncheckedState = intArrayOf(-android.R.attr.state_checked)
        val states = arrayOf(checkedState, uncheckedState)

        val bgColors = ColorStateList(states, intArrayOf(primary, background))
        val txtColors = ColorStateList(states, intArrayOf(onPrimary, textColor))
        val strokeColors = ColorStateList(states, intArrayOf(primary, primary))
        for (chip in moodFilterChips) {
            chip.chipBackgroundColor = bgColors
            chip.setTextColor(txtColors)
            chip.chipStrokeColor = strokeColors
        }
    }

    private fun refreshMoodFilterAvailability() {
        lifecycleScope.launch {
            val available = withContext(Dispatchers.IO) {
                moodDB.hasAnyMoodEntry()
            }
            binding.dashboardMoodFilterStrip.beVisibleIf(available)
            if (!available && currentMoodFilter != null) {
                currentMoodFilter = null
                refreshChipsCheckedState()
                loadSnapshot()
            }
        }
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
     * with a smooth-scroll so the tab toggle's
     * `OnPageChangeCallback` keeps the segmented control in sync.
     */
    fun goToTab(position: Int) {
        if (position < 0 || position >= DashboardPagerAdapter.TAB_COUNT) return
        binding.dashboardViewPager.setCurrentItem(position, true)
    }

    private fun rangeButtonId(range: IkdAggregator.Range): Int = when (range) {
        IkdAggregator.Range.TODAY -> R.id.dashboard_range_today
        IkdAggregator.Range.WEEK -> R.id.dashboard_range_week
        IkdAggregator.Range.MONTH -> R.id.dashboard_range_month
        IkdAggregator.Range.ALL_TIME -> R.id.dashboard_range_all
    }

    private fun idToRange(id: Int): IkdAggregator.Range = when (id) {
        R.id.dashboard_range_today -> IkdAggregator.Range.TODAY
        R.id.dashboard_range_week -> IkdAggregator.Range.WEEK
        R.id.dashboard_range_month -> IkdAggregator.Range.MONTH
        R.id.dashboard_range_all -> IkdAggregator.Range.ALL_TIME
        else -> IkdAggregator.Range.WEEK
    }

    companion object {
        private const val STATE_RANGE = "dashboard_range"
        private const val STATE_MOOD_FILTER = "dashboard_mood_filter"
        private const val STATE_TAB_INDEX = "dashboard_tab_index"
        private const val MOOD_FILTER_ALL_SENTINEL = -1
    }
}
