# Project Status

Quick-access summary of phase completion. For detailed scope of each phase see [`FeatureRoadmap.md`](FeatureRoadmap.md) and the per-phase plan files linked below.

| Phase | Title | Status | Plan | Branch |
|---|---|---|---|---|
| 1 | Sensor Calibration & Debug Environment | Implemented | [Phase1_Plan.md](Phase1/Phase1_Plan.md) | merged |
| 1.1 | Live Keyboard Capture & Metric Realignment | Implemented | [Phase1.1_Plan.md](Phase1/Phase1.1_Plan.md) | merged |
| 2 | Background Collection & Local Storage | Implemented | [Phase2_Plan.md](Phase2/Phase2_Plan.md) | merged |
| 3 | User Insights & Dashboard | Implemented | [Phase3_Plan.md](Phase3/Phase3_Plan.md) | merged |
| 4 | Session Detail Refresh | Implemented | [Phase4_Plan.md](Phase4/Phase4_Plan.md) | merged |
| 5 | Session Dashboard | Implemented | [Phase5_Plan.md](Phase5/Phase5_Plan.md) | merged |
| 6 | Diagnostics Screen Improvements | Implemented (reworked) | [Phase6_Plan.md](Phase6/Phase6_Plan.md) | merged |
| 7 | Emoji & Autocorrect Capture | Implemented | [Phase7_Plan.md](Phase7/Phase7_Plan.md) | merged |
| 7.1 | AUTOCORRECT Replacement Weight | Implemented | [Phase7.1_Plan.md](Phase7.1/Phase7.1_Plan.md) | landed on `main` |
| 8 | Mood Bar & Contextual Overlay | Implemented (incl. 8.1 UI polish) | [Phase8_Plan.md](Phase8/Phase8_Plan.md) | merged |
| 8.2 | Mood Bar UX Polish (toggles, capsule background, chat-bubble) | Implemented | — (in-flight; plan rolled into Phase 8 §13) | landed on `main` |
| 8.3 | Mood Distribution over Time (Stacked Bar) | Implemented | [Phase8.3_Plan.md](Phase8/Phase8.3_Plan.md) | landed on `main` |
| 8.4 | Mood-Bar Dance + Haptic on Slot Select | **Planned** | [Phase8.4_Plan.md](Phase8/Phase8.4_Plan.md) | not started |
| 8.5 | Collapsible Mood Bar with Persistent Standing Rating + Inactivity Reset | **Planned** | [Phase8.5_Plan.md](Phase8/Phase8.5_Plan.md) | not started |
| 9 | Global Insights Expansion | Implemented (10 sub-phases; absorbs Phase 11) | [Phase9_Plan.md](Phase9/Phase9_Plan.md) | landed on `main` |
| 9.11 | Insights — TODAY range + vertical tabs | Implemented | [9.11 sub-plan](Phase9/sub_plans/9.11_today_filter_and_tabs.md) | landed on `main` |
| 10 | Rebranding & New Identity (MoodScript) | **Planned (next)** | [Phase10_Plan.md](Phase10/Phase10_Plan.md) | not started |
| ~~11~~ | ~~Usage Map & Daily Activity Charts~~ | ~~Planned~~ — **deleted; absorbed into Phase 9.5–9.10** | — | — |

## Schema state

`IkdDatabase.version = 3` — two migrations registered:

- `Migration(1, 2)` (Phase 8): adds `mood_entries` table (one row per session, ordinal valence score 1–6, CASCADE on session delete).
- `Migration(2, 3)` (Phase 7.1): adds `correction_weight INTEGER NOT NULL DEFAULT 0` column to `ikd_events` and backfills weight 1 onto every existing `is_correction = 1` row so historical error rates remain continuous.

Both migrations are non-destructive and validated end-to-end by `IkdDatabaseMigrationTest` in `app/src/androidTest`.

## Currently on `main` (local, not pushed)

`origin/main` is several commits behind. Recent groups landed locally (newest at top):

- **Phase 9.11 — Insights TODAY range + vertical tabs** (commits `eb8454f2`, `f00b5713`, `be4d98ba`). Adds `Range.TODAY(1, "%Y-%m-%d %H")` as the first option on the Insights range toggle (Today → Week → Month → All Time); X-axis labels render as `HH:00` for hourly buckets. Charts that go degenerate under TODAY (calendar heatmap, daily-keypresses bar, Usage Map) hide cleanly; the Habits longest-streak KPI gains a `StreakUnit.HOURS` mode that prints "Today" / "—". The five Phase 9 dashboard sections become five swipeable tabs via `NavigationRailView` (left rail) + horizontal `ViewPager2`: Trends → Daily Activity → Mood → Keystroke Dynamics → Habits. The global KPI strip, range toggle, and Mood Filter chip row stay above the rail+pager so they apply across every tab. `DashboardActivity` shrunk from ~1100 LOC to ~340 LOC; rendering moved into five `Fragment` subclasses under `activities/dashboard/`. Detekt unchanged at 52 weighted issues (zero drift). New JVM test for the hourly-bucket streak path.

