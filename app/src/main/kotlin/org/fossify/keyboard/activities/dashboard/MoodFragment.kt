package org.fossify.keyboard.activities.dashboard

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.keyboard.R
import org.fossify.keyboard.databinding.FragmentDashboardMoodBinding
import org.fossify.keyboard.databinding.ItemMoodDistributionRowBinding
import org.fossify.keyboard.databinding.ItemMoodLegendSwatchBinding
import org.fossify.keyboard.helpers.IkdAggregator
import org.fossify.keyboard.helpers.IkdMoodAggregator
import org.fossify.keyboard.helpers.MoodEmoji
import org.fossify.keyboard.views.IkdStackedBarChartView

/**
 * Phase 9.11: Mood tab. Phase 8.3 stacked-bar mood-mix chart and Phase 8
 * Mood Distribution panel. Both cards stay GONE when `total == 0` —
 * sessions without an explicit tap have no [MoodEntry] row, which is
 * the absence-of-rating signal.
 *
 * The mood widgets stay unfiltered under a non-`All` chip (Phase 9.4
 * Decision #13). They render the degenerate one-row / one-colour result
 * — that's the user feedback that the filter is in effect.
 */
class MoodFragment : DashboardFragment() {

    private var _binding: FragmentDashboardMoodBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDashboardMoodBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun renderPayload(payload: DashboardPayload) {
        val view = _binding ?: return
        val moodSnap = payload.mood

        applyCardThemeColors()

        if (moodSnap.total == 0) {
            view.dashboardMoodStackedChartCard.beGone()
            view.dashboardMoodDistributionCard.beGone()
            view.fragmentMoodEmptyMessage.beVisible()
            return
        }

        view.fragmentMoodEmptyMessage.beGone()
        view.dashboardMoodStackedChartCard.beVisible()
        view.dashboardMoodDistributionCard.beVisible()

        bindStackedChart(payload.ikd, payload.moodMix)
        bindMoodLegend()
        renderMoodDistribution(moodSnap)
    }

    private fun applyCardThemeColors() {
        val ctx = context ?: return
        val view = _binding ?: return
        val bg = ctx.getProperBackgroundColor()
        view.dashboardMoodStackedChartCard.setCardBackgroundColor(bg)
        view.dashboardMoodDistributionCard.setCardBackgroundColor(bg)
    }

    private fun bindStackedChart(
        ikdSnap: IkdAggregator.Snapshot,
        moodMix: IkdMoodAggregator.MoodMixSnapshot,
    ) {
        val ctx = context ?: return
        val view = _binding ?: return
        val mixByBucket = moodMix.buckets.associateBy { it.label }
        val labels = ikdSnap.buckets.map {
            DashboardLabelFormat.formatBucketLabel(ctx, it.label, ikdSnap.range)
        }
        val segments = MoodEmoji.displayOrder().map { score ->
            val values = ikdSnap.buckets.map { ikdBucket ->
                val mixBucket = mixByBucket[ikdBucket.label]
                if (mixBucket == null || mixBucket.total <= 0) {
                    0f
                } else {
                    val count = mixBucket.counts[score] ?: 0
                    count.toFloat() * PCT_MAX_FLOAT / mixBucket.total.toFloat()
                }
            }
            IkdStackedBarChartView.MoodSegment(
                score = score,
                label = getString(MoodEmoji.labelResFor(score)),
                colorInt = ContextCompat.getColor(ctx, moodColorResFor(score)),
                values = values,
            )
        }
        view.dashboardChartMoodStacked.setData(labels, segments)
    }

    private fun bindMoodLegend() {
        val ctx = context ?: return
        val view = _binding ?: return
        val legend = view.dashboardMoodStackedLegend
        legend.removeAllViews()
        val inflater = LayoutInflater.from(ctx)
        val textColor = ctx.getProperTextColor()
        for (score in MoodEmoji.displayOrder()) {
            val item = ItemMoodLegendSwatchBinding.inflate(inflater, legend, false)
            item.moodLegendSwatch.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(ctx, moodColorResFor(score))
            )
            val emoji = MoodEmoji.emojiFor(score)
            val label = getString(MoodEmoji.labelResFor(score))
            item.moodLegendLabel.text = "$emoji $label"
            item.moodLegendLabel.setTextColor(textColor)
            legend.addView(item.root)
        }
    }

    @ColorRes
    private fun moodColorResFor(score: Int): Int = when (score) {
        MoodEmoji.SCORE_HAPPINESS -> R.color.mood_color_happiness
        MoodEmoji.SCORE_SURPRISE -> R.color.mood_color_surprise
        MoodEmoji.SCORE_DISGUST -> R.color.mood_color_disgust
        MoodEmoji.SCORE_SADNESS -> R.color.mood_color_sadness
        MoodEmoji.SCORE_FEAR -> R.color.mood_color_fear
        MoodEmoji.SCORE_ANGER -> R.color.mood_color_anger
        else -> R.color.mood_color_happiness
    }

    private fun renderMoodDistribution(moodSnap: IkdMoodAggregator.MoodSnapshot) {
        val view = _binding ?: return
        val rows = listOf(
            ItemMoodDistributionRowBinding.bind(view.dashboardMoodRowHappiness.root) to MoodEmoji.SCORE_HAPPINESS,
            ItemMoodDistributionRowBinding.bind(view.dashboardMoodRowSurprise.root) to MoodEmoji.SCORE_SURPRISE,
            ItemMoodDistributionRowBinding.bind(view.dashboardMoodRowDisgust.root) to MoodEmoji.SCORE_DISGUST,
            ItemMoodDistributionRowBinding.bind(view.dashboardMoodRowSadness.root) to MoodEmoji.SCORE_SADNESS,
            ItemMoodDistributionRowBinding.bind(view.dashboardMoodRowFear.root) to MoodEmoji.SCORE_FEAR,
            ItemMoodDistributionRowBinding.bind(view.dashboardMoodRowAnger.root) to MoodEmoji.SCORE_ANGER,
        )
        val total = moodSnap.total
        for ((rowBinding, score) in rows) {
            val count = moodSnap.counts[score] ?: 0
            rowBinding.moodRowEmoji.text = MoodEmoji.emojiFor(score)
            rowBinding.moodRowLabel.setText(MoodEmoji.labelResFor(score))
            rowBinding.moodRowCount.text = count.toString()
            rowBinding.moodRowProgress.progress = if (total <= 0) {
                0
            } else {
                (count * PCT_MAX / total)
            }
        }
    }

    companion object {
        private const val PCT_MAX = 100
        private const val PCT_MAX_FLOAT = 100f
    }
}
