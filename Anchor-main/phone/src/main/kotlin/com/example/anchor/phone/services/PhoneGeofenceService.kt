package com.Anchor.watchguardian.services

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.Anchor.watchguardian.data.model.DriftState
import kotlin.math.*

private const val TAG   = "PhoneGeofenceService"
private const val PREFS = "anchor_geofence"

/**
 * Phone-side geofencing — mirrors GeofenceService.ets on the HarmonyOS watch.
 *
 * Key fixes vs previous version:
 *  1. Registers for BOTH GPS_PROVIDER and NETWORK_PROVIDER simultaneously.
 *     Indoors, GPS is "enabled" in settings but never fires; network fills the gap.
 *  2. Explicit permission + location-enabled check before registering listeners.
 *     Failures are now surfaced via onLocationStatus callback, not silent log lines.
 *  3. onLocationStatus callback lets the ViewModel show live GPS status to the user
 *     ("Waiting for GPS fix…", "GPS fix ready ✓", "Location permission denied", etc.)
 *  4. minDistanceM set to 1f to avoid jitter-storms on sensitive devices.
 *  5. getBestLastLocation() seeds the initial fix on "Set here" before monitoring starts.
 */
class PhoneGeofenceService(private val context: Context) {

    companion object {
        const val DRIFT_RADIUS_M      = 30f
        const val ALERT_RADIUS_M      = 50f
        const val GPS_INTERVAL_ACTIVE = 10_000L
        const val GPS_INTERVAL_REST   = 60_000L
    }

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE)
            as LocationManager
    private val motionDetector  = RealMotionDetector(context)

    // --- State ---
    var currentState: DriftState = DriftState.NOT_SET; private set
    var hasAnchor:    Boolean    = false;               private set
    var lastLocation: Location?  = null;                private set

    private var anchorLat = 0.0
    private var anchorLng = 0.0
    private var isMotionDetected = false

    // Two listeners — one per provider so both run simultaneously
    private var gpsListener:     LocationListener? = null
    private var networkListener: LocationListener? = null

    // Callbacks for ViewModel
    var onStateChange:    ((DriftState, Float, Float) -> Unit)? = null
    var onLocationStatus: ((String) -> Unit)?                   = null

    // ------------------------------------------------------------------
    // Anchor management
    // ------------------------------------------------------------------

    fun init() {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lat   = prefs.getFloat("lat", 0f).toDouble()
        val lng   = prefs.getFloat("lng", 0f).toDouble()

        if (lat != 0.0 && lng != 0.0) {
            anchorLat    = lat
            anchorLng    = lng
            hasAnchor    = true
            currentState = DriftState.SAFE
            Log.i(TAG, "Anchor loaded: ($lat, $lng)")
            onStateChange?.invoke(DriftState.SAFE, 0f, 0f)
        } else {
            onStateChange?.invoke(DriftState.NOT_SET, 0f, 0f)
        }
    }

    fun setAnchorPoint(lat: Double, lng: Double) {
        anchorLat    = lat
        anchorLng    = lng
        hasAnchor    = true
        currentState = DriftState.SAFE
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putFloat("lat", lat.toFloat()).putFloat("lng", lng.toFloat()).apply()
        Log.i(TAG, "Anchor saved: ($lat, $lng)")
        onStateChange?.invoke(DriftState.SAFE, 0f, 0f)
    }

    fun clearAnchor() {
        hasAnchor    = false
        anchorLat    = 0.0
        anchorLng    = 0.0
        currentState = DriftState.NOT_SET
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove("lat").remove("lng").apply()
        onStateChange?.invoke(DriftState.NOT_SET, 0f, 0f)
    }

    fun anchorLatLng(): Pair<Double, Double> = Pair(anchorLat, anchorLng)

    /**
     * Best available location without waiting for a new fix.
     * Order: live fix from our listener → system GPS cache → network cache → passive cache.
     */
    @SuppressLint("MissingPermission")
    fun getBestLastLocation(): Location? {
        if (lastLocation != null) return lastLocation
        if (!hasLocationPermission()) return null
        return try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        } catch (e: Exception) {
            Log.w(TAG, "getLastKnownLocation: ${e.message}")
            null
        }
    }

    // ------------------------------------------------------------------
    // Monitoring lifecycle
    // ------------------------------------------------------------------

    fun startMonitoring() {
        // --- Pre-flight checks ---
        if (!hasLocationPermission()) {
            val msg = "Location permission not granted — go to Settings → Apps → ANCHOR → Permissions"
            Log.e(TAG, msg)
            onLocationStatus?.invoke("Location permission denied")
            return
        }
        if (!LocationManagerCompat.isLocationEnabled(locationManager)) {
            val msg = "Location services are OFF — enable in device Settings"
            Log.e(TAG, msg)
            onLocationStatus?.invoke("Location services are OFF")
            return
        }

        Log.i(TAG, "Starting geofence monitoring")
        onLocationStatus?.invoke("Waiting for GPS fix…")

        // Start motion detector — switches GPS interval on walk/rest changes
        motionDetector.startMonitoring { event ->
            val wasWalking   = isMotionDetected
            isMotionDetected = event.isWalking
            if (event.isWalking != wasWalking) {
                val interval = if (event.isWalking) GPS_INTERVAL_ACTIVE else GPS_INTERVAL_REST
                Log.i(TAG, "Motion changed → walking=${event.isWalking}, interval=${interval / 1000}s")
                restartGps(interval)
            }
        }

        startGps(GPS_INTERVAL_REST)
    }

    fun stopMonitoring() {
        motionDetector.stopMonitoring()
        removeAllListeners()
        Log.i(TAG, "Geofence monitoring stopped")
    }

    // ------------------------------------------------------------------
    // GPS — registers BOTH providers simultaneously
    // ------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun startGps(intervalMs: Long) {
        removeAllListeners()

        val gpsEnabled     = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!gpsEnabled && !networkEnabled) {
            Log.w(TAG, "No location provider available")
            onLocationStatus?.invoke("No location provider available")
            return
        }

        // Shared callback — updates lastLocation with whichever provider fires
        // and keeps the more accurate fix if two arrive at the same time
        fun makeListener(providerName: String) = LocationListener { location ->
            val current = lastLocation
            // Accept the new fix if we have no fix yet, or if the new one is more accurate
            if (current == null || location.accuracy <= current.accuracy) {
                lastLocation = location
                onLocationStatus?.invoke("GPS fix ready ✓")
                Log.d(TAG, "$providerName fix: ${location.latitude}, ${location.longitude}" +
                           " acc=±${location.accuracy.toInt()}m")
            }

            if (!hasAnchor) {
                onStateChange?.invoke(DriftState.NOT_SET, 0f, 0f)
                return@LocationListener
            }

            val distM   = haversineDistance(anchorLat, anchorLng, location.latitude, location.longitude)
            val bearDeg = calculateBearing(anchorLat, anchorLng, location.latitude, location.longitude)
            evaluateDriftState(distM, bearDeg)
        }

        // Register GPS provider (accurate, slower to fix, doesn't work indoors)
        if (gpsEnabled) {
            try {
                val listener = makeListener("GPS")
                gpsListener  = listener
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    intervalMs,
                    1f,   // minDistanceM — small non-zero value avoids jitter-storms
                    listener,
                    Looper.getMainLooper()
                )
                Log.i(TAG, "GPS_PROVIDER registered (${intervalMs / 1000}s interval)")
            } catch (e: Exception) {
                Log.e(TAG, "GPS_PROVIDER registration failed: ${e.message}")
            }
        }

        // Register NETWORK provider (less accurate, works indoors, fast first fix)
        if (networkEnabled) {
            try {
                val listener     = makeListener("Network")
                networkListener  = listener
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    intervalMs,
                    1f,
                    listener,
                    Looper.getMainLooper()
                )
                Log.i(TAG, "NETWORK_PROVIDER registered (${intervalMs / 1000}s interval)")
            } catch (e: Exception) {
                Log.e(TAG, "NETWORK_PROVIDER registration failed: ${e.message}")
            }
        }
    }

    private fun restartGps(intervalMs: Long) {
        removeAllListeners()
        startGps(intervalMs)
    }

    private fun removeAllListeners() {
        gpsListener?.let     { locationManager.removeUpdates(it) }
        networkListener?.let { locationManager.removeUpdates(it) }
        gpsListener     = null
        networkListener = null
    }

    // ------------------------------------------------------------------
    // Drift state machine
    // ------------------------------------------------------------------

    private fun evaluateDriftState(distanceM: Float, bearingDeg: Float) {
        val newState = when {
            !isMotionDetected || distanceM < DRIFT_RADIUS_M -> DriftState.SAFE
            distanceM < ALERT_RADIUS_M                      -> DriftState.DRIFTING
            else                                            -> DriftState.ALERT
        }

        if (newState != currentState) {
            Log.i(TAG, "State: $currentState → $newState (dist=${distanceM.toInt()}m)")
            currentState = newState
            if (newState == DriftState.ALERT) {
                NotificationHelper.showGeofenceAlert(context, distanceM)
            }
        }

        onStateChange?.invoke(newState, distanceM, bearingDeg)
    }

    // ------------------------------------------------------------------
    // Permission helper
    // ------------------------------------------------------------------

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    // ------------------------------------------------------------------
    // Haversine distance + bearing
    // ------------------------------------------------------------------

    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val R    = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a    = sin(dLat / 2).pow(2) +
                   cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return (R * 2 * atan2(sqrt(a), sqrt(1 - a))).toFloat()
    }

    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val dLon = Math.toRadians(lon2 - lon1)
        val y    = sin(dLon) * cos(Math.toRadians(lat2))
        val x    = cos(Math.toRadians(lat1)) * sin(Math.toRadians(lat2)) -
                   sin(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * cos(dLon)
        return ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
    }
}
