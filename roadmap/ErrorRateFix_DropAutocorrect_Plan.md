# Plan — Drop AUTOCORRECT from the error-rate formula

> **Status:** todo / not started. Filed 2026-05-10 as a follow-up to `ErrorRateFix_TestPlan.md` after on-device verification showed Tests 3 and 4 still failing post-Phase 9.

## Context

Tests 3 and 4 of `roadmap/ErrorRateFix_TestPlan.md` still fail after Phase 9. The capture-side, read-side, and live-side formulas all correctly implement the "weighted with BACKSPACE excluded from denominator" math from commit `569f331b`. The remaining failures are **not** a math bug — they are an attribution bug.

The IME's AUTOCORRECT detector (`SimpleKeyboardIME.maybeRecordExternalReplacement`, `services/SimpleKeyboardIME.kt:749–824`) is a heuristic on `onUpdateSelection` deltas. Phase 7's plan §5.2 already acknowledged that voice-input dictation, range deletes, and OEM-specific spell-check behaviour can fire false positives or miss true positives, and that there is no reliable cross-device way to fix this. In practice:
- **Test 3** (`ocasdasda → october`): the user's spell-check service either does not flag `ocasdasda` at all (no AUTOCORRECT row → reading 0%) or commits via a path the heuristic does not classify as a selection-collapse → reading not 90%.
- **Test 4** (`helxlo` → 3 BS → `lo`): a phantom AUTOCORRECT can fire as the spell-check underlines / re-segments the in-progress text — adding 1 to both `correctionCount` and `correctionWeight`, pulling the reading from 37.5% to ~50%.

User directive: "let's just remove [autocorrect] from the error rate formula." AUTOCORRECT rows continue to be captured (so CSV exports and the per-session detail screen still see them), but the error-rate KPI / chart / live cell stop counting them.

## Approach

Redefine "correction" in the metric layer to mean **BACKSPACE only**. AUTOCORRECT rows remain in `ikd_events`, remain in CSV, remain in the per-session event log; they just no longer contribute to either the numerator or the row that gets subtracted from the denominator of the error-rate formula.

New formula (capture-side unchanged):
```
errorRatePct = 100 * SUM(correction_weight WHERE category = 'BACKSPACE')
                   / (eventCount - backspaceCount - autocorrectCount)
```

Equivalently, since the existing `keystrokeCount` projection already excludes AUTOCORRECT:
```
errorRatePct = 100 * backspaceWeight / (keystrokeCount - backspaceCount)
```

This is byte-identical to the Phase 7.1 formula on sessions that have no AUTOCORRECT rows. Tests 1, 2, 4, 5, 6 all stay at their planned readings. Test 3 is the only behaviour change: it now reads `0 %` (the user typed `ocasdasda`, no backspaces, autocorrect is informational only).

## Critical files

### 1. DAO projections (rename `correctionCount`/`correctionWeight` → `backspaceCount`/`backspaceWeight`)

`interfaces/IkdEventDao.kt`:
- `getEventBuckets` (line 34–50): change
  ```
  SUM(CASE WHEN is_correction THEN 1 ELSE 0 END) AS correctionCount,
  SUM(correction_weight) AS correctionWeight,
  ```
  to
  ```
  SUM(CASE WHEN event_category = 'BACKSPACE' THEN 1 ELSE 0 END) AS backspaceCount,
  SUM(CASE WHEN event_category = 'BACKSPACE' THEN correction_weight ELSE 0 END) AS backspaceWeight,
  ```
- `getEventBucketsForMood` (line 57–80): same change.
- `getSessionStats` (line 89–105): same change.
- **Leave `getHabitsBuckets` alone for now** — Phase 9.3 introduced it; it consumes `correctionWeight` for the Habits chart, and that surface is out of scope for this fix. The `correctionWeight` column there can keep summing all `correction_weight` rows. Track in §6 follow-ups whether Habits should align too.

### 2. POJOs

`interfaces/EventBucketRow.kt`: rename `correctionCount` → `backspaceCount`, `correctionWeight` → `backspaceWeight`. Update KDoc to reflect that AUTOCORRECT rows do not contribute.

`interfaces/SessionStatsRow.kt`: same rename. Keep `eventCount` and `keystrokeCount` as-is (those are still useful for WPM and total-events display).

### 3. Aggregator (`helpers/IkdAggregator.kt`)

- `productiveKeystrokes(row)` (line 227–230): change to
  ```kotlin
  return (row.keystrokeCount - row.backspaceCount).coerceAtLeast(0)
  ```
  (uses the existing AUTOCORRECT-aware `keystrokeCount`, then subtracts BACKSPACE — yields the count of "kept-text" keystrokes).
- `computeBucketErrorRate(...)` and `computeOverallErrorRate(...)` (line 210–225): swap `correctionWeight` for `backspaceWeight`. Math otherwise unchanged.
- Update the comment block at line 174–180 and 212–215 to read "BACKSPACE-only weight" / "AUTOCORRECT rows are informational and do not contribute to the metric".

### 4. Per-session loader (`helpers/IkdSessionStatsLoader.kt`)

- Line 85: `productive = (statsRow.keystrokeCount - statsRow.backspaceCount).coerceAtLeast(0)`.
- Line 86: pass `statsRow.backspaceWeight` to `computeErrorRate`.
- Update the docstring at line 71–75.

### 5. Live diagnostics (`activities/DiagnosticsActivity.kt`)

`updateComputedMetrics(events)` at line 334–368:
- Replace `productiveKeystrokes = events.count { !it.isCorrection }` with
  ```kotlin
  val productiveKeystrokes = events.count {
      it.eventCategory != EVENT_CATEGORY_BACKSPACE &&
      it.eventCategory != EVENT_CATEGORY_AUTOCORRECT
  }
  ```
