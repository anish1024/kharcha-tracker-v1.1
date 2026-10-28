package com.kharcha.tracker.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Request body for `POST /auth/login` (backend reads `email` + `password`). */
data class LoginRequest(
    @SerializedName("email")
    val email: String,
    @SerializedName("password")
    val password: String
)

/** Request body for `POST /auth/refresh` (backend reads `refresh_token`). */
data class RefreshRequest(
    @SerializedName("refresh_token")
    val refreshToken: String
)

/** Request body for `POST /auth/signup` (backend reads name/email/password/avatar_url). */
data class SignupRequest(
    @SerializedName("name")
    val name: String,
    @SerializedName("email")
    val email: String,
    @SerializedName("password")
    val password: String,
    @SerializedName("avatar_url")
    val avatarUrl: String? = null
)

/** Request body for `POST /auth/magic-link` (backend reads `email`). */
data class MagicLinkRequest(
    @SerializedName("email")
    val email: String
)

/** Request body for `POST /auth/magic-link/verify` (backend reads `token`). */
data class MagicLinkVerifyRequest(
    @SerializedName("token")
    val token: String
)

/**
 * Response of `POST /auth/magic-link` (202).
 *
 * In this gateway build the token is returned in the response (no mail
 * transport), so the client verifies it immediately to complete the
 * passwordless login - new accounts are auto-provisioned on verify.
 */
data class MagicLinkResponse(
    @SerializedName("email")
    val email: String? = null,
    @SerializedName("magic_token")
    val magicToken: String? = null,
    @SerializedName("magic_link")
    val magicLink: String? = null,
    @SerializedName("expires_at")
    val expiresAt: String? = null
)
