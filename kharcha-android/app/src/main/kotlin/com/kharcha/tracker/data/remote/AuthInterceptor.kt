package com.kharcha.tracker.data.remote

import com.google.gson.Gson
import com.kharcha.tracker.data.local.TokenStore
import com.kharcha.tracker.data.remote.dto.AuthTokens
import com.kharcha.tracker.data.remote.dto.RefreshRequest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Bearer-token lifecycle for every API request:
 *
 * 1. Attaches `Authorization: Bearer <auth_token>` to non-public requests
 *    (the login/refresh endpoints are left untouched).
 * 2. On a 401, exchanges the stored refresh token for a fresh session and
 *    replays the original request exactly once with the new token. The
 *    backend rotates refresh tokens and they are single-use, so the
 *    exchange is single-flight: exactly one refresh runs while others wait,
 *    and a waiter that finds a fresh session already stored reuses it
 *    instead of burning the newly rotated token.
 * 3. If the exchange fails, the stored session is cleared and
 *    [TokenStore.sessionInvalidated] is emitted so the UI force-signs-out.
 *
 * The refresh call itself goes through a dedicated [OkHttpClient] with no
 * interceptors, avoiding recursion and pool exhaustion on the main client.
 */
class AuthInterceptor(
    private val tokenStore: TokenStore,
    private val baseUrl: String = ApiConstants.BASE_URL
) : Interceptor {

    private val refreshMutex = Mutex()
    private val gson = Gson()

    private val refreshClient = OkHttpClient.Builder()
        .connectTimeout(REFRESH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(REFRESH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(REFRESH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    // Guarded by [refreshMutex]: the access token issued by the most recent
    // successful exchange (plus when it happened). Lets concurrent waiters
    // reuse a fresh session instead of refreshing again.
    private var lastIssuedAccessToken: String? = null
    private var lastIssuedAtMillis: Long = 0L

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val isAuthEndpoint = isAuthEndpoint(original.url)

        val token = if (isAuthEndpoint) null else tokenStore.authTokenNow()
        val request =
            if (token.isNullOrBlank()) {
                original
            } else {
                original.newBuilder()
                    .header(HEADER_AUTHORIZATION, "Bearer $token")
                    .build()
            }

        val response = chain.proceed(request)

        val retryable = response.code == 401 &&
            !isAuthEndpoint &&
            request.header(HEADER_ALREADY_RETRIED) == null &&
            !tokenStore.refreshTokenNow().isNullOrBlank()

        if (retryable && runBlocking { refreshSession() }) {
            val newToken = tokenStore.authTokenNow()
            if (!newToken.isNullOrBlank()) {
                response.close()
                val replay = request.newBuilder()
                    .header(HEADER_AUTHORIZATION, "Bearer $newToken")
                    .header(HEADER_ALREADY_RETRIED, "true")
                    .build()
                return chain.proceed(replay)
            }
        }
        // Not retryable, or the refresh failed: the session was cleared and
        // [TokenStore.sessionInvalidated] emitted; surface the original 401.
        return response
    }

    /**
     * Single-flight refresh. Returns true when a usable session is stored
     * afterwards (either freshly exchanged or exchanged by a previous
     * waiter).
     */
    private suspend fun refreshSession(): Boolean {
        val lock = refreshMutex.lock()
        try {
            // A previous waiter already exchanged the refresh token and a
            // fresh session is stored: reuse it (refresh tokens are
            // single-use and rotated).
            val current = tokenStore.authTokenNow()
            if (
                !current.isNullOrBlank() &&
                current == lastIssuedAccessToken &&
                System.currentTimeMillis() - lastIssuedAtMillis < FRESH_SESSION_WINDOW_MS
            ) {
                return true
            }

            val refreshToken = tokenStore.refreshTokenNow() ?: return false
            return exchangeRefreshToken(refreshToken)
        } finally {
            refreshMutex.unlock(lock)
        }
    }

    private suspend fun exchangeRefreshToken(refreshToken: String): Boolean {
        val url = buildString {
            append(baseUrl.trimEnd('/'))
            append('/')
            append(ApiConstants.API_VERSION)
            append("/auth/refresh")
        }
        val request = Request.Builder()
            .url(url)
            .post(gson.toJson(RefreshRequest(refreshToken)).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return try {
            refreshClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    invalidateSession()
                    return false
                }
                val tokens = response.body?.string()?.let { gson.fromJson(it, AuthTokens::class.java) }
                if (tokens == null || tokens.authToken.isNullOrBlank()) {
                    invalidateSession()
                    return false
                }
                tokenStore.save(tokens)
                lastIssuedAccessToken = tokens.authToken
                lastIssuedAtMillis = System.currentTimeMillis()
                true
            }
        } catch (_: Exception) {
            // Network failure mid-refresh: we cannot prove the session is
            // still usable, so fail closed and force a re-login.
            invalidateSession()
            false
        }
    }

    private suspend fun invalidateSession() {
        tokenStore.clear()
        tokenStore.emitInvalidated()
    }

    private fun isAuthEndpoint(url: HttpUrl): Boolean {
        val segments = url.pathSegments
        return segments.contains("auth") &&
            (segments.contains("login") || segments.contains("refresh"))
    }

    private companion object {
        const val HEADER_AUTHORIZATION = "Authorization"
        const val HEADER_ALREADY_RETRIED = "X-Kharcha-Retried"
        const val REFRESH_TIMEOUT_SECONDS = 15L
        const val FRESH_SESSION_WINDOW_MS = 60_000L
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
