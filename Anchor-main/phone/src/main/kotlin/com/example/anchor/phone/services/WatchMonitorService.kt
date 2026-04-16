package com.Anchor.watchguardian.services

import android.Manifest
import android.annotation.SuppressLint
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
 * ACTION_ACL_CONNECTED / ACTION_ACL_DISCONNECTED fire whenever any Bluetooth device
 * connects or disconnects from the phone, giving us real-time watch status.
 *
 * Lifecycle (mirrors the old stub interface exactly so HomeViewModel needs no changes):
 *   init(context)             — called from MainActivity.onCreate(), stores app context
 *                               and does an initial BLE check for already-connected devices
 *   startMonitoring()         — called from HomeViewModel.init(), registers BroadcastReceiver
 *   stopMonitoring()          — called from MainActivity.onDestroy(), unregisters receiver
 *   refreshConnectedDevices() — called on "Refresh" tap, re-checks GATT connected devices
 *
 * Requires permissions (already declared in AndroidManifest.xml):
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
     * Store the application context and do an initial BLE connection check.
     * Called from MainActivity.onCreate() before the ViewModel is created.
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        Log.i(TAG, "WatchMonitorService initialized")
        checkCurrentConnectionState()
    }

    fun setCallbacks(onConnect: ConnectCallback, onDisconnect: DisconnectCallback) {
        this.onConnect    = onConnect
        this.onDisconnect = onDisconnect
    }

    /**
     * Register a BroadcastReceiver for Bluetooth ACL connect/disconnect events.
     * These are system broadcasts sent by the Bluetooth stack — no export flag issues.
     * Safe to call multiple times; won't register twice.
     */
    fun startMonitoring() {
        val ctx = appContext ?: run {
            Log.w(TAG, "startMonitoring: init() not called yet — skipping")
            return
        }
        if (btReceiver != null) return  // already registered

        btReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val device: BluetoothDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                } ?: return

                val name = safeGetDeviceName(device)

                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        isWatchConnected    = true
                        connectedDeviceName = name
                        Log.i(TAG, "Device connected via Bluetooth: $name")
                        onConnect?.invoke(name)
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        isWatchConnected    = false
                        connectedDeviceName = name
                        Log.w(TAG, "Device disconnected via Bluetooth: $name")
                        onDisconnect?.invoke(name)
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }

        // API 33+ requires an explicit export flag for dynamically registered receivers.
        // RECEIVER_NOT_EXPORTED is correct here — only the system Bluetooth stack sends these.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ctx.registerReceiver(btReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ctx.registerReceiver(btReceiver, filter)
        }

        Log.i(TAG, "Bluetooth monitor started — listening for ACL connect/disconnect events")
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

    /**
     * Re-check BLE GATT connected devices and fire callbacks if state changed.
     * Called on "Refresh" tap in HomeScreen.
     *
     * Note: BluetoothManager.getConnectedDevices(GATT) only covers BLE (not Classic BT).
     * Classic BT connection state changes are caught in real-time by the ACL receiver above.
     */
    fun refreshConnectedDevices() {
        Log.d(TAG, "Refresh requested — re-checking GATT connected devices")
        checkCurrentConnectionState()
    }

    /**
     * Query BLE GATT for currently connected devices and update state.
     * Fires onConnect if a device is found and we weren't already connected.
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
            Log.d(TAG, "Bluetooth is disabled — treating watch as disconnected")
            if (isWatchConnected) {
                isWatchConnected = false
                onDisconnect?.invoke(connectedDeviceName)
            }
            return
        }

        // getConnectedDevices(GATT) returns BLE devices currently in a connected state.
        // Huawei watches use BLE for their companion link, so this covers the primary case.
        val connectedDevices = try {
            btManager.getConnectedDevices(BluetoothProfile.GATT)
        } catch (e: SecurityException) {
            Log.w(TAG, "getConnectedDevices: SecurityException — ${e.message}")
            emptyList()
        }

        // Prefer a device whose name suggests it's a watch; fall back to any BLE device.
        val watchDevice = connectedDevices.firstOrNull { isLikelyWatch(it) }
            ?: connectedDevices.firstOrNull()

        if (watchDevice != null) {
            val name = safeGetDeviceName(watchDevice)
            if (!isWatchConnected) {
                isWatchConnected    = true
                connectedDeviceName = name
                Log.i(TAG, "Initial/refresh check: device found — $name")
                onConnect?.invoke(name)
            } else {
                Log.d(TAG, "Initial/refresh check: already connected to $connectedDeviceName")
            }
        } else {
            Log.d(TAG, "Initial/refresh check: no BLE device found in GATT connections")
            // Don't fire onDisconnect here — absence from GATT list doesn't mean
            // Classic BT is disconnected; that's handled by the ACL receiver.
        }
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

    // --- Debug helpers (used by HomeViewModel.simulateDisconnect / HomeScreen button) ---

    /** Simulate a watch connect event — useful for UI testing without a real watch. */
    fun simulateConnect(deviceName: String = "Huawei Watch Ultimate") {
        isWatchConnected    = true
        connectedDeviceName = deviceName
        onConnect?.invoke(deviceName)
    }

    /** Simulate a watch disconnect event — mirrors the "Simulate disconnect" debug button. */
    fun simulateDisconnect(deviceName: String = "Huawei Watch Ultimate") {
        isWatchConnected    = false
        connectedDeviceName = ""
        onDisconnect?.invoke(deviceName)
    }
}
