# Phase 8.5 — Collapsible Mood Bar with Persistent Standing Rating + Inactivity Reset

**Status:** Planned (no branch yet)
**Depends on:** Phase 8 (mood bar exists, `mood_entries` table), Phase 8.1 (`Config.showMoodBar` toggle), Phase 8.2 (capsule background + chat-bubble + toggle semantics + `IkdMoodBarController.{disablePrivacy,clearMoodForActiveSession}`), Phase 8.4 (planned but **not blocking** — the dance animation runs on the same select-path this plan keeps; if 8.4 lands first the two compose cleanly, if 8.5 lands first 8.4 wires its single-line call site against the same `onMoodSlotClicked` branches)
**Branch (proposed):** `feat/phase8.5-collapsible-mood-bar`
**Scope (one sentence):** Reshape the seven-button mood bar from a centered always-expanded toolbar widget into a **left-anchored, collapsible chip** that remembers the user's last selected mood across sessions and auto-expires that standing rating after one hour of inactivity.

> **Naming note.** Phase 8.4 (Mood-Bar Dance) is concurrent — it is a single-file animation polish that does not touch the bar's layout, persistence, or lifecycle. This plan therefore takes the next sub-phase number (8.5) rather than queueing behind 8.4. The two are compose-safe in either landing order (Decision #11).

---

## 1. Why this change

After Phase 8 / 8.1 / 8.2 shipped, three friction points emerged in daily use:

1. **No memory across sessions.** `mood_entries` is FK + CASCADE-tied to `sessions`; the moment a session ends, the user's selection is gone. Re-tapping the same self-rating every time the keyboard reopens is tedious enough that users stop bothering — and we lose the signal.
2. **Bar dominates the toolbar.** While on, it hides `clipboard_clear` / `suggestions_holder` / `voice_input_button` (Phase 8.2 `applyMoodBarVisibility()`). A user who set their mood five minutes ago doesn't need seven buttons in their face — they need their suggestions back.
3. **Stale ratings linger forever.** A mood set four days ago is still "the user's standing self-rating" until they manually clear it. Privacy-adverse and dilutes the analytical signal.

Phase 8.5 reshapes the bar into a left-anchored chip that:
- Defaults to collapsed (one slot wide); expands on tap to show the full seven slots, collapses back after a selection or chevron tap.
- Remembers the last selected mood across sessions as a **display-only standing rating** in `Config.lastMoodScore`. The DB-side `mood_entries` semantic is unchanged: a row is written **only** when the user explicitly taps a slot in the current session. The standing rating just pre-highlights the chip on re-entry.
- Auto-clears the standing rating after one hour of inactivity (since the last meaningful keyboard use). Reset semantics: clear `Config.lastMoodScore` to NONE and let `Config.privacyModeEnabled` take over via existing `refreshMoodBarFromState()` logic — no force-flip of the privacy flag.

The IKD capture path stays fully frozen. No schema migration (`IkdDatabase.version` stays at 3). No new Gradle dependency. No edits to any aggregator, dashboard, CSV writer, or DAO.

---

## 2. Branch & Layering Discipline

UI + persistence-pref only. Reopens the Phase 8 mood-bar UI surface (already opened by 8.1, 8.2, and the planned 8.4) plus the standing four files for adding pref keys (`Constants.kt` / `Config.kt`) and the IME's start/finish lifecycle hooks for the inactivity timestamp.

### Reopened files

