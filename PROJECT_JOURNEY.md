# moodScript — Project Journey

> Final report for **Trabalho prático 2025/2026**.
> A research-grade Android keyboard that turns everyday typing into a private, on-device behavioural-analytics pipeline.

---

## 1. Project context

This project was developed as the practical work for the academic year 2025/2026 (assignment file: `Trabalho pratico 2526.pdf`). The assignment opened up two routes — implement a small standalone application, or extend an existing system into a research platform. The second route was chosen because it allowed the work to focus on the harder, more interesting questions: *what does behavioural typing data actually look like once you capture it cleanly, and how do you turn it into something a person can read about themselves?*

The final result is called **moodScript**. The name reflects the two pillars of the project:

- **mood** — the subjective and emotional context, captured through self-reporting (a mood bar embedded in the keyboard) and surfaced through dashboards.
- **Script** — typed input, keystroke timing, and the act of writing itself.

The system is a working **research prototype**, not a commercial product. It demonstrates a complete capture → storage → analysis → visualisation pipeline, all running on-device with zero network communication. Data never leaves the user's phone.

### Headline numbers

- **14 implemented phases** (1 → 10, plus 12, 13, 14, 15) and roughly a dozen implemented sub-phases (8.1 – 8.5, 9.1 – 9.18) — **every roadmap phase is now implemented; none remain planned**. Full roadmap in [`roadmap/STATUS.md`](roadmap/STATUS.md).
- **3 Room database migrations** (v1 → v2 → v3 → v4), all additive, all validated by an instrumented migration test. Phase 14 added the third (v3 → v4, the `badges` table).
- **5 dashboard tabs** (Summary · Achievements · Activity · Trends · Keys) with **~15 chart cards** plus a gamification tab, built on top of a single read-side aggregator pattern.
- **The capture hot path stayed off the main thread throughout.** Every database write from `onKey()` is asynchronous, by constraint, in every phase. The handful of post-Phase-7.1 capture-layer reopens were narrow (the two error-rate fixes touched `computeBackspaceWeight`/the BACKSPACE branch; Phase 8.5 / 13 added tiny lifecycle / preference-listener wires; a late privacy hardening added a per-field password-capture skip) — and the phases that reshaped the keyboard *UI* (8 / 8.2 / 8.4 / 8.5 mood bar, 12 / 13 emoji drawer + mood-bar reposition) only touched view code, never the capture origin.
- **Privacy hardened further at the end.** Beyond the default-ON privacy mode, capture now also auto-skips on **password fields** (per-field, never persisted) — nothing is recorded for any text/visible/web/numeric password input.
- **All data on-device.** No internet permission was ever added. Phase 14's badge-unlock notification is a local post and adds only `POST_NOTIFICATIONS` (Android 13+), never `INTERNET`.

---

## 2. Why the project started as a fork

