# Phase 14 — Gamification: Badges (Mood + Keyboard)

**Status:** **Implemented** (landed on `main`, `a8612751` … `4985b684`; scope revised 2026-05-17 — per-badge progress + system notifications brought in-scope; catalog expanded to the full spectrum). v1 shipped **28 badges across 5 groups** (37 catalogued; groups 2 & 6 deferred). The Achievements tab shipped as **Option B — a flat grouped list, not the per-group carousel of §7** (see the superseding note in §7 below). Follow-up reworks landed on `main`: per-field password-capture privacy skip (`f09a6a87`), the Summary "Badges in progress" widget themed uniform with the mood tiles + "Achievements" section label, full Fossify-theme integration of all badge surfaces, and the removal of the active-filter chip pill (`1c0f71d2`).
**Depends on:** Phase 8 (mood entries in `ikd.db`), Phase 9 (Insights dashboard tab structure). **Coupled with [Phase 15](../Phase15/Phase15_Plan.md)** — the Insights IA v2 restructure (drop Habits, re-order tabs). The final tab order is **Summary · Achievements · Activity · Trends · Keys** (`TAB_ACHIEVEMENTS = 1`, `TAB_COUNT = 5`); land Phase 14 + 15 together, or 14 first (interim 6 tabs) then 15 collapses to 5.
**Branch:** Implementation lands directly on `main` (small focused commits per logical change), per recent project hygiene.
**Scope (one sentence):** Add the badge spectrum — **28 badges across 5 groups in v1** (37 catalogued; groups 2 & 6 deferred — see [`Phase14_BadgeCatalog.md`](Phase14_BadgeCatalog.md)) that rewards mood cataloging and keyboard usage **only** (no behaviour-shaping badges), surfaced as the **"Achievements"** tab in the Insights dashboard (index 1 in the Phase 15 final order — see Depends-on), where each locked badge shows **per-badge progress** toward its target, with both an in-app snackbar **and** a local system notification when a new badge unlocks.

