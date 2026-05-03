# Phase 7 — Capture Emoji + Autocorrect Events

**Status:** Planned
**Depends on:** Phase 5 (the saved-session dashboard reads `event_category`; new categories surface there for free)
**Branch:** `feat/phase7-emoji-autocorrect-capture` — cut from `main` after Phase 5 is merged.
**Scope (one sentence):** Capture two new keyboard interaction types that the IKD pipeline currently misses — **emoji insertions from the palette** (today silently bypass `onKey`) and **inline replacement events** (today the only one is the Vietnamese Telex transliteration; the category is forward-compatible for any future autocorrect mechanism) — and wire them through the existing dashboards by tagging them with the right `event_category` and `is_correction` semantics.

This is the **first phase since Phase 2 to deliberately reopen the keyboard layer.** The IME (`SimpleKeyboardIME.kt`), the keyboard view (`MyKeyboardView.kt`), and the listener interface (`OnKeyboardActionListener.kt`) have been on the forbidden list since Phase 3 to keep capture stable while we built the dashboards. Phase 7 is the explicit, scoped unfreeze: capture-side instrumentation only, no new visualization work this phase.

---

## Table of Contents

1. [Concept & Scope](#1-concept--scope)
2. [Branch & Layering Discipline (REOPEN keyboard layer)](#2-branch--layering-discipline-reopen-keyboard-layer)
3. [New Event Categories](#3-new-event-categories)
4. [Capture Paths](#4-capture-paths)
5. [Sub-Phases (3)](#5-sub-phases-3)
   - [6.1 Listener Method + Emoji Capture](#61-listener-method--emoji-capture)
   - [6.2 Autocorrect Detection via `onUpdateSelection`](#62-autocorrect-detection-via-onupdateselection)
   - [6.3 WPM Formula Adjustment + Polish](#63-wpm-formula-adjustment--polish)
6. [Downstream Impact (Dashboards, Insights, CSV)](#6-downstream-impact-dashboards-insights-csv)
7. [Files to Create / Modify (vs. forbidden)](#7-files-to-create--modify-vs-forbidden)
8. [Acceptance Criteria](#8-acceptance-criteria)
9. [Decisions](#9-decisions)
10. [Explicitly Deferred to Phase 7](#10-explicitly-deferred-to-phase-7)

---

## 1. Concept & Scope

The Phase 1.1 capture pipeline categorises every keyboard event into one of six values: `ALPHA`, `DIGIT`, `SPACE`, `BACKSPACE`, `ENTER`, `OTHER`. Two real interactions are not represented:

- **Emoji insertions.** When the user opens the emoji palette and taps an emoji, the path goes `EmojisAdapter.onClick → OnKeyboardActionListener.onText(emoji) → InputConnection.commitText`. It never goes through `onKey`. **No event is recorded — the emoji is invisible to the IKD pipeline.**
- **System spell-check corrections.** When the user types a misspelled word, Android's spell-check service in the target app flags it with a `SuggestionSpan` (the familiar red squiggly underline). When the user long-presses the word and taps a replacement candidate, the system **replaces the text directly via the `InputConnection`** — the IME never sees the corrected text, only a `onUpdateSelection` callback indicating that the cursor/selection moved unexpectedly. **Today the IME only uses `onUpdateSelection` for shift-state maintenance; the correction itself is invisible to the IKD pipeline.** The same callback path also covers the existing Vietnamese Telex inline replacement in `onKey` (`cachedVNTelexData` branch).

Phase 7 fixes both:
1. Emoji palette taps generate an `EMOJI` event, with `is_correction = false`.
2. **External text replacements** detected via `onUpdateSelection` (system spell-check accepts) and the in-IME VN Telex replacement both generate an `AUTOCORRECT` event with `is_correction = true` (so they automatically count toward the existing error-rate metric without any aggregator change).
3. The WPM formulas in `IkdAggregator` and `IkdSessionStatsLoader` are tweaked to exclude `AUTOCORRECT` from the keystroke denominator (autocorrects are corrections, not new typing). Emoji events DO count toward WPM.

### In scope (the entire feature)

- Add `fun onEmojiText(text: String) = onText(text)` to `OnKeyboardActionListener`. Default delegates to `onText` so existing implementations don't break.
- `SimpleKeyboardIME` overrides `onEmojiText` to record an `EMOJI` event (with the current IKD/hold/flight timing semantics) before delegating to its `onText`.
- `MyKeyboardView`'s emoji palette callback (`EmojisAdapter` callback) calls `onEmojiText` instead of `onText`. Clipboard taps continue to call `onText` and remain uncaptured this phase.
- `SimpleKeyboardIME.onUpdateSelection(...)` is instrumented with an "expected cursor/text" tracker so the IME can detect when an external actor (the system spell-check service) replaced text behind its back. Such replacements emit an `AUTOCORRECT` event with `is_correction = true`. The same code path also catches the existing in-IME VN Telex replacement (`setComposingText` in `cachedVNTelexData`) for free, since those edits also surface through `onUpdateSelection`.
- `IkdAggregator.getEventBuckets` and `IkdEventDao.getSessionStats` queries are updated so the WPM denominator and the aggregate event count exclude `AUTOCORRECT`. Error rate is *not* changed — it still uses `is_correction`, which now naturally includes autocorrects.
- New JVM unit tests covering the WPM formula change.

### Out of scope (deferred to Phase 7 — see Section 10)

Capturing clipboard chip taps; recording the actual emoji Unicode character (vs. just the `EMOJI` category); English-style dictionary autocorrect (doesn't exist in this fork — the `AUTOCORRECT` category is forward-compatible); inline-suggestion accepts (system handles the commit, not visible from inside the IME); per-category visualizations (emoji rate KPI, autocorrect rate KPI, category breakdown chart); schema migration to add a separate `is_autocorrect` flag.

---

## 2. Branch & Layering Discipline (REOPEN keyboard layer)

Phase 7 deliberately reopens five files that have been frozen since Phase 3. This is a **scoped, additive unfreeze** — every edit is described below; no existing capture behaviour for non-emoji, non-autocorrect events changes.

### Reopened files (allowed, additive only)

| File | Why reopened | Edit shape |
|---|---|---|
| `services/SimpleKeyboardIME.kt` | Capture site for both new categories | Override `onEmojiText` to record an `EMOJI` event; instrument `onUpdateSelection` with an expected-cursor/text tracker so external replacements (spell-check accepts, VN Telex `setComposingText`, inline autofill) record an `AUTOCORRECT` event; extend the category constants |
| `views/MyKeyboardView.kt` | Emoji palette callback is the entry point for the emoji capture path | One-line change: emoji adapter callback calls `onEmojiText(emoji)` instead of `onText(emoji)`. Clipboard chip and clipboard manager callbacks (also using `onText`) unchanged |
| `interfaces/OnKeyboardActionListener.kt` | Need a method specific to emoji-source text injections so the IME can distinguish them from clipboard taps | Add `fun onEmojiText(text: String) { onText(text) }` (default delegates to existing method — backwards compatible) |
| `helpers/IkdAggregator.kt` | WPM formula must exclude `AUTOCORRECT` from the keystroke denominator | Single SQL change: `SUM(CASE WHEN event_category != 'AUTOCORRECT' THEN 1 ELSE 0 END)` for the WPM event count; or add a sibling `keystrokeCount` projection |
| `helpers/IkdSessionStatsLoader.kt` | Same WPM correction at the per-session level | Mirror the aggregator change in `getSessionStats` |

### Still forbidden

| File | Reason |
|---|---|
| `helpers/LiveCaptureSessionStore.kt` | Capture buffer — frozen. The new event recordings reuse the existing `recordTimingEvent()` API; no buffer changes. |
| `helpers/KinematicSensorHelper.kt` | Sensor path — unrelated to text events |
| `helpers/IkdRetentionWorker.kt` | Background worker — unrelated |
| `databases/IkdDatabase.kt` | **No schema migration this phase.** `event_category` is already a `String`; new values land in the existing column. |
| Any Room `@Entity` (`IkdEvent`, `SensorSample`, `SessionRecord`) | No schema change |
| `databases/ClipsDatabase.kt`, `interfaces/ClipsDao.kt` | Out of scope |
| `helpers/IkdCsvWriter.kt` | CSV format unchanged — new categories appear in the existing `event_category` column |
| `helpers/IkdSessionChartLoader.kt` (Phase 5) | Per-session chart loader — frozen, picks up new categories naturally |
| `views/IkdLineChartView.kt` | Chart wrapper — frozen |

### Branch hygiene

- Branch name: `feat/phase7-emoji-autocorrect-capture`
- Cut from latest `main` after Phase 5 has been merged
- Each sub-phase (6.1 → 6.3) lands as one focused commit using the project's `feat:` / `chore:` / `docs:` convention
- After 6.3 passes acceptance, open a single PR back to `main`

---

## 3. New Event Categories

| Category | When recorded | `is_correction` | Counts toward WPM? | Notes |
|---|---|---|---|---|
| `EMOJI` | User taps an emoji in the palette | `false` | **Yes** — an emoji is a "keystroke equivalent" for typing-speed purposes | One event per palette tap. Hold time is `-1` (palette taps don't trigger `onPress`). Flight time computed from `lastKeyUpTimestamp` if available. |
| `AUTOCORRECT` | External text replacement detected via `onUpdateSelection` (system spell-check accept) **or** in-IME VN Telex `setComposingText` substitution | **`true`** | **No** — autocorrects are corrections, not new typing | Hold time `-1` (no `onPress` for an external edit). Flight time `-1`. IKD computed from the previous `lastKeyUpTimestamp` — gives a meaningful "time-since-last-keystroke" value for the correction. The keystroke that *preceded* the correction is still recorded separately as ALPHA/etc.; the AUTOCORRECT row is the replacement itself. |

Both categories use the existing `IkdEvent` schema; no new columns, no schema migration.

### Privacy invariant (preserved)

The existing privacy guarantee from Phase 1.1 / 2 is "category, never raw text." Phase 7 keeps that:
- **`EMOJI` row records only the category, never the actual emoji Unicode codepoint.** Even though emojis are themselves single-character categories (a 🚀 conveys more meaning than `ALPHA`), the principle of "no raw text in the DB" stays intact. Recording the actual codepoint is an explicit Phase 7 design call (Section 10).
- `AUTOCORRECT` records only the category, never the original or replacement text.

---

## 4. Capture Paths

| Action | Existing call path | After Phase 7 |
|---|---|---|
| Tap emoji in palette | `EmojisAdapter` callback → `onText(emoji)` → `commitText` (no event recorded) | `EmojisAdapter` callback → `onEmojiText(emoji)` → IME records `EMOJI` event with current timing → delegate calls `onText(emoji)` → `commitText` |
| Long-press misspelled word → tap a system spell-check candidate | System replaces text via `InputConnection`; IME receives `onUpdateSelection(...)` (currently used only for shift state) | `onUpdateSelection` detects the unexpected cursor/text delta against the IME's tracked "expected" state → records an `AUTOCORRECT` event with `is_correction = true` |
| VN Telex inline replacement (Vietnamese only) | `onKey` finds matching cached entry → `setComposingText(replacement)` (no event recorded) | Same path. The `setComposingText` triggers `onUpdateSelection` which the new tracker also classifies as `AUTOCORRECT` — same code path as the spell-check accept above |
| Tap clipboard chip | `onText(clipboardContent)` | Unchanged — not captured this phase |
| Tap clipboard manager item | `onText(clip.value)` | Unchanged — not captured this phase |
| Tap inline-autofill suggestion (system chip in `onInlineSuggestionsResponse`) | system handles commit; arrives as `onUpdateSelection` delta | **Captured as `AUTOCORRECT` for free** by the same `onUpdateSelection` tracker, since the tracker doesn't care about the source — any unexpected cursor/text delta is logged. Arguably this should be a `COMPLETION` category, but for v1 we lump it with autocorrect and let Phase 7 split. |
| Regular alphanumeric / space / backspace / enter | `onPress` then `onKey` records via `categorizeKeyCode` | Unchanged |
| User manually taps the field to move the cursor | `onUpdateSelection(...)` | **Excluded from `AUTOCORRECT` capture** by the heuristic in Section 5.2 (no text-length change, no recent IME commit) |

---

## 5. Sub-Phases (3)

Three focused sub-phases. Each compiles, tests, and ships.

---

### 6.1 Listener Method + Emoji Capture

**Goal:** Emoji palette taps produce `EMOJI` events. Clipboard taps still bypass capture (deferred to Phase 7).

#### Deliverables

- `interfaces/OnKeyboardActionListener.kt` (modified) — **add**:
  ```kotlin
  /**
   * Called when text from a structured source (emoji palette) is committed.
   * Default delegates to [onText] for backwards compatibility.
   */
  fun onEmojiText(text: String) = onText(text)
  ```
- `services/SimpleKeyboardIME.kt` (modified):
  - Override `onEmojiText(text)` — record an `EMOJI` event using the same IKD/hold/flight pattern as `onKey` (hold = `-1` since `onPress` wasn't called for palette taps; flight derived from `lastKeyUpTimestamp` if non-zero), then delegate to `onText(text)` (or directly to `currentInputConnection.commitText(text, 1)`)
  - Update `lastKeyUpTimestamp` after recording, the same way `onKey` does
  - Extend `categorizeKeyCode` (or add a `categorizeText` helper) — at minimum, expose an `EMOJI` constant
- `views/MyKeyboardView.kt` (modified) — line 1888: change `mOnKeyboardActionListener!!.onText(emoji.emoji)` → `mOnKeyboardActionListener!!.onEmojiText(emoji.emoji)`. Clipboard callsites at lines 955 and 1716 stay on `onText`.
- New constants in `helpers/Constants.kt` for the category strings (`EVENT_CATEGORY_EMOJI = "EMOJI"`, `EVENT_CATEGORY_AUTOCORRECT = "AUTOCORRECT"`) so the aggregator queries can reference them by symbolic name and not stringly-typed literals.

#### Acceptance

- `./gradlew assembleCoreDebug` succeeds
- `./gradlew detekt` and `./gradlew lint` produce no regressions vs. existing baselines
- Manual smoke: install on device, open emoji palette, tap one emoji while a session is active (privacy mode OFF) → confirm via `adb exec-out run-as ... databases/ikd.db` that an `EMOJI` row appears with the correct timing
- Live diagnostics screen (Diagnostics → "View Log") shows the `EMOJI` row in real time
- Phase 4 sensor toggle, Phase 5 chart dashboard, Phase 1.1 capture all behave unchanged for non-emoji events

#### Commit

`feat(phase7): capture emoji palette taps as EMOJI events`

---

### 6.2 Autocorrect Detection via `onUpdateSelection`

**Goal:** External text replacements — primarily the **system spell-check accept** path (long-press misspelled word → tap a candidate) — produce an `AUTOCORRECT` row with `is_correction = true`. The same code path also covers the in-IME VN Telex replacement and any future inline-autofill accepts for free.

#### Why this is harder than emoji

The IME never sees the corrected text. When the system spell-check service replaces "helloo" with "hello", the user's text changes via `InputConnection.commitText` called *outside* the IME. The IME's only signal is `onUpdateSelection(oldStart, oldEnd, newStart, newEnd, candStart, candEnd)`. We need to distinguish "the user just tapped to move the cursor" from "the system just replaced text."

#### Detection heuristic

Track three pieces of "expected" state in `SimpleKeyboardIME`:

```kotlin
// Phase 7: external-edit detection state
private var expectedCursorPosition: Int = -1   // updated after every IME-initiated text change
private var lastImeEditTimestamp: Long = 0L    // SystemClock.uptimeMillis() of our last commit
private var lastKnownTextLength: Int = -1      // length of the field after our last edit (best-effort, derived from getExtractedText)
```

Update these in three places:
1. `onKey` (after `commitText` for character keys, after `deleteSurroundingText` for backspace) — `expectedCursorPosition += delta` etc.
2. `onText` / `onEmojiText` — set `expectedCursorPosition += text.length`
3. `onStartInputView` — reset all three (new field, expectations cleared)

In `onUpdateSelection`:

```kotlin
val timeSinceImeEdit = SystemClock.uptimeMillis() - lastImeEditTimestamp
val cursorDelta = newSelStart - oldSelStart
val isCollapsed = newSelStart == newSelEnd
val wasSelectionRange = oldSelStart != oldSelEnd

// External edit signature:
// - Either we had a selection range that collapsed to a cursor (canonical spell-check accept), OR
// - It's been > IME_EDIT_GRACE_MS since our last commit but the cursor moved by != 0 AND the field length changed
val likelyExternalReplacement = (wasSelectionRange && isCollapsed) ||
    (timeSinceImeEdit > IME_EDIT_GRACE_MS && cursorDelta != 0 && fieldLengthChanged())

if (likelyExternalReplacement && LiveCaptureSessionStore.isCapturing) {
    recordAutocorrectEvent()
}

// Update tracker for next call
expectedCursorPosition = newSelStart
lastKnownTextLength = currentFieldLength()
```

Constants:
- `IME_EDIT_GRACE_MS = 50` — anything within this window of our own `commitText` is assumed to be a downstream notification of *that* commit, not a separate external edit.

`fieldLengthChanged()` and `currentFieldLength()` use `currentInputConnection.getExtractedText(ExtractedTextRequest(), 0)?.text?.length ?: -1` — best-effort; the call can fail and that's fine, we just won't record an AUTOCORRECT in that frame. **`getExtractedText` runs on the IME thread; this is acceptable here because it's already used elsewhere in `onKey`.**

#### Deliverables

- `services/SimpleKeyboardIME.kt` (modified):
  - Add the three tracker fields and update logic in `onKey`, `onText`, `onEmojiText`, `onStartInputView`
  - Extend `onUpdateSelection` to apply the heuristic and record `AUTOCORRECT` events
  - New private `recordAutocorrectEvent()` helper using the same shape as the existing top-of-`onKey` capture block (hold/flight = `-1`, IKD from `lastKeyUpTimestamp`, `is_correction = true`)
  - The VN Telex `setComposingText` path naturally fires `onUpdateSelection` afterwards, so it's captured by the same code with no extra hook
- New constants in `helpers/Constants.kt`: `IME_EDIT_GRACE_MS`, `EVENT_CATEGORY_AUTOCORRECT`

#### Known false-positives and false-negatives (acknowledged)

- **False positive:** user manually selects a range, then deletes it via the keyboard's backspace. The `oldSelStart != oldSelEnd → newSelStart == newSelEnd` signature matches. Mitigation: skip if `lastImeEditTimestamp` is within the grace window (the backspace `commitText` will have just fired).
- **False negative:** user replaces a single character via spell-check (rare — most spell-check candidates are multi-char). The `wasSelectionRange && isCollapsed` heuristic still catches it as long as the spell-check service selected the word first. Most do.
- **False positive:** user uses voice input which dictates a long phrase via `commitText` from outside the IME. We'd record this as one AUTOCORRECT, which is wrong. Mitigation acknowledged but deferred — voice input through Fossify already routes through a separate path that the user controls.

These are listed explicitly in Section 9 (Decision #3) and Section 10 (Phase 7 candidates).

#### Acceptance

- `./gradlew assembleCoreDebug` succeeds
- Detekt/lint no regressions
- Manual smoke on device:
  - Type "helloo" in any text field with system spell-check enabled → wait for the red underline → long-press → tap "hello" from the suggestion list → confirm an `AUTOCORRECT` row appears in `ikd.db` with `is_correction = true`
  - Just tapping the field to move the cursor (no text change) → no `AUTOCORRECT` row
  - Manually selecting a range and tapping a character key (replace selection with one char) → no `AUTOCORRECT` row (the IME's own commit triggers `onUpdateSelection` within the grace window)
- Vietnamese Telex replacement (typing "viet" → "việt") produces an `AUTOCORRECT` row via the same path

#### Commit

`feat(phase7): detect external text replacements as AUTOCORRECT via onUpdateSelection`

---

### 6.3 WPM Formula Adjustment + Polish

**Goal:** WPM excludes `AUTOCORRECT` from the keystroke count. Error rate naturally includes it (already does, via `is_correction`). Documentation updated.

#### Deliverables

- `helpers/IkdAggregator.kt` (modified): in the `getEventBuckets` query (used by `DashboardActivity`), add a new projection `keystrokeCount = SUM(CASE WHEN event_category != 'AUTOCORRECT' THEN 1 ELSE 0 END)`. Use that for the WPM calculation in `Companion.computeWpm`. Keep the existing `eventCount` (= `COUNT(*)`) unchanged so other consumers stay stable.
- `interfaces/EventBucketRow.kt` (modified): add the new `keystrokeCount: Int` field.
- `interfaces/IkdEventDao.kt` (modified):
  - Bucket query — add the new projection (above)
  - `getSessionStats(sessionId)` — same: add `keystrokeCount` projection, use it where the per-session WPM is computed
- `interfaces/SessionStatsRow.kt` (modified): add the new `keystrokeCount: Int` field.
- `helpers/IkdSessionStatsLoader.kt` (modified): use `keystrokeCount` instead of `eventCount` for WPM. Display still reads "events" in the KPI cell — that label stays as the count of *all* events (including autocorrects), since the user might be surprised to see fewer events than they typed if we hid autocorrects from the count.
- `app/src/test/.../IkdAggregatorTest.kt` (modified): add a test fixture with 100 `ALPHA` + 5 `AUTOCORRECT` events over 60s → assert WPM = `100 / 5 * 60_000 / 60_000 = 20.0` (autocorrects excluded), error rate = `5 / 105 * 100 ≈ 4.76 %` (autocorrects included).
- `app/src/test/.../IkdSessionStatsLoaderTest.kt` (modified): same shape per-session.
- `CLAUDE.md` updated with a Phase 7 section: scope, the two new categories, the privacy invariant, the WPM-vs-error-rate semantics, the reopened files.
- `roadmap/FeatureRoadmap.md` Phase 7 entry marked Implemented.

#### Acceptance

- `./gradlew test` passes the new fixtures
- `./gradlew assembleCoreDebug` succeeds; detekt + lint no regressions
- Aggregate `DashboardActivity` and per-session dashboard render correctly with seeded sessions that include `EMOJI` and `AUTOCORRECT` events
- WPM in both dashboards matches the unit-tested formula
- Error rate in both dashboards reflects autocorrects (verifiable by typing 10 ALPHAs + 1 backspace vs. 10 ALPHAs + 1 autocorrect — both should produce the same error rate)

#### Commit

`feat(phase7): exclude AUTOCORRECT from WPM denominator and document scope`

(or split into one `feat:` for the formula and one `docs(phase7):` for CLAUDE / roadmap if the diff is large)

---

## 6. Downstream Impact (Dashboards, Insights, CSV)

### Phase 3 aggregate dashboard (`DashboardActivity`)

- **WPM line chart:** values change for sessions that contain autocorrects. Before Phase 7, autocorrects didn't exist (zero rows); after Phase 7, autocorrects are excluded from the WPM denominator, so any session with autocorrects shows a *lower* WPM than it would have if autocorrects had been counted as keystrokes — but a *higher* WPM than if no fix had been applied (since autocorrects aren't double-counted as both keystroke and correction).
- **Average IKD chart:** unchanged. AUTOCORRECT rows have IKD just like other events; they're averaged in.
- **Error rate chart:** values rise for sessions with autocorrects (each autocorrect is now an `is_correction = true` row).
- **KPI strip:** sessions, total typing time, avg WPM, error rate — same direction of change as the charts above.

### Phase 4 / 5 per-session screen (`EventFeedActivity` with `EXTRA_SESSION_ID`)

- **KPI cells:** same direction of change as the aggregate dashboard.
- **Three line charts (timing / gyro / accel):** unchanged in shape. The timing chart will include AUTOCORRECT rows in its IKD average (they have IKD values like any other event). EMOJI rows likewise.
- **Live event log (`EventFeedActivity` without `EXTRA_SESSION_ID`):** EMOJI and AUTOCORRECT rows appear inline with other timing rows, distinguishable by the `Category` column.

### CSV export

The `event_category` column already supports any string. Existing exports gain two new possible values; no format change.

### Privacy

Unchanged — see Section 3. Categories only, never raw text.

### Schema

Unchanged — `IkdDatabase.version` stays at 1.

---

## 7. Files to Create / Modify (vs. forbidden)

### Create (0 files this phase)

This is an instrumentation phase; all logic lives in existing files.

### Modify (additive only — no existing logic changes for non-emoji / non-autocorrect events)

| File | Sub-phase | Change |
|---|---|---|
| `interfaces/OnKeyboardActionListener.kt` | 6.1 | **Add** `onEmojiText(text)` with default delegating to `onText(text)` |
| `services/SimpleKeyboardIME.kt` | 6.1 / 6.2 | Override `onEmojiText` to record `EMOJI` event; add expected-state tracker (`expectedCursorPosition`, `lastImeEditTimestamp`, `lastKnownTextLength`) updated in `onKey` / `onText` / `onEmojiText` / `onStartInputView`; extend `onUpdateSelection` to detect external replacements and record `AUTOCORRECT` events |
| `views/MyKeyboardView.kt` | 6.1 | Emoji palette callback calls `onEmojiText` instead of `onText` (one-line change) |
| `helpers/Constants.kt` | 6.1 | Add `EVENT_CATEGORY_EMOJI` / `EVENT_CATEGORY_AUTOCORRECT` symbolic constants |
| `helpers/IkdAggregator.kt` | 6.3 | Bucket query adds `keystrokeCount` projection; WPM uses it instead of `eventCount` |
| `helpers/IkdSessionStatsLoader.kt` | 6.3 | Same change at the per-session level |
| `interfaces/EventBucketRow.kt` | 6.3 | **Add** `keystrokeCount: Int` |
| `interfaces/SessionStatsRow.kt` | 6.3 | **Add** `keystrokeCount: Int` |
| `interfaces/IkdEventDao.kt` | 6.3 | Bucket query + `getSessionStats` query add `keystrokeCount` projection |
| `app/src/test/.../IkdAggregatorTest.kt` | 6.3 | Add fixtures covering autocorrect-WPM exclusion and emoji-counting-toward-WPM |
| `app/src/test/.../IkdSessionStatsLoaderTest.kt` | 6.3 | Same shape per-session |
| `CLAUDE.md` | 6.3 | Phase 7 section (gitignored — local only) |
| `roadmap/FeatureRoadmap.md` | 6.3 | Phase 7 marked Implemented |

### Forbidden (do not touch — see Section 2 for full list)

`LiveCaptureSessionStore.kt`, `KinematicSensorHelper.kt`, `IkdRetentionWorker.kt`, `IkdDatabase.kt`, all `@Entity` data classes (`IkdEvent`, `SensorSample`, `SessionRecord`), `ClipsDatabase.kt`, `ClipsDao.kt`, `IkdCsvWriter.kt`, `IkdSessionChartLoader.kt`, `IkdLineChartView.kt`.

---

## 8. Acceptance Criteria

The whole phase is done when **all** of these are green on `feat/phase7-emoji-autocorrect-capture`:

- [ ] `./gradlew assembleCoreDebug` succeeds
- [ ] `./gradlew test` passes (including the new fixtures in `IkdAggregatorTest` and `IkdSessionStatsLoaderTest`)
- [ ] `./gradlew detekt` and `./gradlew lint` produce no regressions vs. existing baselines
- [ ] `IkdDatabase.version` is unchanged (still 1)
- [ ] No `@Entity` data class is modified
- [ ] `IkdCsvWriter.kt`, `LiveCaptureSessionStore.kt`, `IkdSessionChartLoader.kt`, `IkdLineChartView.kt` are unchanged
- [ ] Manual smoke on device:
  - Tapping an emoji from the palette during an active session writes an `EMOJI` row (verifiable via `adb exec-out run-as ...` + a SQLite viewer)
  - Vietnamese Telex inline replacement writes an `AUTOCORRECT` row with `is_correction = true`
  - Non-Vietnamese typing produces zero `AUTOCORRECT` rows
- [ ] Live event log (Diagnostics → "View Log") shows EMOJI / AUTOCORRECT rows
- [ ] Aggregate dashboard and per-session dashboard render correctly with seeded mixed-category sessions
- [ ] WPM in both dashboards excludes `AUTOCORRECT` from the keystroke denominator (verified by unit test); emoji events do count
- [ ] Error rate in both dashboards rises when a session contains autocorrects, by exactly the autocorrect count over total events × 100
- [ ] CSV export includes `EMOJI` / `AUTOCORRECT` rows when present

---

## 9. Decisions

| # | Topic | Decision |
|---|---|---|
| 1 | Branch | `feat/phase7-emoji-autocorrect-capture`, off `main` after Phase 5 merges |
| 2 | Reopen the keyboard layer? | **Yes — explicitly and scoped.** Five files leave the forbidden list this phase: `SimpleKeyboardIME.kt`, `MyKeyboardView.kt`, `OnKeyboardActionListener.kt`, `IkdAggregator.kt`, `IkdSessionStatsLoader.kt`. All edits are additive. |
| 3 | What is "autocorrect" in this fork? | **System spell-check accepts** (long-press misspelled word → tap a candidate from the system context menu). The fork itself doesn't ship a dictionary; the red squiggly underline + suggestion list comes from Android's spell-check service running in the target app, which replaces text directly via `InputConnection`. We capture this via `onUpdateSelection` delta detection (Section 5.2). The same path also catches the in-IME Vietnamese Telex `setComposingText` replacement and any future inline-autofill accepts — we don't need to special-case the source, only the *external-replacement* signature. |
| 4 | EMOJI privacy: record the actual codepoint? | **No.** Record only the `EMOJI` category. The Phase 1.1 / 2 invariant ("category, never raw text") is a hard line; recording the actual emoji is a Phase 7 design call. |
| 5 | AUTOCORRECT `is_correction` flag | **`true`.** Autocorrects are corrections by nature. Flagging this way means the existing error-rate metric naturally includes them with no aggregator change. |
| 6 | EMOJI `is_correction` flag | **`false`.** Emojis are intentional input, not corrections. |
| 7 | WPM denominator | **Excludes AUTOCORRECT, includes EMOJI.** Autocorrects aren't new typing; emojis are. |
| 8 | Schema migration | **None.** `event_category` is a String column; new values land in it. `IkdDatabase.version` stays at 1. |
| 9 | CSV export | **No format change.** New categories appear in the existing column. |
| 10 | Clipboard tap capture | **Deferred to Phase 7.** Same `onText` path as emoji used to be, but a different semantic — a CLIPBOARD category would need its own decision on `is_correction` semantics and on whether clipboard *content* counts toward typing speed. |
| 11 | Inline-autofill suggestion accept capture | **Captured for free** in Phase 7 via the same `onUpdateSelection` heuristic — they show the same external-replacement signature as a spell-check accept. Phase 7 may want to split inline-autofill out into its own `COMPLETION` category since it's semantically different (filling in missing text vs. fixing typed text). |
| 12 | New visualizations (emoji rate, autocorrect rate KPIs) | **Deferred to Phase 7.** Phase 7 is capture-only. Surface to the user that the data is now in the DB; let the next phase decide which KPIs to add. |

---

## 10. Explicitly Deferred to Phase 7

Listed here so we don't relitigate scope mid-phase.

- **Capture clipboard chip and clipboard manager taps** as a `CLIPBOARD` category event (currently both go through `onText` and bypass capture). Needs a small follow-up edit to `MyKeyboardView` callsites and a new listener method or a parameter.
- **Record the actual emoji Unicode codepoint** alongside the `EMOJI` category. Reopens the privacy-invariant decision; adds analytics value (emoji frequency, popular emojis per session).
- **Split `AUTOCORRECT` into `AUTOCORRECT` (replacing typed text) vs. `COMPLETION` (filling in missing text from inline autofill).** Phase 7 lumps both under `AUTOCORRECT` because the `onUpdateSelection` signature is the same; distinguishing them requires comparing pre/post text content, not just lengths.
- **Improve voice-input disambiguation.** A long voice-dictated phrase shows up as a single `AUTOCORRECT` event today. A separate `VOICE` category and detection (probably via `EditorInfo.IME_FLAG_FORCE_ASCII` or measuring text-length deltas larger than ~30 chars) would be more accurate.
- **`SuggestionSpan`-aware capture.** Use `getExtractedText` to inspect spans on the replaced text — if the previous text had a `SuggestionSpan.FLAG_MISSPELLED`, we know with certainty it was a spell-check correction. Reduces false positives.
- **Per-category visualizations:**
  - Emoji rate KPI (emojis per minute of session) on the aggregate and per-session dashboards
  - Autocorrect rate KPI
  - Stacked-bar or pie-chart of category breakdown per session
  - Category-filtered IKD chart (e.g., "IKD for ALPHA only")
- **`is_autocorrect` boolean column on `IkdEvent`** as a replacement for the category-string approach. Cleaner schema but requires a migration; the category-string approach is fit-for-purpose at this scale.
- **Swipe typing capture** — out of scope; this fork is tap-only.
- **Distinguishing "completion" (suggested word that doesn't replace anything yet) from "correction"** (suggested word that replaces something already typed). Different semantics, would warrant a third category.

Anything from this list earns its own focused mini-plan in `roadmap/Phase7/` if and when the user wants it.
