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
