package org.fossify.keyboard.activities

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
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
import org.fossify.keyboard.helpers.IkdHabitsAggregator
import org.fossify.keyboard.helpers.MoodEmoji
import java.util.Locale

/**
 * Phase 9.11/9.12: the Insights screen is now a thin host. The activity owns
 *  - the global header (KPI strip, tab toggle, range toggle, mood-filter chip row),
 *  - the data hop (one `Dispatchers.IO` round-trip per `loadSnapshot`),
 *  - the empty-state view,
 *
 * and the rest lives in five `DashboardFragment` subclasses driven by a
 * [DashboardPagerAdapter] backing a [ViewPager2]. Phase 9.12 dropped the
 * NavigationRail in favour of a top-of-screen
 * [MaterialButtonToggleGroup] styled identically to the range toggle —
 * the user wanted the tab strip on top.
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
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.dashboardAppbar, NavigationIcon.Arrow)
        binding.apply {
            updateTextColors(dashboardGlobalHeader)
        }
        binding.dashboardEmptyMessage.setTextColor(getProperTextColor())
        applyToggleGroupColors(binding.dashboardRangeGroup)
        applyToggleGroupColors(binding.dashboardTabGroup)
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
     * Phase 9.12: wire the top tab toggle group to the ViewPager2 in both
     * directions. Toggle taps drive `setCurrentItem`; pager scrolls
     * (manual swipe) drive `check(buttonId)` so the toggle tracks the
     * current page. Restore the last tab index from `savedInstanceState`.
     */
    private fun setupPagerAndToggle(savedInstanceState: Bundle?) {
        pagerAdapter = DashboardPagerAdapter(this)
        binding.dashboardViewPager.adapter = pagerAdapter
        // Keep all five fragments in memory so swipe re-renders are instant
        // and the legend strips / chart caches survive page changes.
        binding.dashboardViewPager.offscreenPageLimit = DashboardPagerAdapter.TAB_COUNT - 1

        binding.dashboardTabGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val target = tabButtonIdToTabIndex(checkedId)
            if (target >= 0 && target != binding.dashboardViewPager.currentItem) {
                binding.dashboardViewPager.currentItem = target
            }
        }

        binding.dashboardViewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    val buttonId = tabIndexToTabButtonId(position)
                    if (binding.dashboardTabGroup.checkedButtonId != buttonId) {
                        binding.dashboardTabGroup.check(buttonId)
                    }
                }
            }
        )

        val initialTab = savedInstanceState?.getInt(STATE_TAB_INDEX, DashboardPagerAdapter.TAB_TRENDS)
            ?: DashboardPagerAdapter.TAB_TRENDS
        binding.dashboardViewPager.setCurrentItem(initialTab, false)
        binding.dashboardTabGroup.check(tabIndexToTabButtonId(initialTab))
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

        renderKpiStrip(payload)

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
     * Phase 9.12: 6-cell global KPI strip. Sessions · Total typing time ·
     * Avg WPM · Error rate · Avg session duration · Longest streak.
     * The last two cells are pulled from the Habits aggregator output —
     * which the activity already gathers on its single Dispatchers.IO
     * hop — and replace the per-tab Habits KPI strip.
     */
    private fun renderKpiStrip(payload: DashboardPayload) {
        val placeholder = getString(R.string.dashboard_value_placeholder)
        val locale = Locale.getDefault()
        val snap = payload.ikd
        val habits = payload.habits
        val minutes = snap.totalTypingTimeMs.toDouble() / MS_PER_MINUTE

        binding.dashboardKpiSessionsValue.text = snap.totalSessions.toString()
        binding.dashboardKpiTypingTimeValue.text = if (snap.totalTypingTimeMs <= 0L) {
            placeholder
        } else {
            getString(R.string.dashboard_kpi_typing_time_value, String.format(locale, "%.1f", minutes))
        }
        binding.dashboardKpiWpmValue.text = snap.avgWpm
            ?.let { getString(R.string.dashboard_kpi_wpm_value, it) } ?: placeholder
        binding.dashboardKpiErrorRateValue.text = snap.avgErrorRatePct
            ?.let { getString(R.string.dashboard_kpi_error_rate_value, it) } ?: placeholder

        binding.dashboardKpiAvgSessionValue.text = habits.avgSessionDurationMs?.let {
            getString(R.string.dashboard_habits_avg_session_value, it / MS_PER_SECOND)
        } ?: placeholder

        binding.dashboardKpiStreakValue.text = when (habits.streakUnit) {
            IkdHabitsAggregator.StreakUnit.HOURS -> {
                if (habits.totalSessions > 0) {
                    getString(R.string.dashboard_habits_streak_today)
                } else {
                    placeholder
                }
            }
            IkdHabitsAggregator.StreakUnit.DAYS -> getString(
                R.string.dashboard_habits_streak_days, habits.longestStreak,
            )
            IkdHabitsAggregator.StreakUnit.WEEKS -> getString(
                R.string.dashboard_habits_streak_weeks, habits.longestStreak,
            )
        }
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

    private fun tabButtonIdToTabIndex(buttonId: Int): Int = when (buttonId) {
        R.id.dashboard_tab_trends -> DashboardPagerAdapter.TAB_TRENDS
        R.id.dashboard_tab_daily_activity -> DashboardPagerAdapter.TAB_DAILY_ACTIVITY
        R.id.dashboard_tab_mood -> DashboardPagerAdapter.TAB_MOOD
        R.id.dashboard_tab_keystroke_dynamics -> DashboardPagerAdapter.TAB_KEYSTROKE_DYNAMICS
        R.id.dashboard_tab_habits -> DashboardPagerAdapter.TAB_HABITS
        else -> -1
    }

    private fun tabIndexToTabButtonId(position: Int): Int = when (position) {
        DashboardPagerAdapter.TAB_TRENDS -> R.id.dashboard_tab_trends
        DashboardPagerAdapter.TAB_DAILY_ACTIVITY -> R.id.dashboard_tab_daily_activity
        DashboardPagerAdapter.TAB_MOOD -> R.id.dashboard_tab_mood
        DashboardPagerAdapter.TAB_KEYSTROKE_DYNAMICS -> R.id.dashboard_tab_keystroke_dynamics
        DashboardPagerAdapter.TAB_HABITS -> R.id.dashboard_tab_habits
        else -> R.id.dashboard_tab_trends
    }

    companion object {
        private const val STATE_RANGE = "dashboard_range"
        private const val STATE_MOOD_FILTER = "dashboard_mood_filter"
        private const val STATE_TAB_INDEX = "dashboard_tab_index"
        private const val MOOD_FILTER_ALL_SENTINEL = -1
        private const val MS_PER_MINUTE = 60_000L
        private const val MS_PER_SECOND = 1_000.0
    }
}
