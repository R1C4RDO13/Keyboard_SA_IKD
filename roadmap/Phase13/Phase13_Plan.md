# Phase 13 — Persistent Right-Anchored Mood Bar (visible inside the emoji drawer)

**Status:** Implemented — landed on `main` (commit `5dd826a5`, `feat(phase13): persistent right-anchored mood bar + insights shortcut`).
**Depends on:** Phase 8 / 8.1 / 8.2 / 8.5 (the existing collapsible mood bar UI + `IkdMoodBarController` + standing-mood persistence), Phase 12 (mood-curated emoji section — Phase 13 makes the user's mood actually *changeable* while the curated section is on screen)
**Branch:** landed directly on `main` (small focused commits per logical change), per recent project hygiene.
**Scope (one sentence):** Move the keyboard's mood bar from its current leading-edge anchor to the **trailing edge** and lift it into an overlay layer above both the regular keyboard toolbar **and** the emoji drawer, so the user can swap their mood without closing the drawer — which in turn re-curates the Phase 12 mood section immediately.

> **Phase numbering note.** Phase 12 (mood-curated emoji section) landed at `bf305176`. This feature picks up from there as Phase 13.

---

## 1. Why this change

The Phase 12 curated section is keyed on `Config.lastMoodScore`. Today, that value can only be flipped via the mood bar in the keyboard's **toolbar** — and the toolbar is hidden the moment the emoji drawer opens (`emoji_palette_holder` constrained to `top_toTopOf="@+id/toolbar_holder"`, covering everything). Result: the user has to **close the drawer, change mood, reopen the drawer** to see a different curated set. That round-trip kills the feature's main promise (Phase 12 §1: "the drawer respects the bar's selection").

Phase 13 makes the mood bar a first-class persistent UI element:

- Visible in **both** the regular keyboard view and the emoji drawer
- Anchored on the **trailing edge** (right side) so it never collides with the emoji drawer's leading-edge close button + label
- Same Phase 8.5 collapsed chip ↔ expanded slots semantics, same Phase 8.4 dance, same Phase 8.2 chat-bubble feedback — purely a re-position + re-layer
- When the user changes mood while the drawer is open, the drawer's adapter rebuilds with the new curated section at the top (Phase 12 §5.3 already reads `Config.lastMoodScore` on every `setupEmojis` call — Phase 13 just adds a refresh trigger)

No new capture-path code, no schema change, no DAO touch, no new aggregator.

---

## 2. Branch & Layering Discipline

UI + view-position only. The Phase 8.5 freeze is largely respected — the mood-bar widget *itself* doesn't get rewired, only its parent and anchors. The emoji drawer's adapter pipeline is reopened in a narrow, additive way.

### Reopened files

| File | Why reopened | Edit shape |
|---|---|---|
| `res/layout/keyboard_view_keyboard.xml` | The `mood_bar` LinearLayout moves out of `toolbar_holder` and becomes a direct child of `keyboard_holder` so it can render above both `toolbar_holder` and `emoji_palette_holder`. Trailing-edge anchor (`end_toEndOf="parent"`). Declared AFTER `emoji_palette_holder` in the XML so it sits on top in z-order. Right-side toolbar items (`voice_input_button`, `pinned_clipboard_items`, `settings_cog`) shift left to make room | Move the `<LinearLayout android:id="@+id/mood_bar">` block. Re-anchor `suggestions_holder.end_toStartOf` to point at the *first* right-side icon now visible (`voice_input_button`), unchanged from today. Re-anchor `settings_cog.end_toEndOf` to `startOf("@+id/mood_bar")` instead of `parent`. The bar gets `app:elevation="4dp"` so the system shadow makes it visually float over the emoji drawer's top bar. `clipChildren="false"` on `keyboard_holder` so the Phase 8.4 dance peak (1.6×) and the Phase 8.2 chat bubble are not clipped by the root |
| `views/MyKeyboardView.kt` | `applyMoodBarVisibility` and `applyMoodBarConstraints` reference `clipboard_clear` and `suggestions_holder` for the **leading-edge** layout — those references need to flip to the **trailing-edge** equivalents. `setupMoodBar` keeps every click handler verbatim. A new private helper `notifyEmojiAdapterMoodChanged()` fires on every successful `onMoodSlotClicked` write so the emoji drawer rebuilds while it's open | `applyMoodBarVisibility`: keep `clipboard_clear` always-visible (no longer covered by the bar), hide-or-show `settings_cog` based on `Config.showMoodBar` (it's the right-side neighbour now). `applyMoodBarConstraints`: re-anchor `suggestions_holder.end_toStartOf` between two states — `startOf(mood_bar)` when the bar is on, `startOf(voice_input_button)` when it's off. `onMoodSlotClicked`: after the controller call resolves, call `notifyEmojiAdapterMoodChanged()` which checks `emojiPaletteHolder.isVisible` and re-runs `setupEmojis()` if so. No new mood-bar fields, no new pref keys |
| `helpers/EmojiHelper.kt` | The `mood_curated:<emoji>` category title rebuilds when the drawer is re-prepared (Phase 12 §5.2). Phase 13 doesn't change `EmojiHelper`, but the curated section's emoji glyph must refresh against the **new** standing score on a mid-drawer mood swap | No edit. Verify behaviour on-device — `getCategoryTitle(context, category)` is called fresh by the adapter on every rebind, so a fresh `setupEmojis` call automatically picks up the new glyph |
| `views/MyKeyboardView.kt` (`setupEmojis`, `prepareEmojiItems`) | Same file as above. The Phase 12 `addCuratedMoodSection(items)` call sits at the top of `prepareEmojiItems` and reads `Config.lastMoodScore` once. Phase 13 needs this to re-fire when mood changes mid-drawer | No change to `prepareEmojiItems` body. The trigger is the new `notifyEmojiAdapterMoodChanged()` helper above, which calls `setupEmojis()` — which already calls `prepareEmojiItems(filteredEmojis)` on `Dispatchers.IO` and posts the new adapter back to the main thread |

