package org.fossify.keyboard.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.keyboard.R
import org.fossify.keyboard.databinding.ItemBadgeListHeaderBinding
import org.fossify.keyboard.databinding.ItemBadgeListRowBinding
import org.fossify.keyboard.helpers.IkdBadgeCatalog.BadgeProgress
import org.fossify.keyboard.helpers.IkdBadgeCatalog.UnitKind
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

/**
 * Phase 14: one immutable badge as rendered on the Achievements list /
 * Summary tile. Built by `AchievementsFragment` from the catalog + the
 * `EvaluationResult` payload.
 *
 * @property isUnlocked drives the row visual state (1dp primary inset +
 *   date vs. greyed emoji + progress bar). [unlockedAt] is the persisted
 *   timestamp for the date caption; it may be absent even when
 *   [isUnlocked] is true (defensive), in which case the row still renders
 *   in its unlocked style but with no date.
 * @property progress null only for the (deferred) boolean badges; all
 *   28 v1 badges carry a non-null progress.
 */
data class BadgeUiModel(
    val key: String,
    val emoji: String,
    val title: String,
    val description: String,
    val isUnlocked: Boolean,
    val unlockedAt: Long?,
    val progress: BadgeProgress?,
)

/**
 * Phase 14 (Option B): one row of the Achievements list — either a group
 * section header (optionally carrying the Daily-devotion 14-day strip) or
 * a full-width badge row. The fragment flattens the catalog into this
 * ordered list of [Item]s; the adapter renders three view types.
 *
 * @property titleRes group header label (uppercase, primary-tinted).
 * @property unlockedCount / [total] feed the trailing "u / t" count.
 * @property dayStrip non-null only on the Daily-devotion header — the
 *   last-14-days qualifying booleans for [org.fossify.keyboard.views.IkdBadgeDayStripView].
 */
sealed interface BadgeListItem {
    data class Header(
        val titleRes: Int,
        val unlockedCount: Int,
        val total: Int,
        val dayStrip: List<Boolean>?,
    ) : BadgeListItem

    data class Row(val badge: BadgeUiModel) : BadgeListItem
}

/**
 * Phase 14 (Option B): the flat, multi-view-type Achievements list
 * adapter. Replaces the per-group horizontal carousel
 * (`BadgeGroupAdapter` + `BadgeCardAdapter`). Two view types:
 *
 *  - [TYPE_HEADER] — `item_badge_list_header.xml`: the group name +
 *    `unlocked/total`. The Daily-devotion header additionally reveals the
 *    embedded [org.fossify.keyboard.views.IkdBadgeDayStripView] card so
 *    the strip always sits directly under that group's header (the third
 *    "view type" is folded into the header layout, keeping order trivial).
 *  - [TYPE_ROW] — `item_badge_list_row.xml`: a dense full-width badge row
 *    (emoji · title/desc/progress · state).
 *
 * Theme: MaterialCardView's default `?attr/colorSurface` does not track
 * Fossify's runtime theme, so the row background / inset stroke / bar
 * tints are applied here at bind time — same discipline as the Summary
 * mood tiles and the carousel it replaces.
 */
