# Phase 14 — Gamification: Badges (Mood + Keyboard)

**Status:** Planned
**Depends on:** Phase 8 (mood entries in `ikd.db`), Phase 9 (Insights dashboard tab structure), Phase 9.15 (current 5-tab layout: Summary · Trends · Daily Activity · Keystroke Dynamics · Habits)
**Branch:** Implementation lands directly on `main` (small focused commits per logical change), per recent project hygiene.
**Scope (one sentence):** Add a starter set of **~13 badges** that reward consistent mood cataloging and keyboard usage, surfaced as a new sixth **"Achievements"** tab in the Insights dashboard, with a brief in-app snackbar when a new badge unlocks.

> **Phase numbering note.** This badge plan was originally drafted as Phase 13. The slot was re-used by the **"Persistent Right-Anchored Mood Bar"** feature (implemented, landed on `main` — see [`Phase13_Plan.md`](../Phase13/Phase13_Plan.md)) before this badge work was started, so the gamification feature is renumbered to Phase 14 to preserve continuity with what shipped. The technical content of this plan is unchanged from the original Phase 13 draft.

---

## 1. Why this change

The roadmap up to Phase 12 builds a complete passive-data + insights loop, but it doesn't *reward* the user for keeping that loop alive. Two of MoodScript's core pillars — **routine management** and **mood cataloging** — only pay off when the user actually engages over time. Lightweight gamification is the standard, low-friction way to bridge that engagement gap:

- A user who logs mood 7 days in a row gets a small visual confirmation that the routine is forming.
- A user who reaches 10,000 keystrokes sees that the keyboard is working (and is being measured) silently in the background.
- Because the badges live inside Insights — already the user's regular touchpoint with the captured data — they don't introduce a new attention channel.

This phase deliberately keeps the gamification layer *additive and reversible*: badges are derived from existing tables (no new captured data, no privacy posture change), live behind their own tab so users who don't care can ignore them, and the unlock signal is an in-app snackbar (no system notifications, no permissions).

---

## 2. Branch & Layering Discipline

Read-side feature with one schema bump. The capture path stays frozen.

### Reopened files

| File | Why reopened | Edit shape |
|---|---|---|
| `databases/IkdDatabase.kt` | Schema bump 3 → 4 (add `badges` table); register `BadgeDao` | One new `Migration(3, 4)` registered on the builder; `version = 4`; new `abstract fun BadgeDao(): BadgeDao` |
| `extensions/ContextExt.kt` | Lazy singleton accessor for `IkdBadgeEvaluator`, mirroring existing `Context.ikdAggregator` etc. | One new `val Context.ikdBadgeEvaluator` |
| `activities/DashboardActivity.kt` | Run badge evaluator alongside existing aggregators on `loadDashboard`; show snackbar on newly-unlocked badge(s) | Add one new evaluator call to the existing `Dispatchers.IO` hop. On result, if `NewlyUnlocked` list is non-empty, render a `Snackbar` at the bottom of the activity with the badge title (e.g. `"🌱 First Mood unlocked!"`). Multiple unlocks chain queue snackbars (or batch into one with first title + `"+N more"`) |
| `activities/dashboard/DashboardPagerAdapter.kt` | Add 6th tab | `TAB_COUNT = 6`; new `TAB_ACHIEVEMENTS = 5`; new branch in `createFragment` returning `AchievementsFragment()` |
| `activities/DashboardActivity.kt` (tab title array, `TabLayoutMediator` setup, `STATE_TAB_INDEX` saved-instance handling) | Display the 6th tab label and icon | Add Achievements label string and icon to the existing tab arrays |
| `res/values/strings.xml` | New strings for the tab title, snackbar template, and 13 badge titles + descriptions | Additive only |
| `res/values/colors.xml` and `res/values-night/colors.xml` | Locked-state and unlocked-state badge tint tokens | Additive only — `badge_locked_tint`, `badge_unlocked_glow`, plus a faded surface for locked cards |

### New files

