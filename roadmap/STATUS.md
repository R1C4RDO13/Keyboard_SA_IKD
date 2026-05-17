# Project Status

Quick-access summary of phase completion. For detailed scope of each phase see [`FeatureRoadmap.md`](FeatureRoadmap.md), the per-phase plan files linked below, and `CLAUDE.md` (architecture notes per phase). Narrative report: [`../PROJECT_JOURNEY.md`](../PROJECT_JOURNEY.md) / [`../PROJECT_JOURNEY.html`](../PROJECT_JOURNEY.html).

_Last updated: 2026-05-17 (Phase 14 + Phase 15 implemented; Achievements rebuilt as a flat grouped list; ~20 post-implementation UX reworks incl. password-field auto-privacy and the active-filter chip removal). `main` is ahead of `origin/main` by the Phase 14 + Phase 15 commits._

| Phase | Title | Status | Plan |
|---|---|---|---|
| 1 | Sensor Calibration & Debug Environment | Implemented | [Phase1_Plan.md](Phase1/Phase1_Plan.md) |
| 1.1 | Live Keyboard Capture & Metric Realignment | Implemented | [Phase1.1_Plan.md](Phase1/Phase1.1_Plan.md) |
| 2 | Background Collection & Local Storage | Implemented | [Phase2_Plan.md](Phase2/Phase2_Plan.md) |
| 3 | User Insights & Dashboard | Implemented | [Phase3_Plan.md](Phase3/Phase3_Plan.md) |
| 4 | Session Detail Refresh | Implemented | [Phase4_Plan.md](Phase4/Phase4_Plan.md) |
| 5 | Session Dashboard | Implemented | [Phase5_Plan.md](Phase5/Phase5_Plan.md) |
| 6 | Diagnostics Screen Improvements | Implemented (reworked) | [Phase6_Plan.md](Phase6/Phase6_Plan.md) |
| 7 | Emoji & Autocorrect Capture | Implemented | [Phase7_Plan.md](Phase7/Phase7_Plan.md) |
| 7.1 | AUTOCORRECT Replacement Weight | Implemented | [Phase7.1_Plan.md](Phase7.1/Phase7.1_Plan.md) |
| — | Error-rate fix: exclude BACKSPACE from denominator + weight by deletion count | Implemented (`569f331b`) | [ErrorRateFix_TestPlan.md](ErrorRateFix_TestPlan.md) |
| — | Error-rate fix: drop AUTOCORRECT from the formula entirely | Implemented (`303e792f`) | [ErrorRateFix_DropAutocorrect_Plan.md](ErrorRateFix_DropAutocorrect_Plan.md) |
| 8 | Mood Bar & Contextual Overlay | Implemented (incl. 8.1 UI polish) | [Phase8_Plan.md](Phase8/Phase8_Plan.md) |
| 8.2 | Mood Bar UX Polish (toggles, capsule background, chat-bubble) | Implemented | [Phase8_Plan.md §13](Phase8/Phase8_Plan.md#13-mood-bar-ux-polish-phase-82) |
| 8.3 | Mood Distribution over Time (Stacked Bar) | Implemented | [Phase8.3_Plan.md](Phase8/Phase8.3_Plan.md) |
| 8.4 | Mood-Bar Dance + Haptic on Slot Select | Implemented | [Phase8.4_Plan.md](Phase8/Phase8.4_Plan.md) |
| 8.5 | Collapsible Mood Bar with Persistent Standing Rating + Inactivity Reset | Implemented | [Phase8.5_Plan.md](Phase8/Phase8.5_Plan.md) |
| 9 | Global Insights Expansion (sub-phases 9.1 – 9.10; absorbs the deleted Phase 11) | Implemented | [Phase9_Plan.md](Phase9/Phase9_Plan.md) + [sub_plans/](Phase9/sub_plans/) |
| 9.11 | Insights — TODAY range + multi-tab `ViewPager2` | Implemented | [9.11 sub-plan](Phase9/sub_plans/9.11_today_filter_and_tabs.md) |
| 9.12 | Insights — themed tabs, top tab toggle, KPI dedup | Implemented | [9.12 sub-plan](Phase9/sub_plans/9.12_theming_top_tabs_kpi_dedup.md) |
| 9.13 | Insights — tap-to-explain widget info icons | Implemented | [9.13 sub-plan](Phase9/sub_plans/9.13_widget_info_icons.md) |
| 9.14 | Insights — Summary tab, TabLayout w/ icons, filter bottom sheet | Implemented | [9.14 sub-plan](Phase9/sub_plans/9.14_summary_tab_icons_filters.md) |
| 9.15 | Insights — Summary tab rework (3×2 KPI, Usage Map, Mood), drop Mood tab | Implemented | [9.15 sub-plan](Phase9/sub_plans/9.15_summary_rework_drop_mood.md) |
| 9.16 | Insights — bucket-size hint, Sessions-history shortcut, streak fix | Implemented | (folded into Phase 9 plan) |
| 9.17 | Insights — mood-colour refresh, distribution-first Summary, mood-tinted Usage Map | Implemented | [9.17 sub-plan](Phase9/sub_plans/9.17_mood_colors_distribution_first.md) |
| 9.18 | Insights — Distribution tiles double as the Mood Filter | Implemented | [9.18 sub-plan](Phase9/sub_plans/9.18_distribution_tiles_as_mood_filter.md) |
| 10 | Rebrand & New Identity (MoodScript — name, launcher glyph, About screen) | Implemented | [Phase10_Plan.md](Phase10/Phase10_Plan.md) |
| ~~11~~ | ~~Usage Map & Daily Activity Charts~~ | **Deleted** — absorbed into Phase 9.5 / 9.6 / 9.9 / 9.10 | — |
| 12 | Mood-Curated Emoji Section in the drawer | Implemented | [Phase12_Plan.md](Phase12/Phase12_Plan.md) |
| 13 | Persistent Right-Anchored Mood Bar (visible inside the emoji drawer) | Implemented | [Phase13_Plan.md](Phase13/Phase13_Plan.md) |
| 14 | Gamification: Badges (Mood + Keyboard) — interim sixth "Achievements" tab | Implemented | [Phase14_Plan.md](Phase14/Phase14_Plan.md) + [Phase14_BadgeCatalog.md](Phase14/Phase14_BadgeCatalog.md) |
| 15 | Insights IA v2 — drop Habits, re-order to Summary · Achievements · Activity · Trends · Keys | Implemented (collapsed Phase 14's interim 6 tabs to 5) | [Phase15_Plan.md](Phase15/Phase15_Plan.md) |

## Schema state

`IkdDatabase.version = 4` — three migrations registered, all non-destructive, all validated end-to-end by `IkdDatabaseMigrationTest` in `app/src/androidTest`:

- `Migration(1, 2)` (Phase 8): adds the `mood_entries` table (one row per session, ordinal valence 1–6, unique index on `session_id`, CASCADE on session delete).
- `Migration(2, 3)` (Phase 7.1): adds `correction_weight INTEGER NOT NULL DEFAULT 0` to `ikd_events` and backfills weight 1 onto every existing `is_correction = 1` row.
- `Migration(3, 4)` (Phase 14): adds the `badges` table (`id`, unique `badge_key`, `unlocked_at`) — purely additive, no existing row touched. Exported schema at `app/schemas/.../IkdDatabase/4.json`.

## Dashboard shape (current)

`DashboardActivity` is a thin host over a top `TabLayout` + `ViewPager2` with **five** tab fragments under `activities/dashboard/`. Phase 15 (Insights IA v2) set the final order, dropped the Habits tab, relocated its two surviving charts, and removed three low-signal widgets. `DashboardPagerAdapter`: `TAB_COUNT = 5`, `TAB_SUMMARY=0 · TAB_ACHIEVEMENTS=1 · TAB_ACTIVITY=2 · TAB_TRENDS=3 · TAB_KEYS=4`:

1. **Summary** — six coloured mood-distribution tiles (double as the screen's colour legend **and** a one-tap Mood Filter shortcut; zero-entry tiles are disabled, and tapping one no longer pops a Toast), an "Achievements" section label above a "Badges in progress" tile strip (Phase 14, one tile per group's focus badge → Achievements tab; tiles themed uniform with the mood tiles, outer card dropped), 3×2 KPI grid (KPI tiles now jump to Trends — Habits tab gone), Usage Map bubble chart, Mood Mix stacked bar.
2. **Achievements** (Phase 14, promoted to index 1 by Phase 15) — a single **flat vertical list** (`BadgeListAdapter`) of group section headers + full-width badge rows for the 28 v1 badges (5 groups), always all-time (ignores Range/Mood). This is **Option B**, an owner-review rebuild that replaced the original per-group horizontal carousel (the `BadgeGroupAdapter` / `BadgeCardAdapter` / `‹›` arrows / page dots were deleted). Locked rows show a progress bar + `current/target`; the Daily-devotion group's `IkdBadgeDayStripView` 14-day mini-keyboard strip is preserved, folded into that group's section header. Newly-unlocked badges fire a Snackbar + a local notification (gated by `Config.badgeNotificationsEnabled` + `POST_NOTIFICATIONS`; no `INTERNET`). Badges are fully themed via the runtime Fossify theme.
3. **Activity** — daily keypress bar, 24-hour bar, hour×weekday **Circadian heatmap** (card title renamed from "When you type" in Phase 15). The Calendar heatmap was removed (Phase 15 §2).
4. **Trends** — 5 line charts (WPM, avg IKD, error %, avg gyro magnitude, avg accel magnitude) **+ Avg session duration** (relocated from the dropped Habits tab; the single error-rate trend stays — §6 default, no duplicate).
5. **Keys** ("Keystroke Dynamics") — IKD / dwell / flight log-scale histograms, orientation breakdown donut **+ Avg flight time** (relocated from the dropped Habits tab). The backspaces-vs-autocorrects quality scatter was removed (Phase 15 §2).

The **Habits** tab was removed entirely in Phase 15: its 4-cell KPI strip (incl. longest-streak) was dropped (re-homing OOS, §6); Sessions-per-period + Activity-quality scatter were removed; Avg session duration → Trends, Avg flight time → Keys. `IkdHabitsAggregator` / `IkdQualityAggregator` and the calendar-heatmap branch of `IkdActivityAggregator` stay intact (frozen read pipeline, harmless dead output).

Global header above the tabs: app bar · toolbar **Filters** button → `InsightsFiltersBottomSheet` for Range (Today / Week / Month / All Time) + Mood Filter · bucket-size hint. The small **active-filter chip pill below the tabs was removed** (owner directive, `1c0f71d2`); the bucket-size hint stays. Range/Mood filtering itself is fully retained — an over-removal that hard-fixed the dashboard to ALL_TIME and deleted the bottom sheet (`0e46cf89`) was reverted (`cbe1784a`). State persisted via `onSaveInstanceState` (per-screen-instance, not `SharedPreferences`).

### Capture-path privacy (post-Phase-15)

`SimpleKeyboardIME.isPasswordField(editorInfo)` (commit `f09a6a87`) auto-skips all capture on password fields — text/visible/web password variations under `TYPE_CLASS_TEXT` and the numeric password variation under `TYPE_CLASS_NUMBER`. No session, no sensors, no IKD/sensor/mood rows for that field, exactly as if `Config.privacyModeEnabled` were on but scoped per-field and **not persisted** (recomputed from `editorInfo` on every `onStartInputView`). A narrow, sanctioned capture-path reopen.

## Error-rate metric (current definition)

Since `303e792f`, "correction" in the metric layer means **BACKSPACE only**:

```
errorRatePct = 100 * SUM(correction_weight WHERE category = 'BACKSPACE') / (keystrokeCount - backspaceCount)
```

where `keystrokeCount = COUNT(*) - COUNT(AUTOCORRECT)`. AUTOCORRECT rows are still captured (CSV, per-session event log) but no longer feed the error-rate KPI / chart / live cell — the IME-level autocorrect heuristic can't reliably distinguish a true spell-check accept from spell-check noise across OEMs. Sessions with no autocorrects are byte-identical to the pre-fix formula. (Phase 15 removed the Habits-tab error chart and the Daily-Quality scatter from the UI, so the only remaining AUTOCORRECT-as-correction code path is the now-unsurfaced `IkdHabitsAggregator` / `IkdQualityAggregator` output — harmless, frozen.)

## Next step

**All roadmap phases (1 → 15) are implemented — nothing is outstanding.** Phase 14 (gamification badges) was the last planned phase; it shipped (28 v1 badges across 5 groups; the Achievements tab was rebuilt as a flat grouped list after owner review). Phase 15 (Insights IA v2) shipped the final five-tab dashboard — **Summary · Achievements · Activity · Trends · Keys** (`TAB_COUNT = 5`, `TAB_ACHIEVEMENTS = 1`) — collapsing Phase 14's interim 6-tab layout: the Habits tab was dropped, Avg session duration relocated to Trends, Avg flight time to Keys, and the Calendar heatmap / Sessions-per-period / Activity-quality scatter widgets were removed. ~20 post-implementation UX reworks followed (badge theming, Summary tile parity, password-field auto-privacy, the active-filter-chip removal, zero-entry mood-tile disabling, Trends-card parity, removal of mood-change Toasts). No further roadmap phases are queued.

Backlog (no detailed plan yet): **badge groups 2 (Mood Diversity) and 6 (KB Sessions) are deferred** — catalogued in `Phase14_BadgeCatalog.md` but not built in v1; adding them later is purely additive (new `BadgeDef` rows + snapshot fields). The Phase 14 ↔ 15 dead-aggregator cleanup is tracked in `Phase15_Plan` §6. Still-unaligned: the `IkdHabitsAggregator` / `IkdQualityAggregator` correction metrics still treat AUTOCORRECT as a correction — harmless now that Phase 15 unsurfaced both from the UI, but a tracked follow-up. Other: a release-readiness pass (`applicationId` rename + one-time `ikd.db` copy, fastlane/store metadata, screenshots, `versionName` bump) deferred out of the cosmetic Phase 10 rebrand; mood quick-note (long-press a mood emoji to add a 1–2 line note — needs an explicit text-storage opt-in); mood ↔ activity overlay on the Phase 9.5 calendar heatmap.

## Frozen surfaces (do not edit without an explicit unfreeze in a phase plan)

- **Capture path:** `SimpleKeyboardIME.kt` *(reopened narrowly by Phase 7.1 for the autocorrect heuristic, by `569f331b` for `computeBackspaceWeight`, by `303e792f` for the BACKSPACE-only metric, by Phase 8.5 / 13 for tiny lifecycle / preference-listener wires, and by `f09a6a87` for the per-field password-capture skip `isPasswordField`)*, `LiveCaptureSessionStore.kt`, `KinematicSensorHelper.kt`, `IkdRetentionWorker.kt`
- **Schema entities:** `IkdEvent`, `SensorSample`, `SessionRecord`, `MoodEntry`
- **Read pipeline:** `IkdAggregator` *(Phase 9.4 added a `moodFilter` param; `303e792f` swapped the correction projections)*, `IkdSessionStatsLoader`, `IkdSessionChartLoader`, `IkdMoodLoader`, `IkdMoodAggregator`, plus the Phase 9 aggregators `IkdSensorAggregator`, `IkdHabitsAggregator`, `IkdActivityAggregator` *(Phase 9.17 added the dominant-mood post-pass)*, `IkdDistributionAggregator`, `IkdOrientationAggregator`, `IkdQualityAggregator`
- **CSV:** `IkdCsvWriter.kt` — dual-block format (`ikd_events` + `sensor_samples` + `mood_entries`), `correction_weight` column on the timing block; format is frozen
- **Charts:** `views/IkdLineChartView.kt`, `views/IkdStackedBarChartView.kt`, `views/IkdHeatmapView.kt` *(9.17 reads `dominantMood`)*, `views/IkdBubbleMapView.kt` *(9.17 mood tinting)*, `views/IkdHistogramView.kt`
- **Phase 6 surface:** `DiagnosticsActivity.kt`, `activity_diagnostics.xml` *(error-rate fixes updated `updateComputedMetrics`)*
- **Phase 5 surface:** `EventFeedActivity.kt`, `activity_event_feed.xml` *(Phase 8.1 dedupe edit; otherwise frozen)*
- **Phase 3/9 surface:** `DashboardActivity.kt` + `activity_dashboard.xml` + `activities/dashboard/*` *(reopened cumulatively through Phase 9.18; the dashboard fragments are the live edit surface, the activity host is otherwise frozen)*
- **Phase 8 mood surface:** `views/MyKeyboardView.kt` (mood-bar wiring) *(reopened by 8.2 / 8.4 / 8.5 / 12 / 13)*, `helpers/MoodEmoji.kt` *(8.5 `SCORE_NONE`, 9.17 `colorResFor`, 12 `curatedEmojisFor`)*, `helpers/IkdMoodBarController.kt` *(8.2 / 8.5 additive methods)*, `models/MoodEntry.kt`, `interfaces/MoodDao.kt` *(8.3 / 9.4 / 9.17 additive queries)*
- **Brand surface (Phase 10):** `activities/AboutActivity.kt`, `res/drawable/ic_launcher_foreground.xml` / `ic_launcher_monochrome.xml`, `app_name` / `app_launcher_name` strings
- **Badge surface (Phase 14):** `models/Badge.kt`, `interfaces/BadgeDao.kt`, `helpers/IkdBadgeCatalog.kt`, `helpers/IkdBadgeEvaluator.kt`, `helpers/IkdBadgeNotifier.kt`, `activities/dashboard/AchievementsFragment.kt`, `adapters/BadgeListAdapter.kt`, `views/IkdBadgeDayStripView.kt` *(catalog frozen at the 28 v1 badges; groups 2 & 6 are an additive future change, not an edit)*

For the full per-phase forbidden lists see each plan's "Branch & Layering Discipline" section.
