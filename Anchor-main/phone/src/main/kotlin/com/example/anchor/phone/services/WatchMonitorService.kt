package com.Anchor.watchguardian.services

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

private const val TAG = "WatchMonitorService"

// Huawei watches keep multiple simultaneous BT links (Classic BT + BLE). When the
// BLE link briefly drops and reconnects in the background, ACTION_ACL_DISCONNECTED fires
// even though the watch is still physically on your wrist. We wait this long before
// treating a disconnect event as real, giving the watch time to re-establish its link.
private const val DISCONNECT_DEBOUNCE_MS = 5_000L

typealias ConnectCallback    = (deviceName: String) -> Unit
typealias DisconnectCallback = (deviceName: String) -> Unit

/**
 * Monitors Bluetooth connectivity to the Huawei Watch.
 *
 * Uses standard Android Bluetooth ACL broadcast events — no WearEngine AAR required.
 *
 * Signal sources:
 *   1. ACTION_ACL_CONNECTED      — fires immediately; watch is connected
 *   2. ACTION_ACL_DISCONNECTED   — debounced 5s then verified; avoids false alarms from
 *                                   transient BLE link drops on multi-link watches
 *   3. ACTION_STATE_CHANGED      — Bluetooth turned off; fire disconnect with no debounce
 *   4. checkCurrentConnectionState() — bonded + GATT scan on startup and Refresh tap
 *
 * Lifecycle:
 *   init(context)             — called from MainActivity.onCreate(), stores context only
 *   setCallbacks(...)         — called from HomeViewModel before startMonitoring()
 *   startMonitoring()         — registers receiver, then does initial state check
 *   stopMonitoring()          — unregisters receiver, cancels pending debounce
 *   refreshConnectedDevices() — re-runs state check on "Refresh" tap
 */
class WatchMonitorService private constructor() {

