// Android settings for the phone companion module.
// The watch module (entry/) is a separate HarmonyOS project built in DevEco Studio —
// it is not part of this Gradle build.

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // Huawei HMS / AGConnect plugins
        maven { url = uri("https://developer.huawei.com/repo/") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Huawei HMS libraries (WearEngine, Push Kit, Account Kit, AGConnect)
        maven { url = uri("https://developer.huawei.com/repo/") }
    }
}

rootProject.name = "Anchor"
include(":phone")
