# Phase 8 — Mood Bar & Contextual Overlay

**Status:** Implemented (sub-phases 8.1 → 8.4 merged via `feat/phase8-mood-bar`); UI polish landed via `fix/phase8-ui-polish` — see [Section 12](#12-post-merge-ui-polish-phase-81).
**Depends on:** Phase 5 (per-session dashboard layout — adds a mood KPI cell), Phase 3 (`DashboardActivity` — adds a mood trend chart, distribution panel, and Avg Mood KPI), Phase 7 (capture-layer reopen pattern that this phase mirrors at smaller scope), Phase 2 (existing privacy-toggle button — subsumed into the new bar; existing `Config.privacyModeEnabled` flag — surfaced in settings)
**Branch:** `feat/phase8-mood-bar` — cut from `main` after Phase 7 has been merged. Polish landed on `fix/phase8-ui-polish` in a follow-up merge.
**Scope (one sentence):** Replace the standalone privacy-toggle button with a seven-button integrated emotion bar — privacy shield + Ekman's six basic emotions ordered best-to-worst by valence (`🛡️ 😊 😲 🤢 😢 😨 😠`) — on the keyboard toolbar, surface the existing `Config.privacyModeEnabled` flag in `IkdSettingsActivity` as a "Privacy mode on by default" toggle, persist the chosen emotion as a per-session `MoodEntry` row in `ikd.db`, and surface that signal as a new dimension on both the per-session dashboard (`EventFeedActivity`) and the global insights dashboard (`DashboardActivity`) — without touching the IKD capture path.

> **Migration version note.** This plan is written against the original
> phase ordering, in which Phase 8 was the first migration on `ikd.db`
> (1 → 2). In practice, Phase 8 shipped first and Phase 7.1 — the
> AUTOCORRECT replacement-weight phase — landed afterwards, adding its
> own `Migration(2, 3)` for the `correction_weight` column. So while the
> body of this plan refers throughout to `Migration(1, 2)` and
> "first-ever migration", the live `IkdDatabase.version` is now `3`, and
> `MIGRATION_1_2` here is one of two registered migrations alongside
> `MIGRATION_2_3` from Phase 7.1. The schema bytes added by this phase
> are unchanged.

This is the **first phase to bump `IkdDatabase.version`** (1 → 2). The `mood_entries` table is added via a strictly additive `Migration(1, 2)` — no existing column or row is rewritten. It is also the first phase since Phase 7 to reopen `MyKeyboardView.kt`, but only to add the emotion bar and remove the now-redundant standalone `privacy_toggle_button` from Phase 2; the existing key-press / key-up / emoji code paths are not edited. Capture (`SimpleKeyboardIME`, `LiveCaptureSessionStore`, `KinematicSensorHelper`, the `IkdEvent` / `SensorSample` entities, `IkdRetentionWorker`) is fully frozen.

The six emotion categories are **Ekman's basic emotions** [1, 2] — anger, disgust, fear, happiness, sadness, surprise. The Ekman list is research-grounded; the **best-to-worst valence ordering** of the score id (1 = Happiness, 6 = Anger) is a deliberate Phase 8 product choice, not part of Ekman's discrete-emotions framework — see Decision #4 and Section 11 for what's sourced and what's a product call. The ordinal scoring means `AVG(mood_score)` is mathematically meaningful, so the global dashboard supports both an "Avg Mood" KPI and a "Mood over Time" line chart.

**No Neutral category.** The no-auto-write rule (Decision #10) already covers "user typed but did not feel a strong enough emotion to label" — sessions without a `MoodEntry` row serve that role implicitly. Adding a Neutral slot would either require auto-writing it (which corrupts dashboard aggregations) or duplicate the same "no row" semantic with a row, which is worse.

---

## Table of Contents

1. [Concept & Scope](#1-concept--scope)
2. [Branch & Layering Discipline (REOPEN keyboard view; SCHEMA MIGRATION)](#2-branch--layering-discipline-reopen-keyboard-view-schema-migration)
3. [Schema Change: `mood_entries` Table](#3-schema-change-mood_entries-table)
4. [Mood Bar UI Spec](#4-mood-bar-ui-spec)
5. [Sub-Phases (4)](#5-sub-phases-4)
   - [8.1 Schema Migration + MoodEntry Entity + MoodDao](#81-schema-migration--moodentry-entity--mooddao)
   - [8.2 Emotion Bar on Keyboard Toolbar + Privacy-Default Setting](#82-emotion-bar-on-keyboard-toolbar--privacy-default-setting)
   - [8.3 Mood Overlay on Session Dashboard](#83-mood-overlay-on-session-dashboard)
   - [8.4 Mood Trend Chart, Distribution Panel, and Avg Mood KPI on Global Dashboard](#84-mood-trend-chart-distribution-panel-and-avg-mood-kpi-on-global-dashboard)
6. [Downstream Impact (Dashboards, CSV, Privacy)](#6-downstream-impact-dashboards-csv-privacy)
7. [Files to Create / Modify (vs. forbidden)](#7-files-to-create--modify-vs-forbidden)
8. [Acceptance Criteria](#8-acceptance-criteria)
9. [Decisions](#9-decisions)
10. [Explicitly Deferred to Later Phases](#10-explicitly-deferred-to-later-phases)
11. [References](#11-references)
12. [Post-merge UI Polish (Phase 8.1)](#12-post-merge-ui-polish-phase-81)

---

## 1. Concept & Scope

The IKD pipeline today captures *what* the user typed and *how fast* — six event categories (eight after Phase 7), timing, sensors, error rate. It does not capture *how the user felt while typing it.* Phase 8 adds a single tap-and-forget channel for that signal, integrated with the existing privacy control: a horizontal seven-button strip in the keyboard's top bar where the leftmost button (`🛡️`) is the privacy toggle and the next six (`😊 😲 🤢 😢 😨 😠`) annotate the active session's emotion using a valence-ordered set of Ekman's six basic emotions [1, 2]. All seven are mutually exclusive — at most one is highlighted — so the bar's state cleanly encodes both privacy and (optionally) emotion in one control surface.

The recorded emotion becomes a first-class dimension in the dashboards: a fifth KPI cell and a metadata chip on the per-session screen, plus a "Mood over Time" line chart, a "Mood Distribution" panel, and an "Avg Mood" KPI on the global insights screen. Sessions without a mood entry are excluded from mood aggregations — missing data is *missing*, not zero, and not synthetic.

`IkdSettingsActivity` gains one new row — "Privacy mode on by default" — which exposes the existing `Config.privacyModeEnabled` flag from Phase 2 as a settings-screen toggle. Today the only way for users to flip privacy mode is via the keyboard's 🛡️ button; this row gives them an alternate path from the settings UI and surfaces the capability for users who haven't discovered the bar.

### Why this taxonomy

The six **Ekman emotions** (anger, disgust, fear, happiness, sadness, surprise) are the most-cited basic-emotions set in psychology, grounded in cross-cultural facial-expression recognition and the foundation of FACS [1, 2, 3]. They give us a defensible categorical baseline.

One deliberate deviation from pure Ekman, made for product reasons:
- **Best-to-worst valence ordering on the score id** — Ekman treats his six categories as discrete and unordered. We impose a 1 (Happiness) → 6 (Anger) ordering so the integer is informative on its own and `AVG(mood_score)` is meaningful. The ordering is a UX convention, not a claim about psychological reality.

Pure Ekman 6 is preserved (no Neutral, no merged categories). The "user did not pick an emotion" state is represented by the **absence of a `MoodEntry` row**, not by a synthetic Neutral entry — Decision #10. This keeps the dashboard aggregations honest: every category in the Distribution panel is a category the user actively chose.

Other taxonomies considered and rejected for the keyboard top bar:
- Plutchik's 8 (joy, trust, fear, surprise, sadness, disgust, anger, anticipation) [4] — adds *trust* and *anticipation*, awkward as session-level annotations for a typing experience.
- Geneva Emotion Wheel (20 emotions on valence × control) [5] — research instrument, far too many for a single-tap keyboard bar.
- Cowen & Keltner 27 categories [6] — empirically validated for self-report but assumes a tiered selection UI we don't have.
- Apple Health "State of Mind" hybrid (valence slider + ~14 labels) [7] — strong product design, but adds a two-tier interaction layer beyond a single keyboard row.
- Pure 5-point valence (Daylio-style) — averaging is sound but conflates anger with sadness, which the IKD dataset cannot recover after the fact.
- Ekman 6 + Neutral hybrid (an earlier Phase 8 iteration) — rejected once we confirmed the no-auto-write rule already serves Neutral's purpose without polluting the schema with a synthetic category.

Decision #4 records this analysis. References are listed in Section 11.

### Emotion model

Six categorical emotions, ordered best-to-worst by valence (left-to-right after the shield):

| Slot | Emoji | Emotion | Stored `mood_score` | Source |
|---|---|---|---|---|
| 1 | 😊 | Happiness | 1 (best) | Ekman |
| 2 | 😲 | Surprise | 2 | Ekman |
| 3 | 🤢 | Disgust | 3 | Ekman |
| 4 | 😢 | Sadness | 4 | Ekman |
| 5 | 😨 | Fear | 5 | Ekman |
| 6 | 😠 | Anger | 6 (worst) | Ekman |

The integer is an **ordinal valence id**: lower = better, higher = worse. This means:
- `AVG(mood_score)` is mathematically meaningful (it's a valence average); a result of 3.2 means "around Disgust, slightly toward Sadness."
- `MIN`, `MAX`, and percentile aggregations are also valid.
- The Distribution panel still uses counts per category — no information is lost.

### In scope

- **Schema:** New `mood_entries` table with columns `id` (PK, autogen), `sessionId` (nullable FK → `sessions.session_id`, CASCADE on delete), `timestamp` (Long, ms), `moodScore` (Int, 1–6 ordinal). One row per session at most (enforced by `UNIQUE` index on `sessionId WHERE sessionId IS NOT NULL`); the `sessionId IS NULL` case is allowed by the schema for future-proofing but is **not produced by the keyboard bar in this phase** — see Decision #6.
- **Migration:** `IkdDatabase.version` 1 → 2 via a `Migration(1, 2)` that runs `CREATE TABLE` + `CREATE UNIQUE INDEX`. Non-destructive, no existing data touched.
- **DAO:** New `MoodDao` with `insertOrReplaceForSession`, `clearForSession`, `getForSession`, `getMoodBuckets` (per-day/week avg-and-count aggregation for the global trend chart and Avg Mood KPI), `getMoodDistribution` (6-row count projection — feeds the Mood Distribution panel directly).
- **Emotion Bar UI:** Seven-button horizontal strip rendered in `MyKeyboardView`'s top bar — `🛡️ 😊 😲 🤢 😢 😨 😠`. Buttons are mutually exclusive. The bar **replaces** the standalone privacy-toggle button shipped in Phase 2; the existing `privacy_toggle_button` view is removed. Bar is **always visible** — there is no setting to hide it.
- **Privacy toggle (now via 🛡️ slot):** Tapping 🛡️ enables privacy mode (same effect as the old shield button — `Config.privacyModeEnabled = true`, in-flight session finalised, any in-flight `MoodEntry` deleted). Tapping any of the six emotion buttons disables privacy mode (`Config.privacyModeEnabled = false`) and writes/replaces the active session's `MoodEntry`. Default highlighted state is 🛡️ when privacy is on (today's Phase 2 default); when privacy is off and the user has not yet tapped an emotion, **no button is highlighted** — this is a valid "capture is on, no rating yet" state.
- **No auto-write:** the bar does not write any default mood row. A session without a user tap simply has no `MoodEntry` row, regardless of privacy state. This preserves the integrity of the dashboard aggregations (no synthetic entries diluting the signal) — Decision #10.
- **Privacy-default setting:** `IkdSettingsActivity` gains one new row — "Privacy mode on by default" — that surfaces the existing `Config.privacyModeEnabled` flag from Phase 2 as a settings-screen toggle. Toggling it has the same effect as tapping the keyboard's 🛡️ button. Users who prefer capture-first can flip the flag here once and the state persists. The `Config.privacyModeEnabled` flag itself is unchanged from Phase 2 — only the UI affordance is new. See Decision #25.
- **Per-session overlay:** `EventFeedActivity` (DB-backed mode only) gets a fifth KPI cell rendering the emotion emoji + label when `MoodEntry` exists for that session; gracefully collapses to four cells otherwise. *(The originally planned metadata-line "Mood: 😊 Happiness" chip was dropped in the Phase 8.1 polish — see Section 12 — because it duplicated the KPI cell.)*
- **Global dashboard:** `DashboardActivity` gains three additions when at least one mood entry exists in the selected range:
  - A fourth `IkdLineChartView` card "Mood over Time" rendering daily/weekly avg mood scores (y-axis labelled "Mood (1=Happiness → 6=Anger)"); null buckets render as line breaks.
  - A "Mood Distribution" panel below the line charts (six rows, one per emotion, each with emoji + label + horizontal progress bar + count).
  - A fifth KPI cell "Avg Mood" showing the rounded-to-nearest-emotion label plus the precise number (e.g. `🤢 3.2`).
- **Privacy invariant:** `MoodEntry` stores only the integer ordinal score (1–6) and a timestamp. The score → emotion mapping lives in the UI layer only (`helpers/MoodEmoji.kt`). CSV export gains a third dual-block segment for mood rows.

### Out of scope (deferred — see Section 10)

- Stacked-bar-by-day chart (sub-bars per category) — a richer rendering that the same `getMoodBuckets` DAO method could power; deferred to Phase 9.
- Mood-correlated metrics (e.g. "your typing speed when angry vs. happy") — Phase 9.
- Multi-mood per session (the bar is for quick annotation, not a time-series log; one row per session at most).
- Free-text journal entries (privacy invariant violation; a future phase would need a rethink of the privacy model).
- Custom emotion taxonomies / wording / locales — Phase 10 (rebrand) concern.
- Reminders / nudges to rate the session — yagni.
- Mood signal in CSV header columns (we add a sibling `mood_entries` block, not a per-event column).
- Mood on the Diagnostics screen (`DiagnosticsActivity`) — Phase 6 surface stays frozen.
- Sessionless mood entries (`session_id = NULL`) — column stays nullable but the keyboard bar never writes them (mutual exclusion guarantees a session id whenever an emotion is tapped).
- Intensity / strength of emotion (e.g. annoyance → anger → rage) — Plutchik's intensity model; not part of the Phase 8 schema. Out of scope.
- Valence / arousal axes (Russell's circumplex) — out of scope; the Phase 8 ordering is single-axis valence only.
- Auto-write a default mood on session start — Decision #10. Would corrupt Avg Mood and Distribution by inflating one category's count with passive entries.
- A Neutral / no-emotion category — Decision #11. The no-auto-write rule already serves this purpose via row absence.
- Separate `Config.privacyOnByDefault` flag — Decision #25. The settings row reads/writes the existing `Config.privacyModeEnabled` directly; no new flag is added in this phase.

---

## 2. Branch & Layering Discipline (REOPEN keyboard view + settings; SCHEMA MIGRATION)

Phase 8 reopens **three** previously-frozen surfaces. All three are scoped; one is additive + a Phase 2 view removal, one is purely additive (settings), one is a schema bump.

### Reopened files

| File | Why reopened | Edit shape |
|---|---|---|
| `views/MyKeyboardView.kt` | Hosts the seven-button emotion bar in the top bar; subsumes the Phase 2 privacy toggle | **Additive:** add a horizontal `LinearLayout` (or seven `MyButton`s) in the top bar with click handlers calling `IkdMoodBarController`. **Removal:** delete the existing `privacy_toggle_button` view and its click handler — its semantics move to the new 🛡️ slot. The existing key-press / drag / popup code is untouched. |
| `databases/IkdDatabase.kt` | Schema bump 1 → 2 with the new `mood_entries` table | Add `MoodEntry::class` to `entities`, add `MoodDao()` abstract, add `Migration(1, 2)` running `CREATE TABLE mood_entries (...)` and `CREATE UNIQUE INDEX ...` |
| `activities/IkdSettingsActivity.kt` and its layout (`activity_ikd_settings.xml`) | Surface the existing Phase 2 `Config.privacyModeEnabled` flag as a settings-screen toggle | Add one new toggle row "Privacy mode on by default"; wire it to read/write `Config.privacyModeEnabled` directly. No new flag added — see Decision #25. |
| Top-bar layout XML referenced by `MyKeyboardView` (e.g. the keyboard view's top-bar include) | Remove `privacy_toggle_button` view; add the seven-button bar | One view delete + one new `LinearLayout` + 7 child views |

### Still forbidden (capture path frozen)

| File | Reason |
|---|---|
| `services/SimpleKeyboardIME.kt` | Capture path — frozen since Phase 7 closed. Mood writes do **not** go through the IME |
| `helpers/LiveCaptureSessionStore.kt` | Capture buffer — frozen. Mood writes use a sibling `IkdMoodBarController` that calls `MoodDao` directly on a background `Thread { }` |
| `helpers/KinematicSensorHelper.kt` | Sensor path — unrelated to mood |
| `helpers/IkdRetentionWorker.kt` | Retention worker is generic over `sessions` deletion; CASCADE handles `mood_entries` automatically. Because the keyboard bar never writes `session_id = NULL` rows (mutual exclusion), there are no sessionless mood entries to retain — the worker needs no edit |
| `models/IkdEvent.kt`, `models/SensorSample.kt`, `models/SessionRecord.kt` | No new column on existing entities; the new table is sibling, not an extension |
| `helpers/IkdAggregator.kt` | Phase 3 read surface — extended only via the additive `MoodDao.getMoodBuckets` / `getMoodDistribution` methods called from a sibling `IkdMoodAggregator` (new helper). The existing `Companion.buildSnapshot(...)` remains pure-IKD aggregation; the activity composes mood + IKD snapshots |
| `helpers/IkdSessionStatsLoader.kt` | Phase 4 read surface — frozen. Per-session mood is loaded via a separate `IkdMoodLoader` on the same `Dispatchers.IO` hop |
| `helpers/IkdSessionChartLoader.kt` | Phase 5 read surface — frozen |
| `helpers/IkdCsvWriter.kt` | CSV format gets one **additional** dual-block segment (`#mood_entries\nsession_id,timestamp_ms,mood_score`) appended to the existing two — strictly additive, parsers reading only the first two blocks remain compatible |
| `views/IkdLineChartView.kt` | Chart wrapper — frozen at single-series. The Mood-over-Time line chart reuses it as-is via the existing `setData(labels, values, yLabel)` API; no edit needed |
| `activities/DiagnosticsActivity.kt`, `res/layout/activity_diagnostics.xml`, `res/menu/menu_diagnostics.xml`, `res/values{,-night}/colors.xml` (status chips) | Phase 6 surface — frozen |
| `helpers/Constants.kt`, `helpers/Config.kt` | No new pref keys this phase. The settings row in 8.2 reuses the existing `Config.privacyModeEnabled` from Phase 2 — no new property, no new constant. |

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

- **`session_id` nullable** — the column stays nullable for resilience and future-proofing. In Phase 8 the keyboard bar guarantees a `sessionId` whenever it writes (mutual exclusion: tapping an emotion implicitly turns capture on), but a future writer (e.g. a journaling surface) might want sessionless entries — keeping the column nullable avoids a future migration.
- **`UNIQUE WHERE session_id IS NOT NULL`** — at most one `MoodEntry` per session. Tapping a different emotion triggers `INSERT OR REPLACE` to overwrite. Tapping 🛡️ deletes the row. No history.
- **`mood_score INT` (1–6 ordinal valence)** — the only stored signal. Lower values are more positive (1 = Happiness), higher are more negative (6 = Anger). Privacy invariant preserved: no text, no codepoint.
- **`ON DELETE CASCADE`** — when retention worker drops a session row, its mood entry goes with it. Same pattern as `ikd_events` / `sensor_samples`.
- **`AUTOINCREMENT id`** — surrogate key. Allows future sessionless-mood rows without rethinking the FK constraint.

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
- Assert that inserting two mood entries with `session_id = NULL` succeeds (validates the partial unique index — even though the keyboard never produces such rows, the column allows them by design)

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

`IkdCsvWriter.kt` is reopened **only** to append the third block. Parsers that read only the first two blocks remain compatible (the `#mood_entries` line is treated as an unknown header and the rest as garbage — the existing parser already tolerates trailing garbage). `mood_score` values follow the ordinal valence map from Section 1 (1 = Happiness ... 6 = Anger).

---

## 4. Mood Bar UI Spec

### Visual layout (keyboard top bar)

```
┌──────────────────────────────────────────────────────────────────────┐
│  🛡️  😊  😲  🤢  😢  😨  😠              [other top-bar]              │
└──────────────────────────────────────────────────────────────────────┘
                                                                       ▼ keyboard rows
```

Seven buttons in a horizontal `LinearLayout`, leading-aligned. Each button is a `MyButton` with `text` set to the emoji and a fixed width (`@dimen/mood_bar_button_size = 32dp`). The standalone Phase 2 `privacy_toggle_button` view is **removed** — its semantics move to the leftmost 🛡️ slot. Order is best-to-worst valence (Happiness → Surprise → Disgust → Sadness → Fear → Anger).

### Mutual-exclusion state machine

At most one button is highlighted at any time. When privacy is off and the user has not yet tapped an emotion this session, **no button is highlighted** — this is a valid "capture is on, no rating chosen" state.

| User action | DB write | `Config.privacyModeEnabled` after | UI update |
|---|---|---|---|
| Open keyboard, privacy on (Phase 2 default) | none | `true` (preserved) | 🛡️ highlighted, others dim |
| Open keyboard, privacy off | none | `false` (preserved) | All buttons dim until the user taps one (or until a `MoodEntry` is found for the current session id, in which case the matching emotion is highlighted) |
| Tap any of the six emotions from any state | `INSERT OR REPLACE INTO mood_entries (session_id, timestamp, mood_score) VALUES (?, NOW, score)` after privacy is flipped off and a session is started if needed | `false` | tapped emoji highlighted |
| Re-tap currently-highlighted emotion | no-op | unchanged | no change |
| Tap a different emotion than the one currently highlighted | `INSERT OR REPLACE … score = newScore` (same `session_id` so the unique index overwrites) | `false` | new emoji highlighted |
| Tap 🛡️ from any other state | `DELETE FROM mood_entries WHERE session_id = ?` (clears the in-flight session's mood); in-flight session is finalised by the existing Phase 2 path (`LiveCaptureSessionStore.stopSession()`) | `true` | 🛡️ highlighted, others dim |
| Toggle "Privacy mode on by default" in `IkdSettingsActivity` to ON | If a session is in flight when the user returns to the keyboard, the existing 🛡️ flow runs (finalise + clear); otherwise the next keyboard open just sees `Config.privacyModeEnabled = true` | `true` | next keyboard open: 🛡️ highlighted |
| Toggle the same setting to OFF | Same as the user tapping 🤷 — but no `MoodEntry` is written (no auto-write) | `false` | next keyboard open: all buttons dim |
| Keyboard re-opens after privacy was on | none | `true` | 🛡️ highlighted |
| Keyboard re-opens after privacy was off (new session id) | none (no auto-write) | `false` | All buttons dim — user must tap an emotion to rate the new session |

**Mutual-exclusion guarantee:** because tapping any of the six emotion buttons flips `Config.privacyModeEnabled` to `false` and writes a row keyed on the active session id, the keyboard never writes a `MoodEntry` row with `session_id = NULL`. The schema permits it; the UI does not produce it.

**Privacy parity with Phase 2:** the Phase 2 privacy-toggle behaviour (toggling `Config.privacyModeEnabled` and finalising the in-flight session via `LiveCaptureSessionStore.stopSession()` when flipping to ON) is preserved verbatim — it now lives on the 🛡️ slot's click handler instead of the standalone button's click handler. The settings-screen toggle (8.2) updates the same flag and triggers the same finalisation path when the user is currently in a session and flips it on.

**Why no auto-write:** auto-writing any default emotion on session start would inflate that emotion's count in the Distribution panel and pull the Avg Mood KPI in its direction with passive (non-chosen) entries. Sessions where the user did not tap stay empty; that absence is the absence-of-rating signal. Decision #10. This same rule also makes a "Neutral" slot redundant — Decision #11.

### Score → emoji / state mapping (UI layer only)

| Slot | Emoji | Score (DB) | Privacy mode | Label key |
|---|---|---|---|---|
| 0 | 🛡️ | (no row) | `true` | `privacy_label_on` |
| 1 | 😊 | 1 (best) | `false` | `mood_label_happiness` |
| 2 | 😲 | 2 | `false` | `mood_label_surprise` |
| 3 | 🤢 | 3 | `false` | `mood_label_disgust` |
| 4 | 😢 | 4 | `false` | `mood_label_sadness` |
| 5 | 😨 | 5 | `false` | `mood_label_fear` |
| 6 | 😠 | 6 (worst) | `false` | `mood_label_anger` |

The DB only stores the integer ordinal valence (1–6). Privacy state lives on `Config.privacyModeEnabled` (unchanged from Phase 2). The 🛡️ slot is a UI-only state with no DB row. Score → emoji mapping lives in `helpers/MoodEmoji.kt` so it can be reused across the keyboard bar, the per-session KPI cell, the metadata chip, and the global dashboard panel + KPI + chart.

### Highlight styling

Selected button: `background = ?attr/colorAccent` (or the existing keyboard-key pressed colour from `MyKeyboardView`'s theme), `alpha = 1.0f`. Unselected: `background = transparent`, `alpha = 0.6f`. Theme integration follows the same pattern as Phase 6's status chip — colours are theme-attr-driven, no hardcoded RGB.

### Settings

`IkdSettingsActivity` gains two new rows in the existing capture/privacy section:

| Row | Backing flag | Default | Description |
|---|---|---|---|
| Privacy mode on by default | `Config.privacyModeEnabled` (existing Phase 2 flag) | `true` | When enabled, the keyboard starts in privacy mode (no data captured). Tap the keyboard's 🛡️ button or this toggle to change. |
| Show mood bar in keyboard *(added in Phase 8.1 polish — see Section 12)* | `Config.showMoodBar` (new) | `true` | When disabled, the seven-button bar is `View.GONE` in `MyKeyboardView`. Privacy can still be toggled from the row above. |

The first toggle is bidirectional with the keyboard's 🛡️ button — both write to the same flag. There is no separate `Config.privacyOnByDefault` (Decision #25). Toggling the setting to ON while a session is in flight runs the same finalisation path as tapping 🛡️ on the keyboard (see Phase 2 semantics).

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
  - `@Query("SELECT mood_score AS score, COUNT(*) AS entryCount FROM mood_entries WHERE timestamp BETWEEN :fromMs AND :toMs GROUP BY mood_score ORDER BY mood_score") suspend fun getMoodDistribution(fromMs: Long, toMs: Long): List<MoodDistributionRow>`
  - `@Query("SELECT strftime(:bucketFormat, timestamp / 1000, 'unixepoch', 'localtime') AS bucket, AVG(mood_score) AS avgScore, COUNT(*) AS entryCount FROM mood_entries WHERE timestamp BETWEEN :fromMs AND :toMs GROUP BY bucket ORDER BY bucket") suspend fun getMoodBuckets(bucketFormat: String, fromMs: Long, toMs: Long): List<MoodBucketRow>` (powers both the Mood-over-Time line chart and the Avg Mood KPI in 8.4.)
- `interfaces/MoodDistributionRow.kt` (new): POJO `score: Int`, `entryCount: Int`.
- `interfaces/MoodBucketRow.kt` (new): POJO `bucket: String`, `avgScore: Double`, `entryCount: Int`.
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

### 8.2 Emotion Bar on Keyboard Toolbar + Privacy-Default Setting

**Goal:** Seven emoji buttons appear in the keyboard top bar (always visible). The leftmost 🛡️ slot replaces the Phase 2 standalone privacy-toggle button. Tapping any of the six emotion buttons disables privacy mode and writes a `MoodEntry` row keyed on the active session. Tapping 🛡️ enables privacy mode and clears any existing mood row for the in-flight session. No auto-write on session start — the bar simply has nothing highlighted until the user explicitly taps an emotion (or 🛡️ stays highlighted if privacy was on). `IkdSettingsActivity` gains a "Privacy mode on by default" toggle backed by the existing `Config.privacyModeEnabled` flag.

#### Deliverables

- `helpers/MoodEmoji.kt` (new): `object MoodEmoji { fun emojiFor(score: Int): String; fun labelResFor(score: Int): Int; fun displayOrder(): IntArray = intArrayOf(1,2,3,4,5,6); const SHIELD_EMOJI = "🛡️"; const PRIVACY_LABEL = R.string.privacy_label_on }`. The six emotions are mapped (1 → 😊 / `mood_label_happiness`, 2 → 😲 / `mood_label_surprise`, 3 → 🤢 / `mood_label_disgust`, 4 → 😢 / `mood_label_sadness`, 5 → 😨 / `mood_label_fear`, 6 → 😠 / `mood_label_anger`). Mood-emoji mapping does **not** live in `Constants.kt` (UI-only).
- `helpers/IkdMoodBarController.kt` (new): exposed via `Context.ikdMoodBarController`. Methods (all on `Dispatchers.IO` unless noted):
  - `suspend fun setMoodForActiveSession(score: Int)` — flips `Config.privacyModeEnabled` to `false` if needed, ensures `LiveCaptureSessionStore` has an active session id, writes a `MoodEntry` keyed on that id with the given ordinal score (1–6).
  - `suspend fun enablePrivacyAndClearMood()` — flips `Config.privacyModeEnabled` to `true`; if a `MoodEntry` exists for the in-flight session, deletes it. Existing Phase 2 stop-session logic (calling `LiveCaptureSessionStore.stopSession()`) runs from the existing call path, untouched.
  - `suspend fun getMoodForActiveSession(): MoodEntry?`
  - The controller does **not** edit `LiveCaptureSessionStore` — it only reads `currentSessionId`. The controller never auto-writes a default mood.
- `views/MyKeyboardView.kt` (modified):
  - Remove the existing `privacy_toggle_button` view and its click handler.
  - Add a horizontal `LinearLayout` (or seven `MyButton`s programmatically) in the top bar. Always visible.
  - Click handlers launch `Thread { }` (or a `CoroutineScope(Dispatchers.IO)`) calling the appropriate `IkdMoodBarController` method. UI state updates on the main thread via `runOnUiThread`.
  - On `onStartInputView` (which `MyKeyboardView` is told about by the IME), reset the bar's highlighted slot from `Config.privacyModeEnabled` (🛡️ if true; nothing highlighted if false). Re-fetch any existing `MoodEntry` for the active session and highlight the corresponding emotion if a row exists (defensive — under the spec a fresh session has no mood row until the user taps).
- `activities/IkdSettingsActivity.kt` (modified):
  - Add a new `MyAppCompatCheckbox` row "Privacy mode on by default" in the existing capture/privacy section (above or near the existing privacy-related rows).
  - Bind it to `Config.privacyModeEnabled` (existing Phase 2 flag — read on activity create, write on toggle). Toggling to ON while a session is in flight calls the same `IkdMoodBarController.enablePrivacyAndClearMood()` path used by the keyboard's 🛡️ button (delegating to `LiveCaptureSessionStore.stopSession()` and clearing any in-flight mood row).
  - Use the same row template as existing settings rows; theme-attr colours.
- `res/layout/activity_ikd_settings.xml` (modified):
  - Add the new toggle row (`ikd_settings_privacy_default_holder` / `_text` / `_check` ids matching the project's existing settings naming pattern).
- `res/values/strings.xml` (modified):
  - Add `mood_label_happiness`, `mood_label_surprise`, `mood_label_disgust`, `mood_label_sadness`, `mood_label_fear`, `mood_label_anger`, `privacy_label_on`.
  - Add `ikd_settings_privacy_default = "Privacy mode on by default"`, `ikd_settings_privacy_default_summary = "When enabled, the keyboard starts in privacy mode (no data captured). Toggle from the keyboard's shield button or here."`.
- `res/values/dimens.xml` (modified): add `mood_bar_button_size = 32dp`.
- Top-bar layout XML (modified): remove the `privacy_toggle_button` view; add the seven-button strip.

#### Acceptance

- `./gradlew assembleCoreDebug` BUILD SUCCESSFUL
- `./gradlew detekt` and `lint` no regressions
- Manual smoke (keyboard bar):
  - Privacy on (default): open keyboard → seven emoji buttons appear; 🛡️ highlighted; no row in `mood_entries`.
  - Tap 😊 → highlight moves to 😊, capture on, row written with score = 1.
  - Tap 😠 → highlight moves to 😠, row replaced with score = 6.
  - Tap 🛡️ → highlight moves to 🛡️, row deleted, in-flight session finalised (Phase 2 behaviour).
  - Re-open keyboard with privacy off (set via prior 🛡️ untoggle): no emotion highlighted; no row in `mood_entries` for the new session id.
  - Re-open keyboard with privacy on: 🛡️ highlighted, no row.
- Manual smoke (settings toggle):
  - Open `IkdSettingsActivity` → "Privacy mode on by default" row reflects current `Config.privacyModeEnabled`.
  - Toggle it OFF → return to keyboard → no emotion highlighted (privacy off, no mood yet); next keyboard open behaves the same.
  - Toggle it ON while a session was in flight → in-flight session is finalised, mood row (if any) deleted, next keyboard open shows 🛡️ highlighted.
  - Bar's 🛡️ button and the settings toggle stay in sync.
- Confirm the standalone Phase 2 privacy button is gone — there is exactly one privacy control on the top bar now (the 🛡️ slot).

#### Commit

`feat(phase8): integrate seven-button valence-ordered emotion bar and surface privacy default in settings`

---

### 8.3 Mood Overlay on Session Dashboard

**Goal:** When viewing a saved session in `EventFeedActivity` (DB-backed mode), if a `MoodEntry` exists for it, the KPI strip shows a fifth cell (emoji + label) and the metadata one-liner gains a `· Mood: 😊 Happiness` segment. Sessions without mood show four cells (existing layout).

#### Deliverables

- `helpers/IkdMoodLoader.kt` (new): mirrors `IkdSessionStatsLoader`. `suspend fun load(sessionId: String): MoodEntry?` on `Dispatchers.IO`. Pure derivation lives on `Companion` (just a passthrough but follows the established pattern for unit testing).
- `extensions/ContextExt.kt` (modified): add `val Context.ikdMoodLoader: IkdMoodLoader`.
- `activities/EventFeedActivity.kt` (modified — within the DB-backed code path; live mode untouched):
  - In `onResume`, call `ikdMoodLoader.load(sessionId)` on the same `Dispatchers.IO` hop already used for stats + chart loaders.
  - If non-null, populate the new KPI cell view (emoji + label via `MoodEmoji`); show the metadata chip segment.
  - If null, hide the KPI cell (the cell `View.GONE`, the row's `weightSum` adjusts accordingly).
- `res/layout/activity_event_feed.xml` (modified — DB-backed dashboard container only):
  - Add a fifth KPI cell (`event_feed_kpi_mood_cell`) inside the existing KPI strip `LinearLayout`. Layout weights kept at `1` per cell so the cell collapses cleanly when hidden (Phase 5 pattern).
  - Add a `mood_chip_text` `MyTextView` to the metadata one-liner row, shown via `View.VISIBLE` when mood is present.
- `res/values/strings.xml` (modified): add `session_mood_kpi_label = "Mood"`, `session_mood_chip_format = "Mood: %1$s %2$s"`.

#### Acceptance

- `./gradlew assembleCoreDebug` BUILD SUCCESSFUL; tests pass
- Open a saved session that the user explicitly tagged 😊 → fifth KPI cell renders `😊 / Happiness`; metadata one-liner shows `· Mood: 😊 Happiness`.
- Open a saved session that the user explicitly tagged 😠 → fifth KPI cell renders `😠 / Anger`.
- Open a saved session that has no mood entry (privacy was on, or the user did not tap an emotion) → KPI strip shows four cells (no fifth cell, no gap); metadata one-liner does not include the mood segment.
- Theme tinting unchanged (the new KPI cell uses the same `bigger_text_size` + `smaller_text_size` mirrors Phase 5's existing cells).

#### Commit

`feat(phase8): show emotion KPI cell and metadata chip on session dashboard`

---

### 8.4 Mood Trend Chart, Distribution Panel, and Avg Mood KPI on Global Dashboard

**Goal:** `DashboardActivity` adds three things, all gated on at least one mood entry existing in the selected range:
1. A fourth `IkdLineChartView` card "Mood over Time" rendering daily/weekly avg mood scores. Y-axis labelled "Mood (1=Happiness → 6=Anger)" (lower is better). Null buckets render as line breaks.
2. A "Mood Distribution" panel below the four line-chart cards: six rows, one per emotion (in display order — happiness, surprise, disgust, sadness, fear, anger), each with emoji + label + horizontal progress bar (filled proportional to that emotion's count vs. the range total) + count.
3. A fifth KPI cell "Avg Mood" showing the rounded-to-nearest-emotion label plus the precise number (e.g. `🤢 3.2`).

All three adapt to Week / Month / All Time toggles. When `snapshot.total == 0`, all three are hidden.

#### Deliverables

- `helpers/IkdMoodAggregator.kt` (new): `suspend fun snapshot(range: Range): MoodSnapshot` on `Dispatchers.IO`. Pure derivation on `Companion.buildSnapshot(buckets, distribution)`. Reuses the same `Range` enum as `IkdAggregator` (Week / Month / All Time) and the same bucket-format selection (`%Y-%m-%d` for Week/Month, `%Y-%W` for All Time). `MoodSnapshot` data class:
  - `buckets: List<MoodBucket>` (one per time bucket; `avgScore: Double?` nullable for empty buckets)
  - `counts: Map<Int /* score 1..6 */, Int /* count */>` (always six entries; missing scores default to 0)
  - `total: Int` (sum of all counts)
  - `averageScore: Double?` (overall average across the range; `null` when `total == 0`)
- `extensions/ContextExt.kt` (modified): add `val Context.ikdMoodAggregator: IkdMoodAggregator`.
- `activities/DashboardActivity.kt` (modified):
  - In `onResume`, call `ikdMoodAggregator.snapshot(currentRange)` alongside the existing `ikdAggregator.snapshot(...)` on the same `Dispatchers.IO` hop.
  - When `snapshot.total > 0`:
    - Show the Mood-over-Time chart card; call `IkdLineChartView.setData(labels, values, "Mood (1=Happiness → 6=Anger)")` where `values` is the `avgScore` list (with `null` for empty buckets, propagated as line breaks).
    - Show the Mood Distribution panel; render six rows in display order with progress-bar `progress = count * 100 / total`.
    - Show the Avg Mood KPI cell with `MoodEmoji.emojiFor(round(averageScore))` + the rounded label + the precise number.
  - When `snapshot.total == 0`: hide the chart card (`View.GONE`), the distribution card, and the KPI cell.
- `res/layout/activity_dashboard.xml` (modified):
  - Add a fourth `IkdLineChartView` card (`dashboard_mood_chart_card`) below the existing three line-chart cards (same `card_corner_radius` / `card_elevation`).
  - Add a `MaterialCardView` (`dashboard_mood_distribution_card`) below the line-chart cards. Inside: a vertical `LinearLayout` with six horizontal `LinearLayout` rows (`dashboard_mood_row_happiness` through `dashboard_mood_row_anger`), each containing emoji `MyTextView` + label `MyTextView` + horizontal `ProgressBar` + count `MyTextView`.
  - Add a fifth KPI cell to the existing strip (`dashboard_kpi_avg_mood_cell`); same `weightSum` adjustment pattern as Phase 5.
- `res/values/strings.xml` (modified): add `dashboard_chart_mood_title = "Mood over Time"`, `dashboard_chart_mood_y_label = "Mood (1=Happiness → 6=Anger)"`, `dashboard_mood_distribution_title = "Mood Distribution"`, `dashboard_kpi_avg_mood_label = "Avg Mood"`.
- `res/drawable/progress_horizontal_mood.xml` (new, optional): a theme-tinted horizontal progress drawable. If the default is acceptable, skip this file.
- `app/src/test/.../IkdMoodAggregatorTest.kt` (new): JVM-only unit tests for `Companion.buildSnapshot(...)`. Cover: empty inputs → `total = 0`, `averageScore = null`, `counts` has six zeros, `buckets` empty; mixed week with three days at scores 1/3/6 → averageScore = 3.33, total = 3, top emotion = whichever has the highest count (ties broken by lowest score id); null buckets propagate to the chart line breaks; out-of-range scores (e.g. score = 7) ignored.
- `CLAUDE.md` (modified): add a Phase 8 section in the same shape as Phase 5/6/7 — scope, files, decisions, what's preserved, perf budget, forbidden list. Note the Ekman 6 + valence ordering taxonomy and why no Neutral / no auto-write.
- `roadmap/FeatureRoadmap.md` (modified): flip Phase 8 status from `Planned` to `Implemented`; add a `Detailed scope:` link to `roadmap/Phase8/Phase8_Plan.md`.

#### Acceptance

- `./gradlew assembleCoreDebug` BUILD SUCCESSFUL
- `./gradlew testCoreDebugUnitTest` passes (new fixtures + all existing)
- `./gradlew detekt` and `lint` no regressions vs `main` baseline
- Open `DashboardActivity` with a populated DB and at least one mood entry → Mood-over-Time chart renders with avg-score line, Distribution panel renders six rows, Avg Mood KPI shows the rounded emoji + number. Switch Week / Month / All Time → all three re-aggregate.
- Open `DashboardActivity` with a populated DB but no mood entries (e.g. privacy was on throughout the range, or the user did not tap an emotion) → the chart card, distribution card, and Avg Mood KPI are all hidden; the KPI strip shows four cells.

#### Commit

(Split as needed for clarity — the `feat:` commit may be sizable enough to warrant a separate `test(phase8):` and `docs(phase8):`.)

```
feat(phase8): add mood trend chart, distribution panel, and avg mood KPI to global dashboard
test(phase8): cover MoodAggregator buildSnapshot edge cases
docs(phase8): document Phase 8 in CLAUDE.md and FeatureRoadmap
```

---

## 6. Downstream Impact (Dashboards, CSV, Privacy)

### Per-session screen (`EventFeedActivity` with `EXTRA_SESSION_ID`)

- Fifth KPI cell appears when the session has a mood entry; gracefully collapses to four cells otherwise.
- Metadata one-liner gains a `Mood: 😊 Happiness` segment when present.
- Three line charts (timing / gyro / accel) and the existing chip row are unchanged.
- Live mode (`EventFeedActivity` without `EXTRA_SESSION_ID`) is unchanged — no mood UI on the live event log (it's a real-time feed and mood is a session-scoped annotation, not an event).

### Global dashboard (`DashboardActivity`)

- New "Mood over Time" line-chart card (fourth) below the existing three.
- New "Mood Distribution" panel below the four chart cards. Six rows in display order — Happiness → Surprise → Disgust → Sadness → Fear → Anger.
- Fifth KPI cell "Avg Mood" appears when the selected range has at least one mood entry; otherwise hidden.
- Existing three line charts are unchanged in shape and value. Mood is a sibling dimension.
- Range toggle (Week / Month / All Time) re-aggregates the three IKD charts plus the Mood-over-Time chart and the Distribution panel.

### Diagnostics screen (`DiagnosticsActivity`)

- **Unchanged.** Phase 6 surface stays frozen. Mood is reachable via `View Log` → session dashboard. Reopening diagnostics for mood is explicitly deferred (Section 10).

### Settings screen (`IkdSettingsActivity`)

- One new row: "Privacy mode on by default" — backed by the existing Phase 2 `Config.privacyModeEnabled` flag. Toggling it has the same effect as tapping the keyboard's 🛡️ button (including finalising any in-flight session). No new flag or constant added in this phase.

### CSV export

- Single-session export and bulk export both gain a third dual-block segment (`#mood_entries\nsession_id,timestamp_ms,mood_score`).
- `mood_score` is the stored ordinal valence (1=Happiness, 2=Surprise, 3=Disgust, 4=Sadness, 5=Fear, 6=Anger); mapping to emotion name is the reader's job (or the score → emotion table from §1 / §4).
- Parsers reading only the first two blocks remain backwards-compatible.
- Every mood row in the CSV has a `session_id` (mutual exclusion guarantees no `NULL` rows from the keyboard).

### Privacy invariants (preserved)

- `MoodEntry` stores only the integer ordinal valence (1–6) and a timestamp. No text. No emoji codepoint.
- Score → emoji mapping lives in the UI layer only (`helpers/MoodEmoji.kt`).
- Privacy toggle (now via 🛡️ slot or settings row) preserves Phase 2 semantics verbatim — the in-flight session is finalised when privacy flips on, no further events are recorded, and any existing `MoodEntry` for that session is deleted.
- No auto-write: a session has a `MoodEntry` row only if the user explicitly tapped an emotion. Privacy-on sessions never produce mood rows.
- Retention worker continues to honour `Config.retentionDays`; CASCADE drops mood entries when their session is dropped. **No edit to `IkdRetentionWorker.kt`** — sessionless mood entries are not produced by Phase 8 (mutual exclusion), so the worker has nothing extra to clean up. The retention worker stays fully on the forbidden list.

### Forbidden during Phase 8 (added beyond Phase 7's freeze)

All of Phase 7's forbidden list, with three surgical exceptions noted in Section 2: `databases/IkdDatabase.kt` (schema bump), `views/MyKeyboardView.kt` (emotion bar replaces privacy-toggle button), and `activities/IkdSettingsActivity.kt` + its layout (one new row surfacing the existing privacy flag). All other previously-frozen surfaces stay frozen — including `helpers/IkdRetentionWorker.kt`, `activities/DiagnosticsActivity.kt` and its layout / menu, the Phase 6 status-chip colours in `res/values{,-night}/colors.xml`, `views/IkdLineChartView.kt` (used as-is via the existing API), and `helpers/Constants.kt` / `helpers/Config.kt` (no new pref keys this phase).

---

## 7. Files to Create / Modify (vs. forbidden)

### Create (10 files)

| File | Sub-phase |
|---|---|
| `models/MoodEntry.kt` | 8.1 |
| `interfaces/MoodDao.kt` | 8.1 |
| `interfaces/MoodDistributionRow.kt` | 8.1 |
| `interfaces/MoodBucketRow.kt` | 8.1 |
| `helpers/MoodEmoji.kt` | 8.2 |
| `helpers/IkdMoodBarController.kt` | 8.2 |
| `helpers/IkdMoodLoader.kt` | 8.3 |
| `helpers/IkdMoodAggregator.kt` | 8.4 |
| `app/src/androidTest/.../IkdDatabaseMigrationTest.kt` | 8.1 |
| `app/src/test/.../IkdMoodAggregatorTest.kt` | 8.4 |

### Modify (additive only, except for the standalone privacy-toggle removal in 8.2)

| File | Sub-phase | Change |
|---|---|---|
| `databases/IkdDatabase.kt` | 8.1 | Schema bump 1 → 2; register `MoodEntry`, `MoodDao`, `MIGRATION_1_2` |
| `extensions/ContextExt.kt` | 8.1 / 8.3 / 8.4 | Add `ikdMoodLoader`, `ikdMoodAggregator`, `ikdMoodBarController`, possibly `moodDB` if pattern fits |
| `views/MyKeyboardView.kt` | 8.2 | Add the seven-button emotion bar; **remove** the Phase 2 `privacy_toggle_button` view + handler (semantics move to 🛡️ slot) |
| Top-bar layout XML referenced by `MyKeyboardView` | 8.2 | Remove `privacy_toggle_button` view; add the seven-button strip |
| `activities/IkdSettingsActivity.kt` | 8.2 | Add "Privacy mode on by default" toggle row backed by existing `Config.privacyModeEnabled` |
| `res/layout/activity_ikd_settings.xml` | 8.2 | Add the new toggle row |
| `activities/EventFeedActivity.kt` | 8.3 | Wire `ikdMoodLoader` on the existing `Dispatchers.IO` hop; populate fifth KPI cell + metadata chip |
| `res/layout/activity_event_feed.xml` | 8.3 | Add fifth KPI cell + metadata `mood_chip_text` |
| `activities/DashboardActivity.kt` | 8.4 | Wire `ikdMoodAggregator`; populate Mood-over-Time chart, Distribution panel, Avg Mood KPI cell |
| `res/layout/activity_dashboard.xml` | 8.4 | Add fourth `IkdLineChartView` card + `MaterialCardView` for Distribution panel (6 rows) + fifth KPI cell |
| `res/values/strings.xml` | 8.2 / 8.3 / 8.4 | Add emotion + privacy-label strings + dashboard chart strings + settings row strings |
| `res/values/dimens.xml` | 8.2 | Add `mood_bar_button_size` |
| `helpers/IkdCsvWriter.kt` | 8.1 / 8.2 | Append the third dual-block segment for mood entries |
| `CLAUDE.md` | 8.4 | Add Phase 8 section (parallel to Phase 5/6/7) |
| `roadmap/FeatureRoadmap.md` | 8.4 | Status → Implemented; link to this plan |

### Forbidden (do not touch — see Section 2 for full list)

`SimpleKeyboardIME.kt`, `LiveCaptureSessionStore.kt`, `KinematicSensorHelper.kt`, `IkdRetentionWorker.kt`, all `@Entity` data classes from earlier phases, `ClipsDatabase.kt`, `ClipsDao.kt`, `IkdAggregator.kt`, `IkdSessionStatsLoader.kt`, `IkdSessionChartLoader.kt`, `views/IkdLineChartView.kt` (used as-is via the existing API), `interfaces/SessionDao.kt` / `IkdEventDao.kt` / `SensorSampleDao.kt`, `interfaces/EventBucketRow.kt` / `SessionBucketRow.kt` / `TimingBucketRow.kt` / `SensorBucketRow.kt` / `SessionStatsRow.kt`. Phase 6 surfaces (`activities/DiagnosticsActivity.kt`, `res/layout/activity_diagnostics.xml`, `res/menu/menu_diagnostics.xml`, `res/values{,-night}/colors.xml` for status chips) — also frozen. `helpers/Constants.kt` and `helpers/Config.kt` — no new pref keys this phase (the settings row reuses existing `Config.privacyModeEnabled`).

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
- [ ] Seven-button emotion bar appears in the keyboard top bar; 🛡️ highlighted by default (privacy on); standalone Phase 2 `privacy_toggle_button` is removed.
- [ ] Tapping 🛡️ enables privacy mode, finalises the in-flight session (Phase 2 behaviour preserved), and deletes any existing mood row for that session.
- [ ] Tapping any of the six emotion buttons disables privacy mode and writes/replaces a mood row keyed on the active session id with the correct ordinal valence id (😊=1, 😲=2, 🤢=3, 😢=4, 😨=5, 😠=6).
- [ ] Opening the keyboard with privacy off produces NO `mood_entries` row until the user explicitly taps an emotion (no auto-write invariant).
- [ ] No `mood_entries` row exists with `session_id = NULL` after exercising the keyboard (mutual-exclusion invariant).
- [ ] `IkdSettingsActivity` shows a "Privacy mode on by default" row that reflects and updates the existing `Config.privacyModeEnabled` flag in real time; the keyboard's 🛡️ button and the settings row stay in sync.
- [ ] Per-session dashboard shows the fifth KPI cell + metadata chip when a `MoodEntry` exists; gracefully collapses otherwise.
- [ ] Global dashboard shows the Mood-over-Time chart (avg score line), the Mood Distribution panel (six rows), and the Avg Mood KPI cell when at least one mood entry exists in the selected range; all three are hidden otherwise.
- [ ] CSV export produces a third `#mood_entries` block with rows holding ordinal `mood_score` integers (1–6); bulk export does likewise. Existing two blocks unchanged.
- [ ] `MoodEntry` rows store only `(id, session_id, timestamp, mood_score)` — no text, no emoji codepoint.
- [ ] `roadmap/Phase8/Phase8_Plan.md` exists and cites Ekman in Section 11.
- [ ] `CLAUDE.md` has a Phase 8 section that records the Ekman 6 schema choice and points to Section 11 of this plan for the source.
- [ ] `roadmap/FeatureRoadmap.md` Phase 8 status is updated to `Implemented`.

---

## 9. Decisions

| # | Topic | Decision |
|---|---|---|
| 1 | Branch strategy | All work on `feat/phase8-mood-bar`; left local for the user's review — **no push, no PR opened by the implementer**. Same pattern as Phase 6 / Phase 7. |
| 2 | Schema migration vs. additive column on `sessions` | **New `mood_entries` table.** A column on `sessions` would conflate "the user typed in this session" with "the user rated this session" — the row exists from the moment the keyboard opens, before the user taps anything. A separate table is also future-proof for any sessionless writer that may exist down the line. |
| 3 | One-mood-per-session vs. mood-as-time-series | **One mood per session** (enforced via partial unique index). The bar is for quick annotation, not a time-series log; allowing multiple would invite "what does mood mean over the session?" interpretation questions that we don't want to answer in v1. Re-tap simply replaces. |
| 4 | Emotion taxonomy | **Ekman 6, valence-ordered (1=Happiness → 6=Anger).** The six Ekman emotions [1, 2] are research-grounded; the best-to-worst ordering is a deliberate Phase 8 product addition for UX and for enabling meaningful averages. Rejected alternatives: Plutchik 8 (adds awkward "trust" / "anticipation" categories), Geneva Emotion Wheel 20 (too many for a keyboard bar), Cowen-Keltner 27 (assumes a tiered selection UI), Apple Health hybrid valence + label (two-tier interaction we don't have), pure 5-point valence (conflates anger with sadness), Ekman 6 + Neutral hybrid (Decision #11 — Neutral is redundant with no-auto-write). Sources in Section 11. |
| 5 | Score → emoji mapping location | **UI layer only** (`helpers/MoodEmoji.kt`). The DB is privacy-clean. |
| 6 | Mood while privacy mode is on | **Not possible by construction.** The 🛡️ and emotion slots are mutually exclusive on the bar — tapping an emotion implicitly disables privacy mode; opening the keyboard with privacy on simply does not write a row. The schema permits `session_id = NULL` rows but the keyboard never produces them. |
| 7 | Bar default highlighted state | **🛡️ when `Config.privacyModeEnabled == true`; nothing highlighted when `false` and no `MoodEntry` exists for the active session.** The 🛡️-by-default branch matches today's Phase 2 "privacy on by default" — preserves user expectation. The "nothing highlighted" branch reflects the no-auto-write rule honestly: the bar shows what's stored, and an unrecorded session has no emotion stored. |
| 8 | Bar position in keyboard | **Top bar, leading-aligned, replaces the standalone privacy-toggle button.** One control surface for "data is/isn't flowing + what's the mood" — fewer top-bar widgets, no duplication. |
| 9 | Settings toggle for the bar itself | **Reversed in Phase 8.1 polish — see Section 12.** Originally **None** (rationale: the bar houses the privacy toggle, so gating it behind a setting would hide the privacy control). The follow-up landed `Config.showMoodBar` (default `true`) plus an `IkdSettingsActivity` row, because users who want to suppress the emoji bar still have a privacy affordance via the same settings screen. |
| 10 | Auto-write a default mood on session start | **No.** Auto-writing any default emotion on session start would inflate that emotion's count in the Distribution panel and pull the Avg Mood KPI in its direction with passive (non-chosen) entries. An unrecorded session is an honest "user didn't feel a strong enough emotion to label" — that absence is itself signal. |
| 11 | Neutral / no-rating slot | **Removed.** Earlier iterations of this plan included a Neutral category (or a 🤷 "no rating" slot). Both are made redundant by the no-auto-write rule (Decision #10) — sessions without a `MoodEntry` row already mean "user did not pick an emotion." Adding Neutral back would either need auto-writing (Decision #10 says no) or duplicate the same "no row" semantic with a row, which is worse for the dashboard aggregations. |
| 12 | Score ordering: arbitrary id vs. ordinal valence | **Ordinal valence (1=best → 6=worst).** Lets us compute meaningful averages (Avg Mood KPI, Mood-over-Time line chart) using existing SQLite `AVG` and the existing `IkdLineChartView` wrapper. The cost is that the ordering is a product-defined valence ranking, not a research-derived axis — explicitly noted in Section 1 and Section 11. |
| 13 | Avg Mood KPI display | **Rounded emoji + label + precise number** (e.g. `🤢 3.2`). The categorical anchor is the most intuitive read; the precise number rewards users who want gradient detail. |
| 14 | Global dashboard chart shape | **Both** Mood-over-Time line chart (avg per bucket) and Mood Distribution panel (counts per category). The line chart shows trend, the panel shows shape — they're complementary. |
| 15 | Mood in CSV | **Third dual-block segment.** Strictly additive; existing parsers stay backwards-compatible. `mood_score` is the ordinal valence (1–6); consumers map it via the §1 table. |
| 16 | Score → emotion wording (locale) | **English only in v1.** The six emoji are universal; the labels follow the keyboard's existing string-translation flow. Custom emotion taxonomies for non-English speakers are a Phase 10 (rebrand) concern. |
| 17 | Color / style of selected button | **Theme attribute (`?attr/colorAccent`)**, not hardcoded. Matches Phase 6's status-chip discipline. |
| 18 | Capture path edits | **None.** `SimpleKeyboardIME.kt` and `LiveCaptureSessionStore.kt` are not edited. The mood bar reads `LiveCaptureSessionStore.currentSessionId` and writes via `MoodDao` directly on a background thread. The 🛡️ slot delegates to the same Phase 2 path that finalises the in-flight session. |
| 19 | Retention worker edit scope | **None — `IkdRetentionWorker.kt` stays frozen.** Sessioned mood entries are dropped by CASCADE; sessionless ones don't exist (Decision #6). The worker has nothing extra to clean up. |
| 20 | Migration test surface | **Instrumented (`androidTest`)** because Room migration tests require the actual SQLite implementation. Everything else stays JVM-only. |
| 21 | DAO query coverage shipped this phase | **Both `getMoodBuckets` (avg + count per bucket) and `getMoodDistribution` (count per category) are consumed in 8.4.** A future stacked-bar-by-day view would need a third query that splits per category per bucket — deferred to Phase 9. |
| 22 | Per-session live-mode mood | **Not surfaced.** Live mode is the real-time event log; mood is a session-level annotation. The two don't compose. |
| 23 | Privacy + emotion integration shape | **Seven-button mutually-exclusive bar (`🛡️ 😊 😲 🤢 😢 😨 😠`)** replacing the Phase 2 standalone shield. Six emotions (pure Ekman 6) ordered best-to-worst by valence. |
| 24 | Intensity / strength of emotion | **Not modelled.** Ekman 6 has no intensity dimension (Plutchik adds it as a separate axis [4]). Adding intensity would expand the schema (`intensity INT`) and force a tap-cycling interaction; out of scope for v1. |
| 25 | Settings row backing flag (separate `privacyOnByDefault` vs. existing `privacyModeEnabled`) | **Reuse the existing `Config.privacyModeEnabled` flag.** The settings row "Privacy mode on by default" and the keyboard bar's 🛡️ button are two UI affordances over the same persisted boolean. No new constant or property is added in this phase, which keeps `helpers/Constants.kt` and `helpers/Config.kt` on the forbidden list. The "by default" wording reflects that the flag persists across keyboard opens (it's the user's *default* state until they change it again). A future phase could introduce a separate "preference vs. current state" split if needed; out of scope here. |

---

## 10. Explicitly Deferred to Later Phases

These were on the mood-bar wishlist but are out of scope for Phase 8:

- **Stacked-bar-by-day chart on global dashboard** — a richer rendering that splits each daily bucket into per-category sub-bars. Needs a third DAO query (per-bucket × per-category counts); deferred to Phase 9.
- **Mood-correlated metrics** ("your typing speed when angry vs. happy") — Phase 9. Needs a JOIN of `ikd_events` aggregations against the `mood_entries` filter.
- **Custom emotion taxonomies / wording / locales** — Phase 10 (rebrand) concern.
- **Free-text journal entries** — privacy invariant violation; needs a separate design pass.
- **Multi-mood per session as time-series** — explicit decision against (Decision #3).
- **Mood reminders / nudges** — yagni.
- **Live-mode mood overlay** — Decision #22.
- **Mood in `DiagnosticsActivity`** — Phase 6 surface is frozen; the diagnostics screen stays IKD-only.
- **Sessionless `MoodEntry` rows from any UI surface** — schema permits it, Phase 8 doesn't produce it (Decision #6). A future journaling surface could write them; the retention worker would then need a sibling deletion query.
- **`MoodEntry` exposed via the bulk JSON export** (if/when JSON export is added) — out of scope until JSON export ships.
- **Anomaly markers on the mood charts** — already deferred from Phase 3.
- ~~**Settings toggle to hide the emotion bar**~~ — *no longer deferred:* shipped in the Phase 8.1 UI polish (Section 12). Backed by the new `Config.showMoodBar` flag. Decision #9 documents the reversal.
- **Auto-write a default mood on session start** — Decision #10. Would corrupt the dashboard aggregations.
- **Neutral / no-rating slot** — Decision #11. Redundant with the no-auto-write rule.
- **Intensity / strength dimension** (annoyance → anger → rage) — Decision #24. Plutchik adds this; this phase does not. Out of scope.
- **Plutchik 8 / Geneva 20 / Cowen-Keltner 27 / Apple-style hybrid taxonomies** — researched and rejected for the keyboard top bar (Decision #4 + Section 11 references). Any future expansion to a richer taxonomy earns its own mini-plan.
- **Valence × arousal axes (Russell's circumplex)** — not modelled. The Phase 8 ordering is single-axis valence only. Adding arousal would require a second column and a 2D selection UI.
- **Separate `Config.privacyOnByDefault` flag** — Decision #25. The settings row reuses the existing `Config.privacyModeEnabled`; a future phase could introduce a "preference vs. current state" split if research shows users want it.

Anything from this list earns its own focused mini-plan in `roadmap/Phase{N}/` if and when the user wants it.

---

## 11. References

The Phase 8 emotion taxonomy is research-grounded with one explicit product deviation: the six emotion **categories** are sourced from Ekman; the **best-to-worst ordinal scoring** is a Phase 8 design call made for UX and dashboard purposes. This section makes the split explicit so future readers can trace what's research-backed and what's a deliberate Phase 8 choice.

### What's sourced from Ekman

The six categories — anger, disgust, fear, happiness, sadness, surprise — are taken from:

1. **Ekman, P. (1972).** "Universals and cultural differences in facial expressions of emotion." In J. Cole (Ed.), *Nebraska Symposium on Motivation*, Vol. 19, pp. 207–283. Lincoln, NE: University of Nebraska Press. — The original cross-cultural study identifying six universal emotions.
2. **Ekman, P. (1992).** "An argument for basic emotions." *Cognition & Emotion*, 6(3–4), 169–200. doi:10.1080/02699939208411068. — Ekman's consolidated theoretical defence of the basic-emotions framework, most-cited reference for the canonical list.
3. **Ekman, P., & Friesen, W. V. (1971).** "Constants across cultures in the face and emotion." *Journal of Personality and Social Psychology*, 17(2), 124–129. — The earlier empirical foundation; foundational input to FACS.

### What's a Phase 8 product choice (not from Ekman)

- **Imposing a best-to-worst ordering on the score id** (1=Happiness → 6=Anger) — Ekman treats his six categories as discrete and unordered. The Phase 8 ordering is a UX convention for enabling `AVG(mood_score)` to be meaningful, not a claim about psychological reality.
- **No Neutral category** (despite earlier Phase 8 iterations exploring it) — Ekman's framework excludes neutral on purpose, and the no-auto-write rule already serves the "user didn't pick" case via row absence. Decisions #10 and #11.

### Schemas considered and rejected in Decision #4

(Linked for traceability — readers don't need to consume them to understand Phase 8.)

4. **Plutchik, R. (1980).** "A general psychoevolutionary theory of emotion." In R. Plutchik & H. Kellerman (Eds.), *Emotion: Theory, Research, and Experience*, Vol. 1, pp. 3–33. New York: Academic Press. — Eight primary emotions on a wheel with intensity layers.
5. **Scherer, K. R. (2005).** "What are emotions? And how can they be measured?" *Social Science Information*, 44(4), 695–729. — Foundation for the Geneva Emotion Wheel (GEW) research instrument; 20 emotions on valence × control axes. See also Sacharin, Schlegel, & Scherer (2012), Geneva Emotion Wheel Rating Study, University of Geneva. <https://www.unige.ch/cisa/gew/>
6. **Cowen, A. S., & Keltner, D. (2017).** "Self-report captures 27 distinct categories of emotion bridged by continuous gradients." *Proceedings of the National Academy of Sciences*, 114(38), E7900–E7909. doi:10.1073/pnas.1702247114. <https://www.pnas.org/doi/10.1073/pnas.1702247114>
7. **Apple Inc.** "Log your state of mind in Health on iPhone." Apple Support guide. <https://support.apple.com/guide/iphone/log-your-state-of-mind-iph6a6decb13/ios>. — Hybrid valence-slider + label-picker product UI, iOS 17+.
8. **Watson, D., Clark, L. A., & Tellegen, A. (1988).** "Development and validation of brief measures of positive and negative affect: The PANAS scales." *Journal of Personality and Social Psychology*, 54(6), 1063–1070. — Two-factor research instrument (positive affect, negative affect).

### Public encyclopaedic overview consulted during taxonomy comparison

9. **Wikipedia.** "Emotion classification." <https://en.wikipedia.org/wiki/Emotion_classification>. — Comparative overview of basic-emotions taxonomies.

---

## 12. Post-merge UI Polish (Phase 8.1)

After the original four sub-phases shipped on `feat/phase8-mood-bar`, on-device review surfaced four UI issues. Fixes landed on `fix/phase8-ui-polish` and were merged into `main` without reopening the schema, the capture path, or any frozen surface beyond what Phase 8 already reopened. This section is the change log for that follow-up so future readers can trace which decisions were revised.

### What changed

| # | Issue | Fix |
|---|---|---|
| 1 | Mood bar buttons rendered tiny inside their 32 dp boxes; the bar hugged the clipboard-clear icon. | `mood_bar_button_size` 32 dp → 36 dp; `mood_bar_button_text_size` 18 sp → 22 sp; bar gains `layout_marginStart="@dimen/medium_margin"` and inner `gravity="center"` so emoji glyphs fill their boxes. |
| 2 | The new `MaterialCardView` cards on `DashboardActivity` (Mood-over-Time and Mood Distribution) rendered as un-themed white slabs on dark themes — their default `?attr/colorSurface` doesn't track Fossify's runtime background colour. | New `applyCardThemeColors()` in `DashboardActivity` calls `setCardBackgroundColor(getProperBackgroundColor())` on both cards on every `onResume`. Same pattern Phase 5 already uses in `EventFeedActivity.applyThemeColors`. |
| 3 | No way to hide the emotion bar — Decision #9 had explicitly omitted this. Users who don't want emoji on their keyboard had no escape hatch. | Added `Config.showMoodBar` (default `true`) and the `SHOW_MOOD_BAR` constant; new "Show mood bar in keyboard" row in `IkdSettingsActivity`; `MyKeyboardView.applyMoodBarVisibility()` gates the bar's `View.VISIBLE` / `View.GONE` on every refresh. Privacy stays reachable via the existing "Privacy mode on by default" row when the bar is hidden. **This reverses Decision #9.** |
| 4 | The session dashboard (`EventFeedActivity` DB-backed mode) showed the mood twice: once in the fifth KPI cell and once again as a standalone "Mood: 😊 Happiness" line below the metadata. | Removed the standalone `mood_chip_text` view from `activity_event_feed.xml` and the matching `binding.moodChipText` plumbing in `applyMoodOverlay`. The fifth KPI cell remains as the single mood surface. The `session_mood_chip_format` string in `strings.xml` is retained but unused — harmless. |

### Files touched (Phase 8.1, in addition to the Phase 8 set)

| File | Change |
|---|---|
| `app/src/main/res/values/dimens.xml` | Bumped `mood_bar_button_size` and `mood_bar_button_text_size`. |
| `app/src/main/res/layout/keyboard_view_keyboard.xml` | Added margin and centred inner gravity on the mood bar `LinearLayout`. |
| `app/src/main/res/layout/activity_event_feed.xml` | Deleted the `mood_chip_text` view. |
| `app/src/main/res/layout/activity_ikd_settings.xml` | Added the "Show mood bar in keyboard" toggle row. |
| `app/src/main/res/values/strings.xml` | Added `ikd_settings_show_mood_bar` and its summary. |
| `app/src/main/kotlin/.../helpers/Constants.kt` | Added `SHOW_MOOD_BAR` pref key. |
| `app/src/main/kotlin/.../helpers/Config.kt` | Added `var showMoodBar: Boolean`. |
| `app/src/main/kotlin/.../activities/IkdSettingsActivity.kt` | Wired the new toggle. |
| `app/src/main/kotlin/.../activities/DashboardActivity.kt` | Added `applyCardThemeColors()`. |
| `app/src/main/kotlin/.../activities/EventFeedActivity.kt` | Removed the mood-chip plumbing from `applyMoodOverlay`. |
| `app/src/main/kotlin/.../views/MyKeyboardView.kt` | Added `applyMoodBarVisibility()`, called from `refreshMoodBarFromState`. |

### Decision deltas

- **Decision #9** — reversed: there is now a setting to hide the emotion bar (Issue #3 above).
- **Decision #11** — unchanged: still no Neutral category, still no auto-write.
- **Decision #25** — unchanged: the new `Config.showMoodBar` is a **separate** new flag from the existing `Config.privacyModeEnabled`. The "Privacy mode on by default" row continues to be the single surface for the privacy flag.
- The polish opens **`helpers/Constants.kt` and `helpers/Config.kt`** — both were on the Section 2 forbidden list during the original Phase 8. The Phase 8.1 reversal of Decision #9 made the new pref key unavoidable; the edit is strictly additive (one new constant, one new property).

### Gates re-run after the polish merge

- `./gradlew assembleCoreDebug` — BUILD SUCCESSFUL.
- `./gradlew testCoreDebugUnitTest` — BUILD SUCCESSFUL (no test changes; existing 38 unit tests still pass).
