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
| 9 | Global Insights Expansion | Implemented (10 sub-phases; absorbs Phase 11) | [Phase9_Plan.md](Phase9/Phase9_Plan.md) | landed on `main` |
| 10 | Rebranding & New Identity | **Planned (next)** | — | not started |
| ~~11~~ | ~~Usage Map & Daily Activity Charts~~ | ~~Planned~~ — **deleted; absorbed into Phase 9.5–9.10** | — | — |

## Schema state

`IkdDatabase.version = 3` — two migrations registered:

- `Migration(1, 2)` (Phase 8): adds `mood_entries` table (one row per session, ordinal valence score 1–6, CASCADE on session delete).
- `Migration(2, 3)` (Phase 7.1): adds `correction_weight INTEGER NOT NULL DEFAULT 0` column to `ikd_events` and backfills weight 1 onto every existing `is_correction = 1` row so historical error rates remain continuous.

Both migrations are non-destructive and validated end-to-end by `IkdDatabaseMigrationTest` in `app/src/androidTest`.

## Currently on `main` (local, not pushed)

`origin/main` is several commits behind. Recent groups landed locally (newest at top):

- **Phase 8.3** (landed on `main`, no feature branch — user override) — drops the Avg Mood KPI cell + Mood-over-Time line chart from the global dashboard; replaces them with a per-bucket stacked-bar mood-mix chart that shows the percentage breakdown of each Ekman category over time. Mood Distribution panel preserved verbatim. Read-side only, no schema change.
- **Phase 7.1** (landed on `main`, no feature branch — user override) — `correction_weight` column on `ikd_events` (`Migration(2, 3)`); weighted error-rate formula `100 * SUM(correction_weight) / keystrokeCount`; CSV gains an eighth column. Folded in a Phase 7 follow-up fix for the autocorrect false-positive when the IME commits onto a user-selected range.
- **Phase 8.2** (commits `44f18f06`, `14be3079`, landed on `main` ahead of 8.3) — mood bar promoted to a centered "stretched-key" capsule with theme-aware `mKeyColor`/`mStrokeColor` background; chat-bubble `PopupWindow` replaces toasts with first-person feedback ("I'm feeling happy", "I want privacy"); every slot is now a true toggle (tap-highlighted-slot deselects); selected glyph rendered at `scaleX/scaleY 1.25` (rendering only — tap area unchanged); dimmed alpha tightened 0.6 → 0.45; clipboard chip / inline suggestions / clear / voice buttons hidden while the bar is on; `Config.showMoodPopup` (default `true`) plus settings row to silence the bubble without disabling the state change. New `IkdMoodBarController.disablePrivacy` / `clearMoodForActiveSession` controller methods mirror the existing `enablePrivacyAndClearMood` path. No schema change.
- **Phase 8.1 UI polish** (`fix/phase8-ui-polish`, merged) — mood bar sizing/centering, dashboard card theming, `Config.showMoodBar` toggle, dedupe of the duplicate session-dashboard mood chip. Reverses Phase 8 Decision #9; full change log in [`Phase8_Plan.md` Section 12](Phase8/Phase8_Plan.md#12-post-merge-ui-polish-phase-81).
- **Phase 8** (`feat/phase8-mood-bar`, merged) — schema bump 1→2, seven-button Ekman-6 emotion bar, mood KPI on session dashboard, Mood-over-Time chart + Mood Distribution panel + Avg Mood KPI on global dashboard, third CSV block.
- Phase 7 (merged), Phase 6 rework (merged), and the diagnostics KPI metric polish are all upstream of the above.

## Next step

**Phase 9 — Global Insights Expansion.** Plan split into an orchestrator + 10 self-contained sub-plan files so each implementer agent loads only its own scope:

- [`Phase9/Phase9_Plan.md`](Phase9/Phase9_Plan.md) — orchestrator (~210 lines): implementation order, agent assignments, dependency graph, frozen surfaces, cross-cutting decisions.
- [`Phase9/sub_plans/9.X_*.md`](Phase9/sub_plans/) — 10 sub-plan files (~44–120 lines each), one per sub-phase.

Implementer agents are briefed with the orchestrator path + their assigned sub-plan path; no other sub-plan is loaded. Ten sub-phases on a single read-side-only branch — Phase 11 is absorbed entirely (orchestrator Decision #16):

- 9.1 — Mood Section formalisation (label + group existing 8.3 widgets).
- 9.2 — Aggregate gyro / accel magnitude trend charts on the global dashboard.
- 9.3 — Typing Habits section (avg session duration / sessions per day / avg error rate / avg flight time + four-cell Habits KPI strip with longest-streak).
- 9.4 — Mood Filter chip row above the range selector — re-renders every chart and KPI scoped to a chosen Ekman category. Single-select, persisted in `onSaveInstanceState`. Tapping the active chip toggles back to `All`.
- 9.5 — *BiAffect-inspired.* Calendar heatmap (week × day-of-week, intensity = keystrokes) + daily keypress bar chart.
- 9.6 — *BiAffect-inspired.* 24-hour bar chart (range-scoped) + hour × weekday heatmap (always all-time).
- 9.7 — *BiAffect-inspired.* IKD / dwell / flight distribution histograms with log-scale X axis.
- 9.8 — Orientation breakdown donut surfacing `SessionRecord.orientation` for the first time.
- 9.9 — *Lifted from Phase 11.* Bubble Usage Map (date × hour, bubble size = keystroke count, tooltip on tap).
- 9.10 — *Lifted from Phase 11.* Activity Quality scatter (backspaces vs. autocorrections per day, most-recent-day visually emphasised).

Five labelled sections on the dashboard: **Trends → Daily Activity → Mood → Keystroke Dynamics → Habits**. Two new custom `View` wrappers (`IkdHeatmapView`, `IkdBubbleMapView`) sit alongside the existing `IkdLineChartView` / `IkdStackedBarChartView`. No schema bump (`IkdDatabase.version` stays at 3). No keyboard-layer reopen. No new Gradle dependency. Forbidden surfaces enumerated in plan §2.

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
