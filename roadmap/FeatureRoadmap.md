# Feature Roadmap: Keystroke Dynamics Integration

This roadmap outlines the strategic phases for integrating passive behavioral tracking and sensor analytics into the custom keyboard project. The focus is on securely capturing device interactions and transforming them into meaningful, actionable insights for the user.

## Visual Roadmap Overview

```mermaid
flowchart TD
    classDef phase fill:#f9f9f9,stroke:#333,stroke-width:2px;
    classDef feature fill:#e1f5fe,stroke:#0288d1,stroke-width:1px;

    P1[Phase 1: Sensor Calibration & Debug Environment]:::phase
    P1_1[Phase 1.1: Live Keyboard Data & Metric Alignment]:::phase
    P2[Phase 2: Background Collection & Local Storage]:::phase
    P3[Phase 3: User Insights & Dashboard Presentation]:::phase

    P1 --> P1_1
    P1_1 --> P2
    P2 --> P3

    subgraph Phase 1 Features
        F1[Real-Time Data Interface]:::feature
        F2[Kinematic Sensor Integration]:::feature
        F3[Touch Dynamics Tracking]:::feature
    end

    subgraph Phase 1.1 Features
        F1_1[Live Keyboard Capture]:::feature
        F1_2[Metric Realignment]:::feature
    end

    subgraph Phase 2 Features
        F4[Lifecycle-Aware Activation]:::feature
        F5[Privacy-First Metadata Extraction]:::feature
        F6[Asynchronous Local Storage]:::feature
    end

    subgraph Phase 3 Features
        F7[Data Aggregation Engine]:::feature
        F8[Visual Analytics Modules]:::feature
        F9[Subjective Context Overlay]:::feature
    end

    P1 -.-> F1 & F2 & F3
    P1_1 -.-> F1_1 & F1_2
    P2 -.-> F4 & F5 & F6
    P3 -.-> F7 & F8 & F9
```

---

## Phase 1: Sensor Calibration and Debug Environment
**Status: Complete (2026-05-01)**

**Objective:** Establish a controlled, isolated testing environment within the companion application to verify that all device sensors are capturing data accurately and at the appropriate frequencies before deploying to the live keyboard.

*   **Real-Time Data Interface:** 
    *   Develop a dedicated diagnostic screen where developers and testers can input text and observe live sensor metrics.
*   **Kinematic Sensor Integration:** 
    *   Connect the device’s internal gyroscope and accelerometer. 
    *   Visualize the X, Y, and Z axes in real-time to observe the physical force and device posture changes during typing.
*   **Touch Dynamics Tracking:** 
    *   Implement logic to precisely measure micro-interactions.
    *   Calculate **Dwell Time** (duration a key is depressed) and **Flight Time** (the transition speed between keys).
*   **Data Validation Export:** 
    *   Provide an option to export short diagnostic sessions to a raw text or spreadsheet format to verify data structure and timing accuracy.

---

## Phase 1.1: Live Keyboard Data Validation & Metric Alignment
**Status: Not started**

**Objective:** Correct measurement mismatches from Phase 1 and upgrade data capture to the *actual* keyboard interface. Implement the updated metric standards, setting up accurate behavioral signals for the backend.

*   **Typing Speed Analysis:** 
    *   Measure true processing speed through the statistical distribution of flight times, revealing various mental states (cognitive fatigue/depression vs. alertness/anxiety).
*   **Flight Time:** 
    *   Calculate the interval between key release and next key press (UP to DOWN). High ranges characterize hesitation, cognitive overload, or distraction, while short ranges suggest urgency or impulsivity.
*   **Inter-Key Delay (IKD):** 
    *   Track the legacy interval between consecutive key releases (UP to UP) to maintain historical comparison and additional rhythm signatures.
*   **Error Rates Tracking:** 
    *   Calculate the frequency of corrections and typos. Monitor for deviations indicative of cognitive stress or severe depression.
*   **Key Hold Time (Dwell Time):** 
    *   Repurpose "Dwell Time" into Key Hold Time (duration a key is pressed) to assess motor function and potential cognitive slowing vs inattention/restlessness.
*   **Contextual Accelerometer & Gyroscope Data:** 
    *   Incorporate smartphone-specific accelerometer data handling to provide background on physical context (walking, standing, vehicle) simultaneously with keystrokes.

---

## Phase 2: Unobtrusive Background Collection & Secure Local Storage
**Status: Not started**

**Objective:** Seamlessly transition the sensor tracking into the live keyboard environment, ensuring data is collected passively without draining the device battery, causing input lag, or compromising user privacy.

*   **Lifecycle-Aware Activation:** 
    *   Configure the sensors to wake up strictly when the keyboard is summoned on-screen and to power down immediately when the keyboard is hidden.
*   **Privacy-First Metadata Extraction:** 
    *   Hook into the keystroke events to capture timing and physical interaction metrics.
    *   Enforce strict privacy filters: log event types (e.g., "Alphanumeric", "Backspace", "Space") and timestamps while permanently discarding the actual linguistic characters inputted.
*   **Asynchronous Local Storage:** 
    *   Establish a secure, on-device local database to store the collected sensor events.
    *   Ensure data processing and saving occur in the background, keeping the user's typing experience fluid and uninterrupted.

---

## Phase 3: User Insights and Dashboard Presentation
**Status: Not started**

**Objective:** Utilize the companion application to translate vast amounts of raw behavioral and kinematic data into digestible, visual insights that empower the user to understand their digital habits and cognitive states.

*   **Data Aggregation Engine:** 
    *   Create internal processes to summarize the raw data points into daily and weekly averages (e.g., average words per minute, daily backspace frequency, average dwell times).
*   **Visual Analytics Modules:**
    *   **Typing Rhythm Trends:** A visual graph displaying typing speed and fluidity over time, helping to establish a user baseline and highlight deviations.
    *   **Cognitive Fatigue Heatmap:** Visual representations of error rates and auto-correct reliance to indicate potential moments of low focus or fatigue.
    *   **Circadian Usage Patterns:** Time-of-day visualizations that map when typing sessions occur, specifically flagging late-night usage that may indicate sleep disruption.
*   **Subjective Context Overlay (Optional):** 
    *   Implement a simple daily check-in allowing users to log their mood or energy levels.
    *   Overlay this subjective self-reporting onto the objective sensor graphs to help users identify personal behavioral patterns.