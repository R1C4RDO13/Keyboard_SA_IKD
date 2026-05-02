# Phase 3 — Step-by-Step Implementation Plan

This document breaks the four sub-phases of `Phase3_Plan.md` into small, individually
build-green commits. Each step is one focused change; each ends in a single
conventional-commit on `feat/phase3-dashboard`.

Status is updated as each step lands.

---

## Step 0 — Branch + roadmap pointer

**Files:**
- `roadmap/FeatureRoadmap.md` (already locally modified — pointer to Phase3_Plan)
- `roadmap/Phase3/Phase3_Plan.md` (untracked — needs to land on the branch)
- `roadmap/Phase3/Phase3_Steps.md` (this file)

**Commit:** `docs(phase3): add implementation step breakdown`

**Acceptance:** branch exists, roadmap points to Phase 3 plan, step list committed.

---

## Sub-Phase 3.1 — Aggregator + Read-Only DAO Additions

### Step 1 — Room POJOs (`EventBucketRow`, `SessionBucketRow`)

**Files (create):**
- `app/src/main/kotlin/org/fossify/keyboard/interfaces/EventBucketRow.kt`
- `app/src/main/kotlin/org/fossify/keyboard/interfaces/SessionBucketRow.kt`

Plain Kotlin data classes. No `@Entity` — just shapes returned from `@Query` projections.

**Validation:** `./gradlew assembleCoreDebug` must succeed (POJOs unused so far, but compile cleanly).

**Commit:** `feat(phase3): add bucket row POJOs for SQL aggregation`

---

### Step 2 — `IkdEventDao.getEventBuckets(...)` query

**Files (modify):**
- `app/src/main/kotlin/org/fossify/keyboard/interfaces/IkdEventDao.kt`

Add a single `@Query` method returning `List<EventBucketRow>`. Existing methods untouched.

**Validation:** `./gradlew assembleCoreDebug` (KSP regenerates DAO impl).

**Commit:** `feat(phase3): add bucketed event aggregation query`

---

### Step 3 — `SessionDao` aggregation queries

**Files (modify):**
- `app/src/main/kotlin/org/fossify/keyboard/interfaces/SessionDao.kt`

Add `getSessionBuckets(bucketFormat, fromMs, toMs): List<SessionBucketRow>` and
`getEarliestSessionStart(): Long?`. Existing methods untouched.

**Validation:** `./gradlew assembleCoreDebug`.

**Commit:** `feat(phase3): add bucketed session and earliest-start queries`

---

### Step 4 — `IkdAggregator` skeleton (Range enum + DTOs only)

**Files (create):**
- `app/src/main/kotlin/org/fossify/keyboard/helpers/IkdAggregator.kt`

`Range` enum with `WEEK`, `MONTH`, `ALL_TIME`. `Snapshot` and `Bucket` data classes.
`snapshot()` stub that returns an empty result (computed contracts only — no SQL yet).

**Validation:** `./gradlew assembleCoreDebug`.

**Commit:** `feat(phase3): scaffold IkdAggregator with Range enum and DTOs`

---

### Step 5 — `IkdAggregator.snapshot()` real implementation

**Files (modify):**
- `app/src/main/kotlin/org/fossify/keyboard/helpers/IkdAggregator.kt`

Implement `snapshot(range)` calling the new DAOs. Wraps the work in
`withContext(Dispatchers.IO)`. Zip-by-bucket strategy for WPM; sentinel-aware avg
IKD; aggregate KPIs computed across buckets. No coroutines library is currently a
project dependency, but kotlinx-coroutines-core is transitively available via Room
KTX and WorkManager runtime KTX — verify before adding the import.

**Validation:** `./gradlew assembleCoreDebug` + `./gradlew detekt` (no regression).

**Commit:** `feat(phase3): implement IkdAggregator snapshot with SQL group-by`

---

### Step 6 — `Context.ikdAggregator` extension

**Files (modify):**
- `app/src/main/kotlin/org/fossify/keyboard/extensions/ContextExt.kt`

Add `val Context.ikdAggregator: IkdAggregator` returning `IkdAggregator(ikdDB)`.

**Validation:** `./gradlew assembleCoreDebug`.

**Commit:** `feat(phase3): add Context.ikdAggregator extension`

