# Behavioral Analytics — Implementation Plan

This document explains how the keyboard project is structured and provides a step-by-step implementation plan aligned with the phases defined in `ROADMAP/FeatureRoadmap.md`.

---

## Part 1 — Project Map (What You Need to Know)

### How the project is organized (current state)

```
app/src/main/kotlin/org/fossify/keyboard/
│
├── services/
│   └── SimpleKeyboardIME.kt        ← THE KEYBOARD SERVICE (runs when you type)
│
├── views/
│   └── MyKeyboardView.kt           ← THE KEYBOARD UI (draws keys, handles touch)
│
├── helpers/
│   ├── MyKeyboard.kt               ← Keyboard model (key definitions, XML layout parser)
│   ├── Config.kt                   ← User settings (SharedPreferences wrapper)
│   ├── Constants.kt                ← All constants, preference keys, language IDs
│   ├── KeyboardFeedbackManager.kt  ← Vibration and sound on keypress
│   ├── ClipsHelper.kt              ← Clipboard helper
│   ├── EmojiHelper.kt              ← Emoji loading
│   └── AccessHelper.kt             ← Accessibility / TalkBack support
│
├── interfaces/
│   ├── OnKeyboardActionListener.kt ← KEY INTERFACE — connects the View to the Service
│   ├── ClipsDao.kt                 ← Room DAO for clipboard
│   └── RefreshClipsListener.kt
│
├── databases/
│   └── ClipsDatabase.kt            ← Room database (currently only clips)
│
├── models/
│   ├── Clip.kt                     ← Clipboard item entity
│   └── ListItem.kt                 ← Base model for lists
│
├── activities/
│   ├── MainActivity.kt             ← Main screen (enable keyboard instructions)
│   ├── SettingsActivity.kt         ← Settings UI
│   ├── ManageClipboardItemsActivity.kt ← Clipboard management + export/import
│   ├── SimpleActivity.kt           ← Base activity class
│   └── SplashActivity.kt           ← Launcher splash
│
├── adapters/                        ← RecyclerView adapters for lists/grids
├── dialogs/                         ← Dialog helpers (add clip, switch language, etc.)
├── extensions/                      ← Kotlin extension functions
│
└── App.kt                          ← Application class (emoji init)
```

### How a key press flows through the code

```
YOUR FINGER TOUCHES THE SCREEN
        │
        ▼
┌──────────────────────────────────────────────────┐
│  MyKeyboardView.onTouchEvent()                   │  ← Entry point
│    └── onModifiedTouchEvent()                    │
│                                                  │
│  ACTION_DOWN (finger touches):                   │
│    ├── Records mDownTime = event.eventTime       │
│    ├── Finds which key was touched               │
│    ├── Calls listener.onPress(keyCode)  ─────────┼──► SimpleKeyboardIME.onPress()
│    └── Shows key preview popup                   │     (vibration/sound + dwell start)
│                                                  │
│  ACTION_UP (finger lifts):                       │
│    ├── Debounce check (70ms minimum)             │
│    ├── detectAndSendKey()                        │
│    │   └── Calls listener.onKey(keyCode) ────────┼──► SimpleKeyboardIME.onKey()
│    └── Calls listener.onActionUp()  ─────────────┼──► SimpleKeyboardIME.onActionUp()
└──────────────────────────────────────────────────┘
        │
        ▼
┌──────────────────────────────────────────────────┐
│  SimpleKeyboardIME.onKey(code)                   │
│                                                  │
│  IKD  = now − lastKeyUpTimestamp                 │  ← Inter-Key Delay
│  Dwell = now − lastKeyDownTimestamp              │  ← Dwell Time
│  Flight = lastKeyDownTimestamp − lastKeyUpTS     │  ← Flight Time (prev up → this down)
│                                                  │
│  If code is a letter/number/symbol:              │
│    └── inputConnection.commitText(char, 1)       │
│  If code is DELETE:                              │
│    └── inputConnection.deleteSurroundingText()   │
└──────────────────────────────────────────────────┘
```

