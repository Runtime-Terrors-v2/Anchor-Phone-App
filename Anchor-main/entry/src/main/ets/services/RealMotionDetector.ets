// RealMotionDetector.ets
// Accelerometer-based motion detector for real Huawei Watch hardware.
//
// Algorithm:
//   1. Subscribe to accelerometer at 'normal' (~5 Hz) — low power.
//   2. For each reading, compute net acceleration = |magnitude − 9.8| (strips gravity).
//   3. Maintain a rolling window of MOTION_SAMPLE_WINDOW readings and take the average.
//   4. If the average exceeds MOTION_NET_THRESHOLD → candidate state = walking.
//   5. Commit the candidate state only after it has been stable for MOTION_DEBOUNCE_MS.
//      This prevents a single jostle or bump from triggering a false walking event.
//
// NOT usable on the DevEco Studio watch emulator — the emulator does not support
// raw accelerometer injection. Use MockMotionDetector when IS_EMULATOR is true.

import sensor from '@ohos.sensor';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { MotionDetector, MotionEvent } from './MotionDetector';
import { AppConfig } from '../config/AppConfig';

const DOMAIN = 0x0000;

export class RealMotionDetector implements MotionDetector {
  // Registered callback — null when monitoring is stopped
  private callback: ((event: MotionEvent) => void) | null = null;

  // Rolling window of net-acceleration samples for averaging
  private motionSamples: number[] = [];

  // Committed walking state — only changes after debounce period
  private walking: boolean = false;

  // Candidate state tracking for debounce logic:
  //   candidateWalking — the state the sensor data is currently suggesting
  //   candidateStartTime — when this candidate state first appeared (epoch ms)
  // We don't commit to a new state until the candidate has been stable for MOTION_DEBOUNCE_MS.
  private candidateWalking: boolean = false;
  private candidateStartTime: number = 0;

  // Stored reference to the sensor callback so we can unsubscribe cleanly.
  // Using the same pattern as GeofenceService's locationListener.
  private sensorCallback: ((data: sensor.AccelerometerResponse) => void) | null = null;

  startMonitoring(onMotionChange: (event: MotionEvent) => void): void {
    this.callback = onMotionChange;
    try {
      // 'normal' interval ≈ 5 Hz — sufficient for walking detection, low power draw.
      // Must be explicitly typed to satisfy arkts-no-untyped-obj-literals.
      const sensorOptions: sensor.Options = { interval: 'normal' };
      this.sensorCallback = (data: sensor.AccelerometerResponse) => {
        this.processSample(data.x, data.y, data.z);
      };
      sensor.on(sensor.SensorId.ACCELEROMETER, this.sensorCallback, sensorOptions);
      hilog.info(DOMAIN, 'RealMotionDetector', 'Accelerometer started');
    } catch (err) {
      hilog.error(DOMAIN, 'RealMotionDetector', 'Accelerometer start failed: %{public}s', JSON.stringify(err));
    }
  }

  stopMonitoring(): void {
    try {
      // Unsubscribe the full sensor type — matches the existing GeofenceService pattern.
      // Note: if RealFallDetector is also active, it will need to re-subscribe after this.
      sensor.off(sensor.SensorId.ACCELEROMETER);
    } catch (err) {
      hilog.warn(DOMAIN, 'RealMotionDetector', 'Accelerometer stop failed: %{public}s', JSON.stringify(err));
    }
    this.callback = null;
    this.sensorCallback = null;
    hilog.info(DOMAIN, 'RealMotionDetector', 'Stopped');
  }

  isCurrentlyWalking(): boolean {
    return this.walking;
  }

  // Called on each accelerometer reading (~5 Hz).
  private processSample(x: number, y: number, z: number): void {
    // Total vector magnitude — at rest this equals ~9.8 m/s² (gravity)
    const magnitude = Math.sqrt(x * x + y * y + z * z);

    // Subtract gravity to get net acceleration from actual body movement
    const netAccel = Math.abs(magnitude - 9.8);

    // Maintain rolling window — drop oldest reading once window is full
    this.motionSamples.push(netAccel);
    if (this.motionSamples.length > AppConfig.MOTION_SAMPLE_WINDOW) {
      this.motionSamples.shift();
    }

    // Average net accel across window — sustained movement, not a single jostle
    const avgNetAccel =
      this.motionSamples.reduce((a: number, b: number) => a + b, 0) / this.motionSamples.length;

    const newCandidateWalking = avgNetAccel > AppConfig.MOTION_NET_THRESHOLD;

    // If the candidate state has flipped, reset the stability timer
    if (newCandidateWalking !== this.candidateWalking) {
      this.candidateWalking = newCandidateWalking;
      this.candidateStartTime = Date.now();
    }

    // Commit the candidate state only after it has been stable for MOTION_DEBOUNCE_MS.
    // This prevents GPS or motion jitter from causing rapid state flipping.
    const now = Date.now();
    if (
      this.candidateWalking !== this.walking &&
      (now - this.candidateStartTime) >= AppConfig.MOTION_DEBOUNCE_MS
    ) {
      this.walking = this.candidateWalking;
      hilog.info(DOMAIN, 'RealMotionDetector',
        'Motion state committed: walking=%{public}s', String(this.walking));
      this.emitState();
    }
  }

  private emitState(): void {
    if (this.callback !== null) {
      // ArkTS requires object literals to be explicitly typed (arkts-no-untyped-obj-literals)
      const event: MotionEvent = {
        isWalking: this.walking,
        stepCount: 0, // RealMotionDetector uses accelerometer, not pedometer — step count N/A
        timestamp: Date.now()
      };
      this.callback(event);
    }
  }
}
