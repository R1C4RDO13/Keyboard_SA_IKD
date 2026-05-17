package org.fossify.keyboard.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.keyboard.R
import org.fossify.keyboard.databinding.ItemBadgeGroupBinding

/**
 * Phase 14 §7.2: one immutable group row for the Achievements vertical
 * list — a header + a horizontal carousel of [badges] focused on
 * [focusIndex] (the first not-yet-unlocked badge, else the last).
 *
 * @property recentDayQualified non-null only for the Daily-devotion
 *   group (drives the 14-day mini-keyboard strip under that group's
 *   card); null hides the strip card for every other group.
 */
data class BadgeGroupUiModel(
    val titleRes: Int,
    val badges: List<BadgeUiModel>,
    val unlockedCount: Int,
    val focusIndex: Int,
    val recentDayQualified: List<Boolean>?,
)

/**
 * Phase 14 §7.2: the parent vertical RecyclerView adapter — one VH per
 * v1 group. Each VH inflates `item_badge_group.xml` (header + ‹/›
 * ImageButtons + nested horizontal ViewPager2 + page dots + optional
 * devotion day-strip).
 *
 * Per-group carousel position is held in [pagePositions] keyed on the
 * group title so swiping a group, scrolling away, and scrolling back
 * keeps that group on the same badge (Decision #14 — each group keeps
 * its own carousel position across config changes / rebinds).
 */
class BadgeGroupAdapter(
    private var groups: List<BadgeGroupUiModel>,
) : RecyclerView.Adapter<BadgeGroupAdapter.VH>() {

    private val pagePositions = HashMap<Int, Int>()

    fun submit(newGroups: List<BadgeGroupUiModel>) {
        groups = newGroups
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemBadgeGroupBinding.inflate(
            LayoutInflater.from(parent.context), parent, false,
        )
        return VH(binding, pagePositions)
    }

    override fun getItemCount(): Int = groups.size

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(groups[position])

    class VH(
        private val binding: ItemBadgeGroupBinding,
        private val pagePositions: HashMap<Int, Int>,
    ) : RecyclerView.ViewHolder(binding.root) {

        private val cardAdapter = BadgeCardAdapter(emptyList())
        private var pageCallback: ViewPager2.OnPageChangeCallback? = null

        init {
            binding.badgeGroupPager.adapter = cardAdapter
            // Peek the neighbouring cards so the carousel reads as a
            // pageable strip (clipToPadding=false + a horizontal pad).
            (binding.badgeGroupPager.getChildAt(0) as? RecyclerView)?.apply {
                clipToPadding = false
                val pad = resources.getDimensionPixelSize(
                    org.fossify.commons.R.dimen.normal_margin,
                )
                setPadding(pad, 0, pad, 0)
            }
        }

        fun bind(model: BadgeGroupUiModel) {
            val ctx = binding.root.context
            val primary = ctx.getProperPrimaryColor()

            binding.badgeGroupTitle.text = ctx.getString(model.titleRes)
            binding.badgeGroupTitle.setTextColor(primary)
            binding.badgeGroupCount.text = ctx.getString(
                R.string.achievements_group_count_format,
                model.unlockedCount,
                model.badges.size,
            )

            cardAdapter.submit(model.badges)
            buildDots(model.badges.size)

            val key = model.titleRes
            val startPos = pagePositions[key] ?: model.focusIndex
            val clamped = startPos.coerceIn(0, (model.badges.size - 1).coerceAtLeast(0))
            binding.badgeGroupPager.setCurrentItem(clamped, false)
            updateArrowsAndDots(clamped, model.badges.size, primary)

            pageCallback?.let { binding.badgeGroupPager.unregisterOnPageChangeCallback(it) }
            val cb = object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    pagePositions[key] = position
                    updateArrowsAndDots(position, model.badges.size, primary)
                }
            }
            pageCallback = cb
            binding.badgeGroupPager.registerOnPageChangeCallback(cb)

            binding.badgeGroupPrev.setOnClickListener {
                val cur = binding.badgeGroupPager.currentItem
                if (cur > 0) binding.badgeGroupPager.setCurrentItem(cur - 1, true)
            }
            binding.badgeGroupNext.setOnClickListener {
                val cur = binding.badgeGroupPager.currentItem
                if (cur < model.badges.size - 1) {
                    binding.badgeGroupPager.setCurrentItem(cur + 1, true)
                }
            }
            binding.badgeGroupPrev.setColorFilter(primary)
            binding.badgeGroupNext.setColorFilter(primary)

            bindDayStrip(model)
        }

        private fun bindDayStrip(model: BadgeGroupUiModel) {
            val strip = model.recentDayQualified
            if (strip == null) {
                binding.badgeGroupDayStripCard.visibility = View.GONE
                return
            }
            val ctx = binding.root.context
            binding.badgeGroupDayStripCard.visibility = View.VISIBLE
            binding.badgeGroupDayStripCard.setCardBackgroundColor(
                ContextCompat.getColor(ctx, R.color.badge_locked_surface),
            )
            binding.badgeGroupDayStrip.setData(strip)
        }

        private fun buildDots(count: Int) {
            val container = binding.badgeGroupDots
            container.removeAllViews()
            val ctx = container.context
            val size = ctx.resources.getDimensionPixelSize(R.dimen.badge_dot_size)
            val gap = ctx.resources.getDimensionPixelSize(R.dimen.badge_dot_gap)
            repeat(count) {
                val dot = View(ctx)
                val lp = LinearLayout.LayoutParams(size, size)
                lp.marginStart = gap
                lp.marginEnd = gap
                dot.layoutParams = lp
                dot.setBackgroundResource(R.drawable.badge_dot)
                container.addView(dot)
            }
        }

        private fun updateArrowsAndDots(position: Int, count: Int, primary: Int) {
            binding.badgeGroupPrev.isEnabled = position > 0
            binding.badgeGroupPrev.alpha = if (position > 0) 1f else DISABLED_ALPHA
            binding.badgeGroupNext.isEnabled = position < count - 1
            binding.badgeGroupNext.alpha =
                if (position < count - 1) 1f else DISABLED_ALPHA

            val container = binding.badgeGroupDots
            for (i in 0 until container.childCount) {
                val dot = container.getChildAt(i)
                dot.isSelected = i == position
                dot.alpha = if (i == position) 1f else INACTIVE_DOT_ALPHA
            }
        }

        companion object {
            private const val DISABLED_ALPHA = 0.2f
            private const val INACTIVE_DOT_ALPHA = 0.35f
        }
    }
}
