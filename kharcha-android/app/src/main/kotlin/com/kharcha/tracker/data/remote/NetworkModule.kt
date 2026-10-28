package com.kharcha.tracker.data.remote

import android.content.Context
import com.kharcha.tracker.data.local.TokenStore
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds the shared OkHttp / Retrofit stack.
 *
 * [init] must be called once (from [com.kharcha.tracker.MainActivity])
 * before any network call: the DataStore-backed [TokenStore] needs an
 * application context.
 */
object NetworkModule {

    private var tokenStore: TokenStore? = null
    private var apiService: ApiService? = null

    val api: ApiService
        get() = checkNotNull(apiService) {
            "NetworkModule not initialised. Call NetworkModule.init(context) first."
        }

    val tokens: TokenStore
        get() = checkNotNull(tokenStore) {
            "NetworkModule not initialised. Call NetworkModule.init(context) first."
        }

    @Synchronized
    fun init(context: Context) {
        if (apiService != null) return

        val store = TokenStore(context.applicationContext)

        val logging = HttpLoggingInterceptor().apply {
            // TODO(Stage 7): BODY logs request/response headers, including the
            //  Authorization token - lower the level or redact it for release.
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(store))
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(ApiConstants.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        tokenStore = store
        apiService = retrofit.create(ApiService::class.java)
    }
}
