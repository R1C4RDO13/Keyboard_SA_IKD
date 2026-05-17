# Error-Rate Fix — Manual Test Plan

**Commits under test:** `569f331b` (exclude BACKSPACE from the denominator + weight backspaces by deletion count) **and** `303e792f` (drop AUTOCORRECT from the formula entirely).
**Branch:** `main`

The current (post-`303e792f`) error-rate definition — "correction" means **BACKSPACE only**:

```
errorRatePct = 100 * SUM(correction_weight WHERE category = 'BACKSPACE') / (eventCount - backspaceCount - autocorrectCount)
            ≡ 100 * backspaceWeight / (keystrokeCount - backspaceCount)
```

Two things to remember while running these tests:
1. **Denominator** = *productive keystrokes* — every event minus BACKSPACE rows minus AUTOCORRECT rows.
2. **BACKSPACE weight** equals the actual deletion count (selection length or grapheme count), not always 1.
3. **AUTOCORRECT rows are captured but do not feed the metric** — they show up in the CSV and the per-session event log, but contribute neither numerator nor denominator. (Known follow-up: the Habits-tab error metric and the Daily-Quality scatter still count AUTOCORRECT.)

---

## 0. Prerequisites

1. Install the build:
   ```powershell
   $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
   $env:Path = "$env:JAVA_HOME\bin;$env:Path"
   ./gradlew installCoreDebug
   ```
2. On the device, open **Settings → Languages & input → On-screen keyboard** and select **KeyboardSA IKD**.
3. Open any text field (Messages, Notes, browser address bar — anything with system spell-check).
4. **Disable privacy mode**: tap any of the six emotion emojis on the top bar of the keyboard. The shield 🛡️ is "privacy ON". Any other emoji = "privacy OFF, mood tagged".
5. Confirm capture is on by typing one letter and seeing the event count tick in **Settings → IKD → Diagnostics → Event count cell**.

After each test below: open **Insights → tap the most recent session row** to see the per-session dashboard. The "Error rate" cell is the value to verify.

To pull the raw DB if a reading looks wrong:
```powershell
adb exec-out run-as org.fossify.keyboard cat databases/ikd.db > ikd.db
# then open ikd.db with any SQLite browser and query:
#   SELECT event_category, is_correction, correction_weight FROM ikd_events WHERE session_id='<id>';
```

---

## 1. The user-reported bug — full retype delete

**Test:** Type `hello world` (11 chars including the space). Then press **BACKSPACE 11 times**, one keypress per character, until the field is empty.

**Expected DB rows:**
- 5 ALPHA + 1 SPACE + 5 ALPHA + 11 BACKSPACE = **22 events total**
- Each BACKSPACE row: `is_correction = 1`, `correction_weight = 1`
- Each ALPHA / SPACE row: `is_correction = 0`, `correction_weight = 0`

**Expected error rate:**
- `correctionCount = 11` (all BS rows)
- `correctionWeight = 11` (1 per BS)
- `productive = 22 - 11 = 11`
- **Error rate = 11 / 11 = 100.0 %**

**Where to verify:**
- Per-session dashboard ("Error rate" KPI cell): **`100.0 %`**
- Diagnostics live cell while typing the test: should climb toward **`100.0 %`** as you backspace
- Insights dashboard for today's bucket: error-rate chart point for today **`= 100 %`**

**Pass criteria:** Reading is **100 %**, not 50 %.

---

## 2. Backspace-on-selection — single BS deletes many chars

**Test:** Type `hello world` (11 chars). **Long-press to select** the entire phrase (or use any selection gesture). Press **BACKSPACE once**. The selection collapses, the field is empty.

**Expected DB rows:**
- 11 ALPHA / SPACE rows + 1 BACKSPACE row = **12 events**
- The single BACKSPACE row: `correction_weight = 11` (← the new behaviour; was always 1)

**Expected error rate:**
- `correctionCount = 1`, `correctionWeight = 11`, `productive = 12 - 1 = 11`
- **Error rate = 11 / 11 = 100.0 %**

**Pass criteria:** Reading is **100 %**, identical to test 1. Both phrasings of "delete everything I typed" must produce the same number.

If the BACKSPACE row in the DB shows `correction_weight = 1` instead of 11, the capture-side fix is wrong — re-check `SimpleKeyboardIME.computeBackspaceWeight()`.

---

## 3. The canonical autocorrect case — `ocasdasda → october`

**Test:** Confirm the device's system spell-check service is **ON** (Settings → Languages & input → Spell check → on). In the text field, type **`ocasdasda`** (9 lowercase chars), then a **space**. Wait for the system to replace the misspelled word with `october`.