| File | Purpose |
|---|---|
| `models/Badge.kt` | Room entity — `@Entity(tableName = "badges")` with `id: Long`, `badgeKey: String` (`@ColumnInfo(name = "badge_key")`, unique index), `unlockedAt: Long` (`@ColumnInfo(name = "unlocked_at")`) |
| `interfaces/BadgeDao.kt` | DAO — `@Insert(onConflict = REPLACE) suspend fun upsert(...)`, `@Query("SELECT badge_key FROM badges") suspend fun getAllUnlockedKeys(): List<String>`, `@Query("SELECT * FROM badges ORDER BY unlocked_at DESC") suspend fun getAllUnlocked(): List<Badge>` |
| `helpers/IkdBadgeCatalog.kt` | Static catalog: list of `BadgeDef(key, emoji, titleRes, descRes, criteria)` for all 13 starter badges. Pure data — no Room, no I/O |
| `helpers/IkdBadgeEvaluator.kt` | `suspend fun evaluate(): EvaluationResult` on `Dispatchers.IO`. Reads existing tables (mood_entries, ikd_events, sessions), computes which badges should be unlocked, persists new unlocks via `BadgeDao.upsert(...)`. Returns `EvaluationResult(allUnlocked: List<Badge>, newlyUnlocked: List<BadgeDef>)`. Pure derivation lives on `Companion.evaluateBadges(criteria, snapshot)` for unit testing — same pattern as `IkdAggregator.Companion.buildSnapshot` |
| `activities/dashboard/AchievementsFragment.kt` | Sixth dashboard fragment. Extends `DashboardFragment` so it picks up the existing payload-host plumbing. Renders a 2-column `RecyclerView.GridLayoutManager` of badge cards (locked = grey-tinted, alpha 0.45; unlocked = full-colour with subtle glow). Card layout uses `item_badge_card.xml` |
| `adapters/BadgeAdapter.kt` | RecyclerView adapter with one viewType. Bind: emoji, title, description, locked/unlocked state, unlock date (when unlocked) |
| `res/layout/fragment_dashboard_achievements.xml` | Card section header + RecyclerView + empty-state TextView ("No badges unlocked yet — keep typing and logging mood!") |
| `res/layout/item_badge_card.xml` | One badge card: large emoji, title (`bigger_text_size`), description (`smaller_text_size`), unlock date (gone when locked). Wrapped in `MaterialCardView`. Same `card_corner_radius` / `card_elevation` from `dimens.xml` as the rest of the dashboard cards |
| `app/src/test/.../IkdBadgeEvaluatorTest.kt` | JVM unit tests for `Companion.evaluateBadges` — one fixture per criterion, plus an "all-locked" baseline fixture and an "all-unlocked" check |
| `app/src/androidTest/.../IkdDatabaseMigrationTest.kt` *(extend)* | Add `migrate_3_to_4_addsBadgesTableAndIndex` test confirming v3 data is preserved and the `badges` table + unique index land correctly |

### Still forbidden (everything earlier phases froze)

| File | Reason |
|---|---|
| All capture-path code (`SimpleKeyboardIME.kt`, `LiveCaptureSessionStore.kt`, `KinematicSensorHelper.kt`, `IkdRetentionWorker.kt`) | Frozen since Phase 7.1 |
| `models/IkdEvent.kt`, `models/SensorSample.kt`, `models/SessionRecord.kt`, `models/MoodEntry.kt` | No edits to existing entities |
| `helpers/IkdCsvWriter.kt` | CSV format frozen — badges are derived state, not captured data, and never appear in the CSV |
| All read pipelines other than the new evaluator (`IkdAggregator`, `IkdSessionStatsLoader`, `IkdSessionChartLoader`, `IkdMoodLoader`, `IkdMoodAggregator`, plus the Phase 9 sub-phase aggregators) | Frozen — the new evaluator queries the existing tables independently |
| `views/IkdLineChartView.kt`, `views/IkdStackedBarChartView.kt`, `views/IkdHeatmapView.kt`, `views/IkdBubbleMapView.kt` | Chart wrappers untouched |
| `helpers/Config.kt`, `helpers/Constants.kt` | No new prefs (all badge state lives in the DB) |
| `helpers/IkdMoodBarController.kt`, `helpers/MoodEmoji.kt`, `views/MyKeyboardView.kt` and the keyboard top-bar XML | Mood-bar UX unchanged |

