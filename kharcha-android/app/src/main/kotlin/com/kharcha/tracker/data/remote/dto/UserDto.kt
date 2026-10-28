package com.kharcha.tracker.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Mirrors the backend `User` model (returned by `POST /auth/signup` with
 * 201 and by `GET /users/me`).
 *
 * The backend `User` model also declares a `password` field (empty string
 * on the wire). It is deliberately not mapped here; Gson ignores fields
 * that are not part of the data class.
 */
data class UserDto(
    @SerializedName("id")
    val id: String? = null,
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("email")
    val email: String? = null,
    @SerializedName("avatar_url")
    val avatarUrl: String? = null
)