### Timing metrics defined

| Metric | Measurement | Captured in |
|---|---|---|
| **Key Hold Time (Dwell Time)** | Duration a key is held down (ACTION_DOWN → ACTION_UP for same key) | `onPress()` start, `onKey()` end |
| **Flight Time** | Time interval between key release and next key press (prev ACTION_UP → next ACTION_DOWN) | `onKey()` end, next `onPress()` start |
| **Inter-Key Delay (IKD)** | Time between consecutive key commits (prev ACTION_UP → current ACTION_UP) | `onKey()` to `onKey()` |
| **Typing Speed** | Measured via statistical distribution of flight times | Backend / Phase 3 Analytics |
| **Error Rates** | Frequency of backspace / corrections | `onKey(DELETE)` |

### The database (Room)

Currently there is one database (`clips.db`, version 1) with one table:

| Table | Entity class | Fields |
|---|---|---|
| `clips` | `Clip.kt` | `id: Long`, `value: String` |

The analytics data will live in a **separate database** (`ikd.db`) to avoid migration complexity.

### Settings storage

Settings are stored in `SharedPreferences` via `Config.kt`. Access pattern:

```kotlin
context.config.vibrateOnKeypress   // Boolean
context.config.keyboardLanguage    // Int
```

Adding a new setting means: add a property to `Config.kt`, add the preference key to `Constants.kt`, and add a UI toggle in `SettingsActivity.kt`.

---

## Part 2 — Implementation Plan

The phases below map directly to the Roadmap phases.

---

### Phase 1: Sensor Calibration & Debug Environment
**Status: Complete (2026-05-01) — BUILD SUCCESSFUL**

**Roadmap objective:** A controlled, isolated diagnostic screen inside the companion app where developers can type and observe all sensor metrics live before deploying to the background keyboard.

---

#### Step 1.1 — In-memory data models ✅ COMPLETE

Phase 1 uses plain Kotlin data classes — no Room annotations. These will be promoted to Room entities in Phase 2.

**`models/KeyTimingEvent.kt`** (created):
```kotlin
data class KeyTimingEvent(
    val sessionId: String,
    val timestamp: Long,
    val ikdMs:     Long,    // -1 = first event
    val dwellMs:   Long,    // -1 = unknown
    val flightMs:  Long     // -1 = first event
)
```

**`models/SensorReadingEvent.kt`** (created):
```kotlin
data class SensorReadingEvent(
    val timestamp:  Long,
    val sensorType: String,   // "GYRO" or "ACCEL"
    val x: Float,
    val y: Float,
    val z: Float
)
```

---

#### Step 1.2 — DAOs (deferred to Phase 2)

Room DAOs (`IkdDao`, `SensorDao`) are not created in Phase 1. All data lives in `MutableList` in memory for the duration of the `DiagnosticsActivity` session.

---

#### Step 1.3 — Analytics database (deferred to Phase 2)

`IkdDatabase`, `ContextExt.ikdDB`, and Room entities are Phase 2 work. Phase 1 has zero new Gradle dependencies.

---

#### Step 1.4 — Kinematic sensor helper ✅ COMPLETE

**File:** `app/src/main/kotlin/org/fossify/keyboard/helpers/KinematicSensorHelper.kt`

