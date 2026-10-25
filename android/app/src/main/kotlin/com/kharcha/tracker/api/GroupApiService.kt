package com.kharcha.tracker.api

import com.kharcha.tracker.models.*
import retrofit2.Response
import retrofit2.http.*

interface GroupApiService {
    
    @GET("/api/v1/groups")
    suspend fun getGroups(@Header("Authorization") token: String, @Query("userId") userId: String): Response<GroupsResponse>
    
    @GET("/api/v1/groups/{groupId}")
    suspend fun getGroup(@Header("Authorization") token: String, @Path("groupId") groupId: String): Response<Group>
    
    @POST("/api/v1/groups")
    suspend fun createGroup(@Header("Authorization") token: String, @Body request: CreateGroupRequest): Response<Group>
    
    @PUT("/api/v1/groups/{groupId}")
    suspend fun updateGroup(@Header("Authorization") token: String, @Path("groupId") groupId: String, @Body request: UpdateGroupRequest): Response<Group>
    
    @DELETE("/api/v1/groups/{groupId}")
    suspend fun deleteGroup(@Header("Authorization") token: String, @Path("groupId") groupId: String): Response<Unit>
    
    @POST("/api/v1/groups/{groupId}/settlement")
    suspend fun createSettlement(@Header("Authorization") token: String, @Path("groupId") groupId: String, @Body request: SettlementRequest): Response<Settlement>
    
    @GET("/api/v1/groups/{groupId}/settlements")
    suspend fun getSettlements(@Header("Authorization") token: String, @Path("groupId") groupId: String): Response<List<Settlement>>
    
    @GET("/api/v1/groups/{groupId}/settlements/approvals")
    suspend fun getSettlementApprovals(@Header("Authorization") token: String, @Path("groupId") groupId: String): Response<SettlementApprovalResponse>
    
    @POST("/api/v1/groups/{groupId}/settlement/{settlementId}/approve")
    suspend fun approveSettlement(@Header("Authorization") token: String, @Path("groupId") groupId: String, @Path("settlementId") settlementId: String): Response<Settlement>
    
    @POST("/api/v1/groups/{groupId}/settlement/{settlementId}/reject")
    suspend fun rejectSettlement(@Header("Authorization") token: String, @Path("groupId") groupId: String, @Path("settlementId") settlementId: String, @Body request: SettlementRejectRequest): Response<Settlement>
    
    @DELETE("/api/v1/groups/{groupId}/settlement/{settlementId}")
    suspend fun cancelSettlement(@Header("Authorization") token: String, @Path("groupId") groupId: String, @Path("settlementId") settlementId: String): Response<Unit>
    
    @GET("/api/v1/groups/{groupId}/expenses")
    suspend fun getGroupExpenses(@Header("Authorization") token: String, @Path("groupId") groupId: String): Response<GroupExpensesResponse>
    
    @GET("/api/v1/groups/{groupId}/income-expenses")
    suspend fun getIncomeExpenses(@Header("Authorization") token: String, @Path("groupId") groupId: String): Response<IncomeExpensesResponse>
    
    @GET("/api/v1/groups/{groupId}/balance")
    suspend fun getBalance(@Header("Authorization") token: String, @Path("groupId") groupId: String): Response<BalanceResponse>
    
    @POST("/api/v1/groups/{groupId}/settle")
    suspend fun settleExpense(@Header("Authorization") token: String, @Path("groupId") groupId: String): Response<SettlementResponse>
    
    @POST("/api/v1/groups/{groupId}/dispute")
    suspend fun disputeSettlement(@Header("Authorization") token: String, @Path("groupId") groupId: String, @Body request: SettlementDisputeRequest): Response<Settlement>
    
    @GET("/api/v1/groups/{groupId}/disputes")
    suspend fun getSettlementDisputes(@Header("Authorization") token: String, @Path("groupId") groupId: String): Response<SettlementDisputeResponse>
    
    @POST("/api/v1/groups/{groupId}/dispute/{disputeId}/approve")
    suspend fun approveSettlementDispute(@Header("Authorization") token: String, @Path("groupId") groupId: String, @Path("disputeId") disputeId: String): Response<Settlement>
    
    @DataClass("GroupsResponse")
    data class GroupsResponse(
        val groups: List<Group>,
        val total: Int,
        val limit: Int,
        val page: Int,
        val hasMore: Boolean
    )
    
    @DataClass("GroupExpensesResponse")
    data class GroupExpensesResponse(
        val expenses: List<Expense>,
        val totalAmount: Double,
        val expenseCount: Int,
        val startDate: String,
        val endDate: String
    )
    
    @DataClass("IncomeExpensesResponse")
    data class IncomeExpensesResponse(
        val totalExpenses: Double,
        val totalIncome: Double,
        val netBalance: Double,
        val expensesCount: Int,
        val incomeCount: Int,
        val startDate: String,
        val endDate: String,
        val currency: String = "INR"
    )
    
    @DataClass("BalanceResponse")
    data class BalanceResponse(
        val totalExpenses: Double,
        val pendingPayments: Double,
        val pendingSettlements: Double,
        val pendingIncome: Double,
        val netBalance: Double,
        val currency: String = "INR"
    )
    
    @DataClass("SettlementApprovalResponse")
    data class SettlementApprovalResponse(
        val success: Boolean,
        val data: Settlement?,
        val message: String,
        val settlements: List<Settlement>? = null
    )
    
    @DataClass("SettlementDisputeResponse")
    data class SettlementDisputeResponse(
        val disputes: List<SettlementDispute>,
        val totalDisputedAmount: Double
    ) {
        data class SettlementDispute(
            val id: String,
            val settlementId: String,
            val settlementAmount: Double,
            val disputedAmount: Double,
            val disputeReason: String,
            val status: String,
            val createdAt: String,
            val updatedAt: String,
            val userId: String,
            val userName: String
        )
    }
    
    @DataClass("SettlementRejectRequest")
    data class SettlementRejectRequest(
        val reason: String
    )
    
    @DataClass("SettlementDisputeRequest")
    data class SettlementDisputeRequest(
        val amount: Double? = null,
        val reason: String? = null
    )
}