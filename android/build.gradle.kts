plugins {
    alias(libs.plugins.androidApplication) apply(false)
    alias(libs.plugins.androidLibrary) apply(false)
    alias(libs.plugins.jetbrainsKotlinAndroid) apply(false)
    alias(libs.plugins.kotlinCompose) apply(false)
    alias(libs.plugins.ksp) apply(false)
    alias(libs.plugins.comGoogleAndroidMetrics) apply(false)
    alias(libs.plugins.hilt) apply(false)
    alias(libs.plugins.koinAndroid) apply(false)
}

buildscript {
    repositories {
        google()
        mavenCentral()
        maven { url "https://jitpack.io" }
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.2.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.20")
        classpath("org.jetbrains.kotlin:kotlin-serialization:2.0.20")
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.7.7")
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.52")
        classpath("io.insert-koin:kotlinx-plugin:3.5.3")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url "https://jitpack.io" }
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory.dir("outputs"))
}

group = "com.kharcha.tracker"
version = "1.0.0"
