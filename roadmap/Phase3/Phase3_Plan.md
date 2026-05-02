# Phase 3 — Insights Dashboard (Minimal, Performance-First)

**Status:** Not started
**Depends on:** Phase 2 (`ikd.db` populated)
**Branch:** `feat/phase3-dashboard` — all work lands on this branch and is merged to `main` only when sub-phase 3.4 (Polish) passes acceptance.
**Scope (one sentence):** A read-only dashboard activity that shows daily/weekly/monthly trends from `ikd.db` using a small number of simple charts, with all aggregation done server-side in SQL, and **zero edits to the keyboard layer**.

This plan deliberately replaces the broader earlier draft. The user direction is: **stupid simple, feasible, performance-first**. Whatever isn't required for "see your typing trends over a week / month" is deferred to Phase 4.

---

## Table of Contents

1. [Concept & Scope](#1-concept--scope)
2. [Branch & Layering Discipline](#2-branch--layering-discipline)
3. [Metrics (4, not 12)](#3-metrics-4-not-12)
4. [Aggregation Strategy: SQL `GROUP BY`, Never In-Memory Folding](#4-aggregation-strategy-sql-group-by-never-in-memory-folding)
5. [Time Ranges (3, not 5)](#5-time-ranges-3-not-5)
6. [Charting Library](#6-charting-library)
7. [Sub-Phases (4, not 8)](#7-sub-phases-4-not-8)
   - [3.1 Aggregator + Read-Only DAO Additions](#31-aggregator--read-only-dao-additions)
   - [3.2 Dashboard Activity Shell + Navigation](#32-dashboard-activity-shell--navigation)
   - [3.3 Three Trend Charts](#33-three-trend-charts)
   - [3.4 Polish: Empty State, Refresh, Performance Validation](#34-polish-empty-state-refresh-performance-validation)
8. [Files to Create / Modify (vs. forbidden)](#8-files-to-create--modify-vs-forbidden)
9. [Acceptance Criteria](#9-acceptance-criteria)
10. [Decisions](#10-decisions)
11. [Explicitly Deferred to Phase 4](#11-explicitly-deferred-to-phase-4)

---

## 1. Concept & Scope

Phase 2 stores every keyboard session in `ikd.db`. Phase 3 turns those rows into one screen — `DashboardActivity` — that answers: *"How is my typing trending this week / this month / overall?"*

The dashboard is a **pure read-side feature**. It does not change capture. It does not change the keyboard. It cannot, by construction, slow down typing, because it doesn't run while the keyboard is open.

### In scope (the entire feature)

- One new activity: `DashboardActivity`
- Range selector with **3 options**: **Week**, **Month**, **All Time**
- One **headline KPI strip** at the top showing 4 numbers (sessions, total typing time, average WPM, error rate)
- **Three line charts** stacked below: Speed (WPM per day/week), Average IKD (per day/week), Error rate (per day/week)
- Aggregation done in **SQL `GROUP BY`** queries that return small result sets (≤ ~52 rows worst case)
- Two navigation entry points: from `MainActivity` (a button) and from `IkdSettingsActivity` (a row)
- Empty state when there is no data

### Out of scope (deferred to Phase 4 — see Section 11)

Heatmaps, circadian charts, drill-down cards, bottom sheets, mood/energy overlay, schema migration to v2, aggregate CSV export, rolling baselines, comparison strings, IkdDataChangedBus invalidation, summary tables, anomaly detection, daily digest notifications, accessibility pass beyond defaults.

---

## 2. Branch & Layering Discipline

Phase 3 must be **independent of the keyboard layer**. The implementation lives entirely in the *companion app* layer and the *data* layer. The capture pipeline is finished and frozen for the duration of this phase.

### Forbidden edits (do not modify in this phase)

| File | Reason |
|---|---|
| `services/SimpleKeyboardIME.kt` | Capture path — frozen |
| `views/MyKeyboardView.kt` | Keyboard UI — frozen |
| `helpers/LiveCaptureSessionStore.kt` | Capture buffer — frozen |
| `helpers/KinematicSensorHelper.kt` | Capture path — frozen |
| `helpers/IkdRetentionWorker.kt` | Background worker — frozen |
| `databases/IkdDatabase.kt` | No schema migration this phase (no version bump) |
| Any Room `@Entity` (`IkdEvent`, `SensorSample`, `SessionRecord`) | No schema change |
| `databases/ClipsDatabase.kt`, `interfaces/ClipsDao.kt` | Out of scope, as in Phase 2 |

### Allowed edits

| File | Allowed change |
|---|---|
| `interfaces/IkdEventDao.kt` | **Add** new `@Query` methods only — no edits to existing methods |
| `interfaces/SessionDao.kt` | Same — additive `@Query` methods only |
| `activities/MainActivity.kt` | Add a single button that launches `DashboardActivity` |
| `activities/IkdSettingsActivity.kt` | Add a single row that launches `DashboardActivity` |
| `app/build.gradle.kts` | Add MPAndroidChart dependency |
| `settings.gradle.kts` (root) | Ensure JitPack repo (verify first) |
| `res/values/strings.xml` | Add dashboard strings under `dashboard_*` namespace |

### Branch hygiene

- Branch name: `feat/phase3-dashboard`
- Cut from latest `main` after Phase 2 has been merged
- Each sub-phase (3.1 → 3.4) lands as one focused commit using the project's `feat:` / `chore:` convention
- After 3.4 passes acceptance, open a single PR back to `main`

---

## 3. Metrics (4, not 12)

Only four metrics ship. Anything else can be added later without touching the dashboard skeleton.

| Metric | Where | Definition |
|---|---|---|
| **Sessions** | KPI strip + chart implicitly via per-bucket counts | `count(distinct session_id)` in range |
| **Total typing time** | KPI strip | `sum(ended_at - started_at)` for sessions in range, where `ended_at IS NOT NULL` |
| **WPM (avg)** | KPI strip + Speed chart | `count(events) / 5 * 60_000 / sum(session_duration_ms)` per bucket |
| **Average IKD (ms)** | IKD chart | `avg(ikd_ms WHERE ikd_ms >= 0)` per bucket |
| **Error rate (%)** | KPI strip + Error chart | `100.0 * sum(is_correction) / count(*)` per bucket |

There is no median IKD, no rolling baseline, no flight time chart, no hold time chart, no sensor-derived metric. They can be added in Phase 4 by adding chart instances to the existing layout.

### Privacy invariants (unchanged from Phase 2)

- Dashboard reads only aggregate counts and `eventCategory`-derived data. Raw text is never reconstructable.
- No app/package context is read.

---

## 4. Aggregation Strategy: SQL `GROUP BY`, Never In-Memory Folding

This is the core performance commitment. **Aggregation queries return at most ~52 rows** (one per week, for "All Time" capped at retention) — never raw events. Forbidden patterns: fetching `getEventsForSession()` and folding in Kotlin. The previous draft did this; it scales poorly and we are not doing it.

### How the bucketing works in SQL

`SQLite` exposes `strftime` and integer arithmetic on epoch milliseconds. Bucket key strategies:

```sql
-- Daily bucket (local time): yyyy-MM-dd
strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime')

-- Weekly bucket (ISO-ish): yyyy-WW
strftime('%Y-%W', timestamp / 1000, 'unixepoch', 'localtime')

-- Monthly bucket: yyyy-MM
strftime('%Y-%m', timestamp / 1000, 'unixepoch', 'localtime')
```

Note: SQLite's `%W` is "Sunday-first" weeks. That's fine for personal-use trend charts — the boundary is consistent within the user's own data. We document this and move on. (ISO weeks are a Phase 4 nicety.)

### Single query per chart per range

Every trend chart fires **one** query that returns a `List<DailyMetricRow>` (or weekly / monthly equivalent). No N+1, no per-session loop on the main thread, no per-session loop on the IO thread either.

Example query (used by all three trend charts via projection):

```kotlin
@Query("""
    SELECT
        strftime(:bucketFormat, timestamp / 1000, 'unixepoch', 'localtime') AS bucket,
        AVG(CASE WHEN ikd_ms >= 0 THEN ikd_ms END) AS avgIkdMs,
        100.0 * SUM(CASE WHEN is_correction THEN 1 ELSE 0 END) / COUNT(*) AS errorRatePct,
        COUNT(*) AS eventCount,
        COUNT(DISTINCT session_id) AS sessionCount
    FROM ikd_events
    WHERE timestamp >= :fromMs AND timestamp < :toMs
    GROUP BY bucket
    ORDER BY bucket
""")
fun getEventBuckets(bucketFormat: String, fromMs: Long, toMs: Long): List<EventBucketRow>
```

`EventBucketRow` is a Room POJO under `interfaces/`:

```kotlin
data class EventBucketRow(
    val bucket: String,
    val avgIkdMs: Double?,        // nullable when no non-sentinel rows in bucket
    val errorRatePct: Double,
    val eventCount: Int,
    val sessionCount: Int
)
```

### Sessions query (for KPI strip + WPM denominator)

```kotlin
@Query("""
    SELECT
        strftime(:bucketFormat, started_at / 1000, 'unixepoch', 'localtime') AS bucket,
        SUM(CASE WHEN ended_at IS NOT NULL THEN ended_at - started_at ELSE 0 END) AS totalDurationMs,
        COUNT(*) AS sessionCount
    FROM sessions
    WHERE started_at >= :fromMs AND started_at < :toMs
    GROUP BY bucket
    ORDER BY bucket
""")
fun getSessionBuckets(bucketFormat: String, fromMs: Long, toMs: Long): List<SessionBucketRow>
```

The aggregator zips `EventBucketRow` and `SessionBucketRow` by `bucket` to compute WPM = `(eventCount / 5) * 60_000 / totalDurationMs`.

### Threading

- Every aggregator call runs on `Dispatchers.IO` via `lifecycleScope.launch(Dispatchers.IO)` from the activity, posting the result back with `withContext(Dispatchers.Main)`.
- Debug builds enable `StrictMode.ThreadPolicy.detectDiskReads()` to fail-fast if any path slips back onto the main thread.

### Cache (deliberately small)

A single `var lastResult: DashboardSnapshot? = null` field on the activity. Refreshed on `onResume` and on Refresh menu tap. No singleton bus, no SharedFlow, no observers across screens. If the user deletes data in another screen and returns, `onResume` re-aggregates. Simple.

### Performance budget (hard constraint)

| Range | Bucket | Worst-case rows returned | Target wall-clock for full dashboard load |
|---|---|---|---|
| Week (7 days) | daily | 7 | < 100 ms |
| Month (~30 days) | daily | ~30 | < 150 ms |
| All Time (≤ 90 days at default retention) | weekly | ≤ 14 | < 200 ms |

Measurements taken on a Pixel 4–class device with ~50 sessions/day populated. Validated under sub-phase 3.4.

---

## 5. Time Ranges (3, not 5)

Three options on the segmented control: **Week** · **Month** · **All Time**.

| Range | Window | Bucket size | Bucket count |
|---|---|---|---|
| **Week** | last 7 local days, ending today | daily | 7 |
| **Month** | last 30 local days, ending today | daily | 30 |
| **All Time** | from earliest session to now (capped by retention) | weekly | ≤ 14 |

`Day` and `3 Months` from the earlier draft are gone — the first is too narrow to call a "trend", the second adds layout complexity without new insight at this stage.

Default selection on first open: **Week**.

---

## 6. Charting Library

**MPAndroidChart 3.1.0** — same recommendation as the earlier draft, same reasoning (no Compose in this codebase). All three charts use `LineChart`. No heatmap, no custom views.

### Dependency additions

- `app/build.gradle.kts`: `implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")`
- Verify `settings.gradle.kts` already lists JitPack; if not, add `maven { url = uri("https://jitpack.io") }` to `dependencyResolutionManagement.repositories`
- If JitPack is not acceptable, fall back to `app/libs/MPAndroidChart-v3.1.0.aar` and `implementation(files("libs/MPAndroidChart-v3.1.0.aar"))`

The chart is wrapped in one tiny helper, `views/IkdLineChartView.kt`, that:
- Applies the app theme (primary color via `getProperPrimaryColor()`, `updateTextColors`)
- Disables right Y axis, description label, and the legend on small screens
- Accepts `(labels: List<String>, values: List<Float?>, yAxisLabel: String)` — nothing else

That's the entire charting surface area.

---

## 7. Sub-Phases (4, not 8)

Four sub-phases. Each one compiles and ships. Sub-phases 3.1–3.3 land as commits; 3.4 closes the branch.

---

### 3.1 Aggregator + Read-Only DAO Additions

**Goal:** All data-layer plumbing, no UI. After this commit, `IkdAggregator` is fully unit-testable in isolation.

#### Deliverables

- `interfaces/IkdEventDao.kt` (modified) — add `getEventBuckets(bucketFormat, fromMs, toMs)` only. No edits to existing methods.
- `interfaces/SessionDao.kt` (modified) — add `getSessionBuckets(bucketFormat, fromMs, toMs)` and `getEarliestSessionStart()`. No edits to existing methods.
- `interfaces/EventBucketRow.kt` — Room POJO
- `interfaces/SessionBucketRow.kt` — Room POJO
- `helpers/IkdAggregator.kt` — single class:

```kotlin
class IkdAggregator(private val db: IkdDatabase) {

    enum class Range(val days: Int?, val bucketFormat: String) {
        WEEK(7, "%Y-%m-%d"),
        MONTH(30, "%Y-%m-%d"),
        ALL_TIME(null, "%Y-%W")
    }

    data class Snapshot(
        val range: Range,
        val totalSessions: Int,
        val totalTypingTimeMs: Long,
        val avgWpm: Double?,
        val avgErrorRatePct: Double?,
        val buckets: List<Bucket>
    )

    data class Bucket(
        val label: String,         // already-formatted X-axis label
        val wpm: Double?,
        val avgIkdMs: Double?,
        val errorRatePct: Double?
    )

    suspend fun snapshot(range: Range): Snapshot     // runs on Dispatchers.IO inside the function
}
```

- `extensions/ContextExt.kt` (modified) — add `val Context.ikdAggregator: IkdAggregator` (lazy singleton wrapping the DB)
- `app/src/test/.../IkdAggregatorTest.kt` — unit tests against an in-memory Room DB:
  - Empty DB → `Snapshot.totalSessions == 0`, `buckets.isEmpty()`
  - Seeded 7-day dataset → bucket count = 7, KPI math agrees with hand-computed values
  - Seeded with `ikd_ms = -1` only → `avgIkdMs == null` (sentinel handling)
  - Seeded across local-midnight boundary → events land in the correct bucket
  - 90-day × 50-sessions/day fake dataset → `snapshot(ALL_TIME)` returns in < 200 ms (logged duration; not asserted hard, but warns)

#### Acceptance

- `./gradlew test` passes the new `IkdAggregatorTest`
- `./gradlew detekt` and `./gradlew lint` produce no new warnings beyond existing baselines
- `./gradlew assembleCoreDebug` succeeds
- `IkdDatabase.version` is **still 1** (no migration this phase)

---

### 3.2 Dashboard Activity Shell + Navigation

**Goal:** A working activity with the range selector, KPI strip, and three empty chart placeholders. No real charts yet — they land in 3.3.

#### Deliverables

- `activities/DashboardActivity.kt` extending `SimpleActivity`, view binding (`ActivityDashboardBinding`):
  - Top: `MaterialButtonToggleGroup` with three buttons (Week / Month / All Time), default `Week`, selection persisted in `onSaveInstanceState`
  - Below: KPI card with 4 `MyTextView`s — sessions, total typing time, avg WPM, error rate
  - Below: 3 `FrameLayout` placeholders (filled in 3.3)
  - `onResume` triggers a coroutine: `lifecycleScope.launch(Dispatchers.IO) { val snap = ikdAggregator.snapshot(currentRange); withContext(Main) { render(snap) } }`
  - `render` populates the KPI card and (in 3.2) leaves placeholders as "—"
  - Refresh menu item: re-runs `snapshot(currentRange)`
- `res/layout/activity_dashboard.xml`
- `res/menu/menu_dashboard.xml` — only Refresh
- **Empty state**: when `snap.totalSessions == 0`, hide the chart placeholders + KPI card, show a single full-bleed `MyTextView` with copy *"Type with privacy mode off and your trends will appear here. Open Data Collection & Privacy → toggle Privacy mode."* Tap → opens `IkdSettingsActivity`.
- `activities/MainActivity.kt` (modified) — add an "Insights" button that launches `DashboardActivity`. Single addition; do not refactor existing layout.
- `activities/IkdSettingsActivity.kt` (modified) — add a "View Dashboard" row above "View Sessions History".
- `res/values/strings.xml` — `dashboard_title`, `dashboard_range_week/month/all`, `dashboard_kpi_sessions`, `dashboard_kpi_typing_time`, `dashboard_kpi_wpm`, `dashboard_kpi_error_rate`, `dashboard_empty_message`, `dashboard_open_settings`.

#### Acceptance

- Activity opens from both entry points
- Range selector preserves selection across rotation
- KPI strip displays correct values from a seeded test DB
- Empty state appears on a fresh install with no sessions
- No edits to any "Forbidden" file from Section 2

---

### 3.3 Three Trend Charts

**Goal:** Wire the three line charts into the placeholders from 3.2.

#### Deliverables

- `views/IkdLineChartView.kt` — small wrapper around MPAndroidChart `LineChart` (described in Section 6)
- `app/build.gradle.kts` (modified) — add MPAndroidChart dependency
- `settings.gradle.kts` (modified, if needed) — JitPack repo
- 3 `<view class="...IkdLineChartView" />` instances inflated in `activity_dashboard.xml` placeholder slots:
  1. **Speed** — Y = WPM per bucket; null buckets rendered as missing points (line breaks, no fake zeros)
  2. **Average IKD** — Y = ms per bucket
  3. **Error rate** — Y = % per bucket
- `DashboardActivity.render()` extended to populate each chart from `snapshot.buckets`

#### Performance guardrails

- Charts receive at most ~52 floats each (capped by All-Time bucket count). MPAndroidChart handles thousands; we are nowhere near that.
- No animation on first render (animations on every range switch get expensive on lower-end devices). MPAndroidChart's `chart.animateX(0)` essentially disables it.
- Chart `setHardwareAccelerationEnabled(true)` (the MPAndroidChart default; verify per chart instance).

#### Acceptance

- All three charts render with seeded data; values match the unit-tested aggregator output
- Switching range re-aggregates and re-renders within the Section 4 budget
- Light/dark theme honored

---

### 3.4 Polish: Empty State, Refresh, Performance Validation

**Goal:** Close the loop. Validate the performance budget on a real device. No new features.

#### Deliverables

- StrictMode debug check verifying no main-thread DB reads during dashboard use
- A throwaway debug-only menu item (gated behind `BuildConfig.DEBUG`) that seeds the DB with N sessions for performance smoke-testing — removed from the activity at the end of the sub-phase, kept only as a `@VisibleForTesting` helper in `IkdAggregatorTest`
- Manual perf log: open Logcat, exercise Week / Month / All Time, confirm each `snapshot()` call logs a wall-clock duration under the Section 4 budget. Document the measurements in the PR description.
- README/CLAUDE.md update noting the dashboard exists and how to reach it
- Detekt baseline + Lint baseline updated only if needed; never with `-PdisableLint`

#### Acceptance

- All Sub-Phase 3.1–3.3 acceptance items still pass
- Section 4 performance budget met on a representative device
- StrictMode does not flag any disk reads on the main thread for the dashboard's golden path
- Phase 3 PR description lists the three perf measurements and the device used

---

## 8. Files to Create / Modify (vs. forbidden)

### Create (8 files)

| File | Sub-phase |
|---|---|
| `helpers/IkdAggregator.kt` | 3.1 |
| `interfaces/EventBucketRow.kt` | 3.1 |
| `interfaces/SessionBucketRow.kt` | 3.1 |
| `activities/DashboardActivity.kt` | 3.2 |
| `views/IkdLineChartView.kt` | 3.3 |
| `res/layout/activity_dashboard.xml` | 3.2 |
| `res/menu/menu_dashboard.xml` | 3.2 |
| `app/src/test/.../IkdAggregatorTest.kt` | 3.1 |

### Modify (additive only — no existing logic touched)

| File | Sub-phase | Change |
|---|---|---|
| `interfaces/IkdEventDao.kt` | 3.1 | **Add** `getEventBuckets(...)` only |
| `interfaces/SessionDao.kt` | 3.1 | **Add** `getSessionBuckets(...)`, `getEarliestSessionStart()` only |
| `extensions/ContextExt.kt` | 3.1 | **Add** `val Context.ikdAggregator` only |
| `activities/MainActivity.kt` | 3.2 | Add a single "Insights" launch button |
| `activities/IkdSettingsActivity.kt` | 3.2 | Add a single "View Dashboard" row |
| `app/build.gradle.kts` | 3.3 | Add MPAndroidChart dependency |
| `settings.gradle.kts` (root) | 3.3 | Ensure JitPack repo (verify first) |
| `res/values/strings.xml` | 3.2 | Add `dashboard_*` strings |

### Forbidden (do not touch — see Section 2 for full list)

`SimpleKeyboardIME.kt`, `MyKeyboardView.kt`, `LiveCaptureSessionStore.kt`, `KinematicSensorHelper.kt`, `IkdRetentionWorker.kt`, `IkdDatabase.kt`, all `@Entity` data classes, `ClipsDatabase.kt`, `ClipsDao.kt`.

---

## 9. Acceptance Criteria

The whole phase is done when **all** of these are green on `feat/phase3-dashboard`:

- [ ] `./gradlew assembleCoreDebug` succeeds
- [ ] `./gradlew test` passes (including the new `IkdAggregatorTest`)
- [ ] `./gradlew detekt` and `./gradlew lint` produce no regressions vs. existing baselines
- [ ] `IkdDatabase.version` is unchanged (still 1)
- [ ] No file in Section 2's "Forbidden edits" list is modified (verifiable via `git diff main..feat/phase3-dashboard --name-only`)
- [ ] `DashboardActivity` opens from both `MainActivity` and `IkdSettingsActivity`
- [ ] Range selector shows Week / Month / All Time, defaults to Week, persists across rotation
- [ ] KPI strip shows correct values matching the unit-tested aggregator output
- [ ] All three line charts render with seeded data and respect the app theme
- [ ] Empty state renders on a fresh install with no sessions and links to `IkdSettingsActivity`
- [ ] Performance budget from Section 4 met on a Pixel 4–class device — measurements posted in PR description
- [ ] StrictMode in debug build does not flag main-thread DB reads on the dashboard's golden path

---

## 10. Decisions

| # | Topic | Decision |
|---|---|---|
| 1 | Branch strategy | All work on `feat/phase3-dashboard`; merged to `main` only after 3.4 |
| 2 | Independence from keyboard layer | Section 2's "Forbidden edits" list is hard. Any required keyboard-side change blocks the PR and reopens scope. |
| 3 | Aggregation strategy | SQL `GROUP BY` returning small result sets (≤ ~52 rows). No in-memory event folding. |
| 4 | Charting library | MPAndroidChart 3.1.0 — three `LineChart`s, no heatmap, no custom canvas |
| 5 | Time-range options | Week / Month / All Time. Day and 3 Months removed. |
| 6 | Default range | Week |
| 7 | Bucket sizes | Week & Month → daily; All Time → weekly (`%Y-%W`, SQLite default Sunday-first; ISO weeks deferred) |
| 8 | Metrics | Sessions, total typing time, avg WPM, avg IKD, error rate. Median, hold, flight, sensor, baseline, mood — all deferred. |
| 9 | Schema migration | None this phase. `IkdDatabase.version` stays at 1. |
| 10 | Cache invalidation | Re-aggregate on `onResume` and Refresh menu tap. No `IkdDataChangedBus`, no observers. |
| 11 | Drill-down | None. Use the existing `SessionsListActivity` from Phase 2 to inspect individual sessions. |
| 12 | Mood/energy overlay | Out of scope this phase (was sub-phase 3.7 in the earlier draft) |
| 13 | Aggregate CSV export | Out of scope this phase. The existing Phase 2 mega-CSV is sufficient. |

---

## 11. Explicitly Deferred to Phase 4

These were in the earlier Phase 3 draft and are now Phase 4 candidates. Listing them here so we don't relitigate scope mid-phase.

- Cognitive fatigue heatmap (24 × 7 hour-by-day error rate grid)
- Circadian usage pattern chart with late-night highlighting
- Per-session insight cards / drill-down bottom sheets
- 14-day rolling baseline reference lines on charts
- Comparison strings ("▲ 18 % faster than baseline")
- Sensor-magnitude proxy ("mostly stationary / in motion")
- Mood/energy overlay + `mood_entries` table + DB schema migration
- Aggregate CSV export
- `IkdDataChangedBus` cross-screen invalidation
- Materialized `daily_metrics` summary table (consider only if profiling on real devices ever shows we exceed Section 4's budget)
- Median IKD, hold-time chart, flight-time chart
- ISO-8601 week boundaries (vs. SQLite's `%W` Sunday-first)
- Day / 3-Months range options
- Accessibility pass beyond the project's defaults
- Battery-trend metric (depends on the Phase 2 follow-up that adds a battery field to `SessionRecord`)
- Daily digest notifications, anomaly detection, multi-device sync

Anything from this list earns its own focused mini-plan in `roadmap/Phase4/` if and when the user wants it.
