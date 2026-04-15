// GeofenceService.ets
// Core business logic for ANCHOR. Combines GPS and motion detection to determine
// the patient's drift state and alert caregivers when necessary.
//
// This service consumes sensor data exclusively through the MotionDetector and
// FallDetector interfaces — it never imports @ohos.sensor directly. This allows
// the same logic to run on both the real watch (real detectors) and the emulator
// (mock detectors), toggled by AppConfig.IS_EMULATOR.

import geoLocationManager from '@ohos.geoLocationManager';
import vibrator from '@ohos.vibrator';
import preferences from '@ohos.data.preferences';
import { hilog } from '@kit.PerformanceAnalysisKit';
import common from '@ohos.app.ability.common';

import { DriftState } from '../common/DriftState';
import { AppConfig } from '../config/AppConfig';
import { MotionDetector, MotionEvent } from './MotionDetector';
import { FallDetector, FallEvent } from './FallDetector';
import { NotificationService } from './NotificationService';

const DOMAIN = 0x0000;
const PREFS_STORE = 'AnchorPrefs';
const KEY_ANCHOR_LAT = 'anchorLat';
const KEY_ANCHOR_LNG = 'anchorLng';
const KEY_PATIENT_NAME = 'patientName';

// --- Public types ---

export interface AnchorPoint {
  latitude: number;
  longitude: number;
}

// Callback the UI registers to receive state + distance + bearing on every GPS update.
// bearing: clockwise radians from north — used by the canvas to position the user dot.
export type StateChangeCallback = (state: DriftState, distanceMetres: number, bearing: number) => void;
export type FallDetectedCallback = () => void;

// --- GeofenceService ---

export class GeofenceService {
  private context: common.UIAbilityContext;
  private anchorPoint: AnchorPoint | null = null;

  // Start as NOT_SET — never assume SAFE until an anchor exists and GPS confirms it.
  private currentState: DriftState = DriftState.NOT_SET;

  // True when the MotionDetector has reported sustained walking movement.
  // Updated via the motionDetector callback, read by evaluateDriftState.
  private isMotionDetected: boolean = false;

  // Most recent GPS fix — included in fall alert messages.
  private lastKnownLocation: geoLocationManager.Location | null = null;

  // Patient name loaded from preferences — shown in alert messages.
  private patientName: string = 'Your family member';

  // Reference to the active GPS listener so we can unsubscribe.
  private locationListener: ((location: geoLocationManager.Location) => void) | null = null;

  // Emulator-only: setInterval handle for polling getLastLocation().
  // locationChange subscription never fires on the DevEco emulator, so we poll instead.
  private emulatorPollTimer: number = -1;

  // Motion and fall detectors — injected at construction time.
  // GeofenceService never creates these itself; the caller (Index.ets) decides
  // which implementation to use based on AppConfig.IS_EMULATOR.
  private motionDetector: MotionDetector;
  private fallDetector: FallDetector;

  // Callbacks provided by the UI.
  private onStateChange: StateChangeCallback;
  private onFallDetected: FallDetectedCallback;

  constructor(
    context: common.UIAbilityContext,
    onStateChange: StateChangeCallback,
    onFallDetected: FallDetectedCallback,
    motionDetector: MotionDetector,
    fallDetector: FallDetector
  ) {
    this.context = context;
    this.onStateChange = onStateChange;
    this.onFallDetected = onFallDetected;
    this.motionDetector = motionDetector;
    this.fallDetector = fallDetector;
  }

  // ------------------------------------------------------------------
  // Anchor management
  // ------------------------------------------------------------------