### Branch hygiene

Five focused commits, in this order:

1. Schema bump: `Badge` entity + `BadgeDao` + `Migration(3, 4)` + migration test.
2. Catalog + evaluator: `IkdBadgeCatalog`, `IkdBadgeEvaluator` (incl. companion-only logic), unit tests.
3. UI: `AchievementsFragment`, `BadgeAdapter`, layouts, strings, colours.
4. Dashboard wiring: pager adapter + `DashboardActivity` evaluator hop + snackbar handling.
5. Docs: this plan, [`STATUS.md`](../STATUS.md) row, optional `CLAUDE.md` update.

No push, no PR opened by the implementer.

---

## 3. Locked decisions

| # | Decision | Value |
|---|---|---|
| 1 | Placement | **New 6th dashboard tab "Achievements"**, slotted after Habits. No changes to the existing 5 tabs. (Considered: an "X/Y badges" cell on the Habits KPI strip — rejected for v1 to keep tab content focused. May add later as a small footer link from the Habits tab to the Achievements tab) |
| 2 | Initial catalog size | **13 badges** — six mood-cataloging, seven keyboard-usage. Listed in §4. Tight enough to ship cleanly, broad enough that any active user unlocks at least one in their first week |
| 3 | Badge icons | **Emoji codepoints only** for v1. No custom vector drawables. Locked badges render in greyscale via a `ColorFilter` saturation matrix (no Photoshopped variants needed) |
| 4 | Schema | Add `badges` table via `Migration(3, 4)`. Bump `IkdDatabase.version` 3 → 4 |
| 5 | Evaluation timing | **Lazy, on dashboard open.** `IkdBadgeEvaluator.evaluate()` runs alongside existing aggregators in `DashboardActivity.loadDashboard()` on `Dispatchers.IO`. No real-time evaluation in the capture path |
| 6 | Unlock feedback | **In-app `Snackbar`** anchored to the dashboard view when one or more new badges land during this evaluation. No system notifications, no notification permission, no nav-bar banner. Multiple simultaneous unlocks: show a single snackbar with the first badge's title + `"+N more"` (taps the snackbar action → jump to Achievements tab) |
| 7 | Privacy | Unchanged. Badges are derived from existing `ikd_events`, `sessions`, and `mood_entries`. No new captured data, no new pref keys, no CSV column |
| 8 | Re-evaluation cost | The evaluator runs once per `loadDashboard()` call. Worst case is ~3-4 small SQL queries (count + min/max + DISTINCT mood count + streak query). Logs wall-clock time to Logcat (tag `IkdBadgeEvaluator`) when `BuildConfig.DEBUG` — same pattern as `IkdAggregator` |
| 9 | Tab title and icon | Tab label: "Achievements" (`R.string.dashboard_tab_achievements`). Icon: `🏆` rendered as a tab item via the existing `TabLayoutMediator` pattern, or a vector drawable equivalent (`ic_tab_achievements_vector`) for theme tinting consistency. **Implementer's choice** based on what scales cleanest at the existing tab size |

---

## 4. Initial badge catalog

Six **mood-cataloging** badges + seven **keyboard-usage** badges = 13 starter badges. Each `key` is the stable id stored in `badges.badge_key`.

### Mood Cataloging

| Key | Icon | Title | Criteria |
|---|---|---|---|
| `mood_first` | 🌱 | First Mood | `COUNT(*) FROM mood_entries >= 1` |
| `mood_streak_7` | 📅 | 7-Day Diarist | At least one mood entry on each of 7 consecutive calendar days |
| `mood_streak_30` | 🗓️ | 30-Day Reflector | At least one mood entry on each of 30 consecutive calendar days |
| `mood_full_spectrum` | 🌈 | Full Spectrum | At least one mood entry exists for each of the 6 Ekman scores (1..6) |
| `mood_centurion` | 🎯 | Centurion | `COUNT(*) FROM mood_entries >= 100` |
| `mood_returner` | 🔄 | Returner | At least one mood entry **after** a gap of 7+ days with no mood entries (resumed logging) |

