package com.kharcha.tracker.data.remote

import com.kharcha.tracker.data.remote.dto.AuthTokens
import com.kharcha.tracker.data.remote.dto.LoginRequest
import com.kharcha.tracker.data.remote.dto.RefreshRequest
import com.kharcha.tracker.data.remote.dto.SignupRequest
import com.kharcha.tracker.data.remote.dto.UserDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Retrofit interface for the kharcha gateway (FastAPI, base path `/api/v1`).
 *
 * Endpoint contracts verified against `kharcha_gateway/app/main.py`:
 * - `POST /auth/login`   -> 200 `Base` ([AuthTokens])
 * - `POST /auth/refresh` -> 200 `Base` ([AuthTokens]; refresh token is single-use, rotated)
 * - `POST /auth/signup`  -> 201 `User` ([UserDto])
 * - `GET  /users/me`     -> 200 `User` ([UserDto]; requires `Authorization: Bearer`)
 */
interface ApiService {

    @POST("${ApiConstants.API_VERSION}/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthTokens>

    @POST("${ApiConstants.API_VERSION}/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): Response<AuthTokens>

    @POST("${ApiConstants.API_VERSION}/auth/signup")
    suspend fun signup(@Body request: SignupRequest): Response<UserDto>

    @GET("${ApiConstants.API_VERSION}/users/me")
    suspend fun me(): Response<UserDto>

    @GET("${ApiConstants.API_VERSION}/health")
    suspend fun healthCheck(): Response<Map<String, String>>
}
