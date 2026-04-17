package com.Anchor.watchguardian.services

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import com.Anchor.watchguardian.data.model.AlertContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG          = "SmsService"
private const val ACTION_SENT  = "com.Anchor.watchguardian.SMS_SENT"

/**
 * Sends SMS alerts directly via Android SmsManager.
 *
 * Key fixes vs original:
 *  1. Uses context.getSystemService(SmsManager) on API 31+ — getDefault() silently
 *     fails on Android 12+ especially on dual-SIM devices.
 *  2. Passes a sentIntent PendingIntent so we can log whether each SMS was actually
 *     accepted by the radio layer (RESULT_OK) or rejected (error code).
 *  3. Uses divideMessage() + sendMultipartTextMessage() so long messages are never
 *     silently truncated at 160 characters.
 */
object SmsService {

    /**
     * Send a disconnect alert SMS to every contact that has a phone number.
     * Called by HomeViewModel when WatchMonitorService fires the disconnect callback.
     */
    suspend fun sendDisconnectAlert(
        context:   Context,
        watchName: String,
        ownerName: String,
        contacts:  List<AlertContact>
    ) = withContext(Dispatchers.IO) {

        val smsContacts = contacts.filter { it.phoneNumber.isNotBlank() }
        if (smsContacts.isEmpty()) {
            Log.w(TAG, "No SMS-capable contacts — skipping")
            return@withContext
        }

        val body = buildSmsBody(ownerName, watchName)
        Log.i(TAG, "Sending SMS to ${smsContacts.size} contact(s): \"$body\"")

        // API 31+ requires context.getSystemService(); getDefault() silently fails there.
        val smsManager: SmsManager = resolveSmsManager(context)

        smsContacts.forEach { contact ->
            trySend(context, smsManager, contact, body)
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun buildSmsBody(ownerName: String, watchName: String): String {
        val name = ownerName.ifBlank { "Your family member" }
        val device = watchName.ifBlank { "their watch" }
        return "ANCHOR Alert: $name's $device has disconnected. Please check in immediately."
    }

    /** Returns the correct SmsManager for the running API level. */
    @Suppress("DEPRECATION")
    private fun resolveSmsManager(context: Context): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API 31+ — getSystemService is the non-deprecated path
            context.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }

    private fun trySend(
        context:    Context,
        smsManager: SmsManager,
        contact:    AlertContact,
        body:       String
    ) {
        try {
            // sentIntent fires a broadcast when the SMS is accepted (or rejected) by the radio.
            // We register a one-shot receiver so we can log the actual outcome.
            val action     = "${ACTION_SENT}_${contact.phoneNumber.replace("+", "")}"
            val sentIntent = PendingIntent.getBroadcast(
                context,
                contact.phoneNumber.hashCode(),
                Intent(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Register a one-shot receiver to log the send result
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    when (resultCode) {
                        Activity.RESULT_OK ->
                            Log.i(TAG, "SMS sent OK → ${contact.name} (${contact.phoneNumber})")
                        SmsManager.RESULT_ERROR_GENERIC_FAILURE ->
                            Log.e(TAG, "SMS FAILED (generic) → ${contact.name}")
                        SmsManager.RESULT_ERROR_NO_SERVICE ->
                            Log.e(TAG, "SMS FAILED (no service) → ${contact.name}")
                        SmsManager.RESULT_ERROR_RADIO_OFF ->
                            Log.e(TAG, "SMS FAILED (radio off) → ${contact.name}")
                        else ->
                            Log.e(TAG, "SMS FAILED (code $resultCode) → ${contact.name}")
                    }
                    try { ctx?.unregisterReceiver(this) } catch (_: Exception) {}
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, IntentFilter(action),
                    Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, IntentFilter(action))
            }

            // Split message if > 160 chars to avoid silent truncation
            val parts = smsManager.divideMessage(body)
            if (parts.size == 1) {
                smsManager.sendTextMessage(
                    contact.phoneNumber,
                    null,
                    body,
                    sentIntent,
                    null           // deliveryIntent — not needed for now
                )
            } else {
                val sentIntents = ArrayList<PendingIntent>(parts.size).apply {
                    add(sentIntent)                    // track only the first part
                    repeat(parts.size - 1) { add(null) }
                }
                smsManager.sendMultipartTextMessage(
                    contact.phoneNumber,
                    null,
                    parts,
                    sentIntents,
                    null
                )
            }

            Log.i(TAG, "SMS dispatched to ${contact.name} (${contact.phoneNumber})" +
                       " — ${parts.size} part(s)")

        } catch (e: SecurityException) {
            Log.e(TAG, "SMS permission denied for ${contact.name}: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "SMS exception for ${contact.name}: ${e.message}")
        }
    }
}
