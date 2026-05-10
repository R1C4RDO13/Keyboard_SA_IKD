# Feature Roadmap: Keystroke Dynamics Integration

This roadmap outlines the strategic phases for integrating passive behavioral tracking and sensor analytics into the custom keyboard project. The focus is on securely capturing device interactions and transforming them into meaningful, actionable insights for the user.

## Visual Roadmap Overview

```mermaid
flowchart TD
    classDef phase fill:#f9f9f9,stroke:#333,stroke-width:2px;
    classDef feature fill:#e1f5fe,stroke:#0288d1,stroke-width:1px;

    P1[Phase 1: Sensor Calibration & Debug Environment]:::phase
    P1_1[Phase 1.1: Live Keyboard Data & Metric Alignment]:::phase
    P2[Phase 2: Background Collection & Local Storage]:::phase
    P3[Phase 3: User Insights & Dashboard Presentation]:::phase
    P4[Phase 4: Session Detail Refresh]:::phase
    P5[Phase 5: Session Dashboard]:::phase
    P6[Phase 6: Diagnostics Screen Improvements]:::phase
    P7[Phase 7: Emoji & Autocorrect Capture]:::phase
    P8[Phase 8: Mood Bar & Contextual Overlay]:::phase
    P9[Phase 9: Global Insights Expansion]:::phase
    P10[Phase 10: Rebranding & New Identity]:::phase

    P1 --> P1_1
    P1_1 --> P2
    P2 --> P3
    P3 --> P4
    P4 --> P5
    P5 --> P6
    P6 --> P7
    P7 --> P8
    P8 --> P9
    P9 --> P10

    subgraph Phase 1 Features
        F1[Real-Time Data Interface]:::feature
        F2[Kinematic Sensor Integration]:::feature
        F3[Touch Dynamics Tracking]:::feature
    end

    subgraph Phase 1.1 Features
        F1_1[Live Keyboard Capture]:::feature
        F1_2[Metric Realignment]:::feature
    end

    subgraph Phase 2 Features
        F4[Lifecycle-Aware Activation]:::feature
        F5[Privacy-First Metadata Extraction]:::feature
        F6[Asynchronous Local Storage]:::feature
    end

    subgraph Phase 3 Features
        F7[Data Aggregation Engine]:::feature
        F8[Visual Analytics Modules]:::feature
        F9[Subjective Context Overlay]:::feature
    end

    subgraph Phase 4 Features
        F10[Session Metadata Header]:::feature
        F11[Magnitude-First Sensor Display]:::feature
    end

    subgraph Phase 5 Features
        F12[Per-Session KPI + Metadata Strip]:::feature
        F13[IKD / Gyro / Accel Time-Series Charts]:::feature
        F14[Raw Lists Replaced by Charts]:::feature
    end

    P1 -.-> F1 & F2 & F3
    P1_1 -.-> F1_1 & F1_2
    P2 -.-> F4 & F5 & F6
    P3 -.-> F7 & F8 & F9
    P4 -.-> F10 & F11
    subgraph Phase 6 Features
        F15[Live Session Insights Shortcut]:::feature
        F16[Diagnostics Screen UI Refresh]:::feature
    end

    subgraph Phase 7 Features
        F17[Emoji Palette Capture]:::feature
        F18[Autocorrect / Spell-Check Detection]:::feature
        F19[WPM Formula Correction]:::feature
    end

    subgraph Phase 8 Features
        F20[Mood Bar on Keyboard Toolbar]:::feature
        F21[Mood Overlay on Session Dashboard]:::feature
        F22[Mood Trend Chart on Global Insights]:::feature
    end

    subgraph Phase 9 Features
        F23[Mood Section Formalisation]:::feature
        F24[Gyro & Accel Global Trend Charts]:::feature
        F25[Typing Habits & Session Statistics]:::feature
        F25b[Mood Filter on Insights]:::feature
        F25c[Daily Activity & Calendar Heatmap]:::feature
        F25d[Hourly Activity & Time-of-Day Heatmap]:::feature
        F25e[Keystroke Dynamics Distribution Histograms]:::feature
        F25f[Orientation Breakdown]:::feature
        F25g[Usage Map Bubble Chart]:::feature
        F25h[Activity Quality Scatter]:::feature
    end

    subgraph Phase 10 Features
        F26[New App Name & Identity]:::feature
        F27[Visual Design System]:::feature
        F28[Store Listing & Metadata Refresh]:::feature
    end

    P5 -.-> F12 & F13 & F14
    P6 -.-> F15 & F16
    P7 -.-> F17 & F18 & F19
    P8 -.-> F20 & F21 & F22
    P9 -.-> F23 & F24 & F25 & F25b & F25c & F25d & F25e & F25f & F25g & F25h
    P10 -.-> F26 & F27 & F28
```

---

## Phase 1: Sensor Calibration and Debug Environment
**Status: Complete (2026-05-01)**

**Objective:** Establish a controlled, isolated testing environment within the companion application to verify that all device sensors are capturing data accurately and at the appropriate frequencies before deploying to the live keyboard.

*   **Real-Time Data Interface:** 
    *   Develop a dedicated diagnostic screen where developers and testers can input text and observe live sensor metrics.
*   **Kinematic Sensor Integration:** 
    *   Connect the device’s internal gyroscope and accelerometer. 
    *   Visualize the X, Y, and Z axes in real-time to observe the physical force and device posture changes during typing.
*   **Touch Dynamics Tracking:** 
    *   Implement logic to precisely measure micro-interactions.
    *   Calculate **Dwell Time** (duration a key is depressed) and **Flight Time** (the transition speed between keys).
*   **Data Validation Export:** 
    *   Provide an option to export short diagnostic sessions to a raw text or spreadsheet format to verify data structure and timing accuracy.

---

## Phase 1.1: Live Keyboard Data Validation & Metric Alignment
**Status: Complete (2026-05-01)**

**Objective:** Correct measurement mismatches from Phase 1 and upgrade data capture to the *actual* keyboard interface. Implement the updated metric standards, setting up accurate behavioral signals for the backend.

Detailed scope: [`Phase1/Phase1.1_Plan.md`](Phase1/Phase1.1_Plan.md)