  // Load a previously saved anchor point from persistent storage.
  // Call this once when the app starts, before startMonitoring().
  async init(): Promise<void> {
    try {
      const store = await preferences.getPreferences(this.context, PREFS_STORE);
      const lat = await store.get(KEY_ANCHOR_LAT, 0) as number;
      const lng = await store.get(KEY_ANCHOR_LNG, 0) as number;

      if (lat !== 0 && lng !== 0) {
        this.anchorPoint = { latitude: lat, longitude: lng };
        hilog.info(DOMAIN, 'GeofenceService', 'Anchor loaded: %{public}s', JSON.stringify(this.anchorPoint));
        // Emit SAFE immediately so the UI shows a correct state on restart
        // rather than staying on NOT_SET until the first GPS update (up to 60s).
        // Distance 0 is a placeholder — GPS will update it on the next fix.
        this.currentState = DriftState.SAFE;
        this.onStateChange(DriftState.SAFE, 0, 0);
      } else {
        hilog.info(DOMAIN, 'GeofenceService', 'No saved anchor — emitting NOT_SET');
        // Immediately tell the UI we have no anchor so it shows "Setup Required",
        // not a stale SAFE badge from a previous session.
        this.onStateChange(DriftState.NOT_SET, 0, 0);
      }

      const savedName = await store.get(KEY_PATIENT_NAME, '') as string;
      if (savedName !== '') {
        this.patientName = savedName;
      }
    } catch (err) {
      hilog.error(DOMAIN, 'GeofenceService', 'Failed to load anchor: %{public}s', JSON.stringify(err));
      // On error assume no anchor — safe default is NOT_SET
      this.onStateChange(DriftState.NOT_SET, 0, 0);
    }
  }

  // Save the patient's name — shown in fall and drift alert messages.
  async setPatientName(name: string): Promise<void> {
    this.patientName = name.trim() || 'Your family member';
    try {
      const store = await preferences.getPreferences(this.context, PREFS_STORE);
      await store.put(KEY_PATIENT_NAME, this.patientName);
      await store.flush();
    } catch (err) {
      hilog.error(DOMAIN, 'GeofenceService', 'Failed to save patient name: %{public}s', JSON.stringify(err));
    }
  }

  // Save a new anchor point — called when the caregiver taps "Set Home Point".
  async setAnchorPoint(latitude: number, longitude: number): Promise<void> {
    this.anchorPoint = { latitude, longitude };
    try {
      const store = await preferences.getPreferences(this.context, PREFS_STORE);
      await store.put(KEY_ANCHOR_LAT, latitude);
      await store.put(KEY_ANCHOR_LNG, longitude);
      await store.flush(); // write through to disk immediately
      hilog.info(DOMAIN, 'GeofenceService', 'Anchor saved: %{public}s', JSON.stringify(this.anchorPoint));
    } catch (err) {
      hilog.error(DOMAIN, 'GeofenceService', 'Failed to save anchor: %{public}s', JSON.stringify(err));
    }

    // Anchor is now in memory. Transition to SAFE immediately so the UI doesn't
    // stay on "Setup Required" while waiting for the next GPS update (up to 60s).
    // Distance 0 is a placeholder — GPS will update it on the next fix.
    this.currentState = DriftState.SAFE;
    this.onStateChange(DriftState.SAFE, 0, 0);
  }

  hasAnchor(): boolean {
    return this.anchorPoint !== null;
  }

  getAnchorPoint(): AnchorPoint | null {
    return this.anchorPoint;
  }

  getLastLocation(): geoLocationManager.Location | null {
    return this.lastKnownLocation;
  }

  // ------------------------------------------------------------------
  // Monitoring lifecycle
  // ------------------------------------------------------------------

