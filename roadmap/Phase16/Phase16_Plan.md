# Phase 16 — On-Device Machine Learning (Mood Prediction + behavioural models)

**Status:** Proposed (draft created 2026-05-17). First ML phase of the project.
**Depends on:** Phase 2 (`ikd.db`: `sessions`, `ikd_events`, `sensor_samples`), Phase 8 (`mood_entries` — the only labels we have), Phase 9 (read-side aggregator pattern), Phase 14/15 (Insights tab structure, schema v4).
**Branch:** lands directly on `main` in small focused commits, per recent project hygiene.
**Scope (one sentence):** Add a small family of **on-device, per-user, exploratory** ML features driven by the already-captured keystroke-dynamics + motor + mood data — headlined by **Mood Prediction** — keeping the project's invariants: no network, no raw text, individual baseline over global classifier, *suggestion* not diagnosis.

> **Framing (carried from the Phase 1/2 reports — non-negotiable).** Every model here is **exploratory and individual**. Output is a *suggestion* or a *deviation indicator vs. the user's own baseline*, never a clinical inference. No determinism in copy ("you seem sad"); the UI says "does this match how you feel?" and lets the user confirm/correct. Models run **entirely on the device**; nothing is transmitted.

---

## 1. Why this phase

The system already captures a clean, privacy-preserving behavioural signal (IKD, hold, flight, correction weight, categories, accel/gyro per session) and a sparse subjective label (`mood_entries`, Ekman 1–6 per annotated session). Until now everything downstream is **descriptive analytics** (aggregators + charts). The Sensorização e Ambiente Fase-2 brief explicitly requires *conceiving and implementing ML models*. Phase 16 turns the existing pipeline output into learned, personalised models — the natural next step and the missing piece for the academic report.

The design constraint is unusual and is the whole point: **the ML must be as private as the capture** — on-device, per-user, no data egress. That rules out a cloud model and favours light, personalised models trained/updated locally.

---

## 2. Proposed ML features

Ordered by priority. **MoodPrediction is the flagship**; the others reuse the same feature pipeline so they are cheap add-ons.