*   **Typing Speed Analysis:** 
    *   Measure true processing speed through the statistical distribution of flight times, revealing various mental states (cognitive fatigue/depression vs. alertness/anxiety).
*   **Flight Time:** 
    *   Calculate the interval between key release and next key press (UP to DOWN). High ranges characterize hesitation, cognitive overload, or distraction, while short ranges suggest urgency or impulsivity.
*   **Inter-Key Delay (IKD):** 
    *   Track the legacy interval between consecutive key releases (UP to UP) to maintain historical comparison and additional rhythm signatures.
*   **Error Rates Tracking:** 
    *   Calculate the frequency of corrections and typos. Monitor for deviations indicative of cognitive stress or severe depression.
*   **Key Hold Time (Dwell Time):** 
    *   Repurpose "Dwell Time" into Key Hold Time (duration a key is pressed) to assess motor function and potential cognitive slowing vs inattention/restlessness.
*   **Contextual Accelerometer & Gyroscope Data:** 
    *   Incorporate smartphone-specific accelerometer data handling to provide background on physical context (walking, standing, vehicle) simultaneously with keystrokes.

---

## Phase 2: Unobtrusive Background Collection & Secure Local Storage
**Status: Complete (2026-05-01)**

Detailed scope: [`Phase2/Phase2_Plan.md`](Phase2/Phase2_Plan.md)

**Objective:** Seamlessly transition the sensor tracking into the live keyboard environment, ensuring data is collected passively without draining the device battery, causing input lag, or compromising user privacy.

*   **Lifecycle-Aware Activation:** 
    *   Configure the sensors to wake up strictly when the keyboard is summoned on-screen and to power down immediately when the keyboard is hidden.
*   **Privacy-First Metadata Extraction:** 
    *   Hook into the keystroke events to capture timing and physical interaction metrics.
    *   Enforce strict privacy filters: log event types (e.g., "Alphanumeric", "Backspace", "Space") and timestamps while permanently discarding the actual linguistic characters inputted.
*   **Asynchronous Local Storage:** 
    *   Establish a secure, on-device local database to store the collected sensor events.
    *   Ensure data processing and saving occur in the background, keeping the user's typing experience fluid and uninterrupted.

---

## Phase 3: User Insights and Dashboard Presentation
**Status: Complete (2026-05-03)**

Detailed scope: [`Phase3/Phase3_Plan.md`](Phase3/Phase3_Plan.md) · step breakdown: [`Phase3/Phase3_Steps.md`](Phase3/Phase3_Steps.md)

**Objective:** Utilize the companion application to translate vast amounts of raw behavioral and kinematic data into digestible, visual insights that empower the user to understand their digital habits and cognitive states.

The shipped Phase 3 deliberately scoped down to the minimum useful dashboard: three line charts (WPM, average IKD, error rate) over Week / Month / All Time, plus a four-number KPI strip. Cognitive Fatigue Heatmap, Circadian Usage Patterns, and the Subjective Context Overlay listed below were deferred to keep scope tight; they remain candidate features for a future phase.

*   **Data Aggregation Engine:**
    *   Create internal processes to summarize the raw data points into daily and weekly averages (e.g., average words per minute, daily backspace frequency, average dwell times). **(Shipped — `helpers/IkdAggregator.kt`, single SQL `GROUP BY` per chart, ≤ ~52 rows per range.)**
*   **Visual Analytics Modules:**
    *   **Typing Rhythm Trends:** A visual graph displaying typing speed and fluidity over time, helping to establish a user baseline and highlight deviations. **(Shipped — three line charts in `DashboardActivity`.)**
    *   **Cognitive Fatigue Heatmap:** Visual representations of error rates and auto-correct reliance to indicate potential moments of low focus or fatigue. **(Deferred.)**
    *   **Circadian Usage Patterns:** Time-of-day visualizations that map when typing sessions occur, specifically flagging late-night usage that may indicate sleep disruption. **(Deferred.)**
*   **Subjective Context Overlay (Optional):**
    *   Implement a simple daily check-in allowing users to log their mood or energy levels.
    *   Overlay this subjective self-reporting onto the objective sensor graphs to help users identify personal behavioral patterns. **(Deferred — needs schema migration for a `mood_entries` table.)**

---

## Phase 4: Session Detail Refresh — Magnitude-First Sensors + Rich Session Metadata
**Status: Implemented**

Detailed scope: [`Phase4/Phase4_Plan.md`](Phase4/Phase4_Plan.md)

**Objective:** Make the per-session detail screen actually useful at a glance — surface the metadata already captured in `sessions` (orientation, locale, started/ended, counts) alongside derived per-session metrics (WPM, error rate, avg IKD / dwell / flight), and replace the per-axis sensor wall-of-numbers with a magnitude-by-default view that can toggle back to X / Y / Z.

Like Phase 3, Phase 4 is a read-side refresh: zero edits to the keyboard / capture layer, no schema migration, no new dependencies.

*   **Session Metadata Header:** **(Shipped — `event_feed_session_header` LinearLayout in `activity_event_feed.xml`, populated from `IkdSessionStatsLoader.load(sessionId)`.)**
    *   New header card on the session detail screen (`EventFeedActivity` when launched with a session ID) showing started / ended / duration, orientation, locale, event and sensor counts, plus the four derived metrics (WPM, error rate, avg IKD, avg dwell, avg flight).
    *   Powered by one additive `IkdEventDao.getSessionStats(sessionId)` query — averages and counts in a single SQL round-trip — joined in Kotlin with the existing `SessionRecord` row.
*   **Magnitude-First Sensor Display:** **(Shipped — toolbar action in both `EventFeedActivity` and `DiagnosticsActivity`, default mode `MAGNITUDE`.)**
    *   Sensor list rows and Diagnostics live bars default to a single magnitude value (`sqrt(x² + y² + z²)`) instead of three per-axis values.
    *   A single toolbar action toggles between `MAGNITUDE` and `AXES` modes; the choice persists via a new `Config.sensorDisplayMode` preference.
    *   Magnitude is derived in Kotlin at read time — never stored in the DB and never added to the CSV export, so the experiment's data contract stays frozen.

> **Phase 4 follow-up identified during review:** the metadata header turned out to be a 12-row label/value strip *above* unchanged raw timing + sensor lists. The raw rows still dominate the screen and the strip didn't read like a "dashboard." The fix is scoped as Phase 5 below.

