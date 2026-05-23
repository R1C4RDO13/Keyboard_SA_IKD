package org.fossify.keyboard.activities.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.keyboard.adapters.BadgeListAdapter
import org.fossify.keyboard.adapters.BadgeListItem
import org.fossify.keyboard.adapters.BadgeUiModel
import org.fossify.keyboard.databinding.FragmentDashboardAchievementsBinding
import org.fossify.keyboard.helpers.IkdBadgeCatalog
import org.fossify.keyboard.helpers.IkdBadgeEvaluator

/**
 * Phase 14 (Option B): the Achievements tab — a single vertical list of
 * group section headers and full-width badge rows, in catalog order.
 * Extends [DashboardFragment] so it picks up the existing payload-host
 * plumbing (the host activity runs the badge evaluator on the same IO hop
 * and dispatches the [DashboardPayload] here).
 *
 * Always all-time (Decision #13): the global Range / Mood filters do
 * not scope this tab — the evaluator already reads unfiltered tables and
 * the fixed "all-time" note sits at the top of the layout.
 */
class AchievementsFragment : DashboardFragment() {

    private var _binding: FragmentDashboardAchievementsBinding? = null
    private val binding get() = _binding!!

    private val listAdapter = BadgeListAdapter(emptyList())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDashboardAchievementsBinding.inflate(inflater, container, false)
        binding.achievementsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.achievementsRecycler.adapter = listAdapter
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun renderPayload(payload: DashboardPayload) {
        val ctx = context ?: return
        val view = _binding ?: return
        (activity as? androidx.fragment.app.FragmentActivity)?.updateTextColors(view.root)

        val result = payload.badges

        val anyUnlocked = result.allUnlocked.isNotEmpty()
        view.achievementsEmptyMessage.beVisibleIf(!anyUnlocked)
        if (!anyUnlocked) {
            view.achievementsEmptyMessage.setTextColor(ctx.getProperTextColor())
        } else {
            view.achievementsEmptyMessage.beGone()
        }

        listAdapter.submit(buildItems(result))
    }

    /**
     * Flatten the static catalog + the evaluation result into one ordered
     * list: for each v1 group (in [IkdBadgeCatalog.GROUPS] order) a
     * [BadgeListItem.Header] followed by its badges as
     * [BadgeListItem.Row]s. The Daily-devotion header carries the
     * last-14-days strip so it renders directly under that header.
     */
    private fun buildItems(
        result: IkdBadgeEvaluator.EvaluationResult,
    ): List<BadgeListItem> {
        val ctx = requireContext()
        val unlockedKeys = result.allUnlocked
        val unlockedAt = result.unlockedAtByKey
        val items = ArrayList<BadgeListItem>()

        for (group in IkdBadgeCatalog.GROUPS) {
            val defs = IkdBadgeCatalog.badgesFor(group)
            val isTodayScoped = group == IkdBadgeCatalog.BadgeGroup.MOOD_DAILY_CHECKIN
            val badges = defs.map { def ->
                val unlocked = def.key in unlockedKeys
                BadgeUiModel(
                    key = def.key,
                    emoji = def.emoji,
                    title = ctx.getString(def.titleRes),
                    description = ctx.getString(def.descRes),
                    isUnlocked = unlocked,
                    unlockedAt = if (unlocked) unlockedAt[def.key] else null,
                    progress = result.progressByKey[def.key],
                    isTodayScoped = isTodayScoped,
                )
            }
            items += BadgeListItem.Header(
                titleRes = group.titleRes,
                unlockedCount = badges.count { it.isUnlocked },
                total = badges.size,
                dayStrip = if (group.hasDayStrip) {
                    result.snapshot.recentDayQualified
                } else {
                    null
                },
            )
            badges.forEach { items += BadgeListItem.Row(it) }
        }
        return items
    }
}
