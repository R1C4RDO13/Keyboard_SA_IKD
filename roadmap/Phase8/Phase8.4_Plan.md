# Phase 8.4 — Mood-Bar Dance + Haptic on Slot Select

**Status:** Implemented — landed on `main` (commit `7e8895ab`, `feat(phase8.4): mood-bar emoji dances on select with subtle haptic`).
**Depends on:** Phase 8 (mood bar exists), Phase 8.1 polish (`clipChildren="false"` on `mood_bar`), Phase 8.2 (selected-glyph scaling at `1.25×`, `vibrateIfNeeded()` already wired into every slot click handler)
**Branch:** landed directly on `main` (single commit, single file)
**Scope (one sentence):** When the user **selects** a slot on the mood bar (privacy or one of the six emotions), the tapped emoji plays a short pop + wiggle animation while the existing keyboard haptic fires; deselect taps remain silent (preserves Phase 8.2 Decision that "the highlight change is the feedback").

> **Naming note.** Phase 8.1 = post-merge UI polish in `Phase8_Plan.md` §12. Phase 8.2 = mood-bar UX polish (toggles + capsule + chat-bubble) in `Phase8_Plan.md` §13. Phase 8.3 = stacked-bar mood-mix dashboard chart in `Phase8.3_Plan.md`. Phase 8.4 sits as a standalone plan beside 8.3 because it is a contained UI feature with its own verification surface.

---

## 1. Why this change

The mood-bar select-tap currently surfaces three feedback signals: the slot's `scaleX/scaleY` jumps to `1.25×` (Phase 8.2), the chat-bubble popup appears (Phase 8.2, gated on `Config.showMoodPopup`), and the keyboard haptic fires via `vibrateIfNeeded()` (Phase 2 baseline, gated on `Config.vibrateOnKeypress`). The pick is functionally clear but visually static. Adding a brief celebratory animation — pop bigger, wiggle, settle — makes the mood-tap feel rewarding and reinforces that something was recorded, without changing what is recorded.

The haptic is **not** new; it is already wired. Phase 8.4 reuses it untouched. The only new code is one private animator method and two single-line wires.

---

## 2. Branch & Layering Discipline

**Read-side / UI-only.** No keyboard-layer reopen beyond `MyKeyboardView.kt` (which Phase 8 + 8.2 have already opened for mood-bar UI work). No schema migration (`IkdDatabase.version` stays at 3). No new dependency. No new pref keys. No CSV format change.

### Reopened files

| File | Why | Edit shape |
|---|---|---|
| `views/MyKeyboardView.kt` | Mood-bar UI surface — same file Phase 8.2 worked in | Add one private method `animateMoodSlotDance(slot, settleScale)`, one companion-object constant `MOOD_BAR_SCALE_DANCE_PEAK = 1.6f`, one nullable field `currentMoodDanceAnimator: AnimatorSet?`, and two single-line call sites in `onMoodSlotClicked` (one per select-branch). Net ~35 lines. |

### Still forbidden (everything 8.3 froze, plus the rest of Phase 8 surface outside the mood-bar UX)

| File | Reason |
|---|---|
| `services/SimpleKeyboardIME.kt`, `helpers/LiveCaptureSessionStore.kt`, `helpers/KinematicSensorHelper.kt`, `helpers/IkdRetentionWorker.kt` | Capture path — frozen since Phase 7.1 |
| `databases/IkdDatabase.kt`, `models/MoodEntry.kt`, `models/IkdEvent.kt`, `models/SensorSample.kt`, `models/SessionRecord.kt` | No schema change |
| `helpers/IkdMoodBarController.kt`, `helpers/IkdMoodLoader.kt`, `helpers/IkdMoodAggregator.kt`, `helpers/MoodEmoji.kt`, `interfaces/MoodDao.kt` | Mood data + read surface — frozen |
| `helpers/KeyboardFeedbackManager.kt` | Haptic helper — reused via existing `vibrateIfNeeded()` call site, **never edited** |
| `res/layout/keyboard_view_keyboard.xml` | `clipChildren="false"` already set in Phase 8.2; no XML edit needed |
| `helpers/Constants.kt`, `helpers/Config.kt` | No new prefs |
| `activities/IkdSettingsActivity.kt`, `res/layout/activity_ikd_settings.xml` | No new settings row |
| Any Phase 9 surface (dashboard, aggregators, sub-plans) | Phase 9 surface — completely unrelated |
| `helpers/IkdCsvWriter.kt` | CSV format unchanged |

