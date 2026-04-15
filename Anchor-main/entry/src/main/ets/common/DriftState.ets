// DriftState.ets
// Single source of truth for all geofence drift states.
// Imported by GeofenceService (logic) and Index.ets (UI) so both always agree on state names.

export enum DriftState {
  // No anchor point has been configured yet.
  // IMPORTANT: never show SAFE if the state is NOT_SET — that is a false negative.
  NOT_SET = 'NOT_SET',

  // Patient is within 30m of the anchor point, or no sustained motion is detected.
  SAFE = 'SAFE',

  // Sustained walking detected AND patient is 30–50m from anchor. Early warning.
  DRIFTING = 'DRIFTING',

  // Sustained walking detected AND patient is beyond 50m. Caregiver must be notified now.
  ALERT = 'ALERT'
}