  // Start motion detection + GPS subscriptions.
  // Always call init() first so the anchor point is loaded.
  startMonitoring(): void {
    hilog.info(DOMAIN, 'GeofenceService', 'Starting monitoring');

    // Subscribe to motion events. The detector (mock or real) calls this callback
    // whenever the walking state changes. We update isMotionDetected and switch
    // GPS polling rate accordingly.
    this.motionDetector.startMonitoring((event: MotionEvent) => {
      const wasWalking = this.isMotionDetected;
      this.isMotionDetected = event.isWalking;

      // Switch GPS polling rate when motion state changes to save battery.
      if (event.isWalking !== wasWalking) {
        hilog.info(DOMAIN, 'GeofenceService',
          'Motion changed → walking=%{public}s', String(event.isWalking));
        const interval = event.isWalking
          ? AppConfig.GPS_INTERVAL_ACTIVE
          : AppConfig.GPS_INTERVAL_REST;
        this.restartGpsTracking(interval);
      }
    });

    // Subscribe to fall events. The detector fires once per confirmed fall.
    // We ignore the FallEvent payload here — handleFall() uses lastKnownLocation
    // from the GPS subscription for the most up-to-date position.
    this.fallDetector.startMonitoring((_event: FallEvent) => {
      this.handleFall();
    });

    // Start GPS at the rest interval — motion detector hasn't reported yet.
    this.startGpsTracking(AppConfig.GPS_INTERVAL_REST);
  }

  // Stop all monitoring. Call in onBackground / onDestroy.
  stopMonitoring(): void {
    this.motionDetector.stopMonitoring();
    this.fallDetector.stopMonitoring();

    if (AppConfig.IS_EMULATOR) {
      // Clear the emulator poll timer instead of unsubscribing locationChange.
      if (this.emulatorPollTimer !== -1) {
        clearInterval(this.emulatorPollTimer);
        this.emulatorPollTimer = -1;
      }
    } else {
      try {
        if (this.locationListener !== null) {
          geoLocationManager.off('locationChange', this.locationListener);
          this.locationListener = null;
        }
      } catch (err) {
        hilog.warn(DOMAIN, 'GeofenceService', 'GPS stop failed: %{public}s', JSON.stringify(err));
      }
    }

    hilog.info(DOMAIN, 'GeofenceService', 'Monitoring stopped');
  }

  // ------------------------------------------------------------------
  // GPS tracking
  // ------------------------------------------------------------------

  private startGpsTracking(intervalSeconds: number): void {
    if (AppConfig.IS_EMULATOR) {
      // locationChange never fires on the DevEco emulator — poll getLastLocation() instead.
      // This picks up whatever coordinates are injected via the emulator toolbar.
      if (this.emulatorPollTimer !== -1) {
        clearInterval(this.emulatorPollTimer);
      }
      // On emulator cap at 3s regardless of the requested interval — makes testing snappy.
      const pollMs = Math.min(intervalSeconds * 1000, 3000);
      this.emulatorPollTimer = setInterval(() => {
        try {
          const loc: geoLocationManager.Location = geoLocationManager.getLastLocation();
          this.onLocationUpdate(loc);
        } catch (_err) {
          // No cached location yet — skip this tick, try again next interval.
          hilog.warn(DOMAIN, 'GeofenceService', 'Emulator poll: no cached location yet');
        }
      }, pollMs) as number;
      hilog.info(DOMAIN, 'GeofenceService', 'Emulator GPS poll started at %{public}dms', pollMs);
      return;
    }

    const request: geoLocationManager.LocationRequest = {
      priority: geoLocationManager.LocationRequestPriority.FIRST_FIX,
      scenario: geoLocationManager.LocationRequestScenario.UNSET,
      timeInterval: intervalSeconds,
      distanceInterval: 0,
      maxAccuracy: 100 // loose accuracy — works better indoors
    };

    this.locationListener = (location: geoLocationManager.Location) => {
      this.onLocationUpdate(location);
    };

    try {
      geoLocationManager.on('locationChange', request, this.locationListener);
      hilog.info(DOMAIN, 'GeofenceService', 'GPS started at %{public}ds interval', intervalSeconds);
    } catch (err) {
      hilog.error(DOMAIN, 'GeofenceService', 'GPS start failed: %{public}s', JSON.stringify(err));
    }
  }