### 2.1 MoodPrediction (flagship)
- **Task:** from the typing + motor features of a session (and short recent history), predict the user's self-reported mood for that session.
- **Target granularity:** start with **coarse valence** — `positive` / `neutral` / `negative` derived from the Ekman 1–6 ordinal (e.g. 1–2 → positive, 3–4 → neutral, 5–6 → negative; exact mapping is Decision #2) — because per-class data for 6 classes will be far too sparse for a single user. Optionally expose the 6-class head later if a user has enough labels.
- **Per-user model.** A small **multinomial logistic regression / softmax** (or a tiny gradient-boosted stump set) trained **on-device** from that user's own `mood_entries`-labelled sessions. No shipped global model (privacy + heterogeneity argument from Phase 1).
- **Cold start:** until the user has ≥ N labelled sessions (Decision #3, e.g. N≈20), MoodPrediction is dormant (no guess) — shown as "learning your patterns…".
- **Surface:** a gentle, optional **suggestion on the keyboard mood bar** ("Looks like a 🙂 day — tap to confirm or pick another") and/or an Insights card. Every confirm/correct is itself a new label → online update. Must not nag (see 2.4).
- **Honest evaluation:** per-user temporal split, compare against the trivial majority-class baseline; report accuracy/F1 + confusion; never claim more than "weak personalised signal".

### 2.2 Baseline-deviation indicator (unsupervised, no labels needed)
- Per-user **robust baseline** of core rhythm features (median IKD, IKD dispersion, correction rate, flight time) via robust z-score / EWMA, or a one-class model (Isolation-Forest-lite / Mahalanobis on robust covariance).
- Flags a session/day that deviates markedly from *that user's* usual pattern. Surfaced as a neutral Insights note ("your typing rhythm today is unlike your usual — could be many things"), explicitly non-diagnostic.
- Highest-value, lowest-risk: needs **zero labels**, works from day one, directly matches the Fase-1/2 "desvio face ao padrão individual" goal.

### 2.3 Typing-regime clustering (unsupervised)
- k-means / GMM over per-session feature vectors → 3–5 descriptive "modes" (e.g. *fast & clean*, *slow & corrective*, *on-the-move* using accel magnitude). Cluster labels are descriptive, not evaluative.
- Surfaced as a small distribution/timeline on Insights ("most of your sessions this week were in mode A"). Gives interpretable structure without any label.

### 2.4 Smart mood-prompt timing (uses 2.1/2.2 outputs)
- Use model uncertainty (2.1) or a detected deviation (2.2) to choose *when* to (subtly, at most once/day) surface the mood prompt — improving label quality and quantity without nagging.
- **Strict guardrail (Phase 1 §8):** must not increase typing, must not gamify behaviour, must not reveal which metrics drive it; purely a low-frequency, dismissible prompt. Off by default; user-toggle.

### 2.5 (Optional, low priority) Mood-curated pre-warm / streak-risk nudge
- Predicted mood (2.1) could pre-warm the Phase 12 curated-emoji set before the user taps; or a simple model could flag "streak at risk" for the badges. Nice-to-have; only if 2.1 lands cleanly.

---

## 3. Data & feature pipeline

- **No new captured data.** All features are derived from existing tables — same privacy posture (metadata/ordinals only).
- New `helpers/IkdMlFeatureExtractor.kt` (companion-testable, `Dispatchers.IO`): turns a session (or a window) into a fixed feature vector reusing the existing aggregator SQL where possible:
  - Timing: median/IQR IKD, mean hold, mean flight, backspace-weighted error rate, keystroke count, session duration, kpm.
  - Motor: mean/var accel magnitude, mean/var gyro magnitude, dominant-orientation proxy (already derived in Phase 5/9).
  - Temporal context: hour-of-day bucket, weekday (control variables, per Phase 1 — flag, don't over-trust).
  - Label (for 2.1 only): the session's `mood_entries.mood_score` → valence class.
- Feature standardisation **per user** (running robust mean/scale) so a global scale never leaks across users.
- A frozen, versioned feature schema (`FEATURE_VERSION`) so a model trained on v1 features isn't fed v2 vectors after an update.

---

## 4. On-device ML approach (library decision — Decision #1)

Privacy invariant = **no network, model lives on device**. Options, in increasing weight:

- **A. Pure-Kotlin classical models (recommended default).** Logistic/softmax regression, k-means, robust z-score / Mahalanobis, small decision stumps — a few hundred LOC, no new Gradle dependency, fully inspectable, trivially on-device, incrementally trainable per user. Best fit for the project's "minimal deps, on-device, exploratory" ethos and small N.
- **B. Bundled pre-trained TFLite model** trained offline on exported CSV, shipped read-only. Adds the TFLite dependency; contradicts the per-user-personalisation argument; only worth it if a global model clearly beats per-user — unlikely at this N. Keep as fallback for 2.1's 6-class head if ever needed.
- **C. On-device training framework** — out of scope (overkill).

**Recommendation:** **Option A** for all of 2.1–2.4. Revisit B only if the report needs a "global model" comparison. Decision pending owner sign-off.

---

## 5. Branch & layering discipline

Read-side + a small new persistence surface. **Capture path stays frozen.**

### New files
| File | Purpose |
|---|---|
| `helpers/IkdMlFeatureExtractor.kt` | Session/window → feature vector (pure derivation on companion; IO hop to read rows) |
| `helpers/IkdMoodModel.kt` | Per-user softmax/logistic model: `train(samples)`, `predict(features) → (class, confidence)`, serialise/restore. Pure-Kotlin |
| `helpers/IkdAnomalyDetector.kt` | Per-user robust baseline + deviation score (2.2) |
| `helpers/IkdRegimeClusterer.kt` | k-means/GMM over session features (2.3); pure `Companion` for tests |
| `helpers/IkdMlOrchestrator.kt` | Ties feature extraction → train/update → predict on one `Dispatchers.IO` hop; debug wall-clock log (same pattern as `IkdAggregator`) |
| `models/MlModelState.kt` + `interfaces/MlModelDao.kt` | Persist per-user model params + `FEATURE_VERSION` + last-trained timestamp (Room) |
| `models/MoodPrediction.kt` + DAO | Optional: store predictions + user confirm/correct for online learning + evaluation |
| `activities/dashboard/*` or a card | Insights surface for 2.2/2.3; `MyKeyboardView` mood-bar suggestion for 2.1 |
| `app/src/test/.../Ikd{MoodModel,AnomalyDetector,RegimeClusterer}Test.kt` | JVM unit tests on pure companions (synthetic fixtures: separable classes, drift, degenerate) |
| `app/src/androidTest/.../IkdDatabaseMigrationTest.kt` *(extend)* | `Migration(4,5)` test |

### Reopened (narrow)
- `databases/IkdDatabase.kt` — schema bump 4 → 5 (model state + predictions tables), register new DAOs.
- `extensions/ContextExt.kt` — lazy `ikdMlOrchestrator` accessor.
- `activities/DashboardActivity.kt` — run the ML orchestrator on the existing IO hop; pass results in `DashboardPayload`.
- `views/MyKeyboardView.kt` + mood-bar wiring — only if 2.1's keyboard suggestion is greenlit (UI-only; capture path untouched).
- `helpers/Config.kt` / `Constants.kt` — toggles: `moodPredictionEnabled`, `smartMoodPromptEnabled` (default OFF for the prompt).
- `activities/IkdSettingsActivity.kt` + layout — the toggles + a "reset learned model" control (privacy/agency).

### Still forbidden
Capture path (`SimpleKeyboardIME` capture, `LiveCaptureSessionStore`, `KinematicSensorHelper`, `IkdRetentionWorker`), existing entities' columns, `IkdCsvWriter` format, the existing aggregators' computed outputs (we *consume* them, don't change them), chart `Ikd*View` internals. No `INTERNET` permission — ever.

### Branch hygiene (sub-phases / commits)
1. `Migration(4,5)` + model-state/prediction entities + DAOs + migration test.
2. `IkdMlFeatureExtractor` + feature versioning + unit tests.
3. `IkdAnomalyDetector` (2.2) + Insights surface — ships first (no labels, lowest risk, immediately useful).
4. `IkdRegimeClusterer` (2.3) + surface.
5. `IkdMoodModel` (2.1) + orchestrator + cold-start + keyboard/Insights suggestion + confirm/correct → online update.
6. Smart mood-prompt timing (2.4), default-off, behind guardrails.
7. Docs: this plan, `STATUS.md`, `FeatureRoadmap.md`, `PROJECT_JOURNEY`, `CLAUDE.md`; feeds the Fase-2 report Cap. 7.

No push / no PR by the implementer.

---

## 6. Schema

`IkdDatabase.version` 4 → 5 via additive `Migration(4,5)`:
- `ml_model_state` — `id`, `kind` (mood/anomaly/regime), `feature_version`, `params` (BLOB/TEXT JSON of small param vectors), `trained_at`, `sample_count`.
- `mood_predictions` *(optional, for 2.1 evaluation + online learning)* — `session_id?`, `predicted_class`, `confidence`, `shown_at`, `user_response` (confirmed/corrected/dismissed), `actual_class?`.
- No change to any existing table. CSV export format unchanged (predictions are derived state — not part of the captured-data contract; an optional separate export could come later, out of scope).

---

## 7. Evaluation methodology (for the report, Cap. 7/8)

- **Per-user, temporal split** (train on earlier sessions, test on later) — never random split (leakage, per Phase 1).
- Compare every model against the **trivial baseline** (majority class for 2.1; "never anomalous" for 2.2).
- Metrics: 2.1 → accuracy, macro-F1, confusion matrix, calibration of `confidence`; 2.2 → precision of flags vs. user feedback where available + qualitative; 2.3 → silhouette + interpretability of clusters.
- Honest reporting: small N, sparse labels, confounders (device/idiom/context), intra- vs inter-individual variation. State explicitly what cannot be concluded.
- Reproducible: models trainable offline from the existing **CSV export** so the report's numbers can be regenerated.

---

## 8. Privacy & ethics (hard constraints)

- No new captured data; all features derived from existing metadata/ordinals.
- Model + params **stay on device** (Room/files); never serialised off-device; **no `INTERNET`**.
- Per-user models only — no cross-user pooling, no shipped global model trained on others' data.
- "Reset learned model" + master off-toggle in settings (data agency).
- Output copy is suggestive/non-deterministic and non-clinical; the smart prompt is off by default and rate-limited; nothing reveals the exact features driving inference (Phase 1 §8 anti-gaming).

---

## 9. Verification

1. `assembleCoreDebug` SUCCESS; `:app:testCoreDebugUnitTest` incl. new model tests pass; detekt/lint flat-or-better.
2. `Migration(4,5)` instrumented test passes; existing v1→v4 tests still pass; `IkdDatabase.version == 5`.
3. On-device: anomaly note + regime view render from real captured sessions; MoodPrediction stays dormant under the cold-start threshold then begins suggesting; confirm/correct writes a label and updates the model; reset-model clears state.
4. Privacy: CSV byte-identical (no model/prediction block); no `INTERNET` in manifest; airplane-mode end-to-end still fully functional.
5. Report reproducibility: offline script regenerates Cap. 7 metrics from an exported CSV.

---

## 10. Open decisions (need owner sign-off before implementing)

1. **ML approach:** confirm Option A (pure-Kotlin classical, no new dependency) — recommended.
2. **MoodPrediction target:** 3-class valence first (recommended) vs. attempt 6-class; the 1–6 → valence mapping.
3. **Cold-start threshold N** (min labelled sessions before predicting) — proposal: 20.
4. **Surface for 2.1:** keyboard mood-bar suggestion, Insights card, or both.
5. **Scope for v1:** ship 2.2 (+2.3) first as the safe, label-free win, and 2.1 as the headline; defer 2.4/2.5? (Recommended: 2.2 → 2.3 → 2.1, then 2.4 if time.)
6. Whether to add the optional `mood_predictions` table now (needed for honest 2.1 evaluation + online learning) or keep 2.1 stateless v1.

## 11. Out of scope (deferred)
- Cloud / federated training; cross-user models; deep nets; continuous sensor streams.
- Clinical/diagnostic claims of any kind.
- Auto-acting on predictions (e.g., changing UI mood state without user confirmation).
- Real-time per-keystroke inference (capture hot path stays frozen — inference is per-session/off-thread).
