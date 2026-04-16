package com.Anchor.watchguardian.services

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import com.Anchor.watchguardian.data.model.DriftState
import kotlin.math.*

private const val TAG   = "PhoneGeofenceService"
private const val PREFS = "anchor_geofence"

/**
 * Phone-side geofencing — mirrors GeofenceService.ets on the HarmonyOS watch.
 *
 * Combines GPS and motion detection to evaluate whether the monitored person
 * has drifted from their home anchor point, then fires state-change callbacks
 * that the UI and notification layer consume.
 *
 * State machine (identical to watch):
 *   NOT_SET  → no anchor configured
 *   SAFE     → within DRIFT_RADIUS_M, or no sustained motion
 *   DRIFTING → motion + 30–50m from anchor (early warning)
 *   ALERT    → motion + beyond ALERT_RADIUS_M (caregiver notified)
 *
 * GPS uses Android LocationManager with GPS_PROVIDER (falls back to NETWORK_PROVIDER).
 * Battery-aware: 60s interval at rest, 10s when motion is detected.
 *
 * Requires permissions (declared in AndroidManifest and requested at runtime):
 *   ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION
 */
class PhoneGeofenceService(private val context: Context) {

    companion object {
        const val DRIFT_RADIUS_M        = 30f       // metres — mirrors AppConfig.DRIFT_RADIUS
        const val ALERT_RADIUS_M        = 50f       // metres — mirrors AppConfig.ALERT_RADIUS
        const val GPS_INTERVAL_ACTIVE   = 10_000L   // ms when walking — mirrors GPS_INTERVAL_ACTIVE
        const val GPS_INTERVAL_REST     = 60_000L   // ms at rest     — mirrors GPS_INTERVAL_REST
    }

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val motionDetector  = RealMotionDetector(context)

    // --- State ---
    var currentState:  DriftState = DriftState.NOT_SET; private set
    var hasAnchor:     Boolean    = false;               private set
    var lastLocation:  Location?  = null;                private set

    private var anchorLat: Double  = 0.0
    private var anchorLng: Double  = 0.0
    private var isMotionDetected   = false
    private var locationListener: LocationListener? = null

    // Callbacks registered by GeofenceViewModel
    var onStateChange:  ((state: DriftState, distanceM: Float, bearingDeg: Float) -> Unit)? = null
    var onFallDetected: (() -> Unit)? = null

    // ------------------------------------------------------------------
    // Anchor management (mirrors GeofenceService.init / setAnchorPoint)
    // ------------------------------------------------------------------

    /**
     * Load a saved anchor from SharedPreferences and emit initial state.
     * Call once before startMonitoring() — mirrors GeofenceService.init().
     */
    fun init() {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lat   = prefs.getFloat("lat", 0f).toDouble()
        val lng   = prefs.getFloat("lng", 0f).toDouble()

        if (lat != 0.0 && lng != 0.0) {
            anchorLat = lat
            anchorLng = lng
            hasAnchor = true
            currentState = DriftState.SAFE
            Log.i(TAG, "Anchor loaded: ($lat, $lng)")
            // Emit SAFE immediately — distance 0 is a placeholder until first GPS fix
            onStateChange?.invoke(DriftState.SAFE, 0f, 0f)
        } else {
            Log.i(TAG, "No saved anchor — emitting NOT_SET")
            onStateChange?.invoke(DriftState.NOT_SET, 0f, 0f)
        }
    }

    /** Save a new anchor point — called when caregiver taps "Set Anchor Here". */
    fun setAnchorPoint(lat: Double, lng: Double) {
        anchorLat = lat
        anchorLng = lng
        hasAnchor = true
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat("lat", lat.toFloat())
            .putFloat("lng", lng.toFloat())
            .apply()
        currentState = DriftState.SAFE
        Log.i(TAG, "Anchor saved: ($lat, $lng)")
        onStateChange?.invoke(DriftState.SAFE, 0f, 0f)
    }

    /** Remove the anchor — returns to NOT_SET state. */
    fun clearAnchor() {
        hasAnchor = false
        anchorLat = 0.0
        anchorLng = 0.0
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove("lat")
            .remove("lng")
            .apply()
        currentState = DriftState.NOT_SET
        onStateChange?.invoke(DriftState.NOT_SET, 0f, 0f)
        Log.i(TAG, "Anchor cleared")
    }

    fun anchorLatLng(): Pair<Double, Double> = Pair(anchorLat, anchorLng)