| File | Why reopened | Edit shape |
|---|---|---|
| `views/MyKeyboardView.kt` | Mood-bar UI surface — same file Phase 8 / 8.1 / 8.2 worked in | Biggest delta: collapsed-state indicator rendering; `applyMoodBarLayout(expanded, animate)` mirroring Phase 6's `applySensorExpansion`; `applyMoodBarHighlight` switches from instant transforms to `view.animate()…` chains with cancel-before-restart; runtime `ConstraintSet` re-anchor of `suggestions_holder` for the `Config.showMoodBar == false` case; new tap targets on indicator + chevron |
| `res/layout/keyboard_view_keyboard.xml` | Three-zone mood bar (collapsed indicator + expanded slots + chevron) replaces today's flat seven-`TextView` row; mood bar moves to leading edge | One container, three zones, deterministic widths so `suggestions_holder` doesn't jitter on expand/collapse. Re-anchor `suggestions_holder.start = endOf(mood_bar)` (was `endOf(clipboard_clear)`). `clipboard_clear` stays inflated; visibility flipped at runtime. |
| `helpers/Constants.kt` | Three new pref keys + one timeout constant | `LAST_MOOD_SCORE`, `LAST_MOOD_ACTIVITY_TIMESTAMP`, `MOOD_BAR_EXPANDED`, `MOOD_INACTIVITY_TIMEOUT_MS = 60L * 60L * 1000L` |
| `helpers/Config.kt` | Three new properties | `var lastMoodScore: Int` (default `MoodEmoji.SCORE_NONE`), `var lastMoodActivityTimestamp: Long` (default 0L), `var moodBarExpanded: Boolean` (default `false`) |
| `helpers/MoodEmoji.kt` | One sentinel constant + one helper | `const val SCORE_NONE = 0`; `fun isStandingScore(score: Int) = isValidScore(score)` (readability for the controller / view sites — no behaviour change to `isValidScore`) |
| `helpers/IkdMoodBarController.kt` | Three of the five existing methods grow Config writes alongside their DAO writes. **No new method; no new threading.** | `setMoodForActiveSession`: also write `config.lastMoodScore = score` and `config.lastMoodActivityTimestamp = now`. `clearMoodForActiveSession`: also write `config.lastMoodScore = SCORE_NONE`. `enablePrivacyAndClearMood`: same. `disablePrivacy` and `getMoodForActiveSession` unchanged. |
| `services/SimpleKeyboardIME.kt` | Inactivity timestamp + on-show staleness check | New private `maybeResetStaleMood()` called at the top of `onStartInputView(...)` **before** `LiveCaptureSessionStore.startSession(...)`. One-line `config.lastMoodActivityTimestamp = System.currentTimeMillis()` write inside `onFinishInputView(...)` as a best-effort backstop. **No edits to the IKD capture path** — the helper is a Config read + arithmetic + at-most-one Config write; nothing touches sensors, sessions, or events. |
| `res/values/strings.xml` | Two new strings | `mood_bar_collapsed_placeholder` (the placeholder glyph codepoint, e.g. `🙂`), `mood_bar_toggle_content_description` (chevron + indicator a11y label, e.g. "Expand mood bar" / "Collapse mood bar") |
| `res/values/dimens.xml` | One new dimen if needed | `mood_bar_chevron_size` if the existing icon dimens don't fit the visual budget. Reuse `mood_bar_button_size` for the collapsed indicator's width. |
| `res/drawable/ic_chevron_down_vector.xml` | New vector if no existing chevron asset is reusable | Minimal vector using `?attr/colorControlNormal` — same discipline as the Phase 4 sensor-mode toggle icons |

### Still forbidden (everything Phase 8.4 freezes, plus the rest of Phase 8 surface outside the mood-bar UX)

| File | Reason |
|---|---|
| `helpers/LiveCaptureSessionStore.kt`, `helpers/KinematicSensorHelper.kt`, `helpers/IkdRetentionWorker.kt` | Capture path — frozen since Phase 7.1; the IME edit in this plan is two narrow lifecycle wires, not a capture-path reopen |
| `databases/IkdDatabase.kt`, `models/MoodEntry.kt`, `models/IkdEvent.kt`, `models/SensorSample.kt`, `models/SessionRecord.kt` | No schema change. `IkdDatabase.version` stays at 3 |
| `interfaces/MoodDao.kt`, `interfaces/SessionDao.kt`, `interfaces/IkdEventDao.kt`, `interfaces/SensorSampleDao.kt` | No DAO changes. The standing rating is **display-only**; nothing new gets written or read from the DB |
| `helpers/IkdMoodLoader.kt`, `helpers/IkdMoodAggregator.kt`, `helpers/IkdAggregator.kt`, `helpers/IkdSessionStatsLoader.kt`, `helpers/IkdSessionChartLoader.kt`, all Phase 9 aggregators | Read pipeline — frozen. Phase 8.5 is invisible to every dashboard surface |
| `helpers/IkdCsvWriter.kt` | CSV format unchanged. The third dual-block segment (`#mood_entries`) keeps its same shape |
| `views/IkdLineChartView.kt`, `views/IkdStackedBarChartView.kt`, `views/IkdHeatmapView.kt`, `views/IkdBubbleMapView.kt` | Charting surfaces — unrelated |
| `activities/EventFeedActivity.kt`, `res/layout/activity_event_feed.xml` | Per-session dashboard — Phase 5/8 surface, unchanged |
| `activities/DashboardActivity.kt`, `activities/dashboard/*.kt`, `res/layout/activity_dashboard.xml` | Insights dashboard — Phase 9 surface, unchanged |
| `activities/IkdSettingsActivity.kt`, `res/layout/activity_ikd_settings.xml` | No new settings row. `Config.showMoodBar` (Phase 8.1) and `Config.showMoodPopup` (Phase 8.2) are unchanged; `moodBarExpanded` is user-driven via the chevron, not via a settings switch |
| `activities/DiagnosticsActivity.kt` and its layout / menu / status-chip resources | Phase 6 surface — frozen |
| `helpers/KeyboardFeedbackManager.kt` | Haptic helper — no haptic changes here. Phase 8.4 owns the haptic story |

