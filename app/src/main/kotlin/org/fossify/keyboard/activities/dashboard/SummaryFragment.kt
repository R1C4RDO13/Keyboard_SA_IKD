package org.fossify.keyboard.activities.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import org.fossify.keyboard.databinding.ItemBadgeProgressTileBinding
import org.fossify.keyboard.databinding.ItemMoodTileBinding
import org.fossify.keyboard.databinding.ItemSummaryKpiTileBinding
import org.fossify.keyboard.helpers.IkdActivityAggregator
import org.fossify.keyboard.helpers.IkdAggregator
import org.fossify.keyboard.helpers.IkdHabitsAggregator
import org.fossify.keyboard.helpers.IkdBadgeCatalog
import org.fossify.keyboard.helpers.IkdBadgeEvaluator
import org.fossify.keyboard.helpers.IkdMoodAggregator
import org.fossify.keyboard.helpers.MoodEmoji
import org.fossify.keyboard.helpers.WidgetInfo
import org.fossify.keyboard.helpers.attachWidgetInfo
import org.fossify.keyboard.views.IkdBubbleMapView
import org.fossify.keyboard.views.IkdStackedBarChartView
import java.util.Locale

/**
 * Phase 9.15 / 9.17: Summary tab — the Insights landing page.
 *
 * Contents (top → bottom, post-9.17):
 *  - **Mood Distribution tiles** (NEW position — Decision #9 of the 9.17
 *    plan). Horizontal row of six coloured `MaterialCardView` tiles, one
 *    per Ekman category, each rendering an emoji + integer percentage +
 *    label on a per-mood `mood_color_*` background. Tap → toast with
 *    `"<emoji> <label> · <count> sessions (<pct>%)"`. The tile row
 *    doubles as the global colour legend for every mood widget below.
 *    Hidden when `MoodSnapshot.total == 0`.
 *  - **3x2 KPI grid** (Sessions / Typing time / WPM | Error rate / Avg
 *    session / Streak). Each tile is label + big value only.
 *  - **Usage Map** (`IkdBubbleMapView`) — bubbles tinted by the dominant
 *    mood of sessions in each `(day, hour)` cell, falling back to the
 *    primary tone for cells with no mood-tagged session.
 *  - **Mood Mix over Time** — stacked-bar chart, rendered without an
 *    in-card legend now that the tiles upstairs cover that role
 *    (Decision #5 of the 9.17 plan).
 *
 * Tile click routing (preserved from Phase 9.14.1 decision #7):
 *  - Sessions / Typing time / Avg session / Streak → Habits tab
 *  - WPM / Error rate                              → Trends tab
 *
 * The aggregator surface is reused as-is — values come from the host
 * activity's existing `Dispatchers.IO` hop via
 * [DashboardActivity.latestPayload]. No new SQL on this fragment's hot
 * path; the per-cell dominant-mood JOIN happens server-side via the
 * sibling `IkdEventDao.getDayHourMoodBuckets` query Phase 9.17 added
 * alongside the existing `getDayHourBuckets`.
 */
class SummaryFragment : DashboardFragment() {

    private var _binding: FragmentDashboardSummaryBinding? = null
    private val binding get() = _binding!!

    /**
     * Task B: mood scores (1..6) with at least one entry in the current
     * snapshot. A tile not in this set is greyed + non-clickable and must
     * stay that way through [applyMoodFilterHighlight] (which otherwise
     * rewrites every tile's alpha on each payload swap). Empty until the
     * first [renderMoodTiles].
     */
    private var selectableMoodScores: Set<Int> = emptySet()

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
        // Phase 15: the Habits tab was dropped. The session/time KPI tiles
        // previously jumped there; Avg session duration relocated to the
        // Trends tab, so all six KPI tiles now route to Trends (the tab
        // that owns the underlying session + speed + error charts).
        val toTrends = View.OnClickListener { goToTab(DashboardPagerAdapter.TAB_TRENDS) }
        view.summaryTileSessions.setOnClickListener(toTrends)
        view.summaryTileTypingTime.setOnClickListener(toTrends)
        view.summaryTileAvgSession.setOnClickListener(toTrends)
        view.summaryTileStreak.setOnClickListener(toTrends)
        view.summaryTileWpm.setOnClickListener(toTrends)
        view.summaryTileErrorRate.setOnClickListener(toTrends)