The first implementation decision — to fork [Fossify Keyboard](https://github.com/FossifyOrg/Keyboard) instead of writing a keyboard from scratch — was made deliberately and early. Building a keyboard engine *and* a research pipeline *and* an analytics dashboard inside one academic semester was not realistic. Forking removed the keyboard-engine question.

Fossify Keyboard was the right base for several concrete reasons:

- **Modern Android codebase.** Kotlin throughout, Material design, JDK 17 toolchain, AndroidX. No legacy Java code to migrate.
- **Privacy-focused by design.** Offline operation, no analytics SDKs, no network permissions. A natural foundation for an on-device-only research prototype.
- **Clean architectural separation.** The IME service (`SimpleKeyboardIME`), the keyboard view (`MyKeyboardView`), the listener bridge (`OnKeyboardActionListener`), and the SharedPreferences wrapper (`Config`) were already well-isolated. New instrumentation could be added at the right seams without surgery.
- **Production-grade quality.** Released on F-Droid and Play Store, used by real users — meaning the typing experience itself was already polished and any regression in keyboard quality would be immediately obvious.

That last point set the tone for the whole project. The keyboard had to keep working as a normal keyboard while it also collected research data in the background. Phase boundaries were drawn — and re-drawn — to protect that property.

---

## 3. The roadmap as an operating system

The roadmap began as a small four-step plan:

1. Build a diagnostic environment to validate sensors and timing math.
2. Move that validation into the live keyboard interface.
3. Capture data passively in the background and store it locally.
4. Turn the raw data into useful visual insights.

It expanded organically into ~15 phases plus sub-phases as each implemented step exposed new questions. Three things made the roadmap function as the *operating system* of the project rather than a static planning artifact:

- **Each phase had explicit frozen surfaces.** A plan would name exactly which files were "reopened" and which were "still forbidden", carried forward from prior phases. This kept changes narrow and prevented accidental drift into unrelated refactors.
- **Each phase had a verification recipe.** Build commands, on-device smoke tests, lint/detekt budgets — written before the code, used to confirm the work was done.
- **Plans evolved during implementation.** When on-device review surfaced a UX problem (e.g., the Phase 4 metadata header turned out to be a 12-row label/value strip *above* unchanged raw lists), the next phase was re-scoped to fix it (Phase 5 became "Charts replace raw lists"). The roadmap is the place where those mid-flight adjustments were captured.

The full roadmap, with per-phase plans, lives under [`roadmap/`](roadmap/). The current state is summarised in [`roadmap/STATUS.md`](roadmap/STATUS.md).

---

## 4. How the work evolved, phase by phase

The phases below are described in implementation order. Each entry pairs a one-line objective with the concrete deliverable that landed.

### Phase 1 — Sensor calibration and debug environment

**Objective:** confirm the device sensors and the timing math do what we think they do, before risking the live keyboard.

A standalone `DiagnosticsActivity` was built with a real-time view of inter-key delay (IKD), key hold time (dwell), flight time, and a live read-out of the gyroscope and accelerometer. The screen surfaced these as both numeric values and animated bars. This phase produced trust in the measurements before any of them were used downstream.

### Phase 1.1 — Live keyboard data and metric alignment

**Objective:** move the validated capture out of the diagnostic screen and into the actual keyboard, then recheck the metric definitions.

This is where the project committed to capturing inside `SimpleKeyboardIME.onKey()`, off the main thread. Phase 1.1 also fixed a category mismatch in the original metric definitions (e.g., dwell time was reframed from "duration of key press" to *Key Hold Time*) so the live keyboard, the diagnostic screen, and the eventual database all agreed on what each timing meant.

### Phase 2 — Background collection and local storage

**Objective:** persist the captured signals to disk without compromising battery, latency, or privacy.

The Room database `ikd.db` was introduced with three tables — `sessions`, `ikd_events`, `sensor_samples` — plus a write-behind buffer (`LiveCaptureSessionStore`) that batched events to disk every 500 ms or every 100 events. A privacy mode (default ON) and a SAF-based CSV export round out the phase. Sensor capture is gated on session activity: sensors come up when the keyboard does, and are stopped immediately on close.

### Phase 3 — Insights and dashboard presentation

**Objective:** turn raw on-device data into something the user can read.

The `DashboardActivity` shipped with three line charts (typing speed, average IKD, error rate) over Week / Month / All-Time, plus a four-cell KPI strip. Aggregation was done server-side in SQLite — a single `GROUP BY` query per chart per range, returning ≤ 52 rows even for All-Time. MPAndroidChart was added through JitPack as the only new external dependency.

### Phase 4 — Session detail refresh and magnitude-first sensors

**Objective:** make the per-session detail screen useful at a glance.

The screen gained a metadata header (started, duration, orientation, locale, counts, derived metrics) powered by a single per-session SQL query. The sensor list switched from a wall of X / Y / Z numbers to a single magnitude value per sample, with a toolbar toggle to flip back to per-axis view. Magnitude is computed in Kotlin at read time; it is never stored in the database and never appears in the CSV.

### Phase 5 — Session dashboard, charts replace raw lists

**Objective:** turn the saved-session screen into a real dashboard parallel to the global one.

The raw timing/sensor row dump was removed in favour of a KPI strip + three line charts (IKD over time, gyro magnitude over time, accelerometer magnitude over time). SQL-side downsampling guarantees that each chart receives at most 200 points regardless of session length, with bucket width chosen as `max(50 ms, ceil(durationMs / 200))`.

### Phase 6 — Diagnostics screen polish + live session shortcut

**Objective:** elevate the diagnostic screen from "raw debug panel" to "polished developer screen", and add a one-tap shortcut from there to the latest session.

A coloured status chip was added (active / idle / privacy-on, all theme-aware), the metric cards were re-grouped into KPI grids matching the Phase 5 session dashboard, and the sensor block became a collapsible card whose state persists across re-entries. A Session Insights toolbar action either opens the live event log (active session) or jumps straight to the most recent session's dashboard (no active session) via a single `SELECT … ORDER BY started_at DESC LIMIT 1`.

### Phase 7 — Emoji & autocorrect capture (+ Phase 7.1: weighted error rate)

**Objective:** close two capture blind spots — emoji palette taps and external text replacements (system spell-check, VN Telex inline transliteration).

This was the first phase since Phase 2 to deliberately reopen the keyboard layer. Every edit was instrumentation-only:

- Emoji palette taps now flow through a new `OnKeyboardActionListener.onEmojiText` and are recorded as `EMOJI` events.
- External replacements are detected via an `onUpdateSelection` heuristic (cursor moved by an unexpected amount, outside a 50 ms grace window of our last IME edit) and recorded as `AUTOCORRECT` events.

Phase 7.1 then upgraded the error-rate metric: a new `correction_weight` column on `ikd_events` (`Migration(2, 3)`) lets a single autocorrect of a long misspelled word contribute proportional weight to the error rate, instead of the same "1" that a single backspace contributes. Sessions without autocorrects are byte-identical between the old and new formulas; autocorrect-dominated sessions correctly read as much higher error rates.

### Phase 8 — Mood bar and contextual overlay (+ 8.1, 8.2, 8.3)

**Objective:** let the user annotate their emotional state with a single keyboard tap, and lift that signal into both per-session and global dashboards.

The keyboard top bar gained a seven-button mood bar — `🛡️ 😊 😲 🤢 😢 😨 😠` — covering the privacy shield (preserved verbatim from Phase 2) plus Ekman's six basic emotions, valence-ordered. Tapping a mood writes a `MoodEntry` row keyed on the active session (`Migration(1, 2)`).

- **8.1** added UI polish (sizing, dashboard card theming, toggleable mood bar visibility).
- **8.2** turned each slot into a true two-way toggle and dressed the bar as a stretched-key capsule with chat-bubble feedback popups (replacing the original toasts).
- **8.3** dropped the "Avg Mood" KPI and the "Mood over Time" line chart — averaging across categorical labels produces interpretively vague numbers — and replaced them with a per-bucket **stacked-bar mood-mix chart** showing the percentage breakdown of each Ekman category over time. The Mood Distribution panel (counts per category) was kept verbatim.
- **8.4** added a short pop + wiggle "dance" animation (plus the existing keyboard haptic) when the user *selects* a slot; deselect taps stay silent.
- **8.5** reshaped the bar into a **collapsible, right-anchored chip** that remembers the user's standing self-rating across sessions (display-only `Config.lastMoodScore` — no auto-write to `mood_entries`) and auto-clears it after one hour of inactivity. The chip expands to the full seven-slot row on tap and auto-collapses after a selection.

### Error-rate refinements (post-Phase 8)

Two on-device review passes corrected the error-rate metric without touching the schema:

- **Exclude BACKSPACE from the denominator** (`fix(error-rate)`, `569f331b`). Typing `hello world` and backspacing all eleven characters read 11/22 = 50 %; switching the denominator to *productive keystrokes* (`eventCount − correctionCount`) makes it read the expected 100 %. The same commit also made a single BACKSPACE that deletes an N-char selection contribute weight N instead of 1.
- **Drop AUTOCORRECT from the error-rate formula entirely** (`feat(error-rate)`, `303e792f`). The IME-level autocorrect heuristic can't reliably distinguish a true spell-check accept from spell-check noise across OEMs, so AUTOCORRECT rows are still captured (CSV, per-session event log) but no longer feed the error-rate KPI / chart / live cell. The metric layer's definition of "correction" is now **BACKSPACE only**: `errorRatePct = 100 × SUM(BACKSPACE.correction_weight) / (keystrokeCount − backspaceCount)`. The Habits-tab error metric and the Daily-Quality scatter still treat AUTOCORRECT as a correction — flagged as a deliberate follow-up.

### Phase 9 — Global insights expansion

**Objective:** expand `DashboardActivity` from a single page of three charts into a full multi-section analytics hub modelled on research-grade behavioural dashboards (BiAffect-inspired).

Phase 9 was deliberately split into 10 self-contained sub-plans, each with its own implementer, on a single read-side-only branch:

- **9.1** Mood section formalisation.
- **9.2** Aggregate gyro / accel magnitude trend charts.
- **9.3** Typing Habits section (avg session duration, sessions per day, avg error rate, avg flight time, longest-streak KPI).
- **9.4** Mood Filter chip row — re-runs every aggregator scoped to a chosen Ekman category.
- **9.5** Calendar heatmap (week × day-of-week) + daily keypress bar chart.
- **9.6** 24-hour bar chart + hour × weekday circadian heatmap.
- **9.7** IKD / dwell / flight log-scale histograms.
- **9.8** Orientation breakdown donut.
- **9.9** Time-vs-date bubble usage map.
- **9.10** Backspaces-vs-autocorrections quality scatter.

Then a run of micro-phases reshaped the dashboard's *navigation and presentation* on top of those charts:

- **9.11** added a TODAY range (hourly buckets) and converted the dashboard into a multi-tab `ViewPager2` layout, extracting per-tab rendering into `Fragment` subclasses under `activities/dashboard/` and shrinking `DashboardActivity` from ~1100 LOC to ~340 LOC.
- **9.12** themed the tab strip, moved the tabs to the top of the screen, and de-duplicated the global KPI strip.
- **9.13** added tap-to-explain info icons (`WidgetInfoDialog`) to every Insights widget.
- **9.14** added a **Summary** tab as the new landing page (KPI grid + tile click-through), switched the tab toggle to a `TabLayout` with icons, and collapsed the range + mood filters into a toolbar bottom sheet with an active-filter chip below the tabs.
- **9.15** reworked the Summary tab into a flat 3×2 KPI grid, moved the Usage Map and the Mood Mix + Mood Distribution widgets onto it, and **deleted the dedicated Mood tab** — the dashboard is now five tabs: **Summary · Trends · Daily Activity · Keystroke Dynamics · Habits**.
- **9.16** added a bucket-size hint under the active filter chip, a Sessions-history shortcut in the overflow menu, and a streak fix (only counts calendar-consecutive days).
- **9.17** refreshed the six mood-colour tokens to match the conventional emotional associations (gold = happy, orange = surprise, green = disgust, blue = sad, purple = fear, red = anger), moved the Mood Distribution panel to the top of the Summary tab as six coloured percentage tiles that double as the screen's colour legend, and tinted each Usage Map bubble by the dominant mood of the sessions in its cell.
- **9.18** wired each Distribution tile to act as a one-tap shortcut for the global Mood Filter — tap a tile to scope every chart and KPI on every tab to that mood; tap the active tile (or the active-filter chip ✕) to revert to All.

Two new custom views (`IkdHeatmapView`, `IkdBubbleMapView`, plus the `IkdHistogramView` used by 9.7) joined the existing `IkdLineChartView` / `IkdStackedBarChartView`. No schema migration was needed (`IkdDatabase.version` stayed at 3); the capture path stayed frozen; no new dependency was added.

### Phase 10 — Rebrand to moodScript (implemented)

**Objective:** retire the "Fossify Keyboard" identity now that the app has evolved into a behavioural-analytics research platform.

Cosmetic-only scope: new app name (**MoodScript**), new launcher glyph (the original Fossify keyboard glyph cradled by three concentric heart outlines — both brand pillars in one mark), new About screen via a local override of Commons `AboutActivity`. Deliberately *not* in scope (and still deferred to a future release-readiness pass): `applicationId` rename, fastlane/store metadata, `colorPrimary` palette change, `versionName` bump. The visual mockup is at [`roadmap/Phase10/Phase10_Identity_Mockup.html`](roadmap/Phase10/Phase10_Identity_Mockup.html); the full plan is at [`roadmap/Phase10/Phase10_Plan.md`](roadmap/Phase10/Phase10_Plan.md).

### Phase 12 — Mood-curated emoji section (implemented)

**Objective:** when the user opens the keyboard's emoji drawer with a standing mood selected (`Config.lastMoodScore != SCORE_NONE`), prepend a context-aware section at the top of the emoji list with ~15–25 curated emojis for that mood; when no mood is set, the drawer renders unchanged.

Pure presentation work; reuses Phase 7's `onEmojiText` capture pipeline so the privacy posture is unchanged — the DB still stores only `eventCategory = "EMOJI"`, never the codepoint. The per-mood curated lists live in `helpers/MoodEmoji.kt` only. Plan at [`roadmap/Phase12/Phase12_Plan.md`](roadmap/Phase12/Phase12_Plan.md).

### Phase 13 — Persistent right-anchored mood bar (implemented)

**Objective:** make the keyboard's mood bar a first-class persistent element. The bar moved from the leading edge to the **trailing edge** and is lifted into an overlay layer above both the regular keyboard toolbar and the emoji drawer, so the user can swap their mood without closing the drawer — which immediately re-curates the Phase 12 mood section (via the existing `OnSharedPreferenceChangeListener` path in `SimpleKeyboardIME`). No new capture-path code, no schema change. Plan at [`roadmap/Phase13/Phase13_Plan.md`](roadmap/Phase13/Phase13_Plan.md).

### Phase 14 — Gamification: badges (implemented)

**Objective:** reward consistent mood cataloging and keyboard usage with a badge spectrum, surfaced in an "Achievements" tab on the dashboard.

Added a new `badges` table via `Migration(3, 4)` (the third migration; `IkdDatabase.version` bumped 3 → 4). The catalog has **37 badges catalogued, 28 built in v1** across 5 groups — Mood Volume, Mood Daily check-in, Mood Daily devotion (a strict consecutive ≥3-logs-per-day streak), Keyboard Keystroke volume, Keyboard Session streak; groups 2 (Mood Diversity) and 6 (KB Sessions) are deferred. The evaluator runs lazily on dashboard open (`Dispatchers.IO`, pure-`Companion` derivation, reads existing tables only). Every locked badge shows per-badge progress (`current / target`). Unlock fires both an in-app snackbar and a **local** system notification (gated by a settings toggle, default ON, plus the `POST_NOTIFICATIONS` runtime permission — still no `INTERNET`). The privacy posture is unchanged: badges are derived entirely from existing `ikd_events` / `sessions` / `mood_entries`, with no new captured data and no `badges` block in the CSV export.

The Achievements-tab UI is worth a note as a mid-flight design correction: it was first built as a **per-group horizontal carousel** (ViewPager2 + `‹›` arrows + page dots), then **rebuilt after owner review as "Option B" — a flat vertical list** of group section headers and full-width badge rows (`BadgeListAdapter`). The carousel adapters and layouts were deleted; the Daily-devotion 14-day mini-keyboard streak strip was preserved, folded into that group's section header. The plan was originally drafted as "Phase 13" but that slot was claimed by the right-anchored-mood-bar feature, so the gamification work is renumbered to 14. Plan at [`roadmap/Phase14/Phase14_Plan.md`](roadmap/Phase14/Phase14_Plan.md).

### Phase 15 — Insights information-architecture v2 (implemented)

**Objective:** lean, re-prioritised dashboard IA after Phase 14 added a sixth tab.

Read-side only — no schema change, no capture-path reopen, no new aggregator. The **Habits tab was dropped**; its two surviving charts were relocated (Avg session duration → Trends, Avg flight time → Keys), the Orientation breakdown moved to Trends, and three low-signal widgets (Calendar heatmap, Sessions-per-period, Activity-quality scatter) were removed. The circadian heatmap was renamed ("When you type" → "Circadian heatmap") and moved to the top of the Activity tab. The final tab order is **Summary · Achievements · Activity · Trends · Keys** (`TAB_COUNT = 5`). Two deviations from the plan are on record: `SummaryFragment` was reopened so its KPI tiles re-route to Trends (Habits being gone), and the error-rate-duplication open point was resolved with the plan's default (only Avg session duration moved; Habits' error-rate dropped; single Trends error-rate kept). A subsequent over-removal that hard-fixed the dashboard to All-Time and deleted the Filters bottom sheet was reverted — Range/Mood filtering is fully retained; only the small active-filter chip pill below the tabs was removed. Plan at [`roadmap/Phase15/Phase15_Plan.md`](roadmap/Phase15/Phase15_Plan.md).