### Branch hygiene

- One focused commit per logical change. Suggested order (each commit independently buildable, detekt-clean, passing existing tests):
  1. `helpers/Constants.kt` + `helpers/Config.kt` + `helpers/MoodEmoji.kt` (pref keys + properties + SCORE_NONE sentinel)
  2. `helpers/IkdMoodBarController.kt` (Config writes alongside DAO writes — three method bodies grow)
  3. `services/SimpleKeyboardIME.kt` (`maybeResetStaleMood` + `onFinishInputView` timestamp wire)
  4. `res/layout/keyboard_view_keyboard.xml` + `res/values/strings.xml` + `res/values/dimens.xml` + `res/drawable/ic_chevron_down_vector.xml` (layout restructure + new resources)
  5. `views/MyKeyboardView.kt` (collapsed indicator rendering, `applyMoodBarLayout`, animations, runtime `ConstraintSet` for `showMoodBar=false`)
- After acceptance, leave the branch local for the user to review — **no push, no PR opened by the implementer.**

---

## 3. Persistence model — `Config.lastMoodScore` is display-only

The user's intent is "my last mood is my standing self-rating." The cleanest way to honour that without polluting analytics:

- `Config.lastMoodScore` is a **UI-only standing rating**. It pre-highlights the chip on re-entry; it never auto-writes to `mood_entries`.
- A `mood_entries` row is created **only** when the user explicitly taps a slot in the current session (existing `IkdMoodBarController.setMoodForActiveSession` path). Tap-to-confirm: a user who reopens the keyboard, sees their persisted 😊 in the collapsed chip, and taps it once in the expanded bar gets a `mood_entries` row written for the new session — same as today.
- `IkdMoodBarController.clearMoodForActiveSession` and `enablePrivacyAndClearMood` clear `Config.lastMoodScore` to `MoodEmoji.SCORE_NONE` alongside their existing DAO `clearForSession` call. `disablePrivacy` does **not** touch `lastMoodScore` (privacy-off is not a mood selection).
- The activity timestamp is updated on **mood selection** (the meaningful "user is engaged with the bar" signal) and on **`onFinishInputView`** as a best-effort backstop. **It is not updated per-keystroke** — that would mean a SharedPreferences write on the IME thread for every key, which violates the "no DB / I/O on `onKey`" rule (CLAUDE.md "Critical Constraint").

`refreshMoodBarFromState()` reads:

```
val highlight = getMoodForActiveSession()          // suspend; existing path
                ?: config.lastMoodScore.takeIf { isStandingScore(it) }
                ?: NONE
```

Display table:

| Privacy on? | `lastMoodScore` | In-session row | Indicator shows | Alpha |
|---|---|---|---|---|
| true | any | any | 🛡️ | 1.0 |
| false | NONE (0) | none | 🙂 (placeholder) | 0.45 |
| false | NONE | row exists | emoji of row score | 1.0 |
| false | valid score | none | emoji of `lastMoodScore` | 1.0 |
| false | valid score | row exists | emoji of row score | 1.0 |

The "row exists" path always wins over the standing rating — a user who actually tapped a slot in this session sees that selection, not a stale Config value.

---

## 4. Inactivity reset — dual check (best-effort backstop + on-show validation)

One hour (`MOOD_INACTIVITY_TIMEOUT_MS = 60L * 60L * 1000L`) since the last meaningful interaction. Two write sites + two check sites — bounded, cheap, no background timer or `WorkManager` job needed.

**Write sites** (each updates `config.lastMoodActivityTimestamp = System.currentTimeMillis()`):
1. **Mood selection** in `IkdMoodBarController.setMoodForActiveSession`. Selecting a mood is itself the strongest possible "user is engaged" signal.
2. **`SimpleKeyboardIME.onFinishInputView`** — best-effort backstop. If the IME stays attached across input fields without `onFinishInputView` ever firing (rare but possible — IME can persist across `EditText` switches), the on-show check below catches it.

