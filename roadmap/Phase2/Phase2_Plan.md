# Phase 2 — Persistent Local Storage & Privacy-First Collection

**Status:** Complete (2026-05-01)
**Depends on:** Phase 1.1 (IME-driven live capture)
**Scope:** Promote ephemeral in-memory capture to persistent local storage; add a user-controlled privacy switch on the keyboard top bar; expose a sessions browser with per-session export and deletion.

---

## Table of Contents

1. [Concept & Scope](#1-concept--scope)
2. [Storage Strategy & Rationale](#2-storage-strategy--rationale)
3. [Database Schema](#3-database-schema)
4. [Internal Sub-Phases](#4-internal-sub-phases)
   - [2.1 Storage Layer](#21-storage-layer)
   - [2.2 Privacy Mode & Keyboard Toggle](#22-privacy-mode--keyboard-toggle)
   - [2.3 IKD Settings Page](#23-ikd-settings-page)
   - [2.4 Sessions Browser](#24-sessions-browser)
   - [2.5 Report Management (Delete & Export)](#25-report-management-delete--export)
   - [2.6 Retention Policy & Storage Hygiene](#26-retention-policy--storage-hygiene)
5. [Files to Create / Modify](#5-files-to-create--modify)
6. [Acceptance Criteria](#6-acceptance-criteria)
7. [Decisions](#7-decisions)

---

## 1. Concept & Scope

Phase 1.1 captured live keyboard timing and sensor data into an in-memory store (`LiveCaptureSessionStore`). Data was lost on session end. Phase 2 makes capture **persistent and user-controlled**:

- Every keyboard open/close cycle becomes a stored session
- Users can flip a single switch on the keyboard to halt all collection (privacy mode)
- Users can browse, export, and delete past sessions
- A retention policy auto-deletes old sessions to bound storage growth

**In scope:**
- Room database (`ikd.db`) with three tables: sessions, key timing events, sensor samples
- In-memory write buffer with batched flush (perf-critical for high-rate sensor data)
- Privacy mode toggle on the keyboard's top toolbar (default: privacy ON, no collection)
- Dedicated **IKD Settings page** consolidating all data-collection controls (sampling rate, sensor selection, metadata selection, retention, clear-all, on-device disclaimer)
- Sessions list activity (browse, sort by date, summary stats); access from app Settings only — keyboard's existing settings cog already gets users there
- Per-session export (CSV) and bulk export (single mega-CSV with `session_id` column)
- Per-session and bulk deletion
- Configurable retention period (Forever, or N days)

**Out of scope (deferred to Phase 3):**
- Aggregation/analytics dashboard, charts, trend lines
- Cloud sync or remote upload
- Session replay/scrubbing UI
- Cross-session correlation or insights
- First-launch consent screen (deferred — see Section 7)

---

## 2. Storage Strategy & Rationale

**Recommendation: Room database (`ikd.db`), separate from the existing `clips.db`.**

### Why Room

| Criterion | Room | JSON files | Plain CSV | DataStore |
|---|---|---|---|---|
| Already used in project | ✅ (`ClipsDatabase`) | ❌ | ❌ | ❌ |
| KSP wired up | ✅ | n/a | n/a | n/a |
| SQL queries (filter / sort / aggregate) | ✅ | ❌ | ❌ | ❌ |
| Indexed lookups (e.g., `WHERE session_id = ?`) | ✅ | ❌ | ❌ | ❌ |
| Schema migration tooling | ✅ | ❌ | ❌ | partial |
| Suitable for thousands of rows / session | ✅ | slow | slow | ❌ |
| Familiarity for future contributors | ✅ | ✅ | ✅ | partial |

JSON/CSV files would need full re-reads to query, won't scale past a few sessions, and don't fit the project's existing patterns. DataStore is for small key-value preferences, not high-volume rows.

### Why a separate database (not extending ClipsDatabase)

`ClipsDatabase` is at `version = 1`. Adding analytics tables would:
- Force every existing user to migrate the clipboard DB on Phase 2 install
- Couple unrelated concerns (clips have nothing to do with timing data)
- Risk clipboard data corruption from analytics-side bugs

Following the original `IKD_PLAN.md` direction: keep `clips.db` untouched, create a new `ikd.db`.

### Reference patterns to reuse

- `databases/ClipsDatabase.kt` — singleton with `getInstance()`, WAL enabled, `destroyInstance()` for tests
- `extensions/ContextExt.kt` — `val Context.clipsDB: ClipsDao` extension property pattern
- `interfaces/ClipsDao.kt` — DAO with `@Insert`, `@Query`, `@Delete`
- KSP already in `app/build.gradle.kts`

---

## 3. Database Schema

Three entities. Indexed on `session_id` so per-session reads stay O(log N).

```kotlin
@Entity(tableName = "sessions")
data class SessionRecord(
    @PrimaryKey val sessionId: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long?,           // null if interrupted
    @ColumnInfo(name = "event_count") val eventCount: Int,
    @ColumnInfo(name = "sensor_count") val sensorCount: Int,
    @ColumnInfo(name = "device_orientation") val deviceOrientation: Int,  // Configuration.ORIENTATION_*
    @ColumnInfo(name = "locale") val locale: String              // e.g., "en-US"
)

@Entity(
    tableName = "ikd_events",
    indices = [Index("session_id"), Index("timestamp")],
    foreignKeys = [ForeignKey(
        entity = SessionRecord::class,
        parentColumns = ["sessionId"],
        childColumns = ["session_id"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class IkdEvent(
    @PrimaryKey(autoGenerate = true) val id: Long? = null,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "event_category") val eventCategory: String,
    @ColumnInfo(name = "ikd_ms") val ikdMs: Long,
    @ColumnInfo(name = "hold_time_ms") val holdTimeMs: Long,
    @ColumnInfo(name = "flight_time_ms") val flightTimeMs: Long,
    @ColumnInfo(name = "is_correction") val isCorrection: Boolean
)

@Entity(
    tableName = "sensor_samples",
    indices = [Index("session_id")],
    foreignKeys = [ForeignKey(
        entity = SessionRecord::class,
        parentColumns = ["sessionId"],
        childColumns = ["session_id"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class SensorSample(
    @PrimaryKey(autoGenerate = true) val id: Long? = null,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "sensor_type") val sensorType: String,    // "GYRO" | "ACCEL"
    @ColumnInfo(name = "x") val x: Float,
    @ColumnInfo(name = "y") val y: Float,
    @ColumnInfo(name = "z") val z: Float
)
```

`ON DELETE CASCADE` means deleting a `SessionRecord` row also wipes its events/samples — ideal for per-session and "delete all" flows.

---

## 4. Internal Sub-Phases

Five logical sub-phases, each independently shippable and verifiable.

### 2.1 Storage Layer

Establish the persistent layer before any UI work. Existing diagnostics screen continues to function on top of the new store.

**Deliverables:**
- `models/IkdEvent.kt`, `models/SensorSample.kt`, `models/SessionRecord.kt` (Room entities replacing the plain data classes)
- `interfaces/IkdEventDao.kt`, `interfaces/SensorSampleDao.kt`, `interfaces/SessionDao.kt`
- `databases/IkdDatabase.kt` — `version = 1`, `entities = [SessionRecord::class, IkdEvent::class, SensorSample::class]`, WAL enabled
- `extensions/ContextExt.kt` — add `val Context.ikdDB: IkdDatabase`
- Refactor `LiveCaptureSessionStore` to a **write-behind buffered store**:
  - Active session: keep events in memory for instant UI updates
  - Background `Executors.newSingleThreadExecutor()` flushes events in batches every 500ms or 100 events (whichever first)
  - On `stopSession()`: drain the buffer and finalize the `SessionRecord` row (`ended_at`, final counts)
- `KeyTimingEvent` and `SensorReadingEvent` plain data classes can be reused as in-memory transit objects, mapped to entities at flush time

**Performance guard:**
- IME `onKey` and `onPress` must remain non-blocking — buffer enqueue is O(1)
- Flusher runs off the IME thread, off the UI thread
- Use `@Insert(onConflict = REPLACE)` with `List<>` for batch inserts

**Acceptance:**
- Phase 1.1 functionality preserved (diagnostics screen, event feed activity, CSV export)
- After typing a session and force-quitting the app, reopening shows the session in the database (verifiable via `adb exec-out run-as ... cat databases/ikd.db`)

---

### 2.2 Privacy Mode & Keyboard Toggle

Single source of truth: a `Config` Boolean. The keyboard toolbar exposes the toggle for instant access while typing; the IKD Settings page (sub-phase 2.3) provides a discoverable mirror.

**Default state: privacy mode ON (no collection).** Users opt in by tapping the toggle.

**Deliverables:**
- `helpers/Constants.kt` — `const val PRIVACY_MODE_ENABLED = "privacy_mode_enabled"` (default: `true`)
- `helpers/Config.kt` — `var privacyModeEnabled: Boolean` returning `prefs.getBoolean(PRIVACY_MODE_ENABLED, true)`
- `res/drawable/ic_privacy_on_vector.xml` (shield icon) and `ic_privacy_off_vector.xml` (recording dot icon) — 24dp, white, matching `ic_clipboard_vector` style
- `res/layout/keyboard_view_keyboard.xml` — add `ImageView` with id `privacy_toggle_button` in `toolbar_holder`, positioned **opposite the clipboard button** (i.e., on the **left** side, near `clipboard_clear`). Use ConstraintLayout `app:layout_constraintStart_toStartOf="parent"`.
- `views/MyKeyboardView.kt`:
  - In `onFinishInflate`/setup: read `config.privacyModeEnabled`, set the icon accordingly
  - Click handler: toggle `config.privacyModeEnabled`, update icon, show a brief toast ("Privacy mode on" / "Data collection on")
- `services/SimpleKeyboardIME.kt`:
  - In `onStartInputView(restarting=false)`: gate `LiveCaptureSessionStore.startSession()` behind `if (!config.privacyModeEnabled)`
  - In `onPress`/`onKey`: existing `isCapturing` check continues to work (no session = nothing recorded)

**Visual indicator semantics:**
- **Privacy mode OFF (collecting)**: red recording dot icon
- **Privacy mode ON (private)**: white shield icon — the default
- Always visible — users always know the state at a glance

**Acceptance:**
- On fresh install: privacy mode is ON (icon shows shield, no data collected)
- Tapping the icon flips state, and the next keyboard open respects the new state
- IKD Settings toggle (2.3) and keyboard toggle stay in sync (Config is read live, no caching — confirmed in `KeyboardFeedbackManager`)
- With privacy mode ON: no rows added to `ikd_events`, `sensor_samples`, or `sessions` for the duration

---

### 2.3 IKD Settings Page

A dedicated screen consolidating all data-collection controls in one place. Reached from the main `SettingsActivity` via a "Data Collection & Privacy" entry. The keyboard's existing settings cog also lands here (or Settings → Data Collection — a single tap deeper).

**Deliverables:**
- `activities/IkdSettingsActivity.kt` — dedicated configuration screen
- `res/layout/activity_ikd_settings.xml`

**Sections (top to bottom):**

1. **Disclaimer card** (static, prominent at top):
   > "All data collected by this keyboard is stored locally on your device. Nothing is transmitted, uploaded, or shared with any server. You can export or delete your data at any time."

2. **Privacy mode toggle** — mirrors the keyboard's privacy switch (Config-backed, instant sync)

3. **Sensor collection** — checkboxes (only shown for sensors the device has):
   - "Collect gyroscope data" (`collectGyro`, default `true`)
   - "Collect accelerometer data" (`collectAccel`, default `true`)
   - These gate `KinematicSensorHelper`'s `registerListener` calls per sensor

4. **Sensor sampling rate** — radio group (configurable per the user's research need):
   - Fastest (~200Hz, `SENSOR_DELAY_FASTEST`)
   - Game (~50Hz, `SENSOR_DELAY_GAME`) — current default
   - UI (~16Hz, `SENSOR_DELAY_UI`)
   - Normal (~5Hz, `SENSOR_DELAY_NORMAL`)
   - Show estimated rows/min beside each option
   - Stored as Int constant; `KinematicSensorHelper.start()` reads it on registration

5. **Session metadata to capture** — checkboxes:
   - "Device orientation" (`captureOrientation`, default `true`)
   - "Locale" (`captureLocale`, default `true`)
   - Note: app/package context is intentionally not offered (privacy-sensitive). Battery level was originally proposed here and has been dropped — not in scope for this project.

6. **Retention period** — dropdown / radio:
   - Forever, 7, 14, 30 (default), 60, 90 days
   - Stored as Int (`-1` for forever)

7. **Storage usage** — read-only display:
   - DB file size (e.g., "12.4 MB")
   - Session / event / sensor row counts

8. **Action buttons:**
   - "View Sessions History" → `SessionsListActivity` (sub-phase 2.4)
   - "Export all data" → bulk export from sub-phase 2.5
   - "Delete all data" (red/warning style) → confirmation dialog → wipes DB

**New Config keys (in `Constants.kt` and `Config.kt`):**
```kotlin
const val PRIVACY_MODE_ENABLED = "privacy_mode_enabled"      // 2.2 — also reused here
const val COLLECT_GYRO = "ikd_collect_gyro"                  // default: true
const val COLLECT_ACCEL = "ikd_collect_accel"                // default: true
const val SENSOR_SAMPLING_RATE = "ikd_sensor_sampling_rate"  // default: SENSOR_DELAY_GAME
const val CAPTURE_ORIENTATION = "ikd_capture_orientation"    // default: true
const val CAPTURE_LOCALE = "ikd_capture_locale"              // default: true
const val RETENTION_DAYS = "ikd_retention_days"              // default: 30, -1 = forever
```

**Navigation entry from main Settings:**
- Add a new "Data Collection & Privacy" row in `activity_settings.xml` Developer section (or promote to its own section), opening `IkdSettingsActivity`

**Acceptance:**
- All controls reflect current `Config` values on open
- Changing any value persists immediately and is reflected next time the relevant code path reads it (e.g., next keyboard open for sampling rate / sensor enables)
- "Delete all data" requires confirmation and empties all three Room tables
- Disclaimer text is clearly visible above the fold

---

### 2.4 Sessions Browser

A list of all stored sessions with summary cards, plus reuse of `EventFeedActivity` for per-session detail.

**Deliverables:**
- `activities/SessionsListActivity.kt` — RecyclerView of sessions, newest first
  - Summary card per session: date/time, duration, event count, KPM, error rate, sensor sample count
  - Tap → opens `EventFeedActivity` with `EXTRA_SESSION_ID` (load that session's data instead of the live store)
  - Long-press → context menu: Export, Delete (handled in 2.5)
- `res/layout/activity_sessions_list.xml`, `res/layout/item_session_summary.xml`
- Refactor `EventFeedActivity` to accept an optional `sessionId` extra:
  - If provided: load events/samples from `ikdDB` for that specific session
  - If absent: existing live behavior (read from in-memory store)
- **Navigation entry points:**
  - IKD Settings → "View Sessions History" → `SessionsListActivity`
  - No keyboard shortcut — the keyboard's existing settings cog button reaches main Settings → IKD Settings → Sessions History
- `res/menu/menu_sessions_list.xml` — overflow with "Delete all", "Export all"

**Acceptance:**
- Sessions appear in the list immediately after the keyboard closes
- Each row shows accurate summary stats computed from the underlying rows
- Detail view shows the same data the live event feed would have shown during that session

---

### 2.5 Report Management (Delete & Export)

**Deliverables:**
- Per-session delete: `SessionDao.deleteSession(sessionId)` (CASCADE wipes events/samples)
- Bulk delete: confirmation dialog → `SessionDao.deleteAll()` + cascading deletes (or single SQL `DELETE FROM sessions` with `ON DELETE CASCADE`)
- Per-session CSV export: same dual-block format as Phase 1.1, filename `ikd_session_<short-id>_<yyyymmdd>.csv`
- **Bulk export: single mega-CSV** with `session_id` column on every row. Two blocks separated by `#sensor_readings`. Filename `ikd_export_<yyyymmdd>.csv`. Streamed straight to a SAF document URI (no in-memory accumulation — the file may be large).
- All delete operations behind a confirmation dialog (Material 3 `AlertDialog`)

**Mega-CSV format:**
```
session_id,timestamp_ms,event_category,ikd_ms,hold_time_ms,flight_time_ms,is_correction
<all timing rows from all sessions, sorted by session_id then timestamp>

#sensor_readings
session_id,timestamp_ms,sensor_type,x,y,z
<all sensor rows from all sessions, sorted by session_id then timestamp>
```

The "Delete all data" button on the IKD Settings page (sub-phase 2.3) calls into the same bulk-delete code path here.

**Acceptance:**
- Deleting a session removes it from the list and from disk
- Per-session export produces a CSV byte-identical to what the live diagnostics export would produce for the same data
- Bulk export contains rows from every non-empty session, distinguishable by `session_id`

---

### 2.6 Retention Policy & Storage Hygiene

The retention period is **configurable** via the IKD Settings page (sub-phase 2.3). This sub-phase delivers the backend cleanup mechanism only; the UI control lives in 2.3.

**Deliverables:**
- `helpers/IkdRetentionWorker.kt` — `androidx.work.CoroutineWorker` that:
  - Reads `config.retentionDays`
  - If `retentionDays == -1` (Forever): no-op
  - Else: deletes sessions where `started_at < (now - retentionDays * MS_PER_DAY)`
- Schedule once, on app start (`App.kt`): `WorkManager.getInstance(this).enqueueUniquePeriodicWork("ikd-retention", KEEP, periodicWorkRequest)` — daily cadence, `battery-not-low` constraint
- Storage usage helper: a function that returns DB file size + per-table row counts, called by the IKD Settings page (2.3)

**Acceptance:**
- Setting retention to 7 days and triggering the worker manually (or waiting 24h) deletes sessions older than 7 days
- "Forever" disables auto-deletion
- Storage stat shown on IKD Settings page reflects current state and updates after deletes/exports

---

## 5. Files to Create / Modify

### Create (new files)

| File | Sub-phase | Purpose |
|---|---|---|
| `models/IkdEvent.kt` | 2.1 | Room entity (replaces the plain data class) |
| `models/SensorSample.kt` | 2.1 | Room entity (replaces `SensorReadingEvent`) |
| `models/SessionRecord.kt` | 2.1 | Room entity for session metadata |
| `interfaces/IkdEventDao.kt` | 2.1 | DAO for timing events |
| `interfaces/SensorSampleDao.kt` | 2.1 | DAO for sensor samples |
| `interfaces/SessionDao.kt` | 2.1 | DAO for session records |
| `databases/IkdDatabase.kt` | 2.1 | Singleton Room database (`ikd.db`) |
| `res/drawable/ic_privacy_on_vector.xml` | 2.2 | Shield icon |
| `res/drawable/ic_privacy_off_vector.xml` | 2.2 | Recording dot icon |
| `activities/IkdSettingsActivity.kt` | 2.3 | Dedicated data-collection settings page |
| `res/layout/activity_ikd_settings.xml` | 2.3 | Layout for the settings page |
| `activities/SessionsListActivity.kt` | 2.4 | History browser |
| `adapters/SessionsAdapter.kt` | 2.4 | RecyclerView adapter |
| `res/layout/activity_sessions_list.xml` | 2.4 | Sessions list screen |
| `res/layout/item_session_summary.xml` | 2.4 | Session row layout |
| `res/menu/menu_sessions_list.xml` | 2.4 | Overflow menu (Export all, Delete all) |
| `helpers/IkdStorageStats.kt` | 2.6 | Helper to compute DB size + row counts for the Settings page |
| `helpers/IkdRetentionWorker.kt` | 2.6 | Periodic cleanup of expired sessions |

### Modify

| File | Sub-phase | Change |
|---|---|---|
| `helpers/Constants.kt` | 2.2, 2.3, 2.6 | Add `PRIVACY_MODE_ENABLED`, `COLLECT_GYRO`, `COLLECT_ACCEL`, `SENSOR_SAMPLING_RATE`, `CAPTURE_ORIENTATION`, `CAPTURE_LOCALE`, `RETENTION_DAYS` |
| `helpers/Config.kt` | 2.2, 2.3, 2.6 | Add corresponding properties (privacy default `true`; retention default `30`; sensor enables default `true`) |
| `helpers/LiveCaptureSessionStore.kt` | 2.1 | Add write-behind buffer + batch flusher; add Room-backed reads for past sessions |
| `helpers/KinematicSensorHelper.kt` | 2.3 | Read `collectGyro`/`collectAccel`/`sensorSamplingRate` from Config when registering listeners |
| `extensions/ContextExt.kt` | 2.1 | Add `val Context.ikdDB: IkdDatabase` |
| `services/SimpleKeyboardIME.kt` | 2.2, 2.3 | Gate `startSession()` on `!config.privacyModeEnabled`; populate `SessionRecord` metadata per the capture-metadata flags |
| `views/MyKeyboardView.kt` | 2.2 | Wire up privacy toggle button |
| `activities/SettingsActivity.kt` | 2.3 | Add a single "Data Collection & Privacy" row that opens `IkdSettingsActivity` |
| `activities/EventFeedActivity.kt` | 2.4 | Accept optional `sessionId` extra; load from `ikdDB` if present |
| `activities/DiagnosticsActivity.kt` | 2.1 | Read from in-memory buffer for live events; past sessions browsed via `SessionsListActivity` |
| `res/layout/keyboard_view_keyboard.xml` | 2.2 | Add `privacy_toggle_button` ImageView in `toolbar_holder`, opposite the clipboard cluster |
| `res/layout/activity_settings.xml` | 2.3 | Add "Data Collection & Privacy" row in Developer section |
| `res/values/strings.xml` | all | All new UI strings (incl. on-device disclaimer copy) |
| `App.kt` | 2.6 | Schedule retention WorkManager job on app start |
| `app/build.gradle.kts` | 2.6 | Add `androidx.work:work-runtime-ktx` dependency |

### Do NOT touch

| File | Why |
|---|---|
| `databases/ClipsDatabase.kt` | Phase 2 keeps clipboard storage isolated |
| `interfaces/ClipsDao.kt` | Same |

---

## 6. Acceptance Criteria

### Storage layer (2.1)

- [ ] `ikd.db` exists in app's private storage after first session ends
- [ ] `sessions`, `ikd_events`, `sensor_samples` tables exist with correct schemas
- [ ] Force-quitting the app mid-session preserves events captured before the crash (because of periodic flush)
- [ ] No detectable typing lag during a session of 200+ keystrokes with sensors enabled (sensor batching working)
- [ ] All Phase 1.1 features still function (live diagnostics, real-time UI, single-session CSV export)

### Privacy mode (2.2)

- [ ] On fresh install: privacy mode is ON by default (no data captured)
- [ ] Tapping the keyboard's privacy toggle changes the icon and persists across keyboard restarts
- [ ] IKD Settings privacy toggle and keyboard privacy toggle are bidirectionally synced
- [ ] With privacy mode ON, opening and typing on the keyboard produces zero new rows in any of the three tables
- [ ] Toggling mid-typing (privacy ON during an active session) finalizes the session and stops further capture

### IKD Settings page (2.3)

- [ ] Disclaimer card is the first visible element on the page
- [ ] Sensor checkboxes for unavailable sensors (e.g., gyro on a device that lacks one) are hidden, not disabled
- [ ] Changing the sampling rate takes effect on the next keyboard open (no app restart)
- [ ] Disabling a sensor (e.g., gyro) results in zero rows for that `sensor_type` in the next session
- [ ] Disabling `captureOrientation` results in `device_orientation = -1` (sentinel) in the next `SessionRecord` row
- [ ] "Delete all data" requires confirmation; on confirm, all three tables are emptied
- [ ] Storage usage display refreshes after deletes/exports

### Sessions browser (2.4)

- [ ] Newest session appears at top of list immediately after keyboard close
- [ ] Tapping a session opens the same event log UI as the live diagnostics, populated with that session's data
- [ ] Long-press on a session shows the context menu

### Report management (2.5)

- [ ] Per-session delete removes the session and all its rows
- [ ] "Delete all" (from IKD Settings or Sessions menu) requires confirmation and wipes all three tables
- [ ] Per-session export CSV is byte-identical to the live diagnostics CSV for that data
- [ ] Bulk mega-CSV export contains a `session_id` column on every row and includes data from every non-empty session

### Retention (2.6)

- [ ] After setting retention to N days, sessions with `started_at < now - N days` are removed within 24h
- [ ] Setting retention to "Forever" disables auto-deletion (worker is a no-op)
- [ ] Storage usage display reflects current `ikd.db` row counts and file size

---

## 7. Decisions

| # | Topic | Decision |
|---|---|---|
| 1 | Privacy mode default on first install | **ON** — no data is collected until the user explicitly toggles it off via the keyboard switch or IKD Settings |
| 2 | First-launch consent screen | **Deferred** — not required at this stage; the on-device disclaimer in the IKD Settings page (sub-phase 2.3) is the user-facing notice |
| 3 | Sensor sampling rate | **User-configurable** in the IKD Settings page (sub-phase 2.3). Options: Fastest / Game / UI / Normal. Default: Game (~50Hz). Research need drives the per-device choice |
| 4 | Session metadata to capture | **User-selectable** in the IKD Settings page. Independent toggles for orientation and locale. Battery level was dropped from scope (not a research target). App/package context is intentionally not offered (privacy) |
| 5 | Keyboard shortcut to Sessions browser | **None** — the keyboard's existing settings cog already routes the user to main Settings, from which the IKD Settings page → Sessions History is one tap deeper. No additional toolbar icon |
| 6 | Default retention period | **User-configurable** in the IKD Settings page. Options: Forever, 7, 14, 30, 60, 90 days. Default: 30 days |
| 7 | Bulk export format | **Single mega-CSV** with `session_id` column on every row. Two-block format (`#sensor_readings` separator) preserved. No ZIP wrapping |
| 8 | Internal settings page (added requirement) | **Required** — `IkdSettingsActivity` consolidates privacy toggle, sensor enables, sampling rate, metadata enables, retention, storage usage, View Sessions History, Export all, Delete all, and a static **on-device disclaimer** ("All data remains on this device") |