### Keyboard Usage

| Key | Icon | Title | Criteria |
|---|---|---|---|
| `kb_first_session` | ⌨️ | Hello, World | `COUNT(*) FROM sessions >= 1` |
| `kb_wordsmith` | 📝 | Wordsmith | Cumulative `keystrokeCount` (events excluding `AUTOCORRECT`) >= 10,000 |
| `kb_novelist` | 📚 | Novelist | Cumulative `keystrokeCount` >= 100,000 |
| `kb_speed_demon` | 🚀 | Speed Demon | At least one session with WPM >= 60 |
| `kb_precision` | 🎯 | Precision | At least one session with `keystrokeCount >= 100` and weighted error rate < 2% |
| `kb_streak_7` | 🔥 | Week Streak | At least one session on each of 7 consecutive calendar days |
| `kb_streak_30` | 🏆 | Month Streak | At least one session on each of 30 consecutive calendar days |

> **Title duplication note.** Both `mood_centurion` and `kb_precision` use the 🎯 emoji. The collision is acceptable: badges are identified by their stable `badge_key` and full title in code; the icon is decorative. Future badges can vary the icon if a unique-icon principle is preferred.

Each badge has an English `title` and `description` string-resource pair, e.g.:

```xml
<string name="badge_mood_first_title">First Mood</string>
<string name="badge_mood_first_desc">You logged your first mood. The journey starts here.</string>
<string name="badge_kb_streak_7_title">Week Streak</string>
<string name="badge_kb_streak_7_desc">Typed at least once a day for seven days in a row.</string>
```

Locale translations are encouraged but optional for v1.

---

## 5. Schema migration

`IkdDatabase.version` bumps **3 → 4**. The migration is non-destructive — it only adds a new table. Existing data (sessions, ikd_events, sensor_samples, mood_entries) is preserved.

```kotlin
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `badges` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `badge_key` TEXT NOT NULL,
                `unlocked_at` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS `index_badges_badge_key`
            ON `badges` (`badge_key`)
            """.trimIndent()
        )
    }
}
```

The migration test in `app/src/androidTest/.../IkdDatabaseMigrationTest.kt` covers v3-data preservation and the unique index.

---

## 6. Badge evaluation strategy

`IkdBadgeEvaluator.evaluate()` is one `Dispatchers.IO` hop with at most a handful of small SQL queries:

1. **Snapshot existing-state queries** (executed in parallel where possible):
   - `BadgeDao.getAllUnlockedKeys()` — current unlocked set.
   - `MoodDao.getMoodCount()` (additive query) — total mood entries.
   - `MoodDao.getDistinctMoodScoreCount()` (additive) — number of distinct mood scores logged.
   - `MoodDao.getMoodCalendarDays()` (additive) — list of distinct ISO calendar days with at least one mood entry, ordered ASC.
   - `IkdEventDao.getKeystrokeCountTotal()` (additive) — `SUM(CASE WHEN event_category != 'AUTOCORRECT' THEN 1 ELSE 0 END)` lifetime.
   - `SessionDao.getSessionCalendarDays()` (additive) — list of distinct ISO calendar days with at least one session.
   - `IkdSessionStatsLoader` per-session WPM / error-rate is *not* re-aggregated globally; instead a one-shot `SessionDao.getMaxSessionWpm()` (or pre-computed via `keystrokeCount * 60_000 / durationMs` filter) and `SessionDao.getMinSessionErrorRate(minKeystrokes = 100)` answer the Speed Demon and Precision criteria with a single query each.

2. **Companion-only `evaluateBadges(criteria, snapshot)`** — pure Kotlin folding step:
   - For each `BadgeDef` in `IkdBadgeCatalog.ALL`:
     - If already in `getAllUnlockedKeys()`: skip.
     - Else: evaluate the criterion against the snapshot. If satisfied → add to `newlyUnlocked` list with `unlockedAt = System.currentTimeMillis()`.

