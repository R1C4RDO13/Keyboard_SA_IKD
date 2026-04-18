# KeyboardSA — InterKeyDelay (IKD) Data Collection Keyboard

An Android keyboard for research-oriented InterKeyDelay (IKD) measurement. Based on the open-source [Fossify Keyboard](https://github.com/FossifyOrg/Keyboard).

## Project Goal

This project modifies an existing open-source Android keyboard to **measure and collect InterKeyDelay (IKD) data** — the time interval (in milliseconds) between consecutive key presses during natural typing.

IKD is a behavioral biometric used in keystroke dynamics research. By capturing precise timing data directly from a soft keyboard, this project enables data collection for studies in:

- User authentication via typing patterns
- Neuromotor analysis and typing behavior
- Detection of cognitive or motor impairments
- Human-computer interaction research

## How It Works

The keyboard functions as a normal Android input method. When IKD collection is enabled in the settings, it silently records the timestamp and inter-key delay for each keystroke into a local database on the device. The data can then be exported as CSV or JSON for analysis.

**Key measurements captured per keystroke:**

| Field | Description |
|---|---|
| Session ID | Groups keystrokes by typing session |
| Timestamp | When the key was pressed (milliseconds) |
| Key code | Which key was pressed |
| Inter-key delay | Time since the previous key press (ms) |

All data stays on-device. Nothing is transmitted over the network.

## Implementation Plan

The full technical implementation plan, including project structure documentation, key press flow diagrams, code change details, and a step-by-step guide, is in [IKD_PLAN.md](IKD_PLAN.md).

### Summary of changes from the base Fossify Keyboard

| Area | What changes |
|---|---|
| **IME Service** | Records timestamps and IKD at each key press |
| **Database** | New `IkdDatabase` with `IkdEvent` entity (separate from the existing clips database) |
| **Settings** | Toggle to enable/disable IKD collection, export and clear data buttons |
| **Data export** | CSV/JSON export of all collected IKD events |

### Files added

- `models/IkdEvent.kt` — Room entity for IKD events
- `interfaces/IkdDao.kt` — Data access object for IKD queries
- `databases/IkdDatabase.kt` — Separate Room database for IKD storage

### Files modified

- `services/SimpleKeyboardIME.kt` — IKD capture logic in `onKey()`
- `helpers/Constants.kt` and `helpers/Config.kt` — New preference keys
- `extensions/ContextExt.kt` — Database access extension
- `activities/SettingsActivity.kt` — UI for the new settings

## Based On

This project is a fork of [Fossify Keyboard](https://github.com/FossifyOrg/Keyboard), an open-source, privacy-focused Android keyboard. The base keyboard provides:

- Fully offline operation (no internet permission)
- Multiple languages and layouts
- Clipboard management
- Material Design with dark theme
- Customizable colors and key borders

All original Fossify Keyboard features remain functional.

## Building

See [BUILDING.md](BUILDING.md) for full setup instructions, including first-time environment configuration and how to build from Android Studio or the command line.

Quick build:

```bash
cd "/media/rmca/QuickStorage/PROJETOS GITHUB/KeyboardSA/Keyboard_SA_IKD"
./gradlew assembleCoreDebug
```

Install on a connected device:

```bash
./gradlew installCoreDebug
```

## License

This project inherits the license from the original Fossify Keyboard. See [LICENSE](LICENSE) for details.


