package org.fossify.keyboard.activities.dashboard

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * Phase 9.11: backs the Insights ViewPager2. Five fragments, one per
 * NavigationRail item. Order matters — top-to-bottom on the rail =
 * left-to-right page order:
 *
 *  0 Trends · 1 Daily Activity · 2 Mood · 3 Keystroke Dynamics · 4 Habits
 */
class DashboardPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = TAB_COUNT

    override fun createFragment(position: Int): Fragment = when (position) {
        TAB_TRENDS -> TrendsFragment()
        TAB_DAILY_ACTIVITY -> DailyActivityFragment()
        TAB_MOOD -> MoodFragment()
        TAB_KEYSTROKE_DYNAMICS -> KeystrokeDynamicsFragment()
        TAB_HABITS -> HabitsFragment()
        else -> error("Unknown tab position $position")
    }

    companion object {
        const val TAB_COUNT = 5
        const val TAB_TRENDS = 0
        const val TAB_DAILY_ACTIVITY = 1
        const val TAB_MOOD = 2
        const val TAB_KEYSTROKE_DYNAMICS = 3
        const val TAB_HABITS = 4
    }
}
