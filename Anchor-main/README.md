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

## Getting Started

### Prerequisites

| Target | Requirement |
|--------|-------------|
| Watch app | [Huawei DevEco Studio](https://developer.huawei.com/consumer/en/deveco-studio/) + HarmonyOS SDK 6.0.1 |
| Phone app | Android Studio + JDK 17 + Android SDK (minSdk 26) |

---

### Watch App (HarmonyOS)

1. Open the `entry/` folder in **Huawei DevEco Studio**
2. For emulator testing, ensure this flag is set in `AppConfig.ets`:
   ```typescript
   IS_EMULATOR: boolean = true
   ```
3. **Build:** DevEco Studio → Build → Build Module
4. **Run on emulator:** Use the toolbar to inject mock GPS coordinates
5. **Simulate motion/fall:** Long-press the ANCHOR title on the home screen to reveal the debug panel

> Before deploying to a real watch, set `IS_EMULATOR = false` in `AppConfig.ets`.

---

### Phone App (Android)

```bash
# Build debug APK
./gradlew :phone:assembleDebug

# Install on connected device / emulator
./gradlew :phone:installDebug

# Build release APK
./gradlew :phone:assembleRelease
```

**HMS Setup:** The file `phone/agconnect-services.json` must match your registered application ID (`com.Anchor.watchguardian`) in the [Huawei AppGallery Console](https://developer.huawei.com/consumer/en/appgallery/). The Push Kit and Account Kit won't function until this is correctly configured.

---

## Configuration

All watch-side behaviour is tunable from a single file:

**`entry/src/main/ets/config/AppConfig.ets`**

```typescript
IS_EMULATOR: boolean = true           // Switch to false for real device

// Geofence radii
DRIFT_RADIUS: number = 30             // metres — inner warning zone
ALERT_RADIUS: number = 50             // metres — caregiver notified

// GPS polling
GPS_INTERVAL_ACTIVE: number = 10      // seconds when walking
GPS_INTERVAL_REST: number = 60        // seconds at rest

// Motion detection (accelerometer)
MOTION_NET_THRESHOLD: number = 1.2    // m/s² above gravity baseline
MOTION_SAMPLE_WINDOW: number = 10     // rolling average window (~2s at 5Hz)
MOTION_DEBOUNCE_MS: number = 2000     // stability period before state change

// Fall detection
FALL_IMPACT_THRESHOLD: number = 25    // m/s² (~2.5g) impact spike
FALL_STILLNESS_THRESHOLD: number = 0.5 // m/s² post-fall stillness
FALL_STILLNESS_DURATION: number = 2000 // ms of stillness to confirm fall
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

## Roadmap

- [ ] Real-device testing with Huawei Watch hardware
- [ ] WearEngine full integration (currently stubbed — requires AAR setup)
- [ ] Geofence map view on phone (OSM tile integration)
- [ ] Alert history persistence (local Room database)
- [ ] Background service hardening (Android battery optimisation exemptions)
- [ ] Unit test coverage for GeofenceService state machine
- [ ] Accessibility audit (TalkBack, font scaling)

---

## Contributing

Contributions are welcome. Please open an issue before submitting a pull request for anything beyond a small bug fix, so we can agree on approach first.

1. Fork the repo and create your branch from `main`
2. Make your changes with clear commit messages
3. Open a pull request describing what changed and why

---

## Built With

ANCHOR was originally built as a hackathon project. It is designed around a single constraint: **a stressed caregiver should be able to understand the status in under 2 seconds.**

---

*Project ANCHOR — because wandering silently is the danger that matters most.*
