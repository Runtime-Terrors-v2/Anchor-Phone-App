package com.Anchor.watchguardian.services

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

private const val TAG = "WatchMonitorService"

typealias ConnectCallback    = (deviceName: String) -> Unit
typealias DisconnectCallback = (deviceName: String) -> Unit

/**
 * Monitors Bluetooth connectivity to the Huawei Watch.
 *
 * Uses standard Android Bluetooth ACL broadcast events — no WearEngine AAR required.
 *
 * Three signal sources work together:
 *   1. ACTION_ACL_CONNECTED / ACTION_ACL_DISCONNECTED — real-time events for any BT device
 *   2. ACTION_STATE_CHANGED — fires when the user toggles Bluetooth off (no ACL event in that case)
 *   3. checkCurrentConnectionState() — checks bonded + GATT devices on startup and Refresh tap
 *
 * Lifecycle:
 *   init(context)             — called from MainActivity.onCreate(), stores context only
 *   setCallbacks(...)         — called from HomeViewModel before startMonitoring()
 *   startMonitoring()         — registers BroadcastReceiver, then runs initial state check
 *   stopMonitoring()          — unregisters receiver
 *   refreshConnectedDevices() — re-runs state check on "Refresh" tap
 *
 * Requires permissions (declared in AndroidManifest.xml):
 *   BLUETOOTH, BLUETOOTH_ADMIN, BLUETOOTH_CONNECT (API 31+), BLUETOOTH_SCAN (API 31+)
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

    var isWatchConnected:    Boolean = false
        private set
    var connectedDeviceName: String  = ""
        private set

    private var onConnect:    ConnectCallback?    = null
    private var onDisconnect: DisconnectCallback? = null

    /**
     * Store the application context.
     * Does NOT check connection state here — callbacks are not set yet at this point.
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
     *
     * Must be called after setCallbacks() so the initial state check can fire callbacks.
     */
    fun startMonitoring() {
        val ctx = appContext ?: run {
            Log.w(TAG, "startMonitoring: init() not called yet — skipping")
            return
        }
        if (btReceiver != null) return  // already registered

        btReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {

                    // Real-time connect / disconnect for any Bluetooth device
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

                        val name = safeGetDeviceName(device)

                        when (intent.action) {
                            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                                isWatchConnected    = true
                                connectedDeviceName = name
                                Log.i(TAG, "BT ACL connected: $name")
                                onConnect?.invoke(name)
                            }
                            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                                isWatchConnected    = false
                                connectedDeviceName = name
                                Log.w(TAG, "BT ACL disconnected: $name")
                                onDisconnect?.invoke(name)
                            }
                        }
                    }

                    // Bluetooth adapter turned off — no ACL_DISCONNECTED fires in this case
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(
                            BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR
                        )
                        if (state == BluetoothAdapter.STATE_OFF && isWatchConnected) {
                            val name = connectedDeviceName
                            isWatchConnected    = false
                            connectedDeviceName = ""
                            Log.w(TAG, "Bluetooth turned off — treating watch as disconnected")
                            onDisconnect?.invoke(name)
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

        // API 33+ (targeting API 34) requires an explicit export flag.
        // RECEIVER_NOT_EXPORTED is correct — system broadcasts bypass this flag anyway.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ctx.registerReceiver(btReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ctx.registerReceiver(btReceiver, filter)
        }

        Log.i(TAG, "Bluetooth monitor started — listening for ACL + state events")

        // Initial check now that callbacks are set
        checkCurrentConnectionState()
    }

    fun stopMonitoring() {
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
     * Check which Bluetooth devices are currently connected and update state.
     *
     * Covers both Classic BT (bonded devices) and BLE (GATT profile):
     *   - Iterates all bonded (paired) devices and calls isConnected() on each
     *   - Also checks BluetoothManager.getConnectedDevices(GATT) for BLE-only devices
     *
     * isConnected() is public on API 34+. On API 26–33 it is a hidden method called
     * via reflection — it has existed in AOSP since API 19 and works reliably.
     *
     * Must be called after setCallbacks() to fire callbacks correctly.
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

        // Bonded devices covers Classic BT connections (e.g. Huawei Watch companion link)
        val bondedDevices: Set<BluetoothDevice> = try {
            adapter.bondedDevices ?: emptySet()
        } catch (e: SecurityException) {
            Log.w(TAG, "getBondedDevices: SecurityException — ${e.message}")
            emptySet()
        }

        // GATT list covers BLE-only devices that may not be bonded
        val gattDevices: Set<BluetoothDevice> = try {
            btManager.getConnectedDevices(BluetoothProfile.GATT).toSet()
        } catch (e: SecurityException) {
            emptySet()
        }

        // Filter to devices that are actually connected right now
        val activeDevices = (bondedDevices + gattDevices).filter { isDeviceCurrentlyConnected(it) }

        // Prefer a watch-like name; fall back to any connected device
        val watchDevice = activeDevices.firstOrNull { isLikelyWatch(it) }
            ?: activeDevices.firstOrNull()

        if (watchDevice != null) {
            val name = safeGetDeviceName(watchDevice)
            if (!isWatchConnected) {
                isWatchConnected    = true
                connectedDeviceName = name
                Log.i(TAG, "State check: device connected — $name")
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
                Log.d(TAG, "State check: no device connected")
            }
        }
    }

    /**
     * Check if a BluetoothDevice is currently in a connected state.
     *
     * API 34+ exposes isConnected() as a public method.
     * API 26–33: call the same method via reflection (hidden but stable since API 19).
     * Fallback: check GATT connected list if reflection fails.
     */
    @SuppressLint("MissingPermission")
    private fun isDeviceCurrentlyConnected(device: BluetoothDevice): Boolean =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                device.isConnected
            } else {
                val method = BluetoothDevice::class.java.getMethod("isConnected")
                method.invoke(device) as Boolean
            }
        } catch (e: Exception) {
            Log.d(TAG, "isConnected() unavailable for ${safeGetDeviceName(device)}, falling back to GATT list")
            val btManager = appContext?.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            btManager?.getConnectedDevices(BluetoothProfile.GATT)?.contains(device) ?: false
        }

    /** Heuristic: does the device name suggest it's a Huawei / Honor wearable? */
    private fun isLikelyWatch(device: BluetoothDevice): Boolean {
        val name = safeGetDeviceName(device).lowercase()
        return name.contains("watch") || name.contains("huawei") || name.contains("honor")
    }

    /** Read device.name safely — requires BLUETOOTH_CONNECT on API 31+. */
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

    /**
     * Queues a contact-list payload for delivery to the watch.
     * Real delivery requires the WearEngine AAR (P2pClient.send).
     */
    fun syncContactsToWatch(contactsJson: String) {
        Log.d(TAG, "syncContactsToWatch: payload queued (WearEngine not wired)")
    }

    // --- Debug helpers ---

    fun simulateConnect(deviceName: String = "Huawei Watch Ultimate") {
        isWatchConnected    = true
        connectedDeviceName = deviceName
        onConnect?.invoke(deviceName)
    }

    fun simulateDisconnect(deviceName: String = "Huawei Watch Ultimate") {
        isWatchConnected    = false
        connectedDeviceName = ""
        onDisconnect?.invoke(deviceName)
    }
}
