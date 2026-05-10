# Phase 9 — Global Insights Expansion (Orchestrator)

**Status:** Planned · spec complete · no branch yet
**Depends on:** Phase 8 + 8.1 + 8.2 + 8.3 (mood entries in `ikd.db`, current dashboard surface)
**Absorbs:** the deferred Phase 11 ("Usage Map & Daily Activity Charts") — every Phase 11 chart that survived design review is lifted into a Phase 9 sub-phase. Phase 11 is deleted from the roadmap.
**Branch (proposed):** `feat/phase9-global-insights-expansion` — single branch, **ten** sub-phase commits (Phase-8 sub-phase shape).
**Read-side only:** `IkdDatabase.version` stays at 3, no schema migration, no keyboard-layer reopen, no new Gradle dependency, CSV format unchanged.

This file is an **orchestrator**. It contains everything that's shared across sub-phases — implementation order, dependency graph, agent assignments, frozen surfaces, cross-cutting decisions — and links to the per-sub-phase plan files. Each sub-plan in [`sub_plans/`](sub_plans/) is self-contained: an implementer agent loads only its own sub-plan plus this orchestrator (referenced for the cross-cutting tables), keeping context windows small.

---

## Goal

Expand `DashboardActivity` from a single page of three IKD line charts + a four-cell KPI strip into a multi-section research-style global insights hub modelled after the [BiAffect dashboard](https://www.biaffect.com/8203biaffect-meets-the-world/tracking-mental-health-through-keystroke-dynamics-with-biaffect) (UIC, Leow et al.). Five labelled dashboard sections after Phase 9 lands:

1. **Trends** — speed / IKD / error rate (existing, reorganised under header) + gyro / accel global trends (9.2).
2. **Daily Activity** — calendar heatmap + daily keypress bar (9.5) + 24-hour bar + hour×weekday heatmap (9.6) + Usage Map bubble chart (9.9).
3. **Mood** — Phase 8.3 stacked-bar mood-mix + Mood Distribution panel (existing, reorganised under header).
4. **Keystroke Dynamics** — IKD / dwell / flight histograms (9.7) + Orientation breakdown donut (9.8).
5. **Habits** — Habits KPI strip + four trend charts (9.3) + activity-quality scatter (9.10).

Plus one cross-cutting capability: **Mood Filter** chip row (9.4) — re-renders every chart and KPI scoped to a chosen Ekman category.

---

## Implementation order

10 sub-phase commits on a single branch. The dependency graph below is acyclic — independent sub-phases (9.2, 9.3, 9.7, 9.8) can ship in any order between their gates.

```
9.1  ─────►  9.2 ─────────────────────────────────────►  9.4
  │      └►  9.3 ──────────────────────────────────► 9.10 ► 9.4
  │      └►  9.5 ─►  9.6 ─►  9.9 ──────────────────►  9.4
  │      └►  9.7 ──────────────────────────────────►  9.4
  │      └►  9.8 ──────────────────────────────────►  9.4
```

| # | Sub-phase | Plan file | Depends on | Commit title |
|---|---|---|---|---|
| 1 | **9.1** Mood Section formalisation | [`sub_plans/9.1_mood_section_formalisation.md`](sub_plans/9.1_mood_section_formalisation.md) | none | `feat(phase9.1): formalise dashboard sections with primary-coloured headers` |
| 2 | **9.2** Gyro & Accel global trends | [`sub_plans/9.2_sensor_global_trends.md`](sub_plans/9.2_sensor_global_trends.md) | 9.1 | `feat(phase9.2): add aggregate gyro/accel magnitude trend charts` |
| 3 | **9.3** Typing Habits + KPI strip | [`sub_plans/9.3_typing_habits.md`](sub_plans/9.3_typing_habits.md) | 9.1 | `feat(phase9.3): add Typing Habits section with KPI strip and four trend charts` |
| 4 | **9.5** Daily Activity (calendar + daily bar) | [`sub_plans/9.5_daily_activity.md`](sub_plans/9.5_daily_activity.md) | 9.1 | `feat(phase9.5): add Daily Activity section with calendar heatmap and daily keypress bar` |
| 5 | **9.6** Hourly Activity (24-bar + hour×weekday heatmap) | [`sub_plans/9.6_hourly_activity.md`](sub_plans/9.6_hourly_activity.md) | 9.5 | `feat(phase9.6): add hourly activity bar and hour-by-weekday circadian heatmap` |
| 6 | **9.9** Usage Map (bubble) | [`sub_plans/9.9_usage_map.md`](sub_plans/9.9_usage_map.md) | 9.5, 9.6 | `feat(phase9.9): add Usage Map bubble chart` |
| 7 | **9.7** Keystroke Dynamics histograms | [`sub_plans/9.7_keystroke_distribution.md`](sub_plans/9.7_keystroke_distribution.md) | 9.1 | `feat(phase9.7): add IKD/Dwell/Flight distribution histograms` |
| 8 | **9.8** Orientation Breakdown | [`sub_plans/9.8_orientation_breakdown.md`](sub_plans/9.8_orientation_breakdown.md) | 9.7 | `feat(phase9.8): add orientation breakdown donut to Keystroke Dynamics section` |
| 9 | **9.10** Activity Quality Scatter | [`sub_plans/9.10_activity_quality_scatter.md`](sub_plans/9.10_activity_quality_scatter.md) | 9.3 | `feat(phase9.10): add backspaces-vs-autocorrections scatter` |
| 10 | **9.4** Mood Filter chip row (LAST — cross-cutting) | [`sub_plans/9.4_mood_filter.md`](sub_plans/9.4_mood_filter.md) | 9.2, 9.3, 9.5, 9.6, 9.7, 9.8, 9.9, 9.10 | `feat(phase9.4): mood filter chip row re-renders dashboard scoped to Ekman category` |

**Why 9.4 last.** It's cross-cutting — it adds a `moodFilter: Int? = null` parameter to every aggregator created in 9.2 / 9.3 / 9.5–9.10. Implementing it last means every aggregator already exists; 9.4 only swaps `null` for `currentMoodFilter` at the call sites and adds the chip row UI.

**Parallelisation.** A single agent can work the list top-to-bottom; multiple agents can parallelise the four independent branches (9.2 / 9.3 / [9.5→9.6→9.9] / [9.7→9.8] / 9.10) in separate worktrees if scheduled in parallel. The `android-feature-implementer` agent is suitable for every sub-phase.

---

## Agent assignment

Every sub-phase is implemented by [`android-feature-implementer`](../../.claude/agents/android-feature-implementer.md). To launch a sub-phase, brief the agent with **only**:

1. The path to the sub-phase plan: `roadmap/Phase9/sub_plans/9.X_<name>.md`
2. The path to this orchestrator (for the Frozen Surfaces table and the Cross-Cutting Decisions table): `roadmap/Phase9/Phase9_Plan.md`

Do not load other sub-plans into the agent's context — each sub-plan is self-contained and references this orchestrator for shared concerns. This is the entire reason the plan was split.

---

## Frozen surfaces

Every Phase 9 sub-phase respects the same frozen-surfaces list. Files **not** to touch under any circumstance:

| Surface | Phase frozen | Notes |
|---|---|---|
| `services/SimpleKeyboardIME.kt` | 7.1 | Capture path. Phase 7.1 had a scoped reopen for the autocorrect heuristic — closed since. |
| `helpers/LiveCaptureSessionStore.kt` | 7.1 | Capture buffer. |
| `helpers/KinematicSensorHelper.kt` | 1.1 | Sensor capture. |
| `helpers/IkdRetentionWorker.kt` | 2 | Retention worker. |
| `databases/IkdDatabase.kt` | 7.1 | `IkdDatabase.version = 3`. **No schema migration in Phase 9.** |
| `models/IkdEvent.kt`, `SensorSample.kt`, `SessionRecord.kt`, `MoodEntry.kt` | 8 / 7.1 | Schema entities. |
| `helpers/IkdCsvWriter.kt` | 8 | CSV format. Phase 9 doesn't touch the export. |
| `helpers/IkdMoodAggregator.kt`, `IkdMoodLoader.kt`, `IkdMoodBarController.kt` | 8.3 | Mood read surface. Phase 9 reads through public APIs only — the mood widgets are reused unchanged. |
| `helpers/IkdSessionStatsLoader.kt`, `helpers/IkdSessionChartLoader.kt` | 5 | Per-session loaders. |
| `views/IkdLineChartView.kt`, `views/IkdStackedBarChartView.kt` | 8.3 | Chart wrappers. **Reused unchanged** through their existing `setData(...)` APIs. |
| `views/MyKeyboardView.kt`, `res/layout/keyboard_view_keyboard.xml` | 8.2 | Mood bar UI. |
| `activities/EventFeedActivity.kt`, `res/layout/activity_event_feed.xml` | 8.1 | Per-session dashboard. |
| `activities/IkdSettingsActivity.kt`, `res/layout/activity_ikd_settings.xml` | 8.2 | Settings rows. **No new prefs in Phase 9.** |
| `activities/DiagnosticsActivity.kt`, `res/layout/activity_diagnostics.xml`, `res/menu/menu_diagnostics.xml`, status-chip colour resources | 6 | Diagnostics screen. |
| `helpers/Constants.kt`, `helpers/Config.kt` | 9 (new) | **No new pref keys.** Filter state lives on `Bundle` only, not `SharedPreferences`. |

The capture path (events, sessions, sensor samples, mood entries) and the read-side loaders for *single-session* dashboards remain entirely untouched. Every Phase 9 edit lives on the global dashboard activity, the new aggregators, additive DAO queries, and the two new custom `View` classes (`IkdHeatmapView`, `IkdBubbleMapView`).

---

## Cross-cutting decisions

Each sub-plan refers to these decision numbers without restating the rationale.

| # | Topic | Decision |
|---|---|---|
| 1 | Phase shape | **Read-side only.** Capture path + schema frozen. Mirrors Phases 5, 8.3. |
| 2 | Mood-filter persistence | **`onSaveInstanceState` only — not `SharedPreferences`.** Filter is per-screen-instance. No new pref keys. |
| 3 | Aggregator strategy | **One aggregator per logical surface, six new ones.** Extending `IkdAggregator.Snapshot` would bloat it past unit-test readability; six aggregators run in parallel via `async` on the same `Dispatchers.IO` hop in `loadDashboard()`. |
| 4 | Mood-filter chip position | **Above the range selector.** Filter chooses sessions; range chooses time-window of those sessions. Outermost filter first. |
| 5 | Heatmaps / scatter / bubble | **Now in Phase 9** (was deferred to Phase 11; reversed by user request). Two new custom `View` wrappers (`IkdHeatmapView`, `IkdBubbleMapView`) follow existing chart-wrapper discipline. |
| 6 | New activity for any sub-phase | **No.** Every sub-phase lands on `DashboardActivity`. *"organize everything inside the insights"* (user). |
| 7 | Mood-filter chip vs. dropdown | **Chip row.** Surfaces all seven choices, mirrors keyboard mood bar mental model. |
| 8 | Streak suffix (days/weeks) | **Carried by `HabitsSnapshot.streakUnit: StreakUnit` enum.** Single `when` block in the activity. |
| 9 | All-Time streak granularity | **Weeks for All Time, days for Week / Month.** Matches the bucket key. KPI label suffix changes accordingly. |
| 10 | Frozen surfaces | **Strict.** `IkdMoodAggregator`, `IkdLineChartView`, `IkdStackedBarChartView`, etc. reused via existing public APIs only. |
| 11 | Compound filters | **Out of scope.** Single-axis chip row only. Filter-by-mood × filter-by-locale × … is a future phase. |
| 12 | Chip toggle behaviour | **Tap-checked-chip reverts to `All`.** Mirrors Phase 8.2 mood-bar slot toggles. |
| 13 | Mood widgets under non-`All` filter | **Render the degenerate result.** Distribution panel collapses to one row, stacked bar to one colour — that's the user feedback that the filter is in effect. |
| 14 | Filter SQL (`IS NULL` vs. two queries) | **Two queries** for `IkdEventDao` + `SessionDao` (highest-frequency tables). **One parameterised** `IS NULL` query for `SensorSampleDao`, `MoodDao`, and the new aggregators (lower frequency, acceptable cost). |
| 15 | New deps | **None.** MPAndroidChart on classpath since Phase 3; Material `Chip` already in use. The two custom `View` classes use `Canvas` + `Paint` only. |
| 16 | Phase 11 disposition | **Deleted.** Every Phase 11 chart absorbed into a Phase 9 sub-phase (9.6 / 9.9 / 9.10). `STATUS.md` and `FeatureRoadmap.md` drop the Phase 11 entry. |
| 17 | Section count | **Five labelled sections, not ten.** Sub-phases group inside the section that logically owns them. |
| 18 | Hour×weekday heatmap range scope | **Always all-time.** Single-week data is too sparse. Card subtitle reads "Across all your sessions". |
| 19 | Histogram chart wrapper | **New `IkdHistogramView` wrapping MPAndroidChart `BarChart`** for log-scale X axis + outlier annotation. |
| 20 | Histogram bucket edges | **Hardcoded log-scale `LongArray` constant.** Three queries per snapshot (one per metric). SQL `CASE` ladder generated in Kotlin from `BUCKET_EDGES_MS`. |
| 21 | Histogram outliers | **Clipped into the top bucket** + small "+ outliers" annotation when the overflow is non-empty. |
| 22 | Bubble Usage Map data source | **Reuses `dayHourCells`** payload from `IkdActivityAggregator` (added in 9.6). Zero additional queries for 9.9. |
| 23 | Bubble colour-by-locale | **Conditional.** When > 1 locale exists in the range, colour bubbles per locale; otherwise all `getProperPrimaryColor()`. |
| 24 | Tooltip / drilldown on heatmap & bubble cells | **Toast / snackbar only.** No detail screen. |

---

## Performance budget

| Aggregator | Worst case | Target |
|---|---|---|
| `IkdAggregator.snapshot` | 30 rows | < 150 ms |
| `IkdSensorAggregator.snapshot` | 60 rows (Month, 2 sensors) | < 150 ms |
| `IkdHabitsAggregator.snapshot` | 30 rows | < 150 ms |
| `IkdActivityAggregator.snapshot` | 720 rows (30d × 24h Usage Map) | < 250 ms |
| `IkdDistributionAggregator.snapshot` | 30 rows (3 histograms × ~10 buckets) | < 150 ms |
| `IkdOrientationAggregator.snapshot` | ~5 rows | < 50 ms |
| `IkdQualityAggregator.snapshot` | 90 rows | < 100 ms |
| **Total** `loadDashboard()` (all aggregators in parallel via `async` on one `Dispatchers.IO` hop) | bounded by slowest single query | **< 600 ms** |

Every aggregator emits a `Log.d(<TAG>, "snapshot(${range.name}, mood=${moodFilter}) took ${ms}ms")` in `BuildConfig.DEBUG` builds, mirroring the existing `IkdAggregator` and `IkdMoodAggregator` perf logs.

---

## Privacy invariants

Phase 9 reads existing tables only — no new captured data, no new pref keys (`Constants.kt` and `Config.kt` stay frozen — Decision #2), no CSV format change, no schema migration. The mood filter joins on already-stored `mood_entries.session_id` and `mood_entries.mood_score`; nothing is written. Every visible UI string lives in `strings.xml` and never echoes captured content.

---

## Acceptance gate (whole phase)

- [ ] All 10 sub-phase commits land on `feat/phase9-global-insights-expansion` in the order above.
- [ ] `./gradlew assembleCoreDebug` and `./gradlew testCoreDebugUnitTest` succeed at every commit.
- [ ] `./gradlew detekt` and `./gradlew lint` clean at the final commit.
- [ ] Five labelled section headers render in order: **Trends → Daily Activity → Mood → Keystroke Dynamics → Habits**.
- [ ] Total `loadDashboard()` finishes < 600 ms for a Month range with 1000 events / 100 sessions.
- [ ] No file in the [Frozen Surfaces](#frozen-surfaces) table is touched (verifiable via `git diff main..HEAD --name-only`).
- [ ] Mood filter chip row re-renders every chart and every KPI in the dashboard within the same `loadDashboard()` hop budget.
- [ ] `roadmap/STATUS.md` Phase 9 row updated to `Implemented`; Phase 11 row stays deleted.
- [ ] `roadmap/FeatureRoadmap.md` Phase 9 section updated to Implemented; Phase 11 section stays deleted.
- [ ] `CLAUDE.md` gains a Phase 9 architecture section covering the ten sub-phases.

---

## References

### Internal patterns

| Source | What it grounds |
|---|---|
| [`Phase3/Phase3_Plan.md`](../Phase3/Phase3_Plan.md) | Original aggregator pattern (suspend method, pure `Companion.buildSnapshot`, JVM tests, perf log). The six new Phase 9 aggregators replicate this. |
| [`Phase5/Phase5_Plan.md`](../Phase5/Phase5_Plan.md) | `AVG(x*x+y*y+z*z)` SQL + Kotlin `sqrt` split for sensor magnitudes (used in 9.2). |
| [`Phase6/Phase6_Plan.md`](../Phase6/Phase6_Plan.md) §3 | Theme integration discipline — no hardcoded hex literals, every colour from `res/values{,-night}/colors.xml`. |
| [`Phase8_Plan.md`](../Phase8/Phase8_Plan.md) §4 | Single-select state machine pattern (used in 9.4 chip row). |
| [`Phase8.3_Plan.md`](../Phase8/Phase8.3_Plan.md) §3 | Composition pattern of multiple aggregators on one `Dispatchers.IO` hop. |

### External (BiAffect)

Phase 9 takes explicit design inspiration from the BiAffect research dashboard at the user's request. Where a sub-phase below is BiAffect-inspired, its plan file calls out the BiAffect surface it mirrors.

| Sub-phase | BiAffect surface |
|---|---|
| 9.5 | "Daily keypresses graph" (BiAffect3 v3.0.2 release notes) + dashboard daily-activity grid |
| 9.6 | "Circadian rhythms visualization" + activity heatmaps |
| 9.7 | Keystroke-dynamics distribution research (JMIR 2018 paper, log-normal flight-time tails) |
| 9.9 | Date×hour activity dashboard |
| 9.10 | Errors / pauses / backspaces surface |

| Source | URL |
|---|---|
| BiAffect — "Tracking mental health through keystroke dynamics" | <https://www.biaffect.com/8203biaffect-meets-the-world/tracking-mental-health-through-keystroke-dynamics-with-biaffect> |
| BiAffect main site | <https://www.biaffect.com> |
| BiAffect3 on the App Store | <https://apps.apple.com/us/app/biaffect3/id6451423106> |
| Zulueta et al. (2018) JMIR — Predicting Mood Disturbance Severity with Mobile Phone Keystroke Metadata | <https://www.jmir.org/2018/7/e241/> |
| Vesel et al. (2020) — Keystroke dynamics + mood + aging diurnal patterns | <https://pubmed.ncbi.nlm.nih.gov/32467973/> |
| Leow et al. (2025) Frontiers Psychiatry — Cognitive function in bipolar disorder via BiAffect | <https://www.frontiersin.org/journals/psychiatry/articles/10.3389/fpsyt.2025.1430303/full> |

---

## Explicitly deferred to later phases

After Phase 11 was absorbed (Decision #16):

- **Compound filters** — out of scope (Decision #11). Hypothetical Phase 12+.
- **Per-app session attribution** — would require new capture-layer instrumentation; out of scope for read-side phase.
- **Export filtered data to CSV** — small follow-up, doesn't need its own phase.
- **Mood × typing-speed correlation chart** — different chart shape (regression scatter); needs its own design pass.
- **Per-cell drilldown screens** — Decision #24 keeps cells at toast-only.
- **Cognitive tests (Go/No-Go, Trailmaking)** — BiAffect ships these as separate active tasks; this project deliberately stays passive-only.
