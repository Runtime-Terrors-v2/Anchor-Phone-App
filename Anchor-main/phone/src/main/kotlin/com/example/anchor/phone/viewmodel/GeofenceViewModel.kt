package com.Anchor.watchguardian.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.Anchor.watchguardian.data.model.DriftState
import com.Anchor.watchguardian.services.PhoneGeofenceService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "GeofenceViewModel"

/**
 * ViewModel for GeofenceScreen.
 *
 * Owns PhoneGeofenceService and exposes its state as StateFlows for Compose.
 * Mirrors the relationship between HomeViewModel and WatchMonitorService.
 */
class GeofenceViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context = application.applicationContext
    private val geofenceService  = PhoneGeofenceService(context)

    // --- Exposed state ---
    private val _driftState   = MutableStateFlow(DriftState.NOT_SET)
    private val _distanceM    = MutableStateFlow(0f)
    private val _bearingDeg   = MutableStateFlow(0f)
    private val _isMonitoring = MutableStateFlow(false)
    private val _hasAnchor    = MutableStateFlow(false)
    private val _anchorText   = MutableStateFlow("Not set")
    private val _anchorCoords = MutableStateFlow<Pair<Double, Double>?>(null)
    /** Human-readable GPS fix status shown near the "Set here" button. */
    private val _gpsStatus    = MutableStateFlow("No GPS fix yet")

    val driftState:   StateFlow<DriftState>              = _driftState
    val distanceM:    StateFlow<Float>                   = _distanceM
    val bearingDeg:   StateFlow<Float>                   = _bearingDeg
    val isMonitoring: StateFlow<Boolean>                 = _isMonitoring
    val hasAnchor:    StateFlow<Boolean>                 = _hasAnchor
    val anchorText:   StateFlow<String>                  = _anchorText
    /** Lat/lng of the current anchor — non-null only when hasAnchor is true. */
    val anchorCoords: StateFlow<Pair<Double, Double>?>   = _anchorCoords
    val gpsStatus:    StateFlow<String>                  = _gpsStatus

    init {
        // State change callback — drives the drift state card and distance readout
        geofenceService.onStateChange = { state, dist, bearing ->
            _driftState.value = state
            _distanceM.value  = dist
            _bearingDeg.value = bearing
        }

        // Location status callback — drives the GPS status label near "Set here"
        geofenceService.onLocationStatus = { status ->
            _gpsStatus.value = status
        }

        geofenceService.init()
        _hasAnchor.value = geofenceService.hasAnchor
        updateAnchorText()
        if (geofenceService.hasAnchor) {
            _anchorCoords.value = geofenceService.anchorLatLng()
        }

        // Probe immediately for a cached fix so gpsStatus shows something useful
        // before the user taps Start — no permission prompt, just reads cache
        val cached = geofenceService.getBestLastLocation()
        _gpsStatus.value = if (cached != null) "GPS fix ready ✓" else "No GPS fix yet"
    }

    /** Start GPS + motion monitoring. Called when the user enables monitoring on-screen. */
    fun startMonitoring() {
        if (_isMonitoring.value) return
        geofenceService.startMonitoring()
        _isMonitoring.value = true
        Log.i(TAG, "Monitoring started")
    }

    /** Stop GPS + motion monitoring. */
    fun stopMonitoring() {
        if (!_isMonitoring.value) return
        geofenceService.stopMonitoring()
        _isMonitoring.value = false
        Log.i(TAG, "Monitoring stopped")
    }

    /**
     * Save the provided coordinates as the home anchor point.
     * Typically called with the device's last known location.
     */
    fun setAnchorPoint(lat: Double, lng: Double) {
        geofenceService.setAnchorPoint(lat, lng)
        _hasAnchor.value    = true
        _anchorCoords.value = Pair(lat, lng)
        updateAnchorText()
        Log.i(TAG, "Anchor set: ($lat, $lng)")
    }

    /** Remove the anchor — returns to NOT_SET state. */
    fun clearAnchor() {
        if (_isMonitoring.value) stopMonitoring()
        geofenceService.clearAnchor()
        _hasAnchor.value    = false
        _anchorText.value   = "Not set"
        _anchorCoords.value = null
    }

    /** Best available GPS fix — tries live location, then system cache. */
    fun getLastLocation() = geofenceService.getBestLastLocation()

    private fun updateAnchorText() {
        if (!geofenceService.hasAnchor) { _anchorText.value = "Not set"; return }
        val (lat, lng) = geofenceService.anchorLatLng()
        _anchorText.value = "${"%.4f".format(lat)}°, ${"%.4f".format(lng)}°"
    }

    override fun onCleared() {
        super.onCleared()
        geofenceService.stopMonitoring()
    }
}
