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
 * Phase 14 added the Achievements tab as an **interim** 6th tab. Phase 15
 * (Insights IA v2) collapses that to the final five-tab order and drops
 * the Habits tab entirely — Habits' two surviving charts were relocated
 * (Avg session duration → Trends, Avg flight time → Keys); the other
 * three Habits widgets + the Calendar heatmap were removed.
 *
 * Final order (Phase 15 §1):
 *  0 Summary · 1 Achievements · 2 Activity · 3 Trends · 4 Keys
 */
class DashboardPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = TAB_COUNT

    override fun createFragment(position: Int): Fragment = when (position) {
        TAB_SUMMARY -> SummaryFragment()
        TAB_ACHIEVEMENTS -> AchievementsFragment()
        TAB_ACTIVITY -> DailyActivityFragment()
        TAB_TRENDS -> TrendsFragment()
        TAB_KEYS -> KeystrokeDynamicsFragment()
        else -> error("Unknown tab position $position")
    }

    companion object {
        const val TAB_COUNT = 5
        const val TAB_SUMMARY = 0
        const val TAB_ACHIEVEMENTS = 1
        const val TAB_ACTIVITY = 2
        const val TAB_TRENDS = 3
        const val TAB_KEYS = 4
    }
}
