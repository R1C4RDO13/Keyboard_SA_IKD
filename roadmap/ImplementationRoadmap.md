# Behavioral Analytics — Implementation Plan

This document explains how the keyboard project is structured and provides a step-by-step implementation plan aligned with the three phases defined in `ROADMAP/Roadmap.md`.

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
| **Dwell Time** | Duration a key is held (ACTION_DOWN → ACTION_UP for same key) | `onPress()` start, `onKey()` end |
| **Flight Time** | Transition speed between keys (prev ACTION_UP → next ACTION_DOWN) | `onKey()` end, next `onPress()` start |
| **Inter-Key Delay (IKD)** | Time between consecutive key commits (ACTION_UP → ACTION_UP) | `onKey()` to `onKey()` |

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

The three phases below map directly to the Roadmap phases.

---

### Phase 1: Sensor Calibration & Debug Environment

**Roadmap objective:** A controlled, isolated diagnostic screen inside the companion app where developers can type and observe all sensor metrics live before deploying to the background keyboard.

---

#### Step 1.1 — Expand the data model

**Create:** `app/src/main/kotlin/org/fossify/keyboard/models/IkdEvent.kt`

```kotlin
@Entity(tableName = "ikd_events")
data class IkdEvent(
    @PrimaryKey(autoGenerate = true) val id: Long? = null,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "key_code") val keyCode: Int,
    @ColumnInfo(name = "inter_key_delay_ms") val interKeyDelayMs: Long,   // -1 for first key
    @ColumnInfo(name = "dwell_time_ms") val dwellTimeMs: Long,             // key hold duration
    @ColumnInfo(name = "flight_time_ms") val flightTimeMs: Long,           // -1 for first key
    @ColumnInfo(name = "is_repeat") val isRepeat: Boolean = false
)
```

**Create:** `app/src/main/kotlin/org/fossify/keyboard/models/SensorSample.kt`

```kotlin
@Entity(tableName = "sensor_samples")
data class SensorSample(
    @PrimaryKey(autoGenerate = true) val id: Long? = null,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "sensor_type") val sensorType: String,  // "GYRO" or "ACCEL"
    @ColumnInfo(name = "x") val x: Float,
    @ColumnInfo(name = "y") val y: Float,
    @ColumnInfo(name = "z") val z: Float
)
```

---

#### Step 1.2 — Create the DAOs

**Create:** `app/src/main/kotlin/org/fossify/keyboard/interfaces/IkdDao.kt`

```kotlin
@Dao
interface IkdDao {
    @Insert fun insert(event: IkdEvent)
    @Insert fun insertAll(events: List<IkdEvent>)
    @Query("SELECT * FROM ikd_events ORDER BY timestamp ASC") fun getAll(): List<IkdEvent>
    @Query("SELECT * FROM ikd_events WHERE session_id = :sessionId ORDER BY timestamp ASC") fun getBySession(sessionId: String): List<IkdEvent>
    @Query("SELECT DISTINCT session_id FROM ikd_events ORDER BY session_id") fun getSessions(): List<String>
    @Query("DELETE FROM ikd_events") fun deleteAll()
    @Query("SELECT COUNT(*) FROM ikd_events") fun getCount(): Long
}
```

**Create:** `app/src/main/kotlin/org/fossify/keyboard/interfaces/SensorDao.kt`

```kotlin
@Dao
interface SensorDao {
    @Insert fun insert(sample: SensorSample)
    @Insert fun insertAll(samples: List<SensorSample>)
    @Query("SELECT * FROM sensor_samples WHERE session_id = :sessionId ORDER BY timestamp ASC") fun getBySession(sessionId: String): List<SensorSample>
    @Query("DELETE FROM sensor_samples") fun deleteAll()
    @Query("SELECT COUNT(*) FROM sensor_samples") fun getCount(): Long
}
```

---

#### Step 1.3 — Create the analytics database

**Create:** `app/src/main/kotlin/org/fossify/keyboard/databases/IkdDatabase.kt`

```kotlin
@Database(entities = [IkdEvent::class, SensorSample::class], version = 1)
abstract class IkdDatabase : RoomDatabase() {
    abstract fun ikdDao(): IkdDao
    abstract fun sensorDao(): SensorDao

    companion object {
        private var db: IkdDatabase? = null

        fun getInstance(context: Context): IkdDatabase {
            if (db == null) {
                synchronized(IkdDatabase::class) {
                    db = Room.databaseBuilder(context.applicationContext, IkdDatabase::class.java, "ikd.db")
                        .build()
                }
            }
            return db!!
        }
    }
}
```

**Modify:** `app/src/main/kotlin/org/fossify/keyboard/extensions/ContextExt.kt`

```kotlin
val Context.ikdDB: IkdDatabase get() = IkdDatabase.getInstance(applicationContext)
```

---

#### Step 1.4 — Kinematic sensor helper

**Create:** `app/src/main/kotlin/org/fossify/keyboard/helpers/KinematicSensorHelper.kt`

This class wraps Android's `SensorManager` to subscribe to gyroscope and accelerometer events during a session.

```kotlin
class KinematicSensorHelper(
    private val context: Context,
    private val onSample: (SensorSample) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var sessionId: String = ""

    fun startSession(sessionId: String) {
        this.sessionId = sessionId
        val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyro?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        accel?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stopSession() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val type = when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> "GYRO"
            Sensor.TYPE_ACCELEROMETER -> "ACCEL"
            else -> return
        }
        onSample(SensorSample(
            sessionId = sessionId,
            timestamp = SystemClock.uptimeMillis(),
            sensorType = type,
            x = event.values[0], y = event.values[1], z = event.values[2]
        ))
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
}
```

---

#### Step 1.5 — Build the diagnostics activity

**Create:** `app/src/main/kotlin/org/fossify/keyboard/activities/DiagnosticsActivity.kt`

This is a dedicated screen where the developer types in a `EditText` while observing live metrics. The activity hosts its own `KinematicSensorHelper` and a lightweight keystroke tracker (not the live keyboard service — input comes from the soft keyboard already active on the device).

Key UI elements to build in the corresponding layout (`activity_diagnostics.xml`):
- `EditText` for typing input
- Real-time text labels: last IKD, last dwell time, last flight time, session event count
- Live `TextView` or simple bar indicators for gyroscope X/Y/Z and accelerometer X/Y/Z
- "Export Session" button → exports current session to CSV via SAF (`ACTION_CREATE_DOCUMENT`)
- "Clear" button → wipes the in-memory session buffer

The activity uses `TextWatcher` + manual timestamp tracking on `onTouchEvent` of the `EditText` to measure IKD, dwell, and flight time within the app itself (this is a diagnostic-only approximation; the live keyboard captures these more precisely via `onPress`/`onKey`).

Add a navigation entry from `MainActivity` or `SettingsActivity` to reach this screen.

---

#### Step 1.6 — Data validation export (Phase 1)

Export a raw diagnostic session to CSV for verifying data structure and timing accuracy. Follow the existing clipboard export pattern in `ManageClipboardItemsActivity.kt`:

```csv
session_id,timestamp,key_code,inter_key_delay_ms,dwell_time_ms,flight_time_ms,is_repeat
abc-123,1700000000000,104,-1,98,-1,false
abc-123,1700000000210,101,210,87,112,false
```

Sensor samples export to a separate CSV:

```csv
session_id,timestamp,sensor_type,x,y,z
abc-123,1700000000015,GYRO,0.012,-0.003,0.041
abc-123,1700000000015,ACCEL,0.21,9.78,-0.11
```

---

### Phase 2: Unobtrusive Background Collection & Secure Local Storage

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