Implemented as a `SensorEventListener` wrapping both gyroscope and accelerometer. Key differences from the original plan:
- API is `start()` / `stop()` (no `sessionId` arg — the activity owns session identity)
- Emits `SensorReadingEvent` (plain data class, not Room entity) via `onSample` callback
- Callbacks fire on the sensor thread; `DiagnosticsActivity` posts UI updates via `runOnUiThread`
- `hasGyro` and `hasAccel` properties let the caller hide unavailable sensor sections

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
    fun start()  { gyro?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
                   accel?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) } }
    fun stop()   { manager.unregisterListener(this) }
    override fun onSensorChanged(event: SensorEvent) { /* emits SensorReadingEvent */ }
    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
}
```

---

#### Step 1.5 — Diagnostics activity ✅ COMPLETE

**Files:**
- `app/src/main/kotlin/org/fossify/keyboard/activities/DiagnosticsActivity.kt`
- `app/src/main/res/layout/activity_diagnostics.xml`
- `app/src/main/res/menu/menu_diagnostics.xml`

`DiagnosticsActivity` extends `SimpleActivity` and uses view binding (`ActivityDiagnosticsBinding`).

**Touch timing** — `OnTouchListener` on `MyEditText`:
```kotlin
ACTION_DOWN  → pressDownTime = SystemClock.uptimeMillis()
ACTION_UP    → dwell  = now - pressDownTime
               flight = if (lastReleaseTime > 0) pressDownTime - lastReleaseTime else -1
               ikd    = if (lastReleaseTime > 0) now - lastReleaseTime else -1
               lastReleaseTime = now
```
Returns `false` so `EditText` still receives text input.

**Sensor display** — six `ProgressBar` + `MyTextView` pairs (gyroX/Y/Z, accelX/Y/Z). Progress mapping:
- Gyro: `((v + 10f) / 20f * 100).toInt().coerceIn(0, 100)`
- Accel: `(v / 20f * 100).toInt().coerceIn(0, 100)`

Gyro section (`diagnostics_gyro_section_label` + `diagnostics_gyro_container`) is set to `GONE` if `!sensorHelper.hasGyro`.

**Session lifecycle:**
- `onResume` → `sensorHelper.start()`
- `onPause` → `sensorHelper.stop()`
- `onSaveInstanceState` persists `sessionId`, `pressDownTime`, `lastReleaseTime`, `eventCount`

**Toolbar menu** (`menu_diagnostics.xml`):
- `diagnostics_new_session` — clears lists, resets labels, generates new `UUID`, shown `ifRoom` with `ic_plus_vector`
- `diagnostics_save_csv` — overflow; disabled when `sessionEvents.isEmpty()`

**Navigation:** `SettingsActivity.setupDiagnostics()` sets a click listener on `settingsDiagnosticsHolder` that starts `DiagnosticsActivity`. The holder and `settingsDeveloperSectionLabel` are in `activity_settings.xml` and the label is included in the primary-color array in `SettingsActivity.onResume()`.

---

#### Step 1.6 — CSV export ✅ COMPLETE

Uses `ActivityResultContracts.CreateDocument("text/csv")`. Filename: `ikd_session_<first 8 chars of UUID>.csv`.

Single file, two blocks:
```
session_id,timestamp_ms,ikd_ms,dwell_ms,flight_ms
<keystroke rows>

#sensor_readings
session_id,timestamp_ms,sensor_type,x,y,z
<sensor rows>
```

`-1` in `ikd_ms` / `flight_ms` = first event in session (no prior reference). The Save menu item is disabled until `sessionEvents` is non-empty.

---

### Phase 1.1: Live Keyboard Data Validation & Metric Alignment
**Status: Not started**

**Roadmap objective:** Move capturing to the actual `SimpleKeyboardIME` interface to collect real live typing data instead of diagnostic dummy inputs. Realign captured models to match BiAffect methodologies (Hold Time, Flight Time sequences, Error Rate/Backspace integration, explicit orientation capture).

Detailed scope: [`Phase1/Phase1.1_Plan.md`](Phase1/Phase1.1_Plan.md)

#### Step 1.1.1 — Update Internal Models
Update the `KeyTimingEvent` to match the new definitions:
- Swap `dwellMs` to `holdTimeMs`
- Update flight time calculation to match the correct standard interval between downward strokes.

#### Step 1.1.2 — Live Key Capture Sandbox
Hook directly into `MyKeyboardView`'s live production text flow, and confirm metrics mirror natural typing. Establish the baseline for how Error Rates (BACKSPACE) are reported vs a standard space-delimited typing burst, and prepare the models for Phase 2's SQLite backend.

---

### Phase 2: Unobtrusive Background Collection & Secure Local Storage
**Status: Not started**

**Roadmap objective:** Passive, privacy-respecting collection inside the live keyboard with zero impact on typing experience.

---

#### Step 2.1 — Add the collection setting

**Modify:** `app/src/main/kotlin/org/fossify/keyboard/helpers/Constants.kt`

```kotlin
const val IKD_COLLECTION_ENABLED = "ikd_collection_enabled"
```

**Modify:** `app/src/main/kotlin/org/fossify/keyboard/helpers/Config.kt`

```kotlin
var ikdCollectionEnabled: Boolean
    get() = prefs.getBoolean(IKD_COLLECTION_ENABLED, false)
    set(ikdCollectionEnabled) = prefs.edit().putBoolean(IKD_COLLECTION_ENABLED, ikdCollectionEnabled).apply()
