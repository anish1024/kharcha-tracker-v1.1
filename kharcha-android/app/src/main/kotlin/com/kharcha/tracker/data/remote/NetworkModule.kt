package com.kharcha.tracker.data.remote

import android.content.Context
import android.util.Log
import com.kharcha.tracker.BuildConfig
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

        // Full request/response bodies in debug builds; in release only the
        // basic request/response lines. The Authorization header is
        // redacted in both, so the session token never reaches logcat.
        val logging = HttpLoggingInterceptor { message ->
            Log.d("KharchaHttp", message.replace(Regex("Bearer \\S+"), "Bearer <redacted>"))
        }.apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.BASIC
            }
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
