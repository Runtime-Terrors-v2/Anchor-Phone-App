// Phone module (EMUI / Android) build configuration.
// This replaces the old HarmonyOS build-profile.json5 + hvigorfile.ts.

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    // AGConnect plugin removed — it reads agconnect-services.json at build time and fails
    // if the package_name inside doesn't match applicationId. HMS Push and Account Kit
    // work fine at runtime without the build-time plugin.
}

android {
    namespace   = "com.Anchor.watchguardian"
    compileSdk  = 34

    defaultConfig {
        applicationId  = "com.Anchor.watchguardian"
        minSdk         = 26          // Android 8.0 — baseline for HMS Core 6.x
        targetSdk      = 34
        versionCode    = 1
        versionName    = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    // Kotlin source directory
    sourceSets["main"].java.srcDirs("src/main/kotlin")
}

dependencies {
    // --- Jetpack Compose BOM: pins all Compose library versions consistently ---
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Activity + Navigation + ViewModel for Compose
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.navigation:navigation-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    // --- HMS Core SDKs (all require the Huawei maven repo in settings.gradle.kts) ---

    // HMS Push Kit — replaces @kit.PushKit / PushExtensionAbility from HarmonyOS
    implementation("com.huawei.hms:push:6.11.0.300")

    // HMS Account Kit — replaces @kit.AccountKit / LoginWithHuaweiIDButton
    implementation("com.huawei.hms:hwid:6.11.0.300")

    // WearEngine Android SDK — NOT available on Maven; must be downloaded as AAR from
    // https://developer.huawei.com/consumer/en/doc/development/HMSCore-Guides/dev-process-0000001051068977
    // and added as a local file dependency. Stubbed out for now so the app builds.
    // To enable: download the AAR, place it in phone/libs/, and replace this line with:
    //   implementation(files("libs/wearengine-x.x.x.x.aar"))

    // AGConnect removed — re-add once agconnect-services.json is updated with the
    // new applicationId (com.Anchor.watchguardian) in the Huawei AppGallery Console.

    // Coroutines for async SMS dispatch and background polling
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
