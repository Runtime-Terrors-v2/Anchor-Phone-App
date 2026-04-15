package com.Anchor.watchguardian.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.Anchor.watchguardian.data.UserSession
import com.Anchor.watchguardian.data.model.Contact
import com.Anchor.watchguardian.services.WatchMonitorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

private const val TAG          = "ContactsViewModel"
private const val PREFS_NAME   = "anchor_contacts"
private const val KEY_CONTACTS = "contacts"

/**
 * ViewModel for ContactsScreen.
 *
 * Manages the list of priority emergency contacts.
 * On every add/remove it:
 *   1. Saves to SharedPreferences (replaces AppStorage from ContactsPage.ets)
 *   2. Syncs the updated list to the HarmonyOS watch via WearEngine P2p
 *      (mirrors: WatchMonitorService.getInstance().syncContactsToWatch(payload))
 */
class ContactsViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context = application.applicationContext

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts

    init {
        loadContacts()
    }

    private fun loadContacts() {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CONTACTS, "[]") ?: "[]"
        _contacts.value = parseContacts(json)
    }

    /**
     * Add a new contact. Mirrors ContactsPage.addContact() in ArkTS.
     * ID is a timestamp string (same approach as the original).
     */
    fun addContact(name: String, phoneNumber: String, openID: String) {
        val contact = Contact(
            id          = System.currentTimeMillis().toString(),
            name        = name.trim(),
            phoneNumber = phoneNumber.trim(),
            pushToken   = "", // populated later if they sign in and share their push token
            openID      = openID.trim()
        )
        _contacts.value = _contacts.value + contact
        persistAndSync()
    }

    /** Remove contact by ID. Mirrors ContactsPage.removeContact(id). */
    fun removeContact(id: String) {
        _contacts.value = _contacts.value.filter { it.id != id }
        persistAndSync()
    }

    /**
     * Persist to SharedPreferences and sync to watch via WearEngine P2p.
     * Mirrors: saveContacts() → AppStorage.setOrCreate + WatchMonitorService.syncContactsToWatch()
     */
    private fun persistAndSync() {
        // 1. Save to SharedPreferences
        val arr = JSONArray()
        _contacts.value.forEach { c ->
            arr.put(JSONObject().apply {
                put("id",          c.id)
                put("name",        c.name)
                put("phoneNumber", c.phoneNumber)
                put("pushToken",   c.pushToken)
                put("openID",      c.openID)
            })
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_CONTACTS, arr.toString()).apply()

        // 2. Build the same payload format as the original ArkTS ContactsPage.saveContacts()
        //    so the watch app's P2p listener can deserialize it without changes.
        val payload = JSONObject().apply {
            put("contacts",  arr)
            put("ownerName", UserSession.getOpenID(context).ifBlank { "Guardian" })
            put("authToken", UserSession.getIDToken(context))
        }.toString()

        WatchMonitorService.getInstance().syncContactsToWatch(payload)
        Log.i(TAG, "Contacts persisted and synced: ${_contacts.value.size}")
    }

    private fun parseContacts(json: String): List<Contact> =
        try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                arr.getJSONObject(i).let { obj ->
                    Contact(
                        id          = obj.optString("id", System.currentTimeMillis().toString()),
                        name        = obj.optString("name", ""),
                        phoneNumber = obj.optString("phoneNumber", ""),
                        pushToken   = obj.optString("pushToken", ""),
                        openID      = obj.optString("openID", "")
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseContacts: ${e.message}")
            emptyList()
        }
}
