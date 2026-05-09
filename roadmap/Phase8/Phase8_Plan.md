# Phase 8 — Mood Bar & Contextual Overlay

**Status:** Planned
**Depends on:** Phase 5 (per-session dashboard layout — adds a mood KPI cell), Phase 3 (`DashboardActivity` — adds a mood trend chart and an Avg Mood KPI), Phase 7 (capture-layer reopen pattern that this phase mirrors at smaller scope)
**Branch:** `feat/phase8-mood-bar` — cut from `main` after Phase 7 has been merged.
**Scope (one sentence):** Add a one-tap mood-annotation bar to the keyboard toolbar, persist the chosen mood as a per-session `MoodEntry` row in `ikd.db`, and surface that signal as a new dimension on both the per-session dashboard (`EventFeedActivity`) and the global insights dashboard (`DashboardActivity`) — without touching the IKD capture path.

This is the **first phase to bump `IkdDatabase.version`** (1 → 2). The `mood_entries` table is added via a strictly additive `Migration(1, 2)` — no existing column or row is rewritten. It is also the first phase since Phase 7 to reopen `MyKeyboardView.kt`, but only to add a six-button toolbar strip; the existing key-press / key-up / emoji / privacy-toggle code paths are not edited. Capture (`SimpleKeyboardIME`, `LiveCaptureSessionStore`, `KinematicSensorHelper`, the IkdEvent / SensorSample entities) is fully frozen.

---

## Table of Contents

