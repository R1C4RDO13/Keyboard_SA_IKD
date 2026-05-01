# Phase 1 — Sensor Calibration & Debug Environment

**Roadmap goal:** A controlled diagnostic screen where a developer can type freely and observe every timing and sensor metric live — proving the capture logic is correct before anything runs silently in the keyboard.

**Status:** Not started  
**Depends on:** Nothing  
**Scope:** Companion app only. No keyboard service changes. No database. All data lives in memory for the duration of the screen session.

---

## Table of Contents

1. [Concept & Scope](#1-concept--scope)
2. [Screen Layout](#2-screen-layout)
3. [Feature: Touch Dynamics Tracking](#3-feature-touch-dynamics-tracking)
4. [Feature: Kinematic Sensor Integration](#4-feature-kinematic-sensor-integration)
5. [Feature: Session Export](#5-feature-session-export)
6. [In-Memory Data Structures](#6-in-memory-data-structures)
7. [Files to Create / Modify](#7-files-to-create--modify)
8. [Acceptance Criteria](#8-acceptance-criteria)

---

## 1. Concept & Scope

Phase 1 has no user-facing product value. Its purpose is **verification only**:

- Prove the timing math (IKD, dwell, flight) produces plausible numbers
- Prove the gyroscope and accelerometer are reacting to typing motion
- Produce an exportable CSV that can be inspected offline to confirm data structure and timing accuracy

**What is intentionally excluded from this phase:**
- Room / SQLite — no persistence at all; everything is held in a `MutableList` in memory
- Live keyboard integration — the device's current soft keyboard is used inside a normal `EditText`
- Settings toggles — this screen is always accessible, no feature flag needed
- Any aggregation or analytics — raw events only

The data structures defined here (plain Kotlin data classes) will be promoted to Room entities in Phase 2. Keeping them plain now means Phase 1 has zero Gradle dependencies beyond what already exists.

---

## 2. Screen Layout

```
┌─────────────────────────────────────────────────────┐
│  [←]  Diagnostics                      [New] [Save] │  ← Toolbar
├─────────────────────────────────────────────────────┤
│                                                     │
│  ┌───────────────────────────────────────────────┐  │
│  │  Type here…                                   │  │  ← EditText (multiline)
│  │                                               │  │    ~25% of screen height
│  └───────────────────────────────────────────────┘  │
│                                                     │
│  ── Keystroke Timing ───────────────────────────    │
│                                                     │
│  Inter-Key Delay (IKD)   ·····   142 ms             │
│  Dwell Time              ·····    87 ms             │
│  Flight Time             ·····   112 ms             │
│  Events this session     ·····    38                │
│                                                     │
│  ── Gyroscope  (rad/s) ─────────────────────────    │
│                                                     │
│  X  ████████░░░░░░░░░░░░  +0.142                    │
│  Y  ░░░░░░░░░░░░░░░░░░░░  -0.003                    │
│  Z  █████░░░░░░░░░░░░░░░  +0.041                    │
│                                                     │
│  ── Accelerometer  (m/s²) ──────────────────────    │
│                                                     │
│  X  ███░░░░░░░░░░░░░░░░░  +0.21                     │
│  Y  ████████████████████  +9.78                     │
│  Z  ░░░░░░░░░░░░░░░░░░░░  -0.11                     │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### Toolbar actions

| Button | Behaviour |
|---|---|
| **[New]** | Clears the `EditText` and the in-memory event list. Resets all metric labels to `—`. Generates a new `sessionId`. Sensors keep running. |
| **[Save]** | Opens an SAF file-save picker and writes the current in-memory session to CSV. Disabled (greyed out) if the event list is empty. |

### Navigation entry point

Add a **"Diagnostics"** row to `SettingsActivity` under a `Developer` section header (placed after all user-facing settings). Tapping it starts `DiagnosticsActivity`.

---

## 3. Feature: Touch Dynamics Tracking

### The three timing signals

| Metric | Definition | Formula |
|---|---|---|
| **Dwell Time** | How long a key is physically held | `ACTION_UP.time − ACTION_DOWN.time` (same touch) |
| **Flight Time** | Gap between releasing one key and pressing the next | `current ACTION_DOWN.time − previous ACTION_UP.time` |
| **IKD** | Time between consecutive key releases | `current ACTION_UP.time − previous ACTION_UP.time` |

All three are captured from a single `OnTouchListener` on the `EditText`. No key codes are available from touch events at this level — that is expected and fine for Phase 1 (Phase 2 gets real key codes from `onPress`/`onKey`).

### Implementation

```kotlin
private var pressDownTime   = 0L   // when current key went down
private var lastReleaseTime = 0L   // when previous key was released

editText.setOnTouchListener { _, event ->
    when (event.action) {
        MotionEvent.ACTION_DOWN -> {
            pressDownTime = SystemClock.uptimeMillis()
        }
        MotionEvent.ACTION_UP -> {
            val now    = SystemClock.uptimeMillis()
            val dwell  = now - pressDownTime
            val flight = if (lastReleaseTime > 0L) pressDownTime - lastReleaseTime else -1L
            val ikd    = if (lastReleaseTime > 0L) now - lastReleaseTime else -1L
            lastReleaseTime = now

            recordEvent(ikd, dwell, flight)
            updateTimingDisplay(ikd, dwell, flight)
        }
    }
    false   // do not consume — let EditText handle text input normally
}
```

### Recording events

`recordEvent()` appends to the in-memory list and increments the counter label:

```kotlin
private val sessionEvents = mutableListOf<KeyTimingEvent>()

private fun recordEvent(ikd: Long, dwell: Long, flight: Long) {
    sessionEvents.add(
        KeyTimingEvent(
            sessionId   = currentSessionId,
            timestamp   = SystemClock.uptimeMillis(),
            ikdMs       = ikd,
            dwellMs     = dwell,
            flightMs    = flight
        )
    )
    eventCountLabel.text = sessionEvents.size.toString()
}
```

### Display update

`updateTimingDisplay()` runs on the main thread (the touch listener already fires there) and sets three `TextView` labels. Use `"— ms"` for `-1` values (first event in session):

```kotlin
private fun updateTimingDisplay(ikd: Long, dwell: Long, flight: Long) {
    ikdLabel.text    = if (ikd    >= 0) "$ikd ms"    else "—"
    dwellLabel.text  = if (dwell  >= 0) "$dwell ms"  else "—"
    flightLabel.text = if (flight >= 0) "$flight ms" else "—"
}
```

---

## 4. Feature: Kinematic Sensor Integration

### Sensors

| Sensor | Constant | Unit | Expected at rest |
|---|---|---|---|
| Gyroscope | `Sensor.TYPE_GYROSCOPE` | rad/s | ~0 on all axes |
| Accelerometer | `Sensor.TYPE_ACCELEROMETER` | m/s² | Y ≈ +9.8 (gravity) |

### KinematicSensorHelper

**Create:** `app/src/main/kotlin/org/fossify/keyboard/helpers/KinematicSensorHelper.kt`

```kotlin
class KinematicSensorHelper(
    context: Context,
    private val onSample: (SensorReadingEvent) -> Unit
) : SensorEventListener {

    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyro    = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accel   = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    val hasGyro:  Boolean get() = gyro  != null
    val hasAccel: Boolean get() = accel != null

    fun start() {
        gyro?.let  { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        accel?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        manager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val type = when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE     -> "GYRO"
            Sensor.TYPE_ACCELEROMETER -> "ACCEL"
            else -> return
        }
        onSample(
            SensorReadingEvent(
                timestamp  = SystemClock.uptimeMillis(),
                sensorType = type,
                x = event.values[0],
                y = event.values[1],
                z = event.values[2]
            )
        )
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
}
```

**Sampling rate:** `SENSOR_DELAY_GAME` (~50 Hz). Fast enough to capture typing motion; slow enough not to flood the UI thread.

**Missing sensor:** If `gyro == null`, hide the Gyroscope section from the layout instead of crashing. Check `sensorHelper.hasGyro` after construction.

### Wiring into DiagnosticsActivity

```kotlin
private val sensorReadings = mutableListOf<SensorReadingEvent>()
private lateinit var sensorHelper: KinematicSensorHelper

override fun onResume() {
    super.onResume()
    sensorHelper.start()
}

override fun onPause() {
    super.onPause()
    sensorHelper.stop()
}
```

Sensor callbacks arrive on a background thread. Post UI updates to the main thread:

```kotlin
sensorHelper = KinematicSensorHelper(this) { reading ->
    sensorReadings.add(reading)         // in-memory buffer (background thread — fine for a list)

    runOnUiThread {
        when (reading.sensorType) {
            "GYRO"  -> updateGyroDisplay(reading.x, reading.y, reading.z)
            "ACCEL" -> updateAccelDisplay(reading.x, reading.y, reading.z)
        }
    }
}
```

### Bar indicator helper

Map sensor value to a 0–100 progress bar. Gyro range ±10 rad/s; accel range 0–20 m/s²:

```kotlin
private fun toGyroProgress(v: Float)  = ((v + 10f) / 20f * 100).toInt().coerceIn(0, 100)
private fun toAccelProgress(v: Float) = (v / 20f   * 100).toInt().coerceIn(0, 100)

private fun updateGyroDisplay(x: Float, y: Float, z: Float) {
    gyroXBar.progress = toGyroProgress(x);  gyroXValue.text = "%.3f".format(x)
    gyroYBar.progress = toGyroProgress(y);  gyroYValue.text = "%.3f".format(y)
    gyroZBar.progress = toGyroProgress(z);  gyroZValue.text = "%.3f".format(z)
}
```

---

## 5. Feature: Session Export

### What is written

Two CSV blocks — one for keystroke timing events, one for sensor readings — written to a single file separated by a blank line and a header comment. This keeps the export to a single SAF picker interaction.

```
session_id,timestamp_ms,ikd_ms,dwell_ms,flight_ms
a3f9c1,1700000000000,-1,92,-1
a3f9c1,1700000000215,215,84,123
a3f9c1,1700000000401,186,91,102

#sensor_readings
session_id,timestamp_ms,sensor_type,x,y,z
a3f9c1,1700000000010,GYRO,0.0124,-0.0031,0.0413
a3f9c1,1700000000010,ACCEL,0.2100,9.7800,-0.1100
```

`-1` means "not applicable" (first event in session — no prior reference exists).

### Export flow

```kotlin
private val saveLauncher = registerForActivityResult(
    ActivityResultContracts.CreateDocument("text/csv")
) { uri ->
    uri ?: return@registerForActivityResult
    Thread {
        contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
            writer.write("session_id,timestamp_ms,ikd_ms,dwell_ms,flight_ms\n")
            sessionEvents.forEach {
                writer.write("${it.sessionId},${it.timestamp},${it.ikdMs},${it.dwellMs},${it.flightMs}\n")
            }
            writer.write("\n#sensor_readings\n")
            writer.write("session_id,timestamp_ms,sensor_type,x,y,z\n")
            sensorReadings.forEach {
                writer.write("${currentSessionId},${it.timestamp},${it.sensorType},${it.x},${it.y},${it.z}\n")
            }
        }
    }.start()
}
```

The **[Save]** toolbar button calls `saveLauncher.launch("ikd_diag_${currentSessionId.take(6)}.csv")`. It is disabled when `sessionEvents.isEmpty()`.

---

## 6. In-Memory Data Structures

Plain Kotlin data classes — no Room annotations. These will be promoted to Room entities in Phase 2.

**Create:** `app/src/main/kotlin/org/fossify/keyboard/models/KeyTimingEvent.kt`

```kotlin
data class KeyTimingEvent(
    val sessionId: String,
    val timestamp: Long,
    val ikdMs:     Long,    // -1 = first event
    val dwellMs:   Long,    // -1 = unknown
    val flightMs:  Long     // -1 = first event
)
```

**Create:** `app/src/main/kotlin/org/fossify/keyboard/models/SensorReadingEvent.kt`

```kotlin
data class SensorReadingEvent(
    val timestamp:  Long,
    val sensorType: String,   // "GYRO" or "ACCEL"
    val x: Float,
    val y: Float,
    val z: Float
)
```

No DAOs, no database, no Gradle dependencies added.

---

## 7. Files to Create / Modify

### Create

| File | What it contains |
|---|---|
| `models/KeyTimingEvent.kt` | Plain data class — one entry per keypress |
| `models/SensorReadingEvent.kt` | Plain data class — one sensor reading |
| `helpers/KinematicSensorHelper.kt` | Gyro + accel listener |
| `activities/DiagnosticsActivity.kt` | The diagnostic screen |
| `res/layout/activity_diagnostics.xml` | Screen layout |

### Modify

| File | Change |
|---|---|
| `activities/SettingsActivity.kt` | Add "Diagnostics" navigation row under a Developer section |
| `res/values/strings.xml` | Strings for the diagnostics screen |
| `AndroidManifest.xml` | Register `DiagnosticsActivity` |

### Do NOT touch

| File | Why |
|---|---|
| `SimpleKeyboardIME.kt` | Phase 2 only |
| `MyKeyboardView.kt` | Phase 2 only |
| `ClipsDatabase.kt` | No database this phase |
| Any existing model or DAO | No changes to existing data layer |

---

## 8. Acceptance Criteria

### Screen
- [ ] "Diagnostics" row appears in Settings and opens the screen
- [ ] Screen survives rotation without losing the event list or resetting displays

### Touch Dynamics
- [ ] Typing in the `EditText` updates IKD, dwell, and flight labels after each keypress
- [ ] First keypress shows `—` for IKD and flight (no prior reference)
- [ ] IKD values are plausible: 50 ms – 1500 ms for normal typing pace
- [ ] Dwell values are plausible: 40 ms – 300 ms
- [ ] **[New]** resets all labels to `—` and the event counter to `0`

### Sensors
- [ ] Gyro X/Y/Z bars visibly react when the device is tilted
- [ ] Accelerometer Y bar reads near full when device is held upright (~9.8 m/s²)
- [ ] On a device with no gyroscope the gyro section is hidden — no crash
- [ ] Sensor bars stop updating when the activity goes to background (`onPause`)
- [ ] Sensor bars resume when the activity returns to foreground (`onResume`)

### Export
- [ ] **[Save]** is disabled when no events have been recorded
- [ ] Tapping **[Save]** opens a file-save picker with a pre-filled filename
- [ ] The exported file contains correct CSV headers and one row per keypress
- [ ] Sensor readings block is present in the same file
- [ ] Timing values in the file match what was shown on screen during the session
