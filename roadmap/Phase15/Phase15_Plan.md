# Phase 15 — Insights Information-Architecture v2 (drop Habits, re-order tabs, consolidate widgets)

**Status:** Planned (created 2026-05-17, owner directive)
**Depends on:** Phase 9 (current 5-tab dashboard), Phase 14 (adds the **Achievements** tab — the new order interleaves it). **Land Phase 15 together with, or immediately after, Phase 14** — the two share the `DashboardPagerAdapter` tab constants.
**Branch:** lands directly on `main`, small focused commits, per recent project hygiene.
**Scope (one sentence):** Restructure the global Insights dashboard — drop the **Habits** tab, relocate its surviving charts, remove three widgets, rename one, and set the final tab order to **Summary · Achievements · Activity · Trends · Keys**.

> **Why.** After Phase 14 the dashboard would be 6 tabs (Summary · Trends · Daily Activity · Keystroke Dynamics · Habits · Achievements). The owner wants a leaner, re-prioritised IA: Achievements promoted to second, Habits dissolved into the tabs it overlaps, and a few low-signal widgets removed. Read-side only — **no schema change, no capture-path reopen, no new aggregator** (`IkdDatabase.version` stays at 4 after Phase 14, or 3 if Phase 15 lands first).

---

## 1. Final tab order

| Idx | Tab | Was | Change |
|---|---|---|---|
| 0 | **Summary** | 0 | unchanged (gains the Phase 14 "Badges in progress" widget) |
| 1 | **Achievements** | (Phase 14 new, 6th) | **moved to index 1** |
| 2 | **Activity** | "Daily Activity" (idx 2) | kept; loses Calendar heatmap; circadian card renamed |
| 3 | **Trends** | idx 1 | gains 2 relocated Habits charts |
| 4 | **Keys** ("Keystroke Dynamics") | idx 3 | gains Avg flight time |
| — | ~~Habits~~ (idx 4) | idx 4 | **removed entirely** |

`DashboardPagerAdapter`: `TAB_COUNT = 5`, constants
`TAB_SUMMARY=0 · TAB_ACHIEVEMENTS=1 · TAB_ACTIVITY=2 · TAB_TRENDS=3 · TAB_KEYS=4`.
This **supersedes** Phase 14's interim `TAB_ACHIEVEMENTS=5 / TAB_COUNT=6`; Phase 14's
`createFragment` branch + tab title/icon arrays are authored against this final order
when the two land together.

---

## 2. Widget disposition (the 5 Habits widgets + 2 other removals + 1 rename)

| Widget | String key | Disposition |
|---|---|---|
| Avg session duration (Habits) | `info_habits_session_duration_*` | **Move → Trends** |
| Avg error rate (Habits) | `info_habits_error_rate_*` | **Move → Trends** — ⚠️ Trends already has an error-rate trend (`info_trends_error_rate_*`). **Open point (§6):** confirm whether to keep both or drop the duplicate. |
| Avg flight time (Habits) | `info_habits_flight_*` | **Move → Keys** (owner directive) |
| Sessions per period ("Sessions per Bucket") | `info_habits_sessions_*` | **Remove** |
| Activity quality scatter (Habits, Phase 9.10) | `info_habits_quality_*` | **Remove** |
| Calendar heatmap (Daily Activity, Phase 9.5) | `info_daily_calendar_heatmap_*` | **Remove** |
| Circadian heatmap card title "When you type" | `dashboard_chart_circadian_title` | **Rename** value `When you type` → `Circadian heatmap` (the hour×weekday heatmap card; widget-info already says "Circadian heatmap" — this aligns the on-card title) |

The Habits 4-cell KPI strip (incl. longest-streak, Phase 9.3) is **removed with the
tab**. Surfacing longest-streak elsewhere is **out of scope** (note in §6).

After this, the **Activity** tab = Daily keypress bar · Hourly distribution (24 h) ·
Circadian heatmap · Usage map. **Trends** = WPM · IKD · error-rate · gyro · accel ·
**+ Avg session duration** (+ Avg error rate pending §6). **Keys** = IKD/Dwell/Flight
distribution histograms · Orientation breakdown · **+ Avg flight time**.

---

## 3. Branch & Layering Discipline

Read-side / presentation only. **No schema, no DAO, no aggregator, no capture-path,
no chart-view edits.** Existing aggregators stay intact even where their output is no
longer surfaced (harmless, read-side; optional dead-code cleanup deferred — §6).

### Reopened files

