package com.Anchor.watchguardian.data.model

/**
 * Geofence drift states — mirrors DriftState.ets on the HarmonyOS watch exactly.
 * Single source of truth used by PhoneGeofenceService, GeofenceViewModel, and GeofenceScreen.
 */
enum class DriftState {
    /** No anchor point configured yet. Never show SAFE when in this state. */
    NOT_SET,

    /** Within 30m of anchor, or no sustained motion detected. */
    SAFE,

    /** Sustained walking AND 30–50m from anchor. Early warning. */
    DRIFTING,

    /** Sustained walking AND beyond 50m from anchor. Alert caregivers immediately. */
    ALERT
}
