# Phase 4 — Session Detail Refresh (Magnitude-First Sensors + Rich Session Metadata)

**Status:** Planned
**Depends on:** Phase 2 (`ikd.db` populated, `SessionRecord` already carries orientation + locale), Phase 3 (read-side patterns established)
**Branch:** `feat/phase4-session-detail` — all work lands on this branch and merges to `main` only when sub-phase 4.4 (Polish) passes acceptance.
**Scope (one sentence):** Make the per-session detail screen actually useful — show orientation, locale, WPM, error rate, and other already-stored metadata at a glance, and replace the per-axis sensor wall-of-numbers with a magnitude-by-default view that can toggle back to X/Y/Z.

This phase is again **stupid simple, feasible, performance-first**, and follows the Phase 3 layering discipline: zero edits to the keyboard layer, no schema migration, no new dependencies.

---

## Table of Contents

1. [Concept & Scope](#1-concept--scope)
2. [Branch & Layering Discipline](#2-branch--layering-discipline)
3. [What's Shown (and Where)](#3-whats-shown-and-where)
4. [Aggregation Strategy: One SQL Round-Trip per Session](#4-aggregation-strategy-one-sql-round-trip-per-session)
5. [Sensor Magnitude Strategy](#5-sensor-magnitude-strategy)
6. [Sub-Phases (4)](#6-sub-phases-4)
   - [4.1 Per-Session Stats Query + Helper](#41-per-session-stats-query--helper)
   - [4.2 Session Detail Header Card](#42-session-detail-header-card)
   - [4.3 Sensor Magnitude Toggle (Event Feed + Diagnostics)](#43-sensor-magnitude-toggle-event-feed--diagnostics)
   - [4.4 Polish: Empty States, Refresh, Persisted Toggle, Validation](#44-polish-empty-states-refresh-persisted-toggle-validation)
7. [Files to Create / Modify (vs. forbidden)](#7-files-to-create--modify-vs-forbidden)
8. [Acceptance Criteria](#8-acceptance-criteria)
9. [Decisions](#9-decisions)
10. [Explicitly Deferred to Phase 5](#10-explicitly-deferred-to-phase-5)

---

## 1. Concept & Scope

Today, tapping a session in the **Sessions list** opens `EventFeedActivity` and shows two unbroken scrollable lists: one row per timing event and one row per sensor sample. There's no header. You can't tell *when* the session started, what *device orientation* it was captured in, what *locale* the keyboard was in, or what the user's actual *typing speed / accuracy* looked like — without scrolling through hundreds of rows and doing the math by eye. The sensor list is also a wall of three near-meaningless decimal values per row.

Phase 4 fixes both problems on the same screen:

1. **A metadata header card at the top of the session detail screen** that summarises the session in ~8 numbers — pulled from `sessions` + a single aggregation query over the session's events.
2. **Sensor display switches default from X/Y/Z to magnitude** (one number per sample), with a toggle to switch back to per-axis. The toggle is exposed in both the session detail screen *and* the live diagnostics screen, since both surface the same shape of data.

### In scope

- `EventFeedActivity` becomes a session detail screen when invoked with `EXTRA_SESSION_ID`:
  - New header card above the existing timing + sensor lists
  - Stats: started at · ended at · duration · orientation · locale · events · sensor samples · WPM · error rate · avg IKD · avg dwell · avg flight
- One new aggregator helper (`IkdSessionStatsLoader.kt`) returning a single `SessionStats` data class
- One new additive `@Query` on `IkdEventDao` returning a `SessionStatsRow` (averages + counts in one round-trip)
- Sensor list row reworks: one column ("magnitude") by default, with a single toolbar action that toggles to X/Y/Z and back
- `DiagnosticsActivity` sensor bars: collapse the three per-axis bars into one magnitude bar by default; same toolbar action toggles back to per-axis bars
- Toggle preference persisted in `Config.sensorDisplayMode` (default: `MAGNITUDE`)

### Out of scope (deferred to Phase 5 — see Section 10)

- Rendering session metrics as a mini chart on the detail screen (sparklines)
- Per-session "compare to baseline" strings
- App-context capture (which app/IME field the session came from)
- Mood / energy overlay row
- CSV export schema bump to include magnitude
- Heatmaps / circadian charts (already deferred from Phase 3)

---

## 2. Branch & Layering Discipline

Phase 4 is, like Phase 3, **independent of the keyboard layer**. The capture pipeline is finished and frozen. All work lives in the *companion app* read-side and the *settings* layer.

### Forbidden edits (do not modify in this phase)

| File | Reason |
|---|---|
| `services/SimpleKeyboardIME.kt` | Capture path — frozen |
| `views/MyKeyboardView.kt` | Keyboard UI — frozen |
| `helpers/LiveCaptureSessionStore.kt` | Capture buffer — frozen |
| `helpers/KinematicSensorHelper.kt` | Capture path — frozen (writes raw X/Y/Z; magnitude is derived at read time, never stored) |
| `helpers/IkdRetentionWorker.kt` | Background worker — frozen |
| `databases/IkdDatabase.kt` | No schema migration this phase (no version bump) |
| Any Room `@Entity` (`IkdEvent`, `SensorSample`, `SessionRecord`) | No schema change |
| `databases/ClipsDatabase.kt`, `interfaces/ClipsDao.kt` | Out of scope, as in earlier phases |
| `helpers/IkdAggregator.kt` | Phase 3 surface — additive only via a *separate* loader, not by editing this file |
| `helpers/IkdCsvWriter.kt` | CSV format is the experiment's data contract — no shape change this phase |

### Allowed edits

| File | Allowed change |
|---|---|
| `interfaces/IkdEventDao.kt` | **Add** `getSessionStats(sessionId)` returning a `SessionStatsRow` POJO — no edits to existing methods |
| `interfaces/SensorSampleDao.kt` | (Optional) Add `getSamplesForSessionWithMagnitude(...)` only if profiling shows we need a SQL projection — see Section 4. Default: derive in Kotlin. |
| `helpers/Config.kt` | **Add** `sensorDisplayMode: String` (`"MAGNITUDE"` / `"AXES"`, default `MAGNITUDE`) |
| `helpers/Constants.kt` | **Add** the preference key + the two enum strings as `const val` |
| `activities/EventFeedActivity.kt` | Add header card + sensor toggle handling. Keep timing list intact. |
| `activities/DiagnosticsActivity.kt` | Replace per-axis sensor block with toggle-aware sensor block. Keep all timing UI intact. |
| `res/layout/activity_event_feed.xml` | Add header card above existing sections |
| `res/layout/activity_diagnostics.xml` | Add a magnitude row alongside the existing axis rows; visibility driven by mode |
| `res/layout/item_sensor_reading.xml` | Add a magnitude column; per-mode visibility |
| `res/menu/menu_event_feed.xml` | Add a "Toggle sensor view" action |
| `res/menu/menu_diagnostics.xml` | Add the same action |
| `res/values/strings.xml` | Add `session_detail_*` and `sensor_view_*` strings |

### Branch hygiene

- Branch name: `feat/phase4-session-detail`
- Cut from latest `main` after Phase 3 has been merged
- Each sub-phase (4.1 → 4.4) lands as one focused commit using the project's `feat:` / `chore:` convention
- After 4.4 passes acceptance, open a single PR back to `main`

---

## 3. What's Shown (and Where)

### Session detail header card (new — `EventFeedActivity` with `EXTRA_SESSION_ID`)

Single card pinned at the top of the existing `NestedScrollView`, above the "Key Events" section. Two-column key/value rows.

| Key | Source | Derivation |
|---|---|---|
| Started at | `SessionRecord.startedAt` | `SimpleDateFormat("MMM d yyyy, HH:mm:ss")` |
| Ended at | `SessionRecord.endedAt` | Same; `"—"` if `null` (in-flight session) |
| Duration | `endedAt - startedAt` | `Xm Ys` (reuse `SessionsAdapter.formatDuration` lifted to a top-level helper) |
| Orientation | `SessionRecord.deviceOrientation` | Map `0` → "Portrait", `1` → "Landscape", `2` → "Reverse Portrait", `3` → "Reverse Landscape", `-1` → "—" (sentinel for "capture disabled") |
| Locale | `SessionRecord.locale` | Show as-is when non-empty, `"—"` when empty |
| Events / Sensor samples | `eventCount` / `sensorCount` from `SessionRecord` | Direct |
| WPM | aggregator (Section 4) | `events / 5 * 60_000 / durationMs`; `"—"` if `events <= 1` or `durationMs <= 0` |
| Error rate | aggregator (Section 4) | `100 * correctionCount / events`; one decimal |
| Avg IKD | aggregator (Section 4) | `AVG(ikd_ms WHERE ikd_ms >= 0)`; one decimal ms |
| Avg dwell | aggregator (Section 4) | `AVG(hold_time_ms WHERE hold_time_ms >= 0)`; one decimal ms |
| Avg flight | aggregator (Section 4) | `AVG(flight_time_ms WHERE flight_time_ms >= 0)`; one decimal ms |

**Privacy invariants (unchanged from Phase 2):** all of these are already stored or derivable from `eventCategory`/timing rows. No raw text is reconstructable.

The card is *only* shown when `EXTRA_SESSION_ID` is present (i.e., when launched from the Sessions list, not when used as the live event log from `DiagnosticsActivity`). For the live mode, the existing layout is unchanged — keep it minimal so the screen stays useful as a real-time log.

### Sensor display modes (both `EventFeedActivity` and `DiagnosticsActivity`)

| Mode | What is rendered | Default? |
|---|---|---|
| `MAGNITUDE` | One value per sample: `sqrt(x*x + y*y + z*z)`. In the row list: one column. In Diagnostics: one progress bar per sensor. | ✅ |
| `AXES` | Three values per sample (X, Y, Z) — exactly what is rendered today. | |

Toggle persisted via `Config.sensorDisplayMode`. On the toolbar action tap: flip the value in prefs, then re-render the visible adapter / bars (no re-query — the underlying `SensorReadingEvent` rows are unchanged).

---

## 4. Aggregation Strategy: One SQL Round-Trip per Session

Per-session metadata comes from two sources combined in Kotlin:

1. **`SessionRecord`** — already in memory or one cheap PK lookup (`SessionDao.getById(sessionId)`)
2. **A new `getSessionStats` query** on `IkdEventDao` that returns averages + counts in a single row

```kotlin
// interfaces/SessionStatsRow.kt
data class SessionStatsRow(
    val eventCount: Int,
    val correctionCount: Int,
    val avgIkdMs: Double?,
    val avgHoldMs: Double?,
    val avgFlightMs: Double?,
    val firstTimestamp: Long?,
    val lastTimestamp: Long?,
)
```

```kotlin
// interfaces/IkdEventDao.kt — additive
@Query("""
    SELECT
        COUNT(*)                                                AS eventCount,
        SUM(CASE WHEN is_correction THEN 1 ELSE 0 END)          AS correctionCount,
        AVG(CASE WHEN ikd_ms         >= 0 THEN ikd_ms         END) AS avgIkdMs,
        AVG(CASE WHEN hold_time_ms   >= 0 THEN hold_time_ms   END) AS avgHoldMs,
        AVG(CASE WHEN flight_time_ms >= 0 THEN flight_time_ms END) AS avgFlightMs,
        MIN(timestamp)                                          AS firstTimestamp,
        MAX(timestamp)                                          AS lastTimestamp
    FROM ikd_events
    WHERE session_id = :sessionId
""")
fun getSessionStats(sessionId: String): SessionStatsRow
```

One row, one round-trip. Even for a session with thousands of events the query never materialises the rows in memory — SQLite folds them in place.

`IkdSessionStatsLoader` (new helper) wraps `SessionRecord` + `SessionStatsRow` into a single `SessionStats` data class with all the fields the header card needs, plus the WPM / error-rate derivations:

```kotlin
class IkdSessionStatsLoader(private val db: IkdDatabase) {
    data class SessionStats(
        val record: SessionRecord,
        val durationMs: Long?,        // null when endedAt is null
        val wpm: Double?,
        val errorRatePct: Double?,
        val avgIkdMs: Double?,
        val avgHoldMs: Double?,
        val avgFlightMs: Double?,
    )

    suspend fun load(sessionId: String): SessionStats?  // runs on Dispatchers.IO; null when session row not found
}
```

`Context.ikdSessionStatsLoader` is added in `extensions/ContextExt.kt` as a lazy singleton, mirroring the Phase 3 `Context.ikdAggregator` pattern.

### Threading & cache (deliberately small)

- `EventFeedActivity.onResume()` already calls `loadDataFromDb` in `ensureBackgroundThread`. Add a parallel `IkdSessionStatsLoader.load(...)` call in the same background hop, post results back to the main thread together via one `runOnUiThread`. No singleton, no SharedFlow, no observers.
- StrictMode (already enabled in debug from Phase 3) will catch any accidental main-thread hop.

### Magnitude derivation

Magnitude is **never stored in the DB**. It's derived in Kotlin per row at read time:

```kotlin
fun SensorReadingEvent.magnitude(): Float =
    sqrt(x * x + y * y + z * z)
```

Reasons this stays out of SQL:
- `SensorSample` is an `@Entity` and so is forbidden to edit this phase
- A computed projection adds DB schema surface that's only useful to one screen
- A single `Math.sqrt` per row is free at any realistic sensor sampling rate (the existing `KinematicSensorHelper` runs at `SENSOR_DELAY_GAME` ≈ 50 Hz)
- Keeping CSV export untouched is an explicit Phase 4 invariant — both because the experiment's data contract is fixed and because the user can derive magnitude from the existing X/Y/Z columns themselves

If a future Phase finds a real perf problem here, then we can add a SQL projection in `SensorSampleDao`. We are not doing it now.

---

## 5. Sensor Magnitude Strategy

### Defaults

- New `Config.sensorDisplayMode`, default `"MAGNITUDE"` (matches the user's stated preference).
- Persisted via `SharedPreferences` — same pattern as every other `Config` field (`config.privacyModeEnabled`, etc.).
- New `Constants.kt` keys: `SENSOR_DISPLAY_MODE`, `SENSOR_DISPLAY_MODE_MAGNITUDE`, `SENSOR_DISPLAY_MODE_AXES`.

### Render behaviour

| Surface | `MAGNITUDE` mode | `AXES` mode |
|---|---|---|
| `EventFeedActivity` sensor list row | columns: type · magnitude | columns: type · X · Y · Z (today's behaviour) |
| `DiagnosticsActivity` gyro section | one progress bar (magnitude) | three bars (X / Y / Z, today's behaviour) |
| `DiagnosticsActivity` accel section | one progress bar (magnitude) | three bars (X / Y / Z, today's behaviour) |

Both layouts keep all the per-axis views inflated, controlled by `View.GONE` / `View.VISIBLE` on a parent group. No layout re-inflation; flipping the mode is a single visibility flip + one `notifyDataSetChanged()` on the sensor adapter.

### Sensor magnitude bar math (Diagnostics)

The existing per-axis bar math is:
- Gyro: `((v + 10f) / 20f * 100).toInt().coerceIn(0, 100)` (range ±10 rad/s, centred at 50)
- Accel: `(v / 20f * 100).toInt().coerceIn(0, 100)` (range 0–20 m/s²)

For magnitude, a non-negative scalar, the gyro mapping changes to one-sided:
- Gyro magnitude: `(magnitude / 10f * 100).toInt().coerceIn(0, 100)` (range 0–10 rad/s)
- Accel magnitude: `(magnitude / 20f * 100).toInt().coerceIn(0, 100)` (range 0–20 m/s², unchanged)

These ranges are pragmatic, not calibrated. The same numeric label below the bar (`"%.2f"`) is reused.

### Toolbar action

Single icon button on the toolbar of both screens. Icon flips between two states:
- `MAGNITUDE` → show a "split" / "branches" icon hinting at "tap to see X / Y / Z"
- `AXES` → show a "merge" / "single bar" icon hinting at "tap to see magnitude"

The action's `contentDescription` updates with the mode. Tap → flip `Config.sensorDisplayMode` → re-render. Existing menu items (Refresh on `EventFeedActivity`, Save as CSV on `DiagnosticsActivity`) are kept.

---

## 6. Sub-Phases (4)

Four sub-phases. Each compiles and ships. 4.1–4.3 land as commits; 4.4 closes the branch.

---

### 4.1 Per-Session Stats Query + Helper

**Goal:** All data-layer plumbing for the new header card. No UI changes yet. After this commit, `IkdSessionStatsLoader` is unit-testable in isolation.

#### Deliverables

- `interfaces/SessionStatsRow.kt` — Room POJO (Section 4)
- `interfaces/IkdEventDao.kt` (modified) — **add** `getSessionStats(sessionId)` only. No edits to existing methods.
- `helpers/IkdSessionStatsLoader.kt` — `SessionStats` data class + `suspend fun load(sessionId): SessionStats?` (Section 4)
- `extensions/ContextExt.kt` (modified) — **add** `val Context.ikdSessionStatsLoader: IkdSessionStatsLoader` lazy singleton
- `app/src/test/.../IkdSessionStatsLoaderTest.kt` — JVM unit tests for the pure derivation logic (lift the WPM / error-rate calc into a `Companion.compute(record, statsRow)` function so it's testable without Room, mirroring `IkdAggregator.Companion.buildSnapshot`):
  - Empty session (no events) → `wpm == null`, `errorRatePct == null`, `durationMs` reflects `endedAt - startedAt`
  - In-flight session (`endedAt == null`) → `durationMs == null`, `wpm == null`
  - Sentinel-only IKD rows (`ikd_ms = -1`) → `avgIkdMs == null`
  - 100 events with 7 corrections over 60 s → `wpm = 20.0`, `errorRatePct = 7.0`

#### Acceptance

- `./gradlew test` passes the new `IkdSessionStatsLoaderTest`
- `./gradlew detekt` and `./gradlew lint` produce no regressions vs. existing baselines
- `./gradlew assembleCoreDebug` succeeds
- `IkdDatabase.version` is **still 1** (no migration this phase)

#### Commit

`feat(phase4): add per-session stats query and loader`

---

### 4.2 Session Detail Header Card

**Goal:** The header card renders correctly when `EventFeedActivity` is launched from the Sessions list. Live mode (no `EXTRA_SESSION_ID`) is unchanged.

#### Deliverables

- `res/layout/activity_event_feed.xml` (modified) — add header card view block above the timing section. Structure:
  - Wrap header in a `<LinearLayout android:id="@+id/event_feed_session_header" android:visibility="gone">` so live mode hides it cheaply
  - Two-column rows for each metadata key/value (reuse `SettingsTextLabelStyle` / `SettingsTextValueStyle`; same patterns as `IkdSettingsActivity`)
- `activities/EventFeedActivity.kt` (modified):
  - Inject `ikdSessionStatsLoader`
  - In `loadDataFromDb(sessionId)`, also call `ikdSessionStatsLoader.load(sessionId)` on the same background hop
  - Hide header in live mode (`loadDataFromLiveStore`)
  - On result, populate the header rows; show/hide the header `LinearLayout`
- `res/values/strings.xml` (modified) — add `session_detail_started_at`, `session_detail_ended_at`, `session_detail_duration`, `session_detail_orientation`, `session_detail_locale`, `session_detail_events`, `session_detail_sensors`, `session_detail_wpm`, `session_detail_error_rate`, `session_detail_avg_ikd`, `session_detail_avg_dwell`, `session_detail_avg_flight`, plus the four orientation labels `session_detail_orientation_portrait` etc.
- Lift `formatDuration(ms)` from `SessionsAdapter` to a small top-level helper (e.g., `helpers/IkdFormatters.kt`) and have both call sites use it. This is the only refactor allowed in this sub-phase.

#### Acceptance

- Tapping a session in `SessionsListActivity` opens `EventFeedActivity` with the new header populated and the existing event/sensor lists below
- Sessions with `endedAt == null` render `"—"` for ended-at, duration, and WPM (no crash)
- Sessions with orientation/locale capture disabled (sentinel `-1` / empty) render `"—"`
- Live event log entry from `DiagnosticsActivity` → `EventFeedActivity` does **not** show the header
- All values match the unit-tested loader output
- No edits to any "Forbidden" file from Section 2

#### Commit

`feat(phase4): show session metadata header on detail screen`

---

### 4.3 Sensor Magnitude Toggle (Event Feed + Diagnostics)

**Goal:** Both sensor screens default to magnitude and can flip back to per-axis from a toolbar action. The preference persists across launches.

#### Deliverables

- `helpers/Constants.kt` (modified) — `const val SENSOR_DISPLAY_MODE`, `const val SENSOR_DISPLAY_MODE_MAGNITUDE = "MAGNITUDE"`, `const val SENSOR_DISPLAY_MODE_AXES = "AXES"`
- `helpers/Config.kt` (modified) — `var sensorDisplayMode: String` getter/setter wrapping the pref, default `SENSOR_DISPLAY_MODE_MAGNITUDE`
- Tiny extension on `SensorReadingEvent` — `fun SensorReadingEvent.magnitude(): Float` — colocated with the model
- `res/layout/item_sensor_reading.xml` (modified) — add `sensor_magnitude` column. All four columns kept inflated, visibility flipped per mode.
- `res/layout/activity_diagnostics.xml` (modified) — add one progress-bar row per sensor (`diagnostics_gyro_magnitude_*`, `diagnostics_accel_magnitude_*`). Wrap each per-axis trio in its own container with an id so the visibility flip is one statement.
- `res/menu/menu_event_feed.xml` (modified) — add `event_feed_toggle_sensor_view` action
- `res/menu/menu_diagnostics.xml` (modified) — add `diagnostics_toggle_sensor_view` action
- `res/drawable/ic_view_axes_vector.xml` + `res/drawable/ic_view_magnitude_vector.xml` — two simple vector icons (≤ 30 lines each, follow the existing project style; reuse a Material icon if a close one already exists in the project — search first)
- `activities/EventFeedActivity.kt` (modified):
  - Read `config.sensorDisplayMode` in `onResume`
  - Pass mode to `SensorAdapter`
  - On menu action tap: flip pref, set new mode on adapter, `notifyDataSetChanged()`, swap menu icon + content description
  - `SensorAdapter.bind(...)` populates either the magnitude column or the X/Y/Z columns based on mode
- `activities/DiagnosticsActivity.kt` (modified):
  - Read `config.sensorDisplayMode` in `onResume`; flip visibility on the magnitude rows vs. per-axis rows
  - Add `updateSensorDisplay(event)` magnitude branch: compute `event.magnitude()`, set bar progress, set numeric label
  - On menu action tap: same flip flow as `EventFeedActivity`
- `res/values/strings.xml` (modified) — add `sensor_view_toggle`, `sensor_view_magnitude_cd`, `sensor_view_axes_cd`, `sensor_header_magnitude`

#### Acceptance

- Fresh install opens `EventFeedActivity` (any mode) showing the magnitude column; opens `DiagnosticsActivity` showing the magnitude bars
- Toolbar action flips the mode; choice persists after killing and relaunching the app
- Magnitude is computed in Kotlin (`sqrt(x*x + y*y + z*z)`) — verified by spot-checking three sensor rows in Logcat or with a debug breakpoint
- No DB schema or `@Entity` change
- No edits to any "Forbidden" file from Section 2

#### Commit

`feat(phase4): default sensor view to magnitude with axis toggle`

---

### 4.4 Polish: Empty States, Refresh, Persisted Toggle, Validation

**Goal:** Close the loop. Make the new surfaces feel finished and validate they don't introduce regressions.

#### Deliverables

- Header card empty-state: when the loader returns `null` (session row deleted between list and open), hide the header gracefully, surface a single `MyTextView` "Session not found" instead of a blank card
- Refresh menu on `EventFeedActivity` (existing) re-runs both the events query *and* the stats loader — verify in code
- Detekt + Lint baselines updated only if needed; never with `-PdisableLint`
- `CLAUDE.md` updated with a Phase 4 section mirroring the Phase 3 entry: scope, files, key decisions, capture invariants
- `roadmap/FeatureRoadmap.md` updated to reflect Phase 4 scope and to mark the magnitude / metadata items as no-longer-deferred

#### Acceptance

- All Sub-Phase 4.1–4.3 acceptance items still pass
- StrictMode in debug build does not flag any main-thread DB reads on the session-detail or diagnostics golden paths
- Performance: opening a session detail screen for the largest seeded session (10 k events) loads the header in well under 200 ms on a Pixel 4–class device — measured by `Log.d` in the loader, mirroring `IkdAggregator`'s wall-clock log
- Phase 4 PR description lists the timings and the device used

#### Commit

`docs(phase4): wire scope into CLAUDE.md and feature roadmap`

---

## 7. Files to Create / Modify (vs. forbidden)

### Create (5 files)

| File | Sub-phase |
|---|---|
| `interfaces/SessionStatsRow.kt` | 4.1 |
| `helpers/IkdSessionStatsLoader.kt` | 4.1 |
| `helpers/IkdFormatters.kt` (lifted `formatDuration`) | 4.2 |
| `app/src/test/.../IkdSessionStatsLoaderTest.kt` | 4.1 |
| `res/drawable/ic_view_axes_vector.xml` (+ `ic_view_magnitude_vector.xml` if no Material equivalent) | 4.3 |

### Modify (additive only — no existing logic touched)

| File | Sub-phase | Change |
|---|---|---|
| `interfaces/IkdEventDao.kt` | 4.1 | **Add** `getSessionStats(sessionId)` |
| `extensions/ContextExt.kt` | 4.1 | **Add** `val Context.ikdSessionStatsLoader` |
| `helpers/Constants.kt` | 4.3 | **Add** `SENSOR_DISPLAY_MODE` keys + values |
| `helpers/Config.kt` | 4.3 | **Add** `sensorDisplayMode` property |
| `models/SensorReadingEvent.kt` (or sibling extension file) | 4.3 | **Add** `magnitude()` extension only |
| `activities/EventFeedActivity.kt` | 4.2 / 4.3 | Header card wiring + sensor toggle |
| `activities/DiagnosticsActivity.kt` | 4.3 | Sensor toggle wiring |
| `adapters/SessionsAdapter.kt` | 4.2 | Switch to shared `IkdFormatters.formatDuration` (no behaviour change) |
| `res/layout/activity_event_feed.xml` | 4.2 | Add header `LinearLayout`; existing sections preserved |
| `res/layout/activity_diagnostics.xml` | 4.3 | Add per-sensor magnitude rows; existing rows wrapped in axis containers |
| `res/layout/item_sensor_reading.xml` | 4.3 | Add `sensor_magnitude` column |
| `res/menu/menu_event_feed.xml` | 4.3 | Add toggle action |
| `res/menu/menu_diagnostics.xml` | 4.3 | Add toggle action |
| `res/values/strings.xml` | 4.2 / 4.3 | Add `session_detail_*` and `sensor_view_*` strings |
| `CLAUDE.md` | 4.4 | Phase 4 section |
| `roadmap/FeatureRoadmap.md` | 4.4 | Reflect Phase 4 scope |

### Forbidden (do not touch — see Section 2 for full list)

`SimpleKeyboardIME.kt`, `MyKeyboardView.kt`, `LiveCaptureSessionStore.kt`, `KinematicSensorHelper.kt`, `IkdRetentionWorker.kt`, `IkdDatabase.kt`, all `@Entity` data classes (`IkdEvent`, `SensorSample`, `SessionRecord`), `ClipsDatabase.kt`, `ClipsDao.kt`, `IkdAggregator.kt`, `IkdCsvWriter.kt`.

---

## 8. Acceptance Criteria

The whole phase is done when **all** of these are green on `feat/phase4-session-detail`:

- [ ] `./gradlew assembleCoreDebug` succeeds
- [ ] `./gradlew test` passes (including the new `IkdSessionStatsLoaderTest`)
- [ ] `./gradlew detekt` and `./gradlew lint` produce no regressions vs. existing baselines
- [ ] `IkdDatabase.version` is unchanged (still 1)
- [ ] `helpers/IkdCsvWriter.kt` is unchanged — exported CSV format is identical to Phase 2/3
- [ ] No file in Section 2's "Forbidden edits" list is modified (verifiable via `git diff main..feat/phase4-session-detail --name-only`)
- [ ] Tapping a session in `SessionsListActivity` shows the metadata header with all 11 fields populated correctly (or `"—"` for genuinely missing values)
- [ ] In-flight sessions (no `endedAt`) render `"—"` for duration / WPM without crashing
- [ ] Sensor display defaults to magnitude on a fresh install in both `EventFeedActivity` and `DiagnosticsActivity`
- [ ] Toolbar toggle flips between magnitude and axes; the choice persists across app restart
- [ ] Live event log opened from `DiagnosticsActivity` (no `EXTRA_SESSION_ID`) does **not** show the metadata header
- [ ] StrictMode does not flag any main-thread DB reads on the session-detail or diagnostics golden paths
- [ ] Header loads under 200 ms for the largest test session — measurement in PR description

---

## 9. Decisions

| # | Topic | Decision |
|---|---|---|
| 1 | Branch strategy | All work on `feat/phase4-session-detail`; merged to `main` only after 4.4 |
| 2 | Independence from keyboard layer | Section 2's "Forbidden edits" list is hard. Any required keyboard-side change blocks the PR and reopens scope. |
| 3 | Schema migration | None this phase. `IkdDatabase.version` stays at 1. |
| 4 | Magnitude derivation | Computed in Kotlin per row at read time. Not stored. Not added to CSV. |
| 5 | Default sensor mode | `MAGNITUDE`. User stated preference. |
| 6 | Toggle persistence | `Config.sensorDisplayMode` (`SharedPreferences`). Same pattern as every other setting. |
| 7 | Header card location | Inside `EventFeedActivity` only, gated on `EXTRA_SESSION_ID`. Live mode unchanged. |
| 8 | WPM formula | Phase 3's: `events / 5 * 60_000 / durationMs`. `kpm` from `DiagnosticsActivity` is *not* used here (KPM stays in the live screen). |
| 9 | Stats query shape | One `@Query` returning `SessionStatsRow` (averages + counts in one round-trip). No N+1, no in-Kotlin folding of raw rows. |
| 10 | CSV format | Frozen. Magnitude is a UI concern, not a data-contract concern. |
| 11 | `IkdAggregator` | Not edited. New stats helper is a sibling, not a method on the existing aggregator — keeps Phase 3's surface stable and isolates per-session scope from per-bucket scope. |
| 12 | Cache invalidation | Re-run on `onResume` and Refresh menu tap. No `IkdDataChangedBus`, no observers — same as Phase 3. |
| 13 | Toggle scope | Sensor display toggle applies in both `EventFeedActivity` and `DiagnosticsActivity`. Both surfaces show the same shape of data; one preference covers both. |

---

## 10. Explicitly Deferred to Phase 5

These are tempting Phase 4 scope creep. Listing them so we don't relitigate scope mid-phase.

- Per-session sparkline / mini-chart on the detail screen (rolling IKD by minute)
- App-context capture (which app the session was typed into) — privacy implications reopen the Phase 2 disclaimer scope
- Magnitude column added to CSV export — keep CSV stable
- "Compare to baseline" strings on the header (e.g., "▲ 12 ms slower than your week average") — needs the rolling-baseline work that's already deferred from Phase 3
- Mood / energy overlay row — still pending the `mood_entries` schema migration deferred from Phase 3
- Per-session export from inside `EventFeedActivity` (currently lives only in `SessionsListActivity` long-press)
- Session note / annotation field
- A "view as table" mode for the timing event list (timing + computed WPM column inline)
- Heatmap / circadian charts (already deferred from Phase 3)
- ISO-8601 week boundaries (already deferred from Phase 3)

Anything from this list earns its own focused mini-plan in `roadmap/Phase5/` if and when the user wants it.
