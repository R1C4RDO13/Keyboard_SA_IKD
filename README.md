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
| **Inter-Key Delay (IKD)** | Time between consecutive key releases (ms) |
| **Dwell Time** | How long each key is physically held down (ms) |
| **Flight Time** | Transition speed between keys: prev release → next press (ms) |
| **Gyroscope** | Device rotational velocity (X/Y/Z, rad/s) during typing |
| **Accelerometer** | Device linear acceleration (X/Y/Z, m/s²) during typing |

All data stays on-device. Nothing is transmitted over the network.

## Roadmap

Development is structured in three phases. See [`ROADMAP/Roadmap.md`](ROADMAP/Roadmap.md) for the full overview.

| Phase | Goal | Status |
|---|---|---|
| **1 — Sensor Calibration & Debug Environment** | Diagnostic screen to verify sensors and timing math in real time | Not started |
| **2 — Background Collection & Local Storage** | Passive capture inside the live keyboard, stored in a local database | Not started |
| **3 — User Insights & Dashboard** | Visual analytics: typing rhythm, fatigue heatmap, circadian patterns | Not started |

Detailed implementation plans:

- [`ROADMAP/Phase1/Phase1_Plan.md`](ROADMAP/Phase1/Phase1_Plan.md) — Phase 1 feature spec
- [`ROADMAP/IKD_PLAN.md`](ROADMAP/IKD_PLAN.md) — Full technical plan covering all three phases

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