---

### Step 7 — Aggregator unit tests

**Files (create):**
- `app/src/test/kotlin/org/fossify/keyboard/helpers/IkdAggregatorTest.kt`
- `app/build.gradle.kts` — only if an `androidTest` runner is required and we
  decide to use Robolectric for Room. Otherwise, prefer plain JVM unit tests
  that run aggregator math against a fake DAO interface (since Room queries
  cannot execute on plain JVM).

**Strategy decision:** Room queries can't run on plain JVM. Two paths:
1. **Robolectric** + in-memory Room DB — adds testRuntimeOnly Robolectric
   dependency. Heavier but lets us validate real SQL `GROUP BY`.
2. **Fake DAOs** + math-only tests — only validates the aggregator's bucket
   zipping and KPI math, not the SQL itself.

We adopt **Path 2** (fake DAOs). Reasons: the project has no existing test
infrastructure, no Robolectric, no androidTest runner; adding either is out
of scope for an incremental commit. The SQL is small, declarative, and gets
exercised end-to-end at runtime in 3.4's perf validation. Math-only tests
catch the harder-to-spot bugs in the zip + reducer.

**Validation:** `./gradlew test` (after the step) must pass. `./gradlew
assembleCoreDebug` still succeeds.

**Commit:** `test(phase3): add IkdAggregator math unit tests`

---

## Sub-Phase 3.2 — Dashboard Activity Shell + Navigation

### Step 8 — Dashboard strings + menu

**Files (modify):**
- `app/src/main/res/values/strings.xml` — add `dashboard_*` namespace.

**Files (create):**
- `app/src/main/res/menu/menu_dashboard.xml` — single Refresh item.

**Validation:** `./gradlew assembleCoreDebug` + `./gradlew lint` (UnusedResources
warnings expected for strings until Step 9; lint baseline must not regress
beyond the temporary additions; finalised in Step 9).

**Commit:** `chore(phase3): add dashboard strings and menu resources`

---

### Step 9 — Dashboard layout + activity shell + manifest

**Files (create):**
- `app/src/main/res/layout/activity_dashboard.xml` — toggle group, KPI strip,
  3 placeholder `FrameLayout` slots, full-bleed empty-state `MyTextView`.
- `app/src/main/kotlin/org/fossify/keyboard/activities/DashboardActivity.kt` —
  `SimpleActivity` subclass with view binding; range selector; onResume
  triggers IO + main render; restore selected range across rotation; KPI strip
  bind; empty-state branch when no sessions.

**Files (modify):**
- `app/src/main/AndroidManifest.xml` — register `DashboardActivity`.

**Validation:** `./gradlew assembleCoreDebug`. Activity reachable only via
`adb shell am start -n org.fossify.keyboard.debug/org.fossify.keyboard.activities.DashboardActivity`
until Step 10 wires entry points.

**Commit:** `feat(phase3): add DashboardActivity shell with KPI strip and range selector`

---

### Step 10 — Navigation entry points

**Files (modify):**
- `app/src/main/kotlin/org/fossify/keyboard/activities/IkdSettingsActivity.kt` —
  add a "View Dashboard" row above "View Sessions History".
- `app/src/main/res/layout/activity_ikd_settings.xml` — add the holder row.
- `app/src/main/res/values/strings.xml` — `ikd_view_dashboard_title` if needed
  (or reuse `dashboard_title`).
- `app/src/main/kotlin/org/fossify/keyboard/activities/MainActivity.kt` — wire
  the existing or a new "Insights" launch path. **Decision pending review of
  `activity_main.xml`:** the current main layout is dominated by sample
  `MyEditText`s for keyboard testing; adding a button risks visual clutter.
  If the project layout has no clear slot, surface `DashboardActivity` from
  the existing `mainToolbar` overflow menu (`menu_main`) instead of adding
  a button to the body. This keeps `activity_main.xml` untouched and stays
  within the spirit of "add a single launch point".

**Validation:** `./gradlew assembleCoreDebug` + `./gradlew lint`. Both entry
points open the dashboard.

**Commit:** `feat(phase3): wire dashboard navigation from main and IKD settings`

---

## Sub-Phase 3.3 — Three Trend Charts

