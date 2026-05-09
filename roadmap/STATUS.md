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
| 8.3 | Mood Distribution over Time (Stacked Bar) | Implemented | [Phase8.3_Plan.md](Phase8/Phase8.3_Plan.md) | landed on `main` |
| 9 | Global Insights Expansion | **Planned (next)** | — | not started |
| 10 | Rebranding & New Identity | Planned | — | not started |
| 11 | Usage Map & Daily Activity Charts | Planned | — | not started |

## Schema state

`IkdDatabase.version = 3` — two migrations registered:

- `Migration(1, 2)` (Phase 8): adds `mood_entries` table (one row per session, ordinal valence score 1–6, CASCADE on session delete).
- `Migration(2, 3)` (Phase 7.1): adds `correction_weight INTEGER NOT NULL DEFAULT 0` column to `ikd_events` and backfills weight 1 onto every existing `is_correction = 1` row so historical error rates remain continuous.

Both migrations are non-destructive and validated end-to-end by `IkdDatabaseMigrationTest` in `app/src/androidTest`.

## Currently on `main` (local, not pushed)

`origin/main` is several commits behind. Recent groups landed locally (newest at top):

- **Phase 8.3** (landed on `main`, no feature branch — user override) — drops the Avg Mood KPI cell + Mood-over-Time line chart from the global dashboard; replaces them with a per-bucket stacked-bar mood-mix chart that shows the percentage breakdown of each Ekman category over time. Mood Distribution panel preserved verbatim. Read-side only, no schema change.
- **Phase 7.1** (landed on `main`, no feature branch — user override) — `correction_weight` column on `ikd_events` (`Migration(2, 3)`); weighted error-rate formula `100 * SUM(correction_weight) / keystrokeCount`; CSV gains an eighth column. Folded in a Phase 7 follow-up fix for the autocorrect false-positive when the IME commits onto a user-selected range.
- **Phase 8.1 UI polish** (`fix/phase8-ui-polish`, merged) — mood bar sizing/centering, dashboard card theming, `Config.showMoodBar` toggle, dedupe of the duplicate session-dashboard mood chip. Reverses Phase 8 Decision #9; full change log in [`Phase8_Plan.md` Section 12](Phase8/Phase8_Plan.md#12-post-merge-ui-polish-phase-81).
- **Phase 8** (`feat/phase8-mood-bar`, merged) — schema bump 1→2, seven-button Ekman-6 emotion bar, mood KPI on session dashboard, Mood-over-Time chart + Mood Distribution panel + Avg Mood KPI on global dashboard, third CSV block.
- Phase 7 (merged), Phase 6 rework (merged), and the diagnostics KPI metric polish are all upstream of the above.

## Next step

**Phase 9 — Global Insights Expansion.** No plan file yet — the spec lives in [`FeatureRoadmap.md` § Phase 9](FeatureRoadmap.md#phase-9-global-insights-expansion). When ready, draft a `Phase9/Phase9_Plan.md` with the same shape as Phase 8, then cut `feat/phase9-*`.

## Frozen surfaces (do not edit without an explicit unfreeze in a phase plan)

- Capture path: `SimpleKeyboardIME.kt` *(Phase 7.1 has a scoped reopen for the autocorrect heuristic)*, `LiveCaptureSessionStore.kt`, `KinematicSensorHelper.kt`, `IkdRetentionWorker.kt`
- Schema entities: `IkdEvent`, `SensorSample`, `SessionRecord`, `MoodEntry`
- Read pipeline: `IkdAggregator`, `IkdSessionStatsLoader`, `IkdSessionChartLoader`, `IkdMoodLoader`, `IkdMoodAggregator`
- CSV: `IkdCsvWriter.kt` (Phase 8 added the third dual-block segment; the format is again frozen)
- Charts: `views/IkdLineChartView.kt` (single-series only)
- Phase 6 surface: `DiagnosticsActivity.kt`, `activity_diagnostics.xml`
- Phase 5 surface: `EventFeedActivity.kt`, `activity_event_feed.xml` *(Phase 8.1 made the dedupe edit; otherwise frozen)*
- Phase 3 surface: `DashboardActivity.kt`, `activity_dashboard.xml` *(Phase 8 reopened for the fourth chart and distribution panel; Phase 8.1 added card theming)*
- Phase 8 surface: `views/MyKeyboardView.kt` (mood bar wiring), `helpers/MoodEmoji.kt`, `helpers/IkdMoodBarController.kt`, `models/MoodEntry.kt`, `interfaces/MoodDao.kt`

For the full per-phase forbidden lists see each plan's "Branch & Layering Discipline" section.
