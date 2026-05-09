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
| 8 | Mood Bar & Contextual Overlay | **Planned (next)** | [Phase8_Plan.md](Phase8/Phase8_Plan.md) | not started |
| 9 | Global Insights Expansion | Planned | — | not started |
| 10 | Rebranding & New Identity | Planned | — | not started |
| 11 | Usage Map & Daily Activity Charts | Planned | — | not started |

## Schema state

`IkdDatabase.version = 1` — no migration yet. Phase 8 will be the first bump (1 → 2, adding `mood_entries`).

## Currently on `main` (local, not pushed)

13 commits ahead of `origin/main`:
- 4 commits — Phase 6 rework (theme-aware status chip, KPI metric grid, persisted sensor collapse, animated chevron)
- 1 plan-doc commit — Phase 6 rework plan
- 4 feature commits — Phase 7 (EMOJI + AUTOCORRECT capture, WPM exclusion)
- 2 merge commits — `feat/phase6-rework` and `feat/phase7-emoji-autocorrect-capture`
- 1 plan-doc commit — Phase 8 plan + stale-numbering fixes
- 1 docs commit — CLAUDE.md / FeatureRoadmap updates after Phase 6 rework

## Next step

Cut `feat/phase8-mood-bar` from `main` and execute the four sub-phases in [Phase8_Plan.md](Phase8/Phase8_Plan.md):

1. **8.1** — Schema migration + `MoodEntry` entity + `MoodDao`
2. **8.2** — Mood bar on keyboard top bar (gated by `Config.showMoodBar`)
3. **8.3** — Mood KPI cell + metadata chip on session dashboard
4. **8.4** — Mood trend chart + Avg Mood KPI on global dashboard

## Frozen surfaces (do not edit without an explicit unfreeze in a phase plan)

- Capture path: `SimpleKeyboardIME.kt`, `LiveCaptureSessionStore.kt`, `KinematicSensorHelper.kt`
- Schema entities: `IkdEvent`, `SensorSample`, `SessionRecord`
- Read pipeline: `IkdAggregator`, `IkdSessionStatsLoader`, `IkdSessionChartLoader`
- CSV: `IkdCsvWriter.kt` (Phase 8 will append a third dual-block segment — strictly additive)
- Charts: `views/IkdLineChartView.kt` (single-series only)
- Phase 6 surface: `DiagnosticsActivity.kt`, `activity_diagnostics.xml`
- Phase 5 surface: `EventFeedActivity.kt`, `activity_event_feed.xml`
- Phase 3 surface: `DashboardActivity.kt`, `activity_dashboard.xml` (Phase 8 will reopen for the fourth chart)

For the full per-phase forbidden lists see each plan's "Branch & Layering Discipline" section.
