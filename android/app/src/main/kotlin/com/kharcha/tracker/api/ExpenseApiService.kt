package com.kharcha.tracker.api

import com.kharcha.tracker.models.*
import retrofit2.Response
import retrofit2.http.*

interface ExpenseApiService {
    
    @GET("/api/v1/expenses")
    suspend fun getExpenses(@Header("Authorization") token: String): Response<ExpenseResponse>
    
    @GET("/api/v1/expenses/{expenseId}")
    suspend fun getExpense(@Header("Authorization") token: String, @Path("expenseId") expenseId: String): Response<Expense>
    
    @POST("/api/v1/expenses")
    suspend fun createExpense(@Header("Authorization") token: String, @Body request: CreateExpenseRequest): Response<Expense>
    
    @PUT("/api/v1/expenses/{expenseId}")
    suspend fun updateExpense(@Header("Authorization") token: String, @Path("expenseId") expenseId: String, @Body request: UpdateExpenseRequest): Response<Expense>
    
    @DELETE("/api/v1/expenses/{expenseId}")
    suspend fun deleteExpense(@Header("Authorization") token: String, @Path("expenseId") expenseId: String, @Body request: DeleteExpenseRequest): Response<Expense>
    
    @POST("/api/v1/expenses/bulk")
    suspend fun createBulkExpense(@Header("Authorization") token: String, @Body request: BulkCreateExpenseRequest): Response<BulkExpenseResponse>
    
    @POST("/api/v1/expenses/{expenseId}/submit")
    suspend fun submitExpense(@Header("Authorization") token: String, @Path("expenseId") expenseId: String): Response<Expense>
    
    @POST("/api/v1/expenses/{expenseId}/approve")
    suspend fun approveExpense(@Header("Authorization") token: String, @Path("expenseId") expenseId: String): Response<Expense>
    
    @POST("/api/v1/expenses/{expenseId}/reject")
    suspend fun rejectExpense(@Header("Authorization") token: String, @Path("expenseId") expenseId: String, @Body request: ExpenseRejectRequest): Response<Expense>
    
    @POST("/api/v1/expenses/{expenseId}/add-receipt")
    suspend fun addReceipt(@Header("Authorization") token: String, @Path("expenseId") expenseId: String, @Body request: ReceiptUploadRequest): Response<Expense>
    
    @DELETE("/api/v1/expenses/{expenseId}/attachments/{attachmentId}")
    suspend fun deleteAttachment(@Header("Authorization") token: String, @Path("expenseId") expenseId: String, @Path("attachmentId") attachmentId: String): Response<Unit>
    
    @GET("/api/v1/expenses/{expenseId}/settlements")
    suspend fun getSettlements(@Header("Authorization") token: String, @Path("expenseId") expenseId: String): Response<List<Settlement>>
    
    @GET("/api/v1/expenses/{expenseId}/history")
    suspend fun getExpenseHistory(@Header("Authorization") token: String, @Path("expenseId") expenseId: String): Response<ExpenseHistoryResponse>
    
    @POST("/api/v1/expenses/{expenseId}/mark-settled")
    suspend fun markAsSettled(@Header("Authorization") token: String, @Path("expenseId") expenseId: String, @Body request: SettlementMarkRequest): Response<SettlementUpdateResponse>
    
    @POST("/api/v1/expenses/{expenseId}/settle")
    suspend fun settleExpense(@Header("Authorization") token: String, @Path("expenseId") expenseId: String, @Body request: SettlementRequest): Response<SettlementResponse>
    
    @GET("/api/v1/expenses/disputes")
    suspend fun getExpensesDisputes(@Header("Authorization") token: String): Response<ExpenseDisputeResponse>
    
    @POST("/api/v1/expenses/{expenseId}/dispute")
    suspend fun disputeExpense(@Header("Authorization") token: String, @Path("expenseId") expenseId: String, @Body request: ExpenseDisputeRequest): Response<Expense>
    
    @GET("/api/v1/expenses/{expenseId}/approve/disputes")
    suspend fun getApproveDisputes(@Header("Authorization") token: String, @Path("expenseId") expenseId: String): Response<ExpenseDisputeResponse>
    
    @POST("/api/v1/expenses/{expenseId}/approve-dispute")
    suspend fun approveDispute(@Header("Authorization") token: String, @Path("expenseId") expenseId: String, @Body request: DisputeApprovalRequest): Response<SettlementApprovalResponse>
    
    @GET("/api/v1/expenses/{expenseId}/approve-status")
    suspend fun getApproveStatus(@Header("Authorization") token: String, @Path("expenseId") expenseId: String): Response<ExpenseApprovalResponse>
    
    @DataClass("BulkExpenseResponse")
    data class BulkExpenseResponse(
        val success: Boolean,
        val data: List<Expense>?,
        val message: String
    )
    
    @DataClass("ExpenseHistoryResponse")
    data class ExpenseHistoryResponse(
        val expenses: List<Expense>,
        val totalAmount: Double,
        val startDate: String,
        val endDate: String
    )
    
    @DataClass("ExpenseDisputeResponse")
    data class ExpenseDisputeResponse(
        val disputes: List<ExpenseDispute>
    ) {
        data class ExpenseDispute(
            val id: String,
            val expenseId: String,
            val expenseName: String,
            val disputedAmount: Double,
            val disputeReason: String,
            val status: String,
            val createdAt: String,
            val updatedAt: String,
            val userId: String,
            val userName: String
        )
    }
    
    @DataClass("ExpenseApprovalResponse")
    data class ExpenseApprovalResponse(
        val status: String,
        val approved: Boolean,
        val pendingApprovals: Int,
        val nextApprover: String?
    )
    
    @DataClass("SettlementResponse")
    data class SettlementResponse(
        val success: Boolean,
        val data: Settlement?,
        val message: String,
        val settlementId: String?
    )
    
    @DataClass("SettlementUpdateResponse")
    data class SettlementUpdateResponse(
        val success: Boolean,
        val data: Settlement?,
        val message: String
    )
    
    @DataClass("SettlementApprovalResponse")
    data class SettlementApprovalResponse(
        val success: Boolean,
        val data: Settlement,
        val message: String
    )
    
    @DataClass("ExpenseDisputeRequest")
    data class ExpenseDisputeRequest(
        val reason: String
    )
    
    @DataClass("ExpenseRejectRequest")
    data class ExpenseRejectRequest(
        val reason: String
    )
    
    @DataClass("ReceiptUploadRequest")
    data class ReceiptUploadRequest(
        @Multipart
        val receipt: MultipartBody.Part?,
        val notes: String? = null
    )
    
    @DataClass("SettlementRequest")
    data class SettlementRequest(
        val amount: Double,
        val currency: String = "INR",
        val paymentMode: String = "cash",
        val reference: String? = null,
        val notes: String? = null,
        val userId: String
    )
    
    @DataClass("SettlementMarkRequest")
    data class SettlementMarkRequest(
        val status: String = "pending"
    )
    
    @DataClass("ExpenseUpdateReceiptRequest")
    data class ExpenseUpdateReceiptRequest(
        @Multipart
        val receipt: MultipartBody.Part?,
        val notes: String? = null
    )
}