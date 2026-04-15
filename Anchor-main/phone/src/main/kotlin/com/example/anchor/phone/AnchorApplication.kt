package com.Anchor.watchguardian

import android.app.Application
import android.util.Log

/**
 * Application subclass.
 *
 * AGConnect initialization is skipped until agconnect-services.json is updated
 * with the new applicationId (com.Anchor.watchguardian) in the Huawei AppGallery Console.
 * HMS Push Kit and Account Kit still work at runtime via the manifest registration.
 */
class AnchorApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i("AnchorApplication", "App started")
    }
}