---

## 5. Technical architecture

The project ended up with a clean three-layer separation that is worth highlighting:

### Layer 1 — Capture (Phases 1.1, 2, 7)

All capture happens inside `SimpleKeyboardIME.onKey()` and `MyKeyboardView`'s touch listeners. Events are pushed to `LiveCaptureSessionStore` (an in-memory, thread-safe singleton) which exposes them to the diagnostic UI in real time *and* batches them to disk via a write-behind flusher. Sensor sampling lives in `KinematicSensorHelper` and is lifecycle-bound to the keyboard's open/close events.

The capture *hot path* (`SimpleKeyboardIME.onKey()` and the write-behind buffer) was frozen as soon as it stabilised; Phase 7.1 closed it for good. The analytics phases (3, 4, 5, 6, 9 and its sub-phases, plus 14 and 15) are read-side only — they query the database and render charts, never editing the IME, the buffer, or the sensor helper. The phases that *did* reopen the keyboard layer (8 / 8.2 / 8.4 / 8.5 for the mood bar, 12 / 13 for the emoji drawer + mood-bar reposition) only touched `MyKeyboardView`, the keyboard layout XML, and the mood-bar helpers — never the capture origin. The single late exception was a privacy hardening: `SimpleKeyboardIME.isPasswordField(editorInfo)` (commit `f09a6a87`) gates the session/sensor start so nothing is captured on password fields — a narrow, sanctioned reopen of `onStartInputView`, per-field and never persisted. This separation is what allowed the analytics and mood layers to grow without risking input latency.

