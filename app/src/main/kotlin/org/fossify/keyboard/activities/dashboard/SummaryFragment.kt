package org.fossify.keyboard.activities.dashboard

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.keyboard.R
import org.fossify.keyboard.activities.DashboardActivity
import org.fossify.keyboard.databinding.FragmentDashboardSummaryBinding
import org.fossify.keyboard.databinding.ItemMoodDistributionRowBinding
import org.fossify.keyboard.databinding.ItemMoodLegendSwatchBinding
import org.fossify.keyboard.databinding.ItemSummaryKpiTileBinding
import org.fossify.keyboard.helpers.IkdActivityAggregator
import org.fossify.keyboard.helpers.IkdAggregator
import org.fossify.keyboard.helpers.IkdHabitsAggregator
import org.fossify.keyboard.helpers.IkdMoodAggregator
import org.fossify.keyboard.helpers.MoodEmoji
import org.fossify.keyboard.helpers.WidgetInfo
import org.fossify.keyboard.helpers.attachWidgetInfo
import org.fossify.keyboard.views.IkdBubbleMapView
import org.fossify.keyboard.views.IkdStackedBarChartView
import java.util.Locale

/**
 * Phase 9.15: Summary tab — the Insights landing page.
 *
 * Contents (top → bottom):
 *  - **3x2 KPI grid** (Sessions / Typing time / WPM | Error rate / Avg
 *    session / Streak). Each tile is label + big value only — sparkline
 *    strip and delta line introduced as Phase 9.14.4 placeholders are
 *    retired (Phase 9.15 decision #2 — explicit user feedback).
 *  - **Usage Map** (`IkdBubbleMapView`) — moved here from the Daily
 *    Activity tab and rendered at 240 dp height (decision #4). Its
 *    Daily Activity card is deleted; this is the only render site now.
 *  - **Mood Mix over Time** + **Mood Distribution** — moved here from
 *    the deleted Mood tab. Both cards stay `View.GONE` when
 *    `payload.mood.total == 0`.
 *
 * Tile click routing (preserved from Phase 9.14.1 decision #7):
 *  - Sessions / Typing time / Avg session / Streak → Habits tab
 *  - WPM / Error rate                              → Trends tab
 *
 * The aggregator surface is reused as-is — values come from the host
 * activity's existing `Dispatchers.IO` hop via
 * [DashboardActivity.latestPayload]. No new SQL, no new aggregator.
 */
class SummaryFragment : DashboardFragment() {

