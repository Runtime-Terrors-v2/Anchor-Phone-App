package com.Anchor.watchguardian.viewmodel

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Anchor.watchguardian.data.UserSession
import com.Anchor.watchguardian.data.model.AlertContact
import com.Anchor.watchguardian.data.model.AlertEvent
import com.Anchor.watchguardian.services.NotificationHelper
import com.Anchor.watchguardian.services.SmsService
import com.Anchor.watchguardian.services.WatchMonitorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private const val TAG              = "HomeViewModel"
private const val PREFS_ALERTS     = "anchor_alerts"
private const val PREFS_CONTACTS   = "anchor_contacts"
private const val KEY_HISTORY      = "history"
private const val KEY_CONTACTS     = "contacts"

/**
 * ViewModel for HomeScreen.
 *
 * Owns watch connection state and alert history.
 * Mirrors the logic that was spread across EntryAbility.ets (WatchMonitorService init,
 * SMS dispatch on disconnect) and HomePage.ets (display state, alert count).
 *
 * State is exposed as StateFlow so Compose can collect it reactively.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context = application.applicationContext

    // --- Exposed state ---
    private val _watchConnected  = MutableStateFlow(false)
    private val _watchName       = MutableStateFlow("")
    private val _alertHistory    = MutableStateFlow<List<AlertEvent>>(emptyList())

    val watchConnected:  StateFlow<Boolean>          = _watchConnected
    val watchName:       StateFlow<String>           = _watchName
    val alertHistory:    StateFlow<List<AlertEvent>> = _alertHistory

    init {
        loadAlertHistory()
        setupWatchMonitor()
    }

    /**
     * Wire up WatchMonitorService callbacks and start the 10-second poll.
     * Mirrors: the monitor.setCallbacks(...) block in EntryAbility.ets.
     */
    private fun setupWatchMonitor() {
        WatchMonitorService.getInstance().setCallbacks(
            onConnect = { deviceName ->
                _watchConnected.value = true
                _watchName.value      = deviceName
                Log.i(TAG, "Watch connected: $deviceName")
            },
            onDisconnect = { deviceName ->
                _watchConnected.value = false
                _watchName.value      = deviceName
                Log.w(TAG, "Watch disconnected: $deviceName")

                // Record in history
                val event = AlertEvent(
                    ownerName = UserSession.getOpenID(context).ifBlank { "Guardian" },
                    watchName = deviceName,
                    timestamp = System.currentTimeMillis()
                )
                _alertHistory.value = listOf(event) + _alertHistory.value
                saveAlertHistory()

                // Load contacts once — used for both SMS dispatch and notification body
                val contacts = loadContactsAsAlertContacts()
                val smsCount = contacts.count { it.phoneNumber.isNotBlank() }

                // Show a local notification on the caregiver's phone so they're
                // alerted even when the app is in the background
                NotificationHelper.showWatchDisconnected(
                    context      = context,
                    watchName    = deviceName,
                    contactCount = smsCount
                )

                // Fire SMS to all priority contacts that have a phone number
                viewModelScope.launch {
                    SmsService.sendDisconnectAlert(
                        context   = context,
                        watchName = deviceName,
                        ownerName = event.ownerName,
                        contacts  = contacts
                    )
                }
            }
        )
        WatchMonitorService.getInstance().startMonitoring()
    }

    /** Called when the user taps "Refresh status" on HomeScreen. */
    fun refreshWatchStatus() {
        WatchMonitorService.getInstance().refreshConnectedDevices()
        // Give the async getBondedDevices Task ~500ms to complete, then read state
        Handler(Looper.getMainLooper()).postDelayed({
            val monitor           = WatchMonitorService.getInstance()
            _watchConnected.value = monitor.isWatchConnected
            _watchName.value      = monitor.connectedDeviceName
        }, 500L)
    }

    /**
     * Debug-only: simulate a disconnect event without needing a real watch.
     * Mirrors the "Simulate disconnect" button in HomePage.ets.
     */
    fun simulateDisconnect() {
        val simName   = "Watch Ultimate"
        val ownerName = UserSession.getOpenID(context).ifBlank { "Guardian" }
        _watchConnected.value = false
        _watchName.value      = simName

        val event = AlertEvent(
            ownerName = ownerName,
            watchName = simName,
            timestamp = System.currentTimeMillis()
        )
        _alertHistory.value = listOf(event) + _alertHistory.value
        saveAlertHistory()

        // Mirror real disconnect — show notification + fire SMS to priority contacts
        val contacts = loadContactsAsAlertContacts()
        NotificationHelper.showWatchDisconnected(
            context      = context,
            watchName    = simName,
            contactCount = contacts.count { it.phoneNumber.isNotBlank() }
        )
        viewModelScope.launch {
            SmsService.sendDisconnectAlert(
                context   = context,
                watchName = simName,
                ownerName = ownerName,
                contacts  = contacts
            )
        }
    }

    fun clearAlertHistory() {
        _alertHistory.value = emptyList()
        context.getSharedPreferences(PREFS_ALERTS, Context.MODE_PRIVATE)
            .edit().putString(KEY_HISTORY, "[]").apply()
    }

    /** Count of contacts — shown on HomeScreen stats row. */
    fun getContactCount(): Int {
        val json = context.getSharedPreferences(PREFS_CONTACTS, Context.MODE_PRIVATE)
            .getString(KEY_CONTACTS, "[]") ?: "[]"
        return try { JSONArray(json).length() } catch (e: Exception) { 0 }
    }

    /** Alerts that occurred in the past 7 days — shown on HomeScreen stats row. */
    fun getAlertCountThisWeek(): Int {
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        return _alertHistory.value.count { it.timestamp > weekAgo }
    }

    // --- Persistence helpers ---

    private fun loadAlertHistory() {
        val json = context.getSharedPreferences(PREFS_ALERTS, Context.MODE_PRIVATE)
            .getString(KEY_HISTORY, "[]") ?: "[]"
        _alertHistory.value = parseAlertHistory(json)
    }

    private fun saveAlertHistory() {
        val arr = JSONArray()
        _alertHistory.value.forEach { event ->
            arr.put(JSONObject().apply {
                put("ownerName", event.ownerName)
                put("watchName", event.watchName)
                put("timestamp", event.timestamp)
            })
        }
        context.getSharedPreferences(PREFS_ALERTS, Context.MODE_PRIVATE)
            .edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    private fun parseAlertHistory(json: String): List<AlertEvent> =
        try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                arr.getJSONObject(i).let { obj ->
                    AlertEvent(
                        ownerName = obj.optString("ownerName", ""),
                        watchName = obj.optString("watchName", ""),
                        timestamp = obj.optLong("timestamp", 0L)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseAlertHistory: ${e.message}")
            emptyList()
        }

    private fun loadContactsAsAlertContacts(): List<AlertContact> {
        val json = context.getSharedPreferences(PREFS_CONTACTS, Context.MODE_PRIVATE)
            .getString(KEY_CONTACTS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                arr.getJSONObject(i).let { obj ->
                    AlertContact(
                        name        = obj.optString("name", ""),
                        phoneNumber = obj.optString("phoneNumber", ""),
                        pushToken   = obj.optString("pushToken", ""),
                        openID      = obj.optString("openID", "")
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
