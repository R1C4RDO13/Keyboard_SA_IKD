# Phase 5 — Session Dashboard (Charts Replace Raw Lists)

**Status:** Planned
**Depends on:** Phase 2 (`ikd.db` populated), Phase 3 (`IkdLineChartView` + MPAndroidChart already wired in), Phase 4 (`IkdSessionStatsLoader` powering the KPI strip)
**Branch:** `feat/phase5-session-dashboard` — cut from `main` after Phase 4 is merged.
**Scope (one sentence):** Replace the wall of raw timing rows + raw sensor sample rows on the session detail screen with a chart-first per-session dashboard — compact KPI strip, metadata chip, and three line charts (IKD over time, gyro magnitude over time, accelerometer magnitude over time) — built on the same Phase 3 `IkdLineChartView` wrapper used by the aggregate dashboard.

This phase is the corrective scope-up that Phase 4 deliberately did not include: Phase 4 added a label/value metadata strip *above* the unchanged raw lists; the raw rows still dominated the screen and the strip didn't read like a "dashboard." Phase 5 makes the session detail an actual dashboard.

---

## Table of Contents

1. [Concept & Scope](#1-concept--scope)
2. [Branch & Layering Discipline](#2-branch--layering-discipline)
3. [What's Shown (and Where)](#3-whats-shown-and-where)
4. [Aggregation Strategy: SQL-Side Downsampling, ≤ 200 Points per Chart](#4-aggregation-strategy-sql-side-downsampling--200-points-per-chart)
5. [Charting Library: Reuse Phase 3](#5-charting-library-reuse-phase-3)
6. [Sub-Phases (4)](#6-sub-phases-4)
   - [5.1 Per-Session Chart Loader + DAO Additions](#51-per-session-chart-loader--dao-additions)
   - [5.2 Session Dashboard Layout + KPI / Metadata Strip](#52-session-dashboard-layout--kpi--metadata-strip)
   - [5.3 Three Per-Session Line Charts](#53-three-per-session-line-charts)
   - [5.4 Polish: Empty States, Style Pass, Performance Validation](#54-polish-empty-states-style-pass-performance-validation)
7. [Files to Create / Modify (vs. forbidden)](#7-files-to-create--modify-vs-forbidden)
8. [Acceptance Criteria](#8-acceptance-criteria)
9. [Decisions](#9-decisions)
10. [Explicitly Deferred to Phase 6](#10-explicitly-deferred-to-phase-6)

---

## 1. Concept & Scope

Today, opening a session from the Sessions list shows: a 12-row label/value metadata strip (Phase 4) followed by *every* timing event (one row each, often hundreds) followed by *every* sensor sample (one row each at ~50 Hz, often thousands). The lists are useful for raw debugging but they overshadow the metadata and there is no visual summary. Tapping a 60-second session can scroll for thousands of pixels of decimal numbers.

Phase 5 turns the saved-session detail screen into a **dashboard**, parallel to the aggregate `DashboardActivity` from Phase 3 but scoped to one session:

1. A compact KPI strip at the top (4 numbers, same as the Phase 3 dashboard's strip but per-session: WPM, error rate, average IKD, total events).
2. A single-line metadata chip row (started at · duration · orientation · locale).
3. Three `IkdLineChartView` instances stacked below: IKD over time, gyro magnitude over time, accelerometer magnitude over time.
4. **The raw timing list and raw sensor list are removed from the saved-session screen.** The CSV export is unchanged, so the raw rows are still recoverable per-session via the existing long-press → Export action on the Sessions list.

### In scope (the entire feature)

- A session-detail-mode rewrite of `EventFeedActivity` (only when launched with `EXTRA_SESSION_ID` from `SessionsListActivity`)
- New `IkdSessionChartLoader` returning a `SessionChartData` payload — single suspend call, runs on `Dispatchers.IO`, mirrors the Phase 3 `IkdAggregator` pattern
- Two new additive `@Query` methods (`IkdEventDao.getSessionTimingBuckets`, `SensorSampleDao.getSessionSensorBuckets`) doing SQL-side downsampling
- Reuse of `views/IkdLineChartView.kt` and the existing MPAndroidChart dependency
- Style pass: Material `CardView` containers around the KPI strip and each chart, theme-aware colors via the existing `getProperPrimaryColor()` / `getProperTextColor()` helpers, consistent with `DashboardActivity`

### Explicitly preserved (out of scope, deliberately)

- **Live mode (`EventFeedActivity` launched without `EXTRA_SESSION_ID` from `DiagnosticsActivity`)** keeps its existing raw lists. That screen is a real-time "what just happened" debug feed and a different feature; chartifying it has different perf concerns and would defeat its purpose. Phase 5 only touches the saved-session code path.
- The **Phase 4 `Config.sensorDisplayMode` toggle (magnitude vs axes)** still works in live mode and in `DiagnosticsActivity`. On the new session-detail dashboard the toggle is irrelevant (charts always show magnitude in v1) so its toolbar action is hidden when `EXTRA_SESSION_ID` is present. See Decision #6.
- The **session metadata header from Phase 4** is replaced by the new KPI strip + metadata chip, but no metadata is dropped — orientation, locale, started/ended/duration, and counts all still surface, just compactly.
- **CSV export format is unchanged.**

### Out of scope (deferred to Phase 6 — see Section 10)

Multi-series axis charts (X/Y/Z overlaid), hold-time and flight-time as additional series on the timing chart, per-event chart drill-down, "compare to baseline" overlays, live-mode chart-ification, app-context fields, mood overlay, schema migration to v2, anomaly markers, IkdDataChangedBus.

---

## 2. Branch & Layering Discipline

Phase 5 is, like Phases 3 and 4, **independent of the keyboard layer**. The capture pipeline stays frozen. All work is read-side, in the companion app.

### Forbidden edits (do not modify in this phase)

| File | Reason |
|---|---|
| `services/SimpleKeyboardIME.kt` | Capture path — frozen since Phase 3 |
| `views/MyKeyboardView.kt` | Keyboard UI — frozen |
| `helpers/LiveCaptureSessionStore.kt` | Capture buffer — frozen |
| `helpers/KinematicSensorHelper.kt` | Capture path — frozen |
| `helpers/IkdRetentionWorker.kt` | Background worker — frozen |
| `databases/IkdDatabase.kt` | No schema migration this phase (no version bump) |
| Any Room `@Entity` (`IkdEvent`, `SensorSample`, `SessionRecord`) | No schema change |
| `databases/ClipsDatabase.kt`, `interfaces/ClipsDao.kt` | Out of scope, as in earlier phases |
| `helpers/IkdAggregator.kt` | Phase 3 aggregate-dashboard surface — additive only via a *separate* loader |
| `helpers/IkdSessionStatsLoader.kt` | **Phase 4 surface — frozen.** Reused unchanged for the KPI strip values. |
| `helpers/IkdCsvWriter.kt` | CSV format is the experiment's data contract — no shape change |
| `views/IkdLineChartView.kt` | **Frozen at single-series.** Phase 5 reuses the existing wrapper as-is; multi-series is a Phase 6 design call, not an in-place edit. |

### Allowed edits

| File | Allowed change |
|---|---|
| `interfaces/IkdEventDao.kt` | **Add** `getSessionTimingBuckets(sessionId, startMs, bucketWidthMs)` only |
| `interfaces/SensorSampleDao.kt` | **Add** `getSessionSensorBuckets(sessionId, startMs, bucketWidthMs)` only |
| `extensions/ContextExt.kt` | **Add** `val Context.ikdSessionChartLoader: IkdSessionChartLoader` |
| `activities/EventFeedActivity.kt` | Rewrite the *DB-backed* (session-id) code path; live-mode path untouched |
| `res/layout/activity_event_feed.xml` | Add a session-dashboard container; existing live-mode container stays |
| `res/menu/menu_event_feed.xml` | Conditionally hide `event_feed_toggle_sensor_view` when `EXTRA_SESSION_ID` is present (chart mode has no raw rows to toggle) |
| `res/values/strings.xml` | Add `session_dashboard_*` strings |
| `res/values/colors.xml` and/or `dimens.xml` | Add card-style dimensions if reused values aren't already in `commons` |

### Branch hygiene

- Branch name: `feat/phase5-session-dashboard`
- Cut from latest `main` after Phase 4 has been merged (Phase 4 is currently on `feat/phase4-session-detail` — that PR must land first or Phase 5 is rebased on it)
- Each sub-phase (5.1 → 5.4) lands as one focused commit using the project's `feat:` / `chore:` / `docs:` convention
- After 5.4 passes acceptance, open a single PR back to `main`

---

## 3. What's Shown (and Where)

### Saved-session mode (`EventFeedActivity` with `EXTRA_SESSION_ID`)

Single scrollable column inside the existing `NestedScrollView`. Three cards stacked top-to-bottom:

1. **KPI card** — a 4-cell horizontal strip styled identically to `DashboardActivity`'s KPI row:

| Cell | Source | Format |
|---|---|---|
| Sessions | always `1` (this *is* one session) → replaced by **Events** | `eventCount` from stats loader |
| Total typing time | derived from stats loader's `durationMs` | `Xm Ys` (reuse `IkdFormatters.formatDuration`) |
| WPM | stats loader | one decimal, `"—"` when null |
| Error rate | stats loader | one decimal % |

The Phase 3 dashboard's KPI strip uses Sessions / Total typing time / WPM / Error rate; for a single session "Sessions = 1" is uninteresting, so the per-session KPI strip swaps that cell for **Events**. WPM and Error rate keep their formulas. Avg IKD / Avg dwell / Avg flight move from being top-level KPI cells into a secondary row of three smaller chips beneath the headline KPIs — still visible at-a-glance, just visually de-emphasised compared to WPM and error rate.

2. **Metadata chip row** — a single line under the KPI card showing `Started at · Duration · Orientation · Locale`, separated by middle-dots. Compact one-liner, secondary text color. If a value is the sentinel (`-1` orientation or empty locale), that segment is omitted from the line rather than rendering "—".

3. **Three line charts** in their own cards, in this order:
   - **Timing — IKD (ms)**: average IKD per time bucket. Sentinel `-1` rows excluded from the average via SQL `CASE WHEN ikd_ms >= 0`. X axis labels: `m:ss` relative to session start.
   - **Gyro magnitude (rad/s)**: average gyro magnitude per time bucket. Empty buckets render as line breaks (no fake zeros) — same convention as the Phase 3 dashboard.
   - **Accel magnitude (m/s²)**: average accelerometer magnitude per time bucket.

### Live mode (`EventFeedActivity` without `EXTRA_SESSION_ID`) — UNCHANGED

The existing layout — empty placeholder + section labels + raw timing `RecyclerView` + raw sensor `RecyclerView` — is preserved verbatim. Phase 5 adds a *parallel* dashboard container in the same XML, gated by `View.GONE` / `View.VISIBLE` based on whether a session ID was passed in.

### Privacy invariants (unchanged from Phase 2)

All charts read aggregate counts and `eventCategory`-derived values. No raw text reconstructable. No new fields read.

---

## 4. Aggregation Strategy: SQL-Side Downsampling, ≤ 200 Points per Chart

This phase keeps the Phase 3 commitment: **never fold raw rows in Kotlin**. Sensor sessions can hit 30k+ rows (5 minutes × 50 Hz × 2 sensors); pulling those across the Room boundary just to bucket them in Kotlin is wasteful and would re-introduce StrictMode noise. Bucket in SQL, return ≤ 200 rows per chart, render directly.

### Bucket-width selection

`IkdSessionChartLoader.bucketWidthMs(sessionDurationMs)` produces an integer width in ms:

```
bucketWidthMs = max(MIN_BUCKET_MS, ceil(sessionDurationMs / TARGET_BUCKETS))
```

with `MIN_BUCKET_MS = 50` (one slot per ~3 sensor samples at 50 Hz) and `TARGET_BUCKETS = 200`. So:

| Session length | Bucket width | Buckets returned |
|---|---|---|
| 10 s | 50 ms | 200 |
| 30 s | 150 ms | 200 |
| 1 min | 300 ms | 200 |
| 5 min | 1500 ms | 200 |
| 1 hour | 18000 ms (18 s) | 200 |

For sessions with `endedAt == null` (in-flight) the duration is computed from `MAX(timestamp) - MIN(timestamp)` of the events themselves, falling back to a fixed 30 s window if nothing has been recorded yet (the loader returns an empty `SessionChartData` in that fallback path).

### Bucket key

`(timestamp - :startMs) / :bucketWidthMs` — relative-time integer division. Each row's bucket index is its 0-based position in the timeline. The loader maps that index back to a label like `0:00`, `0:30`, `1:00`, …

### Timing query (single round-trip)

```kotlin
@Query("""
    SELECT
        ((timestamp - :startMs) / :bucketWidthMs) AS bucketIndex,
        AVG(CASE WHEN ikd_ms         >= 0 THEN ikd_ms         END) AS avgIkdMs,
        AVG(CASE WHEN hold_time_ms   >= 0 THEN hold_time_ms   END) AS avgHoldMs,
        AVG(CASE WHEN flight_time_ms >= 0 THEN flight_time_ms END) AS avgFlightMs,
        COUNT(*) AS eventCount
    FROM ikd_events
    WHERE session_id = :sessionId
    GROUP BY bucketIndex
    ORDER BY bucketIndex
""")
fun getSessionTimingBuckets(sessionId: String, startMs: Long, bucketWidthMs: Long): List<TimingBucketRow>
```

`avgHoldMs` and `avgFlightMs` are computed but not rendered in v1 — they are returned for free by the same group, ready to be plotted in Phase 6 when multi-series is added without a second query.

### Sensor query

SQLite does not expose `sqrt` as a built-in scalar. The cleanest way to keep aggregation SQL-side is to average the *squared* magnitude per bucket and take the square root in Kotlin per row — one `Math.sqrt` per ≤ 200 rows is free.

```kotlin
@Query("""
    SELECT
        ((timestamp - :startMs) / :bucketWidthMs) AS bucketIndex,
        sensor_type AS sensorType,
        AVG(x * x + y * y + z * z) AS avgSquaredMagnitude,
        AVG(x) AS avgX,
        AVG(y) AS avgY,
        AVG(z) AS avgZ,
        COUNT(*) AS sampleCount
    FROM sensor_samples
    WHERE session_id = :sessionId
    GROUP BY bucketIndex, sensor_type
    ORDER BY sensor_type, bucketIndex
""")
fun getSessionSensorBuckets(sessionId: String, startMs: Long, bucketWidthMs: Long): List<SensorBucketRow>
```

`avgX/Y/Z` are returned for free by the same group, ready for Phase 6 multi-series axis charts. v1 computes magnitude as `sqrt(avgSquaredMagnitude)` in Kotlin and discards X/Y/Z.

### Loader output

```kotlin
class IkdSessionChartLoader(private val db: IkdDatabase) {

    data class TimingPoint(val label: String, val avgIkdMs: Double?)
    data class SensorPoint(val label: String, val avgMagnitude: Float?)

    data class SessionChartData(
        val sessionId: String,
        val durationMs: Long,
        val bucketWidthMs: Long,
        val timing: List<TimingPoint>,
        val gyro: List<SensorPoint>,
        val accel: List<SensorPoint>,
    )

    suspend fun load(sessionId: String): SessionChartData?  // null when session row not found
}
```

Threading and perf-log conventions copy `IkdAggregator` and `IkdSessionStatsLoader`: `withContext(Dispatchers.IO) { measureTimeMillis { ... }; if (BuildConfig.DEBUG) Log.d(LOG_TAG, "load(...) took ${ms}ms") }`.

### Performance budget

| Session length | Sensor rows raw | Sensor rows after SQL bucket | Target wall-clock |
|---|---|---|---|
| 30 s | ~3 000 | ~400 (200 per sensor type) | < 100 ms |
| 5 min | ~30 000 | ~400 | < 200 ms |
| 1 hour | ~360 000 | ~400 | < 400 ms |

Validation: same shape as Phase 3 — log wall-clock per call, post measurements in the PR description.

---

## 5. Charting Library: Reuse Phase 3

**MPAndroidChart 3.1.0** is already in `app/build.gradle.kts` (Phase 3) and the theme-aware `views/IkdLineChartView.kt` wrapper is already shipped. Phase 5 reuses both verbatim. **No new dependency, no chart-library swap.** Same primary color, same null-bucket-as-line-break convention, same legend-off-on-small-screens behaviour.

`IkdLineChartView` is on the **forbidden** list this phase: it stays single-series. If multi-series support is wanted (axis chart, multi-line timing chart), Phase 6 either edits it or ships a sibling `IkdMultiLineChartView`. That's an explicit Phase 6 design call, not a side-effect of Phase 5 work.

---

## 6. Sub-Phases (4)

Four sub-phases. Each compiles and ships. 5.1–5.3 land as commits; 5.4 closes the branch.

---

### 5.1 Per-Session Chart Loader + DAO Additions

**Goal:** All data-layer plumbing for the three new charts, no UI. After this commit `IkdSessionChartLoader` is JVM-unit-testable in isolation.

#### Deliverables

- `interfaces/TimingBucketRow.kt` — Room POJO (Section 4)
- `interfaces/SensorBucketRow.kt` — Room POJO
- `interfaces/IkdEventDao.kt` (modified) — **add** `getSessionTimingBuckets(...)` only
- `interfaces/SensorSampleDao.kt` (modified) — **add** `getSessionSensorBuckets(...)` only
- `helpers/IkdSessionChartLoader.kt` — `SessionChartData` data class + `suspend fun load(sessionId): SessionChartData?` + `Companion.bucketWidthMs(durationMs): Long` + `Companion.buildChartData(rows...): SessionChartData` (pure; unit-testable)
- `extensions/ContextExt.kt` (modified) — **add** `val Context.ikdSessionChartLoader: IkdSessionChartLoader`
- `app/src/test/.../IkdSessionChartLoaderTest.kt`:
  - `bucketWidthMs(0)` returns `MIN_BUCKET_MS`
  - `bucketWidthMs(10_000)` returns 50 ms (200 buckets at 50 ms)
  - `bucketWidthMs(60_000)` returns 300 ms
  - `buildChartData` from empty rows → `timing`/`gyro`/`accel` empty lists
  - `buildChartData` from a hand-built fixture covering both sensor types → magnitude is `sqrt(avgSquaredMagnitude)` and X-axis labels are `m:ss`
  - sentinel-only IKD rows (`ikd_ms = -1`) → `avgIkdMs == null` after `CASE WHEN` filter (verified via the row-builder shape, since the SQL is exercised at integration level by the existing tests when we run on Android)

#### Acceptance

- `./gradlew test` passes the new `IkdSessionChartLoaderTest`
- `./gradlew detekt` and `./gradlew lint` produce no regressions vs. existing baselines
- `./gradlew assembleCoreDebug` succeeds
- `IkdDatabase.version` is **still 1** (no migration this phase)

#### Commit

`feat(phase5): add per-session chart loader and bucketed queries`

---

### 5.2 Session Dashboard Layout + KPI / Metadata Strip

**Goal:** The new dashboard layout renders, KPI strip and metadata chip are populated. Charts still placeholders. Live mode unchanged.

#### Deliverables

- `res/layout/activity_event_feed.xml` (modified):
  - Add a `LinearLayout android:id="@+id/event_feed_session_dashboard" android:visibility="gone"` at the top of the existing scroll content
  - Inside it: KPI `MaterialCardView` with 4 cells (events / typing time / WPM / error rate) styled to match `DashboardActivity`'s strip; secondary chip row with avg IKD / avg dwell / avg flight; metadata chip row (started · duration · orientation · locale); 3 placeholder `MaterialCardView`s for charts
  - **Remove** the Phase 4 `event_feed_session_header` LinearLayout and its 12 rows (its contents are absorbed into the new KPI + chip rows)
  - Keep the existing `event_feed_timing_label`, `event_feed_timing_header`, `event_feed_timing_list`, `event_feed_sensor_label`, `event_feed_sensor_header`, `event_feed_sensor_list` blocks as-is — they are now "live-mode only" and gated by a sibling container `event_feed_live_container` so we can flip visibility cleanly
  - Existing `event_feed_empty_text` and `event_feed_not_found_text` are kept and reused
- `activities/EventFeedActivity.kt` (modified):
  - In `onCreate`/`onResume`, branch on `intent.hasExtra(EXTRA_SESSION_ID)`:
    - DB-backed → show `event_feed_session_dashboard`, hide `event_feed_live_container`, call `ikdSessionStatsLoader.load(...)` AND `ikdSessionChartLoader.load(...)` on the same background hop, populate KPI + metadata chips on result
    - Live-mode → show `event_feed_live_container`, hide `event_feed_session_dashboard`, existing logic unchanged
  - Sensor-toggle menu action (`event_feed_toggle_sensor_view` from Phase 4) is hidden when in DB-backed mode (`menu.findItem(...).isVisible = !hasSessionId`)
- `res/values/strings.xml` — `session_dashboard_kpi_events`, `session_dashboard_kpi_typing_time`, `session_dashboard_kpi_wpm`, `session_dashboard_kpi_error_rate`, `session_dashboard_avg_ikd`, `session_dashboard_avg_dwell`, `session_dashboard_avg_flight`, `session_dashboard_metadata_separator`
- The Phase 4 strings (`session_detail_started_at`, `session_detail_ended_at`, etc.) are **kept** — they are still used by the in-flight session not-found path and may be reused by the metadata chip's content-description, so we leave them rather than churn translations

#### Acceptance

- Tapping a session in `SessionsListActivity` opens the new dashboard layout (KPI strip + metadata chip + 3 empty chart placeholders); the raw rows are gone
- Live-mode `EventFeedActivity` still shows the raw rows and live behaviour is unchanged (verified by Diagnostics → "View Log")
- KPI values match unit-tested loader output
- Phase-4 sensor-toggle action is hidden in DB-backed mode and visible in live mode
- Sessions with `endedAt == null` render the dashboard with `"—"` for the duration cell and `"—"` for WPM, no crash
- Sessions with orientation/locale capture disabled render those segments dropped from the metadata chip line
- No edits to any "Forbidden" file from Section 2

#### Commit

`feat(phase5): rewrite session detail as KPI + metadata dashboard layout`

---

### 5.3 Three Per-Session Line Charts

**Goal:** Wire IKD timing chart + gyro magnitude chart + accelerometer magnitude chart into the placeholders from 5.2.

#### Deliverables

- 3 `<view class=".views.IkdLineChartView" />` instances inflated in `activity_dashboard.xml` placeholder slots:
  1. **Timing — IKD (ms)** — Y = avg IKD per bucket; null buckets render as line breaks
  2. **Gyro magnitude (rad/s)** — Y = `sqrt(avgSquaredMagnitude)` per bucket
  3. **Accel magnitude (m/s²)** — Y = `sqrt(avgSquaredMagnitude)` per bucket
- `EventFeedActivity.render()` extended to call `chart.setData(labels, values, yLabel)` on each `IkdLineChartView` from `SessionChartData`
- `res/values/strings.xml` — chart titles: `session_dashboard_chart_ikd`, `session_dashboard_chart_gyro`, `session_dashboard_chart_accel`; Y-axis units strings
- Each chart gets its own `MaterialCardView` parent with a small title `MyTextView` above it (using the project's existing card / section-label patterns); titles use `getProperPrimaryColor()` for tint, matching `DashboardActivity`

#### Performance guardrails

- Each chart receives at most 200 floats (Section 4 cap). MPAndroidChart handles thousands; we are nowhere near that.
- No animation on first render (matches Phase 3): `chart.animateX(0)`.
- Hardware acceleration left at the MPAndroidChart default (verified per chart instance during 5.4).

#### Acceptance

- All three charts render with seeded data; values match unit-tested loader output
- Switching between sessions re-aggregates and re-renders within Section 4's budget
- Light/dark theme honored — same primary/text colours as `DashboardActivity` charts
- A session with no sensor samples (e.g., privacy-mode-on session somehow re-opened) renders the timing chart and a "no sensor data" empty-state card in place of the gyro/accel charts (see 5.4)

#### Commit

`feat(phase5): wire IKD, gyro, and accel charts into session dashboard`

---

### 5.4 Polish: Empty States, Style Pass, Performance Validation

**Goal:** Close the loop. Validate perf on a real device. Style the screen so it reads as a dashboard, not as a settings page.

#### Deliverables

- **Empty states** for each chart card: when the loader returns an empty list for one of `timing` / `gyro` / `accel`, hide the chart and show a centered `MyTextView` "No data" inside the same `MaterialCardView`, instead of rendering an empty chart with no axis ticks
- **Style pass:**
  - KPI cells use bold large text for values, smaller secondary text for labels — same hierarchy as `DashboardActivity`
  - All cards use a consistent corner radius (a single `dimens.xml` value, e.g. `card_corner_radius`) and elevation
  - Section labels use `getProperPrimaryColor()`, same as the rest of the app
  - Padding/margins match the Phase 3 dashboard so a user moving between aggregate dashboard and per-session dashboard feels they belong to the same family
- **Performance log** on `IkdSessionChartLoader.load()` mirroring `IkdAggregator`: `measureTimeMillis` + `Log.d(LOG_TAG, "load(${shortId}) took ${durationMs}ms")` gated on `BuildConfig.DEBUG`
- StrictMode (already enabled debug-only since Phase 3) verifies no main-thread DB reads during the dashboard's golden path
- `CLAUDE.md` updated with a Phase 5 section: scope, files, key decisions, that live mode is preserved, and the chart-library reuse note
- `roadmap/FeatureRoadmap.md` updated to add Phase 5 status, summary, and link
- Detekt baseline + Lint baseline updated only if needed; never with `-PdisableLint`

#### Acceptance

- All Sub-Phase 5.1–5.3 acceptance items still pass
- Empty-state cards render correctly for sessions missing one or both sensor types
- StrictMode does not flag main-thread DB reads on the saved-session golden path
- Phase 5 PR description lists wall-clock measurements (Section 4 budget) and the device used
- Visual style is consistent with `DashboardActivity` — verified by side-by-side screenshots in the PR description

#### Commit

`docs(phase5): document session dashboard scope and visual conventions`

---

## 7. Files to Create / Modify (vs. forbidden)

### Create (4 files)

| File | Sub-phase |
|---|---|
| `interfaces/TimingBucketRow.kt` | 5.1 |
| `interfaces/SensorBucketRow.kt` | 5.1 |
| `helpers/IkdSessionChartLoader.kt` | 5.1 |
| `app/src/test/.../IkdSessionChartLoaderTest.kt` | 5.1 |

### Modify (additive only — no existing logic touched)

| File | Sub-phase | Change |
|---|---|---|
| `interfaces/IkdEventDao.kt` | 5.1 | **Add** `getSessionTimingBuckets(...)` |
| `interfaces/SensorSampleDao.kt` | 5.1 | **Add** `getSessionSensorBuckets(...)` |
| `extensions/ContextExt.kt` | 5.1 | **Add** `val Context.ikdSessionChartLoader` |
| `activities/EventFeedActivity.kt` | 5.2 / 5.3 | DB-backed mode rewrite; live-mode untouched |
| `res/layout/activity_event_feed.xml` | 5.2 / 5.3 | Add session-dashboard container + 3 chart cards; live-mode container preserved as a sibling |
| `res/menu/menu_event_feed.xml` | 5.2 | Sensor-toggle item visibility is now controlled at runtime — XML attribute change only if needed |
| `res/values/strings.xml` | 5.2 / 5.3 | Add `session_dashboard_*` strings |
| `res/values/dimens.xml` | 5.4 | Add `card_corner_radius` if not already defined |
| `CLAUDE.md` | 5.4 | Phase 5 section (note: file is gitignored — committed locally only) |
| `roadmap/FeatureRoadmap.md` | 5.4 | Reflect Phase 5 scope |

### Forbidden (do not touch — see Section 2 for full list)

`SimpleKeyboardIME.kt`, `MyKeyboardView.kt`, `LiveCaptureSessionStore.kt`, `KinematicSensorHelper.kt`, `IkdRetentionWorker.kt`, `IkdDatabase.kt`, all `@Entity` data classes, `ClipsDatabase.kt`, `ClipsDao.kt`, `IkdAggregator.kt`, `IkdSessionStatsLoader.kt` (Phase 4 — frozen), `IkdCsvWriter.kt`, `IkdLineChartView.kt`.

---

## 8. Acceptance Criteria

The whole phase is done when **all** of these are green on `feat/phase5-session-dashboard`:

- [ ] `./gradlew assembleCoreDebug` succeeds
- [ ] `./gradlew test` passes (including the new `IkdSessionChartLoaderTest`)
- [ ] `./gradlew detekt` and `./gradlew lint` produce no regressions vs. existing baselines
- [ ] `IkdDatabase.version` is unchanged (still 1)
- [ ] `helpers/IkdCsvWriter.kt`, `helpers/IkdAggregator.kt`, `helpers/IkdSessionStatsLoader.kt`, and `views/IkdLineChartView.kt` are unchanged
- [ ] No file in Section 2's "Forbidden edits" list is modified (verifiable via `git diff main..feat/phase5-session-dashboard --name-only`)
- [ ] Tapping a session in `SessionsListActivity` opens the new dashboard with KPI + metadata + 3 charts; **no raw timing or sensor list is shown**
- [ ] Live-mode `EventFeedActivity` (Diagnostics → "View Log") shows the raw lists exactly as before
- [ ] Sessions with `endedAt == null` render with `"—"` for duration and WPM without crashing
- [ ] Empty-state cards render for sessions missing gyro / accel data
- [ ] Charts respect light/dark theme and match `DashboardActivity`'s visual language
- [ ] StrictMode does not flag main-thread DB reads on the saved-session golden path
- [ ] Loader perf < 200 ms for the largest seeded session — measurement in PR description

---

## 9. Decisions

| # | Topic | Decision |
|---|---|---|
| 1 | Branch strategy | All work on `feat/phase5-session-dashboard`; merged to `main` only after 5.4 |
| 2 | Independence from keyboard layer | Section 2's "Forbidden edits" list is hard. Same as Phases 3 and 4. |
| 3 | Schema migration | None this phase. `IkdDatabase.version` stays at 1. |
| 4 | Charting library | **Reuse Phase 3's MPAndroidChart + `IkdLineChartView` wrapper.** No new dependency, no library swap. |
| 5 | Replacement, not addition | Raw timing list and raw sensor list are **removed** from the saved-session screen. Raw data still exists in `ikd.db` and is still recoverable via the existing per-session CSV export. |
| 6 | Live mode preserved | `EventFeedActivity` without `EXTRA_SESSION_ID` keeps the raw lists. Different feature, different perf concerns. |
| 7 | Chart series count | v1 ships single-series charts only (IKD; gyro magnitude; accel magnitude). Multi-series (X/Y/Z, hold/flight) is a Phase 6 design call. |
| 8 | Sensor toggle in session detail | Phase 4's `Config.sensorDisplayMode` toggle action is hidden when on the session dashboard (no raw rows to toggle; charts always show magnitude). The toggle still works in live mode and in `DiagnosticsActivity`. |
| 9 | Aggregation strategy | SQL `GROUP BY` returning ≤ 200 rows per chart, mirroring Phase 3. No in-memory event folding. |
| 10 | Bucket-width formula | `max(50 ms, ceil(durationMs / 200))`. Constants live in `IkdSessionChartLoader.Companion` so they're testable. |
| 11 | SQL has no `sqrt` | Aggregate `AVG(x*x + y*y + z*z)` per bucket, take `sqrt` in Kotlin per row. ≤ 400 sqrt calls per session load — free. |
| 12 | Cache invalidation | Re-aggregate on `onResume` and Refresh menu tap. No `IkdDataChangedBus`, no observers — same as Phase 3 / 4. |
| 13 | KPI cell choice | "Sessions" cell from Phase 3's strip becomes "Events" on the per-session dashboard. WPM, error rate, total typing time stay. |
| 14 | Phase 4 metadata header | Replaced. The 12-row label/value strip becomes one KPI card + one secondary chip row + one metadata one-liner. No metadata is dropped. |
| 15 | Style alignment | All cards / labels / colors mirror `DashboardActivity` so the two dashboards look like a family. Single `card_corner_radius` dimension, theme-aware tints via existing `getProperPrimaryColor()` / `getProperTextColor()`. |

---

## 10. Explicitly Deferred to Phase 6

These are the obvious next moves; listing them here so we don't relitigate scope mid-phase.

- Multi-series charts (X / Y / Z overlaid on the sensor charts; hold-time and flight-time as additional series on the timing chart) — requires either editing or siblinging `IkdLineChartView`
- Per-event drill-down (tap a chart point → bottom sheet with the underlying events / samples in that bucket)
- "Compare to baseline" overlays (this session vs. your weekly average IKD line)
- Live-mode chart-ification (charts that update every second while the keyboard is open) — different perf model, different UX
- App-context capture (which app the session was typed into) — would gain a `SELECT` projection here once the column exists, but reopens the Phase 2 privacy disclaimer scope first
- Rolling 14-day baseline reference lines on charts (was on the Phase 3 deferred list)
- Mood / energy overlay row (was on the Phase 3 deferred list; needs `mood_entries` schema migration)
- Per-session export from inside the session dashboard (currently lives only on the Sessions list long-press)
- A "show raw log" expander linking to a debug-only sub-activity that displays the dropped raw rows (purely for power-user debugging; not a regular UX path)
- Heatmap / circadian charts on the aggregate dashboard (already deferred from Phase 3)
- Median IKD chart (deferred from Phase 3)

Anything from this list earns its own focused mini-plan in `roadmap/Phase6/` if and when the user wants it.