- Replace `weight = events.sumOf { effectiveWeight(it).toLong() }` with
  ```kotlin
  val weight = events.filter { it.eventCategory == EVENT_CATEGORY_BACKSPACE }
      .sumOf { effectiveWeight(it).toLong() }
  ```
- Update the doc comment at line 343–352 to describe the BACKSPACE-only semantics. `effectiveWeight(...)` (line 387–391) is unchanged — its weight-1 fallback now only applies to BACKSPACE rows that pre-date the v2→v3 migration, which is correct.

### 6. Tests

`app/src/test/.../IkdAggregatorTest.kt` and `app/src/test/.../IkdSessionStatsLoaderTest.kt`:
- Rename helper-builder fields `correctionCount`/`correctionWeight` → `backspaceCount`/`backspaceWeight`. Existing fixtures whose intent was "BACKSPACE-only correction" stay the same numerically.
- The Phase 7.1 fixtures `errorRate_isWeightedByCorrectionWeight*` (the canonical `ocasdasda` 90% case) now expect `null` or `0.0 %` since the inputs only describe AUTOCORRECT events. Either delete these fixtures or rewrite them as `errorRate_isUnaffectedByAutocorrect*` asserting that an AUTOCORRECT row contributes neither numerator nor denominator change.
- Add a new fixture mirroring Test 4 from the plan: 6 ALPHA + 3 BACKSPACE (weight 1 each) + 2 ALPHA → expected error rate 37.5 %.
- Add a new fixture for "session with autocorrect only": 9 ALPHA + 1 SPACE + 1 AUTOCORRECT → expected error rate `0.0 %`.

### 7. Test-plan documentation (`roadmap/ErrorRateFix_TestPlan.md`)

- Section header: replace the "fix changes two things" preamble with a short note that the formula has changed: now `100 * SUM(BACKSPACE.correction_weight) / (events − BACKSPACE − AUTOCORRECT)`.
- **Test 3** (`ocasdasda → october`): change "Expected error rate ≈ 90.0 %" to "Expected error rate **0.0 %** (AUTOCORRECT rows are captured but do not feed the metric)". The pass criterion becomes: reading is `0.0 %` (or `—`) AND the session-detail screen lists the AUTOCORRECT row in the event log (proving capture still works).
- **Test 4**: unchanged — still 37.5 %.
- **Tests 1, 2, 5, 6, 7, 8**: unchanged. Tests 1 and 2 already had no AUTOCORRECT and remain at 100 %.

### 8. Project doc (`CLAUDE.md`)

Update the Phase 7.1 section's "New error-rate formula" paragraph to:
- Numerator: `SUM(correction_weight)` over `BACKSPACE` rows only.
- Denominator: `eventCount − backspaceCount − autocorrectCount`.
- Add a one-line note: AUTOCORRECT events are still captured and exported but are intentionally excluded from the metric since the IME-level heuristic that detects them cannot reliably distinguish a true autocorrect from spell-check noise across OEMs.

## Files NOT changing

- `services/SimpleKeyboardIME.kt` — capture path unchanged. AUTOCORRECT rows still recorded with `is_correction = true` and the existing weight; that data is still useful for non-metric surfaces (event log, CSV, future analyses).
- `helpers/LiveCaptureSessionStore.kt`, `helpers/IkdCsvWriter.kt`, `models/IkdEvent.kt`, `models/KeyTimingEvent.kt` — capture/buffer/CSV stay byte-identical.
- `databases/IkdDatabase.kt` — no schema bump. The `correction_weight` column stays; readers just project a different projection out of it.
- Phase 9.3 `helpers/IkdHabitsAggregator.kt`, `interfaces/HabitsBucketRow.kt`, `interfaces/IkdEventDao.kt::getHabitsBuckets`, and Phase 9.10 `helpers/IkdQualityAggregator.kt` / `interfaces/DayQualityRow.kt` — out of scope; their semantics may need to be re-evaluated as a follow-up but are not part of the error-rate KPI surface this fix targets.

## Verification

End-to-end on a real device:

1. `./gradlew assembleCoreDebug && ./gradlew installCoreDebug` (with `JAVA_HOME` set per CLAUDE.md).
2. Run all six manual test cases in `roadmap/ErrorRateFix_TestPlan.md` against the new build:
   - Tests 1, 2, 4, 5, 6 should pass unchanged (100 %, 100 %, 37.5 %, 0 %, 0 %→100 % progression).
   - Test 3 should now read 0.0 % (or `—` if rendering nulls) — verify the AUTOCORRECT row is still visible in the per-session event log.
   - Test 7: dashboard avg-error-rate cell aggregates the same way; no crash, no NaN.
3. Pull `ikd.db` and run the diagnostic SQL from §9 of the test plan against a session that contains AUTOCORRECT rows. Confirm `100 * backspaceWeight / (eventCount - backspaceCount - autocorrectCount)` matches the UI reading.

JVM unit tests:
```
./gradlew :app:testCoreDebugUnitTest --tests 'org.fossify.keyboard.helpers.IkdAggregatorTest'
./gradlew :app:testCoreDebugUnitTest --tests 'org.fossify.keyboard.helpers.IkdSessionStatsLoaderTest'
```
Both should pass with the new BACKSPACE-only fixtures.

Static analysis:
```
./gradlew detekt
```
Should report no new findings.

## Out of scope / follow-ups

- Habits chart's own correction metric (Phase 9.3) and the Daily Quality scatter (Phase 9.10) still treat AUTOCORRECT as a correction. If they should align with the new error-rate semantics, that is a separate, optional cleanup.
- Renaming the column `correction_weight` on `ikd_events` to `backspace_weight` would require a schema migration and is **not** done here — the column name stays generic; only the higher-level projection / POJO names change.
