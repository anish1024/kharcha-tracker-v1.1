plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.koinAndroid)
}

android {
    namespace = "com.kharcha.tracker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kharcha.tracker"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Enable Jetpack Compose
        composeOptions {
            kotlinCompilerExtensionVersion = "1.5.1"
        }

        // Add dimensions if needed
        defaultConfig {
            resValue("string", "app_name", "Kharcha Tracker")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isProguardFiles = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Compose integration
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
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Compose
    implementation(compose.bom)
    implementation(compose.ui)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.navigation)
    implementation(compose.materialIconsExtended)
    implementation(compose.runtime)
    implementation("androidx.compose.material:material-icons-extended:1.5.4")
    implementation("androidx.lifecycle:lifecycle-process:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-kotlin:2.8.3")

    // Platform Integration
    implementation("com.google.accompanist:accompanist-swale:0.32.0")
    implementation("com.google.accompanist:accompanist-workman:0.28.0")
    implementation("com.google.accompanist:accompanist-permissions:0.33.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Dependency Injection
    implementation(libs.hilt.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    ksp(libs.hilt.compiler)
    ksp(libs.koin.compiler)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Serialization
    implementation(libs.serialization.json)

    // Database (Room)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Logging
    implementation("ch.qos.logback:logback-android:1.5.0")
    implementation("com.google.code.gson:gson:2.11.0")

    // Image Loading
    implementation(libs.coil)
    implementation(libs.coil.compose)

    // Preferences
    implementation(libs.androidx.datastore.preferences)

    // Testing
    testImplementation(junit.junit)
    testImplementation(libs.truth)

    // Android Test
    androidTestImplementation(junit.junit)
    androidTestImplementation(libs.truth)
    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation(compose.uiTestJunit4)
    androidTestImplementation(compose.uiTestManifest)

    // Accompanist UI Tests
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation(compose.uiTestJunit4)
    debugImplementation(compose.uiTestManifest)
    debugImplementation("com.google.accompanist:accompanist-ui-test-junit4:0.32.0")
}
