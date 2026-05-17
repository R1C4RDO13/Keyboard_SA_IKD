package org.fossify.keyboard.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.content.res.ColorStateList
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.keyboard.R
import org.fossify.keyboard.databinding.ItemBadgeCardBinding
import org.fossify.keyboard.helpers.IkdBadgeCatalog.BadgeProgress
import org.fossify.keyboard.helpers.IkdBadgeCatalog.UnitKind
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase 14: one immutable badge as rendered on the Achievements
 * carousel / Summary tile. Built by `AchievementsFragment` from the
 * catalog + the `EvaluationResult` payload.
 *
 * @property isUnlocked drives the card visual state (glow + date vs.
 *   greyscale + progress bar). [unlockedAt] is the persisted timestamp
 *   for the date row; it may be absent even when [isUnlocked] is true
 *   (defensive), in which case the date row is hidden but the card still
 *   renders in its unlocked style.
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
 * Phase 14 §7.2/§7.3: the nested per-group horizontal pager of badge
 * focus cards. One viewType. The emoji is a `MyTextView` glyph (the
 * catalog stores no drawable — Decision #3), so a locked badge is dimmed
 * to alpha 0.55 rather than ColorMatrix-desaturated (a font emoji's
 * colour is not paint-tintable); a locked card also shows a determinate
 * progress bar + grouped `current / target` caption. Unlocked cards swap
 * that for a glow border + unlock date and full-opacity emoji.
 *
 * Theme: MaterialCardView's default `?attr/colorSurface` does not track
 * Fossify's runtime theme, so the card background / glow / bar tints are
 * applied here at bind time — same discipline as the Summary mood tiles.
 */
class BadgeCardAdapter(
    private var items: List<BadgeUiModel>,
) : RecyclerView.Adapter<BadgeCardAdapter.VH>() {

    fun submit(newItems: List<BadgeUiModel>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemBadgeCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false,
        )
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(items[position])

    class VH(private val binding: ItemBadgeCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(model: BadgeUiModel) {
            val ctx = binding.root.context
            binding.badgeCardTitle.text = model.title
            binding.badgeCardDesc.text = model.description
            binding.badgeCardEmoji.text = model.emoji

            // All colours come from the active Fossify runtime theme (same
            // discipline as SummaryFragment / EventFeed) — never fixed
            // colour tokens, which only track light/night and ignore a
            // user's custom theme. Locked-ness is conveyed by alpha only.
            val textColor = ctx.getProperTextColor()
            binding.badgeCardTitle.setTextColor(textColor)
            binding.badgeCardDesc.setTextColor(textColor)

            val card = binding.root as MaterialCardView
            card.setCardBackgroundColor(ctx.getProperBackgroundColor())

            if (model.isUnlocked) {
                binding.badgeCardEmoji.alpha = 1f
                binding.badgeCardTitle.alpha = 1f
                binding.badgeCardDesc.alpha = 1f
                binding.badgeCardProgressRow.visibility = ViewGroup.GONE
                val at = model.unlockedAt
                if (at != null && at > 0L) {
                    binding.badgeCardUnlockDate.visibility = ViewGroup.VISIBLE
                    val dateStr = DateFormat
                        .getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
                        .format(Date(at))
                    binding.badgeCardUnlockDate.text =
                        ctx.getString(R.string.achievements_unlocked_date_format, dateStr)
                    binding.badgeCardUnlockDate.setTextColor(ctx.getProperPrimaryColor())
                } else {
                    binding.badgeCardUnlockDate.visibility = ViewGroup.GONE
                }
                card.strokeWidth = ctx.resources
                    .getDimensionPixelSize(R.dimen.summary_mood_tile_stroke_active)
                card.setStrokeColor(ctx.getProperPrimaryColor())
            } else {
                binding.badgeCardEmoji.alpha = LOCKED_EMOJI_ALPHA
                binding.badgeCardTitle.alpha = LOCKED_CONTENT_ALPHA
                binding.badgeCardDesc.alpha = LOCKED_CONTENT_ALPHA
                binding.badgeCardUnlockDate.visibility = ViewGroup.GONE
                card.strokeWidth = 0
                bindProgress(model.progress)
            }
        }

        private fun bindProgress(progress: BadgeProgress?) {
            val ctx = binding.root.context
            if (progress == null) {
                // Boolean badge (deferred group 2) — no bar.
                binding.badgeCardProgressRow.visibility = ViewGroup.GONE
                return
            }
            binding.badgeCardProgressRow.visibility = ViewGroup.VISIBLE
            val current = progress.current.coerceAtMost(progress.target)
            val pct = if (progress.target <= 0L) {
                0
            } else {
                ((current * PCT_MAX) / progress.target).toInt().coerceIn(0, PCT_MAX)
            }
            val primary = ctx.getProperPrimaryColor()
            binding.badgeCardProgressBar.progress = pct
            binding.badgeCardProgressBar.progressTintList =
                ColorStateList.valueOf(primary)
            binding.badgeCardProgressBar.progressBackgroundTintList =
                ColorStateList.valueOf(primary.adjustAlpha(PROGRESS_TRACK_ALPHA))
            val nf = java.text.NumberFormat.getIntegerInstance(Locale.getDefault())
            binding.badgeCardProgressText.text = when (progress.unitKind) {
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
            binding.badgeCardProgressText.setTextColor(ctx.getProperTextColor())
        }

        companion object {
            private const val LOCKED_EMOJI_ALPHA = 0.55f
            private const val LOCKED_CONTENT_ALPHA = 0.6f
            private const val PROGRESS_TRACK_ALPHA = 0.25f
            private const val PCT_MAX = 100
        }
    }
}
