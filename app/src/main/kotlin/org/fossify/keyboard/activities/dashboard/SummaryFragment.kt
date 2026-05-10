package org.fossify.keyboard.activities.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.card.MaterialCardView
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.keyboard.R
import org.fossify.keyboard.activities.DashboardActivity
import org.fossify.keyboard.databinding.FragmentDashboardSummaryBinding
import org.fossify.keyboard.databinding.ItemSummaryKpiTileBinding
import org.fossify.keyboard.helpers.IkdHabitsAggregator
import java.util.Locale

/**
 * Phase 9.14.1: Summary tab — the new Insights landing page.
 *
 * Hosts the six headline KPIs (Sessions / Typing time / WPM / Error rate
 * / Avg session / Longest streak) as a 2x3 grid of `MaterialCardView`
 * tiles. Each tile is a tappable jump into the deeper tab that owns the
 * underlying chart (Decision #7):
 *
 *   - Sessions / Typing time / Avg session / Streak → Habits
 *   - WPM / Error rate                              → Trends
 *
 * Sparkline rendering and the per-tile delta vs the previous comparable
 * range are deferred to Phase 9.14.4 (Decision #5). Until then the
 * sparkline strip is a flat `?attr/colorControlHighlight` bar and the
 * delta cell shows the same em-dash as the global empty placeholder.
 *
 * The aggregator surface is reused as-is — values come from the host
 * activity's existing `Dispatchers.IO` hop via `latestPayload`. No new
 * SQL, no new aggregator (Decision #6).
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
        return binding.root
    }

    /**
     * Decision #7: every tile is a clickable jump to the deeper tab that
     * owns the underlying chart. Routed through the host activity's
     * `goToTab(position)` so the index lookup stays centralised.
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
    }

    /**
     * Phase 9.14: theming pass — same discipline as `HabitsFragment` /
     * `EventFeedActivity.applyThemeColors`. MaterialCardView's default
     * `?attr/colorSurface` does not track Fossify's runtime background
     * token, so on a custom theme each tile would render as an unthemed
     * white slab. Tinting the cards to `getProperBackgroundColor()` and
     * the value labels to `getProperPrimaryColor()` brings the Summary
     * tab in line with the Phase 9.12 / 9.13 fragments.
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

        val primary = ctx.getProperPrimaryColor()
        val textColor = ctx.getProperTextColor()
        for (body in tileBodies()) {
            body.summaryTileValue.setTextColor(primary)
            body.summaryTileLabel.setTextColor(textColor)
            body.summaryTileDelta.setTextColor(textColor)
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
     * Populate one tile's body — label + value, plus the deferred-9.14.4
     * delta placeholder. The card itself was wired in [wireTileClicks].
     */
    private fun bindTile(
        card: MaterialCardView,
        body: ItemSummaryKpiTileBinding,
        label: String,
        value: String,
    ) {
        val ctx = context ?: return
        body.summaryTileLabel.text = label
        body.summaryTileValue.text = value
        body.summaryTileDelta.text = ctx.getString(R.string.summary_tile_delta_placeholder)
        // Click target is the card; the body itself stays non-clickable
        // so the ripple originates from the tile edge.
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

    companion object {
        private const val MS_PER_MINUTE = 60_000L
        private const val MS_PER_SECOND = 1_000.0
    }
}
