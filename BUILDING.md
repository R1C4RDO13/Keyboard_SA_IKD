# Building And Navigating This Repo

---

## What Was Done to Get the First Build Working (This Machine)

This section records exactly what was required to build for the first time on this Linux machine. Skip it once the environment is already set up.

### Problems found and how they were fixed

#### 1. `JAVA_HOME` was not set — Gradle picked the wrong Java

The system had two Java versions installed: Java 21 (JRE only, no compiler) and Java 17 (full JDK with `javac`). Gradle defaulted to Java 21 and failed with:

```
Toolchain installation '/usr/lib/jvm/java-21-openjdk-amd64' does not provide the required capabilities: [JAVA_COMPILER]
```

**Fix:** Set `JAVA_HOME` to the JDK 17 path before running Gradle:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
```

To make this permanent, add it to `~/.bashrc` and run `source ~/.bashrc`.

#### 2. `local.properties` was missing — Gradle could not find the Android SDK

```
SDK location not found. Define a valid SDK location with an ANDROID_HOME environment variable
or by setting the sdk.dir path in your project's local.properties file.
```

The Android SDK was already installed at `/home/rmca/Android/Sdk` (by Android Studio), but Gradle did not know where it was.

**Fix:** Create `local.properties` in the repo root:

```properties
sdk.dir=/home/rmca/Android/Sdk
```

This file already exists now. It is listed in `.gitignore` and is not committed.

#### 3. Android SDK Build-Tools 36 and Platform 36 were not installed

Gradle downloaded and installed them automatically on the first build. No manual action was needed, but it added a few minutes to the first run.

### Summary of the first successful build command

```bash
cd "/media/rmca/QuickStorage/PROJETOS GITHUB/KeyboardSA/Keyboard_SA_IKD"
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
ANDROID_HOME=/home/rmca/Android/Sdk \
./gradlew assembleCoreDebug
```

The APK was generated at:

```
app/build/outputs/apk/core/debug/keyboard-14-core-debug.apk
```

Build time on first run: ~4 minutes (including SDK component downloads). Subsequent builds are faster.

---

## How to Build from Now On

After the first build, the environment is already configured. Use this quick reference.

### Option A — Android Studio (recommended for development)

This is the easiest approach and works well even without prior Android experience.

1. Open Android Studio.
2. Choose **File → Open** and select this folder:
   ```
   /media/rmca/QuickStorage/PROJETOS GITHUB/KeyboardSA/Keyboard_SA_IKD
   ```
3. Wait for the **Gradle sync** to finish (progress bar at the bottom). This may take a minute on the first open.
4. In the toolbar at the top, find the **build variant selector** (usually says `coreDebug`). Leave it as `coreDebug` for development.
5. To build: click **Build → Make Project** (or press `Ctrl+F9`).
6. To run on a connected phone or emulator: click the green **Run** button (▶) or press `Shift+F10`.

If Android Studio shows an error about the JDK:
- Go to **File → Settings → Build, Execution, Deployment → Build Tools → Gradle**
- Set **Gradle JDK** to `java-17-openjdk-amd64`

### Option B — Command line

Make sure `JAVA_HOME` is set first. If you added it to `~/.bashrc`, it is already set in every new terminal.

To verify:
```bash
echo $JAVA_HOME
# should print: /usr/lib/jvm/java-17-openjdk-amd64
```

Build the debug APK:
```bash
cd "/media/rmca/QuickStorage/PROJETOS GITHUB/KeyboardSA/Keyboard_SA_IKD"
./gradlew assembleCoreDebug
```

Install directly to a connected Android device (USB debugging must be enabled on the phone):
```bash
./gradlew installCoreDebug
```

The APK is always saved at:
```
app/build/outputs/apk/core/debug/keyboard-14-core-debug.apk
```

### Environment variables to add to `~/.bashrc` (do once, then forget)

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/home/rmca/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH="$ANDROID_HOME/platform-tools:$PATH"
```

After adding them:
```bash
source ~/.bashrc
```

---

## Overview

This repository contains a single Android application module:

- `app`: the keyboard app itself

The project uses:

- Gradle Kotlin DSL
- Android Gradle Plugin 9.0.1
- Kotlin 2.3.10
- KSP
- Detekt
- Room

Build variants are defined as:

- Product flavors: `core`, `foss`, `gplay`
- Build types: `debug`, `release`

Examples of final variant names:

- `coreDebug`
- `fossDebug`
- `gplayRelease`

Project build targets from `gradle/libs.versions.toml`:

- `compileSdk = 36`
- `targetSdk = 36`
- `minSdk = 26`
- Java target `17`
- Kotlin JVM target `17`

## Repo Layout

Useful locations:

- `app/src/main/kotlin/org/fossify/keyboard/activities`: app screens and settings screens
- `app/src/main/kotlin/org/fossify/keyboard/services`: input method service implementation
- `app/src/main/kotlin/org/fossify/keyboard/helpers`: keyboard logic, constants, and internal helpers
- `app/src/main/kotlin/org/fossify/keyboard/views`: custom keyboard rendering and interaction views
- `app/src/main/kotlin/org/fossify/keyboard/adapters`: RecyclerView and list adapters
- `app/src/main/kotlin/org/fossify/keyboard/databases`: Room database code
- `app/src/main/res`: layouts, drawables, strings, themes, and localized resources
- `app/schemas`: Room schema exports
- `fastlane`: release automation and store metadata workflows

Important entry points:

