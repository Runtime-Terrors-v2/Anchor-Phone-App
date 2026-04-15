# ANCHOR – Claude Code Instructions

## Project Overview

**ANCHOR** is a HarmonyOS app for Huawei Watch (and phone companion) designed for seniors (60+). The core danger: a patient may get up and quietly walk out of their house
without a caregiver noticing. ANCHOR detects this early and alerts caregivers immediately.

**Team:** Ujwal

**Dev environment:** Huawei DevEco Studio (emulator only — no physical watch)

---

## Your Role

You are helping implement and extend this project in **ArkTS** (TypeScript-based language for HarmonyOS).

Before writing a single line of code, you must:

1. **Read and understand the entire existing codebase** — explore all files, understand the structure, naming
   conventions, component patterns, and any existing logic.
2. **Summarise what you found** — give a brief plain-English overview of what's already built, what files exist,
   and how things connect.
3. **Only then propose your plan** — outline the exact steps you intend to take, confirm with Ujwal before
   proceeding.

> ⚠️ Never assume. If something is unclear about the existing code, ask before guessing.
> ⚠️ Always explain what you're about to do, list files you'll touch, and wait for Ujwal's go-ahead.

---

## Language & Platform Notes

- **Language:** ArkTS — TypeScript-based, HarmonyOS-specific. Decorators like `@State`, `@Component`, `@Entry`
  are core patterns.
- **Platform:** HarmonyOS (Huawei Watch + Phone companion)
- **IDE:** Huawei DevEco Studio (emulator-based testing — no physical device)
- **Key APIs:**
  - `@ohos.geoLocationManager` — GPS/GNSS location tracking
  - `@ohos.vibrator` — haptic feedback on watch
  - `@ohos.sensor` — accelerometer for motion detection
  - `@ohos.notification` or push kit — caregiver phone alerts
- Take time to understand any imports and APIs already used before introducing new ones.

---

## Use Case (Read This Carefully)

The primary user is a **seniors**. The danger scenario:

> A user gets up from their chair or bed and quietly walks out of the house. The caregiver doesn't notice.
> Within minutes, the patient can be lost, disoriented, or in danger.

This means:
- Alerts must trigger **early** — catching the exit, not after they've wandered far
- **False negatives are dangerous** (missing a real exit) — err on the side of alerting
- **False positives are annoying but acceptable** — a carer checking in is fine
- The patient themselves may not understand or respond to the watch — the **caregiver alert is the critical path**

---

## Geofencing Approach: Motion-Triggered + House-Level GPS

### Why this combination?
GPS alone indoors is unreliable (10–50m error through walls). A pure 5–10m geofence would produce constant
false alarms or miss exits. Instead, we use **two signals that must agree**:

1. **Accelerometer (motion trigger)** — detects sustained walking movement (not just shifting in a seat)
2. **GPS drift (house-level, 30–50m radius)** — confirms the person has left the building's vicinity

An alert fires when **both conditions are true**: motion detected AND GPS shows drift beyond the anchor zone.

### Drift States
- `SAFE` — within 50m of anchor point (or no sustained motion detected)
- `DRIFTING` — sustained motion detected AND 30–50m from anchor (early warning)
- `ALERT` — sustained motion AND beyond 50m from anchor (caregiver notified immediately)

### Polling & Battery
- GPS: poll every **10 seconds** when motion is detected, every **60 seconds** at rest
- Accelerometer: continuous low-power monitoring (step/motion detection mode)
- High-power GPS only activates when the accelerometer detects sustained movement

### Anchor Point
- Set by the caregiver on first setup (tap to anchor current GPS location as "Home")
- Persisted via `@ohos.data.preferences` so it survives app restarts
- Haversine formula used for distance calculation

---

## Alert System

### Who gets alerted?
- **The watch** — haptic vibration pattern on the patient's wrist (gentle, non-alarming)
- **Caregiver's phone** — push notification via HarmonyOS notification/push kit
- Both fire simultaneously on ALERT state

### Haptic Patterns (watch)
- `DRIFTING`: 1 short pulse (200ms) — subtle nudge
- `ALERT`: 3 firm pulses (300ms on, 150ms off × 3) — clear alert

### Caregiver Notification (phone)
- Title: "ANCHOR Alert"
- Body: "Your family member may have left home. Last known: [X]m from home point."
- Tap to open app with last known GPS position3

### Fall Detection
- If the accelerometer detects a sudden high-G impact followed by stillness (classic fall signature), immediately notify both the caregiver and all emergency contacts with the alert: "ANCHOR Fall Alert — [Name] may have fallen. Location: [GPS coords]. Time: [timestamp]."
- This triggers independently of the geofence state — a fall inside the house is just as critical as one outside.
---

## Tasks to Implement

### 1. Permissions & Setup
- Declare `ohos.permission.LOCATION` and `ohos.permission.APPROXIMATELY_LOCATION` in `module.json5`
- Request runtime permissions in `EntryAbility.ets` using `abilityAccessCtrl`
- Add accelerometer permission if required by HarmonyOS sensor API

### 2. GeofenceService (new file)
- Standalone ArkTS class: `entry/src/main/ets/services/GeofenceService.ets`
- Handles: anchor storage, location subscription, motion detection, distance calculation, state management
- Emits drift state changes to the UI via callback or shared state
- Triggers haptics and caregiver notifications on state change

### 3. Senior-Friendly UI (caregiver-facing)
- **Setup screen** (first launch): Large "Set Home Point" button, plain language explanation
- **Home screen**: Clear status card (SAFE / DRIFTING / ALERT), distance readout, last updated time
- Dark high-contrast theme — readable at a glance, minimal cognitive load
- UI is primarily for the **caregiver** setting up and monitoring, not the patient

---

## UI Design Rules

### Colour Palette (dark, high-contrast)
- Background: `#0D0D0D`
- Safe green: `#4ADE80`
- Drift amber: `#FBBF24`
- Alert red: `#F87171`
- Text: `#FFFFFF`

### Typography & Layout
- Minimum font size: 20sp for body, 32sp+ for status
- Large tap targets (minimum 48×48dp)
- No maps, no complex navigation — status and distance only
- One primary action per screen

---

## Coding Rules

### Before coding:
- Explain your plan in plain English — what you're about to do and why
- List every file you'll touch and what changes you'll make
- **Wait for Ujwal's confirmation before writing or modifying any code**

### While coding:
- Write comments throughout — explain what each function/block does, especially ArkTS-specific patterns
- Match the existing code style strictly — same naming conventions, formatting, patterns
- Make changes incrementally — one phase at a time
- Do not refactor existing working code unless explicitly asked

### After coding:
- Summarise what was added/changed
- Flag anything that needs testing or has known limitations (especially emulator vs real device gaps)
- Ask if Ujwal wants to proceed to the next step

---

## Testing Notes (DevEco Emulator)

- GPS can be simulated in the emulator via mock location injection — use this to test drift states
- Accelerometer simulation may be limited — may need to mock the motion detection signal in code for testing
- Note any features that will behave differently on a real watch vs the emulator
- Flag emulator limitations clearly so Ujwal knows what to expect during the demo

---

## Constraints

- **Hackathon project** — working demo over perfect architecture
- **Privacy-first** — no medical data stored, local processing preferred, standard Location permissions only
- **Caregiver sets up, patient wears** — the patient should never need to interact with the app
- Every UI decision: *"Could a stressed caregiver understand this in 2 seconds?"*