  // Unsubscribe and re-subscribe at a new interval.
  // Called when motion state changes to switch between active and rest polling.
  private restartGpsTracking(intervalSeconds: number): void {
    if (AppConfig.IS_EMULATOR) {
      // Just restart the poll timer at the new interval.
      this.startGpsTracking(intervalSeconds);
      return;
    }
    try {
      if (this.locationListener !== null) {
        geoLocationManager.off('locationChange', this.locationListener);
        this.locationListener = null;
      }
    } catch (err) {
      hilog.warn(DOMAIN, 'GeofenceService', 'GPS stop (for restart) failed: %{public}s', JSON.stringify(err));
    }
    this.startGpsTracking(intervalSeconds);
  }

  // Called each time a new GPS fix arrives from the system.
  private onLocationUpdate(location: geoLocationManager.Location): void {
    // Always store the latest fix — used in fall alerts regardless of anchor state.
    this.lastKnownLocation = location;

    if (this.anchorPoint === null) {
      // No anchor set yet — tell the UI to show NOT_SET rather than returning silently.
      // This ensures the status badge never shows a stale SAFE if GPS wakes up before setup.
      this.onStateChange(DriftState.NOT_SET, 0, 0);
      return;
    }

    const distanceM = this.haversineDistance(
      this.anchorPoint.latitude,
      this.anchorPoint.longitude,
      location.latitude,
      location.longitude
    );

    // Bearing from anchor to current position (radians, clockwise from north).
    // Used by the canvas radar to place the user dot in the right direction.
    const bearing = this.calculateBearing(
      this.anchorPoint.latitude,
      this.anchorPoint.longitude,
      location.latitude,
      location.longitude
    );

    hilog.info(DOMAIN, 'GeofenceService', 'Distance: %{public}dm  bearing: %{public}d°',
      Math.round(distanceM), Math.round(bearing * 180 / Math.PI));

    this.evaluateDriftState(distanceM, bearing);
  }

  // ------------------------------------------------------------------
  // Drift state machine
  // ------------------------------------------------------------------

  // Determines the current DriftState from distance + motion signal.
  // Fires haptics and notifications only on state *transitions* to avoid spam.
  // Always calls onStateChange so the UI distance readout stays current.
  private evaluateDriftState(distanceM: number, bearing: number): void {
    let newState: DriftState;

    if (!this.isMotionDetected || distanceM < AppConfig.DRIFT_RADIUS) {
      // Still, or within the inner safe zone — no concern.
      newState = DriftState.SAFE;
    } else if (distanceM < AppConfig.ALERT_RADIUS) {
      // Motion detected AND in the 30–50m band — early warning.
      newState = DriftState.DRIFTING;
    } else {
      // Motion detected AND beyond 50m — caregiver must be notified now.
      newState = DriftState.ALERT;
    }

    const stateChanged = newState !== this.currentState;

    if (stateChanged) {
      hilog.info(DOMAIN, 'GeofenceService',
        'State: %{public}s → %{public}s', this.currentState, newState);
      this.currentState = newState;

      // Haptic on every state change so the patient gets a wrist cue.
      this.triggerHaptic(newState);

      // Caregiver notification only on the critical transition to ALERT.
      if (newState === DriftState.ALERT) {
        this.triggerCaregiverAlert(distanceM);
      }
    }

    // Always call back so the UI updates the distance readout and map position.
    this.onStateChange(newState, distanceM, bearing);
  }

  // ------------------------------------------------------------------
  // Haptic feedback
  // ------------------------------------------------------------------

  private triggerHaptic(state: DriftState): void {
    try {
      if (state === DriftState.DRIFTING) {
        // 1 short pulse — a gentle nudge, not alarming.
        this.vibrate(200);
      } else if (state === DriftState.ALERT) {
        // 3 firm pulses (300ms on, 150ms off) — clear, unmistakeable alert.
        // HarmonyOS vibrator is single-shot; chain calls via setTimeout.
        this.vibrate(300);
        setTimeout(() => { this.vibrate(300); }, 450);
        setTimeout(() => { this.vibrate(300); }, 900);
      }
      // No haptic for SAFE or NOT_SET — silence is reassurance.
    } catch (err) {
      hilog.error(DOMAIN, 'GeofenceService', 'Vibrator failed: %{public}s', JSON.stringify(err));
    }
  }