        // Phase 14 §7.6: every "Badges in progress" tile jumps to the
        // Achievements tab.
        val toAchievements = View.OnClickListener {
            goToTab(DashboardPagerAdapter.TAB_ACHIEVEMENTS)
        }
        for (tile in badgeTileBindings(view)) {
            tile.root.setOnClickListener(toAchievements)
        }
    }

    private fun badgeTileBindings(
        view: FragmentDashboardSummaryBinding,
    ): List<ItemBadgeProgressTileBinding> = listOf(
        ItemBadgeProgressTileBinding.bind(view.summaryBadgeTileMoodVolume.root),
        ItemBadgeProgressTileBinding.bind(view.summaryBadgeTileMoodCheckin.root),
        ItemBadgeProgressTileBinding.bind(view.summaryBadgeTileMoodDevotion.root),
        ItemBadgeProgressTileBinding.bind(view.summaryBadgeTileKbKeys.root),
        ItemBadgeProgressTileBinding.bind(view.summaryBadgeTileKbStreak.root),
    )

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

        renderMoodTiles(payload.mood)
        renderBadgesWidget(payload.badges)
        renderUsageMap(payload.activity)
        renderMoodWidgets(payload)

        // Phase 9.18: re-apply the host's mood filter highlight after every
        // payload swap so range / refresh / rotation cycles don't blow away
        // the active-tile stroke and dim states.
        applyMoodFilterHighlight((activity as? DashboardActivity)?.currentMoodFilter)
    }

    /**
     * Phase 9.15 / 9.17: Usage Map (formerly on Daily Activity). The
     * TODAY range collapses the day axis to a single column — keep the
     * same hide rule the Daily Activity tab used so the bubble chart
     * never degenerates to one tall column. Phase 9.17: bubbles are
     * tinted by `dominantMood` (sessions in the cell with the largest
     * mood-tagged keystroke count); falls back to the primary tone when
     * the cell has no mood-tagged session.
     */
    private fun renderUsageMap(activity: IkdActivityAggregator.ActivitySnapshot) {
        val ctx = context ?: return
        val view = _binding ?: return
        val isHourly = activity.range.isHourly()
        val hasDayHour = !isHourly && activity.dayHourCells.isNotEmpty()
        view.dashboardUsageMapCard.beVisibleIf(hasDayHour)
        if (!hasDayHour) return
        val bubbles = activity.dayHourCells.map {
            IkdBubbleMapView.Bubble(
                day = it.day,
                hour = it.hour,
                count = it.keystrokeCount,
                dominantMood = it.dominantMood,
            )
        }
        view.dashboardUsageMap.setData(bubbles)
        view.dashboardUsageMap.setOnBubbleClickListener { bubble ->
            val msg = if (bubble.dominantMood != null) {
                getString(
                    R.string.dashboard_usage_map_tooltip_with_mood,
                    bubble.day,
                    bubble.hour,
                    bubble.count,
                    MoodEmoji.emojiFor(bubble.dominantMood!!),
                    getString(MoodEmoji.labelResFor(bubble.dominantMood!!)),
                )
            } else {
                getString(
                    R.string.dashboard_usage_map_tooltip_format,
                    bubble.day,
                    bubble.hour,
                    bubble.count,
                )
            }
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Phase 9.15: Mood Mix (formerly the Mood tab). The card stays GONE
     * when `total == 0` — sessions without an explicit mood tap have no
     * [MoodEntry] row, which is the absence-of-rating signal preserved
     * from Phase 8. Phase 9.17: the in-card legend is dropped (the
     * Mood Distribution tile row at the top of the Summary tab doubles
     * as the global colour legend — Decision #5).
     */
    private fun renderMoodWidgets(payload: DashboardPayload) {
        val view = _binding ?: return
        val moodSnap = payload.mood
        if (moodSnap.total == 0) {
            view.dashboardMoodStackedChartCard.beGone()
            return
        }
        view.dashboardMoodStackedChartCard.beVisible()
        bindStackedChart(payload.ikd, payload.moodMix)
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
     * Phase 9.17 leaves the six mood-distribution tiles untinted by the
     * background token — those cards intentionally render in their
     * mood colour. They get their fill in [renderMoodTiles] instead.
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
     *
     * Phase 9.17: passes `drawLegend = false` to the chart so the
     * built-in legend stays hidden — the Summary tab's tile row at the
     * top is the global legend now (Decision #5 of the 9.17 plan).
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
                colorInt = ContextCompat.getColor(ctx, MoodEmoji.colorResFor(score)),
                values = values,
            )
        }
        view.dashboardChartMoodStacked.setData(labels, segments, drawLegend = false)
    }

    /**
     * Phase 9.17: render the six-tile Mood Distribution row at the top
     * of the Summary tab. Each tile is filled in its mood's
     * `mood_color_*` token (resolved at runtime so light / dark theme
     * overrides apply automatically), shows the rounded integer
     * percentage and the localized label. Tapping a tile shows a Toast
     * with the verbose breakdown including the absolute count
     * (Decision #3 of the 9.17 plan — counts are toast-only).
     *
     * Hidden as a whole when `total == 0` (no mood entries yet).
     */
    private fun renderMoodTiles(moodSnap: IkdMoodAggregator.MoodSnapshot) {
        val ctx = context ?: return
        val view = _binding ?: return
        if (moodSnap.total <= 0) {
            view.summaryMoodTilesSection.beGone()
            return
        }
        view.summaryMoodTilesSection.beVisible()
        view.summaryMoodTilesTitle.setTextColor(ctx.getProperTextColor())

        val tiles = listOf(
            view.summaryMoodTileHappiness to MoodEmoji.SCORE_HAPPINESS,
            view.summaryMoodTileSurprise to MoodEmoji.SCORE_SURPRISE,
            view.summaryMoodTileDisgust to MoodEmoji.SCORE_DISGUST,
            view.summaryMoodTileSadness to MoodEmoji.SCORE_SADNESS,
            view.summaryMoodTileFear to MoodEmoji.SCORE_FEAR,
            view.summaryMoodTileAnger to MoodEmoji.SCORE_ANGER,
        )
        val total = moodSnap.total
        selectableMoodScores = tiles
            .map { it.second }
            .filter { (moodSnap.counts[it] ?: 0) > 0 }
            .toSet()
        for ((cardRoot, score) in tiles) {
            val tile = ItemMoodTileBinding.bind(cardRoot.root)
            val card = tile.root
            val count = moodSnap.counts[score] ?: 0
            val pct = if (total <= 0) 0 else (count * PCT_MAX + total / 2) / total
            val emoji = MoodEmoji.emojiFor(score)
            val label = getString(MoodEmoji.labelResFor(score))
            // Task B: a mood with zero entries cannot be a useful filter —
            // selecting it would scope every aggregator to zero sessions
            // and strand the user on the blank empty-state screen (the
            // tiles that would let them recover live inside the hidden
            // ViewPager). Such a tile is greyed and made non-clickable so
            // the empty-filter path is unreachable from the UI.
            val isSelectable = count > 0

            card.setCardBackgroundColor(ContextCompat.getColor(ctx, MoodEmoji.colorResFor(score)))
            card.alpha = if (isSelectable) 1f else DISABLED_TILE_ALPHA
            tile.summaryMoodTileEmoji.text = emoji
            tile.summaryMoodTilePct.text = ctx.getString(R.string.summary_mood_tile_pct_format, pct)
            tile.summaryMoodTileLabel.text = label
            card.contentDescription = ctx.getString(
                R.string.summary_mood_tile_content_description,
                emoji,
                label,
                count,
                pct,
            )
            if (isSelectable) {
                card.isClickable = true
                card.setOnClickListener {
                    // Owner directive: no notification/toast when changing
                    // mood inside Insights. Tapping a tile just toggles the
                    // mood filter; the tile's selected highlight is the
                    // feedback (the count/pct still live in the tile itself
                    // and its contentDescription for a11y). Reverses Phase
                    // 9.18 Decision #5's tap-toast.
                    (activity as? DashboardActivity)?.onMoodTileTapped(score)
                }
            } else {
                // Drop any listener a recycled binding may still carry and
                // swallow taps so a zero-entry mood can never be selected.
                card.setOnClickListener(null)
                card.isClickable = false
            }
        }
    }

    /**
     * Phase 9.18: paint the active-tile stroke + scale, dim the rest.
     *
     * Called after every [renderPayload] (so the highlight survives range
     * changes, refresh, rotation) and directly from
     * [DashboardActivity.onMoodTileTapped] (so the tile snaps to the new
     * state ahead of the IO-bound `loadSnapshot()` round-trip).
     *
     * Decision #2 of the 9.18 plan:
     *  - Active tile: 1.5 dp primary stroke + 1.05× scale + full alpha.
     *  - Inactive tiles while a filter is active: zero stroke + 1× scale +
     *    0.55 alpha (the dim state).
     *  - All tiles while no filter is active: zero stroke + 1× scale +
     *    full alpha.
     *
     * Uses [MaterialCardView.strokeWidth] / [MaterialCardView.setStrokeColor]
     * — the layout default is `strokeWidth=0`, so inactive tiles look
     * unchanged from Phase 9.17 when nothing is filtered.
     */
    fun applyMoodFilterHighlight(activeScore: Int?) {
        val ctx = context ?: return
        val view = _binding ?: return
        val tiles = listOf(
            view.summaryMoodTileHappiness to MoodEmoji.SCORE_HAPPINESS,
            view.summaryMoodTileSurprise to MoodEmoji.SCORE_SURPRISE,
            view.summaryMoodTileDisgust to MoodEmoji.SCORE_DISGUST,
            view.summaryMoodTileSadness to MoodEmoji.SCORE_SADNESS,
            view.summaryMoodTileFear to MoodEmoji.SCORE_FEAR,
            view.summaryMoodTileAnger to MoodEmoji.SCORE_ANGER,
        )
        val strokePx = ctx.resources
            .getDimensionPixelSize(R.dimen.summary_mood_tile_stroke_active)
        val primary = ctx.getProperPrimaryColor()
        val hasFilter = activeScore != null
        for ((cardRoot, score) in tiles) {
            val card = ItemMoodTileBinding.bind(cardRoot.root).root
            // Task B: a zero-entry tile stays greyed + non-active even if a
            // stale filter (e.g. restored from instance state) names its
            // score. It can never legitimately be the active filter.
            val isSelectable = score in selectableMoodScores
            if (!isSelectable) {
                card.strokeWidth = 0
                card.scaleX = 1f
                card.scaleY = 1f
                card.alpha = DISABLED_TILE_ALPHA
                continue
            }
            val isActive = score == activeScore
            if (isActive) {
                card.strokeWidth = strokePx
                card.setStrokeColor(primary)
                card.scaleX = ACTIVE_TILE_SCALE
                card.scaleY = ACTIVE_TILE_SCALE
                card.alpha = 1f
            } else {
                card.strokeWidth = 0
                card.scaleX = 1f
                card.scaleY = 1f
                card.alpha = if (hasFilter) INACTIVE_TILE_ALPHA else 1f
            }
        }
    }

    /**
     * Phase 14 §7.6: the compact "Badges in progress" strip — one tile
     * per v1 group showing that group's *current in-progress* badge (the
     * same focus the Achievements carousel centres on; a fully-unlocked
     * group shows its top badge as "✓ done"). Pure presentation of the
     * same [IkdBadgeEvaluator.EvaluationResult] the host already produced
     * on the single `loadSnapshot` hop — no extra query. Hidden entirely
     * when there is no captured data (same empty-state discipline as the
     * other Summary widgets).
     */
    private fun renderBadgesWidget(result: IkdBadgeEvaluator.EvaluationResult) {
        val ctx = context ?: return
        val view = _binding ?: return
        val snap = result.snapshot
        val hasData = snap.moodCount > 0 ||
            snap.keystrokeTotal > 0L ||
            snap.sessionStreak > 0
        if (!hasData) {
            view.summaryBadgesSection.beGone()
            return
        }
        view.summaryBadgesSection.beVisible()
        view.summaryBadgesTitle.setTextColor(ctx.getProperTextColor())

        // Task D: every tile state (in-progress, no-progress, done) uses
        // the runtime Fossify theme — no fixed badge_* tokens. Card to the
        // background tone, caption to the text tone, progress bar to the
        // primary tone.
        val cardBg = ctx.getProperBackgroundColor()
        val textColor = ctx.getProperTextColor()
        val primary = ctx.getProperPrimaryColor()
        val tiles = badgeTileBindings(view)
        IkdBadgeCatalog.GROUPS.forEachIndexed { index, group ->
            val tile = tiles[index]
            val defs = IkdBadgeCatalog.badgesFor(group)
            val focusDef = defs.firstOrNull { it.key !in result.allUnlocked }
            val isDone = focusDef == null
            val def = focusDef ?: defs.last()

            tile.badgeTileEmoji.text = def.emoji
            tile.badgeTileEmoji.alpha = if (isDone) 1f else LOCKED_TILE_ALPHA

            val progress = result.progressByKey[def.key]
            val pct: Int
            val caption: String
            if (isDone || progress == null) {
                pct = PCT_MAX
                caption = ctx.getString(R.string.summary_badge_tile_done)
            } else {
                val current = progress.current.coerceAtMost(progress.target)
                pct = if (progress.target <= 0L) {
                    0
                } else {
                    ((current * PCT_MAX) / progress.target).toInt().coerceIn(0, PCT_MAX)
                }
                caption = when (progress.unitKind) {
                    IkdBadgeCatalog.UnitKind.DAYS -> ctx.getString(
                        R.string.summary_badge_tile_days_format,
                        current.toInt(),
                        progress.target.toInt(),
                    )
                    IkdBadgeCatalog.UnitKind.COUNT ->
                        if (progress.target >= LARGE_TARGET_THRESHOLD) {
                            ctx.getString(R.string.summary_badge_tile_pct_format, pct)
                        } else {
                            val nf = java.text.NumberFormat
                                .getIntegerInstance(Locale.getDefault())
                            ctx.getString(
                                R.string.summary_badge_tile_count_format,
                                nf.format(current),
                                nf.format(progress.target),
                            )
                        }
                }
            }
            tile.badgeTileProgress.progress = pct
            tile.badgeTileProgress.progressTintList =
                android.content.res.ColorStateList.valueOf(primary)
            tile.badgeTileEmoji.setTextColor(textColor)
            tile.badgeTileCaption.text = caption
            tile.badgeTileCaption.setTextColor(textColor)
            tile.root.setCardBackgroundColor(cardBg)
        }
    }

    companion object {
        private const val MS_PER_MINUTE = 60_000L
        private const val MS_PER_SECOND = 1_000.0
        private const val PCT_MAX = 100
        private const val PCT_MAX_FLOAT = 100f
        private const val LOCKED_TILE_ALPHA = 0.7f
        private const val LARGE_TARGET_THRESHOLD = 1_000L

        // Phase 9.18: active-tile scale-up factor and the dim alpha applied
        // to the other five tiles while a filter is active. Plan Decision #2.
        private const val ACTIVE_TILE_SCALE = 1.05f
        private const val INACTIVE_TILE_ALPHA = 0.55f

        // Task B: a zero-entry mood tile is greyed at this alpha and made
        // non-clickable so it can never be selected as a filter.
        private const val DISABLED_TILE_ALPHA = 0.4f
    }
}
