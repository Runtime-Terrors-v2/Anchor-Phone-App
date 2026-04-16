package com.Anchor.watchguardian.services

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

private const val TAG = "MotionDetector"

// Mirrors AppConfig thresholds from the HarmonyOS watch exactly.
private const val NET_THRESHOLD_MS2  = 1.2f  // net acceleration (|magnitude − 9.8|) to count as motion
private const val SAMPLE_WINDOW_SIZE = 10     // rolling average over ~2 seconds at SENSOR_DELAY_NORMAL
private const val DEBOUNCE_MS        = 2_000L // new state must hold for 2s before committing

data class MotionEvent(
    val isWalking: Boolean,
    val timestamp: Long
)

/**
 * Contract for motion detection.
 * Decouples PhoneGeofenceService from the Android sensor API — mirrors MotionDetector.ets.
 */
interface MotionDetector {
    fun startMonitoring(onMotionChange: (MotionEvent) -> Unit)
    fun stopMonitoring()
    fun isCurrentlyWalking(): Boolean
}

/**
 * Real accelerometer-based motion detector for Android phones.
 *
 * Algorithm mirrors RealMotionDetector.ets on the watch:
 *   1. Compute net acceleration = |magnitude − 9.8| on each sample (removes gravity)
 *   2. Maintain a rolling window of SAMPLE_WINDOW_SIZE samples
 *   3. If the rolling average exceeds NET_THRESHOLD, classify as walking
 *   4. Debounce state transitions by DEBOUNCE_MS to ignore chair-shuffles and one-off jolts
 *
 * NOTE: Phone orientation varies (pocket, table, held) so the raw threshold may need
 * tuning vs the watch. The 2-second debounce absorbs most single-jolt false positives.
 */
class RealMotionDetector(context: Context) : MotionDetector {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var callback: ((MotionEvent) -> Unit)? = null
    private val sampleWindow = ArrayDeque<Float>(SAMPLE_WINDOW_SIZE + 1)
    private var isWalking = false

    private val debounceHandler  = Handler(Looper.getMainLooper())
    private var pendingTransition: Runnable? = null

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val magnitude = sqrt(x * x + y * y + z * z)
            val net       = abs(magnitude - 9.8f)

            // Maintain rolling window
            sampleWindow.addLast(net)
            if (sampleWindow.size > SAMPLE_WINDOW_SIZE) sampleWindow.removeFirst()

            val avg       = sampleWindow.average().toFloat()
            val nowWalking = avg > NET_THRESHOLD_MS2

            if (nowWalking != isWalking) {
                // Cancel any pending transition before scheduling a new one —
                // prevents rapid oscillation from committing multiple state changes.
                pendingTransition?.let { debounceHandler.removeCallbacks(it) }
                val runnable = Runnable {
                    pendingTransition = null
                    isWalking = nowWalking
                    Log.i(TAG, "Motion state committed: walking=$nowWalking (avg=${"%.2f".format(avg)} m/s²)")
                    callback?.invoke(MotionEvent(nowWalking, System.currentTimeMillis()))
                }
                pendingTransition = runnable
                debounceHandler.postDelayed(runnable, DEBOUNCE_MS)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    override fun startMonitoring(onMotionChange: (MotionEvent) -> Unit) {
        callback = onMotionChange
        if (accelerometer == null) {
            Log.w(TAG, "No accelerometer available — motion detection disabled")
            // Emit a single not-walking event so the service starts in rest state
            onMotionChange(MotionEvent(false, System.currentTimeMillis()))
            return
        }
        sensorManager.registerListener(
            sensorListener,
            accelerometer,
            SensorManager.SENSOR_DELAY_NORMAL  // ~5 Hz — matches watch interval
        )
        // Emit initial state immediately so GeofenceService doesn't wait for first transition
        onMotionChange(MotionEvent(false, System.currentTimeMillis()))
        Log.i(TAG, "Accelerometer started")
    }

    override fun stopMonitoring() {
        sensorManager.unregisterListener(sensorListener)
        pendingTransition?.let { debounceHandler.removeCallbacks(it) }
        pendingTransition = null
        callback  = null
        Log.i(TAG, "Accelerometer stopped")
    }

    override fun isCurrentlyWalking(): Boolean = isWalking
}