### New files

None. No new drawables (the chip uses Phase 8.5's `mood_bar_background`), no new dimens, no new strings, no new pref keys.

### Still forbidden (everything earlier phases froze)

| File | Reason |
|---|---|
| All capture-path code (`SimpleKeyboardIME.kt`, `LiveCaptureSessionStore.kt`, `KinematicSensorHelper.kt`, `IkdRetentionWorker.kt`) | Capture pipeline frozen since Phase 7.1 |
| All schema (`databases/IkdDatabase.kt`, `models/IkdEvent.kt`, `models/SensorSample.kt`, `models/SessionRecord.kt`, `models/MoodEntry.kt`) | No migration. `IkdDatabase.version` stays at 3 |
| All read pipelines (`IkdAggregator`, `IkdSessionStatsLoader`, `IkdSessionChartLoader`, `IkdMoodLoader`, `IkdMoodAggregator`) | Phase 9 surface — frozen |
| `helpers/IkdCsvWriter.kt` | CSV format frozen |
| `adapters/EmojisAdapter.kt` | Two view types remain sufficient. Phase 13 only triggers a rebuild, doesn't change view types |
| `helpers/Config.kt`, `helpers/Constants.kt` | No new prefs. `Config.lastMoodScore` (Phase 8.5) is still the data source. `Config.moodBarExpanded` (Phase 8.5) still persists collapse state |
| `helpers/IkdMoodBarController.kt`, `helpers/MoodEmoji.kt` | Mood data path unchanged; curated lists from Phase 12 unchanged |
| `res/values/strings.xml`, `res/values/dimens.xml` | Existing strings / dimens already cover everything — bar height + button size + chevron + bubble + collapsed-indicator dimens all reused as-is |
| `res/drawable/mood_bar_background.xml`, `res/drawable/ic_chevron_right_vector.xml`, `res/drawable/mood_bubble_background.xml` | Phase 8.2 / 8.5 drawables reused |
| `activities/*` and their layouts | Dashboards / Diagnostics / Sessions unaffected |

### Branch hygiene

- Two focused commits:
  1. `res/layout/keyboard_view_keyboard.xml` — re-position the mood bar to trailing-edge overlay, shift right-side icons, re-anchor `suggestions_holder`
  2. `views/MyKeyboardView.kt` — flip `applyMoodBarVisibility` / `applyMoodBarConstraints` to the trailing-edge convention + add the `notifyEmojiAdapterMoodChanged()` mid-drawer rebuild
- No push, no PR opened by the implementer. User reviews locally first.

---

## 3. Locked decisions

| # | Topic | Decision |
|---|---|---|
| 1 | Switch sides? | **Yes — move to the trailing (right) edge.** The emoji drawer's `emoji_palette_top_bar` has the close arrow + label on the LEADING (left) edge; a left-side mood bar would collide. Trailing edge is empty in both the regular toolbar and the emoji drawer's top bar |
| 2 | Single bar or two bars? | **Single bar instance, lifted to root z-order.** Move `mood_bar` from inside `toolbar_holder` to be a direct child of `keyboard_holder`, declared AFTER `emoji_palette_holder` in the XML so it's drawn on top. Two-bar duplication was rejected (state-sync nightmare; doubles the Phase 8.5 ConstraintSet logic) |
| 3 | Toolbar-icon collision | **Right-side icons shift left.** `settings_cog` (currently `end_toEndOf="parent"`) becomes `end_toStartOf="@+id/mood_bar"`. `pinned_clipboard_items` and `voice_input_button` already chain off `settings_cog` so they cascade naturally. When `Config.showMoodBar = false`, `settings_cog` re-anchors back to `parent` via the same `applyMoodBarConstraints` ConstraintSet flip pattern Phase 8.5 already uses for `suggestions_holder` |
| 4 | Expanded-bar collision with right-side icons | **Hide `voice_input_button` and `pinned_clipboard_items` while the bar is expanded.** Same UX precedent as Phase 8.5 hiding `clipboard_clear` + `suggestions_holder` in the old leading-edge layout. Settings cog stays visible (it's the closest neighbour and the bar's stretched-key background ends short of it). Restored when bar collapses |
| 5 | Bar overlay above emoji drawer's top bar | **Yes, via XML sibling order + elevation.** The mood bar declared as the LAST child of `keyboard_holder` (after `emoji_palette_holder`) lands on top in the natural draw order. `app:elevation="4dp"` adds a Material shadow so the floating capsule reads correctly. `keyboard_holder` gains `android:clipChildren="false"` so the dance peak isn't cropped at the toolbar boundary |
| 6 | Mood-bar visibility when drawer is open | **Always visible.** No state change on `openEmojiPalette` / `closeEmojiPalette` — the bar lives in a sibling layer and isn't affected. Phase 8.5 `applyMoodBarVisibility` keeps gating on `Config.showMoodBar` |
| 7 | Mid-drawer mood change → re-curate | **Yes.** After every `IkdMoodBarController` write completes, if the emoji drawer is currently open, run `setupEmojis()` to rebuild the list with the new curated section at the top. Implementation: a `notifyEmojiAdapterMoodChanged()` helper that checks `keyboardViewBinding!!.emojiPaletteHolder.isVisible` and, if true, calls `setupEmojis()` (which already runs on `Dispatchers.IO` per Phase 12) |
| 8 | Rebuild scroll position | **Scroll to top.** `closeEmojiPalette` already scrolls to position 0 on close (`emojisList.scrollToPosition(0)`). Mid-drawer mood swap also scrolls to 0 so the user sees the new curated section immediately. Otherwise they'd be looking at the same scroll offset of the previous mood's list, which is the worst of both worlds |
| 9 | Suppress rebuild for redundant taps | **No.** Tapping the already-highlighted slot is a *deselect* (Phase 8.2 toggle semantics) and that path also flips the curated section to "no mood" → empty curated list. So every successful controller call should rebuild |
| 10 | Privacy 🛡️ tap mid-drawer | **Rebuild applies.** Tapping privacy finalises the session and clears `Config.lastMoodScore` (Phase 8.5 Decision #16). Curated section disappears on the next rebuild. Capture is off, but the drawer keeps working — `EMOJI` events stop being recorded as expected |
| 11 | First-paint / window-attach state | **Same as today.** Phase 8.5 already calls `applyMoodBarLayout(animate=false)` in `onVisibilityChanged(VISIBLE)` to avoid first-paint flicker. Trailing-edge anchor reuses this verbatim |
| 12 | Animation envelopes | **Unchanged from Phase 8.5 + 8.4.** 150 ms chevron rotation, 200 ms cross-fade, ~360 ms dance, 380 ms post-tap auto-collapse. The Phase 8.5 hotfix that snaps slot α/scale directly (no view.animate) stays — direct property assignment was the only reliable path |
| 13 | RTL languages | **No special handling for this phase.** The bar is anchored `end_toEndOf="parent"` which auto-flips under RTL layouts. Slot order stays valence-ordered LTR. Phase 8 already did not localise slot direction — Phase 13 follows the same precedent |
| 14 | Accessibility | **TalkBack announces the bar before the emoji drawer's content.** The bar lives in a sibling overlay; default accessibility traversal order should land it first because it sits in the trailing edge of the toolbar region. No `importantForAccessibility` overrides — keep the default tree. Verify on-device with TalkBack enabled |
| 15 | Test coverage | **JVM tests cover the controller hooks only.** The `notifyEmojiAdapterMoodChanged()` trigger is a View-level callback wired to existing controller paths — its correctness is covered by the existing `IkdMoodBarControllerTest` (Phase 8) and a new instrumented or screenshot test would be overkill for what is fundamentally a layer-position change. Manual verification per §10 below |

---

## 4. Layout restructure detail

### Current (Phase 8.5)

```
keyboard_holder (ConstraintLayout)
├── toolbar_holder (ConstraintLayout)
│   ├── clipboard_clear           ← leading edge (hidden when bar on)
│   ├── mood_bar                  ← leading edge: chip + slots + chevron
│   ├── suggestions_holder        ← start_toEndOf=mood_bar
│   ├── voice_input_button        ← end_toStartOf=pinned_clipboard_items
│   ├── pinned_clipboard_items    ← end_toStartOf=settings_cog
│   └── settings_cog              ← trailing edge
├── keyboard_view                  ← main typing keys
├── clipboard_manager_holder       ← (overlay)
└── emoji_palette_holder           ← (overlay, top_toTopOf=toolbar_holder)
    ├── emoji_palette_top_bar     ← close arrow + "Emojis" label
    ├── emoji_content_holder      ← emojis_list (RecyclerView)
    └── emoji_palette_bottom_bar  ← mode change + input + backspace
```

### Phase 13

```
keyboard_holder (ConstraintLayout, clipChildren=false)
├── toolbar_holder (ConstraintLayout)
│   ├── clipboard_clear           ← leading edge (always visible now)
│   ├── suggestions_holder        ← start_toEndOf=clipboard_clear,
│   │                                end_toStartOf=voice_input_button
│   ├── voice_input_button        ← end_toStartOf=pinned_clipboard_items
│   ├── pinned_clipboard_items    ← end_toStartOf=settings_cog
│   └── settings_cog              ← end_toStartOf=mood_bar (via ConstraintSet
│                                     when bar on; end_toEndOf=parent when off)
├── keyboard_view                  ← main typing keys
├── clipboard_manager_holder       ← (overlay)
├── emoji_palette_holder           ← (overlay)
└── mood_bar (LinearLayout, elevation=4dp)  ← ★ MOVED — direct child of
    ├── mood_bar_collapsed_indicator           keyboard_holder, anchored
    ├── mood_bar_expanded_slots                end_toEndOf="parent",
    │   ├── mood_bar_privacy                   top_toTopOf="parent",
    │   ├── mood_bar_happiness                 declared LAST so it draws on top
    │   ├── … (six emotion slots)
    │   └── mood_bar_anger
    └── mood_bar_toggle_chevron
```

**Key XML changes:**

1. `keyboard_holder` gains `android:clipChildren="false"` so the dance peak (1.6×) and chat bubble are not cropped.
2. `mood_bar` LinearLayout block lifts out of `toolbar_holder` and lands as a direct child of `keyboard_holder`, **after** `emoji_palette_holder` in declaration order.
3. `mood_bar` constraints: `app:layout_constraintTop_toTopOf="parent"`, `app:layout_constraintEnd_toEndOf="parent"`, `android:layout_marginEnd="@dimen/medium_margin"` (replaces the current `layout_marginStart`). `app:elevation="4dp"`.
4. `settings_cog` constraint: `app:layout_constraintEnd_toStartOf="@+id/mood_bar"` (default XML state). At runtime, when `Config.showMoodBar = false`, `applyMoodBarConstraints` flips it back to `app:layout_constraintEnd_toEndOf="parent"` via the same `ConstraintSet` pattern Phase 8.5 already uses.
5. `suggestions_holder.layout_constraintEnd_toStartOf` flips between `voice_input_button` (when bar off) and `voice_input_button` (when bar on — unchanged because the bar no longer eats suggestions-area width when it's on the right edge). Actually: this anchor stays at `voice_input_button` in both states. The bar's expansion overlaps `voice_input_button` and `pinned_clipboard_items` only when **expanded** — those get hidden via `applyMoodBarVisibility` for that period.

### Runtime visibility states

| State | `voice_input_button` | `pinned_clipboard_items` | `settings_cog` | `clipboard_clear` |
|---|---|---|---|---|
| `Config.showMoodBar = false` | `applyMoodBarVisibility` defaults | always visible | visible (end=parent) | visible |
| Bar on, collapsed (chip) | visible | visible | visible | visible |
| Bar on, expanded | **GONE** (hidden by `applyMoodBarVisibility`) | **GONE** | visible | visible |
| Emoji drawer open | covered by drawer (not touched by mood-bar code) | covered | covered | covered |
| Emoji drawer open AND bar expanded | covered + GONE (no-op) | covered + GONE | covered | covered |

The bar itself is **always above** the emoji drawer thanks to its z-order, regardless of the toolbar items' covered state.

---

## 5. Mid-drawer mood-change trigger

Single helper added to `MyKeyboardView`:

```kotlin
/**
 * Phase 13: when a mood-bar tap changes Config.lastMoodScore (or clears it),
 * and the emoji drawer is currently visible, rebuild the emoji list so the
 * Phase 12 curated section reflects the new mood. Re-runs setupEmojis()
 * which already dispatches its work to Dispatchers.IO and posts the new
 * adapter back to the main thread.
 *
 * Cheap: setupEmojis re-parses the full emoji list off the main thread
 * once per tap; the user's tap-to-bubble-feedback animation runs over
 * ~360 ms which gives the rebuild plenty of slack.
 */
private fun notifyEmojiAdapterMoodChanged() {
    val binding = keyboardViewBinding ?: return
    if (binding.emojiPaletteHolder.isVisible) {
        setupEmojis()
    }
}
```

Call sites — append to each branch of `onMoodSlotClicked` *after* the controller `launch` is dispatched (controller writes Config asynchronously on Dispatchers.IO; the rebuild reads Config inside its own IO dispatch, so the order naturally settles — the rebuild's `prepareEmojiItems` call reads whatever Config holds at *that* moment, not at click time):

```kotlin
in MoodEmoji.SCORE_HAPPINESS..MoodEmoji.SCORE_ANGER -> {
    // ...existing branch body...
    moodController.setMoodForActiveSession(score, anchor)
    notifyEmojiAdapterMoodChanged()       // ★
}
```

Same call appended to the privacy and deselect branches. The single helper centralises the "is the drawer open?" check.

### Why this is cheap

- `setupEmojis()` is already `Dispatchers.IO`-dispatched per Phase 12 — no main-thread blocking
- The full emoji parse is ~2-5 ms on modern devices; curated-section construction is `O(curatedCount)` where curated count is ≤ 23
- The user's tap → bubble animation runs over ~360 ms (dance), giving the rebuild *much* more time than it needs
- No new DAO read, no new DB write, no schema work

### Race-condition note

The controller writes `Config.lastMoodScore` on `Dispatchers.IO`. `notifyEmojiAdapterMoodChanged()` is called immediately after the `launch` line (which only *posts* the work; the write hasn't necessarily completed when the helper fires). The helper's `setupEmojis()` call also runs on IO. Order:

1. **T = 0:** click handler runs. `moodController.setMoodForActiveSession(score, anchor)` *posts* IO work.
2. **T = 0+ε:** `notifyEmojiAdapterMoodChanged()` fires. If drawer is open, *posts* `setupEmojis()` IO work.
3. **T = ~1 ms:** IO dispatcher picks up the controller write. Config updated.
4. **T = ~2 ms:** IO dispatcher picks up the rebuild. Reads the freshly-written Config.

In rare cases (3) and (4) could interleave differently. If `setupEmojis` reads Config *before* the controller writes, the rebuild surfaces the *old* curated section. Mitigations evaluated:

- **A: Suspend `setupEmojis()` until controller completes.** Requires changing controller signature to `suspend` + return Job. Too invasive.
- **B: Trigger rebuild from inside `IkdMoodBarController` after the Config write.** Couples the controller (helper layer) to the view layer. Layering violation.
- **C: Trigger rebuild on `SharedPreferences.OnSharedPreferenceChangeListener`.** `SimpleKeyboardIME` already implements this listener (Phase 8.5 registration at line 204). Adding `LAST_MOOD_SCORE` to its filtered-key array routes the rebuild through the existing event channel, *after* the Config write commits.

**Decision: option C.** `SimpleKeyboardIME.onSharedPreferenceChanged` adds a check for `key == LAST_MOOD_SCORE` and, if so, calls `keyboardView?.notifyEmojiAdapterMoodChanged()` — which becomes a public function (still no-ops when the drawer isn't open). This decouples the rebuild from the click handler, guarantees the rebuild runs *after* the write commits, and aligns with the existing keyboard refresh pattern (theme / language / height changes already use this path). Net cost: 3 lines in `SimpleKeyboardIME`, 1-character visibility change in `MyKeyboardView` (`private fun` → `fun`).

**Forbidden-list note:** `SimpleKeyboardIME.kt` is in the Phase 7.1 capture-path freeze. But the edit here is **strictly the existing preference-listener filter** — a one-line addition to an already-existing `arrayOf(...)` of pref keys that trigger a keyboard refresh. No new behaviour, no new threading, no capture-path touch. Same pattern Phase 8.5 used when it added two narrow IME lifecycle wires (Phase 8.5 §2 "narrow IME lifecycle wires for the inactivity timestamp"). Explicit Phase 13 carve-out.

---

## 6. Acceptance criteria

- [ ] `./gradlew assembleCoreDebug` succeeds
- [ ] `./gradlew detekt` introduces no new issues (baseline = current main)
- [ ] `./gradlew lint` introduces no new warnings
- [ ] `./gradlew testCoreDebugUnitTest` passes (existing suite — Phase 13 adds no new tests)
- [ ] On device, regular keyboard view: mood bar is on the right edge, chip mode by default, expand → tap mood → auto-collapse all work per Phase 8.5
- [ ] Tap the emoji button (mode change) → emoji drawer opens. **Mood bar remains visible on the right edge.**
- [ ] In the emoji drawer with no mood set: no curated section. Tap chip → expand → tap 😊 → drawer rebuilds and shows "Mood: 😊" curated section at the top. Bar auto-collapses showing 😊 chip.
- [ ] Tap a different mood (e.g. 😢) → curated section rebuilds to "Mood: 😢" with the sadness emoji list. Scroll position resets to top.
- [ ] Tap the highlighted mood (deselect) → curated section disappears. Drawer shows just the standard nine categories.
- [ ] Tap privacy 🛡️ → bar shows 🛡️ chip, session finalises, curated section disappears.
- [ ] Close drawer → bar still in right-edge position with the last-selected mood chip. Reopen drawer → curated section reflects the current mood.
- [ ] On RTL locale (Arabic, Hebrew): bar auto-flips to the leading edge (which is the LTR right edge), still works.
- [ ] Phase 8.4 dance plays on tap. Phase 8.2 chat bubble surfaces above the slot. No clipping at the toolbar / drawer top-bar boundary (because `keyboard_holder` and parents are `clipChildren=false`).
- [ ] Rapid taps don't desync the curated section: each tap eventually triggers exactly one rebuild via the `OnSharedPreferenceChangeListener` path.

---

## 7. Risks

| Risk | Mitigation |
|---|---|
| Mood bar overlaps the emoji drawer's top-bar label / search if the label is too wide on a small device | `emoji_palette_label` is left-anchored with `lines="1"` + `ellipsize="end"`. Bar is right-anchored. They can't collide unless the device is *extremely* narrow. Verify on a 320 dp width emulator |
| Existing right-side toolbar icons (`voice_input_button`, `pinned_clipboard_items`) collide with the bar on small devices | Already hidden when bar is expanded (Phase 8.5 precedent). In collapsed state the chip is ~80 dp, leaving room for both icons on a 320 dp screen |
| The `OnSharedPreferenceChangeListener` rebuild path could fire multiple times per tap if Config is written in stages | Controller writes are atomic (a single `.apply()` call per setter). Listener fires once per write. The listener-side `setupEmojis()` call is idempotent — running it twice in quick succession is wasteful but harmless |
| `keyboard_holder` having `clipChildren=false` allows other children to render outside their bounds, potentially leaking elements outside the keyboard's intended region | The only oversized child is the mood bar's dance peak and the chat bubble, both <20 dp of overflow. Same overflow Phase 8.4 already requires via `clipChildren=false` on the toolbar holder; lifting that to `keyboard_holder` is a strictly weaker constraint than what's already in production |
| RTL locales might place the bar awkwardly | `end_toEndOf="parent"` auto-flips to "start" in RTL → bar lands on the leading edge. That happens to *also* be where the LTR-leading emoji-drawer close button lives. **Open issue:** RTL might re-collide with the close button. Worth a follow-up check in §10 manual verification |

---

## 8. Verification (manual)

1. `./gradlew installCoreDebug`. Open any text field; bring up the keyboard.
2. **Regular kbd:** bar shows a `>` chip on the right edge. Suggestions / voice / clipboard / settings cog visible on the right with the bar nestled between suggestions and settings. Tap chip → expands; chip becomes `<`, slot row appears.
3. **Tap 😊:** bubble pops above the slot ("I'm feeling happy"), Phase 8.4 dance plays, bar auto-collapses showing 😊 at full alpha after ~580 ms.
4. **Open emoji drawer** (keyboard's emoji-mode button). The drawer's standard nine categories appear *below* the (visible) mood bar. The bar's `>` chip is on the right of the drawer's top bar.
5. **Top section shows "Mood: 😊"** + curated happiness emoji list (because Phase 12 already wired the curated read).
6. **Tap chip → expanded slots overlay the drawer's top bar's right side** (close + label still visible on the left). Tap 😢 → bubble, dance, bar auto-collapses showing 😢 chip. The drawer **rebuilds**: top section now reads "Mood: 😢" with the sadness emoji list. Scroll position resets to 0.
7. **Tap the highlighted 😢 (deselect):** chip shows the dim placeholder 🙂. The curated section disappears from the drawer.
8. **Tap privacy 🛡️:** bar shows 🛡️ chip. The capture session finalises. The curated section stays gone.
9. **Close drawer (back arrow on left of drawer top bar):** drawer hides; bar remains where it was on the right edge of the regular toolbar.
10. **`Config.showMoodBar = false`** (Settings → "Show mood bar in keyboard" off): bar is GONE; `settings_cog` re-anchors to the right edge of the toolbar.
11. **Theme switch** (light/dark, custom colours): bar's stretched-key background re-tints; chip emoji still renders at full color (Phase 8.2 invariant); chevron tinted via `mTextColor` (Phase 8.5 hotfix).
12. **RTL locale switch** (system language → Arabic / Hebrew): bar should auto-flip to the system's *trailing* edge. If it overlaps with the emoji drawer's close arrow in RTL, log an issue and decide whether to anchor by absolute `right` instead.
13. **Rapid mood toggling while drawer is open:** mash chip → 😊 → 😢 → 😨 in quick succession. Bubble cancellations should be clean, dance animations should sequence, and the curated section should converge on the final mood (with at most one frame of intermediate-state rebuild visible).

---

## 9. Files touched (summary)

**Modified (3 files):**
- `app/src/main/res/layout/keyboard_view_keyboard.xml` — mood-bar reparent + re-anchor, `keyboard_holder` clipChildren=false, settings_cog default anchor, elevation
- `app/src/main/kotlin/org/fossify/keyboard/views/MyKeyboardView.kt` — `applyMoodBarVisibility` / `applyMoodBarConstraints` flip to trailing-edge convention; `notifyEmojiAdapterMoodChanged()` public helper; right-side icons hidden during expanded state
- `app/src/main/kotlin/org/fossify/keyboard/services/SimpleKeyboardIME.kt` — one-line addition to `onSharedPreferenceChanged`'s filter array for `LAST_MOOD_SCORE`, calling `keyboardView?.notifyEmojiAdapterMoodChanged()`

**New (0 files):** none.

**Roadmap docs:**
- `roadmap/Phase13/Phase13_Plan.md` (this file)
- `roadmap/STATUS.md` (append Phase 13 row on implementation)
- `CLAUDE.md` (architecture note added on implementation, mirroring Phase 8.5 / 12 sections)

---

## 10. Risk level

**Low-medium.** Most edits are XML re-anchoring of an already-shipped widget — the mood-bar widget's internal state machine, animations, theming, and accessibility were already validated in Phase 8.2 / 8.4 / 8.5. The new behaviour (mid-drawer rebuild on mood change) is a tiny additive trigger routed through an existing preference-change listener.

**Reversibility:** every edit is a pure layout / view-position swap. No persistent state, no schema, no DAO. Reverting Phase 13 takes the bar back to the leading-edge layout of Phase 8.5 with zero data implications.

**Known follow-ups deliberately deferred:**
- RTL collision check (§7 risk) — verify on device, decide whether to force LTR anchor
- Per-user "mood bar position" setting (left / right / top-of-drawer) — premature; ship the right-side default first
- Animating the bar's *position* between toolbar and emoji-drawer top bar (i.e., not a static overlay but a continuous slide) — visually overkill for what's gained
