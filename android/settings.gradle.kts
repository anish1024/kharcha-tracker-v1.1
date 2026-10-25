pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "KharchaTracker"

// Compose app with Android support
include(":composeApp")
project(":composeApp").projectDir = file("composeApp/src")

// Android app
include(":app")

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url "https://jitpack.io" }
    }
}
