# Phase 14 — Full Badge Spectrum Catalog

**Status:** Catalog finalised for implementation (created 2026-05-17; revised 2026-05-17 per owner review).
**Parent plan:** [`Phase14_Plan.md`](Phase14_Plan.md)

This is the **finalised** badge spectrum for the gamification feature. Every criterion
below is computable from the **existing** tables (`mood_entries`, `sessions`,
`ikd_events`) with no schema change beyond the `badges` table itself and no new
captured data.

**Design principle (owner directive, 2026-05-17):** badges reward **mood cataloging
and keyboard usage only**. No badge rewards a behaviour the user would otherwise
change *to chase the badge* — nothing about typing *faster*, *cleaner*, *at a
particular time of day*, or *for a particular duration*. The earlier Speed,
Precision, Circadian, and Habit/Combined groups were dropped for this reason; the
two redundant "daily streak" groups were merged into one (keyboard-session streak,
keeping its icon set); and the per-emotion group was dropped (it could nudge the user
to log emotions they don't feel just to complete the set).

Conventions:

- **Key** is the stable id stored in `badges.badge_key`. Never reused, never renamed.
- **Progress** is the `(current, target)` pair the per-badge progress UI renders
  (Phase 14 change #1). Boolean badges with no meaningful intermediate state have
  progress `—` (the card shows only locked/unlocked, no bar).
- `keystrokeCount` = events excluding `AUTOCORRECT` (the project-wide definition).
- "Calendar day" = local-time ISO day, matching `IkdHabitsAggregator.computeLongestStreak`.
- The streak group uses **longest** streak, not current, so a broken streak never
  *re-locks* an earned badge.
- **Intentional mood log:** every `mood_entries` row is one — the standing /
  last-session rating never writes a row (verified in `IkdMoodBarController`). Because
  `mood_entries` has a unique index on `session_id`, re-tapping a different mood
  *within the same session* *replaces* that session's row rather than adding one, so
  a per-day count = **distinct sessions annotated that day** (deliberate-logging
  frequency). It never over-counts.

Total catalogued: **37 badges** across 7 groups (mood groups 1–4, keyboard groups 5–7).

> **v1 implementation scope (owner directive, 2026-05-17):** **Groups 2 (Mood —
> Diversity) and 6 (Keyboard — Sessions) are deferred** — catalogued here for the
> record but **not built in the first implementation pass**. v1 ships
> **28 badges across 5 groups**: 1 (Volume), 3 (Daily check-in), 4 (Daily
> devotion), 5 (Keystroke volume), 7 (Session streak). The deferred groups carry no
> dependency for the others, so adding them later is purely additive (new
> `BadgeDef` rows + their snapshot fields).

---

## 1. Mood — Volume  (total rows in `mood_entries`)

| Key | Icon | Title | Criteria | Progress |
|---|---|---|---|---|
| `mood_vol_1` | 🌱 | First Mood | ≥ 1 mood entry | count / 1 |
| `mood_vol_10` | 🌿 | Getting Reflective | ≥ 10 mood entries | count / 10 |
| `mood_vol_50` | 🍀 | Habit Forming | ≥ 50 mood entries | count / 50 |
| `mood_vol_100` | 🎯 | Centurion | ≥ 100 mood entries | count / 100 |
| `mood_vol_500` | 🌳 | Deep Diary | ≥ 500 mood entries | count / 500 |
| `mood_vol_1000` | 🏔️ | Thousand Feelings | ≥ 1000 mood entries | count / 1000 |

## 2. Mood — Diversity  · ⏸️ DEFERRED (not in v1)

| Key | Icon | Title | Criteria | Progress |
|---|---|---|---|---|
| `mood_spectrum` | 🌈 | Full Spectrum | ≥ 1 entry for **each** of the 6 Ekman scores | distinctScores / 6 |
| `mood_balanced` | ⚖️ | Balanced | ≥ 10 entries for **each** of the 6 scores | min(perScoreCount) / 10 |
| `mood_week_rainbow` | 🌦️ | Rainbow Week | All 6 scores logged within a single rolling 7-day window | maxScoresInAny7dWindow / 6 |
| `mood_returner` | 🔄 | Returner | A mood entry logged **after** a gap of ≥ 7 days with none | — (boolean) |

## 3. Mood — Daily check-in  (intentional mood logs within one calendar day)

Lightweight, anyone-can-earn tier. Uses the **best single calendar day ever**, so the
badge persists once earned and a quiet day never re-locks it.

| Key | Icon | Title | Criteria | Progress |
|---|---|---|---|---|
| `mood_day_1` | 📝 | Daily Check-In | ≥ 1 intentional mood log in one calendar day | bestDayLogs / 1 |
| `mood_day_2` | ✍️ | Double Check-In | ≥ 2 intentional mood logs in one calendar day | bestDayLogs / 2 |
| `mood_day_3` | 📔 | Mood Journal | ≥ 3 intentional mood logs in one calendar day | bestDayLogs / 3 |

## 4. Mood — Daily devotion  (secondary system: 3+ intentional logs per day, on **consecutive** days)

The **strict-streak** counterpart to group 3. A **qualifying day** = a calendar day
with **≥ 3** intentional mood logs. These badges require an **unbroken run of
consecutive qualifying days** — miss a day (fewer than 3 logs, or none) and the run
resets to 0. (Cumulative "total days" rewards live in the volume groups; this group
is deliberately the hard, habit-forming one — owner directive 2026-05-17.)

`devotionStreak` = longest run of consecutive calendar days each with ≥ 3 intentional
logs, via the same `computeLongestStreak` used by the keyboard session-streak group.
Like every streak badge it uses the **longest** run ever, so a lapse never re-locks
an earned badge; the progress bar reflects the best run so far.

| Key | Icon | Title | Criteria | Progress |
|---|---|---|---|---|
| `mood_3pd_1` | 🌼 | Devoted Day | 1 qualifying day (≥ 3 logs in a day) | devotionStreak / 1 |
| `mood_3pd_3` | 💪 | Engaged | 3 consecutive qualifying days | devotionStreak / 3 |
| `mood_3pd_7` | 🧗 | Devoted Week | 7 consecutive qualifying days | devotionStreak / 7 |
| `mood_3pd_15` | 🏵️ | Steadfast | 15 consecutive qualifying days | devotionStreak / 15 |
| `mood_3pd_30` | 💎 | Mood Maven | 30 consecutive qualifying days | devotionStreak / 30 |
| `mood_3pd_60` | 🌠 | Mood Sovereign | 60 consecutive qualifying days | devotionStreak / 60 |

> **Recent-days strip (UI).** The Daily-devotion group's focused card renders a
> "mini-keyboard" day strip — the **last 14 calendar days** as small key-like cells,
> each filled (primary) if it was a qualifying day (≥ 3 logs) or dim if it wasn't.
> It makes the consecutive-streak rule legible: the user sees exactly where a run
> broke. Needs a `recentDayQualified: List<Boolean>` (newest-last) for the last 14
> local days, derived in Kotlin from the same per-day counts — no extra query.

## 5. Keyboard — Keystroke volume  (cumulative `keystrokeCount` across all sessions)

| Key | Icon | Title | Criteria | Progress |
|---|---|---|---|---|
| `kb_keys_1k` | 🔤 | Warming Up | ≥ 1,000 keystrokes | total / 1 000 |
| `kb_keys_10k` | 📝 | Wordsmith | ≥ 10,000 keystrokes | total / 10 000 |
| `kb_keys_100k` | 📚 | Novelist | ≥ 100,000 keystrokes | total / 100 000 |
| `kb_keys_500k` | 🖋️ | Prolific | ≥ 500,000 keystrokes | total / 500 000 |
| `kb_keys_1m` | ⌨️ | Millionaire | ≥ 1,000,000 keystrokes | total / 1 000 000 |
| `kb_keys_5m` | 🗿 | Monolith | ≥ 5,000,000 keystrokes | total / 5 000 000 |

## 6. Keyboard — Sessions  · ⏸️ DEFERRED (not in v1)  (total rows in `sessions`)

| Key | Icon | Title | Criteria | Progress |
|---|---|---|---|---|
| `kb_sess_1` | 👋 | Hello, World | ≥ 1 session | count / 1 |
| `kb_sess_10` | 🔟 | Getting Going | ≥ 10 sessions | count / 10 |
| `kb_sess_100` | 💼 | Regular | ≥ 100 sessions | count / 100 |
| `kb_sess_500` | 🏛️ | Veteran | ≥ 500 sessions | count / 500 |
| `kb_sess_1000` | 🎖️ | Thousand Sessions | ≥ 1000 sessions | count / 1000 |

## 7. Keyboard — Session streak  (longest run of consecutive calendar days with ≥ 1 session)

*The single surviving "daily streak" group — merged from the old mood-streak +
keyboard-streak duplicates, keeping the keyboard-streak icon set per owner preference.
Purely keyboard usage: the user only has to open the keyboard on consecutive days.*

| Key | Icon | Title | Criteria | Progress |
|---|---|---|---|---|
| `kb_streak_3` | 🔥 | Spark | 3-day typing streak | longestStreak / 3 |
| `kb_streak_7` | 🔥 | Week Streak | 7-day typing streak | longestStreak / 7 |
| `kb_streak_14` | 🔥 | Two-Week Burn | 14-day typing streak | longestStreak / 14 |
| `kb_streak_30` | 🏆 | Month Streak | 30-day typing streak | longestStreak / 30 |
| `kb_streak_60` | 🏅 | Iron Habit | 60-day typing streak | longestStreak / 60 |
| `kb_streak_100` | 💯 | Century Streak | 100-day typing streak | longestStreak / 100 |
| `kb_streak_365` | 👑 | Year-Long | 365-day typing streak | longestStreak / 365 |

---

## Snapshot fields the evaluator needs

All derivable from existing tables in ≤ ~6 small SQL queries:

| Snapshot field | Source query | Used by |
|---|---|---|
| `moodCount` | `SELECT COUNT(*) FROM mood_entries` | Group 1 |
| `moodCountByScore[1..6]` | `SELECT mood_score, COUNT(*) FROM mood_entries GROUP BY mood_score` | Group 2 — ⏸️ deferred with the group; not queried in v1 |
| `moodEntriesOrdered` (timestamp) | `SELECT timestamp FROM mood_entries ORDER BY timestamp` (single pass; ≤ a few thousand rows) | Groups 3 & 4 (per-day counts). *(Also group 2's Rainbow Week / Returner — deferred.)* |
| `perDayLogCounts` → `bestDayLogs`, `devotionStreak`, `recentDayQualified` | **derived in Kotlin** from `moodEntriesOrdered` — bucket by local ISO day. `bestDayLogs` = max per-day count (group 3); `devotionStreak` = longest run of consecutive days with count ≥ 3, via `computeLongestStreak` over the qualifying-day list (group 4); `recentDayQualified` = last-14-days `List<Boolean>` of "≥ 3 that day" (group 4 strip UI). No extra query | Groups 3, 4 |
| `sessionCount` | `SELECT COUNT(*) FROM sessions` | Group 6 — ⏸️ deferred with the group; not queried in v1 |
| `sessionDays` (sorted ISO days) | `SELECT DISTINCT date(started_at/1000,'unixepoch','localtime') FROM sessions ORDER BY 1` | Group 7 |
| `keystrokeTotal` | `SELECT SUM(CASE WHEN event_category!='AUTOCORRECT' THEN 1 ELSE 0 END) FROM ikd_events` | Group 5 |

`distinctScores` and `min(perScoreCount)` are derived in Kotlin from
`moodCountByScore`. `computeLongestStreak(days)` is reused from
`IkdHabitsAggregator` (Phase 9.3) for **both** the keyboard session-streak group (7,
over `sessionDays`) **and** the mood daily-devotion group (4, over the qualifying-day
list) — the same pure, already-unit-tested function.

No per-session WPM/error-rate query, no session start-hour query, no session-duration
query — those existed only for the dropped Speed/Precision/Circadian/Habit groups.

---

## Notes for the owner

- **37 badges, 7 groups.** Reshaped from the 55/11 draft after the 2026-05-17 reviews:
  dropped Speed / Precision / Circadian / Habit/Combined (behaviour-shaping); dropped
  the per-emotion group; merged the two redundant daily-streak groups into one
  keyboard-session streak (group 7, keeping its icons); split the in-day idea into
  **group 3** (lightweight 1 / 2 / 3+ daily check-in, cumulative best-day) and
  **group 4** (the strict "3+ per day on **consecutive** days" devotion streak).
- **Group 4 = strict consecutive streak (resolved 2026-05-17).** A missed day resets
  the run. Cumulative "total days" rewards are deliberately *not* here — the volume
  groups already cover accumulation; group 4 is the hard, habit-forming one. Uses
  `computeLongestStreak` over the qualifying-day list, so it behaves exactly like the
  keyboard session-streak group (longest run ever; never re-locks).
- **Highest tiers** (`mood_vol_1000`, `kb_keys_5m`, `kb_streak_365`, `mood_3pd_60`)
  are intentionally aspirational — they keep a "next goal" visible on the progress
  bars indefinitely.
- **Icon collisions** within a tiered family (`🔥` across `kb_streak_3/7/14`) are
  intentional — identity is the `badge_key`, the emoji is decorative; a tiered family
  reads as a set. `📝` is used by both `mood_day_1` and `kb_keys_10k` (different
  groups, different context) — acceptable; swap `mood_day_1` to `🗒️` if a globally
  unique-icon rule is preferred.
- **Privacy unchanged:** badges are derived from existing `mood_entries` / `sessions`
  / `ikd_events`. No new captured data, no CSV column, no `badges` block in the export.
- **No negative/shadow badges**, no per-badge sharing, no reset surface — unchanged
  from the parent plan's §10 principles.