| File | Why |
|---|---|
| `activities/dashboard/DashboardPagerAdapter.kt` | New tab set/order/constants (with Phase 14's Achievements branch) |
| `activities/DashboardActivity.kt` | Tab title/icon arrays, `TabLayoutMediator`, `STATE_TAB_INDEX` default → new order; `EXTRA_OPEN_TAB`/Phase-14 deep-link target index updated to `TAB_ACHIEVEMENTS=1` |
| `activities/dashboard/TrendsFragment.kt` + `res/layout/fragment_dashboard_trends.xml` | Add the relocated Avg session duration (+ Avg error rate pending §6) chart card(s), reusing the existing `IkdLineChartView` + `IkdHabitsAggregator` outputs already in the payload |
| `activities/dashboard/KeystrokeDynamicsFragment.kt` + its layout | Add the relocated Avg flight time chart card |
| `activities/dashboard/DailyActivityFragment.kt` + its layout | Remove the Calendar heatmap card; keep the rest |
| `res/values/strings.xml` | Rename `dashboard_chart_circadian_title`; remove/repurpose `dashboard_section_habits`; tab-label array no longer references Habits |
| `res/values/strings_widget_info.xml` | The moved widgets keep their existing `info_habits_*` / `info_daily_*` info entries (or are re-keyed to `info_trends_*`/`info_kd_*` — implementer's choice; keep it simple, reuse keys). Remove the info entries for the 3 removed widgets |

### Deleted files

| File | Why |
|---|---|
| `activities/dashboard/HabitsFragment.kt` | Tab removed |
| `res/layout/fragment_dashboard_habits.xml` | Tab removed |
| `res/drawable/ic_dashboard_habits_vector.xml` (if a dedicated tab icon exists) | Tab removed |

### Still forbidden

`IkdHabitsAggregator.kt`, `IkdQualityAggregator.kt`, `IkdActivityAggregator.kt` and
every other aggregator (read pipeline frozen); all chart `views/Ikd*View.kt`; the
capture path; all entities/DAOs; `IkdCsvWriter`; the mood bar / keyboard. Phase 15 is
a fragment-and-string refactor only — it **re-arranges** what the frozen aggregators
already emit, it does not change what they compute.

### Branch hygiene — three focused commits

1. Tab IA: `DashboardPagerAdapter` + `DashboardActivity` new order/constants, delete `HabitsFragment` + layout + icon, drop the Habits tab label/section strings. (Co-authored with Phase 14's Achievements branch if they land together.)
2. Widget moves: Avg session duration (+ Avg error rate per §6) → Trends; Avg flight time → Keys; remove Calendar heatmap, Sessions-per-period, Activity-quality cards + their widget-info entries.
3. Rename + docs: `dashboard_chart_circadian_title` → "Circadian heatmap"; this plan, [`STATUS.md`](../STATUS.md) row, `CLAUDE.md` Phase 9 note amendment, mockup/journey touch-ups.

---

## 4. Privacy / data

No change. Pure presentation reshuffle — no new query, no new captured field, no CSV
change, no schema bump. Removed widgets simply stop being rendered; their underlying
rows are still captured exactly as before.

---

## 5. Verification

1. Clean `assembleCoreDebug`; lint/detekt flat or net-negative (a tab + fragment + 3 cards removed).
2. On device: Insights opens on **Summary**; tab strip reads **Summary · Achievements · Activity · Trends · Keys** in that order; **no Habits tab**.
3. **Trends** shows Avg session duration (+ Avg error rate per §6) below the existing 5 charts; **Keys** shows Avg flight time; **Activity** has **no Calendar heatmap**; the hour×weekday card title reads **"Circadian heatmap"** (not "When you type").
4. **Sessions-per-period** and **Activity-quality scatter** are gone everywhere.
5. Phase 14 deep-links (snackbar/notification "VIEW") land on the Achievements tab at the new index 1; `onSaveInstanceState` tab restore still works.
6. Empty-state + range/mood filter behaviour on the surviving tabs unchanged.

---

## 6. Open points / out of scope

- **⚠️ Error-rate duplication (needs owner call).** Moving Habits' "Avg error rate"
  into Trends puts it next to the existing Trends error-rate trend. They are the same
  metric family. **Default assumption unless told otherwise: do NOT add a second
  error-rate chart to Trends — keep only the existing Trends error-rate, move only
  "Avg session duration".** Confirm if you'd rather keep both.
- **Longest-streak KPI** (Phase 9.3, in the removed Habits KPI strip) is dropped with
  the tab. Re-homing it (e.g., a Summary tile, or the Phase 14 Session-streak group
  already shows it as a badge) is out of scope here.
- **Dead aggregator output.** `IkdQualityAggregator` and the calendar-heatmap branch
  of `IkdActivityAggregator` / Sessions-per-period of `IkdHabitsAggregator` stop
  being surfaced. Aggregators are left intact (read-side, frozen, harmless). Pruning
  them is a separate optional cleanup.
- **Phase 14 ↔ 15 ordering.** Both touch `DashboardPagerAdapter`. Implement together
  (preferred) or Phase 14 first then 15; if 14 ships alone first it temporarily uses
  6 tabs and Phase 15 collapses to 5.