class BadgeListAdapter(
    private var items: List<BadgeListItem>,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    fun submit(newItems: List<BadgeListItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is BadgeListItem.Header -> TYPE_HEADER
        is BadgeListItem.Row -> TYPE_ROW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderVH(ItemBadgeListHeaderBinding.inflate(inflater, parent, false))
        } else {
            RowVH(ItemBadgeListRowBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is BadgeListItem.Header -> (holder as HeaderVH).bind(item)
            is BadgeListItem.Row -> (holder as RowVH).bind(item.badge)
        }
    }

    class HeaderVH(private val binding: ItemBadgeListHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(model: BadgeListItem.Header) {
            val ctx = binding.root.context
            binding.badgeGroupTitle.text = ctx.getString(model.titleRes)
            binding.badgeGroupTitle.setTextColor(ctx.getProperPrimaryColor())
            binding.badgeGroupCount.text = ctx.getString(
                R.string.achievements_group_count_format,
                model.unlockedCount,
                model.total,
            )
            binding.badgeGroupCount.setTextColor(ctx.getProperTextColor())

            val strip = model.dayStrip
            if (strip == null) {
                binding.badgeGroupDayStripCard.visibility = View.GONE
            } else {
                binding.badgeGroupDayStripCard.visibility = View.VISIBLE
                binding.badgeGroupDayStripCard.setCardBackgroundColor(
                    ctx.getProperBackgroundColor(),
                )
                binding.badgeGroupDayStripLabel.setTextColor(ctx.getProperTextColor())
                binding.badgeGroupDayStrip.setData(strip)
            }
        }
    }

    class RowVH(private val binding: ItemBadgeListRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(model: BadgeUiModel) {
            val ctx = binding.root.context
            binding.badgeRowEmoji.text = model.emoji
            binding.badgeRowTitle.text = model.title
            binding.badgeRowDesc.text = model.description

            // All colours come from the active Fossify runtime theme (same
            // discipline as SummaryFragment / EventFeed) — never fixed
            // colour tokens, which only track light/night and ignore a
            // user's custom theme. Locked-ness is conveyed by alpha only.
            val textColor = ctx.getProperTextColor()
            binding.badgeRowTitle.setTextColor(textColor)
            binding.badgeRowDesc.setTextColor(textColor)

            val card = binding.root as MaterialCardView
            card.setCardBackgroundColor(ctx.getProperBackgroundColor())

            if (model.isUnlocked) {
                binding.badgeRowEmoji.alpha = 1f
                binding.badgeRowTitle.alpha = 1f
                binding.badgeRowDesc.alpha = 1f
                binding.badgeRowProgressBar.visibility = View.GONE
                val at = model.unlockedAt
                if (at != null && at > 0L) {
                    binding.badgeRowState.visibility = View.VISIBLE
                    val dateStr = DateFormat
                        .getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
                        .format(Date(at))
                    binding.badgeRowState.text =
                        ctx.getString(R.string.achievements_unlocked_date_format, dateStr)
                    binding.badgeRowState.setTextColor(ctx.getProperPrimaryColor())
                } else {
                    binding.badgeRowState.visibility = View.GONE
                }
                card.strokeWidth = ctx.resources
                    .getDimensionPixelSize(R.dimen.summary_mood_tile_stroke_active)
                card.setStrokeColor(ctx.getProperPrimaryColor())
            } else {
                binding.badgeRowEmoji.alpha = LOCKED_EMOJI_ALPHA
                binding.badgeRowTitle.alpha = LOCKED_CONTENT_ALPHA
                binding.badgeRowDesc.alpha = LOCKED_CONTENT_ALPHA
                card.strokeWidth = 0
                bindProgress(model.progress)
            }
        }

        private fun bindProgress(progress: BadgeProgress?) {
            val ctx = binding.root.context
            if (progress == null) {
                // Boolean badge (deferred group 2) — no bar, no caption.
                binding.badgeRowProgressBar.visibility = View.GONE
                binding.badgeRowState.visibility = View.GONE
                return
            }
            binding.badgeRowProgressBar.visibility = View.VISIBLE
            val current = progress.current.coerceAtMost(progress.target)
            val pct = if (progress.target <= 0L) {
                0
            } else {
                ((current * PCT_MAX) / progress.target).toInt().coerceIn(0, PCT_MAX)
            }
            val primary = ctx.getProperPrimaryColor()
            binding.badgeRowProgressBar.progress = pct
            binding.badgeRowProgressBar.progressTintList =
                ColorStateList.valueOf(primary)
            binding.badgeRowProgressBar.progressBackgroundTintList =
                ColorStateList.valueOf(primary.adjustAlpha(PROGRESS_TRACK_ALPHA))

            val nf = NumberFormat.getIntegerInstance(Locale.getDefault())
            binding.badgeRowState.visibility = View.VISIBLE
            binding.badgeRowState.text = when (progress.unitKind) {
                UnitKind.DAYS -> ctx.getString(
                    R.string.achievements_progress_days_format,
                    current.toInt(),
                    progress.target.toInt(),
                )
                UnitKind.COUNT -> ctx.getString(
                    R.string.achievements_progress_count_format,
                    nf.format(current),
                    nf.format(progress.target),
                )
            }
            binding.badgeRowState.setTextColor(
                ctx.getProperTextColor().adjustAlpha(STATE_FAINT_ALPHA),
            )
        }

        companion object {
            private const val LOCKED_EMOJI_ALPHA = 0.55f
            private const val LOCKED_CONTENT_ALPHA = 0.6f
            private const val PROGRESS_TRACK_ALPHA = 0.25f
            private const val STATE_FAINT_ALPHA = 0.6f
            private const val PCT_MAX = 100
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ROW = 1
    }
}
