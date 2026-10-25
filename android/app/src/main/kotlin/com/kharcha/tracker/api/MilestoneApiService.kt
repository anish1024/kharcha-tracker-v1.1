package com.kharcha.tracker.api

import com.kharcha.tracker.models.Milestone
import com.kharcha.tracker.models.entities.Milestone as MilestoneEntity
import retrofit2.Response
import retrofit2.http.*

interface MilestoneApiService {
    
    @GET("/api/v1/milestones")
    suspend fun getMilestones(@Header("Authorization") token: String): Response<MilestonesResponse>
    
    @GET("/api/v1/milestones/{milestoneId}")
    suspend fun getMilestone(@Header("Authorization") token: String, @Path("milestoneId") milestoneId: String): Response<Milestone>
    
    @POST("/api/v1/milestones")
    suspend fun createMilestone(@Header("Authorization") token: String, @Body request: CreateMilestoneRequest): Response<Milestone>
    
    @PUT("/api/v1/milestones/{milestoneId}")
    suspend fun updateMilestone(@Header("Authorization") token: String, @Path("milestoneId") milestoneId: String, @Body request: UpdateMilestoneRequest): Response<Milestone>
    
    @DELETE("/api/v1/milestones/{milestoneId}")
    suspend fun deleteMilestone(@Header("Authorization") token: String, @Path("milestoneId") milestoneId: String): Response<Unit>
    
    @POST("/api/v1/milestones/{milestoneId}/transactions")
    suspend fun createTransaction(@Header("Authorization") token: String, @Path("milestoneId") milestoneId: String, @Body request: MilestoneTransactionRequest): Response<TransactionResponse>
    
    @GET("/api/v1/milestones/{milestoneId}/transactions")
    suspend fun getTransactions(@Header("Authorization") token: String, @Path("milestoneId") milestoneId: String): Response<TransactionResponse>
    
    @DataClass("MilestonesResponse")
    data class MilestonesResponse(
        val milestones: List<Milestone>,
        val total: Int,
        val limit: Int,
        val page: Int,
        val hasMore: Boolean
    )
    
    @DataClass("TransactionResponse")
    data class TransactionResponse(
        val transactions: List<com.kharcha.tracker.models.Transaction>,
        val totalAmount: Double,
        val transactionCount: Int
    )
}