### Branch hygiene

- Single commit on a one-file branch. Title: `feat(phase8.4): mood-bar emoji dances on select with subtle haptic`.
- After acceptance, leave the branch local for the user to review — **no push, no PR opened by the implementer.**

---

## 3. Animation spec

Three-phase envelope, ~360 ms total. All transforms are on the tapped slot view (a `TextView`); no layout-param changes.

| Phase | Duration | Property | Values |
|---|---|---|---|
| **Pop** | 0–120 ms | `scaleX`, `scaleY` | `settleScale` → `MOOD_BAR_SCALE_DANCE_PEAK = 1.6f` (eased via `AccelerateDecelerateInterpolator`) |
| **Wiggle (rotation)** | 120–340 ms | `rotation` | `0f → -12f → +12f → -8f → +8f → 0f` (5-keyframe `ObjectAnimator.ofFloat`) |
| **Wiggle (jitter)** | 120–340 ms | `translationX` | `0f → -3 dp → +3 dp → -2 dp → +2 dp → 0f` (parallel to rotation) |
| **Settle** | 340–360 ms | `scaleX`, `scaleY` | `1.6f` → `settleScale` (eased) |

`AnimatorSet.playTogether(...)` for the parallel rotation + jitter inside the wiggle phase. `AnimatorSet.playSequentially(pop, wiggle, settle)` for the three-phase envelope.