  // Fires a single vibration. Extracted so object literals are always explicitly typed —
  // ArkTS forbids untyped inline object literals (arkts-no-untyped-obj-literals).
  private vibrate(durationMs: number): void {
    const effect: vibrator.VibrateTime = { type: 'time', duration: durationMs };
    const attr: vibrator.VibrateAttribute = { id: 0, usage: 'alarm' };
    vibrator.startVibration(effect, attr);
  }

  // ------------------------------------------------------------------
  // Caregiver notifications
  // ------------------------------------------------------------------

  // Sends the ALERT caregiver notification via NotificationService.
  // NotificationService owns all notification API calls — GeofenceService stays clean.
  private triggerCaregiverAlert(distanceM: number): void {
    NotificationService.sendDriftAlert(this.patientName, distanceM);
  }

  // ------------------------------------------------------------------
  // Fall detection
  // ------------------------------------------------------------------

  // Handles a confirmed fall event — fires independently of geofence state.
  // A fall indoors is just as critical as one outside.
  private handleFall(): void {
    // 3 long pulses — distinct from drift pattern so patient/caregiver recognises urgency.
    try {
      this.vibrate(500);
      setTimeout(() => { this.vibrate(500); }, 700);
      setTimeout(() => { this.vibrate(500); }, 1400);
    } catch (err) {
      hilog.error(DOMAIN, 'GeofenceService', 'Fall haptic failed: %{public}s', JSON.stringify(err));
    }

    const now = Date.now();
    const locationText = this.lastKnownLocation !== null
      ? this.formatCoords(this.lastKnownLocation)
      : 'Unknown';

    // Delegate to NotificationService — it logs the payload and publishes to the watch.
    NotificationService.sendFallAlert(this.patientName, locationText, now);

    // Tell the UI to show the fall overlay.
    this.onFallDetected();
  }

  // ------------------------------------------------------------------
  // Formatting helpers
  // ------------------------------------------------------------------

  // e.g. "51.5074°N, 0.1278°W"
  private formatCoords(location: geoLocationManager.Location): string {
    const lat = Math.abs(location.latitude).toFixed(4);
    const lng = Math.abs(location.longitude).toFixed(4);
    const latDir = location.latitude >= 0 ? 'N' : 'S';
    const lngDir = location.longitude >= 0 ? 'E' : 'W';
    return `${lat}°${latDir}, ${lng}°${lngDir}`;
  }

  // ------------------------------------------------------------------
  // Distance and bearing calculations
  // ------------------------------------------------------------------

  // Returns the initial bearing in radians (clockwise from north) from point 1 to point 2.
  private calculateBearing(lat1: number, lon1: number, lat2: number, lon2: number): number {
    const dLon = this.toRadians(lon2 - lon1);
    const y = Math.sin(dLon) * Math.cos(this.toRadians(lat2));
    const x =
      Math.cos(this.toRadians(lat1)) * Math.sin(this.toRadians(lat2)) -
      Math.sin(this.toRadians(lat1)) * Math.cos(this.toRadians(lat2)) * Math.cos(dLon);
    return Math.atan2(y, x);
  }

  // Returns the great-circle distance in metres between two lat/lng points.
  private haversineDistance(lat1: number, lon1: number, lat2: number, lon2: number): number {
    const R = 6371000; // Earth's mean radius in metres
    const dLat = this.toRadians(lat2 - lat1);
    const dLon = this.toRadians(lon2 - lon1);
    const a =
      Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos(this.toRadians(lat1)) * Math.cos(this.toRadians(lat2)) *
      Math.sin(dLon / 2) * Math.sin(dLon / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
  }

  private toRadians(degrees: number): number {
    return degrees * (Math.PI / 180);
  }
}
