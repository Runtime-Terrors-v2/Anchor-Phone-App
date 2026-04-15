// MockMotionDetector.ets
// Emulator-safe motion detector — requires no sensor hardware.
// Walking and still states are driven entirely by explicit method calls
// from the hidden debug panel (long-press ANCHOR title on home screen).
//
// Used when AppConfig.IS_EMULATOR is true.
// Do NOT use on a real device — it will never report genuine motion.

import { hilog } from '@kit.PerformanceAnalysisKit';
import { MotionDetector, MotionEvent } from './MotionDetector';

const DOMAIN = 0x0000;

export class MockMotionDetector implements MotionDetector {
  // Registered callback — null when monitoring is stopped
  private callback: ((event: MotionEvent) => void) | null = null;

  // Current simulated walking state
  private walking: boolean = false;

  // Cumulative simulated step count — incremented by simulateWalking()
  private steps: number = 0;

  // Begin mock monitoring.
  // Emits an initial "still" event so GeofenceService has a known starting state.
  startMonitoring(onMotionChange: (event: MotionEvent) => void): void {
    this.callback = onMotionChange;
    hilog.info(DOMAIN, 'MockMotionDetector', 'Started — awaiting debug panel commands');
    // Emit the current (still) state immediately so the service is not left waiting
    this.emitState();
  }

  stopMonitoring(): void {
    this.callback = null;
    hilog.info(DOMAIN, 'MockMotionDetector', 'Stopped');
  }

  isCurrentlyWalking(): boolean {
    return this.walking;
  }

  // Called from the debug panel "Simulate Walking" button.
  // Switches motion state to walking and increments the step counter.
  simulateWalking(): void {
    this.walking = true;
    this.steps += 10;
    hilog.info(DOMAIN, 'MockMotionDetector', 'Simulating walking (steps: %{public}d)', this.steps);
    this.emitState();
  }

  // Called from the debug panel "Simulate Still" button.
  // Switches motion state back to still.
  simulateStill(): void {
    this.walking = false;
    hilog.info(DOMAIN, 'MockMotionDetector', 'Simulating still');
    this.emitState();
  }

  // Fires the registered callback with the current simulated state.
  // Always checks callback is non-null before calling (monitoring may be stopped).
  private emitState(): void {
    if (this.callback !== null) {
      // ArkTS requires object literals to be explicitly typed (arkts-no-untyped-obj-literals)
      const event: MotionEvent = {
        isWalking: this.walking,
        stepCount: this.steps,
        timestamp: Date.now()
      };
      this.callback(event);
    }
  }
}
