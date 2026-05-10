# Phase 10 — Rebrand to MoodScript

**Status:** Planned
**Depends on:** Phase 9 (all major user-facing features complete; the brand can now honestly reflect a feature-complete product)
**Branch:** Implementation lands directly on `main` (small focused commits per logical change), per recent project hygiene.
**Scope (one sentence):** Replace the on-device "Fossify Keyboard" identity with **MoodScript** — new app name, new launcher glyph, refreshed About screen — without renaming `applicationId`, without migrating `ikd.db`, and without touching fastlane/store metadata.

---

## 1. Why this change

The app started as a Fossify Keyboard fork, but eight phases of work have turned it into a keystroke-dynamics + mood + anxiety + routine research platform. The existing on-device identity ("Fossify Keyboard" name, generic keyboard glyph) no longer matches what the app actually does. Phase 10 retires that identity in favour of **MoodScript** — *Mood + Script*, capturing both pillars of the product (emotional state + the typing data it's derived from).

Phase 10 is deliberately **cosmetic only**:

- **No `applicationId` rename** — that triggers a one-time `ikd.db` migration on first launch (the database path is package-tied) and would force a Play Store re-listing. Both are out of scope; the package stays at `org.fossify.keyboard`.
- **No fastlane / store-listing work** — public-facing artefacts (full description, screenshots, locale titles, `versionName` bump) are deferred to a separate "release-readiness" phase.
- **No new `colorPrimary` palette** — the existing 19 Material-700 launcher colours are kept as-is; the user explicitly called the palette "versatile" and worth retaining.
- **No `IkdDatabase` migration** — schema stays at version 3.

That leaves the smallest change set that gives the app a coherent on-device identity: name, About screen, launcher foreground glyph.

---

## 2. Branch & Layering Discipline

Read-side / cosmetic only. No keyboard-layer reopen, no schema migration, no new Gradle dependency.

### Reopened files

| File | Why reopened | Edit shape |
|---|---|---|
| `app/src/main/res/values/donottranslate.xml` (line 4) | Brand name change | `app_name` "Fossify Keyboard" → "MoodScript" |
| `app/src/main/res/values/strings.xml` (lines 3–4) | Launcher label and onboarding redirection note | `app_launcher_name` "Keyboard" → "MoodScript" with `translatable="false"`; `redirection_note` body updated to use "MoodScript" |
| `app/src/main/res/values-*/strings.xml` (78 locale folders) | Locale overrides of `app_launcher_name` and `redirection_note` carry the old brand | Delete `app_launcher_name` overrides everywhere (brand names should not translate); replace "Fossify Keyboard" → "MoodScript" *inline* in the existing localised `redirection_note` translations (the surrounding sentence stays in the locale's language; only the brand token changes) |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | New launcher glyph | Full replacement — Option F design (centred 220×110 keyboard outline + three concentric heart contours, white-on-coloured-background). Vector below in §4 |
| `app/src/main/res/drawable/ic_launcher_monochrome.xml` | Themed-icon variant | Full replacement — same path data as the foreground but with `fillColor`/`strokeColor` removed so Android applies the themed-icon tint at runtime |
| `app/src/main/AndroidManifest.xml` (lines 102–106) | Point AboutActivity at the new local override | `android:name="org.fossify.commons.activities.AboutActivity"` → `android:name="org.fossify.keyboard.activities.AboutActivity"`. All other attributes unchanged |
| `README.md` (lines 1, 3, 49) | Repo-level brand surface | Heading: "MoodScript — Behavioural-Analytics Keyboard". Lead paragraph reframed to introduce MoodScript while preserving the existing `[Fossify Keyboard]` upstream link as credit |

### New files

| File | Purpose |
|---|---|
| `app/src/main/kotlin/org/fossify/keyboard/activities/AboutActivity.kt` | Local override extending `org.fossify.commons.activities.AboutActivity`. Injects MoodScript-led intro copy and a small Fossify upstream credit at the bottom. The implementer reads the Commons `AboutActivity` source first to pick the cleanest extension point (override `getIntroText()` if it exists; otherwise override `onCreate` and reset two `TextView`s after `super.onCreate`) |

### Still forbidden (everything earlier phases froze)

| File | Reason |
|---|---|
| All capture-path code (`SimpleKeyboardIME.kt`, `LiveCaptureSessionStore.kt`, `KinematicSensorHelper.kt`, `IkdRetentionWorker.kt`) | Frozen since Phase 7.1 closed |
| All schema (`databases/IkdDatabase.kt`, `models/IkdEvent.kt`, `models/SensorSample.kt`, `models/SessionRecord.kt`, `models/MoodEntry.kt`) | No migration. `IkdDatabase.version` stays at 3 |
| All read pipelines (`IkdAggregator`, `IkdSessionStatsLoader`, `IkdSessionChartLoader`, `IkdMoodLoader`, `IkdMoodAggregator`, plus the Phase 9 sub-phase aggregators) | Phase 9 surface — frozen |
| All chart wrappers (`views/IkdLineChartView.kt`, `views/IkdStackedBarChartView.kt`, `views/IkdHeatmapView.kt`, `views/IkdBubbleMapView.kt`) | Frozen at their current API |
| All activity surfaces other than `AndroidManifest.xml`'s AboutActivity entry (`DashboardActivity`, `EventFeedActivity`, `IkdSettingsActivity`, `DiagnosticsActivity`, `SessionsListActivity`, `MyKeyboardView`) | Frozen — Phase 10 changes the brand, not the UI flow |
| `helpers/IkdCsvWriter.kt` | CSV data contract is frozen |
| `helpers/Constants.kt`, `helpers/Config.kt` | No new prefs |
| `app/build.gradle.kts`, `gradle.properties` | `applicationId` and `versionName` stay |
| The 19 `mipmap-anydpi-v26/ic_launcher_*.xml` adaptive-icon files | Untouched. They reference `@drawable/ic_launcher_foreground` — replacing that single drawable updates all 19 variants automatically |
| The 19 `<activity-alias>` entries in `AndroidManifest.xml` (lines 114–377) | Untouched. The icon picker keeps working with all 19 colour variants |
| `SimpleActivity.getAppIconIDs()` at `activities/SimpleActivity.kt:7–27` | Untouched. All 19 mipmap IDs still resolve, all 19 still selectable in Settings → Customize Colors → App icon |
| `app/src/main/res/values/ic_launcher_background.xml` | The default green (`#106D20`) stays. Variants get their own `@color/md_*_700` references via Commons |
| `LICENSE` | **MUST NOT be edited** (legal) |
| `fastlane/metadata/**` | Out of scope (deferred to release-readiness phase) |

### Branch hygiene

- One focused commit per logical change: (a) docs/plan + identity mockup, (b) name strings + locale clean-up, (c) launcher foreground + monochrome, (d) AboutActivity override + manifest wiring, (e) README refresh.
- No push, no PR opened by the implementer. User reviews locally first.

---

## 3. Locked decisions

| # | Decision | Value |
|---|---|---|
| 1 | App name | **MoodScript** — Mood (emotional state) + Script (typing/keystroke data). Concise, brand-friendly, unique in app stores. Earlier candidates (Moodly, Cogitactus, KeyInsights, TypeReflect, Inflect) ruled out — Moodly is taken; the others felt either too clinical or too generic |
| 2 | Scope | Cosmetic only: name + launcher glyph + About screen. No applicationId, no fastlane, no DB migration, no `colorPrimary`, no fresh palette |
| 3 | Database migration | None. `ikd.db` stays at the existing path under `org.fossify.keyboard` |
| 4 | Launcher palette | Keep all 19 Material-700 colour variants. Icon picker (Settings → Customize Colors → App icon) untouched |
| 5 | Launcher glyph | **Option F** — centred 220×110 Fossify keyboard glyph (outline + 5×2 dot keys + spacebar pill, all white) cradled by **three concentric heart outlines** at scales 1.00 / 1.25 / 1.50 and stroke alphas 0.7 / 0.45 / 0.27. Both brand pillars in one mark: keyboard says "this is a keyboard app", concentric hearts say "what it tracks is your wellbeing". The full visual is in [`Phase10_Identity_Mockup.html`](Phase10_Identity_Mockup.html) |
| 6 | Icon foreground constraint | White-on-coloured. The foreground vector renders against any of the 19 Material-700 background colours, so no coloured glyph elements |
| 7 | About screen strategy | Local override extending Commons `AboutActivity`. New `org.fossify.keyboard.activities.AboutActivity.kt` injects MoodScript-led intro + small Fossify upstream credit at the bottom |
| 8 | Locale handling for the brand name | `app_launcher_name` becomes `translatable="false"` in the base `strings.xml`, and **all 78 locale `app_launcher_name` overrides are deleted** (brand names do not translate). `redirection_note` keeps its locale translations; only the embedded brand token "Fossify Keyboard" is replaced with "MoodScript" inline in each locale |
| 9 | Repo-level docs | `README.md` updated to lead with MoodScript while preserving the upstream Fossify link. `LICENSE` untouched. `CHANGELOG.md` / `BUILDING.md` / `CODEOWNERS` spot-checked for Fossify mentions; the explore agent found no user-visible occurrences, so likely no edits |

---

## 4. The Option F vector drawable

The full design is locked in the visual mockup at [`Phase10_Identity_Mockup.html`](Phase10_Identity_Mockup.html). The Android-vector translation:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="512" android:viewportHeight="512">

    <!-- Outer heart - faintest, scale=1.50 -->
    <path
        android:strokeColor="#FFFFFF"
        android:strokeWidth="9"
        android:strokeAlpha="0.27"
        android:strokeLineJoin="round"
        android:fillColor="#00000000"
        android:pathData="M256,459 C256,459 37,301 37,166 C37,91 101,53 172,53 C217,53 247,80 256,110 C265,80 295,53 340,53 C411,53 475,91 475,166 C475,301 256,459 256,459 Z" />

    <!-- Middle heart, scale=1.25 -->
    <path
        android:strokeColor="#FFFFFF"
        android:strokeWidth="11"
        android:strokeAlpha="0.45"
        android:strokeLineJoin="round"
        android:fillColor="#00000000"
        android:pathData="M256,425 C256,425 74,294 74,181 C74,119 127,87 186,87 C224,87 249,110 256,135 C264,110 289,87 326,87 C385,87 439,119 439,181 C439,294 256,425 256,425 Z" />

    <!-- Inner heart - brightest, scale=1.00 -->
    <path
        android:strokeColor="#FFFFFF"
        android:strokeWidth="13"
        android:strokeAlpha="0.7"
        android:strokeLineJoin="round"
        android:fillColor="#00000000"
        android:pathData="M256,391 C256,391 110,286 110,196 C110,146 153,121 200,121 C230,121 250,139 256,159 C262,139 282,121 312,121 C359,121 402,146 402,196 C402,286 256,391 256,391 Z" />

    <!-- Keyboard outline (220x110, centred at viewport, rx=12) -->
    <path
        android:strokeColor="#FFFFFF"
        android:strokeWidth="12"
        android:fillColor="#00000000"
        android:pathData="M158,201 L354,201 Q366,201 366,213 L366,299 Q366,311 354,311 L158,311 Q146,311 146,299 L146,213 Q146,201 158,201 Z" />

    <!-- Row 1 dot keys (5 circles, r=7, y=225) -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M183,225 a7,7 0 1 0 14,0 a7,7 0 1 0 -14,0 Z
                         M216,225 a7,7 0 1 0 14,0 a7,7 0 1 0 -14,0 Z
                         M249,225 a7,7 0 1 0 14,0 a7,7 0 1 0 -14,0 Z
                         M282,225 a7,7 0 1 0 14,0 a7,7 0 1 0 -14,0 Z
                         M315,225 a7,7 0 1 0 14,0 a7,7 0 1 0 -14,0 Z" />

    <!-- Row 2 dot keys (y=258) -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M183,258 a7,7 0 1 0 14,0 a7,7 0 1 0 -14,0 Z
                         M216,258 a7,7 0 1 0 14,0 a7,7 0 1 0 -14,0 Z
                         M249,258 a7,7 0 1 0 14,0 a7,7 0 1 0 -14,0 Z
                         M282,258 a7,7 0 1 0 14,0 a7,7 0 1 0 -14,0 Z
                         M315,258 a7,7 0 1 0 14,0 a7,7 0 1 0 -14,0 Z" />

    <!-- Spacebar pill (y=287, h=10, rx=5) -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M225,287 L287,287 Q292,287 292,292 Q292,297 287,297 L225,297 Q220,297 220,292 Q220,287 225,287 Z" />

</vector>
```

The monochrome variant is the same vector with `android:fillColor` and `android:strokeColor` attributes removed (Android tints the drawable automatically when used as the themed-icon foreground).

---

## 5. About screen content

The new `AboutActivity` extends Commons. Implementer chooses the cleanest extension point after reading the Commons source:

- If Commons exposes `getIntroText()` / `getAppDescription()` overrides, use them.
- Otherwise, override `onCreate(...)` and re-bind two `TextView`s after `super.onCreate(savedInstanceState)`.

Copy to display:

> **MoodScript** — A research-grade behavioural-analytics keyboard.
>
> MoodScript captures keystroke timing, kinematic sensor data, and self-reported mood signals during everyday typing, then turns them into private on-device insights about anxiety, routine, and wellbeing. All data stays on this device — nothing is transmitted over the network.
>
> *Built on [Fossify Keyboard](https://github.com/FossifyOrg/Keyboard), an open-source privacy-focused Android keyboard. All upstream features remain functional.*

Strings live in `app/src/main/res/values/strings.xml` under new keys (e.g., `moodscript_about_intro`, `moodscript_about_credit`). The credit line is `translatable="false"` for brand-token consistency; the rest can translate normally.

---

## 6. Verification

End-to-end on-device smoke test after implementation:

1. **Clean build:**
   ```powershell
   $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
   ./gradlew clean assembleCoreDebug
   ```
   - No new lint baseline failures.
   - Detekt count flat or net-negative (we are deleting locale `app_launcher_name` overrides, not adding code).

2. **Install:**
   ```powershell
   ./gradlew installCoreDebug
   ```

3. **Verify on device:**
   - Launcher: app shows as **MoodScript** with the new icon (default green-background variant).
   - Settings → Customize Colors → App icon: all 19 variants still selectable; each one shows the new keyboard-cradled-by-hearts glyph against its own background colour.
   - System keyboard picker (long-press space): MoodScript appears.
   - Settings → About: new MoodScript-led copy with Fossify upstream credit at the bottom.
   - First-launch redirection notice: reads "Please enable MoodScript on the next screen…" (was "Please enable Fossify Keyboard on the next screen…").

4. **Locale spot-check:** switch device to Spanish, Portuguese, German, Japanese, Chinese (zh-CN). Confirm the launcher label reads "MoodScript" in all five (not the old translated "Teclado" / "Tastatur" / etc.). Any stale label means that locale's `app_launcher_name` override wasn't deleted.

5. **Schema verification:** `IkdDatabase.version` still equals 3. No migrations added. The Insights dashboard from earlier phases opens with existing `ikd.db` data intact — confirms no inadvertent data loss.

---

## 7. Out of scope (deferred)

Documented here so the implementer doesn't expand the diff:

- **`applicationId` rename** (`org.fossify.keyboard` → `org.moodscript.keyboard` or similar). Triggers `ikd.db` path migration on first launch and Play Store re-listing. Both deferred to a future "release-readiness" phase.
- **Fastlane metadata** (`fastlane/metadata/android/*/full_description.txt`, screenshots, `title.txt`, locale-specific store text). Deferred — Phase 10 is on-device only.
- **New `colorPrimary` token** for theme-wide accent change. Postponed — the existing palette is intentionally retained.
- **Recolouring or culling the 19 launcher variants.** They stay as-is.
- **`versionName` bump to 1.0.0 / 2.0.0** as a public-debut marker. Deferred to release-readiness.
- **Repo-level renames** beyond `README.md`. `KeyboardSA` directory name, `org.fossify.keyboard` Kotlin package, gradle module names — all kept. The brand change is user-facing only.

---

## 8. Files summary

**Modified (8):**
- `app/src/main/res/values/donottranslate.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-*/strings.xml` (~30–60 locale files for `app_launcher_name` deletion + `redirection_note` brand token replacement; exact count surfaced by `Grep` for `app_launcher_name` and `Fossify Keyboard` across `values-*/`)
- `app/src/main/res/drawable/ic_launcher_foreground.xml`
- `app/src/main/res/drawable/ic_launcher_monochrome.xml`
- `app/src/main/AndroidManifest.xml`
- `README.md`

**Created (1):**
- `app/src/main/kotlin/org/fossify/keyboard/activities/AboutActivity.kt`

**Untouched (deliberately):**
- `app/build.gradle.kts`, `gradle.properties` (`applicationId`, `versionName`)
- 19 `mipmap-anydpi-v26/ic_launcher_*.xml` adaptive-icon files
- 19 `<activity-alias>` entries in `AndroidManifest.xml`
- `SimpleActivity.getAppIconIDs()` and the icon-picker mechanism
- All capture-path / schema / read-pipeline / chart-wrapper / activity surfaces
- `LICENSE`, `CHANGELOG.md`, `BUILDING.md`, `CODEOWNERS`
- `fastlane/metadata/**`
