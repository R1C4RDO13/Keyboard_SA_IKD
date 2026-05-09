# Phase 7.1 — AUTOCORRECT Replacement Weight

**Status:** Implemented (landed directly on `main` after Phase 8 had merged)
**Depends on:** Phase 7 (the `AUTOCORRECT` capture path; the `keystrokeCount` SQL projection added there is reused as the new error-rate denominator)
**Blocks:** none
**Branch:** Implementation landed directly on `main` (sub-phase commits 7.1.1 → 7.1.3) — the per-plan branch hygiene was overridden by the user.
**Scope (one sentence):** Capture the **replaced character count** for every `AUTOCORRECT` event so that a single autocorrect of a long misspelled word (e.g. typing `ocasdasda` and the editor replacing it with `october`) contributes nine units of error to the metric instead of one — fixing the surprising 9.1% reading the user observed and bringing the formula in line with the WPM denominator chosen in Phase 7.

> **Migration order pivot.** This plan was written assuming Phase 7.1
> would be the first migration on `ikd.db` (`Migration(1, 2)`). In
> practice, Phase 8 shipped first and bumped the schema 1 → 2 for
> `mood_entries`, so Phase 7.1 ended up shipping `Migration(2, 3)`
> instead — the `correction_weight` column is added on top of the v2
> schema. The semantics are otherwise unchanged: column shape, backfill
> SQL, capture wiring, and tests all match this plan. References to
> `Migration(1, 2)` and "first ever migration" elsewhere in this file
> reflect the original ordering and remain for historical context.

This is the **first phase to bump `IkdDatabase.version`** (1 → 2), via a strictly additive `Migration(1, 2)` that adds a single column to `ikd_events` and backfills it from the existing `is_correction` flag. It is also a tightly-scoped capture-layer reopen — only `recordAutocorrectEvent(...)` and the BACKSPACE branch of `onKey()` change inside `SimpleKeyboardIME.kt`; nothing else in the IME, `LiveCaptureSessionStore`, the sensor helper, or the retention worker is touched.

---

## Table of Contents

