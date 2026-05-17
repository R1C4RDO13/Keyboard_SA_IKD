# Phase 12 — Mood-Curated Emoji Section in the Drawer

**Status:** Implemented — landed on `main` (commit `bf305176`, `feat(phase12): mood-curated emoji section in the drawer`). Phase 13 then made the standing mood changeable *without closing the drawer* (mid-drawer re-curation).
**Depends on:** Phase 7 (`onEmojiText` capture pipeline), Phase 8 (mood bar + `MoodEmoji` valence taxonomy), Phase 8.5 (`Config.lastMoodScore` standing-mood persistence)
**Branch:** landed directly on `main` (small focused commits per logical change), per recent project hygiene.
**Scope (one sentence):** When the user opens the keyboard's emoji drawer **and a standing mood is currently set** (`Config.lastMoodScore != SCORE_NONE`), prepend a context-aware section at the top of the emoji list containing a curated set of emojis associated with that mood. When no mood is set, the drawer renders unchanged.

> **Phase numbering note.** The original Phase 11 ("Usage Map & Daily Activity Charts") was deleted in Phase 9 (its widgets were absorbed into sub-phases 9.5–9.10). To avoid git-archaeology ambiguity with the strikethrough Phase 11 row in [`STATUS.md`](../STATUS.md), this new mood-emoji feature is numbered **Phase 12**.

---

## 1. Why this change

The mood bar (Phase 8.2) and standing-mood persistence (Phase 8.5) make the user's current emotional state a first-class signal inside the keyboard. The emoji drawer is the most-used adjacent surface, and the standard nine-category layout (Smileys / People / Animals / Food / Travel / Activities / Objects / Symbols / Flags) was not designed around emotion. Phase 12 closes that gap: when a mood is set, the drawer surfaces the emojis most likely to communicate that emotional state, *before* the user has to scroll or search.

Three secondary benefits:

- **Reinforces the mood bar.** The drawer respects the bar's selection — picking a mood now changes what the keyboard offers, not just what the dashboard records.
- **Makes the brand promise visible at the input layer.** The user sees MoodScript adapting to them, not just measuring them.
- **No new privacy surface.** Phase 7's `onEmojiText` capture pipeline already records emoji taps as `EMOJI` events without storing the codepoint; this phase only changes *which* emojis are presented, not what gets stored.

---

## 2. Branch & Layering Discipline

UI-presentation only. No keyboard layer reopen, no schema migration, no Phase 7 capture-pipeline change.

### Reopened files