- **Phase 9 — Global Insights Expansion** (10 sub-phase commits `f2d13b33` … `38ae0d63`, all landed on `main`, no feature branch — user override). `DashboardActivity` grew from a single page of three line charts + four-cell KPI strip into a five-section dashboard: **Trends → Daily Activity → Mood → Keystroke Dynamics → Habits**. New surfaces: aggregate gyro/accel magnitude trend charts (9.2); Habits section with four trend charts + 4-cell KPI strip including longest-streak (9.3); single-select Mood Filter chip row that re-runs every aggregator scoped to one Ekman category (9.4); calendar heatmap + daily keypress bar (9.5); 24-hour bar + hour×weekday circadian heatmap (9.6); IKD/Dwell/Flight log-scale histograms (9.7); orientation breakdown donut (9.8); Usage Map bubble chart (9.9); backspaces-vs-autocorrections quality scatter (9.10). Two new custom views (`IkdHeatmapView`, `IkdBubbleMapView`) join the existing `IkdLineChartView` / `IkdStackedBarChartView`. Six new aggregators (`Ikd{Sensor,Habits,Activity,Distribution,Orientation,Quality}Aggregator`) parallel `IkdAggregator`. DAOs gained `*ForMood` two-query variants for the mood filter (orchestrator Decision #14). 32 new JVM tests; lint clean; detekt +4 issues (LargeClass + TooManyFunctions on `DashboardActivity` / DAOs and ComplexCondition on heatmap/bubble views — scope-acceptable). No schema change (`IkdDatabase.version` stays at 3). Capture path frozen. **Phase 11 deleted** — every chart was absorbed into 9.5 / 9.6 / 9.9 / 9.10.

- **Error-rate denominator fix** (commit `569f331b`, landed on `main` between Phase 8.3 and Phase 9). Two bugs in the Phase 7.1 weighted error-rate metric: (1) the denominator only excluded `AUTOCORRECT` rows, so typing `hello world` (11 chars) and backspacing all 11 chars read 11/22 = **50 %** instead of the user-expected 100 %. Fixed by switching the denominator to *productive keystrokes* — `eventCount - correctionCount` — which excludes both `AUTOCORRECT` and `BACKSPACE`. (2) `BACKSPACE` rows always carried `correction_weight = 1`, even when the keypress deleted an N-char selection in one shot — the IME had the deletion count but threw it away. New `SimpleKeyboardIME.computeBackspaceWeight()` helper reads the selection length (or `getCountToDelete()` for grapheme-aware multi-char deletes) before recording the timing event. DAO additive: `correctionCount` projection on `EventBucketRow`. Test fixtures updated: `ocasdasda<space>`+autocorrect stays at 90 %; legacy backfill drifts 18.18 % → 20 %; the user-reported `hello world<BS×11>` case now reads 100 %. Manual test plan saved at [`roadmap/ErrorRateFix_TestPlan.md`](ErrorRateFix_TestPlan.md).

- **Phase 8.3** (landed on `main`, no feature branch — user override) — drops the Avg Mood KPI cell + Mood-over-Time line chart from the global dashboard; replaces them with a per-bucket stacked-bar mood-mix chart that shows the percentage breakdown of each Ekman category over time. Mood Distribution panel preserved verbatim. Read-side only, no schema change.
- **Phase 7.1** (landed on `main`, no feature branch — user override) — `correction_weight` column on `ikd_events` (`Migration(2, 3)`); weighted error-rate formula `100 * SUM(correction_weight) / keystrokeCount`; CSV gains an eighth column. Folded in a Phase 7 follow-up fix for the autocorrect false-positive when the IME commits onto a user-selected range.
- **Phase 8.2** (commits `44f18f06`, `14be3079`, landed on `main` ahead of 8.3) — mood bar promoted to a centered "stretched-key" capsule with theme-aware `mKeyColor`/`mStrokeColor` background; chat-bubble `PopupWindow` replaces toasts with first-person feedback ("I'm feeling happy", "I want privacy"); every slot is now a true toggle (tap-highlighted-slot deselects); selected glyph rendered at `scaleX/scaleY 1.25` (rendering only — tap area unchanged); dimmed alpha tightened 0.6 → 0.45; clipboard chip / inline suggestions / clear / voice buttons hidden while the bar is on; `Config.showMoodPopup` (default `true`) plus settings row to silence the bubble without disabling the state change. New `IkdMoodBarController.disablePrivacy` / `clearMoodForActiveSession` controller methods mirror the existing `enablePrivacyAndClearMood` path. No schema change.
- **Phase 8.1 UI polish** (`fix/phase8-ui-polish`, merged) — mood bar sizing/centering, dashboard card theming, `Config.showMoodBar` toggle, dedupe of the duplicate session-dashboard mood chip. Reverses Phase 8 Decision #9; full change log in [`Phase8_Plan.md` Section 12](Phase8/Phase8_Plan.md#12-post-merge-ui-polish-phase-81).
- **Phase 8** (`feat/phase8-mood-bar`, merged) — schema bump 1→2, seven-button Ekman-6 emotion bar, mood KPI on session dashboard, Mood-over-Time chart + Mood Distribution panel + Avg Mood KPI on global dashboard, third CSV block.
- Phase 7 (merged), Phase 6 rework (merged), and the diagnostics KPI metric polish are all upstream of the above.

## Next step

**Phase 8.5 — Collapsible Mood Bar with Persistent Standing Rating + Inactivity Reset.** A medium UX rework: the seven-button mood bar moves to the leading edge of the toolbar, defaults to a collapsed one-slot chip showing the user's standing self-rating, and expands on tap. The standing rating is remembered across sessions via a display-only `Config.lastMoodScore` (`mood_entries` semantic is unchanged — a row is written only when the user explicitly taps in this session) and auto-clears after one hour of inactivity. Reopens `views/MyKeyboardView.kt`, the keyboard layout XML, `Constants.kt` / `Config.kt` / `MoodEmoji.kt`, `IkdMoodBarController.kt`, and adds two narrow `SimpleKeyboardIME` lifecycle wires. No schema migration, no DAO changes, no aggregator changes, no capture-path semantic change. Plan file: [`Phase8.5_Plan.md`](Phase8/Phase8.5_Plan.md).

**Phase 8.4 — Mood-Bar Dance + Haptic on Slot Select** remains planned and is compose-safe with 8.5 in either landing order (8.5 Decision #11). It's the smaller of the two; either can land first.

After 8.4 + 8.5 land, the next planned phase is **Phase 10 — Rebranding & New Identity**: retire the "Fossify Keyboard" name and visual identity to honestly reflect a behavioural-analytics research platform. Scope: new app name + `applicationId` (with one-time `ikd.db` copy on package rename), new launcher icon, primary colour token migration, store-listing rewrite. No detailed plan file yet — drafted from `FeatureRoadmap.md` § Phase 10 when ready to start.

The user-reported error-rate fix (`569f331b`) remains worth surfacing for on-device review — see [`roadmap/ErrorRateFix_TestPlan.md`](ErrorRateFix_TestPlan.md) for the manual test sequence.

## Frozen surfaces (do not edit without an explicit unfreeze in a phase plan)

- Capture path: `SimpleKeyboardIME.kt` *(Phase 7.1 reopened for the autocorrect heuristic; the error-rate fix `569f331b` reopened it once more for `computeBackspaceWeight`)*, `LiveCaptureSessionStore.kt`, `KinematicSensorHelper.kt`, `IkdRetentionWorker.kt`
- Schema entities: `IkdEvent`, `SensorSample`, `SessionRecord`, `MoodEntry`
- Read pipeline: `IkdAggregator` *(Phase 9.4 added `moodFilter` parameter)*, `IkdSessionStatsLoader`, `IkdSessionChartLoader`, `IkdMoodLoader`, `IkdMoodAggregator`. New Phase 9 aggregators (`IkdSensorAggregator`, `IkdHabitsAggregator`, `IkdActivityAggregator`, `IkdDistributionAggregator`, `IkdOrientationAggregator`, `IkdQualityAggregator`) join the frozen list now that 9 has shipped.
- CSV: `IkdCsvWriter.kt` (Phase 8 added the third dual-block segment; Phase 7.1 added `correction_weight`; the format is again frozen)
- Charts: `views/IkdLineChartView.kt` (single-series only). Phase 9 adds `views/IkdStackedBarChartView.kt`, `views/IkdHeatmapView.kt`, `views/IkdBubbleMapView.kt` to the frozen list.
- Phase 6 surface: `DiagnosticsActivity.kt`, `activity_diagnostics.xml` *(error-rate fix updated `updateComputedMetrics` denominator)*
- Phase 5 surface: `EventFeedActivity.kt`, `activity_event_feed.xml` *(Phase 8.1 made the dedupe edit; otherwise frozen)*
- Phase 3 surface: `DashboardActivity.kt`, `activity_dashboard.xml` *(Phase 8 / 8.1 / 8.3 / 9 reopened cumulatively; now frozen post-Phase 9)*
- Phase 8 surface: `views/MyKeyboardView.kt` (mood bar wiring), `helpers/MoodEmoji.kt`, `helpers/IkdMoodBarController.kt`, `models/MoodEntry.kt`, `interfaces/MoodDao.kt`

For the full per-phase forbidden lists see each plan's "Branch & Layering Discipline" section.