### Layer 2 — Storage (Phase 2, 7.1, 8)

A single Room database, `ikd.db`, currently at version 4. Three core tables — `sessions`, `ikd_events`, `sensor_samples` — plus `mood_entries` (Phase 8) and `badges` (Phase 14). Every migration has been additive: new columns get sensible defaults, new tables come in as `CREATE TABLE`, and the `IkdDatabaseMigrationTest` instrumented test verifies that every prior version of the database can roll forward without data loss across all three migrations (v1→v2, v2→v3, v3→v4).

Privacy-first invariants are enforced at this layer. Event categories are stored as strings (`ALPHA`, `DIGIT`, `SPACE`, `BACKSPACE`, `ENTER`, `OTHER`, `EMOJI`, `AUTOCORRECT`) — never the actual characters typed. Mood entries store an integer ordinal valence (1..6) and a timestamp — never the emoji codepoint, never any text.

### Layer 3 — Read pipeline (Phases 3, 4, 5, 9 + sub-phases)

A family of aggregators and loaders (`IkdAggregator`, `IkdSessionStatsLoader`, `IkdSessionChartLoader`, `IkdMoodLoader`, `IkdMoodAggregator`, `IkdHabitsAggregator`, `IkdActivityAggregator`, `IkdDistributionAggregator`, `IkdOrientationAggregator`, `IkdQualityAggregator`, `IkdSensorAggregator`) all follow the same shape:

- One `suspend fun snapshot(range, …)` (or `load(sessionId)`) on `Dispatchers.IO`.
- Pure-Kotlin derivation logic on the `Companion` for unit testing.
- A wall-clock duration log to Logcat in debug builds.
- One or two SQL queries, returning ≤ ~200 rows for the worst-case range.

The dashboard activity orchestrates them all on a single `Dispatchers.IO` hop per `loadSnapshot()` call (triggered by `onResume`, the range/mood filter, and the `Refresh` action) and dispatches the resulting payload to whichever tab fragment is visible.

### Privacy guarantees, restated

- No internet permission is declared in `AndroidManifest.xml`. The only runtime permission ever added is `POST_NOTIFICATIONS` (Phase 14, Android 13+), used solely for a local badge-unlock notification — nothing leaves the device.
- Capture is gated by the default-ON privacy mode **and** by a per-field password-field skip — no session, no sensors, no rows for any password input.
- The CSV export is the only data egress path, and it requires explicit Storage Access Framework selection by the user.
- The CSV format is dual-block (`ikd_events` + `sensor_samples` + `mood_entries`); no raw text, no emoji codepoints, no badge keys (the `badges` table is derived, internal state — never exported).

---

## 6. The role of the agent workflow

Most of the engineering work was performed through a custom Claude Code agent workflow, structured around the phase-driven roadmap. The workflow had three jobs:

1. **Read the phase plan.** Every implementation prompt began by loading the relevant `roadmap/PhaseN/PhaseN_Plan.md`. The plans encoded the frozen surfaces, the reopened files, the verification recipe, and the layering discipline. The agent treated those constraints as binding.
2. **Translate the plan into Android changes.** Activities, helpers, layouts, drawables, Room entities, DAOs, migrations, JVM unit tests, instrumented migration tests — each phase laid out exactly which artefacts needed to land.
3. **Validate before declaring done.** Every plan ended with a verification section — clean build, lint baseline check, detekt count check, on-device smoke test. The agent ran those before reporting completion.

This is the reason the codebase reflects a structured progression rather than a loose collection of features. The roadmap was the source of truth; the agent was a disciplined executor of that source of truth; the user reviewed each phase locally before allowing the next one to start. No phase was pushed to remote until the user had reviewed it on-device.

---

## 7. What the prototype demonstrates

moodScript is a working prototype of a research-grade behavioural-analytics keyboard. Concretely, the prototype shows that:

- **Capture** can be done inside a real Android IME without measurable input lag, by routing every database write off the main thread.
- **Storage** can be kept on-device, in a single Room database, with non-destructive migrations and a privacy-by-default posture.
- **Analysis** can be delegated to SQL — `GROUP BY` queries with `strftime` bucketing produce dashboard-ready aggregations in under 100 ms even for All-Time ranges.
- **Visualisation** can be built on a single charting library (MPAndroidChart) extended with a small family of theme-aware wrappers and two custom `View` subclasses for shapes that don't exist out of the box (heatmap, bubble map).
- **Subjective context** (mood) can be mixed in without compromising the objective signal: the mood bar is a single optional tap, the captured score is an integer, and the dashboard treats it as a filter dimension rather than a prediction target.