**Settle scale** is `MOOD_BAR_SCALE_SELECTED = 1.25f` for both call sites (deselect-path doesn't dance — Decision #1 below). The parameter is kept on the helper signature for clarity and future-proofing.

**Defensive cleanup on animation end:** explicitly set `slot.rotation = 0f`, `slot.translationX = 0f`, `slot.scaleX = slot.scaleY = settleScale` so a cancelled animation cannot leave the view stuck mid-wiggle.

**Cancellation:** before starting, `slot.animate().cancel()` on the view + `currentMoodDanceAnimator?.cancel()` on the field. The animator is held in a private `var currentMoodDanceAnimator: AnimatorSet? = null` field, nulled in `onDetachedFromWindow`. Rapid double-taps of the same slot, or selecting a different slot mid-animation, both resolve cleanly with the previous slot snapping to `MOOD_BAR_SCALE_DIMMED = 1.0f` via the existing `applyMoodBarHighlight` cleanup, and the new slot starting fresh.

**Implementation precedent.** The `AnimatorSet` + `ObjectAnimator.ofFloat(...)` shape with `AccelerateDecelerateInterpolator()` mirrors the existing clipboard show/hide animation at `MyKeyboardView.kt:1324–1351`. Same imports, same tone.

---

## 4. Wire points

`onMoodSlotClicked(slot, anchor)` lives at `MyKeyboardView.kt:675–701` and has four branches. Phase 8.4 adds **one line** to the two **select** branches only:

```kotlin
// Privacy-select branch (around :685, after showMoodBubble)
animateMoodSlotDance(anchor, MOOD_BAR_SCALE_SELECTED)

// Emotion-select branch (around :696, after showMoodBubble)
animateMoodSlotDance(anchor, MOOD_BAR_SCALE_SELECTED)
```

The two **deselect** branches (`alreadySelected == true`) get **no** new line — Decision #1 below.

The existing `vibrateIfNeeded()` call at `MyKeyboardView.kt:633` and `:652` runs on every tap regardless of branch and is **not modified**.

---

## 5. Decisions

| # | Topic | Decision |
|---|---|---|
| 1 | Dance trigger | **Only on select-path.** Deselect stays silent — preserves Phase 8.2's "highlight change is the feedback" rule for deselect, and matches the chat-bubble's select-only behaviour. |
| 2 | Vibration | **Reuse `vibrateIfNeeded()`** — already wired on every mood-slot tap. Respects `Config.vibrateOnKeypress`. **Zero new vibration code.** |
| 3 | Shake shape | **Rotation + horizontal jitter combined.** Most expressive option; the parallel `playTogether` keeps the animator code compact. |
| 4 | Total duration | **~360 ms.** Pop 120 ms + wiggle 220 ms + settle 20 ms. Long enough to read as a "dance"; short enough to never block another tap meaningfully. |
| 5 | Peak scale | **`1.6f`** above the resting `1.25f`. Visually distinct from the static highlight without breaking the bar's silhouette. |
| 6 | Settle scale parameter | **Kept on the helper signature** even though both current call sites pass `MOOD_BAR_SCALE_SELECTED`. Lets a future deselect-dance variant pass `1.0f` without touching the animator code. |
| 7 | XML changes | **None.** `clipChildren="false"` already set on `mood_bar` and `toolbar_holder` in Phase 8.2. The 1.6× peak fits within the existing un-clipped envelope. |
| 8 | New strings / resources / dimens | **None.** All numeric tuning lives on the companion object. No translatable strings introduced. |
| 9 | Animation enable/disable pref | **None.** Consistent with project precedent (Phase 6's chevron rotation, Phase 8.2's chat-bubble fade — neither has a pref). If Android's system "Reduce motion" accessibility flag is honoured by `View.animate()` defaults, that suffices. |
| 10 | Theme/locale change mid-animation | **No special handling.** The animator targets `scaleX/scaleY/rotation/translationX` only; theme tinting goes through `applyMoodBarTint()` on a different path. They cannot collide. |

---

## 6. Acceptance Criteria

- [ ] `./gradlew assembleCoreDebug` succeeds.
- [ ] `./gradlew detekt` and `./gradlew lint` clean — no new warnings.
- [ ] Tapping an unselected emoji slot plays the pop + wiggle, settles at `1.25× scale`, and the chat bubble appears (when `Config.showMoodPopup` is on).
- [ ] Tapping the privacy 🛡️ slot when privacy is off plays the same dance; finalises any in-flight session via the existing `IkdMoodBarController.enablePrivacyAndClearMood()` path.
- [ ] Tapping a **highlighted** slot (deselect) does **not** dance — it just dismisses the bubble and clears the highlight via the existing path.
- [ ] Rapid double-tap of the same slot resolves cleanly: the second tap cancels the first dance; the view ends at `scaleX/scaleY = 1.25`, `rotation = 0`, `translationX = 0`.
- [ ] Switching from one selected emoji to another: the previous slot snaps to `1.0×` via `applyMoodBarHighlight`, the newly selected slot dances. No visual stutter.
- [ ] Vibration fires on every tap iff `Config.vibrateOnKeypress` is on (unchanged from Phase 8.2). Disabling that setting silences the haptic; the dance still plays.
- [ ] No edits to any file outside `views/MyKeyboardView.kt`. Verifiable via `git diff main..feat/phase8.4-mood-dance --name-only`.
- [ ] Net diff ≤ ~50 lines.

---

## 7. Verification (manual)

1. `./gradlew installCoreDebug`. Open any text field; bring up the keyboard.
2. **Golden path:** tap 😊 → pops to ~1.6×, wiggles ±12° / ±3 dp, settles at 1.25×. Bubble appears. Phone vibrates briefly.
3. **Switch:** tap 😠 → 😊 snaps to 1.0×, 😠 dances, bubble re-shows for "I'm feeling angry".
4. **Deselect:** tap 😠 again → bubble dismisses, 😠 settles to 1.0×, **no dance**.
5. **Privacy switch:** tap 🛡️ from a no-mood state → 🛡️ dances; capture stops; tap 🛡️ again → silent (deselect-path).
6. **Vibration off:** disable "Vibrate on keypress" in app settings → repeat all of the above; dance still plays, no haptic.
7. **Rapid double-tap:** double-tap 😊 quickly → second dance cancels first; view ends in clean state.
8. **Theme switch mid-animation:** toggle between light/dark theme while spamming mood taps → bar background re-tints correctly via `applyMoodBarTint()`; no animation glitches.
9. **Layout shape:** confirm the `mood_bar` capsule height/width is unchanged. Only child views' transforms animate.

---

## 8. Files touched (summary)

**Modified (1 file):**
- `app/src/main/kotlin/org/fossify/keyboard/views/MyKeyboardView.kt` — one helper method, one constant, one nullable field, two single-line call sites.

**New / deleted:** none.

**Roadmap docs touched alongside this commit:**
- `roadmap/STATUS.md` — Phase 8.4 row.
- `roadmap/FeatureRoadmap.md` — Phase 8.4 section.
- `roadmap/Phase8/Phase8_Plan.md` — Section 14 pointer.
- `CLAUDE.md` — Phase 8.4 architecture note (added on implementation, not on plan).

---

## 9. Risk

Very low. Single-file UI change, ~35 LOC net, no public-API change, animator state explicitly cancellable. Animation runs on the main thread but inside a 360 ms window with default 60 fps timing — well below any frame-budget concern. No data, no schema, no capture-path interaction.
