# Phase 6 — Diagnostics Screen Improvements (Rework)

**Status:** Planned (rework of shipped commit `797e8239`)
**Depends on:** Phase 2 (`ikd.db` populated), Phase 3 (`IkdLineChartView` + dashboard style language), Phase 4 (`IkdSessionStatsLoader`), Phase 5 (`SessionDao.getMostRecentSession()` already shipped, session dashboard style language)
**Branch:** `feat/phase6-rework` — cut from `main` after the original Phase 6 commit landed.
**Scope (one sentence):** Re-deliver Phase 6's diagnostics screen polish so it integrates cleanly with Fossify's runtime theming system (background, text, primary, status colours), matches the Phase 5 session dashboard's KPI-card visual language, and preserves persistent UI state — without touching any keyboard-layer or read-pipeline code.

The original Phase 6 commit (`797e8239`) shipped the right *structural* changes — status chip, two metric cards, collapsible sensor card, Session Insights toolbar action, and an additive `SessionDao.getMostRecentSession()` query. It did not finish the *polish*: status-chip colours were hardcoded RGB literals, `MaterialCardView` backgrounds were never tinted with `getProperBackgroundColor()` (so they stand out as unthemed slabs on custom themes), the count-metrics card had no section label, the "View Event Log" row used the legacy settings-row style instead of a card, paddings were inline `12dp` / `4dp` literals, the sensor-card chevron snapped instead of animating, and the collapse state was not persisted across re-entry. Phase 6 rework fixes all of those without re-doing any of the structural work.

---

## Table of Contents

