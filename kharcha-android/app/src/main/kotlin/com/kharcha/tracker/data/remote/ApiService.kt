package com.kharcha.tracker.data.remote

import com.kharcha.tracker.data.remote.dto.AuthResponse
import com.kharcha.tracker.data.remote.dto.LoginRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {
    @POST("${ApiConstants.API_VERSION}/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("${ApiConstants.API_VERSION}/auth/register")
    suspend fun register(@Body request: LoginRequest): Response<AuthResponse>

    @GET("${ApiConstants.API_VERSION}/health")
    suspend fun healthCheck(): Response<Map<String, String>>
}