    /**
     * Returns the best available location without waiting for a new GPS fix.
     * Checks in priority order:
     *   1. lastLocation — a live fix from our own GPS listener (most accurate)
     *   2. System's last known GPS location — cached by Android from any recent app
     *   3. Network / passive provider — less accurate but available immediately indoors
     *
     * This means "Set here" works even before the first update fires from our listener.
     */
    @SuppressLint("MissingPermission")
    fun getBestLastLocation(): android.location.Location? {
        if (lastLocation != null) return lastLocation
        return try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        } catch (e: Exception) {
            Log.w(TAG, "getLastKnownLocation failed: ${e.message}")
            null
        }
    }

    // ------------------------------------------------------------------
    // Monitoring lifecycle
    // ------------------------------------------------------------------

    /**
     * Start motion detection + GPS subscriptions.
     * Always call init() first so the anchor is loaded from storage.
     */
    fun startMonitoring() {
        Log.i(TAG, "Starting geofence monitoring")

        motionDetector.startMonitoring { event ->
            val wasWalking   = isMotionDetected
            isMotionDetected = event.isWalking

            if (event.isWalking != wasWalking) {
                Log.i(TAG, "Motion changed → walking=${event.isWalking}")
                val interval = if (event.isWalking) GPS_INTERVAL_ACTIVE else GPS_INTERVAL_REST
                restartGps(interval)
            }
        }

        // Start at rest interval — motion hasn't been reported yet
        startGps(GPS_INTERVAL_REST)
    }

    /** Stop all monitoring. Call from ViewModel.onCleared() or when leaving the screen. */
    fun stopMonitoring() {
        motionDetector.stopMonitoring()
        locationListener?.let { locationManager.removeUpdates(it) }
        locationListener = null
        Log.i(TAG, "Geofence monitoring stopped")
    }

    // ------------------------------------------------------------------
    // GPS tracking
    // ------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun startGps(intervalMs: Long) {
        val listener = LocationListener { location ->
            lastLocation = location

            if (!hasAnchor) {
                onStateChange?.invoke(DriftState.NOT_SET, 0f, 0f)
                return@LocationListener
            }

            val distM    = haversineDistance(anchorLat, anchorLng, location.latitude, location.longitude)
            val bearDeg  = calculateBearing(anchorLat, anchorLng, location.latitude, location.longitude)

            Log.d(TAG, "GPS fix: ${location.latitude}, ${location.longitude} — ${distM.toInt()}m from anchor")
            evaluateDriftState(distM, bearDeg)
        }

        locationListener = listener

        // Prefer GPS for accuracy; fall back to network provider if GPS is unavailable
        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)     -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> {
                Log.w(TAG, "No location provider available")
                return
            }
        }

        try {
            locationManager.requestLocationUpdates(
                provider,
                intervalMs,
                0f,              // minDistanceM — time-based only, mirrors watch behaviour
                listener,
                Looper.getMainLooper()
            )
            Log.i(TAG, "GPS started ($provider, ${intervalMs / 1000}s interval)")
        } catch (e: SecurityException) {
            Log.e(TAG, "GPS start failed — permission missing: ${e.message}")
        }
    }

    private fun restartGps(intervalMs: Long) {
        locationListener?.let { locationManager.removeUpdates(it) }
        locationListener = null
        startGps(intervalMs)
    }

    // ------------------------------------------------------------------
    // Drift state machine (mirrors evaluateDriftState in GeofenceService.ets)
    // ------------------------------------------------------------------

    private fun evaluateDriftState(distanceM: Float, bearingDeg: Float) {
        val newState = when {
            !isMotionDetected || distanceM < DRIFT_RADIUS_M -> DriftState.SAFE
            distanceM < ALERT_RADIUS_M                      -> DriftState.DRIFTING
            else                                            -> DriftState.ALERT
        }

        if (newState != currentState) {
            Log.i(TAG, "State: $currentState → $newState")
            currentState = newState

            // Notify caregiver only on the critical ALERT transition
            if (newState == DriftState.ALERT) {
                NotificationHelper.showGeofenceAlert(context, distanceM)
            }
        }

        // Always callback so the UI distance readout stays live
        onStateChange?.invoke(newState, distanceM, bearingDeg)
    }

    // ------------------------------------------------------------------
    // Distance and bearing (mirrors haversineDistance / calculateBearing in GeofenceService.ets)
    // ------------------------------------------------------------------

    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val R    = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a    = sin(dLat / 2).pow(2) +
                   cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return (R * 2 * atan2(sqrt(a), sqrt(1 - a))).toFloat()
    }

    /** Returns bearing in degrees clockwise from north (0–360). */
    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val dLon = Math.toRadians(lon2 - lon1)
        val y    = sin(dLon) * cos(Math.toRadians(lat2))
        val x    = cos(Math.toRadians(lat1)) * sin(Math.toRadians(lat2)) -
                   sin(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * cos(dLon)
        return ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
    }
}