The prototype is intentionally not a finished consumer product. Every originally-planned surface beyond the core pipeline has now shipped: brand identity (Phase 10), emoji-drawer mood curation (Phase 12), the persistent right-anchored mood bar (Phase 13), gamification badges (Phase 14), and the Insights IA v2 restructure (Phase 15). No roadmap phase remains planned. Even so, the academic value of the project is the pipeline itself — capture → storage → analysis → visualisation — not any particular user-facing surface built on top of it.

---

## 8. Why the name moodScript

**mood** refers to the self-reported emotional layer and the broader wellbeing context the dashboards surface. It is the human side of the project.

**Script** refers to written input, typing behaviour, and the keyboard as the source of all the data. It is the technical side of the project.

Together, the name suggests a system that reads the relationship between *how* a person types and *how* they feel. The lowercase/compact visual style of `moodScript` was chosen to feel technical and prototype-ready rather than commercial — appropriate for a university deliverable.

The name also resolves a discoverability problem. The repository was bootstrapped from "Fossify Keyboard"; that name no longer described the work. Phase 10 replaced it on-device — the app now installs as **MoodScript** with its own launcher icon and About screen (the `applicationId` and the Kotlin package stay `org.fossify.keyboard`; a full package rename + store re-listing is deferred to a future release-readiness pass).