**Check sites** (each runs the same staleness check; if `now - last > MOOD_INACTIVITY_TIMEOUT_MS` and `last > 0L`, set `config.lastMoodScore = MoodEmoji.SCORE_NONE` — that's it; no privacy flip, no DAO writes):
1. **`SimpleKeyboardIME.onStartInputView`**, *before* `LiveCaptureSessionStore.startSession(...)`. Primary check — covers the "user came back to the keyboard after >1 h" case.
2. **`MyKeyboardView.refreshMoodBarFromState`** at the top — covers the long-attached-IME edge case where `onStartInputView` doesn't re-fire but the toolbar refreshes for any other reason (theme change, settings toggle, visibility change).

After the check resets `lastMoodScore`, the rest of `refreshMoodBarFromState` runs as today: `Config.privacyModeEnabled` decides whether the indicator shows 🛡️ (privacy on) or the placeholder 🙂 (privacy off, no standing rating).

**Why selection-not-keystroke for the timestamp:** writing on every `onKey` would put a `SharedPreferences.edit().commit()` on the IME thread per keypress — guaranteed lag. Selection is once per session at most; `onFinishInputView` is once per IME-detach. Both are cold paths. The semantic loss is acceptable: a user who types continuously for two hours without re-touching the mood bar should still have their standing rating expire — they likely set it long enough ago that the rating is stale relative to their current state.

---

## 5. Layout restructure — left-anchored, collapsible chip

`res/layout/keyboard_view_keyboard.xml`. Replace today's flat seven-`TextView` `mood_bar` LinearLayout with a three-zone container (one LinearLayout, three child blocks, deterministic widths so `suggestions_holder` doesn't jitter on expand/collapse):

```xml
<LinearLayout android:id="@+id/mood_bar"
              android:orientation="horizontal"
              android:gravity="center_vertical"
              android:clipChildren="false"
              android:background="@drawable/mood_bar_background">

    <TextView android:id="@+id/mood_bar_collapsed_indicator"
              android:layout_width="@dimen/mood_bar_button_size"
              android:layout_height="@dimen/mood_bar_button_size" />

    <LinearLayout android:id="@+id/mood_bar_expanded_slots"
                  android:orientation="horizontal"
                  android:visibility="gone">
        <TextView android:id="@+id/mood_bar_privacy" … />
        <TextView android:id="@+id/mood_bar_happiness" … />
        <TextView android:id="@+id/mood_bar_surprise" … />
        <TextView android:id="@+id/mood_bar_disgust" … />
        <TextView android:id="@+id/mood_bar_sadness" … />
        <TextView android:id="@+id/mood_bar_fear" … />
        <TextView android:id="@+id/mood_bar_anger" … />
    </LinearLayout>

    <ImageView android:id="@+id/mood_bar_toggle_chevron"
               android:src="@drawable/ic_chevron_down_vector"
               android:rotation="0" />
</LinearLayout>
```

Constraint changes on the parent `toolbar_holder`:
- `mood_bar`: `start=parent`, `end=startOf(suggestions_holder)`, `top/bottom=parent`. Drops `horizontal_bias=0.5`. Replaces `clipboard_clear`'s leading anchor.
- `suggestions_holder`: re-anchor `start=endOf(mood_bar)` (was `endOf(clipboard_clear)`).
- `clipboard_clear`: stays inflated, visibility-hidden via `applyMoodBarVisibility()` whenever the bar is on (no behaviour change vs Phase 8.2).