```

**Modify:** `app/src/main/kotlin/org/fossify/keyboard/activities/SettingsActivity.kt`

Add a toggle following the pattern of `vibrateOnKeypress`. Add the corresponding string to `res/values/strings.xml`.

---

#### Step 2.2 — Lifecycle-aware session management in the IME service

**Modify:** `app/src/main/kotlin/org/fossify/keyboard/services/SimpleKeyboardIME.kt`

Add member variables near the existing `lastShiftPressTS`:

```kotlin
// Behavioral analytics tracking
private var lastKeyUpTimestamp = 0L
private var lastKeyDownTimestamp = 0L
private var currentSessionId = ""
private var ikdCollectionActive = false
private var kinematicHelper: KinematicSensorHelper? = null
```

In `onStartInput()` (already exists) — sensors wake up when keyboard appears:

```kotlin
if (config.ikdCollectionEnabled) {
    currentSessionId = UUID.randomUUID().toString()
    lastKeyUpTimestamp = 0L
    lastKeyDownTimestamp = 0L
    ikdCollectionActive = true
    kinematicHelper = KinematicSensorHelper(this) { sample ->
        Thread { try { ikdDB.sensorDao().insert(sample) } catch (_: Exception) {} }.start()
    }
    kinematicHelper?.startSession(currentSessionId)
}
```

Override `onFinishInput()` — sensors power down when keyboard hides:

```kotlin
override fun onFinishInput() {
    super.onFinishInput()
    ikdCollectionActive = false
    lastKeyUpTimestamp = 0L
    lastKeyDownTimestamp = 0L
    kinematicHelper?.stopSession()
    kinematicHelper = null
}
```

---

#### Step 2.3 — Privacy-first keystroke event capture

**In `onPress(primaryCode)`** — record the finger-down moment for dwell and flight calculation:

```kotlin
if (ikdCollectionActive) {
    lastKeyDownTimestamp = SystemClock.uptimeMillis()
}
```

**At the top of `onKey(code)`** — calculate all three metrics and persist asynchronously:

```kotlin
if (ikdCollectionActive) {
    val now = SystemClock.uptimeMillis()
    val ikd = if (lastKeyUpTimestamp > 0L) now - lastKeyUpTimestamp else -1L
    val dwell = if (lastKeyDownTimestamp > 0L) now - lastKeyDownTimestamp else -1L
    val flight = if (lastKeyUpTimestamp > 0L && lastKeyDownTimestamp > lastKeyUpTimestamp)
        lastKeyDownTimestamp - lastKeyUpTimestamp else -1L
    lastKeyUpTimestamp = now

    // Privacy filter: log event category, not the actual character
    val eventCategory = when {
        code in 97..122 || code in 65..90 -> "ALPHA"
        code in 48..57 -> "DIGIT"
        code == 32 -> "SPACE"
        code == -5 -> "BACKSPACE"
        code == -4 -> "ENTER"
        else -> "OTHER"
    }

    Thread {
        try {
            ikdDB.ikdDao().insert(
                IkdEvent(
                    sessionId = currentSessionId,
                    timestamp = now,
                    keyCode = code,          // see privacy note in export step
                    interKeyDelayMs = ikd,
                    dwellTimeMs = dwell,
                    flightTimeMs = flight
                )
            )
        } catch (_: Exception) { }
    }.start()
}
```

> **Privacy rule:** The `key_code` integer is stored, but on export only the `eventCategory` label is included. The raw character is never reconstructed or stored.

> **Performance rule:** Database writes MUST happen off the main thread. Use `Thread { }`, a coroutine, or an `ExecutorService`. The keyboard must never lag.

---

#### Step 2.4 — Export and clear IKD data

**Modify:** `app/src/main/kotlin/org/fossify/keyboard/activities/SettingsActivity.kt`

Add two buttons in the settings screen:

1. **"Export Analytics Data"** — triggers SAF `ACTION_CREATE_DOCUMENT`, serializes `ikd_events` and `sensor_samples` to CSV (with the privacy filter applied — category labels, not raw key codes).
2. **"Clear Analytics Data"** — calls `ikdDB.ikdDao().deleteAll()` and `ikdDB.sensorDao().deleteAll()` after user confirmation.

Follow the existing export/import pattern from `ManageClipboardItemsActivity.kt`.

---

#### Step 2.5 — Phase 2 testing checklist

1. `./gradlew installCoreDebug`
2. Enable collection in app settings
3. Type in any app — verify no lag or stutter
4. Export data — verify CSV contains correct timestamps, IKD, dwell, flight values
5. Verify sensor CSV has gyro/accel samples
6. Disable keyboard → re-enable → confirm new session ID is generated
7. If any lag is observed: switch to batched writes (collect events in a `ConcurrentLinkedQueue`, flush every 5 seconds via a background thread)

---

### Phase 3: User Insights & Dashboard Presentation
**Status: Not started**

**Roadmap objective:** Translate raw behavioral data into digestible visual insights in the companion app.

---

#### Step 3.1 — Data aggregation engine

**Create:** `app/src/main/kotlin/org/fossify/keyboard/helpers/IkdAggregator.kt`

This class reads from `IkdDatabase` and computes daily and weekly summaries. It runs entirely off the main thread (via coroutines or a background executor).

Metrics to compute:

| Metric | Calculation |
|---|---|
| Words per minute (WPM) | Count SPACE events per session ÷ session duration in minutes |
| Average IKD | Mean of all `interKeyDelayMs` values per day |
| Average dwell time | Mean of all `dwellTimeMs` values per day |
| Backspace frequency | Count of `BACKSPACE` events ÷ total events per session |
| Error rate proxy | Backspace frequency as a ratio |
| Session count | Distinct `session_id` values per day |
| Total typing time | Sum of session durations per day |

Aggregated results should be stored in a lightweight in-memory model (or a summary table if querying raw data becomes slow) to feed the dashboard.

---

#### Step 3.2 — Dashboard activity

**Create:** `app/src/main/kotlin/org/fossify/keyboard/activities/DashboardActivity.kt`

The main insights screen. Add a navigation entry from `MainActivity`.

Contains three visual modules (Step 3.3) and an optional mood overlay (Step 3.4).

---

#### Step 3.3 — Visual analytics modules

Use a charting library (e.g., [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart) or the Compose equivalent) for rendering graphs.

**Typing Rhythm Trends**
- Line chart: average WPM and average IKD over time (last 7 / 30 days)
- Goal: establish a personal baseline and highlight deviations
- Data source: daily aggregates from `IkdAggregator`

**Cognitive Fatigue Heatmap**
- Heatmap or bar chart: backspace frequency and error rate by hour of day / day of week
- Elevated backspace rates signal low focus or fatigue
- Data source: hourly breakdown from `IkdAggregator`

**Circadian Usage Patterns**
- Radial or bar chart: session start times distributed across 24 hours
- Flag sessions starting after 22:00 as late-night usage (potential sleep disruption signal)
- Data source: session timestamps from `IkdDatabase`

---

#### Step 3.4 — Subjective context overlay (optional)

**Create:** a daily mood/energy check-in prompt, shown once per day when the user opens the companion app.

```kotlin
@Entity(tableName = "mood_entries")
data class MoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long? = null,
    @ColumnInfo(name = "date") val date: String,         // "YYYY-MM-DD"
    @ColumnInfo(name = "mood_score") val moodScore: Int,  // 1–5
    @ColumnInfo(name = "energy_score") val energyScore: Int // 1–5
)
```

Add `MoodEntry` as an entity to `IkdDatabase` (increment database version + add migration).

On the dashboard, overlay mood/energy scores as a secondary line on the Typing Rhythm Trends chart to let users correlate subjective state with objective typing behavior.

---

## Part 3 — Files Summary

### Files to create

| File | Phase | Purpose |
|---|---|---|
| `models/IkdEvent.kt` | 1 | Room entity — keystroke timing event |
| `models/SensorSample.kt` | 1 | Room entity — gyroscope / accelerometer sample |
| `models/MoodEntry.kt` | 3 | Room entity — daily mood/energy check-in |
| `interfaces/IkdDao.kt` | 1 | Room DAO for keystroke events |
| `interfaces/SensorDao.kt` | 1 | Room DAO for sensor samples |
| `databases/IkdDatabase.kt` | 1 | Separate Room database (`ikd.db`) |
| `helpers/KinematicSensorHelper.kt` | 1 | Gyroscope + accelerometer wrapper |
| `helpers/IkdAggregator.kt` | 3 | Daily/weekly metric aggregation |
| `activities/DiagnosticsActivity.kt` | 1 | Live sensor debug screen |
| `activities/DashboardActivity.kt` | 3 | User insights dashboard |

### Files to modify

| File | Phase | What to change |
|---|---|---|
| `helpers/Constants.kt` | 2 | Add `IKD_COLLECTION_ENABLED` key |
| `helpers/Config.kt` | 2 | Add `ikdCollectionEnabled` property |
| `extensions/ContextExt.kt` | 1 | Add `ikdDB` extension property |
| `services/SimpleKeyboardIME.kt` | 2 | Add IKD/dwell/flight capture in `onPress()` + `onKey()`, session lifecycle |
| `activities/SettingsActivity.kt` | 2 | Add collection toggle + export/clear buttons |
| `activities/MainActivity.kt` | 1, 3 | Add navigation to Diagnostics and Dashboard |
| `res/values/strings.xml` | 1, 2, 3 | UI strings for all new screens |

### Files NOT to modify

| File | Why not |
|---|---|
| `MyKeyboardView.kt` | Touch handling stays the same — timing is captured in the service via `onPress`/`onKey` |
| `MyKeyboard.kt` | Key model stays the same |
| `ClipsDatabase.kt` | Separate database avoids migration issues |
| `OnKeyboardActionListener.kt` | Interface stays the same — no new callbacks needed |

---

## Part 4 — Quick Reference for Development

### Build and install

```bash
./gradlew installCoreDebug
```

### View logs from the keyboard service

```bash
adb logcat | grep -i "keyboard\|ikd\|fossify"
```

### Database inspection

```bash
# Pull the database from the device
adb exec-out run-as org.fossify.keyboard cat databases/ikd.db > ikd.db
# Open with any SQLite viewer (e.g. DB Browser for SQLite)
```

### Key codes reference

| Code | Key |
|---|---|
| `32` | Space |
| `65–90` | A–Z (uppercase) |
| `97–122` | a–z (lowercase) |
| `48–57` | 0–9 |
| `-1` | Shift |
| `-2` | Mode change (letters ↔ symbols) |
| `-4` | Enter |
| `-5` | Delete (backspace) |
| `-6` | Emoji / Language switch |

### Android Studio tips

- **Ctrl+Click** on any class or method → jumps to its definition
- **Ctrl+Shift+F** → search all files
- **Alt+Enter** on an error → quick-fix suggestions
- **Shift+F10** → run/install on device
- **Ctrl+F9** → build without running
- **Logcat panel** (bottom) → real-time device logs
- If Gradle sync fails → **File → Invalidate Caches and Restart**