---

## 9. How to present the project

A natural narrative arc for a presentation:

1. **Open with the assignment context.** The work was structured around a one-semester academic deliverable.
2. **Explain why a keyboard, and why a fork.** The keyboard was the *capture surface*, and Fossify was a stable, modern, privacy-respecting base.
3. **Walk through the roadmap evolution.** From a four-step initial plan to a tree of a dozen-plus phases and sub-phases, including the mid-flight refinements (e.g., Phase 4 → Phase 5, the two error-rate corrections, the Mood-tab → Summary-tab consolidation in 9.15, and the Phase 14 Achievements carousel → flat-list rebuild after owner review).
4. **Highlight the architecture.** Four layers, frozen capture hot path, additive migrations, `Dispatchers.IO` read pipeline.
5. **Demo the dashboards.** Five tabs (Summary · Achievements · Activity · Trends · Keys), ~15 chart cards plus the gamification tab, all driven from on-device data.
6. **End with the mood layer.** Show the keyboard's collapsible right-anchored mood bar, the mood-curated emoji drawer, then the same Insights dashboard scoped by the Mood Filter (tap a Distribution tile), and the Achievements tab's badge progress.
7. **Acknowledge the prototype scope.** Branding (10), emoji curation (12), the right-anchored mood bar (13), gamification (14), and the Insights IA v2 restructure (15) all shipped — every roadmap phase is implemented; the remaining work is release-readiness, not feature scope.

The HTML companion document ([`PROJECT_JOURNEY.html`](PROJECT_JOURNEY.html)) presents the same material as a tabbed visual summary suitable for projection or screen-share during the presentation.

---

## 10. Appendix — key files at a glance

| Concern | File |
|---|---|
| IME capture entry point | `app/src/main/kotlin/org/fossify/keyboard/services/SimpleKeyboardIME.kt` |
| In-memory + write-behind buffer | `app/src/main/kotlin/org/fossify/keyboard/helpers/LiveCaptureSessionStore.kt` |
| Sensor sampler | `app/src/main/kotlin/org/fossify/keyboard/helpers/KinematicSensorHelper.kt` |
| Room database (v4) | `app/src/main/kotlin/org/fossify/keyboard/databases/IkdDatabase.kt` |
| Schema entities | `app/src/main/kotlin/org/fossify/keyboard/models/{IkdEvent, SensorSample, SessionRecord, MoodEntry, Badge}.kt` |
| Badge catalog + evaluator | `app/src/main/kotlin/org/fossify/keyboard/helpers/{IkdBadgeCatalog, IkdBadgeEvaluator, IkdBadgeNotifier}.kt` |
| Aggregator family | `app/src/main/kotlin/org/fossify/keyboard/helpers/Ikd*Aggregator.kt` |
| Dashboard activity | `app/src/main/kotlin/org/fossify/keyboard/activities/DashboardActivity.kt` |
| Dashboard fragments (Phase 9.11+) | `app/src/main/kotlin/org/fossify/keyboard/activities/dashboard/*.kt` |
| Chart wrappers | `app/src/main/kotlin/org/fossify/keyboard/views/Ikd*View.kt` |
| Mood bar wiring + emoji curation | `views/MyKeyboardView.kt` + `helpers/IkdMoodBarController.kt` + `helpers/MoodEmoji.kt` |
| Brand override (Phase 10) | `activities/AboutActivity.kt` + `res/drawable/ic_launcher_foreground.xml` |
| Roadmap | [`roadmap/`](roadmap/) — `STATUS.md` (quick status), `FeatureRoadmap.md` (full overview), per-phase plans, `Phase9/sub_plans/` |
| Build instructions | [`BUILDING.md`](BUILDING.md) |

---

*Last updated 2026-05-17, after Phase 14 (gamification badges; Achievements tab rebuilt as a flat grouped list), Phase 15 (Insights IA v2 — final five-tab order, Habits tab dropped), and the per-field password-capture privacy skip landed on `main`. Current schema version `IkdDatabase.version = 4`. Every roadmap phase (1 → 15) is implemented; no phase remains planned.*