    private var _binding: FragmentDashboardSummaryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDashboardSummaryBinding.inflate(inflater, container, false)
        wireTileClicks()
        attachWidgetInfoButtons()
        return binding.root
    }

    /**
     * Phase 9.14.1 decision #7: each tile is a clickable jump to the
     * deeper tab that owns the underlying chart. Routed through the host
     * activity's `goToTab(position)` so the index lookup stays
     * centralised and any tab-renumbering shows up in one place.
     */
    private fun wireTileClicks() {
        val view = _binding ?: return
        val toHabits = View.OnClickListener { goToTab(DashboardPagerAdapter.TAB_HABITS) }
        val toTrends = View.OnClickListener { goToTab(DashboardPagerAdapter.TAB_TRENDS) }
        view.summaryTileSessions.setOnClickListener(toHabits)
        view.summaryTileTypingTime.setOnClickListener(toHabits)
        view.summaryTileAvgSession.setOnClickListener(toHabits)
        view.summaryTileStreak.setOnClickListener(toHabits)
        view.summaryTileWpm.setOnClickListener(toTrends)
        view.summaryTileErrorRate.setOnClickListener(toTrends)
    }

    /**
     * Phase 9.13: bind tap-to-explain dialogs to each card's info icon.
     * Phase 9.15: re-attached at the new render sites in Summary
     * (Usage Map was previously bound by `DailyActivityFragment`; both
     * mood widgets were previously bound by `MoodFragment`).
     */
    private fun attachWidgetInfoButtons() {
        binding.dashboardUsageMapInfo.attachWidgetInfo(
            WidgetInfo(
                titleRes = R.string.info_daily_usage_map_title,
                descriptionRes = R.string.info_daily_usage_map_desc,
                interpretationRes = R.string.info_daily_usage_map_interpretation,
                formulaRes = R.string.info_daily_usage_map_formula,
            ),
        )
        binding.dashboardMoodStackedChartInfo.attachWidgetInfo(
            WidgetInfo(
                titleRes = R.string.info_mood_mix_title,
                descriptionRes = R.string.info_mood_mix_desc,
                interpretationRes = R.string.info_mood_mix_interpretation,
                formulaRes = R.string.info_mood_mix_formula,
            ),
        )
        binding.dashboardMoodDistributionInfo.attachWidgetInfo(
            WidgetInfo(
                titleRes = R.string.info_mood_distribution_title,
                descriptionRes = R.string.info_mood_distribution_desc,
                interpretationRes = R.string.info_mood_distribution_interpretation,
                formulaRes = R.string.info_mood_distribution_formula,
            ),
        )
    }

    private fun goToTab(position: Int) {
        (activity as? DashboardActivity)?.goToTab(position)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun renderPayload(payload: DashboardPayload) {
        val ctx = context ?: return
        val view = _binding ?: return

        applyThemeColors()

        val placeholder = ctx.getString(R.string.dashboard_value_placeholder)
        val locale = Locale.getDefault()
        val snap = payload.ikd
        val habits = payload.habits

        bindTile(
            view.summaryTileSessions,
            view.summaryTileSessionsBody,
            label = ctx.getString(R.string.summary_tile_label_sessions),
            value = snap.totalSessions.toString(),
        )

        val minutes = snap.totalTypingTimeMs.toDouble() / MS_PER_MINUTE
        bindTile(
            view.summaryTileTypingTime,
            view.summaryTileTypingTimeBody,
            label = ctx.getString(R.string.summary_tile_label_typing_time),
            value = if (snap.totalTypingTimeMs <= 0L) {
                placeholder
            } else {
                ctx.getString(
                    R.string.dashboard_kpi_typing_time_value,
                    String.format(locale, "%.1f", minutes),
                )
            },
        )

        bindTile(
            view.summaryTileWpm,
            view.summaryTileWpmBody,
            label = ctx.getString(R.string.summary_tile_label_wpm),
            value = snap.avgWpm
                ?.let { ctx.getString(R.string.dashboard_kpi_wpm_value, it) }
                ?: placeholder,
        )

        bindTile(
            view.summaryTileErrorRate,
            view.summaryTileErrorRateBody,
            label = ctx.getString(R.string.summary_tile_label_error_rate),
            value = snap.avgErrorRatePct
                ?.let { ctx.getString(R.string.dashboard_kpi_error_rate_value, it) }
                ?: placeholder,
        )

        bindTile(
            view.summaryTileAvgSession,
            view.summaryTileAvgSessionBody,
            label = ctx.getString(R.string.summary_tile_label_avg_session),
            value = habits.avgSessionDurationMs?.let {
                ctx.getString(R.string.dashboard_habits_avg_session_value, it / MS_PER_SECOND)
            } ?: placeholder,
        )

        bindTile(
            view.summaryTileStreak,
            view.summaryTileStreakBody,
            label = ctx.getString(R.string.summary_tile_label_streak),
            value = streakLabel(ctx, habits, placeholder),
        )

        renderUsageMap(payload.activity)
        renderMoodWidgets(payload)
    }

    /**
     * Phase 9.15: Usage Map (formerly on Daily Activity). The TODAY range
     * collapses the day axis to a single column — keep the same hide rule
     * the Daily Activity tab used so the bubble chart never degenerates
     * to one tall column.
     */
    private fun renderUsageMap(activity: IkdActivityAggregator.ActivitySnapshot) {
        val ctx = context ?: return
        val view = _binding ?: return
        val isHourly = activity.range.isHourly()
        val hasDayHour = !isHourly && activity.dayHourCells.isNotEmpty()
        view.dashboardUsageMapCard.beVisibleIf(hasDayHour)
        if (!hasDayHour) return
        val bubbles = activity.dayHourCells.map {
            IkdBubbleMapView.Bubble(day = it.day, hour = it.hour, count = it.keystrokeCount)
        }
        view.dashboardUsageMap.setData(bubbles)
        view.dashboardUsageMap.setOnBubbleClickListener { bubble ->
            Toast.makeText(
                ctx,
                getString(
                    R.string.dashboard_usage_map_tooltip_format,
                    bubble.day,
                    bubble.hour,
                    bubble.count,
                ),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    /**
     * Phase 9.15: Mood Mix + Mood Distribution (formerly the entire Mood
     * tab). Both cards stay GONE when `total == 0` — sessions without an
     * explicit mood tap have no [MoodEntry] row, which is the
     * absence-of-rating signal preserved from Phase 8.
     */
    private fun renderMoodWidgets(payload: DashboardPayload) {
        val view = _binding ?: return
        val moodSnap = payload.mood
        if (moodSnap.total == 0) {
            view.dashboardMoodStackedChartCard.beGone()
            view.dashboardMoodDistributionCard.beGone()
            return
        }
        view.dashboardMoodStackedChartCard.beVisible()
        view.dashboardMoodDistributionCard.beVisible()
        bindStackedChart(payload.ikd, payload.moodMix)
        bindMoodLegend()
        renderMoodDistribution(moodSnap)
    }

    /**
     * Phase 9.14: theming pass — same discipline as `HabitsFragment` /
     * `EventFeedActivity.applyThemeColors`. MaterialCardView's default
     * `?attr/colorSurface` does not track Fossify's runtime background
     * token, so on a custom theme each tile would render as an unthemed
     * white slab. Tinting the cards to `getProperBackgroundColor()` and
     * the value labels to `getProperPrimaryColor()` brings the Summary
     * tab in line with the Phase 9.12 / 9.13 fragments.
     *
     * Phase 9.15 adds the Usage Map and Mood cards to the same pass.
     */
    private fun applyThemeColors() {
        val ctx = context ?: return
        val view = _binding ?: return
        val activity = activity ?: return
        activity.updateTextColors(view.root)

        val cardBg = ctx.getProperBackgroundColor()
        view.summaryTileSessions.setCardBackgroundColor(cardBg)
        view.summaryTileTypingTime.setCardBackgroundColor(cardBg)
        view.summaryTileWpm.setCardBackgroundColor(cardBg)
        view.summaryTileErrorRate.setCardBackgroundColor(cardBg)
        view.summaryTileAvgSession.setCardBackgroundColor(cardBg)
        view.summaryTileStreak.setCardBackgroundColor(cardBg)
        view.dashboardUsageMapCard.setCardBackgroundColor(cardBg)
        view.dashboardMoodStackedChartCard.setCardBackgroundColor(cardBg)
        view.dashboardMoodDistributionCard.setCardBackgroundColor(cardBg)

        val primary = ctx.getProperPrimaryColor()
        val textColor = ctx.getProperTextColor()
        for (body in tileBodies()) {
            body.summaryTileValue.setTextColor(primary)
            body.summaryTileLabel.setTextColor(textColor)
        }
    }

    private fun tileBodies(): List<ItemSummaryKpiTileBinding> {
        val view = _binding ?: return emptyList()
        return listOf(
            view.summaryTileSessionsBody,
            view.summaryTileTypingTimeBody,
            view.summaryTileWpmBody,
            view.summaryTileErrorRateBody,
            view.summaryTileAvgSessionBody,
            view.summaryTileStreakBody,
        )
    }

    /**
     * Populate one tile's body — label + value. The card itself was
     * wired in [wireTileClicks].
     */
    private fun bindTile(
        card: MaterialCardView,
        body: ItemSummaryKpiTileBinding,
        label: String,
        value: String,
    ) {
        body.summaryTileLabel.text = label
        body.summaryTileValue.text = value
        card.contentDescription = "$label, $value"
    }

    private fun streakLabel(
        ctx: android.content.Context,
        habits: IkdHabitsAggregator.HabitsSnapshot,
        placeholder: String,
    ): String = when (habits.streakUnit) {
        IkdHabitsAggregator.StreakUnit.HOURS -> if (habits.totalSessions > 0) {
            ctx.getString(R.string.dashboard_habits_streak_today)
        } else {
            placeholder
        }
        IkdHabitsAggregator.StreakUnit.DAYS -> ctx.getString(
            R.string.dashboard_habits_streak_days,
            habits.longestStreak,
        )
        IkdHabitsAggregator.StreakUnit.WEEKS -> ctx.getString(
            R.string.dashboard_habits_streak_weeks,
            habits.longestStreak,
        )
    }

    /**
     * Phase 9.15 (lifted from `MoodFragment.bindStackedChart`): build the
     * stacked-bar chart segments — one segment per Ekman category, value
     * is the bucket-percentage of mood entries carrying that score.
     */
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

    /**
     * Phase 9.15 (lifted from `MoodFragment.bindMoodLegend`): inflate the
     * six-item swatch legend below the stacked bar.
     */
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

    /**
     * Phase 9.15 (lifted from `MoodFragment.renderMoodDistribution`):
     * fill in the six rows of the Mood Distribution panel.
     */
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
        private const val MS_PER_MINUTE = 60_000L
        private const val MS_PER_SECOND = 1_000.0
        private const val PCT_MAX = 100
        private const val PCT_MAX_FLOAT = 100f
    }
}
