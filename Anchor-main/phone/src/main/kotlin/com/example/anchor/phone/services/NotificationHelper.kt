package com.Anchor.watchguardian.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.Anchor.watchguardian.MainActivity

private const val CHANNEL_ID   = "anchor_watch_status"
private const val NOTIFY_ID    = 2001

/**
 * Shows local notifications on the caregiver's own phone.
 *
 * Separate from AnchorMessagingService (which handles *incoming* cloud push).
 * This is used for events the phone detects directly — currently watch disconnect —
 * so the caregiver is alerted even when the app is in the background.
 *
 * Tapping the notification navigates to AlertHistoryScreen.
 */
object NotificationHelper {

    /**
     * Show a high-priority "watch disconnected" notification.
     *
     * @param watchName    BT device name of the watch that went offline.
     * @param contactCount number of SMS contacts that were notified — shown in body text
     *                     so the caregiver knows alerts are being dispatched.
     */
    fun showWatchDisconnected(context: Context, watchName: String, contactCount: Int) {
        ensureChannel(context)

        val body = if (contactCount > 0) {
            "$watchName went offline. $contactCount priority contact${if (contactCount == 1) "" else "s"} notified via SMS."
        } else {
            "$watchName went offline. Add priority contacts to send SMS alerts."
        }

        // Tapping opens MainActivity and navigates straight to the alert history tab.
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to", "alert_history")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFY_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("ANCHOR: Watch Disconnected")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // NotificationManagerCompat silently drops the call if POST_NOTIFICATIONS
        // permission is not granted (API 33+) — no SecurityException thrown.
        NotificationManagerCompat.from(context).notify(NOTIFY_ID, notification)
    }

    /**
     * Show a high-priority geofence ALERT notification on the caregiver's phone.
     * Uses ID 3001 — distinct from the watch-disconnect notification (2001).
     *
     * @param distanceM how far from the anchor the person is when ALERT fires.
     */
    fun showGeofenceAlert(context: Context, distanceM: Float) {
        ensureChannel(context)

        val body = "Movement detected — ${distanceM.toInt()}m from the safe zone. Check in immediately."

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to", "geofence")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            3001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("ANCHOR: Geofence Alert")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(3001, notification)
    }

    /** Create the notification channel once; no-op if it already exists. */
    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Watch Connection Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifies the caregiver when the ANCHOR watch goes offline"
        }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }
}
