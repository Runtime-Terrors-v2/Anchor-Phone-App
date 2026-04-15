// FallDetector.ets
// Interface, mock implementation, and real implementation for fall detection.
// All three are in this file to keep fall-detection logic co-located.
//
// Fall signature (real device):
//   1. Sudden magnitude spike > FALL_IMPACT_THRESHOLD m/s² — the impact.
//   2. Followed by near-stillness < FALL_STILLNESS_THRESHOLD net m/s²
//      for at least FALL_STILLNESS_DURATION ms — the person lying still.
//
// Fall signature (emulator):
//   Triggered only via MockFallDetector.simulateFall() from the debug panel.

import sensor from '@ohos.sensor';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { AppConfig } from '../config/AppConfig';

const DOMAIN = 0x0000;

// ---------------------------------------------------------------------------
// Shared event type
// ---------------------------------------------------------------------------

// GPS coordinate pair used in FallEvent.
// Extracted as a named interface — ArkTS forbids inline object type literals
// (arkts-no-obj-literals-as-types).
export interface FallLocation {
  latitude: number;
  longitude: number;
}

// Payload passed to the fall callback. Location is optional — only set when
// GeofenceService has a recent GPS fix at the time of the fall.
export interface FallEvent {
  detected: boolean;
  timestamp: number;       // epoch ms
  location?: FallLocation; // last known GPS (if available)
}

// ---------------------------------------------------------------------------
// Interface
// ---------------------------------------------------------------------------

export interface FallDetector {
  // Begin fall monitoring. onFall fires once per confirmed fall event.
  startMonitoring(onFall: (event: FallEvent) => void): void;

  // Stop monitoring and release sensor resources.
  stopMonitoring(): void;
}

// ---------------------------------------------------------------------------
// MockFallDetector — emulator-safe, no sensor hardware required
// ---------------------------------------------------------------------------

// Used when AppConfig.IS_EMULATOR is true.
// A fall is triggered exclusively by calling simulateFall() from the debug panel.
export class MockFallDetector implements FallDetector {
  private callback: ((event: FallEvent) => void) | null = null;

  startMonitoring(onFall: (event: FallEvent) => void): void {
    this.callback = onFall;
    hilog.info(DOMAIN, 'MockFallDetector', 'Started — awaiting simulateFall() from debug panel');
  }

  stopMonitoring(): void {
    this.callback = null;
    hilog.info(DOMAIN, 'MockFallDetector', 'Stopped');
  }

  // Called from the debug panel "Simulate Fall" button.
  // Fires the fall callback exactly once — mirrors real-device single-fire behaviour.
  simulateFall(): void {
    hilog.warn(DOMAIN, 'MockFallDetector', 'Fall simulated via debug panel');
    if (this.callback !== null) {
      // No GPS location in mock falls — debug panel has no real location context.
      // ArkTS requires object literals to be explicitly typed (arkts-no-untyped-obj-literals).
      const event: FallEvent = {
        detected: true,
        timestamp: Date.now()
      };
      this.callback(event);
    }
  }
}

// ---------------------------------------------------------------------------
// RealFallDetector — real Huawei Watch hardware only
// ---------------------------------------------------------------------------

// NOT usable on the DevEco Studio watch emulator — emulator cannot inject raw
// accelerometer data. Use MockFallDetector when AppConfig.IS_EMULATOR is true.
export class RealFallDetector implements FallDetector {
  private callback: ((event: FallEvent) => void) | null = null;

  // Epoch ms of the last detected impact spike — 0 means no active spike window
  private lastImpactTime: number = 0;

  // Stored sensor callback reference for subscription management
  private sensorCallback: ((data: sensor.AccelerometerResponse) => void) | null = null;

  startMonitoring(onFall: (event: FallEvent) => void): void {
    this.callback = onFall;
    try {
      // Must be explicitly typed to satisfy arkts-no-untyped-obj-literals
      const sensorOptions: sensor.Options = { interval: 'normal' };
      this.sensorCallback = (data: sensor.AccelerometerResponse) => {
        this.processSample(data.x, data.y, data.z);
      };
      sensor.on(sensor.SensorId.ACCELEROMETER, this.sensorCallback, sensorOptions);
      hilog.info(DOMAIN, 'RealFallDetector', 'Accelerometer started for fall detection');
    } catch (err) {
      hilog.error(DOMAIN, 'RealFallDetector',
        'Accelerometer start failed: %{public}s', JSON.stringify(err));
    }
  }

  stopMonitoring(): void {
    try {
      // Unsubscribes all accelerometer listeners — same pattern as existing GeofenceService.
      sensor.off(sensor.SensorId.ACCELEROMETER);
    } catch (err) {
      hilog.warn(DOMAIN, 'RealFallDetector',
        'Accelerometer stop failed: %{public}s', JSON.stringify(err));
    }
    this.callback = null;
    this.sensorCallback = null;
    hilog.info(DOMAIN, 'RealFallDetector', 'Stopped');
  }

  // Called on each accelerometer reading (~5 Hz).
  private processSample(x: number, y: number, z: number): void {
    const magnitude = Math.sqrt(x * x + y * y + z * z);
    const netAccel = Math.abs(magnitude - 9.8);

    // Step 1 — detect impact spike (the "fall" moment)
    if (magnitude > AppConfig.FALL_IMPACT_THRESHOLD) {
      this.lastImpactTime = Date.now();
      hilog.info(DOMAIN, 'RealFallDetector',
        'Impact spike detected: %{public}f m/s²', magnitude);
      return; // don't check stillness in the same sample as the spike
    }

    // Step 2 — if a spike occurred recently and the person is now still, confirm fall
    if (
      this.lastImpactTime > 0 &&
      netAccel < AppConfig.FALL_STILLNESS_THRESHOLD &&
      (Date.now() - this.lastImpactTime) > AppConfig.FALL_STILLNESS_DURATION
    ) {
      hilog.warn(DOMAIN, 'RealFallDetector', 'Fall confirmed — spike followed by stillness');
      this.lastImpactTime = 0; // reset so we don't fire again for the same event
      this.fireFallEvent();
    }
  }

  private fireFallEvent(): void {
    if (this.callback !== null) {
      // Location is not set here — GeofenceService injects lastKnownLocation
      // when it receives this event before forwarding to the UI.
      const event: FallEvent = {
        detected: true,
        timestamp: Date.now()
      };
      this.callback(event);
    }
  }
}