### Step 11 — Add MPAndroidChart dependency

**Files (modify):**
- `gradle/libs.versions.toml` — add `mpandroidchart = "v3.1.0"` and library entry.
- `app/build.gradle.kts` — add `implementation(libs.mpandroidchart)`.

**Validation:** `./gradlew assembleCoreDebug` succeeds and pulls the artifact
from JitPack (already in `settings.gradle.kts`).

**Commit:** `build(phase3): add MPAndroidChart dependency`

---

### Step 12 — `IkdLineChartView` wrapper

**Files (create):**
- `app/src/main/kotlin/org/fossify/keyboard/views/IkdLineChartView.kt`

Wraps `LineChart`. Theme-aware (`getProperPrimaryColor()`, `getProperTextColor()`).
Disables right Y axis, description, legend on small screens. Single setter:
`setData(labels, values, yLabel)`. Null values render as line breaks (no fake zeros).

**Validation:** `./gradlew assembleCoreDebug`.

**Commit:** `feat(phase3): add IkdLineChartView theme-aware wrapper`

---

### Step 13 — Wire three charts into the dashboard

**Files (modify):**
- `app/src/main/res/layout/activity_dashboard.xml` — replace `FrameLayout`
  placeholders with three `IkdLineChartView` instances (Speed / IKD / Errors).
- `app/src/main/kotlin/org/fossify/keyboard/activities/DashboardActivity.kt` —
  `render()` populates each chart from `Snapshot.buckets`. Bucket label
  formatting (e.g., short day names, weeks) handled in render.

**Validation:** `./gradlew assembleCoreDebug`. Manual smoke check: dashboard
opens with seeded data, all three charts render, range switch re-aggregates.

**Commit:** `feat(phase3): wire Speed, IKD, and Error rate charts into dashboard`

---

## Sub-Phase 3.4 — Polish + Performance Validation

### Step 14 — StrictMode in debug build

**Files (modify):**
- `app/src/main/kotlin/org/fossify/keyboard/App.kt` — gate
  `StrictMode.ThreadPolicy.detectDiskReads()` on `BuildConfig.DEBUG`.

**Validation:** Run dashboard golden path in debug — no `LogcatStrictMode`
violations. `./gradlew assembleCoreDebug` succeeds.

**Commit:** `feat(phase3): enable StrictMode disk-read detection in debug builds`

---

### Step 15 — Wall-clock perf logging in `IkdAggregator`

**Files (modify):**
- `app/src/main/kotlin/org/fossify/keyboard/helpers/IkdAggregator.kt` — wrap
  `snapshot()` body in `measureTimeMillis` and `Log.d` the duration when
  `BuildConfig.DEBUG`.

**Validation:** `./gradlew assembleCoreDebug` + manual logcat smoke.

**Commit:** `feat(phase3): log aggregator wall-clock duration in debug builds`

---

### Step 16 — CLAUDE.md / docs update

**Files (modify):**
- `CLAUDE.md` — add a "Phase 3 — Insights dashboard (implemented)" subsection
  matching the Phase 1 / Phase 2 pattern.
- `roadmap/Phase3/Phase3_Plan.md` — flip `Status: Not started` → `Status: Complete`.
- `roadmap/Phase3/Phase3_Steps.md` — mark all steps complete.

**Validation:** none required (docs only).

**Commit:** `docs(phase3): mark Phase 3 complete and document dashboard architecture`

---

## Validation gates per commit (every step)

- `./gradlew assembleCoreDebug` succeeds
- `./gradlew test` passes (only when test files are touched — Step 7 onward)
- `./gradlew detekt` produces no new violations vs. baseline (currently 25 weighted)
- `./gradlew lint` produces no new violations vs. existing baseline
- `git diff main..HEAD --name-only` lists nothing in Section 2's "Forbidden edits" set:
  `services/SimpleKeyboardIME.kt`, `views/MyKeyboardView.kt`,
  `helpers/LiveCaptureSessionStore.kt`, `helpers/KinematicSensorHelper.kt`,
  `helpers/IkdRetentionWorker.kt`, `databases/IkdDatabase.kt`,
  any `@Entity` data class, `databases/ClipsDatabase.kt`, `interfaces/ClipsDao.kt`.
