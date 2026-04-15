package com.Anchor.watchguardian.data.model

/**
 * A contact who receives SMS and/or push alerts when the watch disconnects.
 * Mirrors the AlertContact interface from SmsService.ets (HarmonyOS).
 */
data class AlertContact(
    val name:        String,
    val phoneNumber: String, // for direct Android SmsManager dispatch
    val pushToken:   String, // for HMS push-to-device
    val openID:      String  // Huawei ID — used to look up push token via AGConnect
)
