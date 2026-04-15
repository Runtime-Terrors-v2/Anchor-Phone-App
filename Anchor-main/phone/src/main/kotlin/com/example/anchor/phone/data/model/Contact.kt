package com.Anchor.watchguardian.data.model

/**
 * A priority emergency contact stored by the caregiver.
 * Mirrors the Contact interface from ContactsPage.ets (HarmonyOS).
 *
 * Contacts are persisted via SharedPreferences (JSON array) and synced to the
 * HarmonyOS watch via WearEngine P2p so the watch can display them in its CommunityCard.
 */
data class Contact(
    val id:          String, // timestamp-based unique ID
    val name:        String,
    val phoneNumber: String, // used for SMS alerts
    val pushToken:   String, // used for HMS push alerts (can be empty)
    val openID:      String  // Huawei ID openID (can be empty)
)