**Expected DB rows (capture still works):**
- 9 ALPHA + 1 SPACE + 1 AUTOCORRECT = **11 events** (if the OEM spell-checker fires; some don't classify `ocasdasda` as misspelled — see note below)
- The AUTOCORRECT row still records `is_correction = 1` and a `correction_weight` ≈ the replaced-span length

**Expected error rate (post-`303e792f`):**
- `backspaceCount = 0`, `backspaceWeight = 0`, `autocorrectCount = 1`, `productive = 11 - 0 - 1 = 10`
- **Error rate = 0.0 %** — there are no backspaces; the autocorrect is informational only

**Pass criteria:** Reading is **`0.0 %`** (or the `—` placeholder if rendering nulls) **AND** the per-session event log lists the AUTOCORRECT row (proving capture still records it). This is the one behaviour change vs. the earlier fix — the metric layer no longer counts autocorrects, because the IME-level heuristic that detects them can't reliably distinguish a true spell-check accept from spell-check noise across OEMs.

If no AUTOCORRECT row appears, the system spell-check service didn't fire — that's fine for the metric (still 0 %); try another misspelling like `definately` only if you want to verify the capture path still records the row.

---

## 4. Light typo — small backspace correction

**Test:** Type `helxlo` (6 chars, intentional typo). Press **BACKSPACE once** to delete the trailing `o`. Press **BACKSPACE once** to delete the `l`. Press **BACKSPACE once** to delete the `x`. Then type `lo` (2 chars). Final text: `hello`.

**Expected DB rows:**
- 6 ALPHA + 3 BACKSPACE + 2 ALPHA = **11 events**
- 3 BACKSPACE rows each with `correction_weight = 1`

**Expected error rate:**
- `correctionCount = 3`, `correctionWeight = 3`, `productive = 11 - 3 = 8`
- **Error rate = 3 / 8 = 37.5 %**

**Pass criteria:** Reading is between **30 % and 40 %**. A modest correction should not show up as 50 %+.

---

## 5. Clean typing — no errors

**Test:** Type `the quick brown fox` (one space between each word, no typos, no autocorrect). Do **not** press backspace. Do **not** let the system autocorrect anything.

**Expected DB rows:** 19 ALPHA / SPACE events. Zero `is_correction = 1` rows.

**Expected error rate:**
- `correctionCount = 0`, `correctionWeight = 0`, `productive = 19`
- **Error rate = 0 / 19 = 0.0 %**

**Pass criteria:** Reading is **`0.0 %`** (or the screen shows the `—` placeholder if rendering nulls).

---

## 6. Live Diagnostics screen

**Test:** Open **Settings → IKD → Diagnostics**. The screen has its own type-and-watch text field at the top. Type `hello` (5 chars), then press **BACKSPACE 5 times** to clear it. Watch the "Error rate" cell in the KPI grid update in real time.

**Expected progression** (approximate, screen refreshes every 250 ms):
- After typing 5 chars and 0 BS: `0.0 %`
- After 1 BS: `1 / 5 = 20.0 %`
- After 2 BS: `2 / 5 = 40.0 %`
- After 3 BS: `3 / 5 = 60.0 %`
- After 4 BS: `4 / 5 = 80.0 %`
- After 5 BS: `5 / 5 = 100.0 %`

**Pass criteria:** The live cell climbs in increments of ~20 % per BS keypress, ending at **100 %**.

If the cell stays at ~50 % at the end, the live formula in `DiagnosticsActivity.updateComputedMetrics()` is still using the old denominator.

---

## 7. Insights dashboard — daily bucket

**Test:** After running tests 1–5 above (all in the same calendar day), open **MainActivity → Insights**. Make sure the range is **Week**.

**Expected:**
- Today's bucket on the **Error rate** line chart shows a value derived from the *aggregate* of every event captured today.
- KPI strip: "Avg error rate" cell reflects the same aggregate.
- Switching range to **Month** or **All Time** rolls today's events into a wider bucket — readings should be lower than 100 % once they're diluted by older clean sessions.

**Pass criteria:** No crashes, no stale "0 %" reading, no `NaN` / `Infinity`. The chart point exists for today.

---

## 8. (Optional) Edge cases

| Scenario | Expected reading |
|---|---|
| Open keyboard, close it without typing | Session has 0 events; error-rate KPI shows `—` |
| Type 1 char, immediately delete it (1 BS) | 1 / 1 = 100 % (single typo, fully corrected) |
| Type `a`, autocorrect doesn't trigger, no BS | 0 / 1 = 0 % |
| Backspace-spam an empty field (zero deletions per BS) | The BS rows still record `correction_weight = 1` (grapheme floor); error rate climbs as productive keystrokes are 0 — KPI may show `—` for productive ≤ 0 |

---

## 9. If a test fails

1. Pull `ikd.db` (command in Section 0). Run:
   ```sql
   SELECT
     COUNT(*) AS events,
     SUM(CASE WHEN event_category = 'BACKSPACE'  THEN 1 ELSE 0 END) AS backspaces,
     SUM(CASE WHEN event_category = 'AUTOCORRECT' THEN 1 ELSE 0 END) AS autocorrects,
     SUM(CASE WHEN event_category = 'BACKSPACE'  THEN correction_weight ELSE 0 END) AS backspace_weight
   FROM ikd_events
   WHERE session_id = '<id-from-the-failing-session>';
   ```
2. Compute by hand: `100 * backspace_weight / (events - backspaces - autocorrects)`. If that doesn't match the UI reading, the read-side aggregator is wrong (`IkdAggregator` or `IkdSessionStatsLoader`).
3. Check individual BACKSPACE rows: `SELECT timestamp, correction_weight FROM ikd_events WHERE event_category = 'BACKSPACE' AND session_id = '<id>'`. Each row's `correction_weight` should equal the number of chars that BS deleted (1 for single-char, N for selection-delete).

---

**Done when:** all of tests 1–7 pass on a real Android device. Tests 1 and 2 are the user-reported regression — those two are the "must-pass" gate.