---

## Phase 5: Session Dashboard — Charts Replace Raw Lists
**Status: Implemented**

Detailed scope: [`Phase5/Phase5_Plan.md`](Phase5/Phase5_Plan.md)

**Objective:** Turn the saved-session detail screen into a real per-session dashboard, parallel to the aggregate `DashboardActivity` from Phase 3 but scoped to a single session. Drop the raw timing/sensor row dump in favour of a compact KPI strip + metadata chip + three line charts (IKD over time, gyro magnitude over time, accelerometer magnitude over time). Live mode (`EventFeedActivity` without `EXTRA_SESSION_ID`, used as the Diagnostics event log) is preserved as-is.

Like Phases 3 and 4, Phase 5 is a read-side refresh: zero edits to the keyboard / capture layer, no schema migration, **no new dependencies** — the same Phase 3 MPAndroidChart wrapper (`views/IkdLineChartView.kt`) is reused verbatim so the per-session and aggregate dashboards belong to the same visual family.

*   **Per-Session KPI + Metadata Strip:**
    *   Compact 4-cell KPI card at the top of the session detail (Events · Total typing time · WPM · Error rate), styled identically to `DashboardActivity`'s strip
    *   Secondary chip row beneath with avg IKD / avg dwell / avg flight
    *   Single-line metadata chip (Started at · Duration · Orientation · Locale) — sentinel/empty values omitted from the line rather than rendered as "—"
    *   The existing Phase 4 `IkdSessionStatsLoader` (frozen) supplies all four KPI values
*   **IKD / Gyro / Accel Time-Series Charts:**
    *   Three `IkdLineChartView` instances stacked below the KPI strip
    *   Two new additive `@Query` methods (`IkdEventDao.getSessionTimingBuckets`, `SensorSampleDao.getSessionSensorBuckets`) do **SQL-side downsampling** — each chart receives at most 200 points regardless of session length
    *   Bucket width chosen as `max(50 ms, ceil(durationMs / 200))`; SQLite has no `sqrt`, so the loader averages the squared norm in SQL and applies `sqrt` in Kotlin per row (≤ 400 calls per session load)
*   **Raw Lists Replaced by Charts:**
    *   Saved-session mode: the wall of timing rows + sensor sample rows is **removed**. Raw data still exists in `ikd.db` and is still recoverable via the existing per-session CSV export.
    *   Live mode (Diagnostics → "View Log") **keeps** the raw lists — that screen is a real-time debug feed and a different feature.
    *   Phase 4's `Config.sensorDisplayMode` toggle still works in live mode and `DiagnosticsActivity`; on the session dashboard the toolbar action is hidden because there are no rows to toggle.

---

## Phase 6: Diagnostics Screen Improvements
**Status: Implemented (reworked).** Initial implementation in commit `797e8239` shipped the structural changes (status chip, two metric cards, collapsible sensor card, Session Insights toolbar action, additive `SessionDao.getMostRecentSession()`). The rework on `feat/phase6-rework` (see [`Phase6/Phase6_Plan.md`](Phase6/Phase6_Plan.md)) finishes the polish: theme-aware status-chip palette in `res/values{,-night}/colors.xml`, every `MaterialCardView` tinted via `setCardBackgroundColor(getProperBackgroundColor())`, both metric cards using a 3-cell KPI grid that mirrors the Phase 5 session dashboard, persisted sensor-card collapse state, animated chevron, and dimen-referenced paddings.

Detailed scope: [`Phase6/Phase6_Plan.md`](Phase6/Phase6_Plan.md)

**Objective:** Upgrade `DiagnosticsActivity` from a raw-data debug panel into a polished developer screen, and add a one-tap shortcut that surfaces the current (or most recent) keyboard session as a full chart dashboard. This phase is read-side only: zero edits to the keyboard / capture layer, no schema migration, and no new dependencies — all session data already exists in `ikd.db`.
Make it similar in style to the session insights.

*   **Live Session Insights Shortcut:**
    *   A dedicated **"Session Insights"** action is added to the `DiagnosticsActivity` toolbar (icon: the same chart/insights vector used in the overflow menu).
    *   When a session is **currently active** (keyboard open, privacy mode off), tapping the action opens `EventFeedActivity` in live mode — the existing real-time event-log view, exactly as the current "View Log" row already does. The "View Log" row remains for discoverability; the toolbar action is an additional shortcut.
    *   When **no session is active** (keyboard closed or privacy mode on), tapping the action queries `SessionDao.getMostRecentSession()` — a single `SELECT … ORDER BY started_at DESC LIMIT 1` — and opens `EventFeedActivity` in DB-backed mode for that session, landing directly on the session chart dashboard introduced in Phase 5. If the database is empty a snack bar message is shown: *"No sessions recorded yet — start typing to capture your first session."*
    *   `LiveCaptureSessionStore` already exposes whether a session is active; the `DiagnosticsActivity` status-polling handler (runs every 250 ms) already reads this. No new state mechanism is required.
    *   `SessionDao` gains one additive `@Query` method `getMostRecentSession(): SessionRecord?`. Read-only; no existing query is touched.

*   **Diagnostics Screen UI Refresh:**
    *   Metric cards (IKD, Dwell, Flight, Events, Speed, Error Rate) are regrouped into two `MaterialCardView` rows (timing metrics / count metrics) instead of a single flat list, improving scannability.
    *   The **Status** indicator is promoted to a coloured status chip (`active` = green, `idle` = grey, `privacy-on` = amber) above the metric cards rather than buried inline.
    *   Gyro and accel sensor bars are moved into a collapsible **"Sensor Readings"** `MaterialCardView` that is expanded by default but can be collapsed to focus on the typing metrics.
    *   The **"View Log"** row at the bottom is relabelled **"View Event Log"** for clarity now that the toolbar action handles the "quick jump to last session" use case.
    *   Typography and spacing are harmonised with the Phase 5 session dashboard cards (`card_corner_radius = 12 dp`, `card_elevation = 2 dp` from `dimens.xml`).
    *   All layout changes are confined to `res/layout/activity_diagnostics.xml` and `DiagnosticsActivity.kt`; no change to any other activity, helper, or data class.

