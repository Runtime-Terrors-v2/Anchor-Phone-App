package com.Anchor.watchguardian.services

import android.content.Context
import android.util.Log
import java.util.Timer
import java.util.TimerTask

private const val TAG = "WatchMonitorService"

typealias ConnectCallback    = (deviceName: String) -> Unit
typealias DisconnectCallback = (deviceName: String) -> Unit

/**
 * Monitors Bluetooth connectivity to the HarmonyOS watch.
 *
 * NOTE: The WearEngine Android SDK is not available on Maven — it must be downloaded
 * as an AAR from the Huawei Developer Console. Until it is added, this class stubs
 * all WearEngine calls so the rest of the app (UI, SMS, push) builds and runs normally.
 *
 * To enable real watch communication:
 *   1. Download the WearEngine AAR from:
 *      https://developer.huawei.com/consumer/en/doc/development/HMSCore-Guides/dev-process-0000001051068977
 *   2. Place it in phone/libs/
 *   3. Add `implementation(files("libs/wearengine-x.x.x.x.aar"))` to build.gradle.kts
 *   4. Replace the stub methods below with the real WearEngine API calls
 *      (see the original full implementation in git history)
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

    private var pollTimer: Timer? = null
    private var lastSyncPayload: String = ""

    var isWatchConnected:    Boolean = false
        private set
    var connectedDeviceName: String  = ""
        private set

    private var onConnect:    ConnectCallback?    = null
    private var onDisconnect: DisconnectCallback? = null

    /** No-op until the WearEngine AAR is added. */
    fun init(context: Context) {
        Log.w(TAG, "WearEngine AAR not present — watch communication stubbed out")
    }

    fun setCallbacks(onConnect: ConnectCallback, onDisconnect: DisconnectCallback) {
        this.onConnect    = onConnect
        this.onDisconnect = onDisconnect
    }

    /**
     * Starts the polling loop.
     * With no WearEngine, the watch is always reported as disconnected.
     * The UI will show "Watch disconnected" — correct behaviour for a demo without a watch.
     */
    fun startMonitoring() {
        stopMonitoring()
        pollTimer = Timer("AnchorWatchPoll", true).apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    // Stub: no state change — watch stays disconnected until real SDK is wired up
                }
            }, 0L, 10_000L)
        }
        Log.i(TAG, "Watch monitoring started (stub — no WearEngine AAR)")
    }

    fun stopMonitoring() {
        pollTimer?.cancel()
        pollTimer = null
    }

    /** No-op stub — refreshes nothing without the WearEngine AAR. */
    fun refreshConnectedDevices() {
        Log.w(TAG, "refreshConnectedDevices: WearEngine AAR not present")
    }

    /**
     * Stub: logs the payload but cannot send it to the watch.
     * When the WearEngine AAR is added, replace this with a real P2pClient.send() call.
     */
    fun syncContactsToWatch(contactsJson: String) {
        lastSyncPayload = contactsJson
        Log.w(TAG, "syncContactsToWatch: WearEngine AAR not present — payload queued locally")
    }

    // --- Debug helpers (used by HomeViewModel.simulateDisconnect) ---

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
