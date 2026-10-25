package com.kharcha.tracker.api

import com.kharcha.tracker.models.*
import retrofit2.Response
import retrofit2.http.*

interface AuthApiService {
    
    @POST("/api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthTokens>
    
    @POST("/api/v1/auth/signup")
    suspend fun signup(@Body request: SignupRequest): Response<AuthTokens>
    
    @POST("/api/v1/auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Response<Unit>
    
    @POST("/api/v1/auth/reset-password")
    suspend fun resetPassword(@Body request: PasswordResetRequest): Response<AuthTokens>
    
    @POST("/api/v1/auth/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<Unit>
    
    @POST("/api/v1/auth/logout")
    suspend fun logout(@Body request: LogoutRequest): Response<Unit>
    
    @POST("/api/v1/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<AuthTokenRefreshResponse>
    
    @POST("/api/v1/auth/magic-link/send")
    suspend fun sendMagicLink(@Body request: MagicLinkVerificationRequest): Response<Unit>
    
    @POST("/api/v1/auth/magic-link/verify")
    suspend fun verifyMagicLink(@Body request: MagicLinkVerificationRequest): Response<AuthTokens>
    
    @GET("/api/v1/auth/profile")
    suspend fun getProfile(@Header("Authorization") token: String): Response<UserProfileResponse>
    
    @GET("/api/v1/auth/verify-session")
    suspend fun verifySession(@Header("Authorization") token: String): Response<SessionStatusResponse>
    
    @POST("/api/v1/auth/logout-all-devices")
    suspend fun logoutAllDevices(@Body request: LogoutAllDevicesRequest): Response<Unit>
    
    @GET("/api/v1/auth/reset-preference")
    suspend fun resetPreferences(): Response<Unit>
    
    @DataClass("AuthTokens")
    data class AuthTokenRefreshResponse(
        val accessToken: String,
        val refreshToken: String,
        val expiresIn: Long,
        val userId: String
    )
    
    @DataClass("SessionStatusResponse")
    data class SessionStatusResponse(
        val isLoggedIn: Boolean,
        val tokenlessMode: Boolean,
        val deviceId: String,
        val userId: String,
        val username: String
    )
    
    @DataClass("LogoutAllDevicesRequest")
    data class LogoutAllDevicesRequest(
        @SerializedName("device_id")
        val deviceId: String
    )
}

/**
 * Token refresh response (new format)
 */
data class AuthTokenRefreshResponse(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("refresh_token")
    val refreshToken: String,
    @SerializedName("token_type")
    val tokenType: String = "Bearer",
    @SerializedName("expires_in")
    val expiresIn: Long,
    @SerializedName("refresh_expires_in")
    val refreshExpiresIn: Long = 604800,  // 7 days in seconds
    @SerializedName("scope")
    val scope: String? = null,
    @SerializedName("user_id")
    val userId: String? = null
) {
    val user: User? = null
    val userId: String
        get() = userId ?: tokens?.userId ?: ""
}

/**
 * Session status response (when using device-based auth)
 */
data class SessionStatusResponse(
    @SerializedName("is_logged_in")
    val isLoggedIn: Boolean,
    @SerializedName("tokenless_mode")
    val tokenlessMode: Boolean,
    @SerializedName("device_id")
    val deviceId: String,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("username")
    val username: String,
    @SerializedName("user")
    val user: User? = null,
    @SerializedName("session")
    val session: TokenlessSession? = null
) {
    val accessToken: String? = null
    val refreshToken: String? = null
    
    fun toAuthTokens(): AuthTokens? {
        return if (tokenlessMode) {
            null  // Return null, user needs to authenticate
        } else {
            User(
                userId = userId ?: session?.userId ?: "",
                username = username ?: session?.userId ?: "",
                email = "",
                fullName = user?.fullName,
                phone = null,
                role = "user",
                organization = null,
                avatar = user?.avatar,
                isActive = true,
                createdAt = user?.createdAt ?: "",
                updatedAt = user?.updatedAt ?: "",
                twoFactorEnabled = user?.twoFactorEnabled,
                lastLoginAt = user?.lastLoginAt,
                preferences = user?.preferences
            )
        }
    }
}