*   **Rework Polish (Phase 6 rework on `feat/phase6-rework`):**
    *   **Theme-aware status chip** — three hardcoded `Color.parseColor("#…")` literals replaced with per-state colour resources in `res/values/colors.xml` (light) and `res/values-night/colors.xml` (dark). The base `bg_status_chip.xml` drawable now uses `?attr/colorControlNormal` so a chip without a runtime tint still renders something theme-appropriate. Per-state foreground colour means the amber "Privacy On" state now uses black text (white failed AA contrast).
    *   **Card backgrounds tinted** — `applyThemeColors()` mirrors Phase 5's `EventFeedActivity.applyThemeColors()` and calls `setCardBackgroundColor(getProperBackgroundColor())` on the timing card, count card, sensor card, and the new view-log card so they no longer render as unthemed `?attr/colorSurface` slabs on dark themes.
    *   **Count metrics card** gains a "Count Metrics" section label tinted with `getProperPrimaryColor()`, matching the timing card.
    *   **View Event Log row** is wrapped in a `MaterialCardView` matching the metric cards' silhouette.
    *   **KPI-style metric grid** — both metric cards switch from "label : value" rows to a 3-cell horizontal KPI grid (big bold value, small label) identical to Phase 5's session-dashboard KPI strip.
    *   **Persisted sensor-card collapse** — `Config.diagnosticsSensorCardExpanded` (default `true`) survives navigation away from the screen.
    *   **Animated chevron** — 150 ms rotation animation instead of an instantaneous flip.
    *   **Inline `12dp` / `4dp` paddings** replaced with `@dimen/normal_margin` and `@dimen/small_margin`.

---

## Phase 7: Emoji & Autocorrect Capture
**Status: Implemented**

Detailed scope: [`Phase7/Phase7_Plan.md`](Phase7/Phase7_Plan.md)

**Objective:** Close two capture blind-spots that have existed since Phase 1.1 — emoji palette insertions (which bypass `onKey` entirely) and external text replacements triggered by the system spell-check service (which are invisible to the IME except via `onUpdateSelection`). This is the first phase since Phase 2 to deliberately reopen the keyboard layer; every edit is additive and scoped to instrumentation only.

*   **Emoji Palette Capture:**
    *   Add `fun onEmojiText(text: String)` to `OnKeyboardActionListener` (default delegates to `onText` — backward-compatible).
    *   `MyKeyboardView`'s emoji adapter callback routes through `onEmojiText` instead of `onText`; clipboard taps remain uncaptured.
    *   `SimpleKeyboardIME` overrides `onEmojiText` to record an `EMOJI` event with normal IKD / hold / flight timing before committing the text. Emoji events count toward WPM.
*   **Autocorrect / Spell-Check Detection:**
    *   `SimpleKeyboardIME.onUpdateSelection` is instrumented with an "expected cursor/text" tracker. When an external actor (system spell-check accept, VN Telex inline replacement) moves the cursor unexpectedly, the IME emits an `AUTOCORRECT` event with `is_correction = true`.
    *   `AUTOCORRECT` events automatically fold into the existing error-rate metric with no aggregator or schema change required.
    *   The `AUTOCORRECT` category is forward-compatible with any future autocorrect mechanism added to the IME.
*   **WPM Formula Correction:**
    *   `IkdAggregator` and `IkdSessionStatsLoader` WPM queries are updated to exclude `AUTOCORRECT` events from the keystroke denominator (corrections are not new typing). Error rate is unchanged — it continues to derive from `is_correction`.
    *   New JVM unit tests cover the adjusted WPM formula.

> **No schema migration.** `event_category` is already a `TEXT` column; new values land in the existing column. `IkdDatabase.version` stays at 1.

---

## Phase 7.1: AUTOCORRECT Replacement Weight
**Status: Implemented**

Detailed scope: [`Phase7.1/Phase7.1_Plan.md`](Phase7.1/Phase7.1_Plan.md)

