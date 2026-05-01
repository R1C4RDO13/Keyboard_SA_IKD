# KeyboardSA — Behavioral Analytics Keyboard

A research-grade Android keyboard for capturing keystroke dynamics and device sensor data during natural typing. Based on the open-source [Fossify Keyboard](https://github.com/FossifyOrg/Keyboard).

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

Development is structured in phases. See [`ROADMAP/FeatureRoadmap.md`](ROADMAP/FeatureRoadmap.md) for the full overview.

| Phase | Goal | Status |
|---|---|---|
| **1 — Sensor Calibration & Debug Environment** | Diagnostic screen to verify sensors and timing math in real time | Complete (2026-05-01) |
| **1.1 — Live Keyboard Data & Metric Alignment** | Transition to the actual keyboard interface and validate the corrected metrics | Not started |
| **2 — Background Collection & Local Storage** | Passive capture inside the live keyboard, stored in a local database | Not started |
| **3 — User Insights & Dashboard** | Visual analytics: typing rhythm, fatigue heatmap, circadian patterns | Not started |

Detailed implementation plans:

- [`ROADMAP/Phase1/Phase1_Plan.md`](ROADMAP/Phase1/Phase1_Plan.md) — Phase 1 diagnostic screen spec
- [`ROADMAP/Phase1/Phase1.1_Plan.md`](ROADMAP/Phase1/Phase1.1_Plan.md) — Phase 1.1 live keyboard validation spec
- [`ROADMAP/ImplementationRoadmap.md`](ROADMAP/ImplementationRoadmap.md) — Cross-phase technical implementation plan

## Based On

This project is a fork of [Fossify Keyboard](https://github.com/FossifyOrg/Keyboard), an open-source, privacy-focused Android keyboard. All original Fossify Keyboard features remain functional:

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