    companion object {
        @Volatile
        private var INSTANCE: WatchMonitorService? = null

        fun getInstance(): WatchMonitorService =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: WatchMonitorService().also { INSTANCE = it }
            }
    }

    private var appContext: Context? = null
    private var btReceiver: BroadcastReceiver? = null

    // Handler + pending runnable for the disconnect debounce
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingDisconnect: Runnable? = null

    var isWatchConnected:    Boolean = false
        private set
    var connectedDeviceName: String  = ""
        private set

    private var onConnect:    ConnectCallback?    = null
    private var onDisconnect: DisconnectCallback? = null

    /**
     * Store the application context.
     * Does NOT check connection state here — callbacks aren't set yet.
     * State check happens in startMonitoring() after setCallbacks() has been called.
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        Log.i(TAG, "WatchMonitorService initialized")
    }

    fun setCallbacks(onConnect: ConnectCallback, onDisconnect: DisconnectCallback) {
        this.onConnect    = onConnect
        this.onDisconnect = onDisconnect
    }

    /**
     * Register a BroadcastReceiver for Bluetooth events, then check current state.
     * Safe to call multiple times — registers only once.
     * Must be called after setCallbacks().
     */
    fun startMonitoring() {
        val ctx = appContext ?: run {
            Log.w(TAG, "startMonitoring: init() not called yet — skipping")
            return
        }
        if (btReceiver != null) return

        btReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {

                    BluetoothDevice.ACTION_ACL_CONNECTED,
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        val device: BluetoothDevice =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(
                                    BluetoothDevice.EXTRA_DEVICE,
                                    BluetoothDevice::class.java
                                )
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                            } ?: return

                        if (!isWatchDevice(device)) {
                            Log.d(TAG, "Ignoring non-watch BT event: ${safeGetDeviceName(device)}")
                            return
                        }

                        val name = safeGetDeviceName(device)

                        when (intent.action) {
                            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                                // Cancel any pending disconnect — the watch reconnected
                                // within the debounce window (normal for BLE link cycling)
                                cancelPendingDisconnect()
                                if (!isWatchConnected) {
                                    isWatchConnected    = true
                                    connectedDeviceName = name
                                    Log.i(TAG, "Watch connected: $name")
                                    onConnect?.invoke(name)
                                }
                            }
                            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                                // Don't fire immediately — wait DISCONNECT_DEBOUNCE_MS then
                                // verify the watch is actually gone. If the BLE link just
                                // cycled, ACTION_ACL_CONNECTED will arrive first and cancel this.
                                Log.d(TAG, "ACL disconnected for $name — debouncing ${DISCONNECT_DEBOUNCE_MS}ms")
                                schedulePendingDisconnect(device, name)
                            }
                        }
                    }

                    // Bluetooth turned off — all links are gone, no ACL_DISCONNECTED fires
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(
                            BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR
                        )
                        if (state == BluetoothAdapter.STATE_OFF) {
                            cancelPendingDisconnect()
                            if (isWatchConnected) {
                                val name = connectedDeviceName
                                isWatchConnected    = false
                                connectedDeviceName = ""
                                Log.w(TAG, "Bluetooth turned off — watch disconnected")
                                onDisconnect?.invoke(name)
                            }
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ctx.registerReceiver(btReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ctx.registerReceiver(btReceiver, filter)
        }

        Log.i(TAG, "Bluetooth monitor started")
        checkCurrentConnectionState()
    }

    fun stopMonitoring() {
        cancelPendingDisconnect()
        val ctx = appContext ?: return
        btReceiver?.let { receiver ->
            try {
                ctx.unregisterReceiver(receiver)
                Log.i(TAG, "Bluetooth monitor stopped")
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Receiver was already unregistered")
            }
            btReceiver = null
        }
    }

    fun refreshConnectedDevices() {
        Log.d(TAG, "Refresh requested — re-checking connection state")
        checkCurrentConnectionState()
    }

    /**
     * Schedule a disconnect to fire after DISCONNECT_DEBOUNCE_MS.
     * The runnable re-checks the device's actual connection state before firing —
     * if the watch reconnected in the meantime, the callback is suppressed.
     */
    private fun schedulePendingDisconnect(device: BluetoothDevice, name: String) {
        cancelPendingDisconnect()
        val runnable = Runnable {
            pendingDisconnect = null
            if (isDeviceCurrentlyConnected(device)) {
                // The watch re-established its link within the debounce window
                Log.d(TAG, "Debounce: $name is still connected — ignoring transient disconnect")
            } else {
                isWatchConnected    = false
                connectedDeviceName = name
                Log.w(TAG, "Debounce: $name confirmed disconnected")
                onDisconnect?.invoke(name)
            }
        }
        pendingDisconnect = runnable
        mainHandler.postDelayed(runnable, DISCONNECT_DEBOUNCE_MS)
    }

    private fun cancelPendingDisconnect() {
        pendingDisconnect?.let { mainHandler.removeCallbacks(it) }
        pendingDisconnect = null
    }

    /**
     * Check which Bluetooth devices are currently connected and update state.
     * Covers Classic BT (bonded devices via reflection) and BLE (GATT list).
     */
    private fun checkCurrentConnectionState() {
        val ctx = appContext ?: return

        if (!hasBluetoothPermission(ctx)) {
            Log.w(TAG, "BLUETOOTH_CONNECT permission not granted — skipping state check")
            return
        }

        val btManager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return
        val adapter   = btManager.adapter

        if (adapter == null || !adapter.isEnabled) {
            Log.d(TAG, "Bluetooth disabled — watch is disconnected")
            if (isWatchConnected) {
                val name = connectedDeviceName
                isWatchConnected    = false
                connectedDeviceName = ""
                onDisconnect?.invoke(name)
            }
            return
        }

        val bondedDevices: Set<BluetoothDevice> = try {
            adapter.bondedDevices ?: emptySet()
        } catch (e: SecurityException) {
            Log.w(TAG, "getBondedDevices: SecurityException — ${e.message}")
            emptySet()
        }

        val gattDevices: Set<BluetoothDevice> = try {
            btManager.getConnectedDevices(BluetoothProfile.GATT).toSet()
        } catch (e: SecurityException) {
            emptySet()
        }

        val activeDevices = (bondedDevices + gattDevices)
            .filter { isWatchDevice(it) && isDeviceCurrentlyConnected(it) }

        // Prefer a device with the WEARABLE class; fall back to name-matched device
        val watchDevice = activeDevices.firstOrNull {
            it.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.WEARABLE
        } ?: activeDevices.firstOrNull()

        if (watchDevice != null) {
            val name = safeGetDeviceName(watchDevice)
            if (!isWatchConnected) {
                isWatchConnected    = true
                connectedDeviceName = name
                Log.i(TAG, "State check: watch found connected — $name")
                onConnect?.invoke(name)
            } else {
                Log.d(TAG, "State check: still connected to $connectedDeviceName")
            }
        } else {
            if (isWatchConnected) {
                val name = connectedDeviceName
                isWatchConnected    = false
                connectedDeviceName = ""
                Log.w(TAG, "State check: $name no longer connected")
                onDisconnect?.invoke(name)
            } else {
                Log.d(TAG, "State check: no watch connected")
            }
        }
    }

    /**
     * Check if a specific BluetoothDevice is currently connected via reflection.
     * isConnected() has been a stable hidden method in AOSP since API 19.
     * Falls back to the GATT list if reflection fails.
     */
    @SuppressLint("MissingPermission")
    private fun isDeviceCurrentlyConnected(device: BluetoothDevice): Boolean =
        try {
            val method = BluetoothDevice::class.java.getMethod("isConnected")
            method.invoke(device) as Boolean
        } catch (e: Exception) {
            Log.d(TAG, "isConnected() reflection failed — falling back to GATT list")
            val btManager = appContext?.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            btManager?.getConnectedDevices(BluetoothProfile.GATT)?.contains(device) ?: false
        }

    /**
     * Returns true if the device is a smartwatch.
     * Checks BluetoothClass.Device.Major.WEARABLE first (hardware-set, brand-agnostic),
     * then falls back to name keywords.
     */
    private fun isWatchDevice(device: BluetoothDevice): Boolean {
        val btClass = device.bluetoothClass
        if (btClass != null && btClass.majorDeviceClass == BluetoothClass.Device.Major.WEARABLE) {
            return true
        }
        val name = safeGetDeviceName(device).lowercase()
        return name.contains("watch") || name.contains("band") ||
               name.contains("huawei") || name.contains("honor")
    }

    @SuppressLint("MissingPermission")
    private fun safeGetDeviceName(device: BluetoothDevice): String =
        try {
            device.name?.takeIf { it.isNotBlank() } ?: device.address
        } catch (e: SecurityException) {
            device.address
        }

    private fun hasBluetoothPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }

    fun syncContactsToWatch(contactsJson: String) {
        Log.d(TAG, "syncContactsToWatch: payload queued (WearEngine not wired)")
    }

    // --- Debug helpers ---

    fun simulateConnect(deviceName: String = "Huawei Watch Ultimate") {
        cancelPendingDisconnect()
        isWatchConnected    = true
        connectedDeviceName = deviceName
        onConnect?.invoke(deviceName)
    }

    fun simulateDisconnect(deviceName: String = "Huawei Watch Ultimate") {
        cancelPendingDisconnect()
        isWatchConnected    = false
        connectedDeviceName = ""
        onDisconnect?.invoke(deviceName)
    }
}
