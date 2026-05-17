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
import org.fossify.keyboard.adapters.BadgeCardAdapter
import org.fossify.keyboard.adapters.BadgeGroupAdapter
import org.fossify.keyboard.adapters.BadgeGroupUiModel
import org.fossify.keyboard.adapters.BadgeUiModel
import org.fossify.keyboard.databinding.FragmentDashboardAchievementsBinding
import org.fossify.keyboard.helpers.IkdBadgeCatalog
import org.fossify.keyboard.helpers.IkdBadgeEvaluator

/**
 * Phase 14 §7.2: the Achievements tab — a vertical list of per-group
 * carousels. Extends [DashboardFragment] so it picks up the existing
 * payload-host plumbing (the host activity runs the badge evaluator on
 * the same IO hop and dispatches the [DashboardPayload] here).
 *
 * Always all-time (Decision #13): the global Range / Mood filters do
 * not scope this tab — the evaluator already reads unfiltered tables and
 * the fixed "all-time" note sits at the top of the layout.
 */
class AchievementsFragment : DashboardFragment() {

    private var _binding: FragmentDashboardAchievementsBinding? = null
    private val binding get() = _binding!!

    private val groupAdapter = BadgeGroupAdapter(emptyList())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDashboardAchievementsBinding.inflate(inflater, container, false)
        binding.achievementsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.achievementsRecycler.adapter = groupAdapter
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
        val groups = buildGroupModels(result)

        val anyUnlocked = result.allUnlocked.isNotEmpty()
        view.achievementsEmptyMessage.beVisibleIf(!anyUnlocked)
        if (!anyUnlocked) {
            view.achievementsEmptyMessage.setTextColor(ctx.getProperTextColor())
        } else {
            view.achievementsEmptyMessage.beGone()
        }

        groupAdapter.submit(groups)
    }

    /**
     * Fold the static catalog + the evaluation result into one UI model
     * per v1 group. Focus index = the first not-yet-unlocked badge in
     * catalog order, or the last badge when the whole group is unlocked
     * (the "what you're working toward" card).
     */
    private fun buildGroupModels(
        result: IkdBadgeEvaluator.EvaluationResult,
    ): List<BadgeGroupUiModel> {
        val ctx = requireContext()
        val unlockedKeys = result.allUnlocked
        val unlockedAt = result.unlockedAtByKey

        return IkdBadgeCatalog.GROUPS.map { group ->
            val defs = IkdBadgeCatalog.badgesFor(group)
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
                )
            }
            val focusIndex = badges.indexOfFirst { !it.isUnlocked }
                .let { if (it < 0) badges.size - 1 else it }
            BadgeGroupUiModel(
                titleRes = group.titleRes,
                badges = badges,
                unlockedCount = badges.count { it.isUnlocked },
                focusIndex = focusIndex,
                recentDayQualified = if (group.hasDayStrip) {
                    result.snapshot.recentDayQualified
                } else {
                    null
                },
            )
        }
    }
}