3. **Persist newly-unlocked rows** via `BadgeDao.upsert(newlyUnlocked.map { Badge(badgeKey = it.key, unlockedAt = it.unlockedAt) })`.

4. **Return** `EvaluationResult(allUnlocked, newlyUnlocked)`.

The fragment reads from `allUnlocked` to render its grid. The host activity reads `newlyUnlocked` to decide whether to show the snackbar.

**Streak computation:** the implementer can fold the calendar-day list in Kotlin (`Companion.computeLongestStreak(days)` already exists in `IkdHabitsAggregator` per Phase 9.3). Reuse it directly, or copy the logic into the evaluator's companion to keep the modules independent.

**Performance budget:** `evaluate()` logs its wall-clock duration in debug. Target: < 50 ms for a typical user (a few thousand events / few hundred mood entries / few dozen sessions).

---

## 7. UI placement and styling

### 7.1 Tab integration

`DashboardPagerAdapter` gains:

```kotlin
const val TAB_COUNT = 6
const val TAB_ACHIEVEMENTS = 5
```

`DashboardActivity` adds a new entry to the existing tab title array (e.g. `R.array.dashboard_tab_titles`). The `TabLayoutMediator` setup is unchanged in shape — one extra title and one extra icon ID.

### 7.2 `AchievementsFragment` layout

```
+------------------------------------------+
| [section header: "Mood Cataloging"]      |
| +----------+ +----------+                |
| |   🌱     | |   📅     |                |
| | First    | | 7-Day    |                |
| | Mood     | | Diarist  |                |
| | Unlocked | | Locked   |                |
| | 12 May   | | (grey)   |                |
| +----------+ +----------+                |
|                                          |
| +----------+ +----------+                |
| |   🗓️     | |   🌈     |                |
| | …        | | …        |                |
| +----------+ +----------+                |
| …                                        |
|                                          |
| [section header: "Keyboard Usage"]       |
| …                                        |
+------------------------------------------+
```

Two `RecyclerView` sections sharing the same `BadgeAdapter`, separated by a `MyTextView` section header tinted with `getProperPrimaryColor()`. Or one RecyclerView with category headers as additional view types — implementer's choice; both are clean.

### 7.3 Locked vs unlocked

| State | Visual |
|---|---|
| **Locked** | Card alpha = 0.45, emoji rendered with a saturation = 0 ColorMatrix (greyscale), description still visible (incentive to read criteria), no unlock date row |
| **Unlocked** | Card at full alpha, emoji full-colour, subtle 1 dp glow border via `app:strokeColor="@color/badge_unlocked_glow"`, unlock date row visible (`"Unlocked 12 May 2026"`) |

### 7.4 Snackbar on new unlock

Anchored to `dashboardViewPager` (so it floats above the bottom nav). Single unlock:

```
🌱 First Mood unlocked!                [VIEW]
```

Multiple simultaneous unlocks:

```
🌱 First Mood unlocked! +2 more         [VIEW]
```

Tapping `[VIEW]` runs `binding.dashboardViewPager.currentItem = TAB_ACHIEVEMENTS`. The snackbar uses `Snackbar.LENGTH_LONG` (3.5 s), no further action needed if the user dismisses it.

---

## 8. Files summary

**Modified (~7):**
- `databases/IkdDatabase.kt` (schema bump + migration registration)
- `extensions/ContextExt.kt` (lazy evaluator getter)
- `activities/DashboardActivity.kt` (evaluator hop + snackbar)
- `activities/dashboard/DashboardPagerAdapter.kt` (6th tab branch)
- `res/values/strings.xml` (~30 new strings)
- `res/values/colors.xml` and `res/values-night/colors.xml` (badge tints)
- `app/src/androidTest/.../IkdDatabaseMigrationTest.kt` (3→4 migration test)