- `app/src/main/kotlin/org/fossify/keyboard/App.kt`: application startup
- `app/src/main/kotlin/org/fossify/keyboard/activities/MainActivity.kt`: main app UI
- `app/src/main/kotlin/org/fossify/keyboard/activities/SettingsActivity.kt`: settings UI
- `app/src/main/kotlin/org/fossify/keyboard/services/SimpleKeyboardIME.kt`: keyboard service
- `app/src/main/AndroidManifest.xml`: application and service registration

## What You Need Installed

### Required

For local development on Linux, install:

1. A full JDK, preferably JDK 17
2. Android SDK
3. Android platform tools
4. Android build tools for API 36

Recommended setup:

- Android Studio

Android Studio is the simplest path because it manages the Android SDK, emulator, SDK platforms, and command-line tools in one place.

### Optional

Install these only if you need them:

1. `adb` for device installation and debugging
2. Android Emulator for local device testing
3. Ruby and Bundler for Fastlane commands

You do not need to install Gradle globally because the repository includes `./gradlew`.

## Verified Local Build Blockers

The environment check on this machine showed:

- `java` exists
- `git` exists
- `javac` is missing from the active toolchain
- Android SDK is not installed or not configured
- `ANDROID_HOME` and `ANDROID_SDK_ROOT` are unset
- `local.properties` is missing

Gradle was able to start, but project task creation failed because the current Java installation does not provide the `JAVA_COMPILER` capability. In practice, that means you need a real JDK package with `javac`, not only a Java runtime.

## Linux Setup

### 1. Install JDK 17

On Ubuntu or Debian-based systems:

```bash
sudo apt update
sudo apt install openjdk-17-jdk
```

Verify:

```bash
java -version
javac -version
```

If multiple Java versions are installed, make sure Gradle uses JDK 17.

### 2. Install Android Studio

Install Android Studio and then open its SDK Manager.

Install at least:

1. Android SDK Platform 36
2. Android SDK Build-Tools 36
3. Android SDK Platform-Tools
4. Android SDK Command-line Tools

If you want emulator support, also install:

1. An x86_64 or ARM64 system image
2. Android Emulator

### 3. Configure the Android SDK path

Use one of these approaches:

1. Set `ANDROID_SDK_ROOT`
2. Set `ANDROID_HOME`
3. Create `local.properties` in the repo root with `sdk.dir=/path/to/Android/Sdk`

Example for a common Linux SDK location:

```properties
sdk.dir=/home/your-user/Android/Sdk
```

Example shell profile exports:

```bash
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export PATH="$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH"
```

After updating your shell profile, reload it:

```bash
source ~/.bashrc
```

### 4. Verify Android tools

```bash
adb version
sdkmanager --list | head
```

## Build Commands

Run all commands from the repo root:

```bash
cd "/media/rmca/QuickStorage/PROJETOS GITHUB/KeyboardSA/Keyboard_SA_IKD"
```

### Basic checks

```bash
./gradlew --version
./gradlew tasks
```

### Build all debug variants

```bash
./gradlew assemble
```

### Build a specific flavor

```bash
./gradlew assembleCoreDebug
./gradlew assembleFossDebug
./gradlew assembleGplayDebug
```

### Build release artifacts

```bash
./gradlew assembleCoreRelease
./gradlew bundleGplayRelease
```

Release builds can be unsigned locally if signing is not configured, but distributable builds require signing.

### Install on a connected device

```bash
./gradlew installCoreDebug
```

### Run static analysis

```bash
./gradlew lint
./gradlew detekt
```

## Signing

Release signing can be provided in either of these ways:

1. A `keystore.properties` file in the repo root
2. Environment variables:
   - `SIGNING_KEY_ALIAS`
   - `SIGNING_KEY_PASSWORD`
   - `SIGNING_STORE_FILE`
   - `SIGNING_STORE_PASSWORD`

There is a template file in `keystore.properties_sample`.

If neither signing source exists, Gradle warns that the build will be unsigned.

## Fastlane

Fastlane is optional for normal local development.

Available actions are documented in `fastlane/README.md`:

- `fastlane android test`
- `fastlane android deploy`
- `fastlane android metadata`

If you want to use Fastlane, install Ruby and Bundler first.

## Typical First Run

After installing the JDK and Android SDK, the normal first-run sequence is:

```bash
cd "/media/rmca/QuickStorage/PROJETOS GITHUB/KeyboardSA/Keyboard_SA_IKD"
./gradlew tasks
./gradlew assembleCoreDebug
```

If you have a device attached:

```bash
./gradlew installCoreDebug
```

## Troubleshooting

### Error: missing `JAVA_COMPILER`

Cause:

- A Java runtime is available, but not a full JDK with `javac`

Fix:

- Install `openjdk-17-jdk`
- Confirm `javac -version` works

### Error: Android SDK not found

Cause:

- SDK is not installed or Gradle cannot locate it

Fix:

- Install SDK components in Android Studio
- Set `ANDROID_SDK_ROOT`
- Or create `local.properties`

### Error: signing config missing

Cause:

- No `keystore.properties` and no signing environment variables

Fix:

- Ignore for local debug work
- Configure signing for release distribution

## Suggested Workflow

1. Open the project in Android Studio
2. Let Gradle sync fully
3. Confirm JDK 17 is selected in Gradle settings
4. Build `coreDebug` first
5. Run lint and detekt before larger changes


. Fix in Android Studio (Recommended)
1.
Go to Settings (or Settings on Windows/Linux).
2.
Navigate to Build, Execution, Deployment > Build Tools > Gradle.
3.
Under Gradle JDK, select JDK 17 (or point it to the directory where your JDK 17 is installed, e.g., /usr/lib/jvm/java-17-openjdk-amd64).