> **Scope revision (2026-05-17).** Three changes vs. the original draft, on owner request:
> 1. **Finalised catalog** — the 13 starter badges are superseded by the catalog in [`Phase14_BadgeCatalog.md`](Phase14_BadgeCatalog.md): 37 catalogued, **28 built in v1** (groups 2 — Mood Diversity, and 6 — KB Sessions — deferred per owner directive). v1 groups: mood volume / daily check-in / daily devotion + keyboard keystrokes / session-streak. §4 below is now a pointer to that file.
> 2. **Per-badge unlock-progress display** — locked cards render a progress bar + `current / target` text (was §10-deferred).
> 3. **System notifications on unlock** — a local notification fires alongside the snackbar (was §10-deferred). **Local only, no network — the "no internet permission" guarantee is unchanged.** This adds the first runtime permission the app ever requests (`POST_NOTIFICATIONS`, Android 13+), gated behind a user-facing settings toggle (default ON) with graceful degradation if denied (snackbar still shows).

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
| `activities/dashboard/DashboardPagerAdapter.kt` | Add the Achievements tab at the Phase 15 index | Final order `TAB_COUNT = 5`, `TAB_ACHIEVEMENTS = 1` (Summary 0 · Achievements 1 · Activity 2 · Trends 3 · Keys 4); new `createFragment` branch → `AchievementsFragment()`. If Phase 14 lands before 15, interim `TAB_ACHIEVEMENTS = 5 / TAB_COUNT = 6`, collapsed by Phase 15 |
| `activities/dashboard/SummaryFragment.kt` + `res/layout/fragment_dashboard_summary.xml` | Add a compact **"Badges in progress"** widget (Decision #1, mirrors the Phase 9.17 mood-distribution tiles) | Scoped unfreeze of the Phase 9.15/9.17 Summary surface — additive: one new horizontal tile strip rendered from the same `EvaluationResult` payload; tap a tile → Achievements tab |
| `activities/DashboardActivity.kt` (tab title array, `TabLayoutMediator` setup, `STATE_TAB_INDEX` saved-instance handling) | Display the Achievements tab label/icon at its Phase 15 index | Add Achievements label string + icon into the tab arrays at index 1 (final order); coordinate with Phase 15's array rewrite |
| `res/values/strings.xml` | New strings for the tab title, the Summary widget title, snackbar + notification templates, the progress format string, and 28 v1 badge titles + descriptions | Additive only |
| `res/values/colors.xml` and `res/values-night/colors.xml` | Locked-state and unlocked-state badge tint tokens | Additive only — `badge_locked_tint`, `badge_unlocked_glow`, plus a faded surface for locked cards |
| `AndroidManifest.xml` | Declare `POST_NOTIFICATIONS` (Android 13+ runtime permission) | One `<uses-permission>` line. **No `INTERNET` — notifications are local `NotificationManager` posts, never network.** |
| `helpers/Config.kt`, `helpers/Constants.kt` | One new pref: `badgeNotificationsEnabled` (default `true`) | One `const val` key + one `var` accessor. Explicit, scoped unfreeze of the Phase-8 Config/Constants freeze — see Decision #11 |
| `activities/IkdSettingsActivity.kt` + `res/layout/activity_ikd_settings.xml` | "Notify me when I unlock a badge" toggle row | One toggle row backed by `Config.badgeNotificationsEnabled`; mirrors the existing settings-row pattern |

### New files

| File | Purpose |
|---|---|
| `models/Badge.kt` | Room entity — `@Entity(tableName = "badges")` with `id: Long`, `badgeKey: String` (`@ColumnInfo(name = "badge_key")`, unique index), `unlockedAt: Long` (`@ColumnInfo(name = "unlocked_at")`) |
| `interfaces/BadgeDao.kt` | DAO — `@Insert(onConflict = REPLACE) suspend fun upsert(...)`, `@Query("SELECT badge_key FROM badges") suspend fun getAllUnlockedKeys(): List<String>`, `@Query("SELECT * FROM badges ORDER BY unlocked_at DESC") suspend fun getAllUnlocked(): List<Badge>` |
| `helpers/IkdBadgeCatalog.kt` | Static catalog: list of `BadgeDef(key, emoji, titleRes, descRes, criteria, progress)` for the **28 v1 badges** (groups 1, 3, 4, 5, 7) in [`Phase14_BadgeCatalog.md`](Phase14_BadgeCatalog.md). Groups 2 & 6 are catalogued there but **not added to this list in v1** (additive later). `criteria: (BadgeSnapshot) -> Boolean` and `progress: (BadgeSnapshot) -> BadgeProgress?` (`null` = boolean badge, no bar). Pure data — no Room, no I/O |
| `helpers/IkdBadgeEvaluator.kt` | `suspend fun evaluate(): EvaluationResult` on `Dispatchers.IO`. Reads existing tables (mood_entries, ikd_events, sessions), builds one `BadgeSnapshot`, computes which badges should be unlocked + each locked badge's progress, persists new unlocks via `BadgeDao.upsert(...)`. Returns `EvaluationResult(allUnlocked, newlyUnlocked, progressByKey)`. Pure derivation lives on `Companion.evaluateBadges(snapshot)` for unit testing — same pattern as `IkdAggregator.Companion.buildSnapshot` |
| `helpers/IkdBadgeNotifier.kt` | Wraps `NotificationManagerCompat`: creates the `"moodscript_badges"` channel once, posts a local notification for newly-unlocked badges (single = badge title; multiple = first title + "+N more"), tapping it opens `DashboardActivity` on the Achievements tab via a `PendingIntent`. No-ops if `Config.badgeNotificationsEnabled == false` or the runtime permission is not granted |
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
| `helpers/IkdMoodBarController.kt`, `helpers/MoodEmoji.kt`, `views/MyKeyboardView.kt` and the keyboard top-bar XML | Mood-bar UX unchanged |

> **Scoped unfreeze:** `helpers/Config.kt` / `helpers/Constants.kt` were frozen by Phase 8. Phase 14 reopens them for **exactly one** additive pref (`badgeNotificationsEnabled`) — the notification toggle. No other pref, no capture-path key. The IME / capture path stays fully frozen.

### Branch hygiene

Six focused commits, in this order:

1. Schema bump: `Badge` entity + `BadgeDao` + `Migration(3, 4)` + migration test.
2. Catalog + evaluator: `IkdBadgeCatalog` (28 v1 badges + `criteria`/`progress` lambdas), `IkdBadgeEvaluator` (incl. companion-only logic + progress), unit tests.
3. UI: `AchievementsFragment` + `BadgeGroupAdapter` + `BadgeCardAdapter` + `IkdBadgeDayStripView`, per-group carousel layouts (`item_badge_group.xml` / `item_badge_card.xml`, `‹`/`›` + swipe + page dots + devotion day strip), strings, colours.
4. Notifications: `IkdBadgeNotifier`, `POST_NOTIFICATIONS` manifest entry, `Config.badgeNotificationsEnabled` + Constants key + settings toggle row, runtime-permission request.
5. Dashboard wiring: pager adapter + `DashboardActivity` evaluator hop + snackbar + notifier dispatch.
6. Docs: this plan, [`Phase14_BadgeCatalog.md`](Phase14_BadgeCatalog.md), [`STATUS.md`](../STATUS.md) row, optional `CLAUDE.md` update.

No push, no PR opened by the implementer.

---

## 3. Locked decisions

| # | Decision | Value |
|---|---|---|
| 1 | Placement | **"Achievements" tab at index 1** (Phase 15 final order: Summary · Achievements · Activity · Trends · Keys), **plus a compact "Badges in progress" widget on the Summary tab** (owner directive 2026-05-17). The Summary widget mirrors the Phase 9.17 mood-distribution-tile pattern: a horizontal strip of small tiles, one per group showing that group's current in-progress badge (emoji + mini progress + caption); tapping a tile jumps to the Achievements tab. It is a thin presentation of the same `EvaluationResult` the tab uses — no extra query. (Considered + still rejected: an "X/Y" cell on the Habits KPI strip) |
| 2 | Catalog size | **37 catalogued, 28 in v1 across 5 groups.** Defined in [`Phase14_BadgeCatalog.md`](Phase14_BadgeCatalog.md) (§4 below is a pointer). v1 groups: 1 Mood Volume · 3 Daily check-in (1·2·3+, best-day) · 4 Daily devotion (≥3 logs-per-day on **consecutive** days — strict streak) · 5 Keystroke volume · 7 Session streak. **Deferred (catalogued, not built in v1):** 2 Mood Diversity, 6 KB Sessions. Owner directive: **mood-cataloging + keyboard-usage only** — no behaviour-shaping badges (Speed / Precision / Circadian / Habit dropped); per-emotion group dropped; the two redundant daily-streak groups merged into one keyboard-session streak (group 7, keeping its icons) |
| 3 | Badge icons | **Emoji codepoints only** for v1. No custom vector drawables. Locked badges render in greyscale via a `ColorFilter` saturation matrix (no Photoshopped variants needed) |
| 4 | Schema | Add `badges` table via `Migration(3, 4)`. Bump `IkdDatabase.version` 3 → 4 |
| 5 | Evaluation timing | **Lazy, on dashboard open.** `IkdBadgeEvaluator.evaluate()` runs alongside existing aggregators in `DashboardActivity.loadDashboard()` on `Dispatchers.IO`. No real-time evaluation in the capture path |
| 6 | Unlock feedback | **In-app `Snackbar` + local system notification.** When ≥ 1 new badge lands during evaluation: (a) snackbar anchored to the dashboard (first title + `"+N more"`, action → Achievements tab); (b) a local `NotificationManagerCompat` notification posted in parallel via `IkdBadgeNotifier`, tap → `DashboardActivity` on the Achievements tab. The notification is **local only — no network, no `INTERNET` permission**. Gated behind `Config.badgeNotificationsEnabled` (default ON) and the `POST_NOTIFICATIONS` runtime grant; if the pref is off or permission denied, the snackbar still fires (graceful degradation) |
| 10 | Per-badge progress | **In scope.** Every `BadgeDef` carries a `progress: (BadgeSnapshot) -> BadgeProgress?`. Locked cards render a horizontal `ProgressBar` + `"current / target"` text (e.g. `6,432 / 10,000`). `null` progress (boolean badges) → no bar. **All 28 v1 badges have numeric progress** — the only boolean badge (`mood_returner`) is in deferred group 2, so the boolean card variant is spec'd but unused until group 2 ships. Unlocked cards hide the bar and show the unlock date instead |
| 11 | Notification permission & pref | `POST_NOTIFICATIONS` declared in the manifest (Android 13+). One additive pref `Config.badgeNotificationsEnabled` (default `true`) + a settings toggle row — an explicit, minimal unfreeze of the Phase-8 Config/Constants freeze (capture path stays frozen). Runtime permission requested lazily the first time a notification would fire (or from the settings toggle); denial is silent and non-blocking |
| 12 | Progress source | Progress is computed from the **same `BadgeSnapshot`** the criteria use — one extra pure function per `BadgeDef`, zero extra queries. Streak progress (groups 4 & 7) uses **longest** streak so a lapsed streak never visually regresses an in-progress bar or re-locks an earned badge |
| 13 | Filter independence | **The Achievements tab is always all-time and ignores the global Range + Mood filters.** Badges measure lifetime progress; a "this week" or "Anger-only" scoping would be meaningless for them. The evaluator already reads unfiltered tables; additionally the active-filter chip's scope does **not** apply on this tab and the tab shows a fixed "All time" affordance. Owner directive 2026-05-17 |
| 14 | Achievements layout | **Per-group horizontal carousel, not a flat grid.** Each group is one row that shows a single **centred focus card** — defaulting to that group's *current in-progress* badge (first not-yet-unlocked, else the last unlocked). `<` / `>` buttons **and** horizontal swipe page through the rest of that group's badges. The **Daily-devotion** group's focus card additionally renders a "mini-keyboard" **day strip** (last 14 days as small key-cells, filled = qualifying day ≥ 3 logs) so the strict consecutive-streak rule is legible. Owner directive 2026-05-17. Supersedes the original 2-column-grid sketch in §7.2 |
| 7 | Privacy | Unchanged. Badges are derived from existing `ikd_events`, `sessions`, and `mood_entries`. No new captured data, no new pref keys, no CSV column |
| 8 | Re-evaluation cost | The evaluator runs once per `loadDashboard()` call. Worst case is ~3-4 small SQL queries (count + min/max + DISTINCT mood count + streak query). Logs wall-clock time to Logcat (tag `IkdBadgeEvaluator`) when `BuildConfig.DEBUG` — same pattern as `IkdAggregator` |
| 9 | Tab title and icon | Tab label: "Achievements" (`R.string.dashboard_tab_achievements`). Icon: `🏆` rendered as a tab item via the existing `TabLayoutMediator` pattern, or a vector drawable equivalent (`ic_tab_achievements_vector`) for theme tinting consistency. **Implementer's choice** based on what scales cleanest at the existing tab size |

---

## 4. Badge catalog → see `Phase14_BadgeCatalog.md`

The badge spectrum lives in its own reviewable file: **[`Phase14_BadgeCatalog.md`](Phase14_BadgeCatalog.md)** — 37 catalogued, **28 built in v1** across 5 groups (mood volume / daily check-in / daily devotion + keyboard keystrokes / session-streak). Groups 2 (Mood Diversity) and 6 (KB Sessions) are catalogued but **deferred** from v1 per the 2026-05-17 owner directive. Per-emotion and behaviour-shaping groups (Speed, Precision, Circadian, Habit/Combined) and the redundant mood-streak group were dropped entirely; the in-day idea was split into a lightweight check-in group (1·2·3+ logs in a day, best-day) and a strict "≥3 logs-per-day on **consecutive** days" devotion streak.

That file is the single source of truth for: every stable `badge_key`, its icon, title, unlock criterion, **and the `current / target` progress metric** the new per-badge progress UI renders. It also lists the exact `BadgeSnapshot` fields the evaluator must materialise (≤ ~8 small SQL queries, all against existing tables) and the `computeLongestStreak` reuse from `IkdHabitsAggregator`.

`IkdBadgeCatalog.kt` is the literal Kotlin transcription of that table: one `BadgeDef(key, emoji, titleRes, descRes, criteria, progress)` per row. Each badge gets an English `title` + `description` string-resource pair, e.g.:

```xml
<string name="badge_mood_vol_1_title">First Mood</string>
<string name="badge_mood_vol_1_desc">You logged your first mood. The journey starts here.</string>
<string name="badge_kb_streak_7_title">Week Streak</string>
<string name="badge_kb_streak_7_desc">Typed at least once a day for seven days in a row.</string>
```

Locale translations are encouraged but optional for v1. Icon collisions within a tiered family (e.g. `🔥` across `kb_streak_3/7/14`) are intentional — identity is the `badge_key`, the emoji is decorative.

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

1. **Snapshot existing-state queries** — the exact list (≤ ~6, all additive, all on existing tables) is tabulated in [`Phase14_BadgeCatalog.md` § "Snapshot fields the evaluator needs"](Phase14_BadgeCatalog.md#snapshot-fields-the-evaluator-needs):
   **v1 queries (5 — groups 2 & 6 deferred, so their queries are not built yet):**
   - `BadgeDao.getAllUnlockedKeys()` — current unlocked set.
   - `MoodDao.getMoodCount()` — total mood entries (group 1).
   - `MoodDao.getMoodEntriesOrdered()` — timestamps ordered ASC; per-local-day bucketing in Kotlin → `bestDayLogs` = max per-day count (group 3), `devotionStreak` = **longest run of consecutive days with ≥ 3 logs** via `computeLongestStreak` over the qualifying-day list (group 4, strict streak), and `recentDayQualified` = last-14-days `List<Boolean>` for the group-4 day-strip UI. One list, no extra query; ≤ a few thousand rows, single pass.
   - `IkdEventDao.getKeystrokeCountTotal()` — `SUM(CASE WHEN event_category != 'AUTOCORRECT' THEN 1 ELSE 0 END)` lifetime (group 5).
   - `SessionDao.getSessionCalendarDays()` — distinct ISO calendar days with ≥ 1 session, ordered ASC (group 7 session-streak, via `computeLongestStreak`).

   **Deferred with their groups (added when 2 / 6 ship):** `MoodDao.getMoodCountByScore()` (group 2 — Full Spectrum / Balanced), the Rainbow-Week/Returner pass over `getMoodEntriesOrdered()` (group 2), `SessionDao.getSessionCount()` (group 6).

   No WPM / error-rate / start-hour / duration query — those existed only for the dropped Speed / Precision / Circadian / Habit groups.

   The exact field list and per-field query are tabulated in [`Phase14_BadgeCatalog.md` § "Snapshot fields the evaluator needs"](Phase14_BadgeCatalog.md#snapshot-fields-the-evaluator-needs). All of it is one `BadgeSnapshot` value object built on the IO hop.

2. **Companion-only `evaluateBadges(snapshot)`** — pure Kotlin folding step:
   - For each `BadgeDef` in `IkdBadgeCatalog.ALL`:
     - Compute `progress = def.progress?.invoke(snapshot)` → into `progressByKey[def.key]` (used by the locked-card UI).
     - If already in `getAllUnlockedKeys()`: skip the unlock check (keep its progress for completeness, though the card renders unlocked).
     - Else: evaluate `def.criteria(snapshot)`. If satisfied → add to `newlyUnlocked` with `unlockedAt = System.currentTimeMillis()`.

3. **Persist newly-unlocked rows** via `BadgeDao.upsert(newlyUnlocked.map { Badge(badgeKey = it.key, unlockedAt = it.unlockedAt) })`.

4. **Return** `EvaluationResult(allUnlocked, newlyUnlocked, progressByKey)`.

The fragment reads `allUnlocked` + `progressByKey` to render its grid (progress bar on each locked card). The host activity reads `newlyUnlocked` to fire the snackbar **and** dispatch `IkdBadgeNotifier.notify(newlyUnlocked)`.

**Streak computation:** the implementer can fold the calendar-day list in Kotlin (`Companion.computeLongestStreak(days)` already exists in `IkdHabitsAggregator` per Phase 9.3). Reuse it directly, or copy the logic into the evaluator's companion to keep the modules independent.

**Performance budget:** `evaluate()` logs its wall-clock duration in debug. Target: < 50 ms for a typical user (a few thousand events / few hundred mood entries / few dozen sessions).

---

## 7. UI placement and styling

> **⚠️ SUPERSEDED — shipped as Option B (flat grouped list), not the carousel.**
> The Achievements tab in §7 (and Decision #14) describes a **per-group horizontal
> carousel** with `‹`/`›` arrows, swipe paging, and page dots. That layout *was*
> built first, then **rebuilt after owner review** as **Option B: a single flat
> vertical list** of group section headers + full-width badge rows. The shipped
> implementation is `adapters/BadgeListAdapter.kt` + `item_badge_list_header.xml` +
> `item_badge_list_row.xml`; the carousel files (`BadgeGroupAdapter`,
> `BadgeCardAdapter`, `item_badge_group.xml`, `item_badge_card.xml`,
> `badge_arrow_*`, `badge_dot`) were **deleted**. The Daily-devotion 14-day
> `IkdBadgeDayStripView` strip is **preserved**, folded into the devotion group's
> section header. The historical carousel design text in §7 / Decision #14 is
> **retained below as the original design record** — it is no longer the shipped
> behaviour. The alternatives that were weighed are catalogued in
> **[`Phase14_Achievements_Layout_Options.html`](Phase14_Achievements_Layout_Options.html)**.
> Every other §7 spec (card states, progress bar, snackbar + local notification,
> Summary "Badges in progress" widget, all-time scope) shipped as written.

> **Visual mockup (historical, carousel design):** the originally-suggested
> Achievements-tab layout — per-group carousel, centred current-in-progress card,
> `‹`/`›` + swipe paging, the Daily-devotion day strip, card states, snackbar +
> notification, dark variant — is rendered in
> **[`Phase14_Achievements_Layout.html`](Phase14_Achievements_Layout.html)** (open in
> a browser). The subsections below are the original spec; **the shipped UI is the
> flat Option B list described in the superseding note above.**

### 7.1 Tab integration

`DashboardPagerAdapter` gains:

```kotlin
// Final order (with Phase 15): Summary 0 · Achievements 1 · Activity 2 · Trends 3 · Keys 4
const val TAB_COUNT = 5
const val TAB_SUMMARY = 0
const val TAB_ACHIEVEMENTS = 1
const val TAB_ACTIVITY = 2
const val TAB_TRENDS = 3
const val TAB_KEYS = 4
// Interim (only if Phase 14 ships before Phase 15): TAB_COUNT = 6, TAB_ACHIEVEMENTS = 5
```

`DashboardActivity` inserts the Achievements title+icon at index 1 of the tab arrays. The `TabLayoutMediator` setup is unchanged in shape. Phase 15 owns the surrounding re-order + Habits removal — see [`Phase15_Plan.md`](../Phase15/Phase15_Plan.md).

### 7.2 `AchievementsFragment` layout — per-group carousel (Decision #14)

A vertical list of **group rows** (a parent `RecyclerView`, one item per v1 group:
Mood Volume · Daily check-in · Daily devotion · Keystroke volume · Session streak).
Each group row is:

```
+----------------------------------------------------+
|  MOOD · DAILY DEVOTION                    1 / 6  ᴬ |
|                                                    |
|   ‹              ┌───────────────┐             ›   |
|   ◀  (prev)      │      💪       │      (next) ▶   |
|                  │   Engaged     │                 |
|                  │ 3 consecutive │                 |
|                  │ qualifying    │                 |
|                  │ days          │                 |
|                  │ ▓▓▓▓▓░░░░░░░  │                 |
|                  │   1 / 3 days  │                 |
|                  └───────────────┘                 |
|        · · · · ●  · · ·   (page dots, idx 2/6)     |
|                                                    |
|  [ M T W T F S S M T W T F S S ]  ← day strip ᴮ    |
+----------------------------------------------------+
```

- **One centred focus card per group.** Default focus = the group's *current
  in-progress* badge: the first not-yet-unlocked `BadgeDef` in catalog order; if all
  are unlocked, the last (highest) one. This is the badge the user is working toward.
- **`‹` / `›` buttons + horizontal swipe** page through the rest of that group's
  badges (already-unlocked behind, future locked ahead). Implementation: a nested
  horizontal `ViewPager2` (or a `RecyclerView` + `PagerSnapHelper`) per group row,
  with `clipToPadding=false` so neighbours peek. Page dots under the card show
  position within the group. The `‹`/`›` arrows are `ImageButton`s that call
  `viewPager.currentItem ∓ 1` (disabled/greyed at the ends); they exist for
  discoverability and accessibility — swipe does the same thing.
- ᴬ Group header: `MyTextView` tinted `getProperPrimaryColor()`, uppercase, with the
  group's `unlocked / total` count on the trailing edge.
- ᴮ **Day strip — Daily-devotion group only** (see §7.3). Other groups omit it.
- The parent vertical list keeps each group's horizontal scroll position across
  config changes via the adapter holding per-group `currentItem` (or `RecyclerView`
  state). Tab is **always all-time** (Decision #13) — no filter chip effect here.

`AchievementsFragment` extends `DashboardFragment` for the payload plumbing. The
parent adapter is `BadgeGroupAdapter` (one VH per group, inflating
`item_badge_group.xml` = header + arrows + nested pager + dots + optional strip).
The nested pager uses `BadgeCardAdapter` over that group's `List<BadgeUiModel>`.

### 7.3 Card states + the devotion day-strip

**Focus card states** (`item_badge_card.xml`):

| State | Visual |
|---|---|
| **Locked, with progress** | Emoji greyscale (saturation-0 `ColorMatrix`), faded surface, description visible, **horizontal determinate `ProgressBar` + `"1 / 3 days"` / `"42,310 / 100,000"` caption** (grouped digits via `R.string.badge_progress_format`). Bar tinted `?attr/colorPrimary` |
| **Locked, boolean (no progress)** | Same, progress row `GONE` (`def.progress == null`). *No v1 badge hits this — `mood_returner` is deferred group 2; spec'd for later* |
| **Unlocked** | Full-colour emoji, 1 dp glow border `app:strokeColor="@color/badge_unlocked_glow"`, progress row hidden, unlock-date row shown (`"Unlocked 17 May 2026"`) |

`item_badge_card.xml`: `badge_progress_bar` (`ProgressBar`,
`?android:attr/progressBarStyleHorizontal`) + `badge_progress_text`
(`MyTextView`, `smaller_text_size`); `BadgeCardAdapter` flips progress-row vs.
date-row by state and clamps `current ≤ target`.

**Devotion day-strip** (`view/IkdBadgeDayStripView.kt`, a small custom `View` or a
14-cell `LinearLayout`): renders `recentDayQualified` — the last **14 local days**,
oldest→newest, as small rounded "key" cells. A qualifying day (≥ 3 intentional logs)
is filled `?attr/colorPrimary`; a non-qualifying day is the dim locked-surface tint.
Today is the rightmost cell. It is **per-group, shown once under the Daily-devotion
group's card** (not per-badge — the streak data is identical across that group's
tiers). Privacy: it visualises only a boolean-per-day derived from existing
`mood_entries` timestamps; no counts, no text, nothing new stored.

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

### 7.5 System notification on new unlock

Fired in parallel with the snackbar by `IkdBadgeNotifier.notify(newlyUnlocked)`:

- **Channel:** created once, id `"moodscript_badges"`, name "Badge unlocks", `IMPORTANCE_DEFAULT`, no sound override (respects system).
- **Content:** single unlock → title `"🌱 First Mood unlocked!"`, text = badge description. Multiple → title `"3 badges unlocked!"`, text = first title + `"+N more"`. Small icon = the existing app/launcher monochrome icon.
- **Tap action:** `PendingIntent` → `DashboardActivity` with an extra (`EXTRA_OPEN_TAB = TAB_ACHIEVEMENTS`) so `onCreate`/`onNewIntent` jumps straight to the Achievements tab. `FLAG_IMMUTABLE`.
- **Local only.** `NotificationManagerCompat.notify(...)` — no network, no `INTERNET` permission, nothing leaves the device. The notification text is a static badge title/description from `strings.xml`, never any captured data.
- **Gating:** no-op when `Config.badgeNotificationsEnabled == false`. On Android 13+ the `POST_NOTIFICATIONS` runtime permission is requested lazily (first time a notification would post, or from the settings toggle); if not granted, `IkdBadgeNotifier` silently skips — the snackbar already covered the in-app case.
- **No duplicate spam:** only `newlyUnlocked` (this evaluation's deltas) is ever notified — already-unlocked badges never re-notify, because they're filtered out before persistence.

### 7.6 Summary-tab "Badges in progress" widget (Decision #1)

A compact widget on the **Summary** tab, styled after the Phase 9.17
mood-distribution tiles (a `MaterialCardView` section with a primary-tinted title and
a horizontal strip of small tiles):

```
  BADGES IN PROGRESS                          see all ›
  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐
  │  🍀  │ │  📔  │ │  🧗  │ │  📚  │ │  🔥  │
  │37/50 │ │ 2/3  │ │3/7 d │ │ 42%  │ │9/14 d│
  │▓▓▓░░ │ │▓▓░   │ │▓░░░  │ │▓▓░░░ │ │▓▓▓░░ │
  └──────┘ └──────┘ └──────┘ └──────┘ └──────┘
```

- **One tile per v1 group** = that group's *current in-progress* badge (the same
  focus badge the Achievements carousel centres on; if a group is fully unlocked the
  tile shows its top badge as "done", e.g. a ✓). 5 tiles in v1.
- Each tile: greyscale emoji, a thin determinate progress bar, and a terse caption
  (`37/50`, `3/7 d`, `42%` — short form to fit; the full caption lives on the tab).
- **Tap a tile → Achievements tab** (`dashboardViewPager.currentItem =
  TAB_ACHIEVEMENTS`); the strip's "see all ›" header does the same. Mirrors how the
  9.17 distribution tiles double as a shortcut.
- **Data:** `SummaryFragment` reads the *same* `EvaluationResult` already produced by
  the single `loadDashboard()` evaluator hop (the `DashboardPayload` carries it to
  every fragment). **No extra query, no extra evaluation** — pure presentation.
- Hidden entirely (`View.GONE`) when there is no captured data / nothing in progress,
  same empty-state discipline as the other Summary widgets. Always all-time
  (Decision #13) — unaffected by the Summary filter chip.

`fragment_dashboard_summary.xml` gains one `MaterialCardView` (reuses
`card_corner_radius`/`card_elevation`); `SummaryFragment` binds a small
`RecyclerView`/`LinearLayout` of `item_badge_progress_tile.xml`. Additive to the
Phase 9 Summary surface — existing widgets untouched.

---

## 8. Files summary

**Modified (~15):**
- `databases/IkdDatabase.kt` (schema bump + migration registration)
- `extensions/ContextExt.kt` (lazy evaluator getter)
- `activities/DashboardActivity.kt` (evaluator hop + snackbar + notifier dispatch + `EXTRA_OPEN_TAB` handling)
- `activities/dashboard/DashboardPagerAdapter.kt` (Achievements tab branch at the Phase 15 index)
- `activities/dashboard/SummaryFragment.kt` + `res/layout/fragment_dashboard_summary.xml` ("Badges in progress" widget — §7.6)
- `activities/dashboard/DashboardPayload.kt` (carry `EvaluationResult` to fragments — additive field)
- `activities/IkdSettingsActivity.kt` + `res/layout/activity_ikd_settings.xml` (notification toggle row)
- `helpers/Config.kt` + `helpers/Constants.kt` (one pref: `badgeNotificationsEnabled` — scoped unfreeze)
- `AndroidManifest.xml` (`POST_NOTIFICATIONS` uses-permission; no `INTERNET`)
- `res/values/strings.xml` (~62 new strings — 28 v1 title/desc pairs + tab/snackbar/notification/progress templates; groups 2 & 6 strings added when they ship)
- `res/values/colors.xml` and `res/values-night/colors.xml` (badge tints)
- `app/src/androidTest/.../IkdDatabaseMigrationTest.kt` (3→4 migration test)

**Created (~14):**
- `models/Badge.kt`
- `interfaces/BadgeDao.kt`
- `helpers/IkdBadgeCatalog.kt`
- `helpers/IkdBadgeEvaluator.kt`
- `helpers/IkdBadgeNotifier.kt`
- `activities/dashboard/AchievementsFragment.kt` (hosts the per-group vertical list)
- `adapters/BadgeGroupAdapter.kt` (one VH per group: header + arrows + nested pager + dots + optional day strip)
- `adapters/BadgeCardAdapter.kt` (the nested per-group horizontal pager of badge cards)
- `views/IkdBadgeDayStripView.kt` (Daily-devotion last-14-days strip — small custom `View` / 14-cell row)
- `res/layout/fragment_dashboard_achievements.xml` (parent `RecyclerView`)
- `res/layout/item_badge_group.xml` (group row: header + `‹`/`›` + nested `ViewPager2` + page dots + optional strip)
- `res/layout/item_badge_card.xml` (focus card: emoji/title/desc + progress bar + caption / unlock date)
- `res/layout/item_badge_progress_tile.xml` (Summary-tab compact tile — §7.6)
- `app/src/test/.../IkdBadgeEvaluatorTest.kt`

**Untouched (deliberately):**
- All capture-path code, all entities except the new `Badge`
- `helpers/IkdCsvWriter.kt` (CSV format frozen — no `badges` block)
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

- ~~**Per-badge unlock-progress display.**~~ **Now in scope** (Decision #10) — locked cards show a `current / target` progress bar.
- ~~**Push/system notifications on unlock.**~~ **Now in scope** (Decision #6/#11) — a local, no-network notification fires alongside the snackbar, gated behind a settings toggle + `POST_NOTIFICATIONS`.
- **Badge sharing** (export, share-intent, image generation). Privacy-sensitive — postponed.
- **Sound or haptic on unlock.** Notification uses the system default; no extra in-app sound/haptic. Future polish.
- **Per-mood badges** (e.g. "Logged Happy 30 times"). Could double the catalog size — deferred to a future micro-phase if user feedback indicates demand.
- **Weekly / monthly recap badges** (e.g. "Best Week Ever"). Requires more complex aggregation. Deferred.
- **Negative or "shadow" badges.** Skipped on principle — gamification here is positive-reinforcement only.
- **Achievements reset / replay.** Once unlocked, badges stay unlocked. There is no "re-earn" or "reset progress" surface. Future "research-mode reset" feature could clear the table.
- **`SimpleActivity.getAppIconIDs()` swap on badge unlock** (cosmetic launcher-icon reward for power users). Cute idea, but Android requires re-launching to apply, so the UX is poor. Skipped.