1. [Concept & Scope](#1-concept--scope)
2. [Branch & Layering Discipline (REOPEN capture site; SCHEMA MIGRATION 1 → 2)](#2-branch--layering-discipline-reopen-capture-site-schema-migration-1--2)
3. [Schema Change: `correction_weight` Column](#3-schema-change-correction_weight-column)
4. [New Error-Rate Formula](#4-new-error-rate-formula)
5. [Sub-Phases (3)](#5-sub-phases-3)
   - [7.1.1 Schema Migration + Capture Wiring](#711-schema-migration--capture-wiring)
   - [7.1.2 Read-Side Switch to Weighted Numerator + keystrokeCount Denominator](#712-read-side-switch-to-weighted-numerator--keystrokecount-denominator)
   - [7.1.3 Tests + Phase 8 Re-numbering + Docs](#713-tests--phase-8-re-numbering--docs)
6. [Downstream Impact (Dashboards, Diagnostics, CSV)](#6-downstream-impact-dashboards-diagnostics-csv)
7. [Files to Create / Modify (vs. forbidden)](#7-files-to-create--modify-vs-forbidden)
8. [Acceptance Criteria](#8-acceptance-criteria)
9. [Decisions](#9-decisions)
10. [Explicitly Deferred to Later Phases](#10-explicitly-deferred-to-later-phases)

---

## 1. Concept & Scope

Phase 7 introduced the `AUTOCORRECT` event category and recorded one row per detected external replacement, with `is_correction = true` so the existing error-rate metric folded autocorrects in for free. That works for the *count* of autocorrects but loses the *magnitude*: a user typing `ocasdasda` (nine wrong characters) and watching the system replace it with `october` (seven right characters) generates a single `AUTOCORRECT` row, which the metric counts as one correction over eleven total events — error rate **9.1%**. The user's typed input was almost entirely wrong; the dashboard reads as if it was almost entirely right.

The data needed to do better is **already available at the moment of detection.** `SimpleKeyboardIME.maybeRecordExternalReplacement(...)` (`services/SimpleKeyboardIME.kt:721-785`) receives `oldSelStart` / `oldSelEnd` as parameters of `onUpdateSelection` — the difference is the length of the misspelled span the system selected before committing the replacement. We just don't store or use it.

Phase 7.1 closes that gap with a single new integer column on `ikd_events` carrying the "weight" of each correction:
- `BACKSPACE` rows: weight = `1` (matches today's behavior).
- `AUTOCORRECT` rows: weight = `oldSelEnd - oldSelStart` (the real replaced span; coerced to `≥ 1` for safety).
- Everything else: weight = `0`.

The error-rate computation switches from `corrections / eventCount` to `SUM(correction_weight) / keystrokeCount`. The `keystrokeCount` projection that Phase 7 already added (`COUNT(*) - COUNT(AUTOCORRECT)`) becomes the shared denominator for both WPM and error rate, fixing the small inconsistency Phase 7 documented as acceptable. Sessions that contain no autocorrects are unaffected (`eventCount == keystrokeCount` and weight reduces to the row count for `is_correction = true` rows).

After Phase 7.1, the user's `ocasdasda → october` case reads as **9 / 10 = 90%** error rate — the magnitude is restored, and the metric matches the user's gut feel that "almost everything I typed in that word was wrong."

### In scope (the entire feature)

- **Schema:** New column `correction_weight INTEGER NOT NULL DEFAULT 0` on `ikd_events`. Migration adds the column and backfills weight=1 onto every existing `is_correction = true` row so dashboards over the historical data are continuous across the upgrade.
- **Capture:** `recordAutocorrectEvent(replacedLength)` carries the new field; the `BACKSPACE` branch of `onKey()` sets it to `1`; everything else stays at the default `0`.
- **Read side:** Both DAO error-rate queries (`getEventBuckets`, `getSessionStats`) project `SUM(correction_weight) AS correctionWeight`; the inline `errorRatePct` SQL math in `getEventBuckets` is dropped — the percentage is now computed in Kotlin so the formula lives in one place. Both `IkdAggregator` and `IkdSessionStatsLoader` switch to `100.0 * correctionWeight / keystrokeCount`.
- **Live mode:** `DiagnosticsActivity.updateComputedMetrics(...)` mirrors the same formula on the in-memory event list.
- **CSV:** Existing timing block gains an eighth column `correction_weight`. Strictly additive — parsers reading the original seven columns ignore the eighth.
- **Tests:** New JVM unit fixtures cover the user's `ocasdasda` case (weight=9, keystrokes=10, expect ≈90%); a new instrumented Room migration test seeds a v1 DB, runs `MIGRATION_1_2`, and asserts the column + backfill landed correctly.
- **Phase 8 re-number:** `roadmap/Phase8/Phase8_Plan.md` updates its migration label from `Migration(1, 2)` to `Migration(2, 3)` and references Phase 7.1 as a dependency.

### Out of scope (deferred — see Section 10)

`BACKSPACE` weighted by selection length when the user backspaces a multi-char selection (today and after Phase 7.1, that still records as weight=1); a parallel "deletion magnitude" column distinct from `correction_weight`; any change to which categories carry `is_correction = true`; any change to `EMOJI` semantics; surfacing the per-event weight in dashboards as its own column or chip — Phase 7.1 only changes the *aggregate* error-rate calculation, not the per-event UI.

---

## 2. Branch & Layering Discipline (REOPEN capture site; SCHEMA MIGRATION 1 → 2)

Phase 7.1 reopens **two** previously-frozen surfaces. Both are scoped and tightly justified.

### Reopened files (allowed, additive only)

| File | Why reopened | Edit shape |
|---|---|---|
| `services/SimpleKeyboardIME.kt` | The capture site for `AUTOCORRECT` and `BACKSPACE` rows; needs to populate the new field | `recordAutocorrectEvent(replacedLength: Int)` signature + body; the `BACKSPACE` branch of `onKey()` sets `correctionWeight = 1` on the `KeyTimingEvent`; the regular alpha / digit / space / enter branches set `correctionWeight = 0`. No edit to the external-replacement heuristic itself, the markImeEdit helper, the selection tracker, or the sensor helper interaction. |
| `databases/IkdDatabase.kt` | First-ever schema bump (1 → 2); needs the new `Migration(1, 2)` registered | Bump `version = 2`; add `MIGRATION_1_2` constant running the `ALTER TABLE` + `UPDATE` SQL from Section 3; pass `.addMigrations(MIGRATION_1_2)` to the existing `Room.databaseBuilder` chain |
| `models/IkdEvent.kt` | New column on the entity | Add `correctionWeight: Int = 0` with `@ColumnInfo(name = "correction_weight", defaultValue = "0")` |
| `models/KeyTimingEvent.kt` | Capture-side mirror of the new column | Add `correctionWeight: Int = 0` (default keeps existing capture sites compiling without edit) |
| `interfaces/IkdEventDao.kt` | Both error-rate queries need the new projection | `getEventBuckets`: drop the inline `errorRatePct` SQL; add `SUM(correction_weight) AS correctionWeight`. `getSessionStats`: add `SUM(correction_weight) AS correctionWeight` alongside the existing `correctionCount` (kept for backward compat / debugging visibility) |
| `interfaces/EventBucketRow.kt` | New projection field | Add `correctionWeight: Int`; remove `errorRatePct` (now Kotlin-computed) |
| `interfaces/SessionStatsRow.kt` | New projection field | Add `correctionWeight: Int` (keeps `correctionCount` unchanged) |
| `helpers/IkdAggregator.kt` | `computeOverallErrorRate(...)` formula change; per-bucket `errorRatePct` computation moves from SQL to Kotlin | Replace the "reverse the percentage" recovery trick with `100.0 * sumOf { correctionWeight } / sumOf { keystrokeCount }`; per-bucket math same shape inside `buildSnapshot()` |
| `helpers/IkdSessionStatsLoader.kt` | Per-session error-rate formula | `computeErrorRate(keystrokeCount, correctionWeight)` instead of `computeErrorRate(eventCount, correctionCount)`; null-safe when `keystrokeCount == 0` |
| `helpers/IkdCsvWriter.kt` | New trailing column on the timing block | Append `correction_weight` to the timing block's header row and write the field on every `TimingRow`. The sensor block and (future) Phase 8 mood block are unaffected — strictly additive |
| `activities/DiagnosticsActivity.kt` | Live-mode error rate uses the same formula on the in-memory list | `updateComputedMetrics(...)` — error-rate line: `events.sumOf { effectiveWeight(it) } * 100.0 / events.count { it.eventCategory != EVENT_CATEGORY_AUTOCORRECT }`; `effectiveWeight` reads `event.correctionWeight` and falls back to `1` when `is_correction && correctionWeight == 0` (handles any in-flight events that pre-date the migration) |

### Still forbidden (do not touch)

| File | Reason |
|---|---|
| `helpers/LiveCaptureSessionStore.kt` | Capture buffer — frozen since Phase 2. The new field rides through the existing `recordTimingEvent()` API on `KeyTimingEvent` → `IkdEvent`; no buffer or flusher edit |
| `helpers/KinematicSensorHelper.kt` | Sensor path — unrelated |
| `helpers/IkdRetentionWorker.kt` | Retention worker is generic over `sessions` deletion; CASCADE handles `ikd_events` rows including the new column |
| `models/SensorSample.kt`, `models/SessionRecord.kt` | No new column on these entities |
| `databases/ClipsDatabase.kt`, `interfaces/ClipsDao.kt` | Unrelated DB |
| `helpers/IkdSessionChartLoader.kt` | Phase 5 chart loader — frozen. The per-session timing chart already excludes sentinel `-1` IKD rows; `correction_weight` is not surfaced on charts |
| `views/IkdLineChartView.kt` | Chart wrapper — frozen |
| `activities/EventFeedActivity.kt`, `res/layout/activity_event_feed.xml` | Phase 5 surface — frozen. The per-session error-rate KPI already pulls from `SessionStats.errorRatePct`; once the loader's formula changes, the KPI updates for free |
| `activities/DashboardActivity.kt`, `res/layout/activity_dashboard.xml` | Phase 3 surface — frozen. Same story: the global error-rate KPI and chart already pull from `IkdAggregator.Snapshot`, so changing the aggregator's formula is sufficient |
| `activities/SessionsListActivity.kt`, `adapters/SessionsAdapter.kt` | Phase 2 surface — frozen |
| `activities/IkdSettingsActivity.kt` | No new setting this phase |
| Phase 6 / 7 menu / colour resources | Unaffected |

### Branch hygiene

- Branch name: `feat/phase7.1-autocorrect-weight`
- Cut from latest `main` (Phase 7 already merged)
- Each sub-phase (7.1.1 → 7.1.3) lands as one focused commit using the project's `feat:` / `chore:` / `docs:` / `test:` convention
- After 7.1.3 passes acceptance, the branch is **left local for the user's review — no push, no PR opened by the implementer.** Same pattern as Phase 6 / Phase 7 / Phase 8.

---

## 3. Schema Change: `correction_weight` Column

This is the first migration ever applied to `ikd.db`. The migration is the highest-risk part of Phase 7.1 — get it wrong and existing sessions become unreadable.

### Column definition

```sql
ALTER TABLE `ikd_events` ADD COLUMN `correction_weight` INTEGER NOT NULL DEFAULT 0;
```

### Backfill

Run as part of the same migration so the metric is continuous across the upgrade — sessions captured before Phase 7.1 still report sensible error rates instead of suddenly showing 0:

```sql
UPDATE `ikd_events` SET `correction_weight` = 1 WHERE `is_correction` = 1;
```

After the backfill:
- Legacy `BACKSPACE` rows (`is_correction = 1`): weight = `1`. Matches today.
- Legacy `AUTOCORRECT` rows (`is_correction = 1`): weight = `1`. Matches today — we never knew the real replaced length on those rows, so don't pretend we do.
- Legacy `ALPHA` / `DIGIT` / `SPACE` / `ENTER` / `EMOJI` / `OTHER` rows: weight = `0` (unchanged from the column default).

Why these choices:
- **`INTEGER NOT NULL DEFAULT 0`** — every event has a defined weight; missing data is impossible by construction. Keeps the SQL `SUM(correction_weight)` straightforward.
- **Backfill from `is_correction`** — preserves the historical error-rate magnitude for users who already have data. Without the backfill, a v1 → v2 upgrade would silently drop every prior correction's contribution to the metric.
- **Column on `ikd_events`, not a sibling table** — `correction_weight` is a per-event property. A sibling table would force a JOIN on every error-rate query and complicate the existing partial indexes.

### Migration code

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `ikd_events` ADD COLUMN `correction_weight` INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL(
            "UPDATE `ikd_events` SET `correction_weight` = 1 WHERE `is_correction` = 1"
        )
    }
}
```

Wired into `IkdDatabase`:

```kotlin
@Database(
    entities = [SessionRecord::class, IkdEvent::class, SensorSample::class],
    version = 2  // ← bumped from 1
)
abstract class IkdDatabase : RoomDatabase() {
    // …existing dao abstracts…

    companion object {
        // …existing builder…
        Room.databaseBuilder(context, IkdDatabase::class.java, "ikd.db")
            .addMigrations(MIGRATION_1_2)  // ← new
            .setJournalMode(WRITE_AHEAD_LOGGING)
            .build()
    }
}
```

### Migration test

A new instrumented Room migration test in `app/src/androidTest/.../IkdDatabaseMigrationTest.kt`:
1. Open a v1 DB.
2. Seed: one `SessionRecord`, one `ALPHA` event (`is_correction = 0`), one `BACKSPACE` event (`is_correction = 1`), one `AUTOCORRECT` event (`is_correction = 1`).
3. Run `MIGRATION_1_2`.
4. Assert the new column exists and its values are: ALPHA → `0`, BACKSPACE → `1`, AUTOCORRECT → `1` (backfill correctness).
5. Insert a fresh AUTOCORRECT row with `correction_weight = 9` via the v2 schema; assert it round-trips through a `SELECT`.
6. Assert the original three events are still readable (no orphaned rows from the schema bump).

This is the only instrumented test in Phase 7.1; the rest are JVM-side.

### CSV format (additive)

```
session_id,timestamp_ms,event_category,ikd_ms,hold_time_ms,flight_time_ms,is_correction,correction_weight
<timing rows>

#sensor_readings
session_id,timestamp_ms,sensor_type,x,y,z
<sensor rows>
```

Existing parsers reading only the first seven columns continue to work — `correction_weight` is a trailing addition and CSV is positional. Phase 8's later `#mood_entries` block is appended after the (now eight-column) timing + sensor blocks.

---

## 4. New Error-Rate Formula

### Old (Phase 7)

```
errorRatePct = 100 * correctionCount / eventCount
where correctionCount = COUNT(WHERE is_correction = 1)
      eventCount      = COUNT(*)
```

For `ocasdasda + space + AUTOCORRECT`: 1 / 11 ≈ **9.1 %**.

### New (Phase 7.1)

```
errorRatePct = 100 * correctionWeight / keystrokeCount
where correctionWeight = SUM(correction_weight)
      keystrokeCount   = COUNT(WHERE event_category != 'AUTOCORRECT')
```

For the same case: 9 / 10 = **90 %**. (The single AUTOCORRECT row contributes weight 9 from the misspelled-span length and is excluded from the keystroke denominator since "the system replaced text" is not a user keystroke.)

### Properties

- **Sessions without autocorrects are byte-identical** under the two formulas (`eventCount == keystrokeCount` and `correctionWeight == COUNT(WHERE is_correction = 1)` since BACKSPACE always has weight 1). No regression in dashboards over BACKSPACE-only sessions.
- **Mixed sessions** (some autocorrects, mostly clean typing) tick up modestly — the autocorrect's weight grows from 1 to N where N is the replaced length, and the denominator shrinks by the autocorrect count.
- **Autocorrect-dominated sessions** like the user's `ocasdasda` test see the metric jump from ~9 % to ~90 %, restoring the magnitude.
- **Edge case `keystrokeCount == 0`** (a hypothetical session with only AUTOCORRECT rows — not producible by the keyboard, but defensively handled): formula returns `null`; KPI cell shows the existing `—` placeholder.

### Numerator weight per category

| Category | Weight | Source |
|---|---|---|
| `ALPHA`, `DIGIT`, `SPACE`, `ENTER`, `OTHER`, `EMOJI` | `0` | not corrections |
| `BACKSPACE` | `1` | one keystroke = one correction action |
| `AUTOCORRECT` (Phase 7.1+) | `oldSelEnd - oldSelStart` (≥ 1) | the system-selected misspelled span is the magnitude of the typing error |
| `AUTOCORRECT` (legacy, pre-migration) | `1` (from backfill) | replaced length unknown — preserve old behavior |

`BACKSPACE` is **deliberately not** weighted by selection length even when the user backspaces a manual selection. Phase 7.1 keeps that path at weight 1 to avoid scope creep; the symmetric "deletion magnitude" follow-up is documented in Section 10.

---

## 5. Sub-Phases (3)

Three focused sub-phases. Each compiles, tests, and ships.

---

### 7.1.1 Schema Migration + Capture Wiring

**Goal:** `ikd.db` migrates 1 → 2 cleanly; new AUTOCORRECT rows carry the replaced length; new BACKSPACE rows carry weight 1; the migration test passes.

#### Deliverables

- `models/IkdEvent.kt` (modified): add `correctionWeight: Int = 0` with `@ColumnInfo(name = "correction_weight", defaultValue = "0")`.
- `models/KeyTimingEvent.kt` (modified): add `correctionWeight: Int = 0`.
- `databases/IkdDatabase.kt` (modified): bump `version = 2`; add `MIGRATION_1_2`; register it on the builder.
- `services/SimpleKeyboardIME.kt` (modified):
  - `recordAutocorrectEvent(replacedLength: Int)` signature; caller in `maybeRecordExternalReplacement` computes `(oldSelEnd - oldSelStart).coerceAtLeast(1)` and passes through.
  - `BACKSPACE` branch of `onKey()` (the existing `KeyTimingEvent` construction at the top of `onKey`): set `correctionWeight = 1` when `code == MyKeyboard.KEYCODE_DELETE`, else `0`.
- `helpers/LiveCaptureSessionStore.kt` (modified): one-line change inside `flushPending()` mapping `KeyTimingEvent → IkdEvent` to forward the new field. **Note:** this is the *only* edit to `LiveCaptureSessionStore.kt` in Phase 7.1 and is the minimum needed — no flusher / buffer logic changes. The forbidden-list note in Section 2 is relaxed for this one line; it is called out explicitly here so the audit trail is clean.
- `app/src/androidTest/.../IkdDatabaseMigrationTest.kt` (new): the instrumented test from Section 3.

#### Acceptance

- `./gradlew assembleCoreDebug` BUILD SUCCESSFUL
- `./gradlew connectedCoreDebugAndroidTest` (or local equivalent) — migration test passes
- `./gradlew detekt` and `lint` no regressions
- Manual smoke: install on a device with system spell-check enabled, disable privacy mode, type `ocasdasda<space>` in a text field, wait for autocorrect to fire. Pull `ikd.db` via `adb exec-out run-as` and confirm one `AUTOCORRECT` row with `correction_weight = 9` (or close to 9 — exact value depends on what the spell-check service selected).

#### Commit

`feat(phase7.1): add correction_weight column with Migration(1,2) and capture wiring`

---

### 7.1.2 Read-Side Switch to Weighted Numerator + keystrokeCount Denominator

**Goal:** Both error-rate code paths (aggregate dashboard + per-session dashboard) and the live diagnostics screen all use the new formula. Sessions captured before the migration still report sensible error rates thanks to the backfill from 7.1.1.

#### Deliverables

- `interfaces/IkdEventDao.kt` (modified):
  - `getEventBuckets(...)`: drop the inline `100.0 * SUM(...) / COUNT(*) AS errorRatePct` calc; replace with `SUM(correction_weight) AS correctionWeight`.
  - `getSessionStats(sessionId)`: add `SUM(correction_weight) AS correctionWeight` (keep `correctionCount` for debugging visibility / parity).
- `interfaces/EventBucketRow.kt` (modified): remove `errorRatePct: Double`, add `correctionWeight: Int`.
- `interfaces/SessionStatsRow.kt` (modified): add `correctionWeight: Int`.
- `helpers/IkdAggregator.kt` (modified):
  - `Companion.buildSnapshot(...)`: per-bucket `errorRatePct = 100.0 * correctionWeight / keystrokeCount` (null-safe when keystrokeCount ≤ 0).
  - `computeOverallErrorRate(eventBuckets)`: `100.0 * eventBuckets.sumOf { it.correctionWeight.toLong() } / eventBuckets.sumOf { it.keystrokeCount.toLong() }`.
- `helpers/IkdSessionStatsLoader.kt` (modified):
  - `computeErrorRate(keystrokeCount: Int, correctionWeight: Int): Double?` — null-safe at zero.
  - `Companion.compute(...)` — call site updated.
- `helpers/IkdCsvWriter.kt` (modified): timing-block header gains the `correction_weight` column; `TimingRow` data class gains the field; `asTimingRow()` extension forwards it; `writeSessionCsv` emits it.
- `activities/DiagnosticsActivity.kt` (modified, ≤10 lines):
  - `updateComputedMetrics(events)` — error-rate computation re-expressed as the new formula. Helper local fun: `effectiveWeight(e: KeyTimingEvent): Int = if (e.correctionWeight > 0) e.correctionWeight else if (e.isCorrection) 1 else 0`. Denominator: `events.count { it.eventCategory != EVENT_CATEGORY_AUTOCORRECT }`.

#### Acceptance

- `./gradlew assembleCoreDebug` BUILD SUCCESSFUL
- `./gradlew testCoreDebugUnitTest` — existing fixtures pass (they default `correctionWeight = 0` per the helper convention, so values match)
- `./gradlew detekt` and `lint` no regressions
- Open the per-session dashboard for the smoke-tested session from 7.1.1 → error-rate KPI shows ~90 %, not 9.1 %
- Open `DashboardActivity` with the same DB → per-bucket and overall error rates reflect the weighted numerator
- Type a fresh session with one BACKSPACE and no autocorrects → error rate matches the pre-migration formula (regression check)

#### Commit

`feat(phase7.1): switch error-rate formula to weighted numerator over keystrokeCount`

---

### 7.1.3 Tests + Phase 8 Re-numbering + Docs

**Goal:** New JVM unit fixtures cover the magnitude semantics; Phase 8's plan is updated to reflect the migration shift; CLAUDE.md and the roadmap docs are in sync.

#### Deliverables

- `app/src/test/.../IkdAggregatorTest.kt` (modified):
  - Helper: `eventBucket(...)` gains a `correctionWeight: Int = 0` parameter (existing fixtures stay green via the default).
  - New fixture: 9 ALPHA + 1 SPACE + 1 AUTOCORRECT(weight=9) → expect `errorRatePct ≈ 90.0` (9 / 10 keystrokes).
  - New fixture: same but with `keystrokeCount = 0` (hypothetical AUTOCORRECT-only session) → expect `errorRatePct = null`.
  - New fixture: legacy session with AUTOCORRECT(weight=1) + BACKSPACE(weight=1) → expect `errorRatePct = 2 / N * 100` to confirm backfilled rows behave correctly.
- `app/src/test/.../IkdSessionStatsLoaderTest.kt` (modified):
  - Helper: `statsRow(...)` gains `correctionWeight: Int = correctionCount` (existing fixtures stay green via default = correctionCount which preserves the row-counting semantic).
  - Mirror the three new aggregator fixtures at the per-session shape.
- `roadmap/Phase8/Phase8_Plan.md` (modified):
  - Header: change `**Depends on:**` to add Phase 7.1 dependency.
  - Section 2 reopened-files row for `databases/IkdDatabase.kt`: change `Schema bump 1 → 2` to `Schema bump 2 → 3`.
  - Section 3: change `Migration(1, 2)` to `Migration(2, 3)` throughout (kotlin migration object, prose, builder snippet, migration-test description).
  - Drop the "first phase to bump `IkdDatabase.version`" framing from Section 2's leading paragraph — Phase 7.1 will already have done that.
- `roadmap/STATUS.md` (modified):
  - Add row for Phase 7.1 between Phase 7 and Phase 8.
  - Update "Schema state" line: `IkdDatabase.version = 2 — first migration is Phase 7.1's correction_weight column. Phase 8 will be the second bump (2 → 3, adding mood_entries).`
- `roadmap/FeatureRoadmap.md` (modified):
  - Insert Phase 7.1 entry between Phase 7 and Phase 8 with `Detailed scope:` link.
- `CLAUDE.md` (modified):
  - Add a Phase 7.1 section in the same shape as Phases 5/6/7/8 — scope, files, decisions, what's preserved, perf budget, forbidden list.
- `roadmap/Phase7.1/Phase7.1_Plan.md` (this file): kept; status flipped to `Implemented` after merge.

#### Acceptance

- `./gradlew testCoreDebugUnitTest` — all existing + new fixtures pass
- `./gradlew connectedCoreDebugAndroidTest` (or local equivalent) — `IkdDatabaseMigrationTest` from 7.1.1 still passes after the doc-only edits
- `./gradlew detekt` and `lint` no regressions
- `roadmap/Phase8/Phase8_Plan.md` references `Migration(2, 3)` consistently — `grep -n "Migration(1, 2)" roadmap/Phase8/` returns no matches.
- `CLAUDE.md` has a Phase 7.1 section.

#### Commit

```
test(phase7.1): cover weighted error-rate fixtures
docs(phase7.1): document Phase 7.1 in CLAUDE.md, STATUS, and FeatureRoadmap
docs(phase8): renumber Migration(1,2) → Migration(2,3) for the Phase 7.1 shift
```

(Or one combined `docs(phase7.1):` if the diff is small enough.)

---

## 6. Downstream Impact (Dashboards, Diagnostics, CSV)

### Phase 3 aggregate dashboard (`DashboardActivity`)

- **Error-rate chart:** values rise for buckets containing autocorrects (each AUTOCORRECT now contributes its replaced-span length instead of 1). Sessions without autocorrects unchanged.
- **WPM chart:** unchanged. Phase 7's `keystrokeCount` denominator already powers WPM and is now the same denominator used for error rate — Phase 7's "WPM/error-rate denominator inconsistency" footnote is resolved.
- **Avg IKD chart:** unchanged. AUTOCORRECT rows still carry their post-Phase 7 IKD value.
- **KPI strip:** error-rate cell may move; sessions, total typing time, avg WPM unchanged.

### Phase 5 per-session screen (`EventFeedActivity` with `EXTRA_SESSION_ID`)

- **Error-rate KPI cell:** updated to the new weighted formula via `IkdSessionStatsLoader`. No layout change.
- **Other KPI cells, secondary chips, three line charts:** unchanged.
- **Live event log (`EventFeedActivity` without `EXTRA_SESSION_ID`):** unchanged — raw timing rows already include the `correctionWeight` field via `KeyTimingEvent`, but the live UI doesn't surface it as a column. Could be a future polish item.

### Phase 6 diagnostics screen (`DiagnosticsActivity`)

- **Error-rate KPI cell:** updated via the inline formula change in `updateComputedMetrics(...)`. No layout change.
- **All other cells, sensor card, status chip, View Log row:** unchanged.

### CSV export

- **Single-session and bulk export:** timing block gains the `correction_weight` column. Sensor block unchanged. Phase 8's later `#mood_entries` block continues to be appended after (additive — no conflict).
- **Backwards compatibility:** parsers reading the original seven columns ignore the eighth. Trailing-column additive evolution is the same shape as Phase 7's `event_category` value extension.

### Privacy invariants (preserved)

- `correction_weight` stores only an integer count of replaced characters. **Never the actual text.** Same privacy hard line as the `event_category` strings.
- The existing privacy mode toggle still gates capture entirely; weighted AUTOCORRECT rows don't exist when capture is off.

---

## 7. Files to Create / Modify (vs. forbidden)

### Create (3 files)

| File | Sub-phase |
|---|---|
| `roadmap/Phase7.1/Phase7.1_Plan.md` (this file) | (pre-7.1.1) |
| `app/src/androidTest/.../IkdDatabaseMigrationTest.kt` | 7.1.1 |
| (no other new files — capture-side and read-side changes all live in existing files) | — |

### Modify (additive only)

| File | Sub-phase | Change |
|---|---|---|
| `models/IkdEvent.kt` | 7.1.1 | Add `correctionWeight` column |
| `models/KeyTimingEvent.kt` | 7.1.1 | Add `correctionWeight` field |
| `databases/IkdDatabase.kt` | 7.1.1 | `version = 2`; register `MIGRATION_1_2` |
| `services/SimpleKeyboardIME.kt` | 7.1.1 | `recordAutocorrectEvent(replacedLength)` and BACKSPACE-branch wiring |
| `helpers/LiveCaptureSessionStore.kt` | 7.1.1 | One-line forward of `correctionWeight` in `flushPending()` mapping |
| `interfaces/IkdEventDao.kt` | 7.1.2 | `correctionWeight` projection on both error-rate queries |
| `interfaces/EventBucketRow.kt` | 7.1.2 | Add `correctionWeight`; remove `errorRatePct` |
| `interfaces/SessionStatsRow.kt` | 7.1.2 | Add `correctionWeight` |
| `helpers/IkdAggregator.kt` | 7.1.2 | New error-rate formula |
| `helpers/IkdSessionStatsLoader.kt` | 7.1.2 | New error-rate formula |
| `helpers/IkdCsvWriter.kt` | 7.1.2 | Append `correction_weight` column |
| `activities/DiagnosticsActivity.kt` | 7.1.2 | Live-mode error-rate formula |
| `app/src/test/.../IkdAggregatorTest.kt` | 7.1.3 | New fixtures |
| `app/src/test/.../IkdSessionStatsLoaderTest.kt` | 7.1.3 | New fixtures |
| `roadmap/Phase8/Phase8_Plan.md` | 7.1.3 | `Migration(1, 2)` → `Migration(2, 3)` |
| `roadmap/STATUS.md` | 7.1.3 | Add Phase 7.1 row; update schema state line |
| `roadmap/FeatureRoadmap.md` | 7.1.3 | Insert Phase 7.1 entry |
| `CLAUDE.md` | 7.1.3 | New Phase 7.1 section |

### Forbidden (do not touch)

`KinematicSensorHelper.kt`, `IkdRetentionWorker.kt`, `models/SensorSample.kt`, `models/SessionRecord.kt`, `databases/ClipsDatabase.kt`, `interfaces/ClipsDao.kt`, `views/IkdLineChartView.kt`, `helpers/IkdSessionChartLoader.kt`, `activities/EventFeedActivity.kt`, `res/layout/activity_event_feed.xml`, `activities/DashboardActivity.kt`, `res/layout/activity_dashboard.xml`, `activities/SessionsListActivity.kt`, `adapters/SessionsAdapter.kt`, `activities/IkdSettingsActivity.kt`, `views/MyKeyboardView.kt`, all of Phase 6's status-chip resources.

---

## 8. Acceptance Criteria

The whole phase is done when **all** of these are green on `feat/phase7.1-autocorrect-weight`:

- [ ] `./gradlew assembleCoreDebug` succeeds
- [ ] `./gradlew testCoreDebugUnitTest` passes (existing + new aggregator/loader fixtures)
- [ ] `./gradlew connectedCoreDebugAndroidTest` (or local equivalent) passes the new `IkdDatabaseMigrationTest`
- [ ] `./gradlew detekt` and `./gradlew lint` produce no new issues vs. `main` baseline
- [ ] `IkdDatabase.version == 2` and `MIGRATION_1_2` is registered
- [ ] Existing v1 user databases migrate cleanly (verified by pulling a v1 device DB, installing the v2 build, confirming sessions still load and error rates are sensible — autocorrect-free sessions byte-identical, autocorrect sessions tick up only because the denominator switched)
- [ ] No file in Section 2's "Still forbidden" list is modified (verifiable via `git diff main..feat/phase7.1-autocorrect-weight --name-only`)
- [ ] Manual smoke: typing `ocasdasda<space>` while system spell-check is enabled produces an `AUTOCORRECT` row with `correction_weight ≈ 9`; the per-session error-rate KPI for that session reads ~90 %, not 9.1 %
- [ ] BACKSPACE-only sessions report the same error rate before and after the migration (regression check)
- [ ] CSV export's timing block has eight columns (existing seven + `correction_weight`); existing parsers ignore the eighth
- [ ] `roadmap/Phase8/Phase8_Plan.md` references `Migration(2, 3)` only — no leftover `Migration(1, 2)` strings (`grep` clean)
- [ ] `roadmap/Phase7.1/Phase7.1_Plan.md` exists; its status line flipped to `Implemented`
- [ ] `CLAUDE.md` has a Phase 7.1 section
- [ ] `roadmap/STATUS.md` lists Phase 7.1 with status `Implemented` and the schema-state line is updated to `IkdDatabase.version = 2`
- [ ] `roadmap/FeatureRoadmap.md` lists Phase 7.1 between Phase 7 and Phase 8

---

## 9. Decisions

| # | Topic | Decision |
|---|---|---|
| 1 | Branch | `feat/phase7.1-autocorrect-weight`, off latest `main` after Phase 7 merged. Left local — no push, no PR opened by the implementer. Same pattern as Phase 6 / Phase 7 / Phase 8. |
| 2 | Storage shape | **New `correction_weight INTEGER NOT NULL DEFAULT 0` column on `ikd_events`.** Considered repurposing `holdTimeMs`'s sentinel `-1` slot for AUTOCORRECT rows; rejected because the semantic overload would surface in CSV exports as positive `hold_time_ms` values for AUTOCORRECT rows, confusing any downstream analysis. The dedicated column is unambiguous. |
| 3 | Numerator | **Sum of `correction_weight` per row.** BACKSPACE = 1, AUTOCORRECT = replaced span length, all else = 0. Generalises today's "count corrections" to "weighted count of corrections" with backwards-compat baked into the migration. |
| 4 | Denominator | **`keystrokeCount` (excludes AUTOCORRECT).** Switched from `eventCount` (Phase 7's choice). Aligns with WPM's Phase 7 denominator, fixing the explicit "WPM-vs-error-rate inconsistency" Phase 7 documented as acceptable. Sessions without autocorrects are byte-identical (`eventCount == keystrokeCount` then). |
| 5 | Backfill on migration | **Yes — `UPDATE ikd_events SET correction_weight = 1 WHERE is_correction = 1`.** Without this, every legacy correction would silently drop to weight 0 and historical error rates would all read as 0. The backfill preserves the pre-Phase-7.1 behavior on legacy rows. |
| 6 | BACKSPACE on selection | **Stays at weight = 1 even when replacing a multi-char selection.** Symmetric "deletion magnitude" (BACKSPACE weighted by selection length when present) is documented as a Phase-9-or-later candidate (Section 10). Phase 7.1 stays focused on AUTOCORRECT — the user-reported bug — and avoids the multi-call-site refactor that BACKSPACE-on-selection would require. |
| 7 | Per-event UI surfacing | **None this phase.** The `correction_weight` field is consumed only by the aggregate error-rate metric; it is not shown as its own column on the live event log, the per-session timing chart, or the diagnostics screen. Could be a future polish item if the user finds the aggregate value insufficient. |
| 8 | CSV format | **Append eighth column.** Strictly additive; existing parsers reading the original seven columns ignore the new one. Same evolution pattern as Phase 7's `event_category` value extension. |
| 9 | Schema migration | **Yes — first ever migration on `ikd.db`.** Pushes Phase 8's planned `Migration(1, 2)` to `Migration(2, 3)`. Phase 8's plan needs a one-line label change (Section 5.1.3 deliverable). |
| 10 | Pre-Phase-7.1 in-flight events on the live screen | **Defensive fallback in `DiagnosticsActivity`:** if `is_correction == true` and `correctionWeight == 0`, treat as weight 1. Handles the corner case where an event captured just before the v2 install lands in the in-memory list with the default-zero weight. Cost: one extra `if` per row in the live computation. |
| 11 | `correctionCount` projection on `SessionStatsRow` | **Kept alongside `correctionWeight`.** Useful for debugging visibility ("how many correction *rows* are there in this session" vs. "what's the magnitude") and keeps existing test fixtures compiling without rewriting their helper. |
| 12 | `errorRatePct` projection on `EventBucketRow` | **Removed — computed in Kotlin.** With the formula change, computing in SQL forces every aggregator caller to know the weighted formula. Moving it to Kotlin keeps the math in one place (`buildSnapshot()`). The `Companion` is already pure-derivation friendly (Phase 3 pattern), so the unit test surface barely changes. |
| 13 | Privacy invariant | **Preserved.** `correction_weight` stores only an integer count, never any text content. Same hard line as `event_category`. |
| 14 | Capture path edits | **Two — `recordAutocorrectEvent(...)` signature, BACKSPACE branch wiring, plus the one-line `LiveCaptureSessionStore.flushPending()` mapping forward.** No edits to `markImeEdit`, `maybeRecordExternalReplacement`, the selection tracker, the sensor helper, or the retention worker. |
| 15 | Live-mode error-rate path on `DiagnosticsActivity` | **Updated inline.** A few lines inside `updateComputedMetrics(...)` replace the simple count with the weighted formula. No new helper file — the math is small enough to inline. |

---

## 10. Explicitly Deferred to Later Phases

Listed here so we don't relitigate scope mid-phase.

- **BACKSPACE weighted by selection length** when the user backspaces a manual selection (today and after Phase 7.1, that still records as weight 1). A symmetric "deletion magnitude" change would mean reading the selection state inside the BACKSPACE branch of `onKey()`, plumbing an int through `markImeEdit`, and updating the migration to also backfill BACKSPACE-on-selection cases (which we can't reconstruct from existing data — they'd just stay at weight 1). Worth its own mini-plan if the user wants symmetric handling.
- **Per-event surface of `correction_weight`** in the live event log, the per-session timing list, or as a chip on the saved-session screen. Phase 7.1 only changes the aggregate; the per-event field is invisible to the UI. Adds polish if the user wants to see "this autocorrect replaced N chars" inline.
- **A standalone "autocorrect rate" KPI** distinct from the unified error rate (e.g. "your autocorrect rate this week was 45 % of all corrections"). Trivial to add once the column exists; deferred because the aggregate error rate already absorbs the signal.
- **Distinguishing inline-autofill completions from spell-check corrections** at the weight level (Phase 7 lumped both under `AUTOCORRECT`). Both still produce the same `wasSelectionRange && isCollapsed` signature; if Phase 9 splits them, the new category would inherit the same weighted scheme.
- **A "true" error magnitude metric** that compares replaced text to typed text via Levenshtein distance or similar. Out of scope — would need post-replacement text content, violating the Phase 1.1 / 2 privacy invariant ("category, never raw text").
- **Voice-input weighting.** A long voice-dictated phrase still surfaces as one `AUTOCORRECT` event with a (potentially huge) replaced-span weight. The metric will inflate accordingly. If voice input becomes a real category (Phase 7 deferred this), it should get its own weight scheme.

Anything from this list earns its own focused mini-plan in `roadmap/Phase{N}/` if and when the user wants it.