**Created (~10):**
- `models/Badge.kt`
- `interfaces/BadgeDao.kt`
- `helpers/IkdBadgeCatalog.kt`
- `helpers/IkdBadgeEvaluator.kt`
- `activities/dashboard/AchievementsFragment.kt`
- `adapters/BadgeAdapter.kt`
- `res/layout/fragment_dashboard_achievements.xml`
- `res/layout/item_badge_card.xml`
- `app/src/test/.../IkdBadgeEvaluatorTest.kt`
- (Additive DAO query interfaces if extracted into POJO files, e.g. `interfaces/SessionWpmRow.kt` for the Speed Demon query result)

**Untouched (deliberately):**
- All capture-path code, all entities except the new `Badge`
- `helpers/IkdCsvWriter.kt` (CSV format frozen)
- `helpers/Config.kt`, `helpers/Constants.kt` (no new prefs)
- All Phase 9 surface aggregators and chart wrappers
- The mood bar / keyboard top bar
- `app/build.gradle.kts`, `gradle.properties`

---

## 9. Verification

End-to-end on-device smoke test:

1. **Clean build:**
   ```powershell
   $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
   ./gradlew clean assembleCoreDebug
   ```
   - No new lint baseline failures.
   - Detekt count flat or net-positive (~10 new files, mostly small).

2. **Migration test passes:**
   ```powershell
   ./gradlew :app:connectedAndroidTest --tests "*IkdDatabaseMigrationTest*"
   ```
   - `migrate_3_to_4_addsBadgesTableAndIndex` passes.
   - Existing v3 migration tests still pass.

3. **JVM unit tests pass:**
   ```powershell
   ./gradlew :app:testCoreDebugUnitTest --tests "*IkdBadgeEvaluatorTest*"
   ```

4. **Install + on-device behaviour:**
   - Open Insights dashboard. Sixth tab **Achievements** is visible.
   - Empty-state path: install on a device with no captured data. Achievements tab shows all 13 badges in their **locked** state with description visible. No snackbar.
   - First-mood path: open keyboard, tap any mood emoji on the bar. Reopen dashboard. Snackbar shows `🌱 First Mood unlocked!`. Tap `[VIEW]` → jumps to Achievements. The 🌱 First Mood card is now in unlocked state.
   - First-session path: type a few characters with the keyboard (auto-creates a session via Phase 2). Reopen dashboard. Snackbar shows `⌨️ Hello, World unlocked!`.
   - Multi-unlock path: on a device with rich existing data (10K+ keystrokes, 7+ days of typing, 7+ mood entries spanning 7 days), reopen dashboard. Single snackbar shows the first unlock + `"+N more"`. Achievements tab shows multiple badges unlocked.

5. **Privacy verification:** export CSV from `IkdSettingsActivity` after some badges unlock. Confirm CSV format is byte-identical to before — **no `badges` block, no badge keys anywhere** in the export.

6. **Schema verification:** `IkdDatabase.version` equals 4. The Insights dashboard from earlier phases opens with all existing data intact.

---

## 10. Out of scope (deferred)

- **Per-badge unlock-progress display** (e.g. "5/7 days for 7-Day Diarist"). v1 only shows locked vs unlocked; no progress bars. Adding progress requires per-badge predicate-progress functions on `BadgeDef`. Future micro-phase.
- **Badge sharing** (export, share-intent, image generation). Privacy-sensitive — postponed.
- **Push/system notifications on unlock.** v1 stays in-app only (no notification permission ask).
- **Sound or haptic on unlock.** v1 is silent + visual. Future polish.
- **Per-mood badges** (e.g. "Logged Happy 30 times"). Could double the catalog size — deferred to a future micro-phase if user feedback indicates demand.
- **Weekly / monthly recap badges** (e.g. "Best Week Ever"). Requires more complex aggregation. Deferred.
- **Negative or "shadow" badges.** Skipped on principle — gamification here is positive-reinforcement only.
- **Achievements reset / replay.** Once unlocked, badges stay unlocked. There is no "re-earn" or "reset progress" surface. Future "research-mode reset" feature could clear the table.
- **`SimpleActivity.getAppIconIDs()` swap on badge unlock** (cosmetic launcher-icon reward for power users). Cute idea, but Android requires re-launching to apply, so the UX is poor. Skipped.
