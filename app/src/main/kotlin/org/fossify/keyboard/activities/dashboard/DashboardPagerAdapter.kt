package org.fossify.keyboard.activities.dashboard

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * Phase 9.11: backs the Insights ViewPager2.
 *
 * Phase 9.14.1: Summary fragment inserted at index 0 as the new landing
 * page. Tabs were Summary · Trends · Activity · Mood · Keystrokes · Habits.
 *
 * Phase 9.15: the Mood tab is dropped — its two widgets (Mood Mix,
 * Mood Distribution) move into the Summary tab. The host activity
 * addresses tabs through the stable `TAB_*` constants below — Decision
 * #15 of the Phase 9.14 plan.
 *
 *  0 Summary · 1 Trends · 2 Daily Activity · 3 Keystroke Dynamics · 4 Habits
 */
class DashboardPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = TAB_COUNT

    override fun createFragment(position: Int): Fragment = when (position) {
        TAB_SUMMARY -> SummaryFragment()
        TAB_TRENDS -> TrendsFragment()
        TAB_DAILY_ACTIVITY -> DailyActivityFragment()
        TAB_KEYSTROKE_DYNAMICS -> KeystrokeDynamicsFragment()
        TAB_HABITS -> HabitsFragment()
        else -> error("Unknown tab position $position")
    }

    companion object {
        const val TAB_COUNT = 5
        const val TAB_SUMMARY = 0
        const val TAB_TRENDS = 1
        const val TAB_DAILY_ACTIVITY = 2
        const val TAB_KEYSTROKE_DYNAMICS = 3
        const val TAB_HABITS = 4
    }
}
