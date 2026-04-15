package com.Anchor.watchguardian.services

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import com.Anchor.watchguardian.data.model.AlertContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "SmsService"

/**
 * Sends SMS alerts directly via Android SmsManager.
 *
 * Replaces SmsService.ets (HarmonyOS) which called an AGConnect Cloud Function
 * (/functions/3id7okrp/guardianalert-$latest) to dispatch SMS server-side.
 *
 * On Android we can send SMS directly from the device — simpler, no cloud round-trip,
 * and works even when the internet is down. Requires SEND_SMS permission granted at runtime.
 *
 * Contacts with only a pushToken (no phoneNumber) still get HMS push alerts via
 * AnchorMessagingService; this service only handles the SMS path.
 */
object SmsService {

    /**
     * Send a disconnect alert to all contacts who have a phone number.
     * Called by HomeViewModel when WatchMonitorService fires the disconnect callback.
     *
     * Mirrors: SmsService.getInstance().sendDisconnectAlert(watchName, ownerName, contacts)
     */
    suspend fun sendDisconnectAlert(
        context:   Context,
        watchName: String,
        ownerName: String,
        contacts:  List<AlertContact>
    ) = withContext(Dispatchers.IO) {
        val smsContacts = contacts.filter { it.phoneNumber.isNotBlank() }
        if (smsContacts.isEmpty()) {
            Log.w(TAG, "No SMS-capable contacts — skipping SMS dispatch")
            return@withContext
        }

        val body = buildString {
            append("ANCHOR Alert: ")
            append(ownerName.ifBlank { "Your family member" })
            append("'s $watchName has disconnected. ")
            append("Please check in immediately.")
        }

        @Suppress("DEPRECATION")
        val smsManager: SmsManager = SmsManager.getDefault()

        smsContacts.forEach { contact ->
            try {
                // sendTextMessage splits automatically if body > 160 chars in some builds,
                // but our message is short enough to fit in a single SMS.
                smsManager.sendTextMessage(
                    contact.phoneNumber, // destinationAddress
                    null,                // scAddress (null = default)
                    body,
                    null,                // sentIntent
                    null                 // deliveryIntent
                )
                Log.i(TAG, "SMS sent to ${contact.name} (${contact.phoneNumber})")
            } catch (e: Exception) {
                Log.e(TAG, "SMS failed for ${contact.name}: ${e.message}")
            }
        }
    }
}
