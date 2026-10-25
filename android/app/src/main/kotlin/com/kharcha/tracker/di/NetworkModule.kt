package com.kharcha.tracker.di

import com.kharcha.tracker.api.*
import com.kharcha.tracker.utils.auth.AuthStorage
import com.kharcha.tracker.utils.NetworkManager
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.protobuf.ProtoConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import coil.ImageLoader
import coil.SingletonImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

class NetworkModule private constructor() {
    
    @get:Singleton
    val authApiService: AuthApiService by lazy {
        createRetrofitInstance(BASE_URL).create(AuthApiService::class.java)
    }
    
    @get:Singleton
    val expenseApiService: ExpenseApiService by lazy {
        createRetrofitInstance(BASE_URL).create(ExpenseApiService::class.java)
    }
    
    @get:Singleton
    val groupApiService: GroupApiService by lazy {
        createRetrofitInstance(BASE_URL).create(GroupApiService::class.java)
    }
    
    @get:Singleton
    val categoryApiService: CategoryApiService by lazy {
        createRetrofitInstance(BASE_URL).create(CategoryApiService::class.java)
    }
    
    @get:Singleton
    val milestoneApiService: MilestoneApiService by lazy {
        createRetrofitInstance(BASE_URL).create(MilestoneApiService::class.java)
    }
    
    @get:Singleton
    val reportApiService: ReportApiService by lazy {
        createRetrofitInstance(BASE_URL).create(ReportApiService::class.java)
    }
    
    @get:Singleton
    val notificationApiService: NotificationApiService by lazy {
        createRetrofitInstance(BASE_URL).create(NotificationApiService::class.java)
    }
    
    @get:Singleton
    val profileApiService: ProfileApiService by lazy {
        createRetrofitInstance(BASE_URL).create(ProfileApiService::class.java)
    }
    
    private fun createRetrofitInstance(baseUrl: String): Retrofit {
        val networkManager = NetworkManager.getINSTANCE
        val httpUrl = networkManager.networkUrl ?: baseUrl
        
        return Retrofit.Builder()
            .baseUrl(httpUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .addConverterFactory(ProtoConverterFactory.create())
            .client(createHttpURLConnectionClient())
            .connectTimeout(networkManager.connectionTimeout, TimeUnit.SECONDS)
            .readTimeout(networkManager.readTimeout, TimeUnit.SECONDS)
            .build()
    }
    
    private fun createHttpURLConnectionClient(): okhttp3.HttpUrlClient {
        return okhttp3.HttpUrlClient.Builder()
            .build()
    }
    
    @get:Singleton
    val connectionType: ConnectionType by lazy {
        val networkManager = NetworkManager.getINSTANCE
        if (networkManager.isConnected) {
            if (networkManager.connectionType == NetworkManager.ConnectionType.METRIC_AWARE) {
                ConnectionType.METRIC_AWARE
            } else {
                ConnectionType.DEFAULT
            }
        } else {
            ConnectionType.OFFLINE
        }
    }
}

/**
 * Retrofit client with authentication interceptor and connection handling
 */
class HttpConnectionFactory(private val networkModule: NetworkModule) {
    
    fun getRetrofitInstance(authStorage: AuthStorage): Retrofit {
        val baseUrl = networkModule.networkUrl ?: BASE_URL
        val networkManager = NetworkManager.getINSTANCE
        
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .addConverterFactory(ProtoConverterFactory.create())
            .client(createRetrofitClient(authStorage))
            .connectTimeout(networkManager.connectionTimeout, TimeUnit.SECONDS)
            .readTimeout(networkManager.readTimeout, TimeUnit.SECONDS)
            .build()
    }
    
    private fun createRetrofitClient(authStorage: AuthStorage): okhttp3.OkHttpClient {
        val builder = okhttp3.OkHttpClient.Builder()
            .callTimeout(networkModule.callTimeout, TimeUnit.SECONDS)
            .addNetworkInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Authorization", "Bearer ${authStorage.getAccessToken()}")
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .build()
                
                val response = chain.proceed(request)
                
                // Handle token refresh
                if (response.code == 401) {
                    val refreshResponse = authStorage.refreshToken()
                    if (refreshResponse != null && response.code == 401) {
                        val newRequest = chain.request().newBuilder()
                            .header("Authorization", "Bearer ${refreshResponse.accessToken}")
                            .build()
                        chain.proceed(newRequest)
                    } else if (refreshResponse != null) {
                        authStorage.setAccessToken(refreshResponse.accessToken)
                        authStorage.setRefreshToken(refreshResponse.refreshToken)
                        
                        val newRequest = chain.request().newBuilder()
                            .header("Authorization", "Bearer ${refreshResponse.accessToken}")
                            .build()
                        chain.proceed(newRequest)
                    }
                }
                
                response
            }
        
        return builder.build()
    }
    
    fun getRetrofitClientOffline(): Retrofit {
        val baseUrl = BASE_URL_OFFLINE
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .addConverterFactory(ProtoConverterFactory.create())
            .build()
    }
}

enum class ConnectionType {
    ONLINE, OFFLINE, METRIC_AWARE, DEFAULT
}

/**
 * Base API response wrapper
 */
sealed class ApiResult<T> {
    class Success<T>(val data: T) : ApiResult<T>()
    class Error(val code: Int, val message: String) : ApiResult<T>()
    class NetworkError(val code: Int, val message: String, val error: Throwable?) : ApiResult<T>()
}