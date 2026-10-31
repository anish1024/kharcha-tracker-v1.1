package com.kharcha.tracker.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Mirrors the backend `Base` model (FastAPI `response_model` for
 * `/auth/login`, `/auth/refresh` and the magic-link endpoints).
 *
 * Wire shape (verified against `kharcha_service/models/base.py`):
 * ```json
 * {
 *   "auth_user_id": "<uuid>",
 *   "auth_token": "<jwt>",
 *   "auth_token_expiry": "<iso-8601>",
 *   "refresh_token": "<jwt>" | null,
 *   "refresh_token_expiry": "<iso-8601>" | null
 * }
 * ```
 *
 * UUIDs and datetimes arrive as strings and are kept as strings on the
 * client — no `java.time` parsing, which would not be safe on minSdk 24
 * without core library desugaring.
 */
data class AuthTokens(
    @SerializedName("auth_user_id")
    val authUserId: String? = null,
    @SerializedName("auth_token")
    val authToken: String? = null,
    @SerializedName("auth_token_expiry")
    val authTokenExpiry: String? = null,
    @SerializedName("refresh_token")
    val refreshToken: String? = null,
    @SerializedName("refresh_token_expiry")
    val refreshTokenExpiry: String? = null
)
