package com.kharcha.tracker.models

import com.google.gson.annotations.SerializedName

/**
 * User model for authentication and user management
 */
data class User(
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("username")
    val username: String,
    @SerializedName("email")
    val email: String,
    @SerializedName("full_name")
    val fullName: String?,
    @SerializedName("phone")
    val phone: String?,
    @SerializedName("role")
    val role: String,
    @SerializedName("organization")
    val organization: String?,
    @SerializedName("avatar")
    val avatar: String?,
    @SerializedName("is_active")
    val isActive: Boolean,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("updated_at")
    val updatedAt: String,
    @SerializedName("two_factor_enabled")
    val twoFactorEnabled: Boolean? = null,
    @SerializedName("last_login_at")
    val lastLoginAt: String?,
    @SerializedName("preferences")
    val preferences: UserPreferences? = null
) {
    val id: String = userId
    
    data class UserPreferences(
        @SerializedName("theme")
        val theme: String = "system",
        @SerializedName("currency")
        val currency: String = "INR",
        @SerializedName("language")
        val language: String = "en",
        @SerializedName("notification_enabled")
        val notificationEnabled: Boolean = true,
        @SerializedName("dark_mode")
        val darkMode: Boolean = false
    )
}

/**
 * Authentication token response
 */
data class AuthTokens(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("refresh_token")
    val refreshToken: String,
    @SerializedName("token_type")
    val tokenType: String,
    @SerializedName("expires_in")
    val expiresIn: Long,
    @SerializedName("refresh_expires_in")
    val refreshExpiresIn: Long,
    @SerializedName("scope")
    val scope: String? = null,
    @SerializedName("user_id")
    val userId: String? = null,
    @SerializedName("user")
    val user: User? = null
) {
    val user: User? = user?.copy(userId = userId)
}

/**
 * Login request
 */
data class LoginRequest(
    @SerializedName("username")
    val username: String,
    @SerializedName("password")
    val password: String,
    @SerializedName("two_factor_code")
    val twoFactorCode: String? = null,
    @SerializedName("magic_link_token")
    val magicLinkToken: String? = null,
    @SerializedName("remember_me")
    val rememberMe: Boolean? = null
)

/**
 * Signup request
 */
data class SignupRequest(
    @SerializedName("username")
    val username: String,
    @SerializedName("email")
    val email: String,
    @SerializedName("password")
    val password: String,
    @SerializedName("full_name")
    val fullName: String?,
    @SerializedName("phone")
    val phone: String?,
    @SerializedName("organization")
    val organization: String? = null
)

/**
 * Password reset request
 */
data class PasswordResetRequest(
    @SerializedName("email")
    val email: String
)

/**
 * Forgot password request
 */
data class ForgotPasswordRequest(
    @SerializedName("email")
    val email: String
)

/**
 * Change password request
 */
data class ChangePasswordRequest(
    @SerializedName("current_password")
    val currentPassword: String,
    @SerializedName("new_password")
    val newPassword: String
)

/**
 * Magic link verification
 */
data class MagicLinkVerificationRequest(
    @SerializedName("magic_link_token")
    val magicLinkToken: String,
    @SerializedName("code")
    val code: String
)

/**
 * Refresh token request
 */
data class RefreshTokenRequest(
    @SerializedName("refresh_token")
    val refreshToken: String
)

/**
 * Logout request (tokenless)
 */
data class LogoutRequest(
    @SerializedName("access_token")
    val accessToken: String
)

/**
 * Token refresh response
 */
data class TokenRefreshResponse(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("expires_in")
    val expiresIn: Long,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("user")
    val user: User? = null
)

/**
 * User profile response
 */
data class UserProfileResponse(
    @SerializedName("user")
    val user: User,
    @SerializedName("organization")
    val organization: Organization? = null
)

/**
 * Tokenless session (for device-based auth)
 */
data class TokenlessSession(
    @SerializedName("device_id")
    val deviceId: String,
    @SerializedName("session_id")
    val sessionId: String,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("expires_at")
    val expiresAt: String,
    @SerializedName("device_info")
    val deviceInfo: DeviceInfo? = null
) {
    data class DeviceInfo(
        @SerializedName("device_model")
        val deviceModel: String,
        @SerializedName("device_os")
        val deviceOS: String,
        @SerializedName("device_os_version")
        val deviceOSVersion: String,
        @SerializedName("app_version")
        val appVersion: String,
        @SerializedName("app_build")
        val appBuild: String
    )
}