1. [Concept & Scope](#1-concept--scope)
2. [Branch & Layering Discipline (REOPEN keyboard view; SCHEMA MIGRATION)](#2-branch--layering-discipline-reopen-keyboard-view-schema-migration)
3. [Schema Change: `mood_entries` Table](#3-schema-change-mood_entries-table)
4. [Mood Bar UI Spec](#4-mood-bar-ui-spec)
5. [Sub-Phases (4)](#5-sub-phases-4)
   - [8.1 Schema Migration + MoodEntry Entity + MoodDao](#81-schema-migration--moodentry-entity--mooddao)
   - [8.2 Mood Bar on Keyboard Toolbar](#82-mood-bar-on-keyboard-toolbar)
   - [8.3 Mood Overlay on Session Dashboard](#83-mood-overlay-on-session-dashboard)
   - [8.4 Mood Trend Chart on Global Dashboard](#84-mood-trend-chart-on-global-dashboard)
6. [Downstream Impact (Dashboards, CSV, Privacy)](#6-downstream-impact-dashboards-csv-privacy)
7. [Files to Create / Modify (vs. forbidden)](#7-files-to-create--modify-vs-forbidden)
8. [Acceptance Criteria](#8-acceptance-criteria)
9. [Decisions](#9-decisions)
10. [Explicitly Deferred to Later Phases](#10-explicitly-deferred-to-later-phases)

---

## 1. Concept & Scope

The IKD pipeline today captures *what* the user typed and *how fast* — six event categories (eight after Phase 7), timing, sensors, error rate. It does not capture *how the user felt while typing it.* Phase 8 adds a single tap-and-forget channel for that signal: a horizontal strip of six emoji buttons in the keyboard's top bar (next to the existing privacy shield), where the user can annotate the active session as `😞 😟 😐 😊 😄` or leave it un-annotated (`🫥`).

The recorded mood becomes a first-class dimension in the dashboards: a fifth KPI cell and a metadata chip on the per-session screen, plus a fourth line chart and an Avg Mood KPI on the global insights screen. Sessions without a mood entry are excluded from mood aggregations rather than counted as neutral — missing data is *missing*, not zero.

### In scope

- **Schema:** New `mood_entries` table with columns `id` (PK, autogen), `sessionId` (nullable FK → `sessions.session_id`, CASCADE on delete), `timestamp` (Long, ms), `moodScore` (Int, 1–5). One row per session at most (enforced by `UNIQUE` index on `sessionId WHERE sessionId IS NOT NULL`); rows with `sessionId = NULL` are allowed (mood tapped while privacy mode was on).
- **Migration:** `IkdDatabase.version` 1 → 2 via a `Migration(1, 2)` that runs `CREATE TABLE` + `CREATE UNIQUE INDEX`. Non-destructive, no existing data touched.
- **DAO:** New `MoodDao` with `insertOrReplaceForSession`, `clearForSession`, `getForSession`, `getMoodBuckets` (per-day/week aggregation for global dashboard), `getMoodDistribution` (5-row count projection — feeds Phase 9, returned as a stub query for now).
- **Mood Bar UI:** Six-button horizontal strip rendered in `MyKeyboardView`'s top bar, gated by `Config.showMoodBar` (default `false`). Buttons are emoji `MyButton`s — neutral (`🫥`) at index 0 highlighted by default, then `😞 😟 😐 😊 😄`. Tapping a mood emoji writes a `MoodEntry`; tapping `🫥` after a mood is set deletes the row. Selected state highlighted via background tint.
- **Settings toggle:** New row in `IkdSettingsActivity` for "Show mood bar in keyboard" (controls `Config.showMoodBar`).
- **Per-session overlay:** `EventFeedActivity` (DB-backed mode only) gets a fifth KPI cell rendering the mood emoji + label when `MoodEntry` exists for that session; gracefully collapses to four cells otherwise. The metadata one-liner gains a `· Mood: 😊 Happy` segment when present.
- **Global dashboard:** `DashboardActivity` gains a fourth `IkdLineChartView` ("Mood over Time") rendering daily/weekly avg mood scores; null buckets render as gaps. The KPI strip gains an "Avg Mood" cell when at least one mood entry exists in the selected range.
- **Privacy invariant:** `MoodEntry` stores only the integer score (1–5) and a timestamp. The score → emoji mapping lives in the UI layer only. CSV export gains a third dual-block segment for mood rows; privacy mode prevents writes (same gate as `LiveCaptureSessionStore.startSession()`).

### Out of scope (deferred — see Section 10)

- Mood Distribution bar chart (Phase 9 — uses the same DAO method `getMoodDistribution` whose signature is added here).
- Mood-correlated metrics (e.g. "your typing speed when sad vs. happy") — defers to Phase 9.
- Multi-mood per session (the bar is for quick annotation, not a time-series log; one row per session at most).
- Free-text journal entries (privacy invariant violation; a future phase would need a rethink of the privacy model).
- Custom mood scales / wording / locales — the five emoji are universal-enough; localising is a Phase 10 (rebrand) concern.
- Reminders / nudges to rate the session — yagni.
- Mood signal in CSV header columns (we add a sibling `mood_entries` block, not a per-event column).

---

## 2. Branch & Layering Discipline (REOPEN keyboard view; SCHEMA MIGRATION)

Phase 8 reopens **two** previously-frozen surfaces. Both are scoped, both are additive.

### Reopened files (allowed, additive only)

| File | Why reopened | Edit shape |
|---|---|---|
| `views/MyKeyboardView.kt` | Hosts the six-button mood bar in the top bar | Add a horizontal `LinearLayout` (or six `MyButton`s) between the existing privacy-toggle button and the right-edge controls. Click handlers call `IkdMoodBarController.onMoodTapped(score)`. The existing key-press / drag / popup / privacy-toggle code is untouched. |
| `databases/IkdDatabase.kt` | Schema bump 1 → 2 with the new `mood_entries` table | Add `MoodEntry::class` to `entities`, add `MoodDao()` abstract, add `Migration(1, 2)` running `CREATE TABLE mood_entries (...)` and `CREATE UNIQUE INDEX ...` |
| `databases/ClipsDatabase.kt`, `interfaces/ClipsDao.kt` | (still frozen) | — |

### Still forbidden (capture path frozen)

| File | Reason |
|---|---|
| `services/SimpleKeyboardIME.kt` | Capture path — frozen since Phase 7 closed. Mood writes do **not** go through the IME |
| `helpers/LiveCaptureSessionStore.kt` | Capture buffer — frozen. Mood writes use a sibling `IkdMoodBarController` that calls `MoodDao` directly on a background `Thread { }` |
| `helpers/KinematicSensorHelper.kt` | Sensor path — unrelated to mood |
| `helpers/IkdRetentionWorker.kt` | Retention worker is generic over `sessions` deletion; CASCADE handles `mood_entries` automatically (no edit needed) |
| `models/IkdEvent.kt`, `models/SensorSample.kt`, `models/SessionRecord.kt` | No new column on existing entities; the new table is sibling, not an extension |
| `helpers/IkdAggregator.kt` | Phase 3 read surface — extended only via the additive `MoodDao.getMoodBuckets` method called from a sibling `IkdMoodAggregator` (new helper). The existing `Companion.buildSnapshot(...)` remains pure-IKD aggregation; the activity composes mood + IKD snapshots |
| `helpers/IkdSessionStatsLoader.kt` | Phase 4 read surface — frozen. Per-session mood is loaded via a separate `IkdMoodLoader` on the same `Dispatchers.IO` hop |
| `helpers/IkdSessionChartLoader.kt` | Phase 5 read surface — frozen |
| `helpers/IkdCsvWriter.kt` | CSV format gets one **additional** dual-block segment (`#mood_entries\nsession_id,timestamp_ms,mood_score`) appended to the existing two — strictly additive, parsers reading only the first two blocks remain compatible |
| `views/IkdLineChartView.kt` | Chart wrapper — frozen at single-series; the mood chart reuses it as-is |

### Branch hygiene

- Branch name: `feat/phase8-mood-bar`
- Cut from latest `main` after Phase 7 has been merged (Phase 8 builds on top of Phase 7's docs/CLAUDE state — both are now on main).
- Each sub-phase (8.1 → 8.4) lands as one focused commit using the project's `feat:` / `chore:` / `refactor:` / `test:` / `docs:` convention.
- After 8.4 passes acceptance, leave the branch local for the user to review — **no push, no PR opened by the implementer.**

---

## 3. Schema Change: `mood_entries` Table

This is the first phase to migrate `ikd.db`. The migration is the highest-risk part of Phase 8 — get it wrong and existing user sessions become unreadable.

### Table definition

```sql
CREATE TABLE IF NOT EXISTS `mood_entries` (
    `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    `session_id` TEXT,
    `timestamp` INTEGER NOT NULL,
    `mood_score` INTEGER NOT NULL,
    FOREIGN KEY(`session_id`) REFERENCES `sessions`(`session_id`) ON DELETE CASCADE
);
CREATE UNIQUE INDEX IF NOT EXISTS `index_mood_entries_session_id`
    ON `mood_entries`(`session_id`)
    WHERE `session_id` IS NOT NULL;
```

### Why these choices

- **`session_id` nullable** — the user can tap a mood emoji while privacy mode is on (no active session). We still record the timestamp + score; it's just not associated with a session. These rows are excluded from per-session loaders but counted in global aggregations.
- **`UNIQUE WHERE session_id IS NOT NULL`** — at most one `MoodEntry` per session. If the user re-taps a different mood, we `INSERT OR REPLACE` to overwrite. If they tap `🫥` (clear), we delete. No history.
- **`mood_score INT`** — the only stored signal. Score → emoji is a UI-layer mapping (Section 4). Privacy invariant preserved: no text, no codepoint.
- **`ON DELETE CASCADE`** — when retention worker drops a session row, its mood entry goes with it. Same pattern as `ikd_events` / `sensor_samples`.
- **`AUTOINCREMENT id`** — one mood entry can survive after the session is deleted only if `session_id IS NULL` (mood-without-session case). Surrogate key avoids the FK constraint failing on those rows.

### Migration code

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `mood_entries` (
                `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                `session_id` TEXT,
                `timestamp` INTEGER NOT NULL,
                `mood_score` INTEGER NOT NULL,
                FOREIGN KEY(`session_id`) REFERENCES `sessions`(`session_id`) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("""
            CREATE UNIQUE INDEX IF NOT EXISTS `index_mood_entries_session_id`
                ON `mood_entries`(`session_id`)
                WHERE `session_id` IS NOT NULL
        """.trimIndent())
    }
}
```

Wired into `IkdDatabase`:

```kotlin
@Database(
    entities = [SessionRecord::class, IkdEvent::class, SensorSample::class, MoodEntry::class],
    version = 2
)
abstract class IkdDatabase : RoomDatabase() {
    abstract fun SessionDao(): SessionDao
    abstract fun IkdEventDao(): IkdEventDao
    abstract fun SensorSampleDao(): SensorSampleDao
    abstract fun MoodDao(): MoodDao

    companion object {
        // ...existing builder...
        Room.databaseBuilder(context, IkdDatabase::class.java, "ikd.db")
            .addMigrations(MIGRATION_1_2)
            .setJournalMode(WRITE_AHEAD_LOGGING)
            .build()
    }
}
```

### Migration test

A new instrumented Room migration test in `app/src/androidTest/.../IkdDatabaseMigrationTest.kt`:
- Open a v1 DB, write a `SessionRecord` + `IkdEvent` + `SensorSample` row
- Run `MIGRATION_1_2`
- Assert the new `mood_entries` table exists, the unique index exists, and the original three tables still contain their seed rows untouched
- Assert that inserting two mood entries with the same `session_id` fails the unique constraint
- Assert that inserting two mood entries with `session_id = NULL` succeeds (partial unique index)

This is the only instrumented test in Phase 8; everything else is JVM-side.

### CSV format (additive)

```
session_id,timestamp_ms,event_category,ikd_ms,hold_time_ms,flight_time_ms,is_correction
<timing rows>

#sensor_readings
session_id,timestamp_ms,sensor_type,x,y,z
<sensor rows>

#mood_entries
session_id,timestamp_ms,mood_score
<mood rows>
```

`IkdCsvWriter.kt` is reopened **only** to append the third block. Parsers that read only the first two blocks remain compatible (the `#mood_entries` line is treated as an unknown header and the rest as garbage — the existing parser already tolerates trailing garbage).

---

## 4. Mood Bar UI Spec

### Visual layout (keyboard top bar)

```
┌─────────────────────────────────────────────────────────────────┐
│  🫥  😞  😟  😐  😊  😄        [shield icon]   [other top-bar]  │
└─────────────────────────────────────────────────────────────────┘
                                                                  ▼ keyboard rows
```

Six buttons in a horizontal `LinearLayout`, leading-aligned. Each button is a `MyButton` with `text` set to the emoji and a fixed width (`@dimen/mood_bar_button_size = 32dp`). The privacy-toggle shield button keeps its existing position on the right-trailing side.

### State machine

| User action | DB write | UI update |
|---|---|---|
| Open keyboard, no prior mood for active session | none | 🫥 highlighted, others dim |
| Tap 😊 (score 4) | `INSERT OR REPLACE INTO mood_entries (session_id, timestamp, mood_score) VALUES (?, NOW, 4)` | 😊 highlighted, 🫥 + others dim |
| Tap 😟 (score 2) after 😊 was set | `INSERT OR REPLACE … score = 2` (same `session_id` so the unique index overwrites) | 😟 highlighted, 😊 + 🫥 + others dim |
| Tap 🫥 after 😟 was set | `DELETE FROM mood_entries WHERE session_id = ?` | 🫥 highlighted, all others dim (back to no-choice state) |
| Privacy mode is ON when user taps 😊 | `INSERT INTO mood_entries (session_id, timestamp, mood_score) VALUES (NULL, NOW, 4)` (no session row) | 😊 highlighted (in-memory only; cleared on next open) |
| Keyboard re-opens (new `onStartInputView`, new `sessionId`) | none | 🫥 highlighted (defaults reset; existing session's row stays in DB but the bar always defaults to no-choice on session start) |

### Score → emoji mapping (UI layer only)

| Score | Emoji | Label key |
|---|---|---|
| 1 | 😞 | `mood_label_very_sad` |
| 2 | 😟 | `mood_label_sad` |
| 3 | 😐 | `mood_label_neutral` |
| 4 | 😊 | `mood_label_happy` |
| 5 | 😄 | `mood_label_very_happy` |
| (none) | 🫥 | `mood_label_no_choice` |

The DB only stores the integer. The mapping lives in a single `helpers/MoodEmoji.kt` object so it can be reused across the keyboard bar, the per-session KPI cell, the metadata chip, and the global KPI cell.

### Highlight styling

Selected button: `background = ?attr/colorAccent` (or the existing keyboard-key pressed colour from `MyKeyboardView`'s theme), `alpha = 1.0f`. Unselected: `background = transparent`, `alpha = 0.6f`. Theme integration follows the same pattern as Phase 6's status chip — colours are theme-attr-driven, no hardcoded RGB.

### Settings toggle

`IkdSettingsActivity` gains one new row, placed under the existing "Capture" section:

| Row | Pref | Default |
|---|---|---|
| Show mood bar in keyboard | `Config.showMoodBar` | `false` |

When `false`, the entire `LinearLayout` is `View.GONE` in `MyKeyboardView` — zero visual impact for users who don't opt in.

---

## 5. Sub-Phases (4)

Four focused sub-phases. Each compiles, tests, and ships.

---

### 8.1 Schema Migration + MoodEntry Entity + MoodDao

**Goal:** `ikd.db` migrates 1 → 2 cleanly; `MoodEntry` is queryable; the migration test passes.

#### Deliverables

- `models/MoodEntry.kt` (new): `@Entity(tableName = "mood_entries", foreignKeys = ..., indices = [@Index(value = ["session_id"], unique = true)])` with the four columns from Section 3.
- `interfaces/MoodDao.kt` (new):
  - `@Insert(onConflict = REPLACE) suspend fun insertOrReplace(entry: MoodEntry)`
  - `@Query("DELETE FROM mood_entries WHERE session_id = :sessionId") suspend fun clearForSession(sessionId: String)`
  - `@Query("SELECT * FROM mood_entries WHERE session_id = :sessionId LIMIT 1") suspend fun getForSession(sessionId: String): MoodEntry?`
  - `@Query("SELECT strftime(:bucketFormat, timestamp / 1000, 'unixepoch', 'localtime') AS bucket, AVG(mood_score) AS avgScore, COUNT(*) AS entryCount FROM mood_entries WHERE timestamp BETWEEN :fromMs AND :toMs GROUP BY bucket ORDER BY bucket") suspend fun getMoodBuckets(bucketFormat: String, fromMs: Long, toMs: Long): List<MoodBucketRow>`
  - `@Query("SELECT mood_score AS score, COUNT(*) AS entryCount FROM mood_entries WHERE timestamp BETWEEN :fromMs AND :toMs GROUP BY mood_score ORDER BY mood_score") suspend fun getMoodDistribution(fromMs: Long, toMs: Long): List<MoodDistributionRow>` (returned-type stub used by Phase 9; DAO method exists today so the schema/contract is locked.)
- `interfaces/MoodBucketRow.kt` (new): POJO `bucket: String`, `avgScore: Double`, `entryCount: Int`.
- `interfaces/MoodDistributionRow.kt` (new): POJO `score: Int`, `entryCount: Int`.
- `databases/IkdDatabase.kt` (modified): add `MoodEntry::class` to entities, add `abstract fun MoodDao(): MoodDao`, bump `version = 2`, register `MIGRATION_1_2`.
- `extensions/ContextExt.kt` (modified): add `val Context.moodDB get() = ikdDB.MoodDao()` (or expose via the existing `ikdDB` getter — match prior pattern).
- `app/src/androidTest/.../IkdDatabaseMigrationTest.kt` (new): Room migration test described in Section 3.

#### Acceptance

- `./gradlew assembleCoreDebug` BUILD SUCCESSFUL
- `./gradlew connectedCoreDebugAndroidTest` (or the equivalent androidTest task) — migration test passes
- `./gradlew detekt` and `lint` no regressions
- Manually pull `ikd.db` from a device that already has v1 data, install the v2 build → confirm existing sessions are still readable AND the new `mood_entries` table exists (verifiable via `sqlite3 ikd.db ".schema"`)

#### Commit

`feat(phase8): add mood_entries table with Migration(1,2) and MoodDao`

---

### 8.2 Mood Bar on Keyboard Toolbar

**Goal:** Six emoji buttons appear in the keyboard top bar (gated by `Config.showMoodBar`). Tapping a mood writes to the DB; tapping 🫥 clears. Selected state is visible.

#### Deliverables

- `helpers/Constants.kt` (modified): add `SHOW_MOOD_BAR = "ikd_show_mood_bar"` and `MOOD_SCORE_*` (1..5) const ints. Mood-emoji mapping does **not** live here (UI-only — see `MoodEmoji.kt`).
- `helpers/Config.kt` (modified): add `var showMoodBar: Boolean` (default `false`).
- `helpers/MoodEmoji.kt` (new): `object MoodEmoji { fun emojiFor(score: Int): String; fun labelResFor(score: Int): Int; const NEUTRAL_EMOJI = "🫥"; const NO_CHOICE_LABEL = R.string.mood_label_no_choice }`.
- `helpers/IkdMoodBarController.kt` (new): exposed via `Context.ikdMoodBarController`. Methods:
  - `suspend fun setMoodForActiveSession(score: Int)` — reads the currently-active sessionId from `LiveCaptureSessionStore`, writes a `MoodEntry`. If no active session and privacy mode on, writes with `sessionId = null`.
  - `suspend fun clearMoodForActiveSession()` — same lookup, calls `MoodDao.clearForSession`.
  - `suspend fun getMoodForActiveSession(): MoodEntry?`
  - All three run on `Dispatchers.IO`. The controller does **not** edit `LiveCaptureSessionStore` — it only reads `currentSessionId`.
- `views/MyKeyboardView.kt` (modified):
  - Add a horizontal `LinearLayout` (or six `MyButton`s programmatically) between the privacy-toggle button and the right-edge controls.
  - Visibility gated on `context.config.showMoodBar`.
  - Click handler launches a `CoroutineScope(Dispatchers.IO)` (or uses `Thread { }`) to call `IkdMoodBarController.setMoodForActiveSession(score)`. UI state updates on the main thread via `runOnUiThread`.
  - On `onStartInputView` (which `MyKeyboardView` is told about by the IME), reset the bar to "no choice" highlighted.
- `res/values/strings.xml` (modified): add `mood_label_no_choice`, `mood_label_very_sad`, `mood_label_sad`, `mood_label_neutral`, `mood_label_happy`, `mood_label_very_happy`, `mood_bar_setting_title`, `mood_bar_setting_summary`.
- `res/values/dimens.xml` (modified): add `mood_bar_button_size = 32dp`.
- `activities/IkdSettingsActivity.kt` (modified) + its layout (modified): add the "Show mood bar in keyboard" toggle row.

#### Acceptance

- `./gradlew assembleCoreDebug` BUILD SUCCESSFUL
- `./gradlew detekt` and `lint` no regressions
- Manual smoke: enable "Show mood bar" in settings → open the keyboard → six emoji buttons appear in the top bar; 🫥 highlighted by default. Tap 😊 → highlight moves to 😊. Pull `ikd.db` → confirm a `mood_entries` row exists with the active session's id and score = 4. Tap 🫥 → row deleted. Repeat with privacy mode ON → row inserted with `session_id = NULL`.
- Disable "Show mood bar" → the bar disappears entirely; existing sessions' mood rows stay in `ikd.db`.

#### Commit

`feat(phase8): add six-button mood bar to keyboard toolbar`

---

### 8.3 Mood Overlay on Session Dashboard

**Goal:** When viewing a saved session in `EventFeedActivity` (DB-backed mode), if a `MoodEntry` exists for it, the KPI strip shows a fifth cell (emoji + label) and the metadata one-liner gains a `· Mood: 😊 Happy` segment. Sessions without mood show four cells (existing layout).

#### Deliverables

- `helpers/IkdMoodLoader.kt` (new): mirrors `IkdSessionStatsLoader`. `suspend fun load(sessionId: String): MoodEntry?` on `Dispatchers.IO`. Pure derivation lives on `Companion` (just a passthrough but follows the established pattern for unit testing).
- `extensions/ContextExt.kt` (modified): add `val Context.ikdMoodLoader: IkdMoodLoader`.
- `activities/EventFeedActivity.kt` (modified — within the DB-backed code path; live mode untouched):
  - In `onResume`, call `ikdMoodLoader.load(sessionId)` on the same `Dispatchers.IO` hop already used for stats + chart loaders.
  - If non-null, populate the new KPI cell view; show the metadata chip segment.
  - If null, hide the KPI cell (the cell `View.GONE`, the row's `weightSum` adjusts accordingly).
- `res/layout/activity_event_feed.xml` (modified — DB-backed dashboard container only):
  - Add a fifth KPI cell (`event_feed_kpi_mood_cell`) inside the existing KPI strip `LinearLayout`. Layout weights kept at `1` per cell so the cell collapses cleanly when hidden (Phase 5 pattern).
  - Add a `mood_chip_text` `MyTextView` to the metadata one-liner row, shown via `View.VISIBLE` when mood is present.
- `res/values/strings.xml` (modified): add `session_mood_kpi_label = "Mood"`, `session_mood_chip_format = "Mood: %1$s %2$s"`.

#### Acceptance

- `./gradlew assembleCoreDebug` BUILD SUCCESSFUL; tests pass
- Open a saved session that has a mood entry → fifth KPI cell renders (e.g. `😊 / Happy`); metadata one-liner shows `· Mood: 😊 Happy`.
- Open a saved session that has no mood entry → KPI strip shows four cells (no fifth cell, no gap); metadata one-liner does not include the mood segment.
- Theme tinting unchanged (the new KPI cell uses the same `bigger_text_size` + `smaller_text_size` mirrors Phase 5's existing cells).

#### Commit

`feat(phase8): show mood KPI cell and metadata chip on session dashboard`

---

### 8.4 Mood Trend Chart on Global Dashboard

**Goal:** `DashboardActivity` adds a fourth chart ("Mood over Time") and an "Avg Mood" KPI cell when at least one mood entry exists in the selected range. Null buckets render as gaps; both adapt to Week / Month / All Time toggles.

#### Deliverables

- `helpers/IkdMoodAggregator.kt` (new): `suspend fun snapshot(range: Range): MoodSnapshot` on `Dispatchers.IO`. Pure derivation on `Companion.buildSnapshot(buckets, distribution)`. Reuses the same `Range` enum as `IkdAggregator` (Week / Month / All Time) and the same bucket-format selection (`%Y-%m-%d` for Week/Month, `%Y-%W` for All Time). `MoodSnapshot` data class: `buckets: List<MoodBucket>`, `averageScore: Double?`, `totalEntries: Int`.
- `extensions/ContextExt.kt` (modified): add `val Context.ikdMoodAggregator: IkdMoodAggregator`.
- `activities/DashboardActivity.kt` (modified):
  - In `onResume`, call `ikdMoodAggregator.snapshot(currentRange)` alongside the existing `ikdAggregator.snapshot(...)` on the same `Dispatchers.IO` hop.
  - Populate the new chart and the new KPI cell when the snapshot has any entries; hide both otherwise (cell `View.GONE`).
- `res/layout/activity_dashboard.xml` (modified):
  - Add a fourth `IkdLineChartView` card below the existing three (same `card_corner_radius` / `card_elevation` as the other charts).
  - Add a fifth KPI cell to the existing strip (`dashboard_kpi_avg_mood_cell`); same `weightSum` adjustment pattern as Phase 5.
- `res/values/strings.xml` (modified): add `dashboard_chart_mood_title`, `dashboard_kpi_avg_mood_label`, `dashboard_chart_mood_y_label = "Mood (1–5)"`.
- `app/src/test/.../IkdMoodAggregatorTest.kt` (new): JVM-only unit tests for `Companion.buildSnapshot(...)`. Cover: empty buckets → `averageScore = null`, `totalEntries = 0`; mixed week with three days at scores 5/3/1 → avg = 3.0, totalEntries = 3; null buckets propagate to the chart line breaks (verified via the `Float?` value list returned to `IkdLineChartView`).
- `CLAUDE.md` (modified): add a Phase 8 section in the same shape as Phase 5/6/7 — scope, files, decisions, what's preserved, perf budget, forbidden list.
- `roadmap/FeatureRoadmap.md` (modified): flip Phase 8 status from `Planned` to `Implemented`; add a `Detailed scope:` link to `roadmap/Phase8/Phase8_Plan.md`.

#### Acceptance

- `./gradlew assembleCoreDebug` BUILD SUCCESSFUL
- `./gradlew testCoreDebugUnitTest` passes (new fixtures + all existing)
- `./gradlew detekt` and `lint` no regressions vs `main` baseline
- Open `DashboardActivity` with a populated DB → fourth chart renders with the mood-over-time line; KPI strip shows `Avg Mood`. Switch Week / Month / All Time → all four charts re-aggregate.
- Open `DashboardActivity` with a populated DB but no mood entries → the mood card is hidden; the KPI strip shows four cells (no `Avg Mood`).

#### Commit

(Split as needed for clarity — the `feat:` commit may be sizable enough to warrant a separate `test(phase8):` and `docs(phase8):`.)

```
feat(phase8): add mood trend chart and avg mood KPI to global dashboard
test(phase8): cover MoodAggregator buildSnapshot edge cases
docs(phase8): document Phase 8 in CLAUDE.md and FeatureRoadmap
```

---

## 6. Downstream Impact (Dashboards, CSV, Privacy)

### Per-session screen (`EventFeedActivity` with `EXTRA_SESSION_ID`)

- Fifth KPI cell appears when the session has a mood entry; gracefully collapses to four cells otherwise.
- Metadata one-liner gains a `Mood: 😊 Happy` segment when present.
- Three line charts (timing / gyro / accel) and the existing chip row are unchanged.
- Live mode (`EventFeedActivity` without `EXTRA_SESSION_ID`) is unchanged — no mood UI on the live event log (it's a real-time feed and mood is a session-scoped annotation, not an event).

### Global dashboard (`DashboardActivity`)

- Fourth chart "Mood over Time" between the existing three charts and the empty-state row.
- Fifth KPI cell "Avg Mood" appears when the selected range has at least one mood entry.
- Existing three charts (WPM, avg IKD, error rate) are unchanged in shape and value. Mood is a sibling dimension, not a multiplier.
- Range toggle (Week / Month / All Time) re-aggregates all four charts.

### CSV export

- Single-session export and bulk export both gain a third dual-block segment (`#mood_entries\nsession_id,timestamp_ms,mood_score`).
- Parsers reading only the first two blocks remain backwards-compatible.
- Privacy mode disables collection at the source — mood entries are not written when privacy mode is on **and** there is no active session. (Mood entries with `session_id = NULL` are still written when the user explicitly taps a mood emoji while privacy mode is on; this is the user's choice and the bar is gated by an opt-in setting.)

### Privacy invariants (preserved)

- `MoodEntry` stores only the integer score (1–5) and a timestamp. No text. No emoji codepoint.
- Score → emoji mapping lives in the UI layer only (`helpers/MoodEmoji.kt`).
- Retention worker continues to honour `Config.retentionDays`; CASCADE drops mood entries when their session is dropped. Sessionless mood entries (rare) are dropped by their own timestamp via a new query in `IkdRetentionWorker`'s next run if they're older than the cutoff. **Confirm:** the existing retention worker only deletes from `sessions`; we extend it minimally with a sibling `DELETE FROM mood_entries WHERE session_id IS NULL AND timestamp < ?` call. This is the **only** edit to `IkdRetentionWorker.kt` — and the only reason it's not on the forbidden list this phase.

### Forbidden during Phase 8 (added beyond Phase 7's freeze)

All of Phase 7's forbidden list, with two surgical exceptions noted in Section 2: `databases/IkdDatabase.kt` (schema bump) and `views/MyKeyboardView.kt` (mood bar). Plus one minimal additive edit to `helpers/IkdRetentionWorker.kt` (sessionless mood retention). All other previously-frozen surfaces stay frozen.

---

## 7. Files to Create / Modify (vs. forbidden)

### Create (8 files)

| File | Sub-phase |
|---|---|
| `models/MoodEntry.kt` | 8.1 |
| `interfaces/MoodDao.kt` | 8.1 |
| `interfaces/MoodBucketRow.kt` | 8.1 |
| `interfaces/MoodDistributionRow.kt` | 8.1 |
| `helpers/MoodEmoji.kt` | 8.2 |
| `helpers/IkdMoodBarController.kt` | 8.2 |
| `helpers/IkdMoodLoader.kt` | 8.3 |
| `helpers/IkdMoodAggregator.kt` | 8.4 |
| `app/src/androidTest/.../IkdDatabaseMigrationTest.kt` | 8.1 |
| `app/src/test/.../IkdMoodAggregatorTest.kt` | 8.4 |

### Modify (additive only)

| File | Sub-phase | Change |
|---|---|---|
| `databases/IkdDatabase.kt` | 8.1 | Schema bump 1 → 2; register `MoodEntry`, `MoodDao`, `MIGRATION_1_2` |
| `extensions/ContextExt.kt` | 8.1 / 8.3 / 8.4 | Add `ikdMoodLoader`, `ikdMoodAggregator`, possibly `moodDB` if pattern fits |
| `helpers/Constants.kt` | 8.2 | Add `SHOW_MOOD_BAR` pref key |
| `helpers/Config.kt` | 8.2 | Add `var showMoodBar: Boolean` |
| `views/MyKeyboardView.kt` | 8.2 | Add the six-button mood bar (gated by `Config.showMoodBar`) |
| `activities/IkdSettingsActivity.kt` + its layout | 8.2 | Add "Show mood bar in keyboard" toggle row |
| `activities/EventFeedActivity.kt` | 8.3 | Wire `ikdMoodLoader` on the existing `Dispatchers.IO` hop; populate fifth KPI cell + metadata chip |
| `res/layout/activity_event_feed.xml` | 8.3 | Add fifth KPI cell + metadata `mood_chip_text` |
| `activities/DashboardActivity.kt` | 8.4 | Wire `ikdMoodAggregator`; populate fourth chart + fifth KPI cell |
| `res/layout/activity_dashboard.xml` | 8.4 | Add fourth `IkdLineChartView` card + fifth KPI cell |
| `res/values/strings.xml` | 8.2 / 8.3 / 8.4 | Add mood-related strings |
| `res/values/dimens.xml` | 8.2 | Add `mood_bar_button_size` |
| `helpers/IkdCsvWriter.kt` | 8.1 / 8.2 | Append the third dual-block segment for mood entries |
| `helpers/IkdRetentionWorker.kt` | 8.1 | Add the sessionless-mood retention call (one-line addition) |
| `CLAUDE.md` | 8.4 | Add Phase 8 section (parallel to Phase 5/6/7) |
| `roadmap/FeatureRoadmap.md` | 8.4 | Status → Implemented; link to this plan |

### Forbidden (do not touch — see Section 2 for full list)

`SimpleKeyboardIME.kt`, `LiveCaptureSessionStore.kt`, `KinematicSensorHelper.kt`, all `@Entity` data classes from earlier phases, `ClipsDatabase.kt`, `ClipsDao.kt`, `IkdAggregator.kt`, `IkdSessionStatsLoader.kt`, `IkdSessionChartLoader.kt`, `views/IkdLineChartView.kt`, `interfaces/SessionDao.kt` / `IkdEventDao.kt` / `SensorSampleDao.kt`, `interfaces/EventBucketRow.kt` / `SessionBucketRow.kt` / `TimingBucketRow.kt` / `SensorBucketRow.kt` / `SessionStatsRow.kt`. Phase 6 surfaces (`activities/DiagnosticsActivity.kt`, `res/layout/activity_diagnostics.xml`, `res/menu/menu_diagnostics.xml`, `res/values{,-night}/colors.xml` for status chips) — also frozen.

---

## 8. Acceptance Criteria

The whole phase is done when **all** of these are green on `feat/phase8-mood-bar`:

- [ ] `./gradlew assembleCoreDebug` succeeds.
- [ ] `./gradlew testCoreDebugUnitTest` passes (existing + new `IkdMoodAggregatorTest`).
- [ ] `./gradlew connectedCoreDebugAndroidTest` (or the equivalent local instrumented task) passes the new `IkdDatabaseMigrationTest`.
- [ ] `./gradlew detekt` and `./gradlew lint` produce no new issues vs. `main` baseline.
- [ ] `IkdDatabase.version == 2` and `MIGRATION_1_2` is registered.
- [ ] Existing v1 user databases migrate cleanly (verified by pulling a v1 device DB, installing the v2 build, confirming sessions still load).
- [ ] No file in Section 2's "Still forbidden" list is modified (verifiable via `git diff main..feat/phase8-mood-bar --name-only`).
- [ ] Mood bar appears in the keyboard top bar when `Config.showMoodBar = true`; hidden otherwise.
- [ ] Tapping a mood emoji writes one row (or replaces the existing row) for the active session; tapping 🫥 deletes the row.
- [ ] Per-session dashboard shows the fifth KPI cell + metadata chip when a `MoodEntry` exists; gracefully collapses otherwise.
- [ ] Global dashboard shows the fourth "Mood over Time" chart and the "Avg Mood" KPI cell when at least one mood entry exists in the selected range; both are hidden otherwise.
- [ ] CSV export produces a third `#mood_entries` block; bulk export does likewise. Existing two blocks unchanged.
- [ ] `MoodEntry` rows store only `(id, session_id, timestamp, mood_score)` — no text, no emoji codepoint.
- [ ] `roadmap/Phase8/Phase8_Plan.md` exists.
- [ ] `CLAUDE.md` has a Phase 8 section.
- [ ] `roadmap/FeatureRoadmap.md` Phase 8 status is updated to `Implemented`.

---

## 9. Decisions

| # | Topic | Decision |
|---|---|---|
| 1 | Branch strategy | All work on `feat/phase8-mood-bar`; left local for the user's review — **no push, no PR opened by the implementer**. Same pattern as Phase 6 / Phase 7. |
| 2 | Schema migration vs. additive column on `sessions` | **New `mood_entries` table.** A column on `sessions` would conflate "the user typed in this session" with "the user rated this session" — the row exists from the moment the keyboard opens, before the user has had a chance to rate. A separate table also lets us cleanly support "tap a mood while privacy mode is on" via `session_id = NULL`. |
| 3 | One-mood-per-session vs. mood-as-time-series | **One mood per session** (enforced via partial unique index). The bar is for quick annotation, not a time-series log; allowing multiple would invite "what does mood mean over the session?" interpretation questions that we don't want to answer in v1. Re-tap simply replaces. |
| 4 | Score range | **1–5 integer.** Mirrors the five emoji. Float scores invite false precision. |
| 5 | Score → emoji mapping location | **UI layer only** (`helpers/MoodEmoji.kt`). The DB is privacy-clean. |
| 6 | Mood while privacy mode is on | **Allowed**, written with `session_id = NULL`. The user explicitly opted in to the mood bar (via `Config.showMoodBar`); excluding privacy-mode taps would be more surprising than including them. These rows feed the global dashboard but are excluded from per-session loaders. |
| 7 | Bar default state | **🫥 highlighted (no choice).** Forces an explicit user gesture before any DB write — privacy by default. |
| 8 | Bar position in keyboard | **Top bar, leading-aligned, before the privacy-toggle shield.** Doesn't fight the existing right-edge controls. Phase 6 already established the top bar as the home for IKD-related controls. |
| 9 | Settings toggle | **Off by default.** A six-emoji bar is a visual change to the keyboard; opt-in is the right default. Surfaced as one row in `IkdSettingsActivity`. |
| 10 | Bucket formula for global mood chart | **Same as the existing IKD chart** — `%Y-%m-%d` for Week/Month, `%Y-%W` for All Time. Lets the user visually correlate mood with WPM / IKD / error rate at the same X-axis bucket width. |
| 11 | Avg-mood KPI behaviour when no mood entries | **Hidden** (cell `View.GONE`). Showing "—" or "N/A" would imply a value; hiding makes "the data isn't here" explicit. |
| 12 | Null-bucket rendering on the chart | **Line break (gap)** — same as the existing IKD / sensor charts. `IkdLineChartView` already supports this via `Float?` values. |
| 13 | Mood in CSV | **Third dual-block segment.** Strictly additive; existing parsers stay backwards-compatible. |
| 14 | Score → emoji wording (locale) | **English only in v1.** The five emoji are universal; the labels follow the keyboard's existing string-translation flow. Custom mood scales for non-English speakers are a Phase 10 (rebrand) concern. |
| 15 | Color / style of selected button | **Theme attribute (`?attr/colorAccent`)**, not hardcoded. Matches Phase 6's status-chip discipline. |
| 16 | Capture path edits | **None.** `SimpleKeyboardIME.kt` and `LiveCaptureSessionStore.kt` are not edited. The mood bar reads `LiveCaptureSessionStore.currentSessionId` and writes via `MoodDao` directly on a background thread. |
| 17 | Retention worker edit scope | **One additive `DELETE FROM mood_entries WHERE session_id IS NULL AND timestamp < ?` call.** The sessioned mood entries are dropped by CASCADE when the session row is dropped — no extra code needed. The sessionless ones need the explicit query. This is a four-line diff. |
| 18 | Migration test surface | **Instrumented (`androidTest`)** because Room migration tests require the actual SQLite implementation. Everything else stays JVM-only. |
| 19 | DAO `getMoodDistribution` shipped this phase | **Yes, signature locked here.** Phase 9 will consume it for the bar chart; defining the projection now means Phase 9 is read-side-only. |
| 20 | Per-session live-mode mood | **Not surfaced.** Live mode is the real-time event log; mood is a session-level annotation. The two don't compose. |

---

## 10. Explicitly Deferred to Later Phases

These were on the mood-bar wishlist but are out of scope for Phase 8:

- **Mood Distribution bar chart on global dashboard** — DAO method shipped here; rendering deferred to Phase 9.
- **Mood-correlated metrics** ("your typing speed when sad vs. happy") — Phase 9. Needs a JOIN of `ikd_events` aggregations against the `mood_entries` filter.
- **Custom emoji / wording / locales** — Phase 10 (rebrand) concern.
- **Free-text journal entries** — privacy invariant violation; needs a separate design pass.
- **Multi-mood per session as time-series** — explicit decision against (Decision #3).
- **Mood reminders / nudges** — yagni.
- **Live-mode mood overlay** — Decision #20.
- **Mood in `DiagnosticsActivity`** — Phase 6 surface is frozen; the diagnostics screen stays IKD-only.
- **`MoodEntry` exposed via the bulk JSON export** (if/when JSON export is added) — out of scope until JSON export ships.
- **Anomaly markers on the mood chart** — already deferred from Phase 3.

Anything from this list earns its own focused mini-plan in `roadmap/Phase{N}/` if and when the user wants it.
