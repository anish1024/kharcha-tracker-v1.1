plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.koinAndroid)
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.kharcha.tracker.composeapp"
    compileSdk = 34

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        // Compose target
        targetSdk = 34
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,CDC1.0}"
        }
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)

    // Compose BOM (app module provides this)
    implementation(project(":app"))

    // Compose
    implementation(compose.bom)
    implementation(compose.ui)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.navigation)
    implementation(compose.materialIconsExtended)
    implementation(compose.runtime)
    implementation("androidx.compose.material:material-icons-extended:1.5.4")
    implementation("androidx.compose.material:material:1.5.4")

    // Navigation (Compose Navigation)
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Dependency Injection
    implementation(libs.hilt.core)
    implementation(libs.koin.core)
    ksp(libs.hilt.compiler)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.serialization.json)
    implementation(libs.serialization.core)
    implementation(libs.serialization.protobuf)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Database (Room)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Logging
    implementation("ch.qos.logback:logback-core:1.5.0")
    implementation("ch.qos.logback:logback-classic:1.5.0")

    // Image Loading
    implementation(libs.coil)
    implementation(libs.coil.compose)

    // Testing
    testImplementation(junit.junit)
    testImplementation(libs.truth)

    // Local Testing Utils
    implementation("io.mockk:mockk:1.13.9")
    implementation("app.cash.turbine:turbine:1.2.0")
}
