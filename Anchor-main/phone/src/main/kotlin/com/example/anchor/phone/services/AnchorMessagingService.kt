package com.Anchor.watchguardian.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.Anchor.watchguardian.MainActivity
import com.huawei.hms.push.HmsMessageService
import com.huawei.hms.push.RemoteMessage
import org.json.JSONArray
import org.json.JSONObject

private const val TAG        = "AnchorMessagingService"
private const val CHANNEL_ID = "anchor_alerts"

/**
 * HMS Push Kit message receiver.
 *
 * Mirrors PushAbility.ets (HarmonyOS PushExtensionAbility) — receives push messages
 * sent from the AGConnect Cloud Function (or any upstream service) when the watch
 * fires an ALERT or FALL event, then:
 *   1. Shows a local notification to the caregiver
 *   2. Persists the event to SharedPreferences so AlertHistoryScreen can display it
 *
 * Registered in AndroidManifest.xml with:
 *   <action android:name="com.huawei.push.action.MESSAGING_EVENT" />
 *
 * Also handles onNewToken — saves the HMS push token to SharedPreferences so
 * MainActivity can read it and upload to AGConnect cloud (mirrors PushService.ets).
 */
class AnchorMessagingService : HmsMessageService() {

    /**
     * Called when HMS Push Kit issues a new token for this device.
     * Mirrors: PushService.getPushToken() → AppStorage.setOrCreate('pushToken', token).
     */
    override fun onNewToken(token: String) {
        Log.i(TAG, "New HMS push token received")
        getSharedPreferences("anchor_push", Context.MODE_PRIVATE)
            .edit()
            .putString("pushToken", token)
            .apply()
    }

    /**
     * Called when a push message arrives from the AGConnect cloud backend.
     * Mirrors: PushAbility.onReceiveMessage(want) in HarmonyOS.
     *
     * Expected data fields in the push payload:
     *   type      — "disconnect" | "alert" | "fall"
     *   ownerName — patient / caregiver display name
     *   watchName — device name for the notification body
     */
    override fun onMessageReceived(message: RemoteMessage) {
        Log.i(TAG, "Push message received")
        try {
            val data      = message.dataOfMap
            val alertType = data["type"]      ?: "disconnect"
            val ownerName = data["ownerName"] ?: "Someone"
            val watchName = data["watchName"] ?: "their watch"

            when (alertType) {
                "disconnect" -> {
                    showNotification(
                        title = "Watch Disconnected",
                        body  = "$ownerName's $watchName has gone offline"
                    )
                    saveAlertToHistory(ownerName, watchName)
                }
                "alert" -> {
                    showNotification(
                        title = "ANCHOR Alert",
                        body  = "$ownerName may have left home. Last known location attached."
                    )
                    saveAlertToHistory(ownerName, watchName)
                }
                "fall" -> {
                    showNotification(
                        title = "ANCHOR Fall Alert",
                        body  = "$ownerName may have fallen. Check immediately."
                    )
                    saveAlertToHistory(ownerName, watchName)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "onMessageReceived error: ${e.message}")
        }
    }

    /** Show a high-priority local notification; tapping opens AlertHistoryScreen. */
    private fun showNotification(title: String, body: String) {
        ensureNotificationChannel()

        // Tapping the notification opens MainActivity and navigates to alert_history
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("navigate_to", "alert_history")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(System.currentTimeMillis().toInt(), notification)
    }

    /**
     * Persist the alert to SharedPreferences (prepend, newest first).
     * HomeViewModel and AlertHistoryScreen read from the same "anchor_alerts" prefs.
     */
    private fun saveAlertToHistory(ownerName: String, watchName: String) {
        val prefs    = getSharedPreferences("anchor_alerts", Context.MODE_PRIVATE)
        val existing = prefs.getString("history", "[]") ?: "[]"
        val arr      = JSONArray(existing)
        val entry    = JSONObject().apply {
            put("ownerName", ownerName)
            put("watchName", watchName)
            put("timestamp", System.currentTimeMillis())
        }
        // Build a new array with this entry at the front
        val updated = JSONArray().put(entry)
        for (i in 0 until arr.length()) updated.put(arr.get(i))
        prefs.edit().putString("history", updated.toString()).apply()
    }

    /** Create the notification channel once (no-op if it already exists). */
    private fun ensureNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ANCHOR Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Watch disconnect and safety alerts from ANCHOR"
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }
}
