# MoodScript — Behavioural-Analytics Keyboard

**MoodScript** is a research-grade Android keyboard prototype that captures keystroke timing, kinematic sensor data, and self-reported mood signals during everyday typing, then turns them into private on-device insights about anxiety, routine, and wellbeing. It is built on the open-source [Fossify Keyboard](https://github.com/FossifyOrg/Keyboard), and all upstream keyboard features remain functional. (The on-device app name and launcher icon were rebranded to MoodScript in Phase 10; the Android `applicationId` and Kotlin package are still `org.fossify.keyboard`.)

For the full project narrative — the roadmap evolution, the custom Claude Code agent workflow, the university assignment context, and the naming rationale — see [PROJECT_JOURNEY.md](PROJECT_JOURNEY.md) (or the tabbed visual version, [PROJECT_JOURNEY.html](PROJECT_JOURNEY.html)).

## Project Goal

This project modifies an existing open-source Android keyboard to passively collect behavioral and kinematic signals during normal typing — with no impact on the user's experience and no data leaving the device.

The data collected supports research in:

- Keystroke dynamics and behavioral biometrics
- Neuromotor analysis and typing behavior
- Detection of cognitive or motor fatigue
- Circadian rhythm and sleep-disruption patterns
- Human-computer interaction research

## What Gets Measured

| Signal | Description |
|---|---|
| **Inter-Key Delay (IKD)** | Time between consecutive key releases (UP to UP) (ms) |
| **Key Hold Time (Dwell Time)** | How long each key is physically held down (ms) |
| **Flight Time** | Time interval between key release and next key press (UP to DOWN) (ms) |
| **Typing Speed** | Statistical distribution of flight times indicating processing speed and alertness |
| **Error Rates** | Frequency of corrections and typos during input |
| **Accelerometer & Gyroscope** | Device physical context (e.g., walking, standing) using movement/orientation |

All data stays on-device. Nothing is transmitted over the network.

## Roadmap

Development is structured in phases. **Phases 1 → 10 and 12 → 15 are all implemented and on `main`** (plus every sub-phase: 8.1 – 8.5, 9.1 – 9.18, two error-rate corrections, and ~20 post-implementation UX reworks). **Every roadmap phase is shipped — none remain planned.** `IkdDatabase.version = 4`. Capture also auto-skips on password fields (per-field, not persisted).

| Phase | Goal | Status |
|---|---|---|
| **1 / 1.1 — Sensor calibration & live keyboard alignment** | Diagnostic screen → capture inside the live IME, off the main thread | Implemented |
| **2 — Background collection & local storage** | Privacy-default passive capture into `ikd.db`; SAF CSV export; retention worker | Implemented |
| **3 — Insights dashboard v1** | Three trend line charts + KPI strip; SQL-side aggregation | Implemented |
| **4 / 5 — Session detail → per-session dashboard** | Metadata header, magnitude-first sensors → KPI strip + 3 time-series charts | Implemented |
| **6 — Diagnostics polish + live-session shortcut** | Status chip, KPI grids, collapsible sensor card, Session Insights action | Implemented |
| **7 / 7.1 — Emoji & autocorrect capture; weighted error rate** | `EMOJI` + `AUTOCORRECT` events; `correction_weight` column (Migration 2→3) | Implemented |
| **8 (+ 8.1–8.5) — Mood bar & contextual overlay** | Seven-slot Ekman mood bar; `mood_entries` table (Migration 1→2); mood widgets; capsule UI; dance; collapsible right-anchored chip | Implemented |
| **9 (+ 9.1–9.18) — Global insights expansion** | Multi-tab `ViewPager2` dashboard; calendar/circadian heatmaps; histograms; Usage Map; quality scatter; mood filter | Implemented |
| **10 — Rebrand to MoodScript** | New app name, launcher glyph, About screen (cosmetic only) | Implemented |
| **12 — Mood-curated emoji section** | Curated emoji section in the drawer when a standing mood is set | Implemented |
| **13 — Persistent right-anchored mood bar** | Mood bar overlays both the keyboard toolbar and the emoji drawer | Implemented |
| **14 — Gamification: badges** | 28 v1 badges (37 catalogued) across 5 groups; "Achievements" tab as a flat grouped list; local unlock notification; `badges` table (Migration 3→4) | Implemented |
| **15 — Insights IA v2** | Final 5-tab order (Summary · Achievements · Activity · Trends · Keys); Habits tab dropped & charts relocated; circadian heatmap renamed/moved | Implemented |

Where to look:

- [`roadmap/STATUS.md`](roadmap/STATUS.md) — quick per-phase status table + frozen-surface list
- [`roadmap/FeatureRoadmap.md`](roadmap/FeatureRoadmap.md) — full roadmap overview
- [`roadmap/PhaseN/`](roadmap/) — per-phase plan files (frozen surfaces, reopened files, verification recipe); [`roadmap/Phase9/sub_plans/`](roadmap/Phase9/sub_plans/) for the Phase 9 sub-phases
- [`PROJECT_JOURNEY.md`](PROJECT_JOURNEY.md) / [`PROJECT_JOURNEY.html`](PROJECT_JOURNEY.html) — the narrative report
- `CLAUDE.md` — architecture notes per phase, build commands, conventions
- [`roadmap/ImplementationRoadmap.md`](roadmap/ImplementationRoadmap.md) — *archived* early cross-phase plan (superseded; kept for history)

## Based On

MoodScript is a fork of [Fossify Keyboard](https://github.com/FossifyOrg/Keyboard), an open-source, privacy-focused Android keyboard. All original Fossify Keyboard features remain functional:

- Fully offline operation (no internet permission)
- Multiple languages and layouts
- Clipboard management
- Customizable colors and key borders

## Building

See [BUILDING.md](BUILDING.md) for full setup instructions.

Quick build:

```bash
./gradlew assembleCoreDebug
```

Install on a connected device:

```bash
./gradlew installCoreDebug
```

**Toolchain requirements:** JDK 17, Android SDK (compileSdk 36, minSdk 26), Kotlin 2.3.10.

## License

This project inherits the license from the original Fossify Keyboard. See [LICENSE](LICENSE) for details.