1. [Concept & Scope](#1-concept--scope)
2. [Branch & Layering Discipline](#2-branch--layering-discipline)
3. [What's Reworked (and What Stays)](#3-whats-reworked-and-what-stays)
4. [Theming Strategy](#4-theming-strategy)
5. [Sub-Phases (5)](#5-sub-phases-5)
   - [6.1 Theme-Aware Status Chip](#61-theme-aware-status-chip)
   - [6.2 Card Backgrounds + Section Labels](#62-card-backgrounds--section-labels)
   - [6.3 Persisted Sensor Card Collapse + Animated Chevron](#63-persisted-sensor-card-collapse--animated-chevron)
   - [6.4 KPI-Style Metric Layout](#64-kpi-style-metric-layout)
   - [6.5 Polish: Inline Paddings, Docs, Acceptance](#65-polish-inline-paddings-docs-acceptance)
6. [Files to Create / Modify (vs. forbidden)](#6-files-to-create--modify-vs-forbidden)
7. [Acceptance Criteria](#7-acceptance-criteria)
8. [Decisions](#8-decisions)
9. [Explicitly Deferred to Later Phases](#9-explicitly-deferred-to-later-phases)

---

## 1. Concept & Scope

The user opens the diagnostics screen on a **custom Fossify theme** (e.g. dark grey background, accent purple, off-white text). Today the screen still shows white `MaterialCardView` slabs (because their `?attr/colorSurface` does not track Fossify's runtime background), the status chip flashes as one of three hardcoded RGB constants (so on the amber state the white text fails contrast and on dark themes the grey "no data" chip nearly disappears), and the sensor card collapse state is lost the moment the user navigates away. Phase 6 rework makes this screen feel like it belongs to the same visual family as the Phase 5 session dashboard — same KPI strip vocabulary, same theme-aware card tinting, same persisted UI state.

### In scope

- Theme-aware status chip: state colours live in `res/values/colors.xml` (with a `res/values-night/colors.xml` counterpart) instead of hardcoded `Color.parseColor("#…")` strings; the base drawable uses `?attr/colorControlNormal` (or a similar theme attribute) instead of `#9E9E9E`; chip text colour adapts per state instead of being locked to `@android:color/white`.
- Every `MaterialCardView` on the screen is tinted via `setCardBackgroundColor(getProperBackgroundColor())` in `applyThemeColors()`, mirroring `EventFeedActivity.applyThemeColors()` (Phase 5).
- The count-metrics card gains a section label styled identically to the timing card's label, both tinted with `getProperPrimaryColor()`.
- The "View Event Log" row is wrapped in a `MaterialCardView` so it shares the screen's overall card aesthetic.
- KPI-style metric layout (3-cell horizontal row per card, big value + small label) replacing the label/value rows — matches the Phase 5 session dashboard's KPI strip visual.
- Inline `12dp` / `4dp` paddings on the status chip become `@dimen` references (`small_margin`, etc.).
- Sensor-card chevron animates (`animate().rotation(...).setDuration(150).start()`).
- Sensor-card collapse state persists via a new `Config.diagnosticsSensorCardExpanded` boolean (default `true`), backed by `DIAGNOSTICS_SENSOR_CARD_EXPANDED` in `Constants.kt`.

### Explicitly preserved (out of scope, deliberately)

- **Live Session Insights Shortcut** (the toolbar action that opens `EventFeedActivity` in live or DB-backed mode based on whether the keyboard is open). This was the highest-value piece of `797e8239` and the rework keeps it verbatim, including the additive `SessionDao.getMostRecentSession()` query and the empty-DB toast.
- **The two metric cards (timing / count) and the collapsible sensor card structure.** The shapes are right; only their *theming* and *layout polish* are reworked.
- **Sensor-toggle menu action and per-axis / magnitude container flip.** Phase 4 surface, untouched.
- **CSV export.** Phase 1.1/2 contract, untouched.
- **`SessionDao.getMostRecentSession()`.** Already merged in `797e8239`; kept.
- **`ic_insights_vector.xml` and `ic_expand_vector.xml`.** Both already use `?attr/colorControlNormal`; kept verbatim.

### Out of scope (deferred — see Section 9)

Multi-series sensor charts, per-event drill-down, "compare to baseline" overlays, live-mode chartification, mood overlay row, schema migration, anomaly markers, IkdDataChangedBus, autocorrect capture, app-context capture.

---

## 2. Branch & Layering Discipline

Phase 6 rework is **independent of the keyboard layer** (capture, storage, retention) and the read pipeline (aggregator, stats loader, chart loader, chart view). All work is on the diagnostics activity, its layout, its drawables, and one additive `Config` boolean.

### Forbidden edits (do not modify in this phase)

| File | Reason |
|---|---|
| `services/SimpleKeyboardIME.kt` | Capture path — frozen since Phase 3 |
| `views/MyKeyboardView.kt` | Keyboard UI — frozen |
| `helpers/LiveCaptureSessionStore.kt` | Capture buffer — frozen |
| `helpers/KinematicSensorHelper.kt` | Capture path — frozen |
| `helpers/IkdRetentionWorker.kt` | Background worker — frozen |
| `databases/IkdDatabase.kt` | No schema migration this phase (no version bump) |
| Any Room `@Entity` (`IkdEvent`, `SensorSample`, `SessionRecord`) | No schema change |
| `databases/ClipsDatabase.kt`, `interfaces/ClipsDao.kt` | Out of scope |
| `helpers/IkdAggregator.kt` | Phase 3 read surface — frozen |
| `helpers/IkdSessionStatsLoader.kt` | Phase 4 read surface — frozen |
| `helpers/IkdSessionChartLoader.kt` | Phase 5 read surface — frozen |
| `helpers/IkdCsvWriter.kt` | CSV format is the experiment's data contract — no shape change |
| `views/IkdLineChartView.kt` | Phase 3/5 chart wrapper — frozen at single-series |
| `activities/EventFeedActivity.kt`, `res/layout/activity_event_feed.xml` | Phase 5 session-dashboard surface — frozen |
| `activities/DashboardActivity.kt`, `res/layout/activity_dashboard.xml` | Phase 3 aggregate-dashboard surface — frozen |
| `activities/SessionsListActivity.kt`, `adapters/SessionsAdapter.kt` | Phase 2 surface — frozen |
| `interfaces/SessionDao.kt` | `getMostRecentSession()` already merged in `797e8239`; no further additions this rework |

### Allowed edits

| File | Allowed change |
|---|---|
| `activities/DiagnosticsActivity.kt` | Reworked theming, animated chevron, persisted collapse, KPI bindings — no behavioural change to capture/export/menu actions |
| `res/layout/activity_diagnostics.xml` | KPI-style 3-cell rows in each metric card; section label on count card; "View Event Log" wrapped in `MaterialCardView`; inline paddings replaced with `@dimen` references |
| `res/drawable/bg_status_chip.xml` | Solid colour switched to `?attr/colorControlNormal` so the runtime tint via `backgroundTintList` rides on a theme-attr base instead of a hardcoded grey |
| `res/values/colors.xml` | **Add** `ikd_status_active`, `ikd_status_idle`, `ikd_status_privacy`, `ikd_status_no_data`, plus matching `ikd_status_*_text` foreground colours |
| `res/values-night/colors.xml` | **New file** — dark-mode counterparts so the chip remains legible on dark themes |
| `res/values/strings.xml` | **Add** `diagnostics_count_metrics` (count-card section label); **add** `diagnostics_kpi_*` labels for the KPI cells; existing strings kept |
| `helpers/Constants.kt` | **Add** `DIAGNOSTICS_SENSOR_CARD_EXPANDED` const |
| `helpers/Config.kt` | **Add** `var diagnosticsSensorCardExpanded: Boolean` (default `true`) |
| `CLAUDE.md` | **Add** Phase 6 section matching the Phase 5 section's depth |
| `roadmap/FeatureRoadmap.md` | Update Phase 6 status to "Implemented (reworked)"; link to `roadmap/Phase6/Phase6_Plan.md` |
| `roadmap/Phase6/Phase6_Plan.md` | **New file** — this document |

### Branch hygiene

- Branch name: `feat/phase6-rework`
- Cut from latest `main` (which already contains `797e8239`).
- Each sub-phase (6.1 → 6.5) lands as one focused commit using the project's `feat:` / `refactor:` / `chore:` / `docs:` convention.
- After 6.5 passes acceptance, the branch is left local for user review — **no push, no PR opened by the implementer.**

---

## 3. What's Reworked (and What Stays)

### Visual hierarchy stays

```
[ Typing area ]
[ Status chip ]
[ Timing card    : IKD / Dwell / Flight ]
[ Count card     : Events / Speed / Error rate ]
[ Sensor card    : (collapsible) Gyro + Accel ]
[ View Event Log : "Event log → 42 events" ]
```

This sequence was correct in `797e8239`. Phase 6 rework keeps it.

### What's reworked, top to bottom

1. **Status chip** — base drawable becomes theme-attr-driven (`?attr/colorControlNormal`); state tints (`ikd_status_active` / `_idle` / `_privacy` / `_no_data`) live in `colors.xml` with a `values-night/` counterpart; chip text colour matches the chip-background luminance instead of being locked to white; inline `12dp` / `4dp` paddings become `@dimen` references.
2. **Timing card and Count card** — wrapped in `MaterialCardView`s already; rework restyles their innards as a 3-cell horizontal KPI row (`bigger_text_size` value + `smaller_text_size` label, identical to Phase 5's KPI strip cells). The Count card gains a section label tinted with `getProperPrimaryColor()`.
3. **Sensor card** — header chevron animates (`.animate().rotation(...).setDuration(150)`); collapse state persists via `Config.diagnosticsSensorCardExpanded`. Collapsed content (gyro + accel sensor bars) stays inflated; only its visibility is toggled.
4. **View Event Log row** — wrapped in a `MaterialCardView` with rounded corners + small elevation; tinted via `setCardBackgroundColor(getProperBackgroundColor())` in `applyThemeColors()`. Existing label + count behaviour preserved.
5. **`applyThemeColors()`** — extended to also tint each `MaterialCardView`'s background via `setCardBackgroundColor(getProperBackgroundColor())`, mirroring `EventFeedActivity.applyThemeColors()` lines 213-230 (the Phase 5 pattern).

### What stays (verbatim)

- Toolbar (`save_csv`, `session_insights`, `toggle_sensor_view`).
- Status-refresh `Handler` polling every 250 ms.
- `LiveCaptureSessionStore` listener registration in `onResume`.
- `KinematicSensorHelper` setup, gyro-visibility branch, sensor magnitude/axes flip.
- Live derived metrics (KPM and error rate) computed from the in-memory store.
- CSV export launcher.
- Session Insights navigation (live mode if capturing, most-recent-session DB fetch otherwise, toast if DB empty).

---

## 4. Theming Strategy

The user feedback "do not forget keyboard theme, etc." points at Fossify-commons' runtime theming system. Fossify themes write three core colours into `BaseConfig` at runtime: a *background* colour (used for the app body), a *text* colour, and a *primary* colour (used for accents). The activity reads these via `getProperBackgroundColor()`, `getProperTextColor()`, and `getProperPrimaryColor()` — Phase 5 already follows this pattern in `EventFeedActivity.applyThemeColors()`.

The original Phase 6 commit only called `updateTextColors(...)` (which fixes text but not card backgrounds) and tinted four section labels with `getProperPrimaryColor()`. The four `MaterialCardView`s were left to inherit Material's default `?attr/colorSurface` — which on a dark Fossify theme renders as a near-white slab. The rework adds the missing `setCardBackgroundColor(getProperBackgroundColor())` calls in `applyThemeColors()`, matching `EventFeedActivity` line-for-line.

For the **status chip**, the four states need brand-stable colours that don't fight the activity's background but also don't fade into it. The rework defines:

| State | Light-mode colour | Light-mode text | Dark-mode colour | Dark-mode text |
|---|---|---|---|---|
| Active (capturing) | green `#2E7D32` | white | `#66BB6A` | black |
| Idle (stopped, has data) | grey `#9E9E9E` | white | `#BDBDBD` | black |
| Privacy on | amber `#F57F17` | black | `#FFB300` | black |
| No data | grey `#9E9E9E` | white | `#757575` | white |

These are picked once, declared in `res/values/colors.xml` and `res/values-night/colors.xml`, and resolved in Kotlin via `ContextCompat.getColor(this, R.color.ikd_status_active)`. The base `bg_status_chip.xml` drawable's solid colour is `?attr/colorControlNormal` (so a chip that loses its `backgroundTintList` for any reason still renders something theme-appropriate, never a hardcoded `#9E9E9E`).

For the **chip text colour**, the rework computes per state in Kotlin (`R.color.ikd_status_active_text` etc.) instead of hardcoding `@android:color/white` in the layout. The amber state in particular fails contrast against white text; black text is the right choice there.

For **all card backgrounds**, the rework calls `setCardBackgroundColor(getProperBackgroundColor())` once per card in `applyThemeColors()`. Card silhouettes are demarcated by `cardElevation` (the existing `@dimen/card_elevation = 2dp`), exactly as on the Phase 5 session dashboard.

For **section labels**, the rework tints both `diagnostics_timing_section_label` *and* the new `diagnostics_count_section_label` with `getProperPrimaryColor()` in `applyThemeColors()`. The sensor-readings, gyro, and accel labels were already tinted; nothing changes for them.

---

## 5. Sub-Phases (5)

Five sub-phases. Each compiles and ships. Each lands as one focused commit.

---

### 6.1 Theme-Aware Status Chip

**Goal:** The status chip's three colours are no longer hardcoded `Color.parseColor("#…")` strings; the chip text colour matches its background's luminance per state; the base drawable does not contain a hardcoded grey.

#### Deliverables

- `res/drawable/bg_status_chip.xml` — solid colour switched from `#9E9E9E` to `?attr/colorControlNormal`.
- `res/values/colors.xml` — add `ikd_status_active`, `ikd_status_idle`, `ikd_status_privacy`, `ikd_status_no_data`, and matching `ikd_status_*_text` foreground colours (Section 4 table).
- `res/values-night/colors.xml` — new file; dark-mode counterparts for the eight colours above.
- `res/layout/activity_diagnostics.xml` — drop `android:textColor="@android:color/white"` from the status chip TextView (text colour is now set in code per state).
- `activities/DiagnosticsActivity.kt`:
  - `updateStatusDisplay()` no longer calls `Color.parseColor(...)`. Each state resolves its background and text colour via `ContextCompat.getColor(this, R.color.ikd_status_…)`.
  - The chip's `backgroundTintList` is set from the background colour; the chip's `setTextColor(...)` is set from the text colour.
- Imports: drop `android.graphics.Color`; add `androidx.core.content.ContextCompat`.

#### Acceptance

- `grep "Color.parseColor" app/src/main/kotlin/org/fossify/keyboard/activities/DiagnosticsActivity.kt` returns zero matches.
- `grep "#9E9E9E" app/src/main/res/drawable/bg_status_chip.xml` returns zero matches.
- `grep '@android:color/white' app/src/main/res/layout/activity_diagnostics.xml` returns zero matches.
- The chip is legible (passes WCAG AA against its own background) on the four states in both light and dark mode.

#### Commit

`refactor(diagnostics): theme-aware status chip with state colors`

---

### 6.2 Card Backgrounds + Section Labels

**Goal:** Every `MaterialCardView` on the diagnostics screen tracks Fossify's runtime background, and the count-metrics card has the same section label treatment as the timing card.

#### Deliverables

- `res/values/strings.xml` — add `diagnostics_count_metrics` ("Volume Metrics" or "Count Metrics" — Decision #5).
- `res/layout/activity_diagnostics.xml`:
  - Add a `<TextView ... android:id="@+id/diagnostics_count_section_label" style="@style/SettingsSectionLabelStyle" ... android:text="@string/diagnostics_count_metrics" />` at the top of the count-metrics card's inner `LinearLayout`, mirroring the timing card.
  - Wrap the existing "View Event Log" `ConstraintLayout` in a `MaterialCardView` with `card_corner_radius` + `card_elevation` from Phase 5 dimens. The inner `ConstraintLayout` keeps the existing `SettingsHolderTextViewOneLinerStyle` style for its label/value rows.
- `activities/DiagnosticsActivity.kt`:
  - In `applyThemeColors()`, add `setCardBackgroundColor(getProperBackgroundColor())` calls for every `MaterialCardView` ID — timing card, count card, sensor card, "View Event Log" card.
  - Tint `diagnostics_count_section_label` with `getProperPrimaryColor()` alongside the existing `diagnostics_timing_section_label` line.
- Both card-binding views need explicit `android:id`s on the `MaterialCardView` elements so the activity can grab them via view-binding.

#### Acceptance

- Open the diagnostics screen on the Fossify default theme, then on a custom dark theme — every card's background tracks the theme.
- The count-metrics card has a "Count Metrics" (or chosen wording) header in `getProperPrimaryColor()` matching the timing card.
- The "View Event Log" row sits inside a card with the same corner radius / elevation as the metric cards.
- `git diff` shows no unrelated edits.

#### Commit

`feat(diagnostics): tint cards via getProperBackgroundColor and add count section label`

---

### 6.3 Persisted Sensor Card Collapse + Animated Chevron

**Goal:** The sensor card remembers whether the user has it expanded or collapsed across re-entries, and the chevron animates instead of snapping.

#### Deliverables

- `helpers/Constants.kt` — add `const val DIAGNOSTICS_SENSOR_CARD_EXPANDED = "diagnostics_sensor_card_expanded"` (no namespace prefix to match the existing `RETENTION_DAYS` style).
- `helpers/Config.kt` — add `var diagnosticsSensorCardExpanded: Boolean` (default `true`).
- `activities/DiagnosticsActivity.kt`:
  - Drop the in-memory `var isSensorExpanded = true`. Read the state from `config.diagnosticsSensorCardExpanded` on `onResume` instead.
  - On `toggleSensorCard()`, flip the `Config` flag, then call a single `applySensorExpansion(animate: Boolean)` helper that sets the content visibility and animates the chevron via `binding.diagnosticsSensorReadingsChevron.animate().rotation(targetDeg).setDuration(150).start()` (or sets `rotation` directly when called from `onResume` to avoid the initial paint flicker).
- `applySensorExpansion(animate: Boolean = false)`:
  - target rotation: `0f` for expanded, `-90f` for collapsed (matches the existing convention).
  - content visibility: `View.VISIBLE` / `View.GONE` on `binding.diagnosticsSensorReadingsContent`.

#### Acceptance

- Collapse the sensor card; navigate away (back to settings); re-enter Diagnostics; the card is still collapsed, the chevron is at `-90f`.
- Tapping the header animates the chevron over 150 ms (visually verified).
- `Config.diagnosticsSensorCardExpanded` survives process death (it's a `SharedPreferences` boolean — same semantics as every other `Config` flag).

#### Commit

`feat(diagnostics): persist sensor card collapse and animate chevron`

---

### 6.4 KPI-Style Metric Layout

**Goal:** Both metric cards switch from "label : value" rows to a 3-cell horizontal KPI grid identical to the Phase 5 session dashboard's KPI strip — big bold value on top, small label underneath, three cells per card with `weightSum="3"`.

This sub-phase is the user's "Make it similar in style to the session insights" comment in `FeatureRoadmap.md`. It is the change that makes the screen actually feel "polished" instead of just "tidied".

#### Deliverables

- `res/values/strings.xml` — add `diagnostics_kpi_ikd`, `diagnostics_kpi_dwell`, `diagnostics_kpi_flight`, `diagnostics_kpi_events`, `diagnostics_kpi_speed`, `diagnostics_kpi_error_rate` (short labels: "IKD", "Dwell", "Flight", "Events", "Speed", "Errors"). Existing `diagnostics_*_label` strings (which were the long labels for the row layout) are kept for backward compatibility but no longer referenced.
- `res/layout/activity_diagnostics.xml`:
  - Replace each metric card's three `LinearLayout` (label + value) rows with a single horizontal `LinearLayout` with `weightSum="3"`, three child cells (`layout_weight="1"`, `gravity="center_horizontal"`, `orientation="vertical"`), each containing a value `MyTextView` (`bigger_text_size`, bold) and a label `MyTextView` (`smaller_text_size`).
  - The `android:id` for each value view is preserved (`diagnostics_ikd_value`, `diagnostics_dwell_value`, etc.) so `DiagnosticsActivity.kt`'s `updateTimingDisplay` / `updateComputedMetrics` / `refreshDisplayFromStore` keep working without rewrites.
  - Both cards keep their section labels (`diagnostics_timing_section_label` and the new `diagnostics_count_section_label`).
- No Kotlin change in `DiagnosticsActivity.kt`'s metric-update functions — they only `setText` on the same view IDs.

#### Acceptance

- The two metric cards visually mirror Phase 5's KPI strip (compare side-by-side with `EventFeedActivity` opened on a saved session).
- All six metrics still update in real time as the user types (verified by typing into the diagnostics edit text).
- Values fit horizontally on a 5-inch device without overflow (the longest typical value is "9999 ms" or "100.0%" — well within a third of the screen width).

#### Commit

`feat(diagnostics): KPI-style metric layout matching session dashboard`

---

### 6.5 Polish: Inline Paddings, Docs, Acceptance

**Goal:** Replace the remaining inline `12dp` / `4dp` paddings with `@dimen` references; document Phase 6 in `CLAUDE.md` and `FeatureRoadmap.md`; run `detekt` and `lint`; verify the build.

#### Deliverables

- `res/layout/activity_diagnostics.xml`:
  - Status chip `paddingStart`/`paddingEnd` → `@dimen/normal_margin` (8dp ≈ 12dp; the screen has no other 12dp paddings, so the closest existing dimen wins to keep `dimens.xml` slim).
  - Status chip `paddingTop`/`paddingBottom` → `@dimen/small_margin` (4dp ≈ 4dp).
  - Sensor card chevron `paddingBottom="4dp"` → `@dimen/small_margin`.
- `CLAUDE.md` — add a Phase 6 section in the same shape as Phase 5: scope, files, decisions, what's preserved, perf budget (n/a here — UI-only), forbidden list.
- `roadmap/FeatureRoadmap.md` — change Phase 6 status from `Planned` to `Implemented`; link to `roadmap/Phase6/Phase6_Plan.md`.
- Run `JAVA_HOME="C:\Program Files\Android\Android Studio\jbr" ./gradlew assembleCoreDebug` — must succeed.
- Run `JAVA_HOME="C:\Program Files\Android\Android Studio\jbr" ./gradlew detekt` and `lint` — no new issues vs. `main` baseline.

#### Acceptance

- `grep "12dp\|4dp" res/layout/activity_diagnostics.xml` returns zero matches (or only matches inside comments / unrelated unchanged blocks if any).
- `./gradlew assembleCoreDebug` is green.
- `CLAUDE.md` Phase 6 section exists.
- `FeatureRoadmap.md` Phase 6 status is updated.
- `git log main..feat/phase6-rework --oneline` shows clean conventional commits.

#### Commit

`docs: update CLAUDE.md and FeatureRoadmap status for Phase 6 rework`

---

## 6. Files to Create / Modify (vs. forbidden)

### Create (3 files)

| File | Sub-phase |
|---|---|
| `res/values-night/colors.xml` | 6.1 |
| `roadmap/Phase6/Phase6_Plan.md` | (this file — created first commit) |
| (no new `.kt` files) | — |

### Modify (additive only — no existing logic touched)

| File | Sub-phase | Change |
|---|---|---|
| `res/drawable/bg_status_chip.xml` | 6.1 | Solid colour → `?attr/colorControlNormal` |
| `res/values/colors.xml` | 6.1 | Add 8 status-chip colours |
| `res/layout/activity_diagnostics.xml` | 6.1 / 6.2 / 6.4 / 6.5 | Drop hardcoded white, add count-card section label, wrap "View Event Log" in card, KPI-grid layout, dimen references |
| `res/values/strings.xml` | 6.2 / 6.4 | Add `diagnostics_count_metrics`, `diagnostics_kpi_*` |
| `helpers/Constants.kt` | 6.3 | Add `DIAGNOSTICS_SENSOR_CARD_EXPANDED` const |
| `helpers/Config.kt` | 6.3 | Add `diagnosticsSensorCardExpanded` property |
| `activities/DiagnosticsActivity.kt` | 6.1 / 6.2 / 6.3 | `updateStatusDisplay` rewrite; `applyThemeColors` extended; `applySensorExpansion(animate)`; persisted collapse |
| `CLAUDE.md` | 6.5 | Phase 6 section |
| `roadmap/FeatureRoadmap.md` | 6.5 | Phase 6 status updated |

### Forbidden (do not touch — see Section 2 for full list)

`SimpleKeyboardIME.kt`, `MyKeyboardView.kt`, `LiveCaptureSessionStore.kt`, `KinematicSensorHelper.kt`, `IkdRetentionWorker.kt`, `IkdDatabase.kt`, all `@Entity` data classes, `ClipsDatabase.kt`, `ClipsDao.kt`, `IkdAggregator.kt`, `IkdSessionStatsLoader.kt`, `IkdSessionChartLoader.kt`, `IkdCsvWriter.kt`, `IkdLineChartView.kt`, `EventFeedActivity.kt`, `activity_event_feed.xml`, `DashboardActivity.kt`, `activity_dashboard.xml`, `SessionsListActivity.kt`, `SessionsAdapter.kt`, `SessionDao.kt`.

---

## 7. Acceptance Criteria

The whole rework is done when **all** of these are green on `feat/phase6-rework`:

- [ ] `./gradlew assembleCoreDebug` succeeds.
- [ ] `./gradlew detekt` and `./gradlew lint` produce no new issues vs. `main` baseline.
- [ ] `IkdDatabase.version` is unchanged (still 1).
- [ ] No file in Section 2's "Forbidden edits" list is modified (verifiable via `git diff main..feat/phase6-rework --name-only`).
- [ ] `grep "Color.parseColor" app/src/main/kotlin/org/fossify/keyboard/activities/DiagnosticsActivity.kt` returns zero matches.
- [ ] `grep "#9E9E9E" app/src/main/res/drawable/bg_status_chip.xml` returns zero matches.
- [ ] `grep '@android:color/white' app/src/main/res/layout/activity_diagnostics.xml` returns zero matches.
- [ ] `applyThemeColors()` calls `setCardBackgroundColor(getProperBackgroundColor())` on every `MaterialCardView` ID.
- [ ] Both metric cards have a section label tinted with `getProperPrimaryColor()`.
- [ ] The "View Event Log" row sits inside a `MaterialCardView` matching the metric cards' style.
- [ ] The metric cards use a 3-cell KPI grid layout (big value + small label).
- [ ] The sensor card's chevron animates on toggle.
- [ ] The sensor card's collapse state survives back-out and re-entry.
- [ ] `roadmap/Phase6/Phase6_Plan.md` exists.
- [ ] `CLAUDE.md` has a Phase 6 section.
- [ ] `roadmap/FeatureRoadmap.md` Phase 6 status is updated to `Implemented` (or `Reworked`).

---

## 8. Decisions

| # | Topic | Decision |
|---|---|---|
| 1 | Branch strategy | All work on `feat/phase6-rework`; left local for the user to review — **no push, no PR opened by the implementer**. |
| 2 | Independence from keyboard / read pipeline | Section 2's "Forbidden edits" list is hard. Same as Phases 3, 4, 5. |
| 3 | Schema migration | None this phase. `IkdDatabase.version` stays at 1. |
| 4 | Chip text colour | Per-state colour resource (`ikd_status_*_text`), not a hardcoded `@android:color/white`. Amber state needs black text (white fails contrast); green/grey states keep white. |
| 5 | Count-card label wording | "Count Metrics" — short, parallel to "Keystroke Timing", reads as a card heading. |
| 6 | KPI grid vs label/value rows | KPI grid (Sub-phase 6.4). Recommended in the rework brief and the only change that makes the screen actually feel like a sibling of the Phase 5 session dashboard. |
| 7 | Persisted collapse storage | `Config.diagnosticsSensorCardExpanded` (`SharedPreferences` boolean), default `true`. Matches every other `Config` flag's lifecycle. Not stored on `savedInstanceState` — that wouldn't persist across re-entry, only across rotation. |
| 8 | Chevron animation duration | 150 ms — matches Material's "subtle expansion" duration; long enough to perceive, short enough to feel snappy. |
| 9 | Status drawable base colour | `?attr/colorControlNormal` — picks up Material's runtime icon colour, so a chip without `backgroundTintList` still renders something theme-appropriate. |
| 10 | Dark-mode chip palette | Lighter, less saturated versions of the light-mode palette so the chip reads as a coloured pill on dark backgrounds without screaming. Black text on amber and green; white text on grey. |
| 11 | Live Session Insights Shortcut | Kept verbatim from `797e8239`. The additive `SessionDao.getMostRecentSession()` is the right API for this; reworking it would be churn. |
| 12 | Inline padding cleanup | Closest existing dimen wins. `12dp` ≈ `normal_margin (8dp)`; `4dp` ≈ `small_margin (4dp)`. Don't introduce new dimens unless Phase 5 didn't already cover the use case. |
| 13 | Live derived metrics formula | Unchanged. KPM and error rate are still computed in `DiagnosticsActivity` from the live store; KPM stays a live-only metric (Phase 4 noted this). |
| 14 | Layout style alignment | All cards / labels / colours mirror Phase 5's `EventFeedActivity` so the user sees the diagnostics screen as a sibling of the session dashboard. |
| 15 | Toolbar action discoverability | "View Event Log" row is kept (per the original `FeatureRoadmap.md` spec) for discoverability; the toolbar Session Insights action is the additional shortcut. |

---

## 9. Explicitly Deferred to Later Phases

These were on the diagnostics-improvement wishlist but are out of scope for the rework:

- A live mini-chart on the diagnostics screen (real-time IKD spark line) — different perf model, defers to live-mode chart-ification work.
- Per-axis sensor sparkline charts (ditto).
- "Compare to baseline" overlays on the live metrics — needs the rolling 14-day baseline already deferred from Phase 3.
- Diagnostics screen battery & temperature readouts — needs new sensor reads and a Phase 2 privacy disclaimer scope reopening.
- Customisable status-chip palette — yagni.
- Long-press → reset-to-default on the sensor card collapse state — yagni.
- Mood overlay on diagnostics (Phase 8 schema work).

Anything from this list earns its own focused mini-plan in `roadmap/Phase{N}/` if and when the user wants it.