**Runtime constraint switch when `Config.showMoodBar == false`** (Decision #3): in `MyKeyboardView.applyMoodBarVisibility()`, when the bar is hidden, use a `ConstraintSet` to re-anchor `suggestions_holder.start = endOf(clipboard_clear)` and restore `clipboard_clear` to VISIBLE. Don't drive layout through visibility alone — XML constraints persist when the constrained anchor is GONE, leaving `suggestions_holder` orphaned at the leading edge.

**Why one container + visibility-flip instead of two siblings:** keeps the `mood_bar_background` capsule drawable on a single view (Phase 8.2 stretched-key visual integrity); keeps `clipChildren="false"` on a single ancestor (Phase 8.4 dance won't be clipped); minimises constraint churn — `suggestions_holder` only re-anchors when `Config.showMoodBar` flips, never on expand/collapse.

---

## 6. Collapsed-state rendering & tap targets

`mood_bar_collapsed_indicator` text + alpha is computed every refresh (`refreshMoodBarFromState()`) per the table in §3.

**Tap targets in collapsed mode:**
- Tap the indicator → `Config.moodBarExpanded = true`; `applyMoodBarLayout(expanded=true, animate=true)`. Discoverability: the chip itself is the tap target; users don't have to find the chevron.
- Tap the chevron → same toggle.

**Tap targets in expanded mode** (existing Phase 8.2 semantics, unchanged for the slot taps):
- Tap the chevron → collapse.
- Tap a slot → existing `onMoodSlotClicked(slot, view)` logic runs (privacy / select / deselect / chat-bubble all unchanged). After the controller call resolves, also collapse the bar (`Config.moodBarExpanded = false`, `applyMoodBarLayout(expanded=false, animate=true)`) so the user sees their selection echoed in the chip — fewer steps than "tap mood, then tap chevron to collapse."

**Phase 8.4 compatibility:** the dance animation runs on the slot view *before* the post-tap collapse fires. The collapse cross-fade (200 ms — see §7) starts after the slot's settle phase ends (~360 ms after tap), so the user sees: tap → dance → bar collapses with the new emoji in the chip. If 8.4 isn't merged yet, the collapse just happens immediately after the controller call resolves; the UX still reads correctly.

---

## 7. Animation spec — subtle, with cancel-before-restart

All animations mirror Phase 6's `applySensorExpansion` discipline (150 ms, no overshoot, accept the `animate: Boolean` parameter so first-paint flicker on `onVisibilityChanged(VISIBLE)` is avoidable).

**`applyMoodBarHighlight(slot)`** — selection animation, currently instant:
- Switch from `view.scaleX = target` to `view.animate().cancel(); view.animate().scaleX(target).scaleY(target).alpha(target).setDuration(150L).start()` for each slot.
- The `.cancel()` prevents animator accumulation when refresh fires mid-animation (e.g., privacy toggle during a selection animation).

**`applyMoodBarLayout(expanded, animate)`** — new helper, mirrors `applySensorExpansion`:
- Chevron rotation: `chevron.animate().cancel(); chevron.animate().rotation(target).setDuration(150L).start()`. Targets: `0f` when expanded, `180f` when collapsed.
- Cross-fade between `mood_bar_collapsed_indicator` and `mood_bar_expanded_slots`:
  - When expanding: `collapsed_indicator.animate().alpha(0f).withEndAction { visibility = GONE }`; `expanded_slots.visibility = VISIBLE; expanded_slots.alpha = 0f; expanded_slots.animate().alpha(1f).setDuration(200L)`.
  - When collapsing: reversed — fade slots out, set GONE on end, set indicator VISIBLE + fade in.
- When `animate = false`: set visibility, rotation, and alphas directly. Used on `onVisibilityChanged(VISIBLE)` to avoid first-paint flicker — same pattern as Phase 6.

**`cancelMoodAnimators()`** — single helper that walks the seven slots + indicator + chevron and calls `.animate().cancel()` on each. Called from `onDetachedFromWindow` and at the top of `applyMoodBarLayout` to guarantee a clean starting state.

**Phase 8.4 hand-off:** Phase 8.4 adds a `currentMoodDanceAnimator: AnimatorSet?` field with its own cancel-before-start discipline; that field is independent of the cancellations in this plan. The two animator graphs do not share state — a slot's `view.animate()` chain (this plan) and an `ObjectAnimator` inside an `AnimatorSet` (Phase 8.4) compose cleanly because they target the same view but cancel each other via `view.animate().cancel()` (which also cancels the dance's pop/wiggle/settle steps that run via `ObjectAnimator.ofFloat(view, …)`). Decision #11.

---

## 8. Decisions

| # | Topic | Decision |
|---|---|---|
| 1 | Standing rating storage | **`Config.lastMoodScore` (Int, SharedPreferences-backed), display-only.** A new DB table or sessionless `mood_entries` row was considered and rejected: it would muddy the `mood_entries` semantic ("a row = the user actively annotated this session"), force a schema migration, and require the inactivity reset to delete from the DB. Config is one `Int` — no migration, no I/O outside `SharedPreferences`. |
| 2 | Auto-seed `mood_entries` from standing rating | **No.** The standing rating only pre-highlights the chip and is read on the UI side. A `mood_entries` row is written only by an explicit user tap. Rejected the auto-seed approach because (a) it would mean every session looks annotated even if the user never touched the bar, polluting analytics, (b) it would race with privacy-toggle and `clearMoodForActiveSession`, and (c) the inactivity-reset becomes load-bearing for data hygiene rather than just UX. |
| 3 | Layout for `Config.showMoodBar == false` | **Runtime `ConstraintSet` re-anchor.** When the bar is hidden, `suggestions_holder.start` re-anchors to `endOf(clipboard_clear)`. Drives the constraint at runtime instead of through visibility alone, because XML constraints persist when the anchor target is GONE, leaving `suggestions_holder` orphaned at the leading edge otherwise. |
| 4 | Default `Config.moodBarExpanded` | **`false` (collapsed).** First-launch users see a compact chip; existing users on upgrade see their bar collapse, which is a visible-but-recoverable surprise (they tap the chip to expand). The collapsed state is the entire point of the redesign — defaulting `true` would undermine it. The standing rating + chip placeholder makes the collapsed state immediately functional. |
| 5 | Empty collapsed glyph | **Grayed neutral placeholder (🙂 at alpha 0.45).** The shield (🛡️) was rejected because it means "privacy on" today, and using it as a "no mood" sentinel when privacy is OFF would be ambiguous. A "+" or "?" icon was considered but loses the visual continuity with the emoji slots. The dim 🙂 reads as "absence of choice" without conflicting with any existing semantic. |
| 6 | Side placement | **Left (replaces `clipboard_clear` leading position).** Right was considered but the right-side icon cluster (voice / pinned-clipboard / settings cog) is already crowded. Left is symmetric: privacy/mood UI on the leading edge, settings on the trailing edge. |
| 7 | Inactivity timeout duration | **1 hour (`MOOD_INACTIVITY_TIMEOUT_MS = 60L * 60L * 1000L`).** The user's request. No setting to change this in Phase 8.5 — settings can come in a follow-up if the chosen value proves wrong in practice. |
| 8 | Inactivity timestamp write site | **Mood-selection + `onFinishInputView` only.** Per-keystroke writes were rejected because they put `SharedPreferences.edit().commit()` on the IME thread (CLAUDE.md "Critical Constraint"). The semantic loss — a user typing continuously for >1 h still has their rating expire — is acceptable: long-running typing without touching the bar means the rating is genuinely stale. |
| 9 | Inactivity reset action | **Clear `Config.lastMoodScore` only; do not flip `Config.privacyModeEnabled`.** The user explicitly chose "respect Config.privacyModeEnabled default" — let the existing logic in `refreshMoodBarFromState` take over (privacy on → 🛡️; privacy off → placeholder 🙂). No surprise privacy toggling. |
| 10 | Auto-collapse after slot tap | **Yes — collapse after the controller call resolves.** Saves the user a chevron tap. Privacy 🛡️ tap (which currently finalises the in-flight session) also collapses; the user sees their new shield in the chip without needing a second tap. |
| 11 | Phase 8.4 compatibility | **Compose-safe in either landing order.** Phase 8.4 adds one helper + one constant + two single-line wires inside `onMoodSlotClicked`. Phase 8.5 rewrites `applyMoodBarHighlight` to use `view.animate()` chains and adds `applyMoodBarLayout`. The two cancellation graphs do not share state but compose: `view.animate().cancel()` (this plan) also cancels the dance's `ObjectAnimator.ofFloat(view, …)` steps. If 8.4 lands first, 8.5's `applyMoodBarLayout(expanded=false, animate=true)` runs after the 360 ms dance settles and the user sees the dance → collapse sequence. If 8.5 lands first, 8.4 wires its single-line dance call against the unchanged `onMoodSlotClicked` branches; the post-dance collapse still fires. |
| 12 | Animation enable/disable pref | **None.** Consistent with Phase 6 (chevron rotation), Phase 8.2 (chat-bubble fade), Phase 8.4 (dance) — none have a pref. If Android's system "Reduce motion" accessibility flag is honoured by `View.animate()` defaults, that suffices. |
| 13 | New settings row for collapsed/expanded toggle | **None.** The chevron is the toggle. Adding a settings row would be a click-saver for one specific UX path and clutter the IkdSettings screen for everyone else. `Config.moodBarExpanded` is user-driven, not user-configured. |
| 14 | Animator field on `MyKeyboardView` | **No new field.** All animations go through `view.animate()` chains, which Android tracks per-View internally. The cancel-before-restart pattern is sufficient. (Phase 8.4 introduces `currentMoodDanceAnimator: AnimatorSet?` because its dance is built from `ObjectAnimator`s in an `AnimatorSet`, which Android does **not** track per-View — a different shape requires a different cleanup.) |
| 15 | Standing rating cleared by `clearMoodForActiveSession` | **Yes.** When the user taps a highlighted slot to deselect (Phase 8.2 semantic), the standing rating is also cleared — the user explicitly retracted their rating; honour it. The activity timestamp is **not** cleared (clearing is itself an interaction, so the next on-show check shouldn't immediately reset based on the same timestamp). |
| 16 | Standing rating cleared by `enablePrivacyAndClearMood` | **Yes.** Tapping 🛡️ from any state already finalises the in-flight session and deletes the in-flight `mood_entries` row; clearing the standing rating in the same path is the matching display-side cleanup. The user's choice to enable privacy means "stop tracking me," which includes the standing self-rating. |
| 17 | Standing rating preserved by `disablePrivacy` | **Yes.** Privacy off ≠ mood selection. A user who flips privacy off should see the same standing rating they had before turning privacy on (if any). |

---

## 9. Acceptance Criteria

- [ ] `./gradlew assembleCoreDebug` succeeds.
- [ ] `./gradlew detekt` and `./gradlew lint` clean — no new warnings.
- [ ] `./gradlew testCoreDebugUnitTest` passes — existing JVM tests (`IkdMoodAggregatorTest`, `IkdSessionStatsLoaderTest`, `IkdAggregatorTest`, `IkdMoodAggregatorMixTest`, etc.) all pass unchanged. This phase is read-side-neutral.
- [ ] Fresh install (cleared app data): bar is collapsed; indicator shows the placeholder 🙂 at alpha 0.45.
- [ ] Tap the indicator → bar expands with the chevron rotated to 0°. Tap a happiness slot → row written, bar collapses, indicator now shows 😊 at alpha 1.0.
- [ ] Close the keyboard, reopen on a different input field → indicator still shows 😊 at alpha 1.0; **no `mood_entries` row written for the new session yet.** (Verify with `adb exec-out run-as org.fossify.keyboard cat databases/ikd.db > ikd.db; sqlite3 ikd.db 'SELECT * FROM mood_entries WHERE session_id = (SELECT MAX(id) FROM sessions);'` — empty result.)
- [ ] Expand bar, tap 😊 again → row written for the new session; bar collapses; indicator unchanged.
- [ ] Tap a different slot (e.g. 😢) → row replaced; bar collapses; indicator shows 😢. `Config.lastMoodScore == 4`.
- [ ] Tap the highlighted 😢 to deselect → row deleted; bar collapses; indicator shows the placeholder 🙂. `Config.lastMoodScore == 0`.
- [ ] From any state, expand bar and tap 🛡️ → bar collapses showing 🛡️ at alpha 1.0; in-flight session finalised; `Config.lastMoodScore == 0`.
- [ ] Inactivity reset: set a mood, then via `adb shell run-as org.fossify.keyboard "sed -i 's/<long name=\"ikd_last_mood_activity_ts\" value=\"[0-9]*\"/<long name=\"ikd_last_mood_activity_ts\" value=\"1\"/' shared_prefs/Prefs.xml"` (or unit-test the helper), reopen the keyboard. Indicator shows the placeholder 🙂.
- [ ] `Config.showMoodBar = false` (Settings → "Show mood bar in keyboard" off): bar is GONE; `clipboard_clear` is restored at the leading edge; `suggestions_holder` re-anchors to `endOf(clipboard_clear)`. Toggle back on: layout returns to mood-bar-leading without restart.
- [ ] Rapid double-tap of the chevron resolves cleanly (cancel-before-restart): no leftover alpha state on indicator or expanded slots; chevron ends at the correct rotation.
- [ ] Theme switch mid-animation: bar background re-tints via `applyMoodBarTint()`; no animation glitches.
- [ ] If Phase 8.4 has landed: tap a slot → dance plays (~360 ms), then bar collapses showing the new emoji. Both animations resolve; no stuck transforms.
- [ ] Net diff fits inside the reopened files listed in §2; `git diff main..feat/phase8.5-collapsible-mood-bar --name-only` matches.

---

## 10. Verification (manual)

1. `./gradlew installCoreDebug`. Open any text field; bring up the keyboard.
2. **Fresh-state golden path:** clear app data first. Bar shows collapsed chip with dim 🙂 placeholder at the leading edge. Suggestions / voice / clipboard chip / settings cog all visible to the right.
3. **Expand:** tap the chip (or chevron) → ~150 ms chevron rotation; expanded slots fade in over 200 ms. Suggestions / voice / clipboard chip get hidden by `applyMoodBarVisibility()`.
4. **Select:** tap 😊 → existing chat bubble appears ("I'm feeling happy"); slot scales to 1.25× via animated `applyMoodBarHighlight`; bar collapses; chip now shows 😊 at full alpha. (If Phase 8.4 has landed, dance plays before collapse.)
5. **Persistence:** dismiss keyboard; reopen on a different EditText. Chip still shows 😊. `mood_entries` for the new session is empty until you tap again.
6. **Tap-to-confirm:** expand bar, tap 😊 → bubble appears; row written; bar collapses; chip unchanged.
7. **Switch:** expand, tap 😢 → 😊 highlight clears, 😢 highlights, bubble updates; row replaced; bar collapses; chip shows 😢.
8. **Deselect:** expand, tap 😢 again → highlight clears; row deleted; bar collapses; chip shows the placeholder 🙂. Standing rating is gone.
9. **Privacy on:** expand, tap 🛡️ → in-flight session finalised; chip shows 🛡️ at full alpha; `Config.lastMoodScore == 0`.
10. **Privacy off:** expand, tap 🛡️ again (deselect path) → privacy off; chip shows whatever the prior state was (placeholder if no standing rating, persisted emoji otherwise).
11. **Inactivity reset:** set a mood, then either (a) wait one hour with the keyboard closed, or (b) shortcut via `adb shell run-as org.fossify.keyboard sqlite3` on the prefs file, or (c) attach a debugger and force `Config.lastMoodActivityTimestamp` back. Reopen keyboard → chip shows the placeholder 🙂. (If `Config.privacyModeEnabled` is on, chip shows 🛡️ instead — Decision #9.)
12. **Long-attached IME edge case:** with the keyboard attached, toggle `Config.showMoodBar` off and back on. The on-toolbar-refresh path (`refreshMoodBarFromState`) re-checks staleness. (Hard to manually trigger 1 h of inactivity inside the IME; the unit test will cover this.)
13. **Settings — show mood bar off:** Settings → "Show mood bar in keyboard" off. Bar is GONE; `clipboard_clear` visible at leading edge; suggestions re-anchored. Toggle back on; layout returns.
14. **Theme switch:** light/dark theme toggle via system settings while the bar is expanded. Capsule background re-tints; chip alpha and rotation are preserved.
15. **Rapid taps:** double-tap chevron, double-tap a slot, alternate slots fast. No accumulated jitter; final state is always clean.

---

## 11. Files touched (summary)

**Modified (8 files):**
- `app/src/main/kotlin/org/fossify/keyboard/views/MyKeyboardView.kt` — collapsed indicator rendering, `applyMoodBarLayout`, animation discipline, runtime `ConstraintSet` for `showMoodBar=false`.
- `app/src/main/res/layout/keyboard_view_keyboard.xml` — three-zone mood bar, leading anchor.
- `app/src/main/kotlin/org/fossify/keyboard/helpers/Constants.kt` — three pref keys + timeout constant.
- `app/src/main/kotlin/org/fossify/keyboard/helpers/Config.kt` — three new properties.
- `app/src/main/kotlin/org/fossify/keyboard/helpers/MoodEmoji.kt` — `SCORE_NONE` sentinel + `isStandingScore()` helper.
- `app/src/main/kotlin/org/fossify/keyboard/helpers/IkdMoodBarController.kt` — Config writes alongside DAO writes (no new method, no new threading).
- `app/src/main/kotlin/org/fossify/keyboard/services/SimpleKeyboardIME.kt` — `maybeResetStaleMood()` in `onStartInputView`, timestamp write in `onFinishInputView`.
- `app/src/main/res/values/strings.xml` — `mood_bar_collapsed_placeholder`, `mood_bar_toggle_content_description`.

**New (0–2 files, depending on existing assets):**
- `app/src/main/res/drawable/ic_chevron_down_vector.xml` — only if no reusable chevron asset is present.
- `app/src/main/res/values/dimens.xml` — only if `mood_bar_chevron_size` is needed (likely reuse existing dimens).

**Deleted:** none.

**Roadmap docs touched alongside this commit:**
- `roadmap/STATUS.md` — Phase 8.5 row + "Next step" update.
- `roadmap/FeatureRoadmap.md` — Phase 8.5 section.
- `roadmap/Phase8/Phase8_Plan.md` — Section 15 pointer.
- `CLAUDE.md` — Phase 8.5 architecture note (added on implementation, not on plan).

---

## 12. Risk

**Medium-low.** Larger surface than 8.4 (8 files vs 1) but every reopen is narrowly scoped: pref keys, three Config writes alongside existing DAO writes, two narrow IME lifecycle wires, one layout restructure, one view file's display logic. No schema migration, no DAO changes, no aggregator changes, no capture-path semantic change. The biggest risk is the `MyKeyboardView.kt` delta — runtime `ConstraintSet` re-anchoring is non-trivial and can leave the toolbar in an inconsistent state if not driven cleanly. Mitigation: §10 verification step 13 specifically exercises the `showMoodBar` flip path; the constraint set is built once at startup and switched between two pre-built states, not mutated incrementally.

**Reversibility:** the only persistent state is three new SharedPreferences keys. Reverting the change leaves those keys orphaned in `Prefs.xml` — harmless. `mood_entries` rows written under Phase 8.5 semantics are byte-identical to those under Phase 8.2 semantics (one row per session, written only on explicit user tap). No migration needed to roll forward or back.

**Known follow-ups deliberately deferred:**
- A settings row to tune the inactivity timeout. Defer until daily use confirms 1 h is wrong.
- Long-press on the collapsed chip to clear the standing rating without expanding. Marginal UX win; defer.
- An onboarding tooltip on first launch explaining the chip → bar tap. Defer until upgrader feedback warrants it.
