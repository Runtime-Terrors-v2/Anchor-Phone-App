package com.Anchor.watchguardian.data.model

/**
 * A single watch-disconnect alert event stored in the alert history.
 * Mirrors the AlertRecord interface used in HomePage.ets and AlertHistoryPage.ets.
 */
data class AlertEvent(
    val ownerName: String,  // caregiver / user name shown in history
    val watchName: String,  // name of the disconnected watch device
    val timestamp: Long     // epoch millis — used for "X mins ago" formatting
)
