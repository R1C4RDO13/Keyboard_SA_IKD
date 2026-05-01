# Building This Project

---

## Requirements

| Tool | Version | Notes |
|---|---|---|
| JDK | 17 | Full JDK with `javac` — not just a runtime |
| Android SDK | API 36 | Install via Android Studio SDK Manager |
| Android Build-Tools | 36 | Install via Android Studio SDK Manager |
| Android Platform-Tools | latest | Needed for `adb` |
| Kotlin | 2.3.10 | Managed by Gradle — no separate install needed |

Android Studio is the recommended setup path. It handles the SDK, emulator, and JDK configuration in one place.

---

## Setup

### 1 — Install JDK 17

**Linux (Ubuntu/Debian):**
```bash
sudo apt update
sudo apt install openjdk-17-jdk
java -version && javac -version
```

**Windows:**

Download and install [Eclipse Temurin JDK 17](https://adoptium.net/) (or any JDK 17 distribution). After installation, verify in PowerShell:
```powershell
java -version
javac -version
```

If multiple JDK versions are installed, make sure `JAVA_HOME` points to the JDK 17 install (see step 3).

---

### 2 — Install Android Studio and the Android SDK

Install [Android Studio](https://developer.android.com/studio). After installation, open the **SDK Manager** (Tools → SDK Manager) and install:

- Android SDK Platform 36
- Android SDK Build-Tools 36
- Android SDK Platform-Tools
- Android SDK Command-line Tools (latest)

Optionally, for emulator support:
- Android Emulator
- An x86_64 or ARM64 system image

Default SDK locations after installation:

| OS | Default path |
|---|---|
| Linux | `~/Android/Sdk` |
| Windows | `%LOCALAPPDATA%\Android\Sdk` |

---

### 3 — Configure environment variables

Gradle needs to know where the JDK and Android SDK are. Set these once and they apply to every build.

**Linux — add to `~/.bashrc` (or `~/.zshrc`):**
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=$HOME/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
```
Then reload: `source ~/.bashrc`

**Windows — set in System Environment Variables (or PowerShell profile):**
```powershell
[System.Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Eclipse Adoptium\jdk-17.*", "User")
[System.Environment]::SetEnvironmentVariable("ANDROID_HOME", "$env:LOCALAPPDATA\Android\Sdk", "User")
[System.Environment]::SetEnvironmentVariable("ANDROID_SDK_ROOT", "$env:LOCALAPPDATA\Android\Sdk", "User")
```
Or set them via **System → Advanced system settings → Environment Variables**.

---

### 4 — Create `local.properties`

If Gradle cannot locate the Android SDK automatically, create `local.properties` in the repo root:

**Linux:**
```properties
sdk.dir=/home/your-user/Android/Sdk
```

**Windows:**
```properties
sdk.dir=C\:\\Users\\your-user\\AppData\\Local\\Android\\Sdk
```

> This file is listed in `.gitignore` and is never committed.

---

## Building

Run all commands from the repo root.

**Linux:**
```bash
./gradlew assembleCoreDebug
```

**Windows (PowerShell):**
```powershell
.\gradlew assembleCoreDebug
```

The debug APK is written to:
```
app/build/outputs/apk/core/debug/keyboard-14-core-debug.apk
```

---

## Common Commands

| Task | Linux | Windows |
|---|---|---|
| Build debug APK | `./gradlew assembleCoreDebug` | `.\gradlew assembleCoreDebug` |
| Install on device | `./gradlew installCoreDebug` | `.\gradlew installCoreDebug` |
| Build all flavors | `./gradlew assemble` | `.\gradlew assemble` |
| Build release APK | `./gradlew assembleCoreRelease` | `.\gradlew assembleCoreRelease` |
| Run lint | `./gradlew lint` | `.\gradlew lint` |
| Run detekt | `./gradlew detekt` | `.\gradlew detekt` |
| List all tasks | `./gradlew tasks` | `.\gradlew tasks` |

---

## Build Variants

| Flavor | Description |
|---|---|
| `core` | Minimal — no store dependencies |
| `foss` | F-Droid compatible |
| `gplay` | Google Play variant |

Combined with `debug` / `release` build types. Use `coreDebug` for all local development.

---

## Building from Android Studio

1. Open Android Studio → **File → Open** → select the repo root folder
2. Wait for the Gradle sync to finish
3. Confirm the build variant selector (top toolbar) is set to `coreDebug`
4. **Build → Make Project** (`Ctrl+F9`) to compile
5. **Run** (`Shift+F10`) to install on a connected device or emulator

If Android Studio reports a JDK error:
- Go to **File → Settings → Build, Execution, Deployment → Build Tools → Gradle**
- Set **Gradle JDK** to JDK 17

---

## Device Setup for Testing

1. Enable **Developer Options** on the Android device (tap Build Number 7 times in Settings → About Phone)
2. Enable **USB Debugging** in Developer Options
3. Connect via USB and accept the debug prompt on the device
4. Verify the device is recognized:

**Linux:**
```bash
adb devices
```
**Windows:**
```powershell
adb devices
```

---

## Signing

Release builds require a keystore. Configure signing via either:

1. A `keystore.properties` file in the repo root (see `keystore.properties_sample` for the format)
2. Environment variables:
   - `SIGNING_KEY_ALIAS`
   - `SIGNING_KEY_PASSWORD`
   - `SIGNING_STORE_FILE`
   - `SIGNING_STORE_PASSWORD`

Debug builds are signed automatically with the debug keystore — no configuration needed.

---

## Troubleshooting

### `JAVA_COMPILER` capability missing

**Cause:** A Java runtime (JRE) is installed but not a full JDK.  
**Fix:** Install `openjdk-17-jdk` (Linux) or a full JDK 17 distribution (Windows). Confirm `javac -version` works.

### Android SDK not found

**Cause:** Gradle cannot locate the SDK.  
**Fix:** Create `local.properties` with the correct `sdk.dir` path, or set the `ANDROID_HOME` environment variable.

### Wrong Java version picked by Gradle

**Cause:** `JAVA_HOME` points to a different version, or Android Studio's Gradle JDK setting is wrong.  
**Fix (command line):** Set `JAVA_HOME` explicitly before the Gradle command:

Linux:
```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew assembleCoreDebug
```
Windows:
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.*"
.\gradlew assembleCoreDebug
```

**Fix (Android Studio):** File → Settings → Build Tools → Gradle → set Gradle JDK to 17.

### Gradle sync fails on first open

**Fix:** File → Invalidate Caches and Restart, then let the sync complete fully before building.

### `adb: command not found` (Linux)

**Fix:** Add platform-tools to `PATH`:
```bash
export PATH="$ANDROID_HOME/platform-tools:$PATH"
```

---

## Repo Layout

| Path | Contents |
|---|---|
| `app/src/main/kotlin/org/fossify/keyboard/activities/` | App screens and settings |
| `app/src/main/kotlin/org/fossify/keyboard/services/` | Input method service |
| `app/src/main/kotlin/org/fossify/keyboard/helpers/` | Keyboard logic, constants, helpers |
| `app/src/main/kotlin/org/fossify/keyboard/views/` | Custom keyboard rendering |
| `app/src/main/kotlin/org/fossify/keyboard/databases/` | Room database code |
| `app/src/main/res/` | Layouts, strings, drawables, themes |
| `app/schemas/` | Room schema exports |
| `ROADMAP/` | Feature roadmap and implementation plans |
| `fastlane/` | Release automation and store metadata |
