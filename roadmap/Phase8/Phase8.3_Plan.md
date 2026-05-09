# Phase 8.3 — Mood Distribution over Time (Stacked Bar) on Insights

**Status:** Planned
**Depends on:** Phase 8 (mood entries exist in `ikd.db`), Phase 8.1 polish (current dashboard surface)
**Branch:** `feat/phase8.3-mood-distribution-stacked-bar` (cut from latest `main` after Phase 8.1 was merged)
**Scope (one sentence):** Drop the "Avg Mood" KPI cell and the "Mood over Time" line chart from `DashboardActivity`, and replace them with a per-bucket **stacked-bar chart** showing the percentage breakdown of each Ekman category in each time bucket (day or week, matching the existing range selector). The Mood Distribution panel — counts per category for the whole range — is kept verbatim.

> **Naming note.** `Phase 8.1` is already used in this repo for the post-merge UI polish that shipped on `fix/phase8-ui-polish` (see [`Phase8_Plan.md` §12](Phase8_Plan.md#12-post-merge-ui-polish-phase-81)). `Phase 8.2` is reserved as a buffer slot for any small follow-up that lands before this rework. This new chart-shape change is therefore numbered **Phase 8.3** at the user's request.

---

## 1. Why this change

Phase 8 imposed a 1 (Happiness) → 6 (Anger) ordering on the six Ekman categories so that `AVG(mood_score)` would be mathematically meaningful and could drive both an "Avg Mood" KPI and a "Mood over Time" line chart. After on-device review, that assumption no longer holds:

- A computed average like `🤢 3.2` ("around Disgust, slightly toward Sadness") is technically valid but interpretively vague — it conflates a position on a designer-imposed valence ordering with a real psychological state. The number does not correspond to anything a user can self-report.
- The "Mood over Time" line chart inherits the same problem at every bucket — averaging across categorical labels produces a curve that wiggles between non-states.
- The Mood Distribution panel already conveys the actual signal as **counts per category for the whole range**, but the *time dimension* is lost there.

The right shape for time-distributed categorical data is a **stacked bar chart**: each bar is one time bucket, each segment is one Ekman category, the bar sums to 100%. The time dimension is preserved without re-introducing a meaningless average.

This is also a clean reversal of Decision #14 in [`Phase8_Plan.md` §9](Phase8_Plan.md#9-decisions): we lift the deferred "Stacked-bar-by-day chart" out of [`Phase8_Plan.md` §10 — Explicitly Deferred](Phase8_Plan.md#10-explicitly-deferred-to-later-phases) and ship it now in place of the average-based widgets, instead of waiting for Phase 9.

---

## 2. Branch & Layering Discipline

Read-side only. No keyboard layer, no schema migration, no new dependency (MPAndroidChart has been on the classpath since Phase 3).

### Reopened files

| File | Why reopened | Edit shape |
|---|---|---|
| `activities/DashboardActivity.kt` | Replace `renderMood`'s line-chart + KPI wiring with stacked-bar wiring; keep Distribution renderer | Remove avg-mood KPI assignment; remove line-chart `setData` call; add stacked-bar `setData` call |
| `res/layout/activity_dashboard.xml` | Drop two views, add one new card | Delete `dashboard_kpi_avg_mood_cell` and `dashboard_mood_chart_card`; add `dashboard_mood_stacked_chart_card` containing the new `IkdStackedBarChartView` and a horizontal legend `LinearLayout` |
| `helpers/IkdMoodAggregator.kt` | Add the per-bucket-per-category aggregation path | Add `MoodMixSnapshot` (+ `MoodMixBucket`) data class; add `suspend fun mixSnapshot(range)` running on `Dispatchers.IO`; pure derivation `Companion.buildMixSnapshot(buckets)` for unit testing. Existing `MoodSnapshot` and `snapshot()` stay (Distribution panel still uses them). The companion's old `buildSnapshot` is unchanged; only the `averageScore` field on `MoodSnapshot` becomes unused — leave it on the data class for backwards-compatible test fixtures and delete it in a follow-up if it has truly zero readers after this lands |
| `interfaces/MoodDao.kt` | One additive query | New `@Query` method `getMoodCategoryBuckets(bucketFormat, fromMs, toMs)` returning `List<MoodCategoryBucketRow>`. Existing `getMoodBuckets` and `getMoodDistribution` are untouched |
| `interfaces/MoodCategoryBucketRow.kt` | New POJO | Three columns: `bucket: String`, `score: Int`, `entryCount: Int` |
| `views/IkdStackedBarChartView.kt` | New chart wrapper | Theme-aware MPAndroidChart `BarChart` wrapper, mirrors `IkdLineChartView`'s API: `setData(labels: List<String>, segments: List<MoodSegment>)` |
| `res/values/strings.xml` | New chart strings | Add `dashboard_chart_mood_mix_title`, `dashboard_chart_mood_mix_y_label`, six `mood_legend_*` labels (already-existing English labels in `helpers/MoodEmoji.kt` resources can be reused). Remove `dashboard_chart_mood_title`, `dashboard_chart_mood_y_label`, `dashboard_kpi_avg_mood_label`, `dashboard_avg_mood_value_format` |
| `res/values/colors.xml` and `res/values-night/colors.xml` | Six per-category bar colours | Add `mood_color_happiness` … `mood_color_anger` for both light and dark. Same theme-aware discipline Phase 6 used for status chips — no `Color.parseColor("#…")` literals in Kotlin |

### Still forbidden (everything Phase 8 froze, plus the Phase 8 surface itself outside this dashboard)

| File | Reason |
|---|---|
| `services/SimpleKeyboardIME.kt`, `helpers/LiveCaptureSessionStore.kt`, `helpers/KinematicSensorHelper.kt`, `helpers/IkdRetentionWorker.kt` | Capture path — frozen since Phase 7 closed |
| `databases/IkdDatabase.kt`, `models/MoodEntry.kt`, `models/IkdEvent.kt`, `models/SensorSample.kt`, `models/SessionRecord.kt` | No schema change. `IkdDatabase.version` stays at 2 |
| `helpers/IkdCsvWriter.kt` | CSV format unchanged. The third dual-block `#mood_entries` segment from Phase 8 is the data contract |
| `helpers/IkdMoodBarController.kt`, `helpers/IkdMoodLoader.kt` | Mood capture / per-session loader — Phase 8 surfaces |
| `views/MyKeyboardView.kt`, `res/layout/keyboard_view_keyboard.xml` | Mood bar UI unchanged |
| `activities/EventFeedActivity.kt`, `res/layout/activity_event_feed.xml` | Per-session dashboard unchanged |
| `activities/IkdSettingsActivity.kt`, `res/layout/activity_ikd_settings.xml` | Settings rows unchanged |
| `activities/DiagnosticsActivity.kt`, `res/layout/activity_diagnostics.xml`, `res/menu/menu_diagnostics.xml`, status-chip colour resources | Phase 6 surface — frozen |
| `helpers/IkdAggregator.kt`, `helpers/IkdSessionStatsLoader.kt`, `helpers/IkdSessionChartLoader.kt`, `views/IkdLineChartView.kt` | Earlier-phase read surfaces — frozen at single-series |
| `helpers/Constants.kt`, `helpers/Config.kt` | No new prefs |

### Branch hygiene

- One focused commit per logical change (DAO + POJO; aggregator + tests; new view; activity + layout; string/colour deletions; CLAUDE/roadmap doc).
- After acceptance, leave the branch local for the user to review — **no push, no PR opened by the implementer.**

---

## 3. Aggregation strategy

One additive SQL query, scoped to the selected range:

```sql
SELECT
    strftime(:bucketFormat, timestamp / 1000, 'unixepoch', 'localtime') AS bucket,
    mood_score AS score,
    COUNT(*) AS entryCount
FROM mood_entries
WHERE timestamp BETWEEN :fromMs AND :toMs
GROUP BY bucket, mood_score
ORDER BY bucket ASC, mood_score ASC
```

Worst case: 30 daily buckets × 6 categories = 180 rows for Month range; 14 weekly buckets × 6 = 84 rows for All Time. Same shape as the existing `getMoodBuckets` query — no new performance budget needed.

`IkdMoodAggregator.Companion.buildMixSnapshot(range, rows)` folds the partial DAO output into:

```kotlin
data class MoodMixBucket(
    val label: String,
    /** counts[score] for each 1..6, zero-defaulted */
    val counts: Map<Int, Int>,
    val total: Int,
)

data class MoodMixSnapshot(
    val range: Range,
    val buckets: List<MoodMixBucket>,
    /** total entries across all buckets — same number `MoodSnapshot.total` produces */
    val total: Int,
)
```

`DashboardActivity.onResume()` already runs both `IkdAggregator.snapshot(range)` and `IkdMoodAggregator.snapshot(range)` on a single `Dispatchers.IO` hop. Phase 8.2 adds a third call (`IkdMoodAggregator.mixSnapshot(range)`) on the same hop. Wall-clock duration logged via `Log.d("IkdMoodAggregator", "mixSnapshot(${range.name}) took ${ms}ms")` in debug builds, mirroring the existing `snapshot` log.

---

## 4. Stacked Bar Chart UI Spec

- **Component:** new `views/IkdStackedBarChartView.kt` wraps MPAndroidChart `BarChart`. API mirrors `IkdLineChartView`: `fun setData(labels: List<String>, segments: List<MoodSegment>)`. `MoodSegment` carries the score id, English label, colour resource, and the per-bar values list.
- **Mode:** stacked (`BarDataSet.stackLabels = …`, `BarData(stackedDataSet)`).
- **Y axis:** percentage 0–100. Right axis disabled. Description disabled. MPAndroidChart's built-in legend disabled — we draw our own legend below for emoji + label + colour-swatch composition.
- **X axis:** same bucket labels as the IKD charts (uses the existing `formatBucketLabel(...)` helper in `DashboardActivity`). Rotated -45° on dense ranges (default MPAndroidChart behaviour).
- **Bar colour scheme** (display order, top-to-bottom of the stack):

  | Score | Emoji | Label | Light bar colour | Dark bar colour |
  |---|---|---|---|---|
  | 1 | 😊 | Happiness | green | desaturated green |
  | 2 | 😲 | Surprise | teal | desaturated teal |
  | 3 | 🤢 | Disgust | olive / yellow-green | desaturated olive |
  | 4 | 😢 | Sadness | blue | desaturated blue |
  | 5 | 😨 | Fear | purple | desaturated purple |
  | 6 | 😠 | Anger | red | desaturated red |

  Concrete hex values are picked during implementation against Fossify's neutral-grey light/dark themes; tokens live in `res/values/colors.xml` and `res/values-night/colors.xml`. Hardcoded literals in Kotlin are forbidden.

- **Empty buckets** (zero entries) render as zero-height bars — empty space on the X axis. The bar is **not** hidden; keeping the X axis aligned with adjacent IKD charts is more important than tightening empty buckets (Decision #4 below).
- **Legend (below chart):** horizontal `LinearLayout` (or `FlexboxLayout` if it wraps cleanly) of six `TextView`s, each rendering `<small colour swatch> emoji label`. The colour swatch is a 12 dp × 12 dp `View` tinted with the same `mood_color_*` resource. Wraps on small screens.
- **Theme integration:** `applyCardThemeColors()` (introduced in Phase 8.1) gains a third call on the new card so it tints alongside the existing Distribution and IKD chart cards. The chart itself reads `getProperBackgroundColor()` and `getProperTextColor()` for axis labels, mirroring `IkdLineChartView`.

---

## 5. DashboardActivity rendering changes

Net edits in `DashboardActivity.kt`:

- `loadDashboard()` adds `val moodMix = moodAgg.mixSnapshot(range)` to the same `withContext(Dispatchers.IO)` block; `render(...)` gains a `moodMix: IkdMoodAggregator.MoodMixSnapshot` parameter.
- `renderMood(...)` is renamed `renderMoodSection(snap, moodSnap, moodMix)`. It:
  - **Drops** the `dashboardKpiAvgMoodCell` text + visibility code.
  - **Drops** the `dashboardMoodChartCard` + `dashboardChartMood.setData(...)` block.
  - **Adds** `bindStackedChart(moodMix)` which iterates `moodMix.buckets`, builds six lists of `Float` percentages (`count * 100f / total` per bucket), and calls `dashboardMoodStackedChart.setData(labels, segments)`.
  - **Keeps** the Distribution panel call `renderMoodDistribution(moodSnap)` verbatim — counts-per-category for the whole range remain useful.
- All three new/kept views (stacked chart card + Distribution panel) gated on `moodSnap.total > 0`. `moodSnap.total == 0` ⟺ `moodMix.total == 0`, so a single check covers both. The KPI strip falls back to four cells unconditionally — `dashboard_kpi_avg_mood_cell` is gone from the layout.

---

## 6. Acceptance Criteria

- [ ] Build green: `./gradlew assembleCoreDebug` succeeds.
- [ ] No new `lint` or `detekt` warnings.
- [ ] `Avg Mood` KPI cell, `Mood over Time` chart card, and their string + theme resources are removed from `activity_dashboard.xml`, `strings.xml`, and `DashboardActivity.kt`.
- [ ] KPI strip falls back to four cells when at least one mood entry exists in the range; previously the strip grew to five cells.
- [ ] New "Mood Mix over Time" stacked bar chart appears below the existing IKD charts when ≥ 1 mood entry exists in the selected range; otherwise the card is `View.GONE`.
- [ ] Each bar's segments sum to 100% (bucket has entries) or the bar has zero height (bucket has no entries).
- [ ] Mood Distribution panel beneath the stacked chart is preserved verbatim and still shows counts per category for the whole range.
- [ ] Stacked-bar X axis labels are identical to IKD charts' X labels for the same range (same `formatBucketLabel` output, same bucket alignment).
- [ ] Switching range (Week → Month → All Time) re-aggregates and re-renders the stacked chart in a single `Dispatchers.IO` hop.
- [ ] `Log.d("IkdMoodAggregator", "mixSnapshot(${range.name}) took ${ms}ms")` is emitted on every `mixSnapshot` call when `BuildConfig.DEBUG`.
- [ ] New JVM unit tests in `app/src/test/.../IkdMoodAggregatorMixTest.kt` cover `Companion.buildMixSnapshot`: empty input → empty buckets list, single-bucket → 100% one category, mixed bucket with multiple categories → percentage sums to 100, out-of-range scores ignored, total tally matches the sum of all bucket counts.
- [ ] No file in Section 2's "Still forbidden" list is touched (verifiable via `git diff main..feat/phase8.3-mood-distribution-stacked-bar --name-only`).
- [ ] `roadmap/FeatureRoadmap.md` Phase 8.3 row is added.
- [ ] `CLAUDE.md` gains a one-paragraph Phase 8.3 note pointing at this file.

---

## 7. Decisions

| # | Topic | Decision |
|---|---|---|
| 1 | Why drop avg-mood + line chart | Averaging an ordinal valence over six Ekman categories produces numbers that don't correspond to any user-reportable state. The line chart is the same problem at every bucket. Reverses Phase 8 Decision #14 — we now believe the line chart was the wrong rendering for categorical data, even with an ordinal valence id. |
| 2 | Bar units (count vs. percentage) | **Percentage.** Lets users compare bucket *shape* across buckets of unequal total size. Absolute counts hide low-volume buckets entirely on a 100%-axis chart. |
| 3 | Bucket alignment with IKD charts | **Same `Range.bucketFormat`.** Lets users correlate "I typed faster on May 3" with "the May 3 bar is mostly Happiness." Same X axis is half the value of the chart. |
| 4 | Empty buckets | **Render as zero-height bars.** Hiding empty buckets would compress the X axis and lose alignment with IKD charts (Decision #3). |
| 5 | Charting library | **MPAndroidChart `BarChart` in stacked mode.** Already on classpath since Phase 3 (added via JitPack in Phase 2's `settings.gradle.kts` edit). New wrapper view (`IkdStackedBarChartView`) mirrors the Phase 3 `IkdLineChartView` discipline. |
| 6 | Colour mapping | **Six tokens in `colors.xml` + `values-night/colors.xml`.** Same theme-aware discipline Phase 6 used for status chips. Hardcoded `Color.parseColor("#…")` literals in Kotlin are forbidden (Phase 6 lesson). |
| 7 | Legend | **Custom legend below the chart.** MPAndroidChart's built-in legend doesn't support emoji + colour-swatch composition cleanly. A simple horizontal `LinearLayout` of six `TextView`s gives full control. |
| 8 | Distribution panel | **Kept verbatim.** Counts-per-category for the whole range is still useful — and complementary to the time-distributed stacked chart. |
| 9 | New schema or column | **None.** All data already in `mood_entries`. New DAO query is additive. |
| 10 | DB migration | **None.** `IkdDatabase.version` stays at 2. |
| 11 | Aggregator surface shape | **New `mixSnapshot(range)` alongside existing `snapshot(range)`.** The activity composes both on the same `Dispatchers.IO` hop. The existing `MoodSnapshot.averageScore` field is left on the data class for backwards-compatible test fixtures and removed in a follow-up if it has zero readers after this lands. |
| 12 | Phase numbering | **Phase 8.3** at the user's request. `Phase 8.1` already names the post-merge UI polish (see this file's header note); `8.2` is reserved as a buffer slot for any small follow-up that lands before this rework. |

---

## 8. Files to create / modify (summary)

**New:**
- `app/src/main/kotlin/.../views/IkdStackedBarChartView.kt`
- `app/src/main/kotlin/.../interfaces/MoodCategoryBucketRow.kt`
- `app/src/test/kotlin/.../IkdMoodAggregatorMixTest.kt`
- `roadmap/Phase8/Phase8.3_Plan.md` (this file)

**Modified:**
- `app/src/main/kotlin/.../activities/DashboardActivity.kt`
- `app/src/main/kotlin/.../helpers/IkdMoodAggregator.kt`
- `app/src/main/kotlin/.../interfaces/MoodDao.kt`
- `app/src/main/res/layout/activity_dashboard.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values-night/colors.xml`
- `roadmap/FeatureRoadmap.md`
- `CLAUDE.md`

**Deleted (string + view IDs):**
- `dashboard_kpi_avg_mood_cell` (layout)
- `dashboard_mood_chart_card` + `dashboard_chart_mood` (layout)
- `dashboard_kpi_avg_mood_label`, `dashboard_avg_mood_value_format`, `dashboard_chart_mood_title`, `dashboard_chart_mood_y_label` (strings)
