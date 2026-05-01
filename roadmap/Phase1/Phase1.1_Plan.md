# Phase 1.1 — Live Keyboard Data & Metric Alignment

**Roadmap goal:** Validate the corrected behavioral metrics on the real keyboard input path before Phase 2 adds passive storage and long-lived collection.

**Status:** Not started  
**Depends on:** Phase 1  
**Scope:** Live `SimpleKeyboardIME` capture only. Developer-facing validation flow. No Room persistence, no passive always-on collection, and no dashboard aggregation.

---

## Table of Contents

1. [Concept & Scope](#1-concept--scope)
2. [Metric Alignment](#2-metric-alignment)
3. [Feature: Live Keyboard Capture](#3-feature-live-keyboard-capture)
4. [Feature: Sensor Context on Real Typing](#4-feature-sensor-context-on-real-typing)
5. [Feature: Developer Review and Export](#5-feature-developer-review-and-export)
6. [In-Memory Data Structures](#6-in-memory-data-structures)
7. [Files to Create / Modify](#7-files-to-create--modify)
8. [Acceptance Criteria](#8-acceptance-criteria)

---

## 1. Concept & Scope

Phase 1 proved the basic math and sensor wiring on a normal `EditText`. Phase 1.1 does **not** replace that work. It corrects the metric definitions that were conflated in earlier roadmap drafts and validates them on the real keyboard path:

- `MyKeyboardView.onPress()` and `SimpleKeyboardIME.onPress()` for key-down timing
- `SimpleKeyboardIME.onKey()` for key-up timing and event categorization
- IME lifecycle callbacks for session start/stop

**What this phase must achieve:**
- Keep **Flight Time** and **Inter-Key Delay (IKD)** as separate measurements
- Capture **real keyboard** timings instead of `EditText` touch approximations
- Preserve privacy by storing only event categories, never reconstructed text
- Keep the data **ephemeral** and developer-facing so metric validation happens before Phase 2 persistence

**What is intentionally excluded from this phase:**
- Room / SQLite persistence
- User-facing background collection toggle
- Long-running passive capture across sessions
- Daily/weekly aggregation, charts, or dashboard work

Phase 1.1 is a bridge phase: it fixes the definitions and the capture surface, but it still behaves like a controlled validation environment.

---

## 2. Metric Alignment

The corrected metric definitions for the project are:

| Metric | Definition | Formula | Notes |
|---|---|---|---|
| **Key Hold Time (Dwell Time)** | How long a key is physically held | `current ACTION_UP.time - current ACTION_DOWN.time` | `Dwell Time` remains the legacy label used in Phase 1 UI/export |
| **Flight Time** | Gap between releasing one key and pressing the next | `current ACTION_DOWN.time - previous ACTION_UP.time` | This is the finger-in-flight interval and must remain separate from IKD |
| **Inter-Key Delay (IKD)** | Time between consecutive key releases | `current ACTION_UP.time - previous ACTION_UP.time` | Kept for continuity with Phase 1 and historical comparisons |
| **Typing Speed** | Distribution-level indicator derived from raw timing events | Derived later from flight-time statistics and session duration | Not stored as a separate raw event in Phase 1.1 |
| **Error Rates** | Frequency of corrections during typing | Backspace or correction events divided by total input events | Raw correction events are captured now; aggregate ratios come later |
| **Accelerometer / Gyroscope Context** | Physical motion and orientation while typing | Sensor samples collected during the active IME session | Used to contextualize typing behavior, not to infer text |

If any implementation or export surface uses different formulas than the table above, that is a Phase 1.1 bug.

---

## 3. Feature: Live Keyboard Capture

### Capture surface

Phase 1.1 moves timing collection from a generic `EditText` to the real keyboard callback chain:

```text
MyKeyboardView ACTION_DOWN
  -> listener.onPress(keyCode)
  -> record key-down timestamp

MyKeyboardView ACTION_UP
  -> listener.onKey(keyCode)
  -> compute hold time, flight time, IKD
  -> categorize event (ALPHA, DIGIT, SPACE, BACKSPACE, ENTER, OTHER)
```

### Session lifecycle

One validation session exists only while the keyboard is actively shown for a developer-initiated capture run.

- Session starts when the IME becomes active for a validation run
- Session stops when the IME is dismissed or the validation run is ended
- All captured data stays in memory until explicitly reviewed or exported
- Starting a new validation run clears the previous in-memory session

### Timing state

```kotlin
private var currentSessionId = ""
private var lastKeyDownTimestamp = 0L
private var lastKeyUpTimestamp = 0L
private var liveCaptureActive = false
```

### Metric calculation

```kotlin
onPress(primaryCode) {
    val now = SystemClock.uptimeMillis()
    if (liveCaptureActive) {
        val flightTime = if (lastKeyUpTimestamp > 0L) now - lastKeyUpTimestamp else -1L
        lastKeyDownTimestamp = now
        recordPendingFlightTime(flightTime)
    }
}

onKey(primaryCode) {
    val now = SystemClock.uptimeMillis()
    if (liveCaptureActive) {
        val holdTime = if (lastKeyDownTimestamp > 0L) now - lastKeyDownTimestamp else -1L
        val ikd = if (lastKeyUpTimestamp > 0L) now - lastKeyUpTimestamp else -1L
        val flightTime = consumePendingFlightTime()
        lastKeyUpTimestamp = now

        recordEvent(
            eventCategory = toEventCategory(primaryCode),
            holdTimeMs = holdTime,
            flightTimeMs = flightTime,
            ikdMs = ikd,
            isCorrection = primaryCode == Keyboard.KEYCODE_DELETE
        )
    }
}
```

This split is important: **Flight Time is computed on the next key-down edge**, while **IKD is computed on the current key-up edge**.

---

## 4. Feature: Sensor Context on Real Typing

Phase 1.1 reuses the existing sensor helper, but now samples are attached to the same live IME session as the timing events.

### Rules

- Sensors start only for the active validation session
- Sensors stop as soon as the live capture session ends
- Samples are stored in memory with the session identifier
- Missing gyroscope support must still degrade gracefully to accelerometer-only capture

### Purpose

The goal is not analytics yet. The goal is to verify that sensor samples and corrected timing events line up during natural typing on the actual keyboard.

---

## 5. Feature: Developer Review and Export

Phase 1.1 still needs a human-verifiable output, but without introducing persistence.

### Review flow

- Developer starts a live validation run
- Developer types with the real keyboard in any text field
- Developer returns to a review surface in the companion app
- The most recent in-memory session is displayed and can be exported

### Export format

One CSV file with two blocks:

```text
session_id,timestamp_ms,event_category,ikd_ms,hold_time_ms,flight_time_ms,is_correction
<timing rows>

#sensor_readings
session_id,timestamp_ms,sensor_type,x,y,z
<sensor rows>
```

### Privacy rule

- Export only event categories such as `ALPHA`, `DIGIT`, `SPACE`, `BACKSPACE`, `ENTER`, `OTHER`
- Do not export raw characters or reconstructed text

This gives Phase 1.1 real keyboard data without crossing into Phase 2 persistence.

---

## 6. In-Memory Data Structures

Phase 1.1 keeps the data layer plain Kotlin, but updates the models to match the corrected metrics.

**Update:** `app/src/main/kotlin/org/fossify/keyboard/models/KeyTimingEvent.kt`

```kotlin
data class KeyTimingEvent(
    val sessionId: String,
    val timestamp: Long,
    val eventCategory: String,
    val ikdMs: Long,
    val holdTimeMs: Long,
    val flightTimeMs: Long,
    val isCorrection: Boolean
)
```

**Update:** `app/src/main/kotlin/org/fossify/keyboard/models/SensorReadingEvent.kt`

```kotlin
data class SensorReadingEvent(
    val sessionId: String,
    val timestamp: Long,
    val sensorType: String,
    val x: Float,
    val y: Float,
    val z: Float
)
```

**Create:** `app/src/main/kotlin/org/fossify/keyboard/helpers/LiveCaptureSessionStore.kt`

This in-memory store owns the current validation session, exposes the latest captured data to the review screen, and is cleared when a new session starts.

No DAOs, no Room entities, and no migrations belong in this phase.

---

## 7. Files to Create / Modify

### Create

| File | What it contains |
|---|---|
| `helpers/LiveCaptureSessionStore.kt` | In-memory holder for the latest IME validation session |
| `activities/LiveCaptureReviewActivity.kt` | Developer review/export screen for the latest live keyboard capture |

### Modify

| File | Change |
|---|---|
| `services/SimpleKeyboardIME.kt` | Add live validation session state and corrected metric capture |
| `models/KeyTimingEvent.kt` | Rename `dwellMs` to `holdTimeMs`; add category/correction fields |
| `models/SensorReadingEvent.kt` | Add `sessionId` so IME sensor samples align with timing rows |
| `helpers/KinematicSensorHelper.kt` | Reuse helper from IME lifecycle for validation sessions |
| `activities/DiagnosticsActivity.kt` | Link to the live review/export surface or show the latest IME session |
| `activities/SettingsActivity.kt` | Add a developer entry point for live keyboard validation |
| `res/values/strings.xml` | Strings for the live capture validation flow |

### Do NOT touch

| File | Why |
|---|---|
| `databases/ClipsDatabase.kt` | Phase 1.1 remains non-persistent |
| Room DAO files | Storage still belongs to Phase 2 |
| Dashboard files | Analytics visualization belongs to Phase 3 |

---

## 8. Acceptance Criteria

### Metric correctness

- [ ] Flight Time is computed as `previous ACTION_UP -> current ACTION_DOWN`
- [ ] IKD is computed as `previous ACTION_UP -> current ACTION_UP`
- [ ] Key Hold Time is computed as `current ACTION_DOWN -> current ACTION_UP`
- [ ] Flight Time and IKD diverge when hold times are non-zero, proving they are separate measurements

### Live capture

- [ ] A developer can start a validation session and type with the real keyboard
- [ ] The session uses `SimpleKeyboardIME` callbacks, not `EditText` touch events
- [ ] Backspace events are recorded as correction events without storing raw text
- [ ] Sensor samples are captured only during the active validation session

### Review and export

- [ ] The latest live-captured session can be reviewed after typing
- [ ] Export produces the corrected CSV schema with `event_category`, `hold_time_ms`, `flight_time_ms`, and `ikd_ms`
- [ ] Export contains no raw character data
- [ ] Starting a new validation session clears the previous in-memory data

### Phase boundary

- [ ] No Room database is introduced
- [ ] No passive background collection survives beyond the active validation session
- [ ] No aggregation or dashboard code is added in this phase