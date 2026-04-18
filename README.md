# ANCHOR — Wearable Safety Companion for Seniors

> **"Your neighborhood, always within reach."**
ANCHOR is an open-source wearable safety system for seniors that silently watches for wandering and falls, then alerts caregivers the moment something goes wrong — before it becomes an emergency.

Built on a Huawei Watch running **HarmonyOS**, with an **Android** companion app, ANCHOR combines GPS geofencing and accelerometer-based motion detection to catch a quiet exit from home within seconds, not minutes.

---

## The Problem

A senior gets up from their chair and quietly walks out of the house. The caregiver is in another room. Within minutes, the patient can be lost, disoriented, or in danger.

Existing solutions are either too intrusive (constant monitoring), too reactive (only respond after an incident), or too complex for a stressed caregiver to operate. ANCHOR is designed around a single principle: **the caregiver should be alerted early, automatically, and without the patient needing to do anything.**

---

## How It Works

ANCHOR uses two signals that must agree before raising an alert — this dramatically cuts false alarms:

1. **Accelerometer (motion trigger)** — detects sustained walking (not just fidgeting)
2. **GPS drift** — confirms the person has physically left the home's vicinity (30–50m radius)

An alert fires only when *both* are true at once.

```
Patient wears watch passively
        │
        ▼
Accelerometer: sustained walking?
        │ yes
        ▼
GPS: drifted beyond anchor point?
        │ yes
        ▼
Haptics on watch + push notification to caregiver's phone
```

### Drift States

| State | Condition | Watch Response | Caregiver Alert |
|-------|-----------|----------------|-----------------|
| `SAFE` | Within 30m of home | None | None |
| `DRIFTING` | 30–50m + walking detected | 1 short pulse | None |
| `ALERT` | Beyond 50m + walking | 3 firm pulses | Push notification |
| `FALL` | High-G impact + stillness | 3 long pulses | Immediate alert + SMS |

Fall detection works **independently of the geofence** — a fall inside the house is just as critical as one outside.

---

## Features

**Watch (HarmonyOS)**
- GPS geofencing with Haversine distance calculation
- Accelerometer-based motion detection — filters out sitting/shifting
- Fall detection (impact spike + post-fall stillness signature)
- Adaptive GPS polling (10s when walking, 60s at rest — saves battery)
- Haptic feedback with distinct patterns per alert type
- Emergency contact card with tap-to-call and medical summary
- Caregiver setup screen — one tap to anchor home point
- Debug panel for emulator testing (motion/fall simulation)

**Phone Companion (Android)**
- Real-time drift state + distance display
- Alert history
- Emergency contact management
- SMS alerts to contacts on fall detection
- HMS push notifications from watch
- Geofence visualisation with anchor point control

---

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Watch app | ArkTS (HarmonyOS), Huawei DevEco Studio |
| Build system (watch) | Hvigor + Gradle |
| Phone app | Kotlin + Jetpack Compose |
| Build system (phone) | Gradle 8.x |
| Location | `@ohos.geoLocationManager`, Android geofencing |
| Motion / Fall | `@ohos.sensor` (accelerometer) |
| Haptics | `@ohos.vibrator` |
| Push notifications | HarmonyOS Notification Kit, HMS Push Kit |
| Cross-device comms | Huawei WearEngine |
| Auth | HMS Account Kit (Huawei ID) |
| Data persistence | `@ohos.data.preferences` (watch), SharedPreferences (phone) |
| Architecture | MVVM (phone), service + state-machine (watch) |

---

## Project Structure

```
Anchor-main/
├── entry/                          # HarmonyOS watch app (ArkTS)
│   └── src/main/ets/
│       ├── pages/
│       │   ├── Index.ets           # Main UI (home, setup, debug)
│       │   └── CommunityCard.ets   # Emergency contact + medical card
│       ├── services/
│       │   ├── GeofenceService.ets # Core state machine (GPS + motion → alerts)
│       │   ├── RealMotionDetector.ets
│       │   ├── MockMotionDetector.ets
│       │   ├── FallDetector.ets
│       │   └── NotificationService.ets
│       ├── common/
│       │   └── DriftState.ets      # NOT_SET | SAFE | DRIFTING | ALERT
│       └── config/
│           └── AppConfig.ets       # Thresholds, intervals, feature flags
│
└── phone/                          # Android companion app (Kotlin)
    └── src/main/kotlin/com/Anchor/watchguardian/
        ├── MainActivity.kt
        ├── ui/screens/             # Compose screens (Home, Contacts, Geofence, Alerts)
        ├── viewmodel/              # MVVM ViewModels
        ├── services/               # WearEngine, HMS Push, SMS, notifications
        └── data/model/             # AlertContact, AlertEvent, UserSession
```

---

## Architecture Notes

**Why GPS + motion together?**
GPS indoors has 10–50m of error through walls. A pure 5–10m geofence would fire constantly from drift noise. Using motion as a gate means GPS only needs to confirm the person is *clearly outside* — a much more reliable signal.

**Why a state machine?**
The `DriftState` enum (`NOT_SET → SAFE → DRIFTING → ALERT`) lets transitions drive haptics and notifications once per change, rather than re-firing every GPS poll. The `NOT_SET` guard prevents false negatives before the anchor is configured.

**Why mock/real detector split?**
`GeofenceService` accepts `MotionDetector` and `FallDetector` as constructor arguments. The same business logic runs on an emulator (mock implementations) and a real watch (sensor-backed implementations), toggled by `AppConfig.IS_EMULATOR`. No sensor code lives inside the core service.

**Notification ID pinning**
Drift alerts always update notification ID `1001`; fall alerts always update `1002`. This prevents the caregiver's notification shade from filling with stale entries.

---

## Permissions

**Watch (HarmonyOS `module.json5`)**
- `ohos.permission.LOCATION` — precise GPS
- `ohos.permission.APPROXIMATELY_LOCATION` — required alongside precise
- `ohos.permission.PLACE_CALL` — tap-to-dial emergency contacts

**Phone (Android `AndroidManifest.xml`)**
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`
- `POST_NOTIFICATIONS`
- `SEND_SMS` — emergency SMS to contacts
- `CALL_PHONE`
- `BLUETOOTH` — WearEngine cross-device communication

---

## Testing

The watch app includes a built-in debug panel for emulator testing (no physical hardware needed):

- **Long-press the ANCHOR title** on the home screen to reveal it
- Toggle walking/still states via the mock motion detector
- Trigger a simulated fall event
- View live GPS coordinates as the emulator injects them

Unit tests use the **Hypium** framework (HarmonyOS native) in `entry/src/test/`.

---


## Built With

ANCHOR was originally built as a hackathon project by team **Runtime Terrors**. It is designed around a single constraint: **a stressed caregiver should be able to understand the status in under 5 seconds.**

---
