// AppConfig.ets
// Central configuration for the ANCHOR app.
// All tuneable thresholds and build-time flags live here so they are easy to adjust.
//
// IMPORTANT: set IS_EMULATOR = false before deploying to a real Huawei Watch.

export class AppConfig {

  // ---------------------------------------------------------------------------
  // Deployment target
  // ---------------------------------------------------------------------------

  // true  → DevEco Studio emulator: uses MockMotionDetector + MockFallDetector
  // false → real Huawei Watch:      uses RealMotionDetector + RealFallDetector
  static readonly IS_EMULATOR: boolean = true;

  // ---------------------------------------------------------------------------
  // Geofence radius thresholds (metres)
  // ---------------------------------------------------------------------------

  // Below this distance: patient is in the SAFE zone
  static readonly DRIFT_RADIUS: number = 30;

  // Beyond this distance (with motion): state escalates to ALERT
  static readonly ALERT_RADIUS: number = 50;

  // ---------------------------------------------------------------------------
  // GPS polling intervals (seconds)
  // ---------------------------------------------------------------------------

  // Faster polling when the accelerometer detects walking — catches exits early
  static readonly GPS_INTERVAL_ACTIVE: number = 10;

  // Slower polling when the patient is still — preserves watch battery
  static readonly GPS_INTERVAL_REST: number = 60;

  // ---------------------------------------------------------------------------
  // Motion detection (accelerometer)
  // ---------------------------------------------------------------------------

  // Net acceleration above gravity (m/s²) required to classify a reading as "moving"
  // Net = |magnitude − 9.8| — removes the constant gravity component
  static readonly MOTION_NET_THRESHOLD: number = 1.2;

  // Rolling window size (number of samples) used for sustained-motion averaging
  // At ~5 Hz (normal interval) this is roughly 2 seconds of data
  static readonly MOTION_SAMPLE_WINDOW: number = 10;

  // A new motion state must persist for this many milliseconds before being committed.
  // Prevents single-jostle or GPS-noise events from flipping the state.
  static readonly MOTION_DEBOUNCE_MS: number = 2000;

  // ---------------------------------------------------------------------------
  // Fall detection (accelerometer)
  // ---------------------------------------------------------------------------

  // Total magnitude (m/s²) that indicates an impact spike — roughly 2.5g
  static readonly FALL_IMPACT_THRESHOLD: number = 25;

  // Net acceleration below this value counts as post-fall stillness (m/s²)
  static readonly FALL_STILLNESS_THRESHOLD: number = 0.5;

  // The patient must remain still for this many ms after the spike to confirm a fall
  static readonly FALL_STILLNESS_DURATION: number = 2000;
}