| File | Why reopened | Edit shape |
|---|---|---|
| `views/MyKeyboardView.kt` | The list of `EmojisAdapter.Item` entries is built here in `setupEmojiPalette` | Read `Config.lastMoodScore` once at palette-open time. If `MoodEmoji.isStandingScore(score)`, prepend a `Category("mood_curated")` item followed by `Emoji` items for each curated codepoint. Otherwise leave the list untouched |
| `helpers/MoodEmoji.kt` | Currently maps score → single emoji + label res. Phase 12 needs score → curated emoji *list* | Add `fun curatedEmojisFor(score: Int): List<String>` returning the curated codepoints in display order. Existing single-emoji functions and the constants stay |
| `helpers/EmojiHelper.kt` | Title resolution for the `mood_curated` pseudo-category | Extend `getCategoryTitleRes` to return a new `R.string.emoji_section_mood_curated` for the `"mood_curated"` key. Optional: `getCategoryIconRes` for an in-list emoji-section icon, but the section header in the drawer is text only — likely no icon edit needed. Verify on-device after the rest is wired |
| `res/values/strings.xml` | New section title | Add `<string name="emoji_section_mood_curated">Mood: %1$s</string>` (formatted to embed the current mood's emoji glyph) and locale fallback. Translatable |

### New files

None. This phase reuses the existing `EmojisAdapter`, `EmojiData`, and `Item.Category` / `Item.Emoji` types.

### Still forbidden (everything earlier phases froze)

| File | Reason |
|---|---|
| All capture-path code (`SimpleKeyboardIME.kt`, `LiveCaptureSessionStore.kt`, `KinematicSensorHelper.kt`, `IkdRetentionWorker.kt`) | Capture pipeline frozen since Phase 7.1 |
| All schema (`databases/IkdDatabase.kt`, `models/IkdEvent.kt`, `models/SensorSample.kt`, `models/SessionRecord.kt`, `models/MoodEntry.kt`) | No migration. `IkdDatabase.version` stays at 3 |
| All read pipelines (`IkdAggregator`, `IkdSessionStatsLoader`, `IkdSessionChartLoader`, `IkdMoodLoader`, `IkdMoodAggregator`) | Phase 9 surface — frozen |
| `helpers/IkdCsvWriter.kt` | CSV format frozen |
| `adapters/EmojisAdapter.kt` | The two-item-type adapter is sufficient. **No new view types required** — the curated section reuses `Item.Category` + `Item.Emoji` |
| `helpers/Config.kt`, `helpers/Constants.kt` | No new prefs (`Config.lastMoodScore` from Phase 8.5 is the data source) |
| `helpers/IkdMoodBarController.kt`, `views/MyKeyboardView.kt` mood-bar wiring | Mood-bar UX unchanged |
| `app/src/main/assets/media/emoji_spec.txt` | Curated emojis are inline Kotlin constants in `MoodEmoji.kt`, not a new asset file |
| `activities/EventFeedActivity.kt`, `activities/DashboardActivity.kt` and their layouts | Per-session and global dashboards unchanged |

### Branch hygiene

- Two focused commits: (a) `MoodEmoji.curatedEmojisFor` + curated lists + string resource; (b) `MyKeyboardView` + `EmojiHelper` wiring with the section-prepend logic.
- No push, no PR opened by the implementer. User reviews locally first.

---

## 3. Locked decisions

| # | Decision | Value |
|---|---|---|
| 1 | Visibility | **Context-aware.** Curated section appears *only when* `Config.lastMoodScore != SCORE_NONE`. No mood = no section, drawer is byte-identical to today |
| 2 | Placement | **Pinned at the top of the emoji list,** above "Recently Used" and the standard categories. No new tab in the category tab strip — keeps the standard nine-tab layout untouched |
| 3 | Section title | `Mood: <emoji>` — e.g. `Mood: 😊`. The emoji glyph reinforces which mood is active without requiring the user to look back at the bar |
| 4 | Section refresh | Curated set is computed once when the emoji palette is opened (`setupEmojiPalette`). If the user changes their mood while the palette is open, the section does **not** re-render until the palette is reopened. (Mid-palette refresh is plausible future work but adds invalidation complexity; v1 ships with the simpler model) |
| 5 | Curated set size | **15–25 emojis per mood.** Big enough to be useful, small enough to fit in one or two rows on a typical keyboard width. Final lists in §4 below |
| 6 | Curated set source | Inline Kotlin constants on `MoodEmoji.kt` — single source of truth for all mood→emoji mappings. Same file already owns the score → bar-emoji mapping; the curated lists are a natural extension |
| 7 | Capture | Existing `onEmojiText(text)` pipeline (Phase 7) handles every tap. Each curated-section tap yields an `EMOJI` event with no codepoint stored. **No new capture event type, no new column** |
| 8 | Privacy | Unchanged. The DB still stores only `eventCategory = "EMOJI"`. The curated-set-per-mood mapping lives in `MoodEmoji.kt` and is never written to disk |

---

## 4. Curated emoji lists per mood

Initial curation (the implementer can refine; v1 ships with these). Aim is **emotionally communicative**, not exhaustive. Order = display order in the curated section.

### Happiness (`SCORE_HAPPINESS`, score = 1)
😊 😄 😁 😀 🙂 🥰 😍 🤗 ☺️ 🥳 😌 🥲 ❤️ 💖 💕 ✨ 🌸 🌞 🌈 🎉 🥂 💫 🦋

### Surprise (`SCORE_SURPRISE`, score = 2)
😲 😯 😮 🤯 🤩 😱 😦 😧 ✨ 💫 🌟 🎇 🤔 ⁉️ ❗ ❓ 🎁

### Disgust (`SCORE_DISGUST`, score = 3)
🤢 🤮 😷 🤧 🥴 😖 😬 😒 🙄 🤨 😑 💩 ⚠️ 🥺

### Sadness (`SCORE_SADNESS`, score = 4)
😢 😭 😞 😔 😟 🙁 ☹️ 💔 🥀 🌧️ 🥹 😪 😩 🫂 🌫️ 💧 ⛈️ 😿

### Fear (`SCORE_FEAR`, score = 5)
😨 😰 😱 😟 😬 🥺 😣 😖 😓 🫨 ⚠️ 🌪️ 🚨 ⚡ 🤞 🙏 🫥

### Anger (`SCORE_ANGER`, score = 6)
😠 😡 🤬 😤 💢 🙄 😒 👿 🔥 ⚡ 💥 🤯 💪 🗯️

> Curation principle: lead with the *face* emoji (matches what the mood bar shows), follow with adjacent face variants, then non-face symbols that carry the same emotional valence (weather, hearts, broken-heart, lightning, etc.). Reserve neutral or ambiguous codepoints for the standard categories — the curated section should be unambiguous about its emotional signal.

---

## 5. Implementation strategy

### 5.1 `MoodEmoji.kt` — new function

```kotlin
// Phase 12: curated emoji lists per Ekman category. Used by the
// emoji drawer to prepend a context-aware section when a standing
// mood is set. Privacy invariant preserved — these lists are
// presentation-only, never written to ikd.db.
fun curatedEmojisFor(score: Int): List<String> = when (score) {
    SCORE_HAPPINESS -> listOf("😊", "😄", "😁", "😀", "🙂", "🥰", "😍", "🤗", "☺️", "🥳", "😌", "🥲", "❤️", "💖", "💕", "✨", "🌸", "🌞", "🌈", "🎉", "🥂", "💫", "🦋")
    SCORE_SURPRISE  -> listOf("😲", "😯", "😮", "🤯", "🤩", "😱", "😦", "😧", "✨", "💫", "🌟", "🎇", "🤔", "⁉️", "❗", "❓", "🎁")
    SCORE_DISGUST   -> listOf("🤢", "🤮", "😷", "🤧", "🥴", "😖", "😬", "😒", "🙄", "🤨", "😑", "💩", "⚠️", "🥺")
    SCORE_SADNESS   -> listOf("😢", "😭", "😞", "😔", "😟", "🙁", "☹️", "💔", "🥀", "🌧️", "🥹", "😪", "😩", "🫂", "🌫️", "💧", "⛈️", "😿")
    SCORE_FEAR      -> listOf("😨", "😰", "😱", "😟", "😬", "🥺", "😣", "😖", "😓", "🫨", "⚠️", "🌪️", "🚨", "⚡", "🤞", "🙏", "🫥")
    SCORE_ANGER     -> listOf("😠", "😡", "🤬", "😤", "💢", "🙄", "😒", "👿", "🔥", "⚡", "💥", "🤯", "💪", "🗯️")
    else -> emptyList()
}
```

### 5.2 `EmojiHelper.kt` — title resolution

```kotlin
fun getCategoryTitleRes(category: String) =
    when (category) {
        "mood_curated"     -> R.string.emoji_section_mood_curated   // NEW
        "smileys_emotion"  -> R.string.smileys_and_emotions
        // ... existing nine cases unchanged
        else -> R.string.recently_used
    }
```

The new string `emoji_section_mood_curated` should accept a single `%1$s` format argument so the activity can substitute the live mood emoji at render time (e.g. `"Mood: 😊"`). The implementer adapts `EmojiCategoryViewHolder.bindView` to pass the formatting argument when the category key is `mood_curated`.

### 5.3 `MyKeyboardView.kt` — list assembly

Locate where `EmojisAdapter` is constructed (in `setupEmojiPalette` per the file's existing structure). Wrap the existing items list with a prepend step:

```kotlin
val score = config.lastMoodScore
val items = buildList<EmojisAdapter.Item> {
    if (MoodEmoji.isStandingScore(score)) {
        add(EmojisAdapter.Item.Category("mood_curated"))
        MoodEmoji.curatedEmojisFor(score).forEach { codepoint ->
            add(EmojisAdapter.Item.Emoji(EmojiData(category = "mood_curated", emoji = codepoint, variants = emptyList())))
        }
    }
    addAll(existingEmojiItems)
}
```

The `EmojiData` constructor stays unchanged (the `category` field is just a key string; existing categories use `"smileys_emotion"` etc., the new pseudo-category is `"mood_curated"`).

### 5.4 Live re-read of mood

`Config.lastMoodScore` is already updated by Phase 8.2's `setMoodForActiveSession` and Phase 8.5's standing-rating wiring. The emoji drawer reads it on each open via `setupEmojiPalette`, so the curated section reflects the latest mood without observer plumbing.

If the user opens the drawer, taps a different mood on the bar (which would re-call `setMoodForActiveSession`), and the drawer is still visible, the section does *not* re-render mid-palette per Decision #4. The next open shows the new mood's curated list. Documented user-facing behaviour: "the curated section refreshes when you reopen the emoji drawer."

---

## 6. Verification

End-to-end on-device smoke test:

1. **Clean build:**
   ```powershell
   $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
   ./gradlew clean assembleCoreDebug
   ```
   No new lint baseline failures. Detekt count flat or net-positive (we are adding ~30 lines of Kotlin and a curated-list constant table).

2. **Install:**
   ```powershell
   ./gradlew installCoreDebug
   ```

3. **Verify behaviour with no mood set:**
   - Tap the privacy 🛡️ slot on the keyboard mood bar (clears any standing mood).
   - Open the emoji drawer.
   - Confirm the drawer renders identically to today: no new section, standard nine categories starting with Recently Used.

4. **Verify behaviour for each of the six moods:**
   - Tap 😊 on the mood bar.
   - Open the emoji drawer.
   - Confirm a `Mood: 😊` section appears at the top, with the curated Happiness list (~23 emojis) before "Recently Used".
   - Tap one of the curated emojis. Confirm it commits to the editor *and* an `EMOJI` event was captured (verify by opening Diagnostics → Event Log; the row's `event_category` should be `EMOJI`).
   - Repeat for 😲, 🤢, 😢, 😨, 😠 — each should show its own curated list with its own header.

5. **Mid-session mood switch:**
   - Tap 😊 → open drawer → confirm Happiness section.
   - Close drawer (without selecting an emoji).
   - Tap 😢 on the bar.
   - Reopen drawer.
   - Confirm the section now reads `Mood: 😢` with the Sadness list. The previous Happiness section is gone.

6. **Schema verification:** `IkdDatabase.version` still equals 3. No migrations added. The Insights dashboard from earlier phases opens with existing `ikd.db` data intact.

7. **Privacy verification:** open the SAF CSV export from `IkdSettingsActivity` after typing a few curated-section emojis. Confirm the timing block contains `EMOJI` rows, not the actual codepoints. The third dual-block `#mood_entries` segment is unchanged. **Curated emoji codepoints must never appear anywhere in the exported CSV.**

---

## 7. Out of scope (deferred)

- **Mid-palette refresh.** Decision #4 — keeping the section static during a single palette-open is the v1 contract. A future micro-phase could observe `Config.lastMoodScore` via `OnSharedPreferenceChangeListener` and re-render via `EmojisAdapter.updateItems`.
- **Per-user curation override.** v1 ships with the inline lists in `MoodEmoji.curatedEmojisFor`. A future phase could expose a settings screen for the user to edit their own curated set per mood. Storage would need a new `mood_curated_emojis` table or a `Config` JSON blob.
- **Curated set localisation.** The curated emoji *codepoints* are universal (Unicode), so the section is locale-neutral. The section *title* ("Mood: 😊") is translatable through `emoji_section_mood_curated`.
- **Animation / transition.** No animation when the curated section appears or disappears between palette opens. Future polish.
- **Curated emojis based on multi-mood history.** v1 keys solely on the current `Config.lastMoodScore`. A more sophisticated future phase could read a mood window (e.g., the last 24 hours of mood entries) and curate based on the dominant or transitioning mood pattern.
- **Recent-curated tracking.** v1 does not track which curated emojis the user actually used. A future phase could add a "Frequently used in this mood" section, but that would require a new lightweight on-device counter (and possibly DB schema), so it is intentionally postponed.

---

## 8. Files summary

**Modified (4):**
- `app/src/main/kotlin/org/fossify/keyboard/views/MyKeyboardView.kt` (one method: `setupEmojiPalette`)
- `app/src/main/kotlin/org/fossify/keyboard/helpers/MoodEmoji.kt` (one new function + the curated-list constants)
- `app/src/main/kotlin/org/fossify/keyboard/helpers/EmojiHelper.kt` (one new branch in `getCategoryTitleRes`)
- `app/src/main/res/values/strings.xml` (one new translatable string with `%1$s`)

**Created (0):** This phase deliberately reuses existing types — `EmojiData`, `EmojisAdapter.Item.Category`, `EmojisAdapter.Item.Emoji`.

**Untouched (deliberately):**
- `adapters/EmojisAdapter.kt` (two view types are sufficient)
- All capture-path / schema / read-pipeline / chart-wrapper / activity surfaces
- `app/src/main/assets/media/emoji_spec.txt` (curated lists are inline Kotlin, not a new asset)
- `helpers/Config.kt`, `helpers/Constants.kt` (no new prefs)
- `helpers/IkdCsvWriter.kt` (CSV contract frozen)
