// MotionDetector.ets
// Interface for motion detection — decouples GeofenceService from the sensor API.
// GeofenceService only ever touches this interface, never @ohos.sensor directly.
// This allows the same business logic to run on both the real watch and the emulator.

// Data passed to the motion change callback on every state transition.
export interface MotionEvent {
  // true when sustained walking movement has been detected
  isWalking: boolean;

  // Cumulative step count — populated by MockMotionDetector (emulator step injection);
  // RealMotionDetector sets this to 0 because it uses raw accelerometer, not pedometer.
  stepCount: number;

  // Unix epoch timestamp in milliseconds when this event was generated
  timestamp: number;
}

// The contract that both MockMotionDetector and RealMotionDetector must satisfy.
export interface MotionDetector {
  // Begin sensor monitoring. Fires onMotionChange whenever the walking state changes.
  // Implementations must also emit an initial event on start so GeofenceService
  // knows the starting state without waiting for the first real transition.
  startMonitoring(onMotionChange: (event: MotionEvent) => void): void;

  // Stop monitoring and release all sensor resources.
  // Must be called in onBackground / onDestroy to avoid background battery drain.
  stopMonitoring(): void;

  // Synchronous snapshot of current walking state.
  // Used by GeofenceService to check motion without waiting for a callback,
  // e.g. when a new GPS fix arrives between motion events.
  isCurrentlyWalking(): boolean;
}