**Objective:** Capture the **replaced character count** for every `AUTOCORRECT` event so a single autocorrect of a long misspelled word (the user's `ocasdasda` → `october` test case) contributes 9 units of error to the metric instead of 1 — fixing the surprising 9.1% reading and aligning the error-rate denominator with WPM's keystroke-only denominator from Phase 7.

*   **New `correction_weight INTEGER NOT NULL DEFAULT 0` column on `ikd_events`** with a strictly-additive `Migration(2, 3)` that backfills weight 1 onto every existing `is_correction = 1` row so dashboards over historical data are continuous across the upgrade. (Phase 8 had already shipped its own `Migration(1, 2)` for `mood_entries` by the time Phase 7.1 landed, so the schema bump is 2 → 3, not the original 1 → 2 the plan describes.)
*   **Capture wiring:** `recordAutocorrectEvent(replacedLength)` carries the new field; `BACKSPACE` rows always set weight 1; the `AUTOCORRECT` heuristic in `maybeRecordExternalReplacement` passes `(oldSelEnd - oldSelStart).coerceAtLeast(1)`.
*   **New error-rate formula** in both `IkdAggregator` and `IkdSessionStatsLoader`: `100 * SUM(correction_weight) / keystrokeCount` (was `100 * COUNT(is_correction) / eventCount`). Sessions without autocorrects are byte-identical between the two formulas.
*   **CSV** gains a trailing `correction_weight` column on the timing block; strictly additive, parsers reading the original seven columns ignore the eighth.
*   **Live diagnostics** (`DiagnosticsActivity.updateComputedMetrics`) mirrors the same formula on the in-memory event list, with a defensive `effectiveWeight()` fallback for events captured just before the migration.
*   **Folded in:** the Phase 7 follow-up fix for the autocorrect false-positive when the IME commits onto a user-selected range (`pendingOurSelectionReplacement` flag in `SimpleKeyboardIME`). Same heuristic, more accurate.

---

## Phase 8: Mood Bar & Contextual Overlay
**Status: Implemented (incl. 8.1 UI polish — see [`Phase8/Phase8_Plan.md`](Phase8/Phase8_Plan.md#12-post-merge-ui-polish-phase-81))**

Detailed scope: [`Phase8/Phase8_Plan.md`](Phase8/Phase8_Plan.md)

**Objective:** Let the user annotate their current emotional state with a single tap during a typing session. That one-tap signal becomes a first-class dimension in both per-session and global dashboards, realising the "Subjective Context Overlay" deferred at the end of Phase 3.

*   **Seven-button Emotion Bar on the Keyboard Toolbar (replaces the Phase 2 standalone privacy shield):**
    *   A horizontal strip of seven buttons appears in the keyboard top bar — `🛡️ 😊 😲 🤢 😢 😨 😠`. The leftmost shield slot subsumes the Phase 2 privacy-toggle button verbatim; the next six are Ekman's six basic emotions [Ekman 1972, 1992] ordered best-to-worst by valence.
    *   The buttons are mutually exclusive — at most one is highlighted. With privacy on the shield is highlighted; with privacy off and no tap yet, no slot is highlighted (Decision #7).
    *   Tapping any of the six emotion buttons disables privacy mode and writes/replaces a `MoodEntry` (sessionId, timestamp, moodScore 1–6) keyed on the active session.
    *   Tapping 🛡️ enables privacy mode, finalises any in-flight session via the existing `LiveCaptureSessionStore.stopSession()` path (Phase 2 semantics preserved), and deletes any mood row for that session.
    *   **No auto-write** — sessions without an explicit emotion tap have no `MoodEntry` row (Decision #10). This sidesteps the Neutral / no-rating problem entirely (Decision #11).
    *   `IkdSettingsActivity` gains a "Privacy mode on by default" row backed by the existing `Config.privacyModeEnabled` flag (Decision #25 — no new pref key). The keyboard 🛡️ button and the settings row are two affordances over the same flag.
    *   *(Phase 8.1 polish)* `IkdSettingsActivity` also gains a "Show mood bar in keyboard" toggle backed by the new `Config.showMoodBar` flag (default true). When disabled, the keyboard's seven-button bar is hidden entirely; privacy stays reachable from the settings row above.
    *   Privacy invariant: `MoodEntry` stores only the integer ordinal valence (1–6) and a timestamp — no text, no emoji codepoint. Score → emoji mapping lives in the UI layer only (`helpers/MoodEmoji.kt`).

*   **Mood Overlay on Session Dashboard (`EventFeedActivity`):**
    *   When a session has a `MoodEntry`, the KPI strip grows from four cells to five (emoji + label).
    *   Sessions without a mood entry keep the original four-cell layout — the fifth cell collapses cleanly.
    *   *(Phase 8.1 polish)* The originally planned standalone "Mood: 😊 Happiness" line under the metadata was removed — it duplicated the KPI cell. The fifth KPI cell is now the single mood surface on this screen.

*   **Mood widgets on Global Insights (`DashboardActivity`):**
    *   A fourth chart card "Mood over Time" — line chart with Y axis 1 (Happiness) → 6 (Anger), bucketed identically to the existing three charts. Null buckets render as line breaks.
    *   A "Mood Distribution" panel below the chart cards — six rows in display order (Happiness → Anger), each emoji + label + horizontal progress bar + count.
    *   A fifth KPI cell "Avg Mood" showing the rounded emoji + the precise number (e.g. `🤢 3.2`).
    *   All three are gated on `total > 0` for the selected range; otherwise hidden, and the KPI strip falls back to four cells.
    *   New `IkdMoodAggregator` (Phase 3 read surface stays frozen); new `MoodDao` with `getMoodBuckets` + `getMoodDistribution` queries.

*   **CSV export gains a third dual-block segment:** `#mood_entries\nsession_id,timestamp_ms,mood_score`. Strictly additive — parsers reading only the first two blocks remain backwards-compatible.

> **Schema change required.** Phase 8 bumps `IkdDatabase.version` to 2 and adds the `mood_entries` table via `Migration(1, 2)`. The migration is non-destructive: it only adds a new table and leaves all existing tables untouched. The migration test in `app/src/androidTest` covers v1-data preservation, the unique index on `session_id`, and SQLite's NULL-distinctness behaviour for sessionless rows.

---

## Phase 8.2: Mood Bar UX Polish (Toggles, Capsule, Chat-Bubble)
**Status: Implemented**

Detailed scope: [`Phase8/Phase8_Plan.md` § 13](Phase8/Phase8_Plan.md#13-mood-bar-ux-polish-phase-82)

**Objective:** A second on-device review pass after Phase 8.1 surfaced three more issues with the mood bar — slots that were "click-to-set, but no way to unset"; a transient toast for the only feedback channel; and a bar that visually read as seven floating glyphs rather than one keyboard control. Phase 8.2 makes the bar feel like a first-class keyboard widget without changing what's stored.

*   **Slots are now true toggles:**
    *   Tapping the highlighted 🛡️ now disables privacy without committing to a mood (closing the gap where the only way out of privacy was picking an emoji).
    *   Tapping a highlighted emotion deletes the `MoodEntry` row without re-enabling privacy.
    *   Two new controller methods mirror the existing `enablePrivacyAndClearMood` path: `IkdMoodBarController.disablePrivacy` (flips `Config.privacyModeEnabled` without touching the mood row) and `clearMoodForActiveSession` (deletes the row without touching privacy). Deselect paths are silent — the highlight change *is* the feedback.
*   **Stretched-key capsule background:**
    *   The horizontal bar is centred in the toolbar and dressed as one rounded "stretched key" capsule, themed at runtime via `mKeyColor` / `mStrokeColor` so it tracks the active keyboard theme.
    *   The clipboard chip / inline suggestions / clear / voice buttons are hidden while the bar is on; the pinned-clipboard and settings buttons stay anchored on the right edge.
    *   Selected glyph rendered at `scaleX/scaleY 1.25` (rendering only — tap area unchanged) so the active emotion lifts above the row. Dimmed alpha tightened from 0.6 → 0.45 to strengthen the selected-vs-unselected hierarchy. `clipChildren=false` on the toolbar holder + mood bar so the scaled glyph isn't cropped.
*   **Chat-bubble feedback popup:**
    *   `PopupWindow` anchored above the tapped slot replaces the generic `Toast` calls with first-person feedback ("I'm feeling happy", "I want privacy", …).
    *   Auto-dismisses after 1500 ms; consecutive taps reset the timer and reuse the same popup so there's no flicker.
    *   New `Config.showMoodPopup` (default `true`) plus a settings row in `IkdSettingsActivity` lets users silence the bubble without disabling the underlying state change. Strictly additive new flag; `Config.showMoodBar` from Phase 8.1 is unchanged.

> **No schema change.** `IkdDatabase.version` stays at 2. Capture path frozen — the only Kotlin code that runs in this phase lives on the keyboard view + settings layer.

---

## Phase 8.3: Mood Distribution over Time (Stacked Bar) on Insights
**Status: Implemented**

Detailed scope: [`Phase8/Phase8.3_Plan.md`](Phase8/Phase8.3_Plan.md)

**Objective:** Drop the Phase 8 "Avg Mood" KPI cell and "Mood over Time" line chart from `DashboardActivity`, and replace them with a per-bucket **stacked-bar chart** showing the percentage breakdown of each Ekman category in each time bucket. Averaging an ordinal valence across six categorical labels produces numbers that don't correspond to any user-reportable state — `🤢 3.2` ("around Disgust, slightly toward Sadness") is technically valid but interpretively vague, and the line chart inherits the same problem at every bucket. The stacked bar chart preserves the time dimension *without* re-introducing a meaningless average. Reverses Phase 8 Decision #14 and lifts the deferred "Stacked-bar-by-day chart" out of Phase 8 §10.

*   **Remove avg-based mood widgets:** `dashboard_kpi_avg_mood_cell` and `dashboard_mood_chart_card` are deleted from `activity_dashboard.xml`; the matching strings (`dashboard_kpi_avg_mood_label`, `dashboard_avg_mood_value_format`, `dashboard_chart_mood_title`, `dashboard_chart_mood_y_label`) are removed from `strings.xml`. KPI strip falls back to four cells.
*   **Add Mood Mix over Time stacked bar chart:** new `views/IkdStackedBarChartView.kt` wraps MPAndroidChart's `BarChart` in stacked mode, mirroring the Phase 3 `IkdLineChartView` discipline. Each bar is one time bucket; each segment is one of the six Ekman categories; bars sum to 100%. X axis aligns with the existing IKD charts (same `Range.bucketFormat`). Custom legend below the chart with emoji + colour swatch + label.
*   **Mood Distribution panel kept verbatim:** counts-per-category for the whole range remains useful — and complementary to the time-distributed stacked chart.
*   **One additive DAO query:** `MoodDao.getMoodCategoryBuckets(bucketFormat, fromMs, toMs)` returning `List<MoodCategoryBucketRow>`; ≤ 30 buckets × 6 categories = 180 rows worst case for Month range. Pure-Kotlin `IkdMoodAggregator.Companion.buildMixSnapshot(...)` for unit testing, parallel to the existing `buildSnapshot`.
*   **No schema change.** `IkdDatabase.version` stays at 2. The capture path stays fully frozen. CSV format unchanged.

---

## Phase 9: Global Insights Expansion
**Status: Planned (next)**

**Depends on:** Phase 8 (mood entries in `ikd.db`), Phase 8.1 polish (current dashboard surface), Phase 8.2 (mood bar UX), Phase 8.3 (mood-mix stacked-bar shape)

**Absorbs:** the deferred Phase 11 ("Usage Map & Daily Activity Charts") — every Phase 11 chart is lifted into a Phase 9 sub-phase. Phase 11 is deleted from the roadmap (Phase 9 plan Decision #16).

Detailed scope:

- [`Phase9/Phase9_Plan.md`](Phase9/Phase9_Plan.md) — orchestrator (implementation order, dependency graph, frozen surfaces, cross-cutting decisions).
- [`Phase9/sub_plans/`](Phase9/sub_plans/) — 10 self-contained sub-plan files, one per sub-phase. Implementer agents load only the orchestrator + their assigned sub-plan to optimise context.

**Objective:** Expand `DashboardActivity` from a single page of three IKD line charts + a four-cell KPI strip into a multi-section, multi-card global insights hub modelled after the [BiAffect research dashboard](https://www.biaffect.com/8203biaffect-meets-the-world/tracking-mental-health-through-keystroke-dynamics-with-biaffect). Ten sub-phases on a single read-side-only branch — no schema migration (`IkdDatabase.version` stays at 3), no keyboard-layer reopen, no new Gradle dependency. Five labelled dashboard sections after Phase 9 lands: **Trends → Daily Activity → Mood → Keystroke Dynamics → Habits**.

*   **9.1 Mood Section formalisation:**
    *   Wrap the existing Phase 8.3 stacked-bar mood-mix card and Phase 8 Mood Distribution panel in a labelled **Mood** section with its own primary-coloured section header, parallel to the new **Habits** section header that 9.3 introduces.
    *   Add a sibling **Trends** section header above the existing IKD chart group so the dashboard reads as four labelled sections (KPI · Trends · Mood · Habits) rather than nine sibling cards.
    *   Pure layout pass — no new charts, no aggregator changes, no DAO changes.

*   **9.2 Gyro & Accel Global Trend Charts:**
    *   Two new aggregate line charts on `DashboardActivity`: **Average Gyro Magnitude over Time** and **Average Accel Magnitude over Time**, bucketed by the same `Range.bucketFormat` as the existing IKD chart so the X axes align.
    *   Mirrors Phase 5's per-session sensor charts but aggregated across sessions: `AVG(x*x + y*y + z*z)` in SQL, `Math.sqrt` per row in Kotlin (same SQLite-has-no-sqrt workaround). Null buckets render as line breaks.
    *   New `helpers/IkdSensorAggregator.kt` (sibling of `IkdAggregator`); new additive `SensorSampleDao.getSensorBuckets(sensorType, bucketFormat, fromMs, toMs, moodScore: Int?)` query. The existing per-session `getSessionSensorBuckets` from Phase 5 is untouched.

*   **9.3 Typing Habits & Session Statistics:**
    *   A new **Habits** section beneath Mood with four trend line charts and a four-cell Habits KPI strip:
        *   **Average Session Duration over Time** (minutes per session, daily/weekly bucketed).
        *   **Sessions per Day / Week** — typing frequency over time, makes gaps in usage visible.
        *   **Average Error Rate over Time** — daily/weekly weighted error rate (matches the Phase 7.1 formula `100 * SUM(correction_weight) / keystrokeCount`).
        *   **Average Flight Time over Time** — trend of the mean flight-time per day/week, proxy for hesitation and cognitive load.
    *   Habits KPI strip: total sessions, total typing time, average session duration, longest streak (consecutive days/weeks with at least one session, computed in Kotlin from the existing aggregation output — no extra SQL).
    *   New `helpers/IkdHabitsAggregator.kt` (sibling of `IkdAggregator`); one additive `IkdEventDao.getHabitsBuckets(...)` query that joins event-side and session-side aggregations on the bucket key in a single round-trip. Pure `Companion.computeLongestStreak(buckets)` for unit testing.

*   **9.4 Mood Filter on Insights:**
    *   Horizontally scrollable single-select chip row above the range selector — `All` + the six emoji (😊 😲 🤢 😢 😨 😠). Default `All` (current behaviour); the chip row is `View.GONE` until the user records their first `MoodEntry`.
    *   Selecting a non-`All` chip re-runs every aggregator on the same `Dispatchers.IO` hop and re-renders every chart and every KPI scoped to *sessions whose `session_id` has a `mood_entries` row with `mood_score = X`*. Lets the user ask the new behavioural question: "*what does my typing look like when I'm angry vs happy?*"
    *   Tapping the active emoji chip toggles back to `All` (mirrors Phase 8.2's mood-bar slot toggles). Filter state is persisted in `onSaveInstanceState`, **not** in `SharedPreferences` — per-screen-instance, not per-user-preference.
    *   New `MoodDao.hasAnyMoodEntry()` query gates chip-row visibility. `IkdAggregator`, `IkdSensorAggregator`, `IkdHabitsAggregator`, and the four new BiAffect-inspired aggregators (9.5 / 9.7 / 9.8 / 9.10) each gain a `moodFilter: Int? = null` parameter on `snapshot(...)`. The mood widgets (Mood Distribution + stacked-bar mood-mix) intentionally render the degenerate one-row / one-colour result under a non-`All` filter — that's the user feedback that the filter is in effect.

*   **9.5 Daily Activity — Calendar Heatmap + Daily Keypress Bar:** *(BiAffect-inspired.)*
    *   GitHub-contributions-style calendar heatmap (rows = ISO week-of-year, columns = day-of-week, cell intensity = total keystrokes). Tap a cell → toast with date + count.
    *   Per-day bar chart of total keypresses across the selected range. Mirrors the BiAffect3 v3.0.2 "Daily keypresses graph" called out in release notes.
    *   New `helpers/IkdActivityAggregator.kt` (one aggregator covers 9.5, 9.6, 9.9 — the queries share the `(day, hour)` shape). New `views/IkdHeatmapView.kt` custom theme-aware `View` (used in 9.5 and 9.6).

*   **9.6 Hourly Activity — 24-Hour Bar + Hour × Weekday Heatmap:** *(BiAffect-inspired.)*
    *   24-bar bar chart (one bar per hour-of-day, range-scoped) showing keystrokes accumulated in each hour across the selected range.
    *   24×7 hour × weekday heatmap rendered using the same `IkdHeatmapView`. **Always all-time** (Decision #18) — single-week data is too sparse to expose a circadian pattern; the chart card subtitle ("Across all your sessions") documents the static range scope to the user.
    *   Lifts the **Time-of-Day Heatmap** from the deleted Phase 11 spec.

*   **9.7 Keystroke Dynamics Distribution — IKD / Dwell / Flight Histograms:** *(BiAffect-inspired.)*
    *   Three histograms surfacing the *distribution shape* of inter-key delay, hold-time, and flight-time over the selected range. Log-scale X axis (`10–20 ms`, `20–40 ms`, …, `5120–10240 ms`); count on Y.
    *   Outliers above the top bucket are clipped into it with a small "+ outliers" annotation.
    *   New `helpers/IkdDistributionAggregator.kt`; three additive `@Query` methods on `IkdEventDao`. Bucket edges are a hardcoded log-scale `LongArray` shared across the three queries.

*   **9.8 Orientation Breakdown:**
    *   Donut (or stacked horizontal bar) showing the share of typing sessions by phone orientation across the selected range. Surfaces `SessionRecord.orientation` for the first time since Phase 2 stored it.
    *   Combined with the 9.4 Mood Filter, answers questions like "*do I type lying down more when I'm sad?*"
    *   Sentinel `-1` ("Not captured" — orientation capture disabled in settings) shown only when ≥ 1 session in the range has that value.

*   **9.9 Usage Map (Bubble Chart):** *(Lifted from Phase 11.)*
    *   Time-vs-date bubble chart — Y axis = calendar date, X axis = hour of day in 1-hour columns, bubble area = keystroke count. Tooltip on tap (date / hour / count / locale).
    *   Bubble radius scales linearly between 1 dp and 12 dp. When more than one locale exists in the range, bubbles are coloured by locale.
    *   New `views/IkdBubbleMapView.kt` custom `View` with per-bubble hit-test for the tooltip. Reuses the `IkdActivityAggregator` `(day, hour)` payload — zero new queries beyond 9.6.

*   **9.10 Activity Quality Scatter:** *(Lifted from Phase 11.)*
    *   X = backspaces for the day, Y = autocorrection count, one bubble per day. Most-recent day rendered larger and at full alpha; older days fade to 20% alpha.
    *   Subtitle preserves the research-led framing: *"When our mind is clear we tend to make fewer typing errors and require less backspace usage. On such days, the most recent data point will be closer to the origin."*
    *   New `helpers/IkdQualityAggregator.kt`; one additive `IkdEventDao.getDailyQuality(...)` query.

> **Privacy invariants preserved.** Phase 9 reads existing tables only — no new captured data, no new pref keys (`Constants.kt` and `Config.kt` stay frozen), no CSV format change. The mood filter joins on the already-stored `mood_entries.session_id` and `mood_entries.mood_score`; nothing is written.

---

## Phase 10: Rebranding & New Identity
**Status: Planned**

**Depends on:** Phase 9 (all major features complete before public-facing identity is locked in)

**Objective:** Retire the "Fossify Keyboard" name and visual identity. The app has evolved from a simple open-source keyboard fork into a behavioural analytics research platform. Phase 9 aligns everything the user sees — name, icon, colour palette, store listing, in-app strings — with that mission. This phase is deliberately scheduled last so the new identity can honestly reflect a feature-complete product.

*   **New App Name & Identity:**
    *   Define a new name that signals the app's core purpose: keystroke dynamics, self-insight, passive behavioural measurement. Candidate directions: something that evokes rhythm, patterns, or self-awareness (e.g., *KeyPulse*, *TypoSelf*, *RhythmKeys*, *IKDense*, *Keyma*). Final name decided by project owner.
    *   Update `app_name` and `app_launcher_name` strings in all `res/values*/strings.xml` files and all `fastlane/metadata` locale directories.
    *   Update `applicationId` in `app/build.gradle.kts` if the package name changes. A companion migration guide must cover existing users' data (the `ikd.db` path is tied to the package name — a package rename requires a one-time database copy on first launch of the renamed build).
    *   Update `CODEOWNERS`, `README.md`, `BUILDING.md`, `CHANGELOG.md`, and all roadmap documents to use the new name.

*   **Visual Design System:**
    *   Commission or design a new launcher icon that replaces the current Fossify coloured circles family. The new icon should be unique and reflect the analytics/research mission (e.g., a waveform, a keystroke pulse, or an abstract brain/finger motif).
    *   Define a primary colour token (`colorPrimary`) that moves away from the Fossify green/teal defaults. Apply it consistently across the keyboard toolbar, app bar, FAB, and chart accent colour (`IkdLineChartView` line colour).
    *   Review and update the `AboutActivity` content (app description, developer info, licence attribution) to credit both the Fossify upstream and the IKD additions.
    *   All 19 existing launcher icon variants (`ic_launcher_red` … `ic_launcher_grey_black`) can be retained as user-selectable accent icons or culled to a smaller curated set — decision for project owner.

*   **Store Listing & Metadata Refresh:**
    *   Rewrite `fastlane/metadata/android/en-US/full_description.txt` and `short_description.txt` to lead with the research/analytics mission rather than the generic keyboard pitch.
    *   Update screenshots in `fastlane/metadata/android/en-US/images/` to feature the Insights dashboard, Session Dashboard, and Mood Bar — the differentiating screens.
    *   Update the `title.txt` in every supported locale, or reduce the locale set to those actively maintained.
    *   Bump `versionName` in `app/build.gradle.kts` to a `1.0.0` (or `2.0.0` if treating the Fossify fork as v1) release marker to signal the public debut of the rebranded product.

---

## Phase 11: Usage Map & Daily Activity Charts
**Status: Planned**

**Depends on:** Phase 9 (global insights infrastructure) and Phase 10 (rebranded identity to ship under)

**Objective:** Add two new visualisation screens to `DashboardActivity` that answer different questions from the existing line charts — *when* the user types and *how accurately* they type each day. Both charts are custom `View` subclasses (MPAndroidChart does not support bubble/scatter plots out of the box at the fidelity required); they are new additions that never touch the capture layer or schema.

*   **Keyboard Usage Map (`UsageMapView`):**
    *   A **time-vs-date bubble chart** — Y axis = calendar date (newest at top, oldest at bottom, ~30 rows for Month range or ~7 for Week), X axis = time of day (Midnight → 11 PM in 1-hour columns), bubble size = keystroke count in that hour-slot, bubble colour = a configurable dimension (default: locale, which approximates timezone context when the user moves; fallback: single accent colour).
    *   Each bubble represents one `(date, hour)` bucket: `SELECT strftime('%Y-%m-%d', ...) AS day, strftime('%H', ...) AS hour, COUNT(*) AS keys FROM ikd_events GROUP BY day, hour`. This is a single query returning ≤ 24 × 30 = 720 rows for the Month range — well within SQLite's comfort zone.
    *   Bubble radius scales linearly between a `MIN_RADIUS_DP` (1 dp — just visible for low-activity slots) and `MAX_RADIUS_DP` (12 dp — densest slot in the current range). The legend shows the min and max keystroke counts for the visible range, matching the reference design.
    *   Tapping a bubble shows a tooltip: date, hour, keystroke count, and the locale recorded for that session.
    *   The chart lives in a new `UsageMapActivity` launched from `DashboardActivity` via an "Usage Map" card or overflow menu item — it is too tall to embed inline.

*   **Daily Keyboard Activity Scatter Chart (`DailyActivityView`):**
    *   A **backspaces-vs-autocorrections scatter chart** — X axis = backspace count for the day, Y axis = autocorrection count for the day, one bubble per day in the selected range. The most recent data point is rendered larger and darker; older points fade.
    *   Subtitle copy (mirroring the reference): *"When our mind is clear we tend to make fewer typing errors and require less backspace usage. On such days, the most recent data point will be closer to the origin."*
    *   Each bubble represents one `(date)` bucket: `SELECT day, SUM(CASE WHEN event_category='BACKSPACE' THEN 1 ELSE 0 END) AS backspaces, SUM(CASE WHEN is_correction=1 THEN 1 ELSE 0 END) AS autocorrections FROM ikd_events GROUP BY day`. Single query, ≤ 30 rows for Month range.
    *   The "most recent" bubble uses `colorPrimary` at full opacity and 1.8× radius; older bubbles use `colorPrimary` at decreasing alpha (linear fade from 80% → 20% across the date range), same radius. No legend needed beyond the single callout note.
    *   Embedded directly in `DashboardActivity` as a fixed-height card (200 dp) below the Usage Map entry point, visible in all range modes.

*   **Time-of-Day Heatmap (`HeatmapView`):**
    *   A **24-column × 7-row grid** (hour × day-of-week) showing aggregate keystroke intensity across all time, regardless of the range selector. Cells are coloured on a gradient from background (zero activity) to `colorPrimary` (peak activity hour).
    *   Answers "what hour and day of the week do I type the most?" — a circadian pattern view complementary to the date-scrolling Usage Map.
    *   Query: `SELECT strftime('%w', ...) AS dow, strftime('%H', ...) AS hour, COUNT(*) AS keys FROM ikd_events GROUP BY dow, hour` — 168 rows maximum, always fast.
    *   Rendered as a custom `View` using `Canvas.drawRoundRect` per cell; no external charting library needed.
    *   Embedded in `DashboardActivity` below the Daily Activity scatter chart as a fixed-height card (160 dp).