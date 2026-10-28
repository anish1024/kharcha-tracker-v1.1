package com.kharcha.tracker.data.remote

import com.kharcha.tracker.BuildConfig

object ApiConstants {

    /**
     * Base URL of the kharcha gateway.
     *
     * Defaults to the Android emulator host bridge (see `build.gradle.kts`);
     * for a device on the LAN, override at build time:
     * `./gradlew assembleDebug -PapiBaseUrl=http://<host-ip>:8000/`
     */
    val BASE_URL: String = BuildConfig.BASE_URL

    /** API version prefix for every endpoint. Must stay a compile-time constant for Retrofit annotations. */
    const val API_VERSION = "api/v1"
}
