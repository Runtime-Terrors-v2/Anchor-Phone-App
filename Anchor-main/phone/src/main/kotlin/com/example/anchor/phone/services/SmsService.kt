package com.Anchor.watchguardian.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.Anchor.watchguardian.data.model.AlertContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "SmsService"

/**
 * Sends SMS alerts directly via Android SmsManager.
 *
 * Safe on all API levels:
 *  - API < 31  : SmsManager.getDefault()
 *  - API 31+   : context.getSystemService(SmsManager::class.java)
 *                (getDefault() silently fails on Android 12+ dual-SIM devices)
 *
 * Uses divideMessage() + sendMultipartTextMessage() so messages longer
 * than 160 chars are never silently truncated.
 *
 * Shows a Toast on screen for each send attempt so the outcome is
 * visible without needing Logcat.
 */
object SmsService {

    suspend fun sendDisconnectAlert(
        context:   Context,
        watchName: String,
        ownerName: String,
        contacts:  List<AlertContact>
    ) = withContext(Dispatchers.IO) {

        // 1. Guard: permission check first — gives a clear log + toast if missing
        if (!hasSmsPermission(context)) {
            Log.e(TAG, "SEND_SMS permission not granted — cannot send alerts")
            showToast(context, "SMS permission not granted — alerts not sent")
            return@withContext
        }

        // 2. Filter contacts that actually have a phone number
        val smsContacts = contacts.filter { it.phoneNumber.isNotBlank() }
        if (smsContacts.isEmpty()) {
            Log.w(TAG, "No contacts with phone numbers — skipping SMS")
            showToast(context, "No contacts with phone numbers saved")
            return@withContext
        }

        // 3. Resolve SmsManager correctly for the running API level
        val smsManager = resolveSmsManager(context)
        if (smsManager == null) {
            Log.e(TAG, "SmsManager unavailable — device may have no SIM card")
            showToast(context, "No SIM card detected — SMS not sent")
            return@withContext
        }

        // 4. Build message body
        val body = buildSmsBody(ownerName, watchName)
        Log.i(TAG, "Sending SMS to ${smsContacts.size} contact(s): \"$body\"")

        // 5. Send to each contact
        var sentCount = 0
        smsContacts.forEach { contact ->
            val success = trySend(smsManager, contact, body)
            if (success) sentCount++
        }

        // 6. Toast summary visible on screen
        val summary = when {
            sentCount == smsContacts.size -> "Alert sent to $sentCount contact(s) ✓"
            sentCount == 0               -> "SMS failed — check Logcat for details"
            else                         -> "Sent $sentCount / ${smsContacts.size} alerts"
        }
        showToast(context, summary)
        Log.i(TAG, summary)
    }

    // ------------------------------------------------------------------

    private fun hasSmsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun resolveSmsManager(context: Context): SmsManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API 31+: getDefault() is deprecated and unreliable on multi-SIM devices
            context.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }

    private fun buildSmsBody(ownerName: String, watchName: String): String {
        val name   = ownerName.ifBlank { "Your family member" }
        val device = watchName.ifBlank { "their watch" }
        return "ANCHOR Alert: $name's $device has disconnected. Please check in immediately."
    }

    /** Returns true if the SMS was dispatched without exception. */
    private fun trySend(
        smsManager: SmsManager,
        contact:    AlertContact,
        body:       String
    ): Boolean {
        return try {
            val parts = smsManager.divideMessage(body)
            if (parts.size == 1) {
                smsManager.sendTextMessage(
                    contact.phoneNumber,
                    null,  // use default SMSC
                    body,
                    null,  // sentIntent — not needed; result visible via Toast
                    null   // deliveryIntent
                )
            } else {
                smsManager.sendMultipartTextMessage(
                    contact.phoneNumber,
                    null,
                    parts,
                    null,
                    null
                )
            }
            Log.i(TAG, "SMS dispatched → ${contact.name} (${contact.phoneNumber}), ${parts.size} part(s)")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "SMS SecurityException → ${contact.name}: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "SMS failed → ${contact.name} (${contact.phoneNumber}): ${e.message}")
            false
        }
    }

    /** Posts a Toast to the main thread — safe to call from any coroutine dispatcher. */
    private fun showToast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}
