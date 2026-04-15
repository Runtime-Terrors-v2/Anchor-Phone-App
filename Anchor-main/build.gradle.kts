buildscript {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://developer.huawei.com/repo/") }
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.5.0")
        classpath("com.huawei.agconnect:agcp:1.9.1.301")
    }
}

plugins {
    id("com.android.application")          version "8.5.0"   apply false
    id("org.jetbrains.kotlin.android")     version "2.0.0"   apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" apply false
}
