# InterKeyDelay (IKD) Implementation Plan

This document explains how this keyboard project is structured, where to find each component, and provides a step-by-step plan for adding IKD data collection.

---

## Part 1 — Project Map (What You Need to Know)

### How the project is organized

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

This is the most important thing to understand. When you tap a key on the keyboard, this happens:

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
│    └── Shows key preview popup                   │     (triggers vibration/sound)
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
│  If code is a letter/number/symbol:              │
│    └── inputConnection.commitText(char, 1)       │  ← Text appears in the app
│                                                  │
│  If code is DELETE:                              │
│    └── inputConnection.deleteSurroundingText()   │
│                                                  │
│  If code is SHIFT:                               │
│    └── Toggle shift state                        │
│                                                  │
│  If code is ENTER:                               │
│    └── Send enter key event                      │
└──────────────────────────────────────────────────┘
```

### The listener interface that connects everything

`OnKeyboardActionListener` (in `interfaces/OnKeyboardActionListener.kt`) is the bridge:

| Method | When it fires | What it does |
|---|---|---|
| `onPress(primaryCode)` | Finger touches key (ACTION_DOWN) | Vibration/sound feedback |
| `onKey(code)` | Finger lifts off key (ACTION_UP) | **Processes the key — this is where text gets committed** |
| `onActionUp()` | After onKey | Cleanup (e.g., switch back to letters after typing a symbol) |
| `onText(text)` | Clipboard paste, emoji tap | Commit a whole string |

`SimpleKeyboardIME` implements this interface. `MyKeyboardView` holds a reference to it and calls these methods.

### The database (Room)

Currently there is one database (`clips.db`, version 1) with one table:

| Table | Entity class | Fields |
|---|---|---|
| `clips` | `Clip.kt` | `id: Long`, `value: String` |

Access: `context.clipsDB.ClipsDao().getClips()`

### Settings storage

Settings are stored in `SharedPreferences` via `Config.kt`. Access pattern:

```kotlin
context.config.vibrateOnKeypress   // Boolean
context.config.keyboardLanguage    // Int
```

Adding a new setting means: add a property to `Config.kt`, add the preference key to `Constants.kt`, and add a UI toggle in `SettingsActivity.kt`.

### Existing export/import pattern

`ManageClipboardItemsActivity.kt` already has export/import:
- Export: reads clips from DB → serializes to JSON with Gson → writes to file via SAF
- Import: reads file → parses JSON → inserts into DB

You can follow this exact pattern for IKD data export.

---

## Part 2 — InterKeyDelay (IKD) Implementation Plan

### What is IKD?

InterKeyDelay is the time between consecutive key presses. For example, if you type "hello":
- h pressed at T=0ms
- e pressed at T=150ms → IKD = 150ms
- l pressed at T=280ms → IKD = 130ms
- l pressed at T=400ms → IKD = 120ms
- o pressed at T=520ms → IKD = 120ms

### Where to capture IKD

**Best hook point: `SimpleKeyboardIME.onKey(code)`** (line ~254 in SimpleKeyboardIME.kt)

This is called every time a key is committed. You add two lines at the very start of this method:

```kotlin
val now = SystemClock.uptimeMillis()
val ikd = if (lastKeyTimestamp > 0) now - lastKeyTimestamp else -1
lastKeyTimestamp = now
```

That gives you the inter-key delay for every single keystroke.

**Alternative: `onPress(primaryCode)`** (line ~193) — fires on finger-down instead of finger-up. Choose based on your research definition of IKD.

### Step-by-step implementation plan

#### Phase 1 — Create the data model (files to create/modify)

**Step 1.1 — Create the IKD event entity**

Create new file: `app/src/main/kotlin/org/fossify/keyboard/models/IkdEvent.kt`

```kotlin
@Entity(tableName = "ikd_events")
data class IkdEvent(
    @PrimaryKey(autoGenerate = true) val id: Long? = null,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long,        // absolute time (uptimeMillis)
    @ColumnInfo(name = "key_code") val keyCode: Int,            // the key that was pressed
    @ColumnInfo(name = "inter_key_delay_ms") val interKeyDelayMs: Long,  // -1 for first key in session
    @ColumnInfo(name = "is_repeat") val isRepeat: Boolean = false
)
```

**Step 1.2 — Create the DAO**

Create new file: `app/src/main/kotlin/org/fossify/keyboard/interfaces/IkdDao.kt`

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

**Step 1.3 — Create a separate IKD database**

Create new file: `app/src/main/kotlin/org/fossify/keyboard/databases/IkdDatabase.kt`

> Use a **separate database** instead of modifying ClipsDatabase. This avoids migration complexity and keeps IKD data independent.

```kotlin
@Database(entities = [IkdEvent::class], version = 1)
abstract class IkdDatabase : RoomDatabase() {
    abstract fun ikdDao(): IkdDao

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

**Step 1.4 — Add extension property for easy access**

Modify: `app/src/main/kotlin/org/fossify/keyboard/extensions/ContextExt.kt`

```kotlin
val Context.ikdDB: IkdDatabase get() = IkdDatabase.getInstance(applicationContext)
```

---

#### Phase 2 — Add the IKD setting

**Step 2.1 — Add preference key**

Modify: `app/src/main/kotlin/org/fossify/keyboard/helpers/Constants.kt`

```kotlin
const val IKD_COLLECTION_ENABLED = "ikd_collection_enabled"
```

**Step 2.2 — Add config property**

Modify: `app/src/main/kotlin/org/fossify/keyboard/helpers/Config.kt`

```kotlin
var ikdCollectionEnabled: Boolean
    get() = prefs.getBoolean(IKD_COLLECTION_ENABLED, false)
    set(ikdCollectionEnabled) = prefs.edit().putBoolean(IKD_COLLECTION_ENABLED, ikdCollectionEnabled).apply()
```

**Step 2.3 — Add toggle in settings UI**

Modify: `app/src/main/kotlin/org/fossify/keyboard/activities/SettingsActivity.kt`
(Follow the pattern of existing toggles like `vibrateOnKeypress`)

---

#### Phase 3 — Capture IKD in the IME service

**Step 3.1 — Add state variables**

Modify: `app/src/main/kotlin/org/fossify/keyboard/services/SimpleKeyboardIME.kt`

Add member variables near line ~152 (where `lastShiftPressTS` is):

```kotlin
// IKD tracking
private var lastKeyTimestamp = 0L
private var currentSessionId = ""
private var ikdCollectionActive = false
```

**Step 3.2 — Start/end sessions**

Override `onStartInput()` (already exists at line ~198) — add at the start:

```kotlin
// Start a new IKD session
if (config.ikdCollectionEnabled) {
    currentSessionId = UUID.randomUUID().toString()
    lastKeyTimestamp = 0L
    ikdCollectionActive = true
}
```

Optionally override `onFinishInput()` (not currently overridden) to mark session end:

```kotlin
override fun onFinishInput() {
    super.onFinishInput()
    ikdCollectionActive = false
    lastKeyTimestamp = 0L
}
```

**Step 3.3 — Record IKD on every key press**

At the top of `onKey(code)` (line ~254), add:

```kotlin
if (ikdCollectionActive) {
    val now = SystemClock.uptimeMillis()
    val ikd = if (lastKeyTimestamp > 0L) now - lastKeyTimestamp else -1L
    lastKeyTimestamp = now

    // Write to database on background thread
    Thread {
        try {
            ikdDB.ikdDao().insert(
                IkdEvent(
                    sessionId = currentSessionId,
                    timestamp = now,
                    keyCode = code,
                    interKeyDelayMs = ikd
                )
            )
        } catch (_: Exception) { }
    }.start()
}
```

> **Important:** Database writes MUST happen off the main thread. Use `Thread { }`, a coroutine, or an `ExecutorService`. The keyboard must never lag.

---

#### Phase 4 — Export IKD data

**Step 4.1 — Create an export activity or add to existing settings**

Follow the pattern in `ManageClipboardItemsActivity.kt`:

1. Add a button in `SettingsActivity` → "Export IKD Data"
2. Use SAF (`ACTION_CREATE_DOCUMENT`) to pick a save location
3. Serialize all `IkdEvent` rows to JSON or CSV
4. Write to the output stream

Example CSV format:
```csv
session_id,timestamp,key_code,inter_key_delay_ms,is_repeat
abc-123,1645200000,104,0,false
abc-123,1645200150,101,150,false
abc-123,1645200280,108,130,false
```

> **Privacy note:** Do NOT include the actual character typed if you export key_code. For research, you may want to record only the IKD values and anonymize the key codes.

**Step 4.2 — Add a "Clear IKD Data" button**

In the same settings area, add a button that calls `ikdDB.ikdDao().deleteAll()`.

---

#### Phase 5 — Testing

1. **Build and install:** `./gradlew installCoreDebug`
2. **Enable IKD collection** in the app settings
3. **Type in any app** — the keyboard collects data silently
4. **Export the data** from settings
5. **Verify the CSV/JSON** contains correct timestamps and IKD values
6. **Performance check:** typing should feel exactly the same — no lag or stutter. If there is lag, switch to batched writes (collect events in memory, flush every N seconds).

---

### Files you will create

| File | Purpose |
|---|---|
| `models/IkdEvent.kt` | Room entity for IKD data |
| `interfaces/IkdDao.kt` | Room DAO for IKD queries |
| `databases/IkdDatabase.kt` | Separate Room database for IKD |

### Files you will modify

| File | What to change |
|---|---|
| `helpers/Constants.kt` | Add `IKD_COLLECTION_ENABLED` key |
| `helpers/Config.kt` | Add `ikdCollectionEnabled` property |
| `extensions/ContextExt.kt` | Add `ikdDB` extension property |
| `services/SimpleKeyboardIME.kt` | Add IKD capture in `onKey()`, session management |
| `activities/SettingsActivity.kt` | Add toggle + export/clear buttons |
| `res/values/strings.xml` | Add UI strings for the new settings |

### Files you will NOT modify

| File | Why not |
|---|---|
| `MyKeyboardView.kt` | Touch handling stays the same — capture happens in the service |
| `MyKeyboard.kt` | Key model stays the same |
| `ClipsDatabase.kt` | Separate database avoids migration issues |
| `OnKeyboardActionListener.kt` | Interface stays the same — no new callbacks needed |

---

## Part 3 — Quick Reference for Development

### Build and install

```bash
cd "/media/rmca/QuickStorage/PROJETOS GITHUB/KeyboardSA/Keyboard_SA_IKD"
./gradlew installCoreDebug
```

### View logs from the keyboard service

```bash
adb logcat | grep -i "keyboard\|ikd\|fossify"
```

### Database inspection

```bash
# Pull the database file from the device
adb exec-out run-as org.fossify.keyboard cat databases/ikd.db > ikd.db
# Open with any SQLite viewer
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

### Android Studio tips for beginners

- **Ctrl+Click** on any class or method name → jumps to its definition
- **Ctrl+Shift+F** → search all files in the project
- **Alt+Enter** on an error → shows quick-fix suggestions
- **Shift+F10** → run/install on device
- **Ctrl+F9** → build without running
- **Logcat panel** (bottom) → shows device logs in real-time
- If Gradle sync fails → **File → Invalidate Caches and Restart**
